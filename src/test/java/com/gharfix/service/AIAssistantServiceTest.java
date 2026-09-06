package com.gharfix.service;

import com.gharfix.dto.AIChatRequest;
import com.gharfix.dto.AIChatResponse;
import com.gharfix.entity.ServiceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIAssistantServiceTest {

    @Mock
    private GeminiService geminiService;

    @Mock
    private ServiceCategoryService serviceCategoryService;

    private AICostEstimationService costEstimationService;
    private AIAssistantService assistantService;

    @BeforeEach
    void setUp() {
        costEstimationService = new AICostEstimationService(serviceCategoryService);
        assistantService = new AIAssistantService(geminiService, costEstimationService);
    }

    @Test
    void testDetectIntent_RefundRequest() {
        String intent = assistantService.detectIntent("I want a refund for my cancelled plumber booking");
        assertEquals(AIAssistantService.INTENT_REFUND_REQUEST, intent);
    }

    @Test
    void testDetectIntent_CancellationAssistance() {
        String intent = assistantService.detectIntent("Can I cancel my electrician appointment for tomorrow?");
        assertEquals(AIAssistantService.INTENT_CANCELLATION_ASSISTANCE, intent);
    }

    @Test
    void testDetectIntent_IssueReporting() {
        String intent = assistantService.detectIntent("The worker was extremely rude and left the work unfinished, I want to file a complaint");
        assertEquals(AIAssistantService.INTENT_ISSUE_REPORTING, intent);
    }

    @Test
    void testDetectIntent_CostEstimation() {
        String intent = assistantService.detectIntent("How much will it cost to repair my AC?");
        assertEquals(AIAssistantService.INTENT_COST_ESTIMATION, intent);
    }

    @Test
    void testDetectIntent_Diagnosis() {
        String intent = assistantService.detectIntent("My bathroom pipe is leaking and water is overflowing");
        assertEquals(AIAssistantService.INTENT_DIAGNOSIS, intent);
    }

    @Test
    void testDetectIntent_BookingAssistance() {
        String intent = assistantService.detectIntent("How can I book a carpenter for furniture assembly?");
        assertEquals(AIAssistantService.INTENT_BOOKING_ASSISTANCE, intent);
    }

    @Test
    void testDetectIntent_CustomerSupport() {
        String intent = assistantService.detectIntent("What are your customer support hours and payment methods?");
        assertEquals(AIAssistantService.INTENT_CUSTOMER_SUPPORT, intent);
    }

    @Test
    void testChat_CostEstimation_GroundedInDatabaseRates() {
        ServiceEntity acService = new ServiceEntity("AC Service", "fa-snowflake", "AC Repair", 90, 200);
        when(serviceCategoryService.findByNameIgnoreCase("AC Service")).thenReturn(Optional.of(acService));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");
        when(geminiService.generateContent(anyString(), anyString())).thenReturn("AC servicing starts at ₹200/hr.");

        AIChatRequest request = new AIChatRequest("How much does AC service cost?");
        AIChatResponse response = assistantService.chat(request);

        assertTrue(response.isSuccess());
        assertEquals(AIAssistantService.INTENT_COST_ESTIMATION, response.getIntent());
        assertNotNull(response.getCostEstimate());
        assertEquals("AC Service", response.getCostEstimate().getServiceCategory());
        assertEquals(200, response.getCostEstimate().getBasePricePerHour());
        assertNotNull(response.getCostEstimate().getDisclaimer());
        assertTrue(response.getCostEstimate().getDisclaimer().contains("Indicative estimate"));
    }

    @Test
    void testChat_Cancellation_CreatesSafeActionProposalWithoutExecuting() {
        when(geminiService.isConfigured()).thenReturn(false); // test fallback

        AIChatRequest request = new AIChatRequest("I want to cancel my booking");
        request.setBookingId(42L);

        AIChatResponse response = assistantService.chat(request);

        assertTrue(response.isSuccess());
        assertEquals(AIAssistantService.INTENT_CANCELLATION_ASSISTANCE, response.getIntent());
        assertNotNull(response.getProposedAction(), "Should propose cancellation action");
        assertEquals("CANCEL_BOOKING", response.getProposedAction().getActionType());
        assertTrue(response.getProposedAction().isRequiresUserConfirmation());
        assertEquals("/bookings", response.getProposedAction().getNextStepUrl());
        assertEquals(42L, response.getProposedAction().getParameters().get("bookingId"));
    }

    @Test
    void testChat_RefundRequest_CreatesSafeActionProposalWithoutExecuting() {
        when(geminiService.isConfigured()).thenReturn(false); // test fallback

        AIChatRequest request = new AIChatRequest("Please give me a refund for my order");
        request.setBookingId(99L);

        AIChatResponse response = assistantService.chat(request);

        assertTrue(response.isSuccess());
        assertEquals(AIAssistantService.INTENT_REFUND_REQUEST, response.getIntent());
        assertNotNull(response.getProposedAction(), "Should propose refund action");
        assertEquals("REQUEST_REFUND", response.getProposedAction().getActionType());
        assertTrue(response.getProposedAction().isRequiresUserConfirmation());
        assertEquals("/bookings", response.getProposedAction().getNextStepUrl());
    }

    @Test
    void testChat_BlankMessage_ReturnsError() {
        AIChatResponse response = assistantService.chat(new AIChatRequest("   "));
        assertFalse(response.isSuccess());
        assertEquals("Message text cannot be empty.", response.getError());
    }
}
