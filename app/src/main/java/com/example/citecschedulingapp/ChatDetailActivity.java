package com.example.citecschedulingapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.ChatMessage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class ChatDetailActivity extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvChatContactName;
    private TextView tvChatContactRole;
    private ScrollView scrollView;
    private LinearLayout containerMessages;
    private TextInputEditText etMessageInput;
    private MaterialButton btnSend;

    private SessionManager sessionManager;
    private ChatRepository chatRepository;

    private String currentUserId;
    private String currentUserName;
    private String currentUserRole;

    private String recipientId;
    private String recipientName;
    private String recipientRole;

    private Handler pollHandler;
    private Runnable pollRunnable;
    private int lastMessageCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_detail);

        sessionManager = new SessionManager(this);
        chatRepository = ChatRepository.getInstance(this);

        currentUserId = sessionManager.isFaculty()
                ? "PROF-" + sessionManager.getUserId()
                : sessionManager.getStudentId();
        currentUserName = sessionManager.getFullName();
        currentUserRole = sessionManager.getRole();

        readIntentExtras();
        initViews();
        setupListeners();
        setupPolling();
    }

    private void readIntentExtras() {
        if (getIntent() != null) {
            recipientId = getIntent().getStringExtra("RECIPIENT_ID");
            recipientName = getIntent().getStringExtra("RECIPIENT_NAME");
            recipientRole = getIntent().getStringExtra("RECIPIENT_ROLE");
        }

        if (TextUtils.isEmpty(recipientId)) {
            recipientId = "PROF-001";
        }
        if (TextUtils.isEmpty(recipientName)) {
            recipientName = "Faculty Consultation";
        }
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvChatContactName = findViewById(R.id.tvChatContactName);
        tvChatContactRole = findViewById(R.id.tvChatContactRole);
        scrollView = findViewById(R.id.scrollView);
        containerMessages = findViewById(R.id.containerMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);

        if (tvChatContactName != null) {
            tvChatContactName.setText(recipientName);
        }
        if (tvChatContactRole != null) {
            tvChatContactRole.setText(recipientRole != null ? recipientRole : "Active • CITEC Portal");
        }
    }

    private void setupListeners() {
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> attemptSendMessage());
        }
    }

    private void setupPolling() {
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                loadMessages();
                pollHandler.postDelayed(this, 3000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.post(pollRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void attemptSendMessage() {
        if (etMessageInput == null || etMessageInput.getText() == null) return;

        String text = etMessageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }

        chatRepository.sendMessage(
                currentUserId,
                currentUserName,
                currentUserRole,
                recipientId,
                recipientName,
                text
        );

        etMessageInput.setText("");
        loadMessages();
    }

    private void loadMessages() {
        if (containerMessages == null || chatRepository == null) return;

        List<ChatMessage> conversation = chatRepository.getConversation(currentUserId, recipientId);

        // Update view only if message count changed
        if (conversation.size() == lastMessageCount) {
            return;
        }
        lastMessageCount = conversation.size();

        containerMessages.removeAllViews();

        if (conversation.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No messages yet. Send a message to start chatting!");
            tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 40, 0, 0);
            containerMessages.addView(tvEmpty);
            return;
        }

        for (ChatMessage msg : conversation) {
            boolean isSentByMe = msg.getSenderId().equalsIgnoreCase(currentUserId);
            View bubble = createMessageBubble(msg, isSentByMe);
            containerMessages.addView(bubble);
        }

        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private View createMessageBubble(ChatMessage message, boolean isSentByMe) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(isSentByMe ? Gravity.END : Gravity.START);
        row.setPadding(0, 8, 0, 8);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = isSentByMe ? Gravity.END : Gravity.START;
        card.setLayoutParams(cardParams);
        card.setRadius(16f);
        card.setCardElevation(2f);

        if (isSentByMe) {
            card.setCardBackgroundColor(getResources().getColor(R.color.primary));
        } else {
            card.setCardBackgroundColor(getResources().getColor(R.color.white));
            card.setStrokeColor(getResources().getColor(R.color.border_color));
            card.setStrokeWidth(1);
        }

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(24, 16, 24, 16);

        TextView tvText = new TextView(this);
        tvText.setText(message.getMessageText());
        tvText.setTextSize(14f);
        tvText.setTextColor(isSentByMe ? getResources().getColor(R.color.white) : getResources().getColor(R.color.text_primary));

        TextView tvTime = new TextView(this);
        tvTime.setText(message.getTimestamp());
        tvTime.setTextSize(10f);
        tvTime.setTextColor(isSentByMe ? getResources().getColor(R.color.bg_light) : getResources().getColor(R.color.text_secondary));
        tvTime.setGravity(Gravity.END);
        tvTime.setPadding(0, 4, 0, 0);

        cardContent.addView(tvText);
        cardContent.addView(tvTime);
        card.addView(cardContent);

        row.addView(card);
        return row;
    }
}
