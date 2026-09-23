package com.example.citecschedulingapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.citecschedulingapp.model.User;

public class SessionManager {

    private static final String PREF_NAME = "CITEC_USER_SESSION";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_STUDENT_ID = "studentId";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveUserSession(User user) {
        if (user == null) return;
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, user.getUserId());
        editor.putString(KEY_STUDENT_ID, user.getStudentId());
        editor.putString(KEY_FULL_NAME, user.getFullName());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, -1);
    }

    public String getStudentId() {
        return sharedPreferences.getString(KEY_STUDENT_ID, "");
    }

    public String getFullName() {
        return sharedPreferences.getString(KEY_FULL_NAME, "");
    }

    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, "");
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
