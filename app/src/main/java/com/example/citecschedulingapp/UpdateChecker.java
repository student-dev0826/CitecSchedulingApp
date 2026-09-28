package com.example.citecschedulingapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks version.json on your server and offers to download + install a newer APK.
 * Call UpdateChecker.check(this) from onCreate() of LoginActivity, HomeActivity and FacultyHomeActivity.
 */
public class UpdateChecker {

    // version.json lives on your own hosting, so you can edit it without rebuilding the app.
    public static final String VERSION_URL = "https://citecscheduling.x10.mx/api/version.json";

    private static boolean checked = false;

    public static void check(final Activity activity) {
        if (checked) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject j = new JSONObject(sb.toString());
                final long latest = j.getLong("versionCode");
                final String name = j.optString("versionName", "");
                final String apkUrl = j.getString("apkUrl");
                final String notes = j.optString("notes", "");
                final boolean force = j.optBoolean("force", false);

                long current = PackageInfoCompat.getLongVersionCode(
                        activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0));

                if (latest <= current) { checked = true; return; }

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return; // retry from next screen
                    checked = true;
                    showDialog(activity, name, notes, apkUrl, force);
                });
            } catch (Exception ignored) {
                // No internet / bad JSON: silently skip, never block the app.
            }
        }).start();
    }

    private static void showDialog(Activity a, String name, String notes, String apkUrl, boolean force) {
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Update available" + (name.isEmpty() ? "" : " (v" + name + ")"))
                .setMessage(notes.isEmpty() ? "A new version of CITEC Scheduling is available." : notes)
                .setCancelable(!force)
                .setPositiveButton("Update", (d, w) -> startUpdate(a, apkUrl));
        if (!force) b.setNegativeButton("Later", null);
        b.show();
    }

    private static void startUpdate(Activity a, String apkUrl) {
        // Android needs a one-time "install unknown apps" permission for this app.
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow installs from this app, then reopen the app to update.", Toast.LENGTH_LONG).show();
            checked = false;
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }

        final Context app = a.getApplicationContext();
        final File out = new File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CitecScheduling-update.apk");
        if (out.exists()) out.delete();

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("CITEC Scheduling update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(out));
        final DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        final long id = dm.enqueue(req);
        Toast.makeText(app, "Downloading update...", Toast.LENGTH_SHORT).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                app.unregisterReceiver(this);

                boolean ok = false;
                try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cur != null && cur.moveToFirst()) {
                        ok = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                == DownloadManager.STATUS_SUCCESSFUL;
                    }
                }
                if (ok) install(app, out);
                else Toast.makeText(app, "Update download failed. Please try again later.", Toast.LENGTH_LONG).show();
            }
        };
        ContextCompat.registerReceiver(app, receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
    }

    private static void install(Context ctx, File apk) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
