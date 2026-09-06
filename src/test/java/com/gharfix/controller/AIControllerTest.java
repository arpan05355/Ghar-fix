package com.gharfix.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharfix.dto.AIDiagnoseRequest;
import com.gharfix.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiService geminiService;

    @Test
    void testDiagnose_Success() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");
        when(geminiService.generateContent(eq("My kitchen sink pipe is leaking under the basin"), anyString()))
                .thenReturn("Probable Cause: Damaged trap seal or loose slip joint nut.\nRecommended Service: Plumber.");

        AIDiagnoseRequest request = new AIDiagnoseRequest("My kitchen sink pipe is leaking under the basin");

        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.problem", is("My kitchen sink pipe is leaking under the basin")))
                .andExpect(jsonPath("$.diagnosis", containsString("Probable Cause")))
                .andExpect(jsonPath("$.response", containsString("Probable Cause")))
                .andExpect(jsonPath("$.model", is("gemini-3.6-flash")));
    }

    @Test
    void testDiagnose_WithPromptField_Success() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");
        when(geminiService.generateContent(eq("AC is blowing warm air"), anyString()))
                .thenReturn("Probable Cause: Refrigerant leak or dirty condenser coils.\nRecommended Service: AC Service.");

        AIDiagnoseRequest request = new AIDiagnoseRequest();
        request.setPrompt("AC is blowing warm air");

        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.problem", is("AC is blowing warm air")))
                .andExpect(jsonPath("$.diagnosis", containsString("Refrigerant leak")));
    }

    @Test
    void testDiagnose_EmptyProblem_ReturnsBadRequest() throws Exception {
        AIDiagnoseRequest request = new AIDiagnoseRequest("");

        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("Problem description is required")));
    }

    @Test
    void testDiagnose_NullBody_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("Problem description is required")));
    }

    @Test
    void testDiagnose_ServiceNotConfigured_ReturnsServiceUnavailable() throws Exception {
        when(geminiService.isConfigured()).thenReturn(false);

        AIDiagnoseRequest request = new AIDiagnoseRequest("Ceiling fan making strange humming sound");

        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("GEMINI_API_KEY")));
    }

    @Test
    void testDiagnose_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");
        when(geminiService.generateContent(anyString(), anyString()))
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        AIDiagnoseRequest request = new AIDiagnoseRequest("Short circuit in switchboard");

        mockMvc.perform(post("/api/ai/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("API rate limit exceeded")));
    }

    @Test
    void testGetStatus_ReturnsModelAndConfiguredState() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");

        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.model", is("gemini-3.6-flash")));
    }

    @Test
    void testChat_Success() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");
        when(geminiService.generateContent(anyString(), anyString()))
                .thenReturn("Hello! I can assist with that.");

        com.gharfix.dto.AIChatRequest chatReq = new com.gharfix.dto.AIChatRequest("How do I book a plumber?");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.intent", is("BOOKING_ASSISTANCE")))
                .andExpect(jsonPath("$.serviceCategory", is("Plumber")))
                .andExpect(jsonPath("$.reply", containsString("Hello! I can assist")));
    }

    @Test
    void testChat_BlankMessage_ReturnsBadRequest() throws Exception {
        com.gharfix.dto.AIChatRequest chatReq = new com.gharfix.dto.AIChatRequest("");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("Message cannot be blank")));
    }

    @Test
    void testEstimateCost_Success() throws Exception {
        java.util.Map<String, String> payload = java.util.Map.of("service", "Plumber");

        mockMvc.perform(post("/api/ai/estimate-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.estimate.serviceCategory", is("Plumber")))
                .andExpect(jsonPath("$.estimate.basePricePerHour", notNullValue()))
                .andExpect(jsonPath("$.estimate.disclaimer", containsString("Indicative estimate")));
    }

    @Test
    void testEstimateCost_UnknownService_ReturnsNotFound() throws Exception {
        java.util.Map<String, String> payload = java.util.Map.of("service", "RocketRepair");

        mockMvc.perform(post("/api/ai/estimate-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("No catalog service found")));
    }

    @Test
    void testEstimateCost_UnknownService_WithAllowPreliminary_ReturnsPreliminaryEstimate() throws Exception {
        java.util.Map<String, String> payload = java.util.Map.of(
                "service", "RocketRepair",
                "allowPreliminary", "true"
        );

        mockMvc.perform(post("/api/ai/estimate-cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.estimate.databaseGrounded", is(false)))
                .andExpect(jsonPath("$.estimate.estimateType", is("PRELIMINARY_AI_ESTIMATE")))
                .andExpect(jsonPath("$.estimate.disclaimer", containsString("Preliminary AI estimate")));
    }

    @Test
    void testAnalyzeImage_Success() throws Exception {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");

        String mockAiResponse =
                "VISUAL SUMMARY: Visible water leak from pipe under sink.\n" +
                "RECOMMENDED SERVICE: Plumber\n" +
                "CONFIDENCE: HIGH\n" +
                "SEVERITY: MEDIUM\n" +
                "POSSIBLE CAUSES:\n* Worn gasket\n" +
                "PRECAUTIONS:\n* Turn off valve\n";

        when(geminiService.generateContentWithImage(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(mockAiResponse);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pipe.jpg",
                "image/jpeg",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/ai/analyze-image")
                        .file(file)
                        .param("description", "Sink leaking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.recommendedService", is("Plumber")))
                .andExpect(jsonPath("$.confidence", is("HIGH")))
                .andExpect(jsonPath("$.costEstimate.serviceCategory", is("Plumber")))
                .andExpect(jsonPath("$.costEstimate.databaseGrounded", is(true)));
    }

    @Test
    void testAnalyzeImage_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/ai/analyze-image")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("Image file is required")));
    }

    @Test
    void testConfirmAction_Unauthenticated_ReturnsUnauthorized() throws Exception {
        java.util.Map<String, String> payload = java.util.Map.of("confirmationToken", "tok_test_123");

        mockMvc.perform(post("/api/ai/actions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void testDismissAction_Unauthenticated_ReturnsUnauthorized() throws Exception {
        java.util.Map<String, String> payload = java.util.Map.of("confirmationToken", "tok_test_123");

        mockMvc.perform(post("/api/ai/actions/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void testGetActiveBookings_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ai/bookings/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void testGetUserComplaints_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ai/complaints"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
