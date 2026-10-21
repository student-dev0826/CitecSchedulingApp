package com.example.citecschedulingapp;

import com.example.citecschedulingapp.model.PostedSchedule;
import com.google.gson.Gson;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class BookingCalendarTest {
    private final long now = AppTime.parseDateTime("2026-10-21 12:00:00");
    private PostedSchedule slot(int id, String date, String time, String faculty, String department, String status) {
        return new Gson().fromJson("{\"schedule_id\":" + id + ",\"schedule_date\":\"" + date
                + "\",\"start_at\":\"" + date + " " + time + ":00\",\"faculty_name\":\"" + faculty
                + "\",\"department\":\"" + department + "\",\"status\":\"" + status + "\"}", PostedSchedule.class);
    }
    @Test public void countsDifferentProfessorsAndTimesTogetherOnTheirDate() {
        List<PostedSchedule> input = Arrays.asList(slot(1,"2026-11-03","09:00","Alice","IT","OPEN"),
                slot(2,"2026-11-03","09:00","Bob","IT","OPEN"), slot(3,"2026-11-04","10:00","Alice","IT","OPEN"));
        Map<String,List<PostedSchedule>> days = BookingCalendar.availableByDate(input, "", now);
        assertEquals(2, days.get("2026-11-03").size()); assertEquals(1, days.get("2026-11-04").size()); assertEquals(3, input.size());
    }
    @Test public void omitsPastStartedAndUnavailableAppointments() {
        List<PostedSchedule> input = Arrays.asList(slot(1,"2026-10-20","09:00","Alice","IT","OPEN"),
                slot(2,"2026-10-21","12:00","Alice","IT","OPEN"), slot(3,"2026-11-03","09:00","Alice","IT","PENDING"),
                slot(4,"2026-11-03","10:00","Alice","IT","ACCEPTED"), slot(5,"2026-11-03","11:00","Alice","IT","CANCELLED"),
                slot(6,"2026-11-03","13:00","Alice","IT","OPEN"), null);
        Map<String,List<PostedSchedule>> days = BookingCalendar.availableByDate(input, "", now);
        assertEquals(1, days.size()); assertEquals(6, days.get("2026-11-03").get(0).getId());
    }
    @Test public void searchesNamesAndDepartmentsWithoutCaseSensitivity() {
        List<PostedSchedule> input = Arrays.asList(slot(1,"2026-11-03","09:00","Alice","Computing","OPEN"), slot(2,"2026-11-03","09:00","Bob","Math","OPEN"));
        assertEquals(1, BookingCalendar.availableByDate(input,"  COMPUTING  ",now).get("2026-11-03").size());
        assertEquals(2, BookingCalendar.availableByDate(input,"bOb",now).get("2026-11-03").get(0).getId());
        assertTrue(BookingCalendar.availableByDate(input,"missing",now).isEmpty());
    }
    @Test public void sortsChronologicallyThenByProfessorAndId() {
        List<PostedSchedule> input = Arrays.asList(slot(3,"2026-11-03","10:00","Alice","IT","OPEN"), slot(2,"2026-11-03","09:00","Bob","IT","OPEN"), slot(1,"2026-11-03","09:00","Alice","IT","OPEN"));
        List<PostedSchedule> result = BookingCalendar.availableByDate(input,null,now).get("2026-11-03");
        assertEquals(1,result.get(0).getId()); assertEquals(2,result.get(1).getId()); assertEquals(3,result.get(2).getId());
    }
    @Test public void ignoresMalformedAndStudentAttachedOpenRows() {
        PostedSchedule attached = new Gson().fromJson("{\"schedule_id\":1,\"schedule_date\":\"2026-11-03\",\"start_at\":\"2026-11-03 09:00:00\",\"status\":\"OPEN\",\"student_id\":9}",PostedSchedule.class);
        assertTrue(BookingCalendar.availableByDate(Arrays.asList(attached,new PostedSchedule()),"",now).isEmpty());
    }
}
