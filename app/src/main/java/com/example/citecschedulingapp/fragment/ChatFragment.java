package com.example.citecschedulingapp.fragment;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.ChatDetailActivity;
import com.example.citecschedulingapp.ChatRepository;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.ChatMessage;
import com.example.citecschedulingapp.model.FacultyItem;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {

    private LinearLayout containerFacultyContacts;

    private SessionManager sessionManager;
    private ScheduleRepository scheduleRepository;
    private ChatRepository chatRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            sessionManager = new SessionManager(getContext());
            scheduleRepository = ScheduleRepository.getInstance(getContext());
            chatRepository = ChatRepository.getInstance(getContext());
        }

        containerFacultyContacts = view.findViewById(R.id.containerFacultyContacts);

        loadFacultyContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFacultyContacts();
    }

    private void loadFacultyContacts() {
        if (containerFacultyContacts == null || scheduleRepository == null) return;

        scheduleRepository.loadFaculty(new ScheduleRepository.ResultCallback<List<FacultyItem>>() {
            @Override
            public void onSuccess(List<FacultyItem> data, String message) {
                if (data != null && !data.isEmpty()) {
                    displayFacultyList(data);
                } else {
                    displayFallbackFacultyList();
                }
            }

            @Override
            public void onError(String message) {
                displayFallbackFacultyList();
            }
        });
    }

    private void displayFacultyList(List<FacultyItem> facultyList) {
        if (containerFacultyContacts == null) return;
        containerFacultyContacts.removeAllViews();

        String currentStudentId = sessionManager != null ? sessionManager.getStudentId() : "STUDENT-01";

        for (FacultyItem f : facultyList) {
            String profId = "PROF-" + f.getFacultyId();
            String profName = f.getDisplayName();
            ChatMessage lastMsg = chatRepository != null ? chatRepository.getLastMessage(currentStudentId, profId) : null;
            View card = createContactCardView(profName, profId, lastMsg);
            containerFacultyContacts.addView(card);
        }
    }

    private void displayFallbackFacultyList() {
        if (containerFacultyContacts == null) return;
        containerFacultyContacts.removeAllViews();

        List<String> fallbackNames = new ArrayList<>();
        fallbackNames.add("Prof. Juan Santos (IT Dept)");
        fallbackNames.add("Dr. Ricardo Reyes (CS Dept)");
        fallbackNames.add("Engr. Gabriel Mendoza (IS Dept)");

        String currentStudentId = sessionManager != null ? sessionManager.getStudentId() : "STUDENT-01";

        for (String profName : fallbackNames) {
            String profId = "PROF-" + Math.abs(profName.hashCode() % 1000);
            ChatMessage lastMsg = chatRepository != null ? chatRepository.getLastMessage(currentStudentId, profId) : null;
            View card = createContactCardView(profName, profId, lastMsg);
            containerFacultyContacts.addView(card);
        }
    }

    private View createContactCardView(String profName, String profId, ChatMessage lastMsg) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setCardElevation(2f);
        card.setRadius(16f);
        card.setStrokeColor(getResources().getColor(R.color.border_color));
        card.setStrokeWidth(1);

        RelativeLayout layout = new RelativeLayout(requireContext());
        layout.setPadding(32, 28, 32, 28);

        LinearLayout textLayout = new LinearLayout(requireContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(requireContext());
        tvName.setText(profName);
        tvName.setTextSize(16f);
        tvName.setTextColor(getResources().getColor(R.color.primary));
        tvName.setTypeface(null, Typeface.BOLD);

        TextView tvSnippet = new TextView(requireContext());
        String snippet = lastMsg != null ? lastMsg.getMessageText() : "Tap to start conversation...";
        tvSnippet.setText(snippet);
        tvSnippet.setTextSize(13f);
        tvSnippet.setTextColor(getResources().getColor(R.color.text_secondary));
        tvSnippet.setPadding(0, 4, 0, 0);

        textLayout.addView(tvName);
        textLayout.addView(tvSnippet);

        layout.addView(textLayout);
        card.addView(layout);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
            intent.putExtra("RECIPIENT_ID", profId);
            intent.putExtra("RECIPIENT_NAME", profName);
            intent.putExtra("RECIPIENT_ROLE", "Faculty / Professor");
            startActivity(intent);
        });

        return card;
    }
}
