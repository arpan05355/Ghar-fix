package com.gharfix.controller;

import com.gharfix.dto.*;
import com.gharfix.entity.User;
import com.gharfix.security.CustomUserDetails;
import com.gharfix.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for GharFix AI features powered by Google Gemini.
 * Provides home-service diagnosis, multimodal image analysis, and cost estimation.
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    private static final String DIAGNOSIS_SYSTEM_INSTRUCTION =
            "You are GharFix AI, an expert home maintenance and service diagnostic assistant. " +
            "Analyze the user's home-service issue or repair problem and provide helpful, structured guidance:\n" +
            "1. Probable Root Cause(s)\n" +
            "2. Immediate Safety/DIY Precautions (if any)\n" +
            "3. Recommended GharFix Service Category (e.g., Plumber, Electrician, Carpenter, AC Service, House Maid, Painter, Appliance Repair, Pest Control)\n" +
            "4. Estimated Severity / Urgency (Low, Medium, High, Emergency)\n\n" +
            "Keep your response professional, concise, reassuring, and practical for homeowners.";

    private final GeminiService geminiService;
    private final AIAssistantService aiAssistantService;
    private final AICostEstimationService costEstimationService;
    private final AIImageAnalysisService aiImageAnalysisService;
    private final AIActionExecutionService actionExecutionService;
    private final UserService userService;
    private final BookingService bookingService;
    private final ServiceComplaintService complaintService;

    public AIController(GeminiService geminiService,
                        AIAssistantService aiAssistantService,
                        AICostEstimationService costEstimationService,
                        AIImageAnalysisService aiImageAnalysisService) {
        this(geminiService, aiAssistantService, costEstimationService, aiImageAnalysisService, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AIController(GeminiService geminiService,
                        AIAssistantService aiAssistantService,
                        AICostEstimationService costEstimationService,
                        AIImageAnalysisService aiImageAnalysisService,
                        AIActionExecutionService actionExecutionService,
                        UserService userService,
                        BookingService bookingService,
                        ServiceComplaintService complaintService) {
        this.geminiService = geminiService;
        this.aiAssistantService = aiAssistantService;
        this.costEstimationService = costEstimationService;
        this.aiImageAnalysisService = aiImageAnalysisService;
        this.actionExecutionService = actionExecutionService;
        this.userService = userService;
        this.bookingService = bookingService;
        this.complaintService = complaintService;
    }

    /**
     * Multimodal image analysis endpoint for household repair and maintenance defects.
     * Endpoint: POST /api/ai/analyze-image
     */
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AIImageAnalysisResponse> analyzeImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        AIImageAnalysisResponse response = aiImageAnalysisService.analyzeImage(file, description);
        if (!response.isSuccess() && response.getError() != null &&
                (response.getError().contains("required") || response.getError().contains("limit") || response.getError().contains("Unsupported"))) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Conversational endpoint for the GharFix AI Assistant.
     * Supports intent detection (diagnosis, cost estimation, support, refunds, complaints, bookings, cancellations).
     * Endpoint: POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> chat(
            @RequestBody(required = false) AIChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(
                    AIChatResponse.error("Message cannot be blank. Please provide a message.")
            );
        }

        User currentUser = null;
        if (userDetails != null && userService != null) {
            currentUser = userService.findById(userDetails.getId()).orElse(null);
        }

        AIChatResponse response = (currentUser != null)
                ? aiAssistantService.chat(request, currentUser)
                : aiAssistantService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Dedicated cost estimation endpoint grounded in GharFix catalog rates.
     * Endpoint: POST /api/ai/estimate-cost
     */
    @PostMapping("/estimate-cost")
    public ResponseEntity<?> estimateCost(@RequestBody(required = false) Map<String, String> payload) {
        String query = payload != null ? payload.getOrDefault("service",
                payload.getOrDefault("serviceName",
                payload.getOrDefault("problem",
                payload.get("issueDescription")))) : null;

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Please provide a 'service' category name or 'problem' description for cost estimation."
            ));
        }

        boolean allowPreliminary = payload != null && Boolean.parseBoolean(
                payload.getOrDefault("allowPreliminary", payload.getOrDefault("fallback", "false")));

        Optional<AICostEstimateDto> catalogEstimate = costEstimationService.estimateCost(query)
                .or(() -> costEstimationService.estimateForQuery(query));

        if (catalogEstimate.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "estimate", catalogEstimate.get()
            ));
        }

        if (allowPreliminary) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "estimate", costEstimationService.estimatePreliminary(query)
            ));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "error", "No catalog service found matching '" + query + "'. Available services: Plumber, Electrician, Carpenter, AC Service, House Maid, Laundry, Labour, Cook."
        ));
    }

    /**
     * Diagnose a user's home-service issue using Google Gemini AI.
     * Endpoint: POST /api/ai/diagnose
     *
     * @param request The request containing the problem description
     * @return AI diagnosis response containing root cause, precautions, and recommended service
     */
    @PostMapping("/diagnose")
    public ResponseEntity<AIDiagnoseResponse> diagnose(@RequestBody(required = false) AIDiagnoseRequest request) {
        if (request == null || request.resolveProblem() == null) {
            return ResponseEntity.badRequest().body(
                    AIDiagnoseResponse.error("Problem description is required. Please describe the home-service issue.")
            );
        }

        String problem = request.resolveProblem();

        if (!geminiService.isConfigured()) {
            log.warn("AI diagnosis requested but GeminiService is not configured with GEMINI_API_KEY.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    AIDiagnoseResponse.error("Gemini AI service is currently unavailable. Please configure GEMINI_API_KEY.")
            );
        }

        try {
            log.info("Diagnosing home-service problem with Gemini model '{}'", geminiService.getModelName());
            String diagnosis = geminiService.generateContent(problem, DIAGNOSIS_SYSTEM_INSTRUCTION);

            return ResponseEntity.ok(
                    AIDiagnoseResponse.success(problem, diagnosis, geminiService.getModelName())
            );
        } catch (IllegalStateException e) {
            log.error("Gemini configuration issue during diagnosis: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    AIDiagnoseResponse.error("AI service is not configured properly.")
            );
        } catch (Exception e) {
            log.error("Failed to diagnose problem with Gemini AI: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    AIDiagnoseResponse.error("Failed to generate AI diagnosis: " + e.getMessage())
            );
        }
    }

    /**
     * Confirms and executes an AI-proposed business action using the single-use confirmation token.
     * Endpoint: POST /api/ai/actions/confirm
     */
    @PostMapping("/actions/confirm")
    public ResponseEntity<?> confirmAction(
            @RequestBody(required = false) AIActionConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized",
                    "message", "You must be signed in to confirm business actions."
            ));
        }
        if (request == null || request.getConfirmationToken() == null || request.getConfirmationToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", "Confirmation token is required."
            ));
        }

        if (actionExecutionService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                    "error", "Service Unavailable",
                    "message", "Action execution service is not configured."
            ));
        }

        try {
            AIActionExecutionResult result = actionExecutionService.executeAction(
                    userDetails.getId(), request.getConfirmationToken().trim());
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "Forbidden",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                    "success", false,
                    "error", "Token Expired or Replayed",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to execute confirmed action: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Execution Failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Dismisses an AI-proposed action token without executing it.
     * Endpoint: POST /api/ai/actions/dismiss
     */
    @PostMapping("/actions/dismiss")
    public ResponseEntity<?> dismissAction(
            @RequestBody(required = false) AIActionConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized",
                    "message", "You must be signed in."
            ));
        }
        if (request == null || request.getConfirmationToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Confirmation token is required."
            ));
        }

        boolean dismissed = (actionExecutionService != null)
                && actionExecutionService.dismissAction(userDetails.getId(), request.getConfirmationToken());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "dismissed", dismissed,
                "message", dismissed ? "Action proposal dismissed." : "Token not found or already consumed."
        ));
    }

    /**
     * Lists active bookings for the authenticated user to assist in conversational interactions.
     * Endpoint: GET /api/ai/bookings/active
     */
    @GetMapping("/bookings/active")
    public ResponseEntity<?> getActiveBookings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized",
                    "message", "You must be signed in to view your bookings."
            ));
        }

        if (bookingService == null) {
            return ResponseEntity.ok(List.of());
        }

        List<BookingResponseDto> active = bookingService.getUserBookings(userDetails.getId()).stream()
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()))
                .map(BookingResponseDto::fromEntity)
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "bookings", active
        ));
    }

    /**
     * Lists complaints registered by the authenticated user.
     * Endpoint: GET /api/ai/complaints
     */
    @GetMapping("/complaints")
    public ResponseEntity<?> getUserComplaints(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized",
                    "message", "You must be signed in to view complaints."
            ));
        }

        if (complaintService == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> complaints = complaintService.getUserComplaints(userDetails.getId()).stream()
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", c.getId());
                    map.put("ticketNumber", c.getTicketNumber());
                    map.put("serviceCategory", c.getServiceCategory());
                    map.put("description", c.getDescription());
                    map.put("severity", c.getSeverity());
                    map.put("status", c.getStatus());
                    map.put("resolutionNotes", c.getResolutionNotes());
                    map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                    map.put("bookingId", c.getBooking() != null ? c.getBooking().getId() : null);
                    return map;
                })
                .toList();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "complaints", complaints
        ));
    }

    /**
     * Checks the configuration status of the AI service.
     * Endpoint: GET /api/ai/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "configured", geminiService.isConfigured(),
                "model", geminiService.getModelName()
        ));
    }
}
