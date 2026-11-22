package com.example.citecschedulingapp;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.citecschedulingapp.model.NotificationItem;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A summary inbox and full-message view using only the existing notification API. */
public class NotificationInboxDialog extends BottomSheetDialogFragment {
    public static final String TAG = "notification-inbox";
    private ScheduleRepository repository;
    private SessionManager session;
    private View root;
    private int requestGeneration;
    private final Set<Integer> opened = new HashSet<>();
    private String selectedMessage, selectedCreatedAt;

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle state) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());
        dialog.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (selectedMessage != null) showInbox();
                else dismiss();
            }
        });
        return dialog;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.dialog_notifications, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        root = view;
        session = new SessionManager(requireContext());
        repository = ScheduleRepository.getInstance(requireContext());
        view.findViewById(R.id.btnCloseNotifications).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnNotificationBack).setOnClickListener(v -> showInbox());
        view.findViewById(R.id.btnNotificationReturn).setOnClickListener(v -> showInbox());
        view.findViewById(R.id.btnRetryNotifications).setOnClickListener(v -> load());
        view.findViewById(R.id.btnNotificationOpenAppointments).setOnClickListener(v -> {
            if (getActivity() instanceof FacultyHomeActivity) {
                ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_schedule);
            } else if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).selectTab(R.id.nav_appointments);
            }
            dismiss();
        });
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
        if (state != null) {
            ArrayList<Integer> ids = state.getIntegerArrayList("openedNotifications");
            if (ids != null) opened.addAll(ids);
        }
        load();
        if (state != null && state.containsKey("selectedNotification")) {
            String message = state.getString("selectedNotification", "");
            showDetails(message, state.getString("selectedCreatedAt"), NotificationSummary.classify(message));
        }
    }

    @Override public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * .86f);
            sheet.setLayoutParams(params);
            sheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putIntegerArrayList("openedNotifications", new ArrayList<>(opened));
        if (selectedMessage != null) {
            state.putString("selectedNotification", selectedMessage);
            state.putString("selectedCreatedAt", selectedCreatedAt);
        }
    }

    @Override public void onDestroyView() {
        requestGeneration++;
        root = null;
        super.onDestroyView();
    }

    private boolean ready(int generation) {
        return isAdded() && root != null && generation == requestGeneration;
    }

    private void load() {
        final int generation = ++requestGeneration;
        showState(R.string.notification_loading, getString(R.string.notification_loading_hint), true, false);
        repository.loadNotifications(session.getUserId(), session.isFaculty() ? "FACULTY" : "STUDENT",
                new ScheduleRepository.ResultCallback<List<NotificationItem>>() {
                    @Override public void onSuccess(List<NotificationItem> items, String message) {
                        if (!ready(generation)) return;
                        if (items == null || items.isEmpty()) {
                            showState(R.string.notification_empty, getString(R.string.notification_empty_hint), false, false);
                            return;
                        }
                        render(items);
                        // Preserve the app's existing mark-read-on-open behavior and API.
                        repository.markNotificationsRead(session.getUserId(), session.isFaculty() ? "FACULTY" : "STUDENT");
                        if (getParentFragment() != null && getParentFragment().getView() != null) {
                            View badge = getParentFragment().getView().findViewById(R.id.tvNotifBadge);
                            if (badge != null) badge.setVisibility(View.GONE);
                        }
                    }
                    @Override public void onError(String message) {
                        if (ready(generation)) showState(R.string.notification_error, message, false, true);
                    }
                });
    }

    private void showState(int title, String message, boolean loading, boolean retry) {
        root.findViewById(R.id.notificationListScroll).setVisibility(View.GONE);
        root.findViewById(R.id.tvNotificationCount).setVisibility(View.GONE);
        root.findViewById(R.id.notificationState).setVisibility(View.VISIBLE);
        root.findViewById(R.id.notificationProgress).setVisibility(loading ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.btnRetryNotifications).setVisibility(retry ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.tvNotificationStateTitle)).setText(title);
        ((TextView) root.findViewById(R.id.tvNotificationStateMessage)).setText(message);
    }

    private void render(List<NotificationItem> items) {
        root.findViewById(R.id.notificationState).setVisibility(View.GONE);
        root.findViewById(R.id.notificationListScroll).setVisibility(View.VISIBLE);
        TextView count = root.findViewById(R.id.tvNotificationCount);
        count.setVisibility(View.VISIBLE);
        count.setText(getResources().getQuantityString(R.plurals.notification_update_count, items.size(), items.size()));
        LinearLayout list = root.findViewById(R.id.notificationList);
        list.removeAllViews();
        for (NotificationItem item : items) {
            NotificationSummary.Kind kind = NotificationSummary.classify(item.getMessage());
            MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_notification, list, false);
            String summary = summary(kind);
            ((TextView) card.findViewById(R.id.tvNotificationSummary)).setText(summary);
            TextView time = card.findViewById(R.id.tvNotificationTime);
            setTime(time, item.getCreatedAt());
            TextView badge = card.findViewById(R.id.tvNotificationNew);
            badge.setVisibility(item.isUnread() || opened.contains(item.getId()) ? View.VISIBLE : View.GONE);
            badge.setText(opened.contains(item.getId()) ? R.string.notification_viewed : R.string.notification_new);
            card.setCardBackgroundColor(requireContext().getColor(item.isUnread() && !opened.contains(item.getId())
                    ? R.color.brand_tint : R.color.white));
            card.setOnClickListener(v -> {
                opened.add(item.getId());
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.notification_viewed);
                card.setCardBackgroundColor(requireContext().getColor(R.color.white));
                showDetails(item.getMessage(), item.getCreatedAt(), kind);
            });
            list.addView(card);
        }
    }

    private void setTime(TextView view, String raw) {
        String when = AppTime.prettyDateTime(raw);
        view.setText(when);
        view.setVisibility(when.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showDetails(String message, String createdAt, NotificationSummary.Kind kind) {
        selectedMessage = message;
        selectedCreatedAt = createdAt;
        root.findViewById(R.id.notificationInbox).setVisibility(View.GONE);
        root.findViewById(R.id.notificationDetail).setVisibility(View.VISIBLE);
        root.findViewById(R.id.btnNotificationBack).setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.tvNotificationsTitle)).setText(R.string.notification_detail_title);
        ((TextView) root.findViewById(R.id.tvNotificationsSubtitle)).setText(R.string.notification_detail_subtitle);
        ((TextView) root.findViewById(R.id.tvNotificationDetailSummary)).setText(summary(kind));
        setTime(root.findViewById(R.id.tvNotificationDetailTime), createdAt);
        ((TextView) root.findViewById(R.id.tvNotificationFullMessage)).setText(TextUtils.isEmpty(message)
                ? getString(R.string.notification_no_details) : message);
        TextView open = root.findViewById(R.id.btnNotificationOpenAppointments);
        open.setVisibility(kind == NotificationSummary.Kind.UPDATE ? View.GONE : View.VISIBLE);
        open.setText(session.isFaculty() ? R.string.notification_open_schedules : R.string.notification_open_appointments);
        ((ScrollView) root.findViewById(R.id.notificationDetailScroll)).scrollTo(0, 0);
        root.findViewById(R.id.tvNotificationDetailSummary).sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void showInbox() {
        if (root == null) return;
        selectedMessage = selectedCreatedAt = null;
        root.findViewById(R.id.notificationDetail).setVisibility(View.GONE);
        root.findViewById(R.id.notificationInbox).setVisibility(View.VISIBLE);
        root.findViewById(R.id.btnNotificationBack).setVisibility(View.GONE);
        ((TextView) root.findViewById(R.id.tvNotificationsTitle)).setText(R.string.notification_title);
        ((TextView) root.findViewById(R.id.tvNotificationsSubtitle)).setText(R.string.notification_subtitle);
    }

    private String summary(NotificationSummary.Kind kind) {
        int text;
        switch (kind) {
            case SLOT_REQUESTED: text = R.string.notification_summary_slot_requested; break;
            case CUSTOM_REQUEST: text = R.string.notification_summary_custom_request; break;
            case SLOT_BOOKED: text = R.string.notification_summary_slot_booked; break;
            case ACCEPTED: text = R.string.notification_summary_accepted; break;
            case DECLINED: text = R.string.notification_summary_declined; break;
            case CANCELLED: text = session.isFaculty() ? R.string.notification_summary_cancelled_faculty : R.string.notification_summary_cancelled_student; break;
            case WITHDRAWN: text = R.string.notification_summary_withdrawn; break;
            case TRANSFERRED: text = session.isFaculty() ? R.string.notification_summary_transferred_faculty : R.string.notification_summary_transferred_student; break;
            case EXPIRED: text = R.string.notification_summary_expired; break;
            default: text = R.string.notification_summary_update;
        }
        return getString(text);
    }
}
