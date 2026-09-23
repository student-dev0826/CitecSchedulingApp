package com.example.citecschedulingapp.fragment;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class FacultyAvailabilityFragment extends Fragment {

    private TextInputEditText etPostDate;
    private AutoCompleteTextView actPostTimeSlot;
    private AutoCompleteTextView actPostCategory;
    private TextInputEditText etPostLocation;
    private MaterialButton btnPostSchedule;
    private TextView tvNoPostedSchedules;
    private LinearLayout containerPostedSchedules;

    private SessionManager sessionManager;
    private ScheduleRepository scheduleRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_availability, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            sessionManager = new SessionManager(getContext());
            scheduleRepository = ScheduleRepository.getInstance(getContext());
        }

        initViews(view);
        setupDropdowns();
        setupListeners();
        loadPostedSchedules();
    }

    private void initViews(View view) {
        etPostDate = view.findViewById(R.id.etPostDate);
        actPostTimeSlot = view.findViewById(R.id.actPostTimeSlot);
        actPostCategory = view.findViewById(R.id.actPostCategory);
        etPostLocation = view.findViewById(R.id.etPostLocation);
        btnPostSchedule = view.findViewById(R.id.btnPostSchedule);
        tvNoPostedSchedules = view.findViewById(R.id.tvNoPostedSchedules);
        containerPostedSchedules = view.findViewById(R.id.containerPostedSchedules);
    }

    private void setupDropdowns() {
        if (getContext() == null) return;

        String[] timeSlots = new String[]{
                "08:00 AM - 09:00 AM",
                "09:00 AM - 10:00 AM",
                "10:00 AM - 11:00 AM",
                "01:00 PM - 02:00 PM",
                "02:00 PM - 03:00 PM",
                "03:00 PM - 04:00 PM"
        };

        String[] categories = new String[]{
                "Academic Advising",
                "Thesis & Capstone Consultation",
                "Document Clearance & Request",
                "Faculty Office Visit"
        };

        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, timeSlots);
        if (actPostTimeSlot != null) {
            actPostTimeSlot.setAdapter(timeAdapter);
            actPostTimeSlot.setText(timeSlots[2], false);
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, categories);
        if (actPostCategory != null) {
            actPostCategory.setAdapter(categoryAdapter);
            actPostCategory.setText(categories[0], false);
        }
    }

    private void setupListeners() {
        if (btnPostSchedule != null) {
            btnPostSchedule.setOnClickListener(v -> attemptPostSchedule());
        }
    }

    private void attemptPostSchedule() {
        String date = etPostDate != null && etPostDate.getText() != null ? etPostDate.getText().toString().trim() : "";
        String timeSlot = actPostTimeSlot != null && actPostTimeSlot.getText() != null ? actPostTimeSlot.getText().toString().trim() : "";
        String category = actPostCategory != null && actPostCategory.getText() != null ? actPostCategory.getText().toString().trim() : "";
        String location = etPostLocation != null && etPostLocation.getText() != null ? etPostLocation.getText().toString().trim() : "";

        if (TextUtils.isEmpty(date)) {
            if (etPostDate != null) etPostDate.setError("Please enter a date.");
            return;
        }

        if (TextUtils.isEmpty(location)) {
            if (etPostLocation != null) etPostLocation.setError("Please enter office location.");
            return;
        }

        String profName = sessionManager != null && !TextUtils.isEmpty(sessionManager.getFullName())
                ? "Prof. " + sessionManager.getFullName()
                : "Faculty Member";

        String profId = sessionManager != null
                ? "PROF-" + sessionManager.getUserId()
                : "PROF-001";

        PostedSchedule newSchedule = new PostedSchedule(profName, profId, date, timeSlot, category, location);

        if (scheduleRepository != null) {
            scheduleRepository.addPostedSchedule(newSchedule);
        }

        if (getContext() != null) {
            Toast.makeText(getContext(), "New consultation schedule posted successfully!", Toast.LENGTH_LONG).show();
        }

        loadPostedSchedules();
    }

    private void loadPostedSchedules() {
        if (containerPostedSchedules == null || scheduleRepository == null || sessionManager == null) return;

        containerPostedSchedules.removeAllViews();

        String profName = "Prof. " + sessionManager.getFullName();
        List<PostedSchedule> mySchedules = scheduleRepository.getSchedulesByFaculty(profName);

        if (mySchedules.isEmpty()) {
            if (tvNoPostedSchedules != null) tvNoPostedSchedules.setVisibility(View.VISIBLE);
        } else {
            if (tvNoPostedSchedules != null) tvNoPostedSchedules.setVisibility(View.GONE);

            for (PostedSchedule schedule : mySchedules) {
                View cardView = createScheduleCardView(schedule);
                containerPostedSchedules.addView(cardView);
            }
        }
    }

    private View createScheduleCardView(PostedSchedule schedule) {
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

        TextView tvCategory = new TextView(requireContext());
        tvCategory.setText(schedule.getCategory());
        tvCategory.setTextSize(16f);
        tvCategory.setTextColor(getResources().getColor(R.color.primary));
        tvCategory.setTypeface(null, Typeface.BOLD);

        TextView tvDateTime = new TextView(requireContext());
        tvDateTime.setText(schedule.getDate() + " • " + schedule.getTimeSlot());
        tvDateTime.setTextSize(14f);
        tvDateTime.setTextColor(getResources().getColor(R.color.text_primary));
        tvDateTime.setPadding(0, 8, 0, 0);

        TextView tvLocation = new TextView(requireContext());
        String statusText = schedule.isBooked() ? "BOOKED BY: " + schedule.getBookedByStudentName() : "Location: " + schedule.getLocation() + " (ACTIVE & AVAILABLE)";
        tvLocation.setText(statusText);
        tvLocation.setTextSize(13f);
        tvLocation.setTextColor(schedule.isBooked() ? getResources().getColor(R.color.accent) : getResources().getColor(R.color.text_secondary));
        tvLocation.setPadding(0, 4, 0, 0);

        layout.addView(tvCategory);
        layout.addView(tvDateTime);
        layout.addView(tvLocation);

        card.addView(layout);
        return card;
    }
}
