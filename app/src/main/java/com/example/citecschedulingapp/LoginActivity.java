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
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etIdentifier;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView tvRegisterLink;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Auto-login check: if user session exists, go directly to HomeActivity
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
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

        LoginRequest request = new LoginRequest(email, password);

        RetrofitClient.getApiService()
                .loginUser(request.getEmail(), request.getPassword())
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

                                sessionManager.saveUserSession(
                                        loginResponse.getUser()
                                );

                                Toast.makeText(
                                        LoginActivity.this,
                                        loginResponse.getMessage(),
                                        Toast.LENGTH_SHORT
                                ).show();

                                Intent intent = new Intent(
                                        LoginActivity.this,
                                        HomeActivity.class
                                );

                                startActivity(intent);
                                finish();

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
