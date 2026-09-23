package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("user_id")
    private int userId;

    @SerializedName("student_id")
    private String studentId;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("email")
    private String email;

    @SerializedName("role")
    private String role;

    public User() {
    }

    public User(int userId, String studentId, String fullName, String email) {
        this.userId = userId;
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.role = "STUDENT";
    }

    public User(int userId, String studentId, String fullName, String email, String role) {
        this.userId = userId;
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRawRole() {
        return role;
    }

    public String getRole() {
        return role != null ? role : "STUDENT";
    }

    public void setRole(String role) {
        this.role = role;
    }
}
