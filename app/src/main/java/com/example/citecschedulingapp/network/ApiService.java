package com.example.citecschedulingapp.network;

import com.example.citecschedulingapp.model.LoginResponse;
import com.example.citecschedulingapp.model.RegisterResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface ApiService {

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
            @Field("password") String password,
            @Field("role") String role
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
}
