package com.example.citecschedulingapp.model;

import com.example.citecschedulingapp.AppTime;
import com.google.gson.annotations.SerializedName;

/** One consultation slot as stored on the server. */
public class PostedSchedule {

    @SerializedName("schedule_id")
    private int id;

    @SerializedName("faculty_id")
    private int facultyId;

    @SerializedName("faculty_name")
    private String facultyName;

    @SerializedName("department")
    private String department;

    /** yyyy-MM-dd */
    @SerializedName("schedule_date")
    private String date;

    @SerializedName("time_slot")
    private String timeSlot;

    /** yyyy-MM-dd HH:mm:ss (Philippine time) */
    @SerializedName("start_at")
    private String startAt;

    @SerializedName("end_at")
    private String endAt;

    @SerializedName("category")
    private String category;

    @SerializedName("location")
    private String location;

    /** OPEN, BOOKED, PENDING, ACCEPTED, DECLINED */
    @SerializedName("status")
    private String status;

    @SerializedName("student_id")
    private Integer studentId;

    @SerializedName("student_name")
    private String studentName;

    @SerializedName("student_number")
    private String studentNumber;

    @SerializedName("purpose")
    private String purpose;

    @SerializedName("transfer_reason")
    private String transferReason;

    @SerializedName("decline_reason")
    private String declineReason;

    public int getId() { return id; }
    public int getFacultyId() { return facultyId; }
    public String getFacultyName() { return facultyName != null ? facultyName : ""; }
    public String getDepartment() { return department; }
    public String getDate() { return date; }
    public String getTimeSlot() { return timeSlot != null ? timeSlot : ""; }
    public String getCategory() { return category != null ? category : ""; }
    public String getLocation() { return location != null ? location : ""; }
    public Integer getStudentId() { return studentId; }
    public String getStudentName() { return studentName != null ? studentName : ""; }
    public String getStudentNumber() { return studentNumber; }
    public String getPurpose() { return purpose != null ? purpose : ""; }
    public String getTransferReason() { return transferReason != null ? transferReason : ""; }
    public String getDeclineReason() { return declineReason != null ? declineReason : ""; }
    public String getStatus() { return status != null ? status : "OPEN"; }

    public boolean isBooked() { return "BOOKED".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status) || "DECLINED".equalsIgnoreCase(status); }
    public boolean isPending() { return "PENDING".equalsIgnoreCase(status); }
    public boolean isAccepted() { return "ACCEPTED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status) || "BOOKED".equalsIgnoreCase(status); }
    public boolean isDeclined() { return "DECLINED".equalsIgnoreCase(status); }

    public String getDisplayDate() { return AppTime.prettyDate(date); }

    public long getStartMillis() { return AppTime.parseDateTime(startAt); }

    public long getEndMillis() { return AppTime.parseDateTime(endAt); }

    /** True once the slot's end time has passed (uses the live clock). */
    public boolean hasEnded() {
        long end = getEndMillis();
        return end > 0 && end < System.currentTimeMillis();
    }

    public String getFacultyDisplayName() { return formatFacultyName(facultyName); }

    /** Adds "Prof." unless the name already carries a title. */
    public static String formatFacultyName(String name) {
        if (name == null || name.trim().isEmpty()) return "Faculty";
        String n = name.trim();
        String lower = n.toLowerCase();
        if (lower.startsWith("prof") || lower.startsWith("dr.") || lower.startsWith("dr ")
                || lower.startsWith("engr") || lower.startsWith("mr.") || lower.startsWith("ms.")
                || lower.startsWith("mrs.")) {
            return n;
        }
        return "Prof. " + n;
    }
}
