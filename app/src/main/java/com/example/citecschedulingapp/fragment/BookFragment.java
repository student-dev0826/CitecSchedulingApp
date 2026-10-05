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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookFragment extends Fragment {

    private AutoCompleteTextView actSelectProfessor;
    private AutoCompleteTextView actAvailableSlot;
    private TextInputEditText etBookingReason;
    private MaterialButton btnSubmitBooking;

    private ScheduleRepository repository;
    private SessionManager sessionManager;

    private final Map<String, List<PostedSchedule>> slotsByProfessor = new LinkedHashMap<>();
    private List<PostedSchedule> currentSlots = new ArrayList<>();
    private PostedSchedule selectedSlot = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ScheduleRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        actSelectProfessor = view.findViewById(R.id.actSelectProfessor);
        actAvailableSlot = view.findViewById(R.id.actAvailableSlot);
        etBookingReason = view.findViewById(R.id.etBookingReason);
        btnSubmitBooking = view.findViewById(R.id.btnSubmitBooking);

        btnSubmitBooking.setOnClickListener(v -> attemptBooking());
        loadSlots();
    }

    /** Pulls the latest open slots from the server. */
    private void loadSlots() {
        btnSubmitBooking.setEnabled(false);
        btnSubmitBooking.setText("LOADING SCHEDULES…");
        actSelectProfessor.setText("Loading…", false);
        actAvailableSlot.setText("", false);

        repository.loadOpenSlots(new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
            @Override
            public void onSuccess(List<PostedSchedule> data, String message) {
                if (!isAdded()) return;
                slotsByProfessor.clear();
                if (data != null) {
                    for (PostedSchedule s : data) {
                        String key = s.getFacultyDisplayName();
                        List<PostedSchedule> list = slotsByProfessor.get(key);
                        if (list == null) {
                            list = new ArrayList<>();
                            slotsByProfessor.put(key, list);
                        }
                        list.add(s);
                    }
                }
                showProfessors();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                actSelectProfessor.setText("Couldn't load schedules", false);
                btnSubmitBooking.setText("TRY AGAIN");
                btnSubmitBooking.setEnabled(true);
                btnSubmitBooking.setOnClickListener(v -> {
                    btnSubmitBooking.setOnClickListener(x -> attemptBooking());
                    loadSlots();
                });
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showProfessors() {
        btnSubmitBooking.setOnClickListener(v -> attemptBooking());

        if (slotsByProfessor.isEmpty()) {
            actSelectProfessor.setAdapter(null);
            actSelectProfessor.setText("No professor schedules posted yet", false);
            actAvailableSlot.setAdapter(null);
            actAvailableSlot.setText("No available schedules", false);
            selectedSlot = null;
            btnSubmitBooking.setEnabled(false);
            btnSubmitBooking.setText("NO SCHEDULES AVAILABLE TO BOOK");
            return;
        }

        List<String> names = new ArrayList<>(slotsByProfessor.keySet());
        actSelectProfessor.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, names));
        actSelectProfessor.setText(names.get(0), false);
        actSelectProfessor.setOnItemClickListener((parent, v, position, id) ->
                showSlotsFor((String) parent.getItemAtPosition(position)));

        showSlotsFor(names.get(0));
    }

    private void showSlotsFor(String professor) {
        currentSlots = slotsByProfessor.get(professor);
        if (currentSlots == null || currentSlots.isEmpty()) {
            actAvailableSlot.setAdapter(null);
            actAvailableSlot.setText("No active slots for this professor", false);
            selectedSlot = null;
            btnSubmitBooking.setEnabled(false);
            btnSubmitBooking.setText("NO SLOTS AVAILABLE");
            return;
        }

        List<String> labels = new ArrayList<>();
        for (PostedSchedule s : currentSlots) {
            labels.add(s.getDisplayDate() + " • " + s.getTimeSlot() + " | "
                    + s.getCategory() + " (" + s.getLocation() + ")");
        }
        actAvailableSlot.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels));
        actAvailableSlot.setText(labels.get(0), false);
        selectedSlot = currentSlots.get(0);
        actAvailableSlot.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < currentSlots.size()) selectedSlot = currentSlots.get(position);
        });

        btnSubmitBooking.setEnabled(true);
        btnSubmitBooking.setText("BOOK SELECTED SCHEDULE");
    }

    private void attemptBooking() {
        if (selectedSlot == null) {
            Toast.makeText(requireContext(), "Please select an available schedule slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        String purpose = etBookingReason.getText() != null ? etBookingReason.getText().toString().trim() : "";
        if (TextUtils.isEmpty(purpose)) {
            etBookingReason.setError("Please enter the purpose of your appointment.");
            return;
        }

        final PostedSchedule slot = selectedSlot;
        btnSubmitBooking.setEnabled(false);
        btnSubmitBooking.setText("BOOKING…");

        repository.bookSlot(slot.getId(), sessionManager.getUserId(), purpose,
                new ScheduleRepository.ResultCallback<PostedSchedule>() {
                    @Override
                    public void onSuccess(PostedSchedule data, String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        if (getActivity() instanceof HomeActivity) {
                            ((HomeActivity) getActivity()).selectTab(R.id.nav_appointments);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        // The slot may have been taken meanwhile, so refresh the list.
                        loadSlots();
                    }
                });
    }
}
