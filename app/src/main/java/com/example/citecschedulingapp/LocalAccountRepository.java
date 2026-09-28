package com.example.citecschedulingapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.citecschedulingapp.model.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LocalAccountRepository {

    private static final String PREF_NAME = "CITEC_LOCAL_ACCOUNTS";
    private static final String KEY_ACCOUNT_LIST = "accountsList";
    private static LocalAccountRepository instance;

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    public static class AccountEntry {
        public String studentId;
        public String firstName;
        public String lastName;
        public String fullName;
        public String email;
        public String password;
        public String role;
        public String department;

        public AccountEntry() {
        }

        public AccountEntry(String studentId, String firstName, String lastName, String email, String password, String role, String department) {
            this.studentId = studentId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.fullName = (firstName + " " + lastName).trim();
            this.email = email;
            this.password = password;
            this.role = role;
            this.department = department;
        }

        public User toUser(int userId) {
            return new User(userId, studentId, firstName, lastName, email, role);
        }
    }

    private LocalAccountRepository(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized LocalAccountRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LocalAccountRepository(context);
        }
        return instance;
    }

    public List<AccountEntry> getAccounts() {
        String json = sharedPreferences.getString(KEY_ACCOUNT_LIST, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<AccountEntry>>() {}.getType();
        List<AccountEntry> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void saveAccount(String studentId, String firstName, String lastName, String email, String password, String role, String department) {
        List<AccountEntry> list = getAccounts();
        for (AccountEntry entry : list) {
            if (entry.email.equalsIgnoreCase(email) || entry.studentId.equalsIgnoreCase(studentId)) {
                entry.firstName = firstName;
                entry.lastName = lastName;
                entry.fullName = (firstName + " " + lastName).trim();
                entry.password = password;
                entry.role = role;
                entry.department = department;
                sharedPreferences.edit().putString(KEY_ACCOUNT_LIST, gson.toJson(list)).apply();
                return;
            }
        }
        list.add(new AccountEntry(studentId, firstName, lastName, email, password, role, department));
        sharedPreferences.edit().putString(KEY_ACCOUNT_LIST, gson.toJson(list)).apply();
    }

    public AccountEntry authenticate(String identifier, String password, String targetRole) {
        List<AccountEntry> list = getAccounts();
        for (AccountEntry entry : list) {
            if ((entry.email.equalsIgnoreCase(identifier) || entry.studentId.equalsIgnoreCase(identifier))
                    && entry.password.equals(password)) {
                if (targetRole == null || targetRole.isEmpty() || entry.role.equalsIgnoreCase(targetRole)) {
                    return entry;
                }
            }
        }
        return null;
    }
}
