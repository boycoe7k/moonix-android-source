package com.moonix.downloader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.Holder> {
    public interface Actions {
        void onVideo(MediaItem item);
        void onAudio(MediaItem item);
        void onDetails(MediaItem item);
    }

    private final List<MediaItem> items = new ArrayList<>();
    private final Actions actions;

    public MediaAdapter(Actions actions) { this.actions = actions; }

    public void submit(List<MediaItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_media, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = items.get(position);
        holder.title.setText(item.title);
        String meta = item.creator;
        if (!item.duration.isEmpty()) meta = meta.isEmpty() ? item.duration : meta + " · " + item.duration;
        if (!item.platform.isEmpty()) meta = meta.isEmpty() ? item.platform : meta + " · " + item.platform;
        holder.meta.setText(meta);
        if (item.movie) holder.itemView.setOnClickListener(v -> actions.onDetails(item));
        else holder.itemView.setOnClickListener(null);
        if (!item.thumbnail.isEmpty()) {
            Glide.with(holder.thumbnail).load(item.thumbnail).centerCrop().placeholder(R.drawable.card_surface).error(R.drawable.card_surface).into(holder.thumbnail);
        } else {
            holder.thumbnail.setImageResource(item.local ? R.drawable.icon_video : R.drawable.moonix_logo);
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
        if (item.movie) {
            holder.video.setText(item.hasResource ? "Download" : "Unavailable");
            holder.video.setEnabled(item.hasResource);
            holder.audio.setVisibility(View.GONE);
            holder.video.setOnClickListener(v -> { if (item.hasResource) actions.onVideo(item); });
        } else if (item.local) {
            holder.video.setText("Open");
            holder.video.setEnabled(true);
            holder.audio.setVisibility(View.GONE);
            holder.video.setOnClickListener(v -> actions.onVideo(item));
        } else {
            holder.video.setText("Video");
            holder.video.setEnabled(true);
            holder.audio.setVisibility(View.VISIBLE);
            holder.video.setOnClickListener(v -> actions.onVideo(item));
            holder.audio.setOnClickListener(v -> actions.onAudio(item));
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView meta;
        final MaterialButton video;
        final MaterialButton audio;

        Holder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.media_thumbnail);
            title = itemView.findViewById(R.id.media_title);
            meta = itemView.findViewById(R.id.media_meta);
            video = itemView.findViewById(R.id.media_video);
            audio = itemView.findViewById(R.id.media_audio);
        }
    }
}
