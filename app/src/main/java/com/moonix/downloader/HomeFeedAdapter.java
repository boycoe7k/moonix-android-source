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

/** Large-card adapter for actual keyword-search results shown on Home. */
public final class HomeFeedAdapter extends RecyclerView.Adapter<HomeFeedAdapter.Holder> {
    public interface Actions {
        void onPlay(MediaItem item);
        void onDownload(MediaItem item);
    }

    private final List<MediaItem> items = new ArrayList<>();
    private final Actions actions;

    public HomeFeedAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<MediaItem> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_home_video, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = items.get(position);
        holder.title.setText(item.title);
        String creator = item.creator.isEmpty() ? "YouTube" : item.creator;
        String details = creator;
        if (!item.views.isEmpty()) details += " · " + item.views;
        if (!item.published.isEmpty()) details += " · " + item.published;
        if (item.views.isEmpty() && item.published.isEmpty()) details += " · YouTube search result";
        holder.creator.setText(details);
        if (item.duration.isEmpty()) {
            holder.duration.setVisibility(View.GONE);
        } else {
            holder.duration.setVisibility(View.VISIBLE);
            holder.duration.setText(item.duration);
        }
        Glide.with(holder.thumbnail)
                .load(item.thumbnail)
                .centerCrop()
                .placeholder(R.drawable.card_surface)
                .error(R.drawable.card_surface)
                .into(holder.thumbnail);
        View.OnClickListener playAction = v -> actions.onPlay(item);
        holder.play.setOnClickListener(playAction);
        holder.playOverlay.setOnClickListener(playAction);
        holder.thumbnail.setOnClickListener(playAction);
        holder.download.setOnClickListener(v -> actions.onDownload(item));
        holder.itemView.setOnClickListener(playAction);
    }

    @Override public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView creator;
        final TextView duration;
        final MaterialButton play;
        final MaterialButton playOverlay;
        final MaterialButton download;

        Holder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.home_video_thumbnail);
            title = itemView.findViewById(R.id.home_video_title);
            creator = itemView.findViewById(R.id.home_video_creator);
            duration = itemView.findViewById(R.id.home_video_duration);
            play = itemView.findViewById(R.id.home_video_play);
            playOverlay = itemView.findViewById(R.id.home_video_play_overlay);
            download = itemView.findViewById(R.id.home_video_download);
        }
    }
}
