package com.gharfix.service;

import com.gharfix.dto.AICostEstimateDto;
import com.gharfix.dto.AIImageAnalysisResponse;
import com.gharfix.entity.ServiceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIImageAnalysisServiceTest {

    @Mock
    private GeminiService geminiService;

    @Mock
    private ServiceCategoryService serviceCategoryService;

    private AICostEstimationService costEstimationService;
    private AIImageAnalysisService imageAnalysisService;

    @BeforeEach
    void setUp() {
        costEstimationService = new AICostEstimationService(serviceCategoryService);
        imageAnalysisService = new AIImageAnalysisService(geminiService, costEstimationService);
    }

    @Test
    void testAnalyzeImage_EmptyFile_ReturnsError() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(emptyFile, "leaking sink");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Image file is required"));
    }

    @Test
    void testAnalyzeImage_OversizedFile_ReturnsError() {
        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeBytes);
        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(largeFile, "test");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("5MB limit"));
    }

    @Test
    void testAnalyzeImage_UnsupportedMimeType_ReturnsError() {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(pdfFile, "test");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Unsupported file format"));
    }

    @Test
    void testAnalyzeImage_ValidImage_SuccessWithDatabaseGroundedCost() {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");

        String mockAiResponse =
                "VISUAL SUMMARY: Visible water accumulation and dripping under the PVC P-trap pipe.\n" +
                "RECOMMENDED SERVICE: Plumber\n" +
                "CONFIDENCE: HIGH (Leaking joint is clearly visible with active droplets)\n" +
                "SEVERITY: MEDIUM\n" +
                "POSSIBLE CAUSES:\n" +
                "* Perished rubber seal inside slip nut\n" +
                "* Loose threaded connector\n" +
                "PRECAUTIONS:\n" +
                "* Shut off under-sink angle valve\n" +
                "* Place a bucket directly below the trap\n" +
                "SAFETY NOTICE: None\n" +
                "NEXT STEPS: Book a verified GharFix Plumber.";

        when(geminiService.generateContentWithImage(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(mockAiResponse);

        ServiceEntity plumber = new ServiceEntity("Plumber", "fa-wrench", "Pipe fitting and water repairs", 60, 180);
        when(serviceCategoryService.findByNameIgnoreCase("Plumber")).thenReturn(Optional.of(plumber));

        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "leak.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(validFile, "Water dripping under the sink");

        assertTrue(response.isSuccess());
        assertEquals("Plumber", response.getRecommendedService());
        assertEquals("HIGH", response.getConfidence());
        assertEquals("MEDIUM", response.getSeverity());
        assertFalse(response.getPossibleCauses().isEmpty());
        assertFalse(response.getImmediatePrecautions().isEmpty());
        assertNotNull(response.getLimitationDisclaimer());

        // Check grounded cost estimation
        AICostEstimateDto cost = response.getCostEstimate();
        assertNotNull(cost);
        assertEquals("Plumber", cost.getServiceCategory());
        assertTrue(cost.isDatabaseGrounded(), "Must be grounded in database catalog");
        assertEquals(AICostEstimateDto.TYPE_DATABASE_CATALOG, cost.getEstimateType());
        assertEquals(180, cost.getBasePricePerHour());
        assertNotNull(cost.getEstimatedMinCost());
        assertNotNull(cost.getEstimatedMaxCost());
        assertTrue(cost.getDisclaimer().contains("GharFix catalog"));
    }

    @Test
    void testAnalyzeImage_UncataloguedService_UsesPreliminaryAiEstimate() {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");

        String mockAiResponse =
                "VISUAL SUMMARY: Damaged rooftop solar water heating manifold.\n" +
                "RECOMMENDED SERVICE: Solar Heater Technician\n" +
                "CONFIDENCE: MODERATE\n" +
                "SEVERITY: HIGH\n" +
                "POSSIBLE CAUSES:\n* Broken vacuum tube\n" +
                "PRECAUTIONS:\n* Isolate solar inlet valve\n";

        when(geminiService.generateContentWithImage(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(mockAiResponse);

        when(serviceCategoryService.findByNameIgnoreCase("Solar Heater Technician")).thenReturn(Optional.empty());

        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "solar.jpg",
                "image/jpeg",
                "fake solar image".getBytes()
        );

        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(validFile, "Solar heater broken");

        assertTrue(response.isSuccess());
        AICostEstimateDto cost = response.getCostEstimate();
        assertNotNull(cost);
        assertFalse(cost.isDatabaseGrounded(), "Must NOT be marked as database grounded for uncatalogued trade");
        assertEquals(AICostEstimateDto.TYPE_PRELIMINARY_AI, cost.getEstimateType());
        assertTrue(cost.getDisclaimer().contains("Preliminary AI estimate"));
    }

    @Test
    void testAnalyzeImage_EmergencyHazard_SetsUrgentSafetyNotice() {
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.getModelName()).thenReturn("gemini-3.6-flash");

        String mockAiResponse =
                "VISUAL SUMMARY: Charred electrical switchboard with visible smoke residue.\n" +
                "RECOMMENDED SERVICE: Electrician\n" +
                "CONFIDENCE: HIGH\n" +
                "SEVERITY: EMERGENCY\n" +
                "PRECAUTIONS:\n* Do not touch the switchboard\n";

        when(geminiService.generateContentWithImage(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn(mockAiResponse);

        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "fire.png",
                "image/png",
                "fake fire image".getBytes()
        );

        AIImageAnalysisResponse response = imageAnalysisService.analyzeImage(validFile, "Switchboard burnt");

        assertTrue(response.isSuccess());
        assertEquals("EMERGENCY", response.getSeverity());
        assertNotNull(response.getSafetyNotice());
        assertTrue(response.getSafetyNotice().contains("URGENT SAFETY NOTICE"));
    }
}
