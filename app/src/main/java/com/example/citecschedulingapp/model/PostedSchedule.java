package com.example.citecschedulingapp.model;

import java.util.UUID;

public class PostedSchedule {
    private String id;
    private String facultyName;
    private String facultyId;
    private String date;
    private String timeSlot;
    private String category;
    private String location;
    private boolean isBooked;
    private String bookedByStudentName;

    public PostedSchedule() {
        this.id = UUID.randomUUID().toString();
    }

    public PostedSchedule(String facultyName, String facultyId, String date, String timeSlot, String category, String location) {
        this.id = UUID.randomUUID().toString();
        this.facultyName = facultyName;
        this.facultyId = facultyId;
        this.date = date;
        this.timeSlot = timeSlot;
        this.category = category;
        this.location = location;
        this.isBooked = false;
        this.bookedByStudentName = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isBooked() {
        return isBooked;
    }

    public void setBooked(boolean booked) {
        isBooked = booked;
    }

    public String getBookedByStudentName() {
        return bookedByStudentName;
    }

    public void setBookedByStudentName(String bookedByStudentName) {
        this.bookedByStudentName = bookedByStudentName;
    }
}
