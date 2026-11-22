package com.example.citecschedulingapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.ChatMessage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.nio.charset.StandardCharsets;

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
    private boolean loading = false;
    private boolean sending = false;
    private boolean deleting;
    private int readGeneration;
    private long lastDisplayedId;
    private MaterialButton removeConversation;
    private View replyPreview;
    private TextView tvReplyAuthor, tvReplyExcerpt;
    private ImageButton btnCancelReply;
    private String replyMessageId, replyAuthor, replyExcerpt;
    private BottomSheetDialog messageActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_detail);
        UiUtil.applyWindowInsets(this);

        sessionManager = new SessionManager(this);
        chatRepository = ChatRepository.getInstance(this);

        currentUserId = sessionManager.isFaculty()
                ? "PROF-" + sessionManager.getUserId()
                : sessionManager.getStudentId();
        currentUserName = sessionManager.getFullName();
        currentUserRole = sessionManager.getRole();

        readIntentExtras();
        ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .cancel(ChatUnreadMonitor.notificationId(recipientId));
        initViews();
        if (savedInstanceState != null) {
            replyMessageId = savedInstanceState.getString("replyMessageId");
            replyAuthor = savedInstanceState.getString("replyAuthor");
            replyExcerpt = savedInstanceState.getString("replyExcerpt");
            updateReplyPreview();
        }
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
        removeConversation = findViewById(R.id.btnRemoveConversation);
        removeConversation.setEnabled(false);
        removeConversation.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, v);
            menu.getMenu().add(R.string.chat_delete_conversation);
            menu.setOnMenuItemClickListener(item -> { confirmRemoval(null); return true; });
            menu.show();
        });
        replyPreview = findViewById(R.id.replyPreview);
        tvReplyAuthor = findViewById(R.id.tvReplyAuthor);
        tvReplyExcerpt = findViewById(R.id.tvReplyExcerpt);
        btnCancelReply = findViewById(R.id.btnCancelReply);
        btnCancelReply.setOnClickListener(v -> clearReply());

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
        if (messageActions != null) messageActions.dismiss();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("replyMessageId", replyMessageId);
        outState.putString("replyAuthor", replyAuthor);
        outState.putString("replyExcerpt", replyExcerpt);
    }

    private void showMessageActions(ChatMessage message) {
        if (sending || deleting || isFinishing()) return;
        if (messageActions != null) messageActions.dismiss();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(etMessageInput.getWindowToken(), 0);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View actions = getLayoutInflater().inflate(R.layout.dialog_chat_message_actions, null);
        ((TextView) actions.findViewById(R.id.tvSelectedMessage)).setText(messageBody(message));
        actions.findViewById(R.id.btnReplyMessage).setOnClickListener(v -> {
            dialog.dismiss();
            replyMessageId = message.getMessageId();
            replyAuthor = TextUtils.isEmpty(message.getSenderName())
                    ? (message.getSenderId().equalsIgnoreCase(currentUserId) ? currentUserName : recipientName)
                    : message.getSenderName();
            replyExcerpt = ChatReply.excerpt(message.getMessageText());
            updateReplyPreview();
            etMessageInput.requestFocus();
            etMessageInput.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) keyboard.showSoftInput(etMessageInput, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        });
        actions.findViewById(R.id.btnCopyMessage).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Message", messageBody(message)));
            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.chat_copied, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        actions.findViewById(R.id.btnDeleteMessage).setOnClickListener(v -> {
            dialog.dismiss();
            confirmRemoval(message.getMessageId());
        });
        dialog.setContentView(actions);
        dialog.setOnDismissListener(d -> { if (messageActions == dialog) messageActions = null; });
        messageActions = dialog;
        dialog.show();
    }

    private String messageBody(ChatMessage message) {
        ChatReply reply = ChatReply.parse(message.getMessageText());
        return reply == null ? message.getMessageText() : reply.body;
    }

    private void updateReplyPreview() {
        replyPreview.setVisibility(replyAuthor == null ? View.GONE : View.VISIBLE);
        if (replyAuthor != null) {
            tvReplyAuthor.setText(getString(R.string.chat_replying_to, replyAuthor));
            tvReplyExcerpt.setText(replyExcerpt);
        }
    }

    private void clearReply() {
        replyMessageId = replyAuthor = replyExcerpt = null;
        updateReplyPreview();
    }

    private void attemptSendMessage() {
        if (etMessageInput == null || etMessageInput.getText() == null || sending || deleting) return;

        final String text = etMessageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        final String outgoing = replyAuthor == null ? text : ChatReply.encode(replyAuthor, replyExcerpt, text);
        // The existing server can truncate long payloads; reject rather than lose text.
        if (outgoing.getBytes(StandardCharsets.UTF_8).length > 2000) {
            etMessageInput.setError(getString(R.string.chat_message_too_long));
            return;
        }

        sending = true;
        btnSend.setEnabled(false);
        etMessageInput.setEnabled(false);
        btnCancelReply.setEnabled(false);
        chatRepository.sendMessage(currentUserId, currentUserName, currentUserRole,
                recipientId, recipientName, outgoing, new ChatRepository.Result<Object>() {
                    @Override
                    public void onSuccess(Object data) {
                        sending = false;
                        if (isFinishing() || isDestroyed()) return;
                        btnSend.setEnabled(true);
                        etMessageInput.setEnabled(true);
                        btnCancelReply.setEnabled(true);
                        etMessageInput.setText("");
                        clearReply();
                        loadMessages();
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        if (isFinishing() || isDestroyed()) return;
                        btnSend.setEnabled(true);
                        etMessageInput.setEnabled(true);
                        btnCancelReply.setEnabled(true);
                        android.widget.Toast.makeText(ChatDetailActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadMessages() {
        if (containerMessages == null || chatRepository == null || loading || deleting) return;
        loading = true;
        final int generation = readGeneration;

        chatRepository.fetchConversation(currentUserId, recipientId, new ChatRepository.Result<List<ChatMessage>>() {
            @Override
            public void onSuccess(List<ChatMessage> conversation) {
                if (generation != readGeneration) return;
                loading = false;
                if (isFinishing() || isDestroyed() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) || conversation == null) return;
                showMessages(conversation);
            }

            @Override
            public void onError(String message) {
                if (generation != readGeneration) return;
                loading = false;   // keep what is on screen; try again on the next poll
                if (isFinishing() || isDestroyed() || containerMessages.getChildCount() > 0) return;
                TextView error = new TextView(ChatDetailActivity.this);
                error.setText(message);
                error.setTextColor(getColor(R.color.text_primary));
                containerMessages.addView(error);
            }
        });
    }

    private void showMessages(List<ChatMessage> conversation) {
        lastDisplayedId = 0;
        for (ChatMessage message : conversation) {
            try { lastDisplayedId = Math.max(lastDisplayedId, Long.parseLong(message.getMessageId())); }
            catch (NumberFormatException ignored) { }
        }
        removeConversation.setEnabled(lastDisplayedId > 0 && !deleting);
        // Anything the other person sent that I haven't read is read now: tell the server.
        boolean hasIncomingUnread = false;
        for (ChatMessage m : conversation) {
            boolean mine = m.getSenderId().equalsIgnoreCase(currentUserId);
            if (!mine && !m.isRead()) hasIncomingUnread = true;
        }
        if (hasIncomingUnread) {
            chatRepository.markRead(currentUserId, recipientId);
        }

        if (conversation.isEmpty()) {
            if (containerMessages.getChildCount() == 1 && "empty".equals(containerMessages.getChildAt(0).getTag())) return;
            containerMessages.removeAllViews();
            TextView tvEmpty = new TextView(this);
            tvEmpty.setTag("empty");
            tvEmpty.setText("No messages yet. Send a message to start chatting!");
            tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 40, 0, 0);
            containerMessages.addView(tvEmpty);
            return;
        }

        if (containerMessages.getChildCount() == 1 && "empty".equals(containerMessages.getChildAt(0).getTag())) {
            containerMessages.removeAllViews();
        }
        int previousCount = containerMessages.getChildCount();

        int lastMineIndex = -1;
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i).getSenderId().equalsIgnoreCase(currentUserId)) lastMineIndex = i;
        }

        for (int i = 0; i < conversation.size(); i++) {
            ChatMessage msg = conversation.get(i);
            boolean isSentByMe = msg.getSenderId().equalsIgnoreCase(currentUserId);
            // Show "Seen" under my most recent message once they have read it.
            boolean showSeen = isSentByMe && i == lastMineIndex && msg.isRead();
            // Reuse unchanged bubbles; a new message must not rebuild the entire history.
            List<String> content = java.util.Arrays.asList(msg.getMessageId(), msg.getSenderId(),
                    msg.getMessageText(), msg.getTimestamp(), Boolean.toString(showSeen));
            View existing = containerMessages.getChildAt(i);
            if (existing != null && content.equals(existing.getTag())) continue;
            View bubble = createMessageBubble(msg, isSentByMe, showSeen);
            bubble.setTag(content);
            if (existing != null) containerMessages.removeViewAt(i);
            containerMessages.addView(bubble, i);
        }

        while (containerMessages.getChildCount() > conversation.size()) {
            containerMessages.removeViewAt(containerMessages.getChildCount() - 1);
        }

        if (scrollView != null && conversation.size() > previousCount) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void confirmRemoval(String messageId) {
        if (deleting || sending || (messageId == null && lastDisplayedId == 0)) return;
        final long throughId = lastDisplayedId;
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.WeeklyAvailabilityDialog)
                .setTitle(messageId == null ? R.string.chat_delete_conversation : R.string.chat_delete)
                .setMessage(messageId == null ? R.string.chat_remove_conversation_confirm : R.string.chat_remove_message_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_delete_for_me, (dialog, which) -> remove(messageId, throughId))
                .show();
    }

    private void remove(String messageId, long throughId) {
        if (deleting || sending) return;
        deleting = true;
        readGeneration++; // Ignore an older poll that might otherwise restore a removed bubble.
        loading = false;
        btnSend.setEnabled(false);
        removeConversation.setEnabled(false);
        ChatRepository.Result<Object> callback = new ChatRepository.Result<Object>() {
            @Override public void onSuccess(Object data) {
                deleting = false;
                if (isFinishing() || isDestroyed()) return;
                btnSend.setEnabled(true);
                if (messageId != null && messageId.equals(replyMessageId)) clearReply();
                ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                        .cancel(ChatUnreadMonitor.notificationId(recipientId));
                if (messageId == null) finish();
                else loadMessages();
            }
            @Override public void onError(String message) {
                deleting = false;
                if (isFinishing() || isDestroyed()) return;
                btnSend.setEnabled(true);
                removeConversation.setEnabled(lastDisplayedId > 0);
                new androidx.appcompat.app.AlertDialog.Builder(ChatDetailActivity.this, R.style.WeeklyAvailabilityDialog)
                        .setTitle(R.string.chat_remove_failed).setMessage(message)
                        .setPositiveButton(android.R.string.ok, null).show();
                loadMessages();
            }
        };
        if (messageId == null) chatRepository.deleteConversation(recipientId, throughId, callback);
        else chatRepository.deleteMessage(messageId, callback);
    }

    private View createMessageBubble(ChatMessage message, boolean isSentByMe, boolean showSeen) {
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
        card.setRadius(UiUtil.dp(this, 14));
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
        int bubblePadding = UiUtil.dp(this, 12);
        cardContent.setPadding(bubblePadding, bubblePadding, bubblePadding, UiUtil.dp(this, 8));
        int textWidth = (int) (getResources().getDisplayMetrics().widthPixels * .75f) - bubblePadding * 2;
        ChatReply quotedReply = ChatReply.parse(message.getMessageText());
        if (quotedReply != null) {
            LinearLayout quote = new LinearLayout(this);
            quote.setOrientation(LinearLayout.VERTICAL);
            int padding = UiUtil.dp(this, 10);
            quote.setPadding(padding, padding, padding, padding);
            GradientDrawable background = new GradientDrawable();
            background.setColor(getColor(R.color.brand_tint));
            background.setCornerRadius(UiUtil.dp(this, 8));
            quote.setBackground(background);
            TextView author = new TextView(this);
            author.setText(quotedReply.author);
            author.setTextColor(getColor(R.color.primary));
            author.setTextSize(12);
            author.setTypeface(null, android.graphics.Typeface.BOLD);
            author.setMaxWidth(textWidth - padding * 2);
            author.setMaxLines(1);
            author.setEllipsize(TextUtils.TruncateAt.END);
            TextView excerpt = new TextView(this);
            excerpt.setText(quotedReply.excerpt);
            excerpt.setTextColor(getColor(R.color.text_secondary));
            excerpt.setTextSize(13);
            excerpt.setMaxWidth(textWidth - padding * 2);
            excerpt.setMaxLines(2);
            excerpt.setEllipsize(TextUtils.TruncateAt.END);
            quote.addView(author);
            quote.addView(excerpt);
            LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            quoteParams.bottomMargin = UiUtil.dp(this, 8);
            cardContent.addView(quote, quoteParams);
        }

        TextView tvText = new TextView(this);
        tvText.setText(messageBody(message));
        tvText.setMaxWidth(textWidth);
        tvText.setTextSize(14f);
        tvText.setTextColor(isSentByMe ? getResources().getColor(R.color.white) : getResources().getColor(R.color.text_primary));

        TextView tvTime = new TextView(this);
        tvTime.setText(message.getTimestamp() + (showSeen ? "  \u2022 Seen" : ""));
        tvTime.setTextSize(10f);
        tvTime.setTextColor(isSentByMe ? getResources().getColor(R.color.bg_light) : getResources().getColor(R.color.text_secondary));
        tvTime.setGravity(Gravity.END);
        tvTime.setPadding(0, 4, 0, 0);

        cardContent.addView(tvText);
        cardContent.addView(tvTime);
        card.addView(cardContent);

        row.addView(card);
        card.setFocusable(true);
        card.setOnLongClickListener(v -> { showMessageActions(message); return true; });
        androidx.appcompat.widget.TooltipCompat.setTooltipText(card, getString(R.string.chat_message_actions_hint));
        androidx.core.view.ViewCompat.replaceAccessibilityAction(card,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                getString(R.string.chat_message_actions), (view, arguments) -> { showMessageActions(message); return true; });
        return row;
    }
}
