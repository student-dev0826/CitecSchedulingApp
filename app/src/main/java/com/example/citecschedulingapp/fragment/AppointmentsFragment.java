package com.example.citecschedulingapp.fragment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.UiUtil;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class AppointmentsFragment extends Fragment {

    private static final int FILTER_ALL = 0, FILTER_UPCOMING = 1, FILTER_COMPLETED = 2;

    private MaterialButton btnFilterAll, btnFilterUpcoming, btnFilterCompleted;
    private TextView tvEmpty;
    private LinearLayout container;

    private ScheduleRepository repository;
    private SessionManager sessionManager;

    private List<PostedSchedule> appointments = new ArrayList<>();
    private int filter = FILTER_ALL;
    private boolean loaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appointments, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ScheduleRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        btnFilterAll = view.findViewById(R.id.btnFilterAll);
        btnFilterUpcoming = view.findViewById(R.id.btnFilterUpcoming);
        btnFilterCompleted = view.findViewById(R.id.btnFilterCompleted);
        tvEmpty = view.findViewById(R.id.tvAppointmentsEmpty);
        container = view.findViewById(R.id.containerAppointments);

        btnFilterAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        btnFilterUpcoming.setOnClickListener(v -> setFilter(FILTER_UPCOMING));
        btnFilterCompleted.setOnClickListener(v -> setFilter(FILTER_COMPLETED));

        styleFilters();
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        tvEmpty.setText("Loading…");
        tvEmpty.setVisibility(View.VISIBLE);
        repository.loadStudentAppointments(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> data, String message) {
                        if (!isAdded()) return;
                        appointments = data != null ? data : new ArrayList<>();
                        loaded = true;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        tvEmpty.setText(message);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void setFilter(int newFilter) {
        filter = newFilter;
        styleFilters();
        if (loaded) render();
    }

    private void styleFilters() {
        int primary = requireContext().getColor(R.color.primary);
        applyStyle(btnFilterAll, filter == FILTER_ALL, primary);
        applyStyle(btnFilterUpcoming, filter == FILTER_UPCOMING, primary);
        applyStyle(btnFilterCompleted, filter == FILTER_COMPLETED, primary);
    }

    private void applyStyle(MaterialButton b, boolean selected, int primary) {
        b.setBackgroundTintList(ColorStateList.valueOf(selected ? primary : Color.TRANSPARENT));
        b.setTextColor(selected ? Color.WHITE : primary);
    }

    private void render() {
        int upcomingCount = 0, completedCount = 0;
        for (PostedSchedule p : appointments) {
            if (p.hasEnded()) completedCount++; else upcomingCount++;
        }
        btnFilterAll.setText("All (" + appointments.size() + ")");
        btnFilterUpcoming.setText("Upcoming (" + upcomingCount + ")");
        btnFilterCompleted.setText("Completed (" + completedCount + ")");

        container.removeAllViews();
        int shown = 0;

        for (PostedSchedule p : appointments) {
            boolean ended = p.hasEnded();
            if (filter == FILTER_UPCOMING && ended) continue;
            if (filter == FILTER_COMPLETED && !ended) continue;
            container.addView(buildCard(p, ended));
            shown++;
        }

        if (shown == 0) {
            tvEmpty.setText(filter == FILTER_COMPLETED ? "No completed appointments yet."
                    : filter == FILTER_UPCOMING ? "No upcoming appointments. Tap Book to schedule one."
                    : "No appointments yet. Tap Book to schedule one.");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private View buildCard(final PostedSchedule p, boolean ended) {
        Context c = requireContext();
        MaterialCardView card = UiUtil.card(c);
        LinearLayout col = UiUtil.column(c);

        boolean isPending = p.isPending();
        boolean isDeclined = p.isDeclined();
        boolean isCancelled = p.isCancelled();

        TextView badge;
        if (isCancelled) {
            badge = UiUtil.badge(c, "CANCELLED BY PROFESSOR", "#FEE2E2", "#991B1B");
        } else if (ended && isPending) {
            badge = UiUtil.badge(c, "EXPIRED (NO RESPONSE)", "#E5E7EB", "#374151");
        } else if (ended && isDeclined) {
            badge = UiUtil.badge(c, "DECLINED BY PROFESSOR", "#FEE2E2", "#991B1B");
        } else if (ended) {
            badge = UiUtil.badge(c, "COMPLETED", "#E5E7EB", "#374151");
        } else if (isPending) {
            badge = UiUtil.badge(c, "PENDING APPROVAL", "#FEF3C7", "#92400E");
        } else if (isDeclined) {
            badge = UiUtil.badge(c, "DECLINED BY PROFESSOR", "#FEE2E2", "#991B1B");
        } else {
            badge = UiUtil.badge(c, "ACCEPTED / CONFIRMED", "#DCFCE7", "#166534");
        }

        col.addView(UiUtil.headerRow(c, p.getCategory(), badge));
        col.addView(UiUtil.text(c, "Professor: " + p.getFacultyDisplayName(), 13f, R.color.text_primary, false));
        col.addView(UiUtil.text(c, p.getDisplayDate() + " • " + p.getTimeSlot(), 13f, R.color.text_secondary, false));
        col.addView(UiUtil.text(c, "Location: " + p.getLocation(), 13f, R.color.text_secondary, false));

        if (!p.getPurpose().isEmpty()) {
            col.addView(UiUtil.text(c, "Purpose: " + p.getPurpose(), 13f, R.color.text_secondary, false));
        }

        if (isDeclined && !p.getDeclineReason().isEmpty()) {
            col.addView(UiUtil.textColor(c, "Decline Reason from Professor: " + p.getDeclineReason(),
                    13f, Color.parseColor("#991B1B"), true));
        }

        if (isCancelled) {
            col.addView(UiUtil.textColor(c, "Reason from Professor: "
                            + (p.getCancelReason().isEmpty() ? "No reason provided." : p.getCancelReason()),
                    13f, Color.parseColor("#991B1B"), true));
        }

        if (!p.getTransferReason().isEmpty()) {
            col.addView(UiUtil.text(c, "Transferred to this professor. Reason: " + p.getTransferReason(),
                    12f, R.color.accent, false));
        }

        if (!ended && !isDeclined && !isCancelled) {
            LinearLayout row = UiUtil.buttonRow(c);
            MaterialButton reschedule = UiUtil.outlinedButton(c, "Reschedule", c.getColor(R.color.primary));
            MaterialButton cancel = UiUtil.outlinedButton(c, "Cancel Request", Color.parseColor("#DC2626"));
            UiUtil.addToButtonRow(c, row, reschedule, true);
            UiUtil.addToButtonRow(c, row, cancel, false);
            reschedule.setOnClickListener(v -> confirm(p, true));
            cancel.setOnClickListener(v -> confirm(p, false));
            col.addView(row);
        }

        card.addView(col);
        return card;
    }

    private void confirm(final PostedSchedule p, final boolean thenRebook) {
        new AlertDialog.Builder(requireContext())
                .setTitle(thenRebook ? "Reschedule appointment?" : "Cancel appointment?")
                .setMessage("This cancels your booking on " + p.getDisplayDate() + " (" + p.getTimeSlot()
                        + ") with " + p.getFacultyDisplayName()
                        + (thenRebook ? " and takes you to pick a new slot." : ". The slot becomes available to others."))
                .setNegativeButton("Keep it", null)
                .setPositiveButton(thenRebook ? "Reschedule" : "Cancel appointment", (d, w) -> cancel(p, thenRebook))
                .show();
    }

    private void cancel(PostedSchedule p, final boolean thenRebook) {
        repository.cancelBooking(p.getId(), sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<Object>() {
                    @Override
                    public void onSuccess(Object data, String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        if (thenRebook && getActivity() instanceof HomeActivity) {
                            ((HomeActivity) getActivity()).selectTab(R.id.nav_book);
                        } else {
                            load();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        load();
                    }
                });
    }
}
