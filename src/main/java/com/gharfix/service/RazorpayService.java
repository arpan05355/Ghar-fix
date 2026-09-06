package com.gharfix.service;

import com.gharfix.dto.RazorpayOrderResponse;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    @Value("${razorpay.key-id:${RAZORPAY_KEY_ID:rzp_test_TYlYzkePVAzKNN}}")
    private String keyId;

    @Value("${razorpay.key-secret:${RAZORPAY_KEY_SECRET:UJVqlxF6r1Xw5dMNDuCP2DRT}}")
    private String keySecret;

    private RazorpayClient client;

    public RazorpayService() {
    }

    // Constructor for testing
    public RazorpayService(String keyId, String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public synchronized RazorpayClient getClient() throws RazorpayException {
        if (client == null) {
            if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
                throw new RazorpayException("Razorpay Key ID or Key Secret is not configured.");
            }
            client = new RazorpayClient(keyId.trim(), keySecret.trim());
        }
        return client;
    }

    /**
     * Create an order on Razorpay
     *
     * @param amountInPaise Amount in paise (1 INR = 100 paise)
     * @param currency Currency code (default INR)
     * @param receipt Receipt identifier
     * @return RazorpayOrderResponse containing order_id, amount, and currency
     */
    public RazorpayOrderResponse createOrder(Long amountInPaise, String currency, String receipt) throws RazorpayException {
        if (amountInPaise == null || amountInPaise < 100) {
            throw new IllegalArgumentException("Amount must be at least 100 paise (₹1.00)");
        }

        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }

        if (receipt == null || receipt.isBlank()) {
            receipt = "rcpt_" + System.currentTimeMillis();
        }

        try {
            RazorpayClient razorpay = getClient();
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);

            log.info("Creating Razorpay order for amount: {} {}, receipt: {}", amountInPaise, currency, receipt);
            Order order = razorpay.orders.create(orderRequest);

            String orderId = order.get("id");
            Long orderAmount = ((Number) order.get("amount")).longValue();
            String orderCurrency = order.get("currency");

            log.info("Razorpay order created successfully: {}", orderId);
            return new RazorpayOrderResponse(orderId, orderAmount, orderCurrency);
        } catch (RazorpayException e) {
            log.error("Razorpay API error creating order: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Verifies payment signature using HMAC-SHA256(order_id + "|" + payment_id, KEY_SECRET)
     *
     * @param orderId Razorpay order id
     * @param paymentId Razorpay payment id
     * @param signature Razorpay signature returned by checkout
     * @return true if signature matches, false otherwise
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank() ||
            paymentId == null || paymentId.isBlank() ||
            signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payload = orderId.trim() + "|" + paymentId.trim();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(keySecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String generatedSignature = hexString.toString();

            // Constant-time byte comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying payment signature: {}", e.getMessage(), e);
            return false;
        }
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * Issues a refund for a successful payment via Razorpay.
     *
     * @param paymentId Razorpay payment ID to refund
     * @param amountInPaise Amount to refund in paise (null or <= 0 for full refund)
     * @param reason Reason for refund
     * @return Refund ID generated by Razorpay
     * @throws RazorpayException if Razorpay API fails
     */
    public String refundPayment(String paymentId, Long amountInPaise, String reason) throws RazorpayException {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment ID is required to process a refund.");
        }

        try {
            RazorpayClient razorpay = getClient();
            JSONObject refundRequest = new JSONObject();
            if (amountInPaise != null && amountInPaise > 0) {
                refundRequest.put("amount", amountInPaise);
            }
            if (reason != null && !reason.isBlank()) {
                JSONObject notes = new JSONObject();
                notes.put("reason", reason.trim());
                refundRequest.put("notes", notes);
            }

            log.info("Initiating Razorpay refund for paymentId={}, amount={}, reason={}",
                    paymentId, amountInPaise, reason);

            com.razorpay.Refund refund = razorpay.payments.refund(paymentId.trim(), refundRequest);
            String refundId = refund.get("id");
            log.info("Razorpay refund processed successfully: id={}", refundId);
            return refundId;
        } catch (RazorpayException e) {
            log.error("Razorpay API error processing refund for paymentId={}: {}", paymentId, e.getMessage(), e);
            throw e;
        }
    }
}
