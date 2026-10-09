package com.example.citecschedulingapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.citecschedulingapp.model.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatRepository {

    private static final String PREF_NAME = "CITEC_CHAT_MESSAGES";
    private static final String KEY_MESSAGES = "allChatMessages";
    private static ChatRepository instance;

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    private ChatRepository(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context);
        }
        return instance;
    }

    public List<ChatMessage> getAllMessages() {
        String json = sharedPreferences.getString(KEY_MESSAGES, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<ChatMessage>>() {}.getType();
        List<ChatMessage> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void saveAllMessages(List<ChatMessage> messages) {
        String json = gson.toJson(messages);
        sharedPreferences.edit().putString(KEY_MESSAGES, json).apply();
    }

    public void sendMessage(String senderId, String senderName, String senderRole, String recipientId, String recipientName, String messageText) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String currentTime = sdf.format(new Date());

        ChatMessage msg = new ChatMessage(senderId, senderName, senderRole, recipientId, recipientName, messageText, currentTime);
        List<ChatMessage> all = getAllMessages();
        all.add(msg);
        saveAllMessages(all);
    }

    public List<ChatMessage> getConversation(String user1Id, String user2Id) {
        List<ChatMessage> all = getAllMessages();
        List<ChatMessage> conversation = new ArrayList<>();

        if (user1Id == null) user1Id = "";
        if (user2Id == null) user2Id = "";

        String clean1 = user1Id.replace("PROF-", "").trim().toLowerCase();
        String clean2 = user2Id.replace("PROF-", "").trim().toLowerCase();

        for (ChatMessage msg : all) {
            String sId = msg.getSenderId() != null ? msg.getSenderId().replace("PROF-", "").trim().toLowerCase() : "";
            String rId = msg.getRecipientId() != null ? msg.getRecipientId().replace("PROF-", "").trim().toLowerCase() : "";

            boolean match1 = (sId.equals(clean1) && rId.equals(clean2));
            boolean match2 = (sId.equals(clean2) && rId.equals(clean1));
            boolean matchFuzzy = (sId.equals(clean2) || rId.equals(clean2)) && (sId.equals(clean1) || rId.equals(clean1));

            if (match1 || match2 || matchFuzzy) {
                conversation.add(msg);
            }
        }

        return conversation;
    }

    public ChatMessage getLastMessage(String user1Id, String user2Id) {
        List<ChatMessage> conversation = getConversation(user1Id, user2Id);
        if (conversation.isEmpty()) {
            return null;
        }
        return conversation.get(conversation.size() - 1);
    }

    public static class ContactInfo {
        public String contactId;
        public String contactName;
        public String contactRole;
        public ChatMessage lastMessage;

        public ContactInfo(String contactId, String contactName, String contactRole, ChatMessage lastMessage) {
            this.contactId = contactId;
            this.contactName = contactName;
            this.contactRole = contactRole;
            this.lastMessage = lastMessage;
        }
    }

    public List<ContactInfo> getRecentContactsForUser(String myUserId, boolean iAmFaculty) {
        List<ChatMessage> all = getAllMessages();
        Map<String, ContactInfo> contactMap = new HashMap<>();

        String cleanMyId = myUserId != null ? myUserId.replace("PROF-", "").trim().toLowerCase() : "";

        for (ChatMessage msg : all) {
            String sId = msg.getSenderId() != null ? msg.getSenderId().replace("PROF-", "").trim().toLowerCase() : "";
            String rId = msg.getRecipientId() != null ? msg.getRecipientId().replace("PROF-", "").trim().toLowerCase() : "";

            boolean isMySent = sId.equals(cleanMyId);
            boolean isMyReceived = rId.equals(cleanMyId);

            if (isMySent || isMyReceived) {
                String otherId = isMySent ? msg.getRecipientId() : msg.getSenderId();
                String otherName = isMySent ? msg.getRecipientName() : msg.getSenderName();
                String otherRole = isMySent ? "Recipient" : msg.getSenderRole();

                if (otherId != null && !otherId.isEmpty()) {
                    contactMap.put(otherId.toLowerCase(), new ContactInfo(otherId, otherName, otherRole, msg));
                }
            } else if (iAmFaculty && "STUDENT".equalsIgnoreCase(msg.getSenderRole())) {
                String otherId = msg.getSenderId();
                String otherName = msg.getSenderName();
                String otherRole = "Student";
                contactMap.put(otherId.toLowerCase(), new ContactInfo(otherId, otherName, otherRole, msg));
            }
        }

        return new ArrayList<>(contactMap.values());
    }
}
