package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request payload for the AI home-service diagnosis endpoint.
 * Accepts problem description (or prompt) from the user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIDiagnoseRequest {

    private String problem;
    private String prompt;

    public AIDiagnoseRequest() {
    }

    public AIDiagnoseRequest(String problem) {
        this.problem = problem;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * Resolves the user input problem text, checking 'problem' then falling back to 'prompt'.
     */
    public String resolveProblem() {
        if (problem != null && !problem.isBlank()) {
            return problem.trim();
        }
        if (prompt != null && !prompt.isBlank()) {
            return prompt.trim();
        }
        return null;
    }
}
