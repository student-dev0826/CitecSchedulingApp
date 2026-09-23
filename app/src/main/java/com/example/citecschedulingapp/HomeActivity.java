package com.example.citecschedulingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName;
    private TextView tvStudentId;
    private TextView tvEmail;

    private MaterialButton btnViewSchedule;
    private MaterialButton btnBookAppointment;
    private MaterialButton btnMyAppointments;
    private MaterialButton btnProfile;
    private MaterialButton btnLogout;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Security check: if user is not logged in, redirect to LoginActivity immediately
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        setContentView(R.layout.activity_home);

        initViews();
        displayUserData();
        setupListeners();
    }

    private void initViews() {
        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        tvStudentId = findViewById(R.id.tvStudentId);
        tvEmail = findViewById(R.id.tvEmail);

        btnViewSchedule = findViewById(R.id.btnViewSchedule);
        btnBookAppointment = findViewById(R.id.btnBookAppointment);
        btnMyAppointments = findViewById(R.id.btnMyAppointments);
        btnProfile = findViewById(R.id.btnProfile);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void displayUserData() {
        String fullName = sessionManager.getFullName();
        String studentId = sessionManager.getStudentId();
        String email = sessionManager.getEmail();

        tvWelcomeName.setText(getString(R.string.welcome_user, fullName));
        tvStudentId.setText(getString(R.string.label_student_id_format, studentId));
        tvEmail.setText(getString(R.string.label_email_format, email));
    }

    private void setupListeners() {
        btnViewSchedule.setOnClickListener(v -> showPlaceholderToast("View Schedule"));
        btnBookAppointment.setOnClickListener(v -> showPlaceholderToast("Book Appointment"));
        btnMyAppointments.setOnClickListener(v -> showPlaceholderToast("My Appointments"));
        btnProfile.setOnClickListener(v -> showPlaceholderToast("Profile"));

        btnLogout.setOnClickListener(v -> performLogout());
    }

    private void showPlaceholderToast(String featureName) {
        Toast.makeText(this, featureName + " feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void performLogout() {
        // Clear SharedPreferences session
        sessionManager.logout();

        Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();

        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        // Clear Activity back stack to prevent going back to HomeActivity with Back button
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
