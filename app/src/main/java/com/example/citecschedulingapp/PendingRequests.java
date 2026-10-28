package com.example.citecschedulingapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Main-thread-only fan-out for identical reads. Results are never cached. */
final class PendingRequests<T> {
    private final Map<String, List<T>> pending = new HashMap<>();

    boolean join(String key, T listener) {
        List<T> listeners = pending.get(key);
        boolean first = listeners == null;
        if (first) {
            listeners = new ArrayList<>();
            pending.put(key, listeners);
        }
        listeners.add(listener);
        return first;
    }

    List<T> finish(String key) {
        List<T> listeners = pending.remove(key);
        return listeners == null ? new ArrayList<>() : listeners;
    }
}
