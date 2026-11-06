package com.moonix.downloader;

import android.app.DownloadManager;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Unified list rows for device media and Android DownloadManager records. */
public class MyFilesAdapter extends RecyclerView.Adapter<MyFilesAdapter.Holder> {
    public interface Actions {
        void onOpen(Entry entry);
        void onCancel(Entry entry);
    }

    public static final class Entry {
        public final MediaItem mediaItem;
        public final long downloadId;
        public final int status;
        public final long bytesDownloaded;
        public final long totalBytes;
        public final String mimeType;
        public final Uri contentUri;
        public final String title;
        public final String thumbnail;

        private Entry(MediaItem mediaItem, long downloadId, int status, long bytesDownloaded,
                      long totalBytes, String mimeType, Uri contentUri, String title, String thumbnail) {
            this.mediaItem = mediaItem;
            this.downloadId = downloadId;
            this.status = status;
            this.bytesDownloaded = Math.max(0L, bytesDownloaded);
            this.totalBytes = totalBytes;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.contentUri = contentUri;
            this.title = title == null || title.trim().isEmpty() ? "Untitled file" : title;
            this.thumbnail = thumbnail == null ? "" : thumbnail;
        }

        /** Factory for a locally discovered MediaItem row. */
        public static Entry fromMedia(MediaItem item) {
            return fromMedia(item, item != null && inferMime(item).startsWith("audio/"));
        }

        /** Factory for a MediaStore row when the caller already knows the collection's media type. */
        public static Entry fromMedia(MediaItem item, boolean audio) {
            if (item == null) throw new IllegalArgumentException("MediaItem must not be null");
            return new Entry(item, -1L, 0, 0L, -1L, audio ? "audio/*" : "video/*", null,
                    item.title, item.thumbnail);
        }

        /** Factory for a DownloadManager row; pass its MIME type, nullable content URI and optional HTTP thumbnail. */
        public static Entry fromDownload(long downloadId, int status, long bytesDownloaded,
                                         long totalBytes, String mimeType, Uri contentUri,
                                         String title, String thumbnail) {
            return new Entry(null, downloadId, status, bytesDownloaded, totalBytes, mimeType,
                    contentUri, title, thumbnail);
        }

        /** Convenience overload when a DownloadManager record has no thumbnail. */
        public static Entry fromDownload(long downloadId, int status, long bytesDownloaded,
                                         long totalBytes, String mimeType, Uri contentUri, String title) {
            return fromDownload(downloadId, status, bytesDownloaded, totalBytes, mimeType,
                    contentUri, title, "");
        }

        public boolean isLocalMedia() { return mediaItem != null; }

        private static String inferMime(MediaItem item) {
            if (item.movie) return "video/*";
            String value = (item.title + " " + item.url).toLowerCase(Locale.ROOT);
            return value.matches(".*\\.(mp3|m4a|aac|wav|ogg|flac)(?:\\?.*)?$") ? "audio/*" : "video/*";
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Actions actions;

    public MyFilesAdapter(Actions actions) {
        if (actions == null) throw new IllegalArgumentException("Actions must not be null");
        this.actions = actions;
    }

    public void submit(List<Entry> data) {
        entries.clear();
        if (data != null) entries.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_my_file, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Entry entry = entries.get(position);
        holder.title.setText(entry.title);
        boolean audio = entry.mimeType.toLowerCase(Locale.ROOT).startsWith("audio/");
        holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (!entry.thumbnail.isEmpty() && entry.thumbnail.startsWith("http")) {
            Glide.with(holder.thumbnail).load(entry.thumbnail).centerCrop()
                    .placeholder(audio ? R.drawable.icon_audio : R.drawable.icon_video)
                    .error(audio ? R.drawable.icon_audio : R.drawable.icon_video).into(holder.thumbnail);
        } else if (!audio && entry.isLocalMedia() && entry.mediaItem.url.startsWith("content://")) {
            Glide.with(holder.thumbnail).load(Uri.parse(entry.mediaItem.url)).centerCrop()
                    .placeholder(R.drawable.icon_video).error(R.drawable.icon_video).into(holder.thumbnail);
        } else if (!audio && entry.status == DownloadManager.STATUS_SUCCESSFUL && entry.contentUri != null) {
            Glide.with(holder.thumbnail).load(entry.contentUri).centerCrop()
                    .placeholder(R.drawable.icon_video).error(R.drawable.icon_video).into(holder.thumbnail);
        } else {
            holder.thumbnail.setImageResource(entry.isLocalMedia()
                    ? (audio ? R.drawable.icon_audio : R.drawable.icon_video)
                    : R.drawable.icon_nav_download);
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }

        if (entry.isLocalMedia()) {
            String subtitle = entry.mediaItem.creator;
            if (!entry.mediaItem.duration.isEmpty()) subtitle = join(subtitle, entry.mediaItem.duration);
            holder.subtitle.setText(subtitle.isEmpty() ? "On this device" : subtitle);
            holder.progress.setVisibility(View.GONE);
            holder.action.setText("Play/Open");
            holder.action.setVisibility(View.VISIBLE);
            holder.action.setOnClickListener(v -> actions.onOpen(entry));
            holder.itemView.setOnClickListener(v -> actions.onOpen(entry));
        } else {
            holder.subtitle.setText(downloadStatus(entry));
            boolean active = isCancelable(entry.status);
            boolean successful = entry.status == DownloadManager.STATUS_SUCCESSFUL;
            if (active && entry.totalBytes > 0) {
                holder.progress.setIndeterminate(false);
                holder.progress.setMax(100);
                holder.progress.setProgress((int) Math.min(100L, entry.bytesDownloaded * 100L / entry.totalBytes));
                holder.progress.setVisibility(View.VISIBLE);
            } else if (active) {
                holder.progress.setIndeterminate(true);
                holder.progress.setVisibility(View.VISIBLE);
            } else {
                holder.progress.setVisibility(View.GONE);
            }
            if (successful) {
                holder.action.setText("Open");
                holder.action.setVisibility(View.VISIBLE);
                holder.action.setOnClickListener(v -> actions.onOpen(entry));
                holder.itemView.setOnClickListener(v -> actions.onOpen(entry));
            } else if (active) {
                holder.action.setText("Cancel");
                holder.action.setVisibility(View.VISIBLE);
                holder.action.setOnClickListener(v -> actions.onCancel(entry));
                holder.itemView.setOnClickListener(null);
            } else {
                holder.action.setVisibility(View.GONE);
                holder.action.setOnClickListener(null);
                holder.itemView.setOnClickListener(null);
            }
        }
    }

    private static boolean isCancelable(int status) {
        return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
                || status == DownloadManager.STATUS_PAUSED;
    }

    private static String downloadStatus(Entry entry) {
        String status;
        switch (entry.status) {
            case DownloadManager.STATUS_PENDING: status = "Pending"; break;
            case DownloadManager.STATUS_RUNNING: status = "Downloading"; break;
            case DownloadManager.STATUS_PAUSED: status = "Paused"; break;
            case DownloadManager.STATUS_SUCCESSFUL: status = "Downloaded"; break;
            case DownloadManager.STATUS_FAILED: status = "Failed"; break;
            default: status = "Download status unavailable"; break;
        }
        if (entry.totalBytes > 0 && isCancelable(entry.status)) {
            status += " · " + formatBytes(entry.bytesDownloaded) + " / " + formatBytes(entry.totalBytes);
        } else if (entry.totalBytes < 0 && isCancelable(entry.status)) {
            status += " · " + formatBytes(entry.bytesDownloaded);
        }
        return status;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String join(String left, String right) {
        if (TextUtils.isEmpty(left)) return right;
        if (TextUtils.isEmpty(right)) return left;
        return left + " · " + right;
    }

    @Override public int getItemCount() { return entries.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView subtitle;
        final ProgressBar progress;
        final MaterialButton action;

        Holder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.my_file_thumbnail);
            title = itemView.findViewById(R.id.my_file_title);
            subtitle = itemView.findViewById(R.id.my_file_subtitle);
            progress = itemView.findViewById(R.id.my_file_progress);
            action = itemView.findViewById(R.id.my_file_action);
        }
    }
}
