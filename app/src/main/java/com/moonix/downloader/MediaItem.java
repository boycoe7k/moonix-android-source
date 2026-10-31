package com.moonix.downloader;

import android.net.Uri;

import org.json.JSONObject;

public class MediaItem {
    public final String id;
    public final String title;
    public final String creator;
    public final String duration;
    public final String thumbnail;
    public final String url;
    public final String platform;
    public final boolean local;
    public final boolean movie;
    public final boolean hasResource;
    public final String detailPath;
    public final String description;
    public final String releaseDate;
    public final String genre;
    public final String rating;

    public MediaItem(String id, String title, String creator, String duration, String thumbnail,
                     String url, String platform, boolean local) {
        this(id, title, creator, duration, thumbnail, url, platform, local,
                false, true, "", "", "", "", "");
    }

    public MediaItem(String id, String title, String creator, String duration, String thumbnail,
                     String url, String platform, boolean local, boolean movie, boolean hasResource,
                     String detailPath, String description, String releaseDate, String genre, String rating) {
        this.id = id == null ? "" : id;
        this.title = title == null || title.isEmpty() ? "Untitled media" : title;
        this.creator = creator == null ? "" : creator;
        this.duration = duration == null ? "" : duration;
        this.thumbnail = thumbnail == null ? "" : thumbnail;
        this.url = url == null ? "" : url;
        this.platform = platform == null ? "" : platform;
        this.local = local;
        this.movie = movie;
        this.hasResource = hasResource;
        this.detailPath = detailPath == null ? "" : detailPath;
        this.description = description == null ? "" : description;
        this.releaseDate = releaseDate == null ? "" : releaseDate;
        this.genre = genre == null ? "" : genre;
        this.rating = rating == null ? "" : rating;
    }

    public static MediaItem fromSearch(JSONObject json) {
        String videoId = json.optString("videoId", json.optString("id", ""));
        String mediaUrl = json.optString("url", videoId.isEmpty() ? "" : "https://www.youtube.com/watch?v=" + videoId);
        return new MediaItem(videoId, json.optString("title"), json.optString("channel"),
                json.optString("duration"), json.optString("thumbnail"), mediaUrl, "YouTube", false);
    }

    public static MediaItem fromInfo(JSONObject json) {
        return new MediaItem(json.optString("id"), json.optString("title"),
                json.optString("uploader"), json.optString("duration"),
                json.optString("thumbnail"), json.optString("webpageUrl"),
                json.optString("platform"), false);
    }

    public static MediaItem fromMovie(JSONObject json) {
        String id = json.optString("subjectId", json.optString("id", ""));
        String title = json.optString("title", json.optString("name", "Untitled movie"));
        JSONObject coverObject = json.optJSONObject("cover");
        String cover = coverObject == null ? json.optString("cover", "") : coverObject.optString("url", "");
        if (cover.isEmpty()) cover = json.optString("thumbnail", "");
        int subjectType = json.optInt("subjectType", 1);
        String type = json.optString("type", subjectType == 2 ? "TV Series" : "Movie");
        boolean isMovie = subjectType == 1 || "movie".equalsIgnoreCase(type);
        String year = json.optString("year", json.optString("releaseDate", ""));
        String genre = json.optString("genre", "");
        String rating = json.optString("imdbRatingValue", json.optString("rating", ""));
        String metaRating = rating.isEmpty() || "0".equals(rating) ? "" : "IMDb " + rating;
        String country = json.optString("countryName", "");
        String hasResourceValue = json.optString("hasResource", "true");
        boolean hasResource = json.optBoolean("hasResource", !"false".equalsIgnoreCase(hasResourceValue));
        return new MediaItem(id, title, year, genre, cover, "", metaRating, false,
                isMovie, hasResource, json.optString("detailPath", ""),
                json.optString("description", ""), json.optString("releaseDate", year),
                genre, rating);
    }

    public static MediaItem local(String title, String duration, Uri uri) {
        return new MediaItem(uri.toString(), title, "On this device", duration, "", uri.toString(), "Local", true);
    }
}
