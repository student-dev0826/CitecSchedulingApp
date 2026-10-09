package com.example.citecschedulingapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.citecschedulingapp.ChatContactCard;
import com.example.citecschedulingapp.ChatDetailActivity;
import com.example.citecschedulingapp.ChatRepository;
import com.example.citecschedulingapp.R;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.ChatConversation;

import java.util.List;

/** Faculty chat list: students who messaged, unread ones highlighted and first. */
public class FacultyChatFragment extends Fragment {

    private static final long REFRESH_MS = 5000;

    private LinearLayout containerStudentContacts;
    private SessionManager sessionManager;
    private ChatRepository chatRepository;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };
    private String lastSignature = "";

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
    }

    @Override
    public void onResume() {
        super.onResume();
        lastSignature = "";
        handler.post(refreshTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    private String myId() {
        return sessionManager != null ? "PROF-" + sessionManager.getUserId() : "";
    }

    private void refresh() {
        if (chatRepository == null) return;
        chatRepository.loadConversations(myId(), new ChatRepository.Result<List<ChatConversation>>() {
            @Override public void onSuccess(List<ChatConversation> data) {
                if (isAdded()) render(data);
            }
            @Override public void onError(String message) { }
        });
    }

    private void render(List<ChatConversation> data) {
        if (containerStudentContacts == null) return;

        // Server already sorts by newest; pull unread ones to the top, keeping that order.
        java.util.ArrayList<ChatConversation> list = new java.util.ArrayList<>();
        if (data != null) {
            for (ChatConversation c : data) if (c.getUnread() > 0) list.add(c);
            for (ChatConversation c : data) if (c.getUnread() == 0) list.add(c);
        }

        StringBuilder sig = new StringBuilder();
        for (ChatConversation c : list) sig.append(c.getContactId()).append(':').append(c.getUnread()).append(':').append(c.getLastId()).append(';');
        if (sig.toString().equals(lastSignature)) return;
        lastSignature = sig.toString();

        containerStudentContacts.removeAllViews();

        if (list.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText("No messages yet. Students who message you will appear here.");
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            containerStudentContacts.addView(tv);
            return;
        }

        for (ChatConversation c : list) {
            final String studentId = c.getContactId();
            final String studentName = !c.getContactName().isEmpty() ? c.getContactName() : "Student (" + studentId + ")";
            boolean mine = c.getLastSenderId().equalsIgnoreCase(myId());
            String snippet = (mine ? "You: " : "") + c.getLastMessage();

            containerStudentContacts.addView(ChatContactCard.build(requireContext(),
                    studentName + " (" + studentId + ")", snippet, c.getLastTime(), c.getUnread(), v -> {
                Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
                intent.putExtra("RECIPIENT_ID", studentId);
                intent.putExtra("RECIPIENT_NAME", studentName);
                intent.putExtra("RECIPIENT_ROLE", "Student");
                startActivity(intent);
            }));
        }
    }
}
