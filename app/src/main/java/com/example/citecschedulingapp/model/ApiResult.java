package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

/** Standard reply from schedules.php: { success, message, data }. */
public class ApiResult<T> {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private T data;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message != null ? message : ""; }
    public T getData() { return data; }
}
