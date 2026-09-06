package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Response payload for image-based household defect analysis.
 * Conveys visual diagnosis, confidence, safety precautions,
 * and grounded catalog price estimates.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIImageAnalysisResponse {

    private boolean success;
    private String visualSummary;
    private String recommendedService;
    private String confidence; // "HIGH", "MODERATE", "LOW"
    private String confidenceReason;
    private String severity;   // "LOW", "MEDIUM", "HIGH", "EMERGENCY"
    private List<String> possibleCauses = new ArrayList<>();
    private List<String> immediatePrecautions = new ArrayList<>();
    private String safetyNotice;
    private AICostEstimateDto costEstimate;
    private String limitationDisclaimer = "Visual analysis is preliminary and based solely on the provided image. " +
            "Concealed pipe defects, internal wiring issues, and structural integrity cannot be determined from photographs alone. " +
            "A qualified on-site professional must verify before repair.";
    private String model;
    private String error;

    public AIImageAnalysisResponse() {
    }

    public static AIImageAnalysisResponse success(String visualSummary, String recommendedService,
                                                  String confidence, String confidenceReason, String severity,
                                                  List<String> possibleCauses, List<String> immediatePrecautions,
                                                  String safetyNotice, AICostEstimateDto costEstimate, String model) {
        AIImageAnalysisResponse res = new AIImageAnalysisResponse();
        res.success = true;
        res.visualSummary = visualSummary;
        res.recommendedService = recommendedService;
        res.confidence = confidence;
        res.confidenceReason = confidenceReason;
        res.severity = severity;
        res.possibleCauses = possibleCauses != null ? possibleCauses : new ArrayList<>();
        res.immediatePrecautions = immediatePrecautions != null ? immediatePrecautions : new ArrayList<>();
        res.safetyNotice = safetyNotice;
        res.costEstimate = costEstimate;
        res.model = model;
        return res;
    }

    public static AIImageAnalysisResponse error(String error) {
        AIImageAnalysisResponse res = new AIImageAnalysisResponse();
        res.success = false;
        res.error = error;
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getVisualSummary() {
        return visualSummary;
    }

    public void setVisualSummary(String visualSummary) {
        this.visualSummary = visualSummary;
    }

    public String getRecommendedService() {
        return recommendedService;
    }

    public void setRecommendedService(String recommendedService) {
        this.recommendedService = recommendedService;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getConfidenceReason() {
        return confidenceReason;
    }

    public void setConfidenceReason(String confidenceReason) {
        this.confidenceReason = confidenceReason;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getPossibleCauses() {
        return possibleCauses;
    }

    public void setPossibleCauses(List<String> possibleCauses) {
        this.possibleCauses = possibleCauses;
    }

    public List<String> getImmediatePrecautions() {
        return immediatePrecautions;
    }

    public void setImmediatePrecautions(List<String> immediatePrecautions) {
        this.immediatePrecautions = immediatePrecautions;
    }

    public String getSafetyNotice() {
        return safetyNotice;
    }

    public void setSafetyNotice(String safetyNotice) {
        this.safetyNotice = safetyNotice;
    }

    public AICostEstimateDto getCostEstimate() {
        return costEstimate;
    }

    public void setCostEstimate(AICostEstimateDto costEstimate) {
        this.costEstimate = costEstimate;
    }

    public String getLimitationDisclaimer() {
        return limitationDisclaimer;
    }

    public void setLimitationDisclaimer(String limitationDisclaimer) {
        this.limitationDisclaimer = limitationDisclaimer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
