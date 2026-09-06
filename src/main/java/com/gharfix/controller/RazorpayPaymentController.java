package com.gharfix.controller;

import com.gharfix.dto.RazorpayOrderRequest;
import com.gharfix.dto.RazorpayOrderResponse;
import com.gharfix.dto.RazorpayVerifyRequest;
import com.gharfix.entity.Booking;
import com.gharfix.repository.BookingRepository;
import com.gharfix.service.RazorpayService;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RazorpayPaymentController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentController.class);

    private final RazorpayService razorpayService;
    private final BookingRepository bookingRepository;

    public RazorpayPaymentController(RazorpayService razorpayService, BookingRepository bookingRepository) {
        this.razorpayService = razorpayService;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Exposes public Razorpay Key ID for frontend checkout.
     * Note: KEY_SECRET is NEVER returned.
     */
    @GetMapping({"/razorpay/key", "/razorpay/config"})
    public ResponseEntity<Map<String, String>> getRazorpayKey() {
        return ResponseEntity.ok(Map.of("key_id", razorpayService.getKeyId()));
    }

    /**
     * STEP 1: BACKEND - Create Order
     * Endpoint: POST /api/create-order
     * Request: { amount (paise), currency, receipt }
     * Return: { order_id, amount, currency }
     * Minimum amount: 100 paise
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) RazorpayOrderRequest request) {
        if (request == null || request.getAmount() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", "Amount is required"
            ));
        }

        // Validate amount >= 100 paise
        if (request.getAmount() < 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", "Amount must be at least 100 paise (₹1.00)"
            ));
        }

        try {
            RazorpayOrderResponse response = razorpayService.createOrder(
                    request.getAmount(),
                    request.getCurrency(),
                    request.getReceipt()
            );

            // If a booking ID was provided, associate the order ID with the booking if found
            if (request.getBookingId() != null) {
                bookingRepository.findById(request.getBookingId()).ifPresent(booking -> {
                    booking.setRazorpayOrderId(response.getOrderId());
                    booking.setPaymentStatus("PROCESSING");
                    bookingRepository.save(booking);
                });
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (RazorpayException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Razorpay error";
            log.error("Razorpay exception during order creation: {}", errorMsg, e);
            if (errorMsg.contains("401") || errorMsg.toLowerCase().contains("unauthorized") || errorMsg.toLowerCase().contains("authentication failed")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "error", "Unauthorized",
                        "message", "Razorpay authentication failed: check credentials"
                ));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Internal Server Error",
                    "message", "Razorpay API error: " + errorMsg
            ));
        } catch (Exception e) {
            log.error("Unexpected error creating Razorpay order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Internal Server Error",
                    "message", "Unexpected server error: " + e.getMessage()
            ));
        }
    }

    /**
     * STEP 3: BACKEND - Verify Signature
     * Endpoint: POST /api/verify-payment
     * Algorithm: HMAC-SHA256(order_id + "|" + payment_id, KEY_SECRET)
     * Compare generated signature with razorpay_signature
     * Return success only if signatures match
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody(required = false) RazorpayVerifyRequest request) {
        // Missing fields validation
        if (request == null ||
            request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank() ||
            request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank() ||
            request.getRazorpaySignature() == null || request.getRazorpaySignature().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", "Missing required fields: razorpay_order_id, razorpay_payment_id, and razorpay_signature are all required"
            ));
        }

        boolean isValid = razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!isValid) {
            // Signature mismatch: return 400, do NOT mark as paid
            log.warn("Payment signature verification failed for orderId={}, paymentId={}",
                    request.getRazorpayOrderId(), request.getRazorpayPaymentId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Invalid Signature",
                    "message", "Signature verification failed: payment could not be authenticated"
            ));
        }

        log.info("Payment verified successfully for orderId={}, paymentId={}",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId());

        // Update booking status if bookingId was provided or found by razorpayOrderId
        Booking targetBooking = null;
        if (request.getBookingId() != null) {
            targetBooking = bookingRepository.findById(request.getBookingId()).orElse(null);
        }
        if (targetBooking == null && request.getRazorpayOrderId() != null) {
            targetBooking = bookingRepository.findAll().stream()
                    .filter(b -> request.getRazorpayOrderId().equals(b.getRazorpayOrderId()))
                    .findFirst()
                    .orElse(null);
        }

        if (targetBooking != null) {
            targetBooking.setPaymentStatus("PAID");
            targetBooking.setRazorpayOrderId(request.getRazorpayOrderId());
            targetBooking.setRazorpayPaymentId(request.getRazorpayPaymentId());
            if ("requested".equalsIgnoreCase(targetBooking.getStatus()) || "pending".equalsIgnoreCase(targetBooking.getStatus())) {
                targetBooking.setStatus("confirmed");
            }
            bookingRepository.save(targetBooking);
            log.info("Updated booking id={} to PAID and confirmed", targetBooking.getId());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Payment verified successfully");
        response.put("order_id", request.getRazorpayOrderId());
        response.put("payment_id", request.getRazorpayPaymentId());
        if (targetBooking != null) {
            response.put("booking_id", targetBooking.getId());
            response.put("booking_status", targetBooking.getStatus());
            response.put("payment_status", targetBooking.getPaymentStatus());
        }

        return ResponseEntity.ok(response);
    }
}
