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
import com.example.citecschedulingapp.ScheduleRepository;
import com.example.citecschedulingapp.SessionManager;
import com.example.citecschedulingapp.model.ChatConversation;
import com.example.citecschedulingapp.model.FacultyItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Student chat list: every professor, unread ones highlighted and sorted to the top. */
public class ChatFragment extends Fragment {

    private static final long REFRESH_MS = 5000;

    private LinearLayout containerFacultyContacts;
    private SessionManager sessionManager;
    private ScheduleRepository scheduleRepository;
    private ChatRepository chatRepository;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private List<FacultyItem> faculty;                 // null until loaded
    private List<ChatConversation> conversations = new ArrayList<>();
    private String lastSignature = "";

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
    }

    @Override
    public void onResume() {
        super.onResume();
        lastSignature = "";            // redraw right away (e.g. after reading a chat)
        handler.post(refreshTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    private String myId() {
        return sessionManager != null ? sessionManager.getStudentId() : "";
    }

    private void refresh() {
        if (chatRepository == null || scheduleRepository == null) return;

        chatRepository.loadConversations(myId(), new ChatRepository.Result<List<ChatConversation>>() {
            @Override public void onSuccess(List<ChatConversation> data) {
                if (!isAdded()) return;
                conversations = data != null ? data : new ArrayList<>();
                render();
            }
            @Override public void onError(String message) { }
        });

        if (faculty == null) {
            scheduleRepository.loadFaculty(new ScheduleRepository.ResultCallback<List<FacultyItem>>() {
                @Override public void onSuccess(List<FacultyItem> data, String message) {
                    if (!isAdded()) return;
                    faculty = data != null ? data : new ArrayList<>();
                    render();
                }
                @Override public void onError(String message) {
                    if (!isAdded()) return;
                    faculty = new ArrayList<>();
                    render();
                }
            });
        }
    }

    private static class Entry {
        String id, name, snippet, time;
        int unread, lastId;
    }

    private void render() {
        if (containerFacultyContacts == null || faculty == null || !isAdded()) return;

        Map<String, ChatConversation> byId = new HashMap<>();
        for (ChatConversation c : conversations) byId.put(c.getContactId().toLowerCase(), c);

        List<Entry> entries = new ArrayList<>();
        for (FacultyItem f : faculty) {
            Entry e = new Entry();
            e.id = "PROF-" + f.getFacultyId();
            e.name = f.getDisplayName();
            ChatConversation c = byId.get(e.id.toLowerCase());
            if (c != null) {
                boolean mine = c.getLastSenderId().equalsIgnoreCase(myId());
                e.snippet = (mine ? "You: " : "") + c.getLastMessage();
                e.time = c.getLastTime();
                e.unread = c.getUnread();
                e.lastId = c.getLastId();
            } else {
                e.snippet = "Tap to start conversation...";
                e.time = "";
            }
            entries.add(e);
        }

        // Unread first, then most recent conversation, then everyone else in server order.
        Collections.sort(entries, (a, b) -> {
            if ((a.unread > 0) != (b.unread > 0)) return a.unread > 0 ? -1 : 1;
            return Integer.compare(b.lastId, a.lastId);
        });

        StringBuilder sig = new StringBuilder();
        for (Entry e : entries) sig.append(e.id).append(':').append(e.unread).append(':').append(e.lastId).append(';');
        if (sig.toString().equals(lastSignature)) return;
        lastSignature = sig.toString();

        containerFacultyContacts.removeAllViews();

        if (entries.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText("No professors found. Check your connection and try again.");
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            containerFacultyContacts.addView(tv);
            return;
        }

        for (Entry e : entries) {
            final Entry entry = e;
            containerFacultyContacts.addView(ChatContactCard.build(requireContext(), e.name, e.snippet, e.time, e.unread, v -> {
                Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
                intent.putExtra("RECIPIENT_ID", entry.id);
                intent.putExtra("RECIPIENT_NAME", entry.name);
                intent.putExtra("RECIPIENT_ROLE", "Faculty / Professor");
                startActivity(intent);
            }));
        }
    }
}
