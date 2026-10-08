package com.example.citecschedulingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.citecschedulingapp.fragment.AppointmentsFragment;
import com.example.citecschedulingapp.fragment.BookFragment;
import com.example.citecschedulingapp.fragment.ChatFragment;
import com.example.citecschedulingapp.fragment.HomeFragment;
import com.example.citecschedulingapp.fragment.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private SessionManager sessionManager;

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

        // Role check: if user is faculty/professor, redirect to FacultyHomeActivity
        if (sessionManager.isFaculty()) {
            startActivity(new Intent(HomeActivity.this, FacultyHomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_home);

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        setupBottomNavigation();

        // Load default Home fragment if starting fresh
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment fragment = null;

            if (itemId == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (itemId == R.id.nav_book) {
                fragment = new BookFragment();
            } else if (itemId == R.id.nav_chat) {
                fragment = new ChatFragment();
            } else if (itemId == R.id.nav_appointments) {
                fragment = new AppointmentsFragment();
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
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
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        // Clear Activity back stack to prevent going back to HomeActivity with Back button
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
