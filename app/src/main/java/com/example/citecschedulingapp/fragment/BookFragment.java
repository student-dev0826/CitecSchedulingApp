package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class BookFragment extends Fragment {

    private AutoCompleteTextView actSelectProfessor;
    private AutoCompleteTextView actAvailableSlot;
    private MaterialButton btnSubmitBooking;

    private ScheduleRepository scheduleRepository;
    private SessionManager sessionManager;

    private List<PostedSchedule> currentSelectedProfSchedules = new ArrayList<>();
    private PostedSchedule selectedScheduleSlot = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            scheduleRepository = ScheduleRepository.getInstance(getContext());
            sessionManager = new SessionManager(getContext());
        }

        actSelectProfessor = view.findViewById(R.id.actSelectProfessor);
        actAvailableSlot = view.findViewById(R.id.actAvailableSlot);
        btnSubmitBooking = view.findViewById(R.id.btnSubmitBooking);

        setupDropdowns();
        setupListeners();
    }

    private void setupDropdowns() {
        if (getContext() == null || scheduleRepository == null) return;

        List<String> facultyList = scheduleRepository.getAllFacultyNamesWithPostedSchedules();

        if (facultyList.isEmpty()) {
            if (actSelectProfessor != null) {
                actSelectProfessor.setText("No professor schedules posted yet", false);
            }
            if (actAvailableSlot != null) {
                actAvailableSlot.setText("No available schedules", false);
            }
            if (btnSubmitBooking != null) {
                btnSubmitBooking.setEnabled(false);
                btnSubmitBooking.setText("NO SCHEDULES AVAILABLE TO BOOK");
            }
            return;
        }

        if (btnSubmitBooking != null) {
            btnSubmitBooking.setEnabled(true);
            btnSubmitBooking.setText("BOOK SELECTED SCHEDULE");
        }

        ArrayAdapter<String> profAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, facultyList);

        if (actSelectProfessor != null) {
            actSelectProfessor.setAdapter(profAdapter);
            actSelectProfessor.setText(facultyList.get(0), false);

            actSelectProfessor.setOnItemClickListener((parent, view, position, id) -> {
                String selectedProf = (String) parent.getItemAtPosition(position);
                loadAvailableSlotsForProfessor(selectedProf);
            });
        }

        loadAvailableSlotsForProfessor(facultyList.get(0));
    }

    private void loadAvailableSlotsForProfessor(String profName) {
        if (getContext() == null || actAvailableSlot == null || scheduleRepository == null) return;

        currentSelectedProfSchedules = scheduleRepository.getUnbookedSchedulesByFaculty(profName);

        if (currentSelectedProfSchedules.isEmpty()) {
            actAvailableSlot.setText("No active slots for this professor", false);
            selectedScheduleSlot = null;
            if (btnSubmitBooking != null) btnSubmitBooking.setEnabled(false);
            return;
        }

        if (btnSubmitBooking != null) btnSubmitBooking.setEnabled(true);

        List<String> slotDisplayTexts = new ArrayList<>();
        for (PostedSchedule s : currentSelectedProfSchedules) {
            String display = s.getDate() + " • " + s.getTimeSlot() + " | " + s.getCategory() + " (" + s.getLocation() + ")";
            slotDisplayTexts.add(display);
        }

        ArrayAdapter<String> slotAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, slotDisplayTexts);
        actAvailableSlot.setAdapter(slotAdapter);
        actAvailableSlot.setText(slotDisplayTexts.get(0), false);
        selectedScheduleSlot = currentSelectedProfSchedules.get(0);

        actAvailableSlot.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentSelectedProfSchedules.size()) {
                selectedScheduleSlot = currentSelectedProfSchedules.get(position);
            }
        });
    }

    private void setupListeners() {
        if (btnSubmitBooking != null) {
            btnSubmitBooking.setOnClickListener(v -> {
                if (selectedScheduleSlot == null) {
                    Toast.makeText(getContext(), "Please select an available schedule slot.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String studentName = sessionManager != null && !TextUtils.isEmpty(sessionManager.getFullName())
                        ? sessionManager.getFullName()
                        : "Student";

                boolean success = scheduleRepository.bookSchedule(selectedScheduleSlot.getId(), studentName);

                if (success && getContext() != null) {
                    Toast.makeText(getContext(), "Appointment successfully booked with " + selectedScheduleSlot.getFacultyName() + " for " + selectedScheduleSlot.getDate() + " (" + selectedScheduleSlot.getTimeSlot() + ")!", Toast.LENGTH_LONG).show();

                    if (getActivity() instanceof HomeActivity) {
                        ((HomeActivity) getActivity()).selectTab(R.id.nav_appointments);
                    }
                }
            });
        }
    }
}
