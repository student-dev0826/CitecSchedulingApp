package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {

    @SerializedName("identifier")
    private String identifier;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("role")
    private String role;

    public LoginRequest(String identifier, String password) {
        this.identifier = identifier;
        this.email = identifier;
        this.password = password;
    }

    public LoginRequest(String identifier, String password, String role) {
        this.identifier = identifier;
        this.email = identifier;
        this.password = password;
        this.role = role;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }
}
