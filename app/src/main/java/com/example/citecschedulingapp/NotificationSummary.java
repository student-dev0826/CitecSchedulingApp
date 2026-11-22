package com.example.citecschedulingapp;

import java.util.Locale;

/** Recognizes existing notification text without requiring new server fields. */
public final class NotificationSummary {
    public enum Kind { SLOT_REQUESTED, CUSTOM_REQUEST, SLOT_BOOKED, ACCEPTED, DECLINED,
        CANCELLED, WITHDRAWN, TRANSFERRED, EXPIRED, UPDATE }

    private NotificationSummary() {}

    public static Kind classify(String message) {
        if (message == null) return Kind.UPDATE;
        String event = message.trim().toLowerCase(Locale.ROOT);
        // A reason may mention another event; classify only the actual update.
        int reason = event.indexOf(". reason:");
        if (reason >= 0) event = event.substring(0, reason);
        if (event.contains(" expired because ") || event.contains("request expired")) return Kind.EXPIRED;
        if (event.contains(" was transferred ") || event.contains(" transferred ")) return Kind.TRANSFERRED;
        if (event.contains(" withdrew the request ")) return Kind.WITHDRAWN;
        if (event.contains(" cancelled your ") || event.contains(" cancelled the ")
                || event.contains(" cancelled their ")) return Kind.CANCELLED;
        if (event.contains(" has been declined") || event.contains(" was declined")) return Kind.DECLINED;
        if (event.contains(" has been accepted") || event.contains(" was approved")) return Kind.ACCEPTED;
        if (event.startsWith("new appointment request from ")) return Kind.CUSTOM_REQUEST;
        if (event.contains(" requested ") && event.contains("open schedule to accept or decline")) return Kind.SLOT_REQUESTED;
        if (event.contains(" booked your ") || event.contains("has been booked by ")) return Kind.SLOT_BOOKED;
        return Kind.UPDATE;
    }
}
