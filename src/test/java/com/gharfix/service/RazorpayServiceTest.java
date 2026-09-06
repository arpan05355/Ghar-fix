package com.gharfix.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayServiceTest {

    private RazorpayService razorpayService;
    private final String keyId = "rzp_test_TYlYzkePVAzKNN";
    private final String keySecret = "UJVqlxF6r1Xw5dMNDuCP2DRT";

    @BeforeEach
    void setUp() {
        razorpayService = new RazorpayService(keyId, keySecret);
    }

    private String generateExpectedSignature(String orderId, String paymentId, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void testVerifyPaymentSignature_ValidSignature_ReturnsTrue() throws Exception {
        String orderId = "order_9A33XWu170gUtm";
        String paymentId = "pay_29QQoUBi66xm2f";
        String validSig = generateExpectedSignature(orderId, paymentId, keySecret);

        boolean result = razorpayService.verifyPaymentSignature(orderId, paymentId, validSig);
        assertTrue(result, "Valid signature must return true");
    }

    @Test
    void testVerifyPaymentSignature_TamperedSignature_ReturnsFalse() throws Exception {
        String orderId = "order_9A33XWu170gUtm";
        String paymentId = "pay_29QQoUBi66xm2f";
        String validSig = generateExpectedSignature(orderId, paymentId, keySecret);
        String tamperedSig = validSig.substring(0, validSig.length() - 1) + (validSig.endsWith("a") ? "b" : "a");

        boolean result = razorpayService.verifyPaymentSignature(orderId, paymentId, tamperedSig);
        assertFalse(result, "Tampered signature must return false");
    }

    @Test
    void testVerifyPaymentSignature_NullOrEmptyInputs_ReturnsFalse() {
        assertFalse(razorpayService.verifyPaymentSignature(null, "pay_123", "sig_123"));
        assertFalse(razorpayService.verifyPaymentSignature("order_123", null, "sig_123"));
        assertFalse(razorpayService.verifyPaymentSignature("order_123", "pay_123", null));
        assertFalse(razorpayService.verifyPaymentSignature("", "pay_123", "sig_123"));
    }

    @Test
    void testGetKeyId() {
        assertEquals(keyId, razorpayService.getKeyId());
    }

    @Test
    void testRefundPayment_NullOrEmptyPaymentId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                razorpayService.refundPayment(null, 1000L, "reason"));
        assertThrows(IllegalArgumentException.class, () ->
                razorpayService.refundPayment("   ", 1000L, "reason"));
    }
}
