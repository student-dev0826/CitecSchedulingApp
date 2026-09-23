package com.example.citecschedulingapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.RegisterResponse;
import com.example.citecschedulingapp.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleRoleGroup;
    private TextInputLayout tilStudentId;
    private TextInputLayout tilDepartment;
    private AutoCompleteTextView actDepartment;
    private TextInputEditText etStudentId;
    private TextInputEditText etFullName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;
    private TextView tvSubtitleRegister;
    private TextView tvLoginLink;

    private boolean isFacultyMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupDepartmentDropdown();
        setupToggleGroup();
        setupListeners();
    }

    private void initViews() {
        toggleRoleGroup = findViewById(R.id.toggleRoleGroup);
        tilStudentId = findViewById(R.id.tilStudentId);
        tilDepartment = findViewById(R.id.tilDepartment);
        actDepartment = findViewById(R.id.actDepartment);
        etStudentId = findViewById(R.id.etStudentId);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);
        tvSubtitleRegister = findViewById(R.id.tvSubtitleRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);
    }

    private void setupDepartmentDropdown() {
        String[] departments = new String[]{
                "College of Information Tech & Computing (CITEC)",
                "Department of Computer Science (CS)",
                "Department of Information Technology (IT)",
                "Department of Information Systems (IS)",
                "Department of Computer Engineering (CpE)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, departments);
        if (actDepartment != null) {
            actDepartment.setAdapter(adapter);
        }
    }

    private void setupToggleGroup() {
        if (toggleRoleGroup != null) {
            toggleRoleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.btnRoleFaculty) {
                        setFacultyMode(true);
                    } else {
                        setFacultyMode(false);
                    }
                }
            });
        }
    }

    private void setFacultyMode(boolean faculty) {
        isFacultyMode = faculty;
        if (faculty) {
            tilStudentId.setHint(getString(R.string.label_faculty_id));
            if (tilDepartment != null) tilDepartment.setVisibility(View.VISIBLE);
            if (tvSubtitleRegister != null) tvSubtitleRegister.setText(R.string.subtitle_register_faculty);
            if (btnRegister != null) btnRegister.setText(R.string.btn_register_faculty);
        } else {
            tilStudentId.setHint(getString(R.string.label_student_id));
            if (tilDepartment != null) tilDepartment.setVisibility(View.GONE);
            if (tvSubtitleRegister != null) tvSubtitleRegister.setText(R.string.subtitle_register);
            if (btnRegister != null) btnRegister.setText(R.string.btn_register_student);
        }
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());

        tvLoginLink.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void attemptRegister() {
        String role = isFacultyMode ? "FACULTY" : "STUDENT";

        String studentId = etStudentId.getText() != null ? etStudentId.getText().toString().trim() : "";
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        // Client-side Validation
        if (TextUtils.isEmpty(studentId)) {
            etStudentId.setError(isFacultyMode ? "Faculty ID is required." : getString(R.string.err_student_id_required));
            etStudentId.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError(getString(R.string.err_full_name_required));
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.err_email_required));
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.err_email_invalid));
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.err_password_required));
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            etPassword.setError(getString(R.string.err_password_length));
            etPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.err_confirm_password_required));
            etConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.err_passwords_do_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        showLoading(true);

        RetrofitClient.getApiService()
                .registerUser(studentId, fullName, email, password, role)
                .enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(@NonNull Call<RegisterResponse> call, @NonNull Response<RegisterResponse> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse registerResponse = response.body();

                    if (registerResponse.isSuccess()) {
                        Toast.makeText(RegisterActivity.this, registerResponse.getMessage(), Toast.LENGTH_SHORT).show();

                        // Navigate to LoginActivity
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        intent.putExtra("REGISTERED_ROLE", role);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, registerResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    String serverErrMsg = "Server error: HTTP " + response.code();

                    try {
                        if (response.errorBody() != null) {
                            serverErrMsg += "\n" + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Toast.makeText(
                            RegisterActivity.this,
                            serverErrMsg,
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<RegisterResponse> call, @NonNull Throwable t) {
                showLoading(false);
                Toast.makeText(RegisterActivity.this, getString(R.string.err_network_connection), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnRegister.setEnabled(false);
            btnRegister.setText(R.string.btn_loading);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
            btnRegister.setText(isFacultyMode ? R.string.btn_register_faculty : R.string.btn_register_student);
        }
    }
}
