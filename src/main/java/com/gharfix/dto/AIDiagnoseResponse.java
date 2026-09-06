package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response payload for the AI home-service diagnosis endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AIDiagnoseResponse {

    private boolean success;
    private String problem;
    private String diagnosis;
    private String response;
    private String model;
    private String error;

    public AIDiagnoseResponse() {
    }

    public static AIDiagnoseResponse success(String problem, String diagnosis, String model) {
        AIDiagnoseResponse res = new AIDiagnoseResponse();
        res.success = true;
        res.problem = problem;
        res.diagnosis = diagnosis;
        res.response = diagnosis;
        res.model = model;
        return res;
    }

    public static AIDiagnoseResponse error(String error) {
        AIDiagnoseResponse res = new AIDiagnoseResponse();
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

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
        this.response = diagnosis;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
        this.diagnosis = response;
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
