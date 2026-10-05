package com.example.citecschedulingapp;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Shows the device's current date and time and refreshes every second.
 * Call start() in onResume() and stop() in onPause() so it never leaks.
 */
public class LiveClock {

    private final TextView dateView;
    private final TextView timeView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = AppTime.display("EEEE, MMMM d, yyyy");
    private final SimpleDateFormat timeFormat = AppTime.display("hh:mm:ss a");

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            if (dateView != null) dateView.setText(dateFormat.format(now));
            if (timeView != null) timeView.setText(timeFormat.format(now));
            // Re-run exactly on the next second boundary so the clock never drifts.
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    public LiveClock(TextView dateView, TextView timeView) {
        this.dateView = dateView;
        this.timeView = timeView;
    }

    public void start() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        handler.removeCallbacks(tick);
    }
}
