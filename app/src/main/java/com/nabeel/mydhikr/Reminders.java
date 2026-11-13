package com.nabeel.mydhikr;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;

/** Morning and night reminders, skipped when all of today's dhikr are done. */
public class Reminders extends BroadcastReceiver {
    static final String CHANNEL = "reminders";

    static void schedule(Context c) {
        SharedPreferences p = c.getSharedPreferences("dhikr", 0);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        String[] times = {p.getString("morning", "07:00"), p.getString("night", "21:00")};
        for (int kind = 0; kind < 2; kind++) {
            Intent i = new Intent(c, Reminders.class).putExtra("kind", kind);
            PendingIntent pi = PendingIntent.getBroadcast(c, kind, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            if (!p.getBoolean("remind", true)) continue;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(times[kind]), pi);
        }
    }

    static long next(String hhmm) {
        int h = 7, m = 0;
        try { String[] a = hhmm.split(":"); h = Integer.parseInt(a[0]); m = Integer.parseInt(a[1]); } catch (Exception ignored) {}
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        schedule(c);   // always line up the next ones
        if (action != null) return;   // boot / time change / app update
        SharedPreferences p = c.getSharedPreferences("dhikr", 0);
        if (!p.getBoolean("remind", true)) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(p.getString("doneDate", ""))) return;   // all done today: stay quiet

        List<String> left = names(today.equals(p.getString("leftDate", "")) ? p.getString("left", "[]") : p.getString("all", "[]"));
        if (left.isEmpty() && today.equals(p.getString("leftDate", ""))) return;
        boolean morning = intent.getIntExtra("kind", 0) == 0;
        String title = morning ? "Time for your dhikr" : "You haven't finished your dhikr today";
        String body = left.isEmpty() ? "Open My Dhikr to start." : (morning ? "Today: " : "Still left: ") + list(left);
        post(c, morning ? 1 : 2, title, body);
    }

    static List<String> names(String json) {
        ArrayList<String> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(json); for (int i = 0; i < a.length(); i++) out.add(a.getString(i)); } catch (Exception ignored) {}
        return out;
    }

    static String list(List<String> l) {
        if (l.size() <= 3) return String.join(", ", l);
        return String.join(", ", l.subList(0, 3)) + " and " + (l.size() - 3) + " more";
    }

    static void post(Context c, int id, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Dhikr reminders", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF0D5A45)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        nm.cancel(id == 1 ? 2 : 1);
        nm.notify(id, n);
    }
}
