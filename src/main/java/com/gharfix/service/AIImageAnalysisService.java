package com.gharfix.service;

import com.gharfix.dto.AICostEstimateDto;
import com.gharfix.dto.AIImageAnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Service for analyzing household maintenance issues from user-submitted images
 * using Gemini Multimodal Vision and grounding results in GharFix database catalog rates.
 */
@Service
public class AIImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AIImageAnalysisService.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final String IMAGE_ANALYSIS_SYSTEM_INSTRUCTION =
            "You are GharFix AI Visual Diagnostic Assistant for home repair and maintenance.\n" +
            "Analyze the image of the household issue provided by the homeowner.\n\n" +
            "CRITICAL SAFETY & INTEGRITY RULES:\n" +
            "1. NO DANGEROUS ADVICE: Never instruct users to handle live bare wires, inspect gas valves with flames, or perform hazardous structural work. Always recommend a qualified technician.\n" +
            "2. PRELIMINARY ONLY: Visual analysis from a photo cannot reveal concealed piping, internal wiring faults, or compressor status. State your visual confidence honestly (HIGH, MODERATE, or LOW).\n" +
            "3. NO GUARANTEED QUOTES: Do not invent final fixed prices. Mention that estimates require on-site diagnostic verification.\n" +
            "4. RECOGNIZE EMERGENCY HAZARDS: If active water gushing, charred outlets, or sparking is visible, tag severity as EMERGENCY and instruct shutting off main breakers/valves immediately.\n\n" +
            "STRUCTURE YOUR ANSWER WITH CLEAR SECTIONS:\n" +
            "- VISUAL SUMMARY: Short description of what is visible.\n" +
            "- RECOMMENDED SERVICE: Trade (e.g. Plumber, Electrician, Carpenter, AC Service, House Maid, Laundry, Labour, Cook).\n" +
            "- CONFIDENCE: HIGH, MODERATE, or LOW (with reason).\n" +
            "- SEVERITY: LOW, MEDIUM, HIGH, or EMERGENCY.\n" +
            "- POSSIBLE CAUSES: 2-3 bullet points.\n" +
            "- PRECAUTIONS: 2-3 immediate safe DIY containment steps.\n" +
            "- SAFETY NOTICE: Any urgent electrical/gas/water hazard notice.\n" +
            "- NEXT STEPS: Recommendation to book a verified GharFix professional.";

    private final GeminiService geminiService;
    private final AICostEstimationService costEstimationService;

    public AIImageAnalysisService(GeminiService geminiService, AICostEstimationService costEstimationService) {
        this.geminiService = geminiService;
        this.costEstimationService = costEstimationService;
    }

    /**
     * Validates and analyzes an uploaded image file with optional problem description.
     */
    public AIImageAnalysisResponse analyzeImage(MultipartFile file, String description) {
        if (file == null || file.isEmpty()) {
            return AIImageAnalysisResponse.error("Image file is required. Please upload a clear photo of the issue.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return AIImageAnalysisResponse.error("File size exceeds 5MB limit. Please upload an image under 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return AIImageAnalysisResponse.error("Unsupported file format. Please upload a JPEG, PNG, or WEBP image.");
        }

        try {
            byte[] bytes = file.getBytes();
            return analyzeImageBytes(bytes, contentType, description);
        } catch (IOException e) {
            log.error("Failed to read uploaded image bytes: {}", e.getMessage());
            return AIImageAnalysisResponse.error("Failed to read image file. Please try again.");
        }
    }

    /**
     * Analyzes raw image bytes and MIME type with optional description.
     */
    public AIImageAnalysisResponse analyzeImageBytes(byte[] imageBytes, String mimeType, String description) {
        if (imageBytes == null || imageBytes.length == 0) {
            return AIImageAnalysisResponse.error("Image content cannot be empty.");
        }

        if (!geminiService.isConfigured()) {
            log.warn("Image analysis requested but GeminiService is not configured with GEMINI_API_KEY.");
            return AIImageAnalysisResponse.error("Gemini AI service is currently unavailable. Please configure GEMINI_API_KEY.");
        }

        String userPrompt = (description != null && !description.isBlank())
                ? "User Description: \"" + description.trim() + "\"\nAnalyze this photo and provide visual diagnosis, precautions, and recommended service."
                : "Analyze this photo of a household issue and provide visual diagnosis, precautions, and recommended service.";

        try {
            log.info("Analyzing household issue image with Gemini model '{}'", geminiService.getModelName());
            String responseText = geminiService.generateContentWithImage(
                    imageBytes,
                    mimeType,
                    userPrompt,
                    IMAGE_ANALYSIS_SYSTEM_INSTRUCTION
            );

            return parseAnalysisResponse(responseText, description);
        } catch (Exception e) {
            log.error("Failed visual analysis with Gemini AI: {}", e.getMessage());
            return AIImageAnalysisResponse.error("Visual analysis failed: " + e.getMessage());
        }
    }

    /**
     * Parses the unstructured text from Gemini into a structured AIImageAnalysisResponse DTO,
     * extracting identified category and attaching grounded database pricing.
     */
    private AIImageAnalysisResponse parseAnalysisResponse(String text, String description) {
        if (text == null || text.isBlank()) {
            return AIImageAnalysisResponse.error("Empty analysis returned by AI.");
        }

        // Extract recommended service category
        String extractedService = extractField(text, "RECOMMENDED SERVICE", "Service");
        if (extractedService == null || extractedService.isBlank()) {
            extractedService = costEstimationService.detectServiceCategory(text)
                    .or(() -> costEstimationService.detectServiceCategory(description))
                    .orElse("General Maintenance");
        }
        final String finalService = extractedService;

        // Extract confidence
        String confidence = extractField(text, "CONFIDENCE", "Confidence");
        if (confidence == null || confidence.isBlank()) {
            confidence = text.toUpperCase(Locale.ROOT).contains("HIGH CONFIDENCE") ? "HIGH" : "MODERATE";
        } else if (confidence.toUpperCase(Locale.ROOT).contains("HIGH")) {
            confidence = "HIGH";
        } else if (confidence.toUpperCase(Locale.ROOT).contains("LOW")) {
            confidence = "LOW";
        } else {
            confidence = "MODERATE";
        }

        // Extract severity
        String severity = extractField(text, "SEVERITY", "Severity");
        if (severity == null || severity.isBlank()) {
            if (text.toUpperCase(Locale.ROOT).contains("EMERGENCY")) {
                severity = "EMERGENCY";
            } else if (text.toUpperCase(Locale.ROOT).contains("HIGH SEVERITY") || text.toUpperCase(Locale.ROOT).contains("SEVERITY: HIGH")) {
                severity = "HIGH";
            } else {
                severity = "MEDIUM";
            }
        } else if (severity.toUpperCase(Locale.ROOT).contains("EMERGENCY")) {
            severity = "EMERGENCY";
        } else if (severity.toUpperCase(Locale.ROOT).contains("HIGH")) {
            severity = "HIGH";
        } else if (severity.toUpperCase(Locale.ROOT).contains("LOW")) {
            severity = "LOW";
        } else {
            severity = "MEDIUM";
        }

        String visualSummary = extractVisualSummary(text);
        List<String> causes = extractBulletPoints(text, "POSSIBLE CAUSES", "CAUSES");
        List<String> precautions = extractBulletPoints(text, "PRECAUTIONS", "SAFETY / DIY PRECAUTIONS");
        String safetyNotice = extractSafetyNotice(text, severity);

        // Fetch grounded cost estimate from database catalog
        AICostEstimateDto costEstimate = costEstimationService.estimateCostWithComplexity(finalService, severity)
                .orElseGet(() -> costEstimationService.estimatePreliminary(finalService));

        return AIImageAnalysisResponse.success(
                visualSummary,
                finalService,
                confidence,
                "Visual features analyzed from uploaded photo. Physical accessibility and concealed parts require on-site technician inspection.",
                severity,
                causes,
                precautions,
                safetyNotice,
                costEstimate,
                geminiService.getModelName()
        );
    }

    private String extractField(String text, String... headers) {
        for (String header : headers) {
            int idx = text.toUpperCase(Locale.ROOT).indexOf(header.toUpperCase(Locale.ROOT));
            if (idx != -1) {
                int start = text.indexOf(':', idx);
                if (start != -1 && start - idx < header.length() + 5) {
                    int end = text.indexOf('\n', start);
                    String val = (end != -1) ? text.substring(start + 1, end).trim() : text.substring(start + 1).trim();
                    val = val.replaceAll("[*#_]", "").trim();
                    if (!val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    private String extractVisualSummary(String text) {
        String summary = extractField(text, "VISUAL SUMMARY");
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        int firstLineEnd = text.indexOf('\n');
        return (firstLineEnd != -1) ? text.substring(0, firstLineEnd).replaceAll("[*#_]", "").trim() : text;
    }

    private List<String> extractBulletPoints(String text, String... headers) {
        List<String> items = new ArrayList<>();
        for (String header : headers) {
            int idx = text.toUpperCase(Locale.ROOT).indexOf(header.toUpperCase(Locale.ROOT));
            if (idx != -1) {
                int sectionEnd = text.indexOf("\n\n", idx + header.length());
                if (sectionEnd == -1) sectionEnd = Math.min(text.length(), idx + 400);
                String section = text.substring(idx, sectionEnd);
                for (String line : section.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("-") || (trimmed.length() > 2 && Character.isDigit(trimmed.charAt(0)) && trimmed.charAt(1) == '.')) {
                        String clean = trimmed.replaceFirst("^[*\\-\\d.]+\\s*", "").replaceAll("[*#_]", "").trim();
                        if (!clean.isBlank()) {
                            items.add(clean);
                        }
                    }
                }
                if (!items.isEmpty()) break;
            }
        }
        return items;
    }

    private String extractSafetyNotice(String text, String severity) {
        if ("EMERGENCY".equalsIgnoreCase(severity)) {
            return "URGENT SAFETY NOTICE: Potential hazard detected. Disconnect primary electrical breaker or close main water shutoff valve immediately.";
        }
        return extractField(text, "SAFETY NOTICE", "SAFETY WARNING");
    }
}
