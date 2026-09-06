package com.gharfix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharfix.dto.RazorpayOrderRequest;
import com.gharfix.dto.RazorpayOrderResponse;
import com.gharfix.dto.RazorpayVerifyRequest;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.gharfix.repository.BookingRepository;
import com.gharfix.repository.UserRepository;
import com.gharfix.service.RazorpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RazorpayPaymentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RazorpayService razorpayService;

    private static final String TEST_KEY_ID = "rzp_test_TYlYzkePVAzKNN";
    private static final String TEST_KEY_SECRET = "UJVqlxF6r1Xw5dMNDuCP2DRT";

    private User testUser;

    private String calculateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @BeforeEach
    void setUp() {
        when(razorpayService.getKeyId()).thenReturn(TEST_KEY_ID);

        testUser = userRepository.findByEmail("razorpay_test_user@gharfix.com").orElseGet(() -> {
            User u = new User();
            u.setName("Razorpay Test User");
            u.setEmail("razorpay_test_user@gharfix.com");
            u.setPasswordHash("hash123");
            u.setPhone("9988776655");
            return userRepository.save(u);
        });
    }

    private Booking createTestBooking(String orderId, String paymentStatus) {
        Booking booking = new Booking();
        booking.setUser(testUser);
        booking.setServiceName("Electrician");
        booking.setAddress("456 Park Avenue");
        booking.setCity("Mumbai");
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setBookingTime(LocalTime.of(10, 0));
        booking.setStatus("requested");
        booking.setPaymentStatus(paymentStatus);
        booking.setRazorpayOrderId(orderId);
        booking.setCreatedAt(LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    @Test
    void testGetRazorpayKey_ReturnsPublicIdOnly() throws Exception {
        mockMvc.perform(get("/api/razorpay/key"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key_id", is(TEST_KEY_ID)))
                .andExpect(jsonPath("$.key_secret").doesNotExist());
    }

    @Test
    void testCreateOrder_AmountLessThan100Paise_ReturnsBadRequest() throws Exception {
        RazorpayOrderRequest req = new RazorpayOrderRequest();
        req.setAmount(50L); // 50 paise is less than minimum 100 paise

        mockMvc.perform(post("/api/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("at least 100 paise")));
    }

    @Test
    void testCreateOrder_NullAmount_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Amount is required")));
    }

    @Test
    void testCreateOrder_ValidAmount_ReturnsOrderDetails() throws Exception {
        when(razorpayService.createOrder(anyLong(), anyString(), nullable(String.class)))
                .thenReturn(new RazorpayOrderResponse("order_test_12345", 50000L, "INR"));

        RazorpayOrderRequest req = new RazorpayOrderRequest();
        req.setAmount(50000L); // ₹500 in paise
        req.setCurrency("INR");
        req.setReceipt("rcpt_test_1");

        mockMvc.perform(post("/api/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id", is("order_test_12345")))
                .andExpect(jsonPath("$.amount", is(50000)))
                .andExpect(jsonPath("$.currency", is("INR")));
    }

    @Test
    void testVerifyPayment_MissingFields_ReturnsBadRequest() throws Exception {
        RazorpayVerifyRequest req = new RazorpayVerifyRequest();
        req.setRazorpayOrderId("order_123");
        // payment id and signature missing

        mockMvc.perform(post("/api/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Missing required fields")));
    }

    @Test
    void testVerifyPayment_InvalidSignature_ReturnsBadRequestAndDoesNotMarkPaid() throws Exception {
        Booking saved = createTestBooking("order_inv_123", "UNPAID");

        when(razorpayService.verifyPaymentSignature(
                "order_inv_123",
                "pay_inv_999",
                "invalid_signature_hash"
        )).thenReturn(false);

        RazorpayVerifyRequest req = new RazorpayVerifyRequest();
        req.setRazorpayOrderId("order_inv_123");
        req.setRazorpayPaymentId("pay_inv_999");
        req.setRazorpaySignature("invalid_signature_hash");
        req.setBookingId(saved.getId());

        mockMvc.perform(post("/api/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("Invalid Signature")));

        // Verify booking was NOT marked as paid
        Optional<Booking> checkBooking = bookingRepository.findById(saved.getId());
        assertTrue(checkBooking.isPresent());
        assertEquals("UNPAID", checkBooking.get().getPaymentStatus());
    }

    @Test
    void testVerifyPayment_ValidSignature_ReturnsSuccessAndMarksBookingPaid() throws Exception {
        Booking saved = createTestBooking("order_valid_789", "UNPAID");

        String orderId = "order_valid_789";
        String paymentId = "pay_valid_456";
        String validSignature = calculateHmac(orderId + "|" + paymentId, TEST_KEY_SECRET);

        when(razorpayService.verifyPaymentSignature(orderId, paymentId, validSignature)).thenReturn(true);

        RazorpayVerifyRequest req = new RazorpayVerifyRequest();
        req.setRazorpayOrderId(orderId);
        req.setRazorpayPaymentId(paymentId);
        req.setRazorpaySignature(validSignature);
        req.setBookingId(saved.getId());

        mockMvc.perform(post("/api/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Payment verified successfully")))
                .andExpect(jsonPath("$.order_id", is(orderId)))
                .andExpect(jsonPath("$.payment_id", is(paymentId)))
                .andExpect(jsonPath("$.payment_status", is("PAID")));

        // Verify booking updated in database
        Optional<Booking> updated = bookingRepository.findById(saved.getId());
        assertTrue(updated.isPresent());
        assertEquals("PAID", updated.get().getPaymentStatus());
        assertEquals(paymentId, updated.get().getRazorpayPaymentId());
    }
}
