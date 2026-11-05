package com.moonix.downloader;

import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.database.Cursor;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Separate full-screen search surface, independent of MainActivity's bottom navigation. */
public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_INITIAL_QUERY = "com.moonix.downloader.extra.INITIAL_QUERY";
    private static final String HISTORY_PREFS = "moonix_search_history";
    private static final String HISTORY_KEY = "queries";
    private static final int HISTORY_LIMIT = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextInputEditText queryInput;
    private TextView status;
    private View loading;
    private MediaAdapter adapter;
    private boolean finished;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        queryInput = findViewById(R.id.search_query_input);
        status = findViewById(R.id.search_status);
        loading = findViewById(R.id.search_loading);
        RecyclerView results = findViewById(R.id.search_results);
        results.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MediaAdapter(new MediaAdapter.Actions() {
            @Override public void onVideo(MediaItem item) { startMediaDownload(item, "video"); }
            @Override public void onAudio(MediaItem item) { startMediaDownload(item, "audio"); }
            @Override public void onDetails(MediaItem item) {
                if (isActive()) Toast.makeText(SearchActivity.this, "Details are not available in search.", Toast.LENGTH_SHORT).show();
            }
        });
        results.setAdapter(adapter);
        findViewById(R.id.search_back).setOnClickListener(v -> finish());
        MaterialButton submit = findViewById(R.id.search_submit);
        submit.setOnClickListener(v -> submitQuery());
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                submitQuery();
                return true;
            }
            return false;
        });
        refreshHistory();
        String initial = getIntent().getStringExtra(EXTRA_INITIAL_QUERY);
        if (initial != null && !initial.trim().isEmpty()) {
            queryInput.setText(initial);
            queryInput.setSelection(queryInput.length());
            queryInput.post(this::submitQuery);
        }
    }

    private void submitQuery() {
        if (!isActive()) return;
        String value = queryInput.getText() == null ? "" : queryInput.getText().toString().trim();
        if (value.isEmpty()) {
            setState("Enter a search phrase or paste a supported video link.", false);
            adapter.submit(new ArrayList<>());
            return;
        }
        hideKeyboard();
        saveHistory(value);
        refreshHistory();
        adapter.submit(new ArrayList<>());
        setState("Connecting to VDL API…", true);
        if (ApiClient.isSupportedUrl(value)) {
            ApiClient.info(this, value, new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    if (!isActive()) return;
                    MediaItem item = MediaItem.fromInfo(response);
                    MediaItem withUrl = new MediaItem(item.id, item.title, item.creator, item.duration,
                            item.thumbnail, item.url.isEmpty() ? value : item.url, item.platform, false);
                    List<MediaItem> found = new ArrayList<>(); found.add(withUrl);
                    adapter.submit(found);
                    setState("Media detected · choose Video or Audio to download.", false);
                }
                @Override public void onError(String message) {
                    if (!isActive()) return;
                    setState(message + " Only content available to you can be downloaded.", false);
                }
            });
        } else if (value.startsWith("http://") || value.startsWith("https://")) {
            setState("Unsupported URL. Supported sources include YouTube, TikTok, Facebook and Instagram.", false);
        } else {
            ApiClient.search(this, value, new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    if (!isActive()) return;
                    JSONArray array = ApiClient.array(response, "results");
                    List<MediaItem> items = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject object = array.optJSONObject(i);
                        if (object != null) items.add(MediaItem.fromSearch(object));
                    }
                    adapter.submit(items);
                    setState(items.isEmpty() ? "No results found." : items.size() + " results · choose Video or Audio to download.", false);
                }
                @Override public void onError(String message) {
                    if (!isActive()) return;
                    setState("Search failed: " + message, false);
                }
            });
        }
    }

    private void saveHistory(String query) {
        SharedPreferences prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE);
        JSONArray existing;
        try { existing = new JSONArray(prefs.getString(HISTORY_KEY, "[]")); }
        catch (Exception e) { existing = new JSONArray(); }
        JSONArray updated = new JSONArray();
        updated.put(query);
        for (int i = 0; i < existing.length() && updated.length() < HISTORY_LIMIT; i++) {
            String old = existing.optString(i, "").trim();
            if (!old.isEmpty() && !old.equalsIgnoreCase(query)) updated.put(old);
        }
        prefs.edit().putString(HISTORY_KEY, updated.toString()).apply();
    }

    private void refreshHistory() {
        LinearLayout container = findViewById(R.id.search_history_items);
        container.removeAllViews();
        JSONArray history;
        try { history = new JSONArray(getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE).getString(HISTORY_KEY, "[]")); }
        catch (Exception e) { history = new JSONArray(); }
        for (int i = 0; i < history.length(); i++) {
            String term = history.optString(i, "");
            if (term.trim().isEmpty()) continue;
            MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(term);
            chip.setTextAllCaps(false);
            chip.setMaxLines(1);
            chip.setOnClickListener(v -> { queryInput.setText(term); queryInput.setSelection(queryInput.length()); submitQuery(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            params.setMarginEnd(dp(8));
            container.addView(chip, params);
        }
        findViewById(R.id.search_history_title).setVisibility(history.length() == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.search_history_scroll).setVisibility(history.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void startMediaDownload(MediaItem item, String type) {
        if (!isActive()) return;
        if (item.url.isEmpty()) { toast("The media link is missing. Search or detect it again."); return; }
        String quality = getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("quality", "720");
        setState("Starting authorized " + type + " download…", true);
        ApiClient.startDownload(this, item.url, type, quality, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (!isActive()) return;
                String jobId = response.optString("jobId", "");
                if (jobId.isEmpty()) { setState("API did not return a download job ID.", false); return; }
                setState("Download queued · checking progress…", true);
                pollJob(jobId, item.title, type, 0);
            }
            @Override public void onError(String message) {
                if (isActive()) setState("Could not start download: " + message, false);
            }
        });
    }

    private void pollJob(String jobId, String title, String type, int attempt) {
        if (!isActive()) return;
        ApiClient.status(this, jobId, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (!isActive()) return;
                String state = response.optString("status", "processing");
                if ("failed".equalsIgnoreCase(state)) { setState("Download failed: " + response.optString("error", "The source rejected the request."), false); return; }
                if ("completed".equalsIgnoreCase(state)) {
                    String fileUrl = response.optString("downloadUrl", "");
                    if (fileUrl.isEmpty()) { setState("The API completed the job but did not return a file link.", false); return; }
                    queueFile(fileUrl, title, type);
                    return;
                }
                int progress = response.optInt("progress", -1);
                setState(progress >= 0 ? "Download " + state + "… " + progress + "%" : "Download " + state + "…", true);
                if (attempt < 100) handler.postDelayed(() -> pollJob(jobId, title, type, attempt + 1), 3000);
                else setState("Still processing. Check Android Downloads later.", false);
            }
            @Override public void onError(String message) {
                if (!isActive()) return;
                if (attempt < 100) handler.postDelayed(() -> pollJob(jobId, title, type, attempt + 1), 4000);
                else setState("Could not read job status: " + message, false);
            }
        });
    }

    private void queueFile(String fileUrl, String title, String type) {
        if (!isActive()) return;
        try {
            Uri uri = Uri.parse(fileUrl);
            if (uri.getScheme() == null) uri = Uri.parse(ApiClient.baseUrl(this) + (fileUrl.startsWith("/") ? "" : "/") + fileUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Invalid download URL");
            SharedPreferences prefs = getSharedPreferences("moonix_settings", MODE_PRIVATE);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Moonix · " + safeFileName(title));
            request.setDescription("Saving " + type + " to " + prefs.getString("download_location", "Downloads"));
            request.setNotificationVisibility(prefs.getBoolean("notify_complete", true)
                    ? DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED : DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setAllowedOverMetered(!prefs.getBoolean("wifi_only", false));
            request.setAllowedOverRoaming(prefs.getBoolean("allow_roaming", false));
            String key = prefs.getString("api_key", "").trim();
            if (!key.isEmpty()) request.addRequestHeader("x-api-key", key);
            boolean audio = "audio".equals(type);
            request.setMimeType(audio ? "audio/mpeg" : "video/mp4");
            String dir = "Movies".equalsIgnoreCase(prefs.getString("download_location", "Downloads")) ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS;
            request.setDestinationInExternalPublicDir(dir, safeFileName(title) + (audio ? ".mp3" : ".mp4"));
            long id = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            setState("Android Download Manager is saving your file (ID " + id + ").", false);
        } catch (Exception e) { setState("Could not queue file in Android Downloads: " + e.getMessage(), false); }
    }

    private String safeFileName(String value) {
        String clean = (value == null ? "Moonix download" : value).replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (clean.isEmpty()) clean = "Moonix download";
        return clean.length() > 70 ? clean.substring(0, 70) : clean;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean isActive() { return !finished && !isFinishing() && !isDestroyed(); }
    private void setState(String text, boolean isLoading) {
        if (!isActive()) return;
        status.setText(text);
        loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    private void hideKeyboard() {
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(queryInput.getWindowToken(), 0); }
        catch (Exception ignored) { }
        queryInput.clearFocus();
    }
    private void toast(String text) { if (isActive()) Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        finished = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
