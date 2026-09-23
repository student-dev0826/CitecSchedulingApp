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

public class AppointmentsFragment extends Fragment {

    private MaterialButton btnFilterAll;
    private MaterialButton btnFilterUpcoming;
    private MaterialButton btnFilterCompleted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appointments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnFilterAll = view.findViewById(R.id.btnFilterAll);
        btnFilterUpcoming = view.findViewById(R.id.btnFilterUpcoming);
        btnFilterCompleted = view.findViewById(R.id.btnFilterCompleted);

        View.OnClickListener filterListener = v -> {
            if (v instanceof MaterialButton && getContext() != null) {
                String filterName = ((MaterialButton) v).getText().toString();
                Toast.makeText(getContext(), "Filter applied: " + filterName, Toast.LENGTH_SHORT).show();
            }
        };

        if (btnFilterAll != null) btnFilterAll.setOnClickListener(filterListener);
        if (btnFilterUpcoming != null) btnFilterUpcoming.setOnClickListener(filterListener);
        if (btnFilterCompleted != null) btnFilterCompleted.setOnClickListener(filterListener);
    }
}
