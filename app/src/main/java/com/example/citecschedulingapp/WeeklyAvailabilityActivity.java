package com.example.citecschedulingapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.PostedSchedule;
import com.example.citecschedulingapp.model.RecurringAvailability;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Professor-owned calendar and recurring rules; the server is authoritative for conflicts. */
public class WeeklyAvailabilityActivity extends AppCompatActivity {
    private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private final Calendar month = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
    private final List<PostedSchedule> slots = new ArrayList<>();
    private ScheduleRepository repository;
    private GridLayout grid;
    private LinearLayout rules, daySlots;
    private TextView monthLabel, dateLabel, status;
    private String selectedDate;
    private int calendarRequest;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn() || !session.isFaculty()) { finish(); return; }
        setContentView(R.layout.activity_weekly_availability);
        UiUtil.applyWindowInsets(this);
        repository = ScheduleRepository.getInstance(this);
        grid = findViewById(R.id.gridAvailabilityMonth);
        rules = findViewById(R.id.containerRecurringRules);
        daySlots = findViewById(R.id.containerCalendarSlots);
        monthLabel = findViewById(R.id.tvAvailabilityMonth);
        dateLabel = findViewById(R.id.tvAvailabilityDate);
        status = findViewById(R.id.tvWeeklyStatus);
        selectedDate = format(month, "yyyy-MM-dd");
        month.set(Calendar.DAY_OF_MONTH, 1);
        if (savedInstanceState != null) {
            month.setTimeInMillis(savedInstanceState.getLong("month", month.getTimeInMillis()));
            selectedDate = savedInstanceState.getString("selectedDate", selectedDate);
        }
        findViewById(R.id.btnWeeklyBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPreviousMonth).setOnClickListener(v -> moveMonth(-1));
        findViewById(R.id.btnNextMonth).setOnClickListener(v -> moveMonth(1));
        findViewById(R.id.btnAddWeekly).setOnClickListener(v -> editRule(null));
        refresh();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("month", month.getTimeInMillis()); out.putString("selectedDate", selectedDate);
    }

    private String format(Calendar value, String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Manila")); return f.format(value.getTime());
    }

    private void moveMonth(int delta) {
        month.add(Calendar.MONTH, delta); selectedDate = format(month, "yyyy-MM-dd"); loadCalendar();
    }

    private void refresh() {
        loadCalendar();
        repository.loadRecurring(new ScheduleRepository.ResultCallback<List<RecurringAvailability>>() {
            @Override public void onSuccess(List<RecurringAvailability> data, String message) {
                if (isFinishing() || isDestroyed()) return;
                rules.removeAllViews();
                if (data == null || data.isEmpty()) rules.addView(UiUtil.text(WeeklyAvailabilityActivity.this, "No recurring schedules yet.", 14, R.color.text_secondary, false));
                else for (RecurringAvailability rule : data) renderRule(rule);
            }
            @Override public void onError(String message) { if (!isFinishing() && !isDestroyed()) status.setText(message); }
        });
    }

    private void loadCalendar() {
        final int request = ++calendarRequest;
        slots.clear(); drawCalendar(); drawDay(); status.setText("Loading calendar…");
        repository.loadCalendar(format(month, "yyyy-MM"), new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
            @Override public void onSuccess(List<PostedSchedule> data, String message) {
                if (isFinishing() || isDestroyed() || request != calendarRequest) return;
                slots.clear(); if (data != null) slots.addAll(data);
                status.setText("Calendar up to date. Past dates are shown for reference."); drawCalendar(); drawDay();
            }
            @Override public void onError(String message) {
                if (!isFinishing() && !isDestroyed() && request == calendarRequest) status.setText(message);
            }
        });
    }

    private void drawCalendar() {
        monthLabel.setText(format(month, "MMMM yyyy")); grid.removeAllViews();
        for (String label : new String[]{"M", "T", "W", "T", "F", "S", "S"}) addCell(label, null, 0, 0, true);
        int offset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int i = 0; i < offset; i++) addCell("", null, 0, 0, false);
        for (int day = 1; day <= month.getActualMaximum(Calendar.DAY_OF_MONTH); day++) {
            Calendar date = (Calendar) month.clone(); date.set(Calendar.DAY_OF_MONTH, day);
            String iso = format(date, "yyyy-MM-dd"); int open = 0, booked = 0;
            for (PostedSchedule slot : slots) if (iso.equals(slot.getDate())) {
                if ("OPEN".equals(slot.getStatus())) open++;
                else if (!slot.isCancelled() && !slot.isDeclined()) booked++;
            }
            addCell(String.valueOf(day), iso, open, booked, false);
        }
    }

    private void addCell(String label, String date, int open, int booked, boolean header) {
        TextView cell = new TextView(this);
        cell.setText(label + (open > 0 ? "\n" + open + " open" : booked > 0 ? "\nBooked" : ""));
        cell.setGravity(Gravity.CENTER); cell.setTextSize(header ? 12 : 13);
        cell.setTextColor(getColor(R.color.primary));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; params.height = UiUtil.dp(this, header ? 28 : 64);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(2, 2, 2, 2); cell.setLayoutParams(params);
        cell.setBackgroundColor(getColor(open > 0 ? R.color.brand_yellow : booked > 0 ? R.color.brand_tint : R.color.white));
        if (date != null) {
            cell.setContentDescription(date + ", " + open + " available slots, " + booked + " appointments");
            cell.setSelected(date.equals(selectedDate));
            if (date.equals(selectedDate)) {
                cell.setTypeface(null, android.graphics.Typeface.BOLD);
                cell.setBackgroundColor(getColor(R.color.primary));
                cell.setTextColor(Color.WHITE);
            }
            cell.setOnClickListener(v -> { selectedDate = date; drawCalendar(); drawDay(); });
        }
        grid.addView(cell);
    }

    private void drawDay() {
        dateLabel.setText(selectedDate); daySlots.removeAllViews(); int count = 0;
        for (PostedSchedule slot : slots) if (selectedDate.equals(slot.getDate())) {
            count++;
            MaterialCardView card = UiUtil.card(this); LinearLayout col = UiUtil.column(this);
            col.addView(UiUtil.text(this, slot.getTimeSlot() + " • " + slot.getStatus(), 15, R.color.primary, true));
            col.addView(UiUtil.text(this, slot.getCategory() + " · " + slot.getLocation(), 13, R.color.text_secondary, false));
            if (slot.getRecurrenceId() > 0) col.addView(UiUtil.text(this, "Weekly recurring schedule", 12, R.color.accent, false));
            card.addView(col); daySlots.addView(card);
        }
        if (count == 0) daySlots.addView(UiUtil.text(this, "No availability or appointments on this date.", 14, R.color.text_secondary, false));
    }

    private void renderRule(RecurringAvailability rule) {
        MaterialCardView card = UiUtil.card(this); LinearLayout col = UiUtil.column(this);
        col.addView(UiUtil.text(this, DAYS[Math.max(0, Math.min(6, rule.weekday - 1))] + " · " + rule.startTime + " – " + rule.endTime, 16, R.color.primary, true));
        col.addView(UiUtil.text(this, rule.dateFrom + " to " + rule.dateTo + "\n" + rule.category + " · " + rule.location, 13, R.color.text_secondary, false));
        LinearLayout row = UiUtil.buttonRow(this);
        MaterialButton edit = UiUtil.outlinedButton(this, "Edit", getColor(R.color.accent));
        MaterialButton delete = UiUtil.outlinedButton(this, "Delete", Color.parseColor("#DC2626"));
        edit.setContentDescription("Edit weekly schedule " + rule.id); delete.setContentDescription("Delete weekly schedule " + rule.id);
        edit.setOnClickListener(v -> editRule(rule));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.WeeklyAvailabilityDialog).setTitle("Delete weekly schedule?")
                .setMessage("Future unbooked dates will be removed. Existing appointments and past dates stay in the calendar.")
                .setNegativeButton("Keep", null).setPositiveButton("Delete", (d, w) -> {
                    delete.setEnabled(false);
                    repository.deleteRecurring(rule, new ScheduleRepository.ResultCallback<Object>() {
                        @Override public void onSuccess(Object data, String message) { if (!isDestroyed()) { refresh(); showResult(message); } }
                        @Override public void onError(String message) { if (!isDestroyed()) { delete.setEnabled(true); showResult(message); } }
                    });
                }).show());
        UiUtil.addToButtonRow(this, row, edit, true); UiUtil.addToButtonRow(this, row, delete, false);
        col.addView(row); card.addView(col); rules.addView(card);
    }

    private void showResult(String message) {
        if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this, R.style.WeeklyAvailabilityDialog).setTitle("Weekly availability").setMessage(message).setPositiveButton("OK", null).show();
    }

    private EditText field(LinearLayout parent, String label, String value, boolean picker) {
        parent.addView(UiUtil.text(this, label, 13, R.color.text_secondary, true));
        EditText input = new EditText(this); input.setText(value); input.setContentDescription(label);
        input.setTextColor(getColor(R.color.text_primary));
        input.setSingleLine(true); input.setTextSize(15); input.setPadding(12, 16, 12, 16);
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtil.dp(this, 52)));
        if (picker) { input.setFocusable(false); input.setInputType(0); }
        parent.addView(input); return input;
    }

    private void pickTime(EditText input) {
        String[] parts = input.getText().toString().split(":");
        new TimePickerDialog(this, (p, h, m) -> input.setText(String.format(Locale.US, "%02d:%02d", h, m)), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), false).show();
    }

    private void pickDate(EditText input) {
        Calendar date = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        try { SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US); f.setTimeZone(date.getTimeZone()); date.setTime(f.parse(input.getText().toString())); } catch (ParseException ignored) { }
        new DatePickerDialog(this, (p, y, m, d) -> input.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)), date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void editRule(RecurringAvailability original) {
        LinearLayout form = UiUtil.column(this); ScrollView scroll = new ScrollView(this); scroll.addView(form);
        form.setBackgroundColor(getColor(R.color.white));
        form.addView(UiUtil.text(this, "Applies to each matching weekday in the date range. Past occurrences are skipped.", 13, R.color.text_secondary, false));
        Spinner weekday = new Spinner(this); weekday.setContentDescription("Day of week");
        weekday.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DAYS));
        weekday.setSelection(original == null ? 0 : original.weekday - 1); form.addView(weekday);
        EditText start = field(form, "Start time", original == null ? "09:00" : original.startTime, true);
        EditText end = field(form, "End time", original == null ? "10:00" : original.endTime, true);
        start.setOnClickListener(v -> pickTime(start)); end.setOnClickListener(v -> pickTime(end));
        Calendar last = (Calendar) month.clone(); last.set(Calendar.DAY_OF_MONTH, last.getActualMaximum(Calendar.DAY_OF_MONTH));
        EditText from = field(form, "From date", original == null ? format(month, "yyyy-MM-dd") : original.dateFrom, true);
        EditText to = field(form, "Through date (inclusive)", original == null ? format(last, "yyyy-MM-dd") : original.dateTo, true);
        from.setOnClickListener(v -> pickDate(from)); to.setOnClickListener(v -> pickDate(to));
        EditText category = field(form, "Category", original == null ? "Academic Advising" : original.category, false);
        EditText location = field(form, "Location", original == null ? "" : original.location, false);
        TextView error = UiUtil.text(this, "", 13, R.color.text_secondary, false); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); form.addView(error);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.WeeklyAvailabilityDialog).setTitle(original == null ? "Add weekly schedule" : "Edit weekly schedule")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (start.getText().toString().compareTo(end.getText().toString()) >= 0) { error.setText("End time must be after start time."); return; }
            if (from.getText().toString().compareTo(to.getText().toString()) > 0) { error.setText("End date must be on or after start date."); return; }
            if (category.getText().toString().trim().isEmpty() || location.getText().toString().trim().isEmpty()) { error.setText("Enter category and location."); return; }
            RecurringAvailability rule = new RecurringAvailability();
            rule.id = original == null ? 0 : original.id; rule.version = original == null ? 0 : original.version;
            rule.weekday = weekday.getSelectedItemPosition() + 1;
            rule.startTime = start.getText().toString(); rule.endTime = end.getText().toString();
            rule.dateFrom = from.getText().toString(); rule.dateTo = to.getText().toString();
            rule.category = category.getText().toString().trim(); rule.location = location.getText().toString().trim();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); error.setText("Checking dates and saving…");
            repository.saveRecurring(rule, new ScheduleRepository.ResultCallback<Object>() {
                @Override public void onSuccess(Object data, String message) { if (!isDestroyed()) { dialog.dismiss(); refresh(); showResult(message); } }
                @Override public void onError(String message) { if (!isDestroyed()) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); error.setText(message); } }
            });
        })); dialog.show();
    }
}
