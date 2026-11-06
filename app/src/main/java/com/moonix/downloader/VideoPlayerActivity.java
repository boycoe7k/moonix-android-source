package com.moonix.downloader;

import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** In-app playback for local media and YouTube search results. */
public class VideoPlayerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView videoView;
    private WebView webView;
    private TextView errorView;
    private boolean activityFinished;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        SystemInsets.apply(this);
        videoView = findViewById(R.id.video_view);
        webView = findViewById(R.id.youtube_webview);
        errorView = findViewById(R.id.video_error);

        String title = extra(PlaybackContract.EXTRA_TITLE);
        String creator = extra(PlaybackContract.EXTRA_CREATOR);
        String duration = extra(PlaybackContract.EXTRA_DURATION);
        ((TextView) findViewById(R.id.video_title)).setText(title.isEmpty() ? "Video" : title);
        ((TextView) findViewById(R.id.video_creator)).setText(creator.isEmpty() ? "YouTube" : creator);
        ((TextView) findViewById(R.id.video_duration)).setText(duration);
        String views = extra(PlaybackContract.EXTRA_VIEWS);
        String published = extra(PlaybackContract.EXTRA_PUBLISHED);
        StringBuilder stats = new StringBuilder();
        if (!views.isEmpty()) stats.append(views.toLowerCase(Locale.ROOT).contains("view") ? views : views + " views");
        if (!published.isEmpty()) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append(published);
        }
        ((TextView) findViewById(R.id.video_stats)).setText(stats);
        ((TextView) findViewById(R.id.video_details)).setText(
                stats.length() == 0 ? "Video details are not available." : stats.toString());

        Uri uri = readUri();
        if (isLocalUri(uri)) setupLocalPlayback(uri);
        else setupYoutubePlayback(resolveVideoId());
        setupActions(uri);
    }

    private String extra(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private Uri readUri() {
        String value = extra(PlaybackContract.EXTRA_URI);
        if (value.isEmpty()) return null;
        try { return Uri.parse(value); } catch (RuntimeException exception) { return null; }
    }

    private boolean isLocalUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    private void setupLocalPlayback(Uri uri) {
        webView.setVisibility(View.GONE);
        findViewById(R.id.youtube_fallback).setVisibility(View.GONE);
        if (isKnownWrongType(uri)) {
            showError("This video can’t be opened. Choose a supported local video file.");
            return;
        }
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setOnErrorListener((player, what, extra) -> {
            showError("Moonix couldn’t play this video. The file may be unavailable or in an unsupported format.");
            return true;
        });
        videoView.setOnPreparedListener(player -> videoView.start());
        try { videoView.setVideoURI(uri); }
        catch (RuntimeException exception) { showError("This video can’t be opened. Check that the file is still available."); }
    }

    private boolean isKnownWrongType(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            return type != null && !type.toLowerCase(Locale.ROOT).startsWith("video/");
        } catch (RuntimeException ignored) { return false; }
    }

    private String resolveVideoId() {
        String supplied = extra(PlaybackContract.EXTRA_VIDEO_ID);
        if (supplied.matches("[A-Za-z0-9_-]{11}")) return supplied;
        Uri uri = readUri();
        if (uri == null) return "";
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String candidate = "";
        if (host.equals("youtu.be")) {
            candidate = uri.getLastPathSegment();
        } else if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            candidate = uri.getQueryParameter("v");
            if (candidate == null) {
                String[] segments = uri.getPathSegments().toArray(new String[0]);
                for (int i = 0; i + 1 < segments.length; i++) {
                    if ("embed".equals(segments[i]) || "shorts".equals(segments[i])) candidate = segments[i + 1];
                }
            }
        }
        return candidate != null && candidate.matches("[A-Za-z0-9_-]{11}") ? candidate : "";
    }

    private void setupYoutubePlayback(String videoId) {
        videoView.setVisibility(View.GONE);
        findViewById(R.id.youtube_fallback).setVisibility(View.GONE);
        if (!videoId.matches("[A-Za-z0-9_-]{11}")) {
            showYoutubeFallback("This YouTube video link is unavailable or invalid.");
            return;
        }
        webView.setVisibility(View.VISIBLE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep top-level navigation inside Moonix; allow the YouTube embed's own frame/resources.
                if (!request.isForMainFrame()) return false;
                Uri target = request.getUrl();
                String host = target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT);
                return !(host.equals("www.youtube-nocookie.com") || host.equals("youtube-nocookie.com"));
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showYoutubeFallback("The YouTube player couldn’t load. The embed may be unavailable or disabled for this video.");
            }
        });
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'></head>" +
                "<body style='margin:0;background:#000'><iframe width='100%' height='100%' style='position:absolute;inset:0;border:0' " +
                "src='https://www.youtube-nocookie.com/embed/" + videoId + "?playsinline=1&rel=0' " +
                "title='YouTube video player' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share' allowfullscreen></iframe></body></html>";
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com/", html, "text/html", "UTF-8", null);
    }

    private void showYoutubeFallback(String message) {
        TextView fallback = findViewById(R.id.youtube_fallback);
        fallback.setText(message);
        fallback.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        videoView.setVisibility(View.GONE);
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void setupActions(Uri sourceUri) {
        Button save = findViewById(R.id.action_save);
        Button audio = findViewById(R.id.action_audio);
        Button shareButton = findViewById(R.id.action_share);
        Button download = findViewById(R.id.action_download);
        boolean local = isLocalUri(sourceUri);
        String id = resolveVideoId();
        save.setOnClickListener(v -> toggleSaved(save, id));
        audio.setOnClickListener(v -> showDownloadOptions("audio"));
        download.setOnClickListener(v -> showDownloadOptions("video"));
        if (local) {
            audio.setVisibility(View.GONE);
            download.setVisibility(View.GONE);
            save.setText("On device");
            save.setEnabled(false);
        }
        shareButton.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            if (local) {
                share.setType("video/*");
                share.putExtra(Intent.EXTRA_STREAM, sourceUri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if (!id.isEmpty()) {
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=" + id);
            } else {
                Toast.makeText(this, "There is no shareable link for this video.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(Intent.createChooser(share, "Share video"));
        });
    }

    private void toggleSaved(Button button, String id) {
        if (id.isEmpty()) {
            Toast.makeText(this, "This video cannot be saved to favorites.", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = getSharedPreferences("moonix_saved_videos", MODE_PRIVATE);
        Set<String> saved = new HashSet<>(prefs.getStringSet("ids", new HashSet<>()));
        boolean nowSaved;
        if (saved.contains(id)) { saved.remove(id); nowSaved = false; }
        else { saved.add(id); nowSaved = true; }
        prefs.edit().putStringSet("ids", saved).apply();
        button.setText(nowSaved ? "Saved" : "Save");
        Toast.makeText(this, nowSaved ? "Saved to Moonix favorites." : "Removed from Moonix favorites.", Toast.LENGTH_SHORT).show();
    }

    private void showDownloadOptions(String initialType) {
        String videoId = resolveVideoId();
        String source = extra(PlaybackContract.EXTRA_URI);
        if (source.isEmpty() && !videoId.isEmpty()) source = "https://www.youtube.com/watch?v=" + videoId;
        if (source.isEmpty()) {
            Toast.makeText(this, "There is no supported source link for this video.", Toast.LENGTH_LONG).show();
            return;
        }
        MediaItem item = new MediaItem(videoId, extra(PlaybackContract.EXTRA_TITLE),
                extra(PlaybackContract.EXTRA_CREATOR), extra(PlaybackContract.EXTRA_DURATION),
                extra(PlaybackContract.EXTRA_THUMBNAIL), source, "YouTube", false,
                extra(PlaybackContract.EXTRA_VIEWS), extra(PlaybackContract.EXTRA_PUBLISHED));
        DownloadOptionsSheet.show(this, item, initialType,
                (selectedItem, type, quality, filename, directory) -> startDownload(selectedItem, type, quality, filename, directory));
    }

    private void startDownload(MediaItem item, String type, String quality, String filename, String directory) {
        if (activityFinished || item == null) return;
        Toast.makeText(this, "Starting authorized " + type + " download…", Toast.LENGTH_SHORT).show();
        ApiClient.startDownload(this, item.url, type, quality, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (activityFinished) return;
                String jobId = response.optString("jobId", "");
                if (jobId.isEmpty()) {
                    Toast.makeText(VideoPlayerActivity.this, "The API did not return a download job ID.", Toast.LENGTH_LONG).show();
                    return;
                }
                pollDownload(jobId, filename, type, directory, 0);
            }
            @Override public void onError(String message) {
                if (!activityFinished) Toast.makeText(VideoPlayerActivity.this, "Could not start download: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void pollDownload(String jobId, String filename, String type, String directory, int attempt) {
        if (activityFinished) return;
        ApiClient.status(this, jobId, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (activityFinished) return;
                String state = response.optString("status", "processing");
                if ("failed".equalsIgnoreCase(state)) {
                    Toast.makeText(VideoPlayerActivity.this, "Download failed: " + response.optString("error", "source rejected the request"), Toast.LENGTH_LONG).show();
                } else if ("completed".equalsIgnoreCase(state)) {
                    String downloadUrl = response.optString("downloadUrl", "");
                    if (downloadUrl.isEmpty()) Toast.makeText(VideoPlayerActivity.this, "The completed job did not provide a file link.", Toast.LENGTH_LONG).show();
                    else queueCompletedFile(downloadUrl, filename, type, directory);
                } else if (attempt < 100) {
                    handler.postDelayed(() -> pollDownload(jobId, filename, type, directory, attempt + 1), 3000);
                } else {
                    Toast.makeText(VideoPlayerActivity.this, "The download is still processing. Check My Files → Downloads later.", Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onError(String message) {
                if (!activityFinished && attempt < 100) handler.postDelayed(() -> pollDownload(jobId, filename, type, directory, attempt + 1), 4000);
                else if (!activityFinished) Toast.makeText(VideoPlayerActivity.this, "Could not check download progress: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void queueCompletedFile(String rawUrl, String filename, String type, String directory) {
        try {
            Uri uri = Uri.parse(rawUrl);
            if (uri.getScheme() == null) uri = Uri.parse(ApiClient.baseUrl(this) + (rawUrl.startsWith("/") ? "" : "/") + rawUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Invalid download URL");
            SharedPreferences prefs = getSharedPreferences("moonix_settings", MODE_PRIVATE);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            boolean audio = "audio".equalsIgnoreCase(type);
            String safeName = safeFileName(filename);
            request.setTitle("Moonix · " + safeName);
            request.setDescription("Saving " + type + " to " + directory);
            request.setNotificationVisibility(prefs.getBoolean("notify_complete", true)
                    ? DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED : DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setAllowedOverMetered(!prefs.getBoolean("wifi_only", false));
            request.setAllowedOverRoaming(prefs.getBoolean("allow_roaming", false));
            String key = prefs.getString("api_key", "").trim();
            if (!key.isEmpty()) request.addRequestHeader("x-api-key", key);
            request.setMimeType(audio ? "audio/mpeg" : "video/mp4");
            String target = "Movies".equalsIgnoreCase(directory) ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS;
            request.setDestinationInExternalPublicDir(target, safeName + (audio ? ".mp3" : ".mp4"));
            long id = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this, "Added to Android Downloads (ID " + id + "). Open My Files → Downloads to track it.", Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Could not queue the file: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String safeFileName(String value) {
        String result = (value == null ? "Moonix download" : value).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ").trim().replaceAll("^[. ]+|[. ]+$", "");
        if (result.isEmpty()) result = "Moonix download";
        return result.length() > 120 ? result.substring(0, 120).trim() : result;
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) videoView.pause();
        if (webView != null) webView.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onDestroy() {
        activityFinished = true;
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
