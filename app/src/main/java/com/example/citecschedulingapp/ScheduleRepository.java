package com.example.citecschedulingapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ScheduleRepository {

    private static final String PREF_NAME = "CITEC_SCHEDULE_REPOSITORY";
    private static final String KEY_POSTED_SCHEDULES = "postedSchedules";
    private static ScheduleRepository instance;

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    private ScheduleRepository(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized ScheduleRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleRepository(context);
        }
        return instance;
    }

    public List<PostedSchedule> getAllPostedSchedules() {
        String json = sharedPreferences.getString(KEY_POSTED_SCHEDULES, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<PostedSchedule>>() {}.getType();
        List<PostedSchedule> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void saveAllPostedSchedules(List<PostedSchedule> schedules) {
        String json = gson.toJson(schedules);
        sharedPreferences.edit().putString(KEY_POSTED_SCHEDULES, json).apply();
    }

    public void addPostedSchedule(PostedSchedule schedule) {
        List<PostedSchedule> list = getAllPostedSchedules();
        list.add(schedule);
        saveAllPostedSchedules(list);
    }

    public List<PostedSchedule> getSchedulesByFaculty(String facultyName) {
        List<PostedSchedule> all = getAllPostedSchedules();
        List<PostedSchedule> filtered = new ArrayList<>();
        for (PostedSchedule s : all) {
            if (s.getFacultyName() != null && s.getFacultyName().equalsIgnoreCase(facultyName)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public List<PostedSchedule> getUnbookedSchedulesByFaculty(String facultyName) {
        List<PostedSchedule> all = getAllPostedSchedules();
        List<PostedSchedule> filtered = new ArrayList<>();
        for (PostedSchedule s : all) {
            if (s.getFacultyName() != null && s.getFacultyName().equalsIgnoreCase(facultyName) && !s.isBooked()) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public List<PostedSchedule> getBookedSchedulesByFaculty(String facultyName) {
        List<PostedSchedule> all = getAllPostedSchedules();
        List<PostedSchedule> filtered = new ArrayList<>();
        for (PostedSchedule s : all) {
            if (s.getFacultyName() != null && s.getFacultyName().equalsIgnoreCase(facultyName) && s.isBooked()) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public List<String> getAllFacultyNamesWithPostedSchedules() {
        List<PostedSchedule> all = getAllPostedSchedules();
        List<String> faculties = new ArrayList<>();
        for (PostedSchedule s : all) {
            if (s.getFacultyName() != null && !s.getFacultyName().isEmpty() && !s.isBooked() && !faculties.contains(s.getFacultyName())) {
                faculties.add(s.getFacultyName());
            }
        }
        return faculties;
    }

    public boolean bookSchedule(String scheduleId, String studentName) {
        List<PostedSchedule> all = getAllPostedSchedules();
        boolean found = false;
        for (PostedSchedule s : all) {
            if (s.getId().equals(scheduleId)) {
                s.setBooked(true);
                s.setBookedByStudentName(studentName);
                found = true;
                break;
            }
        }
        if (found) {
            saveAllPostedSchedules(all);
        }
        return found;
    }

    public void clearAllSchedules() {
        sharedPreferences.edit().remove(KEY_POSTED_SCHEDULES).apply();
    }
}
