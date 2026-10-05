package com.example.citecschedulingapp;

import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.model.NotificationItem;

import java.util.List;

/** Drives the bell icon: unread badge + a dialog listing notifications. */
public final class NotificationHelper {

    private NotificationHelper() {}

    private static String role(SessionManager s) {
        return s.isFaculty() ? "FACULTY" : "STUDENT";
    }

    /** Updates the little red count on the bell. */
    public static void refreshBadge(final Fragment fragment, ScheduleRepository repo,
                                    final SessionManager session, final TextView badge) {
        if (badge == null) return;
        repo.loadNotifications(session.getUserId(), role(session),
                new ScheduleRepository.ResultCallback<List<NotificationItem>>() {
                    @Override
                    public void onSuccess(List<NotificationItem> data, String message) {
                        if (!fragment.isAdded()) return;
                        int unread = 0;
                        if (data != null) {
                            for (NotificationItem n : data) if (n.isUnread()) unread++;
                        }
                        if (unread > 0) {
                            badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
                            badge.setVisibility(android.view.View.VISIBLE);
                        } else {
                            badge.setVisibility(android.view.View.GONE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Badge is a nicety; stay quiet if it can't load.
                    }
                });
    }

    /** Opens the list of notifications and marks them as read. */
    public static void showDialog(final Fragment fragment, final ScheduleRepository repo,
                                  final SessionManager session, final TextView badge) {
        repo.loadNotifications(session.getUserId(), role(session),
                new ScheduleRepository.ResultCallback<List<NotificationItem>>() {
                    @Override
                    public void onSuccess(List<NotificationItem> data, String message) {
                        if (!fragment.isAdded()) return;
                        if (data == null || data.isEmpty()) {
                            Toast.makeText(fragment.requireContext(), "No new notifications", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] items = new String[data.size()];
                        for (int i = 0; i < data.size(); i++) {
                            NotificationItem n = data.get(i);
                            String when = AppTime.prettyDateTime(n.getCreatedAt());
                            items[i] = (n.isUnread() ? "● " : "") + n.getMessage()
                                    + (when.isEmpty() ? "" : "\n" + when);
                        }
                        new AlertDialog.Builder(fragment.requireContext())
                                .setTitle("Notifications")
                                .setItems(items, null)
                                .setPositiveButton("Close", null)
                                .show();
                        repo.markNotificationsRead(session.getUserId(), role(session));
                        if (badge != null) badge.setVisibility(android.view.View.GONE);
                    }

                    @Override
                    public void onError(String message) {
                        if (!fragment.isAdded()) return;
                        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
