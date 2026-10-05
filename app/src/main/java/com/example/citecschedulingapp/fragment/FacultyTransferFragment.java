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
import com.example.citecschedulingapp.model.FacultyItem;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class FacultyTransferFragment extends Fragment {

    private AutoCompleteTextView actStudentSchedule;
    private AutoCompleteTextView actTargetFaculty;
    private TextInputEditText etTransferReason;
    private MaterialButton btnConfirmTransfer;

    private ScheduleRepository repository;
    private SessionManager sessionManager;

    private final List<PostedSchedule> transferable = new ArrayList<>();
    private final List<FacultyItem> targets = new ArrayList<>();
    private PostedSchedule selectedSchedule = null;
    private FacultyItem selectedTarget = null;
    private int preselectId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_transfer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ScheduleRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        actStudentSchedule = view.findViewById(R.id.actStudentSchedule);
        actTargetFaculty = view.findViewById(R.id.actTargetFaculty);
        etTransferReason = view.findViewById(R.id.etTransferReason);
        btnConfirmTransfer = view.findViewById(R.id.btnConfirmTransfer);

        if (getActivity() instanceof FacultyHomeActivity) {
            preselectId = ((FacultyHomeActivity) getActivity()).consumePendingTransferId();
        }

        btnConfirmTransfer.setEnabled(false);
        btnConfirmTransfer.setOnClickListener(v -> attemptTransfer());

        loadAppointments();
        loadFaculty();
    }

    private void loadAppointments() {
        actStudentSchedule.setText("Loading…", false);
        repository.loadFacultySlots(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> data, String message) {
                        if (!isAdded()) return;
                        transferable.clear();
                        if (data != null) {
                            for (PostedSchedule s : data) if (s.isBooked() && !s.hasEnded()) transferable.add(s);
                        }
                        showAppointments();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        actStudentSchedule.setText("Couldn't load appointments", false);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showAppointments() {
        if (transferable.isEmpty()) {
            actStudentSchedule.setAdapter(null);
            actStudentSchedule.setText("No booked student appointments to transfer", false);
            selectedSchedule = null;
            updateButton();
            return;
        }

        List<String> labels = new ArrayList<>();
        int startIndex = 0;
        for (int i = 0; i < transferable.size(); i++) {
            PostedSchedule s = transferable.get(i);
            labels.add(s.getStudentName() + " - " + s.getDisplayDate() + " (" + s.getTimeSlot() + ")");
            if (s.getId() == preselectId) startIndex = i;
        }
        actStudentSchedule.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels));
        actStudentSchedule.setText(labels.get(startIndex), false);
        selectedSchedule = transferable.get(startIndex);
        actStudentSchedule.setOnItemClickListener((parent, v, position, id) -> selectedSchedule = transferable.get(position));
        updateButton();
    }

    private void loadFaculty() {
        actTargetFaculty.setText("Loading…", false);
        repository.loadFaculty(new ScheduleRepository.ResultCallback<List<FacultyItem>>() {
            @Override
            public void onSuccess(List<FacultyItem> data, String message) {
                if (!isAdded()) return;
                targets.clear();
                if (data != null) {
                    for (FacultyItem f : data) {
                        if (f.getFacultyId() != sessionManager.getUserId()) targets.add(f); // not yourself
                    }
                }
                if (targets.isEmpty()) {
                    actTargetFaculty.setAdapter(null);
                    actTargetFaculty.setText("No other professors registered yet", false);
                    selectedTarget = null;
                } else {
                    actTargetFaculty.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_dropdown_item_1line, targets));
                    actTargetFaculty.setText(targets.get(0).getDisplayName(), false);
                    selectedTarget = targets.get(0);
                    actTargetFaculty.setOnItemClickListener((parent, v, position, id) -> selectedTarget = targets.get(position));
                }
                updateButton();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                actTargetFaculty.setText("Couldn't load professors", false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateButton() {
        btnConfirmTransfer.setEnabled(selectedSchedule != null && selectedTarget != null);
    }

    private void attemptTransfer() {
        if (selectedSchedule == null || selectedTarget == null) return;
        String reason = etTransferReason.getText() != null ? etTransferReason.getText().toString().trim() : "";

        btnConfirmTransfer.setEnabled(false);
        repository.transferAppointment(selectedSchedule.getId(), sessionManager.getUserId(),
                selectedTarget.getFacultyId(), reason, new ScheduleRepository.ResultCallback<Object>() {
                    @Override
                    public void onSuccess(Object data, String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        if (getActivity() instanceof FacultyHomeActivity) {
                            ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_schedule);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        btnConfirmTransfer.setEnabled(true);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
