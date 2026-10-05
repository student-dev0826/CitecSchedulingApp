package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class FacultyItem {

    @SerializedName("faculty_id")
    private int facultyId;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("department")
    private String department;

    public int getFacultyId() { return facultyId; }

    public String getDisplayName() {
        String name = PostedSchedule.formatFacultyName(fullName);
        return department != null && !department.trim().isEmpty() ? name + " (" + department.trim() + ")" : name;
    }

    @Override
    public String toString() { return getDisplayName(); }
}
