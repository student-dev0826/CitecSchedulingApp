package com.example.citecschedulingapp.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.AppTime;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.UiUtil;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** This week's booked consultations, Monday to Friday, based on today's real date. */
public class ScheduleFragment extends Fragment {

    private final MaterialButton[] dayButtons = new MaterialButton[5];
    private final String[] dayIso = new String[5];
    private final Date[] dayDate = new Date[5];

    private TextView tvWeekRange, tvSelectedDay, tvNoWeek;
    private LinearLayout containerWeek;

    private ScheduleRepository repository;
    private SessionManager sessionManager;
    private List<PostedSchedule> appointments = new ArrayList<>();
    private boolean loaded = false;
    private int selectedDay = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ScheduleRepository.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        dayButtons[0] = view.findViewById(R.id.btnDayMon);
        dayButtons[1] = view.findViewById(R.id.btnDayTue);
        dayButtons[2] = view.findViewById(R.id.btnDayWed);
        dayButtons[3] = view.findViewById(R.id.btnDayThu);
        dayButtons[4] = view.findViewById(R.id.btnDayFri);
        tvWeekRange = view.findViewById(R.id.tvWeekRange);
        tvSelectedDay = view.findViewById(R.id.tvSelectedDay);
        tvNoWeek = view.findViewById(R.id.tvNoWeek);
        containerWeek = view.findViewById(R.id.containerWeek);

        computeWeek();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            dayButtons[i].setOnClickListener(v -> selectDay(index));
        }
        selectDay(selectedDay);
        load();
    }

    /** Works out this week's Monday-Friday dates from the real clock (Philippine time). */
    private void computeWeek() {
        Calendar cal = Calendar.getInstance(AppTime.ZONE);
        int dow = cal.get(Calendar.DAY_OF_WEEK);            // Sun=1 ... Sat=7
        int sinceMonday = (dow + 5) % 7;                    // Mon=0 ... Sun=6
        cal.add(Calendar.DAY_OF_YEAR, -sinceMonday);

        for (int i = 0; i < 5; i++) {
            dayDate[i] = cal.getTime();
            dayIso[i] = AppTime.fixed("yyyy-MM-dd").format(cal.getTime());
            dayButtons[i].setText(AppTime.display("EEE d").format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        tvWeekRange.setText("Week of " + AppTime.display("MMM d").format(dayDate[0])
                + " – " + AppTime.display("MMM d, yyyy").format(dayDate[4]));

        // Open on today, or Monday on weekends.
        String today = AppTime.todayIso();
        selectedDay = 0;
        for (int i = 0; i < 5; i++) if (dayIso[i].equals(today)) selectedDay = i;
    }

    private void load() {
        tvNoWeek.setText("Loading…");
        tvNoWeek.setVisibility(View.VISIBLE);
        repository.loadStudentAppointments(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> data, String message) {
                        if (!isAdded()) return;
                        appointments = data != null ? data : new ArrayList<PostedSchedule>();
                        loaded = true;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        tvNoWeek.setText(message);
                        tvNoWeek.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void selectDay(int index) {
        selectedDay = index;
        int primary = requireContext().getColor(R.color.primary);
        for (int i = 0; i < 5; i++) {
            boolean sel = i == index;
            dayButtons[i].setBackgroundTintList(ColorStateList.valueOf(sel ? primary : Color.TRANSPARENT));
            dayButtons[i].setTextColor(sel ? Color.WHITE : primary);
        }
        tvSelectedDay.setText(AppTime.display("EEEE, MMMM d").format(dayDate[index])
                + (dayIso[index].equals(AppTime.todayIso()) ? "  (today)" : ""));
        if (loaded) render();
    }

    private void render() {
        containerWeek.removeAllViews();
        int shown = 0;
        for (PostedSchedule p : appointments) {
            if (!dayIso[selectedDay].equals(p.getDate())) continue;
            if (p.isDeclined() || p.isCancelled()) continue;
            containerWeek.addView(buildCard(p));
            shown++;
        }
        if (shown == 0) {
            tvNoWeek.setText("No appointments on this day.");
            tvNoWeek.setVisibility(View.VISIBLE);
        } else {
            tvNoWeek.setVisibility(View.GONE);
        }
    }

    private View buildCard(PostedSchedule p) {
        android.content.Context c = requireContext();
        MaterialCardView card = UiUtil.card(c);
        LinearLayout col = UiUtil.column(c);
        TextView badge = p.hasEnded()
                ? UiUtil.badge(c, p.isPending() ? "EXPIRED" : "DONE", "#E5E7EB", "#374151")
                : p.isPending()
                ? UiUtil.badge(c, "PENDING", "#FEF3C7", "#92400E")
                : UiUtil.badge(c, "BOOKED", "#DCFCE7", "#166534");
        col.addView(UiUtil.headerRow(c, p.getCategory(), badge));
        col.addView(UiUtil.text(c, p.getTimeSlot() + " • " + p.getLocation(), 13f, R.color.text_secondary, false));
        col.addView(UiUtil.text(c, "Advisor: " + p.getFacultyDisplayName(), 13f, R.color.text_primary, false));
        card.addView(col);
        return card;
    }
}
