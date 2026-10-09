package com.example.citecschedulingapp;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.citecschedulingapp.model.ChatConversation;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * While a home screen is open, checks the server every few seconds for unread chat messages,
 * shows the red count on the Chat tab, and posts a phone notification for new messages.
 */
public class ChatUnreadMonitor {

    public static final String EXTRA_OPEN_CHAT = "OPEN_CHAT_TAB";
    private static final String CHANNEL_ID = "chat_messages";
    private static final long INTERVAL_MS = 5000;

    private final Activity activity;
    private final BottomNavigationView nav;
    private final int chatItemId;
    private final String myId;
    private final ChatRepository repo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> lastUnread = new HashMap<>();

    private boolean running = false;
    private boolean firstPoll = true;
    private int generation = 0;

    public ChatUnreadMonitor(Activity activity, BottomNavigationView nav, int chatItemId, String myId) {
        this.activity = activity;
        this.nav = nav;
        this.chatItemId = chatItemId;
        this.myId = myId;
        this.repo = ChatRepository.getInstance(activity);
        createChannel();
    }

    public static int notificationId(String contactId) {
        return 7000 + Math.abs(contactId == null ? 0 : contactId.toLowerCase().hashCode() % 100000);
    }

    /** Android 13+ needs the user's OK before any notification can show. */
    public static void requestNotificationPermission(Activity a) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    public void start() {
        running = true;
        generation++;
        poll(generation);
    }

    public void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
    }

    private void poll(final int gen) {
        if (!running || gen != generation || myId == null || myId.isEmpty()) return;
        repo.loadConversations(myId, new ChatRepository.Result<List<ChatConversation>>() {
            @Override
            public void onSuccess(List<ChatConversation> data) {
                if (!running || gen != generation || activity.isFinishing()) return;
                apply(data);
                schedule(gen);
            }

            @Override
            public void onError(String message) {
                if (!running || gen != generation) return;
                schedule(gen);
            }
        });
    }

    private void schedule(final int gen) {
        handler.postDelayed(() -> poll(gen), INTERVAL_MS);
    }

    private void apply(List<ChatConversation> data) {
        int total = ChatRepository.totalUnread(data);

        BadgeDrawable badge = nav.getOrCreateBadge(chatItemId);
        badge.setBackgroundColor(Color.parseColor("#DC2626"));
        badge.setBadgeTextColor(Color.WHITE);
        if (total > 0) {
            badge.setNumber(total);
            badge.setMaxCharacterCount(3);
            badge.setVisible(true);
        } else {
            badge.clearNumber();
            badge.setVisible(false);
        }

        boolean onChatTab = nav.getSelectedItemId() == chatItemId;
        if (data != null) {
            for (ChatConversation c : data) {
                String key = c.getContactId().toLowerCase();
                int before = lastUnread.containsKey(key) ? lastUnread.get(key) : 0;
                if (!firstPoll && c.getUnread() > before && !onChatTab) {
                    notifyNewMessage(c);
                }
                lastUnread.put(key, c.getUnread());
                if (c.getUnread() == 0) cancelNotification(c.getContactId());
            }
        }
        firstPoll = false;
    }

    private void notifyNewMessage(ChatConversation c) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent intent = new Intent(activity, activity.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_OPEN_CHAT, true);
        PendingIntent pi = PendingIntent.getActivity(activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = c.getContactName().isEmpty() ? "New message" : c.getContactName();
        String body = c.getUnread() > 1
                ? c.getUnread() + " new messages: " + c.getLastMessage()
                : c.getLastMessage();

        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notificationId(c.getContactId()), b.build());
    }

    private void cancelNotification(String contactId) {
        NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(contactId));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Chat messages",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("New messages from professors and students");
            NotificationManager nm = activity.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
