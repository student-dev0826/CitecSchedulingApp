package com.example.citecschedulingapp.fragment;

import android.graphics.Typeface;
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
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class FacultyScheduleFragment extends Fragment {

    private TextView tvNoBookedStudents;
    private LinearLayout containerBookedStudents;

    private SessionManager sessionManager;
    private ScheduleRepository scheduleRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            sessionManager = new SessionManager(getContext());
            scheduleRepository = ScheduleRepository.getInstance(getContext());
        }

        tvNoBookedStudents = view.findViewById(R.id.tvNoBookedStudents);
        containerBookedStudents = view.findViewById(R.id.containerBookedStudents);

        loadBookedStudents();
    }

    private void loadBookedStudents() {
        if (containerBookedStudents == null || scheduleRepository == null || sessionManager == null) return;

        containerBookedStudents.removeAllViews();

        String profName = "Prof. " + sessionManager.getFullName();
        List<PostedSchedule> bookedSchedules = scheduleRepository.getBookedSchedulesByFaculty(profName);

        if (bookedSchedules.isEmpty()) {
            if (tvNoBookedStudents != null) tvNoBookedStudents.setVisibility(View.VISIBLE);
        } else {
            if (tvNoBookedStudents != null) tvNoBookedStudents.setVisibility(View.GONE);

            for (PostedSchedule schedule : bookedSchedules) {
                View cardView = createBookedStudentCardView(schedule);
                containerBookedStudents.addView(cardView);
            }
        }
    }

    private View createBookedStudentCardView(PostedSchedule schedule) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 20);
        card.setLayoutParams(params);
        card.setCardElevation(4f);
        card.setRadius(24f);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView tvStudent = new TextView(requireContext());
        tvStudent.setText("Student: " + schedule.getBookedByStudentName());
        tvStudent.setTextSize(16f);
        tvStudent.setTextColor(getResources().getColor(R.color.primary));
        tvStudent.setTypeface(null, Typeface.BOLD);

        TextView tvCategory = new TextView(requireContext());
        tvCategory.setText("Purpose: " + schedule.getCategory());
        tvCategory.setTextSize(14f);
        tvCategory.setTextColor(getResources().getColor(R.color.text_primary));
        tvCategory.setPadding(0, 8, 0, 0);

        TextView tvDateTime = new TextView(requireContext());
        tvDateTime.setText(schedule.getDate() + " • " + schedule.getTimeSlot() + " (" + schedule.getLocation() + ")");
        tvDateTime.setTextSize(13f);
        tvDateTime.setTextColor(getResources().getColor(R.color.text_secondary));
        tvDateTime.setPadding(0, 4, 0, 0);

        MaterialButton btnTransfer = new MaterialButton(requireContext());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 16, 0, 0);
        btnTransfer.setLayoutParams(btnParams);
        btnTransfer.setText("Transfer to Another Faculty");
        btnTransfer.setTextSize(12f);

        btnTransfer.setOnClickListener(v -> {
            if (getActivity() instanceof FacultyHomeActivity) {
                ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_transfer);
            }
        });

        layout.addView(tvStudent);
        layout.addView(tvCategory);
        layout.addView(tvDateTime);
        layout.addView(btnTransfer);

        card.addView(layout);
        return card;
    }
}
