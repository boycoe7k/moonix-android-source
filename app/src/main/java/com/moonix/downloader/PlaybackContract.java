package com.moonix.downloader;

/** Shared intent contract for opening media in Moonix playback activities. */
public final class PlaybackContract {
    public static final String EXTRA_URI = "com.moonix.downloader.extra.MEDIA_URI";
    public static final String EXTRA_TITLE = "com.moonix.downloader.extra.MEDIA_TITLE";
    public static final String EXTRA_VIDEO_ID = "com.moonix.downloader.extra.VIDEO_ID";
    public static final String EXTRA_CREATOR = "com.moonix.downloader.extra.CREATOR";
    public static final String EXTRA_THUMBNAIL = "com.moonix.downloader.extra.THUMBNAIL";
    public static final String EXTRA_DURATION = "com.moonix.downloader.extra.DURATION";
    public static final String EXTRA_VIEWS = "com.moonix.downloader.extra.VIEWS";
    public static final String EXTRA_PUBLISHED = "com.moonix.downloader.extra.PUBLISHED";

    private PlaybackContract() { }
}
