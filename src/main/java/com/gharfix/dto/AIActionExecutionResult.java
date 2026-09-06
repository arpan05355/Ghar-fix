package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIActionExecutionResult {

    private boolean success;
    private String actionType;
    private String message;
    private String referenceId;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public AIActionExecutionResult() {
        this.timestamp = LocalDateTime.now();
    }

    public AIActionExecutionResult(boolean success, String actionType, String message,
                                   String referenceId, Map<String, Object> details) {
        this.success = success;
        this.actionType = actionType;
        this.message = message;
        this.referenceId = referenceId;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public static AIActionExecutionResult success(String actionType, String message,
                                                  String referenceId, Map<String, Object> details) {
        return new AIActionExecutionResult(true, actionType, message, referenceId, details);
    }

    public static AIActionExecutionResult failure(String actionType, String message) {
        return new AIActionExecutionResult(false, actionType, message, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
