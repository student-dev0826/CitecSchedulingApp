package com.example.citecschedulingapp;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.citecschedulingapp.model.ForgotPasswordResponse;
import com.example.citecschedulingapp.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Forgot password flow in one screen, three steps:
 * 1) enter email -> 2) enter 6-digit code -> 3) enter + confirm new password -> back to Login.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "EXTRA_EMAIL";

    private static final int STEP_EMAIL = 1;
    private static final int STEP_CODE = 2;
    private static final int STEP_PASSWORD = 3;
    private static final long RESEND_COOLDOWN_MS = 60_000L;

    private TextView tvFpTitle;
    private TextView tvFpSubtitle;
    private View layoutStepEmail;
    private View layoutStepCode;
    private View layoutStepPassword;

    private TextInputLayout tilFpEmail;
    private TextInputEditText etFpEmail;
    private TextInputLayout tilCode;
    private TextInputEditText etCode;
    private TextInputLayout tilNewPassword;
    private TextInputEditText etNewPassword;
    private TextInputLayout tilConfirmNewPassword;
    private TextInputEditText etConfirmNewPassword;

    private MaterialButton btnSendCode;
    private MaterialButton btnVerifyCode;
    private MaterialButton btnResetPassword;
    private TextView tvResendCode;
    private TextView tvChangeEmail;
    private TextView tvBackToLogin;
    private ProgressBar progressBar;

    private int currentStep = STEP_EMAIL;
    private String email = "";
    private String resetToken = "";
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initViews();
        setupListeners();
        showStep(STEP_EMAIL);

        String prefill = getIntent() != null ? getIntent().getStringExtra(EXTRA_EMAIL) : null;
        if (prefill != null && !prefill.isEmpty()) {
            etFpEmail.setText(prefill);
        }
    }

    private void initViews() {
        tvFpTitle = findViewById(R.id.tvFpTitle);
        tvFpSubtitle = findViewById(R.id.tvFpSubtitle);
        layoutStepEmail = findViewById(R.id.layoutStepEmail);
        layoutStepCode = findViewById(R.id.layoutStepCode);
        layoutStepPassword = findViewById(R.id.layoutStepPassword);

        tilFpEmail = findViewById(R.id.tilFpEmail);
        etFpEmail = findViewById(R.id.etFpEmail);
        tilCode = findViewById(R.id.tilCode);
        etCode = findViewById(R.id.etCode);
        tilNewPassword = findViewById(R.id.tilNewPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        tilConfirmNewPassword = findViewById(R.id.tilConfirmNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);

        btnSendCode = findViewById(R.id.btnSendCode);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        tvResendCode = findViewById(R.id.tvResendCode);
        tvChangeEmail = findViewById(R.id.tvChangeEmail);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnSendCode.setOnClickListener(v -> {
            String typed = getText(etFpEmail);
            tilFpEmail.setError(null);

            if (typed.isEmpty()) {
                tilFpEmail.setError(getString(R.string.err_email_required));
                etFpEmail.requestFocus();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(typed).matches()) {
                tilFpEmail.setError(getString(R.string.err_email_invalid));
                etFpEmail.requestFocus();
                return;
            }
            requestCode(typed);
        });

        btnVerifyCode.setOnClickListener(v -> attemptVerifyCode());

        tvResendCode.setOnClickListener(v -> {
            if (tvResendCode.isEnabled()) {
                requestCode(email);
            }
        });

        tvChangeEmail.setOnClickListener(v -> showStep(STEP_EMAIL));

        btnResetPassword.setOnClickListener(v -> attemptResetPassword());

        tvBackToLogin.setOnClickListener(v -> finish());
    }

    // ------------------------------------------------------------------
    // Step 1 (and "Resend code"): check the email and send the 6-digit code
    // ------------------------------------------------------------------
    private void requestCode(String targetEmail) {
        showLoading(true);

        RetrofitClient.getApiService()
                .forgotPassword(targetEmail)
                .enqueue(new Callback<ForgotPasswordResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ForgotPasswordResponse> call,
                                           @NonNull Response<ForgotPasswordResponse> response) {
                        showLoading(false);

                        if (response.isSuccessful() && response.body() != null) {
                            ForgotPasswordResponse body = response.body();

                            if (body.isSuccess()) {
                                email = targetEmail;
                                etCode.setText("");
                                tilCode.setError(null);
                                showStep(STEP_CODE);
                                startResendCooldown();
                                Toast.makeText(ForgotPasswordActivity.this,
                                        body.getMessage(), Toast.LENGTH_SHORT).show();
                            } else if (currentStep == STEP_EMAIL) {
                                // e.g. "Email not found."
                                tilFpEmail.setError(body.getMessage());
                                etFpEmail.requestFocus();
                            } else {
                                Toast.makeText(ForgotPasswordActivity.this,
                                        body.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Server message: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ForgotPasswordResponse> call, @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(ForgotPasswordActivity.this,
                                getString(R.string.err_network_connection), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Step 2: verify the 6-digit code
    // ------------------------------------------------------------------
    private void attemptVerifyCode() {
        String code = getText(etCode);
        tilCode.setError(null);

        if (code.isEmpty()) {
            tilCode.setError(getString(R.string.err_code_required));
            etCode.requestFocus();
            return;
        }
        if (code.length() != 6) {
            tilCode.setError(getString(R.string.err_code_length));
            etCode.requestFocus();
            return;
        }

        showLoading(true);

        RetrofitClient.getApiService()
                .verifyResetCode(email, code)
                .enqueue(new Callback<ForgotPasswordResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ForgotPasswordResponse> call,
                                           @NonNull Response<ForgotPasswordResponse> response) {
                        showLoading(false);

                        if (response.isSuccessful() && response.body() != null) {
                            ForgotPasswordResponse body = response.body();

                            if (body.isSuccess() && body.getResetToken() != null) {
                                resetToken = body.getResetToken();
                                etNewPassword.setText("");
                                etConfirmNewPassword.setText("");
                                showStep(STEP_PASSWORD);
                            } else if (body.isRestart()) {
                                // code expired / too many attempts: start over
                                Toast.makeText(ForgotPasswordActivity.this,
                                        body.getMessage(), Toast.LENGTH_LONG).show();
                                showStep(STEP_EMAIL);
                            } else {
                                // "Invalid code." -> user tries again
                                tilCode.setError(body.getMessage());
                                etCode.setText("");
                                etCode.requestFocus();
                            }
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Server message: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ForgotPasswordResponse> call, @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(ForgotPasswordActivity.this,
                                getString(R.string.err_network_connection), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Step 3: save the new password, then return to Login
    // ------------------------------------------------------------------
    private void attemptResetPassword() {
        String newPassword = getText(etNewPassword);
        String confirm = getText(etConfirmNewPassword);
        tilNewPassword.setError(null);
        tilConfirmNewPassword.setError(null);

        if (newPassword.isEmpty()) {
            tilNewPassword.setError(getString(R.string.err_password_required));
            etNewPassword.requestFocus();
            return;
        }
        if (newPassword.length() < 8) {
            tilNewPassword.setError(getString(R.string.err_password_length));
            etNewPassword.requestFocus();
            return;
        }
        if (confirm.isEmpty()) {
            tilConfirmNewPassword.setError(getString(R.string.err_confirm_password_required));
            etConfirmNewPassword.requestFocus();
            return;
        }
        if (!newPassword.equals(confirm)) {
            tilConfirmNewPassword.setError(getString(R.string.err_passwords_do_not_match));
            etConfirmNewPassword.requestFocus();
            return;
        }

        showLoading(true);

        RetrofitClient.getApiService()
                .resetPassword(email, resetToken, newPassword, confirm)
                .enqueue(new Callback<ForgotPasswordResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ForgotPasswordResponse> call,
                                           @NonNull Response<ForgotPasswordResponse> response) {
                        showLoading(false);

                        if (response.isSuccessful() && response.body() != null) {
                            ForgotPasswordResponse body = response.body();

                            if (body.isSuccess()) {
                                // Keep the offline copy of the account in sync (login checks it first)
                                LocalAccountRepository.getInstance(ForgotPasswordActivity.this)
                                        .updatePassword(email, newPassword);

                                Toast.makeText(ForgotPasswordActivity.this,
                                        R.string.msg_password_reset_success, Toast.LENGTH_LONG).show();
                                finish(); // back to Login
                            } else if (body.isRestart()) {
                                Toast.makeText(ForgotPasswordActivity.this,
                                        body.getMessage(), Toast.LENGTH_LONG).show();
                                showStep(STEP_EMAIL);
                            } else {
                                tilNewPassword.setError(body.getMessage());
                            }
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Server message: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ForgotPasswordResponse> call, @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(ForgotPasswordActivity.this,
                                getString(R.string.err_network_connection), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private void showStep(int step) {
        currentStep = step;

        layoutStepEmail.setVisibility(step == STEP_EMAIL ? View.VISIBLE : View.GONE);
        layoutStepCode.setVisibility(step == STEP_CODE ? View.VISIBLE : View.GONE);
        layoutStepPassword.setVisibility(step == STEP_PASSWORD ? View.VISIBLE : View.GONE);

        if (step == STEP_EMAIL) {
            tvFpTitle.setText(R.string.title_forgot_password);
            tvFpSubtitle.setText(R.string.subtitle_forgot_email);
            resetToken = "";
            cancelResendCooldown();
        } else if (step == STEP_CODE) {
            tvFpTitle.setText(R.string.title_verify_code);
            tvFpSubtitle.setText(getString(R.string.msg_code_sent_to, email));
        } else {
            tvFpTitle.setText(R.string.title_new_password);
            tvFpSubtitle.setText(R.string.subtitle_new_password);
            cancelResendCooldown();
        }
    }

    private void startResendCooldown() {
        cancelResendCooldown();
        tvResendCode.setEnabled(false);

        resendTimer = new CountDownTimer(RESEND_COOLDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvResendCode.setText(getString(R.string.link_resend_code_wait,
                        (int) (millisUntilFinished / 1000L) + 1));
            }

            @Override
            public void onFinish() {
                tvResendCode.setText(R.string.link_resend_code);
                tvResendCode.setEnabled(true);
            }
        }.start();
    }

    private void cancelResendCooldown() {
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
        tvResendCode.setText(R.string.link_resend_code);
        tvResendCode.setEnabled(true);
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSendCode.setEnabled(!isLoading);
        btnVerifyCode.setEnabled(!isLoading);
        btnResetPassword.setEnabled(!isLoading);
        tvChangeEmail.setEnabled(!isLoading);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    @Override
    protected void onDestroy() {
        cancelResendCooldown();
        super.onDestroy();
    }
}
