package com.moonix.downloader;

import android.app.DownloadManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Displays downloads recorded by Android's system DownloadManager. */
public class DownloadsActivity extends AppCompatActivity {
    private DownloadManager downloadManager;
    private DownloadsAdapter adapter;
    private TextView emptyMessage;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        RecyclerView list = findViewById(R.id.downloads_list);
        emptyMessage = findViewById(R.id.downloads_empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadsAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.downloads_back).setOnClickListener(v -> finish());
        findViewById(R.id.downloads_refresh).setOnClickListener(v -> refreshDownloads());
    }

    @Override protected void onResume() {
        super.onResume();
        refreshDownloads();
    }

    private void refreshDownloads() {
        List<DownloadRecord> records = new ArrayList<>();
        if (downloadManager != null) {
            DownloadManager.Query query = new DownloadManager.Query();
            // Deliberately no setFilterById or row limit: show every system download.
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                    int titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                    int descriptionCol = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION);
                    int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int dateCol = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
                    int mimeCol = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                    int localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    while (cursor.moveToNext()) {
                        long id = valueLong(cursor, idCol, -1);
                        String title = valueString(cursor, titleCol);
                        if (title.isEmpty()) title = valueString(cursor, descriptionCol);
                        if (title.isEmpty()) title = "Download " + id;
                        records.add(new DownloadRecord(id, title,
                                valueString(cursor, mimeCol), valueString(cursor, localUriCol),
                                (int) valueLong(cursor, statusCol, DownloadManager.STATUS_PENDING),
                                valueLong(cursor, bytesCol, 0), valueLong(cursor, totalCol, -1),
                                valueLong(cursor, dateCol, 0)));
                    }
                }
            } catch (RuntimeException ignored) {
                Toast.makeText(this, "Could not read downloads.", Toast.LENGTH_SHORT).show();
            }
        }
        adapter.setItems(records);
        emptyMessage.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static long valueLong(Cursor cursor, int column, long fallback) {
        return column >= 0 && !cursor.isNull(column) ? cursor.getLong(column) : fallback;
    }

    private static String valueString(Cursor cursor, int column) {
        return column >= 0 && !cursor.isNull(column) ? cursor.getString(column) : "";
    }

    private void openDownload(DownloadRecord item) {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return;
        Uri uri = null;
        try { uri = downloadManager.getUriForDownloadedFile(item.id); }
        catch (RuntimeException ignored) { /* handled below */ }
        if (uri == null && !item.localUri.isEmpty()) {
            try { uri = Uri.parse(item.localUri); }
            catch (RuntimeException ignored) { /* malformed URI */ }
        }
        if (uri == null) {
            Toast.makeText(this, "This download is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = item.mimeType == null ? "" : item.mimeType.toLowerCase(Locale.ROOT);
        String name = item.title.toLowerCase(Locale.ROOT);
        if (item.localUri != null && !item.localUri.isEmpty()) {
            String path = Uri.decode(Uri.parse(item.localUri).getLastPathSegment());
            if (path != null) name += " " + path.toLowerCase(Locale.ROOT);
        }
        boolean audio = mime.startsWith("audio/") || hasExtension(name,
                ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".oga", ".opus", ".flac", ".wma");
        Intent intent = new Intent(this, audio ? MusicPlayerActivity.class : VideoPlayerActivity.class);
        intent.setDataAndType(uri, mime.isEmpty() ? (audio ? "audio/*" : "video/*") : mime);
        intent.putExtra(PlaybackContract.EXTRA_URI, uri.toString());
        intent.putExtra(PlaybackContract.EXTRA_TITLE, item.title);
        try { startActivity(intent); }
        catch (Exception e) {
            Toast.makeText(this, "No player is available for this download.", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean hasExtension(String value, String... extensions) {
        for (String extension : extensions) if (value.contains(extension)) return true;
        return false;
    }

    private static final class DownloadRecord {
        final long id, downloaded, total, modified;
        final String title, mimeType, localUri;
        final int status;
        DownloadRecord(long id, String title, String mimeType, String localUri, int status,
                       long downloaded, long total, long modified) {
            this.id = id; this.title = title; this.mimeType = mimeType; this.localUri = localUri;
            this.status = status; this.downloaded = downloaded; this.total = total; this.modified = modified;
        }
    }

    private final class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.Holder> {
        private final List<DownloadRecord> items = new ArrayList<>();
        void setItems(List<DownloadRecord> records) {
            items.clear(); items.addAll(records); notifyDataSetChanged();
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_download, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            DownloadRecord item = items.get(position);
            holder.title.setText(item.title);
            holder.status.setText(statusLabel(item));
            holder.date.setText(item.modified > 0
                    ? DateFormat.getMediumDateFormat(DownloadsActivity.this).format(new Date(item.modified)) + " · "
                      + DateFormat.getTimeFormat(DownloadsActivity.this).format(new Date(item.modified))
                    : "Date unavailable");
            if (item.status == DownloadManager.STATUS_RUNNING && item.total > 0) {
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setIndeterminate(false);
                holder.progress.setMax(100);
                holder.progress.setProgress((int) Math.min(100, item.downloaded * 100 / item.total));
            } else if (item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_PAUSED) {
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setIndeterminate(true);
            } else {
                holder.progress.setVisibility(View.GONE);
            }
            boolean completed = item.status == DownloadManager.STATUS_SUCCESSFUL;
            holder.itemView.setAlpha(completed ? 1f : 0.82f);
            holder.itemView.setOnClickListener(v -> {
                if (completed) openDownload(item);
                else Toast.makeText(DownloadsActivity.this, "Only completed downloads can be opened.", Toast.LENGTH_SHORT).show();
            });
        }
        @Override public int getItemCount() { return items.size(); }

        private String statusLabel(DownloadRecord item) {
            switch (item.status) {
                case DownloadManager.STATUS_SUCCESSFUL: return "Completed";
                case DownloadManager.STATUS_FAILED: return "Failed";
                case DownloadManager.STATUS_PAUSED: return "Paused";
                case DownloadManager.STATUS_PENDING: return "Queued";
                case DownloadManager.STATUS_RUNNING:
                    if (item.total > 0) return String.format(Locale.getDefault(), "Downloading · %d%%", Math.min(100, item.downloaded * 100 / item.total));
                    return "Downloading";
                default: return "Status unavailable";
            }
        }
        final class Holder extends RecyclerView.ViewHolder {
            final TextView title, status, date;
            final ProgressBar progress;
            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.download_title);
                status = view.findViewById(R.id.download_status);
                date = view.findViewById(R.id.download_date);
                progress = view.findViewById(R.id.download_progress);
            }
        }
    }
}
