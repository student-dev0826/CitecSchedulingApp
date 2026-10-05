package com.example.citecschedulingapp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One place for "what time is it" so the app, the clock and the server all agree (Philippine time). */
public final class AppTime {

    public static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Manila");

    private AppTime() {}

    /** Display formatter in Philippine time. */
    public static SimpleDateFormat display(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.getDefault());
        f.setTimeZone(ZONE);
        return f;
    }

    /** Fixed-format formatter (for values exchanged with the server). */
    public static SimpleDateFormat fixed(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(ZONE);
        return f;
    }

    /** "2026-10-26 10:00:00" -> millis, or 0 if it can't be read. */
    public static long parseDateTime(String s) {
        if (s == null) return 0L;
        try {
            Date d = fixed("yyyy-MM-dd HH:mm:ss").parse(s);
            return d != null ? d.getTime() : 0L;
        } catch (ParseException e) {
            return 0L;
        }
    }

    /** "2026-10-26" -> "Oct 26, 2026". */
    public static String prettyDate(String iso) {
        if (iso == null) return "";
        try {
            Date d = fixed("yyyy-MM-dd").parse(iso);
            return d != null ? display("MMM d, yyyy").format(d) : iso;
        } catch (ParseException e) {
            return iso;
        }
    }

    public static String todayIso() {
        return fixed("yyyy-MM-dd").format(new Date());
    }

    public static String isoForDaysFromToday(int days) {
        Calendar c = Calendar.getInstance(ZONE);
        c.add(Calendar.DAY_OF_YEAR, days);
        return fixed("yyyy-MM-dd").format(c.getTime());
    }

    /** "Today", "Tomorrow" or "Oct 26, 2026". */
    public static String dayLabel(String iso) {
        if (iso == null) return "";
        if (iso.equals(todayIso())) return "Today";
        if (iso.equals(isoForDaysFromToday(1))) return "Tomorrow";
        return prettyDate(iso);
    }

    /** "2026-10-05 14:30:00" -> "Oct 5, 2:30 PM". */
    public static String prettyDateTime(String s) {
        long ms = parseDateTime(s);
        return ms == 0L ? "" : display("MMM d, h:mm a").format(new Date(ms));
    }
}
