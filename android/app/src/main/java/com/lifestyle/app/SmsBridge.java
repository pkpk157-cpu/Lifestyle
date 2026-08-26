package com.lifestyle.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The only bridge between the web app and the device.
 *
 * It exposes read-only access to the SMS inbox and nothing else. Messages are
 * handed to the WebView, parsed there, and stored in localStorage on this
 * device. The app declares no INTERNET permission, so nothing it reads can
 * leave the phone.
 */
public class SmsBridge {

    private final MainActivity activity;

    public SmsBridge(MainActivity activity) {
        this.activity = activity;
    }

    /** Lets the web app detect that it is running inside the native shell. */
    @JavascriptInterface
    public boolean isAvailable() {
        return true;
    }

    @JavascriptInterface
    public boolean hasPermission() {
        return activity.checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Shows the system permission dialog. The answer comes back to JS as an
     * "android-sms-permission" event carrying true or false, because a
     * JavascriptInterface call cannot block waiting for it.
     */
    @JavascriptInterface
    public void requestPermission() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.requestSmsPermission();
            }
        });
    }

    /**
     * Reads inbox messages newer than sinceMillis, newest first.
     *
     * @param sinceMillis epoch millis as a string (JS numbers lose precision
     *                    crossing the bridge as longs)
     * @param limit       hard cap on rows returned
     * @return JSON array of {address, body, date}
     */
    @JavascriptInterface
    public String readInbox(String sinceMillis, int limit) {
        JSONArray out = new JSONArray();
        if (!hasPermission()) return out.toString();

        long since = 0L;
        try {
            if (sinceMillis != null && sinceMillis.length() > 0) since = Long.parseLong(sinceMillis);
        } catch (NumberFormatException ignored) {
            since = 0L;
        }
        if (limit <= 0 || limit > 20000) limit = 5000;

        Cursor cursor = null;
        try {
            Uri inbox = Uri.parse("content://sms/inbox");
            String[] columns = new String[]{"address", "body", "date"};
            String selection = "date > ?";
            String[] args = new String[]{String.valueOf(since)};

            cursor = activity.getContentResolver()
                    .query(inbox, columns, selection, args, "date DESC");
            if (cursor == null) return out.toString();

            int iAddress = cursor.getColumnIndex("address");
            int iBody = cursor.getColumnIndex("body");
            int iDate = cursor.getColumnIndex("date");

            int n = 0;
            while (cursor.moveToNext() && n < limit) {
                String body = iBody >= 0 ? cursor.getString(iBody) : null;
                if (body == null || body.length() < 15) continue;
                JSONObject row = new JSONObject();
                row.put("address", iAddress >= 0 ? cursor.getString(iAddress) : "");
                row.put("body", body);
                row.put("date", iDate >= 0 ? cursor.getLong(iDate) : 0L);
                out.put(row);
                n++;
            }
        } catch (Exception e) {
            // A denied or unavailable provider simply yields nothing.
            return out.toString();
        } finally {
            if (cursor != null) cursor.close();
        }
        return out.toString();
    }

    /** Newest inbox timestamp, so the web app can show "last message seen". */
    @JavascriptInterface
    public String newestTimestamp() {
        if (!hasPermission()) return "0";
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    new String[]{"date"}, null, null, "date DESC LIMIT 1");
            if (cursor != null && cursor.moveToFirst()) {
                return String.valueOf(cursor.getLong(0));
            }
        } catch (Exception ignored) {
            // fall through
        } finally {
            if (cursor != null) cursor.close();
        }
        return "0";
    }
}
