package com.example.citecschedulingapp.model;

public class RegisterRequest {

    private String student_id;
    private String full_name;
    private String email;
    private String password;

    public RegisterRequest(String student_id, String full_name, String email, String password) {
        this.student_id = student_id;
        this.full_name = full_name;
        this.email = email;
        this.password = password;
    }

    public String getStudent_id() {
        return student_id;
    }

    public String getFull_name() {
        return full_name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}