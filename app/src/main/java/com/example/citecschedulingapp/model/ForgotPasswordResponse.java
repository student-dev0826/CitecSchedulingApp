package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class ForgotPasswordResponse {

    private boolean success;
    private String message;

    @SerializedName("reset_token")
    private String resetToken;

    // true when the server says the whole flow must be started again (code expired, etc.)
    private boolean restart;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getResetToken() {
        return resetToken;
    }

    public boolean isRestart() {
        return restart;
    }
}
