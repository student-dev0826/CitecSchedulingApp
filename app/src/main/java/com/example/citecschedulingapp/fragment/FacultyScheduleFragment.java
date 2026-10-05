package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
                        if (data != null) for (PostedSchedule s : data) if (s.isBooked()) booked.add(s);

                        if (booked.isEmpty()) {
                            tvNoBookedStudents.setText("No student consultations booked with you yet.");
                            tvNoBookedStudents.setVisibility(View.VISIBLE);
                            return;
                        }
                        tvNoBookedStudents.setVisibility(View.GONE);
                        for (PostedSchedule s : booked) containerBookedStudents.addView(buildCard(s));
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
        android.content.Context c = requireContext();
        MaterialCardView card = UiUtil.card(c);
        LinearLayout col = UiUtil.column(c);

        boolean ended = s.hasEnded();
        TextView badge = ended
                ? UiUtil.badge(c, "COMPLETED", "#E5E7EB", "#374151")
                : UiUtil.badge(c, "UPCOMING", "#DCFCE7", "#166534");
        String student = s.getStudentName();
        if (s.getStudentNumber() != null && !s.getStudentNumber().isEmpty()) {
            student += " (" + s.getStudentNumber() + ")";
        }
        col.addView(UiUtil.headerRow(c, "Student: " + student, badge));
        col.addView(UiUtil.text(c, "Purpose: " + (s.getPurpose().isEmpty() ? s.getCategory() : s.getPurpose()),
                14f, R.color.text_primary, false));
        col.addView(UiUtil.text(c, s.getDisplayDate() + " • " + s.getTimeSlot() + " (" + s.getLocation() + ")",
                13f, R.color.text_secondary, false));

        if (!ended) {
            MaterialButton btn = new MaterialButton(c);
            btn.setText("Transfer to Another Faculty");
            btn.setTextSize(12f);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = UiUtil.dp(c, 12);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> {
                if (getActivity() instanceof FacultyHomeActivity) {
                    FacultyHomeActivity a = (FacultyHomeActivity) getActivity();
                    a.setPendingTransferId(s.getId());
                    a.selectTab(R.id.nav_faculty_transfer);
                }
            });
            col.addView(btn);
        }

        card.addView(col);
        return card;
    }
}
