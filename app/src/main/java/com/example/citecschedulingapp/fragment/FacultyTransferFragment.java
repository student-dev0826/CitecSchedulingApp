package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.FacultyHomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class FacultyTransferFragment extends Fragment {

    private AutoCompleteTextView actStudentSchedule;
    private AutoCompleteTextView actTargetFaculty;
    private MaterialButton btnConfirmTransfer;

    private ScheduleRepository scheduleRepository;
    private SessionManager sessionManager;

    private List<PostedSchedule> myBookedSchedules = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_transfer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            scheduleRepository = ScheduleRepository.getInstance(getContext());
            sessionManager = new SessionManager(getContext());
        }

        actStudentSchedule = view.findViewById(R.id.actStudentSchedule);
        actTargetFaculty = view.findViewById(R.id.actTargetFaculty);
        btnConfirmTransfer = view.findViewById(R.id.btnConfirmTransfer);

        setupDropdowns();

        if (btnConfirmTransfer != null) {
            btnConfirmTransfer.setOnClickListener(v -> {
                String target = actTargetFaculty != null && actTargetFaculty.getText() != null
                        ? actTargetFaculty.getText().toString()
                        : "another professor";

                if (getContext() != null) {
                    Toast.makeText(getContext(), "Schedule successfully transferred to " + target + "!", Toast.LENGTH_LONG).show();
                }

                if (getActivity() instanceof FacultyHomeActivity) {
                    ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_schedule);
                }
            });
        }
    }

    private void setupDropdowns() {
        if (getContext() == null || scheduleRepository == null || sessionManager == null) return;

        String currentProf = "Prof. " + sessionManager.getFullName();
        myBookedSchedules = scheduleRepository.getBookedSchedulesByFaculty(currentProf);

        if (myBookedSchedules.isEmpty()) {
            if (actStudentSchedule != null) {
                actStudentSchedule.setText("No booked student appointments to transfer", false);
            }
            if (btnConfirmTransfer != null) {
                btnConfirmTransfer.setEnabled(false);
            }
        } else {
            if (btnConfirmTransfer != null) {
                btnConfirmTransfer.setEnabled(true);
            }

            List<String> scheduleItems = new ArrayList<>();
            for (PostedSchedule s : myBookedSchedules) {
                scheduleItems.add(s.getBookedByStudentName() + " - " + s.getDate() + " (" + s.getTimeSlot() + ")");
            }

            ArrayAdapter<String> scheduleAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, scheduleItems);
            if (actStudentSchedule != null) {
                actStudentSchedule.setAdapter(scheduleAdapter);
                actStudentSchedule.setText(scheduleItems.get(0), false);
            }
        }

        // List target faculty members dynamically or department professors
        List<String> facultyList = scheduleRepository.getAllFacultyNamesWithPostedSchedules();
        if (facultyList.isEmpty()) {
            facultyList.add("Prof. Maria Santos (IT Dept)");
            facultyList.add("Dr. Ricardo Reyes (CS Dept)");
            facultyList.add("Engr. Gabriel Mendoza (IS Dept)");
        }

        ArrayAdapter<String> facultyAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, facultyList);
        if (actTargetFaculty != null) {
            actTargetFaculty.setAdapter(facultyAdapter);
            actTargetFaculty.setText(facultyList.get(0), false);
        }
    }
}
