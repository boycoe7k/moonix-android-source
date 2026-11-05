package com.moonix.downloader;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

/** Full-screen local video playback for device media and completed downloads. */
public class VideoPlayerActivity extends AppCompatActivity {
    private VideoView videoView;
    private TextView errorView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        videoView = findViewById(R.id.video_view);
        errorView = findViewById(R.id.video_error);
        Uri uri = readUri();
        String title = getIntent().getStringExtra(PlaybackContract.EXTRA_TITLE);
        ((TextView) findViewById(R.id.video_title)).setText(title == null || title.trim().isEmpty() ? "Video" : title);
        if (!isSupportedUri(uri) || (isKnownWrongType(uri, "video/"))) {
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
        try {
            videoView.setVideoURI(uri);
        } catch (RuntimeException exception) {
            showError("This video can’t be opened. Check that the file is still available.");
        }
    }

    private Uri readUri() {
        String value = getIntent().getStringExtra(PlaybackContract.EXTRA_URI);
        if (value == null || value.trim().isEmpty()) return null;
        try { return Uri.parse(value); } catch (RuntimeException exception) { return null; }
    }

    private boolean isSupportedUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    private boolean isKnownWrongType(Uri uri, String prefix) {
        String type = getContentResolver().getType(uri);
        return type != null && !type.toLowerCase(java.util.Locale.ROOT).startsWith(prefix);
    }

    private void showError(String message) {
        videoView.setVisibility(View.GONE);
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) videoView.pause();
    }

    @Override protected void onDestroy() {
        if (videoView != null) videoView.stopPlayback();
        super.onDestroy();
    }
}
