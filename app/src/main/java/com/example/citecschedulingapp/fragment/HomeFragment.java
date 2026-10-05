package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.AppTime;
import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.LiveClock;
import com.example.citecschedulingapp.NotificationHelper;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvWelcomeName, tvStudentId, tvEmail;
    private TextView tvStatUpcoming, tvStatToday, tvStatCompleted;
    private TextView tvNextTitle, tvNextStatus, tvNextTime, tvNextLocation, tvNotifBadge;
    private MaterialCardView cardBookAppointment, cardViewSchedule;
    private ImageView ivNotification;

    private SessionManager sessionManager;
    private ScheduleRepository repository;
    private LiveClock liveClock;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        repository = ScheduleRepository.getInstance(requireContext());

        tvWelcomeName = view.findViewById(R.id.tvWelcomeName);
        tvStudentId = view.findViewById(R.id.tvStudentId);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvStatUpcoming = view.findViewById(R.id.tvStatUpcoming);
        tvStatToday = view.findViewById(R.id.tvStatToday);
        tvStatCompleted = view.findViewById(R.id.tvStatCompleted);
        tvNextTitle = view.findViewById(R.id.tvNextTitle);
        tvNextStatus = view.findViewById(R.id.tvNextStatus);
        tvNextTime = view.findViewById(R.id.tvNextTime);
        tvNextLocation = view.findViewById(R.id.tvNextLocation);
        tvNotifBadge = view.findViewById(R.id.tvNotifBadge);
        cardBookAppointment = view.findViewById(R.id.cardBookAppointment);
        cardViewSchedule = view.findViewById(R.id.cardViewSchedule);
        ivNotification = view.findViewById(R.id.ivNotification);

        liveClock = new LiveClock(
                (TextView) view.findViewById(R.id.tvLiveDate),
                (TextView) view.findViewById(R.id.tvLiveTime));

        displayUserData();
        setupListeners();
        showNext(null);
    }

    private void displayUserData() {
        tvWelcomeName.setText(getString(R.string.welcome_user, sessionManager.getFullName()));
        tvStudentId.setText(getString(R.string.label_student_id_format, sessionManager.getStudentId()));
        tvEmail.setText(getString(R.string.label_email_format, sessionManager.getEmail()));
    }

    private void setupListeners() {
        cardBookAppointment.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).selectTab(R.id.nav_book);
        });
        cardViewSchedule.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) ((HomeActivity) getActivity()).selectTab(R.id.nav_schedule);
        });
        ivNotification.setOnClickListener(v ->
                NotificationHelper.showDialog(this, repository, sessionManager, tvNotifBadge));
    }

    @Override
    public void onResume() {
        super.onResume();
        liveClock.start();
        loadDashboard();
        NotificationHelper.refreshBadge(this, repository, sessionManager, tvNotifBadge);
    }

    @Override
    public void onPause() {
        super.onPause();
        liveClock.stop();
    }

    private void loadDashboard() {
        repository.loadStudentAppointments(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> list, String message) {
                        if (!isAdded() || list == null) return;
                        String today = AppTime.todayIso();
                        int upcoming = 0, todayCount = 0, completed = 0;
                        PostedSchedule next = null;
                        for (PostedSchedule p : list) {
                            if (p.hasEnded()) {
                                completed++;
                            } else {
                                upcoming++;
                                if (next == null) next = p; // list is sorted by start time
                            }
                            if (today.equals(p.getDate())) todayCount++;
                        }
                        tvStatUpcoming.setText(String.valueOf(upcoming));
                        tvStatToday.setText(String.valueOf(todayCount));
                        tvStatCompleted.setText(String.valueOf(completed));
                        showNext(next);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        tvNextTitle.setText("Couldn't load appointments");
                        tvNextStatus.setVisibility(View.GONE);
                        tvNextTime.setText(message);
                        tvNextLocation.setText("");
                    }
                });
    }

    private void showNext(@Nullable PostedSchedule next) {
        if (next == null) {
            tvNextTitle.setText("No upcoming appointment");
            tvNextStatus.setVisibility(View.GONE);
            tvNextTime.setText("Tap Book Now to schedule one");
            tvNextLocation.setText("");
            return;
        }
        tvNextTitle.setText(next.getCategory());
        tvNextStatus.setVisibility(View.VISIBLE);
        tvNextTime.setText(AppTime.dayLabel(next.getDate()) + " • " + next.getTimeSlot());
        tvNextLocation.setText(next.getLocation() + " — " + next.getFacultyDisplayName());
    }
}
