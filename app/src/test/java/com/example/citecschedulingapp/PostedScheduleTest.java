package com.example.citecschedulingapp;

import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class PostedScheduleTest {
    @Test public void parsedDatesSurviveRepeatedReadsAndStayOutOfJson() {
        Gson gson = new Gson();
        PostedSchedule slot = gson.fromJson("{\"start_at\":\"2026-11-03 09:00:00\",\"end_at\":\"2026-11-03 10:00:00\"}", PostedSchedule.class);
        assertEquals(AppTime.parseDateTime("2026-11-03 09:00:00"), slot.getStartMillis());
        assertEquals(3600000L, slot.getEndMillis() - slot.getStartMillis());
        assertFalse(gson.toJson(slot).contains("Millis"));
        PostedSchedule refreshed = gson.fromJson("{\"start_at\":\"2026-11-03 11:00:00\"}", PostedSchedule.class);
        assertEquals(7200000L, refreshed.getStartMillis() - slot.getStartMillis());
    }

    @Test public void missingDatesRemainUnavailable() {
        PostedSchedule slot = new PostedSchedule();
        assertEquals(0L, slot.getStartMillis());
        assertEquals(0L, slot.getEndMillis());
        assertFalse(slot.hasEnded());
    }
}
