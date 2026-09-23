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
import com.example.citecschedulingapp.HomeActivity;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.SessionManager;
import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    private TextView tvProfileName;
    private TextView tvProfileStudentId;
    private TextView tvProfileEmail;
    private MaterialButton btnLogout;

    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
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
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileStudentId = view.findViewById(R.id.tvProfileStudentId);
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail);
        btnLogout = view.findViewById(R.id.btnLogout);
    }

    private void displayUserData() {
        if (sessionManager == null) return;

        String fullName = sessionManager.getFullName();
        String studentId = sessionManager.getStudentId();
        String email = sessionManager.getEmail();

        if (tvProfileName != null) {
            tvProfileName.setText(fullName);
        }
        if (tvProfileStudentId != null) {
            tvProfileStudentId.setText(getString(R.string.label_student_id_format, studentId));
        }
        if (tvProfileEmail != null) {
            tvProfileEmail.setText(email);
        }
    }

    private void setupListeners() {
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                if (getActivity() instanceof HomeActivity) {
                    ((HomeActivity) getActivity()).performLogout();
                } else if (getActivity() instanceof FacultyHomeActivity) {
                    ((FacultyHomeActivity) getActivity()).performLogout();
                }
            });
        }
    }
}
