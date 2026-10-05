package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class NotificationItem {

    @SerializedName("notification_id")
    private int id;

    @SerializedName("message")
    private String message;

    /** 0 = unread, 1 = read */
    @SerializedName("is_read")
    private int isRead;

    @SerializedName("created_at")
    private String createdAt;

    public int getId() { return id; }
    public String getMessage() { return message != null ? message : ""; }
    public boolean isUnread() { return isRead == 0; }
    public String getCreatedAt() { return createdAt; }
}
