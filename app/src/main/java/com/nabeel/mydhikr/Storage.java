package com.nabeel.mydhikr;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

class Storage {
    static final String NAME = "my-dhikr-backup.json";
    static final String FOLDER = "My Dhikr";

    static void saveInternal(Context c, String json) {
        try {
            File tmp = new File(c.getFilesDir(), "dhikr.json.tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(json.getBytes(StandardCharsets.UTF_8)); }
            tmp.renameTo(new File(c.getFilesDir(), "dhikr.json"));
        } catch (Exception ignored) {}
    }

    static String loadInternal(Context c) {
        try {
            File f = new File(c.getFilesDir(), "dhikr.json");
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    /** Writes Documents/My Dhikr/my-dhikr-backup.json. Survives uninstalling the app. Returns a shareable Uri on Android 10+. */
    static Uri saveDocuments(Context c, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            SharedPreferences p = c.getSharedPreferences("dhikr", 0);
            String known = p.getString("docUri", null);
            if (known != null) {
                Uri u = Uri.parse(known);
                if (write(cr, u, bytes)) return u;
            }
            try {
                String rel = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/";
                Uri coll = MediaStore.Files.getContentUri("external");
                Uri found = null;
                try (Cursor q = cr.query(coll, new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{rel, NAME}, null)) {
                    if (q != null && q.moveToFirst()) found = Uri.withAppendedPath(coll, String.valueOf(q.getLong(0)));
                }
                if (found == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, NAME);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                    found = cr.insert(coll, v);
                }
                if (found != null && write(cr, found, bytes)) {
                    p.edit().putString("docUri", found.toString()).apply();
                    return found;
                }
            } catch (Exception ignored) {}
            return null;
        } else {
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER);
                dir.mkdirs();
                try (FileOutputStream o = new FileOutputStream(new File(dir, NAME))) { o.write(bytes); }
            } catch (Exception ignored) {}
            return null;
        }
    }

    private static boolean write(ContentResolver cr, Uri u, byte[] bytes) {
        try (OutputStream o = cr.openOutputStream(u, "wt")) {
            if (o == null) return false;
            o.write(bytes);
            return true;
        } catch (Exception e) { return false; }
    }
}
