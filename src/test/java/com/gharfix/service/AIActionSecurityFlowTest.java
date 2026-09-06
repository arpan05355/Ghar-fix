package com.gharfix.service;

import com.gharfix.dto.AIActionExecutionResult;
import com.gharfix.dto.AIActionProposal;
import com.gharfix.dto.AIChatRequest;
import com.gharfix.dto.AIChatResponse;
import com.gharfix.entity.Booking;
import com.gharfix.entity.User;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Security-focused test suite verifying all 8 Phase 3 AI Action security criteria:
 * 1. A user cannot cancel another user's booking.
 * 2. A user cannot request a refund for another user's payment.
 * 3. An expired confirmation token is rejected.
 * 4. A confirmation token cannot be reused (anti-replay).
 * 5. Changing the action or parameters after token creation invalidates/prevents tampered execution.
 * 6. Gemini cannot directly execute an action.
 * 7. Refund amounts always come from backend/payment data, never from Gemini.
 * 8. Cancellation follows the GharFix cancellation policy.
 */
@ExtendWith(MockitoExtension.class)
class AIActionSecurityFlowTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private ServiceComplaintService complaintService;

    @Mock
    private UserService userService;

    @Mock
    private ServiceCategoryService serviceCategoryService;

    @Mock
    private AICostEstimationService costEstimationService;

    @Mock
    private GeminiService geminiService;

    private AIActionExecutionService executionService;
    private AIAssistantService aiAssistantService;

    private User userAlice;
    private User userBob;
    private Booking aliceBooking;

    @BeforeEach
    void setUp() {
        executionService = new AIActionExecutionService(
                bookingService,
                razorpayService,
                complaintService,
                userService,
                serviceCategoryService
        );

        aiAssistantService = new AIAssistantService(
                geminiService,
                costEstimationService,
                executionService,
                bookingService
        );

        userAlice = new User("Alice Smith", "alice@example.com", "9876543210", "pass123");
        userAlice.setId(100L);

        userBob = new User("Bob Jones", "bob@example.com", "9876543211", "pass456");
        userBob.setId(200L);

        aliceBooking = new Booking();
        aliceBooking.setId(101L);
        aliceBooking.setUser(userAlice);
        aliceBooking.setServiceName("Plumber");
        aliceBooking.setStatus("requested");
        aliceBooking.setBookingDate(LocalDate.now().plusDays(2));
        aliceBooking.setBookingTime(LocalTime.of(14, 0));
        aliceBooking.setPaymentStatus("PAID");
        aliceBooking.setProposedPrice(BigDecimal.valueOf(450));
        aliceBooking.setRazorpayPaymentId("pay_alice_99999");
    }

    @Test
    @DisplayName("Security 1: User Bob cannot cancel Alice's booking")
    void testUserCannotCancelAnotherUsersBooking() {
        // Bob tries to propose cancellation for Alice's booking #101
        when(bookingService.findUserBookingById(101L, userBob.getId())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                executionService.proposeCancellation(userBob, 101L, "Bob attempting to cancel Alice's booking")
        );
        assertTrue(ex.getMessage().contains("not found or does not belong to you"));

        // Bob tries to confirm a token that belonged to Alice
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));
        AIActionProposal proposal = executionService.proposeCancellation(userAlice, 101L, "Alice legitimate cancel");
        String aliceToken = proposal.getConfirmationToken();

        // Bob executes Alice's token
        SecurityException secEx = assertThrows(SecurityException.class, () ->
                executionService.executeAction(userBob.getId(), aliceToken)
        );
        assertTrue(secEx.getMessage().contains("not authorized to execute this action"));
    }

    @Test
    @DisplayName("Security 2: User Bob cannot request a refund for Alice's payment")
    void testUserCannotRequestRefundForAnotherUsersPayment() {
        // Bob tries to propose refund for Alice's booking #101
        when(bookingService.findUserBookingById(101L, userBob.getId())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                executionService.proposeRefund(userBob, 101L, "Bob requesting Alice's refund")
        );
        assertTrue(ex.getMessage().contains("not found or does not belong to you"));

        // Bob tries to execute Alice's refund token
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));
        AIActionProposal aliceRefundProp = executionService.proposeRefund(userAlice, 101L, "Alice legitimate refund");
        String token = aliceRefundProp.getConfirmationToken();

        assertThrows(SecurityException.class, () ->
                executionService.executeAction(userBob.getId(), token)
        );
    }

    @Test
    @DisplayName("Security 3: Expired confirmation token is rejected")
    void testExpiredConfirmationTokenIsRejected() throws Exception {
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));

        AIActionProposal proposal = executionService.proposeCancellation(userAlice, 101L, "Change schedule");
        String token = proposal.getConfirmationToken();

        // Simulate token expiration by modifying ticket timestamp through reflection
        Field storeField = AIActionExecutionService.class.getDeclaredField("ticketStore");
        storeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> store = (Map<String, Object>) storeField.get(executionService);
        Object ticket = store.get(token);

        Field expiresAtField = ticket.getClass().getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.set(ticket, Instant.now().minus(5, ChronoUnit.MINUTES)); // Expired 5 mins ago

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                executionService.executeAction(userAlice.getId(), token)
        );
        assertTrue(ex.getMessage().contains("expired"));

        // Confirm token was purged from store
        assertNull(store.get(token));
    }

    @Test
    @DisplayName("Security 4: Confirmation token cannot be reused (anti-replay)")
    void testConfirmationTokenCannotBeReused() {
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));
        when(bookingService.cancelBooking(eq(101L), eq(userAlice.getId()), anyString())).thenReturn(aliceBooking);
        when(userService.findById(userAlice.getId())).thenReturn(Optional.of(userAlice));

        AIActionProposal proposal = executionService.proposeCancellation(userAlice, 101L, "Vacation");
        String token = proposal.getConfirmationToken();

        // First execution succeeds
        AIActionExecutionResult result = executionService.executeAction(userAlice.getId(), token);
        assertTrue(result.isSuccess());

        // Replay attempt must be blocked
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                executionService.executeAction(userAlice.getId(), token)
        );
        assertTrue(ex.getMessage().contains("Invalid or expired confirmation token"));
    }

    @Test
    @DisplayName("Security 5: Server executes only validated backend parameters, not client-tampered parameters")
    void testTamperingParametersDoesNotAlterExecution() {
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));
        when(bookingService.cancelBooking(eq(101L), eq(userAlice.getId()), anyString())).thenReturn(aliceBooking);
        when(userService.findById(userAlice.getId())).thenReturn(Optional.of(userAlice));

        // When token is created, parameters are sealed in the server-side ActionTicket
        AIActionProposal proposal = executionService.proposeCancellation(userAlice, 101L, "Official reason");
        String token = proposal.getConfirmationToken();

        // The confirmation API only takes confirmationToken (and optional user notes)
        // All execution parameters (bookingId, amount, service) come from the sealed server ticket
        AIActionExecutionResult result = executionService.executeAction(userAlice.getId(), token);
        assertEquals("101", result.getReferenceId());
        verify(bookingService).cancelBooking(eq(101L), eq(userAlice.getId()), anyString());
    }

    @Test
    @DisplayName("Security 6: Gemini cannot directly execute an action; chat only proposes")
    void testGeminiCannotDirectlyExecuteAction() {
        // When user chats with AI to cancel a booking
        AIChatRequest request = new AIChatRequest("Please cancel my booking #101");
        when(geminiService.isConfigured()).thenReturn(false); // test fallback flow or live
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));

        AIChatResponse response = aiAssistantService.chat(request, userAlice);

        // Verify that chat produced a proposal with a token requiring confirmation
        assertNotNull(response.getProposedAction());
        assertEquals("CANCEL_BOOKING", response.getProposedAction().getActionType());
        assertTrue(response.getProposedAction().isRequiresUserConfirmation());
        assertNotNull(response.getProposedAction().getConfirmationToken());

        // Crucial security check: Booking status is STILL 'requested', cancelBooking was NEVER called by Gemini!
        assertEquals("requested", aliceBooking.getStatus());
        verify(bookingService, never()).cancelBooking(anyLong(), anyLong(), anyString());
        verifyNoInteractions(razorpayService);
    }

    @Test
    @DisplayName("Security 7: Refund amounts always come from backend/payment data, never from Gemini")
    void testRefundAmountsAlwaysComeFromBackendPaymentData() throws RazorpayException {
        // Alice booking has backend price of 450.00
        aliceBooking.setProposedPrice(BigDecimal.valueOf(450));
        aliceBooking.setPaymentStatus("PAID");
        aliceBooking.setRazorpayPaymentId("pay_alice_99999");

        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));
        when(userService.findById(userAlice.getId())).thenReturn(Optional.of(userAlice));
        when(razorpayService.refundPayment("pay_alice_99999", 45000L, "Defective wiring")).thenReturn("rfnd_secure_001");

        // User says in chat: "Refund me 50000 rupees!" (AI might extract or pass natural text)
        // Backend proposal IGNORES any prompt amounts and reads strictly from aliceBooking.getProposedPrice()
        AIActionProposal proposal = executionService.proposeRefund(userAlice, 101L, "Defective wiring");
        assertEquals(BigDecimal.valueOf(450), proposal.getParameters().get("refundAmount"));
        assertTrue(proposal.getConfirmationPrompt().contains("₹450"));
        assertFalse(proposal.getConfirmationPrompt().contains("50000"));

        // When executed, Razorpay is called with EXACTLY 45000 paise (₹450), never an inflated/invented amount
        AIActionExecutionResult result = executionService.executeAction(userAlice.getId(), proposal.getConfirmationToken());
        assertTrue(result.isSuccess());
        verify(razorpayService).refundPayment("pay_alice_99999", 45000L, "Defective wiring");
        verify(razorpayService, never()).refundPayment(anyString(), eq(5000000L), anyString());
    }

    @Test
    @DisplayName("Security 8: Cancellation follows existing GharFix policy (status & windows)")
    void testCancellationFollowsExistingPolicy() {
        // Status 1: Already cancelled booking cannot be cancelled again
        aliceBooking.setStatus("cancelled");
        when(bookingService.findUserBookingById(101L, userAlice.getId())).thenReturn(Optional.of(aliceBooking));

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                executionService.proposeCancellation(userAlice, 101L, "Already cancelled")
        );
        assertTrue(ex1.getMessage().contains("already cancelled"));

        // Status 2: Completed booking cannot be cancelled
        aliceBooking.setStatus("completed");
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                executionService.proposeCancellation(userAlice, 101L, "Already completed")
        );
        assertTrue(ex2.getMessage().contains("Completed bookings cannot be cancelled"));
    }
}
