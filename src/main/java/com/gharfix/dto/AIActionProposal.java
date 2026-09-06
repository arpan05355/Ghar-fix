package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Represents a proposed business action identified by the AI Assistant.
 * In accordance with GharFix safety architecture, Gemini NEVER directly executes
 * business actions (cancellations, refunds, bookings). It produces an action proposal
 * that requires user review and backend validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIActionProposal {

    private String actionType; // "NONE", "BOOK_SERVICE", "CANCEL_BOOKING", "REQUEST_REFUND", "REPORT_ISSUE"
    private String description;
    private Map<String, Object> parameters;
    private String confirmationPrompt;
    private boolean requiresUserConfirmation;
    private String nextStepUrl;
    private String confirmationToken;
    private String expiresAt;

    public AIActionProposal() {
    }

    public AIActionProposal(String actionType, String description, String nextStepUrl) {
        this.actionType = actionType;
        this.description = description;
        this.nextStepUrl = nextStepUrl;
        this.requiresUserConfirmation = false;
    }

    public AIActionProposal(String actionType, String description, String confirmationPrompt,
                            Map<String, Object> parameters, String nextStepUrl) {
        this(actionType, description, confirmationPrompt, parameters, nextStepUrl, null, null);
    }

    public AIActionProposal(String actionType, String description, String confirmationPrompt,
                            Map<String, Object> parameters, String nextStepUrl,
                            String confirmationToken, String expiresAt) {
        this.actionType = actionType;
        this.description = description;
        this.confirmationPrompt = confirmationPrompt;
        this.parameters = parameters;
        this.nextStepUrl = nextStepUrl;
        this.requiresUserConfirmation = (confirmationToken != null || confirmationPrompt != null);
        this.confirmationToken = confirmationToken;
        this.expiresAt = expiresAt;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getConfirmationPrompt() {
        return confirmationPrompt;
    }

    public void setConfirmationPrompt(String confirmationPrompt) {
        this.confirmationPrompt = confirmationPrompt;
    }

    public boolean isRequiresUserConfirmation() {
        return requiresUserConfirmation;
    }

    public void setRequiresUserConfirmation(boolean requiresUserConfirmation) {
        this.requiresUserConfirmation = requiresUserConfirmation;
    }

    public String getNextStepUrl() {
        return nextStepUrl;
    }

    public void setNextStepUrl(String nextStepUrl) {
        this.nextStepUrl = nextStepUrl;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public void setConfirmationToken(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
