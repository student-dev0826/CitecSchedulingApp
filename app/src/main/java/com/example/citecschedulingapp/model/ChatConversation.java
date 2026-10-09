package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

/** One row of the chat inbox: a person I talk with, the last message, and my unread count. */
public class ChatConversation {

    @SerializedName("contact_id")
    private String contactId;

    @SerializedName("contact_name")
    private String contactName;

    @SerializedName("last_id")
    private int lastId;

    @SerializedName("last_message")
    private String lastMessage;

    @SerializedName("last_sender_id")
    private String lastSenderId;

    @SerializedName("last_time")
    private String lastTime;

    @SerializedName("unread")
    private int unread;

    public String getContactId() { return contactId != null ? contactId : ""; }
    public String getContactName() { return contactName != null ? contactName : ""; }
    public int getLastId() { return lastId; }
    public String getLastMessage() { return lastMessage != null ? lastMessage : ""; }
    public String getLastSenderId() { return lastSenderId != null ? lastSenderId : ""; }
    public String getLastTime() { return lastTime != null ? lastTime : ""; }
    public int getUnread() { return unread; }
}
