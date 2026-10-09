package com.example.citecschedulingapp;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.citecschedulingapp.model.ApiResult;
import com.example.citecschedulingapp.model.FacultyItem;
import com.example.citecschedulingapp.model.NotificationItem;
import com.example.citecschedulingapp.model.PostedSchedule;
import com.example.citecschedulingapp.network.ApiService;
import com.example.citecschedulingapp.network.RetrofitClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Talks to schedules.php on the server. Everything is asynchronous: results come back on the
 * main thread through ResultCallback, so screens can update their views directly.
 */
public class ScheduleRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data, String message);
        void onError(String message);
    }

    private static final String LOGIN_AGAIN =
            "Please log in again while connected to the internet to use scheduling.";

    private static ScheduleRepository instance;

    private final ApiService api;

    private ScheduleRepository() {
        api = RetrofitClient.getApiService();
    }

    public static synchronized ScheduleRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleRepository();
        }
        return instance;
    }

    // ---------- reads ----------

    public void loadOpenSlots(ResultCallback<List<PostedSchedule>> cb) {
        enqueue(api.scheduleList(req("list_open")), cb);
    }

    public void loadFaculty(ResultCallback<List<FacultyItem>> cb) {
        enqueue(api.facultyList(req("list_faculty")), cb);
    }

    public void loadFacultySlots(int facultyId, ResultCallback<List<PostedSchedule>> cb) {
        if (badId(facultyId, cb)) return;
        Map<String, String> m = req("faculty_slots");
        m.put("faculty_id", String.valueOf(facultyId));
        enqueue(api.scheduleList(m), cb);
    }

    public void loadStudentAppointments(int studentId, ResultCallback<List<PostedSchedule>> cb) {
        if (badId(studentId, cb)) return;
        Map<String, String> m = req("student_appointments");
        m.put("student_id", String.valueOf(studentId));
        enqueue(api.scheduleList(m), cb);
    }

    // ---------- faculty actions ----------

    public void postSlot(int facultyId, String isoDate, String timeSlot, String category,
                         String location, ResultCallback<PostedSchedule> cb) {
        if (badId(facultyId, cb)) return;
        Map<String, String> m = req("post");
        m.put("faculty_id", String.valueOf(facultyId));
        m.put("schedule_date", isoDate);
        m.put("time_slot", timeSlot);
        m.put("category", category);
        m.put("location", location);
        enqueue(api.scheduleOne(m), cb);
    }

    public void deleteSlot(int scheduleId, int facultyId, ResultCallback<Object> cb) {
        if (badId(facultyId, cb)) return;
        Map<String, String> m = req("delete");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("faculty_id", String.valueOf(facultyId));
        enqueue(api.scheduleSimple(m), cb);
    }

    public void transferAppointment(int scheduleId, int fromFacultyId, int toFacultyId,
                                    String reason, ResultCallback<Object> cb) {
        if (badId(fromFacultyId, cb)) return;
        Map<String, String> m = req("transfer");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("from_faculty_id", String.valueOf(fromFacultyId));
        m.put("to_faculty_id", String.valueOf(toFacultyId));
        m.put("reason", reason);
        enqueue(api.scheduleSimple(m), cb);
    }

    public void respondAppointment(int scheduleId, int facultyId, String status, String declineReason, ResultCallback<Object> cb) {
        if (badId(facultyId, cb)) return;
        Map<String, String> m = req("respond_appointment");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("faculty_id", String.valueOf(facultyId));
        m.put("status", status);
        m.put("decline_reason", declineReason != null ? declineReason : "");
        enqueue(api.scheduleSimple(m), cb);
    }

    /**
     * Professor cancels a confirmed appointment.
     * reasonType: SUDDEN_CONFLICT, PERSONAL_EMERGENCY, MEETING or OTHERS (OTHERS needs reasonText).
     */
    public void cancelAppointmentByFaculty(int scheduleId, int facultyId, String reasonType,
                                           String reasonText, ResultCallback<Object> cb) {
        if (badId(facultyId, cb)) return;
        Map<String, String> m = req("faculty_cancel");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("faculty_id", String.valueOf(facultyId));
        m.put("reason_type", reasonType);
        m.put("reason_text", reasonText != null ? reasonText : "");
        enqueue(api.scheduleSimple(m), cb);
    }

    // ---------- student actions ----------

    public void bookSlot(int scheduleId, int studentId, String purpose, ResultCallback<PostedSchedule> cb) {
        if (badId(studentId, cb)) return;
        Map<String, String> m = req("book");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("student_id", String.valueOf(studentId));
        m.put("purpose", purpose);
        enqueue(api.scheduleOne(m), cb);
    }

    public void requestCustomAppointment(int studentId, int facultyId, String date, String timeSlot, String category, String location, String purpose, ResultCallback<PostedSchedule> cb) {
        if (badId(studentId, cb)) return;
        Map<String, String> m = req("request_custom_appointment");
        m.put("student_id", String.valueOf(studentId));
        m.put("faculty_id", String.valueOf(facultyId));
        m.put("schedule_date", date);
        m.put("time_slot", timeSlot);
        m.put("category", category);
        m.put("location", location);
        m.put("purpose", purpose);
        enqueue(api.scheduleOne(m), cb);
    }

    public void cancelBooking(int scheduleId, int studentId, ResultCallback<Object> cb) {
        if (badId(studentId, cb)) return;
        Map<String, String> m = req("cancel");
        m.put("schedule_id", String.valueOf(scheduleId));
        m.put("student_id", String.valueOf(studentId));
        enqueue(api.scheduleSimple(m), cb);
    }

    // ---------- notifications ----------

    public void loadNotifications(int userId, String role, ResultCallback<List<NotificationItem>> cb) {
        if (badId(userId, cb)) return;
        Map<String, String> m = req("list_notifications");
        m.put("user_id", String.valueOf(userId));
        m.put("user_role", role);
        enqueue(api.notificationList(m), cb);
    }

    public void markNotificationsRead(int userId, String role) {
        if (userId <= 0) return;
        Map<String, String> m = req("mark_read");
        m.put("user_id", String.valueOf(userId));
        m.put("user_role", role);
        enqueue(api.scheduleSimple(m), null);
    }

    // ---------- plumbing ----------

    private Map<String, String> req(String action) {
        Map<String, String> m = new HashMap<>();
        m.put("action", action);
        return m;
    }

    private boolean badId(int id, ResultCallback<?> cb) {
        if (id > 0) return false;
        if (cb != null) cb.onError(LOGIN_AGAIN);
        return true;
    }

    private <T> void enqueue(Call<ApiResult<T>> call, final ResultCallback<T> cb) {
        call.enqueue(new Callback<ApiResult<T>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResult<T>> c, @NonNull Response<ApiResult<T>> response) {
                if (cb == null) return;
                ApiResult<T> body = response.body();
                if (response.isSuccessful() && body != null) {
                    if (body.isSuccess()) {
                        cb.onSuccess(body.getData(), body.getMessage());
                    } else {
                        cb.onError(body.getMessage());
                    }
                } else {
                    cb.onError("Server error (" + response.code() + "). Please try again.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResult<T>> c, @NonNull Throwable t) {
                if (cb == null) return;
                if (t instanceof IOException) {
                    cb.onError("No connection. Check your internet and try again.");
                } else {
                    cb.onError("Unexpected reply from the server. Make sure schedules.php is uploaded.");
                }
            }
        });
    }
}
