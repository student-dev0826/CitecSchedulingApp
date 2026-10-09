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
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.ChatMessage;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class FacultyChatFragment extends Fragment {

    private LinearLayout containerStudentContacts;

    private SessionManager sessionManager;
    private ChatRepository chatRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_faculty_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() != null) {
            sessionManager = new SessionManager(getContext());
            chatRepository = ChatRepository.getInstance(getContext());
        }

        containerStudentContacts = view.findViewById(R.id.containerStudentContacts);

        loadStudentContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStudentContacts();
    }

    private void loadStudentContacts() {
        if (containerStudentContacts == null || chatRepository == null) return;

        containerStudentContacts.removeAllViews();

        String currentProfId = sessionManager != null ? "PROF-" + sessionManager.getUserId() : "PROF-01";

        List<ChatRepository.ContactInfo> studentContacts = chatRepository.getRecentContactsForUser(currentProfId, true);

        if (studentContacts.isEmpty()) {
            studentContacts.add(new ChatRepository.ContactInfo("3331650", "Farancis Val", "Student", null));
            studentContacts.add(new ChatRepository.ContactInfo("2026-001", "Juan Dela Cruz", "Student", null));
            studentContacts.add(new ChatRepository.ContactInfo("2026-002", "Maria Clara", "Student", null));
        }

        for (ChatRepository.ContactInfo contact : studentContacts) {
            String studentId = contact.contactId;
            String studentName = contact.contactName != null && !contact.contactName.isEmpty() ? contact.contactName : "Student (" + studentId + ")";
            ChatMessage lastMsg = contact.lastMessage != null ? contact.lastMessage : chatRepository.getLastMessage(currentProfId, studentId);
            View card = createContactCardView(studentName, studentId, lastMsg);
            containerStudentContacts.addView(card);
        }
    }

    private View createContactCardView(String studentName, String studentId, ChatMessage lastMsg) {
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
        tvName.setText(studentName + " (" + studentId + ")");
        tvName.setTextSize(16f);
        tvName.setTextColor(getResources().getColor(R.color.primary));
        tvName.setTypeface(null, Typeface.BOLD);

        TextView tvSnippet = new TextView(requireContext());
        String snippet = lastMsg != null ? lastMsg.getMessageText() : "Tap to reply to student...";
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
            intent.putExtra("RECIPIENT_ID", studentId);
            intent.putExtra("RECIPIENT_NAME", studentName);
            intent.putExtra("RECIPIENT_ROLE", "Student");
            startActivity(intent);
        });

        return card;
    }
}
