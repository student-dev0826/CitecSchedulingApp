package com.example.citecschedulingapp.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.FacultyHomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.UiUtil;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class FacultyScheduleFragment extends Fragment {

    private TextView tvNoBookedStudents;
    private LinearLayout containerBookedStudents;

    private SessionManager sessionManager;
    private ScheduleRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        repository = ScheduleRepository.getInstance(requireContext());
        tvNoBookedStudents = view.findViewById(R.id.tvNoBookedStudents);
        containerBookedStudents = view.findViewById(R.id.containerBookedStudents);

        loadBookedStudents();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBookedStudents();
    }

    private void loadBookedStudents() {
        tvNoBookedStudents.setText("Loading…");
        tvNoBookedStudents.setVisibility(View.VISIBLE);

        repository.loadFacultySlots(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> data, String message) {
                        if (!isAdded()) return;
                        containerBookedStudents.removeAllViews();
                        List<PostedSchedule> booked = new ArrayList<>();
                        if (data != null) {
                            for (PostedSchedule s : data) {
                                if (s.isBooked()) booked.add(s);
                            }
                        }

                        if (booked.isEmpty()) {
                            tvNoBookedStudents.setText("No student consultation requests or bookings yet.");
                            tvNoBookedStudents.setVisibility(View.VISIBLE);
                            return;
                        }
                        tvNoBookedStudents.setVisibility(View.GONE);
                        for (PostedSchedule s : booked) {
                            containerBookedStudents.addView(buildCard(s));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        tvNoBookedStudents.setText(message);
                        tvNoBookedStudents.setVisibility(View.VISIBLE);
                    }
                });
    }

    private View buildCard(final PostedSchedule s) {
        Context c = requireContext();
        MaterialCardView card = UiUtil.card(c);
        LinearLayout col = UiUtil.column(c);

        boolean ended = s.hasEnded();
        boolean isPending = s.isPending();
        boolean isDeclined = s.isDeclined();

        TextView badge;
        if (ended) {
            badge = UiUtil.badge(c, "COMPLETED", "#E5E7EB", "#374151");
        } else if (isPending) {
            badge = UiUtil.badge(c, "PENDING REQUEST", "#FEF3C7", "#92400E");
        } else if (isDeclined) {
            badge = UiUtil.badge(c, "DECLINED", "#FEE2E2", "#991B1B");
        } else {
            badge = UiUtil.badge(c, "ACCEPTED", "#DCFCE7", "#166534");
        }

        String student = s.getStudentName();
        if (s.getStudentNumber() != null && !s.getStudentNumber().isEmpty()) {
            student += " (" + s.getStudentNumber() + ")";
        }
        col.addView(UiUtil.headerRow(c, "Student: " + student, badge));
        col.addView(UiUtil.text(c, "Category / Subject: " + (s.getCategory().isEmpty() ? "Consultation" : s.getCategory()),
                14f, R.color.text_primary, false));
        col.addView(UiUtil.text(c, s.getDisplayDate() + " • " + s.getTimeSlot() + " (" + s.getLocation() + ")",
                13f, R.color.text_secondary, false));

        if (!s.getPurpose().isEmpty()) {
            col.addView(UiUtil.text(c, "Purpose: " + s.getPurpose(), 13f, R.color.text_secondary, false));
        }

        if (isDeclined && !s.getDeclineReason().isEmpty()) {
            col.addView(UiUtil.text(c, "Decline Reason: " + s.getDeclineReason(), 12f, Color.parseColor("#991B1B"), true));
        }

        if (!ended && isPending) {
            // Accept / Decline Row for Pending Requests
            LinearLayout row = UiUtil.buttonRow(c);

            MaterialButton btnAccept = new MaterialButton(c);
            btnAccept.setText("ACCEPT REQUEST");
            btnAccept.setTextSize(12f);
            btnAccept.setBackgroundColor(Color.parseColor("#166534"));

            MaterialButton btnDecline = UiUtil.outlinedButton(c, "DECLINE", Color.parseColor("#DC2626"));

            UiUtil.addToButtonRow(c, row, btnAccept, true);
            UiUtil.addToButtonRow(c, row, btnDecline, false);

            btnAccept.setOnClickListener(v -> respondToRequest(s, "ACCEPTED", null));
            btnDecline.setOnClickListener(v -> promptDeclineReason(s));

            col.addView(row);
        } else if (!ended && !isDeclined) {
            MaterialButton btnTransfer = new MaterialButton(c);
            btnTransfer.setText("Transfer to Another Faculty");
            btnTransfer.setTextSize(12f);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = UiUtil.dp(c, 12);
            btnTransfer.setLayoutParams(p);
            btnTransfer.setOnClickListener(v -> {
                if (getActivity() instanceof FacultyHomeActivity) {
                    FacultyHomeActivity a = (FacultyHomeActivity) getActivity();
                    a.setPendingTransferId(s.getId());
                    a.selectTab(R.id.nav_faculty_transfer);
                }
            });
            col.addView(btnTransfer);
        }

        card.addView(col);
        return card;
    }

    private void promptDeclineReason(PostedSchedule s) {
        Context c = requireContext();
        final EditText input = new EditText(c);
        input.setHint("e.g. Schedule conflict with department meeting");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(c)
                .setTitle("Decline Appointment Request")
                .setMessage("Please provide a short reason for declining " + s.getStudentName() + "'s request:")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Decline Request", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (TextUtils.isEmpty(reason)) {
                        reason = "Schedule conflict during requested time slot.";
                    }
                    respondToRequest(s, "DECLINED", reason);
                })
                .show();
    }

    private void respondToRequest(PostedSchedule s, String status, String declineReason) {
        repository.respondAppointment(s.getId(), sessionManager.getUserId(), status, declineReason,
                new ScheduleRepository.ResultCallback<Object>() {
                    @Override
                    public void onSuccess(Object data, String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        loadBookedStudents();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
