package com.example.citecschedulingapp.fragment;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.AppTime;
import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.FacultyItem;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BookFragment extends Fragment {

    private TextInputEditText etSearchProfessor;
    private MaterialButtonToggleGroup toggleModeGroup;
    private MaterialCardView cardPostedForm;
    private MaterialCardView cardCustomForm;

    // Posted Form Views
    private AutoCompleteTextView actSelectProfessor;
    private AutoCompleteTextView actAvailableSlot;
    private TextInputEditText etBookingReason;
    private MaterialButton btnSubmitBooking;

    // Custom Form Views
    private AutoCompleteTextView actCustomProfessor;
    private TextInputEditText etCustomDate;
    private AutoCompleteTextView actCustomTimeSlot;
    private AutoCompleteTextView actCustomCategory;
    private TextInputEditText etCustomPurpose;
    private MaterialButton btnSubmitCustomRequest;

    private ScheduleRepository repository;
    private SessionManager sessionManager;

    private final Map<String, List<PostedSchedule>> slotsByProfessor = new LinkedHashMap<>();
    private final List<FacultyItem> facultyList = new ArrayList<>();
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

        etSearchProfessor = view.findViewById(R.id.etSearchProfessor);
        toggleModeGroup = view.findViewById(R.id.toggleModeGroup);
        cardPostedForm = view.findViewById(R.id.cardPostedForm);
        cardCustomForm = view.findViewById(R.id.cardCustomForm);

        actSelectProfessor = view.findViewById(R.id.actSelectProfessor);
        actAvailableSlot = view.findViewById(R.id.actAvailableSlot);
        etBookingReason = view.findViewById(R.id.etBookingReason);
        btnSubmitBooking = view.findViewById(R.id.btnSubmitBooking);

        actCustomProfessor = view.findViewById(R.id.actCustomProfessor);
        etCustomDate = view.findViewById(R.id.etCustomDate);
        actCustomTimeSlot = view.findViewById(R.id.actCustomTimeSlot);
        actCustomCategory = view.findViewById(R.id.actCustomCategory);
        etCustomPurpose = view.findViewById(R.id.etCustomPurpose);
        btnSubmitCustomRequest = view.findViewById(R.id.btnSubmitCustomRequest);

        setupToggleMode();
        setupSearchFilter();
        setupCustomForm();

        btnSubmitBooking.setOnClickListener(v -> attemptBooking());
        btnSubmitCustomRequest.setOnClickListener(v -> attemptCustomRequest());

        loadData();
    }

    private void setupToggleMode() {
        if (toggleModeGroup != null) {
            toggleModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.btnModeCustom) {
                        cardPostedForm.setVisibility(View.GONE);
                        cardCustomForm.setVisibility(View.VISIBLE);
                    } else {
                        cardPostedForm.setVisibility(View.VISIBLE);
                        cardCustomForm.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void setupSearchFilter() {
        if (etSearchProfessor != null) {
            etSearchProfessor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterProfessors(s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupCustomForm() {
        // Date Picker for Custom Request
        if (etCustomDate != null) {
            etCustomDate.setText(AppTime.todayIso());
            etCustomDate.setOnClickListener(v -> showDatePicker());
        }

        // Time Slot Options
        String[] timeSlots = new String[]{
                "09:00 AM - 10:00 AM",
                "10:00 AM - 11:00 AM",
                "11:00 AM - 12:00 PM",
                "01:00 PM - 02:00 PM",
                "02:00 PM - 03:00 PM",
                "03:00 PM - 04:00 PM",
                "04:00 PM - 05:00 PM"
        };
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, timeSlots);
        if (actCustomTimeSlot != null) {
            actCustomTimeSlot.setAdapter(timeAdapter);
            actCustomTimeSlot.setText(timeSlots[1], false);
        }

        // Category Options
        String[] categories = new String[]{
                "Thesis Advising & Consultation",
                "Special Project Review",
                "Grade Query & Subject Consultation",
                "Academic Counseling",
                "General Faculty Consultation"
        };
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
        if (actCustomCategory != null) {
            actCustomCategory.setAdapter(catAdapter);
            actCustomCategory.setText(categories[0], false);
        }
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(),
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String selectedDate = String.format("%04d-%02d-%02d", year1, monthOfYear + 1, dayOfMonth);
                    if (etCustomDate != null) etCustomDate.setText(selectedDate);
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    private void loadData() {
        loadFacultyList();
        loadSlots();
    }

    private void loadFacultyList() {
        repository.loadFaculty(new ScheduleRepository.ResultCallback<List<FacultyItem>>() {
            @Override
            public void onSuccess(List<FacultyItem> data, String message) {
                if (!isAdded()) return;
                facultyList.clear();
                if (data != null) facultyList.addAll(data);
                populateCustomProfessors(facultyList);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

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
                filterProfessors(etSearchProfessor != null ? etSearchProfessor.getText().toString().trim() : "");
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                actSelectProfessor.setText("Couldn't load schedules", false);
                btnSubmitBooking.setText("TRY AGAIN");
                btnSubmitBooking.setEnabled(true);
                btnSubmitBooking.setOnClickListener(v -> loadSlots());
            }
        });
    }

    private void filterProfessors(String query) {
        // Filter Posted Slots Professor Dropdown
        List<String> matchingPostedNames = new ArrayList<>();
        for (String name : slotsByProfessor.keySet()) {
            if (TextUtils.isEmpty(query) || name.toLowerCase().contains(query.toLowerCase())) {
                matchingPostedNames.add(name);
            }
        }

        if (matchingPostedNames.isEmpty()) {
            actSelectProfessor.setAdapter(null);
            actSelectProfessor.setText("No matching professors found", false);
            actAvailableSlot.setAdapter(null);
            actAvailableSlot.setText("No slots", false);
            selectedSlot = null;
            btnSubmitBooking.setEnabled(false);
        } else {
            actSelectProfessor.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, matchingPostedNames));
            actSelectProfessor.setText(matchingPostedNames.get(0), false);
            actSelectProfessor.setOnItemClickListener((parent, v, position, id) ->
                    showSlotsFor((String) parent.getItemAtPosition(position)));
            showSlotsFor(matchingPostedNames.get(0));
        }

        // Filter Custom Request Professor Dropdown
        List<FacultyItem> matchingFaculty = new ArrayList<>();
        for (FacultyItem f : facultyList) {
            if (TextUtils.isEmpty(query) || f.getDisplayName().toLowerCase().contains(query.toLowerCase())) {
                matchingFaculty.add(f);
            }
        }
        populateCustomProfessors(matchingFaculty);
    }

    private void populateCustomProfessors(List<FacultyItem> list) {
        if (actCustomProfessor == null || !isAdded()) return;

        if (list.isEmpty()) {
            actCustomProfessor.setAdapter(null);
            actCustomProfessor.setText("No faculty members found", false);
            btnSubmitCustomRequest.setEnabled(false);
            return;
        }

        List<String> displayNames = new ArrayList<>();
        for (FacultyItem f : list) {
            displayNames.add(f.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayNames);
        actCustomProfessor.setAdapter(adapter);
        actCustomProfessor.setText(displayNames.get(0), false);
        btnSubmitCustomRequest.setEnabled(true);
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
                        loadSlots();
                    }
                });
    }

    private void attemptCustomRequest() {
        String customProfName = actCustomProfessor.getText() != null ? actCustomProfessor.getText().toString().trim() : "";
        String requestedDate = etCustomDate.getText() != null ? etCustomDate.getText().toString().trim() : "";
        String requestedTime = actCustomTimeSlot.getText() != null ? actCustomTimeSlot.getText().toString().trim() : "";
        String category = actCustomCategory.getText() != null ? actCustomCategory.getText().toString().trim() : "";
        String purpose = etCustomPurpose.getText() != null ? etCustomPurpose.getText().toString().trim() : "";

        if (TextUtils.isEmpty(customProfName) || facultyList.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a target professor.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(requestedDate)) {
            etCustomDate.setError("Please select a date.");
            return;
        }

        if (TextUtils.isEmpty(purpose)) {
            etCustomPurpose.setError("Please state the purpose of your appointment request.");
            return;
        }

        long startMs = AppTime.slotStartMillis(requestedDate, requestedTime);
        if (startMs > 0 && startMs <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(),
                    "That time has already passed. Pick a later time slot or another date.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Find target faculty ID
        int targetFacultyId = facultyList.get(0).getFacultyId();
        for (FacultyItem f : facultyList) {
            if (f.getDisplayName().equalsIgnoreCase(customProfName)) {
                targetFacultyId = f.getFacultyId();
                break;
            }
        }

        btnSubmitCustomRequest.setEnabled(false);
        btnSubmitCustomRequest.setText("SENDING REQUEST…");

        repository.requestCustomAppointment(
                sessionManager.getUserId(),
                targetFacultyId,
                requestedDate,
                requestedTime,
                category,
                "Faculty Consultation Room",
                purpose,
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
                        btnSubmitCustomRequest.setEnabled(true);
                        btnSubmitCustomRequest.setText("SEND APPOINTMENT REQUEST");
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}
