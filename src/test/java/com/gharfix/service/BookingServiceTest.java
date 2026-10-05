package com.gharfix.service;

import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.WorkerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void testCreateBookingSavesDescription() {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");

        BookingRequestDto dto = new BookingRequestDto();
        dto.setService("Electrician");
        dto.setDate("2026-09-07");
        dto.setTime("09:00");
        dto.setCity("Vadodara");
        dto.setAddress("123 Main Street");
        dto.setDescription("Kitchen power outlet is sparking and needs replacement.");

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.createBooking(user, dto);

        assertNotNull(result);
        assertEquals("Kitchen power outlet is sparking and needs replacement.", result.getDescription());
        assertEquals("Electrician", result.getServiceName());
        assertEquals("123 Main Street", result.getAddress());
        assertEquals("Vadodara", result.getCity());

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertEquals("Kitchen power outlet is sparking and needs replacement.", captor.getValue().getDescription());
    }

    @Test
    void testCreateBookingWithoutDescriptionSucceeds() {
        User user = new User();
        user.setId(2L);

        BookingRequestDto dto = new BookingRequestDto();
        dto.setService("Plumber");
        dto.setDate("2026-09-07");
        dto.setTime("10:00");
        dto.setCity("Vadodara");
        dto.setAddress("456 Park Avenue");
        dto.setDescription(null);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.createBooking(user, dto);

        assertNotNull(result);
        assertEquals(null, result.getDescription());
    }
}
