package com.example.citecschedulingapp.network;

import com.example.citecschedulingapp.model.LoginResponse;
import com.example.citecschedulingapp.model.RegisterResponse;
import com.example.citecschedulingapp.model.ForgotPasswordResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface ApiService {

    @FormUrlEncoded
    @POST("register.php")
    Call<RegisterResponse> registerUser(
            @Field("student_id") String studentId,
            @Field("first_name") String firstName,
            @Field("last_name") String lastName,
            @Field("full_name") String fullName,
            @Field("email") String email,
            @Field("password") String password,
            @Field("role") String role,
            @Field("department") String department
    );

    @FormUrlEncoded
    @POST("register.php")
    Call<RegisterResponse> registerUser(
            @Field("student_id") String studentId,
            @Field("first_name") String firstName,
            @Field("last_name") String lastName,
            @Field("full_name") String fullName,
            @Field("email") String email,
            @Field("password") String password,
            @Field("role") String role
    );

    @FormUrlEncoded
    @POST("register.php")
    Call<RegisterResponse> registerUser(
            @Field("student_id") String studentId,
            @Field("full_name") String fullName,
            @Field("email") String email,
            @Field("password") String password,
            @Field("role") String role,
            @Field("department") String department
    );

    @FormUrlEncoded
    @POST("register.php")
    Call<RegisterResponse> registerUser(
            @Field("student_id") String studentId,
            @Field("full_name") String fullName,
            @Field("email") String email,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("login.php")
    Call<LoginResponse> loginUser(
            @Field("identifier") String identifier,
            @Field("email") String email,
            @Field("password") String password,
            @Field("role") String role
    );

    @FormUrlEncoded
    @POST("login.php")
    Call<LoginResponse> loginUser(
            @Field("email") String email,
            @Field("password") String password,
            @Field("role") String role
    );

    @FormUrlEncoded
    @POST("login.php")
    Call<LoginResponse> loginUser(
            @Field("email") String email,
            @Field("password") String password
    );
    // ---- Forgot password / reset ----

    @FormUrlEncoded
    @POST("forgot_password.php")
    Call<ForgotPasswordResponse> forgotPassword(
            @Field("email") String email
    );

    @FormUrlEncoded
    @POST("verify_reset_code.php")
    Call<ForgotPasswordResponse> verifyResetCode(
            @Field("email") String email,
            @Field("code") String code
    );

    @FormUrlEncoded
    @POST("reset_password.php")
    Call<ForgotPasswordResponse> resetPassword(
            @Field("email") String email,
            @Field("reset_token") String resetToken,
            @Field("new_password") String newPassword,
            @Field("confirm_password") String confirmPassword
    );
}
