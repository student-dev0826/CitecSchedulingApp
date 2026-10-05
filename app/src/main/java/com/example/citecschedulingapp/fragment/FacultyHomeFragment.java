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
import com.example.citecschedulingapp.FacultyHomeActivity;
import com.example.citecschedulingapp.LiveClock;
import com.example.citecschedulingapp.NotificationHelper;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class FacultyHomeFragment extends Fragment {

    private TextView tvWelcomeName, tvFacultyId, tvEmail;
    private TextView tvStatToday, tvStatOpen, tvStatCompleted, tvNotifBadge;
    private MaterialCardView cardSetAvailability, cardTransferSchedule;
    private ImageView ivNotification;

    private SessionManager sessionManager;
    private ScheduleRepository repository;
    private LiveClock liveClock;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        repository = ScheduleRepository.getInstance(requireContext());

        tvWelcomeName = view.findViewById(R.id.tvWelcomeName);
        tvFacultyId = view.findViewById(R.id.tvFacultyId);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvStatToday = view.findViewById(R.id.tvStatToday);
        tvStatOpen = view.findViewById(R.id.tvStatOpen);
        tvStatCompleted = view.findViewById(R.id.tvStatCompleted);
        tvNotifBadge = view.findViewById(R.id.tvNotifBadge);
        cardSetAvailability = view.findViewById(R.id.cardSetAvailability);
        cardTransferSchedule = view.findViewById(R.id.cardTransferSchedule);
        ivNotification = view.findViewById(R.id.ivNotification);

        liveClock = new LiveClock(
                (TextView) view.findViewById(R.id.tvLiveDate),
                (TextView) view.findViewById(R.id.tvLiveTime));

        tvWelcomeName.setText(PostedSchedule.formatFacultyName(sessionManager.getFullName()));
        tvFacultyId.setText("Faculty ID: " + sessionManager.getStudentId());
        tvEmail.setText("Email: " + sessionManager.getEmail());

        cardSetAvailability.setOnClickListener(v -> {
            if (getActivity() instanceof FacultyHomeActivity)
                ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_availability);
        });
        cardTransferSchedule.setOnClickListener(v -> {
            if (getActivity() instanceof FacultyHomeActivity)
                ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_transfer);
        });
        ivNotification.setOnClickListener(v ->
                NotificationHelper.showDialog(this, repository, sessionManager, tvNotifBadge));
    }

    @Override
    public void onResume() {
        super.onResume();
        liveClock.start();
        loadStats();
        NotificationHelper.refreshBadge(this, repository, sessionManager, tvNotifBadge);
    }

    @Override
    public void onPause() {
        super.onPause();
        liveClock.stop();
    }

    private void loadStats() {
        repository.loadFacultySlots(sessionManager.getUserId(),
                new ScheduleRepository.ResultCallback<List<PostedSchedule>>() {
                    @Override
                    public void onSuccess(List<PostedSchedule> list, String message) {
                        if (!isAdded() || list == null) return;
                        String today = AppTime.todayIso();
                        int studentsToday = 0, open = 0, completed = 0;
                        for (PostedSchedule p : list) {
                            if (p.isBooked()) {
                                if (p.hasEnded()) completed++;
                                if (today.equals(p.getDate())) studentsToday++;
                            } else if (!p.hasEnded()) {
                                open++;
                            }
                        }
                        tvStatToday.setText(String.valueOf(studentsToday));
                        tvStatOpen.setText(String.valueOf(open));
                        tvStatCompleted.setText(String.valueOf(completed));
                    }

                    @Override
                    public void onError(String message) {
                        // Leave the zeros; the other tabs show the error in detail.
                    }
                });
    }
}
