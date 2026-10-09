package com.example.citecschedulingapp;

import android.content.Context;

import com.example.citecschedulingapp.model.ApiResult;
import com.example.citecschedulingapp.model.ChatConversation;
import com.example.citecschedulingapp.model.ChatMessage;
import com.example.citecschedulingapp.network.ApiService;
import com.example.citecschedulingapp.network.RetrofitClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Chat now lives on the server (chat.php), so both people see the same messages and the server
 * can tell who has read what. All callbacks arrive on the main thread.
 */
public class ChatRepository {

    public interface Result<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private static ChatRepository instance;
    private final ApiService api;

    private ChatRepository() {
        api = RetrofitClient.getApiService();
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) instance = new ChatRepository();
        return instance;
    }

    private static Map<String, String> req(String action) {
        Map<String, String> m = new HashMap<>();
        m.put("action", action);
        return m;
    }

    public void sendMessage(String senderId, String senderName, String senderRole,
                            String recipientId, String recipientName, String text,
                            Result<Object> cb) {
        Map<String, String> m = req("send");
        m.put("sender_id", senderId);
        m.put("sender_name", senderName);
        m.put("sender_role", senderRole);
        m.put("recipient_id", recipientId);
        m.put("recipient_name", recipientName);
        m.put("message_text", text);
        api.chatSimple(m).enqueue(wrap(cb));
    }

    public void fetchConversation(String myId, String otherId, Result<List<ChatMessage>> cb) {
        Map<String, String> m = req("fetch");
        m.put("user1", myId);
        m.put("user2", otherId);
        api.chatMessages(m).enqueue(wrap(cb));
    }

    public void loadConversations(String myId, Result<List<ChatConversation>> cb) {
        Map<String, String> m = req("conversations");
        m.put("user_id", myId);
        api.chatConversations(m).enqueue(wrap(cb));
    }

    /** Fire-and-forget: tells the server I have now seen everything from contactId. */
    public void markRead(String myId, String contactId) {
        Map<String, String> m = req("mark_read");
        m.put("user_id", myId);
        m.put("contact_id", contactId);
        api.chatSimple(m).enqueue(new Callback<ApiResult<Object>>() {
            @Override public void onResponse(Call<ApiResult<Object>> c, Response<ApiResult<Object>> r) { }
            @Override public void onFailure(Call<ApiResult<Object>> c, Throwable t) { }
        });
    }

    public static int totalUnread(List<ChatConversation> list) {
        int n = 0;
        if (list != null) for (ChatConversation c : list) n += c.getUnread();
        return n;
    }

    private static <T> Callback<ApiResult<T>> wrap(final Result<T> cb) {
        return new Callback<ApiResult<T>>() {
            @Override
            public void onResponse(Call<ApiResult<T>> call, Response<ApiResult<T>> response) {
                ApiResult<T> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    cb.onError("Server error. Please try again.");
                } else if (!body.isSuccess()) {
                    cb.onError(body.getMessage());
                } else {
                    cb.onSuccess(body.getData());
                }
            }

            @Override
            public void onFailure(Call<ApiResult<T>> call, Throwable t) {
                cb.onError("No connection. Check your internet.");
            }
        };
    }
}
