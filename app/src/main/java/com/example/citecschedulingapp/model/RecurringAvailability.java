package com.example.citecschedulingapp.model;

import com.google.gson.annotations.SerializedName;

public class RecurringAvailability {
    @SerializedName("recurrence_id") public int id;
    @SerializedName("weekday") public int weekday;
    @SerializedName("start_time") public String startTime;
    @SerializedName("end_time") public String endTime;
    @SerializedName("date_from") public String dateFrom;
    @SerializedName("date_to") public String dateTo;
    @SerializedName("category") public String category;
    @SerializedName("location") public String location;
    @SerializedName("version") public int version;
}
