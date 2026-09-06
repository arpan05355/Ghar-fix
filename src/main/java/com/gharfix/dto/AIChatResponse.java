package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response payload returned by the GharFix AI Assistant.
 * Includes conversational reply, detected intent, grounded cost estimates,
 * and safe proposed actions when applicable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIChatResponse {

    private boolean success;
    private String reply;
    private String intent;
    private String serviceCategory;
    private AICostEstimateDto costEstimate;
    private AIActionProposal proposedAction;
    private String model;
    private String error;

    public AIChatResponse() {
    }

    public static AIChatResponse success(String reply, String intent, String serviceCategory,
                                         AICostEstimateDto costEstimate, AIActionProposal proposedAction,
                                         String model) {
        AIChatResponse res = new AIChatResponse();
        res.success = true;
        res.reply = reply;
        res.intent = intent;
        res.serviceCategory = serviceCategory;
        res.costEstimate = costEstimate;
        res.proposedAction = proposedAction;
        res.model = model;
        return res;
    }

    public static AIChatResponse error(String error) {
        AIChatResponse res = new AIChatResponse();
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

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getServiceCategory() {
        return serviceCategory;
    }

    public void setServiceCategory(String serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public AICostEstimateDto getCostEstimate() {
        return costEstimate;
    }

    public void setCostEstimate(AICostEstimateDto costEstimate) {
        this.costEstimate = costEstimate;
    }

    public AIActionProposal getProposedAction() {
        return proposedAction;
    }

    public void setProposedAction(AIActionProposal proposedAction) {
        this.proposedAction = proposedAction;
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
