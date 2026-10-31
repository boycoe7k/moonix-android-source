package com.moonix.downloader;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native client for the movie search/source API served by the user's Play Mate deployment. */
public final class MovieApiClient {
    public static final String BASE_URL = "https://movie-lab.zone.id";
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MovieApiClient() { }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    public static void search(String query, Callback callback) {
        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8");
            get("/api-omegatech/api/movie/MovieBox-pro?action=search&keyword=" + encoded + "&page=1", callback);
        } catch (Exception e) {
            callback.onError("Could not encode movie search.");
        }
    }

    public static void sources(MediaItem movie, Callback callback) {
        try {
            String id = URLEncoder.encode(movie.id, "UTF-8");
            String path = URLEncoder.encode(movie.detailPath, "UTF-8");
            String title = URLEncoder.encode(movie.title, "UTF-8");
            String endpoint = "/api/sources/" + id + "?type=Movie&season=0&episode=0&path=" + path + "&title=" + title;
            get(endpoint, callback);
        } catch (Exception e) {
            callback.onError("Could not prepare the movie source request.");
        }
    }

    private static void get(String path, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Moonix-Android/1.0");
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                String body = readFully(stream);
                if (code < 200 || code >= 300) {
                    String message = "Movie API request failed (HTTP " + code + ")";
                    try { message = new JSONObject(body).optString("message", message); } catch (Exception ignored) { }
                    postError(callback, message);
                    return;
                }
                JSONObject response = new JSONObject(body.isEmpty() ? "{}" : body);
                MAIN.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Movie API is unavailable." : e.getMessage();
                postError(callback, message);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    private static void postError(Callback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }

    /** Supports the nested MovieBox-Pro response shape and the normalized Play Mate shape. */
    public static JSONArray results(JSONObject response) {
        JSONArray direct = response.optJSONArray("results");
        if (direct != null) return direct;
        JSONObject current = response;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            JSONArray items = current.optJSONArray("items");
            if (items != null) return items;
            JSONArray results = current.optJSONArray("results");
            if (results != null) return results;
            JSONObject next = current.optJSONObject("data");
            if (next == null) next = current.optJSONObject("raw");
            if (next == null) next = current.optJSONObject("result");
            current = next;
        }
        return new JSONArray();
    }
}
