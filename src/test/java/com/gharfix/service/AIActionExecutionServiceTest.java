package com.gharfix.service;

import com.gharfix.dto.AIActionExecutionResult;
import com.gharfix.dto.AIActionProposal;
import com.gharfix.dto.BookingRequestDto;
import com.gharfix.entity.Booking;
import com.gharfix.entity.ServiceComplaint;
import com.gharfix.entity.User;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIActionExecutionServiceTest {

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

    private AIActionExecutionService executionService;

    private User testUser;
    private Booking testBooking;

    @BeforeEach
    void setUp() {
        executionService = new AIActionExecutionService(
                bookingService,
                razorpayService,
                complaintService,
                userService,
                serviceCategoryService
        );

        testUser = new User("Rahul Sharma", "rahul@example.com", "9876543210", "password123");
        testUser.setId(10L);

        testBooking = new Booking();
        testBooking.setId(101L);
        testBooking.setUser(testUser);
        testBooking.setServiceName("Plumber");
        testBooking.setStatus("requested");
        testBooking.setBookingDate(LocalDate.now().plusDays(2));
        testBooking.setBookingTime(LocalTime.of(14, 0));
        testBooking.setPaymentStatus("UNPAID");
    }

    @Test
    void testProposeCancellation_Unpaid_Success() {
        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));

        AIActionProposal proposal = executionService.proposeCancellation(testUser, 101L, "Change of plans");

        assertNotNull(proposal);
        assertEquals("CANCEL_BOOKING", proposal.getActionType());
        assertTrue(proposal.isRequiresUserConfirmation());
        assertNotNull(proposal.getConfirmationToken());
        assertTrue(proposal.getConfirmationToken().startsWith("tok_"));
        assertEquals(101L, proposal.getParameters().get("bookingId"));
        assertEquals(false, proposal.getParameters().get("isPaid"));
        assertTrue(proposal.getConfirmationPrompt().contains("Free cancellation"));
    }

    @Test
    void testProposeCancellation_Paid_IncludesRefundAmount() {
        testBooking.setPaymentStatus("PAID");
        testBooking.setProposedPrice(BigDecimal.valueOf(250));
        testBooking.setRazorpayPaymentId("pay_test_12345");

        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));

        AIActionProposal proposal = executionService.proposeCancellation(testUser, 101L, "Leaving city");

        assertNotNull(proposal);
        assertEquals("CANCEL_BOOKING", proposal.getActionType());
        assertEquals(true, proposal.getParameters().get("isPaid"));
        assertEquals(BigDecimal.valueOf(250), proposal.getParameters().get("refundableAmount"));
        assertTrue(proposal.getConfirmationPrompt().contains("₹250"));
    }

    @Test
    void testExecuteCancellation_Success_InvalidatesTokenSingleUse() {
        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));
        when(bookingService.cancelBooking(eq(101L), eq(10L), anyString())).thenReturn(testBooking);
        when(userService.findById(10L)).thenReturn(Optional.of(testUser));

        AIActionProposal proposal = executionService.proposeCancellation(testUser, 101L, "Schedule clash");
        String token = proposal.getConfirmationToken();

        // 1st Execution succeeds
        AIActionExecutionResult result = executionService.executeAction(10L, token);
        assertTrue(result.isSuccess());
        assertEquals("CANCEL_BOOKING", result.getActionType());
        assertEquals("101", result.getReferenceId());

        // 2nd Execution with same token must fail (single-use replay prevention)
        assertThrows(IllegalArgumentException.class, () -> executionService.executeAction(10L, token));
    }

    @Test
    void testExecuteCancellation_UnauthorizedUser_ThrowsSecurityException() {
        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));

        AIActionProposal proposal = executionService.proposeCancellation(testUser, 101L, "Schedule clash");
        String token = proposal.getConfirmationToken();

        // Another user (ID 99) tries to execute Rahul's token
        assertThrows(SecurityException.class, () -> executionService.executeAction(99L, token));
    }

    @Test
    void testProposeRefund_UnpaidBooking_ThrowsIllegalStateException() {
        testBooking.setPaymentStatus("UNPAID");
        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));

        assertThrows(IllegalStateException.class, () -> executionService.proposeRefund(testUser, 101L, "Didn't turn up"));
    }

    @Test
    void testProposeRefund_PaidBooking_AndExecuteSuccess() throws RazorpayException {
        testBooking.setPaymentStatus("PAID");
        testBooking.setRazorpayPaymentId("pay_valid_999");
        testBooking.setProposedPrice(BigDecimal.valueOf(350));

        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));
        when(userService.findById(10L)).thenReturn(Optional.of(testUser));
        when(razorpayService.refundPayment("pay_valid_999", 35000L, "No show")).thenReturn("rfnd_test_abc123");

        AIActionProposal proposal = executionService.proposeRefund(testUser, 101L, "No show");
        assertNotNull(proposal.getConfirmationToken());

        AIActionExecutionResult result = executionService.executeAction(10L, proposal.getConfirmationToken());
        assertTrue(result.isSuccess());
        assertEquals("REQUEST_REFUND", result.getActionType());
        assertEquals("rfnd_test_abc123", result.getReferenceId());
        verify(razorpayService).refundPayment("pay_valid_999", 35000L, "No show");
    }

    @Test
    void testProposeBooking_AndExecuteSuccess() {
        BookingRequestDto dto = new BookingRequestDto();
        dto.setService("Electrician");
        dto.setDate(LocalDate.now().plusDays(3).toString());
        dto.setTime("11:30");
        dto.setAddress("404 Blue Avenue, Vadodara");
        dto.setCity("Vadodara");

        Booking created = new Booking();
        created.setId(202L);
        created.setServiceName("Electrician");
        created.setStatus("requested");
        created.setBookingDate(LocalDate.now().plusDays(3));
        created.setBookingTime(LocalTime.of(11, 30));

        when(userService.findById(10L)).thenReturn(Optional.of(testUser));
        when(bookingService.createBooking(eq(testUser), any(BookingRequestDto.class))).thenReturn(created);

        AIActionProposal proposal = executionService.proposeBooking(testUser, dto);
        assertNotNull(proposal.getConfirmationToken());

        AIActionExecutionResult result = executionService.executeAction(10L, proposal.getConfirmationToken());
        assertTrue(result.isSuccess());
        assertEquals("BOOK_SERVICE", result.getActionType());
        assertEquals("202", result.getReferenceId());
    }

    @Test
    void testProposeComplaint_AndExecuteSuccess() {
        ServiceComplaint complaint = new ServiceComplaint(testUser, testBooking, "Plumber", "Damage to tile", "HIGH", "GF-CMP-TEST1234");

        when(userService.findById(10L)).thenReturn(Optional.of(testUser));
        when(complaintService.registerComplaint(eq(testUser), any(), eq("Plumber"), anyString(), eq("HIGH")))
                .thenReturn(complaint);

        AIActionProposal proposal = executionService.proposeComplaint(testUser, 101L, "Plumber", "Damage to tile", "HIGH");
        assertNotNull(proposal.getConfirmationToken());

        AIActionExecutionResult result = executionService.executeAction(10L, proposal.getConfirmationToken());
        assertTrue(result.isSuccess());
        assertEquals("REPORT_ISSUE", result.getActionType());
        assertEquals("GF-CMP-TEST1234", result.getReferenceId());
    }

    @Test
    void testDismissAction_Success() {
        when(bookingService.findUserBookingById(101L, 10L)).thenReturn(Optional.of(testBooking));

        AIActionProposal proposal = executionService.proposeCancellation(testUser, 101L, "Schedule clash");
        String token = proposal.getConfirmationToken();

        boolean dismissed = executionService.dismissAction(10L, token);
        assertTrue(dismissed);

        // After dismissal, attempting to execute must fail
        assertThrows(IllegalArgumentException.class, () -> executionService.executeAction(10L, token));
    }
}
