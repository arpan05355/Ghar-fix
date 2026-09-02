package com.gharfix;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.entity.Worker;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.UserRepository;
import com.gharfix.repository.WorkerRepository;
import com.gharfix.security.WorkerUserDetails;
import com.gharfix.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NegotiationConcurrencyTests {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private MockMvc mockMvc;

    private User testUser;
    private Worker worker1;
    private Worker worker2;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();

        testUser = userRepository.findByEmail("test_customer@gharfix.com").orElseGet(() -> {
            User u = new User();
            u.setName("Test Customer");
            u.setEmail("test_customer@gharfix.com");
            u.setPasswordHash("hash");
            u.setPhone("9998887770");
            return userRepository.save(u);
        });

        worker1 = workerRepository.findByEmail("pro1@gharfix.com").orElseGet(() -> {
            Worker w = new Worker("Pro One", "Plumber", "9000000001", 4.8, null, "5 yrs", "Expert plumber");
            w.setEmail("pro1@gharfix.com");
            w.setPasswordHash("hash");
            w.setServices(new java.util.ArrayList<>(List.of("Plumber")));
            return workerRepository.save(w);
        });

        worker2 = workerRepository.findByEmail("pro2@gharfix.com").orElseGet(() -> {
            Worker w = new Worker("Pro Two", "Plumber", "9000000002", 4.9, null, "6 yrs", "Master plumber");
            w.setEmail("pro2@gharfix.com");
            w.setPasswordHash("hash");
            w.setServices(new java.util.ArrayList<>(List.of("Plumber")));
            return workerRepository.save(w);
        });
    }

    private Booking createOpenBooking() {
        BookingRequestDto dto = new BookingRequestDto();
        dto.setService("Plumber");
        dto.setAddress("123 MG Road");
        dto.setCity("Mumbai");
        dto.setDate(LocalDate.now().plusDays(1).toString());
        dto.setTime("10:00");
        dto.setWorkerId(null); // open request
        return bookingService.createBooking(testUser, dto);
    }

    @Test
    void testProposePriceConcurrencyPreventsOverwriting() {
        Booking booking = createOpenBooking();

        // Worker 1 proposes price
        Booking proposed = bookingService.proposePriceByWorker(booking.getId(), worker1, new BigDecimal("550.00"));
        assertNotNull(proposed.getWorker());
        assertEquals(worker1.getId(), proposed.getWorker().getId());
        assertEquals(new BigDecimal("550.00"), proposed.getProposedPrice());
        assertEquals("PENDING_USER", proposed.getNegotiationStatus());

        // Worker 2 attempts to propose price on the same booking -> MUST fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            bookingService.proposePriceByWorker(booking.getId(), worker2, new BigDecimal("450.00"));
        });

        assertEquals("This request is already being negotiated by another professional.", ex.getMessage());

        // Verify booking in DB was NOT modified by Worker 2
        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(worker1.getId(), refreshed.getWorker().getId());
        assertEquals(new BigDecimal("550.00"), refreshed.getProposedPrice());
    }

    @Test
    void testCounterPriceOnlyAllowedForNegotiatingWorker() {
        Booking booking = createOpenBooking();
        bookingService.proposePriceByWorker(booking.getId(), worker1, new BigDecimal("600.00"));

        // Worker 2 attempts to counter -> MUST fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            bookingService.counterPriceByWorker(booking.getId(), worker2, new BigDecimal("550.00"));
        });
        assertEquals("You are not part of the negotiation on this request.", ex.getMessage());

        // Worker 1 counters -> succeeds
        Booking countered = bookingService.counterPriceByWorker(booking.getId(), worker1, new BigDecimal("580.00"));
        assertEquals(new BigDecimal("580.00"), countered.getProposedPrice());
        assertEquals(worker1.getId(), countered.getWorker().getId());
    }

    @Test
    void testAcceptPriceOnlyAllowedForNegotiatingWorker() {
        Booking booking = createOpenBooking();
        bookingService.proposePriceByWorker(booking.getId(), worker1, new BigDecimal("600.00"));

        // User counters to 500
        bookingService.counterPriceByUser(booking.getId(), testUser.getId(), new BigDecimal("500.00"));

        // Worker 2 attempts to accept price -> MUST fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            bookingService.acceptBookingPriceByWorker(booking.getId(), worker2);
        });
        assertEquals("You are not part of the negotiation on this request.", ex.getMessage());

        // Worker 1 accepts price -> succeeds
        Booking accepted = bookingService.acceptBookingPriceByWorker(booking.getId(), worker1);
        assertEquals("accepted", accepted.getStatus());
        assertEquals("AGREED", accepted.getNegotiationStatus());
        assertEquals(worker1.getId(), accepted.getWorker().getId());
    }

    @Test
    void testAvailableRequestsExcludesOtherWorkersNegotiations() {
        Booking booking1 = createOpenBooking(); // Will be negotiated by Worker 1
        Booking booking2 = createOpenBooking(); // Will remain unassigned

        // Before negotiation: both bookings visible to both workers
        List<Booking> beforeW1 = bookingService.getRequestedBookingsForServices(List.of("Plumber"), worker1.getId());
        List<Booking> beforeW2 = bookingService.getRequestedBookingsForServices(List.of("Plumber"), worker2.getId());
        assertEquals(2, beforeW1.size());
        assertEquals(2, beforeW2.size());

        // Worker 1 proposes price on booking1
        bookingService.proposePriceByWorker(booking1.getId(), worker1, new BigDecimal("500.00"));

        // Worker 1 should still see booking1 and booking2
        List<Booking> afterW1 = bookingService.getRequestedBookingsForServices(List.of("Plumber"), worker1.getId());
        assertEquals(2, afterW1.size());

        // Worker 2 should ONLY see booking2 (booking1 is excluded!)
        List<Booking> afterW2 = bookingService.getRequestedBookingsForServices(List.of("Plumber"), worker2.getId());
        assertEquals(1, afterW2.size());
        assertEquals(booking2.getId(), afterW2.get(0).getId());
    }

    @Test
    void testWorkerDashboardControllerShowsFlashErrorWhenSecondWorkerProposes() throws Exception {
        Booking booking = createOpenBooking();
        bookingService.proposePriceByWorker(booking.getId(), worker1, new BigDecimal("700.00"));

        WorkerUserDetails worker2Details = new WorkerUserDetails(worker2);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(worker2Details, null, worker2Details.getAuthorities());

        // Worker 2 attempts to propose price on the same booking via HTTP POST
        mockMvc.perform(post("/worker/dashboard/requests/" + booking.getId() + "/propose-price")
                        .with(authentication(auth))
                        .param("price", "650"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/worker/dashboard"))
                .andExpect(flash().attribute("flash_error", "This request is already being negotiated by another professional."));

        // Verify booking in DB still belongs to Worker 1
        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(worker1.getId(), refreshed.getWorker().getId());
        assertEquals(new BigDecimal("700.00"), refreshed.getProposedPrice());
    }

    @Test
    void testWorkerDashboardAvailableRequestsListExcludesOtherWorkerNegotiationInView() throws Exception {
        Booking booking = createOpenBooking();
        bookingService.proposePriceByWorker(booking.getId(), worker1, new BigDecimal("700.00"));

        WorkerUserDetails worker2Details = new WorkerUserDetails(worker2);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(worker2Details, null, worker2Details.getAuthorities());

        // Worker 2 loads dashboard
        mockMvc.perform(get("/worker/dashboard").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("availableRequests"))
                .andExpect(model().attribute("availableRequests", List.of())); // Excluded, so list is empty
    }
}
