# MK21 (FBGs-Streaming) — Exhaustive Feature Inventory

**Scope:** existing features already implemented in MK21 across Android (`/app`), Smart TV (`/smart-tv/mk21-tv`) and the Web PWA (repo root → `/web`, `/public`).
**Purpose:** baseline for computing a gap against the Saimo-TV app.
**Analysis date:** repo state at `main` @ `469b5ac` (v2.9.0 / versionCode 290).
**Note:** nothing in the repo was modified; `node scripts/sync-web.mjs --check` was run read-only and reports **OK: 20 arquivos sincronizados**.

---

## A. Architecture summary per platform

### A.1 Canonical-source rule (cross-cutting)

The **repository root is the single source of truth for the web layer**. The following root files are canonical and are mirrored verbatim into `web/` and `public/`:

`index.html`, `sw.js`, `manifest.json`, `apps.json`, `servers.json`, `version.json`, `vercel.json`, `icon.svg`, `icon-192.png`, `icon-512.png`, `api/version.json`, `api/proxy.js`, `assets/logo.png`, plus the whole `css/` and `js/` directories — **20 files** total.

`scripts/sync-web.mjs` is the only writer for the mirrors:
- `node scripts/sync-web.mjs` → copies root → `web/` + `public/` (SHA-256 compared per file).
- `node scripts/sync-web.mjs --check` → fails (exit 1) listing every missing/divergent mirror file. This is the CI gate (`validate.yml`).
- `public/assets/aistudio/` is explicitly excluded from mirroring and never touched.

**Consequence:** any web edit must be made in the root and re-synced; editing `web/index.html` directly is a contract violation.

### A.2 Android / Android TV (`/app`)

| Aspect | Detail |
|---|---|
| Entry point | `app/src/main/java/com/example/MainActivity.kt` → `AppNavigation(viewModel)` (`ui/Screens.kt:158`) |
| Stack | Kotlin 2.1.10, Jetpack Compose (Material 3), AGP 9.1.1, `compileSdk 36`, `minSdk 24`, `targetSdk 36` |
| Identity | `applicationId = com.aistudio.multiservidor.iptvmk` (namespace `com.example`) |
| Package layout | Legacy app in `com.example` (`ui/`, `viewmodel/`, `data/`, `ai/`, `network/`, `player/`, `utils/`); newer hardening modules in `com.fbg.streaming` (`update/`, `security/`, `network/proxy/`) |
| State model | Single `AppViewModel` (AndroidViewModel, 2299 lines) exposing ~30 `StateFlow`/`SharedFlow`; Compose `collectAsState()`; Navigation-Compose for route switching |
| Persistence | Room DB `mk21_iptv_db` (v2, `MIGRATION_1_2` + destructive fallback) + `EncryptedSharedPreferences` (`mk21_secure_pref_store`) |
| Player | Media3 ExoPlayer 1.4.1 + HLS extension; fast-start `minBufferMs`/`bufferForPlaybackMs`; `PlaybackSourceFallback` for multi-source retry |
| OTA | `Mk21UpdateManager` (fail-closed SHA-256, HTTPS-only GitHub hosts, max 5 redirects) + `ApkSignatureVerifier` (certificate pin `EXPECTED_CERT_SHA256`) |
| Local proxy | `Mk21ProxyStation` (raw-socket HTTP proxy on 127.0.0.1 with per-session `SecureRandom` token) + `LocalHttpServer` (cast server) |
| Licensing | ECDSA P-256 server-issued tokens (`MKL1.<payload>.<sig>`), offline-verified by `LicenseTokenVerifier` against `BuildConfig.LICENSE_PUBKEY_B64` |
| AI | Firebase AI Logic — `GeminiAiService` (model `gemini-2.5-flash`) + `GeminiAiViewModel` + `GeminiCopilotDialog` |
| Build config | `app/build.gradle.kts` — release requires `LICENSE_SECRET` (≥16 chars), `STORE_PASSWORD`, `KEY_PASSWORD`, `KEYSTORE_PATH`; `KEY_ALIAS=upload` |

### A.3 Smart TV (`/smart-tv/mk21-tv`, LG webOS + Samsung Tizen)

| Aspect | Detail |
|---|---|
| Entry point | `smart-tv/mk21-tv/src/main.js` (esbuild entry) → bundled to `smart-tv/mk21-tv/app.js` (IIFE) |
| Runtime shell | `smart-tv/mk21-tv/index.html` (791 lines) + `styles.css` (4614 lines) + `libs/hls.min.js`, `libs/mpegts.min.js` |
| Stack | Vanilla ES5-ish JS (no framework); webOS 3.0 → webOS 24; Tizen 3.0+ (`smart-tv/tizen/config.xml`) |
| State model | Module-scoped globals + `localStorage`; a small "Expert Engine" with a Stack-based history FSM for BACK handling (`mk21HandleBack`, `mk21ArmBackHistory`) |
| Bundling | `esbuild.config.mjs` → `app.js`, format `iife`, target `es2019`, minified, **no sourcemap**, hard **350 KB limit** (build fails above it); `hls.js` aliased to `src/shims/hls-global.js` (reexports `window.Hls`) so hls.js is **not** bundled |
| OTA | App-level OTA delivers only `app.js` + `styles.css` (`mk21_ota_app_js`, `mk21_ota_app_version`, `mk21_ota_styles_css`); remote engine flag from `smart-tv/version.json` → `playerEngine` |
| Player architecture | `PlayerService` (adapter) → `PlayerFactory` (TypeScript, `src/players/`) with `TizenAVPlayPlayer`, `WebOSVideoPlayer`, `HLSFallbackPlayer`; legacy path in `src/legacy/app-legacy.js` (9618 lines) |
| Packaging | `smart-tv/package_ipk.py` → reproducible GNU-AR `.ipk` (webOS); `appinfo.json` validated (icon 80×80, largeIcon 130×130, splash 1920×1080) |
| Store catalog | `apps.json` / `smart-tv/apps.json` (Homebrew Channel format) with `ipkUrl` + `ipkHash.sha256` |

### A.4 Web PWA (repo root → `web/` → `public/`)

| Aspect | Detail |
|---|---|
| Entry point | root `index.html` (7119 lines, 418 KB) — a **single-file** app: HTML + ~2600 lines inline JS + inline CSS |
| Stack | Vanilla JS + precompiled Tailwind (`css/tailwind.min.css`) + `css/styles.css` + `css/responsive.css`; CDN `hls.js` (latest) + `mpegts.js@1.7.3`; FontAwesome 6.4 |
| Sub-modules | `js/performance.js`, `js/accessibility.js`, `js/seo-meta.js`, `js/tv-remote-and-pwa.js` (loaded as plain `<script>` tags, non-module) |
| State model | Module-scoped `let` globals + `MK21_CONFIG` object (`localStorage`) + IndexedDB playlist cache + `sessionStorage` EPG cache |
| Serverless | Vercel: `api/proxy.js` (streaming proxy) + `api/license/{activate,refresh}.js` (Neon Postgres) + `api/license/_token.js` |
| PWA | `manifest.json` (standalone, `#E50914`), `sw.js` (network-first, cache `mk21-cache-v2.9.0`) |
| Routing | `vercel.json` rewrites; `/api/proxy` → `/api/proxy.js`, `/web/*` → `/index.html` |

---

## B. Numbered FEATURE INVENTORY

### B.1 Web PWA — 52 features

| # | Feature | What the user does | Implementation notes | Key files |
|---|---|---|---|---|
| 1 | **Login gateway screen** | Picks server, enters user/pass, logs in | Two tabs: "Predefined" (`tab-login-predefined`) and "Manual" (`tab-login-manual`); error banner with shake animation | `index.html:279-433` |
| 2 | **Predefined server picker** | Selects one of the 7 servers | `<select id="server-selector">`; default fallback = `CB6000`; selected label mirrored to `#selected-server-name-label` | `index.html:324-338`, `populateServers()` `:2955` |
| 3 | **Fast server chips** | Taps a chip to switch server instantly | Top-6 chips rendered from `servers.json`; active chip highlighted with emerald dot | `renderFastServerChips()` `index.html:3020` |
| 4 | **Remote `servers.json` sync** | App auto-refreshes the server list | `fetch('./servers.json?ts=' + Date.now())` with cache-bust; falls back to inline `PREDEFINED_SERVERS` replica | `syncServersFromSource()` `index.html:2942`; inline replica `:1657` |
| 5 | **Manual M3U URL login** | Pastes a `get.php?username=…` URL | `#manual-m3u-input` + paste button; URL used as `baseUrl` | `index.html:341-355` |
| 6 | **Remember-credentials toggle** | Checks/unchecks to persist login | `#remember-credentials-checkbox`; when off, `mk21_username`/`mk21_password` are removed | `index.html:390`, `:4200-4209` |
| 7 | **Show/hide password** | Toggles the eye icon | `#toggle-password` swaps `fa-eye-slash` ↔ `fa-eye` | `index.html:381` |
| 8 | **Clipboard paste buttons** | Pastes URL / user / pass from clipboard | `navigator.clipboard.readText()` with a `handlePaste(targetInput)` helper | `index.html:3360` |
| 9 | **Server code auto-resolve** | Types a server code, app resolves it | `resolveServerByCode(code)` matches `lotusCode`/`webCode`/`name` | `index.html:3134`, used at `:4156` |
| 10 | **Quick profiles (saved logins)** | Saves and reuses server+credential sets | `mk21_saved_profiles` in localStorage; rendered as chips, max 12 | `saveQuickProfile()` `:3210`, `renderQuickProfiles()` `:3238` |
| 11 | **Offline mode / local M3U upload** | Picks a `.m3u/.m3u8/.txt` file from the device | `FileReader` → `parseM3U()` → full catalog without any server | `triggerLocalM3uUpload()` `:2800`, `handleLocalM3uFile()` `:2806` |
| 12 | **Multi-server automatic failover** | Logs in; app silently switches servers until one authenticates | Candidate list = selected server first, then all `PREDEFINED_SERVERS`; Xtream `player_api.php` auth probe (`user_info.auth`) | `performLogin()` `index.html:4142`, failover loop `:4322-4364` |
| 13 | **Six-method proxy rotation** | (implicit) app tries 6 transports to reach the server | `vercel` (own serverless), `direct`, `corsproxy.io`, `allorigins`, `codetabs`, `thingproxy`; ordered by measured latency | `index.html:4281-4288` |
| 14 | **Proxy latency benchmark** | App measures proxy RTT and shows "Aceleração de Conexão Ativada" | Parallel GET to `cloudflare.com/cdn-cgi/trace` through 4 proxies, 3.5 s timeout | `runProxyLatencyCheck()` `index.html:5800` |
| 15 | **IndexedDB playlist cache** | Login is instant on revisit | DB `mk21_playlist_cache`, store `playlists`, key `playlist_<base64>_<user>`; 7-day TTL; silent background refresh when > 3 h old | `openCacheDB()` `:3933`, `getCachedPlaylist()` `:3951` |
| 16 | **Cache integrity sweeper** | (implicit) startup removes corrupt/duplicate/expired rows | `verifyAndCleanIndexedDBCache()` — drops null keys, malformed objects, expired entries, dedupes by URL | `index.html:4045` |
| 17 | **Xtream Codes catalog ingestion** | (implicit) live/movie/series lists load from `player_api.php` | `get_live_categories`, `get_live_streams`, then background `get_vod_streams` + `get_series` after 1.2 s | `index.html:4380-4459` |
| 18 | **M3U fallback path** | (implicit) used when `player_api.php` is unavailable | `get.php?type=m3u_plus&output=ts` with HTML/credential-error detection | `index.html:4466-4519` |
| 19 | **Progressive M3U parser (non-blocking)** | (implicit) huge playlists parse without freezing the UI | Character-scanning parser with `fastExtractAttr()` (no regex in hot loop); BOM handling; live/movie/series heuristics | `parseM3UProgressively()` `index.html:5088` |
| 20 | **Background VOD/series loading** | Live channels appear first; VOD fills in behind | `startBackgroundLoading()` with a `bg-sync-indicator` progress HUD (`#bg-sync-progress`) | `index.html:5281`, `#bg-sync-indicator` `:477` |
| 21 | **Multi-segment loader HUD** | Watches live/movies/series progress bars during sync | Three progress bars + a total bar in `#app-loader` | `index.html:1351-1423`, `updateCategoryLoaderProgress()` `:3880` |
| 22 | **Type filter (Live / Movies / Series)** | Switches catalog type | `.type-filter-btn` with `data-type`; `activateLiveHomeView()` resets to LIVE | `index.html:2306-2320` |
| 23 | **Category sidebar** | Filters by provider category | O(N) frequency counter (not N² filters); per-category item counts; desktop sidebar + mobile sheet + horizontal chip bar | `renderCategories()` `index.html:5342` |
| 24 | **Horizontal quick-category chips** | Horizontal scroll of top-35 categories | Netflix/TopIbo-style chip row above the grid | `index.html:5393-5415` |
| 25 | **Saimo-style live channel grid** | Browses channels as square logo cards | `.canal-grid` / `.channel-grid-card`; active channel highlighted; SVG placeholder on logo error with proxy retry | `index.html:5616-5651`; CSS `:143-245` |
| 26 | **Movie poster grid** | Browses VOD as 2:3 posters | Poster with `referrerpolicy=no-referrer`, `loading=lazy`, proxy retry then inline SVG fallback | `index.html:5586-5615` |
| 27 | **Series grouping (Netflix engine)** | Sees one card per show, not per episode | `groupSeriesItems()` parses `SxxExx`, `TxxExx`, `Sx EPx`, `Temp. N Ep. N`, `01x02`, `Temporada N Episódio M`, `EP NN` | `index.html:4834-4955` |
| 28 | **Season + episode selector** | Picks a season, then an episode | `#season-selector` + `#episodes-list`; auto-plays episode 1 on wide screens | `selectShow()` `:4957`, `renderEpisodesForSeason()` `:5029` |
| 29 | **Auto-next episode** | Next episode starts when the current one ends | `yt-next-btn` shown when a next episode exists; `playNextEpisode()` on `ended` | `index.html:6128`, `:6966-6979` |
| 30 | **Search** | Searches channels/movies/series by name or category | `#search-input`, live filter on `input`; a "Resultados da Busca" state | `renderMediaItems()` `index.html:5472-5490` |
| 31 | **Favorites ("Meus Favoritos")** | Hearts a channel/movie/episode | `mk21_favorites` localStorage; dedicated pseudo-category with live count badge `#fav-count-badge` | `toggleFavorite()` `:4770`, `:5366` |
| 32 | **Continue Watching** | Recently played items reappear as a rail | `mk21_continue`, max 12 items, per-item trash button | `addToContinueWatching()` `:4660`, `renderContinueWatching()` `:4691` |
| 33 | **VOD resume with seconds** | Reopens a movie exactly where it stopped | `mk21_vod_resume_progress` (url → `{currentTime,duration,name,updatedAt}`); saved every 4 s for media > 60 s; cleared at > 95 %; resume prompt with "Continuar"/"Do Início" | `js/tv-remote-and-pwa.js:104-219` |
| 34 | **Resume last channel on login** | App re-tunes to the last channel | `MK21_CONFIG.last_channel_id` matched by id/stream_id/name | `resumeLastChannelIfAvailable()` `index.html:2782` |
| 35 | **Zero-delay zapping prefetch** | Channel changes feel instant | Pre-fetches the next 2 manifests with `Range: bytes=0-2048`, 4 s abort | `prefetchZeroDelayChannels()` `index.html:2715` |
| 36 | **YouTube-style custom player** | Full custom control bar | Play/pause, scrub bar (click+drag), buffer bar, volume slider, elapsed/duration, live indicator, big-play animation, auto-hide controls after 2.5 s, single-click play/pause, double-click fullscreen | `initYouTubePlayer()` `index.html:6357-6969` |
| 37 | **Spatial D-Pad navigation** | Remote arrow navigation across the whole UI | Geometric focus engine (`moveSpatial`) over `SPATIAL_SELECTOR`; `.tv-focused` ring styling | `index.html:6414-6504` |
| 38 | **Smart TV remote key mapping** | Color/media/channel keys do useful things | BACK `10009`/`461`/Esc; RED=type toggle, GREEN=refresh list, YELLOW=cast, BLUE=fullscreen; Play/Pause `415`/`19`/`10252`, Stop `413`, FF `417` +15 s, RW `412` −15 s; ChannelUp/Down = zapping | `js/tv-remote-and-pwa.js:282-525` |
| 39 | **Sleep timer ("Modo Soneca")** | Sets a timer that pauses playback | Menu with several minute values; live countdown badge `#yt-sleep-badge` | `js/tv-remote-and-pwa.js:533-574`; menu `index.html:751-763` |
| 40 | **Playback speed (up to 4×)** | Changes playback rate | `#yt-speed-menu`; label `#yt-speed-label` | `js/tv-remote-and-pwa.js:579-590`; menu `index.html:768-781` |
| 41 | **Audio booster (+200 %)** | Boosts loudness via Web Audio | `AudioContext` → `GainNode` → `MediaElementSource`; label `#yt-boost-label` | `js/tv-remote-and-pwa.js:599-626`; menu `index.html:787-795` |
| 42 | **Audio track & subtitle picker** | Switches audio/subtitle tracks | Reads `hlsPlayer.audioTracks`/`subtitleTracks` and native `video.audioTracks`/`textTracks` | `openAudioSubsModal()` `index.html:6984`; modal `:1147` |
| 43 | **Picture-in-Picture** | Floats the player in a mini window | Standard PiP + `webkitSetPresentationMode` fallback | `togglePictureInPicture()` `index.html:6331`; button `:807` |
| 44 | **Cinema / theater mode** | Widens the player column | `.theater-mode-active`, width `min(58vw, 860px)` | `toggleTheaterMode()` `index.html:6916` |
| 45 | **CSS upscaling ("HD Enhance")** | Sharpens the picture | Toggles `.upscale-filter` (`contrast/saturate/brightness`); persisted in `MK21_CONFIG.upscaling_active` | `toggleUpscaleFilter()` `index.html:2757`; CSS `:258` |
| 46 | **Cast / mirror to TV** | Sends playback to a TV | AirPlay (`webkitShowPlaybackTargetPicker`) → Remote Playback API → Google Cast SDK → manual URL modal (`#web-cast-modal` + copy button) | `js/tv-remote-and-pwa.js:660-767` |
| 47 | **External player hand-off (VLC)** | Opens the stream in VLC | `vlcs://` / `intent://…package=org.videolan.vlc` / `vlc-x-callback://` per UA | `getVlcUrl()` `index.html:5891`; buttons `:861`, `:1005` |
| 48 | **Auto-external-player mode** | Makes the external player the default | `mk21_auto_external` toggle; skips the web player entirely | `toggleAutoExternalPlayer()` `index.html:5984`, used `:6146` |
| 49 | **Mixed-content (HTTP on HTTPS) handling** | (implicit) HTTP-only servers still play | Auto-switches to the HTTPS serverless proxy; warns the user; offers a "connection direct" override | `getEffectiveProxyMethod()` `:5722`, `updateHttpPlaybackNotice()` `:5761` |
| 50 | **MPEG-TS playback via mpegts.js** | Raw `.ts` channels play in-browser | `mpegts.createPlayer({type:'mpegts', isLive})` with worker + range seek | `index.html:6186-6222` |
| 51 | **HLS↔TS auto-fallback** | (implicit) channel recovers from a format mismatch | On fatal manifest error, falls back from HLS to native MPEG-TS and rotates the proxy list | `index.html:2578-2613` |
| 52 | **EPG — real short EPG per channel** | Sees "NO AR: <program>" on the channel card | `player_api.php?action=get_short_epg&stream_id=…&limit=12`; multi-key cache (`id_`, `tvgid_`, `tvgname_`, `name_`) + `sessionStorage` hydration | `EpgManager.fetchRealEpg()` `index.html:1782` |
| 53 | **EPG — full XMLTV guide modal** | Opens the full schedule grid | `xmltv.php` fetched and regex-parsed into a per-channel programme map; modal shows "NO AR" + upcoming list | `loadServerXmltv()` `:1858`, `openScheduleModal()` `:2143`, modal `:1186` |
| 54 | **EPG — simulated grid fallback** | (implicit) non-real EPG when the server has none | Hardcoded Brazilian broadcaster grids (Globo, Record, SBT, Band, sports, premium channels) | `getSimulatedProgramInfo()` `index.html:1962-2097` |
| 55 | **EPG refresh button** | Re-fetches the current channel's guide | `window.refreshActiveChannelEpg()` invalidates the cache keys and refetches | `index.html:2259` |
| 56 | **Settings modal (3 tabs)** | Account / Preferences / Notifications | `switchSettingsTab('account'|'preferences'|'notifications')`; preferences include cache, hero banner and category chips toggles | `index.html:1426-1546`, `:2478` |
| 57 | **High-contrast mode** | Improves readability | `.high-contrast` body class, persisted | `toggleHighContrast()` `index.html:5710` |
| 58 | **PWA install flow** | Installs as an app | `beforeinstallprompt` capture, install modal offering APK or PWA, `appinstalled` handler, SW registration, HTTP→HTTPS auto-redirect | `js/tv-remote-and-pwa.js:1-99`; modal `index.html:1238` |
| 59 | **Service worker offline shell** | App shell loads offline | Network-first, cache `mk21-cache-v2.9.0`; deliberately skips `/api/`, proxy URLs and range requests | `sw.js:2-63` |
| 60 | **Accessibility layer** | Keyboard-only navigation | Focus trap in dialogs, arrow-key list navigation, skip link, ARIA labels on 8 controls, live regions, `prefers-reduced-motion` / `prefers-color-scheme` | `js/accessibility.js` (294 lines) |
| 61 | **Performance layer** | Smoother list rendering | Debounce, TTL cache, request dedup, `IntersectionObserver` lazy images, virtual scroller helper | `js/performance.js` (206 lines) |
| 62 | **SEO / metadata layer** | Better link previews | OG + Twitter cards, JSON-LD `WebApplication`, robots, canonical, hreflang, preload/dns-prefetch | `js/seo-meta.js` (246 lines) |
| 63 | **In-app diagnostics console** | Exports a playback log | Per-player log with redaction of credentials; copy + download | `addLog()` `:2345`, `downloadDiagnosticLogs()` `:2407`, `#diagnostic-log-console` `:1057` |
| 64 | **Player error overlay** | Sees why a channel failed | Retry, "open in VLC", "open with other app" | `#player-error-overlay` `index.html:841` |
| 65 | **Hero banner / highlight carousel** | Featured content on the home screen | `#hero-banner-section` with play / detail / favorite and rotation dots | `index.html:549-596` |
| 66 | **Logout** | Returns to the login screen | Stops the stream, clears background loading, animated transition | `performLogout()` `index.html:4588` |
| 67 | **Toast notifications** | Feedback on every action | `showNotification(message, type)` with success/error/info variants, auto-dismiss 3 s | `index.html:4620` |
| 68 | **Copy stream URL** | Copies the raw URL | `copyCurrentStreamUrl()` + `#stream-url-display` (selectable) | `index.html:6324`, `:1012` |

> (The numbering above is sequential; the Web surface is **68** concrete features.)

### B.2 Smart TV — 47 features

| # | Feature | What the user does | Implementation notes | Key files |
|---|---|---|---|---|
| 1 | **Fullscreen login modal** | Enters user/pass on the TV | Absolute-black modal; identity shown on boot; `handleLoginSubmit()` | `mk21-tv/index.html:33`, `app-legacy.js:8969` |
| 2 | **Offline bypass login** | Opens the cached catalog without credentials | `handleOfflineBypass()`; `getBestAvailableCache(preferredId)` | `app-legacy.js:9072`, `:9084` |
| 3 | **Session token verification** | (implicit) app validates the saved session before unlocking | `verifySessionToken(token)` → `mk21_auth_token` | `app-legacy.js:9254` |
| 4 | **3-column unified home** | Categories | Items | Player + info | `mk21-tv/index.html:152-257` (`#sectionUnified`) |
| 5 | **Content-type tabs (Live/Movies/Series/Favorites)** | Switches catalog section | `switchContentType(type)` | `app-legacy.js:5130` |
| 6 | **Category list** | Filters by group | `buildCurrentCategories()` / `renderCategoriesList()` / `selectCategory()` | `app-legacy.js:2133`, `:2184`, `:2232` |
| 7 | **Item list with virtual paging** | Scrolls the catalog | `renderItemsList(preserveScroll)` + IndexedDB catalog index with `queryCatalogIndexPage` / `searchCatalogIndex` | `app-legacy.js:2254`, `:459`, `:486` |
| 8 | **Indexed favorites query** | Favorites load from the index | `queryIndexedFavorites(serverId, favoriteSet)` / `hydrateIndexedFavorites()` | `app-legacy.js:529`, `:553` |
| 9 | **Omnisearch (global search)** | Searches across all servers and types | Dedicated modal `#modalOmnisearch` with per-type counters (`countOmniAll/Live/…`) | `app-legacy.js:3292`, `:3320`, `:3368`; modal `index.html:699` |
| 10 | **Series modal (seasons + episodes)** | Browses a show's seasons/episodes | `#modalSeriesEpisodes`, Netflix-style two columns | `app-legacy.js:2536`, `:2645`; modal `index.html:445` |
| 11 | **EPG — real XMLTV match** | Sees current/next programme on the OSD | `matchEPG()`, `applyRealSmartTvEpg()`, `parseXmltvTimestamp()`, base64 decode `decodeSmartTvEpgText()` | `app-legacy.js:2775`, `:2879`, `:2913` |
| 12 | **EPG — simulated programme grid** | (implicit) fallback grid | `getSimulatedTvProgram(channelName)` | `app-legacy.js:2707` |
| 13 | **Full EPG guide modal** | Opens the complete schedule | `#modalEpgGuide` | `app-legacy.js:3437`; modal `index.html:728` |
| 14 | **Adult-content PIN** | Unlocks 18+ content | `mk21_adult_pin`; PIN modal `#modalPin` + `verifyPin()` | `app-legacy.js:8326`, `:8364`; modal `index.html:407` |
| 15 | **Adult category lock** | (implicit) blocks adult categories until the PIN is entered | `isAdult()` heuristic over name/category | `app-legacy.js:1034` |
| 16 | **Favorites** | Toggles a favorite | `mk21_favs`; `toggleFav(url)`; heart button `#btnFavorite` | `app-legacy.js:984`; button `index.html` header |
| 17 | **Continue Watching** | Re-opens recent items | `mk21_continue_watching`; `recordWatchedItem()` | `app-legacy.js:1016` |
| 18 | **Resume last channel** | Tunes to the last channel at boot | `mk21_last_channel`; `resumeLastChannelIfPossible()` | `app-legacy.js:803`, `:823` |
| 19 | **Numeric channel entry (0–9)** | Types a channel number | Overlay `#channelNumberOverlay`, `handleDigitPress()` / `commitChannelNumber()` | `app-legacy.js:3217`, `:3230`; overlay `index.html:650` |
| 20 | **Channel up/down zapping** | Moves between channels | `playNextChannel()` / `playPreviousChannel()` | `app-legacy.js:4635`, `:4643` |
| 21 | **Multi-source failover across servers** | (implicit) channel recovers from a dead server | `mk21AutoAdvanceServer()`, `mk21TryAlternativeSource()`, `mk21SameChannel()`, `mk21ResetSourceFailover()` | `app-legacy.js:9505`, `:9555`, `:9550`, `:9537` |
| 22 | **Alternative source grouping by tvg-id** | One card, several sources | `core/AlternativeSources.js` — identity by `tvg-id` (with name+category+type fallback), `alternateUrls` array, cross-server matching with quality-suffix normalization | `core/AlternativeSources.js` |
| 23 | **Player options modal (quality / sources / super-res)** | Picks quality or an alternate source | `mk21EnsurePlayerOptionsModal()`, `mk21RenderPlayerOptionsTabs()` | `app-legacy.js:9380`, `:9400`; `components/PlayerOverlay.js` |
| 24 | **Playback quality selection** | Picks 1080p / 4K etc. | `mk21QualityPref()`, `mk21QualityHeights()`, `mk21ApplyPlaybackQuality(hls)` | `app-legacy.js:9279`, `:9294`; modal `:9319` |
| 25 | **Buffer mode / fluidity** | Trades latency vs. stability | `mk21BufferMode()`, `cycleBufferMode()` | `app-legacy.js:4105`, `:4892` |
| 26 | **Stall watchdog + auto-reconnect** | (implicit) recovers a frozen stream | `startStallWatchdog()`, `triggerAutoReconnect()`, `attemptDnsFailover()` | `app-legacy.js:3591`, `:3654`, `:3504` |
| 27 | **DNS failover for dead hosts** | (implicit) resolves an alternative IP | `attemptDnsFailover(originalUrl)` | `app-legacy.js:3504` |
| 28 | **Audio & subtitle modal** | Switches tracks | `#modalAudioSubs`; `openAudioSubsModal()` | `app-legacy.js:4965`; modal `index.html:288` |
| 29 | **Playback speed** | Changes rate | `cyclePlaybackSpeed()` / `applyPlaybackSpeed(rate)` | `app-legacy.js:4943`, `:4927` |
| 30 | **Aspect-ratio cycling** | Changes the picture ratio | `cycleAspectRatio()` | `app-legacy.js:3143` |
| 31 | **Fullscreen + HUD** | Enters fullscreen with an OSD | `toggleFullscreen(force)`, `showFsHud()`, `mk21TeleportModalForFullscreen()` | `app-legacy.js:4709`, `:4657`, `:4695` |
| 32 | **Strict D-Pad focus engine** | Remote navigation never gets lost | `mk21MoveFocus(direction)`, `mk21FocusSafe()`, `mk21ScrollActive()`, `mk21GetFocusables()`, `mk21ResetViewportScroll()` | `app-legacy.js:7584`, `:7573`, `:7835` |
| 33 | **Stack-based BACK history** | BACK closes overlays then exits (with confirm) | `mk21HandleBack()`, `mk21ArmBackHistory()`, `mk21AskExit()` / `mk21ExitApp()` | `app-legacy.js:7727`, `:7778`, `:7710` |
| 34 | **WebOS scroll-rail fix** | Scrollbars behave on LG TVs | `initWebOSScrollFix()`, `mk21EnsureVisualScrollbarRail()` | `app-legacy.js:8728`, `:8650` |
| 35 | **Virtual-keyboard handling** | On-screen keyboard input works | `setupVirtualKeyboardHandling()` | `app-legacy.js:8960` |
| 36 | **Native voice search (Tizen)** | Speaks a query | `startNativeVoiceSearch()` with WebSpeech fallback | `app-legacy.js:8269` |
| 37 | **Server manager modal** | Adds / edits / deletes servers | `#modalServerPicker`; `editServer()`, `deleteServer()`, `renderServerPickerList()` | `app-legacy.js:6915`, `:7041`, `:7063`; modal `index.html:308` |
| 38 | **Per-server catalog metrics** | Sees channels/VOD/series/EPG totals per server | `computeCatalogMetrics()` in `core/CatalogUtils.js`; pills rendered by `buildPerServerMetricsPillsHtml()` | `core/CatalogUtils.js:60`, `app-legacy.js:5383` |
| 39 | **Background sync frequency** | Chooses startup/daily/weekly/manual | `normalizeBgSyncFrequency()` + legacy aliases map | `core/CatalogUtils.js:6-58`, `app-legacy.js:642` |
| 40 | **Sync all servers in background** | Refreshes every configured server | `syncAllConfiguredServersInBackground()`, `syncSingleServerInBackground()` | `app-legacy.js:6772`, `:6700` |
| 41 | **Staged loading (carregamento em etapas)** | Controls live/movie/series load order | `#modalStagedLoading`; `getStagedLoadingPrefs()` | `app-legacy.js:689`, `:6629`; modal `index.html:564` |
| 42 | **Content filter (load live/movies/series)** | Chooses what to download | `mk21_filter_load_live` / `_movies` / `_series` | `app-legacy.js:5609` |
| 43 | **Hidden categories** | Hides unwanted groups | `mk21_hidden_categories`; `renderCategoriesManagerPanel()` | `app-legacy.js:6391` |
| 44 | **Sort / classification picker** | Sorts the catalog | `#modalSortPicker`; `openSortPickerModal()`; `persistSortOrder()` | `app-legacy.js:2484`, `:850`; modal `index.html:521` |
| 45 | **Stream format switch (MPEG-TS ↔ HLS)** | Changes the live transport | `saveStreamFormat(fmt)`; `mk21_stream_format` | `app-legacy.js:5682` |
| 46 | **Speed test** | Measures the connection | `#cfgBtnSpeedTest`; `runSpeedTest()` with parallel download workers, window/steady Mbps, upload workers | `app-legacy.js:5817`, `:6002`, `:6101` |
| 47 | **OTA app update** | Updates the TV app in place | `#modalAppUpdate`; `openAppUpdateModal()`, `startDirectUpdate()`, `compareSemver()`; downloads only `app.js` + `styles.css` | `app-legacy.js:7200`, `:7297`, `:7177`; modal `index.html:467` |
| 48 | **Developer log console + export** | Exports a diagnostic log | `#modalDevLogs`; `mk21RenderAllDevLogs()`, `mk21ExportDevLogs` | `app-legacy.js:3963`, `:3898`; modal `index.html:657` |
| 49 | **Stream diagnostics modal** | Inspects the live stream | `#modalStreamDiagnostics`; `toggleStreamDiagnostics()` | `app-legacy.js:3163`; modal `index.html:680` |
| 50 | **Font size setting** | Scales the UI | `mk21_font_size`; `applyFontSize(size)` | `app-legacy.js:6162`, `:6198` |
| 51 | **Time settings / timezone** | Aligns the clock and EPG | `mk21_timezone`; `renderTimeSettingsPanel()` | `app-legacy.js:6345` |
| 52 | **Toast display mode + on-screen notices** | Controls notification verbosity | `mk21_toast_mode` / `mk21_show_toasts`; `showChannelBanner()` | `app-legacy.js:4562`, `:4589` |
| 53 | **TMDB metadata toggle** | Optionally enriches titles | `#cfgToggleTmdb`; `fetchTmdbMetadata()`, `applyTmdbData()` | `app-legacy.js:2967`, `:2988` |
| 54 | **Player Factory (tri-state engine flag)** | Switches the playback engine | `localStorage.FBG2_PLAYER_LEGACY` → `FBG2_PLAYER_ENGINE` → remote `playerEngine` → default | `src/player-service.js:16-115` |
| 55 | **Atomic stream teardown** | (implicit) no audio leak between channels | `stopCurrentPlayer(abortPlayRequests)`, `safeVideoPlay()`, `mk21SeekBy()` | `app-legacy.js:4025`, `:4006`, `:4088` |
| 56 | **Adjacent-channel prefetch** | Faster zapping | `prefetchAdjacentChannels()`, `prefetchNextChannelStream()` | `app-legacy.js:3012`, `:3064` |
| 57 | **VOD progress tracking** | Resumes movies | `startProgressTracker()`, `saveProgress(contentId,…)`, `updateVodProgressTimeline()` | `app-legacy.js:3093`, `:3104`, `:4510` |
| 58 | **Memory shield cleanup** | Prevents OOM on old TVs | `runMemoryShieldCleanup()` | `app-legacy.js:3271` |
| 59 | **Non-blocking M3U parsing via Web Worker** | Large playlists don't freeze the TV | Batch of 500 items, progress messages | `workers/playlistWorker.js` |
| 60 | **Memory-integrity player overlay (XSS-safe)** | (implicit) safe text rendering | `textContent` only, `innerHTML` removed | `components/PlayerOverlay.js` |

### B.3 Android — 48 features

| # | Feature | What the user does | Implementation notes | Key files |
|---|---|---|---|---|
| 1 | **Startup gate + trial screen** | App shows branding then the license/trial gate | `StartupGateScreen()`, `TrialExpiredScreen(viewModel)` | `ui/Screens.kt:586`, `:207` |
| 2 | **Server configuration screen** | Adds / imports / selects servers | `ServerConfigScreen(viewModel, onNavigateToHome)`; predefined 7 + manual lists | `ui/Screens.kt:608` |
| 3 | **Predefined 7 servers** | Picks a bundled server | Hardcoded `staticDefaultServers` in the ViewModel (same set as `servers.json`) | `viewmodel/AppViewModel.kt:73-81` |
| 4 | **Dynamic server fetch** | Server list refreshes from the network | `fetchDynamicServers()` from `dynamic_servers_url` (default raw GitHub `servers.json`); `mergeWithDefaults()` keeps defaults and drops retired hosts | `AppViewModel.kt:926`, `:567` |
| 5 | **Plain-text server import** | Pastes a list of server URLs | `parseServersFromPlainText()` / `importServersFromPlainText()` — extracts base URL from `get.php`/`player_api.php` lines and infers a name | `AppViewModel.kt:614`, `:730` |
| 6 | **Per-server or global credentials** | Chooses one login set for all | `useSameCredentialsForServers` flag | `data/service/PreferencesService.kt:98` |
| 7 | **Manual M3U playlist CRUD** | Adds/edits/deletes a custom list | `ManualPlaylist` entity + DAO + `addManualPlaylist`/`updateManualPlaylist`/`deleteManualPlaylist` | `model/Models.kt:19`, `AppViewModel.kt:1488-1524` |
| 8 | **M3U download & parse (Okio streaming)** | Catalog loads without OOM | `M3UParser.parse()` over `InputStream.source().buffer()`; progress every 400 items; BOM stripping; `#EXTGRP` support | `data/parser/M3UParser.kt:20` |
| 9 | **Content-type heuristics** | (implicit) live/movie/series classification | URL patterns `/LIVE/`, `/MOVIE/`, `/SERIES/`; live-category list; VOD extensions; series regex + PT-BR keywords | `M3UParser.kt:272-380` |
| 10 | **Adult-content detection** | (implicit) 18+ items flagged | Keyword list (`18+`, `ADULTO`, `XXX`, `PLAYBOY`, `HUSTLER`, `FORA DA LEI`…) | `M3UParser.kt:382` |
| 11 | **Stream-URL sanitization** | (implicit) blocks `file://`/`javascript:` | `sanitizeStreamUrl()` allows only http/https/rtmp/rtsp/mms | `M3UParser.kt:142` |
| 12 | **Alternate-source merging** | One channel, several URLs | `mergeAlternateStreams()` groups by `tvg-id` (or name+category+type) into `alternateUrls` | `M3UParser.kt:101`; Room column `alternateUrls` + `PlaylistItemConverters` |
| 13 | **Multi-pattern playlist URL fallback** | (implicit) works with different panel versions | `get.php` plain → `type=m3uplus` → `type=m3u_plus`, `output=mpegts|hls` per preference | `AppViewModel.kt:1706` |
| 14 | **Content-type server failure diagnosis** | User sees a precise error | HTML portal, JSON error, invalid credentials, expired account, connection limit | `M3UParser.diagnoseContent()` `:391` |
| 15 | **Room-backed catalog** | (implicit) instant offline catalog | `playlist_items` with indices on `(playlistSource, contentType)`, `(playlistSource, category, contentType)`, `(playlistSource, isFavorite)`; chunked inserts of 500 | `data/database/IptvDatabase.kt` |
| 16 | **Favorites** | Hearts a channel/movie | `isFavorite` column + `getFavorites()` + `setFavorite()`; `toggleFavorite()` in the ViewModel | `IptvDatabase.kt:30`, `:76`; `AppViewModel.kt:1297` |
| 17 | **Continue Watching** | Re-opens recent VOD | `lastWatchedTime` column + `getContinueWatching(source, type)` | `IptvDatabase.kt:51`; `AppViewModel.kt:525` |
| 18 | **Per-type history clearing** | Clears only movies / series / live history | `clearMoviesHistory`, `clearSeriesHistory`, `clearLiveHistory`, `clearMoviesAndSeriesHistory` | `IptvDatabase.kt:82-92` |
| 19 | **Search** | Searches the catalog | `searchItems(source, "%query%")` + live `searchQuery` StateFlow | `IptvDatabase.kt:27`; `AppViewModel.kt:358-386` |
| 20 | **Category filtering** | Filters by group per type | `getCategoriesByType()`, `getItemsByCategoryAndType()` | `IptvDatabase.kt:21`, `:24` |
| 21 | **Sorting** | Sorts the list | `SortOrder` enum: A-Z, Z-A, addition, number, rating | `AppViewModel.kt:310-325`, `:393` |
| 22 | **Parental control — adult PIN** | Unlocks adult content with a PIN | `adultPin` (default `0000`), `AdultPinDialog`, `adultPinGranted`, `checkPinAndPlay()`, category-level PIN prompt | `PreferencesService.kt:94`, `ui/Screens.kt:4140`, `AppViewModel.kt:1257` |
| 23 | **Trial / license gating** | Sees remaining trial days or activates | 5-day trial from `trial_start_date`; `activateLicense(code)` → `POST /api/license/activate`; `refreshLicenseIfNeeded()` every 30 days | `PreferencesService.kt:232`, `AppViewModel.kt:125`, `:188` |
| 24 | **ECDSA P-256 offline license verification** | (implicit) license works offline | `LicenseTokenVerifier.verify()` against `BuildConfig.LICENSE_PUBKEY_B64`; token format `MKL1.<b64url payload>.<b64url sig>` | `com/fbg/streaming/security/LicenseTokenVerifier.kt` |
| 25 | **Home screen (grid + carousel)** | Browses the catalog | `HomeScreen(...)`; `HighlightBanner(...)` (random highlights, `getRandomHighlights`); `GroupedSeriesCard(...)` | `ui/Screens.kt:1725`, `:2834`, `:3034` |
| 26 | **Series grouping + episode list** | Browses seasons/episodes | `GroupedSeries` model; `EpisodesList`; season selector with episode counts | `ui/Screens.kt:3034`, `:929` region |
| 27 | **Full-screen player UI** | Watches content | `VideoPlayerUI(...)` — source picker, aspect-ratio, sleep timer, speed, PiP, subtitles, brightness, lock-screen, rotate | `ui/Screens.kt:4244` |
| 28 | **Multi-source / alternate-server switching** | Picks a different server for the same channel | `SaimoSourcesDialog`; `resolveRealChannelUrlOnServer()`; `buildServerStreamUrl()` rebuilds the URL preserving the Xtream path/port; `cleanChannelName()` normalizes names for cross-server matching | `ui/SaimoPlayerDialogs.kt:34`, `AppViewModel.kt:1836`, `:1750`, `:1788` |
| 29 | **Super-resolution dialog** | Enables an upscale filter | `SaimoSuperResolutionDialog`; pref `saimo_super_resolution` | `ui/SaimoPlayerDialogs.kt:150`, `PreferencesService.kt:284` |
| 30 | **Picture-in-Picture with media actions** | Plays in a mini window with Prev/Play-Pause/Next | `PictureInPictureParams` + `RemoteAction`s + `BroadcastReceiver` for `ACTION_PLAY_PAUSE`/`NEXT`/`PREVIOUS`; auto-enter on `onUserLeaveHint` | `MainActivity.kt:19-188` |
| 31 | **Sleep timer** | Pauses after N minutes | `yt-sleep-*` equivalents in Compose | `ui/Screens.kt` player region |
| 32 | **Playback speed** | Changes rate | `currentSpeed` label | `ui/Screens.kt` player region |
| 33 | **Audio & subtitle selection** | Switches tracks | "Áudio & Legendas" sheet; `subtitleConfig` preference | `ui/Screens.kt` player region |
| 34 | **Aspect ratio / zoom / rotate** | Adjusts the picture | "Alternar Modo de Proporção", "Esticar", "Zoom (Cortar)", "Girar Tela" | `ui/Screens.kt` player region |
| 35 | **Screen lock while playing** | Prevents accidental taps | "Bloquear Toques na Tela" | `ui/Screens.kt` player region |
| 36 | **Brightness control** | Adjusts screen brightness | Brightness indicator | `ui/Screens.kt` player region |
| 37 | **DLNA discovery & casting** | Sends playback to a DLNA TV | `DlnaDiscoveryService` (SSDP M-SEARCH), `DlnaSoapClient` (SetAVTransportURI/Play/Pause/Seek/Stop) | `data/service/DlnaDiscoveryService.kt`, `DlnaSoapClient.kt` |
| 38 | **Local cast HTTP server** | TV pulls the stream from the phone | `LocalHttpServer` on the LAN with a session token; remote play/pause/seek/volume control channels | `data/service/LocalHttpServer.kt` (504 lines) |
| 39 | **Smart TV cast dialog** | Picks a TV from the phone | `SmartTvCastDialog(...)` | `ui/Screens.kt:6071` |
| 40 | **External player integration** | Opens VLC/MX Player/Just Player/Kodi | `ExternalPlayerHelper` with package map, install detection, chooser fallback | `data/utils/ExternalPlayerHelper.kt` |
| 41 | **MK21 AI Copilot (Gemini)** | Asks "onde assistir Palmeiras x Santos?" and gets channel recommendations | Firebase AI Logic `gemini-2.5-flash`, streaming `Flow<String>`, 60 s timeout, local catalog synthesis fallback; `getRelevantItemsForCopilot()` routes sports intents to `getLiveSportsChannelsGlobal()` | `ai/GeminiAiService.kt`, `ai/GeminiAiViewModel.kt`, `ui/GeminiCopilotDialog.kt` |
| 42 | **Settings screen (large)** | Configures everything | `SettingsScreen(viewModel, onNavigateBack)` — license, servers, live categories, layout, font, language (PT/EN/ES), time format, stream format, subtitles, external player, AMOLED, cache, logs, backup/restore, auto-update | `ui/Screens.kt:7001` |
| 43 | **Backup export / import (JSON)** | Backs up the catalog+settings | `exportBackupJson()` / `restoreBackupJson()` with credential redaction regexes | `AppViewModel.kt:1895`, `:1953` |
| 44 | **Auto-update with signature pinning** | App updates itself | `checkForUpdates()` → `Mk21UpdateManager` (fail-closed SHA-256, HTTPS-only GitHub hosts, ≤5 redirects, `apksigner` verification via `ApkSignatureVerifier`); installs via FileProvider | `com/fbg/streaming/update/Mk21UpdateManager.kt`, `AppViewModel.kt:2142`, `:2187` |
| 45 | **Crash logger & diagnostics** | Exports a crash report | `CrashLogger.init()` writes to app files; settings shows and copies the log | `utils/CrashLogger.kt` |
| 46 | **Local secure proxy station** | (implicit) proxies streams through a token-protected local server | `Mk21ProxyStation` — random session token, `MessageDigest.isEqual` comparison, HLS manifest rewriting + segment prefetch, TS byte sanitizing | `com/fbg/streaming/network/proxy/Mk21ProxyStation.kt` |
| 47 | **DNS-over-HTTPS resolver** | (implicit) resolves hosts safely | `okhttp-dnsoverhttps` + `DnsResolver` | `network/DnsResolver.kt` |
| 48 | **Encrypted preferences with migration** | (implicit) credentials stored encrypted | `EncryptedSharedPreferences` (AES256-SIV keys / AES256-GCM values) + automatic migration from the legacy plain `mk21_pref_store` | `PreferencesService.kt:17-66` |

---

## C. Data model & persistence

### C.1 Content sources

| Source | Where | Details |
|---|---|---|
| **Xtream Codes API** | Web, TV | `player_api.php` with `action=` in `{get_live_categories, get_live_streams, get_vod_streams, get_series, get_short_epg}` |
| **M3U / M3U_plus** | Web, TV, Android | `get.php?username=…&password=…&type=m3u_plus&output=ts` (+ `m3uplus` variant on Android) |
| **XMLTV EPG** | Web | `xmltv.php?username=…&password=…` (or `url-tvg=` from the M3U header) |
| **Short EPG** | Web, Android | `player_api.php?...&action=get_short_epg&stream_id=<id>&limit=12` (10 on Android) |
| **Local M3U file** | Web, TV (offline cache) | Browser `FileReader`; TV reads its own IndexedDB cache |
| **Server catalog** | All | `servers.json` (7 servers) |

### C.2 Stream URL shapes

```
LIVE   {base}/live/{user}/{pass}/{stream_id}.ts
MOVIE  {base}/movie/{user}/{pass}/{stream_id}.{container_extension|mp4}
SERIES {base}/series/{user}/{pass}/{series_id}
```

### C.3 Item field sets

**Web** (`index.html:4396-4448`, `parseM3UProgressively`):
`name`, `category`, `logo`, `url`, `streamId`, `type` (`LIVE|MOVIE|SERIES`), `streamType`, plus `tvgId`, `tvgName` (M3U) / `rating`, `year` (Xtream).

**TV** (`app-legacy.js`): `name`, `url`, `logo`, `group`, `contentType`, `tvgId`, `tvg-name`, `alternateUrls`, `current_program`, `currentTime`.

**Android** (`data/model/PlaylistItem.kt`):
`id`, `name`, `url`, `logoUrl`, `category`, `contentType`, `isAdult`, `playlistSource`, `lastWatchedTime`, `isFavorite`, `alternateUrls` (JSON-converted).

**Server profile** (`data/model/ServerProfile.kt`): `id`, `name`, `baseUrl`, `username?`, `password?`, `isActive`.
**servers.json** entry: `id`, `name`, `baseUrl`, `lotusCode`, `webCode`.

### C.4 Persistence locations

**Web — localStorage**
| Key | Purpose |
|---|---|
| `MK21_CONFIG` | JSON blob: `last_channel_id`, `volume_level`, `favorites_list`, `upscaling_active`, `zapping_mode` (`zero-delay`\|`instant`\|`smooth`) |
| `mk21_baseUrl`, `mk21_server_name`, `mk21_server_code` | Active server |
| `mk21_username`, `mk21_password`, `mk21_remember` | Credentials (opt-in) |
| `mk21_cors` | Proxy mode (`auto`\|`vercel`\|`direct`\|`corsproxy`\|`allorigins`\|`codetabs`\|`thingproxy`) |
| `mk21_favorites` | Favorites array |
| `mk21_continue` | Continue-watching array (max 12) |
| `mk21_saved_profiles` | Quick login profiles |
| `mk21_player_volume` | Player volume |
| `mk21_auto_external` | Auto external player |
| `mk21_high_contrast` | High-contrast mode |
| `mk21_vod_resume_progress` | VOD resume map (`url → {currentTime,duration,name,updatedAt}`) — written by `js/tv-remote-and-pwa.js:104` |

**Web — sessionStorage**: `mk21_epg_<key>` (`id_*`, `tvgid_*`, `tvgname_*`, `name_*`) — max 15 programmes per key.

**Web — IndexedDB**: DB `mk21_playlist_cache`, store `playlists`, key `playlist_<encodeURIComponent(baseUrl)>_<encodeURIComponent(user)>`, value `{key, lives[], movies[], series[], timestamp}`; TTL 7 days.

**Web — Cache Storage**: `mk21-cache-v2.9.0` (`sw.js`).

**Android**
- Room `mk21_iptv_db` → `playlist_items`, `manual_playlists`; `MIGRATION_1_2` adds `alternateUrls`.
- `EncryptedSharedPreferences` file `mk21_secure_pref_store` (fallback `mk21_secure_pref_store_fallback`): `username`, `password`, `active_server_id`, `active_playlist_name`, `adult_pin`, `last_list_update_<playlist>`, `use_same_credentials`, `use_amoled_mode`, `hide_live_categories`, `use_external_player`, `external_player_type`, `device_type`, `app_language`, `time_format`, `app_layout`, `live_stream_format`, `subtitle_config`, `menu_sort_order`, `trial_start_date`, `activation_key`, `license_token`, `cached_servers_json`, `dynamic_servers_url`, `load_live_foreground`, `load_movies_foreground`, `load_series_foreground`, `sync_interval_frequency`, `sync_all_lists_background`, `hide_background_progress`, `saimo_super_resolution`.
- Device identity: `ANDROID_ID` → hex → 12-char → `virtualMac` (`AA:BB:CC:DD:EE:FF`).

**Smart TV — localStorage**
`iptv_user`, `iptv_pass`, `mk21_user`, `mk21_pass`, `mk21_username`, `mk21_password`, `mk21_credentials`, `mk21_auth_token`, `mk21_servers_list`, `mk21_last_server`, `mk21_last_channel`, `mk21_continue_watching`, `mk21_favs`, `mk21_adult_pin`, `mk21_hidden_categories`, `mk21_filter_load_live|_movies|_series`, `mk21_sort_order`, `mk21_buffer_mode`, `mk21_stream_format`, `mk21_playback_quality`, `mk21_autosync`, `mk21_last_sync_<server>`, `mk21_fast_cache_<id>`, `mk21_fast_cache_time_<id>`, `mk21_fast_cache_universal`, `mk21_toast_mode`, `mk21_show_toasts`, `mk21_font_size`, `mk21_timezone`, `mk21_use_tmdb`, `mk21_speed_test_endpoint`, `mk21_ota_app_js`, `mk21_ota_app_version`, `mk21_ota_styles_css`, `FBG2_PLAYER_LEGACY`, `FBG2_PLAYER_ENGINE`.

**Smart TV — IndexedDB**: fast cache + catalog index (`createCatalogIndexWriter`, `queryCatalogIndexPage`, `searchCatalogIndex`, `queryIndexedFavorites`).

### C.5 API surface (`api/`)

**`api/proxy.js`** — Vercel serverless. Route: `GET|HEAD /api/proxy?url=<encoded>`, plus `OPTIONS` preflight.

| Aspect | Behaviour |
|---|---|
| Origin allowlist | `bgstreaming*.vercel.app`, `localhost`, `127.0.0.1`, `capacitor://localhost`, plus `PROXY_ALLOWED_ORIGINS` and same-host HTTPS (`x-forwarded-host`/`host`) |
| Cross-site without Origin | Rejected when `sec-fetch-site: cross-site` (403) |
| Methods | `GET`, `HEAD`, `OPTIONS` only (405 otherwise) |
| Rate limit | 3000 req/min/IP in memory, keyed on `x-real-ip` → socket → `x-forwarded-for` (429) |
| Anti-SSRF | `isPrivateHost()` blocks `localhost`, `*.local`, `*.internal`, `*.arpa`, non-canonical numeric hosts (decimal/hex/octal/leading-zero); `isPrivateIpv4()` blocks RFC1918, CGNAT, loopback, link-local, IETF/TEST-NET, benchmark, multicast+; `isPrivateIpv6()` blocks `::`, `::1`, ULA `fc00::/7`, link-local, site-local, multicast, IPv4-mapped/compatible, 6to4 `2002::/16`, NAT64 `64:ff9b::/96` |
| DNS-rebinding pin | `resolveAndValidateHost()` resolves **all** A/AAAA records and rejects if any is private; `buildPinnedLookup()` pins the socket to the validated IP |
| Redirects | Followed manually, max 6, each hop re-validated (protocol + DNS) |
| Protocols | `http:`/`https:` only |
| Timeouts | 25 s upstream (`proxyReq.setTimeout`) → 504 |
| Streaming | Pipes the upstream body (`proxyRes.pipe(res)`) — chunked, no buffering |
| Headers forwarded | `Range`, `If-Range` (CR/LF stripped); UA spoofed to Chrome 115 |
| Cache policy | `no-store` for account-bearing/playlist URLs; `public, max-age=86400` for static VOD (`mp4/mkv/avi/mov` or `/movie/`/`/series/`); `no-cache` otherwise |
| XSS neutralization | Upstream `text/html`, `application/xhtml+xml`, `image/svg+xml` are rewritten to `text/plain; charset=utf-8` + `Content-Security-Policy: sandbox` + `Content-Disposition: attachment` |
| Exported for tests | `createProxyHandler`, `isAllowedOrigin`, `isPrivateIp`, `isPrivateHost`, `resolveAndValidateHost` |

**`api/license/activate.js`** — `POST {code, deviceId}` → validates against Neon `orders` (expiry, seats), blocks revoked devices, upserts `licenses`, returns `{token, tier, expiresAt}` where `token = MKL1.<base64url(payload)>.<base64url(ECDSA-SHA256 sig)>`. Errors: 400 (fields/JSON), 404 (unknown code), 403 (expired/revoked), 409 (seats exhausted), 413 (body > 16 KB), 500.
**`api/license/refresh.js`** — `POST {deviceId}` → checks the `licenses` row (not revoked, not expired), bumps `last_seen`, re-signs with the stored expiry, returns the same shape.
**`api/license/_token.js`** — shared helpers: `buildPayload`, `signToken`, `readJsonBody` (16 KB cap), `sendJson` (no-store + nosniff).

---

## D. Build & test contracts (must keep passing)

### D.1 Commands

```bash
# Web mirror sync (root → web/ → public/)
npm run sync:web          # node scripts/sync-web.mjs
npm run check:web         # node scripts/sync-web.mjs --check   (CI gate)

# TV bundle
npm run build:tv          # node esbuild.config.mjs  → smart-tv/mk21-tv/app.js (< 350 KB)

# TypeScript player layer
npm run typecheck         # tsc --noEmit (strict)
npm test                  # jest src/players

# TV package
python3 smart-tv/package_ipk.py

# Release manifests
npm run verify:release    # node scripts/verify-release-manifests.mjs

# Node / Python test suites
node --check smart-tv/mk21-tv/app.js
node --check api/proxy.js && node --check web/api/proxy.js
node --test smart-tv/tests/*.test.js
python3 -m unittest discover -s smart-tv/tests -v

# Android
./gradlew testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
```

### D.2 `sync-web.mjs` mirroring rules

- Canonical list = `SINGLE_FILES` (14 entries) + every file under `css/` and `js/`.
- Mirrors = `web/` and `public/`.
- Comparison = SHA-256 of file contents.
- `--check` mode: exits 1 and prints each `Ausente no espelho:` / `Divergente do arquivo canônico:` line.
- `public/assets/aistudio/` is never written or deleted.
- Currently **in sync**: `[sync-web] OK: 20 arquivos sincronizados entre raiz, web/ e public/`.

### D.3 CI — `.github/workflows/validate.yml` (runs on push/PR to `main`)

| Step | Command |
|---|---|
| TS + Jest | `npm ci --no-audit --no-fund && npm run typecheck && npm test` |
| Remote manifest verification | `node scripts/verify-release-manifests.mjs --apk mk21.apk --ipk smart-tv/mk21play_<ver>_all.ipk --remote` (skipped for fork PRs without network) |
| Keystore prep | Validates `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS` must be `upload`, `LICENSE_SECRET` ≥ 16 chars, `LICENSE_PUBKEY_B64` non-empty; derives `EXPECTED_CERT_SHA256` |
| Android (PR) | `./gradlew testDebugUnitTest --no-daemon` (with one retry) then `./gradlew assembleDebug --no-daemon` |
| Android (main) | `./gradlew assembleRelease --no-daemon` when signed, else `assembleDebug` |
| Package + stamp | `npm run build:tv` → `python3 smart-tv/package_ipk.py` → `node scripts/sync-web.mjs`; verifies the APK is **not** debug-signed when secrets exist (`apksigner verify --print-certs`) |
| Inline Python check | Asserts `apkSha256`/`sizeBytes`/`ipkSha256`/`ipkSizeBytes` match the real binaries across the 8 version manifests (excluding `smart-tv/mk21-tv/version.json`, which cannot contain its own IPK hash) |
| Smart TV + version sync | `node scripts/sync-web.mjs --check`; `node --check smart-tv/mk21-tv/app.js`; `node --check api/proxy.js`; `node --check web/api/proxy.js`; `node --test smart-tv/tests/proxy-security.test.js smart-tv/tests/alternative-sources.test.js smart-tv/tests/catalog-utils.test.js smart-tv/tests/release-manifest.test.js`; `node scripts/verify-release-manifests.mjs --apk … --ipk …`; `python3 -m py_compile smart-tv/package_ipk.py`; `python3 -m unittest discover -s smart-tv/tests -v` |
| Version coherence | 8 `version.json` mirrors must agree on `version`/`versionCode`/`versionName`/`apkSha256`; `manifest.json`, `web/manifest.json`, `public/manifest.json`, `smart-tv/mk21-tv/appinfo.json`, `smart-tv/webos/appinfo.json` must agree on `version`; `smart-tv/tizen/config.xml` `version` attribute; `app/build.gradle.kts` `versionCode`/`versionName` |
| IPK internal version | `dpkg-deb --field "$IPK" Version` must equal `TV_VER` |
| Checksums | `sha256sum` over `mk21.apk`, the IPK and the 8 manifests → `SHA256SUMS` |
| Artifact upload | `generated-apps` artifact with `if-no-files-found: error` |

### D.4 Release-manifest invariants (`scripts/verify-release-manifests.mjs`)

- `version.json.version`/`versionCode` must equal `app/build.gradle.kts` `versionName`/`versionCode`.
- All 8 version mirrors must agree on version, code and `apkSha256`.
- `apkSha256` and `sizeBytes` must match the actual `mk21.apk` (fail-closed).
- `ipkSha256`/`ipkSizeBytes` must match the actual IPK when `--ipk` is given.
- `apps.json`, `smart-tv/apps.json`, `web/apps.json`, `public/apps.json` must all declare the same version, equal `sourceUrl`/`manifest.ipkUrl`, a 64-hex `manifest.ipkHash.sha256` equal to the real IPK, and (with `--require-release-url`) a GitHub **Release** URL rather than the raw `main` URL.
- `--remote` mode downloads and re-hashes the published assets; a 404 on a release URL prints the exact `git tag v… && git push origin v…` command.
- `parseGradleVersions()` and `verifyAppsManifests()` are exported for unit tests.

### D.5 Test inventory

| Suite | Count | Coverage |
|---|---|---|
| `smart-tv/tests/alternative-sources.test.js` | 6 | tvg-id grouping, VOD separation, name/category fallback, URL dedup, cross-server matching, negative cases |
| `smart-tv/tests/catalog-utils.test.js` | 4 | catalog metrics, safe JSON parsing (HTML/401/403), friendly error text, sync-frequency normalization + legacy aliases |
| `smart-tv/tests/license-api.test.js` | 4+ | ECDSA token round-trip (escaped PEM), activate validation matrix, seat/reinstall rules, refresh revocation |
| `smart-tv/tests/proxy-security.test.js` | 5 | private-host blocking (v4/v6/mapped/decimal/hex), DNS-rebinding, CORS allowlist, 403 without leak, XSS neutralization |
| `smart-tv/tests/release-manifest.test.js` | 9 | manifest coherence, hash mismatch, versionCode divergence, 404 tag hint, HEAD→Range fallback, apps.json hash/version, release-URL enforcement |
| `smart-tv/tests/test_package_ipk.py` | (unittest) | IPK structure, reproducible builds, appinfo validation |
| `src/players/__tests__/*.test.ts` | 3 files | `PlayerFactory`, `TizenAVPlayPlayer`, `HLSFallbackPlayer` |
| `app/src/test/**` | 8 files | `IptvRepositoryTest`, `ServerTextParserTest`, `LicenseTokenVerifierTest`, `LocalServerSecurityTest`, `Mk21UpdateSecurityTest`, Robolectric + Roborazzi screenshot tests |

---

## E. Gaps vs. a "full-featured IPTV app" (factual observations)

**Platform coverage of capabilities**

| Capability | Web | Smart TV | Android |
|---|---|---|---|
| Multi-server | ✅ 7 predefined + `servers.json` sync + manual URL | ✅ full manager (add/edit/delete) | ✅ predefined + dynamic fetch + plain-text import |
| Xtream API catalog | ✅ | ✅ | ⚠️ M3U only (`get.php`), no `player_api.php` catalog |
| EPG | ✅ real short EPG + **full XMLTV guide** + simulated fallback | ✅ real XMLTV match + guide modal + simulated fallback | ⚠️ short EPG **preview dialog only**; no full guide screen, no XMLTV |
| Favorites | ✅ | ✅ | ✅ |
| Continue Watching / VOD resume | ✅ (both list and per-second resume) | ✅ | ✅ |
| Search | ✅ (in-catalog) | ✅ Omnisearch (cross-server) | ✅ |
| Parental control / adult PIN | ❌ **absent** | ✅ PIN modal + adult category lock | ✅ PIN + category-level prompt |
| License / activation flow | ❌ **absent** | ❌ **absent** | ✅ ECDSA token activation + refresh + offline verify |
| AI assistant | ❌ **absent** | ❌ **absent** | ✅ Gemini Copilot |
| Server-side EPG proxy | ✅ via `/api/proxy` | ✅ own proxy | ✅ `Mk21ProxyStation` + `LocalHttpServer` |
| TMDB metadata | ❌ | ✅ optional toggle | ❌ |
| Sleep timer / speed / audio boost | ✅ all three | ⚠️ speed only | ⚠️ speed + sleep timer (no Web-Audio boost equivalent) |
| Multi-source / failover | ⚠️ proxy rotation only, no per-channel alternate URLs | ✅ `alternateUrls` + cross-server failover | ✅ `alternateUrls` + per-server source dialog |
| OTA update | ⚠️ PWA install only | ✅ in-app OTA (`app.js`/`styles.css`) | ✅ signed APK OTA with cert pin |

**Concrete gaps**

1. **Web has no parental control.** No PIN, no adult-category lock — the Android PIN heuristic (`M3UParser.isAdultContent`) and the TV's `mk21_adult_pin` have no web equivalent. The web also has no adult-content detection at all.
2. **Web and TV have no licensing/activation path.** `api/license/*` exists and is tested, but only the Android client calls it (`AppViewModel.activateLicense`). The web settings modal's "account" tab shows **hardcoded** placeholder values (`601334065`, `15/03/2027` — `index.html:1460`, `:1476`), not live server data.
3. **Android has no full EPG guide.** It renders a per-channel short-EPG preview dialog (`ChannelEpgPreviewDialog`) but has no XMLTV grid/guide screen and no XMLTV download.
4. **Android does not use the Xtream JSON catalog.** It downloads M3U via `get.php` only, so per-series episode metadata (`get_series` details), ratings and container extensions are approximated by filename heuristics rather than the API.
5. **Series grouping is heuristic on every platform.** Web (`groupSeriesItems`) and Android group by parsing the title; no platform consumes `player_api.php?action=get_series` episode lists. Web does call `get_series` for the card list, but episodes still come from the M3U.
6. **No cloud sync.** Favorites, continue-watching, progress and preferences are strictly local per platform (`localStorage` / `sessionStorage` / IndexedDB / EncryptedSharedPreferences / TV `localStorage`). Moving from phone → TV → web loses all state.
7. **Multi-user / profile switching is minimal.** Each platform stores a single credential set (the web additionally keeps a flat `mk21_saved_profiles` list of quick logins). There is no account/profile abstraction, no per-user favorites, no "who's watching".
8. **No catch-up / TVoArchive / recording.** No archive playback window, no timeshift, no DVR/recording, no scheduled recording.
9. **No downloads / offline media on Android and TV.** Web can load a local M3U file, but nothing persists media for offline playback.
10. **No subtitles/audio-track manifest handling on Android beyond the player sheet.** No `.srt`/`.vtt` sidecar support, no forced-subtitle preference per channel.
11. **No channel-number mapping.** The web and TV index channels by list order; there is no editable channel number (LCN) assignment, and no EPG-based "jump to currently-airing program" feature.
12. **Diagnostics are developer-grade but not user-facing.** Web (`#diagnostic-log-console`), TV (`#modalDevLogs`) and Android (`CrashLogger`) exist, but there is no unified "report a problem" flow, and the web's remote-log path exposes only a copy/download of the console text.
13. **Web accessibility/performance/SEO modules are partially wired.** `js/accessibility.js` exposes `setupContrastChecker()` and `setupTextSizeAdjustment()` but `init()` never calls them; `js/performance.js` exposes `createVirtualScroller()` but the web grid uses a manual `itemsLimit` + `IntersectionObserver` "load more" instead. `js/seo-meta.js` sets a `data:`-URI manifest that shadows the real `manifest.json` only if `setupManifest()` is called — `init()` does not call it.
14. **TV `playerEngine` is published as `"legacy"`.** `version.json` ships `"playerEngine": "legacy"`, so the TypeScript Player Factory is off by default everywhere until the flag flips (see `src/players/README.md`).
15. **No remote-control "voice" on web/Android**, no picture-in-picture on TV (only web + Android have PiP).
16. **`apps.json` catalog covers only the webOS IPK.** There is no Tizen `.wgt` entry and no Android `.apk` entry in the store catalog — Tizen is packaging-only (`smart-tv/tizen/config.xml`).
17. **Web "Continue Watching" and TV "Continue Watching" are different implementations** of the same concept, with different storage keys and semantics (web caps at 12 items; TV stores per-`contentId` progress).

---

## F. Code conventions

### F.1 JavaScript (web + TV)

- **No build step for the web layer.** Root `index.html` is hand-authored; `js/*.js` are plain `<script>` files (not ES modules) that expose a single global each: `accessibilityManager` (`js/accessibility.js:294`), `performanceOptimizer` (`js/performance.js:206`), `seoManager` (`js/seo-meta.js:246`), and an IIFE that exposes `window.mk21Remote` (`js/tv-remote-and-pwa.js:794`).
- **Naming**: camelCase functions; snake_case for some legacy locals (`active_category`); PascalCase for object literals acting as namespaces (`EpgManager`, `MK21_CONFIG`); SCREAMING_SNAKE for constants (`CACHE_NAME`, `PROXY_ROTATION_LIST`, `MAX_REQUESTS_PER_MINUTE`).
- **Global exposure**: legacy globals are attached to `window` explicitly (`window.playStream`, `window.closeMobilePlayer`, `window.EpgManager`, `window.MK21_CONFIG`) so the inline HTML `onclick=` handlers can reach them. The TV uses the `mk21`-prefixed convention (`mk21Ensure…`, `mk21MoveFocus`, `mk21RenderAllDevLogs`).
- **DOM access**: `document.getElementById` in the web layer; a `$('id')` helper in the TV layer.
- **XSS discipline**: web escapes with `escapeHtml()` (`index.html:1640`) and validates image URLs with `sanitizeImageUrl()` (`:1646`); the TV uses `escapeHtml()` (`app-legacy.js:3825`) and the PlayerOverlay uses `textContent` only. `innerHTML` is used for template literals but always with escaped interpolation.
- **Comments**: dense Portuguese section banners (`// ==========================================`, `// 1. PWA SERVICE WORKER…`) and JSDoc in `js/` and `api/`; English is used in newer `src/players/` TypeScript and in some `api/license` files.
- **Error handling**: `try/catch` with `console.warn`/`console.error`; user-facing errors go through `showNotification(message, 'error')`.

### F.2 TypeScript (`src/players/`)

- Strict mode (`tsconfig.json`, `npm run typecheck` → `tsc --noEmit`).
- `IPlayer` interface is the contract every player implements (`src/players/types/player.ts:84`); normalized events: `ready | playing | paused | buffering | buffered | ended | error | tracksReady | timeupdate | durationchange | destroyed`.
- Time is in **milliseconds** at the factory boundary; the legacy TV UI speaks seconds — `player-service.js` converts (`seekMs`, `getCurrentTimeSec`).
- `BasePlayer` provides the event emitter + state + buffer accounting; `PlayerFactory.detectPlatform()` returns `tizen | webos | browser`.
- Logging is gated behind `FBG2_DEBUG` (`src/players/utils/logger.ts`).
- Tests colocated in `src/players/__tests__/*.test.ts`, run by Jest (`jest.config.js` → `preset: ts-jest`, `roots: ['<rootDir>/src/players']`, `testEnvironment: jsdom`).

### F.3 TV bundling (`esbuild.config.mjs`)

- Entry: `smart-tv/mk21-tv/src/main.js`; output: `smart-tv/mk21-tv/app.js`.
- `format: 'iife'`, `target: ['es2019']`, `minify: true`, `sourcemap: false`, `legalComments: 'none'`, `charset: 'utf8'`.
- `alias: { 'hls.js': 'smart-tv/mk21-tv/src/shims/hls-global.js' }` — keeps hls.js out of the bundle (the TV loads `libs/hls.min.js` at runtime).
- `define: { 'process.env.NODE_ENV': '"production"' }`.
- **Hard budget: 350 KB** — the build prints the size and `process.exit(1)` if exceeded. This is why `src/`, `tests/` and `node_modules/` are excluded from the IPK payload (`smart-tv/package_ipk.py:334-336`) and why the OTA ships only `app.js` + `styles.css`.
- The banner comment on `app.js` states: *"MK21 Play - app.js gerado por `npm run build:tv`. Nao editar: o fonte esta em smart-tv/mk21-tv/src/."* — **never hand-edit `app.js`**.
- Module order in the bundle matters: `player-service.js` body runs first and publishes `window.FBG2PlayerService`; then `legacy/app-legacy.js` initializes; then `main.js` schedules the remote flag refresh (1500 ms after boot).

### F.4 Kotlin (Android)

- Packages: `com.example.*` (app) and `com.fbg.streaming.*` (hardening modules) — two coexist by design.
- Data layer: `interface IptvRepository` + `IptvRepositoryImpl` wrapping Room DAOs; all reads return `Flow<List<…>>`; writes are `suspend`.
- State: `private val _x = MutableStateFlow(...)` + `val x: StateFlow<…> = _x.asStateFlow()`; one-off events use `MutableSharedFlow`.
- Compose: single-file UI layer `ui/Screens.kt` (9527 lines) with top-level `@Composable fun`s (`AppNavigation`, `ServerConfigScreen`, `HomeScreen`, `VideoPlayerUI`, `SettingsScreen`, `AdultPinDialog`, `ChannelEpgPreviewDialog`, `HighlightBanner`, `GroupedSeriesCard`, `GridItemCard`, `SmartTvCastDialog`); theme in `ui/theme/{Color,Theme,Type}.kt`.
- Resources: `strings.xml` for the app name; most UI strings are inline PT-BR literals (a known inconsistency).
- Security: `EncryptedSharedPreferences` only; secrets never logged; the settings screen redacts credentials in exported backups via regexes (`AppViewModel.kt:1895+`).
- Tests: JUnit4 + Robolectric + Roborazzi (screenshot) + `kotlinx-coroutines-test`; `testOptions { unitTests { isIncludeAndroidResources = true } }`.

### F.5 Python

- `smart-tv/package_ipk.py` — stdlib only (`gzip`, `tarfile`, `hashlib`, `struct`, `json`, `math`, `shutil`).
- Reproducible builds: fixed `DEFAULT_BUILD_TIMESTAMP = 1735689600` (2025-01-01), overridable via `SOURCE_DATE_EPOCH`; all tar members get normalized uid/gid 0, uname/gname `root`, and mode `0o644`/`0o755`.
- `validate_appinfo()` raises `ValueError` listing **all** problems before packaging (id format, semver, required fields, main file exists, exact PNG dimensions for `icon` 80×80 / `largeIcon` 130×130 / `splashBackground` 1920×1080).
- Manifest stamping helpers: `stamp_version_manifests()`, `stamp_apps_manifests()`, `stamp_apk_manifests()` — each skips manifests that declare a different version, so a stale manifest can never advertise the current build.
- GNU-AR assembly of `debian-binary` + `control.tar.gz` + `data.tar.gz`; the archive member timestamp must not be epoch 0 or webOS's pkgverifier fails with `-5: ipk verified failed`.

### F.6 JSON / manifests

- `version.json` is the release hub: `version`, `versionCode`, `tagName`, `platforms{web,android,webos,tizen}`, `releaseDate`, `releaseNotes`, `body`, `apkUrl`, `downloadUrl`, `ipkUrl`, `apkSha256`, `ipkSha256`, `sizeBytes`, `ipkSizeBytes`, `sha256`, `versionName`, `changelog`, `playerEngine`.
- `apps.json` (webOS store catalog format): `packages[0]` with `id`, `version`, `type`, `title`, `appDescription`, `category`, `icon`, `sourceUrl`, and a nested `manifest{id,version,type,title,icon,largeIcon,ipkUrl,ipkHash.sha256,vendor}`.
- `manifest.json` (PWA): `name`, `short_name`, `version`, `start_url`, `display: standalone`, `background_color`, `theme_color`, icons (192/512/SVG).
- `metadata.json` (Android TV / Google TV): `name`, `description`, `requestFramePermissions: []`, `majorCapabilities: ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]`.
- All JSON is 2-space indented; `servers.json` is an array of `{id, name, baseUrl, lotusCode, webCode}`.
