package com.gharfix.service;

import com.gharfix.dto.AIActionExecutionResult;
import com.gharfix.dto.AIActionProposal;
import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.ServiceComplaint;
import com.gharfix.entity.User;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates safe, human-in-the-loop action proposals and executions for the GharFix AI Assistant.
 *
 * Core Architecture & Security Rules:
 * 1. Gemini NEVER directly calls payment APIs or mutates database state.
 * 2. Gemini extracts intent and parameters; this backend service validates ownership, policy, and eligibility.
 * 3. Actions generate a short-lived (10 min), single-use cryptographic confirmation token.
 * 4. The action is executed ONLY when the user explicitly submits the confirmation token.
 * 5. Replay prevention: Tokens are atomically consumed and invalidated upon first use.
 */
@Service
public class AIActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AIActionExecutionService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final BookingService bookingService;
    private final RazorpayService razorpayService;
    private final ServiceComplaintService complaintService;
    private final UserService userService;
    private final ServiceCategoryService serviceCategoryService;

    // Fast in-memory token store for transient action confirmations
    private final ConcurrentHashMap<String, ActionTicket> ticketStore = new ConcurrentHashMap<>();

    public AIActionExecutionService(BookingService bookingService,
                                    RazorpayService razorpayService,
                                    ServiceComplaintService complaintService,
                                    UserService userService,
                                    ServiceCategoryService serviceCategoryService) {
        this.bookingService = bookingService;
        this.razorpayService = razorpayService;
        this.complaintService = complaintService;
        this.userService = userService;
        this.serviceCategoryService = serviceCategoryService;
    }

    // ==========================================
    // 1. PROPOSAL GENERATION
    // ==========================================

    /**
     * Proposes booking cancellation with refund eligibility calculation.
     */
    public AIActionProposal proposeCancellation(User user, Long bookingId, String reason) {
        if (user == null) {
            throw new IllegalArgumentException("User must be authenticated to cancel a booking.");
        }
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID is required for cancellation.");
        }

        Booking booking = bookingService.findUserBookingById(bookingId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found or does not belong to you."));

        if ("cancelled".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Booking #" + bookingId + " is already cancelled.");
        }
        if ("completed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Completed bookings cannot be cancelled. You may request a refund or report a service issue.");
        }

        boolean isPaid = "PAID".equalsIgnoreCase(booking.getPaymentStatus());
        BigDecimal refundableAmount = isPaid ? resolveBookingPrice(booking) : BigDecimal.ZERO;

        String description = String.format("Cancel %s booking (#%d) scheduled for %s at %s",
                booking.getServiceName(), booking.getId(), booking.getBookingDate(), booking.getBookingTime());

        String prompt = isPaid
                ? String.format("Are you sure you want to cancel Booking #%d (%s)? You have paid ₹%s which will be fully refunded to your original payment method.",
                    booking.getId(), booking.getServiceName(), refundableAmount)
                : String.format("Are you sure you want to cancel Booking #%d (%s)? Free cancellation applies.",
                    booking.getId(), booking.getServiceName());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bookingId", booking.getId());
        params.put("serviceName", booking.getServiceName());
        params.put("bookingDate", booking.getBookingDate().toString());
        params.put("bookingTime", booking.getBookingTime().toString());
        params.put("isPaid", isPaid);
        params.put("refundableAmount", refundableAmount);
        params.put("reason", reason != null ? reason : "Requested by customer via AI Assistant");

        return createTicket(user.getId(), "CANCEL_BOOKING", description, prompt, params, "/my-bookings");
    }

    /**
     * Proposes refund for a paid booking.
     */
    public AIActionProposal proposeRefund(User user, Long bookingId, String reason) {
        if (user == null) {
            throw new IllegalArgumentException("User must be authenticated to request a refund.");
        }
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID is required to request a refund.");
        }

        Booking booking = bookingService.findUserBookingById(bookingId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found or does not belong to you."));

        if (!"PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("Booking #" + bookingId + " has payment status '" + booking.getPaymentStatus()
                    + "'. Only paid bookings can be refunded.");
        }

        if (booking.getRazorpayPaymentId() == null || booking.getRazorpayPaymentId().isBlank()) {
            throw new IllegalStateException("No Razorpay transaction record found for Booking #" + bookingId + ".");
        }

        BigDecimal refundAmount = resolveBookingPrice(booking);

        String description = String.format("Refund ₹%s for Booking #%d (%s)",
                refundAmount, booking.getId(), booking.getServiceName());

        String prompt = String.format("Confirm refund request for Booking #%d (%s). Amount to be refunded: ₹%s to original payment method (takes 5-7 business days).",
                booking.getId(), booking.getServiceName(), refundAmount);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bookingId", booking.getId());
        params.put("serviceName", booking.getServiceName());
        params.put("paymentId", booking.getRazorpayPaymentId());
        params.put("refundAmount", refundAmount);
        params.put("reason", reason != null ? reason : "Customer refund request via AI Assistant");

        return createTicket(user.getId(), "REQUEST_REFUND", description, prompt, params, "/my-bookings");
    }

    /**
     * Proposes creation of a new service booking.
     */
    public AIActionProposal proposeBooking(User user, BookingRequestDto request) {
        if (user == null) {
            throw new IllegalArgumentException("User must be authenticated to create a booking.");
        }
        if (request.getService() == null || request.getService().isBlank()) {
            throw new IllegalArgumentException("Service category name is required.");
        }
        if (request.getDate() == null || request.getTime() == null) {
            throw new IllegalArgumentException("Booking date and time are required.");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw new IllegalArgumentException("Service address is required.");
        }

        String city = (request.getCity() != null && !request.getCity().isBlank()) ? request.getCity() : "Vadodara";
        request.setCity(city);

        String description = String.format("Book %s on %s at %s (%s, %s)",
                request.getService(), request.getDate(), request.getTime(), request.getAddress(), city);

        String prompt = String.format("Confirm booking for %s on %s at %s at %s? A verified technician will be assigned.",
                request.getService(), request.getDate(), request.getTime(), request.getAddress());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("service", request.getService());
        params.put("date", request.getDate());
        params.put("time", request.getTime());
        params.put("address", request.getAddress());
        params.put("city", city);
        if (request.getWorkerId() != null) {
            params.put("workerId", request.getWorkerId());
        }

        return createTicket(user.getId(), "BOOK_SERVICE", description, prompt, params, "/my-bookings");
    }

    /**
     * Proposes filing a formal service complaint.
     */
    public AIActionProposal proposeComplaint(User user, Long bookingId, String serviceCategory,
                                             String description, String severity) {
        if (user == null) {
            throw new IllegalArgumentException("User must be authenticated to file a service complaint.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Complaint description cannot be empty.");
        }

        Booking booking = null;
        if (bookingId != null) {
            booking = bookingService.findUserBookingById(bookingId, user.getId()).orElse(null);
        }

        String category = (serviceCategory != null && !serviceCategory.isBlank())
                ? serviceCategory
                : (booking != null ? booking.getServiceName() : "General Home Service");

        String prompt = String.format("File formal support complaint for %s regarding: \"%s\"? Priority: %s.",
                category, (description.length() > 50 ? description.substring(0, 47) + "..." : description),
                severity != null ? severity : "MEDIUM");

        String desc = "Formal issue report for " + category + (booking != null ? " (Booking #" + booking.getId() + ")" : "");

        Map<String, Object> params = new LinkedHashMap<>();
        if (booking != null) {
            params.put("bookingId", booking.getId());
        }
        params.put("serviceCategory", category);
        params.put("description", description);
        params.put("severity", severity != null ? severity : "MEDIUM");

        return createTicket(user.getId(), "REPORT_ISSUE", desc, prompt, params, "/support");
    }

    // ==========================================
    // 2. ACTION EXECUTION (HUMAN-IN-THE-LOOP)
    // ==========================================

    /**
     * Executes the confirmed action atomically using the single-use confirmation token.
     */
    @Transactional
    public AIActionExecutionResult executeAction(Long userId, String confirmationToken) {
        if (confirmationToken == null || confirmationToken.isBlank()) {
            throw new IllegalArgumentException("Confirmation token is required to execute an action.");
        }

        ActionTicket ticket = ticketStore.get(confirmationToken.trim());
        if (ticket == null) {
            throw new IllegalArgumentException("Invalid or expired confirmation token. Please request a new action.");
        }

        // Authorization check: token must belong to the caller
        if (!ticket.getUserId().equals(userId)) {
            log.warn("Security violation: User {} attempted to execute token belonging to user {}",
                    userId, ticket.getUserId());
            throw new SecurityException("You are not authorized to execute this action.");
        }

        // Expiry check
        if (Instant.now().isAfter(ticket.getExpiresAt())) {
            ticketStore.remove(confirmationToken.trim());
            throw new IllegalStateException("Action confirmation token has expired. Please initiate the request again.");
        }

        // Atomic removal prevents double execution / replay attacks
        ticket = ticketStore.remove(confirmationToken.trim());
        if (ticket == null) {
            throw new IllegalStateException("Action has already been executed or cancelled.");
        }

        log.info("Executing confirmed action type='{}' for userId={}", ticket.getActionType(), userId);

        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for id: " + userId));

        switch (ticket.getActionType()) {
            case "CANCEL_BOOKING":
                return executeCancellation(user, ticket);

            case "REQUEST_REFUND":
                return executeRefund(user, ticket);

            case "BOOK_SERVICE":
                return executeBooking(user, ticket);

            case "REPORT_ISSUE":
                return executeComplaint(user, ticket);

            default:
                throw new UnsupportedOperationException("Unknown action type: " + ticket.getActionType());
        }
    }

    /**
     * Dismisses and cancels a pending proposal token.
     */
    public boolean dismissAction(Long userId, String confirmationToken) {
        if (confirmationToken == null || confirmationToken.isBlank()) {
            return false;
        }
        ActionTicket ticket = ticketStore.get(confirmationToken.trim());
        if (ticket != null && ticket.getUserId().equals(userId)) {
            ticketStore.remove(confirmationToken.trim());
            log.info("Dismissed action proposal token for userId={}", userId);
            return true;
        }
        return false;
    }

    // ==========================================
    // 3. PRIVATE EXECUTION HANDLERS
    // ==========================================

    private AIActionExecutionResult executeCancellation(User user, ActionTicket ticket) {
        Long bookingId = ((Number) ticket.getParameters().get("bookingId")).longValue();
        String reason = (String) ticket.getParameters().get("reason");
        boolean wasPaid = Boolean.TRUE.equals(ticket.getParameters().get("isPaid"));

        Booking cancelled = bookingService.cancelBooking(bookingId, user.getId(), reason);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", cancelled.getId());
        details.put("status", cancelled.getStatus());

        if (wasPaid && cancelled.getRazorpayPaymentId() != null) {
            try {
                BigDecimal refundAmount = resolveBookingPrice(cancelled);
                long paise = refundAmount.multiply(BigDecimal.valueOf(100)).longValue();
                String refundId = razorpayService.refundPayment(cancelled.getRazorpayPaymentId(), paise, "Auto-refund on cancellation");
                cancelled.setPaymentStatus("REFUNDED");
                bookingService.save(cancelled);

                details.put("refundProcessed", true);
                details.put("refundId", refundId);
                details.put("refundAmount", refundAmount);
            } catch (RazorpayException e) {
                log.error("Failed to process auto-refund on cancellation for bookingId={}: {}", bookingId, e.getMessage());
                details.put("refundProcessed", false);
                details.put("refundError", "Cancellation succeeded but automatic refund processing failed. Support notified.");
            }
        }

        return AIActionExecutionResult.success(
                "CANCEL_BOOKING",
                "Booking #" + bookingId + " has been successfully cancelled." + (wasPaid ? " Refund initiated." : ""),
                String.valueOf(bookingId),
                details
        );
    }

    private AIActionExecutionResult executeRefund(User user, ActionTicket ticket) {
        Long bookingId = ((Number) ticket.getParameters().get("bookingId")).longValue();
        String paymentId = (String) ticket.getParameters().get("paymentId");
        String reason = (String) ticket.getParameters().get("reason");
        BigDecimal refundAmount = (BigDecimal) ticket.getParameters().get("refundAmount");

        Booking booking = bookingService.findUserBookingById(bookingId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        try {
            long paise = refundAmount.multiply(BigDecimal.valueOf(100)).longValue();
            String refundId = razorpayService.refundPayment(paymentId, paise, reason);

            booking.setPaymentStatus("REFUNDED");
            if (!"cancelled".equalsIgnoreCase(booking.getStatus())) {
                booking.setStatus("cancelled");
            }
            bookingService.save(booking);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("bookingId", bookingId);
            details.put("refundId", refundId);
            details.put("refundAmount", refundAmount);
            details.put("paymentStatus", "REFUNDED");

            return AIActionExecutionResult.success(
                    "REQUEST_REFUND",
                    "Refund of ₹" + refundAmount + " has been successfully initiated via Razorpay (Refund ID: " + refundId + ").",
                    refundId,
                    details
            );
        } catch (RazorpayException e) {
            log.error("Razorpay refund error for bookingId={}: {}", bookingId, e.getMessage(), e);
            throw new RuntimeException("Failed to initiate Razorpay refund: " + e.getMessage(), e);
        }
    }

    private AIActionExecutionResult executeBooking(User user, ActionTicket ticket) {
        BookingRequestDto dto = new BookingRequestDto();
        dto.setService((String) ticket.getParameters().get("service"));
        dto.setDate((String) ticket.getParameters().get("date"));
        dto.setTime((String) ticket.getParameters().get("time"));
        dto.setAddress((String) ticket.getParameters().get("address"));
        dto.setCity((String) ticket.getParameters().get("city"));
        if (ticket.getParameters().containsKey("workerId")) {
            dto.setWorkerId(((Number) ticket.getParameters().get("workerId")).longValue());
        }

        Booking created = bookingService.createBooking(user, dto);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", created.getId());
        details.put("serviceName", created.getServiceName());
        details.put("status", created.getStatus());
        details.put("date", created.getBookingDate().toString());
        details.put("time", created.getBookingTime().toString());

        return AIActionExecutionResult.success(
                "BOOK_SERVICE",
                "Your " + created.getServiceName() + " booking (#" + created.getId() + ") has been successfully scheduled!",
                String.valueOf(created.getId()),
                details
        );
    }

    private AIActionExecutionResult executeComplaint(User user, ActionTicket ticket) {
        Long bookingId = ticket.getParameters().containsKey("bookingId")
                ? ((Number) ticket.getParameters().get("bookingId")).longValue()
                : null;
        String category = (String) ticket.getParameters().get("serviceCategory");
        String desc = (String) ticket.getParameters().get("description");
        String severity = (String) ticket.getParameters().get("severity");

        Booking booking = null;
        if (bookingId != null) {
            booking = bookingService.findUserBookingById(bookingId, user.getId()).orElse(null);
        }

        ServiceComplaint complaint = complaintService.registerComplaint(user, booking, category, desc, severity);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ticketNumber", complaint.getTicketNumber());
        details.put("category", complaint.getServiceCategory());
        details.put("status", complaint.getStatus());
        details.put("severity", complaint.getSeverity());

        return AIActionExecutionResult.success(
                "REPORT_ISSUE",
                "Service complaint ticket " + complaint.getTicketNumber() + " has been registered. Our support team will review this promptly.",
                complaint.getTicketNumber(),
                details
        );
    }

    // ==========================================
    // 4. HELPER METHODS
    // ==========================================

    private AIActionProposal createTicket(Long userId, String actionType, String description,
                                          String prompt, Map<String, Object> params, String nextStepUrl) {
        String token = "tok_" + UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        String expiresAtStr = DateTimeFormatter.ISO_INSTANT.format(expiresAt);

        ActionTicket ticket = new ActionTicket(token, userId, actionType, description, prompt, params, expiresAt);
        ticketStore.put(token, ticket);

        // Prune expired tickets periodically
        pruneExpiredTickets();

        return new AIActionProposal(actionType, description, prompt, params, nextStepUrl, token, expiresAtStr);
    }

    private void pruneExpiredTickets() {
        Instant now = Instant.now();
        ticketStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));
    }

    private BigDecimal resolveBookingPrice(Booking booking) {
        if (booking.getProposedPrice() != null && booking.getProposedPrice().compareTo(BigDecimal.ZERO) > 0) {
            return booking.getProposedPrice();
        }
        return serviceCategoryService.findByNameIgnoreCase(booking.getServiceName())
                .map(s -> BigDecimal.valueOf(s.getBasePricePerHour()))
                .orElse(BigDecimal.valueOf(180));
    }

    /**
     * Internal immutable container for a pending action proposal.
     */
    public static class ActionTicket {
        private final String token;
        private final Long userId;
        private final String actionType;
        private final String description;
        private final String prompt;
        private final Map<String, Object> parameters;
        private final Instant expiresAt;

        public ActionTicket(String token, Long userId, String actionType, String description,
                            String prompt, Map<String, Object> parameters, Instant expiresAt) {
            this.token = token;
            this.userId = userId;
            this.actionType = actionType;
            this.description = description;
            this.prompt = prompt;
            this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public Long getUserId() {
            return userId;
        }

        public String getActionType() {
            return actionType;
        }

        public String getDescription() {
            return description;
        }

        public String getPrompt() {
            return prompt;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
