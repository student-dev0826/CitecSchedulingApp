package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.SessionManager;
import com.google.android.material.card.MaterialCardView;

public class HomeFragment extends Fragment {

    private TextView tvWelcomeName;
    private TextView tvStudentId;
    private TextView tvEmail;

    private MaterialCardView cardBookAppointment;
    private MaterialCardView cardViewSchedule;
    private ImageView ivNotification;

    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            sessionManager = new SessionManager(getContext());
        }

        initViews(view);
        displayUserData();
        setupListeners();
    }

    private void initViews(View view) {
        tvWelcomeName = view.findViewById(R.id.tvWelcomeName);
        tvStudentId = view.findViewById(R.id.tvStudentId);
        tvEmail = view.findViewById(R.id.tvEmail);

        cardBookAppointment = view.findViewById(R.id.cardBookAppointment);
        cardViewSchedule = view.findViewById(R.id.cardViewSchedule);
        ivNotification = view.findViewById(R.id.ivNotification);
    }

    private void displayUserData() {
        if (sessionManager == null) return;

        String fullName = sessionManager.getFullName();
        String studentId = sessionManager.getStudentId();
        String email = sessionManager.getEmail();

        if (tvWelcomeName != null) {
            tvWelcomeName.setText(getString(R.string.welcome_user, fullName));
        }
        if (tvStudentId != null) {
            tvStudentId.setText(getString(R.string.label_student_id_format, studentId));
        }
        if (tvEmail != null) {
            tvEmail.setText(getString(R.string.label_email_format, email));
        }
    }

    private void setupListeners() {
        if (cardBookAppointment != null) {
            cardBookAppointment.setOnClickListener(v -> {
                if (getActivity() instanceof HomeActivity) {
                    ((HomeActivity) getActivity()).selectTab(R.id.nav_book);
                }
            });
        }

        if (cardViewSchedule != null) {
            cardViewSchedule.setOnClickListener(v -> {
                if (getActivity() instanceof HomeActivity) {
                    ((HomeActivity) getActivity()).selectTab(R.id.nav_schedule);
                }
            });
        }

        if (ivNotification != null) {
            ivNotification.setOnClickListener(v -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "No new notifications", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
