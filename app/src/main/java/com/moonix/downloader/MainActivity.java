package com.moonix.downloader;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Source-only Moonix Android client for the user's VDL API. */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_MEDIA_PERMISSION = 4102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout contentFrame;
    private BottomNavigationView navigation;
    private TextView activeStatus;
    private LottieAnimationView activeLoadingAnimation;
    private LottieAnimationView activeSearchAnimation;
    private final ArrayDeque<DownloadTask> pendingDownloads = new ArrayDeque<>();
    private int activeDownloads = 0;
    private boolean filesAudio = false;
    private boolean activityDestroyed = false;

    private static final class DownloadTask {
        final String label;
        final Runnable start;
        DownloadTask(String label, Runnable start) { this.label = label; this.start = start; }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.content_frame);
        navigation = findViewById(R.id.bottom_navigation);
        ImageButton settings = findViewById(R.id.toolbar_settings);
        settings.setOnClickListener(v -> navigation.setSelectedItemId(R.id.nav_profile));
        navigation.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        navigation.setSelectedItemId(R.id.nav_home);
        requestNotificationsIfNeeded();
    }

    private void requestNotificationsIfNeeded() {
        boolean notificationsEnabled = getSharedPreferences("moonix_settings", MODE_PRIVATE).getBoolean("notify_complete", true);
        if (notificationsEnabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
        }
    }

    private void showTab(int tabId) {
        clearSearchAnimations();
        activeStatus = null;
        contentFrame.animate().cancel();
        contentFrame.setAlpha(0.78f);
        contentFrame.setTranslationY(6f * getResources().getDisplayMetrics().density);
        contentFrame.animate().alpha(1f).translationY(0f).setDuration(190).start();
        if (tabId == R.id.nav_home) {
            showSearchScreen("Explore", "Search YouTube or paste a link you are allowed to save.", "Search or detect link");
        } else if (tabId == R.id.nav_download) {
            showSearchScreen("Download", "Paste a supported link to inspect it, or search for a video.", "Search or detect link");
        } else if (tabId == R.id.nav_movies) {
            showSearchScreen("Movies", "Search movies, view details, and choose a download quality.", "Search movies");
        } else if (tabId == R.id.nav_browser) {
            showBrowserScreen();
        } else if (tabId == R.id.nav_files) {
            showFilesScreen();
        } else if (tabId == R.id.nav_profile) {
            showProfileScreen();
        }
    }

    private void showSearchScreen(String title, String subtitle, String actionLabel) {
        boolean movieSearch = "Movies".equals(title);
        View view = LayoutInflater.from(this).inflate(R.layout.screen_search, contentFrame, false);
        contentFrame.removeAllViews();
        contentFrame.addView(view);
        ((TextView) view.findViewById(R.id.screen_title)).setText(title);
        ((TextView) view.findViewById(R.id.screen_subtitle)).setText(subtitle);
        TextInputEditText input = view.findViewById(R.id.input_query);
        TextInputLayout inputLayout = view.findViewById(R.id.query_input_layout);
        inputLayout.setHint(movieSearch ? "Search for a movie" : "Search or paste a link");
        if (movieSearch) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            view.findViewById(R.id.supported_sources_title).setVisibility(View.GONE);
            view.findViewById(R.id.supported_sources_icons).setVisibility(View.GONE);
        }
        MaterialButton action = view.findViewById(R.id.btn_action);
        action.setText(actionLabel);
        MaterialButton manageDownloads = view.findViewById(R.id.btn_open_downloads);
        if ("Download".equals(title)) {
            manageDownloads.setVisibility(View.VISIBLE);
            manageDownloads.setOnClickListener(v -> {
                tapFeedback(v);
                try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
                catch (Exception e) { toast("Open the Downloads app to manage queued files."); }
            });
        }
        RecyclerView list = view.findViewById(R.id.results_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        TextView status = view.findViewById(R.id.status_text);
        activeStatus = status;
        activeLoadingAnimation = view.findViewById(R.id.loading_animation);
        activeSearchAnimation = view.findViewById(R.id.search_state_animation);
        status.setText(movieSearch ? "Search the movie catalog to begin." : "Search or paste a link to begin.");
        setLoadingAnimation(false);
        setSearchAnimation(true);
        MediaAdapter adapter = new MediaAdapter(new MediaAdapter.Actions() {
            @Override public void onVideo(MediaItem item) { beginDownload(item, "video"); }
            @Override public void onAudio(MediaItem item) { beginDownload(item, "audio"); }
            @Override public void onDetails(MediaItem item) { showMovieDetails(item); }
        });
        list.setAdapter(adapter);
        action.setOnClickListener(v -> {
            tapFeedback(v);
            hideKeyboard(input);
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (movieSearch) runMovieSearch(value, adapter, status);
            else runSearchOrInfo(value, adapter, status);
        });
        input.setOnEditorActionListener((text, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                action.performClick();
                return true;
            }
            return false;
        });
    }

    private void runSearchOrInfo(String value, MediaAdapter adapter, TextView status) {
        if (value.isEmpty()) {
            adapter.submit(new ArrayList<>());
            setSearchAnimationFor(status, true);
            showStatus(status, "Enter a search phrase or paste a supported video link.", false);
            return;
        }
        setSearchAnimationFor(status, false);
        showStatus(status, "Connecting to VDL API…", true);
        if (ApiClient.isSupportedUrl(value)) {
            ApiClient.info(this, value, new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    MediaItem item = MediaItem.fromInfo(response);
                    MediaItem withUrl = new MediaItem(item.id, item.title, item.creator, item.duration,
                            item.thumbnail, item.url.isEmpty() ? value : item.url, item.platform, false);
                    adapter.submit(Arrays.asList(withUrl));
                    setSearchAnimationFor(status, false);
                    showStatus(status, "Media detected · choose Video or Audio below.", false);
                }
                @Override public void onError(String message) {
                    adapter.submit(new ArrayList<>());
                    setSearchAnimationFor(status, true);
                    showStatus(status, message + " Only content available to you can be downloaded.", false);
                }
            });
        } else if (value.startsWith("http://") || value.startsWith("https://")) {
            adapter.submit(new ArrayList<>());
            setSearchAnimationFor(status, true);
            showStatus(status, "This URL is not from a supported source. Supported: YouTube, TikTok, Facebook and Instagram.", false);
        } else {
            ApiClient.search(this, value, new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    JSONArray results = ApiClient.array(response, "results");
                    List<MediaItem> items = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject object = results.optJSONObject(i);
                        if (object != null) items.add(MediaItem.fromSearch(object));
                    }
                    adapter.submit(items);
                    setSearchAnimationFor(status, items.isEmpty());
                    showStatus(status, items.isEmpty() ? "No results found." : items.size() + " YouTube results · choose Video or Audio.", false);
                }
                @Override public void onError(String message) {
                    adapter.submit(new ArrayList<>());
                    setSearchAnimationFor(status, true);
                    showStatus(status, "Search failed: " + message, false);
                }
            });
        }
    }

    private void runMovieSearch(String value, MediaAdapter adapter, TextView status) {
        if (value.length() < 2) {
            adapter.submit(new ArrayList<>());
            setSearchAnimationFor(status, true);
            showStatus(status, "Enter at least two letters to search for a movie.", false);
            return;
        }
        setSearchAnimationFor(status, false);
        showStatus(status, "Searching the movie catalog…", true);
        MovieApiClient.search(value, new MovieApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                JSONArray results = MovieApiClient.results(response);
                List<MediaItem> movies = new ArrayList<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject object = results.optJSONObject(i);
                    if (object == null) continue;
                    MediaItem item = MediaItem.fromMovie(object);
                    if (item.movie) movies.add(item);
                }
                adapter.submit(movies);
                setSearchAnimationFor(status, movies.isEmpty());
                showStatus(status, movies.isEmpty() ? "No movies found." : movies.size() + " movies · tap a card for details or Download for quality options.", false);
            }
            @Override public void onError(String message) {
                adapter.submit(new ArrayList<>());
                setSearchAnimationFor(status, true);
                showStatus(status, "Movie search failed: " + message, false);
            }
        });
    }

    private void confirmAndEnqueueDownload(String label, Runnable start) {
        if (getSharedPreferences("moonix_settings", MODE_PRIVATE).getBoolean("confirm_download", false)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Queue download?")
                    .setMessage(label)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add to queue", (dialog, which) -> enqueueDownload(label, start))
                    .show();
        } else {
            enqueueDownload(label, start);
        }
    }

    private void enqueueDownload(String label, Runnable start) {
        pendingDownloads.addLast(new DownloadTask(label, start));
        dispatchQueuedDownloads();
    }

    private void dispatchQueuedDownloads() {
        if (activityDestroyed) return;
        int max = getSharedPreferences("moonix_settings", MODE_PRIVATE).getInt("max_concurrent_downloads", 0);
        while (!pendingDownloads.isEmpty() && (max <= 0 || activeDownloads < max)) {
            DownloadTask task = pendingDownloads.removeFirst();
            activeDownloads++;
            try {
                task.start.run();
            } catch (Exception e) {
                updateStatus("Could not start " + task.label + ": " + e.getMessage());
                finishDownloadTask();
            }
        }
        if (!pendingDownloads.isEmpty() && activeStatus != null) {
            showStatus(activeStatus, pendingDownloads.size() + " download(s) waiting for an active slot.", false);
        }
    }

    private void finishDownloadTask() {
        if (activeDownloads > 0) activeDownloads--;
        dispatchQueuedDownloads();
    }

    private void beginDownload(MediaItem item, String type) {
        if (item.movie) {
            beginMovieDownload(item);
            return;
        }
        if (item.local) {
            openLocalFile(item);
            return;
        }
        if (item.url.isEmpty()) {
            toast("The media link is missing. Detect the link again.");
            return;
        }
        String quality = getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("quality", "720");
        confirmAndEnqueueDownload(item.title + " · " + type, () -> startVdlDownload(item, type, quality));
    }

    private void startVdlDownload(MediaItem item, String type, String quality) {
        if (activeStatus != null) {
            setSearchAnimation(false);
            showStatus(activeStatus, "Starting authorized " + type + " download…", true);
        }
        ApiClient.startDownload(this, item.url, type, quality, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                String id = response.optString("jobId", "");
                if (id.isEmpty()) {
                    updateStatus("API did not return a download job ID.");
                    finishDownloadTask();
                    return;
                }
                updateStatus("Download job " + id + " queued. Checking progress…");
                pollJob(id, item.title, type, 0, MainActivity.this::finishDownloadTask);
            }
            @Override public void onError(String message) {
                updateStatus("Could not start download: " + message);
                toast("Download could not be started.");
                finishDownloadTask();
            }
        });
    }

    private void showMovieDetails(MediaItem item) {
        StringBuilder details = new StringBuilder();
        if (!item.releaseDate.isEmpty()) details.append("Release: ").append(item.releaseDate).append('\n');
        if (!item.genre.isEmpty()) details.append("Genre: ").append(item.genre).append('\n');
        if (!item.rating.isEmpty() && !"0".equals(item.rating)) details.append("IMDb: ").append(item.rating).append('\n');
        if (!item.description.isEmpty()) {
            if (details.length() > 0) details.append('\n');
            details.append(item.description);
        }
        if (details.length() == 0) details.append("Movie information is available from the catalog.");
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.title)
                .setMessage(details.toString())
                .setPositiveButton(item.hasResource ? "Choose quality" : "OK",
                        (dialog, which) -> { if (item.hasResource) beginMovieDownload(item); })
                .setNegativeButton("Close", null)
                .show();
    }

    private void beginMovieDownload(MediaItem movie) {
        if (!movie.hasResource) {
            updateStatus("No movie source is currently available for this title.");
            return;
        }
        if (activeStatus != null) {
            setSearchAnimation(false);
            showStatus(activeStatus, "Loading movie download qualities…", true);
        }
        MovieApiClient.sources(movie, new MovieApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                JSONArray results = response.optJSONArray("results");
                List<JSONObject> choices = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject source = results.optJSONObject(i);
                        if (source == null) continue;
                        String url = source.optString("download", source.optString("url", "")).trim();
                        String format = source.optString("format", "MP4").toUpperCase(Locale.ROOT);
                        String lowerUrl = url.toLowerCase(Locale.ROOT);
                        boolean hls = format.contains("HLS") || format.contains("M3U8") || lowerUrl.contains(".m3u8");
                        boolean mp4 = format.contains("MP4") || lowerUrl.contains(".mp4");
                        Uri uri = Uri.parse(url);
                        if (url.isEmpty() || hls || !mp4 || !"https".equalsIgnoreCase(uri.getScheme())) continue;
                        choices.add(source);
                        String quality = source.optString("quality", "Auto");
                        String size = source.optString("size", "");
                        if (size.endsWith("pp")) size = size.substring(0, size.length() - 1);
                        labels.add(size.isEmpty() || size.equalsIgnoreCase(quality) ? quality : quality + " · " + size);
                    }
                }
                if (choices.isEmpty()) {
                    updateStatus("No direct MP4 download sources were returned for this movie.");
                    return;
                }
                String preferredQuality = getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("movie_quality", "ask");
                if (!"ask".equalsIgnoreCase(preferredQuality)) {
                    for (int i = 0; i < choices.size(); i++) {
                        if (matchesMovieQuality(preferredQuality, choices.get(i).optString("quality", ""))) {
                            JSONObject selected = choices.get(i);
                            String label = labels.get(i);
                            confirmAndEnqueueDownload(movie.title + " · " + label,
                                    () -> queueMovieFile(movie, selected, MainActivity.this::finishDownloadTask));
                            updateStatus("Selected saved movie quality: " + label + ".");
                            return;
                        }
                    }
                }
                String[] options = labels.toArray(new String[0]);
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Choose movie quality")
                        .setItems(options, (dialog, which) -> {
                            JSONObject selected = choices.get(which);
                            String label = labels.get(which);
                            confirmAndEnqueueDownload(movie.title + " · " + label,
                                    () -> queueMovieFile(movie, selected, MainActivity.this::finishDownloadTask));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                updateStatus(choices.size() + " MP4 quality option" + (choices.size() == 1 ? "" : "s") + " available.");
            }
            @Override public void onError(String message) {
                updateStatus("Could not load movie sources: " + message);
            }
        });
    }

    private boolean matchesMovieQuality(String preferred, String actual) {
        if (preferred.equalsIgnoreCase(actual)) return true;
        return "Best (Auto)".equalsIgnoreCase(preferred) &&
                ("Auto".equalsIgnoreCase(actual) || "Best".equalsIgnoreCase(actual));
    }

    private void queueMovieFile(MediaItem movie, JSONObject source, Runnable onFinished) {
        String fileUrl = source.optString("download", source.optString("url", "")).trim();
        String quality = source.optString("quality", "Auto");
        try {
            Uri uri = Uri.parse(fileUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || fileUrl.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                updateStatus("That source is not a direct HTTPS MP4 file.");
                onFinished.run();
                return;
            }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Moonix · " + safeFileName(movie.title));
            request.setDescription("Movie · " + quality);
            request.setMimeType("video/mp4");
            request.setNotificationVisibility(notificationVisibility());
            SharedPreferences prefs = getSharedPreferences("moonix_settings", MODE_PRIVATE);
            request.setAllowedOverMetered(!prefs.getBoolean("wifi_only", false));
            request.setAllowedOverRoaming(prefs.getBoolean("allow_roaming", false));
            request.addRequestHeader("Referer", "https://moviebox.ph/");
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
            String filename = safeFileName(movie.title) + "_" + safeFileName(quality) + ".mp4";
            request.setDestinationInExternalPublicDir(downloadDirectory(), filename);
            long downloadId = manager.enqueue(request);
            updateStatus("Movie download queued · Android Downloads ID " + downloadId + ".");
            monitorDownload(downloadId, movie.title, onFinished);
        } catch (Exception e) {
            updateStatus("Could not queue the movie download: " + e.getMessage());
            onFinished.run();
        }
    }

    private void pollJob(String jobId, String title, String type, int attempt, Runnable onFinished) {
        if (activityDestroyed) { onFinished.run(); return; }
        ApiClient.status(this, jobId, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                String state = response.optString("status", "processing");
                if ("failed".equalsIgnoreCase(state)) {
                    updateStatus("Download failed: " + response.optString("error", "The source rejected the request."));
                    onFinished.run();
                    return;
                }
                if ("completed".equalsIgnoreCase(state)) {
                    String fileUrl = response.optString("downloadUrl", "");
                    if (fileUrl.isEmpty()) {
                        updateStatus("The API completed the job but did not return a file link.");
                        onFinished.run();
                        return;
                    }
                    queueFile(fileUrl, title, type, onFinished);
                    updateStatus("Ready · Android Download Manager is saving the file.");
                    return;
                }
                int progress = response.optInt("progress", -1);
                updateStatus(progress >= 0 ? "Download " + state + "… " + progress + "%" : "Download " + state + "…");
                if (attempt < 100) handler.postDelayed(() -> pollJob(jobId, title, type, attempt + 1, onFinished), 3000);
                else {
                    updateStatus("Still processing. Check the Downloads tab later.");
                    onFinished.run();
                }
            }
            @Override public void onError(String message) {
                if (attempt < 100) handler.postDelayed(() -> pollJob(jobId, title, type, attempt + 1, onFinished), 4000);
                else {
                    updateStatus("Could not read job status: " + message);
                    onFinished.run();
                }
            }
        });
    }

    private void queueFile(String fileUrl, String title, String type, Runnable onFinished) {
        try {
            Uri uri = Uri.parse(fileUrl);
            if (uri.getScheme() == null) uri = Uri.parse(ApiClient.baseUrl(this) + (fileUrl.startsWith("/") ? "" : "/") + fileUrl);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Moonix · " + safeFileName(title));
            request.setDescription("Saving " + type + " to " + getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("download_location", "Downloads"));
            request.setNotificationVisibility(notificationVisibility());
            SharedPreferences prefs = getSharedPreferences("moonix_settings", MODE_PRIVATE);
            request.setAllowedOverMetered(!prefs.getBoolean("wifi_only", false));
            request.setAllowedOverRoaming(prefs.getBoolean("allow_roaming", false));
            String key = getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("api_key", "").trim();
            if (!key.isEmpty()) request.addRequestHeader("x-api-key", key);
            String ext = "audio".equals(type) ? ".mp3" : ".mp4";
            request.setMimeType("audio".equals(type) ? "audio/mpeg" : "video/mp4");
            request.setDestinationInExternalPublicDir(downloadDirectory(), safeFileName(title) + ext);
            long downloadId = manager.enqueue(request);
            monitorDownload(downloadId, title, onFinished);
        } catch (Exception e) {
            updateStatus("Could not pass the completed file to Android Downloads: " + e.getMessage());
            onFinished.run();
        }
    }

    private String downloadDirectory() {
        String location = getSharedPreferences("moonix_settings", MODE_PRIVATE).getString("download_location", "Downloads");
        return "Movies".equalsIgnoreCase(location) ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS;
    }

    private int notificationVisibility() {
        boolean notify = getSharedPreferences("moonix_settings", MODE_PRIVATE).getBoolean("notify_complete", true);
        return notify ? DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED : DownloadManager.Request.VISIBILITY_VISIBLE;
    }

    private void monitorDownload(long downloadId, String title, Runnable onFinished) {
        Runnable monitor = new Runnable() {
            private boolean completed = false;
            private void finish(String message) {
                if (completed) return;
                completed = true;
                updateStatus(message);
                onFinished.run();
            }
            @Override public void run() {
                if (activityDestroyed) { finish("Download continues in Android Downloads."); return; }
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId))) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        finish("Download entry is no longer available in Android Downloads.");
                        return;
                    }
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        finish("Download complete: " + title);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        finish("Download failed for " + title + " (reason " + reason + ").");
                    } else {
                        handler.postDelayed(this, 2000);
                    }
                } catch (Exception e) {
                    handler.postDelayed(this, 3000);
                }
            }
        };
        handler.postDelayed(monitor, 2000);
    }

    private String safeFileName(String value) {
        String clean = (value == null ? "Moonix download" : value).replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (clean.isEmpty()) clean = "Moonix download";
        return clean.length() > 70 ? clean.substring(0, 70) : clean;
    }

    private void updateStatus(String message) {
        if (activeStatus != null) showStatus(activeStatus, message, false);
        else toast(message);
    }

    private void showStatus(TextView view, String message, boolean loading) {
        view.setAlpha(1f);
        view.setText(message);
        if (view == activeStatus) setLoadingAnimation(loading);
    }

    private void setLoadingAnimation(boolean visible) {
        if (activeLoadingAnimation == null) return;
        if (visible) {
            activeLoadingAnimation.setVisibility(View.VISIBLE);
            activeLoadingAnimation.playAnimation();
        } else {
            activeLoadingAnimation.cancelAnimation();
            activeLoadingAnimation.setVisibility(View.GONE);
        }
    }

    private void setSearchAnimation(boolean visible) {
        if (activeSearchAnimation == null) return;
        if (visible) {
            activeSearchAnimation.setVisibility(View.VISIBLE);
            activeSearchAnimation.playAnimation();
        } else {
            activeSearchAnimation.cancelAnimation();
            activeSearchAnimation.setVisibility(View.GONE);
        }
    }

    private void setSearchAnimationFor(TextView status, boolean visible) {
        if (status != null && status == activeStatus) setSearchAnimation(visible);
    }

    private void clearSearchAnimations() {
        setLoadingAnimation(false);
        setSearchAnimation(false);
        activeLoadingAnimation = null;
        activeSearchAnimation = null;
    }

    private void tapFeedback(View view) {
        view.animate().cancel();
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
    }

    private void showBrowserScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.screen_browser, contentFrame, false);
        contentFrame.removeAllViews();
        contentFrame.addView(view);
        TextInputEditText url = view.findViewById(R.id.browser_url);
        view.findViewById(R.id.browser_open).setOnClickListener(v -> {
            tapFeedback(v);
            String value = url.getText() == null ? "" : url.getText().toString().trim();
            if (value.isEmpty()) { toast("Enter a website address."); return; }
            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
            catch (Exception e) { toast("No browser app is available."); }
        });
    }

    private void showFilesScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.screen_files, contentFrame, false);
        contentFrame.removeAllViews();
        contentFrame.addView(view);
        RecyclerView list = view.findViewById(R.id.files_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        TextView summary = view.findViewById(R.id.storage_summary);
        MediaAdapter adapter = new MediaAdapter(new MediaAdapter.Actions() {
            @Override public void onVideo(MediaItem item) { openLocalFile(item); }
            @Override public void onAudio(MediaItem item) { openLocalFile(item); }
            @Override public void onDetails(MediaItem item) { showMovieDetails(item); }
        });
        list.setAdapter(adapter);
        view.findViewById(R.id.files_videos).setOnClickListener(v -> { filesAudio = false; loadLocalFiles(adapter, summary); });
        view.findViewById(R.id.files_audio).setOnClickListener(v -> { filesAudio = true; loadLocalFiles(adapter, summary); });
        loadLocalFiles(adapter, summary);
    }

    private String permissionForFiles() {
        if (Build.VERSION.SDK_INT >= 33) return filesAudio ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_MEDIA_VIDEO;
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void loadLocalFiles(MediaAdapter adapter, TextView summary) {
        String permission = permissionForFiles();
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            summary.setText("Allow Moonix to read device media to show your local files.");
            requestPermissions(new String[]{permission}, REQUEST_MEDIA_PERMISSION);
            adapter.submit(new ArrayList<>());
            return;
        }
        Uri collection = filesAudio ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.DURATION};
        List<MediaItem> items = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(collection, projection, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                int titleCol = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
                int durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION);
                while (cursor.moveToNext() && items.size() < 100) {
                    long id = cursor.getLong(idCol);
                    String name = nameCol >= 0 ? cursor.getString(nameCol) : "";
                    String title = titleCol >= 0 ? cursor.getString(titleCol) : "";
                    if (title == null || title.trim().isEmpty()) title = name;
                    long durationMs = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                    String duration = durationMs > 0 ? formatDuration(durationMs / 1000) : "";
                    Uri itemUri = ContentUris.withAppendedId(collection, id);
                    items.add(MediaItem.local(title, duration, itemUri));
                }
            }
        } catch (Exception e) {
            summary.setText("Could not read media: " + e.getMessage());
            adapter.submit(new ArrayList<>());
            return;
        }
        summary.setText(items.size() + (filesAudio ? " audio files on this device" : " videos on this device"));
        adapter.submit(items);
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remaining = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remaining);
    }

    private void openLocalFile(MediaItem item) {
        try {
            String mime = filesAudio ? "audio/*" : "video/*";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(item.url), mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) { toast("No compatible media player is installed."); }
    }

    private void showProfileScreen() {
        View view = LayoutInflater.from(this).inflate(R.layout.screen_profile, contentFrame, false);
        contentFrame.removeAllViews();
        contentFrame.addView(view);
        TextInputEditText apiUrl = view.findViewById(R.id.profile_api_url);
        TextInputEditText apiKey = view.findViewById(R.id.profile_api_key);
        Spinner qualitySpinner = view.findViewById(R.id.profile_quality);
        Spinner movieQualitySpinner = view.findViewById(R.id.profile_movie_quality);
        Spinner concurrencySpinner = view.findViewById(R.id.profile_max_downloads);
        Spinner locationSpinner = view.findViewById(R.id.profile_download_location);
        SwitchMaterial wifiOnly = view.findViewById(R.id.profile_wifi_only);
        SwitchMaterial allowRoaming = view.findViewById(R.id.profile_allow_roaming);
        SwitchMaterial notifyComplete = view.findViewById(R.id.profile_notify_complete);
        SwitchMaterial confirmDownload = view.findViewById(R.id.profile_confirm_download);
        SharedPreferences prefs = getSharedPreferences("moonix_settings", MODE_PRIVATE);
        apiUrl.setText(ApiClient.baseUrl(this) + "/");
        apiKey.setText(prefs.getString("api_key", ""));
        ArrayAdapter<CharSequence> videoAdapter = ArrayAdapter.createFromResource(this, R.array.quality_options, android.R.layout.simple_spinner_item);
        videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(videoAdapter);
        ArrayAdapter<CharSequence> movieAdapter = ArrayAdapter.createFromResource(this, R.array.movie_quality_options, android.R.layout.simple_spinner_item);
        movieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        movieQualitySpinner.setAdapter(movieAdapter);
        ArrayAdapter<CharSequence> concurrencyAdapter = ArrayAdapter.createFromResource(this, R.array.max_download_options, android.R.layout.simple_spinner_item);
        concurrencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        concurrencySpinner.setAdapter(concurrencyAdapter);
        ArrayAdapter<CharSequence> locationAdapter = ArrayAdapter.createFromResource(this, R.array.download_location_options, android.R.layout.simple_spinner_item);
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(locationAdapter);

        String savedQuality = prefs.getString("quality", "720");
        String[] qualityValues = getResources().getStringArray(R.array.quality_values);
        for (int i = 0; i < qualityValues.length; i++) if (qualityValues[i].equals(savedQuality)) qualitySpinner.setSelection(i);
        String savedMovieQuality = prefs.getString("movie_quality", "ask");
        String[] movieQualityValues = getResources().getStringArray(R.array.movie_quality_values);
        for (int i = 0; i < movieQualityValues.length; i++) if (movieQualityValues[i].equalsIgnoreCase(savedMovieQuality)) movieQualitySpinner.setSelection(i);
        int savedConcurrency = prefs.getInt("max_concurrent_downloads", 0);
        int[] concurrencyValues = getResources().getIntArray(R.array.max_download_values);
        for (int i = 0; i < concurrencyValues.length; i++) if (concurrencyValues[i] == savedConcurrency) concurrencySpinner.setSelection(i);
        String savedLocation = prefs.getString("download_location", "Downloads");
        String[] locations = getResources().getStringArray(R.array.download_location_options);
        for (int i = 0; i < locations.length; i++) if (locations[i].equals(savedLocation)) locationSpinner.setSelection(i);

        wifiOnly.setChecked(prefs.getBoolean("wifi_only", false));
        allowRoaming.setChecked(prefs.getBoolean("allow_roaming", false));
        notifyComplete.setChecked(prefs.getBoolean("notify_complete", true));
        confirmDownload.setChecked(prefs.getBoolean("confirm_download", false));
        wifiOnly.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("wifi_only", checked).apply());
        allowRoaming.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("allow_roaming", checked).apply());
        notifyComplete.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("notify_complete", checked).apply();
            if (checked) requestNotificationsIfNeeded();
        });
        confirmDownload.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("confirm_download", checked).apply());

        view.findViewById(R.id.profile_save).setOnClickListener(v -> {
            String base = apiUrl.getText() == null ? "" : apiUrl.getText().toString().trim();
            if (!base.matches("(?i)^https://.+")) { toast("Use an https:// API address."); return; }
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String key = apiKey.getText() == null ? "" : apiKey.getText().toString().trim();
            prefs.edit()
                    .putString("api_base", base)
                    .putString("api_key", key)
                    .apply();
            toast("API connection saved.");
        });
        view.findViewById(R.id.profile_save_download_settings).setOnClickListener(v -> {
            int videoIndex = Math.max(0, Math.min(qualitySpinner.getSelectedItemPosition(), qualityValues.length - 1));
            int movieIndex = Math.max(0, Math.min(movieQualitySpinner.getSelectedItemPosition(), movieQualityValues.length - 1));
            int concurrencyIndex = Math.max(0, Math.min(concurrencySpinner.getSelectedItemPosition(), concurrencyValues.length - 1));
            String location = String.valueOf(locationSpinner.getSelectedItem());
            prefs.edit()
                    .putString("quality", qualityValues[videoIndex])
                    .putString("movie_quality", movieQualityValues[movieIndex])
                    .putInt("max_concurrent_downloads", concurrencyValues[concurrencyIndex])
                    .putString("download_location", location)
                    .apply();
            dispatchQueuedDownloads();
            toast("Download settings saved.");
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MEDIA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFilesScreen();
        } else if (requestCode == REQUEST_MEDIA_PERMISSION) {
            toast("Media permission was not granted.");
        }
    }

    @Override protected void onDestroy() {
        activityDestroyed = true;
        handler.removeCallbacksAndMessages(null);
        clearSearchAnimations();
        super.onDestroy();
    }
}
