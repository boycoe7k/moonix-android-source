package com.moonix.downloader;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/** Local audio playback with seek, elapsed time, duration, and lifecycle-safe release. */
public class MusicPlayerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TextView errorView, elapsedView, durationView;
    private MaterialButton playButton;
    private SeekBar seekBar;
    private boolean prepared;
    private boolean seeking;
    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                if (!seeking) seekBar.setProgress(player.getCurrentPosition());
                elapsedView.setText(formatTime(player.getCurrentPosition()));
                playButton.setText(player.isPlaying() ? "Pause" : "Play");
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);
        errorView = findViewById(R.id.music_error);
        elapsedView = findViewById(R.id.music_elapsed);
        durationView = findViewById(R.id.music_duration);
        playButton = findViewById(R.id.music_play_pause);
        seekBar = findViewById(R.id.music_seek);
        String title = getIntent().getStringExtra(PlaybackContract.EXTRA_TITLE);
        ((TextView) findViewById(R.id.music_title)).setText(title == null || title.trim().isEmpty() ? "Audio" : title);
        playButton.setOnClickListener(v -> togglePlayback());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) elapsedView.setText(formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && prepared) player.seekTo(bar.getProgress());
                seeking = false;
            }
        });
        Uri uri = readUri();
        if (!isSupportedUri(uri) || isKnownWrongType(uri)) {
            showError("This audio can’t be opened. Choose a supported local audio file.");
            return;
        }
        try {
            player = new MediaPlayer();
            player.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                seekBar.setMax(mediaPlayer.getDuration());
                durationView.setText(formatTime(mediaPlayer.getDuration()));
                seekBar.setEnabled(true);
                playButton.setEnabled(true);
                mediaPlayer.start();
                handler.post(progressUpdater);
            });
            player.setOnCompletionListener(mediaPlayer -> {
                playButton.setText("Play");
                seekBar.setProgress(0);
                elapsedView.setText(formatTime(0));
            });
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                showError("Moonix couldn’t play this audio. The file may be unavailable or in an unsupported format.");
                return true;
            });
            player.setDataSource(this, uri);
            player.prepareAsync();
        } catch (Exception exception) {
            showError("This audio can’t be opened. Check that the file is still available.");
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

    private boolean isKnownWrongType(Uri uri) {
        String type = getContentResolver().getType(uri);
        return type != null && !type.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    private void togglePlayback() {
        if (player == null || !prepared) return;
        if (player.isPlaying()) player.pause(); else player.start();
        playButton.setText(player.isPlaying() ? "Pause" : "Play");
    }

    private void showError(String message) {
        handler.removeCallbacks(progressUpdater);
        if (player != null) { player.release(); player = null; }
        prepared = false;
        findViewById(R.id.music_controls).setVisibility(View.GONE);
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private static String formatTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && prepared && player.isPlaying()) player.pause();
        handler.removeCallbacks(progressUpdater);
    }

    @Override protected void onResume() {
        super.onResume();
        if (player != null && prepared) handler.post(progressUpdater);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
