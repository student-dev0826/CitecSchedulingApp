package com.example.citecschedulingapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.LoginRequest;
import com.example.citecschedulingapp.model.LoginResponse;
import com.example.citecschedulingapp.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleRoleGroup;
    private TextInputLayout tilIdentifier;
    private TextInputEditText etIdentifier;
    private TextInputEditText etPassword;
    private MaterialCheckBox cbRememberMe;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView tvWelcomeTitle;
    private TextView tvRegisterLink;

    private SessionManager sessionManager;
    private boolean isFacultyMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Auto-login check: route to Faculty or Student dashboard only if "Remember Me" was checked
        if (sessionManager.isRemembered()) {
            navigateToNextScreen();
            return;
        } else {
            // App starts fresh from login page when Remember Me was not checked
            sessionManager.logout();
        }

        setContentView(R.layout.activity_login);

        initViews();
        setupToggleGroup();
        checkIntentRole();
        setupListeners();
    }

    private void initViews() {
        toggleRoleGroup = findViewById(R.id.toggleRoleGroup);
        tilIdentifier = findViewById(R.id.tilIdentifier);
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
    }

    private void setupToggleGroup() {
        if (toggleRoleGroup != null) {
            toggleRoleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    setFacultyMode(checkedId == R.id.btnRoleFaculty);
                }
            });
        }
    }

    private void checkIntentRole() {
        if (getIntent() != null && getIntent().hasExtra("REGISTERED_ROLE")) {
            String role = getIntent().getStringExtra("REGISTERED_ROLE");
            if ("FACULTY".equalsIgnoreCase(role) && toggleRoleGroup != null) {
                toggleRoleGroup.check(R.id.btnRoleFaculty);
            }
        }
    }

    private void setFacultyMode(boolean faculty) {
        isFacultyMode = faculty;
        if (faculty) {
            if (tilIdentifier != null) tilIdentifier.setHint(getString(R.string.label_email_or_faculty_id));
            if (tvWelcomeTitle != null) tvWelcomeTitle.setText("Faculty Portal Login");
        } else {
            if (tilIdentifier != null) tilIdentifier.setHint(getString(R.string.label_email_or_id));
            if (tvWelcomeTitle != null) tvWelcomeTitle.setText(R.string.title_welcome_back);
        }
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        tvRegisterLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        String email = etIdentifier.getText() != null
                ? etIdentifier.getText().toString().trim()
                : "";

        String password = etPassword.getText() != null
                ? etPassword.getText().toString().trim()
                : "";

        // Client-side Validation
        if (TextUtils.isEmpty(email)) {
            etIdentifier.setError(getString(R.string.err_email_or_id_required));
            etIdentifier.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.err_password_required));
            etPassword.requestFocus();
            return;
        }

        showLoading(true);

        String targetRole = isFacultyMode ? "FACULTY" : "STUDENT";
        LoginRequest request = new LoginRequest(email, password);

        RetrofitClient.getApiService()
                .loginUser(request.getEmail(), request.getPassword(), targetRole)
                .enqueue(new Callback<LoginResponse>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<LoginResponse> call,
                            @NonNull Response<LoginResponse> response) {

                        showLoading(false);

                        if (response.isSuccessful() && response.body() != null) {

                            LoginResponse loginResponse = response.body();

                            if (loginResponse.isSuccess()
                                    && loginResponse.getUser() != null) {

                                String assignedRole = loginResponse.getUser().getRawRole();
                                if (assignedRole == null || assignedRole.trim().isEmpty()) {
                                    assignedRole = targetRole;
                                }

                                // Role Validation: prevent Faculty/Instructor from logging in via Student mode
                                if (!isFacultyMode && ("FACULTY".equalsIgnoreCase(assignedRole) || "INSTRUCTOR".equalsIgnoreCase(assignedRole) || "PROFESSOR".equalsIgnoreCase(assignedRole))) {
                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Faculty accounts cannot log in through Student login. Please switch to Faculty portal.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                    return;
                                }

                                if (isFacultyMode && "STUDENT".equalsIgnoreCase(assignedRole)) {
                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Student accounts cannot log in through Faculty login. Please switch to Student portal.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                    return;
                                }

                                boolean rememberMe = cbRememberMe != null && cbRememberMe.isChecked();

                                sessionManager.saveUserSession(
                                        loginResponse.getUser(),
                                        assignedRole,
                                        rememberMe
                                );

                                Toast.makeText(
                                        LoginActivity.this,
                                        loginResponse.getMessage(),
                                        Toast.LENGTH_SHORT
                                ).show();

                                navigateToNextScreen();

                            } else {
                                Toast.makeText(
                                        LoginActivity.this,
                                        loginResponse.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        } else {
                            Toast.makeText(
                                    LoginActivity.this,
                                    "Server error. HTTP " + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<LoginResponse> call,
                            @NonNull Throwable t) {

                        showLoading(false);

                        Toast.makeText(
                                LoginActivity.this,
                                "Connection failed: " + t.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void navigateToNextScreen() {
        Intent intent;
        if (sessionManager.isFaculty()) {
            intent = new Intent(LoginActivity.this, FacultyHomeActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, HomeActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
            btnLogin.setText(R.string.btn_loading);
        } else {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            btnLogin.setText(R.string.btn_login);
        }
    }
}
