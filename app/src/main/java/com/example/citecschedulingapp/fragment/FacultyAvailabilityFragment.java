package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.AppTime;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.UiUtil;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class FacultyAvailabilityFragment extends Fragment {

    private TextInputLayout tilPostDate;
    private TextInputEditText etPostDate;
    private AutoCompleteTextView actPostTimeSlot;
    private AutoCompleteTextView actPostCategory;
    private TextInputEditText etPostLocation;
    private MaterialButton btnPostSchedule;
    private TextView tvNoPostedSchedules;
    private LinearLayout containerPostedSchedules;

    private SessionManager sessionManager;
    private ScheduleRepository repository;

    /** yyyy-MM-dd of the date chosen in the calendar (what the server stores). */
    private String selectedIsoDate = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_availability, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        repository = ScheduleRepository.getInstance(requireContext());

        tilPostDate = view.findViewById(R.id.tilPostDate);
        etPostDate = view.findViewById(R.id.etPostDate);
        actPostTimeSlot = view.findViewById(R.id.actPostTimeSlot);
        actPostCategory = view.findViewById(R.id.actPostCategory);
        etPostLocation = view.findViewById(R.id.etPostLocation);
        btnPostSchedule = view.findViewById(R.id.btnPostSchedule);
        tvNoPostedSchedules = view.findViewById(R.id.tvNoPostedSchedules);
        containerPostedSchedules = view.findViewById(R.id.containerPostedSchedules);

        setupDropdowns();
        etPostDate.setOnClickListener(v -> showDatePicker());
        tilPostDate.setEndIconOnClickListener(v -> showDatePicker());
        btnPostSchedule.setOnClickListener(v -> attemptPostSchedule());
        loadPostedSchedules();
    }

    private void setupDropdowns() {
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
        actPostTimeSlot.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, timeSlots));
        actPostTimeSlot.setText(timeSlots[2], false);
        actPostCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories));
        actPostCategory.setText(categories[0], false);
    }

    /** Calendar popup: past dates are disabled, today is preselected. */
    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select consultation date")
                .setCalendarConstraints(constraints)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            // MaterialDatePicker returns midnight UTC, so format in UTC to avoid an off-by-one day.
            SimpleDateFormat pretty = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            pretty.setTimeZone(TimeZone.getTimeZone("UTC"));
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = new Date(selection);
            selectedIsoDate = iso.format(d);
            etPostDate.setText(pretty.format(d));
            etPostDate.setError(null);
        });

        picker.show(getChildFragmentManager(), "POST_DATE_PICKER");
    }

    private void attemptPostSchedule() {
        String timeSlot = actPostTimeSlot.getText() != null ? actPostTimeSlot.getText().toString().trim() : "";
        String category = actPostCategory.getText() != null ? actPostCategory.getText().toString().trim() : "";
        String location = etPostLocation.getText() != null ? etPostLocation.getText().toString().trim() : "";

        if (selectedIsoDate == null) {
            etPostDate.setError("Please choose a date.");
            return;
        }
        if (TextUtils.isEmpty(location)) {
            etPostLocation.setError("Please enter office location.");
            return;
        }

        btnPostSchedule.setEnabled(false);
        repository.postSlot(sessionManager.getUserId(), selectedIsoDate, timeSlot, category, location,
                new ScheduleRepository.ResultCallback<PostedSchedule>() {
                    @Override
                    public void onSuccess(PostedSchedule data, String message) {
                        if (!isAdded()) return;
                        btnPostSchedule.setEnabled(true);
                        Toast.makeText(requireContext(), "New consultation schedule posted successfully!", Toast.LENGTH_LONG).show();
                        loadPostedSchedules();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        btnPostSchedule.setEnabled(true);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadPostedSchedules() {
        repository.loadFacultySlots(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> data, String message) {
                        if (!isAdded()) return;
                        containerPostedSchedules.removeAllViews();
                        List<PostedSchedule> list = data != null ? data : new ArrayList<PostedSchedule>();
                        if (list.isEmpty()) {
                            tvNoPostedSchedules.setText("No consultation schedules posted yet. Use the form above to post available slots for students.");
                            tvNoPostedSchedules.setVisibility(View.VISIBLE);
                            return;
                        }
                        tvNoPostedSchedules.setVisibility(View.GONE);
                        for (PostedSchedule s : list) containerPostedSchedules.addView(buildCard(s));
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        tvNoPostedSchedules.setText(message);
                        tvNoPostedSchedules.setVisibility(View.VISIBLE);
                    }
                });
    }

    private View buildCard(final PostedSchedule s) {
        android.content.Context c = requireContext();
        MaterialCardView card = UiUtil.card(c);
        LinearLayout col = UiUtil.column(c);

        TextView badge;
        if (s.isCancelled()) {
            badge = UiUtil.badge(c, "CANCELLED", "#FEE2E2", "#991B1B");
        } else if (s.isBooked()) {
            badge = UiUtil.badge(c, "BOOKED", "#DBEAFE", "#1E40AF");
        } else if (s.hasEnded()) {
            badge = UiUtil.badge(c, "EXPIRED", "#E5E7EB", "#374151");
        } else {
            badge = UiUtil.badge(c, "OPEN", "#DCFCE7", "#166534");
        }
        col.addView(UiUtil.headerRow(c, s.getCategory(), badge));
        col.addView(UiUtil.text(c, s.getDisplayDate() + " • " + s.getTimeSlot(), 14f, R.color.text_primary, false));
        col.addView(UiUtil.text(c, s.isBooked()
                        ? (s.isCancelled() ? "Cancelled — was booked by: " : "Booked by: ") + s.getStudentName()
                        : "Location: " + s.getLocation(),
                13f, s.isBooked() ? R.color.accent : R.color.text_secondary, false));

        if (!s.isBooked()) {
            LinearLayout row = UiUtil.buttonRow(c);
            MaterialButton remove = UiUtil.outlinedButton(c, "Remove slot", android.graphics.Color.parseColor("#DC2626"));
            UiUtil.addToButtonRow(c, row, remove, true);
            remove.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle("Remove this slot?")
                    .setMessage(s.getDisplayDate() + " • " + s.getTimeSlot())
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Remove", (d, w) -> removeSlot(s))
                    .show());
            col.addView(row);
        }

        card.addView(col);
        return card;
    }

    private void removeSlot(PostedSchedule s) {
        repository.deleteSlot(s.getId(), sessionManager.getUserId(), new ScheduleRepository.ResultCallback<Object>() {
            @Override
            public void onSuccess(Object data, String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                loadPostedSchedules();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                loadPostedSchedules();
            }
        });
    }
}
