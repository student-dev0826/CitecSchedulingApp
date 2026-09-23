package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.FacultyHomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.SessionManager;
import com.google.android.material.card.MaterialCardView;

public class FacultyHomeFragment extends Fragment {

    private TextView tvWelcomeName;
    private TextView tvFacultyId;
    private TextView tvEmail;

    private MaterialCardView cardSetAvailability;
    private MaterialCardView cardTransferSchedule;

    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_home, container, false);
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
        tvFacultyId = view.findViewById(R.id.tvFacultyId);
        tvEmail = view.findViewById(R.id.tvEmail);

        cardSetAvailability = view.findViewById(R.id.cardSetAvailability);
        cardTransferSchedule = view.findViewById(R.id.cardTransferSchedule);
    }

    private void displayUserData() {
        if (sessionManager == null) return;

        String fullName = sessionManager.getFullName();
        String email = sessionManager.getEmail();

        if (tvWelcomeName != null) {
            tvWelcomeName.setText("Prof. " + fullName);
        }
        if (tvFacultyId != null) {
            tvFacultyId.setText("Faculty ID: PROF-" + sessionManager.getUserId());
        }
        if (tvEmail != null) {
            tvEmail.setText("Email: " + email);
        }
    }

    private void setupListeners() {
        if (cardSetAvailability != null) {
            cardSetAvailability.setOnClickListener(v -> {
                if (getActivity() instanceof FacultyHomeActivity) {
                    ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_availability);
                }
            });
        }

        if (cardTransferSchedule != null) {
            cardTransferSchedule.setOnClickListener(v -> {
                if (getActivity() instanceof FacultyHomeActivity) {
                    ((FacultyHomeActivity) getActivity()).selectTab(R.id.nav_faculty_transfer);
                }
            });
        }
    }
}
