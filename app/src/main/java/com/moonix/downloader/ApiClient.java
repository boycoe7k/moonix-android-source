package com.moonix.downloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP client for the Moonix VDL API. All network work runs off the UI thread. */
public final class ApiClient {
    public static final String DEFAULT_BASE = "https://video-download-api-uohi.onrender.com/";
    private static final String PREFS = "moonix_settings";
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApiClient() { }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    public static String baseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String base = prefs.getString("api_base", DEFAULT_BASE).trim();
        if (base.isEmpty()) base = DEFAULT_BASE;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String apiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("api_key", "").trim();
    }

    public static boolean isSupportedUrl(String value) {
        if (value == null) return false;
        String text = value.trim().toLowerCase();
        if (!(text.startsWith("https://") || text.startsWith("http://"))) return false;
        try {
            String host = new URL(text).getHost();
            return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com") ||
                    host.equals("fb.watch") || host.equals("facebook.com") || host.endsWith(".facebook.com") ||
                    host.equals("tiktok.com") || host.endsWith(".tiktok.com") ||
                    host.equals("instagram.com") || host.endsWith(".instagram.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void search(Context context, String query, Callback callback) {
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            request(context, "GET", "/api/search?q=" + encoded, null, callback);
        } catch (Exception e) {
            callback.onError("Could not encode search text.");
        }
    }

    public static void info(Context context, String mediaUrl, Callback callback) {
        JSONObject body = new JSONObject();
        try { body.put("url", mediaUrl); } catch (JSONException ignored) { }
        request(context, "POST", "/api/media/info", body, callback);
    }

    public static void startDownload(Context context, String mediaUrl, String type, String quality, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("url", mediaUrl);
            body.put("type", type);
            if ("video".equals(type)) body.put("quality", quality);
        } catch (JSONException ignored) { }
        request(context, "POST", "/api/media/download", body, callback);
    }

    public static void status(Context context, String jobId, Callback callback) {
        request(context, "GET", "/api/status/" + jobId, null, callback);
    }

    public static void health(Context context, Callback callback) {
        request(context, "GET", "/health", null, callback);
    }

    private static void request(Context context, String method, String path, JSONObject body, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(baseUrl(context) + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                String key = apiKey(context);
                if (!key.isEmpty()) connection.setRequestProperty("x-api-key", key);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String responseText = readFully(stream);
                if (code < 200 || code >= 300) {
                    JSONObject err = new JSONObject(responseText.isEmpty() ? "{}" : responseText);
                    String message = err.optString("error", "Request failed (HTTP " + code + ")");
                    postError(callback, message);
                    return;
                }
                JSONObject response = new JSONObject(responseText.isEmpty() ? "{}" : responseText);
                if (!response.optBoolean("success", true)) {
                    postError(callback, response.optString("error", "The API returned an error."));
                } else {
                    MAIN.post(() -> callback.onSuccess(response));
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Network request failed." : e.getMessage();
                postError(callback, message);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static void postError(Callback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }

    public static JSONArray array(JSONObject object, String key) {
        JSONArray result = object.optJSONArray(key);
        return result == null ? new JSONArray() : result;
    }
}
