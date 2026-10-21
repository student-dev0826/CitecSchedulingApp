package com.example.citecschedulingapp;

import com.example.citecschedulingapp.model.PostedSchedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Groups only bookable slots. Used for both calendar counts and the expanded day list. */
public final class BookingCalendar {
    private BookingCalendar() { }

    public static Map<String, List<PostedSchedule>> availableByDate(List<PostedSchedule> slots, String query, long now) {
        Map<String, List<PostedSchedule>> dates = new TreeMap<>();
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (PostedSchedule slot : slots) {
            if (slot == null || slot.getId() <= 0 || !"OPEN".equalsIgnoreCase(slot.getStatus())
                    || slot.getStudentId() != null || slot.getDate() == null || slot.getStartMillis() <= now) continue;
            String faculty = slot.getFacultyDisplayName() + " " + (slot.getDepartment() == null ? "" : slot.getDepartment());
            if (!search.isEmpty() && !faculty.toLowerCase(Locale.ROOT).contains(search)) continue;
            if (!dates.containsKey(slot.getDate())) dates.put(slot.getDate(), new ArrayList<>());
            dates.get(slot.getDate()).add(slot);
        }
        Comparator<PostedSchedule> order = Comparator.comparingLong(PostedSchedule::getStartMillis)
                .thenComparing(PostedSchedule::getFacultyDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(PostedSchedule::getId);
        for (List<PostedSchedule> day : dates.values()) day.sort(order);
        return dates;
    }
}
