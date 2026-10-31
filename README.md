# Moonix Android source

A Java/XML Android source project for CodeAssist or Android Studio.

- App label: `Moonix`
- Package/application ID: `com.moonix.downloader`
- Minimum Android: 8.0 (API 26)
- API base URL: `https://video-download-api-uohi.onrender.com/`
- UI direction: based on the supplied Moonix reference image and palette

## Open in CodeAssist

1. Extract the ZIP to a writable folder.
2. In CodeAssist, open/import the extracted project folder containing `settings.gradle`.
3. Allow Gradle sync. The project uses Android Gradle Plugin 8.5.2, Gradle 8.7, Java 17, and compile SDK 35.
4. Build/run the `app` module. If CodeAssist requests an SDK install, install Android SDK Platform 35 and Build Tools for API 35.

## Implemented API flow

- Text searches call `GET /api/search?q=...` (the backend currently searches YouTube).
- Supported URLs call `POST /api/media/info`.
- Video/audio actions call `POST /api/media/download` with the chosen quality/type.
- Moonix polls `GET /api/status/{jobId}` until completion or failure.
- Completed files are passed to Android `DownloadManager`, which stores them in the selected public Downloads or Movies folder and handles transfer progress/notifications.
- The profile screen allows a custom API URL and optional `x-api-key`.

The Movies tab uses the Play Mate deployment at `https://movie-lab.zone.id`. Search calls its `/api-omegatech/api/movie/MovieBox-pro?action=search` proxy. A movie card opens catalog details; choosing Download calls `/api/sources/{subjectId}` with movie parameters and offers direct HTTPS MP4 quality choices. HLS playlists are excluded because Android DownloadManager cannot save them as standalone MP4 files. The Movies tab replaces the dedicated Music tab; audio extraction remains available from supported VDL search results.

The backend’s configured platform list is YouTube, TikTok, Facebook, and Instagram. Search is YouTube-only. A source may reject a request or restrict downloading; use downloads only for media you own or are authorized to save. This app does not bypass DRM or access controls. The VDL service does not expose pause/resume controls for its extraction jobs; file transfer is handled by Android DownloadManager.

## Download settings

The settings screen includes VDL video quality choices (360p through 1080p plus Best available), a preferred movie quality or Ask every time, a public Downloads/Movies destination, Wi-Fi-only transfers, roaming permission, completion notifications, and an optional confirmation prompt. Audio extraction is MP3; its bitrate is fixed by the current VDL API, which does not expose an app-selectable audio bitrate. The maximum concurrent download selector is user-controlled and defaults to **Unlimited**; it offers 1, 2, 3, 5, and 10 active transfers as optional choices. There are no subscription screens, paid tiers, or app plan-based download caps. If a finite concurrency is selected, pending items wait in a FIFO queue while the app process remains alive; transfers already handed to Android DownloadManager continue under Android's background download service.

The Files tab reads local video/audio media with runtime permission. The Browser tab opens links in the device’s browser rather than embedding a downloader into arbitrary websites.

## UI polish

All in-app navigation, settings, media-placeholder, platform, and launcher icons are PNG assets. Tab changes use a short fade-and-slide transition, main actions have light press feedback, and custom Moonix Lottie animations show the idle/empty search state and API loading state.

## Assets

`brand_youtube.png`, `brand_instagram.png`, and `brand_facebook.png` are PNG platform marks sourced from Wikimedia Commons file pages. `brand_tiktok.png` is a TikTok PNG from StickPNG. They are displayed only as source/platform identifiers; the respective marks remain owned by their brand holders. `moonix_logo.png` and launcher icons are original Moonix lettermark artwork generated for this project.

Sources:
- YouTube: https://commons.wikimedia.org/wiki/File:YouTube_full-color_icon_(2017).svg
- Instagram: https://commons.wikimedia.org/wiki/File:Instagram_icon.png
- Facebook: https://commons.wikimedia.org/wiki/File:Facebook_f_logo_(2019).svg
- TikTok: https://www.stickpng.com/

## Project layout

- `app/src/main/java/com/moonix/downloader/`: API client, media model/adapter, and activity.
- `MOVIE_API_NOTES.md`: verified Play Mate API host, route shape, and source references.
- `app/src/main/res/layout/`: activity, search, browser, files, profile, and list-row XML.
- `app/src/main/res/raw/moonix_search.json` and `moonix_loading.json`: original Lottie search and loading animations; Lottie Android 6.7.1 renders them natively.
- `LOTTIE_SOURCE_NOTES.md`: official library setup and version references.
- `app/src/main/res/drawable-nodpi/`: PNG navigation/action icons, platform logos, and Moonix logo.
- `app/src/main/res/mipmap-*`: launcher icon PNGs.

This archive contains source code and image assets, not a signed APK or a published app.
