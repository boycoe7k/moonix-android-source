# Movie API integration notes

## Verified deployment

- GitHub repository: https://github.com/boycoe7k/play-mate
- Working API host (confirmed by the user and checked): https://movie-lab.zone.id
- Search proxy rewrite: `GET /api-omegatech/api/movie/MovieBox-pro?action=search&keyword={query}&page={page}`
- Movie source route: `GET /api/sources/{subjectId}?type=Movie&season=0&episode=0&path={detailPath}&title={title}`

The search route was checked against `https://movie-lab.zone.id` and returned movie records under the nested `data.data.items` response. The source route on the same host returned five MP4 quality entries during validation. The repository source also normalizes provider results at `server/api.js` and deploys `/api/sources/[id].js` for source lookup.

## Source references

- Search and source routes: https://github.com/boycoe7k/play-mate/blob/main/server/api.js
- Vercel serverless source route: https://github.com/boycoe7k/play-mate/blob/main/api/sources/%5Bid%5D.js
- Provider proxy routing: https://github.com/boycoe7k/play-mate/blob/main/vercel.json
- Production deployment: https://movie-lab.zone.id

The source route returns quality choices with URL/download fields. Use the movie source only as authorized by the user; respect the content provider’s rights and terms. The Android client downloads only direct MP4 sources through Android DownloadManager and skips HLS playlists.
