package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class ChatMessage {

    @SerializedName("message_id")
    private String messageId;

    @SerializedName("sender_id")
    private String senderId;

    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("sender_role")
    private String senderRole;

    @SerializedName("recipient_id")
    private String recipientId;

    @SerializedName("recipient_name")
    private String recipientName;

    @SerializedName("message_text")
    private String messageText;

    @SerializedName("timestamp")
    private String timestamp;

    public ChatMessage() {
        this.messageId = UUID.randomUUID().toString();
    }

    public ChatMessage(String senderId, String senderName, String senderRole, String recipientId, String recipientName, String messageText, String timestamp) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.messageText = messageText;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
