package com.example.citecschedulingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.citecschedulingapp.fragment.FacultyAvailabilityFragment;
import com.example.citecschedulingapp.fragment.FacultyChatFragment;
import com.example.citecschedulingapp.fragment.FacultyHomeFragment;
import com.example.citecschedulingapp.fragment.FacultyScheduleFragment;
import com.example.citecschedulingapp.fragment.FacultyTransferFragment;
import com.example.citecschedulingapp.fragment.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class FacultyHomeActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private SessionManager sessionManager;
    private ChatUnreadMonitor chatMonitor;

    // Lets "Transfer" on a booked card open the Transfer tab with that appointment preselected.
    private int pendingTransferId = -1;

    public void setPendingTransferId(int scheduleId) {
        pendingTransferId = scheduleId;
    }

    public int consumePendingTransferId() {
        int id = pendingTransferId;
        pendingTransferId = -1;
        return id;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UpdateChecker.check(this);

        sessionManager = new SessionManager(this);

        // Security check: if user is not logged in, redirect to LoginActivity immediately
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        // Role check: if user is not faculty/professor, redirect to HomeActivity (Student portal)
        if (!sessionManager.isFaculty()) {
            startActivity(new Intent(FacultyHomeActivity.this, HomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_faculty_home);

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        setupBottomNavigation();

        chatMonitor = new ChatUnreadMonitor(this, bottomNavigationView, R.id.nav_faculty_chat, "PROF-" + sessionManager.getUserId());
        ChatUnreadMonitor.requestNotificationPermission(this);

        // Load default Faculty Home fragment if starting fresh
        if (savedInstanceState == null) {
            loadFragment(new FacultyHomeFragment());
        }
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment fragment = null;

            if (itemId == R.id.nav_faculty_home) {
                fragment = new FacultyHomeFragment();
            } else if (itemId == R.id.nav_faculty_schedule) {
                fragment = new FacultyScheduleFragment();
            } else if (itemId == R.id.nav_faculty_availability) {
                fragment = new FacultyAvailabilityFragment();
            } else if (itemId == R.id.nav_faculty_transfer) {
                fragment = new FacultyTransferFragment();
            } else if (itemId == R.id.nav_faculty_chat) {
                fragment = new FacultyChatFragment();
            } else if (itemId == R.id.nav_faculty_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatMonitor != null) chatMonitor.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatMonitor != null) chatMonitor.stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(ChatUnreadMonitor.EXTRA_OPEN_CHAT, false)) {
            selectTab(R.id.nav_faculty_chat);
        }
    }

    public void selectTab(int itemId) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(itemId);
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void performLogout() {
        // Clear SharedPreferences session
        sessionManager.logout();

        Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();

        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(FacultyHomeActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
