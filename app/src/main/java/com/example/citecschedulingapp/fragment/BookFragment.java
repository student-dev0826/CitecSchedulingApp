package com.example.citecschedulingapp.fragment;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.AppTime;
import com.example.citecschedulingapp.BookingCalendar;
import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.UiUtil;
import com.example.citecschedulingapp.model.FacultyItem;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class BookFragment extends Fragment {

    private TextInputEditText etSearchProfessor;
    private MaterialButtonToggleGroup toggleModeGroup;
    private MaterialCardView cardPostedForm;
    private MaterialCardView cardCustomForm;

    // Posted Form Views
    private GridLayout calendarGrid;
    private TextView monthLabel, monthCount, calendarStatus, dateLabel, selectedSummary;
    private LinearLayout daySlots, selectedForm;
    private MaterialButton refreshSlots;
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

    private final List<PostedSchedule> openSlots = new ArrayList<>();
    private Map<String, List<PostedSchedule>> slotsByDate = new java.util.TreeMap<>();
    private final Calendar bookingMonth = Calendar.getInstance(AppTime.ZONE);
    private final List<FacultyItem> facultyList = new ArrayList<>();
    private PostedSchedule selectedSlot = null;
    private String selectedDate;
    private int restoredSlotId, slotsRequest, viewGeneration;
    private boolean loadingSlots, bookingInProgress, hasResumed;
    private String slotsError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (root, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
            root.setPadding(root.getPaddingLeft(), top, root.getPaddingRight(), root.getPaddingBottom());
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(view);

        repository = ScheduleRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());
        viewGeneration++;
        bookingMonth.set(Calendar.DAY_OF_MONTH, 1);
        if (savedInstanceState != null) {
            bookingMonth.setTimeInMillis(savedInstanceState.getLong("bookingMonth", bookingMonth.getTimeInMillis()));
            selectedDate = savedInstanceState.getString("bookingDate");
            restoredSlotId = savedInstanceState.getInt("bookingSlot", 0);
        }

        etSearchProfessor = view.findViewById(R.id.etSearchProfessor);
        toggleModeGroup = view.findViewById(R.id.toggleModeGroup);
        cardPostedForm = view.findViewById(R.id.cardPostedForm);
        cardCustomForm = view.findViewById(R.id.cardCustomForm);

        calendarGrid = view.findViewById(R.id.gridBookingCalendar);
        monthLabel = view.findViewById(R.id.tvBookingMonth);
        monthCount = view.findViewById(R.id.tvBookingMonthCount);
        calendarStatus = view.findViewById(R.id.tvBookingCalendarStatus);
        dateLabel = view.findViewById(R.id.tvBookingSelectedDate);
        selectedSummary = view.findViewById(R.id.tvSelectedBookingSlot);
        daySlots = view.findViewById(R.id.containerBookingDaySlots);
        selectedForm = view.findViewById(R.id.containerSelectedBooking);
        refreshSlots = view.findViewById(R.id.btnRefreshBookingSlots);
        view.findViewById(R.id.btnBookingPreviousMonth).setOnClickListener(v -> changeMonth(-1));
        view.findViewById(R.id.btnBookingNextMonth).setOnClickListener(v -> changeMonth(1));
        refreshSlots.setOnClickListener(v -> loadSlots());
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

    @Override public void onResume() {
        super.onResume();
        if (hasResumed && !bookingInProgress && !loadingSlots) loadSlots();
        hasResumed = true;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("bookingMonth", bookingMonth.getTimeInMillis());
        out.putString("bookingDate", selectedDate);
        out.putInt("bookingSlot", selectedSlot == null ? restoredSlotId : selectedSlot.getId());
    }

    @Override public void onDestroyView() {
        viewGeneration++; slotsRequest++; hasResumed = false; bookingInProgress = false;
        super.onDestroyView();
    }

    private boolean currentView(int generation) {
        return isAdded() && getView() != null && viewGeneration == generation;
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
        final int generation = viewGeneration;
        repository.loadFaculty(new ScheduleRepository.ResultCallback<List<FacultyItem>>() {
            @Override
            public void onSuccess(List<FacultyItem> data, String message) {
                if (!currentView(generation)) return;
                facultyList.clear();
                if (data != null) facultyList.addAll(data);
                filterProfessors(etSearchProfessor.getText() == null ? "" : etSearchProfessor.getText().toString());
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void loadSlots() {
        final int request = ++slotsRequest, generation = viewGeneration;
        loadingSlots = true; slotsError = null;
        renderCalendar();

        repository.loadOpenSlots(new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
            @Override
            public void onSuccess(List<PostedSchedule> data, String message) {
                if (!currentView(generation) || request != slotsRequest) return;
                loadingSlots = false;
                openSlots.clear();
                if (data != null) openSlots.addAll(data);
                filterProfessors(etSearchProfessor != null ? etSearchProfessor.getText().toString().trim() : "");
            }

            @Override
            public void onError(String message) {
                if (!currentView(generation) || request != slotsRequest) return;
                loadingSlots = false; slotsError = message;
                openSlots.clear(); renderCalendar();
            }
        });
    }

    private void filterProfessors(String query) {
        renderCalendar();

        // Filter Custom Request Professor Dropdown
        List<FacultyItem> matchingFaculty = new ArrayList<>();
        for (FacultyItem f : facultyList) {
            if (TextUtils.isEmpty(query) || f.getDisplayName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
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
        String previous = actCustomProfessor.getText().toString();
        actCustomProfessor.setAdapter(adapter);
        actCustomProfessor.setText(displayNames.contains(previous) ? previous : displayNames.get(0), false);
        btnSubmitCustomRequest.setEnabled(true);
    }

    private String monthIso() { return AppTime.fixed("yyyy-MM").format(bookingMonth.getTime()); }

    private void changeMonth(int delta) {
        if (bookingInProgress) return;
        bookingMonth.add(Calendar.MONTH, delta);
        selectedDate = null; selectedSlot = null; restoredSlotId = 0;
        renderCalendar();
    }

    private void renderCalendar() {
        if (!isAdded() || calendarGrid == null) return;
        String query = etSearchProfessor.getText() == null ? "" : etSearchProfessor.getText().toString();
        slotsByDate = BookingCalendar.availableByDate(openSlots, query, System.currentTimeMillis());
        int selectedId = selectedSlot == null ? restoredSlotId : selectedSlot.getId();
        selectedSlot = null;
        List<PostedSchedule> selectedDay = selectedDate == null ? null : slotsByDate.get(selectedDate);
        if (selectedDay != null) for (PostedSchedule slot : selectedDay) if (slot.getId() == selectedId) selectedSlot = slot;
        if (!loadingSlots) restoredSlotId = 0;
        monthLabel.setText(AppTime.display("MMMM yyyy").format(bookingMonth.getTime()));
        String month = monthIso();
        int count = 0;
        for (Map.Entry<String, List<PostedSchedule>> day : slotsByDate.entrySet()) if (day.getKey().startsWith(month + "-")) count += day.getValue().size();
        monthCount.setText(count + (count == 1 ? " available slot this month" : " available slots this month"));
        calendarStatus.setText(loadingSlots ? "Updating availability…" : slotsError != null ? slotsError
                : count == 0 ? "No open slots in this month" + (query.trim().isEmpty() ? ". Try another month or request a custom time." : " matching this professor or department.")
                : "All times are Philippine time. Availability is confirmed when you submit.");
        refreshSlots.setEnabled(!loadingSlots && !bookingInProgress);
        etSearchProfessor.setEnabled(!bookingInProgress);
        if (getView() != null) {
            getView().findViewById(R.id.btnModeCustom).setEnabled(!bookingInProgress);
            getView().findViewById(R.id.btnModePosted).setEnabled(!bookingInProgress);
        }
        calendarGrid.removeAllViews();
        for (String day : new String[]{"M", "T", "W", "T", "F", "S", "S"}) addDateCell(day, null, 0, true);
        int offset = (bookingMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int i = 0; i < offset; i++) addDateCell("", null, 0, false);
        for (int day = 1; day <= bookingMonth.getActualMaximum(Calendar.DAY_OF_MONTH); day++) {
            String date = String.format(Locale.US, "%s-%02d", month, day);
            List<PostedSchedule> available = slotsByDate.get(date);
            addDateCell(String.valueOf(day), date, available == null ? 0 : available.size(), false);
        }
        renderDay();
    }

    private void addDateCell(String label, String date, int count, boolean header) {
        TextView cell = new TextView(requireContext());
        boolean selected = date != null && date.equals(selectedDate);
        cell.setText(label + (count > 0 ? "\n" + count + (count == 1 ? " slot" : " slots") : ""));
        cell.setGravity(Gravity.CENTER); cell.setTextSize(header ? 12 : 11);
        cell.setTypeface(null, count > 0 ? Typeface.BOLD : Typeface.NORMAL);
        cell.setTextColor(selected ? Color.WHITE : getResources().getColor(count > 0 ? R.color.primary : R.color.text_secondary, null));
        GradientDrawable background = new GradientDrawable();
        background.setColor(getResources().getColor(selected ? R.color.primary : count > 0 ? R.color.brand_yellow : R.color.white, null));
        background.setCornerRadius(UiUtil.dp(requireContext(), 6));
        cell.setBackground(background);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; params.height = UiUtil.dp(requireContext(), header ? 28 : 56);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(2, 2, 2, 2); cell.setLayoutParams(params);
        if (date != null) {
            cell.setContentDescription(date + ", " + count + (count == 1 ? " available slot" : " available slots"));
            cell.setSelected(selected);
            cell.setEnabled(count > 0 && !loadingSlots && !bookingInProgress);
            if (count > 0) cell.setOnClickListener(v -> {
                selectedDate = date; selectedSlot = null; restoredSlotId = 0;
                etSearchProfessor.clearFocus();
                android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(cell.getWindowToken(), 0);
                renderCalendar();
                dateLabel.announceForAccessibility(dateLabel.getText());
                final int generation = viewGeneration;
                dateLabel.post(() -> {
                    if (!currentView(generation)) return;
                    android.graphics.Rect area = new android.graphics.Rect(0, 0, dateLabel.getWidth(), dateLabel.getHeight() + UiUtil.dp(requireContext(), 220));
                    dateLabel.requestRectangleOnScreen(area, false);
                });
            });
        }
        calendarGrid.addView(cell);
    }

    private void renderDay() {
        // Keep the request form beside the chosen slot, even when the date has many slots.
        if (selectedForm.getParent() instanceof ViewGroup) ((ViewGroup) selectedForm.getParent()).removeView(selectedForm);
        daySlots.removeAllViews();
        List<PostedSchedule> available = selectedDate == null ? null : slotsByDate.get(selectedDate);
        dateLabel.setText(selectedDate == null ? "Choose a date to view its slots" : AppTime.prettyDate(selectedDate)
                + " · " + (available == null ? 0 : available.size()) + " available");
        if (selectedDate != null && (available == null || available.isEmpty())) {
            daySlots.addView(UiUtil.text(requireContext(), "No open slots on this date match your search. Refresh or choose another highlighted date.", 14, R.color.text_secondary, false));
        }
        if (available != null) for (PostedSchedule slot : available) {
            MaterialCardView card = UiUtil.card(requireContext());
            boolean selected = selectedSlot != null && selectedSlot.getId() == slot.getId();
            card.setStrokeWidth(UiUtil.dp(requireContext(), selected ? 2 : 1));
            card.setStrokeColor(getResources().getColor(selected ? R.color.accent : R.color.border_color, null));
            LinearLayout col = UiUtil.column(requireContext());
            col.addView(UiUtil.text(requireContext(), slot.getTimeSlot(), 17, R.color.primary, true));
            col.addView(UiUtil.text(requireContext(), slot.getFacultyDisplayName(), 15, R.color.text_primary, true));
            col.addView(UiUtil.text(requireContext(), slot.getCategory(), 14, R.color.text_secondary, false));
            col.addView(UiUtil.text(requireContext(), "Location: " + slot.getLocation(), 13, R.color.text_secondary, false));
            MaterialButton choose = UiUtil.outlinedButton(requireContext(), selected ? "Selected" : "Select this slot", getResources().getColor(R.color.accent, null));
            choose.setAllCaps(false); choose.setCheckable(true); choose.setChecked(selected);
            choose.setContentDescription("Select booking slot " + slot.getId());
            choose.setEnabled(!loadingSlots && !bookingInProgress);
            choose.setOnClickListener(v -> { selectedSlot = slot; renderDay(); selectedSummary.announceForAccessibility(selectedSummary.getText()); });
            col.addView(choose);
            if (selected) col.addView(selectedForm);
            card.addView(col); daySlots.addView(card);
        }
        if (selectedSlot == null) daySlots.addView(selectedForm);
        selectedForm.setVisibility(selectedSlot == null ? View.GONE : View.VISIBLE);
        if (selectedSlot != null) selectedSummary.setText("Selected: " + selectedSlot.getDisplayDate() + "\n"
                + selectedSlot.getTimeSlot() + "\n" + selectedSlot.getFacultyDisplayName() + " · " + selectedSlot.getCategory());
        btnSubmitBooking.setEnabled(selectedSlot != null && !loadingSlots && !bookingInProgress);
        btnSubmitBooking.setText(bookingInProgress ? "Sending request…" : "Request selected slot");
    }

    private void attemptBooking() {
        if (loadingSlots || bookingInProgress) return;
        if (selectedSlot == null) {
            Toast.makeText(requireContext(), "Please select an available schedule slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedSlot.getStartMillis() <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "That time has already passed. Please choose another slot.", Toast.LENGTH_LONG).show();
            selectedSlot = null; loadSlots(); return;
        }
        String purpose = etBookingReason.getText() != null ? etBookingReason.getText().toString().trim() : "";
        if (TextUtils.isEmpty(purpose)) {
            etBookingReason.setError("Please enter the purpose of your appointment.");
            return;
        }

        final PostedSchedule slot = selectedSlot;
        final int generation = viewGeneration;
        bookingInProgress = true; renderCalendar();

        repository.bookSlot(slot.getId(), sessionManager.getUserId(), purpose,
                new ScheduleRepository.ResultCallback<PostedSchedule>() {
                    @Override
                    public void onSuccess(PostedSchedule data, String message) {
                        if (!currentView(generation)) return;
                        bookingInProgress = false;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        if (getActivity() instanceof HomeActivity) {
                            ((HomeActivity) getActivity()).selectTab(R.id.nav_appointments);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!currentView(generation)) return;
                        bookingInProgress = false;
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
