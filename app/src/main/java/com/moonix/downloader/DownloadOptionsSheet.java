package com.moonix.downloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

/** Presents media analysis and download options before handing a request to the caller. */
public final class DownloadOptionsSheet {
    private DownloadOptionsSheet() { }

    public interface Listener {
        void onDownload(MediaItem item, String type, String quality, String filename, String directory);
    }

    public static void show(Activity activity, MediaItem item, String initialType, Listener listener) {
        if (activity == null || item == null || listener == null || !isActive(activity)) return;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(LayoutInflater.from(activity).inflate(R.layout.sheet_download_analyzing, null, false));
        dialog.setOnShowListener(ignored -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        View initialView = dialog.findViewById(android.R.id.content);
        TextView loadingTitle = dialog.findViewById(R.id.analyzing_media_title);
        if (loadingTitle != null) loadingTitle.setText(item.title);
        dialog.show();

        ApiClient.info(activity, item.url, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (!isCurrent(activity, dialog)) return;
                showReady(activity, dialog, item, initialType, listener, response, null);
            }
            @Override public void onError(String message) {
                if (!isCurrent(activity, dialog)) return;
                showReady(activity, dialog, item, initialType, listener, null,
                        "Media details could not be checked. You can still choose a quality and try the download job.");
            }
        });
    }

    private static void showReady(Activity activity, BottomSheetDialog dialog, MediaItem original,
                                  String initialType, Listener listener, @Nullable JSONObject info,
                                  @Nullable String warning) {
        if (!isCurrent(activity, dialog)) return;
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_download_ready, null, false);
        dialog.setContentView(content);

        MediaItem item = original;
        if (info != null) {
            MediaItem found = MediaItem.fromInfo(info);
            item = new MediaItem(found.id.isEmpty() ? original.id : found.id,
                    found.title.equals("Untitled media") ? original.title : found.title,
                    found.creator.isEmpty() ? original.creator : found.creator,
                    found.duration.isEmpty() ? original.duration : found.duration,
                    found.thumbnail.isEmpty() ? original.thumbnail : found.thumbnail,
                    found.url.isEmpty() ? original.url : found.url,
                    found.platform.isEmpty() ? original.platform : found.platform, false);
        }
        final MediaItem selectedItem = item;
        TextView title = content.findViewById(R.id.download_title);
        title.setText(selectedItem.title);
        TextView meta = content.findViewById(R.id.download_meta);
        String subtitle = selectedItem.creator;
        if (!selectedItem.duration.isEmpty()) subtitle = subtitle.isEmpty() ? selectedItem.duration : subtitle + " · " + selectedItem.duration;
        meta.setText(subtitle.isEmpty() ? selectedItem.platform : subtitle);
        ImageView preview = content.findViewById(R.id.download_preview);
        if (!selectedItem.thumbnail.isEmpty()) Glide.with(activity).load(selectedItem.thumbnail).centerCrop().into(preview);

        TextView warningView = content.findViewById(R.id.download_warning);
        if (warning != null) {
            warningView.setText(warning);
            warningView.setVisibility(View.VISIBLE);
        }
        TextView sizeView = content.findViewById(R.id.download_size);
        String availableSize = numericSize(info);
        if (availableSize != null) {
            sizeView.setText("Media size: " + formatSize(availableSize));
            sizeView.setVisibility(View.VISIBLE);
        } else {
            warningView.setText((warning == null ? "" : warning + " ") + "The API will choose the closest available format.");
            warningView.setVisibility(View.VISIBLE);
        }

        TextInputEditText filenameInput = content.findViewById(R.id.download_filename);
        String defaultName = sanitizeFilename(selectedItem.title);
        filenameInput.setText(defaultName);
        content.findViewById(R.id.rename_action).setOnClickListener(v -> showRenameDialog(activity, filenameInput));

        MaterialButtonToggleGroup directoryGroup = content.findViewById(R.id.download_directory_group);
        String savedDirectory = activity.getSharedPreferences("moonix_settings", Activity.MODE_PRIVATE)
                .getString("download_location", "Downloads");
        directoryGroup.check("Movies".equalsIgnoreCase(savedDirectory)
                ? R.id.directory_movies : R.id.directory_downloads);
        MaterialButtonToggleGroup typeGroup = content.findViewById(R.id.download_type_group);
        boolean wantAudio = "audio".equalsIgnoreCase(initialType) || "music".equalsIgnoreCase(initialType);
        typeGroup.check(wantAudio ? R.id.type_audio : R.id.type_video);
        RadioGroup videoQuality = content.findViewById(R.id.video_quality_group);
        RadioGroup audioQuality = content.findViewById(R.id.audio_quality_group);
        TextView qualityLabel = content.findViewById(R.id.quality_label);
        String savedQuality = activity.getSharedPreferences("moonix_settings", Activity.MODE_PRIVATE)
                .getString("quality", "720");
        if ("1080".equals(savedQuality) || "1080p".equalsIgnoreCase(savedQuality)) videoQuality.check(R.id.quality_1080);
        else if ("480".equals(savedQuality) || "480p".equalsIgnoreCase(savedQuality)) videoQuality.check(R.id.quality_480);
        else if ("360".equals(savedQuality) || "360p".equalsIgnoreCase(savedQuality)) videoQuality.check(R.id.quality_360);
        else if ("best".equalsIgnoreCase(savedQuality)) videoQuality.check(R.id.quality_best);
        else videoQuality.check(R.id.quality_720);
        updateQualityVisibility(wantAudio, videoQuality, audioQuality, qualityLabel);
        typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateQualityVisibility(checkedId == R.id.type_audio, videoQuality, audioQuality, qualityLabel);
        });

        content.findViewById(R.id.download_confirm).setOnClickListener(v -> {
            if (!isCurrent(activity, dialog)) return;
            String type = typeGroup.getCheckedButtonId() == R.id.type_audio ? "audio" : "video";
            String quality;
            if ("audio".equals(type)) {
                quality = "best";
            } else {
                int selected = videoQuality.getCheckedRadioButtonId();
                if (selected == R.id.quality_1080) quality = "1080p";
                else if (selected == R.id.quality_720) quality = "720p";
                else if (selected == R.id.quality_480) quality = "480p";
                else if (selected == R.id.quality_360) quality = "360p";
                else quality = "best";
            }
            String filename = sanitizeFilename(filenameInput.getText() == null ? "" : filenameInput.getText().toString());
            if (filename.isEmpty()) filename = defaultName;
            String directory = directoryGroup.getCheckedButtonId() == R.id.directory_movies ? "Movies" : "Downloads";
            dialog.dismiss();
            listener.onDownload(selectedItem, type, quality, filename, directory);
        });
    }

    private static void updateQualityVisibility(boolean audio, RadioGroup video, RadioGroup music, TextView label) {
        video.setVisibility(audio ? View.GONE : View.VISIBLE);
        music.setVisibility(audio ? View.VISIBLE : View.GONE);
        label.setText(audio ? "Audio quality" : "Video quality");
    }

    private static void showRenameDialog(Activity activity, TextInputEditText target) {
        if (!isActive(activity)) return;
        EditText entry = new EditText(activity);
        entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setText(target.getText());
        entry.setSelection(entry.length());
        new AlertDialog.Builder(activity)
                .setTitle("Rename file")
                .setView(entry)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> target.setText(sanitizeFilename(entry.getText().toString())))
                .show();
    }

    private static String numericSize(@Nullable JSONObject response) {
        if (response == null) return null;
        JSONObject details = response.optJSONObject("media");
        String[] keys = {"filesize", "size"};
        for (String key : keys) {
            Object raw = response.opt(key);
            if (isNumeric(raw)) return String.valueOf(raw);
            if (details != null && isNumeric(details.opt(key))) return String.valueOf(details.opt(key));
        }
        return null;
    }

    private static boolean isNumeric(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) return false;
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) && parsed >= 0;
        } catch (NumberFormatException ignored) { return false; }
    }

    private static String formatSize(String value) {
        try {
            double bytes = Double.parseDouble(value);
            if (bytes >= 1_000_000_000d) return String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000d);
            if (bytes >= 1_000_000d) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000d);
            if (bytes >= 1_000d) return String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000d);
            return String.format(Locale.getDefault(), "%.0f B", bytes);
        } catch (NumberFormatException ignored) { return value; }
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ").replaceAll("^[. ]+|[. ]+$", "");
        return cleaned.length() > 180 ? cleaned.substring(0, 180).trim() : cleaned;
    }

    private static boolean isActive(Activity activity) {
        return !activity.isFinishing() && (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private static boolean isCurrent(Activity activity, BottomSheetDialog dialog) {
        return isActive(activity) && dialog.isShowing();
    }
}
