package com.nabeel.mydhikr;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.JavascriptInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exposed to the page as window.DhikrNative */
public class Bridge {
    private final Activity act;
    private final SharedPreferences prefs;

    Bridge(Activity a) { act = a; prefs = a.getSharedPreferences("dhikr", 0); }

    /** Settings and today's progress, used by the reminders. */
    @JavascriptInterface
    public void update(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject st = o.getJSONObject("settings");
            String today = o.optString("today");
            SharedPreferences.Editor e = prefs.edit()
                .putBoolean("remind", st.optBoolean("remind", true))
                .putString("morning", st.optString("morning", "07:00"))
                .putString("night", st.optString("night", "21:00"))
                .putString("all", o.optJSONArray("all") != null ? o.getJSONArray("all").toString() : "[]")
                .putString("left", o.optJSONArray("left") != null ? o.getJSONArray("left").toString() : "[]")
                .putString("leftDate", today);
            if (o.optBoolean("doneToday")) e.putString("doneDate", today);
            else if (today.equals(prefs.getString("doneDate", ""))) e.remove("doneDate");
            e.apply();
            Reminders.schedule(act);
        } catch (Exception ignored) {}
    }

    /** Called after every change: keeps an internal copy and the backup file in Documents/My Dhikr. */
    @JavascriptInterface
    public void autoBackup(String json) {
        Storage.saveInternal(act, json);
        if (Storage.saveDocuments(act, json) != null || Build.VERSION.SDK_INT < 29)
            prefs.edit().putLong("lastBackup", System.currentTimeMillis()).apply();
    }

    @JavascriptInterface
    public String loadSaved() { return Storage.loadInternal(act); }

    @JavascriptInterface
    public String lastBackup() {
        long t = prefs.getLong("lastBackup", 0);
        return t == 0 ? "" : new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(new Date(t));
    }

    /** Opens the share menu with the backup file (Google Drive, WhatsApp, email...). */
    @JavascriptInterface
    public void shareBackup(final String json) {
        act.runOnUiThread(() -> {
            Storage.saveInternal(act, json);
            Uri u = Storage.saveDocuments(act, json);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_SUBJECT, "My Dhikr backup");
            if (u != null) {
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, json);
            }
            act.startActivity(Intent.createChooser(i, "Save your backup to…"));
        });
    }

    /** "[350,180,350]" = buzz, pause, buzz (milliseconds). A single number = one buzz. */
    @JavascriptInterface
    public void vibrate(String pattern) {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) v = ((VibratorManager) act.getSystemService(Activity.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
            else v = (Vibrator) act.getSystemService(Activity.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            String p = pattern.trim();
            if (p.startsWith("[")) {
                JSONArray a = new JSONArray(p);
                long[] t = new long[a.length() + 1];
                t[0] = 0;
                for (int k = 0; k < a.length(); k++) t[k + 1] = a.getLong(k);
                v.vibrate(VibrationEffect.createWaveform(t, -1));
            } else {
                long ms = Long.parseLong(p);
                v.vibrate(VibrationEffect.createOneShot(ms, ms < 30 ? 90 : VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}
    }
}
