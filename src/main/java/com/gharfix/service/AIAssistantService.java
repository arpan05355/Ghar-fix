package com.gharfix.service;

import com.gharfix.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Orchestrator service for the GharFix AI Assistant.
 * Coordinates conversation handling, intent detection, safe household guidance,
 * grounded catalog cost estimations, and action proposals.
 *
 * Enforces key safety guardrails:
 * - Gemini never directly modifies bookings, triggers refunds, or cancels orders.
 * - Business logic stays in the backend.
 * - Cost estimates are strictly grounded in catalog data with non-guaranteed disclaimers.
 */
@Service
public class AIAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AIAssistantService.class);

    public static final String INTENT_DIAGNOSIS = "DIAGNOSIS";
    public static final String INTENT_COST_ESTIMATION = "COST_ESTIMATION";
    public static final String INTENT_CUSTOMER_SUPPORT = "CUSTOMER_SUPPORT";
    public static final String INTENT_REFUND_REQUEST = "REFUND_REQUEST";
    public static final String INTENT_ISSUE_REPORTING = "ISSUE_REPORTING";
    public static final String INTENT_BOOKING_ASSISTANCE = "BOOKING_ASSISTANCE";
    public static final String INTENT_CANCELLATION_ASSISTANCE = "CANCELLATION_ASSISTANCE";
    public static final String INTENT_GENERAL = "GENERAL";

    private static final String ASSISTANT_SYSTEM_PROMPT =
            "You are GharFix AI Assistant, the helpful, polite, and expert home-service concierge for GharFix.\n" +
            "GharFix connects homeowners across India with verified local technicians for: Plumber, Electrician, Carpenter, AC Service, House Maid, Laundry, Labour, and Cook.\n\n" +
            "CORE GUARDRAILS & POLICIES:\n" +
            "1. NO DIRECT ACTION EXECUTION: You CANNOT directly cancel bookings, issue refunds, or book appointments. Always guide users to the official GharFix web portal.\n" +
            "2. COST ESTIMATES: Never guarantee an exact final price. When discussing costs, refer to standard catalog rates and state that final quotes require on-site technician inspection.\n" +
            "3. CANCELLATION POLICY: Bookings can be cancelled free of charge from the 'My Bookings' page before technician confirmation or up to 2 hours before scheduled slot.\n" +
            "4. REFUND POLICY: For paid bookings (Razorpay/Online), refund requests can be submitted within 24 hours of service if unfulfilled or disputed. Refunds are processed within 3-5 business days.\n" +
            "5. COMPLAINTS & SAFETY: Empathize with customer grievances, collect details, and assure them our operations team investigates worker conduct or quality issues.\n" +
            "6. SAFETY FIRST: When diagnosing home hazards (electrical sparking, gas/pipe bursts), immediately instruct the user to cut main power or turn off water valves.\n\n" +
            "Always be concise, structured with bullet points where appropriate, and friendly.";

    private final GeminiService geminiService;
    private final AICostEstimationService costEstimationService;
    private final AIActionExecutionService actionExecutionService;
    private final BookingService bookingService;

    public AIAssistantService(GeminiService geminiService, AICostEstimationService costEstimationService) {
        this(geminiService, costEstimationService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AIAssistantService(GeminiService geminiService,
                              AICostEstimationService costEstimationService,
                              AIActionExecutionService actionExecutionService,
                              BookingService bookingService) {
        this.geminiService = geminiService;
        this.costEstimationService = costEstimationService;
        this.actionExecutionService = actionExecutionService;
        this.bookingService = bookingService;
    }

    /**
     * Processes an unauthenticated chat request.
     */
    public AIChatResponse chat(AIChatRequest request) {
        return chat(request, null);
    }

    /**
     * Processes a chat request with optional authenticated user context for safe action execution.
     */
    public AIChatResponse chat(AIChatRequest request, com.gharfix.entity.User currentUser) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return AIChatResponse.error("Message text cannot be empty.");
        }

        String userMessage = request.getMessage().trim();
        String detectedIntent = detectIntent(userMessage);
        Optional<String> detectedCategory = costEstimationService.detectServiceCategory(userMessage);

        log.info("Processing AI Assistant chat: intent='{}', category='{}'",
                detectedIntent, detectedCategory.orElse("none"));

        // Grounded cost estimate from backend database (never invented by Gemini)
        AICostEstimateDto costEstimate = null;
        if (INTENT_COST_ESTIMATION.equals(detectedIntent) || INTENT_DIAGNOSIS.equals(detectedIntent)) {
            costEstimate = detectedCategory.flatMap(costEstimationService::estimateCost).orElse(null);
            if (costEstimate == null && request.getServiceCategory() != null) {
                costEstimate = costEstimationService.estimateCost(request.getServiceCategory()).orElse(null);
            }
        }

        // Build safe action proposal if intent is action-oriented
        AIActionProposal actionProposal = buildActionProposal(detectedIntent, detectedCategory.orElse(null),
                request.getBookingId(), userMessage, currentUser);

        // Generate conversational response from Gemini (supporting text or multimodal image)
        String aiReply;
        if (geminiService.isConfigured()) {
            try {
                String contextualPrompt = buildContextualPrompt(request, detectedIntent, costEstimate);
                if (request.getImageBase64() != null && !request.getImageBase64().isBlank()) {
                    byte[] imgBytes = java.util.Base64.getDecoder().decode(request.getImageBase64().trim());
                    String mimeType = (request.getImageMimeType() != null && !request.getImageMimeType().isBlank())
                            ? request.getImageMimeType().trim()
                            : "image/jpeg";
                    aiReply = geminiService.generateContentWithImage(imgBytes, mimeType, contextualPrompt, ASSISTANT_SYSTEM_PROMPT);
                } else {
                    aiReply = geminiService.generateContent(contextualPrompt, ASSISTANT_SYSTEM_PROMPT);
                }
            } catch (Exception e) {
                log.error("Gemini AI generation failed: {}", e.getMessage());
                aiReply = buildFallbackReply(detectedIntent, detectedCategory.orElse(null), costEstimate);
            }
        } else {
            log.warn("GeminiService is not configured. Serving grounded fallback reply.");
            aiReply = buildFallbackReply(detectedIntent, detectedCategory.orElse(null), costEstimate);
        }

        return AIChatResponse.success(
                aiReply,
                detectedIntent,
                detectedCategory.orElse(null),
                costEstimate,
                actionProposal,
                geminiService.getModelName()
        );
    }

    /**
     * Deterministically classifies the primary user intent.
     */
    public String detectIntent(String query) {
        if (query == null) return INTENT_GENERAL;
        String lower = query.toLowerCase(Locale.ROOT);

        if (lower.contains("refund") || lower.contains("money back") || lower.contains("return payment")) {
            return INTENT_REFUND_REQUEST;
        } else if (lower.contains("cancel") || lower.contains("cancellation") || lower.contains("stop booking") || lower.contains("drop booking")) {
            return INTENT_CANCELLATION_ASSISTANCE;
        } else if (lower.contains("complain") || lower.contains("complaint") || lower.contains("report") || lower.contains("worker did not") || lower.contains("bad service") || lower.contains("poor service") || lower.contains("fraud") || lower.contains("rude")) {
            return INTENT_ISSUE_REPORTING;
        } else if (lower.contains("cost") || lower.contains("price") || lower.contains("charge") || lower.contains("how much") || lower.contains("rate") || lower.contains("fee") || lower.contains("estimate")) {
            return INTENT_COST_ESTIMATION;
        } else if (lower.contains("book") || lower.contains("hire") || lower.contains("reserve") || lower.contains("schedule service") || lower.contains("need a plumber") || lower.contains("need an electrician")) {
            return INTENT_BOOKING_ASSISTANCE;
        } else if (lower.contains("not working") || lower.contains("leak") || lower.contains("broken") || lower.contains("fault") || lower.contains("fix") || lower.contains("spark") || lower.contains("damage") || lower.contains("noise") || lower.contains("smell") || lower.contains("cooling") || lower.contains("running")) {
            return INTENT_DIAGNOSIS;
        } else if (lower.contains("help") || lower.contains("support") || lower.contains("contact") || lower.contains("faq") || lower.contains("hours") || lower.contains("payment method") || lower.contains("how does") || lower.contains("guarantee")) {
            return INTENT_CUSTOMER_SUPPORT;
        }

        return INTENT_GENERAL;
    }

    /**
     * Builds safe action proposals. If the user is authenticated and actionExecutionService is configured,
     * generates a validated, tokenized proposal ready for user confirmation.
     */
    private AIActionProposal buildActionProposal(String intent, String category, Long bookingId,
                                                String userMessage, com.gharfix.entity.User currentUser) {
        Long resolvedBookingId = bookingId;
        if (resolvedBookingId == null && userMessage != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?:booking\\s*(?:#|no\\.?|id\\s*:?\\s*)?|#)(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(userMessage);
            if (matcher.find()) {
                try {
                    resolvedBookingId = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (resolvedBookingId == null && currentUser != null && bookingService != null) {
            resolvedBookingId = bookingService.findLatestActiveBookingForUser(currentUser.getId())
                    .map(b -> b.getId())
                    .orElse(null);
        }

        // When user is authenticated and execution service is wired, attempt concrete proposal
        if (currentUser != null && actionExecutionService != null) {
            try {
                switch (intent) {
                    case INTENT_CANCELLATION_ASSISTANCE:
                        if (resolvedBookingId != null) {
                            return actionExecutionService.proposeCancellation(currentUser, resolvedBookingId, userMessage);
                        }
                        break;
                    case INTENT_REFUND_REQUEST:
                        if (resolvedBookingId != null) {
                            return actionExecutionService.proposeRefund(currentUser, resolvedBookingId, userMessage);
                        }
                        break;
                    case INTENT_ISSUE_REPORTING:
                        return actionExecutionService.proposeComplaint(currentUser, resolvedBookingId, category, userMessage, "MEDIUM");
                    case INTENT_BOOKING_ASSISTANCE:
                        if (category != null && !category.equalsIgnoreCase("none")) {
                            com.gharfix.dto.BookingRequestDto req = new com.gharfix.dto.BookingRequestDto();
                            req.setService(category);
                            req.setDate(java.time.LocalDate.now().plusDays(1).toString());
                            req.setTime("10:00");
                            req.setAddress("Customer Primary Address");
                            req.setCity("Vadodara");
                            return actionExecutionService.proposeBooking(currentUser, req);
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                log.debug("Could not generate tokenized action proposal: {}", e.getMessage());
            }
        }

        // Fallback / informational proposals
        switch (intent) {
            case INTENT_CANCELLATION_ASSISTANCE:
                return new AIActionProposal(
                        "CANCEL_BOOKING",
                        "Cancel an existing service booking",
                        "Are you sure you would like to proceed with cancelling this booking?",
                        resolvedBookingId != null ? java.util.Map.of("bookingId", resolvedBookingId) : java.util.Map.of(),
                        "/bookings"
                );
            case INTENT_REFUND_REQUEST:
                return new AIActionProposal(
                        "REQUEST_REFUND",
                        "Submit a formal refund review request",
                        "Would you like our support team to initiate a refund review for your booking?",
                        resolvedBookingId != null ? java.util.Map.of("bookingId", resolvedBookingId) : java.util.Map.of(),
                        "/bookings"
                );
            case INTENT_ISSUE_REPORTING:
                return new AIActionProposal(
                        "REPORT_ISSUE",
                        "Report a service issue or worker grievance",
                        "Submit this issue report directly to GharFix customer relations?",
                        resolvedBookingId != null ? java.util.Map.of("bookingId", resolvedBookingId) : java.util.Map.of(),
                        "/support"
                );
            case INTENT_BOOKING_ASSISTANCE:
                String targetUrl = (category != null) ? "/#services" : "/#services";
                return new AIActionProposal(
                        "BOOK_SERVICE",
                        "Select a verified professional on the GharFix booking portal",
                        targetUrl
                );
            default:
                return null;
        }
    }

    /**
     * Formulates the multi-turn prompt containing database grounded facts.
     */
    private String buildContextualPrompt(AIChatRequest request, String intent, AICostEstimateDto costEstimate) {
        StringBuilder sb = new StringBuilder();
        sb.append("User Message: \"").append(request.getMessage()).append("\"\n");
        sb.append("Identified Intent: ").append(intent).append("\n");

        if (costEstimate != null) {
            sb.append(String.format("GharFix Verified Catalog Rates for %s: Base rate ₹%d/hr, Standard duration ~%d mins, Estimated indicative range ₹%s - ₹%s.\n" +
                            "IMPORTANT: Inform the user of this indicative range, citing it clearly as an estimate.\n",
                    costEstimate.getServiceCategory(),
                    costEstimate.getBasePricePerHour(),
                    costEstimate.getEstimatedDurationMinutes(),
                    costEstimate.getEstimatedMinCost(),
                    costEstimate.getEstimatedMaxCost()));
        }

        if (request.getBookingId() != null) {
            sb.append("Referenced Booking ID: ").append(request.getBookingId()).append("\n");
        }

        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            sb.append("\nRecent Conversation History:\n");
            for (ChatMessageDto turn : request.getHistory()) {
                sb.append(turn.getRole()).append(": ").append(turn.getContent()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Deterministic fallback response in case Gemini API is temporarily unavailable.
     */
    private String buildFallbackReply(String intent, String category, AICostEstimateDto costEstimate) {
        switch (intent) {
            case INTENT_COST_ESTIMATION:
                if (costEstimate != null) {
                    return String.format(
                            "For **%s**, our standard GharFix catalog base rate is **₹%d/hour**. " +
                            "A typical repair takes around **%d minutes**, with an estimated indicative cost of **₹%s – ₹%s**. " +
                            "\n\n*Note: This is an estimated price range. Final cost is confirmed with the technician on-site based on scope and materials.*",
                            costEstimate.getServiceCategory(),
                            costEstimate.getBasePricePerHour(),
                            costEstimate.getEstimatedDurationMinutes(),
                            costEstimate.getEstimatedMinCost(),
                            costEstimate.getEstimatedMaxCost()
                    );
                }
                return "GharFix service prices start from ₹80 – ₹250/hour depending on the trade (Plumber, Electrician, Carpenter, AC Service). You can view exact catalog rates in the Services section.";

            case INTENT_REFUND_REQUEST:
                return "To request a refund for an unfulfilled or disputed service, please visit your **My Bookings** page. " +
                        "Refund requests submitted within 24 hours of scheduled completion are reviewed by our operations team and processed to your original payment method within 3–5 business days.";

            case INTENT_CANCELLATION_ASSISTANCE:
                return "You can easily cancel a booking from your **My Bookings** section. " +
                        "Cancellations made before technician dispatch are completely free of charge.";

            case INTENT_ISSUE_REPORTING:
                return "We take service quality very seriously. If you experienced an issue with a technician or unfinished work, " +
                        "please file a complaint through your **My Bookings** dashboard or contact GharFix Customer Support.";

            case INTENT_BOOKING_ASSISTANCE:
                return "To book a verified GharFix professional, simply browse our **Services** section, choose your required trade (Plumber, Electrician, AC Repair, etc.), and select a convenient time slot.";

            case INTENT_DIAGNOSIS:
                return "Please describe your household issue (e.g. leaking sink, AC not cooling, sparking switch). " +
                        "For electrical or plumbing emergencies, please turn off your main power breaker or water shut-off valve immediately.";

            default:
                return "Hello! I am your GharFix AI Assistant. I can help diagnose home repair problems, provide estimated catalog costs, answer platform questions, and guide you through booking or support assistance.";
        }
    }
}
