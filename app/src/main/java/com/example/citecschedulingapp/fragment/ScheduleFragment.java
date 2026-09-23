package com.example.citecschedulingapp.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.R;
import com.google.android.material.button.MaterialButton;

public class ScheduleFragment extends Fragment {

    private MaterialButton btnDayMon, btnDayTue, btnDayWed, btnDayThu, btnDayFri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnDayMon = view.findViewById(R.id.btnDayMon);
        btnDayTue = view.findViewById(R.id.btnDayTue);
        btnDayWed = view.findViewById(R.id.btnDayWed);
        btnDayThu = view.findViewById(R.id.btnDayThu);
        btnDayFri = view.findViewById(R.id.btnDayFri);

        View.OnClickListener dayClickListener = v -> {
            if (v instanceof MaterialButton && getContext() != null) {
                String day = ((MaterialButton) v).getText().toString();
                Toast.makeText(getContext(), "Showing schedule for " + day, Toast.LENGTH_SHORT).show();
            }
        };

        if (btnDayMon != null) btnDayMon.setOnClickListener(dayClickListener);
        if (btnDayTue != null) btnDayTue.setOnClickListener(dayClickListener);
        if (btnDayWed != null) btnDayWed.setOnClickListener(dayClickListener);
        if (btnDayThu != null) btnDayThu.setOnClickListener(dayClickListener);
        if (btnDayFri != null) btnDayFri.setOnClickListener(dayClickListener);
    }
}
