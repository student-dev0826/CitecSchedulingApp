package com.example.citecschedulingapp;

import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.model.NotificationItem;

import java.util.List;

/** Drives the bell icon: unread badge and the themed summary inbox. */
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

    /** Opens the summary inbox; its read behavior uses the existing API. */
    public static void showDialog(final Fragment fragment, final ScheduleRepository repo,
                                  final SessionManager session, final TextView badge) {
        if (!fragment.isAdded() || fragment.getView() == null) return;
        androidx.fragment.app.FragmentManager manager = fragment.getChildFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(NotificationInboxDialog.TAG) != null) return;
        new NotificationInboxDialog().showNow(manager, NotificationInboxDialog.TAG);
    }
}
