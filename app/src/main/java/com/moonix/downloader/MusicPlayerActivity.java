package com.moonix.downloader;

import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/** Local audio playback with metadata, seeking, and lifecycle-safe release. */
public class MusicPlayerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TextView errorView, elapsedView, durationView, titleView, artistView, albumView;
    private MaterialButton playButton, shuffleButton, repeatButton;
    private SeekBar seekBar;
    private boolean prepared;
    private boolean seeking;
    private boolean shuffleEnabled;
    private boolean repeatEnabled;

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                try {
                    if (!seeking) seekBar.setProgress(player.getCurrentPosition());
                    elapsedView.setText(formatTime(player.getCurrentPosition()));
                    updatePlayButton();
                    handler.postDelayed(this, 500);
                } catch (IllegalStateException ignored) {
                    // The player may have been released while a queued update was running.
                }
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);
        SystemInsets.apply(this);
        errorView = findViewById(R.id.music_error);
        elapsedView = findViewById(R.id.music_elapsed);
        durationView = findViewById(R.id.music_duration);
        titleView = findViewById(R.id.music_title);
        artistView = findViewById(R.id.music_artist);
        albumView = findViewById(R.id.music_album);
        playButton = findViewById(R.id.music_play_pause);
        shuffleButton = findViewById(R.id.music_shuffle);
        repeatButton = findViewById(R.id.music_repeat);
        seekBar = findViewById(R.id.music_seek);

        String suppliedTitle = getIntent().getStringExtra(PlaybackContract.EXTRA_TITLE);
        titleView.setText(suppliedTitle == null || suppliedTitle.trim().isEmpty() ? "Audio" : suppliedTitle);
        playButton.setOnClickListener(v -> togglePlayback());
        findViewById(R.id.music_previous).setOnClickListener(v -> restartCurrentTrack());
        findViewById(R.id.music_next).setOnClickListener(v ->
                Toast.makeText(this, "No queue is available", Toast.LENGTH_SHORT).show());
        shuffleButton.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            shuffleButton.setAlpha(shuffleEnabled ? 1f : 0.55f);
            shuffleButton.setContentDescription(shuffleEnabled ? "Shuffle on" : "Shuffle off");
        });
        repeatButton.setOnClickListener(v -> {
            repeatEnabled = !repeatEnabled;
            repeatButton.setAlpha(repeatEnabled ? 1f : 0.55f);
            repeatButton.setContentDescription(repeatEnabled ? "Repeat on" : "Repeat off");
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) elapsedView.setText(formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && prepared) {
                    try { player.seekTo(bar.getProgress()); } catch (IllegalStateException ignored) { }
                }
                seeking = false;
            }
        });

        Uri uri = readUri();
        if (!isSupportedUri(uri) || isKnownWrongType(uri)) {
            showError("This audio can’t be opened. Choose a supported local audio file.");
            return;
        }
        loadMetadata(uri, suppliedTitle);
        try {
            player = new MediaPlayer();
            player.setOnPreparedListener(mediaPlayer -> {
                prepared = true;
                seekBar.setMax(mediaPlayer.getDuration());
                durationView.setText(formatTime(mediaPlayer.getDuration()));
                seekBar.setEnabled(true);
                playButton.setEnabled(true);
                mediaPlayer.start();
                updatePlayButton();
                handler.post(progressUpdater);
            });
            player.setOnCompletionListener(mediaPlayer -> {
                if (repeatEnabled) {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                    updatePlayButton();
                } else {
                    playButton.setIconResource(R.drawable.icon_play);
                    playButton.setContentDescription("Play");
                    seekBar.setProgress(0);
                    elapsedView.setText(formatTime(0));
                }
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

    private void loadMetadata(Uri uri, String suppliedTitle) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String metadataTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (metadataTitle != null && !metadataTitle.trim().isEmpty()) titleView.setText(metadataTitle);
            else if (suppliedTitle == null || suppliedTitle.trim().isEmpty()) {
                String name = uri.getLastPathSegment();
                if (name != null && !name.trim().isEmpty()) titleView.setText(name);
            }
            artistView.setText(artist == null || artist.trim().isEmpty() ? "Unknown artist" : artist);
            albumView.setText(album == null || album.trim().isEmpty() ? "Unknown album" : album);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null && art.length > 0) {
                android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (bitmap != null) ((ImageView) findViewById(R.id.music_art)).setImageBitmap(bitmap);
            }
        } catch (RuntimeException ignored) {
            // Keep the supplied title, metadata placeholders, and icon_audio cover fallback.
        } finally {
            try { retriever.release(); } catch (RuntimeException ignored) { }
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
        try {
            String type = getContentResolver().getType(uri);
            return type != null && !type.toLowerCase(Locale.ROOT).startsWith("audio/");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void togglePlayback() {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) player.pause(); else player.start();
            updatePlayButton();
        } catch (IllegalStateException ignored) { }
    }

    private void restartCurrentTrack() {
        if (player == null || !prepared) return;
        try {
            player.seekTo(0);
            elapsedView.setText(formatTime(0));
            seekBar.setProgress(0);
            if (!player.isPlaying()) player.start();
            updatePlayButton();
        } catch (IllegalStateException ignored) { }
    }

    private void updatePlayButton() {
        boolean playing = player != null && prepared && player.isPlaying();
        playButton.setIconResource(playing ? R.drawable.icon_pause : R.drawable.icon_play);
        playButton.setContentDescription(playing ? "Pause" : "Play");
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
        if (player != null && prepared && player.isPlaying()) {
            try { player.pause(); } catch (IllegalStateException ignored) { }
            updatePlayButton();
        }
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
