package com.moonix.downloader;

/** Shared intent contract for opening local media in Moonix playback activities. */
public final class PlaybackContract {
    public static final String EXTRA_URI = "com.moonix.downloader.extra.MEDIA_URI";
    public static final String EXTRA_TITLE = "com.moonix.downloader.extra.MEDIA_TITLE";

    private PlaybackContract() { }
}
