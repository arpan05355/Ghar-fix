package com.gharfix.dto;

public class AIActionConfirmRequest {

    private String confirmationToken;
    private String userNotes;

    public AIActionConfirmRequest() {
    }

    public AIActionConfirmRequest(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public AIActionConfirmRequest(String confirmationToken, String userNotes) {
        this.confirmationToken = confirmationToken;
        this.userNotes = userNotes;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public void setConfirmationToken(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public String getUserNotes() {
        return userNotes;
    }

    public void setUserNotes(String userNotes) {
        this.userNotes = userNotes;
    }
}
