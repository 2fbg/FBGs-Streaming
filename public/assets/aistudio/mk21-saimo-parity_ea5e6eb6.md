diff --git a/.github/workflows/validate.yml b/.github/workflows/validate.yml
index 809b598..e5da49b 100644
--- a/.github/workflows/validate.yml
+++ b/.github/workflows/validate.yml
@@ -343,7 +343,7 @@ jobs:
           node --check smart-tv/mk21-tv/app.js
           node --check api/proxy.js
           node --check web/api/proxy.js
-          node --test smart-tv/tests/proxy-security.test.js smart-tv/tests/alternative-sources.test.js smart-tv/tests/catalog-utils.test.js smart-tv/tests/release-manifest.test.js
+          node --test smart-tv/tests/proxy-security.test.js smart-tv/tests/alternative-sources.test.js smart-tv/tests/catalog-utils.test.js smart-tv/tests/release-manifest.test.js smart-tv/tests/epg-guide.test.js smart-tv/tests/zap-hud.test.js smart-tv/tests/on-screen-keyboard.test.js smart-tv/tests/tv-guide-integration.test.js
           node scripts/verify-release-manifests.mjs --apk mk21.apk --ipk "smart-tv/mk21play_$(python3 -c "import json; print(json.load(open('version.json'))['version'])")_all.ipk"
           python3 -m py_compile smart-tv/package_ipk.py
           python3 -m unittest discover -s smart-tv/tests -v
diff --git a/README.md b/README.md
index c9c545e..1fc8d8a 100644
--- a/README.md
+++ b/README.md
@@ -41,6 +41,9 @@
 - **LG webOS:** Pacote reproduzível `.ipk` (`smart-tv/package_ipk.py`) para webOS 3.0+ a webOS 24.
 - **Samsung Tizen:** Manifesto em `smart-tv/tizen/config.xml` (`v2.8.0`) compatível com Tizen 3.0+.
 - **Bundle & Performance 10-foot UI:** Entrada em `smart-tv/mk21-tv/src/main.js` empacotada para `smart-tv/mk21-tv/app.js` (< 350 KB), navegação estrita via D-Pad, worker não-bloqueante e cache local.
+- **Guia de Programação real (XMLTV):** A TV baixa o `xmltv.php` do servidor (ou o `url-tvg=` da playlist), indexa a grade por canal e a exibe na aba **GUIA** (canais à esquerda com o que está *no ar*, grade do canal à direita) e no HUD de zapping (cartão com **NO AR** / **A SEGUIR** e contagem regressiva). Parser por regex (sem DOMParser) para respeitar a memória das TVs antigas; amarração por `tvg-id`/`epg_channel_id` e por `<display-name>` normalizado. Módulo puro em `smart-tv/mk21-tv/core/EpgGuide.js`.
+- **Teclado virtual (D-pad):** QWERTY PT-BR para a busca, navegável só pelo controle remoto, com modos `abc`/`ABC`/`123`/`#+=`. Módulo puro em `smart-tv/mk21-tv/core/OnScreenKeyboard.js`.
+- **Paridade com o Saimo TV:** o comparativo completo (o que já existe em cada plataforma e o que falta) está em [`docs/SAIMO-TV-PARITY.md`](docs/SAIMO-TV-PARITY.md).
 
 ### Web PWA (Fonte Única na Raiz -> `/web` e `/public`)
 - **Fonte Canônica:** Os arquivos na **raiz** (`index.html`, `sw.js`, `manifest.json`, `apps.json`, `servers.json`, `version.json`, `api/proxy.js`, `css/`, `js/`) são a única fonte de verdade.
diff --git a/docs/CHANGELOG.md b/docs/CHANGELOG.md
index 495a2d6..315092d 100644
--- a/docs/CHANGELOG.md
+++ b/docs/CHANGELOG.md
@@ -2,6 +2,16 @@
 
 Todas as alterações notáveis deste projeto serão documentadas neste arquivo.
 
+## [Não publicado] — Paridade com o Saimo TV (Smart TV)
+
+- **Guia de Programação na Smart TV (LG webOS / Tizen):** nova aba **GUIA** com duas colunas — canais à esquerda (número, logo, nome e o que está *no ar*) e a grade do canal selecionado à direita (faixa de horário, selo **NO AR**, sinopse e barra de progresso). Navegação 100 % por D-pad (`▲▼` coluna ativa, `◀▶` troca de coluna, `OK` abre/sintoniza, `VOLTAR` fecha).
+- **EPG real (XMLTV) na Smart TV:** a TV não tinha **nenhum** carregador de XMLTV — `matchEPG()` era código morto e `epgCache` nunca era preenchido; só existia o `get_short_epg` do canal em foco e uma grade simulada. Agora o `xmltv.php` do servidor (ou o `url-tvg=` da playlist) é baixado, indexado por canal e usado no Guia e no HUD de zapping. Parser por regex (sem DOMParser) para não estourar a memória das TVs antigas, com teto de 250 000 programas; amarração por `tvg-id`/`epg_channel_id`, por id do XMLTV e por `<display-name>` normalizado.
+- **HUD de zapping:** cartão no topo durante a troca de canal com número (LCN), logo, selo **NO AR**, barra de progresso, **A SEGUIR** e contagem regressiva que atualiza a cada segundo, com auto-hide em 7 s. Acionado por `CH +/-`, digitação de número e pelo Guia.
+- **Teclado virtual (D-pad):** QWERTY PT-BR (com `ç` e acentos) para a busca, com 4 modos (`abc`/`ABC`/`123`/`#+=`), linha de controle (modo, espaço, backspace, limpar), seleção que avança como um teclado de celular e trava nas bordas do grid. Botão `⌨️` ao lado do campo de busca.
+- **Testes:** 3 módulos puros novos em `smart-tv/mk21-tv/core/` (`EpgGuide.js`, `ZapHud.js`, `OnScreenKeyboard.js`) com 39 testes em Node, mais um teste de integração em Chromium que sobe um servidor IPTV simulado (Xtream + XMLTV), faz login no app de verdade e valida o Guia, a navegação por D-pad, o VOLTAR, o HUD de zapping e o teclado virtual. O Playwright é opcional — sem ele o teste é ignorado, não quebrado.
+- **Documentação:** `docs/SAIMO-TV-PARITY.md` (comparativo Saimo TV × MK21 e roteiro do que falta), `docs/MK21-FEATURE-INVENTORY.md` e `docs/SAIMO-TV-FEATURE-INVENTORY.md`.
+- **Bundle da TV:** `app.js` de 236 KB para 255 KB (teto de 350 KB mantido).
+
 ## [2.9.0] - 2026-11-15
 
 - **MK21 AI Copilot (Android):** Redimensionamento da janela do diálogo para teclado virtual (`SOFT_INPUT_ADJUST_RESIZE` + `.imePadding()` / `.navigationBarsPadding()`), caixa de entrada expansível até 5 linhas (`132.dp`) com envio alinhado à base e fechamento automático do teclado.
diff --git a/docs/MK21-FEATURE-INVENTORY.md b/docs/MK21-FEATURE-INVENTORY.md
new file mode 100644
index 0000000..cc2029e
--- /dev/null
+++ b/docs/MK21-FEATURE-INVENTORY.md
@@ -0,0 +1,543 @@
+# MK21 (FBGs-Streaming) — Exhaustive Feature Inventory
+
+**Scope:** existing features already implemented in MK21 across Android (`/app`), Smart TV (`/smart-tv/mk21-tv`) and the Web PWA (repo root → `/web`, `/public`).
+**Purpose:** baseline for computing a gap against the Saimo-TV app.
+**Analysis date:** repo state at `main` @ `469b5ac` (v2.9.0 / versionCode 290).
+**Note:** nothing in the repo was modified; `node scripts/sync-web.mjs --check` was run read-only and reports **OK: 20 arquivos sincronizados**.
+
+---
+
+## A. Architecture summary per platform
+
+### A.1 Canonical-source rule (cross-cutting)
+
+The **repository root is the single source of truth for the web layer**. The following root files are canonical and are mirrored verbatim into `web/` and `public/`:
+
+`index.html`, `sw.js`, `manifest.json`, `apps.json`, `servers.json`, `version.json`, `vercel.json`, `icon.svg`, `icon-192.png`, `icon-512.png`, `api/version.json`, `api/proxy.js`, `assets/logo.png`, plus the whole `css/` and `js/` directories — **20 files** total.
+
+`scripts/sync-web.mjs` is the only writer for the mirrors:
+- `node scripts/sync-web.mjs` → copies root → `web/` + `public/` (SHA-256 compared per file).
+- `node scripts/sync-web.mjs --check` → fails (exit 1) listing every missing/divergent mirror file. This is the CI gate (`validate.yml`).
+- `public/assets/aistudio/` is explicitly excluded from mirroring and never touched.
+
+**Consequence:** any web edit must be made in the root and re-synced; editing `web/index.html` directly is a contract violation.
+
+### A.2 Android / Android TV (`/app`)
+
+| Aspect | Detail |
+|---|---|
+| Entry point | `app/src/main/java/com/example/MainActivity.kt` → `AppNavigation(viewModel)` (`ui/Screens.kt:158`) |
+| Stack | Kotlin 2.1.10, Jetpack Compose (Material 3), AGP 9.1.1, `compileSdk 36`, `minSdk 24`, `targetSdk 36` |
+| Identity | `applicationId = com.aistudio.multiservidor.iptvmk` (namespace `com.example`) |
+| Package layout | Legacy app in `com.example` (`ui/`, `viewmodel/`, `data/`, `ai/`, `network/`, `player/`, `utils/`); newer hardening modules in `com.fbg.streaming` (`update/`, `security/`, `network/proxy/`) |
+| State model | Single `AppViewModel` (AndroidViewModel, 2299 lines) exposing ~30 `StateFlow`/`SharedFlow`; Compose `collectAsState()`; Navigation-Compose for route switching |
+| Persistence | Room DB `mk21_iptv_db` (v2, `MIGRATION_1_2` + destructive fallback) + `EncryptedSharedPreferences` (`mk21_secure_pref_store`) |
+| Player | Media3 ExoPlayer 1.4.1 + HLS extension; fast-start `minBufferMs`/`bufferForPlaybackMs`; `PlaybackSourceFallback` for multi-source retry |
+| OTA | `Mk21UpdateManager` (fail-closed SHA-256, HTTPS-only GitHub hosts, max 5 redirects) + `ApkSignatureVerifier` (certificate pin `EXPECTED_CERT_SHA256`) |
+| Local proxy | `Mk21ProxyStation` (raw-socket HTTP proxy on 127.0.0.1 with per-session `SecureRandom` token) + `LocalHttpServer` (cast server) |
+| Licensing | ECDSA P-256 server-issued tokens (`MKL1.<payload>.<sig>`), offline-verified by `LicenseTokenVerifier` against `BuildConfig.LICENSE_PUBKEY_B64` |
+| AI | Firebase AI Logic — `GeminiAiService` (model `gemini-2.5-flash`) + `GeminiAiViewModel` + `GeminiCopilotDialog` |
+| Build config | `app/build.gradle.kts` — release requires `LICENSE_SECRET` (≥16 chars), `STORE_PASSWORD`, `KEY_PASSWORD`, `KEYSTORE_PATH`; `KEY_ALIAS=upload` |
+
+### A.3 Smart TV (`/smart-tv/mk21-tv`, LG webOS + Samsung Tizen)
+
+| Aspect | Detail |
+|---|---|
+| Entry point | `smart-tv/mk21-tv/src/main.js` (esbuild entry) → bundled to `smart-tv/mk21-tv/app.js` (IIFE) |
+| Runtime shell | `smart-tv/mk21-tv/index.html` (791 lines) + `styles.css` (4614 lines) + `libs/hls.min.js`, `libs/mpegts.min.js` |
+| Stack | Vanilla ES5-ish JS (no framework); webOS 3.0 → webOS 24; Tizen 3.0+ (`smart-tv/tizen/config.xml`) |
+| State model | Module-scoped globals + `localStorage`; a small "Expert Engine" with a Stack-based history FSM for BACK handling (`mk21HandleBack`, `mk21ArmBackHistory`) |
+| Bundling | `esbuild.config.mjs` → `app.js`, format `iife`, target `es2019`, minified, **no sourcemap**, hard **350 KB limit** (build fails above it); `hls.js` aliased to `src/shims/hls-global.js` (reexports `window.Hls`) so hls.js is **not** bundled |
+| OTA | App-level OTA delivers only `app.js` + `styles.css` (`mk21_ota_app_js`, `mk21_ota_app_version`, `mk21_ota_styles_css`); remote engine flag from `smart-tv/version.json` → `playerEngine` |
+| Player architecture | `PlayerService` (adapter) → `PlayerFactory` (TypeScript, `src/players/`) with `TizenAVPlayPlayer`, `WebOSVideoPlayer`, `HLSFallbackPlayer`; legacy path in `src/legacy/app-legacy.js` (9618 lines) |
+| Packaging | `smart-tv/package_ipk.py` → reproducible GNU-AR `.ipk` (webOS); `appinfo.json` validated (icon 80×80, largeIcon 130×130, splash 1920×1080) |
+| Store catalog | `apps.json` / `smart-tv/apps.json` (Homebrew Channel format) with `ipkUrl` + `ipkHash.sha256` |
+
+### A.4 Web PWA (repo root → `web/` → `public/`)
+
+| Aspect | Detail |
+|---|---|
+| Entry point | root `index.html` (7119 lines, 418 KB) — a **single-file** app: HTML + ~2600 lines inline JS + inline CSS |
+| Stack | Vanilla JS + precompiled Tailwind (`css/tailwind.min.css`) + `css/styles.css` + `css/responsive.css`; CDN `hls.js` (latest) + `mpegts.js@1.7.3`; FontAwesome 6.4 |
+| Sub-modules | `js/performance.js`, `js/accessibility.js`, `js/seo-meta.js`, `js/tv-remote-and-pwa.js` (loaded as plain `<script>` tags, non-module) |
+| State model | Module-scoped `let` globals + `MK21_CONFIG` object (`localStorage`) + IndexedDB playlist cache + `sessionStorage` EPG cache |
+| Serverless | Vercel: `api/proxy.js` (streaming proxy) + `api/license/{activate,refresh}.js` (Neon Postgres) + `api/license/_token.js` |
+| PWA | `manifest.json` (standalone, `#E50914`), `sw.js` (network-first, cache `mk21-cache-v2.9.0`) |
+| Routing | `vercel.json` rewrites; `/api/proxy` → `/api/proxy.js`, `/web/*` → `/index.html` |
+
+---
+
+## B. Numbered FEATURE INVENTORY
+
+### B.1 Web PWA — 52 features
+
+| # | Feature | What the user does | Implementation notes | Key files |
+|---|---|---|---|---|
+| 1 | **Login gateway screen** | Picks server, enters user/pass, logs in | Two tabs: "Predefined" (`tab-login-predefined`) and "Manual" (`tab-login-manual`); error banner with shake animation | `index.html:279-433` |
+| 2 | **Predefined server picker** | Selects one of the 7 servers | `<select id="server-selector">`; default fallback = `CB6000`; selected label mirrored to `#selected-server-name-label` | `index.html:324-338`, `populateServers()` `:2955` |
+| 3 | **Fast server chips** | Taps a chip to switch server instantly | Top-6 chips rendered from `servers.json`; active chip highlighted with emerald dot | `renderFastServerChips()` `index.html:3020` |
+| 4 | **Remote `servers.json` sync** | App auto-refreshes the server list | `fetch('./servers.json?ts=' + Date.now())` with cache-bust; falls back to inline `PREDEFINED_SERVERS` replica | `syncServersFromSource()` `index.html:2942`; inline replica `:1657` |
+| 5 | **Manual M3U URL login** | Pastes a `get.php?username=…` URL | `#manual-m3u-input` + paste button; URL used as `baseUrl` | `index.html:341-355` |
+| 6 | **Remember-credentials toggle** | Checks/unchecks to persist login | `#remember-credentials-checkbox`; when off, `mk21_username`/`mk21_password` are removed | `index.html:390`, `:4200-4209` |
+| 7 | **Show/hide password** | Toggles the eye icon | `#toggle-password` swaps `fa-eye-slash` ↔ `fa-eye` | `index.html:381` |
+| 8 | **Clipboard paste buttons** | Pastes URL / user / pass from clipboard | `navigator.clipboard.readText()` with a `handlePaste(targetInput)` helper | `index.html:3360` |
+| 9 | **Server code auto-resolve** | Types a server code, app resolves it | `resolveServerByCode(code)` matches `lotusCode`/`webCode`/`name` | `index.html:3134`, used at `:4156` |
+| 10 | **Quick profiles (saved logins)** | Saves and reuses server+credential sets | `mk21_saved_profiles` in localStorage; rendered as chips, max 12 | `saveQuickProfile()` `:3210`, `renderQuickProfiles()` `:3238` |
+| 11 | **Offline mode / local M3U upload** | Picks a `.m3u/.m3u8/.txt` file from the device | `FileReader` → `parseM3U()` → full catalog without any server | `triggerLocalM3uUpload()` `:2800`, `handleLocalM3uFile()` `:2806` |
+| 12 | **Multi-server automatic failover** | Logs in; app silently switches servers until one authenticates | Candidate list = selected server first, then all `PREDEFINED_SERVERS`; Xtream `player_api.php` auth probe (`user_info.auth`) | `performLogin()` `index.html:4142`, failover loop `:4322-4364` |
+| 13 | **Six-method proxy rotation** | (implicit) app tries 6 transports to reach the server | `vercel` (own serverless), `direct`, `corsproxy.io`, `allorigins`, `codetabs`, `thingproxy`; ordered by measured latency | `index.html:4281-4288` |
+| 14 | **Proxy latency benchmark** | App measures proxy RTT and shows "Aceleração de Conexão Ativada" | Parallel GET to `cloudflare.com/cdn-cgi/trace` through 4 proxies, 3.5 s timeout | `runProxyLatencyCheck()` `index.html:5800` |
+| 15 | **IndexedDB playlist cache** | Login is instant on revisit | DB `mk21_playlist_cache`, store `playlists`, key `playlist_<base64>_<user>`; 7-day TTL; silent background refresh when > 3 h old | `openCacheDB()` `:3933`, `getCachedPlaylist()` `:3951` |
+| 16 | **Cache integrity sweeper** | (implicit) startup removes corrupt/duplicate/expired rows | `verifyAndCleanIndexedDBCache()` — drops null keys, malformed objects, expired entries, dedupes by URL | `index.html:4045` |
+| 17 | **Xtream Codes catalog ingestion** | (implicit) live/movie/series lists load from `player_api.php` | `get_live_categories`, `get_live_streams`, then background `get_vod_streams` + `get_series` after 1.2 s | `index.html:4380-4459` |
+| 18 | **M3U fallback path** | (implicit) used when `player_api.php` is unavailable | `get.php?type=m3u_plus&output=ts` with HTML/credential-error detection | `index.html:4466-4519` |
+| 19 | **Progressive M3U parser (non-blocking)** | (implicit) huge playlists parse without freezing the UI | Character-scanning parser with `fastExtractAttr()` (no regex in hot loop); BOM handling; live/movie/series heuristics | `parseM3UProgressively()` `index.html:5088` |
+| 20 | **Background VOD/series loading** | Live channels appear first; VOD fills in behind | `startBackgroundLoading()` with a `bg-sync-indicator` progress HUD (`#bg-sync-progress`) | `index.html:5281`, `#bg-sync-indicator` `:477` |
+| 21 | **Multi-segment loader HUD** | Watches live/movies/series progress bars during sync | Three progress bars + a total bar in `#app-loader` | `index.html:1351-1423`, `updateCategoryLoaderProgress()` `:3880` |
+| 22 | **Type filter (Live / Movies / Series)** | Switches catalog type | `.type-filter-btn` with `data-type`; `activateLiveHomeView()` resets to LIVE | `index.html:2306-2320` |
+| 23 | **Category sidebar** | Filters by provider category | O(N) frequency counter (not N² filters); per-category item counts; desktop sidebar + mobile sheet + horizontal chip bar | `renderCategories()` `index.html:5342` |
+| 24 | **Horizontal quick-category chips** | Horizontal scroll of top-35 categories | Netflix/TopIbo-style chip row above the grid | `index.html:5393-5415` |
+| 25 | **Saimo-style live channel grid** | Browses channels as square logo cards | `.canal-grid` / `.channel-grid-card`; active channel highlighted; SVG placeholder on logo error with proxy retry | `index.html:5616-5651`; CSS `:143-245` |
+| 26 | **Movie poster grid** | Browses VOD as 2:3 posters | Poster with `referrerpolicy=no-referrer`, `loading=lazy`, proxy retry then inline SVG fallback | `index.html:5586-5615` |
+| 27 | **Series grouping (Netflix engine)** | Sees one card per show, not per episode | `groupSeriesItems()` parses `SxxExx`, `TxxExx`, `Sx EPx`, `Temp. N Ep. N`, `01x02`, `Temporada N Episódio M`, `EP NN` | `index.html:4834-4955` |
+| 28 | **Season + episode selector** | Picks a season, then an episode | `#season-selector` + `#episodes-list`; auto-plays episode 1 on wide screens | `selectShow()` `:4957`, `renderEpisodesForSeason()` `:5029` |
+| 29 | **Auto-next episode** | Next episode starts when the current one ends | `yt-next-btn` shown when a next episode exists; `playNextEpisode()` on `ended` | `index.html:6128`, `:6966-6979` |
+| 30 | **Search** | Searches channels/movies/series by name or category | `#search-input`, live filter on `input`; a "Resultados da Busca" state | `renderMediaItems()` `index.html:5472-5490` |
+| 31 | **Favorites ("Meus Favoritos")** | Hearts a channel/movie/episode | `mk21_favorites` localStorage; dedicated pseudo-category with live count badge `#fav-count-badge` | `toggleFavorite()` `:4770`, `:5366` |
+| 32 | **Continue Watching** | Recently played items reappear as a rail | `mk21_continue`, max 12 items, per-item trash button | `addToContinueWatching()` `:4660`, `renderContinueWatching()` `:4691` |
+| 33 | **VOD resume with seconds** | Reopens a movie exactly where it stopped | `mk21_vod_resume_progress` (url → `{currentTime,duration,name,updatedAt}`); saved every 4 s for media > 60 s; cleared at > 95 %; resume prompt with "Continuar"/"Do Início" | `js/tv-remote-and-pwa.js:104-219` |
+| 34 | **Resume last channel on login** | App re-tunes to the last channel | `MK21_CONFIG.last_channel_id` matched by id/stream_id/name | `resumeLastChannelIfAvailable()` `index.html:2782` |
+| 35 | **Zero-delay zapping prefetch** | Channel changes feel instant | Pre-fetches the next 2 manifests with `Range: bytes=0-2048`, 4 s abort | `prefetchZeroDelayChannels()` `index.html:2715` |
+| 36 | **YouTube-style custom player** | Full custom control bar | Play/pause, scrub bar (click+drag), buffer bar, volume slider, elapsed/duration, live indicator, big-play animation, auto-hide controls after 2.5 s, single-click play/pause, double-click fullscreen | `initYouTubePlayer()` `index.html:6357-6969` |
+| 37 | **Spatial D-Pad navigation** | Remote arrow navigation across the whole UI | Geometric focus engine (`moveSpatial`) over `SPATIAL_SELECTOR`; `.tv-focused` ring styling | `index.html:6414-6504` |
+| 38 | **Smart TV remote key mapping** | Color/media/channel keys do useful things | BACK `10009`/`461`/Esc; RED=type toggle, GREEN=refresh list, YELLOW=cast, BLUE=fullscreen; Play/Pause `415`/`19`/`10252`, Stop `413`, FF `417` +15 s, RW `412` −15 s; ChannelUp/Down = zapping | `js/tv-remote-and-pwa.js:282-525` |
+| 39 | **Sleep timer ("Modo Soneca")** | Sets a timer that pauses playback | Menu with several minute values; live countdown badge `#yt-sleep-badge` | `js/tv-remote-and-pwa.js:533-574`; menu `index.html:751-763` |
+| 40 | **Playback speed (up to 4×)** | Changes playback rate | `#yt-speed-menu`; label `#yt-speed-label` | `js/tv-remote-and-pwa.js:579-590`; menu `index.html:768-781` |
+| 41 | **Audio booster (+200 %)** | Boosts loudness via Web Audio | `AudioContext` → `GainNode` → `MediaElementSource`; label `#yt-boost-label` | `js/tv-remote-and-pwa.js:599-626`; menu `index.html:787-795` |
+| 42 | **Audio track & subtitle picker** | Switches audio/subtitle tracks | Reads `hlsPlayer.audioTracks`/`subtitleTracks` and native `video.audioTracks`/`textTracks` | `openAudioSubsModal()` `index.html:6984`; modal `:1147` |
+| 43 | **Picture-in-Picture** | Floats the player in a mini window | Standard PiP + `webkitSetPresentationMode` fallback | `togglePictureInPicture()` `index.html:6331`; button `:807` |
+| 44 | **Cinema / theater mode** | Widens the player column | `.theater-mode-active`, width `min(58vw, 860px)` | `toggleTheaterMode()` `index.html:6916` |
+| 45 | **CSS upscaling ("HD Enhance")** | Sharpens the picture | Toggles `.upscale-filter` (`contrast/saturate/brightness`); persisted in `MK21_CONFIG.upscaling_active` | `toggleUpscaleFilter()` `index.html:2757`; CSS `:258` |
+| 46 | **Cast / mirror to TV** | Sends playback to a TV | AirPlay (`webkitShowPlaybackTargetPicker`) → Remote Playback API → Google Cast SDK → manual URL modal (`#web-cast-modal` + copy button) | `js/tv-remote-and-pwa.js:660-767` |
+| 47 | **External player hand-off (VLC)** | Opens the stream in VLC | `vlcs://` / `intent://…package=org.videolan.vlc` / `vlc-x-callback://` per UA | `getVlcUrl()` `index.html:5891`; buttons `:861`, `:1005` |
+| 48 | **Auto-external-player mode** | Makes the external player the default | `mk21_auto_external` toggle; skips the web player entirely | `toggleAutoExternalPlayer()` `index.html:5984`, used `:6146` |
+| 49 | **Mixed-content (HTTP on HTTPS) handling** | (implicit) HTTP-only servers still play | Auto-switches to the HTTPS serverless proxy; warns the user; offers a "connection direct" override | `getEffectiveProxyMethod()` `:5722`, `updateHttpPlaybackNotice()` `:5761` |
+| 50 | **MPEG-TS playback via mpegts.js** | Raw `.ts` channels play in-browser | `mpegts.createPlayer({type:'mpegts', isLive})` with worker + range seek | `index.html:6186-6222` |
+| 51 | **HLS↔TS auto-fallback** | (implicit) channel recovers from a format mismatch | On fatal manifest error, falls back from HLS to native MPEG-TS and rotates the proxy list | `index.html:2578-2613` |
+| 52 | **EPG — real short EPG per channel** | Sees "NO AR: <program>" on the channel card | `player_api.php?action=get_short_epg&stream_id=…&limit=12`; multi-key cache (`id_`, `tvgid_`, `tvgname_`, `name_`) + `sessionStorage` hydration | `EpgManager.fetchRealEpg()` `index.html:1782` |
+| 53 | **EPG — full XMLTV guide modal** | Opens the full schedule grid | `xmltv.php` fetched and regex-parsed into a per-channel programme map; modal shows "NO AR" + upcoming list | `loadServerXmltv()` `:1858`, `openScheduleModal()` `:2143`, modal `:1186` |
+| 54 | **EPG — simulated grid fallback** | (implicit) non-real EPG when the server has none | Hardcoded Brazilian broadcaster grids (Globo, Record, SBT, Band, sports, premium channels) | `getSimulatedProgramInfo()` `index.html:1962-2097` |
+| 55 | **EPG refresh button** | Re-fetches the current channel's guide | `window.refreshActiveChannelEpg()` invalidates the cache keys and refetches | `index.html:2259` |
+| 56 | **Settings modal (3 tabs)** | Account / Preferences / Notifications | `switchSettingsTab('account'|'preferences'|'notifications')`; preferences include cache, hero banner and category chips toggles | `index.html:1426-1546`, `:2478` |
+| 57 | **High-contrast mode** | Improves readability | `.high-contrast` body class, persisted | `toggleHighContrast()` `index.html:5710` |
+| 58 | **PWA install flow** | Installs as an app | `beforeinstallprompt` capture, install modal offering APK or PWA, `appinstalled` handler, SW registration, HTTP→HTTPS auto-redirect | `js/tv-remote-and-pwa.js:1-99`; modal `index.html:1238` |
+| 59 | **Service worker offline shell** | App shell loads offline | Network-first, cache `mk21-cache-v2.9.0`; deliberately skips `/api/`, proxy URLs and range requests | `sw.js:2-63` |
+| 60 | **Accessibility layer** | Keyboard-only navigation | Focus trap in dialogs, arrow-key list navigation, skip link, ARIA labels on 8 controls, live regions, `prefers-reduced-motion` / `prefers-color-scheme` | `js/accessibility.js` (294 lines) |
+| 61 | **Performance layer** | Smoother list rendering | Debounce, TTL cache, request dedup, `IntersectionObserver` lazy images, virtual scroller helper | `js/performance.js` (206 lines) |
+| 62 | **SEO / metadata layer** | Better link previews | OG + Twitter cards, JSON-LD `WebApplication`, robots, canonical, hreflang, preload/dns-prefetch | `js/seo-meta.js` (246 lines) |
+| 63 | **In-app diagnostics console** | Exports a playback log | Per-player log with redaction of credentials; copy + download | `addLog()` `:2345`, `downloadDiagnosticLogs()` `:2407`, `#diagnostic-log-console` `:1057` |
+| 64 | **Player error overlay** | Sees why a channel failed | Retry, "open in VLC", "open with other app" | `#player-error-overlay` `index.html:841` |
+| 65 | **Hero banner / highlight carousel** | Featured content on the home screen | `#hero-banner-section` with play / detail / favorite and rotation dots | `index.html:549-596` |
+| 66 | **Logout** | Returns to the login screen | Stops the stream, clears background loading, animated transition | `performLogout()` `index.html:4588` |
+| 67 | **Toast notifications** | Feedback on every action | `showNotification(message, type)` with success/error/info variants, auto-dismiss 3 s | `index.html:4620` |
+| 68 | **Copy stream URL** | Copies the raw URL | `copyCurrentStreamUrl()` + `#stream-url-display` (selectable) | `index.html:6324`, `:1012` |
+
+> (The numbering above is sequential; the Web surface is **68** concrete features.)
+
+### B.2 Smart TV — 47 features
+
+| # | Feature | What the user does | Implementation notes | Key files |
+|---|---|---|---|---|
+| 1 | **Fullscreen login modal** | Enters user/pass on the TV | Absolute-black modal; identity shown on boot; `handleLoginSubmit()` | `mk21-tv/index.html:33`, `app-legacy.js:8969` |
+| 2 | **Offline bypass login** | Opens the cached catalog without credentials | `handleOfflineBypass()`; `getBestAvailableCache(preferredId)` | `app-legacy.js:9072`, `:9084` |
+| 3 | **Session token verification** | (implicit) app validates the saved session before unlocking | `verifySessionToken(token)` → `mk21_auth_token` | `app-legacy.js:9254` |
+| 4 | **3-column unified home** | Categories | Items | Player + info | `mk21-tv/index.html:152-257` (`#sectionUnified`) |
+| 5 | **Content-type tabs (Live/Movies/Series/Favorites)** | Switches catalog section | `switchContentType(type)` | `app-legacy.js:5130` |
+| 6 | **Category list** | Filters by group | `buildCurrentCategories()` / `renderCategoriesList()` / `selectCategory()` | `app-legacy.js:2133`, `:2184`, `:2232` |
+| 7 | **Item list with virtual paging** | Scrolls the catalog | `renderItemsList(preserveScroll)` + IndexedDB catalog index with `queryCatalogIndexPage` / `searchCatalogIndex` | `app-legacy.js:2254`, `:459`, `:486` |
+| 8 | **Indexed favorites query** | Favorites load from the index | `queryIndexedFavorites(serverId, favoriteSet)` / `hydrateIndexedFavorites()` | `app-legacy.js:529`, `:553` |
+| 9 | **Omnisearch (global search)** | Searches across all servers and types | Dedicated modal `#modalOmnisearch` with per-type counters (`countOmniAll/Live/…`) | `app-legacy.js:3292`, `:3320`, `:3368`; modal `index.html:699` |
+| 10 | **Series modal (seasons + episodes)** | Browses a show's seasons/episodes | `#modalSeriesEpisodes`, Netflix-style two columns | `app-legacy.js:2536`, `:2645`; modal `index.html:445` |
+| 11 | **EPG — real XMLTV match** | Sees current/next programme on the OSD | `matchEPG()`, `applyRealSmartTvEpg()`, `parseXmltvTimestamp()`, base64 decode `decodeSmartTvEpgText()` | `app-legacy.js:2775`, `:2879`, `:2913` |
+| 12 | **EPG — simulated programme grid** | (implicit) fallback grid | `getSimulatedTvProgram(channelName)` | `app-legacy.js:2707` |
+| 13 | **Full EPG guide modal** | Opens the complete schedule | `#modalEpgGuide` | `app-legacy.js:3437`; modal `index.html:728` |
+| 14 | **Adult-content PIN** | Unlocks 18+ content | `mk21_adult_pin`; PIN modal `#modalPin` + `verifyPin()` | `app-legacy.js:8326`, `:8364`; modal `index.html:407` |
+| 15 | **Adult category lock** | (implicit) blocks adult categories until the PIN is entered | `isAdult()` heuristic over name/category | `app-legacy.js:1034` |
+| 16 | **Favorites** | Toggles a favorite | `mk21_favs`; `toggleFav(url)`; heart button `#btnFavorite` | `app-legacy.js:984`; button `index.html` header |
+| 17 | **Continue Watching** | Re-opens recent items | `mk21_continue_watching`; `recordWatchedItem()` | `app-legacy.js:1016` |
+| 18 | **Resume last channel** | Tunes to the last channel at boot | `mk21_last_channel`; `resumeLastChannelIfPossible()` | `app-legacy.js:803`, `:823` |
+| 19 | **Numeric channel entry (0–9)** | Types a channel number | Overlay `#channelNumberOverlay`, `handleDigitPress()` / `commitChannelNumber()` | `app-legacy.js:3217`, `:3230`; overlay `index.html:650` |
+| 20 | **Channel up/down zapping** | Moves between channels | `playNextChannel()` / `playPreviousChannel()` | `app-legacy.js:4635`, `:4643` |
+| 21 | **Multi-source failover across servers** | (implicit) channel recovers from a dead server | `mk21AutoAdvanceServer()`, `mk21TryAlternativeSource()`, `mk21SameChannel()`, `mk21ResetSourceFailover()` | `app-legacy.js:9505`, `:9555`, `:9550`, `:9537` |
+| 22 | **Alternative source grouping by tvg-id** | One card, several sources | `core/AlternativeSources.js` — identity by `tvg-id` (with name+category+type fallback), `alternateUrls` array, cross-server matching with quality-suffix normalization | `core/AlternativeSources.js` |
+| 23 | **Player options modal (quality / sources / super-res)** | Picks quality or an alternate source | `mk21EnsurePlayerOptionsModal()`, `mk21RenderPlayerOptionsTabs()` | `app-legacy.js:9380`, `:9400`; `components/PlayerOverlay.js` |
+| 24 | **Playback quality selection** | Picks 1080p / 4K etc. | `mk21QualityPref()`, `mk21QualityHeights()`, `mk21ApplyPlaybackQuality(hls)` | `app-legacy.js:9279`, `:9294`; modal `:9319` |
+| 25 | **Buffer mode / fluidity** | Trades latency vs. stability | `mk21BufferMode()`, `cycleBufferMode()` | `app-legacy.js:4105`, `:4892` |
+| 26 | **Stall watchdog + auto-reconnect** | (implicit) recovers a frozen stream | `startStallWatchdog()`, `triggerAutoReconnect()`, `attemptDnsFailover()` | `app-legacy.js:3591`, `:3654`, `:3504` |
+| 27 | **DNS failover for dead hosts** | (implicit) resolves an alternative IP | `attemptDnsFailover(originalUrl)` | `app-legacy.js:3504` |
+| 28 | **Audio & subtitle modal** | Switches tracks | `#modalAudioSubs`; `openAudioSubsModal()` | `app-legacy.js:4965`; modal `index.html:288` |
+| 29 | **Playback speed** | Changes rate | `cyclePlaybackSpeed()` / `applyPlaybackSpeed(rate)` | `app-legacy.js:4943`, `:4927` |
+| 30 | **Aspect-ratio cycling** | Changes the picture ratio | `cycleAspectRatio()` | `app-legacy.js:3143` |
+| 31 | **Fullscreen + HUD** | Enters fullscreen with an OSD | `toggleFullscreen(force)`, `showFsHud()`, `mk21TeleportModalForFullscreen()` | `app-legacy.js:4709`, `:4657`, `:4695` |
+| 32 | **Strict D-Pad focus engine** | Remote navigation never gets lost | `mk21MoveFocus(direction)`, `mk21FocusSafe()`, `mk21ScrollActive()`, `mk21GetFocusables()`, `mk21ResetViewportScroll()` | `app-legacy.js:7584`, `:7573`, `:7835` |
+| 33 | **Stack-based BACK history** | BACK closes overlays then exits (with confirm) | `mk21HandleBack()`, `mk21ArmBackHistory()`, `mk21AskExit()` / `mk21ExitApp()` | `app-legacy.js:7727`, `:7778`, `:7710` |
+| 34 | **WebOS scroll-rail fix** | Scrollbars behave on LG TVs | `initWebOSScrollFix()`, `mk21EnsureVisualScrollbarRail()` | `app-legacy.js:8728`, `:8650` |
+| 35 | **Virtual-keyboard handling** | On-screen keyboard input works | `setupVirtualKeyboardHandling()` | `app-legacy.js:8960` |
+| 36 | **Native voice search (Tizen)** | Speaks a query | `startNativeVoiceSearch()` with WebSpeech fallback | `app-legacy.js:8269` |
+| 37 | **Server manager modal** | Adds / edits / deletes servers | `#modalServerPicker`; `editServer()`, `deleteServer()`, `renderServerPickerList()` | `app-legacy.js:6915`, `:7041`, `:7063`; modal `index.html:308` |
+| 38 | **Per-server catalog metrics** | Sees channels/VOD/series/EPG totals per server | `computeCatalogMetrics()` in `core/CatalogUtils.js`; pills rendered by `buildPerServerMetricsPillsHtml()` | `core/CatalogUtils.js:60`, `app-legacy.js:5383` |
+| 39 | **Background sync frequency** | Chooses startup/daily/weekly/manual | `normalizeBgSyncFrequency()` + legacy aliases map | `core/CatalogUtils.js:6-58`, `app-legacy.js:642` |
+| 40 | **Sync all servers in background** | Refreshes every configured server | `syncAllConfiguredServersInBackground()`, `syncSingleServerInBackground()` | `app-legacy.js:6772`, `:6700` |
+| 41 | **Staged loading (carregamento em etapas)** | Controls live/movie/series load order | `#modalStagedLoading`; `getStagedLoadingPrefs()` | `app-legacy.js:689`, `:6629`; modal `index.html:564` |
+| 42 | **Content filter (load live/movies/series)** | Chooses what to download | `mk21_filter_load_live` / `_movies` / `_series` | `app-legacy.js:5609` |
+| 43 | **Hidden categories** | Hides unwanted groups | `mk21_hidden_categories`; `renderCategoriesManagerPanel()` | `app-legacy.js:6391` |
+| 44 | **Sort / classification picker** | Sorts the catalog | `#modalSortPicker`; `openSortPickerModal()`; `persistSortOrder()` | `app-legacy.js:2484`, `:850`; modal `index.html:521` |
+| 45 | **Stream format switch (MPEG-TS ↔ HLS)** | Changes the live transport | `saveStreamFormat(fmt)`; `mk21_stream_format` | `app-legacy.js:5682` |
+| 46 | **Speed test** | Measures the connection | `#cfgBtnSpeedTest`; `runSpeedTest()` with parallel download workers, window/steady Mbps, upload workers | `app-legacy.js:5817`, `:6002`, `:6101` |
+| 47 | **OTA app update** | Updates the TV app in place | `#modalAppUpdate`; `openAppUpdateModal()`, `startDirectUpdate()`, `compareSemver()`; downloads only `app.js` + `styles.css` | `app-legacy.js:7200`, `:7297`, `:7177`; modal `index.html:467` |
+| 48 | **Developer log console + export** | Exports a diagnostic log | `#modalDevLogs`; `mk21RenderAllDevLogs()`, `mk21ExportDevLogs` | `app-legacy.js:3963`, `:3898`; modal `index.html:657` |
+| 49 | **Stream diagnostics modal** | Inspects the live stream | `#modalStreamDiagnostics`; `toggleStreamDiagnostics()` | `app-legacy.js:3163`; modal `index.html:680` |
+| 50 | **Font size setting** | Scales the UI | `mk21_font_size`; `applyFontSize(size)` | `app-legacy.js:6162`, `:6198` |
+| 51 | **Time settings / timezone** | Aligns the clock and EPG | `mk21_timezone`; `renderTimeSettingsPanel()` | `app-legacy.js:6345` |
+| 52 | **Toast display mode + on-screen notices** | Controls notification verbosity | `mk21_toast_mode` / `mk21_show_toasts`; `showChannelBanner()` | `app-legacy.js:4562`, `:4589` |
+| 53 | **TMDB metadata toggle** | Optionally enriches titles | `#cfgToggleTmdb`; `fetchTmdbMetadata()`, `applyTmdbData()` | `app-legacy.js:2967`, `:2988` |
+| 54 | **Player Factory (tri-state engine flag)** | Switches the playback engine | `localStorage.FBG2_PLAYER_LEGACY` → `FBG2_PLAYER_ENGINE` → remote `playerEngine` → default | `src/player-service.js:16-115` |
+| 55 | **Atomic stream teardown** | (implicit) no audio leak between channels | `stopCurrentPlayer(abortPlayRequests)`, `safeVideoPlay()`, `mk21SeekBy()` | `app-legacy.js:4025`, `:4006`, `:4088` |
+| 56 | **Adjacent-channel prefetch** | Faster zapping | `prefetchAdjacentChannels()`, `prefetchNextChannelStream()` | `app-legacy.js:3012`, `:3064` |
+| 57 | **VOD progress tracking** | Resumes movies | `startProgressTracker()`, `saveProgress(contentId,…)`, `updateVodProgressTimeline()` | `app-legacy.js:3093`, `:3104`, `:4510` |
+| 58 | **Memory shield cleanup** | Prevents OOM on old TVs | `runMemoryShieldCleanup()` | `app-legacy.js:3271` |
+| 59 | **Non-blocking M3U parsing via Web Worker** | Large playlists don't freeze the TV | Batch of 500 items, progress messages | `workers/playlistWorker.js` |
+| 60 | **Memory-integrity player overlay (XSS-safe)** | (implicit) safe text rendering | `textContent` only, `innerHTML` removed | `components/PlayerOverlay.js` |
+
+### B.3 Android — 48 features
+
+| # | Feature | What the user does | Implementation notes | Key files |
+|---|---|---|---|---|
+| 1 | **Startup gate + trial screen** | App shows branding then the license/trial gate | `StartupGateScreen()`, `TrialExpiredScreen(viewModel)` | `ui/Screens.kt:586`, `:207` |
+| 2 | **Server configuration screen** | Adds / imports / selects servers | `ServerConfigScreen(viewModel, onNavigateToHome)`; predefined 7 + manual lists | `ui/Screens.kt:608` |
+| 3 | **Predefined 7 servers** | Picks a bundled server | Hardcoded `staticDefaultServers` in the ViewModel (same set as `servers.json`) | `viewmodel/AppViewModel.kt:73-81` |
+| 4 | **Dynamic server fetch** | Server list refreshes from the network | `fetchDynamicServers()` from `dynamic_servers_url` (default raw GitHub `servers.json`); `mergeWithDefaults()` keeps defaults and drops retired hosts | `AppViewModel.kt:926`, `:567` |
+| 5 | **Plain-text server import** | Pastes a list of server URLs | `parseServersFromPlainText()` / `importServersFromPlainText()` — extracts base URL from `get.php`/`player_api.php` lines and infers a name | `AppViewModel.kt:614`, `:730` |
+| 6 | **Per-server or global credentials** | Chooses one login set for all | `useSameCredentialsForServers` flag | `data/service/PreferencesService.kt:98` |
+| 7 | **Manual M3U playlist CRUD** | Adds/edits/deletes a custom list | `ManualPlaylist` entity + DAO + `addManualPlaylist`/`updateManualPlaylist`/`deleteManualPlaylist` | `model/Models.kt:19`, `AppViewModel.kt:1488-1524` |
+| 8 | **M3U download & parse (Okio streaming)** | Catalog loads without OOM | `M3UParser.parse()` over `InputStream.source().buffer()`; progress every 400 items; BOM stripping; `#EXTGRP` support | `data/parser/M3UParser.kt:20` |
+| 9 | **Content-type heuristics** | (implicit) live/movie/series classification | URL patterns `/LIVE/`, `/MOVIE/`, `/SERIES/`; live-category list; VOD extensions; series regex + PT-BR keywords | `M3UParser.kt:272-380` |
+| 10 | **Adult-content detection** | (implicit) 18+ items flagged | Keyword list (`18+`, `ADULTO`, `XXX`, `PLAYBOY`, `HUSTLER`, `FORA DA LEI`…) | `M3UParser.kt:382` |
+| 11 | **Stream-URL sanitization** | (implicit) blocks `file://`/`javascript:` | `sanitizeStreamUrl()` allows only http/https/rtmp/rtsp/mms | `M3UParser.kt:142` |
+| 12 | **Alternate-source merging** | One channel, several URLs | `mergeAlternateStreams()` groups by `tvg-id` (or name+category+type) into `alternateUrls` | `M3UParser.kt:101`; Room column `alternateUrls` + `PlaylistItemConverters` |
+| 13 | **Multi-pattern playlist URL fallback** | (implicit) works with different panel versions | `get.php` plain → `type=m3uplus` → `type=m3u_plus`, `output=mpegts|hls` per preference | `AppViewModel.kt:1706` |
+| 14 | **Content-type server failure diagnosis** | User sees a precise error | HTML portal, JSON error, invalid credentials, expired account, connection limit | `M3UParser.diagnoseContent()` `:391` |
+| 15 | **Room-backed catalog** | (implicit) instant offline catalog | `playlist_items` with indices on `(playlistSource, contentType)`, `(playlistSource, category, contentType)`, `(playlistSource, isFavorite)`; chunked inserts of 500 | `data/database/IptvDatabase.kt` |
+| 16 | **Favorites** | Hearts a channel/movie | `isFavorite` column + `getFavorites()` + `setFavorite()`; `toggleFavorite()` in the ViewModel | `IptvDatabase.kt:30`, `:76`; `AppViewModel.kt:1297` |
+| 17 | **Continue Watching** | Re-opens recent VOD | `lastWatchedTime` column + `getContinueWatching(source, type)` | `IptvDatabase.kt:51`; `AppViewModel.kt:525` |
+| 18 | **Per-type history clearing** | Clears only movies / series / live history | `clearMoviesHistory`, `clearSeriesHistory`, `clearLiveHistory`, `clearMoviesAndSeriesHistory` | `IptvDatabase.kt:82-92` |
+| 19 | **Search** | Searches the catalog | `searchItems(source, "%query%")` + live `searchQuery` StateFlow | `IptvDatabase.kt:27`; `AppViewModel.kt:358-386` |
+| 20 | **Category filtering** | Filters by group per type | `getCategoriesByType()`, `getItemsByCategoryAndType()` | `IptvDatabase.kt:21`, `:24` |
+| 21 | **Sorting** | Sorts the list | `SortOrder` enum: A-Z, Z-A, addition, number, rating | `AppViewModel.kt:310-325`, `:393` |
+| 22 | **Parental control — adult PIN** | Unlocks adult content with a PIN | `adultPin` (default `0000`), `AdultPinDialog`, `adultPinGranted`, `checkPinAndPlay()`, category-level PIN prompt | `PreferencesService.kt:94`, `ui/Screens.kt:4140`, `AppViewModel.kt:1257` |
+| 23 | **Trial / license gating** | Sees remaining trial days or activates | 5-day trial from `trial_start_date`; `activateLicense(code)` → `POST /api/license/activate`; `refreshLicenseIfNeeded()` every 30 days | `PreferencesService.kt:232`, `AppViewModel.kt:125`, `:188` |
+| 24 | **ECDSA P-256 offline license verification** | (implicit) license works offline | `LicenseTokenVerifier.verify()` against `BuildConfig.LICENSE_PUBKEY_B64`; token format `MKL1.<b64url payload>.<b64url sig>` | `com/fbg/streaming/security/LicenseTokenVerifier.kt` |
+| 25 | **Home screen (grid + carousel)** | Browses the catalog | `HomeScreen(...)`; `HighlightBanner(...)` (random highlights, `getRandomHighlights`); `GroupedSeriesCard(...)` | `ui/Screens.kt:1725`, `:2834`, `:3034` |
+| 26 | **Series grouping + episode list** | Browses seasons/episodes | `GroupedSeries` model; `EpisodesList`; season selector with episode counts | `ui/Screens.kt:3034`, `:929` region |
+| 27 | **Full-screen player UI** | Watches content | `VideoPlayerUI(...)` — source picker, aspect-ratio, sleep timer, speed, PiP, subtitles, brightness, lock-screen, rotate | `ui/Screens.kt:4244` |
+| 28 | **Multi-source / alternate-server switching** | Picks a different server for the same channel | `SaimoSourcesDialog`; `resolveRealChannelUrlOnServer()`; `buildServerStreamUrl()` rebuilds the URL preserving the Xtream path/port; `cleanChannelName()` normalizes names for cross-server matching | `ui/SaimoPlayerDialogs.kt:34`, `AppViewModel.kt:1836`, `:1750`, `:1788` |
+| 29 | **Super-resolution dialog** | Enables an upscale filter | `SaimoSuperResolutionDialog`; pref `saimo_super_resolution` | `ui/SaimoPlayerDialogs.kt:150`, `PreferencesService.kt:284` |
+| 30 | **Picture-in-Picture with media actions** | Plays in a mini window with Prev/Play-Pause/Next | `PictureInPictureParams` + `RemoteAction`s + `BroadcastReceiver` for `ACTION_PLAY_PAUSE`/`NEXT`/`PREVIOUS`; auto-enter on `onUserLeaveHint` | `MainActivity.kt:19-188` |
+| 31 | **Sleep timer** | Pauses after N minutes | `yt-sleep-*` equivalents in Compose | `ui/Screens.kt` player region |
+| 32 | **Playback speed** | Changes rate | `currentSpeed` label | `ui/Screens.kt` player region |
+| 33 | **Audio & subtitle selection** | Switches tracks | "Áudio & Legendas" sheet; `subtitleConfig` preference | `ui/Screens.kt` player region |
+| 34 | **Aspect ratio / zoom / rotate** | Adjusts the picture | "Alternar Modo de Proporção", "Esticar", "Zoom (Cortar)", "Girar Tela" | `ui/Screens.kt` player region |
+| 35 | **Screen lock while playing** | Prevents accidental taps | "Bloquear Toques na Tela" | `ui/Screens.kt` player region |
+| 36 | **Brightness control** | Adjusts screen brightness | Brightness indicator | `ui/Screens.kt` player region |
+| 37 | **DLNA discovery & casting** | Sends playback to a DLNA TV | `DlnaDiscoveryService` (SSDP M-SEARCH), `DlnaSoapClient` (SetAVTransportURI/Play/Pause/Seek/Stop) | `data/service/DlnaDiscoveryService.kt`, `DlnaSoapClient.kt` |
+| 38 | **Local cast HTTP server** | TV pulls the stream from the phone | `LocalHttpServer` on the LAN with a session token; remote play/pause/seek/volume control channels | `data/service/LocalHttpServer.kt` (504 lines) |
+| 39 | **Smart TV cast dialog** | Picks a TV from the phone | `SmartTvCastDialog(...)` | `ui/Screens.kt:6071` |
+| 40 | **External player integration** | Opens VLC/MX Player/Just Player/Kodi | `ExternalPlayerHelper` with package map, install detection, chooser fallback | `data/utils/ExternalPlayerHelper.kt` |
+| 41 | **MK21 AI Copilot (Gemini)** | Asks "onde assistir Palmeiras x Santos?" and gets channel recommendations | Firebase AI Logic `gemini-2.5-flash`, streaming `Flow<String>`, 60 s timeout, local catalog synthesis fallback; `getRelevantItemsForCopilot()` routes sports intents to `getLiveSportsChannelsGlobal()` | `ai/GeminiAiService.kt`, `ai/GeminiAiViewModel.kt`, `ui/GeminiCopilotDialog.kt` |
+| 42 | **Settings screen (large)** | Configures everything | `SettingsScreen(viewModel, onNavigateBack)` — license, servers, live categories, layout, font, language (PT/EN/ES), time format, stream format, subtitles, external player, AMOLED, cache, logs, backup/restore, auto-update | `ui/Screens.kt:7001` |
+| 43 | **Backup export / import (JSON)** | Backs up the catalog+settings | `exportBackupJson()` / `restoreBackupJson()` with credential redaction regexes | `AppViewModel.kt:1895`, `:1953` |
+| 44 | **Auto-update with signature pinning** | App updates itself | `checkForUpdates()` → `Mk21UpdateManager` (fail-closed SHA-256, HTTPS-only GitHub hosts, ≤5 redirects, `apksigner` verification via `ApkSignatureVerifier`); installs via FileProvider | `com/fbg/streaming/update/Mk21UpdateManager.kt`, `AppViewModel.kt:2142`, `:2187` |
+| 45 | **Crash logger & diagnostics** | Exports a crash report | `CrashLogger.init()` writes to app files; settings shows and copies the log | `utils/CrashLogger.kt` |
+| 46 | **Local secure proxy station** | (implicit) proxies streams through a token-protected local server | `Mk21ProxyStation` — random session token, `MessageDigest.isEqual` comparison, HLS manifest rewriting + segment prefetch, TS byte sanitizing | `com/fbg/streaming/network/proxy/Mk21ProxyStation.kt` |
+| 47 | **DNS-over-HTTPS resolver** | (implicit) resolves hosts safely | `okhttp-dnsoverhttps` + `DnsResolver` | `network/DnsResolver.kt` |
+| 48 | **Encrypted preferences with migration** | (implicit) credentials stored encrypted | `EncryptedSharedPreferences` (AES256-SIV keys / AES256-GCM values) + automatic migration from the legacy plain `mk21_pref_store` | `PreferencesService.kt:17-66` |
+
+---
+
+## C. Data model & persistence
+
+### C.1 Content sources
+
+| Source | Where | Details |
+|---|---|---|
+| **Xtream Codes API** | Web, TV | `player_api.php` with `action=` in `{get_live_categories, get_live_streams, get_vod_streams, get_series, get_short_epg}` |
+| **M3U / M3U_plus** | Web, TV, Android | `get.php?username=…&password=…&type=m3u_plus&output=ts` (+ `m3uplus` variant on Android) |
+| **XMLTV EPG** | Web | `xmltv.php?username=…&password=…` (or `url-tvg=` from the M3U header) |
+| **Short EPG** | Web, Android | `player_api.php?...&action=get_short_epg&stream_id=<id>&limit=12` (10 on Android) |
+| **Local M3U file** | Web, TV (offline cache) | Browser `FileReader`; TV reads its own IndexedDB cache |
+| **Server catalog** | All | `servers.json` (7 servers) |
+
+### C.2 Stream URL shapes
+
+```
+LIVE   {base}/live/{user}/{pass}/{stream_id}.ts
+MOVIE  {base}/movie/{user}/{pass}/{stream_id}.{container_extension|mp4}
+SERIES {base}/series/{user}/{pass}/{series_id}
+```
+
+### C.3 Item field sets
+
+**Web** (`index.html:4396-4448`, `parseM3UProgressively`):
+`name`, `category`, `logo`, `url`, `streamId`, `type` (`LIVE|MOVIE|SERIES`), `streamType`, plus `tvgId`, `tvgName` (M3U) / `rating`, `year` (Xtream).
+
+**TV** (`app-legacy.js`): `name`, `url`, `logo`, `group`, `contentType`, `tvgId`, `tvg-name`, `alternateUrls`, `current_program`, `currentTime`.
+
+**Android** (`data/model/PlaylistItem.kt`):
+`id`, `name`, `url`, `logoUrl`, `category`, `contentType`, `isAdult`, `playlistSource`, `lastWatchedTime`, `isFavorite`, `alternateUrls` (JSON-converted).
+
+**Server profile** (`data/model/ServerProfile.kt`): `id`, `name`, `baseUrl`, `username?`, `password?`, `isActive`.
+**servers.json** entry: `id`, `name`, `baseUrl`, `lotusCode`, `webCode`.
+
+### C.4 Persistence locations
+
+**Web — localStorage**
+| Key | Purpose |
+|---|---|
+| `MK21_CONFIG` | JSON blob: `last_channel_id`, `volume_level`, `favorites_list`, `upscaling_active`, `zapping_mode` (`zero-delay`\|`instant`\|`smooth`) |
+| `mk21_baseUrl`, `mk21_server_name`, `mk21_server_code` | Active server |
+| `mk21_username`, `mk21_password`, `mk21_remember` | Credentials (opt-in) |
+| `mk21_cors` | Proxy mode (`auto`\|`vercel`\|`direct`\|`corsproxy`\|`allorigins`\|`codetabs`\|`thingproxy`) |
+| `mk21_favorites` | Favorites array |
+| `mk21_continue` | Continue-watching array (max 12) |
+| `mk21_saved_profiles` | Quick login profiles |
+| `mk21_player_volume` | Player volume |
+| `mk21_auto_external` | Auto external player |
+| `mk21_high_contrast` | High-contrast mode |
+| `mk21_vod_resume_progress` | VOD resume map (`url → {currentTime,duration,name,updatedAt}`) — written by `js/tv-remote-and-pwa.js:104` |
+
+**Web — sessionStorage**: `mk21_epg_<key>` (`id_*`, `tvgid_*`, `tvgname_*`, `name_*`) — max 15 programmes per key.
+
+**Web — IndexedDB**: DB `mk21_playlist_cache`, store `playlists`, key `playlist_<encodeURIComponent(baseUrl)>_<encodeURIComponent(user)>`, value `{key, lives[], movies[], series[], timestamp}`; TTL 7 days.
+
+**Web — Cache Storage**: `mk21-cache-v2.9.0` (`sw.js`).
+
+**Android**
+- Room `mk21_iptv_db` → `playlist_items`, `manual_playlists`; `MIGRATION_1_2` adds `alternateUrls`.
+- `EncryptedSharedPreferences` file `mk21_secure_pref_store` (fallback `mk21_secure_pref_store_fallback`): `username`, `password`, `active_server_id`, `active_playlist_name`, `adult_pin`, `last_list_update_<playlist>`, `use_same_credentials`, `use_amoled_mode`, `hide_live_categories`, `use_external_player`, `external_player_type`, `device_type`, `app_language`, `time_format`, `app_layout`, `live_stream_format`, `subtitle_config`, `menu_sort_order`, `trial_start_date`, `activation_key`, `license_token`, `cached_servers_json`, `dynamic_servers_url`, `load_live_foreground`, `load_movies_foreground`, `load_series_foreground`, `sync_interval_frequency`, `sync_all_lists_background`, `hide_background_progress`, `saimo_super_resolution`.
+- Device identity: `ANDROID_ID` → hex → 12-char → `virtualMac` (`AA:BB:CC:DD:EE:FF`).
+
+**Smart TV — localStorage**
+`iptv_user`, `iptv_pass`, `mk21_user`, `mk21_pass`, `mk21_username`, `mk21_password`, `mk21_credentials`, `mk21_auth_token`, `mk21_servers_list`, `mk21_last_server`, `mk21_last_channel`, `mk21_continue_watching`, `mk21_favs`, `mk21_adult_pin`, `mk21_hidden_categories`, `mk21_filter_load_live|_movies|_series`, `mk21_sort_order`, `mk21_buffer_mode`, `mk21_stream_format`, `mk21_playback_quality`, `mk21_autosync`, `mk21_last_sync_<server>`, `mk21_fast_cache_<id>`, `mk21_fast_cache_time_<id>`, `mk21_fast_cache_universal`, `mk21_toast_mode`, `mk21_show_toasts`, `mk21_font_size`, `mk21_timezone`, `mk21_use_tmdb`, `mk21_speed_test_endpoint`, `mk21_ota_app_js`, `mk21_ota_app_version`, `mk21_ota_styles_css`, `FBG2_PLAYER_LEGACY`, `FBG2_PLAYER_ENGINE`.
+
+**Smart TV — IndexedDB**: fast cache + catalog index (`createCatalogIndexWriter`, `queryCatalogIndexPage`, `searchCatalogIndex`, `queryIndexedFavorites`).
+
+### C.5 API surface (`api/`)
+
+**`api/proxy.js`** — Vercel serverless. Route: `GET|HEAD /api/proxy?url=<encoded>`, plus `OPTIONS` preflight.
+
+| Aspect | Behaviour |
+|---|---|
+| Origin allowlist | `bgstreaming*.vercel.app`, `localhost`, `127.0.0.1`, `capacitor://localhost`, plus `PROXY_ALLOWED_ORIGINS` and same-host HTTPS (`x-forwarded-host`/`host`) |
+| Cross-site without Origin | Rejected when `sec-fetch-site: cross-site` (403) |
+| Methods | `GET`, `HEAD`, `OPTIONS` only (405 otherwise) |
+| Rate limit | 3000 req/min/IP in memory, keyed on `x-real-ip` → socket → `x-forwarded-for` (429) |
+| Anti-SSRF | `isPrivateHost()` blocks `localhost`, `*.local`, `*.internal`, `*.arpa`, non-canonical numeric hosts (decimal/hex/octal/leading-zero); `isPrivateIpv4()` blocks RFC1918, CGNAT, loopback, link-local, IETF/TEST-NET, benchmark, multicast+; `isPrivateIpv6()` blocks `::`, `::1`, ULA `fc00::/7`, link-local, site-local, multicast, IPv4-mapped/compatible, 6to4 `2002::/16`, NAT64 `64:ff9b::/96` |
+| DNS-rebinding pin | `resolveAndValidateHost()` resolves **all** A/AAAA records and rejects if any is private; `buildPinnedLookup()` pins the socket to the validated IP |
+| Redirects | Followed manually, max 6, each hop re-validated (protocol + DNS) |
+| Protocols | `http:`/`https:` only |
+| Timeouts | 25 s upstream (`proxyReq.setTimeout`) → 504 |
+| Streaming | Pipes the upstream body (`proxyRes.pipe(res)`) — chunked, no buffering |
+| Headers forwarded | `Range`, `If-Range` (CR/LF stripped); UA spoofed to Chrome 115 |
+| Cache policy | `no-store` for account-bearing/playlist URLs; `public, max-age=86400` for static VOD (`mp4/mkv/avi/mov` or `/movie/`/`/series/`); `no-cache` otherwise |
+| XSS neutralization | Upstream `text/html`, `application/xhtml+xml`, `image/svg+xml` are rewritten to `text/plain; charset=utf-8` + `Content-Security-Policy: sandbox` + `Content-Disposition: attachment` |
+| Exported for tests | `createProxyHandler`, `isAllowedOrigin`, `isPrivateIp`, `isPrivateHost`, `resolveAndValidateHost` |
+
+**`api/license/activate.js`** — `POST {code, deviceId}` → validates against Neon `orders` (expiry, seats), blocks revoked devices, upserts `licenses`, returns `{token, tier, expiresAt}` where `token = MKL1.<base64url(payload)>.<base64url(ECDSA-SHA256 sig)>`. Errors: 400 (fields/JSON), 404 (unknown code), 403 (expired/revoked), 409 (seats exhausted), 413 (body > 16 KB), 500.
+**`api/license/refresh.js`** — `POST {deviceId}` → checks the `licenses` row (not revoked, not expired), bumps `last_seen`, re-signs with the stored expiry, returns the same shape.
+**`api/license/_token.js`** — shared helpers: `buildPayload`, `signToken`, `readJsonBody` (16 KB cap), `sendJson` (no-store + nosniff).
+
+---
+
+## D. Build & test contracts (must keep passing)
+
+### D.1 Commands
+
+```bash
+# Web mirror sync (root → web/ → public/)
+npm run sync:web          # node scripts/sync-web.mjs
+npm run check:web         # node scripts/sync-web.mjs --check   (CI gate)
+
+# TV bundle
+npm run build:tv          # node esbuild.config.mjs  → smart-tv/mk21-tv/app.js (< 350 KB)
+
+# TypeScript player layer
+npm run typecheck         # tsc --noEmit (strict)
+npm test                  # jest src/players
+
+# TV package
+python3 smart-tv/package_ipk.py
+
+# Release manifests
+npm run verify:release    # node scripts/verify-release-manifests.mjs
+
+# Node / Python test suites
+node --check smart-tv/mk21-tv/app.js
+node --check api/proxy.js && node --check web/api/proxy.js
+node --test smart-tv/tests/*.test.js
+python3 -m unittest discover -s smart-tv/tests -v
+
+# Android
+./gradlew testDebugUnitTest --no-daemon
+./gradlew assembleDebug --no-daemon
+```
+
+### D.2 `sync-web.mjs` mirroring rules
+
+- Canonical list = `SINGLE_FILES` (14 entries) + every file under `css/` and `js/`.
+- Mirrors = `web/` and `public/`.
+- Comparison = SHA-256 of file contents.
+- `--check` mode: exits 1 and prints each `Ausente no espelho:` / `Divergente do arquivo canônico:` line.
+- `public/assets/aistudio/` is never written or deleted.
+- Currently **in sync**: `[sync-web] OK: 20 arquivos sincronizados entre raiz, web/ e public/`.
+
+### D.3 CI — `.github/workflows/validate.yml` (runs on push/PR to `main`)
+
+| Step | Command |
+|---|---|
+| TS + Jest | `npm ci --no-audit --no-fund && npm run typecheck && npm test` |
+| Remote manifest verification | `node scripts/verify-release-manifests.mjs --apk mk21.apk --ipk smart-tv/mk21play_<ver>_all.ipk --remote` (skipped for fork PRs without network) |
+| Keystore prep | Validates `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS` must be `upload`, `LICENSE_SECRET` ≥ 16 chars, `LICENSE_PUBKEY_B64` non-empty; derives `EXPECTED_CERT_SHA256` |
+| Android (PR) | `./gradlew testDebugUnitTest --no-daemon` (with one retry) then `./gradlew assembleDebug --no-daemon` |
+| Android (main) | `./gradlew assembleRelease --no-daemon` when signed, else `assembleDebug` |
+| Package + stamp | `npm run build:tv` → `python3 smart-tv/package_ipk.py` → `node scripts/sync-web.mjs`; verifies the APK is **not** debug-signed when secrets exist (`apksigner verify --print-certs`) |
+| Inline Python check | Asserts `apkSha256`/`sizeBytes`/`ipkSha256`/`ipkSizeBytes` match the real binaries across the 8 version manifests (excluding `smart-tv/mk21-tv/version.json`, which cannot contain its own IPK hash) |
+| Smart TV + version sync | `node scripts/sync-web.mjs --check`; `node --check smart-tv/mk21-tv/app.js`; `node --check api/proxy.js`; `node --check web/api/proxy.js`; `node --test smart-tv/tests/proxy-security.test.js smart-tv/tests/alternative-sources.test.js smart-tv/tests/catalog-utils.test.js smart-tv/tests/release-manifest.test.js`; `node scripts/verify-release-manifests.mjs --apk … --ipk …`; `python3 -m py_compile smart-tv/package_ipk.py`; `python3 -m unittest discover -s smart-tv/tests -v` |
+| Version coherence | 8 `version.json` mirrors must agree on `version`/`versionCode`/`versionName`/`apkSha256`; `manifest.json`, `web/manifest.json`, `public/manifest.json`, `smart-tv/mk21-tv/appinfo.json`, `smart-tv/webos/appinfo.json` must agree on `version`; `smart-tv/tizen/config.xml` `version` attribute; `app/build.gradle.kts` `versionCode`/`versionName` |
+| IPK internal version | `dpkg-deb --field "$IPK" Version` must equal `TV_VER` |
+| Checksums | `sha256sum` over `mk21.apk`, the IPK and the 8 manifests → `SHA256SUMS` |
+| Artifact upload | `generated-apps` artifact with `if-no-files-found: error` |
+
+### D.4 Release-manifest invariants (`scripts/verify-release-manifests.mjs`)
+
+- `version.json.version`/`versionCode` must equal `app/build.gradle.kts` `versionName`/`versionCode`.
+- All 8 version mirrors must agree on version, code and `apkSha256`.
+- `apkSha256` and `sizeBytes` must match the actual `mk21.apk` (fail-closed).
+- `ipkSha256`/`ipkSizeBytes` must match the actual IPK when `--ipk` is given.
+- `apps.json`, `smart-tv/apps.json`, `web/apps.json`, `public/apps.json` must all declare the same version, equal `sourceUrl`/`manifest.ipkUrl`, a 64-hex `manifest.ipkHash.sha256` equal to the real IPK, and (with `--require-release-url`) a GitHub **Release** URL rather than the raw `main` URL.
+- `--remote` mode downloads and re-hashes the published assets; a 404 on a release URL prints the exact `git tag v… && git push origin v…` command.
+- `parseGradleVersions()` and `verifyAppsManifests()` are exported for unit tests.
+
+### D.5 Test inventory
+
+| Suite | Count | Coverage |
+|---|---|---|
+| `smart-tv/tests/alternative-sources.test.js` | 6 | tvg-id grouping, VOD separation, name/category fallback, URL dedup, cross-server matching, negative cases |
+| `smart-tv/tests/catalog-utils.test.js` | 4 | catalog metrics, safe JSON parsing (HTML/401/403), friendly error text, sync-frequency normalization + legacy aliases |
+| `smart-tv/tests/license-api.test.js` | 4+ | ECDSA token round-trip (escaped PEM), activate validation matrix, seat/reinstall rules, refresh revocation |
+| `smart-tv/tests/proxy-security.test.js` | 5 | private-host blocking (v4/v6/mapped/decimal/hex), DNS-rebinding, CORS allowlist, 403 without leak, XSS neutralization |
+| `smart-tv/tests/release-manifest.test.js` | 9 | manifest coherence, hash mismatch, versionCode divergence, 404 tag hint, HEAD→Range fallback, apps.json hash/version, release-URL enforcement |
+| `smart-tv/tests/test_package_ipk.py` | (unittest) | IPK structure, reproducible builds, appinfo validation |
+| `src/players/__tests__/*.test.ts` | 3 files | `PlayerFactory`, `TizenAVPlayPlayer`, `HLSFallbackPlayer` |
+| `app/src/test/**` | 8 files | `IptvRepositoryTest`, `ServerTextParserTest`, `LicenseTokenVerifierTest`, `LocalServerSecurityTest`, `Mk21UpdateSecurityTest`, Robolectric + Roborazzi screenshot tests |
+
+---
+
+## E. Gaps vs. a "full-featured IPTV app" (factual observations)
+
+**Platform coverage of capabilities**
+
+| Capability | Web | Smart TV | Android |
+|---|---|---|---|
+| Multi-server | ✅ 7 predefined + `servers.json` sync + manual URL | ✅ full manager (add/edit/delete) | ✅ predefined + dynamic fetch + plain-text import |
+| Xtream API catalog | ✅ | ✅ | ⚠️ M3U only (`get.php`), no `player_api.php` catalog |
+| EPG | ✅ real short EPG + **full XMLTV guide** + simulated fallback | ✅ real XMLTV match + guide modal + simulated fallback | ⚠️ short EPG **preview dialog only**; no full guide screen, no XMLTV |
+| Favorites | ✅ | ✅ | ✅ |
+| Continue Watching / VOD resume | ✅ (both list and per-second resume) | ✅ | ✅ |
+| Search | ✅ (in-catalog) | ✅ Omnisearch (cross-server) | ✅ |
+| Parental control / adult PIN | ❌ **absent** | ✅ PIN modal + adult category lock | ✅ PIN + category-level prompt |
+| License / activation flow | ❌ **absent** | ❌ **absent** | ✅ ECDSA token activation + refresh + offline verify |
+| AI assistant | ❌ **absent** | ❌ **absent** | ✅ Gemini Copilot |
+| Server-side EPG proxy | ✅ via `/api/proxy` | ✅ own proxy | ✅ `Mk21ProxyStation` + `LocalHttpServer` |
+| TMDB metadata | ❌ | ✅ optional toggle | ❌ |
+| Sleep timer / speed / audio boost | ✅ all three | ⚠️ speed only | ⚠️ speed + sleep timer (no Web-Audio boost equivalent) |
+| Multi-source / failover | ⚠️ proxy rotation only, no per-channel alternate URLs | ✅ `alternateUrls` + cross-server failover | ✅ `alternateUrls` + per-server source dialog |
+| OTA update | ⚠️ PWA install only | ✅ in-app OTA (`app.js`/`styles.css`) | ✅ signed APK OTA with cert pin |
+
+**Concrete gaps**
+
+1. **Web has no parental control.** No PIN, no adult-category lock — the Android PIN heuristic (`M3UParser.isAdultContent`) and the TV's `mk21_adult_pin` have no web equivalent. The web also has no adult-content detection at all.
+2. **Web and TV have no licensing/activation path.** `api/license/*` exists and is tested, but only the Android client calls it (`AppViewModel.activateLicense`). The web settings modal's "account" tab shows **hardcoded** placeholder values (`601334065`, `15/03/2027` — `index.html:1460`, `:1476`), not live server data.
+3. **Android has no full EPG guide.** It renders a per-channel short-EPG preview dialog (`ChannelEpgPreviewDialog`) but has no XMLTV grid/guide screen and no XMLTV download.
+4. **Android does not use the Xtream JSON catalog.** It downloads M3U via `get.php` only, so per-series episode metadata (`get_series` details), ratings and container extensions are approximated by filename heuristics rather than the API.
+5. **Series grouping is heuristic on every platform.** Web (`groupSeriesItems`) and Android group by parsing the title; no platform consumes `player_api.php?action=get_series` episode lists. Web does call `get_series` for the card list, but episodes still come from the M3U.
+6. **No cloud sync.** Favorites, continue-watching, progress and preferences are strictly local per platform (`localStorage` / `sessionStorage` / IndexedDB / EncryptedSharedPreferences / TV `localStorage`). Moving from phone → TV → web loses all state.
+7. **Multi-user / profile switching is minimal.** Each platform stores a single credential set (the web additionally keeps a flat `mk21_saved_profiles` list of quick logins). There is no account/profile abstraction, no per-user favorites, no "who's watching".
+8. **No catch-up / TVoArchive / recording.** No archive playback window, no timeshift, no DVR/recording, no scheduled recording.
+9. **No downloads / offline media on Android and TV.** Web can load a local M3U file, but nothing persists media for offline playback.
+10. **No subtitles/audio-track manifest handling on Android beyond the player sheet.** No `.srt`/`.vtt` sidecar support, no forced-subtitle preference per channel.
+11. **No channel-number mapping.** The web and TV index channels by list order; there is no editable channel number (LCN) assignment, and no EPG-based "jump to currently-airing program" feature.
+12. **Diagnostics are developer-grade but not user-facing.** Web (`#diagnostic-log-console`), TV (`#modalDevLogs`) and Android (`CrashLogger`) exist, but there is no unified "report a problem" flow, and the web's remote-log path exposes only a copy/download of the console text.
+13. **Web accessibility/performance/SEO modules are partially wired.** `js/accessibility.js` exposes `setupContrastChecker()` and `setupTextSizeAdjustment()` but `init()` never calls them; `js/performance.js` exposes `createVirtualScroller()` but the web grid uses a manual `itemsLimit` + `IntersectionObserver` "load more" instead. `js/seo-meta.js` sets a `data:`-URI manifest that shadows the real `manifest.json` only if `setupManifest()` is called — `init()` does not call it.
+14. **TV `playerEngine` is published as `"legacy"`.** `version.json` ships `"playerEngine": "legacy"`, so the TypeScript Player Factory is off by default everywhere until the flag flips (see `src/players/README.md`).
+15. **No remote-control "voice" on web/Android**, no picture-in-picture on TV (only web + Android have PiP).
+16. **`apps.json` catalog covers only the webOS IPK.** There is no Tizen `.wgt` entry and no Android `.apk` entry in the store catalog — Tizen is packaging-only (`smart-tv/tizen/config.xml`).
+17. **Web "Continue Watching" and TV "Continue Watching" are different implementations** of the same concept, with different storage keys and semantics (web caps at 12 items; TV stores per-`contentId` progress).
+
+---
+
+## F. Code conventions
+
+### F.1 JavaScript (web + TV)
+
+- **No build step for the web layer.** Root `index.html` is hand-authored; `js/*.js` are plain `<script>` files (not ES modules) that expose a single global each: `accessibilityManager` (`js/accessibility.js:294`), `performanceOptimizer` (`js/performance.js:206`), `seoManager` (`js/seo-meta.js:246`), and an IIFE that exposes `window.mk21Remote` (`js/tv-remote-and-pwa.js:794`).
+- **Naming**: camelCase functions; snake_case for some legacy locals (`active_category`); PascalCase for object literals acting as namespaces (`EpgManager`, `MK21_CONFIG`); SCREAMING_SNAKE for constants (`CACHE_NAME`, `PROXY_ROTATION_LIST`, `MAX_REQUESTS_PER_MINUTE`).
+- **Global exposure**: legacy globals are attached to `window` explicitly (`window.playStream`, `window.closeMobilePlayer`, `window.EpgManager`, `window.MK21_CONFIG`) so the inline HTML `onclick=` handlers can reach them. The TV uses the `mk21`-prefixed convention (`mk21Ensure…`, `mk21MoveFocus`, `mk21RenderAllDevLogs`).
+- **DOM access**: `document.getElementById` in the web layer; a `$('id')` helper in the TV layer.
+- **XSS discipline**: web escapes with `escapeHtml()` (`index.html:1640`) and validates image URLs with `sanitizeImageUrl()` (`:1646`); the TV uses `escapeHtml()` (`app-legacy.js:3825`) and the PlayerOverlay uses `textContent` only. `innerHTML` is used for template literals but always with escaped interpolation.
+- **Comments**: dense Portuguese section banners (`// ==========================================`, `// 1. PWA SERVICE WORKER…`) and JSDoc in `js/` and `api/`; English is used in newer `src/players/` TypeScript and in some `api/license` files.
+- **Error handling**: `try/catch` with `console.warn`/`console.error`; user-facing errors go through `showNotification(message, 'error')`.
+
+### F.2 TypeScript (`src/players/`)
+
+- Strict mode (`tsconfig.json`, `npm run typecheck` → `tsc --noEmit`).
+- `IPlayer` interface is the contract every player implements (`src/players/types/player.ts:84`); normalized events: `ready | playing | paused | buffering | buffered | ended | error | tracksReady | timeupdate | durationchange | destroyed`.
+- Time is in **milliseconds** at the factory boundary; the legacy TV UI speaks seconds — `player-service.js` converts (`seekMs`, `getCurrentTimeSec`).
+- `BasePlayer` provides the event emitter + state + buffer accounting; `PlayerFactory.detectPlatform()` returns `tizen | webos | browser`.
+- Logging is gated behind `FBG2_DEBUG` (`src/players/utils/logger.ts`).
+- Tests colocated in `src/players/__tests__/*.test.ts`, run by Jest (`jest.config.js` → `preset: ts-jest`, `roots: ['<rootDir>/src/players']`, `testEnvironment: jsdom`).
+
+### F.3 TV bundling (`esbuild.config.mjs`)
+
+- Entry: `smart-tv/mk21-tv/src/main.js`; output: `smart-tv/mk21-tv/app.js`.
+- `format: 'iife'`, `target: ['es2019']`, `minify: true`, `sourcemap: false`, `legalComments: 'none'`, `charset: 'utf8'`.
+- `alias: { 'hls.js': 'smart-tv/mk21-tv/src/shims/hls-global.js' }` — keeps hls.js out of the bundle (the TV loads `libs/hls.min.js` at runtime).
+- `define: { 'process.env.NODE_ENV': '"production"' }`.
+- **Hard budget: 350 KB** — the build prints the size and `process.exit(1)` if exceeded. This is why `src/`, `tests/` and `node_modules/` are excluded from the IPK payload (`smart-tv/package_ipk.py:334-336`) and why the OTA ships only `app.js` + `styles.css`.
+- The banner comment on `app.js` states: *"MK21 Play - app.js gerado por `npm run build:tv`. Nao editar: o fonte esta em smart-tv/mk21-tv/src/."* — **never hand-edit `app.js`**.
+- Module order in the bundle matters: `player-service.js` body runs first and publishes `window.FBG2PlayerService`; then `legacy/app-legacy.js` initializes; then `main.js` schedules the remote flag refresh (1500 ms after boot).
+
+### F.4 Kotlin (Android)
+
+- Packages: `com.example.*` (app) and `com.fbg.streaming.*` (hardening modules) — two coexist by design.
+- Data layer: `interface IptvRepository` + `IptvRepositoryImpl` wrapping Room DAOs; all reads return `Flow<List<…>>`; writes are `suspend`.
+- State: `private val _x = MutableStateFlow(...)` + `val x: StateFlow<…> = _x.asStateFlow()`; one-off events use `MutableSharedFlow`.
+- Compose: single-file UI layer `ui/Screens.kt` (9527 lines) with top-level `@Composable fun`s (`AppNavigation`, `ServerConfigScreen`, `HomeScreen`, `VideoPlayerUI`, `SettingsScreen`, `AdultPinDialog`, `ChannelEpgPreviewDialog`, `HighlightBanner`, `GroupedSeriesCard`, `GridItemCard`, `SmartTvCastDialog`); theme in `ui/theme/{Color,Theme,Type}.kt`.
+- Resources: `strings.xml` for the app name; most UI strings are inline PT-BR literals (a known inconsistency).
+- Security: `EncryptedSharedPreferences` only; secrets never logged; the settings screen redacts credentials in exported backups via regexes (`AppViewModel.kt:1895+`).
+- Tests: JUnit4 + Robolectric + Roborazzi (screenshot) + `kotlinx-coroutines-test`; `testOptions { unitTests { isIncludeAndroidResources = true } }`.
+
+### F.5 Python
+
+- `smart-tv/package_ipk.py` — stdlib only (`gzip`, `tarfile`, `hashlib`, `struct`, `json`, `math`, `shutil`).
+- Reproducible builds: fixed `DEFAULT_BUILD_TIMESTAMP = 1735689600` (2025-01-01), overridable via `SOURCE_DATE_EPOCH`; all tar members get normalized uid/gid 0, uname/gname `root`, and mode `0o644`/`0o755`.
+- `validate_appinfo()` raises `ValueError` listing **all** problems before packaging (id format, semver, required fields, main file exists, exact PNG dimensions for `icon` 80×80 / `largeIcon` 130×130 / `splashBackground` 1920×1080).
+- Manifest stamping helpers: `stamp_version_manifests()`, `stamp_apps_manifests()`, `stamp_apk_manifests()` — each skips manifests that declare a different version, so a stale manifest can never advertise the current build.
+- GNU-AR assembly of `debian-binary` + `control.tar.gz` + `data.tar.gz`; the archive member timestamp must not be epoch 0 or webOS's pkgverifier fails with `-5: ipk verified failed`.
+
+### F.6 JSON / manifests
+
+- `version.json` is the release hub: `version`, `versionCode`, `tagName`, `platforms{web,android,webos,tizen}`, `releaseDate`, `releaseNotes`, `body`, `apkUrl`, `downloadUrl`, `ipkUrl`, `apkSha256`, `ipkSha256`, `sizeBytes`, `ipkSizeBytes`, `sha256`, `versionName`, `changelog`, `playerEngine`.
+- `apps.json` (webOS store catalog format): `packages[0]` with `id`, `version`, `type`, `title`, `appDescription`, `category`, `icon`, `sourceUrl`, and a nested `manifest{id,version,type,title,icon,largeIcon,ipkUrl,ipkHash.sha256,vendor}`.
+- `manifest.json` (PWA): `name`, `short_name`, `version`, `start_url`, `display: standalone`, `background_color`, `theme_color`, icons (192/512/SVG).
+- `metadata.json` (Android TV / Google TV): `name`, `description`, `requestFramePermissions: []`, `majorCapabilities: ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]`.
+- All JSON is 2-space indented; `servers.json` is an array of `{id, name, baseUrl, lotusCode, webCode}`.
diff --git a/docs/SAIMO-TV-FEATURE-INVENTORY.md b/docs/SAIMO-TV-FEATURE-INVENTORY.md
new file mode 100644
index 0000000..bb49f55
--- /dev/null
+++ b/docs/SAIMO-TV-FEATURE-INVENTORY.md
@@ -0,0 +1,1072 @@
+# Saimo TV (Web) — Exhaustive Feature Inventory
+
+**Repo:** `/workspace/work/Saimo-Site` · **Version:** `3.2.3` (`package.json`) · **Locale:** pt-BR
+**Scope of this report:** every user-visible behaviour of the React web app (`src/`), the serverless
+functions (`functions/`, `api/`), the TV packaging (`tv/`, `tv-native/`) and the PWA shell.
+
+> Terminology follows the codebase (Portuguese). File references are `path:line`.
+
+---
+
+## A. Architecture summary
+
+### A.1 Stack
+
+| Layer | Choice | Notes |
+|---|---|---|
+| UI | React **19.3** + TypeScript **~6.0** | Function components, `memo`, `createPortal`, `useSyncExternalStore` |
+| Build | **Vite 8** | Two build modes: normal (web) and `--mode tv` (TV package) |
+| Router | **react-router-dom 7.18** | `BrowserRouter` on the site, `HashRouter` inside the desktop shell |
+| Players | **hls.js 1.7.3**, **mpegts.js 1.8.2**, **shaka-player 5.2.12** (lazy chunk) | HLS / MPEG-TS / DASH-ClearKey |
+| Desktop shell | `@tauri-apps/api` 2.11 (+ updater, process) | Tauri app reuses the same `src/` |
+| Backend | **Cloudflare Pages Functions** (`functions/`) + **Vercel** (`api/proxy.ts`) | `/api/proxy`, `/api/resolve-vod`, `/api/stream`, `/api/events`, `/api/online/*`, download shortcuts |
+| Tests | `node:test` + `node:assert/strict` (`.mjs`, run against `.ts` sources) | 8 test files under `tests/` |
+| Styling | Plain CSS files per component (`*.css` imported from `.tsx`) | No CSS-in-JS, no Tailwind |
+
+### A.2 Entry points
+
+1. **`src/main.tsx` — the web/PWA app.** Order of operations is deliberate:
+   `import './perfis/instalar'` (profile key swap **before any module reads storage**) →
+   `migrarUmaVez()` (one-shot wipe of stale list caches) → `vigiarExtensoes()` (capture-extension blocker) →
+   `iniciarTv()` → `iniciarBrilho()` → `sondarProxy()` probe (repeats every 10 min) →
+   `iniciarTelemetria()` → **render gated on the probe resolving, max 1.5 s** → `createRoot`.
+   Service worker (`/sw.js`) registers only when `PROD && !import.meta.env.VITE_TV && location.protocol === 'https:'`.
+
+2. **`src/entrada-tv.ts` — the TV package entry.** Imports `core-js/stable`, `whatwg-fetch`,
+   `abortcontroller-polyfill`, `intersection-observer`, `./plataforma/polyfills-tv`, then `./main`.
+   Built by `configTv` in `vite.config.ts:52` into a single **IIFE** (`saimo.js`, `saimo.css`),
+   `target: 'chrome68'`, `cssTarget: 'chrome61'`, `define: VITE_TV = "1"`, `outDir: dist-tv`.
+
+3. **`tv-native/src/main.ts` — a *separate*, framework-free native TV app** (Tizen/webOS), written in
+   TypeScript with its own `SpatialNav` engine, `State` store, `Api` layer and screens
+   (`LiveScreen`, `HomeScreen`, `OnDemandScreen`, `FichaScreen`, `PlayerScreen`, `GuideScreen`,
+   `EventsScreen`, `RadiosScreen`, `SearchScreen`, `SettingsScreen`, `FavoritesScreen`,
+   `MinhasListasScreen`, `AddonsScreen`, `DoacaoScreen`, `NovelinhaScreen`, `PerfisScreen`).
+   It **shares** `src/perfis/*` and `src/plataforma/brilho.ts` with the web app.
+   Packaged by `scripts/montar_tv.sh` → `SaimoTV-Samsung.wgt` (Tizen) and `SaimoTV-LG.ipk` (webOS),
+   transpiled to ES5 by `scripts/tv_es5.mjs`.
+
+4. **`tv/comum/carregador.js`** — ES5 loader shipped inside the TV package. On boot it asks the site
+   for `tv/manifesto.json`; if a newer build exists it downloads and runs that code **inside the
+   installed app** (keeping the installed privileges: `http` video, no CORS, remote keys); if the
+   network fails or the new code errors it falls back to the copy inside the package. Reports
+   crashes to `/api/online/v1/event` as `text/plain`.
+
+### A.3 Routing / screen structure
+
+Router selection (`src/App.tsx:741-745`): `BASE_URL === '/'` → `BrowserRouter` (clean URLs);
+otherwise (Tauri `tauri://`) → `HashRouter`. A legacy `#/path` URL arriving on the site is rewritten
+to a clean path at boot.
+
+| Route | Component | Purpose |
+|---|---|---|
+| `/` | `RaizDoSite` → `Landing` **or** `Inicio` | Landing unless `raizAbreOApp()` (TV, TV app, installed PWA, `VITE_TV`) |
+| `/home` | `Inicio` | Home screen (hero + rails) |
+| `/sobre` | `Landing` | Institutional page (also the old home of the landing) |
+| `/privacidade`, `/termos`, `/conteudo`, `/exclusao-de-dados`, `/suporte` | `Legal` | Legal pages |
+| `/formato` | `Formato` | "Saimo list format" documentation |
+| `/busca` | `Busca` | Universal search |
+| `/eventos` | `Eventos` | Sports agenda |
+| `/favoritos` | `Favoritos` | Channels + VOD favourites |
+| `/radios` | `Radios` | Radio screen |
+| `/ajustes` | `Ajustes` | Settings |
+| `/tv`, `/tv/:slug` | `TVPage` | Live TV (`:slug` deep-links a channel) |
+| `/movies` | `MoviesPage` | On Demand catalogue + player overlay |
+| `/app` | `AppDownload` | App downloads |
+| `/apoie` | `ApoiePagina` | Pix donation page (no bottom bar, no floating donation button) |
+| `/novidades` | `Novidades` | Catalogue release rounds |
+| `/teste` | `StreamTester` | **Only if `localStorage['saimo-dev'] === '1'`** |
+
+Cross-cutting mounts in `AppLayout`:
+- `EscolhaPerfil` — profile chooser overlay, rendered **only on app screens**
+  (`TELAS_DO_APP = ['/home','/tv','/movies','/busca','/eventos','/favoritos','/radios','/ajustes']`).
+- `NavegacaoMobile` — bottom tab bar (Início / Ao vivo / Eventos / Filmes e séries / Baixar app).
+- `DoacaoModal` — floating draggable donation button + modal (both hidden on `/apoie`).
+- Every data screen is keyed by `` `${canais}|${filmes}` `` so changing a list address **remounts**
+  the screen and drops the previous list's titles/channels from memory (`src/App.tsx:647-648`).
+
+### A.4 State model
+
+No global store library. Four patterns coexist:
+
+1. **React state in the screen** (`TVPage`, `MoviesPage`, `VodCatalog` — the latter has 44 `useState`s).
+2. **Service-level external stores** consumed with `useSyncExternalStore`:
+   `useListas()` (list addresses), `useAddons()`, `useTemAddons()`, `useDebridAccounts()`,
+   `useHasDebrid()`, `assinar/versaoAtual` (profiles), `radioPlayer.assinar`.
+   Events: `saimo-listas-mudaram`, `saimo-addons-mudou`, `saimo-debrid-mudou`,
+   `saimo-minhas-listas-mudou`, `saimo-canais-personalizados-mudou`, `saimo-vod-favoritos-mudou`,
+   `saimo-zero-delay-mudou`, `saimo-audio-inteligente-mudou`, `saimo-upscaling-mudou`,
+   `saimo-abrir-modal-doacao`, `saimo-abrir-perfis`.
+3. **Caches**: `localStorage` (text/JSON), **IndexedDB** (`saimo-minhas-listas` → store `canais`, for
+   user M3U channel payloads that exceed the 5 MB localStorage quota), in-memory `Map`s.
+4. **Module singletons** for side-effecting subsystems: `castService`, `zeroDelayService`,
+   `audioInteligenteService`, `radioPlayer`, `epgService` (worker + `porCanal` map).
+
+### A.5 TV vs mobile / desktop code paths
+
+**Mobile vs desktop layout** (`src/App.tsx:40-43`): `ehCelular()` = `innerWidth <= 768` **or**
+(`pointer: coarse` **and** `innerHeight <= 500`). Mobile: the player is on top and the channel list
+scrolls below it (`.mobile-content` / `.mobile-player` / `.mobile-canais`); a floating channel drawer
+appears in theater mode or landscape. Desktop: a macOS-style stage (`.stage-aovivo-mac`) with a
+glass channel panel on the side.
+
+**TV detection** (`src/plataforma/tv.ts`):
+- `marcaTv()` → `'tizen' | 'webos' | null` — from `window.__SAIMO_TV__` (set by the package loader)
+  or UA (`Tizen`, `Web0S|webOS|NetCast`) or vendor globals (`window.tizen`, `window.webOS`, `window.PalmSystem`).
+- `ehTv()` — any TV (browser or package). `ehAppDaTv()` — **only the installed package**, which is
+  what unlocks `http` video and no-CORS playback.
+- `iniciarTv()` adds `modo-tv tv-tizen|tv-webos` classes, applies 4K zoom
+  (`1.6` at ≥3200 device px, `1.3` at ≥2200, else `1`) to `document.documentElement`,
+  registers Samsung media/colour keys (`tizen.tvinputdevice.registerKey`) and installs the key translator.
+- **Remote key translation** (`src/plataforma/tv.ts:60-115`): keyCodes `37/38/39/40` → arrows, `13` →
+  Enter; `10009|461|27` → Escape (and, if nothing handles it, exit the app — with a
+  "press Back again to exit" toast, 2.5 s window); `415|19|10252|413` → play/pause/play-pause/stop
+  mapped onto the current `<video>`. Translated keys are re-dispatched as synthetic `KeyboardEvent`s
+  so the whole app sees one key vocabulary.
+- `hlsNativo()` — true when the TV's own decoder can play HLS (`canPlayType('application/vnd.apple.mpegurl')`).
+
+**D-pad / remote navigation model:**
+- `src/contexts/dpadContexto.ts` — context, `useDpad()`, `useFocusable()` (marks
+  `data-focusable="true"` + `tabindex`).
+- `src/contexts/DpadContext.tsx` — `DpadNavigationProvider`. Key behaviours:
+  - **Focusable selector differs by platform**: on TV everything focusable counts
+    (`button`, `a[href]`, `[role=button|tab|menuitem]`, `input`, `select`, `textarea`,
+    `[tabindex]:not([tabindex="-1"])`, plus `[data-focusable="true"]`); on desktop only
+    `[data-focusable="true"]` (and not disabled / `aria-hidden`).
+  - **Spatial movement**: Euclidean distance with the main axis weight 1 and the cross axis weight 3,
+    plus a −1000 alignment bonus proportional to the overlap in the perpendicular axis.
+  - **Navigation zones** (`NAVIGATION_ZONES`): sidebar ↔ player ↔ mobile; crossing a zone boundary
+    fires a `dpad-enter` custom event on `.video-player-container` so the player's controls appear.
+  - **Dialog scoping**: `janelaAberta()` finds the topmost visible `[role=dialog]`/`[aria-modal=true]`;
+    on TV focus is confined to it, and a `MutationObserver` moves focus into a newly opened dialog
+    (`[data-autofocus]`, `.vod-btn-assistir`, `button.primario`, else first focusable) and restores
+    the previous element on close (a focus stack).
+  - **Auto-focus safety net on TV**: 1.5 s after each route change, if the focused element is gone,
+    focus the first focusable.
+  - `rolarAteVer()` scrolls the *nearest horizontally-scrolling ancestor* first (rows of posters),
+    then the page — keeping the focused card inside the row's own scroll box.
+  - **Keys**: Arrows move (100 ms debounce), `Enter`/`Space` click the focused element, `Esc`/`Backspace`
+    trigger the **back-handler stack** (newest first); if no handler consumes it, `navigate(-1)`
+    unless already on `/` or `/home`.
+  - **Gamepad support**: `requestAnimationFrame` polling of `navigator.getGamepads()`, D-pad buttons
+    12–15, A=0 (click), B=1 (back), left analog with a 0.6 deadzone, 150 ms navigation debounce.
+    `navigator.getGamepads()` is always coerced to a plain array (old TV Chromium returns a
+    `GamepadList` without `.some`).
+- `src/hooks/useDpadNavigation.ts` — a **second, standalone** implementation of the same idea
+  (not used by the provider; kept as a reusable hook: `moveFocus`, `focusFirst`, `focusLast`,
+  `focusElement`, optional `enableLoop`, `registerGlobalBackHandler`).
+- `src/hooks/useControleTv.ts` — TV-only remote semantics inside "Ao vivo":
+  - `CH+` (Samsung 427 / LG PageUp 33) → next channel; `CH−` (428 / 34) → previous channel.
+  - From a channel row, `ArrowRight` jumps directly to the player buttons (skips the row's tabs).
+  - From the first player button, `ArrowLeft` returns to the channel list.
+  - When the list is closed and controls are hidden: `ArrowUp/Down` = next/previous channel,
+    `ArrowLeft/Right/Enter` = open the channel list (focus jumps to the on-air channel).
+  - Back handler: close the channel panel → else leave multi-screen → else `navigate(-1)` if there is
+    history, else `/home` (never exits the app).
+  - When multi-screen is enabled, focus jumps to the channel list after 300 ms so the user can pick
+    the channel for Screen 2.
+- `src/hooks/useVoltarFecha.ts` — **layered back**: each open overlay pushes a history entry tagged
+  `saimoCamada: <n>`; a `popstate` closes the layer; closing via X/Esc pops the entry
+  (several simultaneous closes are batched into one `history.go(-n)`).
+- `src/hooks/useMediaSession.ts` — Media Session metadata (`title`, `artist`, `artwork`) and actions
+  `play/pause/seekbackward/seekforward/nexttrack/previoustrack`. Live TV maps **pause → resume**
+  (live never pauses).
+- `src/hooks/useKeyboardShortcuts.ts` — deliberately **empty**: desktop keyboard shortcuts are
+  disabled on the site (the `?` help overlay in `src/components/AtalhosAjuda.tsx` documents them but
+  the hook is a no-op).
+- `src/styles/dpad-navigation.css` — the visual focus ring (`.dpad-focused`).
+
+---
+
+## B. Feature inventory
+
+> `TV` = only/primarily for the Smart-TV path; `Shared` = web + TV; `Web` = web/browser only.
+
+### Live TV (Ao vivo)
+
+**1. Live channel grid with categories, search and progressive rendering** — Shared
+`src/components/PainelDeCanais.tsx`. Left glass panel listing channels: category pills
+(`Todos`, `Favoritos`, then `categoryOrder` then extras alphabetically), free-text search over
+name + category, grouped by category with uppercase headers, per-row channel number, logo tile with
+initials fallback, star (favourite) button. Renders in chunks of **40** with an
+`IntersectionObserver` sentinel (`rootMargin: 350px`) + scroll handler to grow the list — keeps weak
+TVs fluid. On open it scrolls to the on-air channel (`scrollIntoView`, 150 ms delay) and shows
+`active channel index` + total count. Right-click opens a context menu: **Escolher Fonte** submenu,
+**Favoritar/Remover dos favoritos**, **Abrir na Tela 1/2** (multi-screen).
+`src/data/categorias.ts` defines the fixed section order (TV Aberta, Filmes, Series, Esportes,
+Noticias, Infantil, Documentarios, Entretenimento, Pluto TV, 24 Horas, Internacionais, Adulto).
+
+**2. Multiple sources per channel + automatic failover** — Shared
+`src/types/channel.ts` (`ChannelSource`: `url`, `quality`, `name`, `referer`, `userAgent`, `keyId`,
+`key`), `src/utils/streamUrl.ts` (`buildAttempts`), `src/components/VideoPlayer.tsx:197-647`.
+Each published source can yield up to two attempts (direct, then via `/api/proxy`). On failure the
+player walks to the next attempt; a source that already played and dies is reopened once before
+switching. The failure policy is **"the clock decides, not the error counter"**: an error only kills
+the source if the video clock stopped advancing; otherwise the same player is resumed
+(`falhar()` vs `descer()`).
+
+**3. 12-second "no picture" watchdog and 25-second freeze watchdog** — Shared
+`src/components/VideoPlayer.tsx:374-490`. A `timeupdate` listener marks the source as "alive" only
+when the clock advances (`readyState >= 2 && t > 0.1 && t > ultimoTempo + 0.2`). If nothing has
+advanced for **12 s** the attempt is considered dead; if it had played and the picture freezes for
+**25 s** the source is dropped. Rationale in code: viewers prefer a long stutter over a source swap
+that restarts at zero.
+
+**4. Manual source selection ("Fontes do canal")** — Shared
+`src/components/VideoPlayer.tsx:224-281, 1511-1556`. A modal lists the **published sources** (not the
+internal attempts): shows `source.name` (e.g. "Temporada 3") or `Fonte N`, the quality, and badges
+`DRM` / `com Referer` / `HTTP` — **never the server or the raw URL**. The badge in the control bar
+shows the active source; right-click in the channel list offers the same list.
+`src/services/castService.ts` / `src/components/PainelDeCanais.tsx` (context menu) share it.
+
+**5. Channel navigation: number, previous/next** — Shared
+`src/App.tsx:222-234` (`handleNextChannel` / `handlePrevChannel`, wrap-around), channel numbers from
+the index in the channel list, `channelNumber` badge in the player.
+
+**6. Channel favourites** — Shared
+`localStorage['tv-favorites']` (`string[]` of channel ids) via `useLocalStorage`; toggled from the
+channel list star, the context menu, `ChannelCard`, and the Favorites screen. Toast feedback
+("… adicionado aos favoritos" / "… removido dos favoritos").
+
+**7. Multi-screen (two live channels, one with audio)** — Shared (Web full; TV simplified)
+`src/App.tsx:102-197, 318-477`. Desktop renders two `VideoPlayer`s side by side; the inactive side is
+`forceMuted`; each side shows a glass overlay with a volume icon, the channel name and an
+"Áudio ativo" badge; clicking a side activates its audio; each side has a close (✕) button.
+`PainelDeCanais` shows a **Tela 1 / Tela 2** selector at the top and "Abrir na Tela 2" in the
+context menu. Toasts: "Multi-Tela ativada: Escolha o canal da Tela 2 na lista".
+
+**8. "Now on air" overlay on the player** — Shared
+`src/components/ProgramInfo.tsx`. Floating panel over the video: progress bar of the current
+programme, "AGORA" badge with a pulsing dot, title, time range `HH:MM - HH:MM`, duration
+(`45min` / `2h15min`), category, rating, description, "A SEGUIR" next programme, an expand chevron
+revealing the next 5 programmes, and a **Guia** button. If there is no data: "CARREGANDO… /
+Buscando programação…" or "AO VIVO / Transmissão ao vivo / Sem informação de programação".
+Polls every 30 s and re-renders on every EPG update event.
+
+**9. On-air info in the channel list** — Shared
+`PainelDeCanais` row shows the current programme title + a thin progress bar; updated through
+`onEPGUpdate`. `src/components/CartaoNoAr.tsx` is the floating "on air" card used by the macOS-style
+layout (channel logo, number, title, time range, "faltam N min" / "terminando", "A seguir: …").
+
+**10. EPG grid (Guia de programação)** — Shared
+`src/components/ProgramGuide.tsx`. Full-screen modal: one scrollable element for both axes, the
+channel column and the time ruler are `position: sticky` (no JS scroll-sync). Range = **1 h in the
+past → 25 h ahead**, starting at a half-hour boundary. Ruler ticks every 30 min with the day label at
+midnight; a vertical "now" line and label. Channel column shows number, logo (initials fallback,
+broken logos cached in a module-level `Set`) and name. Programme blocks are absolutely positioned
+(`left`/`width` in px at 4 px/min desktop, 3 px/min mobile) with classes `no-ar`, `passou`,
+`estreito` (<44 px), `mini` (<84), `curto` (<128) so short blocks shed the time then the "Ao vivo"
+word. The on-air block has an internal progress bar. Clicking a block opens a **detail panel**
+(poster, channel, title, day + time range, duration, "Ao vivo"/"Encerrado" tags, progress bar,
+category and episode tags, synopsis) with an **"Assistir {canal}"** button. A search field filters
+channels by normalised name. "Agora" button re-centres on the current time. Only the visible window
+(quantised to 4-line / 60-minute margins) is rendered.
+
+**11. Zero Delay (low-latency live)** — Shared
+`src/services/zeroDelay.ts`, `src/utils/vigiaAoVivo.ts`, `src/components/VideoPlayer.tsx:1340-1373`.
+Three modes cycled from the player button or Ajustes:
+- `off` — default hls.js (`backBufferLength: 90`).
+- `equilibrado` — `liveSyncDurationCount 2.8`, `liveMaxLatencyDurationCount 5.5`,
+  `maxLiveSyncPlaybackRate 1.03`, `maxBufferLength 20`, stall increase 1.5 s.
+- `agressivo` (Ultra) — `liveSyncDurationCount 2.0`, `liveMaxLatencyDurationCount 4.0`,
+  `maxLiveSyncPlaybackRate 1.04`, `maxBufferLength 12`, `backBufferLength 15`.
+mpegts.js gets the equivalent `liveSync*` / `liveBufferLatencyChasing*` config; Shaka gets a
+`streaming.liveSync` block. A floating "Ao vivo" badge shows the estimated delay (`-3s PULAR`) and
+turns yellow when off the live edge; clicking it **jumps to the edge** (`pularParaBorda`, with a
+margin of 8.0/4.5/2.8 s by mode). The watchdog only acts on native (`video.src`) playback — with MSE
+the specialised player owns latency. It also re-syncs when the tab becomes visible again.
+Persisted in `localStorage['saimo-zero-delay']`.
+
+**12. "Áudio Inteligente" (dynamic range normalisation)** — Shared
+`src/services/audioInteligente.ts`. Web Audio graph: `MediaElementSource → DynamicsCompressor →
+GainNode → destination`. Enabled by default, toggle in Ajustes, persisted in
+`localStorage['saimo-audio-inteligente']`; connected to the video element in both players.
+
+**13. Multi-format playback: HLS / MPEG-TS / DASH + native HLS** — Shared
+`src/components/VideoPlayer.tsx:508-636`. Decision order:
+1. `nativo` (TV package with native HLS) → `video.src = url`;
+2. DASH (`.mpd`) → `playDash()` (`src/services/dashPlayer.ts`) with **Shaka Player loaded on demand**
+   (`shaka-player.compiled.js`), configuring ClearKey DRM from the catalog's `keyId`/`key`
+   (hex → base64url, `hexToBase64Url` in `src/utils/streamUrl.ts`);
+3. raw MPEG-TS (`.ts`) → **mpegts.js** (`{type:'mpegts', isLive:true, cors:true}`);
+4. HLS → **hls.js** with `enableWorker`, zero-delay config, and either
+   `fLoader: criarCarregadorDireto()` (proxy path) or `loader: makeNestedPathLoader(url)` (direct path,
+   fixes the "segments live in the `url=` folder" problem);
+5. Safari native HLS (`canPlayType('application/vnd.apple.mpegurl')`);
+6. otherwise "Seu navegador não suporta reprodução de vídeo."
+Fatal hls.js manifest errors (`MANIFEST_LOAD_ERROR/TIMEOUT/PARSING_ERROR`, `LEVEL_LOAD_ERROR`,
+`LEVEL_EMPTY_ERROR`) skip straight to the next source instead of retrying.
+
+**14. Audio-track and quality selection (live)** — Shared
+`src/components/VideoPlayer.tsx:564-600, 1375-1399, 1559-1621`. After `MANIFEST_PARSED`, hls.js
+`audioTracks` (>1) and `levels` (>1) are turned into menus. Quality list = "Automática" + one entry
+per level (`1080p (4500k)`). Audio list uses `track.name || track.lang || "Faixa N"`. A single gear
+button opens whichever menu applies; the active one is highlighted with "Em uso".
+
+**15. Resolution badge** — Shared
+`video.videoHeight` → `4K / 2K / 1080p / 720p / 480p / 360p / {h}p`, refreshed on `loadedmetadata`,
+`resize` and every 2 s.
+
+**16. Cast / "Transmitir para dispositivo"** — Shared
+`src/services/castService.ts` (889 lines) + the modal in both players. Methods are **filtered by the
+URL scheme**: only `http://` sources may leave the site — copy link, share, open in an external player,
+open in a new tab. HTTPS never leaves. Supported methods: Google Cast (Chromecast, SDK loaded lazily),
+AirPlay (Safari `webkitShowPlaybackTargetPicker`), Remote Playback API, Presentation API (smart TVs),
+Web Share API, copy URL, external players (VLC, IINA, PotPlayer, MX Player). A persistent
+"Transmitindo para {device}" indicator with a **Parar** button appears while casting.
+
+**17. PiP, fullscreen and theater mode** — Shared
+`src/utils/telaCheia.ts` handles prefixed APIs (`webkitRequestFullscreen`, `webkitEnterFullscreen`),
+locks the orientation to landscape on coarse-pointer devices and releases it on exit. PiP is toggled
+with `requestPictureInPicture` and only rendered when `document.pictureInPictureEnabled`.
+Theater mode enlarges the player (`.theater-mode`).
+
+**18. "This channel only plays over http" notice** — Shared
+`src/components/VideoPlayer.tsx:1115-1159`. When every attempt is `http://` (blocked as mixed content
+inside an https page), the app shows a dedicated overlay: "Este canal toca numa página separada",
+the explanation, a button **"Abrir {canal} numa página separada"** (`window.open`), a hint to use VLC
+if the browser downloads the file, an app-download promo, and "Tentar de novo aqui".
+
+**19. "Play it in the free app" hint** — Shared
+When a channel plays through the site proxy, a dismissible floating note appears
+("Este canal toca mais leve no app grátis" + "Baixar app"). Dismissing hides it for **7 days**
+(`localStorage['saimo-dica-app-fechada']`).
+
+**20. Capture-extension blocker** — Shared
+`src/plataforma/bloqueioExtensoes.ts` detects the FlowPick sniffer (`script[data-m3u8-injected]`,
+`window.__flowpickPageHook`, `FLOWPICK_*` messages). When detected, all video/radio loading stops and
+nothing else is fetched — **silently**, no on-screen warning.
+
+**21. Resume last channel + deep links** — Shared
+`localStorage['tv-last-channel']`; `/tv/:slug` resolves a channel by normalised id or by name
+(`src/App.tsx:128-147`). The channel chosen in a given `/tv/:slug` link is remembered with its slug,
+so opening another link switches the channel. Ajustes → "Ao iniciar" can open the last channel
+instead of the home screen (first entry of the visit only, guarded by `sessionStorage['saimo-entrada']`).
+
+**22. Media Session / lock-screen / Bluetooth controls** — Shared
+`src/hooks/useMediaSession.ts` — title, subtitle ("Ao vivo · Saimo TV"), channel logo as artwork;
+play/pause/seek handlers. For live TV, pause resumes playback.
+
+**23. Live TV never pauses** — Shared
+`src/components/VideoPlayer.tsx:658-719`. A `pause` event immediately calls `play()` again; if that
+fails it retries muted and arms `pendingUnmute`. Autoplay-with-sound is blocked by browsers: the
+video starts muted and is unmuted on the first click/keypress/touch (`pendingUnmute`).
+
+**24. Stream tester (internal tool)** — Web (dev-gated)
+`src/components/StreamTester.tsx`, route `/teste` only when `localStorage['saimo-dev'] === '1'`.
+Paste an M3U8/MP4 URL → detects the format (`.mp4` / `.ts` / HLS / Safari native) → plays it with
+hls.js or mpegts.js. Features: **auto-refresh with invisible double-buffering** (a second `<video>`
++ second HLS instance is loaded in the background and swapped in when ready — volume/mute preserved),
+configurable interval (default 15 s) with a live countdown, a statistics panel (play time, refresh
+count, last refresh), and a timestamped log console (last 100 entries). MP4 is excluded from refresh.
+
+### On Demand / VOD
+
+**25. Catalogue in "rails" mode (rows) and "Explore" mode (A–Z grid)** — Shared
+`src/components/VodCatalog.tsx` (2800 lines). Rails mode: a **hero** at the top (background art +
+title + badges + synopsis, driven by the item under the mouse, debounced 350 ms), a "Categorias" row
+of big collage cards (Filmes / Séries / Animes / Doramas / Novelas / Kids / Novelinhas / 18+ with
+counts), a **Continue assistindo** row with progress bars, the "Destaques" rails from
+`destaques.txt`, and one rail per genre ending in a **"Ver todos"** card that opens that genre's grid.
+Explore mode: a letter filter `#A–Z`, a genre filter, a free-text search and the filter grid.
+
+**26. Filters: year, age rating, minimum rating, sort order** — Shared
+`src/services/infoDosTitulos.ts` (`detalhes.txt`: `ano`, `nota`, `idade`, `popularidade` per title),
+applied in `VodCatalog.tsx:1473-1517`. Sort options: **A–Z**, **mais novos**, **mais populares**
+(by `popularidade`), **melhores avaliados** (by `nota`). Year ranges come from `faixasDeAno()`.
+The "Filtrar" button toggles Explore mode; a filter chip lights up when active.
+
+**27. Title detail sheet (ficha)** — Shared
+`VodCatalog.tsx` — modal with poster, backdrop, title, year, rating, genres, synopsis, cast photos
+(TMDB), season pills, a horizontal 16:9 episode strip with stills and progress, a favourite toggle,
+and the source/version picker. Data comes from `src/services/tmdbService.ts` (ficha, creditos, perfil,
+episodiosDaTemporada) fetched **on demand** (the "ficha sob o ponteiro" hook waits 280 ms for the
+mouse to settle before asking TMDB — `src/hooks/useFichaDe.ts`).
+
+**28. Continue assistindo** — Shared
+`src/services/continuar.ts` — `localStorage['saimo-continuar']`, max 20 entries, sorted by recency,
+stores the catalogue coordinates (`ItemDestaque`), the label, the fraction and the timestamp.
+Finished movies (≥95 %) are removed; series stay so the next episode continues. Each card has an **×**
+to remove it from the row.
+
+**29. VOD favourites** — Shared
+`src/services/vodFavoritos.ts` — `localStorage['saimo-vod-favoritos']`, stores
+`{tipo, titulo, letra, ano, capa}`; the newest first. Shown on Home and on the Favorites screen.
+
+**30. Series episode browser + next/previous episode** — Shared
+`src/components/MoviePlayer.tsx:1669-1769` — a side drawer listing seasons as pills
+(`Temporada N` + episode count) and the episodes of the selected season with TMDB title, still image
+and duration. The player keeps the whole series in `SeriesEpisodeInfo.episodes`. Next/previous episode
+buttons in the control bar, and a **"Próximo episódio" card** that appears when the credits start
+(TheIntroDB credits marker) or 30 s before the end, with a **Reproduzir** button and a dismiss (×).
+
+**31. Automatic next-episode hand-off + source stickiness** — Shared
+`src/utils/fontePreferida.ts` — when the episode changes, the new source list is matched against the
+one that was playing: same `bingeGroup` first, then the same host, then the same index **within the
+same audio version** (dub/leg never switches). `lembrarFonte()` stores the choice per series.
+
+**32. Skip intro / recap / credits** — Shared
+`src/services/pulos.ts` (TheIntroDB `api.theintrodb.org/v3/media?tmdb_id=…`, with season/episode for
+series). Returns `Trecho[]` of type `abertura | recapitulacao | creditos | previa` with start/end in
+seconds; chunks shorter than 3 s are discarded; a segment without an end is only valid for credits.
+The player shows a floating button ("Pular abertura" / recap / preview / credits) with a close (×),
+auto-hides after 8 s when the controls are hidden, and skips to the segment end.
+
+**33. Subtitles: embedded tracks + OpenSubtitles, with sync adjustment** — Shared
+`src/services/legendas.ts` uses the public Stremio OpenSubtitles addon
+(`opensubtitles-v3.strem.io/subtitles`) — no key, open CORS — plus a TMDB→IMDb bridge
+(`vod/imdb/` fragments, ~8 KB) and the `imdbId` already present on catalogue titles.
+Offered languages: Portuguese (BR) up to 5 versions, Portuguese (PT) 3, English 3, Spanish 2.
+Only the *list* is fetched when a title opens; the .srt (~40 KB) is downloaded only when a version is
+chosen. External subtitles are rendered into a synthetic `<track>` via `VTTCue`s, and the **sync
+offset** selector offers −5 s…+5 s in 0.25 s steps (41 options) and re-draws the cues without
+re-downloading. Embedded HLS subtitle tracks are merged with the external ones in one dropdown
+("Desligadas" / tracks / "OpenSubtitles" optgroup); choosing an external one disables the embedded
+display. The chosen language is remembered (`legenda-idioma`).
+
+**34. Resume position for VOD** — Shared
+`localStorage['movie-progress-<movieId>']` stores the seconds; saved on unload and when the source
+changes, **only after 30 s** of playback and **removed above 95 %** progress; restored on load if the
+saved point is >5 s from the end.
+
+**35. Player settings menu** — Shared
+`src/components/MoviePlayer.tsx:2182-2349`: **Qualidade** (select, "Automática" + levels),
+**Idioma do áudio** (hls.js audio tracks), **Legendas** (merged list), **Sincronia da legenda**,
+**Velocidade** (0.25× → 3×, 10 options, with a speed indicator badge when ≠ 1),
+**Pular (segundos)** (5/10/15/30/60 — persisted in `localStorage['movie-skip-time']`),
+**Proporção** (auto / 16:9 / 4:3 / 21:9), **Super Resolução** (off / Nitidez HD / Super Resolução
+Ultra) and **Brilho** (50–150 %). The upscaling choice is stored in `localStorage['saimo-upscaling']`
+and applied as an SVG `feConvolveMatrix` filter on `document.documentElement.dataset.upscaling`
+(HD kernel `0 -0.45 0 / -0.45 2.8 / 0 -0.45 0`; 4K kernel `-0.15 -0.55 -0.15 / -0.55 3.8 / …`).
+Videos whose own resolution already meets the target are tagged `data-nativo="1"` and skipped by
+the filter.
+
+**36. "Trocar Fonte" while loading / source modal for a title** — Shared
+While loading, a "Trocar Fonte" button cycles through the sources. The source modal lists each
+source with `Fonte N`/`leg`/`dub`, the provider label ("Servidor Alternativo (HTTP)"),
+and badges `Debrid | HLS | MP4/Direto | HTTP | HTTPS | Dublado | Legendado` + the addon's own
+warning text — **never the raw URL**.
+
+**37. Novelinhas — vertical short-form player** — Shared
+`src/components/Novelinhas.tsx` + `src/services/novelinhas.ts` (`novelinhas.txt`: id, title, folder,
+episode count, dub/leg, cover, genres, synopsis; episode URL = `base + folder + "/episode-" + NNN.mp4"`).
+A dedicated section with: a "Continue assistindo" row, a search box, audio chips
+(Todas/Dubladas/Legendadas), genre chips (genres with ≥5 titles, top 14), and a grid of portrait
+covers with a DUB/LEG badge and a progress bar. The player is a **fullscreen portrait portal**:
+swipe up → next episode, swipe down → previous, tap → play/pause, wheel → change episode
+(desktop), arrow keys / PageUp / PageDown → change episode, `Space` → pause, `Esc` → close.
+The next episode is **preloaded** with `<link rel="preload" as="video">` while the current one plays,
+and auto-advance happens at `ended`. Progress is saved every 5 s in
+`localStorage['saimo-novelinhas-progresso']`.
+
+**38. Addons — Stremio protocol** — Shared
+`src/services/addons.ts`, `src/utils/addonProtocol.ts`, `src/utils/addonClient.ts`,
+`src/components/AddonsModal.tsx`. **Zero addons pre-installed.** The user pastes an addon URL;
+the manifest is fetched, normalised and stored (`localStorage['saimo_addons_v1']` as `AddonRecord`
+= id, baseUrl, manifestUrl, queryString, manifest, enabled, addedAt, order). The user can
+enable/disable, **test** and remove addons. Resources supported: `catalog`, `meta`, `stream`,
+`subtitles` (with types and idPrefixes). Addons supply catalogue rails, metadata, streams and
+subtitles; streams are mapped to `MovieSource` with `addonId`, `infoHash`, `fileIdx`, `trackers`,
+`behaviorHints` and `webAviso`. All traffic goes **directly from the browser to the addon** — never
+through the Saimo servers. Addons are hidden on the Kids profile.
+
+**39. Debrid accounts (TorBox, Real-Debrid, AllDebrid, Premiumize)** — Shared
+`src/services/debrid.ts` + `src/utils/debridProtocol.ts`. Keys live only in this browser
+(`localStorage['saimo_debrid_v1']`), never proxied. The modal lets the user add a key per provider,
+test the account, toggle `syncWithConnect`, and remove it. Torrent sources are resolved through the
+provider at play time (`resolveDebridStream(infoHash, fileIdx, trackers)`); unresolved torrents show
+"Não foi possível resolver este torrent no Debrid." and sources flagged "Requer Debrid" show
+"Esta fonte torrent requer uma chave Debrid em Ajustes > Addons."
+
+**40. "Saimo Connect" data sync** — Shared
+`aplicarDadosConnect()` / `exportConnectDadosPayload()` implement the retro-compatible payload
+`{ t: "dados", addons?, debrid? }` — the Addons modal has a **Connect** tab with JSON import/export.
+
+### Other screens and features
+
+**41. Home (Início)** — Shared
+`src/telas/Inicio.tsx`. Animated logo intro (`Abertura.tsx`, SVG layers + canvas particles, skipped
+on TV), the hero with the title under the mouse (TMDB backdrop, rating, seasons, genres), then the
+rails: **Continue assistindo**, **Ao vivo agora** (up to 30 wide cards with the on-air programme and
+progress), **Canais favoritos**, **Filmes e séries favoritos**, and the catalogue highlight rails
+(`destaques.txt`) plus, when addons are installed, addon catalogue rails and TMDB "popular" rails.
+Refreshes the on-air data every 60 s and on every EPG event.
+
+**42. Events / sports agenda** — Shared
+`src/services/eventos.ts` + `src/telas/Eventos.tsx` + `src/components/EventsModal.tsx`.
+Two sources: the Saimo agenda (`eventos-extra.json`) says **which channel** carries each game, and
+ESPN scoreboards (23 leagues: Brazilian football, Libertadores, Sudamericana, Champions, Europa,
+Premier, La Liga, Serie A, Bundesliga, Ligue 1, Portuguese, Eredivisie, Argentina, Saudi, World Cup,
+NBA, NFL, MLB, NHL, UFC) provide the live score, minute and better badges. Games are matched by team
+name (accent/prefix-insensitive, club-name tokens removed) and start time (±4 h); a reversed
+home/away ordering swaps the badges and the score. Only games that exist on a channel in the user's
+list are shown (adult channels excluded). Grouped into **Ao vivo agora / Começa em breve / Hoje mais
+tarde / Amanhã / Próximos dias**, with a hero for the hovered game. Refreshes every 3 minutes.
+
+**43. Radios** — Shared
+`src/telas/Radios.tsx` + `src/services/radios.ts` (`radios.txt` → `nome|endereço|logo`, cached in
+`localStorage['saimo-radios-lista']`) + `src/services/radioPlayer.ts` (a single site-wide
+`<audio>` element + hls.js, so audio keeps playing across screens). Rows: "Ouvidas por último"
+(`localStorage['saimo-radios-recentes']`) and "Todas, de A a Z". The playing radio is shown at the
+top with an animated equalizer and a **Parar** button; the top bar shows a mini player with a stop
+button; opening the Live TV screen stops the radio (live takes the sound).
+
+**44. Universal search** — Shared
+`src/telas/Busca.tsx`. Searches channels (accent-insensitive, max 40), films, series, animes,
+doramas — the catalogue index (`busca.txt`, ~30 k titles in <1 MB) plus addon catalogue search and
+TMDB multi-search, merged and de-duplicated. Debounced 220 ms, min 2 characters. Recent searches
+(`localStorage['saimo-buscas-recentes']`, max 8) with a "Limpar" button. Empty state:
+"Nada encontrado / Nada para '{termo}'. Tente outro nome."
+
+**45. Favorites screen** — Shared
+`src/telas/Favoritos.tsx` — filter pills (Tudo / Canais / Filmes e séries), counts, and a remove
+(♥) button per item; empty state explains how to add favourites.
+
+**46. Profiles (up to 10)** — Shared
+`src/perfis/perfis.ts`, `EscolhaPerfil.tsx`. Each profile has an id, a name (max 20 chars), one of
+**21 avatars** (SVG on the site, PNG in the TV package) and its own favourites, "continue watching",
+recents and last channel. Storage trick: the **main profile uses the legacy keys unchanged**, the
+others use `key@<id>`; `instalarChavesPorPerfil()` monkey-patches `Storage.prototype.getItem/
+setItem/removeItem` **before any module reads storage** (imported first in `main.tsx`), so no module
+needs to know about profiles. Switching profile reloads the page. With 2+ profiles the chooser opens
+automatically once per tab (`sessionStorage['saimo-perfil-escolhido']`); it can also be disabled
+("Entrar no último perfil"). Profile metadata: `saimo-perfis`, `saimo-perfil-ativo`,
+`saimo-perfil-perguntar`, `saimo-perfis-removidos`.
+
+**47. Kids profile** — Shared
+`src/perfis/kids.ts`. Only titles with a **known** age rating between 0 and 12 are shown; unrated
+titles are hidden. The filter lives inside the catalogue reads (`vodService`), so rails, grids,
+search and sheets all receive the cleaned list. Adult content never unlocks. Leaving the Kids
+profile, managing it, editing it, or opening Ajustes asks for the 4-digit code.
+
+**48. Parental control (PIN)** — Shared
+`src/perfis/parental.ts` + `src/perfis/ControleParental.tsx`. Device-wide (not per profile).
+Default PIN **0000**, changeable in Ajustes (new PIN must be 4 digits). Stored as
+`sha256('saimo-parental:' + pin)` in `localStorage['saimo-parental-senha']`. Never unlocks on the
+Kids profile. The section sits at the **end** of Ajustes, deliberately discreet. A second entry point:
+**20 clicks on the logo in the top bar** (within 3 s) opens the "+18" PIN modal, and clicking the logo
+again while unlocked re-locks.
+
+**49. Adult content gating** — Shared
+`src/contexts/adultoContexto.ts` (`localStorage['adult-mode-global']`). When unlocked, the
+`+18` badge appears on the logo, the 18+ catalogue section becomes available, and the
+`restritos.txt` channel list is fetched and merged (channels get the `restrito-` id prefix).
+
+**50. "Minhas listas" — user M3U / file / Xtream** — Shared
+`src/telas/MinhasListas.tsx`, `src/services/minhasListas.ts`, `src/utils/leitorM3u.ts`,
+`src/utils/xtream.ts`. Up to **10 lists**; each may be:
+- an **M3U link** (downloaded directly; falls back to `/api/proxy?raw=1` when the browser is blocked),
+- an **M3U file** chosen from disk,
+- a **server + user + password** (Xtream Codes): `player_api.php` is tried first
+  (`get_live_categories` + `get_live_streams`, each stream offered as `.m3u8` **and** `.ts`);
+  if the API is unavailable the `get.php` M3U is used. Passwords never appear in logs or error
+  messages.
+The M3U reader (`leitorM3u.ts`) supports `#EXTINF` (`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`),
+`#EXTGRP`, `#EXTVLCOPT` (`http-referrer`, `http-user-agent`), `#KODIPROP`
+(`inputstream.adaptive.license_type`/`license_key` → ClearKey `KID:KEY`), Kodi-style
+`url|User-Agent=…&Referer=…` lines, resolution tags (`720p`/`1080p`), and strips "noise" tags
+(`Not 24/7`, `geo-blocked`, `backup`, `4K`, `HD`…). Limit 20 000 channels; adult content is filtered
+by name/group regex (with "Adult Swim" explicitly excluded) until the parental PIN is given.
+Lists auto-refresh after **12 h** (`atualizarVencidas`), one at a time, silently.
+
+**51. Ajustes (Settings)** — Shared
+`src/telas/Ajustes.tsx`. Sections: **Listas** (the two addresses with a live network test — the
+address must return 200 for `catalogo.txt` / `indice.txt`; failures offer "Tentar de novo" or
+"Salvar mesmo assim"; "Apagar" disables a list and wipes its caches), **Minhas listas**,
+**Addons & Debrid**, **Perfis** (switch / manage / "perguntar quem está assistindo"), **Ao iniciar**
+(Home vs last channel), **Super Resolução (Upscaling)**, **Áudio Inteligente**, **Zero Delay**
+(off / equilibrado / agressivo, with an explanatory note per mode), **Exportar/importar channels**
+(export all or favourites to `.m3u`, import a Saimo `.txt` catalogue), **Tela** (app brightness
+slider 0–100 %), **Dados deste perfil** (clear Continue-watching / recent searches / recent radios /
+favourites), **Mais** (download the app, Novidades, "Apoiar o projeto", Discord, link to the channel
+list as text). On the Kids profile the whole screen is replaced by a PIN prompt.
+
+**52. Import / export of channels** — Shared
+`src/services/canaisPersonalizados.ts` — imported channels are stored in
+`localStorage['saimo-canais-personalizados']` and merged into the grid after the published list;
+export writes a standard `.m3u` (`saimo-tv-canais.m3u` / `saimo-tv-favoritos.m3u`).
+
+**53. Donations (Pix)** — Shared
+`src/components/DoacaoModal.tsx` + `DoacaoConteudo.tsx` + `src/services/doacaoService.ts`.
+A **draggable floating button** (mouse and touch) whose position is remembered
+(`localStorage['saimo-doacao-pos-v2']`). Clicking opens a modal with the explanation, a **Pix
+copia-e-cola** string (static BR Code for Gabriel Saimo Ferreira) with copy-to-clipboard (2.5 s
+"copiado" feedback, with a DOM fallback when the Clipboard API is blocked), a QR code, and the
+preferred name. The button **hides completely** in fullscreen, theater mode, the movie overlay and
+landscape playback (detected via fullscreen events, a `MutationObserver` on body classes and a
+resize listener). Also a standalone `/apoie` page and a global `saimo-abrir-modal-doacao` event
+("Apoiar o projeto ❤️" in Ajustes).
+
+**54. Novidades (catalogue release rounds)** — Shared
+`src/components/Novidades.tsx` — reads the published `novidades.json` (rounds at 11 h and 18 h) and
+shows, per round, the date/time, the turno label ("Rodada das 11h" / "das 18h" / "manual"), and the
+titles added/updated with covers; missing covers are looked up in TMDB. Read-only page, no link
+points to it.
+
+**55. App download page** — Shared
+`src/components/AppDownload.tsx` + `src/services/versao.ts`. Tabs per platform (iPhone "em breve",
+TV Box, Roku, Samsung, LG, Windows, Linux, Linux ARM, Mac) with requirements, step-by-step install
+instructions, the Downloader code `1506768`, and short URLs (`/tvbox`, `/celular`, `/mac`,
+`/windows`, `/linux`, `/linux-arm`, `/samsung`, `/lg`, `/roku`) served by `functions/*.ts` which
+read the latest GitHub release of `SaimoTV/saimo-downloads` and redirect.
+
+**56. Institutional / legal pages** — Web
+`src/landing/` — `Landing.tsx` (1387 lines): hero, "Como funciona" with a live demo, an interactive
+**MultiTelaSom** section (click a half to move the TV audio to the phone; the phone chooses screen
+1, 2 or both, with a Bluetooth-headset note), a tabbed **Recursos** section with 8 animated
+scenarios, **Tudo que ele faz** (a filterable list of every feature, grouped by Ao vivo / Filmes e
+séries / Som e imagem / Entre aparelhos / Sua lista e sua casa), **App por app**, **Feito para o
+controle remoto** (key tables), **Leve de verdade** (1 GB device measurements), **Aparelhos**,
+**Saimo Connect**, and a privacy section. Plus `TelaDoApp.tsx` (a replica of the real UI),
+`Moldura.tsx` (header/footer), `Formato.tsx` + `formato-saimo.md` (the list format), `Legal.tsx`
+(Privacy, Terms, Content, Data deletion, Support) and `config.ts` (contact e-mail, legal
+responsible, last revision date).
+
+**57. PWA installability** — Web
+`public/manifest.webmanifest` (`start_url: /home`, `display: standalone`, `lang: pt-BR`, 192/512 +
+maskable icons) and `public/sw.js` (caches only the app shell — HTML, JS, CSS, icons; **never**
+video, playlists, catalogues or API responses; network-first for navigations, cache-first for hashed
+assets; `/api/*` always bypasses the cache).
+
+**58. App brightness veil** — Shared
+`src/plataforma/brilho.ts` — a black overlay (`opacity = 1 - v/100`) over the whole page, 0–100 %.
+Below **5 %** the app "swallows" key/pointer events so the first press only restores the brightness
+(ramping back over 1.5 s) — otherwise a user who cannot see the screen would never find the control.
+Shared with the native TV app.
+
+**59. Server kill-switch ("fontes desativadas")** — Shared
+`src/services/fontesDesativadas.ts` — a public JSON list of server names that are down, downloaded
+without a secret and cached for **1 h** (`localStorage['saimo-fontes-desativadas']`), plus a
+hard-coded set of permanently dead servers. Disabled servers are removed from every channel and
+every film source; the channel grid rebuilds without a page reload (polled at the cache TTL).
+
+**60. Toasts** — Shared
+`src/components/Toast.tsx` — success / error / info variants with distinct icons, auto-dismissed
+after 3 s, dismissed on click.
+
+**61. Telemetry (disabled by default)** — Shared
+`src/services/telemetria.ts` — device UUID in `localStorage['saimo-telemetria-id']`, no account, no
+IP storage (the city is derived at the Cloudflare edge), `sendBeacon` with `text/plain` so there is
+no CORS preflight. Reports open/start/played/failed/dropped/stopped and searches. `DESLIGADA = true`
+in the current code — the sampling percentage comes from `public/telemetria.json` (currently 0 %).
+
+---
+
+## C. EPG implementation details
+
+### C.1 Sources and priority
+
+The EPG is assembled in a **Web Worker** (`src/workers/epgWorker.ts`, 720 lines) so that the ~18 MB
+of XML never touches the main thread. The order is deliberate:
+
+1. **`guiadetv.com`** — HTML per channel (`https://www.guiadetv.com/canal/<slug>`); covers ~121
+   channels with exact Brasília times, Portuguese synopses, no 429 blocking. Fetched through
+   `/api/proxy`, concurrency **6**. Channels are grouped by slug (regional variants share a page).
+2. **`meuguia.tv`** — HTML per channel (`https://meuguia.tv/programacao/canal/<code>`), only for
+   channels guiadetv did not cover (e.g. Canal Brasil); concurrency **4**, **one retry** after 1.5 s.
+3. **XMLTV feeds** — listed in `guia.txt`; parsed with a streaming regex over `response.body`
+   (`<(channel|programme)…</\1>`) so the 15 MB feed is never held as one string. These *enrich*
+   (poster, synopsis, episode, category) but **do not overwrite the times** of meuguia/guiadetv.
+4. **Pluto TV guide** (i.mjh.nz, fetched directly without the proxy because of CORS) — matched by the
+   Pluto id embedded in the channel URL (`plu-<24 hex>` / `images.pluto.tv/channels/<24 hex>`).
+   If Pluto is the channel's **principal** source, only its guide is used; if it is a fallback
+   (TV Cultura, CNBC…), its generic schedule is used only when nothing else exists.
+
+**Server-side pre-built guide**: `epg.json` at the channels address is fetched first
+(`{ g: {slug: [[start, stop, title, synopsis?]]}, m: {code: [[start, stop, title, genre?]]} }`,
+times in seconds) — it exists to avoid routing every page through the paid `/api/proxy`.
+
+### C.2 Channel matching
+
+The EPG key is the **normalised channel name**, never the site id
+(`src/utils/nomes.ts`: decode entities → strip a leading `BR - ` → NFD + remove diacritics →
+lowercase → keep `a-z0-9` → collapse spaces → strip trailing noise tokens `hd|sd|fhd|uhd|4k|br`).
+`epgKey(id)` reverses the id hyphenation to recover the key.
+
+Matching channels to XMLTV ids (`resolverIds`):
+1. **exact** normalised `display-name` match → all ids of that name are claimed;
+2. **alias** match (from `alias` lines in `guia.txt`);
+3. only then a **loose prefix** match (`k.startsWith(chave) || chave.startsWith(k)`) — and only if
+   **exactly one** candidate exists, and never over an id already claimed (so "HBO2" cannot steal the
+   id of "HBO 2").
+
+### C.3 Source tables (`guia.txt`)
+
+`src/utils/guiaTabelas.ts` parses a TAB-separated file (lines starting with `#` are comments):
+
+| Type | Fields | Meaning |
+|---|---|---|
+| `meuguia` | `<channel name>` `<code>` | channel → meuguia.tv code |
+| `guiadetv` | `<normalised name>` `<slug or URL>` | channel → guiadetv.com page |
+| `alias` | `<normalised name>` `<feed name>` | channel → name inside the XMLTV feed |
+| `feed` | `-` `<XMLTV URL>` | feed to parse (order matters) |
+| `pluto` | `-` `<Pluto guide URL>` | Pluto TV guide |
+
+The file is fetched from the channels address and cached in `localStorage['saimo-guia-tabelas']`.
+**No per-channel table lives in the code** — this file is the single source of truth.
+
+### C.4 Parsing details
+
+- **meuguia** (`parseMeuGuia`): iterates `<li>` items. A `subheader` item is a day header
+  (`dd/mm`); a programme item has the start time in `lileft time'>`, the title in `<h2>` and the
+  genre in `<h3>`. Times are converted to São Paulo time with the **browser's own** offset
+  (`Intl.DateTimeFormat('en-US', {timeZone:'America/Sao_Paulo'})` → `saoPauloOffset`), so the grid
+  is correct regardless of the visitor's timezone. The end time is not published: a programme runs
+  until the next one starts (last item: +1 h). The list is re-sorted chronologically afterwards
+  (the page order is a layout artefact, not a guarantee).
+- **guiadetv** (`parseGuiaDeTv`): `<li class="flex gap-4 …">` items with
+  `<time datetime="AAAA-MM-DDTHH:MM:SS-03:00">`, a duration span (`1h30` / `45min`), an
+  `<a href="/programa/…">` title and a `<p>` synopsis. Duplicate start times are dropped; if the
+  computed end passes the next start (or is within 60 s of it) the end is clamped to the next start.
+- **XMLTV** (`parseFeed`): attribute extraction is boundary-aware (a space/tab/newline/`<` must
+  precede `name="` so `channel` never matches `xchannel`). Date format `20260902020000 -0300`
+  (offset optional). Each programme yields title, category, start, stop, poster from `<icon src>`,
+  episode from `<episode-num>` (values containing `original-air-date` are discarded — Pluto uses
+  that field for the air date) and description from `<desc>`.
+
+### C.5 Overlap handling
+
+`arrumar()` normalises each channel's list: drop zero-length programmes → sort by start →
+**drop a programme whose start equals the previous one's start** → **clamp the previous programme's
+stop to the new start** if it overlapped. Result: the grid never has overlapping blocks and
+"now on air" always picks the right programme. `firstEndingAfter(programs, t)` is a **binary search**
+for the first programme that has not ended yet (used hundreds of times per minute by the grid and the
+cards).
+
+### C.6 Caching, windowing and notifications
+
+- Window kept: **3 h in the past → 30 h in the future**.
+- Cache: `localStorage['saimo-epg-v6']` = `{at, sig, grade}` with a **6 h TTL**; the signature is
+  `linkCanais() + '#' + sorted normalised channel names`, so a new channel invalidates the cache
+  immediately. Old cache keys (`v3`…`v5`) are deleted on read.
+- The cache is written inside `requestIdleCallback` (fallback `setTimeout`) so serialising the grade
+  never happens during a scroll.
+- Programmes older than **3 h** are dropped when converting to the UI model; titles matching
+  `"No Data"` / `"Sem informação"` / empty are discarded.
+- The worker posts **partial** messages (only the channels that changed); the service merges them
+  into the running grade and notifies listeners with the set of changed channel keys.
+  `isEPGReady()` tells the UI "no data now means no data", so the grid can show
+  "Sem programação" instead of "Carregando".
+- `registerChannels()` restarts the whole worker when the definitive channel list arrives later than
+  the first build (this is what previously left GloboNews/Record/Warner without a schedule).
+- TV builds cannot use a module worker over `file://` — `criarWorker()` imports
+  `../workers/epgWorker?worker&inline` (a blob) when `import.meta.env.VITE_TV` is set.
+
+---
+
+## D. Data / format details
+
+### D.1 Channel catalogue (`catalogo.txt` / `canais.txt`)
+
+`src/services/catalogService.ts` parses **two** files from the channels address:
+`catalogo.txt` (authoritative) and `canais.txt` (fallback/extra). Format is line-based
+`key: value`:
+
+```
+canal:     Nome do Canal
+logo:      https://…/logo.png
+categoria: TV Aberta
+fonte:     https://cdn/x/index.m3u8
+referer:   https://cdn/x/
+nome:      Temporada 3
+qualidade: 1080p
+agente:    Mozilla/5.0 …
+chave:     <KID hex>:<KEY hex>
+```
+
+- `categoria:` becomes the section; `canonizar()` maps free-form labels onto the fixed sections
+  (esporte/ppv/futsal → Esportes, filme/serie/cinema → Filmes, infanti/kids/desenho → Infantil,
+  document → Documentarios, notic/news → Noticias, novela → Series, aberto → TV Aberta,
+  adulto → Adulto, internacion/legendado → Internacionais, `pluto tv` → Pluto TV,
+  `24 horas` → 24 Horas, variedade/estilo de vida/entretenimento/religios/4k → Entretenimento);
+  unknown labels become their own section; a channel with no category lands in "Outros".
+- `fonte:` starts a new source; `referer:`, `nome:`, `qualidade:` and `agente:` apply to the **last**
+  source. `chave:` must be `KID:KEY` — otherwise the source is **dropped** (a source that would play
+  DRM is worse than one source less).
+- If the text starts with `#EXTM3U`, the file is parsed as **M3U** instead (`parseM3u`): `tvg-id` /
+  name (a trailing `(ST)` suffix is stripped), `tvg-logo`, `group-title`; duplicate names merge their
+  sources.
+- `mergeCatalogs()` merges the two files **by normalised name**: the catalogue's channels keep their
+  sources first, the extra file's new sources are appended, and channels that exist only in the
+  extras are added at the end.
+- `toChannels()` sorts by `categoryOrder` (unknown categories last, alphabetical) then by name
+  (`localeCompare('pt-BR')`), assigns a **stable id** = slugified name with a `-2`, `-3` suffix for
+  collisions ("SporTV HD" vs "SporTV"), and puts **HTTPS sources first** (`httpsFirst`).
+- Channel `url` = first source's URL (always HTTPS-preferred).
+- Cache: `localStorage['saimo-catalogo-v2']` = `{at, base, catalogo, canais}`, TTL **6 h**, and the
+  cache is **invalidated when the address changes** (`base` mismatch). The previous visit's list is
+  shown immediately while the new one downloads in the background.
+- `restritos.txt` (adult channels) is fetched **only after the parental unlock**, cached in
+  `localStorage['saimo-restritos-v1']`, and its channels get the id prefix `restrito-`.
+
+### D.2 User M3U lists
+
+`src/utils/leitorM3u.ts` — see feature 50 for the supported tags. Output shape:
+
+```ts
+CanalLido { nome: string; grupo: string | null; logo: string | null; fontes: FonteLida[] }
+FonteLida { url; referer; ua; keyId; key; qualidade }
+```
+Stored in IndexedDB (`saimo-minhas-listas` → store `canais`, keyed by list id); metadata in
+`localStorage['saimo-minhas-listas']` = `{id, nome, link|null, atualizadaEm, ativa, total}[]`.
+Converted to site channels with id `minha-<listId>-<normalisedName>` and category **"Minhas listas"**.
+
+### D.3 Xtream (server + user + password)
+
+`src/utils/xtream.ts`:
+- `normalizarServidor()` → `http://host[:port][/path]` (no scheme → `http://`; panel paths
+  `/get.php`, `/player_api.php`, `/panel_api.php`, `/c` are stripped).
+- `deLink()` recognises `get.php`/`player_api.php` links with `username` & `password`.
+- `verificarEntrada()` → `''` (valid) | `SEM_API` | "Usuário ou senha incorretos" |
+  "Essa conta não está ativa (status)" | "Essa conta venceu" (from `user_info.auth/status/exp_date`).
+- URLs: `…/get.php?username=…&password=…&type=m3u_plus&output=ts`,
+  `…/player_api.php?username=…&password=…[&action=…]`,
+  `…/live|/|movie|series/<user>/<pass>/<id>.<ext>`.
+- Each live stream is offered as **two** sources: `.m3u8` and `.ts`.
+
+### D.4 VOD catalogue (film/series address)
+
+`src/services/vodService.ts` — the published source list is ~30 MB, so it ships **pre-digested** in
+alphabetical shards:
+
+| File | Contents |
+|---|---|
+| `indice.txt` | `base:<n> <url>` lines (the address prefixes) + one line per letter: `letra\tfilmes\tseries\treservados` |
+| `busca.txt` | the search index: `titulo\ttipo(f|s)\tletra\tano` (one line per title, ~30 k titles in <1 MB) |
+| `filmes-<L>.txt` / `reservado-<L>.txt` | `titulo\t<versao>=<url>,<url>…` (TAB-separated, `versao` ∈ dub/leg) |
+| `series-<L>.txt` | `titulo\tano\tpedaco\tepisodios` |
+| `series-<L>-<pedaco>.txt` | `@<titulo>\t<ano>` block header, then `temporada\tnumero\tversao\t<url>,<url>` lines |
+| `destaques.txt` | `capa:<base>` + `fila\t<Título>` + `tipo\ttitulo\tletra\tano\tposter` |
+| `fichas.txt` | `capa:<base>` + `tipo\ttitulo\ttmdbId\tposter\tgenero1,genero2,…` |
+| `categorias.txt` | `tipo\ttitulo\tanime,dorama,novela,kids` |
+| `detalhes.txt` | `tipo\ttitulo\tano\tnota\tidade(L\|n)\tpopularidade` |
+| `novelinhas.txt` | `base: …` + `id\ttitulo\tpasta\tepisodios\taudio(dub\|leg)\tcapa\tgeneros\tsinopse` |
+| `redeflix/links-animes.txt`, `links-doramas.txt` | `@<titulo>\t<ano>\t<tmdbId>` + `temporada\tnumero\tversao\t<urls>` |
+
+URLs inside the catalogue are stored as `baseIndex:rest` (e.g. `0:19927`) and expanded at read time
+(the remainder is appended to the base, with `.mp4` added when the remainder has no dot).
+`filmes-<L>.txt` and `series-<L>-<n>.txt` are fetched **per letter** (each ≤ ~100 KB) and the
+navigation follows the finger. `busca.txt` gives the whole catalogue without downloading it.
+`fichas.txt` provides genres, posters and TMDB ids (used for the ficha, the genre rails, the Kids
+filter and the year/rating filters). `detalhes.txt` provides the On Demand filters.
+`novelinhas.txt` episode URL = `base + pasta + "/episode-" + NNN + ".mp4"`.
+All reads are memoised per visit and dropped when the film address changes.
+
+### D.5 EPG XMLTV
+
+Standard XMLTV (`<tv><channel id="…"><display-name>…</display-name></channel><programme start="20260902020000 -0300" stop="…" channel="…"><title>…</title><desc>…</desc><category>…</category><icon src="…"/><episode-num>…</episode-num></programme></tv>`),
+parsed by streaming regex, not by a DOM parser. `EPG_README.md` documents the iptv-org feeds
+(`meuguia.tv`, `claro.com.br`, `mi.tv` — `…/epg/guides/pt/<site>.epg.xml.gz`) and an old
+`channelToXmltvId` map in `epgService.ts`; **that map no longer exists** in the code — matching is now
+by name (see C.2). `EPG_README.md` is therefore stale.
+
+### D.6 Addons / Debrid formats
+
+`src/utils/addonProtocol.ts` (ES5-safe, no DOM/Node deps):
+`SAIMO_ADDONS_KEY='saimo_addons_v1'`, `SAIMO_DEBRID_KEY='saimo_debrid_v1'`,
+`SAIMO_IMDB_CACHE_KEY='saimo_imdb_cache_v1'`.
+`AddonRecord = {id, baseUrl, manifestUrl, queryString, manifest, enabled, addedAt, order}`;
+`StremioManifest = {id, name, version, description, logo, background, types[], resources[], catalogs[], idPrefixes?, behaviorHints?}`.
+Sync payload `{t:"dados", addons?, debrid?}`.
+
+### D.7 `localStorage` / storage keys
+
+| Key | Content |
+|---|---|
+| `tv-favorites` | `string[]` of channel ids |
+| `tv-last-channel` | last channel id |
+| `tv-volume` | live volume (0–1) |
+| `movie-volume` | VOD volume (0–1) |
+| `movie-skip-time` | skip seconds (5/10/15/30/60) |
+| `movie-progress-<movieId>` | resume seconds |
+| `current-movie-id` | currently playing title |
+| `adult-mode-global` | `true` when the adult mode is unlocked |
+| `saimo-link-canais`, `saimo-link-filmes` | the two list addresses (base with trailing `/`) |
+| `saimo-sem-listas-v1` | one-shot migration marker |
+| `saimo-catalogo-v2` | `{at, base, catalogo, canais}` |
+| `saimo-restritos-v1` | `{base, texto}` (adult channels) |
+| `saimo-epg-v6` (v3–v5 deleted) | `{at, sig, grade}` |
+| `saimo-guia-tabelas` | `{base, texto}` (guia.txt) |
+| `saimo-radios-lista`, `saimo-radios-recentes` | radio list cache / recents |
+| `saimo-buscas-recentes` | recent searches (max 8) |
+| `saimo-vod-favoritos` | `ItemDestaque[]` |
+| `saimo-continuar` | `Andamento[]` (max 20) |
+| `saimo-canais-personalizados` | imported channels |
+| `saimo-minhas-listas` | `MinhaLista[]` (metadata; payloads in IndexedDB) |
+| `saimo-zero-delay` | `off \| equilibrado \| agressivo` |
+| `saimo-audio-inteligente` | `true/false` |
+| `saimo-upscaling` | `off \| hd \| 4k` |
+| `saimo-ao-iniciar` | `inicio \| ultimo` |
+| `saimo-brilho` | 0–100 |
+| `saimo-parental-senha` | sha256 hash |
+| `saimo-perfis`, `saimo-perfil-ativo`, `saimo-perfil-perguntar`, `saimo-perfis-removidos` | profiles |
+| `saimo-perfil-escolhido` | sessionStorage flag |
+| `saimo-fontes-desativadas` | disabled servers (1 h) |
+| `saimo-sem-cors` | hosts that need the proxy |
+| `saimo-segmento-direto` | per-host direct/proxy decision (6 h) |
+| `saimo-novelinhas-progresso` | vertical player resume points |
+| `saimo-doacao-pos-v2` | floating donation button position |
+| `saimo-dica-app-fechada` | timestamp of the last "use the app" dismissal (7 days) |
+| `saimo-extensao-bloqueada` | extension blocker flag |
+| `saimo-telemetria-id`, `saimo-telemetria-amostra` | telemetry |
+| `saimo_addons_v1`, `saimo_debrid_v1`, `saimo_imdb_cache_v1` | addons / debrid / IMDb bridge |
+| `legenda-idioma` | preferred subtitle language |
+| `saimo-entrada`, `saimo-entrou` | session/visit flags |
+| `saimo-dev` | enables the internal `/teste` route |
+| `movie-progress-*`, and `<key>@<profileId>` | per-profile suffixing (feature 46) |
+
+**IndexedDB**: database `saimo-minhas-listas`, object store `canais`.
+**Cache Storage**: only the app shell (`saimo-casca-v4`).
+
+### D.8 Proxy contract
+
+`/api/proxy?url=<encoded>[&referer=][&ua=][&raw=1]` (Cloudflare Pages Function, mirrored at
+`api/proxy.ts` for Vercel on the Node runtime):
+- sends `User-Agent` (default Chrome UA) and `Referer` (default `scheme//host/`), `Origin`, `Accept`,
+  `Accept-Language: pt-BR`; forwards `Range`;
+- follows up to 15 redirects manually, restoring the original Referer/Origin and accumulating
+  `Set-Cookie`;
+- **rewrites HLS playlists** (`rewriteM3u8`) and DASH manifests (`rewriteMpd`) so every child URL
+  goes through the proxy, and fixes the "segments live in the `url=` folder" case
+  (`fixNestedPath`);
+- `raw=1` returns the bytes untouched — required for downloading a user M3U (otherwise `#EXTM3U`
+  would be treated as a playlist and every channel URL rewritten);
+- retries 5xx twice with a 300 ms gap; falls back to other headers on 403/404;
+- special-cases `*.77zzhf54vdll71.com` by upgrading to https and dropping the port;
+- `sondarProxy()` probes `/api/proxy` **without** `url`: a live proxy answers 400 JSON, a quota-cut
+  proxy answers the site HTML → the app switches to the mirror (`saimo-tv2.pages.dev`).
+
+`/api/resolve-vod?url=` — a **strict allowlist** resolver (not a generic proxy): only
+`dbonline.tech-cdn.top` and `hubby-dmca.com` `/movie/` and `/series/` paths; it follows up to 3
+redirects, only accepting `zjo.lat` as an intermediary and only the known final hosts; the final URL
+must be HTTPS and start with `/t/` (DBOnline) or carry a `token` (Hubby). Returns `{url}` or `{url:null}`.
+
+`/api/stream?tipo=movie&id=…[&tipo=series&id=…&s=&e=&versao=]` — the Saimo relay: resolves FenixFlix,
+Magnetflix, FrostStream, HollyPlayer, RedeFlix and NixPlay, picks the **highest-bandwidth** variant
+from the HLS master, and 302-redirects to the fresh token URL. This is why `/api/stream` cannot be
+used as a static source URL: the player must resolve it **at open time**.
+
+---
+
+## E. Notable UX details worth porting
+
+**Loading / empty / error states**
+- Distinct loading visuals: `LogoCarregando` (the channel/movie logo greyscale that fills in with
+  colour as the video opens, never completing before playback) for players, a "Carregando…" spinner
+  for lazy routes (`LoadingFallback` with a TV glyph), "Carregando mais canais…" for the channel
+  list sentinel, "Buscando a programação…" / "Carregando a programação…" for the EPG.
+- Empty states are **actionable**, not apologetic: `SemLista` ("Bem-vindo ao Saimo!") explains that a
+  list M3U, a Stremio addon or a Saimo address is needed and offers the exact button
+  ("Adicionar lista" → `/ajustes#minhas-listas`; "Configurar Addons" → `/ajustes#addons`; "Endereços
+  do Saimo"). It is shown per area (canais / filmes / radios / eventos / guia) and **no network
+  request is made while it is displayed**.
+- "Sem programação" vs "Carregando…" are distinguished once the EPG is ready (`isEPGReady()`).
+- "Sem canal encontrado" in the channel list; "Nada encontrado" in the search.
+
+**Toasts and inline notices**
+- 3-second toasts for every state change (favourite added/removed, source changed, multi-screen
+  toggled, list saved, data cleared, donation unlocked).
+- Inline, dismissible notices inside the player (the app-download hint, the Zero Delay mode toast,
+  the extension-blocked state) and inline form errors (`listas-erro` with an alert role).
+
+**Audio / language handling**
+- Audio-track list only appears when the stream has more than one track; the label falls back to
+  `lang` and then to "Faixa N". The chosen track is highlighted "Em uso".
+- Live and VOD volumes are stored **separately** (`tv-volume`, `movie-volume`).
+- The subtitle language is remembered across titles and auto-enabled when a matching version exists.
+- The audio version (dub/leg) is preserved across episodes of a series.
+- "Áudio Inteligente" makes quiet channels audible without clipping loud ones.
+
+**Resume / continuity**
+- Resume position for VOD (>30 s, removed at ≥95 %, restored only if ≥5 s from the end).
+- "Continue assistindo" row on Home with a progress bar and an × to remove.
+- Series continue across episodes; the next-episode card appears at the credits.
+- Live TV remembers the last channel and can open on it.
+- The radio keeps playing while you navigate, and the top bar shows it.
+
+**Failure handling**
+- Failures are graded: a transient error while the picture still advances is recovered in place
+  (nobody notices); only a long silence (12 s with no picture, 25 s of frozen picture) triggers a
+  source switch; only the exhaustion of all sources becomes a user-visible error.
+- The VOD player walks the source list silently and only shows an error when the last one fails —
+  and even then it offers "Trocar fonte / servidor (N)".
+- HTTP-only sources get a **dedicated** explanation with a one-click "open in a separate page",
+  rather than a generic error.
+- The address of a source is never shown to the user (only "Fonte N", the name and the quality) —
+  a deliberate privacy decision repeated in the live sources modal, the VOD sources modal and the
+  cast modal.
+
+**Performance techniques worth keeping**
+- Channel list and EPG grid render only what is on screen (40-row chunks + IntersectionObserver;
+  a 4-line/60-minute quantised viewport in the grid).
+- The EPG is built in a worker and published in partial batches; the cache is written in
+  `requestIdleCallback`.
+- The EPG "current programme" lookup is a binary search.
+- Lazy routes (`React.lazy`) for the players, the catalogue, the guide, the tester, the download page
+  and the donation page; `manualChunks` splits `vendor-react`, `vendor-hls`, `vendor-shaka`, `player`,
+  `catalog` and `tv` — Shaka is only downloaded when a DASH channel is opened.
+- `ImagemLazy` uses `IntersectionObserver` on TVs (where `loading="lazy"` does not exist) and the
+  native attribute elsewhere.
+- Broken channel logos are remembered per visit so the row does not flicker.
+- The app waits at most 1.5 s for the proxy probe before rendering.
+
+**Accessibility / input**
+- Every interactive element has `data-focusable`, `data-focus-key` and `aria-label`.
+- Dialogs are `role="dialog" aria-modal="true"`; the TV focus is trapped in the topmost dialog and
+  restored on close.
+- The remote-control hint overlay (↑↓←→ Navigate / Enter Select / Esc Back) appears the first time
+  D-pad keys are used.
+- Focus is scrolled into view along the *row* that scrolls, not only the page.
+- `Esc` always closes the top layer, in the capture phase, before the D-pad back handler sees it.
+
+**Mobile specifics**
+- Bottom tab bar with labels (Início / Ao vivo / Eventos / Filmes e séries / Baixar app); tapping the
+  active tab scrolls the page back to the top; the Eventos tab opens a modal because the desktop
+  top bar is hidden on phones.
+- Landscape phone: the video fills the screen and the channel list becomes a floating drawer.
+- Fullscreen locks the orientation to landscape on touch devices and releases it on exit; iPhone uses
+  `webkitEnterFullscreen` on the `<video>`.
+- The vertical (Novelinha) player requests fullscreen + portrait lock on small screens.
+
+---
+
+## F. Hard to port / platform-specific
+
+| Item | Why it is hard |
+|---|---|
+| **`http` video on an https page** | Browsers block mixed content; the Cloudflare IPs are also refused by several CDNs (403). The site therefore cannot play a large part of the catalogue and shows the "opens in a separate page" notice. Porting to a different stack must decide: keep the paid proxy, ship a native app, or accept the limitation. `buildAttempts` encodes this whole policy. |
+| **Cloudflare Pages Functions budget** | `/api/proxy` is a *paid* invocation per request (millions/day). The design counters it with: direct-first attempts, `segmentoDireto.ts` (segments fetched straight from the origin, with a per-host 6 h memory), `saimo-sem-cors` (hosts that must start at the proxy), and `proxyDeVideoLiberado()` (the site disables video proxying entirely; only the TV/desktop apps keep it). The quota probe + mirror (`saimo-tv2.pages.dev`) is also part of this. |
+| **`/api/stream` and `/api/resolve-vod`** | Signed, short-lived URLs: the destination changes per request and may be HLS. The player cannot use the catalogue URL statically. The allowlist resolver is provider-specific and brittle (host + path + token validation, manual redirect following). |
+| **Profile storage monkey-patch** | `instalarChavesPorPerfil()` overrides `Storage.prototype.getItem/setItem/removeItem` **before any module loads**. It works but is fragile: it must run first in the entry point, and any code that captures `localStorage.getItem` before the patch (or uses `localStorage` from another realm) bypasses it. |
+| **TV package (Tizen/webOS)** | `file://` origin, ES5-only output, no module workers, no `loading="lazy"`, no service worker, limited memory. Needs `core-js`/`whatwg-fetch`/`abortcontroller`/`intersection-observer` polyfills, a blob-URL worker, `__SAIMO_TV__` brand detection, `tizen.tvinputdevice.registerKey`, `webOS.platformBack`, and its own 4K zoom. The native TV app (`tv-native/`) is a **separate codebase** — porting the web app to the TV means porting it twice. |
+| **Remote-control key translation** | TV remotes are not keyboards: the app re-dispatches synthetic `KeyboardEvent`s with a forced `keyCode`. Some TVs deliver keys with `key === 'Unidentified'`. Back/Exit semantics differ per brand, hence the double-press-to-exit toast. |
+| **Native TV HLS playback** | `hlsNativo()` uses the TV's own decoder (no MSE, no CORS) — but the same URL may fail on one TV and work on another ("The element has no supported sources"), hence the three-step attempt order (hls.js → native → proxy). |
+| **Eme / ClearKey (DASH)** | Shaka Player is loaded as a **compiled** bundle at runtime and configured with `keySystem: 'org.w3.clearkey'` + the catalog's KID/KEY. The license is not a server call — it is the key in the catalogue — but EME support varies by browser and the Shaka bundle is ~1 MB. |
+| **`mpegts.js`** | Raw MPEG-TS over MSE is only supported where MediaSource exists; the config uses `liveSync*` options that are mpegts-specific. |
+| **Chromecast / AirPlay** | The Cast SDK is loaded lazily from Google; AirPlay needs Safari's non-standard API; the Remote Playback and Presentation APIs need their own feature detection. The URL-policy rule (only `http://` may leave the site) is intertwined with the security model. |
+| **`crypto.subtle` / SHA-256** | The Kids PIN and the parental PIN are hashed with a **pure-JS SHA-256** (`src/perfis/sha256.ts`) because the TV runs from `file://` where `crypto.subtle` is unavailable in some engines. Porting to a modern-only stack could use the WebCrypto API, but then the TV build must keep a fallback. |
+| **`useVoltarFecha` history juggling** | Layered back is implemented by pushing real history entries and batching `history.go(-n)`. It fights with react-router's own index and with browser back gestures; porting it to a different router needs care. |
+| **Extension blocker** | Detecting a third-party extension (FlowPick) relies on injected DOM/globals/messages. It is inherently best-effort. |
+| **The `catalogo.txt` / `guia.txt` / `detalhes.txt` formats** | These are **not** part of any public standard; they are the Saimo project's private formats. A port must either reimplement the parsers exactly (they are documented above) or negotiate new endpoints. `EPG_README.md` and several files under `docs/` are **stale** (they describe an older AllOrigins-based EPG, `src/assets/ListaBR0*.m3u8` inputs and a `src/data/channels.ts` that no longer exist) — treat the code, not the docs, as the specification. |
+| **TMDB API key baked into the client** | `TMDB_API_KEY = '15d2ea6d0dc1d476efbca3eba2b9bbfb'` is shipped in the browser bundle (also `TMDB_CLIENT_KEY` in `addonClient.ts`). Public keys are fine, but the port must decide on its own. |
+| **`versao.json` / GitHub release coupling** | The download page and the short links read the latest release of `SaimoTV/saimo-downloads` at runtime. Porting requires keeping that repository or replacing the mechanism. |
+| **Third-party content providers** | `functions/api/stream.ts` hard-codes FenixFlix / Magnetflix / FrostStream / HollyPlayer / RedeFlix / NixPlay base URLs and credentials (`NIXPLAY_USER/PASS`). These rot; the code is provider-coupled. |
+
+---
+
+## Appendix — quick file map
+
+| Area | Files |
+|---|---|
+| Boot / routing | `src/main.tsx`, `src/entrada-tv.ts`, `src/App.tsx` |
+| Live TV | `src/components/VideoPlayer.tsx`, `PainelDeCanais.tsx`, `ProgramInfo.tsx`, `ProgramGuide.tsx`, `CartaoNoAr.tsx`, `ChannelCard.tsx`, `Sidebar.tsx` |
+| On Demand | `src/components/VodCatalog.tsx`, `MoviePlayer.tsx`, `Novelinhas.tsx`, `src/telas/Inicio.tsx` |
+| Screens | `src/telas/{Inicio,Busca,Eventos,Favoritos,Radios,Ajustes,MinhasListas}.tsx` |
+| Shared UI | `src/components/{BarraTopo,Telas,Toast,Abertura,AppHeader,NavegacaoMobile,ChannelIcon,ImagemLazy,LogoCarregando,IconeMaterial,AtalhosAjuda,FormListas,DoacaoModal,DoacaoConteudo,AddonsModal,EventsModal,Novidades,AppDownload,ApoiePagina,ChuvaApoiadores}.tsx` |
+| TV / D-pad | `src/contexts/{DpadContext.tsx,dpadContexto.ts}`, `src/hooks/{useDpadNavigation,useControleTv,useVoltarFecha,useMediaSession,useKeyboardShortcuts}.ts`, `src/plataforma/tv.ts`, `src/styles/dpad-navigation.css` |
+| Profiles | `src/perfis/{perfis,kids,parental,instalar,sha256}.ts`, `EscolhaPerfil.tsx`, `ControleParental.tsx`, `PedirCodigo.tsx` |
+| Data | `src/services/{catalogService,vodService,epgService,listas,minhasListas,radios,novelinhas,infoDosTitulos,addons,debrid,fontesDesativadas}.ts`, `src/workers/epgWorker.ts` |
+| Playback helpers | `src/utils/{streamUrl,proxyUrl,segmentoDireto,hlsLoader,vigiaAoVivo,sourceOrder,fontePreferida,resolveVodSources,telaCheia,rotas,enderecoLista,guiaTabelas,nomes}.ts`, `src/services/{zeroDelay,audioInteligente,dashPlayer,castService}.ts` |
+| Server | `functions/api/{proxy,resolve-vod,stream,events}.ts`, `api/proxy.ts`, `functions/*.ts` (download shortcuts), `functions/_lib/baixar.ts` |
+| Packaging | `scripts/{montar_tv.sh,tv_para_site.sh,tv_codigo.sh,tv_es5.mjs,publicar_site.sh}`, `tv/comum/carregador.js`, `tv/tizen/config.xml`, `tv/webos/appinfo.json`, `tv-native/**` |
+| Tests | `tests/{minhasListas,xtream,enderecoLista,guiaTabelas,fontePreferida,sourceOrder,modoTv,resolveVod}.test.mjs`, `tests/dados/edge.golden.json` |
diff --git a/docs/SAIMO-TV-PARITY.md b/docs/SAIMO-TV-PARITY.md
new file mode 100644
index 0000000..68725f6
--- /dev/null
+++ b/docs/SAIMO-TV-PARITY.md
@@ -0,0 +1,255 @@
+# Paridade com o Saimo TV — o que já foi portado e o que falta
+
+**Objetivo:** usar o Saimo TV (aplicativo do `github.com/SaimoTV`, pacote LG webOS
+`SaimoTV-LG.ipk` v2.6.10 e o PWA `SaimoTV/Saimo-Site` v3.2.3) como referência de
+funcionalidade para o MK21 Play.
+
+Este documento tem três partes:
+
+1. **O que foi implementado** nesta rodada (Smart TV);
+2. **O inventário comparativo** — recurso do Saimo TV × estado do MK21;
+3. **O roteiro do que ainda falta**, em ordem de prioridade.
+
+> Os inventários completos usados como base estão em
+> `docs/MK21-FEATURE-INVENTORY.md` (MK21: Android + Smart TV + Web, 165 recursos) e
+> `docs/SAIMO-TV-FEATURE-INVENTORY.md` (Saimo TV: 61 recursos no app web +
+> app nativo de TV).
+
+---
+
+## 1. O que foi implementado nesta rodada
+
+Tudo abaixo é **Smart TV (LG webOS / Samsung Tizen)**, que é onde o MK21 tinha a
+maior defasagem: o app de TV do Saimo (`tv-native/`) tem uma tela de guia, um HUD
+de zapping e um teclado virtual próprios, e o MK21 não tinha nenhum dos três.
+
+### 1.1 Guia de Programação (tela dedicada) ✅
+
+Uma nova aba **GUIA** na lateral, com duas colunas:
+
+- **Esquerda — canais:** número do canal, logo (com placeholder por inicial),
+  nome e o que está **no ar** naquele canal, em verde.
+- **Direita — programação do canal selecionado:** faixa de horário, selo
+  **NO AR**, título, sinopse e barra de progresso do programa em andamento.
+
+Navegação 100 % pelo controle remoto: `▲▼` anda na coluna ativa, `◀▶` troca de
+coluna, `OK` abre a programação / sintoniza o canal, `VOLTAR` fecha e devolve o
+foco para a aba de origem.
+
+Arquivos: `smart-tv/mk21-tv/index.html` (`#sectionGuide`),
+`smart-tv/mk21-tv/styles.css` (bloco *GUIA DE PROGRAMAÇÃO*),
+`smart-tv/mk21-tv/src/legacy/app-legacy.js` (`mk21OpenGuide`,
+`mk21RenderGuideChannels`, `mk21RenderGuidePrograms`, `mk21SelectGuideChannel`,
+`mk21GuideMove`, `mk21GuideActivate`).
+
+### 1.2 EPG real (XMLTV) na Smart TV ✅
+
+**Este era o buraco mais fundo.** Antes desta rodada a TV:
+
+- não tinha **nenhum** carregador de XMLTV — a função `matchEPG()` existia mas
+  era código morto, porque `epgCache` nunca era preenchido;
+- só conhecia o EPG do canal **que estava tocando** (`get_short_epg` do Xtream,
+  6 programas);
+- completava o resto com uma **grade simulada** (`getSimulatedTvProgram`, com
+  programações fixas de Globo/Record/SBT).
+
+Agora a TV baixa o `xmltv.php` do servidor (ou o `url-tvg=` do header da
+playlist), indexa por canal e usa a grade real no Guia e no HUD de zapping.
+
+Details que importam na prática:
+
+- **Parser por regex, não DOMParser** — o arquivo pode passar de 50 MB e a
+  árvore DOM estouraria a memória das TVs antigas (mesmo motivo do M3U ir para
+  Web Worker). Há teto de 250 000 programas.
+- **Três formas de amarração canal ↔ EPG**, em ordem:
+  1. `tvg-id` / `epg_channel_id` (id exato do XMLTV);
+  2. id do XMLTV igual ao nome normalizado do canal;
+  3. `<display-name>` do XMLTV — cobre os painéis que **não** devolvem
+     `epg_channel_id`, que é a maioria.
+- A normalização de nome remove sufixos de qualidade (`Globo FHD` → `Globo`).
+- O `get_short_epg` do Xtream foi extraído para `mk21FetchShortEpg()` e passa a
+  ser reaproveitado: o Guia usa as 12 primeiras Programme do canal em foco.
+
+### 1.3 HUD de zapping ✅
+
+Na troca de canal (`CH +/-`, digitação de número, guia), aparece um cartão no
+topo com:
+
+- número do canal (LCN, pela ordem do catálogo) e nome;
+- logo do canal;
+- selo **NO AR** + título do programa em exibição;
+- barra de progresso do programa;
+- **A SEGUIR** + contagem regressiva até o próximo programa (atualiza a cada
+  segundo) e auto-hide em 7 s.
+
+Arquivos: `core/ZapHud.js`, `#zapHud` no `index.html`, `mk21ShowZapHud()` /
+`mk21HideZapHud()` no legado.
+
+### 1.4 Teclado virtual (D-pad) ✅
+
+A TV não tem teclado físico, e o teclado do sistema (OSK) do webOS/Tizen é
+lento. Há agora um teclado QWERTY **PT-BR** (com `ç`, `á`, `é`, `í`, `ó`, `ú`,
+`â`, `ê`, `ô`, `à`, `õ`), navegável só com o D-pad:
+
+- botão `⌨️` ao lado do campo de busca;
+- 4 modos (`abc` / `ABC` / `123` / `#+=`) e linha de controle (modo, espaço,
+  backspace, limpar);
+- a seleção **avança** para a próxima tecla depois de digitar (como teclado de
+  celular) e a borda do grid **prende** o cursor, sem dar a volta;
+- `VOLTAR` fecha sem confirmar; o campo de busca só é alterado ao confirmar.
+
+Arquivos: `core/OnScreenKeyboard.js`, `#oskOverlay` no `index.html`,
+`mk21OpenOsk()` / `mk21RenderOsk()` / `mk21OskMove()` / `mk21OskActivate()`.
+
+### 1.5 Como foi feito (para não repetir trabalho)
+
+A lógica fica em **módulos puros** em `smart-tv/mk21-tv/core/`, testáveis em
+Node, e a camada de DOM fica no legado — o mesmo padrão de `core/CatalogUtils.js`:
+
+| Módulo | Responsabilidade | Testes |
+|---|---|---|
+| `core/EpgGuide.js` | normalização de timestamps (epoch s/ms, XMLTV, ISO), merge de fontes, linha do tempo, "no ar / a seguir", contagem regressiva | `smart-tv/tests/epg-guide.test.js` (16) |
+| `core/ZapHud.js` | payload do HUD, auto-hide, numeração de canal (LCN) | `smart-tv/tests/zap-hud.test.js` (7) |
+| `core/OnScreenKeyboard.js` | grid de teclas, navegação D-pad, modos, cursor de texto | `smart-tv/tests/on-screen-keyboard.test.js` (16) |
+
+Soma-se a isso um **teste de integração real em Chromium**
+(`smart-tv/tests/tv-guide-integration.test.js`): sobe um servidor IPTV simulado
+(Xtream + XMLTV), faz login no app de verdade e confere o login, a guia com
+grade real, a navegação por D-pad, o VOLTAR, o HUD de zapping e o teclado
+virtual. O Playwright é opcional — sem ele o teste é ignorado, não quebrado.
+
+**Tamanho do bundle:** `app.js` foi de 236 KB para 255 KB (teto de 350 KB).
+
+---
+
+## 2. Inventário comparativo
+
+`✅` = no MK21 · `🆕` = implementado nesta rodada · `🟡` = existe, mas diferente
+do Saimo TV · `⬜` = falta no MK21
+
+### 2.1 Ao vivo / navegação
+
+| Recurso do Saimo TV | Estado no MK21 (TV) | Observação |
+|---|---|---|
+| Grade de canais com categorias, busca e render progressivo | ✅ | `renderItemsList()` + índice IndexedDB |
+| Múltiplas fontes por canal com failover automático | ✅ | `core/AlternativeSources.js` + failover entre servidores |
+| Watchdog de 12 s sem imagem / 25 s de congelamento | ✅ | `startStallWatchdog()` |
+| Seleção manual de fonte ("Fonte N", **sem expor a URL**) | 🟡 | O modal existe; ainda mostra a URL |
+| Canal anterior / próximo com número (LCN) | 🆕 | Número exibido no HUD de zapping |
+| Favoritos de canal | ✅ | `mk21_favs` |
+| Multi-tela / Picture-in-Picture | ⬜ | Web e Android têm PiP; a TV não |
+| Retomar o último canal ao abrir | ✅ | `resumeLastChannelIfPossible()` |
+| Volume separado entre live e VOD | ⬜ | Hoje é um volume só |
+| "Áudio Inteligente" (normalizar canais fracos) | ⬜ | O Web tem *audio booster*; a TV não |
+
+### 2.2 Guia de programação (EPG)
+
+| Recurso do Saimo TV | Estado no MK21 (TV) |
+|---|---|
+| Tela de guia com lista de canais + grade | 🆕 |
+| EPG real (XMLTV) indexado por canal | 🆕 |
+| Overlay de "no ar" e "a seguir" com contagem | 🆕 |
+| Guide com fonte pública (`guiadetv` / `meuguia`), sem credencial | ⬜ |
+| Busca do "programa que está no ar agora" | ⬜ |
+| Grade por eixo de tempo (canal × horário) | ⬜ (a coluna da direita é a grade do canal) |
+
+> O Saimo TV usa **duas** fontes de EPG HTML (`guiadetv`, `meuguia`) que
+> funcionam **sem login no servidor**, além do XMLTV. É o item de maior
+>avascripts de alto valor da lista — ver §3.1.
+
+### 2.3 On Demand / VOD
+
+| Recurso do Saimo TV | Estado no MK21 |
+|---|---|
+| Catálogo de filmes/séries com trilhas (rails) e TMDB | ✅ (opcional) |
+| Filtros por ano, faixa etária, nota e ordenação | 🟡 (ordem e filtro por categoria) |
+| Ficha do título (sinopse, elenco, relate) | ⬜ |
+| Pular abertura / recapitular / créditos | ⬜ |
+| Legendas via OpenSubtitles com ajuste de sincronia | 🟡 (escolha de faixa, sem OpenSubtitles) |
+| Retomar VOD de onde parou | ✅ (`mk21_vod_resume_progress`) |
+| "Continue assistindo" com barra de progresso | ✅ |
+| Addons (Stremio) / Debrid | ⬜ |
+| Download offline | ⬜ |
+
+### 2.4 Telas e organização
+
+| Recurso do Saimo TV | Estado no MK21 |
+|---|---|
+| "Minhas listas" (até 10 listas M3U por link/arquivo) | ⬜ (a TV só gerencia servidores Xtream) |
+| Tela de **Radios** | ⬜ |
+| Tela de **Eventos** / Novelidades | ⬜ |
+| Tela de **Doação** (Pix / QR) | ⬜ |
+| Perfil de usuário (10 perfis) + perfil infantil | ⬜ (a TV tem PIN de adulto; Android tem perfil único) |
+| Tela de Ajustes com seções | ✅ (a TV já tem um menu de configurações grande) |
+| Busca universal (canais, filmes, séries) | ✅ (Omnisearch) |
+| Tela de "Ficha" do canal (detalhes) | 🟡 (a 3ª coluna da tela principal mostra EPG e controles) |
+
+### 2.5 Android e Web
+
+| Lacuna | Plataforma |
+|---|---|
+| Sem tela de guia de EPG completa (só o diálogo de pré-visualização) | Android |
+| Sem XMLTV no Android | Android |
+| Não usa o catálogo JSON do Xtream (`get_series` com episódios reais) | Android |
+| Sem controle parental (PIN + trava de categoria adulta) | Web |
+| Sem fluxo de licença/ativação (o `api/license/*` existe e é testado, mas só o Android chama) | Web e TV |
+| Sem numeração de canal (LCN) editável | Todas |
+| Sem sincronização entre celular / TV / web (tudo é local) | Todas |
+| Sem perfis de usuário | Todas |
+| Sem catch-up / timeshift / gravação | Todas |
+
+---
+
+## 3. Roteiro recomendado
+
+### 3.1 Alta prioridade
+
+1. **Guia no Android e grade por eixo de tempo na TV.** O Guia da TV já existe:
+   falta repetir a ideia no Android (que hoje só mostra 6 programas do canal em
+   foco) e desenhar a grade `canal × horário` na TV.
+2. **EPG de fonte pública para quem não tem credencial.** O Saimo TV busca
+   `guiadetv`/`meuguia` sem login. É o que resolve a reclamação mais comum de
+   quem usa lista M3U pura: "não tem programação".
+3. **"Minhas listas" na TV.** O Saimo TV aceita até 10 listas M3U por link ou
+   arquivo; no MK21 a TV só aceita servidores Xtream. É a porta de entrada
+   para quem não tem assinatura.
+4. **Controle parental no Web.** O PIN e a trava de categoria adulta já existem
+   no Android e na TV — faltam no Web.
+
+### 3.2 Média prioridade
+
+5. **Tela de Ficha do canal** (fundo, sinopse, ações) na TV.
+6. **Tela de Radios** e **Eventos** na TV.
+7. **Volume separado entre live e VOD** e **áudio por faixa com "Em uso"**.
+8. **Números de canal (LCN) editáveis**, com salvamento por servidor — hoje o
+   número é a ordem da lista.
+9. **Tirar a URL da tela de fontes** (o Saimo TV só mostra "Fonte N", nome e
+   qualidade — uma decisão de privacidade que o MK21 ainda não segue).
+
+### 3.3 Baixa prioridade
+
+10. **Perfis de usuário** com chave por perfil (o Saimo TV remenda
+    `Storage.prototype`; no MK21 dá para usar um prefixo de chave por perfil,
+    bem mais limpo).
+11. **Legendas OpenSubtitles** com ajuste de sincronia.
+12. **Sync entre plataformas** (favoritos / continuar assistindo / progresso).
+13. **Pular abertura / recapitular / créditos** nos VOD.
+14. **Doação / Telemetria desativada** — só se fizer sentido para o negócio.
+
+---
+
+## 4. Como rodar o que foi implementado
+
+```bash
+# Rebuild do bundle da TV (app.js) — obrigatória depois de mexer em src/
+npm run build:tv
+
+# Suíte da TV (o Playwright é opcional; sem ele o teste de integração é ignorado)
+node --test smart-tv/tests/*.test.js
+npm i -D playwright && npx playwright install chromium   # opcional, p/ o teste de UI
+
+# Web: sincronizar os espelhos web/ e public/
+npm run sync:web && npm run check:web
+```
+
+O pacote `.ipk` continua sendo gerado por `python3 smart-tv/package_ipk.py`.
diff --git a/smart-tv/mk21-tv/app.js b/smart-tv/mk21-tv/app.js
index dffdcb2..4e4e096 100644
--- a/smart-tv/mk21-tv/app.js
+++ b/smart-tv/mk21-tv/app.js
@@ -1,41 +1,41 @@
 /*! MK21 Play - app.js gerado por `npm run build:tv`. Nao editar: o fonte esta em smart-tv/mk21-tv/src/. */
-"use strict";(()=>{var mo=e=>{throw TypeError(e)};var Lr=(e,t)=>()=>{try{return t||e((t={exports:{}}).exports,t),t.exports}catch(n){throw t=0,n}};var ma=(e,t,n)=>t.has(e)||mo("Cannot "+n);var F=(e,t,n)=>(ma(e,t,"read from private field"),n?n.call(e):t.get(e)),ke=(e,t,n)=>t.has(e)?mo("Cannot add the same private member more than once"):t instanceof WeakSet?t.add(e):t.set(e,n),j=(e,t,n,o)=>(ma(e,t,"write to private field"),o?o.call(e,n):t.set(e,n),n),be=(e,t,n)=>(ma(e,t,"access private method"),n);var go=(e,t,n,o)=>({set _(i){j(e,t,i,n)},get _(){return F(e,t,o)}});var wo=Lr((Hl,ko)=>{"use strict";var va={startup:0,daily:864e5,weekly:6048e5,manual:-1},Rr={startup:"A cada inicialização",daily:"Uma vez ao dia",weekly:"Uma vez por semana",manual:"Desativado (Apenas manual)"},Lo={"1h":"startup","6h":"daily","24h":"daily","a cada inicialização":"startup","a cada inicializacao":"startup","uma vez ao dia":"daily","uma vez por semana":"weekly","desativado (apenas manual)":"manual","apenas manual":"manual"};function ya(e){let t=String(e||"").trim().toLowerCase();return Object.prototype.hasOwnProperty.call(va,t)?t:Object.prototype.hasOwnProperty.call(Lo,t)?Lo[t]:"daily"}function Co(e){let t=ya(e),n=va[t];return n>0?n:0}function _r(e,t,n){let o=ya(t);if(o==="manual")return!1;let i=Number(e)||0;if(i<=0||o==="startup")return!0;let r=typeof n=="number"&&Number.isFinite(n)?n:Date.now(),s=Co(o);return r-i>=s}function Br(e,t,n){let o=e||{},i=Array.isArray(o.LIVE)?o.LIVE:[],r=Array.isArray(o.MOVIE)?o.MOVIE:[],s=Array.isArray(o.SERIES)?o.SERIES:[],c=new Set,u=0;for(let l=0;l<i.length;l++){let f=i[l];f&&(f.group&&c.add(String(f.group).trim()),(f.tvgId||f.current_program)&&u++)}for(let l=0;l<r.length;l++){let f=r[l];f&&f.group&&c.add(String(f.group).trim())}for(let l=0;l<s.length;l++){let f=s[l];f&&f.group&&c.add(String(f.group).trim())}let d=0;return t&&typeof t=="object"&&(d=Object.keys(t).length),{totalChannels:i.length,totalVOD:r.length,totalSeries:s.length,totalGroups:c.size,totalEPG:Math.max(u,d),totalItems:i.length+r.length+s.length,hasXmltv:!!n}}function Nr(e,t){let n=String(e||"").trim(),o=Number(t)||0;if(o===401||o===403){let r=new Error("Credenciais inválidas ou acesso negado (HTTP "+o+").");throw r.isAuthError=!0,r.status=o,r}if(!n)throw new Error("Servidor retornou resposta vazia.");let i=n.slice(0,200).toLowerCase();if(i.startsWith("<")||i.includes("<!doctype html")||i.includes("<html")){let r=new Error("Servidor retornou página HTML em vez de dados JSON.");throw r.isHtmlResponse=!0,r}if(/invalid username|invalid password|unauthorized/i.test(i)){let r=new Error("Usuário ou senha inválidos no servidor.");throw r.isAuthError=!0,r}try{return JSON.parse(n)}catch(r){let s=new Error("Resposta inválida do servidor (formato não reconhecido).");throw s.cause=r,s}}function Or(e){let t=e&&e.message?String(e.message):String(e||"");return t?/unexpected token\s*<?\s*in json|json\.parse/i.test(t)?"O servidor respondeu em formato HTML inválido em vez de JSON/M3U.":/failed to fetch|networkerror|load failed/i.test(t)?"Não foi possível conectar ao servidor. Verifique a rede ou troque de fonte.":/aborted|abort/i.test(t)?"Conexão cancelada ou tempo esgotado ao aguardar o servidor.":t:"A conexão foi interrompida antes da lista terminar."}ko.exports={BG_SYNC_INTERVALS_MS:va,BG_SYNC_LABELS:Rr,normalizeBgSyncFrequency:ya,getBgSyncIntervalMs:Co,shouldSyncServer:_r,computeCatalogMetrics:Br,parseSafeJsonResponse:Nr,formatFriendlyLoadError:Or}});var _n="[FBG2-Player]",Cr=()=>{var t;let e=globalThis.process;return((t=e==null?void 0:e.env)==null?void 0:t.NODE_ENV)==="development"},ho=()=>{if(Cr())return!0;try{return typeof localStorage!="undefined"&&localStorage.getItem("FBG2_DEBUG")==="1"}catch{return!1}},$={debug(...e){ho()&&console.debug(_n,...e)},info(...e){ho()&&console.info(_n,...e)},warn(...e){console.warn(_n,...e)},error(...e){console.error(_n,...e)}};var je=class{constructor(t){this.listeners=new Map;this.state="idle";if(!t)throw new Error("[FBG2] BasePlayer: elemento <video> obrigatório.");this.element=t}setVolume(t){try{this.element.volume=Math.max(0,Math.min(1,t))}catch(n){$.warn("setVolume falhou:",n)}}setSubtitleTrack(t){}getState(){return this.state}getBufferedMs(){let t=this.element.buffered;if(!t||t.length===0)return 0;let n=this.element.currentTime||0;for(let o=0;o<t.length;o+=1)if(t.start(o)<=n&&n<=t.end(o))return Math.max(0,(t.end(o)-n)*1e3);return 0}on(t,n){let o=this.listeners.get(t);o||(o=new Set,this.listeners.set(t,o)),o.add(n)}off(t,n){var o;(o=this.listeners.get(t))==null||o.delete(n)}setState(t){this.state=t}emit(t,n){let o=this.listeners.get(t);o&&o.forEach(i=>{try{i(n)}catch(r){$.error(`Listener de "${t}" lançou exceção:`,r)}})}finishDestroy(){this.setState("idle"),this.emit("destroyed"),this.teardownListeners()}teardownListeners(){this.listeners.clear()}};var Bn=(e,t,n)=>Math.max(t,Math.min(e,n)),vo=e=>{if(!e)return{};if(typeof e=="object")return e;try{let t=JSON.parse(e);return t&&typeof t=="object"?t:{}}catch{return{}}},un=class extends je{constructor(n){var o;super(n);this.platform="tizen";this.audioTracks=[];this.destroyed=!1;this.preparing=!1;this.tracksReady=!1;this.bufferedMs=0;this.applyDisplayRect=()=>{let n=this.element.getBoundingClientRect(),o=Math.round(n.width),i=Math.round(n.height);o<=0||i<=0||this.setDisplay({left:n.left,top:n.top,width:o,height:i})};if(!((o=window.webapis)!=null&&o.avplay))throw new Error("[FBG2] webapis.avplay indisponível neste dispositivo.");this.avplay=window.webapis.avplay,this.element.classList.add("fbg2-avplay-surface"),window.addEventListener("resize",this.applyDisplayRect),document.addEventListener("fullscreenchange",this.applyDisplayRect),document.addEventListener("webkitfullscreenchange",this.applyDisplayRect)}async play(n,o={}){var i,r,s,c,u;this.safeClose(),this.destroyed=!1,this.tracksReady=!1,this.preparing=!0,this.setState("preparing");try{this.avplay.open(n),this.avplay.setListener(this.buildListener());let d=(i=o.bufferSizeMs)!=null?i:3e3;try{this.avplay.setStreamingProperty("BUFFER_SIZE",String(d)),$.debug("BUFFER_SIZE =",d)}catch{$.warn("setStreamingProperty(BUFFER_SIZE) não suportado neste modelo.")}let l=Bn(Math.round(d/1e3),3,30);try{(s=(r=this.avplay).setBufferingParam)==null||s.call(r,"PLAYER_BUFFER_FOR_PLAY","PLAYER_BUFFER_SIZE_IN_SECOND",l),(u=(c=this.avplay).setBufferingParam)==null||u.call(c,"PLAYER_BUFFER_FOR_RESUME","PLAYER_BUFFER_SIZE_IN_SECOND",Math.max(l,15)),$.debug("setBufferingParam =",l,"s")}catch{$.warn("setBufferingParam não suportado neste modelo.")}if(this.applyDisplayRect(),await this.prepareAsync(),this.destroyed)return;this.extractTracks(),o.startPositionMs&&o.startPositionMs>0&&this.avplay.seekTo(Bn(o.startPositionMs,0,this.getDuration()||o.startPositionMs)),this.avplay.play(),this.preparing=!1,this.setState("playing"),this.emit("playing"),$.info("AVPlay reproduzindo:",n)}catch(d){this.preparing=!1,this.setState("error"),this.safeClose();let l=d instanceof Error?d.message:String(d);throw this.emit("error",{code:"AVPLAY_OPEN",message:l,fatal:!0,raw:d}),d}}pause(){try{this.avplay.getState()==="PLAYING"&&(this.avplay.pause(),this.setState("paused"),this.emit("paused"))}catch(n){$.warn("pause() falhou:",n)}}resume(){try{this.avplay.getState()==="PAUSED"&&(this.avplay.play(),this.setState("playing"),this.emit("playing"))}catch(n){$.warn("resume() falhou:",n)}}seek(n){try{let o=this.getDuration(),i=o>0?Bn(n,0,o):Math.max(0,n);this.avplay.seekTo(i)}catch(o){$.warn("seek() falhou:",o)}}stop(){this.safeClose(),this.setState("idle")}destroy(){this.destroyed||(this.destroyed=!0,this.safeClose(),window.removeEventListener("resize",this.applyDisplayRect),document.removeEventListener("fullscreenchange",this.applyDisplayRect),document.removeEventListener("webkitfullscreenchange",this.applyDisplayRect),this.finishDestroy())}setAudioTrack(n){let o=this.audioTracks.find(i=>i.id===n);if(!o){$.warn("Faixa de áudio desconhecida:",n);return}try{this.avplay.setSelectTrack("AUDIO",o.index),$.debug("Áudio selecionado:",o.name)}catch(i){$.warn("setSelectTrack falhou:",i)}}setVolume(n){try{this.avplay.setVolume(Bn(n,0,1))}catch(o){$.warn("setVolume falhou:",o)}}getState(){return this.preparing?"preparing":super.getState()}getBufferedMs(){return this.bufferedMs}getDuration(){try{return this.avplay.getDuration()||0}catch{return 0}}getCurrentTime(){try{return this.avplay.getCurrentTime()||0}catch{return 0}}setDisplay(n){try{this.avplay.setDisplayRect(Math.round(n.left),Math.round(n.top),Math.round(n.width),Math.round(n.height))}catch(o){$.warn("setDisplayRect falhou:",o)}}prepareAsync(){return new Promise((n,o)=>{this.avplay.prepareAsync(()=>{this.preparing=!1,n()},i=>{this.preparing=!1,o(i instanceof Error?i:new Error(String(i)))})})}safeClose(){try{this.avplay.getState()!=="IDLE"&&this.avplay.stop()}catch{}try{this.avplay.close()}catch{}this.preparing=!1}buildListener(){return{onbufferingstart:()=>{this.bufferedMs=0,this.setState("buffering"),this.emit("buffering")},onbufferingprogress:n=>{this.bufferedMs=Math.round(n),$.debug("buffering:",n+"%")},onbufferingcomplete:()=>{this.bufferedMs=0,this.setState("playing"),this.emit("buffered")},onstreamcompleted:()=>{this.setState("ended"),this.emit("ended")},oncurrentplaytime:n=>{this.tracksReady||this.extractTracks(),this.emit("timeupdate",{currentTimeMs:n,durationMs:this.getDuration()})},onerror:n=>{this.setState("error"),this.emit("error",{code:n,message:`AVPlay error: ${n}`,fatal:!0})},onevent:(n,o)=>{$.debug("onevent:",n,o)},ondrmevent:(n,o)=>{$.debug("drmevent:",n,o)},onsubtitlechange:(n,o)=>{$.debug("subtitle:",n,o)}}}extractTracks(){var i,r;let n=[];try{n=(i=this.avplay.getTotalTrackInfo())!=null?i:[]}catch{}if(!n||n.length===0)try{n=(r=this.avplay.getCurrentStreamInfo())!=null?r:[]}catch(s){$.warn("getCurrentStreamInfo falhou:",s);return}this.audioTracks=(n!=null?n:[]).filter(s=>s.type==="AUDIO").map((s,c)=>{var b,g,h;let u={...vo(s.info),...vo(s.extra_info)},d=(b=u.track_num)!=null?b:u.track_index,l=Number(d),f=Number.isFinite(l)&&l>=0?l:typeof s.index=="number"&&s.index>=0?s.index:c,p=String((h=(g=u.track_lang)!=null?g:u.language)!=null?h:"und"),m=u.track_name?String(u.track_name):"";return{id:`audio-${f}`,kind:"audio",index:f,name:m||`Áudio (${p.toUpperCase()})`,language:p}}),this.audioTracks.length>0&&(this.tracksReady=!0);let o={audioTracks:this.audioTracks,subtitleTracks:[]};this.emit("tracksReady",o)}};var fn=class extends je{constructor(n){super(n);this.platform="webos";this.audioTracks=[];this.subtitleTracks=[];this.lastError=null;this.handleLoadedMetadata=()=>{this.extractTracks(),this.emit("ready")};this.handleWaiting=()=>{this.setState("buffering"),this.emit("buffering")};this.handlePlaying=()=>{this.setState("playing"),this.emit("buffered")};this.handleEnded=()=>{this.setState("ended"),this.emit("ended")};this.handleError=()=>{var n,o,i,r;this.lastError=this.element.error,this.setState("error"),this.emit("error",{code:(o=(n=this.lastError)==null?void 0:n.code)!=null?o:"UNKNOWN",message:`webOS Video error: code ${(r=(i=this.lastError)==null?void 0:i.code)!=null?r:"desconhecido"}`,fatal:!0,raw:this.lastError})};this.handleTimeUpdate=()=>{this.emit("timeupdate",{currentTimeMs:this.getCurrentTime(),durationMs:this.getDuration()})};n.addEventListener("loadedmetadata",this.handleLoadedMetadata),n.addEventListener("waiting",this.handleWaiting),n.addEventListener("playing",this.handlePlaying),n.addEventListener("ended",this.handleEnded),n.addEventListener("error",this.handleError),n.addEventListener("timeupdate",this.handleTimeUpdate)}async play(n,o={}){try{if(this.stop(),this.lastError=null,this.setState("preparing"),this.element.src=n,o.headers&&$.warn("webOS <video> nativo não suporta headers customizados."),this.element.load(),o.startPositionMs&&o.startPositionMs>0){let i=()=>{var s;let r=Math.min((s=o.startPositionMs)!=null?s:0,(this.element.duration||1/0)*1e3);this.element.currentTime=r/1e3};this.element.addEventListener("loadedmetadata",i,{once:!0})}await this.element.play(),this.setState("playing"),this.emit("playing"),$.info("webOS reproduzindo:",n)}catch(i){if(i instanceof Error&&i.name==="AbortError"){$.debug("play() abortado por nova carga.");return}this.setState("error");let r=i instanceof Error?i.message:String(i);throw this.emit("error",{code:"WEBOS_PLAY",message:r,fatal:!0,raw:i}),i}}pause(){this.element.pause(),this.setState("paused"),this.emit("paused")}resume(){let n=()=>{this.setState("playing"),this.emit("playing")},o=this.element.play();if(o&&typeof o.then=="function"){o.then(n).catch(i=>{let r=i instanceof Error?i.message:String(i);this.emit("error",{code:"WEBOS_RESUME",message:r,fatal:!1,raw:i})});return}n()}seek(n){let o=this.element.duration||0,i=o>0?Math.max(0,Math.min(n/1e3,o)):Math.max(0,n/1e3);this.element.currentTime=i}stop(){this.element.pause(),this.element.removeAttribute("src");try{this.element.load()}catch{}this.setState("idle")}destroy(){this.stop(),this.element.removeEventListener("loadedmetadata",this.handleLoadedMetadata),this.element.removeEventListener("waiting",this.handleWaiting),this.element.removeEventListener("playing",this.handlePlaying),this.element.removeEventListener("ended",this.handleEnded),this.element.removeEventListener("error",this.handleError),this.element.removeEventListener("timeupdate",this.handleTimeUpdate),this.finishDestroy()}setAudioTrack(n){let o=this.audioTracks.find(r=>r.id===n);if(!o){$.warn("Faixa de áudio desconhecida:",n);return}let i=this.element.audioTracks;if(!i){$.warn("audioTracks não exposto por este firmware webOS.");return}for(let r=0;r<i.length;r+=1){let s=i[r];s&&(s.enabled=r===o.index)}$.debug("Áudio selecionado:",o.name)}setSubtitleTrack(n){var i;let o=this.element.textTracks;for(let r=0;r<o.length;r+=1){let s=o[r];s&&(s.mode=n&&((i=this.subtitleTracks.find(c=>c.id===n))==null?void 0:i.index)===r?"showing":"disabled")}}setVolume(n){this.element.volume=Math.max(0,Math.min(1,n))}getDuration(){let n=this.element.duration;return Number.isFinite(n)?n*1e3:0}getCurrentTime(){return(this.element.currentTime||0)*1e3}setDisplay(n){Object.assign(this.element.style,{position:"absolute",left:`${n.left}px`,top:`${n.top}px`,width:`${n.width}px`,height:`${n.height}px`})}extractTracks(){this.audioTracks=[],this.subtitleTracks=[];let n=this.element.audioTracks;if(n)for(let r=0;r<n.length;r+=1){let s=n[r];s&&this.audioTracks.push({id:`audio-${r}`,kind:"audio",index:r,name:s.label||s.language||`Áudio ${r+1}`,language:s.language||"und"})}let o=this.element.textTracks;for(let r=0;r<o.length;r+=1){let s=o[r];s&&(s.kind!=="subtitles"&&s.kind!=="captions"||this.subtitleTracks.push({id:`subtitle-${r}`,kind:"subtitle",index:r,name:s.label||s.language||`Legenda ${r+1}`,language:s.language||"und"}))}let i={audioTracks:this.audioTracks,subtitleTracks:this.subtitleTracks};this.emit("tracksReady",i)}};var pn=typeof window!="undefined"?window.Hls:void 0,ga=pn,rt=pn&&pn.Events||{},Nn=pn&&pn.ErrorTypes||{};var kr=e=>/\.m3u8([?#]|$)/i.test(e)||/[?&]format=m3u8/i.test(e),wr={maxBufferLength:30,maxMaxBufferLength:60,liveSyncDurationCount:3,manifestLoadingMaxRetry:2,levelLoadingMaxRetry:2,fragLoadingMaxRetry:4,startLevel:-1},Gt=class extends je{constructor(n){super(n);this.platform="browser";this.hls=null;this.usingNativeMedia=!1;this.audioTracks=[];this.subtitleTracks=[];this.handleWaiting=()=>{this.setState("buffering"),this.emit("buffering")};this.handleBuffered=()=>{this.setState("playing"),this.emit("buffered")};this.handleEnded=()=>{this.setState("ended"),this.emit("ended")};this.handleVideoError=()=>{let n=this.element.error;this.usingNativeMedia&&n&&(this.setState("error"),this.emit("error",{code:n.code,message:`midia nativa error: code ${n.code}`,fatal:!0,raw:n}))};n.addEventListener("waiting",this.handleWaiting),n.addEventListener("playing",this.handleBuffered),n.addEventListener("ended",this.handleEnded),n.addEventListener("error",this.handleVideoError)}async play(n,o={}){try{this.stop(),this.setState("preparing");let i=kr(n),r=i&&ga.isSupported(),s=i&&!!this.element.canPlayType("application/vnd.apple.mpegurl");if(r)this.playWithHlsJs(n,o),await this.waitForManifest();else if(s||!i)this.playNative(n,o);else throw new Error("[FBG2] Nenhum engine HLS disponível neste dispositivo.");o.startPositionMs&&o.startPositionMs>0&&this.element.addEventListener("loadedmetadata",()=>{var c;this.element.currentTime=((c=o.startPositionMs)!=null?c:0)/1e3},{once:!0}),await this.element.play(),this.setState("playing"),r||this.emitTracks(),this.emit("playing")}catch(i){if(i instanceof Error&&i.name==="AbortError"){$.debug("play() abortado por nova carga.");return}this.setState("error");let r=i instanceof Error?i.message:String(i);throw this.emit("error",{code:"HLS_PLAY",message:r,fatal:!0,raw:i}),i}}pause(){this.element.pause(),this.setState("paused"),this.emit("paused")}resume(){let n=()=>{this.setState("playing"),this.emit("playing")},o=this.element.play();if(o&&typeof o.then=="function"){o.then(n).catch(i=>{let r=i instanceof Error?i.message:String(i);this.emit("error",{code:"HLS_RESUME",message:r,fatal:!1,raw:i})});return}n()}seek(n){let o=this.element.duration||0,i=o>0?Math.max(0,Math.min(n/1e3,o)):Math.max(0,n/1e3);this.element.currentTime=i}stop(){this.hls&&(this.hls.destroy(),this.hls=null),this.usingNativeMedia=!1,this.element.pause(),this.element.removeAttribute("src");try{this.element.load()}catch{}this.setState("idle")}destroy(){this.stop(),this.element.removeEventListener("waiting",this.handleWaiting),this.element.removeEventListener("playing",this.handleBuffered),this.element.removeEventListener("ended",this.handleEnded),this.element.removeEventListener("error",this.handleVideoError),this.finishDestroy()}setAudioTrack(n){var r;let o=this.audioTracks.find(s=>s.id===n),i=o?o.index:Number(n.replace("audio-",""));this.hls&&Number.isInteger(i)&&i>=0?(this.hls.audioTrack=i,$.debug("Áudio selecionado:",(r=o==null?void 0:o.name)!=null?r:n)):$.warn("setAudioTrack sem hls.js ativo:",n)}setSubtitleTrack(n){var i;let o=this.element.textTracks;for(let r=0;r<o.length;r+=1){let s=o[r];s&&(s.mode=n&&((i=this.subtitleTracks.find(c=>c.id===n))==null?void 0:i.index)===r?"showing":"disabled")}}setVolume(n){this.element.volume=Math.max(0,Math.min(1,n))}getDuration(){let n=this.element.duration;return Number.isFinite(n)?n*1e3:0}getCurrentTime(){return(this.element.currentTime||0)*1e3}waitForManifest(n=15e3){let o=this.hls;return!o||o.levels&&o.levels.length>0?Promise.resolve():new Promise((i,r)=>{let s,c=()=>{s&&clearTimeout(s),o.off(rt.MANIFEST_PARSED,u),o.off(rt.ERROR,d)},u=()=>{c(),i()},d=(l,f)=>{!f.fatal||f.type===Nn.NETWORK_ERROR||(c(),r(new Error(`hls.js fatal: ${f.type}/${f.details}`)))};s=setTimeout(()=>{c(),r(new Error("timeout aguardando o manifesto HLS"))},n),o.on(rt.MANIFEST_PARSED,u),o.on(rt.ERROR,d)})}playWithHlsJs(n,o){let i={...wr};o.headers&&Object.keys(o.headers).length>0&&(i.xhrSetup=r=>{var s;Object.entries((s=o.headers)!=null?s:{}).forEach(([c,u])=>r.setRequestHeader(c,u))}),this.hls=new ga(i),this.hls.loadSource(n),this.hls.attachMedia(this.element),this.hls.on(rt.MANIFEST_PARSED,()=>{$.debug("Manifesto HLS carregado."),this.emit("ready"),this.emitTracks()}),this.hls.on(rt.AUDIO_TRACKS_UPDATED,()=>this.emitTracks()),this.hls.on(rt.ERROR,(r,s)=>{var c,u;if(s.fatal){switch($.error("Erro fatal hls.js:",s.type,s.details),s.type){case Nn.NETWORK_ERROR:try{(c=this.hls)==null||c.startLoad();return}catch{}break;case Nn.MEDIA_ERROR:try{(u=this.hls)==null||u.recoverMediaError();return}catch{}break;default:break}this.setState("error"),this.emit("error",{code:s.details,message:`hls.js fatal: ${s.type}/${s.details}`,fatal:!0,raw:s})}})}emitTracks(){var s,c,u;let n=this.hls;this.audioTracks=[],this.subtitleTracks=[],((s=n==null?void 0:n.audioTracks)!=null?s:[]).forEach((d,l)=>{this.audioTracks.push({id:`audio-${l}`,kind:"audio",index:l,name:d.name||d.lang||`Áudio ${l+1}`,language:d.lang||"und"})}),this.audioTracks.length===0&&((u=(c=n==null?void 0:n.levels)==null?void 0:c.length)!=null?u:0)>0&&this.audioTracks.push({id:"audio-0",kind:"audio",index:0,name:"Áudio principal",language:"und"});let i=this.element.textTracks;for(let d=0;d<i.length;d+=1){let l=i[d];l&&(l.kind!=="subtitles"&&l.kind!=="captions"||this.subtitleTracks.push({id:`subtitle-${d}`,kind:"subtitle",index:d,name:l.label||l.language||`Legenda ${d+1}`,language:l.language||"und"}))}let r={audioTracks:this.audioTracks,subtitleTracks:this.subtitleTracks};this.emit("tracksReady",r)}playNative(n,o){this.usingNativeMedia=!0,this.element.src=n,this.element.load(),$.info("Usando reprodução nativa (sem MSE):",n)}};var yo=()=>{var e;return typeof window!="undefined"&&!!((e=window.webapis)!=null&&e.avplay)},_e=class _e{static detectPlatform(){if(yo())return"tizen";let t=typeof navigator!="undefined"?navigator.userAgent:"",n=/Tizen/i.test(t)||/SMART-TV/i.test(t)||/Samsung.*TV/i.test(t),o=typeof window!="undefined"&&typeof window.tizen!="undefined";if(n&&o||o)return"tizen";let i=typeof window!="undefined"&&(typeof window.webOS!="undefined"||typeof window.PalmSystem!="undefined"),r=/Web0S/i.test(t)||/webOS/i.test(t)||/LG.*TV/i.test(t);return i&&r||i?"webos":"browser"}static create(t,n){var i;if(!t)throw new Error("[FBG2] PlayerFactory.create: elemento <video> obrigatório.");let o=n!=null?n:_e.detectPlatform();switch($.info("Criando player para plataforma:",o),o){case"tizen":{if(!yo())return $.warn("webapis.avplay ausente — degradando para HLS/MSE no Tizen."),new Gt(t);if(_e.tizen&&_e.tizen.element===t)return _e.tizen.player;(i=_e.tizen)==null||i.player.destroy();let r=new un(t);return _e.tizen={element:t,player:r},r}case"webos":return new fn(t);default:return new Gt(t)}}static releaseTizen(){var t;(t=_e.tizen)==null||t.player.destroy(),_e.tizen=null}};_e.tizen=null;var bt=_e;var bo="FBG2_PLAYER_LEGACY",So="FBG2_PLAYER_ENGINE",Tr=!0,Ar="https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json",Ir=4e3,Pr=/\.(m3u8|mpd|mp4|m4v|webm|mov)([?#]|$)/i,Mr=e=>{let t=e&&(e.message||e.msg)||"";return(e&&e.name||"")==="AbortError"||/interrupt|abort/i.test(t)},On=e=>{try{return localStorage.getItem(e)}catch{return null}},qt,H,Wt,St,st,jt,mn,gn,Et,xt,hn,ie,we,Eo,xo,Dn=class Dn{constructor(){ke(this,ie);ke(this,H,null);ke(this,Wt,null);ke(this,St,!1);ke(this,st,!1);ke(this,jt,0);ke(this,mn,4e3);ke(this,gn,[]);ke(this,Et,null);ke(this,xt,null);ke(this,hn,{});j(this,xt,null)}static get instance(){return F(this,qt)||j(this,qt,new Dn),F(this,qt)}setHooks(t){return j(this,hn,t||{}),this}get useFactory(){if(On(bo)==="1")return!1;let t=String(On(So)||"").toLowerCase();if(t==="legacy")return!1;if(t==="factory")return!0;let n=String(F(this,xt)||"").toLowerCase();return n==="legacy"?!1:n==="factory"?!0:Tr}get engineReason(){if(On(bo)==="1")return"legado (FBG2_PLAYER_LEGACY=1)";let t=String(On(So)||"").toLowerCase();if(t==="legacy")return"legado (FBG2_PLAYER_ENGINE local)";if(t==="factory")return"factory (FBG2_PLAYER_ENGINE local)";let n=String(F(this,xt)||"").toLowerCase();return n==="legacy"?"legado (version.json)":n==="factory"?"factory (version.json)":"factory (default)"}get player(){return F(this,H)}get platform(){try{return bt.detectPlatform()}catch{return"browser"}}isActive(){return!!(F(this,st)&&F(this,H))}tryPlay(t,n){if(!this.useFactory||!n||!Pr.test(String(n)))return!1;let o;try{o=be(this,ie,Eo).call(this)}catch(s){return console.warn("[PlayerService] factory indisponível, seguindo no legado:",s),!1}if(!o)return!1;j(this,st,!0);let i=++go(this,jt)._,r=t&&t.currentTime>5&&(t.contentType==="MOVIE"||t.contentType==="SERIES")?Math.round(t.currentTime*1e3):0;return be(this,ie,we).call(this,"onFactoryStart",{url:n,platform:o.platform,reason:this.engineReason}),Promise.resolve(o.play(n,{bufferSizeMs:F(this,mn),startPositionMs:r})).catch(s=>{i===F(this,jt)&&(Mr(s)||be(this,ie,we).call(this,"onError",{message:s&&s.message||String(s),fatal:!0,raw:s},!0))}),!0}pause(){return this.isActive()?(F(this,H).pause(),!0):!1}resume(){return this.isActive()?(F(this,H).resume(),!0):!1}toggle(){return this.isActive()?(F(this,H).getState&&F(this,H).getState()==="paused"?F(this,H).resume():F(this,H).pause(),!0):!1}seekMs(t){if(!this.isActive())return!1;let n=Math.max(0,Math.round(Number(t)||0));return F(this,H).seek(n),!0}stop(){if(j(this,jt,F(this,jt)+1),j(this,st,!1),!!F(this,H))try{F(this,H).stop()}catch(t){console.warn("[PlayerService] stop falhou:",t)}}release(){if(this.stop(),F(this,H))try{F(this,H).destroy()}catch(t){console.warn("[PlayerService] destroy falhou:",t)}j(this,H,null),j(this,Wt,null),j(this,St,!1),bt.releaseTizen()}setBufferMs(t){let n=Number(t);!Number.isFinite(n)||n<=0||j(this,mn,Math.max(3e3,Math.min(3e4,Math.round(n))))}getAudioTracks(){return F(this,gn)}getActiveAudioId(){return F(this,Et)}setAudioTrack(t){if(!F(this,H)||!t)return!1;j(this,Et,t);try{return F(this,H).setAudioTrack(t),!0}catch(n){return console.warn("[PlayerService] setAudioTrack falhou:",n),!1}}getCurrentTimeSec(){if(!F(this,H))return 0;try{return(F(this,H).getCurrentTime()||0)/1e3}catch{return 0}}getDurationSec(){if(!F(this,H))return 0;try{return(F(this,H).getDuration()||0)/1e3}catch{return 0}}async hydrateRemote(){let t=typeof AbortController=="function"?new AbortController:null,n=t?setTimeout(()=>t.abort(),Ir):null;try{let o=await fetch(Ar+"?t="+Date.now(),{cache:"no-store",signal:t?t.signal:void 0});if(!o||!o.ok)return;let i=await o.json(),r=i&&i.playerEngine?String(i.playerEngine).toLowerCase():"";(r==="factory"||r==="legacy")&&(j(this,xt,r),be(this,ie,we).call(this,"onEngineFlag",{engine:r,reason:this.engineReason}))}catch{}finally{n&&clearTimeout(n)}}};qt=new WeakMap,H=new WeakMap,Wt=new WeakMap,St=new WeakMap,st=new WeakMap,jt=new WeakMap,mn=new WeakMap,gn=new WeakMap,Et=new WeakMap,xt=new WeakMap,hn=new WeakMap,ie=new WeakSet,we=function(t,...n){let o=F(this,hn)[t];if(typeof o=="function")try{return o(...n)}catch(i){console.warn("[PlayerService] hook "+t+" falhou:",i);return}},Eo=function(){let t=typeof document!="undefined"?document.getElementById("tvPlayer"):null;if(!t)return null;if(F(this,H)&&F(this,Wt)===t)return F(this,H);if(F(this,H)){try{F(this,H).destroy()}catch{}j(this,H,null),j(this,St,!1)}return j(this,Wt,t),j(this,H,bt.create(t)),be(this,ie,xo).call(this),F(this,H)},xo=function(){if(F(this,St)||!F(this,H))return;let t=F(this,H);t.on("buffering",()=>be(this,ie,we).call(this,"onBuffering")),t.on("buffered",()=>be(this,ie,we).call(this,"onPlaying")),t.on("playing",()=>be(this,ie,we).call(this,"onPlaying")),t.on("paused",()=>be(this,ie,we).call(this,"onPaused")),t.on("ended",()=>be(this,ie,we).call(this,"onEnded")),t.on("durationchange",()=>be(this,ie,we).call(this,"onDuration")),t.on("timeupdate",n=>be(this,ie,we).call(this,"onTimeUpdate",n)),t.on("tracksReady",n=>{let o=n&&n.audioTracks||[];j(this,gn,o),!F(this,Et)&&o.length>0&&j(this,Et,o[0].id),be(this,ie,we).call(this,"onTracks",n)}),t.on("error",n=>{let o=!n||n.fatal!==!1;o&&j(this,st,!1),be(this,ie,we).call(this,"onError",n,o)}),t.on("destroyed",()=>{j(this,st,!1)}),j(this,St,!0)},ke(Dn,qt,null);var ha=Dn,Fn=ha.instance;typeof window!="undefined"&&(window.FBG2PlayerService=Fn);var Ce=wo(),a=e=>document.getElementById(e);window.mk21App={playStream:function(e,t){return de(e,t)},stopCurrentPlayer:function(e){return wn(e)},switchContentType:function(e){return qe(e)},openAudioSubsModal:function(){return Be()},logout:function(){return sn()},initWebOSScrollFix:function(){return sr()},get activeItem(){return v},get playerService(){return window.FBG2PlayerService}};var ba=[];(function(){let t=window.FBG2PlayerService;if(!t||typeof t.setHooks!="function"){console.warn("[PlayerService] ausente: app seguira no caminho legado.");return}t.setHooks({bufferSizeMs:function(){let n=Ge[vt()],o=n&&n.hls&&n.hls.maxBufferLength?n.hls.maxBufferLength:4;return Math.max(3e3,o*1e3)},onFactoryStart:function(n){x("[Player Engine] PlayerFactory ("+(n&&n.platform)+") em: "+(n&&n.url),"info"),x("[Player Engine] Motor escolhido: "+(n&&n.reason),"debug")},onBuffering:function(){a("epgStatus")&&(a("epgStatus").textContent="⏳ CARREGANDO...")},onPlaying:function(){et(),Le=0,Ct=Date.now(),Oi(!0),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(v)},onPaused:function(){typeof se=="function"&&se(!1)},onEnded:function(){typeof se=="function"&&se(!1)},onTracks:function(n){ba=n&&n.audioTracks||[],ba.length>0&&x("[Player Engine] "+ba.length+" faixa(s) de audio na factory.","info")},onTimeUpdate:function(){typeof mt=="function"&&mt()},onError:function(n,o){let i=n&&(n.message||n.code)||"erro desconhecido";x("[Player Engine] Erro da factory: "+i,o?"error":"warn"),o&&_t("Erro do player (factory)")},onEngineFlag:function(n){x("[Player Engine] Flag remota: "+(n&&n.engine)+" -> "+(n&&n.reason),"info")}})})();function Kt(e,t){let n=a("splashProgressBar"),o=a("splashStatusText");n&&(n.style.width=e+"%"),o&&t&&(o.textContent=t)}function De(){let e=a("startupSplashScreen");e&&(Kt(100,"Tudo Pronto!"),e.classList.add("fade-out"),e.style.opacity="0",e.style.display="none",e.style.pointerEvents="none",setTimeout(()=>{try{e&&e.parentNode&&e.parentNode.removeChild(e)}catch{}},200))}setTimeout(De,3500);Element.prototype.replaceChildren||(Element.prototype.replaceChildren=function(...e){for(;this.firstChild;)this.removeChild(this.firstChild);this.append(...e)});var Ot=[{id:"vlog",name:"VLOG",url:"http://tocombox21.cyou"},{id:"tannix",name:"TANNIX",url:"http://ovo54.sbs"},{id:"lubtv",name:"LUB TV",url:"http://coxinhasemflango.shop"},{id:"mk21pro",name:"MK21 PRÓ",url:"http://app.tigerouro.shop"},{id:"cb6000",name:"CB6000",url:"http://cdn.caterlune.top"},{id:"cinelon",name:"CINELON",url:"http://parcerialife.lat"},{id:"multttv",name:"MULTT TV",url:"http://dali-as.skin"}];function X(e){return String(e||"").replace(/^[^\w\dÀ-ÿ]+/i,"").trim()||e}var L=Ot.map(e=>({...e}));(function(){try{let t=localStorage.getItem("mk21_servers_list");if(!t)return;let n=JSON.parse(t);if(!Array.isArray(n)||n.length===0)return;let o=Object.create(null),i=Object.create(null);Ot.forEach(c=>{o[c.id]=c,i[X(c.name).toUpperCase()]=c});let r=[],s=new Set;n.forEach(c=>{if(!c||!c.url)return;let u=X(c.name||""),d=o[c.id]||i[u.toUpperCase()]||null;d&&!c.isCustomOverride?(r.push({...d}),s.add(d.id)):(r.push(c),c.id&&s.add(c.id))}),Ot.forEach(c=>{s.has(c.id)||r.push({...c})}),r.length>0&&(L=r,localStorage.setItem("mk21_servers_list",JSON.stringify(L)))}catch{}})();var Ua="mk21_credentials";function Pn(){try{let e=localStorage.getItem(Ua);if(!e)return null;let t=JSON.parse(e);if(t&&typeof t.user=="string"&&typeof t.pass=="string"){let n=t.user.trim(),o=t.pass.trim();if(n.length>0&&o.length>0)return{user:n,pass:o,serverIndex:typeof t.serverIndex=="number"?t.serverIndex:null}}}catch{}return null}function Dr(e,t,n){let o=String(e||"").trim(),i=String(t||"").trim(),r={user:o,pass:i,serverIndex:typeof n=="number"?n:_,savedAt:Date.now()};try{localStorage.setItem(Ua,JSON.stringify(r)),localStorage.setItem("mk21_user",o),localStorage.setItem("mk21_username",o),localStorage.setItem("iptv_user",o),localStorage.setItem("mk21_pass",i),localStorage.setItem("mk21_password",i),localStorage.setItem("iptv_pass",i)}catch{}}function Fr(){try{localStorage.removeItem(Ua),localStorage.removeItem("mk21_auth_token"),localStorage.removeItem("mk21_user"),localStorage.removeItem("mk21_username"),localStorage.removeItem("iptv_user"),localStorage.removeItem("mk21_pass"),localStorage.removeItem("mk21_password"),localStorage.removeItem("iptv_pass")}catch{}}var To=["18+","+18","ADULTO","ADULTOS","ADULT","XXX","SEXY","PLAYBOY","PENTHOUSE","VENUS","HOT ","HUSTLER","FORBIDDEN","FORA DA LEI","S0X","PRIVE","PRIVÉ","RED LIGHT","SEX","EROTIC","ERÓTICO","EROTICO","BRASILEIRINHAS","TIGRESA","CINEMA 18","CINE 18","SEXTREME"],ri="mk21_play_db_v6",Vr=1,nn="catalog_cache_v6";function si(){return new Promise(e=>{if(!window.indexedDB){e(null);return}let t=indexedDB.open(ri,Vr);t.onupgradeneeded=n=>{let o=n.target.result;o.objectStoreNames.contains(nn)||o.createObjectStore(nn,{keyPath:"id"})},t.onsuccess=n=>e(n.target.result),t.onerror=()=>e(null)})}var $r=400,Ao=0;function Gn(e){return e&&(typeof e.isAdult=="undefined"&&(e.isAdult=ne(e.name)||ne(e.group)),e)}function En(e,t,n){try{if(!t||!t.length)return;let o=Date.now();if(!n&&o-Ao<4e3)return;Ao=o;let i=t.slice(0,$r).map(en),r=JSON.stringify({items:i,updatedAt:o,total:t.length});localStorage.setItem("mk21_fast_cache_"+e,r),localStorage.setItem("mk21_fast_cache_time_"+e,String(o)),localStorage.setItem("mk21_fast_cache_universal",JSON.stringify({serverId:e,items:i,updatedAt:o}))}catch(o){console.warn("[MK21] save fast cache falhou",o)}}function Zt(e){let t=o=>{try{let i=localStorage.getItem(o);if(!i)return null;let r=JSON.parse(i),s=Array.isArray(r)?r:r&&Array.isArray(r.items)?r.items:null;return!s||!s.length?null:{items:s.map(Gn),updatedAt:r&&r.updatedAt||0,total:r&&r.total||s.length}}catch{return null}},n=t("mk21_fast_cache_"+e)||t("mk21_fast_cache_universal");return n?{LIVE:n.items,MOVIE:[],SERIES:[],_totalLive:n.total,_updatedAt:n.updatedAt}:null}async function an(e){try{let t=await si();if(t){let n=await new Promise(o=>{let r=t.transaction(nn,"readonly").objectStore(nn).get(e);r.onsuccess=()=>o(r.result?r.result.payload:null),r.onerror=()=>o(null)});try{t.close()}catch{}if(n&&n.LIVE&&n.LIVE.length>0)return n.LIVE.forEach(Gn),(n.MOVIE||[]).forEach(Gn),(n.SERIES||[]).forEach(Gn),n._updatedAt=Date.now(),n}}catch{}return Zt(e)}async function li(e,t){t&&t.LIVE&&t.LIVE.length>0&&En(e,t.LIVE,!0);let n=(t&&t.LIVE||[]).length+(t&&t.MOVIE||[]).length+(t&&t.SERIES||[]).length;if(typeof nt=="function"&&nt(e,t),n>4e4){console.log("[MK21] catálogo grande ("+n+"): cache completo no índice paginado");return}try{let o=await si();if(!o)return;let i={LIVE:(t.LIVE||[]).map(en),MOVIE:(t.MOVIE||[]).map(en),SERIES:(t.SERIES||[]).map(en)};o.transaction(nn,"readwrite").objectStore(nn).put({id:e,payload:i,updatedAt:Date.now()})}catch(o){console.warn("[MK21] save cache falhou",o)}}var ci="mk21_catalog_index_v1",Hr=1,Pe="items",on="meta";function Mn(){return new Promise(e=>{try{if(!window.indexedDB)return e(null);let t=indexedDB.open(ci,Hr);t.onupgradeneeded=n=>{let o=n.target.result;if(!o.objectStoreNames.contains(Pe)){let i=o.createObjectStore(Pe,{keyPath:"key"});i.createIndex("serverType",["serverId","contentType"],{unique:!1}),i.createIndex("serverGroup",["serverId","contentType","group"],{unique:!1}),i.createIndex("serverName",["serverId","contentType","normalizedName"],{unique:!1})}o.objectStoreNames.contains(on)||o.createObjectStore(on,{keyPath:"serverId"})},t.onsuccess=n=>e(n.target.result),t.onerror=()=>e(null)}catch{e(null)}})}async function Ma(e){let t=await Mn();if(!t)return{add(){},async finish(){}};try{await new Promise((s,c)=>{let u=t.transaction(Pe,"readwrite"),l=u.objectStore(Pe).index("serverType").openCursor(IDBKeyRange.bound([e,"LIVE"],[e,"SERIES"]));l.onsuccess=f=>{let p=f.target.result;p&&(p.delete(),p.continue())},u.oncomplete=s,u.onerror=()=>c(u.error)})}catch(s){console.warn("[MK21] limpeza do índice falhou",s)}let n=[],o=Promise.resolve(),i=0,r=()=>{if(!n.length)return;let s=n;n=[],o=o.then(()=>new Promise(c=>{try{let u=t.transaction(Pe,"readwrite"),d=u.objectStore(Pe);s.forEach(l=>d.put({key:e+":"+l.contentType+":"+i++,serverId:e,contentType:l.contentType,name:l.name||"Item",normalizedName:String(l.name||"").toLocaleLowerCase("pt-BR"),group:l.group||"Geral",logo:l.logo||"",tvgId:l.tvgId||"",tvgName:l.tvgName||"",url:l.url,isAdult:!!l.isAdult,position:i-1})),u.oncomplete=c,u.onerror=c}catch{c()}}))};return{add(s){n.push(s),n.length>=500&&r()},async finish(){r(),await o;try{await new Promise(s=>{let c=t.transaction(on,"readwrite");c.objectStore(on).put({serverId:e,updatedAt:Date.now(),itemCount:i}),c.oncomplete=s,c.onerror=s})}catch{}try{t.close()}catch{}}}}async function zr(e,t,n,o,i){let r=await Mn();return r?new Promise(s=>{let c=[];try{let u=r.transaction(Pe,"readonly"),d=u.objectStore(Pe).index("serverType"),l=IDBKeyRange.bound([e,t],[e,t+"￿"]),f=d.openCursor(l),p=0;f.onsuccess=m=>{let b=m.target.result;if(!b||c.length>=o)return;let g=b.value;(!i||g.group===i)&&(p<n?p++:c.push(g)),b.continue()},u.oncomplete=()=>{try{r.close()}catch{}s(c)},u.onerror=()=>{try{r.close()}catch{}s(c)}}catch{try{r.close()}catch{}s([])}}):[]}async function Ur(e,t,n,o,i=240){let r=await Mn();return!r||!n?[]:new Promise(s=>{let c=[],u=n.toLocaleLowerCase("pt-BR");try{let d=r.transaction(Pe,"readonly"),l=d.objectStore(Pe).index("serverType"),f=IDBKeyRange.bound([e,t],[e,t+"￿"]),p=l.openCursor(f);p.onsuccess=m=>{let b=m.target.result;if(!b||c.length>=i)return;let g=b.value;(!o||g.group===o)&&String(g.normalizedName||"").includes(u)&&c.push(g),b.continue()},d.oncomplete=()=>{try{r.close()}catch{}s(c)},d.onerror=()=>{try{r.close()}catch{}s(c)}}catch{try{r.close()}catch{}s([])}})}var Io=0,di="";async function ui(e){let t=S;if(!["LIVE","MOVIE","SERIES"].includes(t)||!e)return;let n=++Io,o=L[_];if(!o)return;let i=Re==="ALL"?"":Re,r=await Ur(o.id,t,e,i,240);n!==Io||(a("inputSearch")&&a("inputSearch").value||"").toLowerCase().trim()!==e||(V=r,Qe=240,a("badgeItemsCount").textContent=V.length,Y())}async function Gr(e,t,n=1e3){let o=await Mn();return!o||!t||!t.size?[]:new Promise(i=>{let r=[];try{let s=o.transaction(Pe,"readonly"),u=s.objectStore(Pe).index("serverType").openCursor(IDBKeyRange.bound([e,"LIVE"],[e,"SERIES￿"]));u.onsuccess=d=>{let l=d.target.result;if(!l||r.length>=n)return;let f=l.value;t.has(f.url)&&r.push(f),l.continue()},s.oncomplete=()=>{try{o.close()}catch{}i(r)},s.onerror=()=>{try{o.close()}catch{}i(r)}}catch{try{o.close()}catch{}i([])}})}var Ra=[],Po=0;async function qr(){let e=L[_];if(!e||S!=="FAVORITES")return;let t=++Po,n=await Gr(e.id,z,1e3);t!==Po||S!=="FAVORITES"||(Ra=n,Q(),te(),Ie("ALL"))}var Sa=!1,Mt={};async function Mo(){if(Sa)return;let e=S;if(!["LIVE","MOVIE","SERIES"].includes(e)){Qe+=60,Y();return}let t=he[Re]||[];if(Qe<t.length){Qe+=60,Y();return}let n=L[_];if(n){Sa=!0;try{let o=w[e]||[],i=Re==="ALL"?"":Re,r=e+"|"+(i||"ALL"),s=i?o.filter(d=>(d.group||"Geral")===i).length:o.length,c=Number.isFinite(Mt[r])?Mt[r]:s,u=await zr(n.id,e,c,240,i);if(Mt[r]=c+u.length,u.length){let d=new Set(o.map(l=>l.url));u.forEach(l=>{d.has(l.url)||o.push(l)}),Qe+=60,Q(),te(),Y()}else E("Fim do catálogo nesta categoria")}finally{Sa=!1}}}function en(e){return e&&{name:e.name,group:e.group,url:e.url,contentType:e.contentType,logo:e.logo?String(e.logo).slice(0,200):"",tvgId:e.tvgId||"",tvgName:e.tvgName||"",rating:e.rating||"",isAdult:!!e.isAdult}}var _=0,me=0,ge=null,Xe=null,He=null,Jn=!1,At=null,xn=!1,S="LIVE",w={LIVE:[],MOVIE:[],SERIES:[]},z=new Set,fi="mk21_bg_sync_frequency",pi="mk21_sync_all_lists",mi="mk21_hide_bg_progress",gi="mk21_fg_live",hi="mk21_fg_movies",vi="mk21_fg_series",yi="mk21_srv_metrics_",qn=Object.create(null);function Vt(){try{return Ce.normalizeBgSyncFrequency(localStorage.getItem(fi)||"daily")}catch{return"daily"}}function Wr(e){let t=Ce.normalizeBgSyncFrequency(e);try{localStorage.setItem(fi,t)}catch{}return da(),typeof at=="function"&&at(),t}function Rn(){try{let e=localStorage.getItem(pi);return e===null?!0:e!=="false"}catch{return!0}}function jr(e){let t=!!e;try{localStorage.setItem(pi,t?"true":"false")}catch{}return typeof at=="function"&&at(),t}function tt(){try{return localStorage.getItem(mi)==="true"}catch{return!1}}function Kr(e){let t=!!e;try{localStorage.setItem(mi,t?"true":"false")}catch{}return typeof at=="function"&&at(),t}function Ga(){try{let e=localStorage.getItem(gi)!=="false",t=localStorage.getItem(hi)==="true",n=localStorage.getItem(vi)==="true";return!e&&!t&&!n&&(e=!0),{live:e,movies:t,series:n}}catch{return{live:!0,movies:!1,series:!1}}}function Yr(e){let t=!!(e&&e.live),n=!!(e&&e.movies),o=!!(e&&e.series);!t&&!n&&!o&&(t=!0);try{localStorage.setItem(gi,t?"true":"false"),localStorage.setItem(hi,n?"true":"false"),localStorage.setItem(vi,o?"true":"false")}catch{}return typeof at=="function"&&at(),{live:t,movies:n,series:o}}function qa(e){let t=e||Ga(),n=[];return t.live&&n.push("Canais"),t.movies&&n.push("Filmes"),t.series&&n.push("Séries"),n.length===3?"Todos no 1º Plano":n.length===0?"1º Plano: Canais":"1º Plano: "+n.join(", ")}function Wa(e){if(!e)return null;if(qn[e])return qn[e];try{let t=localStorage.getItem(yi+e);if(!t)return null;let n=JSON.parse(t);if(n&&typeof n=="object")return qn[e]=n,n}catch{}return null}function Xr(e,t){if(!e||!t)return null;let n={totalChannels:Number(t.totalChannels)||0,totalVOD:Number(t.totalVOD)||0,totalSeries:Number(t.totalSeries)||0,totalGroups:Number(t.totalGroups)||0,totalEPG:Number(t.totalEPG)||0,totalItems:Number(t.totalItems)||0,updatedAt:Number(t.updatedAt)||Date.now()};qn[e]=n;try{localStorage.setItem(yi+e,JSON.stringify(n))}catch{}return n}function nt(e,t,n,o){if(!e||!t)return null;let i=Ce.computeCatalogMetrics(t,n||null,!!o);i.updatedAt=Date.now();let r=Xr(e,i);return typeof ta=="function"&&ta(e,r),r}function ja(){let e=typeof epgCache!="undefined"&&epgCache?epgCache:null;return Ce.computeCatalogMetrics(w,e,!!window.serverXmltvUrl)}function $t(e={}){let t=!!(e&&e.preserveCatalog);if(He&&(clearTimeout(He),He=null),ge){try{ge.abort()}catch{}ge=null}if(Xe){try{Xe.abort()}catch{}Xe=null}me++,ut=!1,typeof Nt!="undefined"&&(Nt=0),typeof Mt!="undefined"&&(Mt={}),t||(w={LIVE:[],MOVIE:[],SERIES:[]},typeof he!="undefined"&&(he={ALL:[]}),typeof Ze!="undefined"&&(Ze=["ALL"]),typeof V!="undefined"&&(V=[])),typeof _a=="function"&&_a()}function Jr(e){if(!(!e||!e.url))try{localStorage.setItem("mk21_last_channel",JSON.stringify({url:e.url,name:e.name,group:e.group||"",contentType:e.contentType||S,serverId:(L[_]||{}).id||""}))}catch{}}function Qr(){try{let e=localStorage.getItem("mk21_last_channel");return e?JSON.parse(e):null}catch{return null}}function bi(){let e=Qr(),t=w&&w.LIVE?w.LIVE:[],n=null;if(e&&e.url&&(n=t.find(o=>o.url===e.url||e.name&&o.name===e.name)||e),!n&&t.length>0&&(n=t[0]),n){S="LIVE";try{["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabSettings"].forEach(o=>{a(o)&&a(o).classList.remove("active")}),a("tabLive")&&a("tabLive").classList.add("active")}catch{}return de(n),E("▶ Sintonizando: "+n.name),!0}return!1}function Zr(){try{localStorage.setItem("mk21_sort_order",Ne)}catch{}}function es(){try{let e=localStorage.getItem("mk21_sort_order");e&&["DEFAULT","RECENT","AZ","ZA","YEAR"].indexOf(e)!==-1&&(Ne=e,typeof Qn=="function"&&Qn())}catch{}}var Ro=null,_o=!1,yn=null;function ts(){let e=a("inputSearch");e&&(document.activeElement&&document.activeElement!==e&&(yn=document.activeElement),A="search")}function It(e=!1){let t=a("inputSearch");if(e&&t&&(t.value=""),e&&(di="",Q(),te(),Y()),t)try{t.blur()}catch{}A="channels";let n=yn&&Me(yn)?yn:a("listItems")&&a("listItems").querySelector(".list-item-btn:not([disabled])")||a("listCategories")&&a("listCategories").querySelector(".cat-item-btn.active")||a("tabLive");yn=null,n?Oe(n):Se();let o=a("tvPlayer");if(o&&_o){_o=!1;try{o.play().catch(()=>{})}catch{}}}function ns(){clearTimeout(Ro),Ro=setTimeout(function(){let e=a("inputSearch"),t=e?e.value.toLowerCase().trim():"";di=t,Y(),t?ui(t):(Q(),te(),Y())},180)}var Bo=null;function oe(){le()&&(ea(!0),clearTimeout(Bo),Bo=setTimeout(function(){le()&&(typeof rn=="function"&&rn()||ea(!1))},5500))}var G=[],Ue=!1,Ke="0000",Fe="",v=null,C=null,Ne="DEFAULT",Dt=1,Ea=[.5,1,1.5,2,4],he={},Ze=[],Re="ALL",V=[],Lt=[],A="channels",Ka=0,Si=0,Ei=0,as=0,os=0,is=0,rs=null,tn=["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabSettings","btnHeaderServer","btnHeaderLogs","btnRefreshList","btnHeaderLogout"];function xi(){let e=new Date,t=String(e.getHours()).padStart(2,"0"),n=String(e.getMinutes()).padStart(2,"0"),o=String(e.getSeconds()).padStart(2,"0");a("liveClock")&&(a("liveClock").textContent=`${t}:${n}:${o}`)}setInterval(xi,1e3);xi();try{let e=localStorage.getItem("mk21_favs");e&&(z=new Set(JSON.parse(e)))}catch{}function ra(e){if(e){z.has(e)?(z.delete(e),typeof gt=="function"&&gt(!1),E("Removido dos Favoritos")):(z.add(e),typeof gt=="function"&&gt(!0),E("⭐ Adicionado aos Favoritos"));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(z)))}catch{}try{if(v&&v.url===e&&a("epgTitle")){let t=z.has(e)?"⭐ ":"▶ ";a("epgTitle").textContent=t+v.name}if(a("fsHudTitle")&&v){let t=z.has(v.url)?"⭐ ":"";a("fsHudTitle").textContent=t+v.name}}catch{}Y()}}try{let e=localStorage.getItem("mk21_continue_watching");e&&(G=JSON.parse(e))}catch{}function ss(e){if(!(!e||!e.url)&&e.contentType!=="LIVE"){G=G.filter(t=>t.url!==e.url),G.unshift({name:e.name,group:e.group,logo:e.logo,url:e.url,contentType:e.contentType,timestamp:Date.now()}),G.length>50&&(G=G.slice(0,50));try{localStorage.setItem("mk21_continue_watching",JSON.stringify(G))}catch{}}}function ne(e){if(!e)return!1;let t=e.toUpperCase();for(let n=0;n<To.length;n++)if(t.includes(To[n]))return!0;return!1}function No(e,t,n){let o=(e||"").toUpperCase().trim(),i=(t||"").toUpperCase().trim(),r=(n||"").toUpperCase().trim();if(r.includes("/SERIES/"))return"SERIES";if(r.includes("/MOVIE/"))return"MOVIE";if(r.includes("/LIVE/"))return"LIVE";let s=/(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i.test(o)||o.includes("TEMPORADA")||o.includes("TEMP.")||o.includes("CAPITULO")||o.includes("CAPÍTULO")||o.includes("EPISODIO")||o.includes("EPISÓDIO");if(i.startsWith("SERIES")||i.startsWith("SÉRIES")||i.startsWith("SERIE")||i.startsWith("SÉRIE")||i.startsWith("NOVELAS")||i.startsWith("NOVELA")||i.startsWith("ANIMES")||i.startsWith("ANIME")||i.startsWith("DORAMAS")||i.startsWith("DORAMA")||i.startsWith("MINISSÉRIES")||i.startsWith("MINISSERIES")||i.includes("SERIES |")||i.includes("SÉRIES |")||i.includes("SERIES:")||i.includes("SÉRIES:")||i.includes("SERIADOS")||s)return"SERIES";let u=/\[ALT\]|\(ALT\)|\[BACKUP\]|\(BACKUP\)|\[OFF\]|\bFHD\b|\bUHD\b|\b4K\b|\bHD\b|\bSD\b|\bHEVC\b|\bH\.?265\b|\b1080P\b|\b720P\b|\b480P\b/i.test(o),l=["A&E","AMC","ANIMAL PLANET","DISCOVERY","HISTORY","NAT GEO","NATIONAL GEOGRAPHIC","TELECINE","MEGAPIX","WARNER","UNIVERSAL","STUDIO UNIVERSAL","TNT","SPACE","AXN","PARAMOUNT","SONY","STAR CHANNEL","FX","HBO","CINEMAX","LIFETIME","TCM","CANAL BRASIL","GLOBO","SBT","RECORD","BAND","REDE TV","CULTURA","PREMIERE","SPORTV","ESPN","FOX SPORTS","COMBATE","BANDSPORTS","CONMEBOL","VIVA","MULTISHOW","GNT","OFF","BIS","GLOOB","CARTOON","NICKELODEON","NICK JR","BOOMERANG","TOONCAST","CNN","GLOBO NEWS","BANDNEWS","RECORD NEWS","JOVEM PAN","TV CULTURA","TV BRASIL"].some(y=>o.startsWith(y+" ")||o.startsWith(y+"-")||o.startsWith(y+":")||o===y||o.includes(" "+y+" ")||o.includes("["+y+"]")||o.includes("("+y+")")),f=r.endsWith(".TS")||r.includes(".TS?")||r.endsWith(".M3U8")||r.includes(".M3U8?")||r.includes("/LIVE/"),p=r.endsWith(".MP4")||r.includes(".MP4?")||r.endsWith(".MKV")||r.includes(".MKV?")||r.endsWith(".AVI")||r.includes(".AVI?");if((u||l)&&!p)return"LIVE";if(p||(/\b\((?:19|20)\d{2}\)\b/.test(o)||/\b\[(?:19|20)\d{2}\]\b/.test(o))&&!u&&!l)return"MOVIE";let b=i.startsWith("VOD")||i.startsWith("ON DEMAND")||i.startsWith("ONDEMAND")||i.startsWith("LANÇAMENTOS")||i.startsWith("LANCAMENTOS")||i.startsWith("FILMES 202")||i.startsWith("FILMES 19")||i.includes("VOD |")||i.includes("VOD:")||i.includes("CINEMA |")||i.includes("4K FILMES")||i.includes("FHD FILMES"),g=i.includes("CANAIS")||i.includes("CANAL")||i.includes("AO VIVO")||i.includes("AOVIVO")||i.includes("24H")||i.includes("24 HORAS")||i.includes("FILMES E SERIES")||i.includes("FILMES E SÉRIES")||i.includes("DOCUMENTÁRIOS")||i.includes("DOCUMENTARIOS")||i.includes("TV ABERTA")||i.includes("ABERTOS")||i.includes("ESPORTES")||i.includes("SPORTS")||i.includes("NOTICIAS")||i.includes("NEWS");return b&&!g&&!f||(i.includes("NETFLIX")||i.includes("AMAZON")||i.includes("PRIME VIDEO")||i.includes("HBO MAX")||i.includes("DISNEY+")||i.includes("APPLE TV")||i.includes("PARAMOUNT+")||i.includes("GLOBOPLAY"))&&!g&&!u&&!f?"MOVIE":"LIVE"}var ls=/(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i;function cs(e){let t={};return e.forEach(n=>{let o=n.name,i=n.name.match(ls);if(i&&i.index>0)o=n.name.substring(0,i.index).trim();else{let r=n.name.match(/(?:\s*[-–:]?\s*\b(?:ep|e|cap)\.?\s*\d+\b.*)/i);r&&r.index>0&&(o=n.name.substring(0,r.index).trim())}o=o.replace(/[-_:,/ ]+$/,"").trim()||n.name,t[o]||(t[o]={title:o,group:n.group,logo:n.logo,episodes:[]}),t[o].episodes.push(n)}),Object.values(t).sort((n,o)=>n.title.localeCompare(o.title))}async function ds(e,t,n){let o=0,i=g=>{o++,t(g)};if(!e.body||typeof e.body.getReader!="function"||typeof TextDecoder=="undefined"){let g=await e.text();if(!g||g.length<30)throw new Error("Lista M3U vazia");let h=g.trim().slice(0,250).toLowerCase();if(h.includes("<!doctype html")||h.includes("<html"))throw new Error("Servidor retornou página HTML em vez de M3U");if(h.includes("invalid username")||h.includes("invalid password")){let T=new Error("Usuário ou senha inválidos no servidor");throw T.isAuthError=!0,T}let y=g.split(/\r?\n/),M=null;for(let T of y){let I=T.trim();if(I.startsWith("#EXTM3U")){let P=I.match(/(?:url-tvg|x-tvg-url)="([^"]*)"/i);P&&P[1]&&(window.serverXmltvUrl=P[1].trim())}else if(I.startsWith("#EXTINF:")){let P=I.lastIndexOf(","),B=I.match(/group-title="([^"]*)"/i),R=I.match(/tvg-logo="([^"]*)"/i),k=I.match(/tvg-id="([^"]*)"/i),N=I.match(/tvg-name="([^"]*)"/i);M={name:P>=0?I.slice(P+1).trim():"Item",group:B&&B[1]?B[1].trim():"Geral",logo:R&&R[1]?R[1].trim():"",tvgId:k&&k[1]?k[1].trim():"",tvgName:N&&N[1]?N[1].trim():""}}else if(M&&/^https?:\/\//i.test(I)){let P=No(M.name,M.group,I);i({...M,url:I,contentType:P,isAdult:ne(M.name)||ne(M.group)}),M=null}}if(o===0)throw new Error("Lista M3U sem canais válidos");return o}let r=e.body.getReader(),s=new TextDecoder("utf-8"),c="",u="",d=!1,l=null,f=0,p=parseInt(e.headers&&e.headers.get&&e.headers.get("content-length")||"0",10)||0,m=(g,h)=>{let y=h+'="',M=g.indexOf(y);if(M===-1)return"";let T=M+y.length,I=g.indexOf('"',T);return I!==-1?g.slice(T,I).trim():""},b=g=>{let h=g.trim();if(h){if(h.charCodeAt(0)===35){if(h.startsWith("#EXTINF:")){let y=h.lastIndexOf(",");l={name:y>=0?h.slice(y+1).trim():"Item",group:m(h,"group-title")||"Geral",logo:m(h,"tvg-logo"),tvgId:m(h,"tvg-id"),tvgName:m(h,"tvg-name")}}else if(h.startsWith("#EXTM3U")){let y=m(h,"url-tvg")||m(h,"x-tvg-url");y&&(window.serverXmltvUrl=y)}}else if(l&&(h.startsWith("http://")||h.startsWith("https://"))){let y=No(l.name,l.group,h);i({...l,url:h,contentType:y,isAdult:ne(l.name)||ne(l.group)}),l=null}}};for(;;){let g=await r.read();if(g.done)break;f+=g.value.byteLength||g.value.length||0;let h=s.decode(g.value,{stream:!0});if(!d&&(u+=h.slice(0,320),u.length>=60)){d=!0;let M=u.trim().toLowerCase();if(M.includes("<!doctype html")||M.includes("<html")){try{r.cancel()}catch{}throw new Error("Servidor retornou página HTML em vez de M3U")}if(M.includes("invalid username")||M.includes("invalid password")){try{r.cancel()}catch{}let T=new Error("Usuário ou senha inválidos no servidor");throw T.isAuthError=!0,T}}c+=h;let y=c.split(/\r?\n/);c=y.pop()||"";for(let M of y)b(M);n&&n(f,p)}if(c+=s.decode(),c&&b(c),o===0)throw new Error("Lista M3U vazia (0 itens)");return o}async function us(e,t,n,o,i,r){let s=e.replace(/\/+$/,""),c=encodeURIComponent(t),u=encodeURIComponent(n),d=`${s}/player_api.php?username=${c}&password=${u}`,l=async(T,I=2e4)=>{let P=typeof AbortController!="undefined"?new AbortController:null,B=P?setTimeout(()=>P.abort(),I):null,R=()=>{P&&P.abort()};o&&o.addEventListener&&o.addEventListener("abort",R,{once:!0});try{let k=await fetch(T,P?{signal:P.signal,mode:"cors"}:{mode:"cors"}),N=await k.text();if(!k.ok&&k.status!==401&&k.status!==403)throw new Error("HTTP "+k.status);return Ce.parseSafeJsonResponse(N,k.status)}finally{B&&clearTimeout(B),o&&o.removeEventListener&&o.removeEventListener("abort",R)}};x("[Rede] Tentando API Xtream em "+s+"/player_api.php","info");let f=await l(d,1e4);if(f&&f.user_info&&Number(f.user_info.auth)===0){let T=new Error("Usuário ou senha inválidos/expirados neste servidor.");throw T.isAuthError=!0,T}let p=0,m=localStorage.getItem("mk21_stream_format")==="hls"?"m3u8":"ts",[b,g]=await Promise.all([l(d+"&action=get_live_categories",2e4).catch(()=>[]),l(d+"&action=get_live_streams",3e4)]),h=Object.create(null);if(Array.isArray(b)&&b.forEach(T=>{T&&T.category_id!=null&&(h[String(T.category_id)]=T.category_name||"Ao Vivo")}),Array.isArray(g)){for(let T=0;T<g.length;T++){let I=g[T];if(!I||!I.stream_id)continue;let P=h[String(I.category_id)]||I.category_name||"Geral",B=I.name||"Canal "+I.stream_id;i({name:B,group:P,logo:I.stream_icon||"",tvgId:I.epg_channel_id||"",tvgName:B,url:`${s}/live/${c}/${u}/${I.stream_id}.${m}`,contentType:"LIVE",isAdult:ne(B)||ne(P)||Number(I.is_adult)===1}),p++}r&&r(40,100)}if(localStorage.getItem("mk21_filter_load_movies")!=="false")try{let[T,I]=await Promise.all([l(d+"&action=get_vod_categories",15e3).catch(()=>[]),l(d+"&action=get_vod_streams",35e3).catch(()=>[])]),P=Object.create(null);if(Array.isArray(T)&&T.forEach(B=>{B&&B.category_id!=null&&(P[String(B.category_id)]=B.category_name||"Filmes")}),Array.isArray(I))for(let B=0;B<I.length;B++){let R=I[B];if(!R||!R.stream_id)continue;let k=P[String(R.category_id)]||R.category_name||"Filmes",N=R.container_extension||"mp4",O=R.name||"Filme "+R.stream_id;i({name:O,group:k,logo:R.stream_icon||"",tvgId:"",tvgName:O,rating:R.rating_5based||R.rating||"",url:`${s}/movie/${c}/${u}/${R.stream_id}.${N}`,contentType:"MOVIE",isAdult:ne(O)||ne(k)||Number(R.is_adult)===1}),p++}r&&r(75,100)}catch{}if(localStorage.getItem("mk21_filter_load_series")!=="false")try{let[T,I]=await Promise.all([l(d+"&action=get_series_categories",15e3).catch(()=>[]),l(d+"&action=get_series",35e3).catch(()=>[])]),P=Object.create(null);if(Array.isArray(T)&&T.forEach(B=>{B&&B.category_id!=null&&(P[String(B.category_id)]=B.category_name||"Séries")}),Array.isArray(I))for(let B=0;B<I.length;B++){let R=I[B];if(!R||!R.series_id)continue;let k=P[String(R.category_id)]||R.category_name||"Séries",N=R.name||"Série "+R.series_id;i({name:N,group:k,logo:R.cover||"",tvgId:"",tvgName:N,rating:R.rating_5based||R.rating||"",url:`${s}/series/${c}/${u}/${R.series_id}.xtream`,contentType:"SERIES",isAdult:ne(N)||ne(k)}),p++}r&&r(95,100)}catch{}if(p===0)throw new Error("API Xtream respondeu vazia (0 itens)");return x("[Rede] Catálogo carregado via player_api.php ("+p+" itens)","success"),p}async function Li(e,t,n,o){let i=String(e||"").trim(),r=Pn()||{user:"",pass:""},s=r.user,c=r.pass,u=i.includes("get.php")||i.includes(".m3u")||i.includes(".ts")||i.includes(".m3u8");if(!u&&(!s||!c))throw new Error("Credenciais ausentes. Faça login novamente.");let d=localStorage.getItem("mk21_stream_format")==="hls"?"hls":"ts",l=i.replace(/\/+$/,""),f=encodeURIComponent(s),p=encodeURIComponent(c),m=u?[i]:[`${l}/get.php?username=${f}&password=${p}&type=m3u_plus&output=${d}`,`${l}/get.php?username=${f}&password=${p}&type=m3u_plus&output=mpegts`,`${l}/get.php?username=${f}&password=${p}&type=m3uplus&output=ts`,`${l}/get.php?username=${f}&password=${p}`],b=null;for(let g=0;g<m.length;g++){if(t&&t.aborted){let P=new Error("Aborted");throw P.name="AbortError",P}let h=m[g],y=Ri(h);console.log("[MK21] Streaming da lista:",y),x("[Rede] Tentando lista: "+y,"debug");let M=typeof AbortController!="undefined"?new AbortController:null,T=M?setTimeout(()=>M.abort(),45e3):null,I=()=>{M&&M.abort()};t&&t.addEventListener&&t.addEventListener("abort",I,{once:!0});try{let P=M?{signal:M.signal,mode:"cors"}:{mode:"cors"},B=await fetch(h,P);if(B.status===401||B.status===403){let k=new Error("Credenciais inválidas ou bloqueadas pelo servidor (HTTP "+B.status+").");throw k.isAuthError=!0,k.status=B.status,x("[Rede] "+y+" -> HTTP "+B.status+" (credenciais)","error"),k}if(!B.ok){let k=new Error("HTTP "+B.status);x("[Rede] "+y+" -> HTTP "+B.status,"warn"),b=k;continue}let R=await ds(B,n,o);x("[Rede] Lista M3U recebida ("+R+" itens)","success");return}catch(P){if(P&&P.name==="AbortError"&&t&&t.aborted||P&&P.isAuthError)throw P;b=P,x("[Rede] Falha em "+y+": "+(P&&P.message?P.message:P),"warn")}finally{T&&clearTimeout(T),t&&t.removeEventListener&&t.removeEventListener("abort",I)}}if(!u)try{await us(l,s,c,t,n,o);return}catch(g){if(g&&g.name==="AbortError"&&t&&t.aborted)throw g;b=g,x("[Rede] Fallback player_api.php falhou: "+(g&&g.message?g.message:g),"warn")}throw b||new Error("Não foi possível baixar a lista do servidor")}function ct(e,t=!1,n={}){let o=a("txtServerSyncBadge"),i=a("txtActiveServer"),r=L[_],s=r?r.name:"Servidor";if(!!(n&&n.background)&&tt()){o&&o.classList.add("hidden"),i&&(i.textContent=X(s));return}if(t)o&&(o.textContent="✓ 100%",o.classList.remove("hidden"),o.classList.add("done"),setTimeout(()=>{o&&o.classList.add("hidden")},4e3)),i&&(i.textContent=X(s));else{let d=`${e}%`;o&&(o.textContent=d,o.classList.remove("hidden"),o.classList.remove("done")),i&&(i.textContent=`${X(s)} (${d})`)}}function _a(){let e=a("hudLoadingOverlay"),t=e&&e.querySelector(".hud-loading-card"),n=a("hudLoadingError"),o=a("btnRetryLoading");t&&t.classList.remove("is-error"),n&&(n.textContent="",n.classList.add("hidden")),o&&(o.classList.add("hidden"),o.onclick=()=>{$t({preserveCatalog:!0}),ve(!0)});let i=e&&e.querySelector(".spinner-ring");i&&(i.style.display="")}function fs(e,t){let n=a("hudLoadingOverlay"),o=n&&n.querySelector(".hud-loading-card"),i=a("hudLoadingError"),r=a("btnRetryLoading"),s=a("hudLoadingActions");if(n){if(o&&o.classList.add("is-error"),a("hudLoadingTitle")&&(a("hudLoadingTitle").textContent=e&&e.isAuthError?"Falha na autenticação do servidor":"Não foi possível carregar a lista"),a("hudLoadingSub")&&(a("hudLoadingSub").textContent=`Servidor: ${t||"atual"}`),a("hudProgressBar")&&(a("hudProgressBar").style.width="0%"),a("hudProgressPercent")&&(a("hudProgressPercent").textContent="Falha"),i){let c=Ce.formatFriendlyLoadError(e);i.textContent=`${c} Seus dados não foram apagados.`,i.classList.remove("hidden")}if(s&&!a("btnHudRelogin")){let c=document.createElement("button");c.id="btnHudRelogin",c.type="button",c.className="ctrl-btn",c.style.cssText="padding: 10px 18px; font-size: 15px; border-color: #ffd54f; color: #ffd54f;",c.tabIndex=0,c.textContent="🔐 Trocar Login",s.appendChild(c)}a("btnHudRelogin")&&(a("btnHudRelogin").onclick=()=>sn()),r&&(r.classList.remove("hidden"),r.onclick=()=>{$t({preserveCatalog:!0}),ve(!0)},setTimeout(()=>r.focus(),0)),n.classList.remove("hidden")}}var ut=!1;async function ps(e){let t=await Mn();if(!t)return null;try{return await new Promise(n=>{let i=t.transaction(on,"readonly").objectStore(on).get(e);i.onsuccess=()=>n(i.result||null),i.onerror=()=>n(null)})}catch{return null}finally{try{t.close()}catch{}}}async function ve(e=!1,t={}){let n=!!(t&&t.silent),o=!!(t&&t.backgroundLowPriority);if(n&&ut)return;He&&(clearTimeout(He),He=null),(!t||!t.fromAutoAdvance)&&(Nt=0);let i=++me;if(ge)try{ge.abort()}catch{}ge=typeof AbortController!="undefined"?new AbortController:null,L.length===0&&(L=[...Ot]),_>=L.length&&(_=0);let r=L[_];a("txtActiveServer")&&(a("txtActiveServer").textContent=X(r.name)),n||ct(15,!1);let s=a("hudLoadingOverlay");if(!n&&!e){let d=await an(r.id);if((!d||!d.LIVE||d.LIVE.length===0)&&(d=Zt(r.id)),i!==me)return;let l=d&&d.LIVE?d.LIVE.length:0,f=d&&d.MOVIE?d.MOVIE.length:0,p=d&&d.SERIES?d.SERIES.length:0;if(d&&l+f+p>0){w={LIVE:d.LIVE||[],MOVIE:d.MOVIE||[],SERIES:d.SERIES||[]},s&&s.classList.add("hidden"),De(),Q(),te(),Ie("ALL"),Y(),ct(100,!0),a("txtCurrentCategoryTitle")&&(a("txtCurrentCategoryTitle").textContent="📺 Memória: "+l.toLocaleString("pt-BR")+" canais"),E("✓ Abrindo da memória local ("+l+" canais). Sincronizando o resto..."),setTimeout(()=>{bi()},150);let m=0;try{m=parseInt(localStorage.getItem("mk21_last_sync_"+r.id)||"0",10)||0}catch{}let b=m>0&&Date.now()-m<1200*1e3,g=f+p===0;!b||g?setTimeout(()=>{me===i&&ve(!0,{silent:!0})},700):ct(100,!0);return}}if(!n){let d=a("tvPlayer");if(d)try{d.pause(),d.src=""}catch{}if(C)try{C.destroy(),C=null}catch{}if(U){try{U.pause(),U.unload(),U.detachMediaElement(),U.destroy()}catch{}U=null}v=null,Kt(25,"Conectando ao "+r.name+"..."),_a(),s&&(a("hudLoadingTitle")&&(a("hudLoadingTitle").textContent="Conectando ao "+r.name),a("hudLoadingSub")&&(a("hudLoadingSub").textContent="Baixando grade de programação ao vivo..."),a("hudProgressBar")&&(a("hudProgressBar").style.width="20%"),a("hudProgressPercent")&&(a("hudProgressPercent").textContent="20%"),s.classList.remove("hidden")),a("btnHudChangeServer")&&(a("btnHudChangeServer").onclick=()=>{if(ge)try{ge.abort()}catch{}s&&s.classList.add("hidden"),De(),ot()}),a("btnHudCancelLoading")&&(a("btnHudCancelLoading").onclick=()=>{if(ge)try{ge.abort()}catch{}s&&s.classList.add("hidden"),De(),w.LIVE&&w.LIVE.length>0&&(Q(),te(),Ie("ALL"),Y())}),a("txtCurrentCategoryTitle").textContent="Conectando ao "+r.name+"...",Kt(35,"Baixando grade de canais ao vivo...")}let c=n?null:setTimeout(()=>{if(i===me&&s&&!s.classList.contains("hidden")&&(!w.LIVE||w.LIVE.length===0)){a("hudLoadingSub")&&(a("hudLoadingSub").textContent="⚠️ Servidor "+r.name+" demorando a responder. Deseja trocar a fonte?");let d=a("hudLoadingActions");d&&(d.style.display="flex");let l=a("btnHudChangeServer");l&&l.focus()}},7e3);ut=!0;let u=0;try{let d={LIVE:[],MOVIE:[],SERIES:[]},l={LIVE:[],MOVIE:[],SERIES:[]},f=Ga(),p=k=>k==="LIVE"?f.live:k==="MOVIE"?f.movies:k==="SERIES"?f.series:!0;n||(w=d),Mt={};let m;if(n){let k=await ps(r.id);m=!k||!k.updatedAt||Date.now()-k.updatedAt>720*60*1e3?await Ma(r.id):{add(){},async finish(){}}}else m=await Ma(r.id);let b=window.MK21AlternativeSources?MK21AlternativeSources.createGrouper():null,g=n,h=0,y=0,M=k=>{if(!n||i!==me||S==="SETTINGS")return;let N=a("inputSearch");if(N&&document.activeElement===N)return;let O=a("listItems");if(O&&document.activeElement&&O.contains(document.activeElement))return;let Te=Date.now();!k&&Te-y<3e3||(y=Te,w=d,Q(),te(),he[Re]||(Re="ALL"),Y(!0))},T=()=>{g||i!==me||!(d.LIVE.length>0||d.MOVIE.length>0||d.SERIES.length>0)||(g=!0,c&&(clearTimeout(c),c=null),w=d,w.LIVE.length>0&&En(r.id,w.LIVE,!0),Kt(80,"Conteúdo inicial liberado ("+(w.LIVE.length+w.MOVIE.length+w.SERIES.length)+")"),w.LIVE.length===0&&S==="LIVE"&&(w.MOVIE.length>0?S="MOVIE":w.SERIES.length>0&&(S="SERIES")),Q(),te(),Ie("ALL"),Y(),!v&&w.LIVE.length>0&&de(w.LIVE[0]),De(),s&&s.classList.add("hidden"),tt()||E("📺 1º Plano liberado! Demais etapas carregando em segundo plano..."))},I=async()=>{let k=[].concat(l.LIVE).concat(l.MOVIE).concat(l.SERIES);if(k.length===0)return;let N=450;for(let O=0;O<k.length;O+=N){if(i!==me)return;let Te=Math.min(k.length,O+N);for(let Ee=O;Ee<Te;Ee++){let ue=k[Ee],fe=ue.contentType;if(m.add(ue),d[fe])if(fe==="LIVE"&&b){let Ht=b.add(ue);Ht.isNew&&d[fe].push(Ht.item)}else d[fe].push(ue)}let J=Math.min(99,Math.round(Te/k.length*100));ct(J,!1,{background:!0}),await new Promise(Ee=>setTimeout(Ee,16))}};if(await Li(r.url,ge&&ge.signal,k=>{if(i!==me)return;let N=en(k),O=N.contentType,Te=localStorage.getItem("mk21_filter_load_live")!=="false",J=localStorage.getItem("mk21_filter_load_movies")!=="false",Ee=localStorage.getItem("mk21_filter_load_series")!=="false";if(O==="LIVE"&&!Te||O==="MOVIE"&&!J||O==="SERIES"&&!Ee)return;let ue=N.isAdult||ne(N.name)||ne(N.group);if(N.isAdult=!!ue,!n&&!p(O)){l[O]&&l[O].push(N);return}if(m.add(N),d[O])if(O==="LIVE"&&b){let fe=b.add(N);fe.isNew&&d[O].push(fe.item)}else d[O].push(N);if(n)M(!1);else{let fe=d.LIVE.length+d.MOVIE.length+d.SERIES.length;O!=="LIVE"&&d.LIVE.length>0&&!g||fe>=8&&!g?T():!g&&d.LIVE.length>0&&d.LIVE.length%50===0&&En(r.id,d.LIVE)}},(k,N)=>{if(i!==me)return;let O=Date.now();if(O-h<250)return;h=O;let Te=d.LIVE.length+d.MOVIE.length+d.SERIES.length+l.LIVE.length+l.MOVIE.length+l.SERIES.length,J=50;if(N&&N>0)J=Math.min(99,Math.max(10,Math.round(k/N*100)));else if(!g)J=Math.min(48,15+Math.round(d.LIVE.length/25*30));else{let Ee=k/1048576,ue=50+Math.round(Math.min(48,Ee/4.5*26+Te/6e3*22));J=Math.min(98,Math.max(50,ue))}ct(J,!1,{background:n}),n||(g||Kt(J,"Lendo 1º plano — "+d.LIVE.length+" canais ao vivo"),s&&!g&&(a("hudProgressBar")&&(a("hudProgressBar").style.width=J+"%"),a("hudProgressPercent")&&(a("hudProgressPercent").textContent=J+"%")))}),i!==me)return;let P=d.LIVE.length+d.MOVIE.length+d.SERIES.length,B=l.LIVE.length+l.MOVIE.length+l.SERIES.length;if(P+B===0)throw new Error("Lista do servidor retornou vazia (0 itens)");if(w=d,!n&&P>0&&(T(),Q(),te(),g||Ie("ALL"),s&&(a("hudProgressBar")&&(a("hudProgressBar").style.width="100%"),a("hudProgressPercent")&&(a("hudProgressPercent").textContent="100%"),s.classList.add("hidden"))),B>0&&(await I(),i!==me))return;ut=!1,w=d,n||T(),M(!0),Q(),te(),!n&&!g&&Ie("ALL");let R=w.LIVE.length+w.MOVIE.length+w.SERIES.length;Kt(100,"Lista completa carregada ("+R.toLocaleString("pt-BR")+" itens)"),s&&(a("hudProgressBar")&&(a("hudProgressBar").style.width="100%"),a("hudProgressPercent")&&(a("hudProgressPercent").textContent="100%"),s.classList.add("hidden")),ct(100,!0,{background:n||o}),Nt=0;try{localStorage.setItem("mk21_last_sync_"+r.id,String(Date.now()))}catch{}if(await m.finish(),En(r.id,w.LIVE,!0),li(r.id,w),nt(r.id,w,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),(S==="LIVE"||S==="MOVIE"||S==="SERIES"||S==="CONTINUE")&&(Q(),te(),Y()),a("txtCurrentCategoryTitle")){let k=w.LIVE.length;a("txtCurrentCategoryTitle").textContent="📺 Catálogo: "+k.toLocaleString("pt-BR")+" canais • "+w.MOVIE.length.toLocaleString("pt-BR")+" filmes • "+w.SERIES.length.toLocaleString("pt-BR")+" séries"}typeof We=="function"&&We(),n&&!o&&u!==R&&!tt()?(u=R,E("✓ Catálogo sincronizado: "+R.toLocaleString("pt-BR")+" itens")):o&&x("[Sync Segundo Plano] Catálogo atualizado silenciosamente ("+R+" itens).","success"),da()}catch(d){if(i===me&&(ut=!1),i!==me||d&&d.name==="AbortError")return;console.error("Server error:",d);let l=w&&Array.isArray(w.LIVE)?w.LIVE.length:0;if(n){if(ct(100,!0),o){x("[Sync Segundo Plano] Falha silenciosa: "+Ce.formatFriendlyLoadError(d),"warn");return}a("txtServerSyncBadge")&&(a("txtServerSyncBadge").textContent="offline",a("txtServerSyncBadge").classList.remove("hidden","done")),E(l>0?"⚠️ Sem conexão — usando o catálogo salvo ("+l+" canais)":"⚠️ Sem conexão com o servidor — tente Atualizar"),s&&s.classList.add("hidden"),De();return}l>0?(Q(),te(),Ie("ALL"),Y(),De(),v||de(w.LIVE[0]),E(`⚠️ Lista parcial: ${l} canais carregados. Tente atualizar novamente.`),a("txtCurrentCategoryTitle")&&(a("txtCurrentCategoryTitle").textContent=`📺 ${l} canais disponíveis (parcial)`),s&&s.classList.add("hidden")):!(d&&d.isAuthError)&&sl(d,r,i)||(fs(d,r.name),a("txtCurrentCategoryTitle")&&(a("txtCurrentCategoryTitle").textContent="Aguardando conexão com o servidor"),a("txtServerSyncBadge")&&(a("txtServerSyncBadge").textContent="erro",a("txtServerSyncBadge").classList.remove("hidden","done")))}finally{i===me&&(ut=!1)}}function Q(){he={ALL:[]},Ze=["ALL"];let e=[];if(S==="FAVORITES"?e=Ra.length?Ra:[...w.LIVE,...w.MOVIE,...w.SERIES].filter(i=>z.has(i.url)):S==="CONTINUE"?e=G:e=w[S]||[],(S==="MOVIE"||S==="SERIES")&&G.length>0){let i=G.filter(r=>r.contentType===S);i.length>0&&(he["▶ Continuar Assistindo"]=i,Ze.push("▶ Continuar Assistindo"))}let t=[];try{t=JSON.parse(localStorage.getItem("mk21_hidden_categories")||"[]")}catch{}let n=Object.create(null),o=t.length?new Set(t):null;for(let i=0;i<e.length;i++){let r=e[i],s=r.group||"Geral";if(!Ue){let c=n[s];if(typeof c=="undefined"&&(c=n[s]=ne(s)),r.isAdult||c)continue}o&&o.has(s)||(he.ALL.push(r),he[s]||(he[s]=[],Ze.push(s)),he[s].push(r))}}function te(){let e=a("listCategories");e.innerHTML="";let t=document.createDocumentFragment();a("badgeCatCount").textContent=Ze.length-1+" grupos",a("txtCatHeaderTitle").textContent=S==="LIVE"?"📁 Categorias TV":S==="MOVIE"?"📁 Gêneros Filmes":S==="SERIES"?"🍿 Gêneros Séries":S==="CONTINUE"?"▶ Continuar Assistindo":"⭐ Favoritos",Ze.forEach((n,o)=>{if(!Ue&&ne(n))return;let i=document.createElement("li"),r=document.createElement("button");r.className="list-item-btn cat-item-btn"+(n===Re?" active":""),r.setAttribute("tabindex","0"),r.setAttribute("data-idx",o);let s=document.createElement("span");s.textContent=n==="ALL"?S==="LIVE"?"🌟 Todos os Canais":S==="MOVIE"?"🌟 Todos os Filmes":S==="SERIES"?"🌟 Todas as Séries":S==="CONTINUE"?"🌟 Todos em Andamento":"🌟 Todos os Favoritos":n;let c=document.createElement("span");c.className="counter-badge",c.textContent=he[n]?he[n].length:0,r.appendChild(s),r.appendChild(c),r.onclick=()=>Ie(n),i.appendChild(r),t.appendChild(i)}),e.appendChild(t),typeof q=="function"&&(q(e),setTimeout(()=>q(e),60))}function Ie(e){Re=e;let t=a("listCategories").querySelectorAll(".cat-item-btn");t.forEach(o=>o.classList.remove("active"));let n=Array.from(t).find(o=>e==="ALL"&&o.textContent.includes("Todos")||o.textContent.startsWith(e));n&&n.classList.add("active"),a("txtCurrentCategoryTitle").textContent=e==="ALL"?S==="LIVE"?"📺 Todos os Canais":S==="MOVIE"?"🎬 Todos os Filmes":S==="SERIES"?"🍿 Todas as Séries":S==="CONTINUE"?"▶ Continuar Assistindo":"⭐ Favoritos":"📁 "+e,a("inputSearch").value="",Qe=80,Y()}var Qe=80;function Y(e){let t=e&&a("listItems")?a("listItems").scrollTop:0,n=a("inputSearch")?a("inputSearch").value.toLowerCase().trim():"",o=he[Re]||[],i=S+"|"+(Re==="ALL"?"ALL":Re),r=Object.prototype.hasOwnProperty.call(Mt,i),s=n?o.filter(f=>f.name&&f.name.toLowerCase().includes(n)||f.normalizedName&&f.normalizedName.includes(n)):o;Ue||(s=s.filter(f=>!f.isAdult));function c(f){let p=String(f||"").match(/\b((?:19|20)\d{2})\b/);return p?parseInt(p[1],10):0}function u(f){let p=parseFloat(f&&(f.rating||f.rating_5based));return Number.isFinite(p)?p:0}Ne==="AZ"?s=s.slice().sort((f,p)=>f.name.localeCompare(p.name,"pt-BR",{sensitivity:"base"})):Ne==="ZA"?s=s.slice().sort((f,p)=>p.name.localeCompare(f.name,"pt-BR",{sensitivity:"base"})):Ne==="RECENT"?s=s.slice().reverse():(Ne==="RATING"||Ne==="YEAR")&&(s=s.slice().sort((f,p)=>{let m=u(f),b=u(p);if(m!==b)return b-m;let g=c(f.name),h=c(p.name);return g&&h&&g!==h?h-g:g&&!h?-1:!g&&h?1:f.name.localeCompare(p.name,"pt-BR",{sensitivity:"base"})})),V=s,a("badgeItemsCount").textContent=V.length;let d=a("listItems");if(d.innerHTML="",V.length===0){d.innerHTML='<li><button class="list-item-btn" disabled>Nenhum item encontrado nesta categoria.</button></li>';return}let l=document.createDocumentFragment();if(S==="SERIES"&&n.length===0){Lt=cs(V),a("badgeItemsCount").textContent=Lt.length+" séries";let f=Math.min(Lt.length,Qe);for(let p=0;p<f;p++){let m=Lt[p],b=document.createElement("li"),g=document.createElement("button");g.className="list-item-btn series-card-btn",g.setAttribute("tabindex","0"),g.setAttribute("data-idx",p);let h=document.createElement("span");h.textContent="🍿 "+m.title;let y=document.createElement("span");y.className="counter-badge",y.textContent=m.episodes.length+" eps",g.appendChild(h),g.appendChild(y),g.onclick=()=>Ba(m),b.appendChild(g),l.appendChild(b)}if(Lt.length>f||r){let p=document.createElement("li"),m=document.createElement("button");m.className="list-item-btn",m.style.textAlign="center",m.style.color="#ffd54f",m.textContent=`➕ Carregar Mais Séries (+150 de ${Lt.length-f} restantes)...`,m.onclick=()=>{Mo()},p.appendChild(m),l.appendChild(p)}}else{let f=Math.min(V.length,Qe);for(let p=0;p<f;p++){let m=V[p],b=document.createElement("li"),g=document.createElement("button");g.className="list-item-btn channel-item-btn channel-row"+(v&&v.url===m.url?" active active-playing":""),g.setAttribute("tabindex","0"),g.setAttribute("data-idx",p),g.setAttribute("data-id",m.id||m.url);let h=document.createElement("div");h.className="channel-logo-wrap";let y=document.createElement("img");y.className="channel-logo",y.referrerPolicy="no-referrer";let M="data:image/svg+xml;utf8,"+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 52 52"><rect width="52" height="52" rx="8" fill="#1e1f26"/><circle cx="26" cy="26" r="16" fill="#141828"/><polygon points="23,19 33,26 23,33" fill="#ffd54f"/></svg>');y.src=m.logo?ks(m.logo,m.name):M,y.alt=m.name,y.loading="lazy",y.decoding="async",y.onerror=function(){this.onerror=null,this.src=M},h.appendChild(y),g.appendChild(h);let T=document.createElement("div");T.className="channel-details";let I=document.createElement("span");I.className="channel-name",I.textContent=m.name,T.appendChild(I);let P=document.createElement("span");if(P.className="channel-epg-sub",P.textContent=m.current_program||(S==="CONTINUE"?"▶ Continuar":m.group||"Ao Vivo"),T.appendChild(P),g.appendChild(T),m.isAdult){let N=document.createElement("span");N.className="counter-badge",N.textContent="+18",g.appendChild(N)}if(m.progressPct&&m.progressPct>0){let N=document.createElement("div");N.className="continue-progress-track";let O=document.createElement("div");O.className="continue-progress-fill",O.style.width=Math.min(100,m.progressPct)+"%",N.appendChild(O),g.appendChild(N)}let B=z.has(m.url),R=document.createElement("button");R.className="channel-fav-btn"+(B?" active":""),R.textContent=B?"⭐":"☆",R.title=B?"Remover dos favoritos":"Favoritar canal",R.tabIndex=-1,R.onclick=N=>{N.stopPropagation(),ra(m.url);let O=z.has(m.url);R.className="channel-fav-btn"+(O?" active":""),R.textContent=O?"⭐":"☆"},g.appendChild(R),g.onclick=()=>de(m);let k=0;g.addEventListener("click",()=>{let N=Date.now();N-k<380&&cn(!0),k=N}),b.appendChild(g),l.appendChild(b)}if(V.length>f||r){let p=document.createElement("li"),m=document.createElement("button");m.className="list-item-btn",m.style.textAlign="center",m.style.color="#ffd54f",m.textContent=`➕ Carregar Mais Itens (+150 de ${V.length-f} restantes)...`,m.onclick=()=>{Mo()},p.appendChild(m),l.appendChild(p)}}if(d.appendChild(l),e)try{d.scrollTop=t}catch{}typeof q=="function"&&(q(d),setTimeout(()=>q(d),60)),!v&&S==="LIVE"&&V.length>0&&!n&&de(V[0])}function Qn(){let e=a("btnSortOrder");if(!e)return;let t={DEFAULT:"↕️ Número",RECENT:"🕒 Adição",RATING:"⭐ Qualificação",YEAR:"⭐ Qualificação",AZ:"🔤 A - Z",ZA:"🔤 Z - A"};e.textContent=t[Ne]||"↕️ Classificação"}function ms(){let e=a("modalSortPicker");if(!e)return;ye(e,!0),e.classList.remove("hidden"),A="modalSortPicker";let t=Ne==="YEAR"?"RATING":Ne,n=e.querySelectorAll(".sort-choice-btn");n.forEach(i=>{let r=i.dataset.order===t;i.classList.toggle("active",r),i.onclick=()=>{Ne=i.dataset.order,Qn(),Zr(),Y(),Ci();let s=i.querySelector(".apk-radio-label")?i.querySelector(".apk-radio-label").textContent.trim():i.textContent.trim();E("Ordenação: "+s)}});let o=e.querySelector(`.sort-choice-btn[data-order="${t}"]`)||n[0];o&&o.focus()}function Ci(){let e=a("modalSortPicker");e&&(e.classList.add("hidden"),ye(e,!1)),A="channels",a("btnSortOrder")&&a("btnSortOrder").focus()}a("btnSortOrder")&&(a("btnSortOrder").onclick=ms,Qn());a("btnCloseSortPicker")&&(a("btnCloseSortPicker").onclick=Ci);a("inputSearch")&&(a("inputSearch").addEventListener("click",()=>ts()),a("inputSearch").addEventListener("focus",()=>{A="search"}),a("inputSearch").addEventListener("input",ns));async function Ba(e){if(e&&Array.isArray(e.episodes)&&e.episodes.length===1&&e.episodes[0]&&typeof e.episodes[0].url=="string"&&e.episodes[0].url.endsWith(".xtream")){let s=e.episodes[0].url.match(/^(https?:\/\/.+?)\/series\/([^/]+)\/([^/]+)\/(\d+)\.xtream$/);if(s)try{E("🍿 Carregando episódios...");let c=`${s[1]}/player_api.php?username=${s[2]}&password=${s[3]}&action=get_series_info&series_id=${s[4]}`,u=await fetch(c,{mode:"cors"});if(u.ok){let d=await u.json(),l=d&&d.episodes?d.episodes:{},f=[];Object.keys(l).forEach(p=>{(Array.isArray(l[p])?l[p]:[]).forEach(b=>{if(!b||!b.id)return;let g=b.container_extension||"mp4",h=`S${String(p).padStart(2,"0")}E${String(b.episode_num||1).padStart(2,"0")} - ${b.title||"Episódio "+(b.episode_num||"")}`;f.push({name:h,group:e.group||"Séries",logo:b.info&&b.info.movie_image||e.logo||"",url:`${s[1]}/series/${s[2]}/${s[3]}/${b.id}.${g}`,contentType:"SERIES",isAdult:!!e.episodes[0].isAdult})})}),f.length>0&&(e.episodes=f)}}catch(c){x("[Séries] Falha ao obter episódios Xtream: "+(c&&c.message),"warn")}}rs=e,a("seriesModalTitle").textContent="🍿 "+e.title;let t={};e.episodes.forEach(r=>{let s=1,c=r.name.match(/(?:s|temporada|temp\.?|t)\s*(\d{1,2})|\b(\d{1,2})x\d{1,4}\b/i);c&&(s=parseInt(c[1]||c[2],10)||1);let u="Temporada "+s;t[u]||(t[u]=[]),t[u].push(r)}),Object.keys(t).forEach(r=>{t[r].sort((s,c)=>{let u=s.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i),d=c.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i),l=u?parseInt(u[1]||u[2],10):0,f=d?parseInt(d[1]||d[2],10):0;return l!==f&&l>0&&f>0?l-f:s.name.localeCompare(c.name,void 0,{numeric:!0})})});let n=Object.keys(t).sort((r,s)=>{let c=parseInt(r.replace(/\D/g,""),10)||0,u=parseInt(s.replace(/\D/g,""),10)||0;return c-u}),o=a("listSeasons");o.innerHTML="",n.forEach((r,s)=>{let c=document.createElement("li"),u=document.createElement("button");u.className="season-tab-btn"+(s===0?" active":""),u.setAttribute("tabindex","0"),u.textContent=r+` (${t[r].length} eps)`,u.onclick=()=>{o.querySelectorAll(".season-tab-btn").forEach(d=>d.classList.remove("active")),u.classList.add("active"),Oo(t[r],r)},c.appendChild(u),o.appendChild(c)}),n.length>0&&Oo(t[n[0]],n[0]),a("modalSeriesEpisodes").classList.remove("hidden"),A="modalSeries",os=0,is=0;let i=o.querySelector(".season-tab-btn");i&&i.focus()}function Oo(e,t){a("episodesListHeading").textContent=`🎬 ${t} (${e.length} episódios)`;let n=a("listEpisodes");n.innerHTML="",e.forEach((o,i)=>{let r=document.createElement("li"),s=document.createElement("button");s.className="episode-item-btn",s.setAttribute("tabindex","0"),s.setAttribute("data-epidx",i);let c=document.createElement("span");c.textContent=o.name;let u=document.createElement("span");u.textContent="▶ Assistir",u.style.color="#ffd54f",s.appendChild(c),s.appendChild(u),s.onclick=()=>{a("modalSeriesEpisodes").classList.add("hidden"),A="player",de(o),cn(!0)},r.appendChild(s),n.appendChild(r)})}a("btnCloseSeriesModal").onclick=()=>{a("modalSeriesEpisodes").classList.add("hidden"),A="channels",Se()};var xa=new Map;function Do(e){if(!e||typeof e!="string")return"";let t=e.trim();if(/^[A-Za-z0-9+/=]+$/.test(t)&&t.length>=4&&t.length%4===0)try{let n=atob(t);if(/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF\u0100-\uFFFF]+$/.test(n))try{return decodeURIComponent(escape(n))}catch{return n}}catch{}return t}function gs(e){let t=new Date,n=t.getHours(),o=t.getMinutes(),i=n*60+o,r=(e||"").toUpperCase(),s="Programação Geral",c="A Seguir: Variedades",u=n,d=0,l=n+1,f=0;r.includes("GLOBO")?n>=6&&n<9?(s="Bom Dia Brasil",c="Mais Você",u=6,l=9):n>=9&&n<12?(s="Mais Você com Ana Maria",c="Praça TV",u=9,l=12):n>=12&&n<14?(s="Praça TV - 1ª Edição",c="Globo Esporte",u=12,l=14):n>=14&&n<17?(s="Sessão da Tarde",c="Vale a Pena Ver de Novo",u=14,l=17):n>=17&&n<19?(s="Vale a Pena Ver de Novo",c="Novela das Seis",u=17,l=19):n>=19&&n<21?(s="Novela das Sete / Jornal Nacional",c="Novela das Nove",u=19,l=21):n>=21&&n<23?(s="Novela das Nove",c="Tela Quente",u=21,l=23):(s="Cinema Especial / Madrugada",c="Hora Um",u=23,l=6):r.includes("RECORD")?n>=6&&n<10?(s="Balanço Geral Manhã / Fala Brasil",c="Hoje em Dia",u=6,l=10):n>=10&&n<12?(s="Hoje em Dia",c="Balanço Geral",u=10,l=12):n>=12&&n<15?(s="Balanço Geral & Hora da Venenosa",c="Novela da Tarde",u=12,l=15):n>=15&&n<17?(s="Novela da Tarde",c="Cidade Alerta",u=15,l=17):n>=17&&n<20?(s="Cidade Alerta com Helicóptero",c="Jornal da Record",u=17,l=20):n>=20&&n<22?(s="Jornal da Record & Novela Bíblica",c="A Fazenda",u=20,l=22):(s="Super Tela / A Fazenda",c="Programação Religiosa",u=22,l=6):r.includes("SBT")?n>=6&&n<10?(s="Primeiro Impacto",c="Bom Dia & Cia",u=6,l=10):n>=10&&n<13?(s="Bom Dia & Cia / Séries",c="Chaves",u=10,l=13):n>=13&&n<15?(s="Chaves & Seriados Amados",c="Fofocalizando",u=13,l=15):n>=15&&n<17?(s="Fofocalizando & Fofocas",c="Novelas Mexicanas",u=15,l=17):n>=17&&n<20?(s="Novelas da Tarde",c="SBT Brasil",u=17,l=20):n>=20&&n<22?(s="SBT Brasil & Romeu e Julieta",c="Programa do Ratinho",u=20,l=22):(s="Programa do Ratinho / A Praça é Nossa",c="The Noite",u=22,l=6):r.includes("SPORT")||r.includes("ESPN")||r.includes("PREMIERE")?n>=8&&n<12?(s="SportsCenter / Redação SporTV",c="Futebol 360",u=8,l=12):n>=12&&n<16?(s="Futebol Ao Vivo - Pré-Jogo",c="Transmissão Ao Vivo",u=12,l=16):n>=16&&n<19?(s="Campeonato Ao Vivo - 1º Tempo",c="Troca de Passes",u=16,l=19):n>=19&&n<22?(s="Jogo da Noite Ao Vivo",c="Gols da Rodada",u=19,l=22):(s="Linha de Passe & Melhores Momentos",c="Giro do Esporte",u=22,l=8):r.includes("TELE")||r.includes("HBO")||r.includes("WARNER")||r.includes("UNIVERSAL")?n>=10&&n<13?(s="Sessão Aventura: Os Vingadores",c="Comédia em Alta",u=10,l=13):n>=13&&n<16?(s="Batman - O Cavaleiro das Trevas",c="Superestreia",u=13,l=16):n>=16&&n<19?(s="Superestreia: Duna Parte 2",c="Série do Ano",u=16,l=19):n>=19&&n<22?(s="House of the Dragon",c="Blockbuster",u=19,l=22):(s="Blockbuster do Ano: Oppenheimer",c="Sessão Noturna",u=22,l=10):(s=`Programação ao Vivo (${n}:00 - ${n+1}:00)`,c=`Transmissão Especial (${n+1}:00)`,u=n,l=n+1);let p=u*60+d,m=l*60+f,b=Math.max(1,m-p),g=Math.max(0,i-p),h=Math.min(100,Math.max(0,Math.round(g/b*100))),y=`${String(u).padStart(2,"0")}:${String(d).padStart(2,"0")} - ${String(l).padStart(2,"0")}:${String(f).padStart(2,"0")}`;return{title:s,nextTitle:c,timeStr:y,progressPercent:h}}async function hs(e){if(!e)return;let t=gs(e.name);a("epgTitle")&&(a("epgTitle").textContent=(z.has(e.url)?"⭐ ":"▶ ")+e.name),a("fsHudTitle")&&(a("fsHudTitle").textContent=(z.has(e.url)?"⭐ ":"")+e.name),a("epgGroup")&&(a("epgGroup").textContent=`Categoria: ${e.group} • ${t.timeStr}`),a("previewEpgNow")&&(a("previewEpgNow").textContent=`NO AR: ${t.title}`),a("previewEpgNext")&&(a("previewEpgNext").textContent=`A SEGUIR: ${t.nextTitle}`),a("epgStatus")&&(a("epgStatus").innerHTML=`🔴 NO AR: <b>${K(t.title)}</b>`);let n=document.querySelector(".epg-progress");if(n&&(n.style.width=`${t.progressPercent}%`),a("epgNext")&&(a("epgNext").textContent=`A seguir: ${t.nextTitle}`),a("fsEpgCurrentTitle")&&(a("fsEpgCurrentTitle").textContent=t.title),a("fsEpgTimeBadge")&&(a("fsEpgTimeBadge").textContent=t.timeStr),a("fsEpgFill")&&(a("fsEpgFill").style.width=`${t.progressPercent}%`),a("fsEpgNextTitle")&&(a("fsEpgNextTitle").textContent=t.nextTitle),e.contentType!=="LIVE"){a("epgStatus")&&(a("epgStatus").textContent="▶ Reproduzindo VOD"),localStorage.getItem("mk21_use_tmdb")!=="false"&&vs(e);return}let o=L[_]||Ot[0],i=e.streamId;if(!i&&e.url){let d=e.url.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);d&&d[1]&&(i=d[1])}let r=Pn()||{user:"",pass:""},s=r.user,c=r.pass;if(!o||!o.url||!i||!s||!c)return;let u=`epg_${i}`;if(xa.has(u)){Vo(e,xa.get(u));return}try{let l=`${o.url.replace(/\/+$/,"")}/player_api.php?username=${encodeURIComponent(s)}&password=${encodeURIComponent(c)}&action=get_short_epg&stream_id=${i}&limit=6`,f=await fetch(l);if(f.ok){let p=await f.json();p&&Array.isArray(p.epg_listings)&&p.epg_listings.length>0&&(xa.set(u,p.epg_listings),v===e&&Vo(e,p.epg_listings))}}catch(d){console.debug("[SmartTV EPG] Falha ao carregar metadados reais, mantendo simulação:",d)}}function Fo(e){if(!e)return NaN;if(typeof e=="number")return e>1e11?e:e*1e3;let t=String(e).trim();if(/^\d{10}$/.test(t))return parseInt(t,10)*1e3;if(/^\d{13}$/.test(t))return parseInt(t,10);let n=t.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);if(n){let i=n[1],r=n[2],s=n[3],c=n[4],u=n[5],d=n[6],l=n[7],f;l?f=`${i}-${r}-${s}T${c}:${u}:${d}${l.slice(0,3)}:${l.slice(3)}`:f=`${i}-${r}-${s}T${c}:${u}:${d}Z`;let p=new Date(f).getTime();if(!isNaN(p))return p}let o=new Date(t.replace(" ","T")).getTime();return isNaN(o)?NaN:o}function Vo(e,t){if(!t||t.length===0||v!==e)return;let n=Date.now(),o=t.map(m=>{let b=Fo(m.start_timestamp||m.start),g=Fo(m.stop_timestamp||m.end||m.stop);return{title:Do(m.title),desc:Do(m.description),start:b,stop:g}}).filter(m=>!isNaN(m.start)&&!isNaN(m.stop));if(o.length===0)return;jn=o;let i=o.find(m=>m.start<=n&&m.stop>n)||o[0],r=o.indexOf(i),s=r>=0&&r+1<o.length?o[r+1]:null,c=new Date(i.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}),u=new Date(i.stop).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}),d=Math.max(1,i.stop-i.start),l=Math.max(0,n-i.start),f=Math.min(100,Math.max(0,Math.round(l/d*100)));if(a("epgTitle")&&(a("epgTitle").textContent=`▶ ${e.name}`),a("epgGroup")&&(a("epgGroup").textContent=`Categoria: ${e.group} • ${c} - ${u}`),a("previewEpgNow")&&(a("previewEpgNow").innerHTML=`NO AR: <b>${K(i.title)}</b>`),a("previewEpgNext")){let m=s?new Date(s.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}):"--:--";a("previewEpgNext").textContent=`A SEGUIR (${m}): ${s?s.title:"Programação Normal"}`}a("epgStatus")&&(a("epgStatus").innerHTML=`🔴 NO AR: <b>${K(i.title)}</b> <span style="font-size:10px; background:rgba(16,185,129,0.25); color:#10b981; border:1px solid rgba(16,185,129,0.4); border-radius:4px; padding:1px 5px; margin-left:6px; font-weight:800;">📡 XMLTV REAL</span>`);let p=document.querySelector(".epg-progress");if(p&&(p.style.width=`${f}%`),a("epgNext")&&s){let m=new Date(s.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"});a("epgNext").textContent=`A seguir (${m}): ${s.title}`}a("fsEpgCurrentTitle")&&(a("fsEpgCurrentTitle").textContent=i.title),a("fsEpgTimeBadge")&&(a("fsEpgTimeBadge").textContent=`${c} - ${u}`),a("fsEpgFill")&&(a("fsEpgFill").style.width=`${f}%`),a("fsEpgNextTitle")&&(a("fsEpgNextTitle").textContent=s?s.title:"Programação Normal")}var La=new Map;async function vs(e){if(!e||!e.name)return;let t=e.name.replace(/\b(1080p|720p|4k|fhd|hd|dublado|legendado|dual|audio|h264|hevc)\b.*$/i,"").trim();if(La.has(t)){$o(e,La.get(t));return}try{let n="https://api.themoviedb.org/3/search/multi?api_key=b41249b6754020a656799015bc2301f2&language=pt-BR&query="+encodeURIComponent(t),o=await fetch(n);if(o.ok){let i=await o.json();if(i&&i.results&&i.results.length>0){let r=i.results[0];La.set(t,r),v===e&&$o(e,r)}}}catch{}}function $o(e,t){if(!t||v!==e)return;let n=t.overview||t.title||t.name;if(a("epgNext")&&n&&(a("epgNext").textContent="Sinopse: "+(n.length>120?n.substring(0,120)+"...":n)),a("epgGroup")){let i=t.release_date||t.first_air_date||"";a("epgGroup").textContent=`${e.group}${i?" • "+i.substring(0,4):""}`}let o=document.querySelector(".badge-quality");o&&t.vote_average&&(o.textContent="★ "+t.vote_average.toFixed(1)+" TMDB")}var Vn=null,wt=new Map;function ys(e){if(!e||!e.url||!V||V.length<=1||S!=="LIVE")return;if(Vn)try{Vn.abort()}catch{}Vn=new AbortController;let t=Vn.signal,n=V.findIndex(s=>s.url===e.url);if(n===-1)return;let o=(n-1+V.length)%V.length,i=(n+1)%V.length,r=[V[i],V[o]];setTimeout(()=>{r.forEach(s=>{if(!(!s||!s.url||s.url===e.url))try{s.url.toLowerCase().includes(".m3u8")&&!wt.has(s.url)?fetch(s.url,{signal:t,headers:{Range:"bytes=0-32767"},cache:"default",priority:"low"}).then(u=>u.ok?u.text():null).then(u=>{if(u&&(u.includes("#EXTM3U")||u.includes("#EXT-X-STREAM-INF"))&&(wt.set(s.url,{text:u,timestamp:Date.now()}),wt.size>12)){let d=wt.keys().next().value;wt.delete(d)}}).catch(()=>{}):fetch(s.url,{method:"HEAD",signal:t,priority:"low"}).catch(()=>{})}catch{}})},400)}var Ho=null;function bs(e){clearInterval(Ho),!(!e||e.contentType==="LIVE")&&(Ho=setInterval(()=>{let t=a("tvPlayer");!t||t.paused||!t.duration||isNaN(t.duration)||Ss(e.url,t.currentTime,t.duration)},1e4))}function Ss(e,t,n){if(!v||v.contentType==="LIVE"||!t||t<3)return;let o=Math.min(100,Math.max(1,Math.round(t/n*100))),i=G.findIndex(s=>s.url===e),r={name:v.name,group:v.group,logo:v.logo,url:e,contentType:v.contentType,currentTime:Math.round(t),duration:Math.round(n),progressPct:o,timestamp:Date.now()};i>=0&&G.splice(i,1),G.unshift(r),G.length>50&&(G=G.slice(0,50));try{localStorage.setItem("mk21_continue_watching",JSON.stringify(G))}catch{}}var Na=[{id:"contain",label:"Proporção: Original (16:9)",short:"Normal",objectFit:"contain"},{id:"cover",label:"Proporção: Zoom (Preencher)",short:"Zoom",objectFit:"cover"},{id:"fill",label:"Proporção: Esticar (Distorcer)",short:"Esticar",objectFit:"fill"}],Wn=0;function ki(){let e=a("tvPlayer");if(!e)return;Wn=(Wn+1)%Na.length;let t=Na[Wn];e.style.objectFit=t.objectFit,E(t.label);let n="📐 "+t.short;a("fsBtnAspect")&&(a("fsBtnAspect").textContent=n),a("btnAspect")&&(a("btnAspect").textContent=n),a("diagAspect")&&(a("diagAspect").textContent=t.label)}function Es(){let e=a("tvPlayer");e&&(e.muted=!e.muted,E(e.muted?"🔇 Áudio Mudo":"🔊 Áudio Ativado"))}function zo(){let e=a("modalStreamDiagnostics");if(!e)return;if(!e.classList.contains("hidden")){e.classList.add("hidden"),A="channels",Se();return}xs(),e.classList.remove("hidden"),A="modalStreamDiagnostics";let t=a("btnCloseDiagnostics");t&&t.focus()}function xs(){let e=a("tvPlayer");if(a("diagTitle")&&(a("diagTitle").textContent=v?v.name:"Nenhum canal ativo"),e){let t=e.videoWidth||0,n=e.videoHeight||0;a("diagResolution")&&(a("diagResolution").textContent=t&&n?`${t}x${n} (${t>=1920?"FHD":t>=1280?"HD":"SD"})`:"Detectando resolução...");let o=0;if(e.buffered&&e.buffered.length>0){for(let i=0;i<e.buffered.length;i++)if(e.currentTime>=e.buffered.start(i)&&e.currentTime<=e.buffered.end(i)){o=Math.round(e.buffered.end(i)-e.currentTime);break}}if(a("diagBuffer")&&(a("diagBuffer").textContent=`${o}s de buffer`),C&&C.levels&&C.currentLevel>=0){let i=C.levels[C.currentLevel];if(i){let r=i.bitrate?(i.bitrate/1e6).toFixed(2)+" Mbps":"Automático (~3.5 Mbps)";a("diagBitrate")&&(a("diagBitrate").textContent=r),a("diagCodec")&&(a("diagCodec").textContent=i.codecs||(v&&v.url.includes(".m3u8")?"HLS (AVC/AAC)":"MPEG-TS"))}}else a("diagBitrate")&&(a("diagBitrate").textContent="Stream Direto (~4.0 Mbps)"),a("diagCodec")&&(a("diagCodec").textContent=v&&v.url.includes(".m3u8")?"HLS (H.264/AAC)":"MPEG-TS/MP4");a("diagAspect")&&(a("diagAspect").textContent=Na[Wn].label)}}var Rt="",Uo=null;function Ls(e){Rt+=String(e);let t=a("channelNumberOverlay"),n=a("channelNumberDigits");n&&(n.textContent=Rt),t&&t.classList.remove("hidden"),clearTimeout(Uo),Uo=setTimeout(()=>{wi()},1800)}function wi(){let e=a("channelNumberOverlay");if(e&&e.classList.add("hidden"),!Rt)return;let t=parseInt(Rt,10);if(Rt="",!V||V.length===0)return;let n=t-1;n>=0&&n<V.length?(de(V[n]),E(`Canal ${t}: ${V[n].name}`)):E(`Canal ${t} não encontrado`)}var Ln=new Map;function Cs(e){let n=`<svg xmlns="http://www.w3.org/2000/svg" width="60" height="40" viewBox="0 0 60 40"><rect width="60" height="40" rx="6" fill="#141828" stroke="rgba(255,255,255,0.1)"/><text x="50%" y="54%" font-family="sans-serif" font-weight="bold" font-size="12" fill="#ffd54f" dominant-baseline="middle" text-anchor="middle">${String(e||"MK21").trim().slice(0,4).toUpperCase()}</text></svg>`;return"data:image/svg+xml;utf8,"+encodeURIComponent(n)}function ks(e,t=""){return!e||typeof e!="string"||e.length<5?Cs(t):Ln.has(e)?Ln.get(e):(Ln.set(e,e),e)}function ws(){try{wt.size>5&&wt.clear(),Ln.size>150&&Ln.clear(),window.gc&&typeof window.gc=="function"&&window.gc()}catch(e){console.debug("[MemoryShield]",e)}}var Ti="ALL",Tt={ALL:[],LIVE:[],MOVIE:[],SERIES:[]},Go=null;function qo(e=""){let t=a("modalOmnisearch");if(!t)return;t.classList.remove("hidden"),A="modalOmnisearch";let n=a("inputOmnisearch");n&&(e&&(n.value=e),setTimeout(()=>{n.focus()},100)),n&&n.value.trim().length>=2?Ya(n.value.trim()):Zn()}function Ai(){let e=a("modalOmnisearch");e&&e.classList.add("hidden"),A="channels",Se()}async function Ya(e){if(!e||e.trim().length<2){Tt={ALL:[],LIVE:[],MOVIE:[],SERIES:[]},Zn();return}let t=e.toLowerCase().trim(),n=allContent.LIVE||[],o=allContent.MOVIE||[],i=allContent.SERIES||[],r=d=>{if(!d||!Ue&&(d.isAdult||ne(d.name)||ne(d.group)))return!1;let l=d.name&&d.name.toLowerCase().includes(t),f=d.normalizedName&&d.normalizedName.includes(t),p=d.group&&d.group.toLowerCase().includes(t);return l||f||p},s=n.filter(r).slice(0,80),c=o.filter(r).slice(0,80),u=i.filter(r).slice(0,80);if(s.length===0&&c.length===0&&u.length===0)try{let d=await ui(t);d&&d.length>0&&d.forEach(l=>{l.contentType==="LIVE"?s.push(l):l.contentType==="MOVIE"?c.push(l):l.contentType==="SERIES"&&u.push(l)})}catch{}Tt={ALL:[...s,...c,...u],LIVE:s,MOVIE:c,SERIES:u},Zn()}function Zn(){a("countOmniAll")&&(a("countOmniAll").textContent=Tt.ALL.length),a("countOmniLive")&&(a("countOmniLive").textContent=Tt.LIVE.length),a("countOmniMovies")&&(a("countOmniMovies").textContent=Tt.MOVIE.length),a("countOmniSeries")&&(a("countOmniSeries").textContent=Tt.SERIES.length);let e=a("omnisearchResultsGrid"),t=a("omnisearchEmptyState");if(!e)return;e.innerHTML="";let n=Tt[Ti]||[];if(n.length===0){if(t){t.classList.remove("hidden");let r=a("inputOmnisearch");t.textContent=r&&r.value.trim().length>=2?'Nenhum resultado encontrado para "'+r.value.trim()+'" nesta categoria.':"Digite uma palavra-chave para buscar instantaneamente em todo o catálogo."}return}t&&t.classList.add("hidden");let o=document.createDocumentFragment(),i=Math.min(n.length,60);for(let r=0;r<i;r++){let s=n[r],c=document.createElement("button");c.className="omnisearch-item-btn",c.setAttribute("tabindex","0");let u=s.contentType==="LIVE"?"badge-live":s.contentType==="MOVIE"?"badge-movie":"badge-series",d=s.contentType==="LIVE"?"Ao Vivo":s.contentType==="MOVIE"?"Filme":"Série";c.innerHTML=`
+"use strict";(()=>{var Ua=e=>{throw TypeError(e)};var Wn=(e,t)=>()=>{try{return t||e((t={exports:{}}).exports,t),t.exports}catch(n){throw t=0,n}};var Vo=(e,t,n)=>t.has(e)||Ua("Cannot "+n);var H=(e,t,n)=>(Vo(e,t,"read from private field"),n?n.call(e):t.get(e)),Ie=(e,t,n)=>t.has(e)?Ua("Cannot add the same private member more than once"):t instanceof WeakSet?t.add(e):t.set(e,n),J=(e,t,n,o)=>(Vo(e,t,"write to private field"),o?o.call(e,n):t.set(e,n),n),Ce=(e,t,n)=>(Vo(e,t,"access private method"),n);var Ga=(e,t,n,o)=>({set _(a){J(e,t,a,n)},get _(){return H(e,t,o)}});var tr=Wn((md,er)=>{"use strict";var zo={startup:0,daily:864e5,weekly:6048e5,manual:-1},Pi={startup:"A cada inicialização",daily:"Uma vez ao dia",weekly:"Uma vez por semana",manual:"Desativado (Apenas manual)"},Qa={"1h":"startup","6h":"daily","24h":"daily","a cada inicialização":"startup","a cada inicializacao":"startup","uma vez ao dia":"daily","uma vez por semana":"weekly","desativado (apenas manual)":"manual","apenas manual":"manual"};function Uo(e){let t=String(e||"").trim().toLowerCase();return Object.prototype.hasOwnProperty.call(zo,t)?t:Object.prototype.hasOwnProperty.call(Qa,t)?Qa[t]:"daily"}function Za(e){let t=Uo(e),n=zo[t];return n>0?n:0}function Ri(e,t,n){let o=Uo(t);if(o==="manual")return!1;let a=Number(e)||0;if(a<=0||o==="startup")return!0;let s=typeof n=="number"&&Number.isFinite(n)?n:Date.now(),i=Za(o);return s-a>=i}function Ni(e,t,n){let o=e||{},a=Array.isArray(o.LIVE)?o.LIVE:[],s=Array.isArray(o.MOVIE)?o.MOVIE:[],i=Array.isArray(o.SERIES)?o.SERIES:[],c=new Set,u=0;for(let l=0;l<a.length;l++){let f=a[l];f&&(f.group&&c.add(String(f.group).trim()),(f.tvgId||f.current_program)&&u++)}for(let l=0;l<s.length;l++){let f=s[l];f&&f.group&&c.add(String(f.group).trim())}for(let l=0;l<i.length;l++){let f=i[l];f&&f.group&&c.add(String(f.group).trim())}let d=0;return t&&typeof t=="object"&&(d=Object.keys(t).length),{totalChannels:a.length,totalVOD:s.length,totalSeries:i.length,totalGroups:c.size,totalEPG:Math.max(u,d),totalItems:a.length+s.length+i.length,hasXmltv:!!n}}function _i(e,t){let n=String(e||"").trim(),o=Number(t)||0;if(o===401||o===403){let s=new Error("Credenciais inválidas ou acesso negado (HTTP "+o+").");throw s.isAuthError=!0,s.status=o,s}if(!n)throw new Error("Servidor retornou resposta vazia.");let a=n.slice(0,200).toLowerCase();if(a.startsWith("<")||a.includes("<!doctype html")||a.includes("<html")){let s=new Error("Servidor retornou página HTML em vez de dados JSON.");throw s.isHtmlResponse=!0,s}if(/invalid username|invalid password|unauthorized/i.test(a)){let s=new Error("Usuário ou senha inválidos no servidor.");throw s.isAuthError=!0,s}try{return JSON.parse(n)}catch(s){let i=new Error("Resposta inválida do servidor (formato não reconhecido).");throw i.cause=s,i}}function Oi(e){let t=e&&e.message?String(e.message):String(e||"");return t?/unexpected token\s*<?\s*in json|json\.parse/i.test(t)?"O servidor respondeu em formato HTML inválido em vez de JSON/M3U.":/failed to fetch|networkerror|load failed/i.test(t)?"Não foi possível conectar ao servidor. Verifique a rede ou troque de fonte.":/aborted|abort/i.test(t)?"Conexão cancelada ou tempo esgotado ao aguardar o servidor.":t:"A conexão foi interrompida antes da lista terminar."}er.exports={BG_SYNC_INTERVALS_MS:zo,BG_SYNC_LABELS:Pi,normalizeBgSyncFrequency:Uo,getBgSyncIntervalMs:Za,shouldSyncServer:Ri,computeCatalogMetrics:Ni,parseSafeJsonResponse:_i,formatFriendlyLoadError:Oi}});var Go=Wn((gd,rr)=>{"use strict";function eo(e){if(e==null||e==="")return NaN;if(e instanceof Date){let a=e.getTime();return Number.isFinite(a)?a:NaN}if(typeof e=="number")return Number.isFinite(e)?e>1e11?e:e*1e3:NaN;let t=String(e).trim();if(!t)return NaN;if(/^\d{10}$/.test(t))return parseInt(t,10)*1e3;if(/^\d{13}$/.test(t))return parseInt(t,10);let n=t.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);if(n){let[,a,s,i,c,u,d,l]=n,f=l?`${l.slice(0,3)}:${l.slice(3)}`:"Z",p=`${a}-${s}-${i}T${c}:${u}:${d}${f}`,m=new Date(p).getTime();if(!Number.isNaN(m))return m}let o=new Date(t.replace(" ","T")).getTime();return Number.isNaN(o)?NaN:o}function Zn(e){if(typeof e!="string")return"";let t=e.trim();if(!t||!/^[A-Za-z0-9+/=]+$/.test(t)||t.length<4||t.length%4!==0)return t;try{let n=atob(t);if(!/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF]+$/.test(n))return t;try{return decodeURIComponent(escape(n))}catch{return n}}catch{return t}}function nr(e){if(!e)return null;let t=Zn(e.title||e.name||e.programme_title||""),n=Zn(e.desc||e.description||e.sub_title||""),o=Zn(e.category||e.genre||""),a=eo(e.start!==void 0?e.start:e.start_timestamp!==void 0?e.start_timestamp:e.startTs),s=eo(e.stop!==void 0?e.stop:e.stop_timestamp!==void 0?e.stop_timestamp:e.end!==void 0?e.end:e.stopTs);return!Number.isFinite(a)||!Number.isFinite(s)||s<=a?null:{title:t||"Programação",desc:n||"",category:o||"",start:a,stop:s,durationMs:s-a}}function to(e){if(!Array.isArray(e))return[];let t=[];for(let n=0;n<e.length;n++){let o=nr(e[n]);o&&t.push(o)}return t.sort((n,o)=>n.start-o.start),t}function Bi(){let e=new Set,t=[];for(let n=0;n<arguments.length;n++){let o=to(arguments[n]);for(let a=0;a<o.length;a++){let s=o[a],i=!1;for(let c=0;c<t.length;c++){let u=t[c];if(Math.abs(u.start-s.start)<6e4&&Math.abs(u.stop-s.stop)<6e4){i=!0,!u.desc&&s.desc&&(u.desc=s.desc),!u.category&&s.category&&(u.category=s.category);break}}i||(e.add(s.start),t.push(s))}}return t.sort((n,o)=>n.start-o.start),t}function Di(e,t){let n=Number.isFinite(t)?t:Date.now();return to(e).map(a=>{let s=a.start<=n&&a.stop>n,i=Math.max(0,n-a.start),c=Math.min(100,Math.max(0,Math.round(i/a.durationMs*100))),u=Math.max(0,a.stop-n);return{title:a.title,desc:a.desc,category:a.category,start:a.start,stop:a.stop,durationMs:a.durationMs,isLive:s,isPast:a.stop<=n,progressPct:c,remainingMs:u}})}function or(e,t){let n=Number.isFinite(t)?t:Date.now(),o=to(e);if(!o.length)return{current:null,next:null};let a=null;for(let c=0;c<o.length;c++)if(o[c].start<=n&&o[c].stop>n){a=o[c];break}a||(a=o.find(u=>u.start>n)||o[o.length-1]);let s=o.indexOf(a),i=s>=0&&s+1<o.length?o[s+1]:null;return{current:a,next:i}}function Fi(e,t){if(!e)return 0;let n=Number.isFinite(t)?t:Date.now();return e.start<=n&&e.stop>n?Math.max(0,Math.round((e.stop-n)/6e4)):0}function Vi(e){let t=eo(e);if(!Number.isFinite(t))return"--:--";let n=new Date(t);return String(n.getHours()).padStart(2,"0")+":"+String(n.getMinutes()).padStart(2,"0")}function $i(e){return!(e instanceof Date)||Number.isNaN(e.getTime())?"--:--":String(e.getHours()).padStart(2,"0")+":"+String(e.getMinutes()).padStart(2,"0")}function ar(e){let t=Math.floor(Number(e)/1e3);if(!Number.isFinite(t)||t<0)return"--:--";let n=Math.floor(t/3600),o=Math.floor(t%3600/60),a=t%60,s=String(o).padStart(2,"0"),i=String(a).padStart(2,"0");return n>0?n+":"+s+":"+i:s+":"+i}function Hi(e,t,n){let o=Number.isFinite(n)?n:Date.now(),{current:a,next:s}=or(t,o),i="",c="",u=0,d=0,l=!1;return a&&(l=a.start<=o&&a.stop>o,i=a.title,d=Math.min(100,Math.max(0,Math.round((o-a.start)/a.durationMs*100)))),s&&(c=s.title,u=Math.max(0,s.start-o)),{name:e&&(e.name||e.tvgName)||"Canal",logo:e&&e.logo||"",group:e&&e.group||"",currentTitle:i,nextTitle:c,countdownMs:u,countdownText:s?ar(u):"--:--",progressPct:d,isLive:l}}rr.exports={MS_PER_MINUTE:6e4,MS_PER_SECOND:1e3,toTimestampMs:eo,decodeMaybeBase64:Zn,normalizeProgram:nr,normalizePrograms:to,mergeProgramSources:Bi,buildTimeline:Di,pickNowNext:or,minutesRemaining:Fi,formatClock:Vi,formatClockDate:$i,formatCountdown:ar,summarizeChannel:Hi}});var lr=Wn((hd,ir)=>{"use strict";var zi=Go(),sr=7e3,Ui=400;function Gi(e){let t=e||{},n=Number.isFinite(t.nowMs)?t.nowMs:Date.now(),o=t.channel||null,a=zi.summarizeChannel(o,t.programs,n),s=Number.isFinite(t.channelNumber)&&t.channelNumber>0?t.channelNumber:"";return{number:s,hasNumber:s!=="",name:a.name,logo:a.logo,group:a.group,currentTitle:a.currentTitle,nextTitle:a.nextTitle,countdownMs:a.countdownMs,countdownText:a.countdownText,progressPct:a.progressPct,isLive:a.isLive,hasEpg:!!a.currentTitle}}function qi(e,t){let n=Number(e);if(!Number.isFinite(n)||n<0)return!1;let o=Number.isFinite(t)&&t>0?t:sr;return n>=o}function Wi(e){let t=String(e==null?"":e).replace(/\D+/g,"");if(!t)return null;let n=parseInt(t,10);return!Number.isFinite(n)||n<=0?null:n}function ji(e){let t=new Map;if(!Array.isArray(e))return t;for(let n=0;n<e.length;n++){let o=e[n];if(!o)continue;let a=qo(o);!a||t.has(a)||t.set(a,n+1)}return t}function qo(e){if(!e)return"";let t=String(e.tvgId||e["tvg-id"]||"").trim().toLowerCase();if(t)return"id:"+t;let n=String(e.name||"").trim().toLowerCase();return n?"name:"+n:""}function Ki(e,t,n){let o=qo(t);if(o&&e&&typeof e.get=="function"){let a=e.get(o);if(Number.isFinite(a))return a}return Number.isFinite(n)&&n>=0?n+1:null}ir.exports={ZAP_AUTO_HIDE_MS:sr,ZAP_MIN_INTERVAL_MS:Ui,buildZapInfo:Gi,shouldAutoHide:qi,parseChannelNumber:Wi,buildChannelNumberMap:ji,channelKey:qo,resolveChannelNumber:Ki}});var mr=Wn((vd,pr)=>{"use strict";var nn=["lower","upper","numbers","symbols"],cr={lower:"abc",upper:"ABC",numbers:"123",symbols:"#+="},dr=[["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l","ç"],["z","x","c","v","b","n","m",",",".","?"],["á","é","í","ó","ú","â","ê","ô","à","õ"]],ur=[["1","2","3","4","5","6","7","8","9","0"],["-",".",",","?","!","'","@","#","/",":"],["(",")","+","=","%","*","$","&","_",'"']],fr=[{action:"MODE",label:"⇧"},{action:"SPACE",label:"␣"},{action:"BACKSPACE",label:"⌫"},{action:"CLEAR",label:"⟲"}];function Mt(e){let t=nn.indexOf(e)>=0?e:"lower",n;return t==="numbers"||t==="symbols"?n=ur.map(o=>o.slice()):(n=dr.map(o=>o.slice()),t==="upper"&&(n=n.map(o=>o.map(a=>a.toUpperCase())))),n.concat([fr.slice()])}function Xi(e,t){let n=String(e==null?"":e);return{value:n,cursor:n.length,mode:nn.indexOf(t)>=0?t:"lower",row:0,col:0}}function Yi(e){return Mt(e&&e.mode).length}function no(e,t){let n=Mt(e&&e.mode),o=typeof t=="number"?t:e?e.row:0;return(n[o]||n[0]||[]).length}function Ji(e,t){if(!e)return e;let o=Mt(e.mode).length-1,a=e.row,s=e.col;if(t==="up")a=a>0?a-1:0;else if(t==="down")a=a<o?a+1:o;else if(t==="left")s=s>0?s-1:0;else if(t==="right"){let c=no(e,a);s=s<c-1?s+1:c-1}let i=no(e,a);return s>=i&&(s=i-1),s<0&&(s=0),Object.assign({},e,{row:a,col:s})}function Qi(e){if(!e)return null;let n=Mt(e.mode)[e.row];return n&&n[e.col]!==void 0?n[e.col]:null}function Zi(e,t){if(!e||!t)return e;let n=String(e.value||""),o=Math.max(0,Math.min(n.length,e.cursor)),a=n.slice(0,o)+t+n.slice(o);return Object.assign({},e,{value:a,cursor:o+t.length})}function el(e){if(!e)return e;let t=String(e.value||""),n=Math.max(0,Math.min(t.length,e.cursor));if(n===0)return e;let o=t.slice(0,n-1)+t.slice(n);return Object.assign({},e,{value:o,cursor:n-1})}function tl(e){return e&&Object.assign({},e,{value:"",cursor:0})}function nl(e){if(!e)return e;let t=nn.indexOf(e.mode),n=nn[(t+1)%nn.length],o=Mt(n);return Object.assign({},e,{mode:n,row:Math.min(e.row,o.length-1),col:0})}function ol(e){if(!e)return e;let t=Mt(e.mode),n=e.row,o=e.col,a=no(e,n);return o<a-1?o++:n<t.length-1?(n++,o=0):(n=0,o=0),Object.assign({},e,{row:n,col:o})}function al(e,t){if(!e)return e;let n=String(e.value||""),o=Math.max(0,Math.min(n.length,(e.cursor||0)+t));return Object.assign({},e,{cursor:o})}function rl(e){return cr[e&&e.mode||"lower"]||"abc"}pr.exports={KEYBOARD_MODES:nn,MODE_LABELS:cr,LETTER_ROWS:dr,NUMBER_ROWS:ur,CONTROL_ROW:fr,getRows:Mt,createState:Xi,getRowCount:Yi,getColCount:no,moveCursor:Ji,advanceCursor:ol,getFocusedKey:Qi,insertChar:Zi,backspace:el,clearText:tl,toggleMode:nl,moveTextCursor:al,getModeLabel:rl}});var jn="[FBG2-Player]",Ci=()=>{var t;let e=globalThis.process;return((t=e==null?void 0:e.env)==null?void 0:t.NODE_ENV)==="development"},qa=()=>{if(Ci())return!0;try{return typeof localStorage!="undefined"&&localStorage.getItem("FBG2_DEBUG")==="1"}catch{return!1}},z={debug(...e){qa()&&console.debug(jn,...e)},info(...e){qa()&&console.info(jn,...e)},warn(...e){console.warn(jn,...e)},error(...e){console.error(jn,...e)}};var Je=class{constructor(t){this.listeners=new Map;this.state="idle";if(!t)throw new Error("[FBG2] BasePlayer: elemento <video> obrigatório.");this.element=t}setVolume(t){try{this.element.volume=Math.max(0,Math.min(1,t))}catch(n){z.warn("setVolume falhou:",n)}}setSubtitleTrack(t){}getState(){return this.state}getBufferedMs(){let t=this.element.buffered;if(!t||t.length===0)return 0;let n=this.element.currentTime||0;for(let o=0;o<t.length;o+=1)if(t.start(o)<=n&&n<=t.end(o))return Math.max(0,(t.end(o)-n)*1e3);return 0}on(t,n){let o=this.listeners.get(t);o||(o=new Set,this.listeners.set(t,o)),o.add(n)}off(t,n){var o;(o=this.listeners.get(t))==null||o.delete(n)}setState(t){this.state=t}emit(t,n){let o=this.listeners.get(t);o&&o.forEach(a=>{try{a(n)}catch(s){z.error(`Listener de "${t}" lançou exceção:`,s)}})}finishDestroy(){this.setState("idle"),this.emit("destroyed"),this.teardownListeners()}teardownListeners(){this.listeners.clear()}};var Kn=(e,t,n)=>Math.max(t,Math.min(e,n)),Wa=e=>{if(!e)return{};if(typeof e=="object")return e;try{let t=JSON.parse(e);return t&&typeof t=="object"?t:{}}catch{return{}}},Cn=class extends Je{constructor(n){var o;super(n);this.platform="tizen";this.audioTracks=[];this.destroyed=!1;this.preparing=!1;this.tracksReady=!1;this.bufferedMs=0;this.applyDisplayRect=()=>{let n=this.element.getBoundingClientRect(),o=Math.round(n.width),a=Math.round(n.height);o<=0||a<=0||this.setDisplay({left:n.left,top:n.top,width:o,height:a})};if(!((o=window.webapis)!=null&&o.avplay))throw new Error("[FBG2] webapis.avplay indisponível neste dispositivo.");this.avplay=window.webapis.avplay,this.element.classList.add("fbg2-avplay-surface"),window.addEventListener("resize",this.applyDisplayRect),document.addEventListener("fullscreenchange",this.applyDisplayRect),document.addEventListener("webkitfullscreenchange",this.applyDisplayRect)}async play(n,o={}){var a,s,i,c,u;this.safeClose(),this.destroyed=!1,this.tracksReady=!1,this.preparing=!0,this.setState("preparing");try{this.avplay.open(n),this.avplay.setListener(this.buildListener());let d=(a=o.bufferSizeMs)!=null?a:3e3;try{this.avplay.setStreamingProperty("BUFFER_SIZE",String(d)),z.debug("BUFFER_SIZE =",d)}catch{z.warn("setStreamingProperty(BUFFER_SIZE) não suportado neste modelo.")}let l=Kn(Math.round(d/1e3),3,30);try{(i=(s=this.avplay).setBufferingParam)==null||i.call(s,"PLAYER_BUFFER_FOR_PLAY","PLAYER_BUFFER_SIZE_IN_SECOND",l),(u=(c=this.avplay).setBufferingParam)==null||u.call(c,"PLAYER_BUFFER_FOR_RESUME","PLAYER_BUFFER_SIZE_IN_SECOND",Math.max(l,15)),z.debug("setBufferingParam =",l,"s")}catch{z.warn("setBufferingParam não suportado neste modelo.")}if(this.applyDisplayRect(),await this.prepareAsync(),this.destroyed)return;this.extractTracks(),o.startPositionMs&&o.startPositionMs>0&&this.avplay.seekTo(Kn(o.startPositionMs,0,this.getDuration()||o.startPositionMs)),this.avplay.play(),this.preparing=!1,this.setState("playing"),this.emit("playing"),z.info("AVPlay reproduzindo:",n)}catch(d){this.preparing=!1,this.setState("error"),this.safeClose();let l=d instanceof Error?d.message:String(d);throw this.emit("error",{code:"AVPLAY_OPEN",message:l,fatal:!0,raw:d}),d}}pause(){try{this.avplay.getState()==="PLAYING"&&(this.avplay.pause(),this.setState("paused"),this.emit("paused"))}catch(n){z.warn("pause() falhou:",n)}}resume(){try{this.avplay.getState()==="PAUSED"&&(this.avplay.play(),this.setState("playing"),this.emit("playing"))}catch(n){z.warn("resume() falhou:",n)}}seek(n){try{let o=this.getDuration(),a=o>0?Kn(n,0,o):Math.max(0,n);this.avplay.seekTo(a)}catch(o){z.warn("seek() falhou:",o)}}stop(){this.safeClose(),this.setState("idle")}destroy(){this.destroyed||(this.destroyed=!0,this.safeClose(),window.removeEventListener("resize",this.applyDisplayRect),document.removeEventListener("fullscreenchange",this.applyDisplayRect),document.removeEventListener("webkitfullscreenchange",this.applyDisplayRect),this.finishDestroy())}setAudioTrack(n){let o=this.audioTracks.find(a=>a.id===n);if(!o){z.warn("Faixa de áudio desconhecida:",n);return}try{this.avplay.setSelectTrack("AUDIO",o.index),z.debug("Áudio selecionado:",o.name)}catch(a){z.warn("setSelectTrack falhou:",a)}}setVolume(n){try{this.avplay.setVolume(Kn(n,0,1))}catch(o){z.warn("setVolume falhou:",o)}}getState(){return this.preparing?"preparing":super.getState()}getBufferedMs(){return this.bufferedMs}getDuration(){try{return this.avplay.getDuration()||0}catch{return 0}}getCurrentTime(){try{return this.avplay.getCurrentTime()||0}catch{return 0}}setDisplay(n){try{this.avplay.setDisplayRect(Math.round(n.left),Math.round(n.top),Math.round(n.width),Math.round(n.height))}catch(o){z.warn("setDisplayRect falhou:",o)}}prepareAsync(){return new Promise((n,o)=>{this.avplay.prepareAsync(()=>{this.preparing=!1,n()},a=>{this.preparing=!1,o(a instanceof Error?a:new Error(String(a)))})})}safeClose(){try{this.avplay.getState()!=="IDLE"&&this.avplay.stop()}catch{}try{this.avplay.close()}catch{}this.preparing=!1}buildListener(){return{onbufferingstart:()=>{this.bufferedMs=0,this.setState("buffering"),this.emit("buffering")},onbufferingprogress:n=>{this.bufferedMs=Math.round(n),z.debug("buffering:",n+"%")},onbufferingcomplete:()=>{this.bufferedMs=0,this.setState("playing"),this.emit("buffered")},onstreamcompleted:()=>{this.setState("ended"),this.emit("ended")},oncurrentplaytime:n=>{this.tracksReady||this.extractTracks(),this.emit("timeupdate",{currentTimeMs:n,durationMs:this.getDuration()})},onerror:n=>{this.setState("error"),this.emit("error",{code:n,message:`AVPlay error: ${n}`,fatal:!0})},onevent:(n,o)=>{z.debug("onevent:",n,o)},ondrmevent:(n,o)=>{z.debug("drmevent:",n,o)},onsubtitlechange:(n,o)=>{z.debug("subtitle:",n,o)}}}extractTracks(){var a,s;let n=[];try{n=(a=this.avplay.getTotalTrackInfo())!=null?a:[]}catch{}if(!n||n.length===0)try{n=(s=this.avplay.getCurrentStreamInfo())!=null?s:[]}catch(i){z.warn("getCurrentStreamInfo falhou:",i);return}this.audioTracks=(n!=null?n:[]).filter(i=>i.type==="AUDIO").map((i,c)=>{var y,g,h;let u={...Wa(i.info),...Wa(i.extra_info)},d=(y=u.track_num)!=null?y:u.track_index,l=Number(d),f=Number.isFinite(l)&&l>=0?l:typeof i.index=="number"&&i.index>=0?i.index:c,p=String((h=(g=u.track_lang)!=null?g:u.language)!=null?h:"und"),m=u.track_name?String(u.track_name):"";return{id:`audio-${f}`,kind:"audio",index:f,name:m||`Áudio (${p.toUpperCase()})`,language:p}}),this.audioTracks.length>0&&(this.tracksReady=!0);let o={audioTracks:this.audioTracks,subtitleTracks:[]};this.emit("tracksReady",o)}};var Ln=class extends Je{constructor(n){super(n);this.platform="webos";this.audioTracks=[];this.subtitleTracks=[];this.lastError=null;this.handleLoadedMetadata=()=>{this.extractTracks(),this.emit("ready")};this.handleWaiting=()=>{this.setState("buffering"),this.emit("buffering")};this.handlePlaying=()=>{this.setState("playing"),this.emit("buffered")};this.handleEnded=()=>{this.setState("ended"),this.emit("ended")};this.handleError=()=>{var n,o,a,s;this.lastError=this.element.error,this.setState("error"),this.emit("error",{code:(o=(n=this.lastError)==null?void 0:n.code)!=null?o:"UNKNOWN",message:`webOS Video error: code ${(s=(a=this.lastError)==null?void 0:a.code)!=null?s:"desconhecido"}`,fatal:!0,raw:this.lastError})};this.handleTimeUpdate=()=>{this.emit("timeupdate",{currentTimeMs:this.getCurrentTime(),durationMs:this.getDuration()})};n.addEventListener("loadedmetadata",this.handleLoadedMetadata),n.addEventListener("waiting",this.handleWaiting),n.addEventListener("playing",this.handlePlaying),n.addEventListener("ended",this.handleEnded),n.addEventListener("error",this.handleError),n.addEventListener("timeupdate",this.handleTimeUpdate)}async play(n,o={}){try{if(this.stop(),this.lastError=null,this.setState("preparing"),this.element.src=n,o.headers&&z.warn("webOS <video> nativo não suporta headers customizados."),this.element.load(),o.startPositionMs&&o.startPositionMs>0){let a=()=>{var i;let s=Math.min((i=o.startPositionMs)!=null?i:0,(this.element.duration||1/0)*1e3);this.element.currentTime=s/1e3};this.element.addEventListener("loadedmetadata",a,{once:!0})}await this.element.play(),this.setState("playing"),this.emit("playing"),z.info("webOS reproduzindo:",n)}catch(a){if(a instanceof Error&&a.name==="AbortError"){z.debug("play() abortado por nova carga.");return}this.setState("error");let s=a instanceof Error?a.message:String(a);throw this.emit("error",{code:"WEBOS_PLAY",message:s,fatal:!0,raw:a}),a}}pause(){this.element.pause(),this.setState("paused"),this.emit("paused")}resume(){let n=()=>{this.setState("playing"),this.emit("playing")},o=this.element.play();if(o&&typeof o.then=="function"){o.then(n).catch(a=>{let s=a instanceof Error?a.message:String(a);this.emit("error",{code:"WEBOS_RESUME",message:s,fatal:!1,raw:a})});return}n()}seek(n){let o=this.element.duration||0,a=o>0?Math.max(0,Math.min(n/1e3,o)):Math.max(0,n/1e3);this.element.currentTime=a}stop(){this.element.pause(),this.element.removeAttribute("src");try{this.element.load()}catch{}this.setState("idle")}destroy(){this.stop(),this.element.removeEventListener("loadedmetadata",this.handleLoadedMetadata),this.element.removeEventListener("waiting",this.handleWaiting),this.element.removeEventListener("playing",this.handlePlaying),this.element.removeEventListener("ended",this.handleEnded),this.element.removeEventListener("error",this.handleError),this.element.removeEventListener("timeupdate",this.handleTimeUpdate),this.finishDestroy()}setAudioTrack(n){let o=this.audioTracks.find(s=>s.id===n);if(!o){z.warn("Faixa de áudio desconhecida:",n);return}let a=this.element.audioTracks;if(!a){z.warn("audioTracks não exposto por este firmware webOS.");return}for(let s=0;s<a.length;s+=1){let i=a[s];i&&(i.enabled=s===o.index)}z.debug("Áudio selecionado:",o.name)}setSubtitleTrack(n){var a;let o=this.element.textTracks;for(let s=0;s<o.length;s+=1){let i=o[s];i&&(i.mode=n&&((a=this.subtitleTracks.find(c=>c.id===n))==null?void 0:a.index)===s?"showing":"disabled")}}setVolume(n){this.element.volume=Math.max(0,Math.min(1,n))}getDuration(){let n=this.element.duration;return Number.isFinite(n)?n*1e3:0}getCurrentTime(){return(this.element.currentTime||0)*1e3}setDisplay(n){Object.assign(this.element.style,{position:"absolute",left:`${n.left}px`,top:`${n.top}px`,width:`${n.width}px`,height:`${n.height}px`})}extractTracks(){this.audioTracks=[],this.subtitleTracks=[];let n=this.element.audioTracks;if(n)for(let s=0;s<n.length;s+=1){let i=n[s];i&&this.audioTracks.push({id:`audio-${s}`,kind:"audio",index:s,name:i.label||i.language||`Áudio ${s+1}`,language:i.language||"und"})}let o=this.element.textTracks;for(let s=0;s<o.length;s+=1){let i=o[s];i&&(i.kind!=="subtitles"&&i.kind!=="captions"||this.subtitleTracks.push({id:`subtitle-${s}`,kind:"subtitle",index:s,name:i.label||i.language||`Legenda ${s+1}`,language:i.language||"und"}))}let a={audioTracks:this.audioTracks,subtitleTracks:this.subtitleTracks};this.emit("tracksReady",a)}};var wn=typeof window!="undefined"?window.Hls:void 0,$o=wn,ut=wn&&wn.Events||{},Xn=wn&&wn.ErrorTypes||{};var Li=e=>/\.m3u8([?#]|$)/i.test(e)||/[?&]format=m3u8/i.test(e),wi={maxBufferLength:30,maxMaxBufferLength:60,liveSyncDurationCount:3,manifestLoadingMaxRetry:2,levelLoadingMaxRetry:2,fragLoadingMaxRetry:4,startLevel:-1},Qt=class extends Je{constructor(n){super(n);this.platform="browser";this.hls=null;this.usingNativeMedia=!1;this.audioTracks=[];this.subtitleTracks=[];this.handleWaiting=()=>{this.setState("buffering"),this.emit("buffering")};this.handleBuffered=()=>{this.setState("playing"),this.emit("buffered")};this.handleEnded=()=>{this.setState("ended"),this.emit("ended")};this.handleVideoError=()=>{let n=this.element.error;this.usingNativeMedia&&n&&(this.setState("error"),this.emit("error",{code:n.code,message:`midia nativa error: code ${n.code}`,fatal:!0,raw:n}))};n.addEventListener("waiting",this.handleWaiting),n.addEventListener("playing",this.handleBuffered),n.addEventListener("ended",this.handleEnded),n.addEventListener("error",this.handleVideoError)}async play(n,o={}){try{this.stop(),this.setState("preparing");let a=Li(n),s=a&&$o.isSupported(),i=a&&!!this.element.canPlayType("application/vnd.apple.mpegurl");if(s)this.playWithHlsJs(n,o),await this.waitForManifest();else if(i||!a)this.playNative(n,o);else throw new Error("[FBG2] Nenhum engine HLS disponível neste dispositivo.");o.startPositionMs&&o.startPositionMs>0&&this.element.addEventListener("loadedmetadata",()=>{var c;this.element.currentTime=((c=o.startPositionMs)!=null?c:0)/1e3},{once:!0}),await this.element.play(),this.setState("playing"),s||this.emitTracks(),this.emit("playing")}catch(a){if(a instanceof Error&&a.name==="AbortError"){z.debug("play() abortado por nova carga.");return}this.setState("error");let s=a instanceof Error?a.message:String(a);throw this.emit("error",{code:"HLS_PLAY",message:s,fatal:!0,raw:a}),a}}pause(){this.element.pause(),this.setState("paused"),this.emit("paused")}resume(){let n=()=>{this.setState("playing"),this.emit("playing")},o=this.element.play();if(o&&typeof o.then=="function"){o.then(n).catch(a=>{let s=a instanceof Error?a.message:String(a);this.emit("error",{code:"HLS_RESUME",message:s,fatal:!1,raw:a})});return}n()}seek(n){let o=this.element.duration||0,a=o>0?Math.max(0,Math.min(n/1e3,o)):Math.max(0,n/1e3);this.element.currentTime=a}stop(){this.hls&&(this.hls.destroy(),this.hls=null),this.usingNativeMedia=!1,this.element.pause(),this.element.removeAttribute("src");try{this.element.load()}catch{}this.setState("idle")}destroy(){this.stop(),this.element.removeEventListener("waiting",this.handleWaiting),this.element.removeEventListener("playing",this.handleBuffered),this.element.removeEventListener("ended",this.handleEnded),this.element.removeEventListener("error",this.handleVideoError),this.finishDestroy()}setAudioTrack(n){var s;let o=this.audioTracks.find(i=>i.id===n),a=o?o.index:Number(n.replace("audio-",""));this.hls&&Number.isInteger(a)&&a>=0?(this.hls.audioTrack=a,z.debug("Áudio selecionado:",(s=o==null?void 0:o.name)!=null?s:n)):z.warn("setAudioTrack sem hls.js ativo:",n)}setSubtitleTrack(n){var a;let o=this.element.textTracks;for(let s=0;s<o.length;s+=1){let i=o[s];i&&(i.mode=n&&((a=this.subtitleTracks.find(c=>c.id===n))==null?void 0:a.index)===s?"showing":"disabled")}}setVolume(n){this.element.volume=Math.max(0,Math.min(1,n))}getDuration(){let n=this.element.duration;return Number.isFinite(n)?n*1e3:0}getCurrentTime(){return(this.element.currentTime||0)*1e3}waitForManifest(n=15e3){let o=this.hls;return!o||o.levels&&o.levels.length>0?Promise.resolve():new Promise((a,s)=>{let i,c=()=>{i&&clearTimeout(i),o.off(ut.MANIFEST_PARSED,u),o.off(ut.ERROR,d)},u=()=>{c(),a()},d=(l,f)=>{!f.fatal||f.type===Xn.NETWORK_ERROR||(c(),s(new Error(`hls.js fatal: ${f.type}/${f.details}`)))};i=setTimeout(()=>{c(),s(new Error("timeout aguardando o manifesto HLS"))},n),o.on(ut.MANIFEST_PARSED,u),o.on(ut.ERROR,d)})}playWithHlsJs(n,o){let a={...wi};o.headers&&Object.keys(o.headers).length>0&&(a.xhrSetup=s=>{var i;Object.entries((i=o.headers)!=null?i:{}).forEach(([c,u])=>s.setRequestHeader(c,u))}),this.hls=new $o(a),this.hls.loadSource(n),this.hls.attachMedia(this.element),this.hls.on(ut.MANIFEST_PARSED,()=>{z.debug("Manifesto HLS carregado."),this.emit("ready"),this.emitTracks()}),this.hls.on(ut.AUDIO_TRACKS_UPDATED,()=>this.emitTracks()),this.hls.on(ut.ERROR,(s,i)=>{var c,u;if(i.fatal){switch(z.error("Erro fatal hls.js:",i.type,i.details),i.type){case Xn.NETWORK_ERROR:try{(c=this.hls)==null||c.startLoad();return}catch{}break;case Xn.MEDIA_ERROR:try{(u=this.hls)==null||u.recoverMediaError();return}catch{}break;default:break}this.setState("error"),this.emit("error",{code:i.details,message:`hls.js fatal: ${i.type}/${i.details}`,fatal:!0,raw:i})}})}emitTracks(){var i,c,u;let n=this.hls;this.audioTracks=[],this.subtitleTracks=[],((i=n==null?void 0:n.audioTracks)!=null?i:[]).forEach((d,l)=>{this.audioTracks.push({id:`audio-${l}`,kind:"audio",index:l,name:d.name||d.lang||`Áudio ${l+1}`,language:d.lang||"und"})}),this.audioTracks.length===0&&((u=(c=n==null?void 0:n.levels)==null?void 0:c.length)!=null?u:0)>0&&this.audioTracks.push({id:"audio-0",kind:"audio",index:0,name:"Áudio principal",language:"und"});let a=this.element.textTracks;for(let d=0;d<a.length;d+=1){let l=a[d];l&&(l.kind!=="subtitles"&&l.kind!=="captions"||this.subtitleTracks.push({id:`subtitle-${d}`,kind:"subtitle",index:d,name:l.label||l.language||`Legenda ${d+1}`,language:l.language||"und"}))}let s={audioTracks:this.audioTracks,subtitleTracks:this.subtitleTracks};this.emit("tracksReady",s)}playNative(n,o){this.usingNativeMedia=!0,this.element.src=n,this.element.load(),z.info("Usando reprodução nativa (sem MSE):",n)}};var ja=()=>{var e;return typeof window!="undefined"&&!!((e=window.webapis)!=null&&e.avplay)},Ve=class Ve{static detectPlatform(){if(ja())return"tizen";let t=typeof navigator!="undefined"?navigator.userAgent:"",n=/Tizen/i.test(t)||/SMART-TV/i.test(t)||/Samsung.*TV/i.test(t),o=typeof window!="undefined"&&typeof window.tizen!="undefined";if(n&&o||o)return"tizen";let a=typeof window!="undefined"&&(typeof window.webOS!="undefined"||typeof window.PalmSystem!="undefined"),s=/Web0S/i.test(t)||/webOS/i.test(t)||/LG.*TV/i.test(t);return a&&s||a?"webos":"browser"}static create(t,n){var a;if(!t)throw new Error("[FBG2] PlayerFactory.create: elemento <video> obrigatório.");let o=n!=null?n:Ve.detectPlatform();switch(z.info("Criando player para plataforma:",o),o){case"tizen":{if(!ja())return z.warn("webapis.avplay ausente — degradando para HLS/MSE no Tizen."),new Qt(t);if(Ve.tizen&&Ve.tizen.element===t)return Ve.tizen.player;(a=Ve.tizen)==null||a.player.destroy();let s=new Cn(t);return Ve.tizen={element:t,player:s},s}case"webos":return new Ln(t);default:return new Qt(t)}}static releaseTizen(){var t;(t=Ve.tizen)==null||t.player.destroy(),Ve.tizen=null}};Ve.tizen=null;var kt=Ve;var Ka="FBG2_PLAYER_LEGACY",Xa="FBG2_PLAYER_ENGINE",ki=!0,Ti="https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json",Ai=4e3,Ii=/\.(m3u8|mpd|mp4|m4v|webm|mov)([?#]|$)/i,Mi=e=>{let t=e&&(e.message||e.msg)||"";return(e&&e.name||"")==="AbortError"||/interrupt|abort/i.test(t)},Yn=e=>{try{return localStorage.getItem(e)}catch{return null}},Zt,U,en,Tt,ft,tn,kn,Tn,At,It,An,le,Me,Ya,Ja,Jn=class Jn{constructor(){Ie(this,le);Ie(this,U,null);Ie(this,en,null);Ie(this,Tt,!1);Ie(this,ft,!1);Ie(this,tn,0);Ie(this,kn,4e3);Ie(this,Tn,[]);Ie(this,At,null);Ie(this,It,null);Ie(this,An,{});J(this,It,null)}static get instance(){return H(this,Zt)||J(this,Zt,new Jn),H(this,Zt)}setHooks(t){return J(this,An,t||{}),this}get useFactory(){if(Yn(Ka)==="1")return!1;let t=String(Yn(Xa)||"").toLowerCase();if(t==="legacy")return!1;if(t==="factory")return!0;let n=String(H(this,It)||"").toLowerCase();return n==="legacy"?!1:n==="factory"?!0:ki}get engineReason(){if(Yn(Ka)==="1")return"legado (FBG2_PLAYER_LEGACY=1)";let t=String(Yn(Xa)||"").toLowerCase();if(t==="legacy")return"legado (FBG2_PLAYER_ENGINE local)";if(t==="factory")return"factory (FBG2_PLAYER_ENGINE local)";let n=String(H(this,It)||"").toLowerCase();return n==="legacy"?"legado (version.json)":n==="factory"?"factory (version.json)":"factory (default)"}get player(){return H(this,U)}get platform(){try{return kt.detectPlatform()}catch{return"browser"}}isActive(){return!!(H(this,ft)&&H(this,U))}tryPlay(t,n){if(!this.useFactory||!n||!Ii.test(String(n)))return!1;let o;try{o=Ce(this,le,Ya).call(this)}catch(i){return console.warn("[PlayerService] factory indisponível, seguindo no legado:",i),!1}if(!o)return!1;J(this,ft,!0);let a=++Ga(this,tn)._,s=t&&t.currentTime>5&&(t.contentType==="MOVIE"||t.contentType==="SERIES")?Math.round(t.currentTime*1e3):0;return Ce(this,le,Me).call(this,"onFactoryStart",{url:n,platform:o.platform,reason:this.engineReason}),Promise.resolve(o.play(n,{bufferSizeMs:H(this,kn),startPositionMs:s})).catch(i=>{a===H(this,tn)&&(Mi(i)||Ce(this,le,Me).call(this,"onError",{message:i&&i.message||String(i),fatal:!0,raw:i},!0))}),!0}pause(){return this.isActive()?(H(this,U).pause(),!0):!1}resume(){return this.isActive()?(H(this,U).resume(),!0):!1}toggle(){return this.isActive()?(H(this,U).getState&&H(this,U).getState()==="paused"?H(this,U).resume():H(this,U).pause(),!0):!1}seekMs(t){if(!this.isActive())return!1;let n=Math.max(0,Math.round(Number(t)||0));return H(this,U).seek(n),!0}stop(){if(J(this,tn,H(this,tn)+1),J(this,ft,!1),!!H(this,U))try{H(this,U).stop()}catch(t){console.warn("[PlayerService] stop falhou:",t)}}release(){if(this.stop(),H(this,U))try{H(this,U).destroy()}catch(t){console.warn("[PlayerService] destroy falhou:",t)}J(this,U,null),J(this,en,null),J(this,Tt,!1),kt.releaseTizen()}setBufferMs(t){let n=Number(t);!Number.isFinite(n)||n<=0||J(this,kn,Math.max(3e3,Math.min(3e4,Math.round(n))))}getAudioTracks(){return H(this,Tn)}getActiveAudioId(){return H(this,At)}setAudioTrack(t){if(!H(this,U)||!t)return!1;J(this,At,t);try{return H(this,U).setAudioTrack(t),!0}catch(n){return console.warn("[PlayerService] setAudioTrack falhou:",n),!1}}getCurrentTimeSec(){if(!H(this,U))return 0;try{return(H(this,U).getCurrentTime()||0)/1e3}catch{return 0}}getDurationSec(){if(!H(this,U))return 0;try{return(H(this,U).getDuration()||0)/1e3}catch{return 0}}async hydrateRemote(){let t=typeof AbortController=="function"?new AbortController:null,n=t?setTimeout(()=>t.abort(),Ai):null;try{let o=await fetch(Ti+"?t="+Date.now(),{cache:"no-store",signal:t?t.signal:void 0});if(!o||!o.ok)return;let a=await o.json(),s=a&&a.playerEngine?String(a.playerEngine).toLowerCase():"";(s==="factory"||s==="legacy")&&(J(this,It,s),Ce(this,le,Me).call(this,"onEngineFlag",{engine:s,reason:this.engineReason}))}catch{}finally{n&&clearTimeout(n)}}};Zt=new WeakMap,U=new WeakMap,en=new WeakMap,Tt=new WeakMap,ft=new WeakMap,tn=new WeakMap,kn=new WeakMap,Tn=new WeakMap,At=new WeakMap,It=new WeakMap,An=new WeakMap,le=new WeakSet,Me=function(t,...n){let o=H(this,An)[t];if(typeof o=="function")try{return o(...n)}catch(a){console.warn("[PlayerService] hook "+t+" falhou:",a);return}},Ya=function(){let t=typeof document!="undefined"?document.getElementById("tvPlayer"):null;if(!t)return null;if(H(this,U)&&H(this,en)===t)return H(this,U);if(H(this,U)){try{H(this,U).destroy()}catch{}J(this,U,null),J(this,Tt,!1)}return J(this,en,t),J(this,U,kt.create(t)),Ce(this,le,Ja).call(this),H(this,U)},Ja=function(){if(H(this,Tt)||!H(this,U))return;let t=H(this,U);t.on("buffering",()=>Ce(this,le,Me).call(this,"onBuffering")),t.on("buffered",()=>Ce(this,le,Me).call(this,"onPlaying")),t.on("playing",()=>Ce(this,le,Me).call(this,"onPlaying")),t.on("paused",()=>Ce(this,le,Me).call(this,"onPaused")),t.on("ended",()=>Ce(this,le,Me).call(this,"onEnded")),t.on("durationchange",()=>Ce(this,le,Me).call(this,"onDuration")),t.on("timeupdate",n=>Ce(this,le,Me).call(this,"onTimeUpdate",n)),t.on("tracksReady",n=>{let o=n&&n.audioTracks||[];J(this,Tn,o),!H(this,At)&&o.length>0&&J(this,At,o[0].id),Ce(this,le,Me).call(this,"onTracks",n)}),t.on("error",n=>{let o=!n||n.fatal!==!1;o&&J(this,ft,!1),Ce(this,le,Me).call(this,"onError",n,o)}),t.on("destroyed",()=>{J(this,ft,!1)}),J(this,Tt,!0)},Ie(Jn,Zt,null);var Ho=Jn,Qn=Ho.instance;typeof window!="undefined"&&(window.FBG2PlayerService=Qn);var Ae=tr(),xt=Go(),bt=lr(),_e=mr(),r=e=>document.getElementById(e);window.mk21App={playStream:function(e,t){return fe(e,t)},stopCurrentPlayer:function(e){return $n(e)},switchContentType:function(e){return Pe(e)},openAudioSubsModal:function(){return $e()},logout:function(){return bn()},initWebOSScrollFix:function(){return Qs()},get activeItem(){return v},get playerService(){return window.FBG2PlayerService},openGuide:function(){return li()},closeGuide:function(){return Va()},guideState:function(){return P},loadXmltv:function(e){return Da(e)},showZapHud:function(e){return _o(e)},hideZapHud:function(){return fi()},openOsk:function(e){return pi(e)},closeOsk:function(e){return $a(e)},oskState:function(){return F}};var Wo=[];(function(){let t=window.FBG2PlayerService;if(!t||typeof t.setHooks!="function"){console.warn("[PlayerService] ausente: app seguira no caminho legado.");return}t.setHooks({bufferSizeMs:function(){let n=Xe[Lt()],o=n&&n.hls&&n.hls.maxBufferLength?n.hls.maxBufferLength:4;return Math.max(3e3,o*1e3)},onFactoryStart:function(n){x("[Player Engine] PlayerFactory ("+(n&&n.platform)+") em: "+(n&&n.url),"info"),x("[Player Engine] Motor escolhido: "+(n&&n.reason),"debug")},onBuffering:function(){r("epgStatus")&&(r("epgStatus").textContent="⏳ CARREGANDO...")},onPlaying:function(){at(),Te=0,Rt=Date.now(),Ts(!0),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(v)},onPaused:function(){typeof de=="function"&&de(!1)},onEnded:function(){typeof de=="function"&&de(!1)},onTracks:function(n){Wo=n&&n.audioTracks||[],Wo.length>0&&x("[Player Engine] "+Wo.length+" faixa(s) de audio na factory.","info")},onTimeUpdate:function(){typeof St=="function"&&St()},onError:function(n,o){let a=n&&(n.message||n.code)||"erro desconhecido";x("[Player Engine] Erro da factory: "+a,o?"error":"warn"),o&&Ht("Erro do player (factory)")},onEngineFlag:function(n){x("[Player Engine] Flag remota: "+(n&&n.engine)+" -> "+(n&&n.reason),"info")}})})();function on(e,t){let n=r("splashProgressBar"),o=r("splashStatusText");n&&(n.style.width=e+"%"),o&&t&&(o.textContent=t)}function ze(){let e=r("startupSplashScreen");e&&(on(100,"Tudo Pronto!"),e.classList.add("fade-out"),e.style.opacity="0",e.style.display="none",e.style.pointerEvents="none",setTimeout(()=>{try{e&&e.parentNode&&e.parentNode.removeChild(e)}catch{}},200))}setTimeout(ze,3500);Element.prototype.replaceChildren||(Element.prototype.replaceChildren=function(...e){for(;this.firstChild;)this.removeChild(this.firstChild);this.append(...e)});var rt=[{id:"vlog",name:"VLOG",url:"http://tocombox21.cyou"},{id:"tannix",name:"TANNIX",url:"http://ovo54.sbs"},{id:"lubtv",name:"LUB TV",url:"http://coxinhasemflango.shop"},{id:"mk21pro",name:"MK21 PRÓ",url:"http://app.tigerouro.shop"},{id:"cb6000",name:"CB6000",url:"http://cdn.caterlune.top"},{id:"cinelon",name:"CINELON",url:"http://parcerialife.lat"},{id:"multttv",name:"MULTT TV",url:"http://dali-as.skin"}];function Z(e){return String(e||"").replace(/^[^\w\dÀ-ÿ]+/i,"").trim()||e}var L=rt.map(e=>({...e}));(function(){try{let t=localStorage.getItem("mk21_servers_list");if(!t)return;let n=JSON.parse(t);if(!Array.isArray(n)||n.length===0)return;let o=Object.create(null),a=Object.create(null);rt.forEach(c=>{o[c.id]=c,a[Z(c.name).toUpperCase()]=c});let s=[],i=new Set;n.forEach(c=>{if(!c||!c.url)return;let u=Z(c.name||""),d=o[c.id]||a[u.toUpperCase()]||null;d&&!c.isCustomOverride?(s.push({...d}),i.add(d.id)):(s.push(c),c.id&&i.add(c.id))}),rt.forEach(c=>{i.has(c.id)||s.push({...c})}),s.length>0&&(L=s,localStorage.setItem("mk21_servers_list",JSON.stringify(L)))}catch{}})();var ga="mk21_credentials";function Wt(){try{let e=localStorage.getItem(ga);if(!e)return null;let t=JSON.parse(e);if(t&&typeof t.user=="string"&&typeof t.pass=="string"){let n=t.user.trim(),o=t.pass.trim();if(n.length>0&&o.length>0)return{user:n,pass:o,serverIndex:typeof t.serverIndex=="number"?t.serverIndex:null}}}catch{}return null}function sl(e,t,n){let o=String(e||"").trim(),a=String(t||"").trim(),s={user:o,pass:a,serverIndex:typeof n=="number"?n:N,savedAt:Date.now()};try{localStorage.setItem(ga,JSON.stringify(s)),localStorage.setItem("mk21_user",o),localStorage.setItem("mk21_username",o),localStorage.setItem("iptv_user",o),localStorage.setItem("mk21_pass",a),localStorage.setItem("mk21_password",a),localStorage.setItem("iptv_pass",a)}catch{}}function il(){try{localStorage.removeItem(ga),localStorage.removeItem("mk21_auth_token"),localStorage.removeItem("mk21_user"),localStorage.removeItem("mk21_username"),localStorage.removeItem("iptv_user"),localStorage.removeItem("mk21_pass"),localStorage.removeItem("mk21_password"),localStorage.removeItem("iptv_pass")}catch{}}var gr=["18+","+18","ADULTO","ADULTOS","ADULT","XXX","SEXY","PLAYBOY","PENTHOUSE","VENUS","HOT ","HUSTLER","FORBIDDEN","FORA DA LEI","S0X","PRIVE","PRIVÉ","RED LIGHT","SEX","EROTIC","ERÓTICO","EROTICO","BRASILEIRINHAS","TIGRESA","CINEMA 18","CINE 18","SEXTREME"],Xr="mk21_play_db_v6",ll=1,gn="catalog_cache_v6";function Yr(){return new Promise(e=>{if(!window.indexedDB){e(null);return}let t=indexedDB.open(Xr,ll);t.onupgradeneeded=n=>{let o=n.target.result;o.objectStoreNames.contains(gn)||o.createObjectStore(gn,{keyPath:"id"})},t.onsuccess=n=>e(n.target.result),t.onerror=()=>e(null)})}var cl=400,hr=0;function uo(e){return e&&(typeof e.isAdult=="undefined"&&(e.isAdult=re(e.name)||re(e.group)),e)}function _n(e,t,n){try{if(!t||!t.length)return;let o=Date.now();if(!n&&o-hr<4e3)return;hr=o;let a=t.slice(0,cl).map(un),s=JSON.stringify({items:a,updatedAt:o,total:t.length});localStorage.setItem("mk21_fast_cache_"+e,s),localStorage.setItem("mk21_fast_cache_time_"+e,String(o)),localStorage.setItem("mk21_fast_cache_universal",JSON.stringify({serverId:e,items:a,updatedAt:o}))}catch(o){console.warn("[MK21] save fast cache falhou",o)}}function dn(e){let t=o=>{try{let a=localStorage.getItem(o);if(!a)return null;let s=JSON.parse(a),i=Array.isArray(s)?s:s&&Array.isArray(s.items)?s.items:null;return!i||!i.length?null:{items:i.map(uo),updatedAt:s&&s.updatedAt||0,total:s&&s.total||i.length}}catch{return null}},n=t("mk21_fast_cache_"+e)||t("mk21_fast_cache_universal");return n?{LIVE:n.items,MOVIE:[],SERIES:[],_totalLive:n.total,_updatedAt:n.updatedAt}:null}async function hn(e){try{let t=await Yr();if(t){let n=await new Promise(o=>{let s=t.transaction(gn,"readonly").objectStore(gn).get(e);s.onsuccess=()=>o(s.result?s.result.payload:null),s.onerror=()=>o(null)});try{t.close()}catch{}if(n&&n.LIVE&&n.LIVE.length>0)return n.LIVE.forEach(uo),(n.MOVIE||[]).forEach(uo),(n.SERIES||[]).forEach(uo),n._updatedAt=Date.now(),n}}catch{}return dn(e)}async function Jr(e,t){t&&t.LIVE&&t.LIVE.length>0&&_n(e,t.LIVE,!0);let n=(t&&t.LIVE||[]).length+(t&&t.MOVIE||[]).length+(t&&t.SERIES||[]).length;if(typeof it=="function"&&it(e,t),n>4e4){console.log("[MK21] catálogo grande ("+n+"): cache completo no índice paginado");return}try{let o=await Yr();if(!o)return;let a={LIVE:(t.LIVE||[]).map(un),MOVIE:(t.MOVIE||[]).map(un),SERIES:(t.SERIES||[]).map(un)};o.transaction(gn,"readwrite").objectStore(gn).put({id:e,payload:a,updatedAt:Date.now()})}catch(o){console.warn("[MK21] save cache falhou",o)}}var Qr="mk21_catalog_index_v1",dl=1,Be="items",vn="meta";function Gn(){return new Promise(e=>{try{if(!window.indexedDB)return e(null);let t=indexedDB.open(Qr,dl);t.onupgradeneeded=n=>{let o=n.target.result;if(!o.objectStoreNames.contains(Be)){let a=o.createObjectStore(Be,{keyPath:"key"});a.createIndex("serverType",["serverId","contentType"],{unique:!1}),a.createIndex("serverGroup",["serverId","contentType","group"],{unique:!1}),a.createIndex("serverName",["serverId","contentType","normalizedName"],{unique:!1})}o.objectStoreNames.contains(vn)||o.createObjectStore(vn,{keyPath:"serverId"})},t.onsuccess=n=>e(n.target.result),t.onerror=()=>e(null)}catch{e(null)}})}async function oa(e){let t=await Gn();if(!t)return{add(){},async finish(){}};try{await new Promise((i,c)=>{let u=t.transaction(Be,"readwrite"),l=u.objectStore(Be).index("serverType").openCursor(IDBKeyRange.bound([e,"LIVE"],[e,"SERIES"]));l.onsuccess=f=>{let p=f.target.result;p&&(p.delete(),p.continue())},u.oncomplete=i,u.onerror=()=>c(u.error)})}catch(i){console.warn("[MK21] limpeza do índice falhou",i)}let n=[],o=Promise.resolve(),a=0,s=()=>{if(!n.length)return;let i=n;n=[],o=o.then(()=>new Promise(c=>{try{let u=t.transaction(Be,"readwrite"),d=u.objectStore(Be);i.forEach(l=>d.put({key:e+":"+l.contentType+":"+a++,serverId:e,contentType:l.contentType,name:l.name||"Item",normalizedName:String(l.name||"").toLocaleLowerCase("pt-BR"),group:l.group||"Geral",logo:l.logo||"",tvgId:l.tvgId||"",tvgName:l.tvgName||"",url:l.url,isAdult:!!l.isAdult,position:a-1})),u.oncomplete=c,u.onerror=c}catch{c()}}))};return{add(i){n.push(i),n.length>=500&&s()},async finish(){s(),await o;try{await new Promise(i=>{let c=t.transaction(vn,"readwrite");c.objectStore(vn).put({serverId:e,updatedAt:Date.now(),itemCount:a}),c.oncomplete=i,c.onerror=i})}catch{}try{t.close()}catch{}}}}async function ul(e,t,n,o,a){let s=await Gn();return s?new Promise(i=>{let c=[];try{let u=s.transaction(Be,"readonly"),d=u.objectStore(Be).index("serverType"),l=IDBKeyRange.bound([e,t],[e,t+"￿"]),f=d.openCursor(l),p=0;f.onsuccess=m=>{let y=m.target.result;if(!y||c.length>=o)return;let g=y.value;(!a||g.group===a)&&(p<n?p++:c.push(g)),y.continue()},u.oncomplete=()=>{try{s.close()}catch{}i(c)},u.onerror=()=>{try{s.close()}catch{}i(c)}}catch{try{s.close()}catch{}i([])}}):[]}async function fl(e,t,n,o,a=240){let s=await Gn();return!s||!n?[]:new Promise(i=>{let c=[],u=n.toLocaleLowerCase("pt-BR");try{let d=s.transaction(Be,"readonly"),l=d.objectStore(Be).index("serverType"),f=IDBKeyRange.bound([e,t],[e,t+"￿"]),p=l.openCursor(f);p.onsuccess=m=>{let y=m.target.result;if(!y||c.length>=a)return;let g=y.value;(!o||g.group===o)&&String(g.normalizedName||"").includes(u)&&c.push(g),y.continue()},d.oncomplete=()=>{try{s.close()}catch{}i(c)},d.onerror=()=>{try{s.close()}catch{}i(c)}}catch{try{s.close()}catch{}i([])}})}var vr=0,Zr="";async function es(e){let t=S;if(!["LIVE","MOVIE","SERIES"].includes(t)||!e)return;let n=++vr,o=L[N];if(!o)return;let a=Fe==="ALL"?"":Fe,s=await fl(o.id,t,e,a,240);n!==vr||(r("inputSearch")&&r("inputSearch").value||"").toLowerCase().trim()!==e||(D=s,nt=240,r("badgeItemsCount").textContent=D.length,Q())}async function pl(e,t,n=1e3){let o=await Gn();return!o||!t||!t.size?[]:new Promise(a=>{let s=[];try{let i=o.transaction(Be,"readonly"),u=i.objectStore(Be).index("serverType").openCursor(IDBKeyRange.bound([e,"LIVE"],[e,"SERIES￿"]));u.onsuccess=d=>{let l=d.target.result;if(!l||s.length>=n)return;let f=l.value;t.has(f.url)&&s.push(f),l.continue()},i.oncomplete=()=>{try{o.close()}catch{}a(s)},i.onerror=()=>{try{o.close()}catch{}a(s)}}catch{try{o.close()}catch{}a([])}})}var aa=[],yr=0;async function ml(){let e=L[N];if(!e||S!=="FAVORITES")return;let t=++yr,n=await pl(e.id,G,1e3);t!==yr||S!=="FAVORITES"||(aa=n,te(),ae(),Oe("ALL"))}var jo=!1,Vt={};async function br(){if(jo)return;let e=S;if(!["LIVE","MOVIE","SERIES"].includes(e)){nt+=60,Q();return}let t=be[Fe]||[];if(nt<t.length){nt+=60,Q();return}let n=L[N];if(n){jo=!0;try{let o=C[e]||[],a=Fe==="ALL"?"":Fe,s=e+"|"+(a||"ALL"),i=a?o.filter(d=>(d.group||"Geral")===a).length:o.length,c=Number.isFinite(Vt[s])?Vt[s]:i,u=await ul(n.id,e,c,240,a);if(Vt[s]=c+u.length,u.length){let d=new Set(o.map(l=>l.url));u.forEach(l=>{d.has(l.url)||o.push(l)}),nt+=60,te(),ae(),Q()}else E("Fim do catálogo nesta categoria")}finally{jo=!1}}}function un(e){return e&&{name:e.name,group:e.group,url:e.url,contentType:e.contentType,logo:e.logo?String(e.logo).slice(0,200):"",tvgId:e.tvgId||"",tvgName:e.tvgName||"",rating:e.rating||"",isAdult:!!e.isAdult}}var N=0,ve=0,ye=null,et=null,We=null,yo=!1,Bt=null,On=!1,S="LIVE",C={LIVE:[],MOVIE:[],SERIES:[]},G=new Set,ts="mk21_bg_sync_frequency",ns="mk21_sync_all_lists",os="mk21_hide_bg_progress",as="mk21_fg_live",rs="mk21_fg_movies",ss="mk21_fg_series",is="mk21_srv_metrics_",fo=Object.create(null);function jt(){try{return Ae.normalizeBgSyncFrequency(localStorage.getItem(ts)||"daily")}catch{return"daily"}}function gl(e){let t=Ae.normalizeBgSyncFrequency(e);try{localStorage.setItem(ts,t)}catch{}return No(),typeof lt=="function"&&lt(),t}function qn(){try{let e=localStorage.getItem(ns);return e===null?!0:e!=="false"}catch{return!0}}function hl(e){let t=!!e;try{localStorage.setItem(ns,t?"true":"false")}catch{}return typeof lt=="function"&&lt(),t}function st(){try{return localStorage.getItem(os)==="true"}catch{return!1}}function vl(e){let t=!!e;try{localStorage.setItem(os,t?"true":"false")}catch{}return typeof lt=="function"&&lt(),t}function ha(){try{let e=localStorage.getItem(as)!=="false",t=localStorage.getItem(rs)==="true",n=localStorage.getItem(ss)==="true";return!e&&!t&&!n&&(e=!0),{live:e,movies:t,series:n}}catch{return{live:!0,movies:!1,series:!1}}}function yl(e){let t=!!(e&&e.live),n=!!(e&&e.movies),o=!!(e&&e.series);!t&&!n&&!o&&(t=!0);try{localStorage.setItem(as,t?"true":"false"),localStorage.setItem(rs,n?"true":"false"),localStorage.setItem(ss,o?"true":"false")}catch{}return typeof lt=="function"&&lt(),{live:t,movies:n,series:o}}function va(e){let t=e||ha(),n=[];return t.live&&n.push("Canais"),t.movies&&n.push("Filmes"),t.series&&n.push("Séries"),n.length===3?"Todos no 1º Plano":n.length===0?"1º Plano: Canais":"1º Plano: "+n.join(", ")}function ya(e){if(!e)return null;if(fo[e])return fo[e];try{let t=localStorage.getItem(is+e);if(!t)return null;let n=JSON.parse(t);if(n&&typeof n=="object")return fo[e]=n,n}catch{}return null}function bl(e,t){if(!e||!t)return null;let n={totalChannels:Number(t.totalChannels)||0,totalVOD:Number(t.totalVOD)||0,totalSeries:Number(t.totalSeries)||0,totalGroups:Number(t.totalGroups)||0,totalEPG:Number(t.totalEPG)||0,totalItems:Number(t.totalItems)||0,updatedAt:Number(t.updatedAt)||Date.now()};fo[e]=n;try{localStorage.setItem(is+e,JSON.stringify(n))}catch{}return n}function it(e,t,n,o){if(!e||!t)return null;let a=Ae.computeCatalogMetrics(t,n||null,!!o);a.updatedAt=Date.now();let s=bl(e,a);return typeof xo=="function"&&xo(e,s),s}function ba(){let e=typeof epgCache!="undefined"&&epgCache?epgCache:null;return Ae.computeCatalogMetrics(C,e,!!window.serverXmltvUrl)}function Kt(e={}){let t=!!(e&&e.preserveCatalog);if(We&&(clearTimeout(We),We=null),ye){try{ye.abort()}catch{}ye=null}if(et){try{et.abort()}catch{}et=null}ve++,ht=!1,typeof Ut!="undefined"&&(Ut=0),typeof Vt!="undefined"&&(Vt={}),t||(C={LIVE:[],MOVIE:[],SERIES:[]},typeof be!="undefined"&&(be={ALL:[]}),typeof ot!="undefined"&&(ot=["ALL"]),typeof D!="undefined"&&(D=[])),typeof ra=="function"&&ra()}function Sl(e){if(!(!e||!e.url))try{localStorage.setItem("mk21_last_channel",JSON.stringify({url:e.url,name:e.name,group:e.group||"",contentType:e.contentType||S,serverId:(L[N]||{}).id||""}))}catch{}}function El(){try{let e=localStorage.getItem("mk21_last_channel");return e?JSON.parse(e):null}catch{return null}}function ls(){let e=El(),t=C&&C.LIVE?C.LIVE:[],n=null;if(e&&e.url&&(n=t.find(o=>o.url===e.url||e.name&&o.name===e.name)||e),!n&&t.length>0&&(n=t[0]),n){S="LIVE";try{["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabGuide","tabSettings"].forEach(o=>{r(o)&&r(o).classList.remove("active")}),r("tabLive")&&r("tabLive").classList.add("active")}catch{}return fe(n),E("▶ Sintonizando: "+n.name),!0}return!1}function xl(){try{localStorage.setItem("mk21_sort_order",He)}catch{}}function Cl(){try{let e=localStorage.getItem("mk21_sort_order");e&&["DEFAULT","RECENT","AZ","ZA","YEAR"].indexOf(e)!==-1&&(He=e,typeof bo=="function"&&bo())}catch{}}var Sr=null,Er=!1,Mn=null;function Ll(){let e=r("inputSearch");e&&(document.activeElement&&document.activeElement!==e&&(Mn=document.activeElement),T="search")}function Dt(e=!1){let t=r("inputSearch");if(e&&t&&(t.value=""),e&&(Zr="",te(),ae(),Q()),t)try{t.blur()}catch{}T="channels";let n=Mn&&De(Mn)?Mn:r("listItems")&&r("listItems").querySelector(".list-item-btn:not([disabled])")||r("listCategories")&&r("listCategories").querySelector(".cat-item-btn.active")||r("tabLive");Mn=null,n?Se(n):Le();let o=r("tvPlayer");if(o&&Er){Er=!1;try{o.play().catch(()=>{})}catch{}}}function wl(){clearTimeout(Sr),Sr=setTimeout(function(){let e=r("inputSearch"),t=e?e.value.toLowerCase().trim():"";Zr=t,Q(),t?es(t):(te(),ae(),Q())},180)}var xr=null;function ie(){ue()&&(Eo(!0),clearTimeout(xr),xr=setTimeout(function(){ue()&&(typeof yn=="function"&&yn()||Eo(!1))},5500))}var K=[],Ke=!1,Qe="0000",Ue="",v=null,k=null,He="DEFAULT",Gt=1,Ko=[.5,1,1.5,2,4],be={},ot=[],Fe="ALL",D=[],Pt=[],T="channels",Sa=0,cs=0,ds=0,kl=0,Tl=0,Al=0,Il=null,fn=["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabGuide","tabSettings","btnHeaderServer","btnHeaderLogs","btnRefreshList","btnHeaderLogout"];function us(){let e=new Date,t=String(e.getHours()).padStart(2,"0"),n=String(e.getMinutes()).padStart(2,"0"),o=String(e.getSeconds()).padStart(2,"0");r("liveClock")&&(r("liveClock").textContent=`${t}:${n}:${o}`)}setInterval(us,1e3);us();try{let e=localStorage.getItem("mk21_favs");e&&(G=new Set(JSON.parse(e)))}catch{}function Io(e){if(e){G.has(e)?(G.delete(e),typeof Et=="function"&&Et(!1),E("Removido dos Favoritos")):(G.add(e),typeof Et=="function"&&Et(!0),E("⭐ Adicionado aos Favoritos"));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(G)))}catch{}try{if(v&&v.url===e&&r("epgTitle")){let t=G.has(e)?"⭐ ":"▶ ";r("epgTitle").textContent=t+v.name}if(r("fsHudTitle")&&v){let t=G.has(v.url)?"⭐ ":"";r("fsHudTitle").textContent=t+v.name}}catch{}Q()}}try{let e=localStorage.getItem("mk21_continue_watching");e&&(K=JSON.parse(e))}catch{}function Ml(e){if(!(!e||!e.url)&&e.contentType!=="LIVE"){K=K.filter(t=>t.url!==e.url),K.unshift({name:e.name,group:e.group,logo:e.logo,url:e.url,contentType:e.contentType,timestamp:Date.now()}),K.length>50&&(K=K.slice(0,50));try{localStorage.setItem("mk21_continue_watching",JSON.stringify(K))}catch{}}}function re(e){if(!e)return!1;let t=e.toUpperCase();for(let n=0;n<gr.length;n++)if(t.includes(gr[n]))return!0;return!1}function Cr(e,t,n){let o=(e||"").toUpperCase().trim(),a=(t||"").toUpperCase().trim(),s=(n||"").toUpperCase().trim();if(s.includes("/SERIES/"))return"SERIES";if(s.includes("/MOVIE/"))return"MOVIE";if(s.includes("/LIVE/"))return"LIVE";let i=/(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i.test(o)||o.includes("TEMPORADA")||o.includes("TEMP.")||o.includes("CAPITULO")||o.includes("CAPÍTULO")||o.includes("EPISODIO")||o.includes("EPISÓDIO");if(a.startsWith("SERIES")||a.startsWith("SÉRIES")||a.startsWith("SERIE")||a.startsWith("SÉRIE")||a.startsWith("NOVELAS")||a.startsWith("NOVELA")||a.startsWith("ANIMES")||a.startsWith("ANIME")||a.startsWith("DORAMAS")||a.startsWith("DORAMA")||a.startsWith("MINISSÉRIES")||a.startsWith("MINISSERIES")||a.includes("SERIES |")||a.includes("SÉRIES |")||a.includes("SERIES:")||a.includes("SÉRIES:")||a.includes("SERIADOS")||i)return"SERIES";let u=/\[ALT\]|\(ALT\)|\[BACKUP\]|\(BACKUP\)|\[OFF\]|\bFHD\b|\bUHD\b|\b4K\b|\bHD\b|\bSD\b|\bHEVC\b|\bH\.?265\b|\b1080P\b|\b720P\b|\b480P\b/i.test(o),l=["A&E","AMC","ANIMAL PLANET","DISCOVERY","HISTORY","NAT GEO","NATIONAL GEOGRAPHIC","TELECINE","MEGAPIX","WARNER","UNIVERSAL","STUDIO UNIVERSAL","TNT","SPACE","AXN","PARAMOUNT","SONY","STAR CHANNEL","FX","HBO","CINEMAX","LIFETIME","TCM","CANAL BRASIL","GLOBO","SBT","RECORD","BAND","REDE TV","CULTURA","PREMIERE","SPORTV","ESPN","FOX SPORTS","COMBATE","BANDSPORTS","CONMEBOL","VIVA","MULTISHOW","GNT","OFF","BIS","GLOOB","CARTOON","NICKELODEON","NICK JR","BOOMERANG","TOONCAST","CNN","GLOBO NEWS","BANDNEWS","RECORD NEWS","JOVEM PAN","TV CULTURA","TV BRASIL"].some(b=>o.startsWith(b+" ")||o.startsWith(b+"-")||o.startsWith(b+":")||o===b||o.includes(" "+b+" ")||o.includes("["+b+"]")||o.includes("("+b+")")),f=s.endsWith(".TS")||s.includes(".TS?")||s.endsWith(".M3U8")||s.includes(".M3U8?")||s.includes("/LIVE/"),p=s.endsWith(".MP4")||s.includes(".MP4?")||s.endsWith(".MKV")||s.includes(".MKV?")||s.endsWith(".AVI")||s.includes(".AVI?");if((u||l)&&!p)return"LIVE";if(p||(/\b\((?:19|20)\d{2}\)\b/.test(o)||/\b\[(?:19|20)\d{2}\]\b/.test(o))&&!u&&!l)return"MOVIE";let y=a.startsWith("VOD")||a.startsWith("ON DEMAND")||a.startsWith("ONDEMAND")||a.startsWith("LANÇAMENTOS")||a.startsWith("LANCAMENTOS")||a.startsWith("FILMES 202")||a.startsWith("FILMES 19")||a.includes("VOD |")||a.includes("VOD:")||a.includes("CINEMA |")||a.includes("4K FILMES")||a.includes("FHD FILMES"),g=a.includes("CANAIS")||a.includes("CANAL")||a.includes("AO VIVO")||a.includes("AOVIVO")||a.includes("24H")||a.includes("24 HORAS")||a.includes("FILMES E SERIES")||a.includes("FILMES E SÉRIES")||a.includes("DOCUMENTÁRIOS")||a.includes("DOCUMENTARIOS")||a.includes("TV ABERTA")||a.includes("ABERTOS")||a.includes("ESPORTES")||a.includes("SPORTS")||a.includes("NOTICIAS")||a.includes("NEWS");return y&&!g&&!f||(a.includes("NETFLIX")||a.includes("AMAZON")||a.includes("PRIME VIDEO")||a.includes("HBO MAX")||a.includes("DISNEY+")||a.includes("APPLE TV")||a.includes("PARAMOUNT+")||a.includes("GLOBOPLAY"))&&!g&&!u&&!f?"MOVIE":"LIVE"}var Pl=/(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i;function Rl(e){let t={};return e.forEach(n=>{let o=n.name,a=n.name.match(Pl);if(a&&a.index>0)o=n.name.substring(0,a.index).trim();else{let s=n.name.match(/(?:\s*[-–:]?\s*\b(?:ep|e|cap)\.?\s*\d+\b.*)/i);s&&s.index>0&&(o=n.name.substring(0,s.index).trim())}o=o.replace(/[-_:,/ ]+$/,"").trim()||n.name,t[o]||(t[o]={title:o,group:n.group,logo:n.logo,episodes:[]}),t[o].episodes.push(n)}),Object.values(t).sort((n,o)=>n.title.localeCompare(o.title))}async function Nl(e,t,n){let o=0,a=g=>{o++,t(g)};if(!e.body||typeof e.body.getReader!="function"||typeof TextDecoder=="undefined"){let g=await e.text();if(!g||g.length<30)throw new Error("Lista M3U vazia");let h=g.trim().slice(0,250).toLowerCase();if(h.includes("<!doctype html")||h.includes("<html"))throw new Error("Servidor retornou página HTML em vez de M3U");if(h.includes("invalid username")||h.includes("invalid password")){let w=new Error("Usuário ou senha inválidos no servidor");throw w.isAuthError=!0,w}let b=g.split(/\r?\n/),R=null;for(let w of b){let M=w.trim();if(M.startsWith("#EXTM3U")){let A=M.match(/(?:url-tvg|x-tvg-url)="([^"]*)"/i);A&&A[1]&&(window.serverXmltvUrl=A[1].trim())}else if(M.startsWith("#EXTINF:")){let A=M.lastIndexOf(","),O=M.match(/group-title="([^"]*)"/i),_=M.match(/tvg-logo="([^"]*)"/i),I=M.match(/tvg-id="([^"]*)"/i),B=M.match(/tvg-name="([^"]*)"/i);R={name:A>=0?M.slice(A+1).trim():"Item",group:O&&O[1]?O[1].trim():"Geral",logo:_&&_[1]?_[1].trim():"",tvgId:I&&I[1]?I[1].trim():"",tvgName:B&&B[1]?B[1].trim():""}}else if(R&&/^https?:\/\//i.test(M)){let A=Cr(R.name,R.group,M);a({...R,url:M,contentType:A,isAdult:re(R.name)||re(R.group)}),R=null}}if(o===0)throw new Error("Lista M3U sem canais válidos");return o}let s=e.body.getReader(),i=new TextDecoder("utf-8"),c="",u="",d=!1,l=null,f=0,p=parseInt(e.headers&&e.headers.get&&e.headers.get("content-length")||"0",10)||0,m=(g,h)=>{let b=h+'="',R=g.indexOf(b);if(R===-1)return"";let w=R+b.length,M=g.indexOf('"',w);return M!==-1?g.slice(w,M).trim():""},y=g=>{let h=g.trim();if(h){if(h.charCodeAt(0)===35){if(h.startsWith("#EXTINF:")){let b=h.lastIndexOf(",");l={name:b>=0?h.slice(b+1).trim():"Item",group:m(h,"group-title")||"Geral",logo:m(h,"tvg-logo"),tvgId:m(h,"tvg-id"),tvgName:m(h,"tvg-name")}}else if(h.startsWith("#EXTM3U")){let b=m(h,"url-tvg")||m(h,"x-tvg-url");b&&(window.serverXmltvUrl=b)}}else if(l&&(h.startsWith("http://")||h.startsWith("https://"))){let b=Cr(l.name,l.group,h);a({...l,url:h,contentType:b,isAdult:re(l.name)||re(l.group)}),l=null}}};for(;;){let g=await s.read();if(g.done)break;f+=g.value.byteLength||g.value.length||0;let h=i.decode(g.value,{stream:!0});if(!d&&(u+=h.slice(0,320),u.length>=60)){d=!0;let R=u.trim().toLowerCase();if(R.includes("<!doctype html")||R.includes("<html")){try{s.cancel()}catch{}throw new Error("Servidor retornou página HTML em vez de M3U")}if(R.includes("invalid username")||R.includes("invalid password")){try{s.cancel()}catch{}let w=new Error("Usuário ou senha inválidos no servidor");throw w.isAuthError=!0,w}}c+=h;let b=c.split(/\r?\n/);c=b.pop()||"";for(let R of b)y(R);n&&n(f,p)}if(c+=i.decode(),c&&y(c),o===0)throw new Error("Lista M3U vazia (0 itens)");return o}async function _l(e,t,n,o,a,s){let i=e.replace(/\/+$/,""),c=encodeURIComponent(t),u=encodeURIComponent(n),d=`${i}/player_api.php?username=${c}&password=${u}`,l=async(w,M=2e4)=>{let A=typeof AbortController!="undefined"?new AbortController:null,O=A?setTimeout(()=>A.abort(),M):null,_=()=>{A&&A.abort()};o&&o.addEventListener&&o.addEventListener("abort",_,{once:!0});try{let I=await fetch(w,A?{signal:A.signal,mode:"cors"}:{mode:"cors"}),B=await I.text();if(!I.ok&&I.status!==401&&I.status!==403)throw new Error("HTTP "+I.status);return Ae.parseSafeJsonResponse(B,I.status)}finally{O&&clearTimeout(O),o&&o.removeEventListener&&o.removeEventListener("abort",_)}};x("[Rede] Tentando API Xtream em "+i+"/player_api.php","info");let f=await l(d,1e4);if(f&&f.user_info&&Number(f.user_info.auth)===0){let w=new Error("Usuário ou senha inválidos/expirados neste servidor.");throw w.isAuthError=!0,w}let p=0,m=localStorage.getItem("mk21_stream_format")==="hls"?"m3u8":"ts",[y,g]=await Promise.all([l(d+"&action=get_live_categories",2e4).catch(()=>[]),l(d+"&action=get_live_streams",3e4)]),h=Object.create(null);if(Array.isArray(y)&&y.forEach(w=>{w&&w.category_id!=null&&(h[String(w.category_id)]=w.category_name||"Ao Vivo")}),Array.isArray(g)){for(let w=0;w<g.length;w++){let M=g[w];if(!M||!M.stream_id)continue;let A=h[String(M.category_id)]||M.category_name||"Geral",O=M.name||"Canal "+M.stream_id;a({name:O,group:A,logo:M.stream_icon||"",tvgId:M.epg_channel_id||"",tvgName:O,url:`${i}/live/${c}/${u}/${M.stream_id}.${m}`,contentType:"LIVE",isAdult:re(O)||re(A)||Number(M.is_adult)===1}),p++}s&&s(40,100)}if(localStorage.getItem("mk21_filter_load_movies")!=="false")try{let[w,M]=await Promise.all([l(d+"&action=get_vod_categories",15e3).catch(()=>[]),l(d+"&action=get_vod_streams",35e3).catch(()=>[])]),A=Object.create(null);if(Array.isArray(w)&&w.forEach(O=>{O&&O.category_id!=null&&(A[String(O.category_id)]=O.category_name||"Filmes")}),Array.isArray(M))for(let O=0;O<M.length;O++){let _=M[O];if(!_||!_.stream_id)continue;let I=A[String(_.category_id)]||_.category_name||"Filmes",B=_.container_extension||"mp4",V=_.name||"Filme "+_.stream_id;a({name:V,group:I,logo:_.stream_icon||"",tvgId:"",tvgName:V,rating:_.rating_5based||_.rating||"",url:`${i}/movie/${c}/${u}/${_.stream_id}.${B}`,contentType:"MOVIE",isAdult:re(V)||re(I)||Number(_.is_adult)===1}),p++}s&&s(75,100)}catch{}if(localStorage.getItem("mk21_filter_load_series")!=="false")try{let[w,M]=await Promise.all([l(d+"&action=get_series_categories",15e3).catch(()=>[]),l(d+"&action=get_series",35e3).catch(()=>[])]),A=Object.create(null);if(Array.isArray(w)&&w.forEach(O=>{O&&O.category_id!=null&&(A[String(O.category_id)]=O.category_name||"Séries")}),Array.isArray(M))for(let O=0;O<M.length;O++){let _=M[O];if(!_||!_.series_id)continue;let I=A[String(_.category_id)]||_.category_name||"Séries",B=_.name||"Série "+_.series_id;a({name:B,group:I,logo:_.cover||"",tvgId:"",tvgName:B,rating:_.rating_5based||_.rating||"",url:`${i}/series/${c}/${u}/${_.series_id}.xtream`,contentType:"SERIES",isAdult:re(B)||re(I)}),p++}s&&s(95,100)}catch{}if(p===0)throw new Error("API Xtream respondeu vazia (0 itens)");return x("[Rede] Catálogo carregado via player_api.php ("+p+" itens)","success"),p}async function fs(e,t,n,o){let a=String(e||"").trim(),s=Wt()||{user:"",pass:""},i=s.user,c=s.pass,u=a.includes("get.php")||a.includes(".m3u")||a.includes(".ts")||a.includes(".m3u8");if(!u&&(!i||!c))throw new Error("Credenciais ausentes. Faça login novamente.");let d=localStorage.getItem("mk21_stream_format")==="hls"?"hls":"ts",l=a.replace(/\/+$/,""),f=encodeURIComponent(i),p=encodeURIComponent(c),m=u?[a]:[`${l}/get.php?username=${f}&password=${p}&type=m3u_plus&output=${d}`,`${l}/get.php?username=${f}&password=${p}&type=m3u_plus&output=mpegts`,`${l}/get.php?username=${f}&password=${p}&type=m3uplus&output=ts`,`${l}/get.php?username=${f}&password=${p}`],y=null;for(let g=0;g<m.length;g++){if(t&&t.aborted){let A=new Error("Aborted");throw A.name="AbortError",A}let h=m[g],b=Cs(h);console.log("[MK21] Streaming da lista:",b),x("[Rede] Tentando lista: "+b,"debug");let R=typeof AbortController!="undefined"?new AbortController:null,w=R?setTimeout(()=>R.abort(),45e3):null,M=()=>{R&&R.abort()};t&&t.addEventListener&&t.addEventListener("abort",M,{once:!0});try{let A=R?{signal:R.signal,mode:"cors"}:{mode:"cors"},O=await fetch(h,A);if(O.status===401||O.status===403){let I=new Error("Credenciais inválidas ou bloqueadas pelo servidor (HTTP "+O.status+").");throw I.isAuthError=!0,I.status=O.status,x("[Rede] "+b+" -> HTTP "+O.status+" (credenciais)","error"),I}if(!O.ok){let I=new Error("HTTP "+O.status);x("[Rede] "+b+" -> HTTP "+O.status,"warn"),y=I;continue}let _=await Nl(O,n,o);x("[Rede] Lista M3U recebida ("+_+" itens)","success");return}catch(A){if(A&&A.name==="AbortError"&&t&&t.aborted||A&&A.isAuthError)throw A;y=A,x("[Rede] Falha em "+b+": "+(A&&A.message?A.message:A),"warn")}finally{w&&clearTimeout(w),t&&t.removeEventListener&&t.removeEventListener("abort",M)}}if(!u)try{await _l(l,i,c,t,n,o);return}catch(g){if(g&&g.name==="AbortError"&&t&&t.aborted)throw g;y=g,x("[Rede] Fallback player_api.php falhou: "+(g&&g.message?g.message:g),"warn")}throw y||new Error("Não foi possível baixar a lista do servidor")}function mt(e,t=!1,n={}){let o=r("txtServerSyncBadge"),a=r("txtActiveServer"),s=L[N],i=s?s.name:"Servidor";if(!!(n&&n.background)&&st()){o&&o.classList.add("hidden"),a&&(a.textContent=Z(i));return}if(t)o&&(o.textContent="✓ 100%",o.classList.remove("hidden"),o.classList.add("done"),setTimeout(()=>{o&&o.classList.add("hidden")},4e3)),a&&(a.textContent=Z(i));else{let d=`${e}%`;o&&(o.textContent=d,o.classList.remove("hidden"),o.classList.remove("done")),a&&(a.textContent=`${Z(i)} (${d})`)}}function ra(){let e=r("hudLoadingOverlay"),t=e&&e.querySelector(".hud-loading-card"),n=r("hudLoadingError"),o=r("btnRetryLoading");t&&t.classList.remove("is-error"),n&&(n.textContent="",n.classList.add("hidden")),o&&(o.classList.add("hidden"),o.onclick=()=>{Kt({preserveCatalog:!0}),Ee(!0)});let a=e&&e.querySelector(".spinner-ring");a&&(a.style.display="")}function Ol(e,t){let n=r("hudLoadingOverlay"),o=n&&n.querySelector(".hud-loading-card"),a=r("hudLoadingError"),s=r("btnRetryLoading"),i=r("hudLoadingActions");if(n){if(o&&o.classList.add("is-error"),r("hudLoadingTitle")&&(r("hudLoadingTitle").textContent=e&&e.isAuthError?"Falha na autenticação do servidor":"Não foi possível carregar a lista"),r("hudLoadingSub")&&(r("hudLoadingSub").textContent=`Servidor: ${t||"atual"}`),r("hudProgressBar")&&(r("hudProgressBar").style.width="0%"),r("hudProgressPercent")&&(r("hudProgressPercent").textContent="Falha"),a){let c=Ae.formatFriendlyLoadError(e);a.textContent=`${c} Seus dados não foram apagados.`,a.classList.remove("hidden")}if(i&&!r("btnHudRelogin")){let c=document.createElement("button");c.id="btnHudRelogin",c.type="button",c.className="ctrl-btn",c.style.cssText="padding: 10px 18px; font-size: 15px; border-color: #ffd54f; color: #ffd54f;",c.tabIndex=0,c.textContent="🔐 Trocar Login",i.appendChild(c)}r("btnHudRelogin")&&(r("btnHudRelogin").onclick=()=>bn()),s&&(s.classList.remove("hidden"),s.onclick=()=>{Kt({preserveCatalog:!0}),Ee(!0)},setTimeout(()=>s.focus(),0)),n.classList.remove("hidden")}}var ht=!1;async function Bl(e){let t=await Gn();if(!t)return null;try{return await new Promise(n=>{let a=t.transaction(vn,"readonly").objectStore(vn).get(e);a.onsuccess=()=>n(a.result||null),a.onerror=()=>n(null)})}catch{return null}finally{try{t.close()}catch{}}}async function Ee(e=!1,t={}){let n=!!(t&&t.silent),o=!!(t&&t.backgroundLowPriority);if(n&&ht)return;We&&(clearTimeout(We),We=null),(!t||!t.fromAutoAdvance)&&(Ut=0);let a=++ve;if(ye)try{ye.abort()}catch{}ye=typeof AbortController!="undefined"?new AbortController:null,L.length===0&&(L=[...rt]),N>=L.length&&(N=0);let s=L[N];r("txtActiveServer")&&(r("txtActiveServer").textContent=Z(s.name)),n||mt(15,!1);let i=r("hudLoadingOverlay");if(!n&&!e){let d=await hn(s.id);if((!d||!d.LIVE||d.LIVE.length===0)&&(d=dn(s.id)),a!==ve)return;let l=d&&d.LIVE?d.LIVE.length:0,f=d&&d.MOVIE?d.MOVIE.length:0,p=d&&d.SERIES?d.SERIES.length:0;if(d&&l+f+p>0){C={LIVE:d.LIVE||[],MOVIE:d.MOVIE||[],SERIES:d.SERIES||[]},i&&i.classList.add("hidden"),ze(),te(),ae(),Oe("ALL"),Q(),mt(100,!0),r("txtCurrentCategoryTitle")&&(r("txtCurrentCategoryTitle").textContent="📺 Memória: "+l.toLocaleString("pt-BR")+" canais"),E("✓ Abrindo da memória local ("+l+" canais). Sincronizando o resto..."),setTimeout(()=>{ls()},150);let m=0;try{m=parseInt(localStorage.getItem("mk21_last_sync_"+s.id)||"0",10)||0}catch{}let y=m>0&&Date.now()-m<1200*1e3,g=f+p===0;!y||g?setTimeout(()=>{ve===a&&Ee(!0,{silent:!0})},700):mt(100,!0);return}}if(!n){let d=r("tvPlayer");if(d)try{d.pause(),d.src=""}catch{}if(k)try{k.destroy(),k=null}catch{}if(W){try{W.pause(),W.unload(),W.detachMediaElement(),W.destroy()}catch{}W=null}v=null,on(25,"Conectando ao "+s.name+"..."),ra(),i&&(r("hudLoadingTitle")&&(r("hudLoadingTitle").textContent="Conectando ao "+s.name),r("hudLoadingSub")&&(r("hudLoadingSub").textContent="Baixando grade de programação ao vivo..."),r("hudProgressBar")&&(r("hudProgressBar").style.width="20%"),r("hudProgressPercent")&&(r("hudProgressPercent").textContent="20%"),i.classList.remove("hidden")),r("btnHudChangeServer")&&(r("btnHudChangeServer").onclick=()=>{if(ye)try{ye.abort()}catch{}i&&i.classList.add("hidden"),ze(),ct()}),r("btnHudCancelLoading")&&(r("btnHudCancelLoading").onclick=()=>{if(ye)try{ye.abort()}catch{}i&&i.classList.add("hidden"),ze(),C.LIVE&&C.LIVE.length>0&&(te(),ae(),Oe("ALL"),Q())}),r("txtCurrentCategoryTitle").textContent="Conectando ao "+s.name+"...",on(35,"Baixando grade de canais ao vivo...")}let c=n?null:setTimeout(()=>{if(a===ve&&i&&!i.classList.contains("hidden")&&(!C.LIVE||C.LIVE.length===0)){r("hudLoadingSub")&&(r("hudLoadingSub").textContent="⚠️ Servidor "+s.name+" demorando a responder. Deseja trocar a fonte?");let d=r("hudLoadingActions");d&&(d.style.display="flex");let l=r("btnHudChangeServer");l&&l.focus()}},7e3);ht=!0;let u=0;try{let d={LIVE:[],MOVIE:[],SERIES:[]},l={LIVE:[],MOVIE:[],SERIES:[]},f=ha(),p=I=>I==="LIVE"?f.live:I==="MOVIE"?f.movies:I==="SERIES"?f.series:!0;n||(C=d),Vt={};let m;if(n){let I=await Bl(s.id);m=!I||!I.updatedAt||Date.now()-I.updatedAt>720*60*1e3?await oa(s.id):{add(){},async finish(){}}}else m=await oa(s.id);let y=window.MK21AlternativeSources?MK21AlternativeSources.createGrouper():null,g=n,h=0,b=0,R=I=>{if(!n||a!==ve||S==="SETTINGS")return;let B=r("inputSearch");if(B&&document.activeElement===B)return;let V=r("listItems");if(V&&document.activeElement&&V.contains(document.activeElement))return;let Re=Date.now();!I&&Re-b<3e3||(b=Re,C=d,te(),ae(),be[Fe]||(Fe="ALL"),Q(!0))},w=()=>{g||a!==ve||!(d.LIVE.length>0||d.MOVIE.length>0||d.SERIES.length>0)||(g=!0,c&&(clearTimeout(c),c=null),C=d,C.LIVE.length>0&&_n(s.id,C.LIVE,!0),on(80,"Conteúdo inicial liberado ("+(C.LIVE.length+C.MOVIE.length+C.SERIES.length)+")"),C.LIVE.length===0&&S==="LIVE"&&(C.MOVIE.length>0?S="MOVIE":C.SERIES.length>0&&(S="SERIES")),te(),ae(),Oe("ALL"),Q(),!v&&C.LIVE.length>0&&fe(C.LIVE[0]),ze(),i&&i.classList.add("hidden"),st()||E("📺 1º Plano liberado! Demais etapas carregando em segundo plano..."))},M=async()=>{let I=[].concat(l.LIVE).concat(l.MOVIE).concat(l.SERIES);if(I.length===0)return;let B=450;for(let V=0;V<I.length;V+=B){if(a!==ve)return;let Re=Math.min(I.length,V+B);for(let we=V;we<Re;we++){let me=I[we],ge=me.contentType;if(m.add(me),d[ge])if(ge==="LIVE"&&y){let Xt=y.add(me);Xt.isNew&&d[ge].push(Xt.item)}else d[ge].push(me)}let ee=Math.min(99,Math.round(Re/I.length*100));mt(ee,!1,{background:!0}),await new Promise(we=>setTimeout(we,16))}};if(await fs(s.url,ye&&ye.signal,I=>{if(a!==ve)return;let B=un(I),V=B.contentType,Re=localStorage.getItem("mk21_filter_load_live")!=="false",ee=localStorage.getItem("mk21_filter_load_movies")!=="false",we=localStorage.getItem("mk21_filter_load_series")!=="false";if(V==="LIVE"&&!Re||V==="MOVIE"&&!ee||V==="SERIES"&&!we)return;let me=B.isAdult||re(B.name)||re(B.group);if(B.isAdult=!!me,!n&&!p(V)){l[V]&&l[V].push(B);return}if(m.add(B),d[V])if(V==="LIVE"&&y){let ge=y.add(B);ge.isNew&&d[V].push(ge.item)}else d[V].push(B);if(n)R(!1);else{let ge=d.LIVE.length+d.MOVIE.length+d.SERIES.length;V!=="LIVE"&&d.LIVE.length>0&&!g||ge>=8&&!g?w():!g&&d.LIVE.length>0&&d.LIVE.length%50===0&&_n(s.id,d.LIVE)}},(I,B)=>{if(a!==ve)return;let V=Date.now();if(V-h<250)return;h=V;let Re=d.LIVE.length+d.MOVIE.length+d.SERIES.length+l.LIVE.length+l.MOVIE.length+l.SERIES.length,ee=50;if(B&&B>0)ee=Math.min(99,Math.max(10,Math.round(I/B*100)));else if(!g)ee=Math.min(48,15+Math.round(d.LIVE.length/25*30));else{let we=I/1048576,me=50+Math.round(Math.min(48,we/4.5*26+Re/6e3*22));ee=Math.min(98,Math.max(50,me))}mt(ee,!1,{background:n}),n||(g||on(ee,"Lendo 1º plano — "+d.LIVE.length+" canais ao vivo"),i&&!g&&(r("hudProgressBar")&&(r("hudProgressBar").style.width=ee+"%"),r("hudProgressPercent")&&(r("hudProgressPercent").textContent=ee+"%")))}),a!==ve)return;let A=d.LIVE.length+d.MOVIE.length+d.SERIES.length,O=l.LIVE.length+l.MOVIE.length+l.SERIES.length;if(A+O===0)throw new Error("Lista do servidor retornou vazia (0 itens)");if(C=d,!n&&A>0&&(w(),te(),ae(),g||Oe("ALL"),i&&(r("hudProgressBar")&&(r("hudProgressBar").style.width="100%"),r("hudProgressPercent")&&(r("hudProgressPercent").textContent="100%"),i.classList.add("hidden"))),O>0&&(await M(),a!==ve))return;ht=!1,C=d,n||w(),R(!0),te(),ae(),!n&&!g&&Oe("ALL");let _=C.LIVE.length+C.MOVIE.length+C.SERIES.length;on(100,"Lista completa carregada ("+_.toLocaleString("pt-BR")+" itens)"),i&&(r("hudProgressBar")&&(r("hudProgressBar").style.width="100%"),r("hudProgressPercent")&&(r("hudProgressPercent").textContent="100%"),i.classList.add("hidden")),mt(100,!0,{background:n||o}),Ut=0;try{localStorage.setItem("mk21_last_sync_"+s.id,String(Date.now()))}catch{}if(await m.finish(),_n(s.id,C.LIVE,!0),Jr(s.id,C),it(s.id,C,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),(S==="LIVE"||S==="MOVIE"||S==="SERIES"||S==="CONTINUE")&&(te(),ae(),Q()),r("txtCurrentCategoryTitle")){let I=C.LIVE.length;r("txtCurrentCategoryTitle").textContent="📺 Catálogo: "+I.toLocaleString("pt-BR")+" canais • "+C.MOVIE.length.toLocaleString("pt-BR")+" filmes • "+C.SERIES.length.toLocaleString("pt-BR")+" séries"}typeof Ye=="function"&&Ye(),n&&!o&&u!==_&&!st()?(u=_,E("✓ Catálogo sincronizado: "+_.toLocaleString("pt-BR")+" itens")):o&&x("[Sync Segundo Plano] Catálogo atualizado silenciosamente ("+_+" itens).","success"),No()}catch(d){if(a===ve&&(ht=!1),a!==ve||d&&d.name==="AbortError")return;console.error("Server error:",d);let l=C&&Array.isArray(C.LIVE)?C.LIVE.length:0;if(n){if(mt(100,!0),o){x("[Sync Segundo Plano] Falha silenciosa: "+Ae.formatFriendlyLoadError(d),"warn");return}r("txtServerSyncBadge")&&(r("txtServerSyncBadge").textContent="offline",r("txtServerSyncBadge").classList.remove("hidden","done")),E(l>0?"⚠️ Sem conexão — usando o catálogo salvo ("+l+" canais)":"⚠️ Sem conexão com o servidor — tente Atualizar"),i&&i.classList.add("hidden"),ze();return}l>0?(te(),ae(),Oe("ALL"),Q(),ze(),v||fe(C.LIVE[0]),E(`⚠️ Lista parcial: ${l} canais carregados. Tente atualizar novamente.`),r("txtCurrentCategoryTitle")&&(r("txtCurrentCategoryTitle").textContent=`📺 ${l} canais disponíveis (parcial)`),i&&i.classList.add("hidden")):!(d&&d.isAuthError)&&Ic(d,s,a)||(Ol(d,s.name),r("txtCurrentCategoryTitle")&&(r("txtCurrentCategoryTitle").textContent="Aguardando conexão com o servidor"),r("txtServerSyncBadge")&&(r("txtServerSyncBadge").textContent="erro",r("txtServerSyncBadge").classList.remove("hidden","done")))}finally{a===ve&&(ht=!1)}}function te(){be={ALL:[]},ot=["ALL"];let e=[];if(S==="FAVORITES"?e=aa.length?aa:[...C.LIVE,...C.MOVIE,...C.SERIES].filter(a=>G.has(a.url)):S==="CONTINUE"?e=K:e=C[S]||[],(S==="MOVIE"||S==="SERIES")&&K.length>0){let a=K.filter(s=>s.contentType===S);a.length>0&&(be["▶ Continuar Assistindo"]=a,ot.push("▶ Continuar Assistindo"))}let t=[];try{t=JSON.parse(localStorage.getItem("mk21_hidden_categories")||"[]")}catch{}let n=Object.create(null),o=t.length?new Set(t):null;for(let a=0;a<e.length;a++){let s=e[a],i=s.group||"Geral";if(!Ke){let c=n[i];if(typeof c=="undefined"&&(c=n[i]=re(i)),s.isAdult||c)continue}o&&o.has(i)||(be.ALL.push(s),be[i]||(be[i]=[],ot.push(i)),be[i].push(s))}}function ae(){let e=r("listCategories");e.innerHTML="";let t=document.createDocumentFragment();r("badgeCatCount").textContent=ot.length-1+" grupos",r("txtCatHeaderTitle").textContent=S==="LIVE"?"📁 Categorias TV":S==="MOVIE"?"📁 Gêneros Filmes":S==="SERIES"?"🍿 Gêneros Séries":S==="CONTINUE"?"▶ Continuar Assistindo":"⭐ Favoritos",ot.forEach((n,o)=>{if(!Ke&&re(n))return;let a=document.createElement("li"),s=document.createElement("button");s.className="list-item-btn cat-item-btn"+(n===Fe?" active":""),s.setAttribute("tabindex","0"),s.setAttribute("data-idx",o);let i=document.createElement("span");i.textContent=n==="ALL"?S==="LIVE"?"🌟 Todos os Canais":S==="MOVIE"?"🌟 Todos os Filmes":S==="SERIES"?"🌟 Todas as Séries":S==="CONTINUE"?"🌟 Todos em Andamento":"🌟 Todos os Favoritos":n;let c=document.createElement("span");c.className="counter-badge",c.textContent=be[n]?be[n].length:0,s.appendChild(i),s.appendChild(c),s.onclick=()=>Oe(n),a.appendChild(s),t.appendChild(a)}),e.appendChild(t),typeof X=="function"&&(X(e),setTimeout(()=>X(e),60))}function Oe(e){Fe=e;let t=r("listCategories").querySelectorAll(".cat-item-btn");t.forEach(o=>o.classList.remove("active"));let n=Array.from(t).find(o=>e==="ALL"&&o.textContent.includes("Todos")||o.textContent.startsWith(e));n&&n.classList.add("active"),r("txtCurrentCategoryTitle").textContent=e==="ALL"?S==="LIVE"?"📺 Todos os Canais":S==="MOVIE"?"🎬 Todos os Filmes":S==="SERIES"?"🍿 Todas as Séries":S==="CONTINUE"?"▶ Continuar Assistindo":"⭐ Favoritos":"📁 "+e,r("inputSearch").value="",nt=80,Q()}var nt=80;function Q(e){let t=e&&r("listItems")?r("listItems").scrollTop:0,n=r("inputSearch")?r("inputSearch").value.toLowerCase().trim():"",o=be[Fe]||[],a=S+"|"+(Fe==="ALL"?"ALL":Fe),s=Object.prototype.hasOwnProperty.call(Vt,a),i=n?o.filter(f=>f.name&&f.name.toLowerCase().includes(n)||f.normalizedName&&f.normalizedName.includes(n)):o;Ke||(i=i.filter(f=>!f.isAdult));function c(f){let p=String(f||"").match(/\b((?:19|20)\d{2})\b/);return p?parseInt(p[1],10):0}function u(f){let p=parseFloat(f&&(f.rating||f.rating_5based));return Number.isFinite(p)?p:0}He==="AZ"?i=i.slice().sort((f,p)=>f.name.localeCompare(p.name,"pt-BR",{sensitivity:"base"})):He==="ZA"?i=i.slice().sort((f,p)=>p.name.localeCompare(f.name,"pt-BR",{sensitivity:"base"})):He==="RECENT"?i=i.slice().reverse():(He==="RATING"||He==="YEAR")&&(i=i.slice().sort((f,p)=>{let m=u(f),y=u(p);if(m!==y)return y-m;let g=c(f.name),h=c(p.name);return g&&h&&g!==h?h-g:g&&!h?-1:!g&&h?1:f.name.localeCompare(p.name,"pt-BR",{sensitivity:"base"})})),D=i,r("badgeItemsCount").textContent=D.length;let d=r("listItems");if(d.innerHTML="",D.length===0){d.innerHTML='<li><button class="list-item-btn" disabled>Nenhum item encontrado nesta categoria.</button></li>';return}let l=document.createDocumentFragment();if(S==="SERIES"&&n.length===0){Pt=Rl(D),r("badgeItemsCount").textContent=Pt.length+" séries";let f=Math.min(Pt.length,nt);for(let p=0;p<f;p++){let m=Pt[p],y=document.createElement("li"),g=document.createElement("button");g.className="list-item-btn series-card-btn",g.setAttribute("tabindex","0"),g.setAttribute("data-idx",p);let h=document.createElement("span");h.textContent="🍿 "+m.title;let b=document.createElement("span");b.className="counter-badge",b.textContent=m.episodes.length+" eps",g.appendChild(h),g.appendChild(b),g.onclick=()=>sa(m),y.appendChild(g),l.appendChild(y)}if(Pt.length>f||s){let p=document.createElement("li"),m=document.createElement("button");m.className="list-item-btn",m.style.textAlign="center",m.style.color="#ffd54f",m.textContent=`➕ Carregar Mais Séries (+150 de ${Pt.length-f} restantes)...`,m.onclick=()=>{br()},p.appendChild(m),l.appendChild(p)}}else{let f=Math.min(D.length,nt);for(let p=0;p<f;p++){let m=D[p],y=document.createElement("li"),g=document.createElement("button");g.className="list-item-btn channel-item-btn channel-row"+(v&&v.url===m.url?" active active-playing":""),g.setAttribute("tabindex","0"),g.setAttribute("data-idx",p),g.setAttribute("data-id",m.id||m.url);let h=document.createElement("div");h.className="channel-logo-wrap";let b=document.createElement("img");b.className="channel-logo",b.referrerPolicy="no-referrer";let R="data:image/svg+xml;utf8,"+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 52 52"><rect width="52" height="52" rx="8" fill="#1e1f26"/><circle cx="26" cy="26" r="16" fill="#141828"/><polygon points="23,19 33,26 23,33" fill="#ffd54f"/></svg>');b.src=m.logo?jl(m.logo,m.name):R,b.alt=m.name,b.loading="lazy",b.decoding="async",b.onerror=function(){this.onerror=null,this.src=R},h.appendChild(b),g.appendChild(h);let w=document.createElement("div");w.className="channel-details";let M=document.createElement("span");M.className="channel-name",M.textContent=m.name,w.appendChild(M);let A=document.createElement("span");if(A.className="channel-epg-sub",A.textContent=m.current_program||(S==="CONTINUE"?"▶ Continuar":m.group||"Ao Vivo"),w.appendChild(A),g.appendChild(w),m.isAdult){let B=document.createElement("span");B.className="counter-badge",B.textContent="+18",g.appendChild(B)}if(m.progressPct&&m.progressPct>0){let B=document.createElement("div");B.className="continue-progress-track";let V=document.createElement("div");V.className="continue-progress-fill",V.style.width=Math.min(100,m.progressPct)+"%",B.appendChild(V),g.appendChild(B)}let O=G.has(m.url),_=document.createElement("button");_.className="channel-fav-btn"+(O?" active":""),_.textContent=O?"⭐":"☆",_.title=O?"Remover dos favoritos":"Favoritar canal",_.tabIndex=-1,_.onclick=B=>{B.stopPropagation(),Io(m.url);let V=G.has(m.url);_.className="channel-fav-btn"+(V?" active":""),_.textContent=V?"⭐":"☆"},g.appendChild(_),g.onclick=()=>fe(m);let I=0;g.addEventListener("click",()=>{let B=Date.now();B-I<380&&En(!0),I=B}),y.appendChild(g),l.appendChild(y)}if(D.length>f||s){let p=document.createElement("li"),m=document.createElement("button");m.className="list-item-btn",m.style.textAlign="center",m.style.color="#ffd54f",m.textContent=`➕ Carregar Mais Itens (+150 de ${D.length-f} restantes)...`,m.onclick=()=>{br()},p.appendChild(m),l.appendChild(p)}}if(d.appendChild(l),e)try{d.scrollTop=t}catch{}typeof X=="function"&&(X(d),setTimeout(()=>X(d),60)),!v&&S==="LIVE"&&D.length>0&&!n&&fe(D[0])}function bo(){let e=r("btnSortOrder");if(!e)return;let t={DEFAULT:"↕️ Número",RECENT:"🕒 Adição",RATING:"⭐ Qualificação",YEAR:"⭐ Qualificação",AZ:"🔤 A - Z",ZA:"🔤 Z - A"};e.textContent=t[He]||"↕️ Classificação"}function Dl(){let e=r("modalSortPicker");if(!e)return;xe(e,!0),e.classList.remove("hidden"),T="modalSortPicker";let t=He==="YEAR"?"RATING":He,n=e.querySelectorAll(".sort-choice-btn");n.forEach(a=>{let s=a.dataset.order===t;a.classList.toggle("active",s),a.onclick=()=>{He=a.dataset.order,bo(),xl(),Q(),ps();let i=a.querySelector(".apk-radio-label")?a.querySelector(".apk-radio-label").textContent.trim():a.textContent.trim();E("Ordenação: "+i)}});let o=e.querySelector(`.sort-choice-btn[data-order="${t}"]`)||n[0];o&&o.focus()}function ps(){let e=r("modalSortPicker");e&&(e.classList.add("hidden"),xe(e,!1)),T="channels",r("btnSortOrder")&&r("btnSortOrder").focus()}r("btnSortOrder")&&(r("btnSortOrder").onclick=Dl,bo());r("btnCloseSortPicker")&&(r("btnCloseSortPicker").onclick=ps);r("inputSearch")&&(r("inputSearch").addEventListener("click",()=>Ll()),r("inputSearch").addEventListener("focus",()=>{T="search"}),r("inputSearch").addEventListener("input",wl));async function sa(e){if(e&&Array.isArray(e.episodes)&&e.episodes.length===1&&e.episodes[0]&&typeof e.episodes[0].url=="string"&&e.episodes[0].url.endsWith(".xtream")){let i=e.episodes[0].url.match(/^(https?:\/\/.+?)\/series\/([^/]+)\/([^/]+)\/(\d+)\.xtream$/);if(i)try{E("🍿 Carregando episódios...");let c=`${i[1]}/player_api.php?username=${i[2]}&password=${i[3]}&action=get_series_info&series_id=${i[4]}`,u=await fetch(c,{mode:"cors"});if(u.ok){let d=await u.json(),l=d&&d.episodes?d.episodes:{},f=[];Object.keys(l).forEach(p=>{(Array.isArray(l[p])?l[p]:[]).forEach(y=>{if(!y||!y.id)return;let g=y.container_extension||"mp4",h=`S${String(p).padStart(2,"0")}E${String(y.episode_num||1).padStart(2,"0")} - ${y.title||"Episódio "+(y.episode_num||"")}`;f.push({name:h,group:e.group||"Séries",logo:y.info&&y.info.movie_image||e.logo||"",url:`${i[1]}/series/${i[2]}/${i[3]}/${y.id}.${g}`,contentType:"SERIES",isAdult:!!e.episodes[0].isAdult})})}),f.length>0&&(e.episodes=f)}}catch(c){x("[Séries] Falha ao obter episódios Xtream: "+(c&&c.message),"warn")}}Il=e,r("seriesModalTitle").textContent="🍿 "+e.title;let t={};e.episodes.forEach(s=>{let i=1,c=s.name.match(/(?:s|temporada|temp\.?|t)\s*(\d{1,2})|\b(\d{1,2})x\d{1,4}\b/i);c&&(i=parseInt(c[1]||c[2],10)||1);let u="Temporada "+i;t[u]||(t[u]=[]),t[u].push(s)}),Object.keys(t).forEach(s=>{t[s].sort((i,c)=>{let u=i.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i),d=c.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i),l=u?parseInt(u[1]||u[2],10):0,f=d?parseInt(d[1]||d[2],10):0;return l!==f&&l>0&&f>0?l-f:i.name.localeCompare(c.name,void 0,{numeric:!0})})});let n=Object.keys(t).sort((s,i)=>{let c=parseInt(s.replace(/\D/g,""),10)||0,u=parseInt(i.replace(/\D/g,""),10)||0;return c-u}),o=r("listSeasons");o.innerHTML="",n.forEach((s,i)=>{let c=document.createElement("li"),u=document.createElement("button");u.className="season-tab-btn"+(i===0?" active":""),u.setAttribute("tabindex","0"),u.textContent=s+` (${t[s].length} eps)`,u.onclick=()=>{o.querySelectorAll(".season-tab-btn").forEach(d=>d.classList.remove("active")),u.classList.add("active"),Lr(t[s],s)},c.appendChild(u),o.appendChild(c)}),n.length>0&&Lr(t[n[0]],n[0]),r("modalSeriesEpisodes").classList.remove("hidden"),T="modalSeries",Tl=0,Al=0;let a=o.querySelector(".season-tab-btn");a&&a.focus()}function Lr(e,t){r("episodesListHeading").textContent=`🎬 ${t} (${e.length} episódios)`;let n=r("listEpisodes");n.innerHTML="",e.forEach((o,a)=>{let s=document.createElement("li"),i=document.createElement("button");i.className="episode-item-btn",i.setAttribute("tabindex","0"),i.setAttribute("data-epidx",a);let c=document.createElement("span");c.textContent=o.name;let u=document.createElement("span");u.textContent="▶ Assistir",u.style.color="#ffd54f",i.appendChild(c),i.appendChild(u),i.onclick=()=>{r("modalSeriesEpisodes").classList.add("hidden"),T="player",fe(o),En(!0)},s.appendChild(i),n.appendChild(s)})}r("btnCloseSeriesModal").onclick=()=>{r("modalSeriesEpisodes").classList.add("hidden"),T="channels",Le()};var Bn=new Map;function wr(e){if(!e||typeof e!="string")return"";let t=e.trim();if(/^[A-Za-z0-9+/=]+$/.test(t)&&t.length>=4&&t.length%4===0)try{let n=atob(t);if(/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF\u0100-\uFFFF]+$/.test(n))try{return decodeURIComponent(escape(n))}catch{return n}}catch{}return t}function ms(e){let t=new Date,n=t.getHours(),o=t.getMinutes(),a=n*60+o,s=(e||"").toUpperCase(),i="Programação Geral",c="A Seguir: Variedades",u=n,d=0,l=n+1,f=0;s.includes("GLOBO")?n>=6&&n<9?(i="Bom Dia Brasil",c="Mais Você",u=6,l=9):n>=9&&n<12?(i="Mais Você com Ana Maria",c="Praça TV",u=9,l=12):n>=12&&n<14?(i="Praça TV - 1ª Edição",c="Globo Esporte",u=12,l=14):n>=14&&n<17?(i="Sessão da Tarde",c="Vale a Pena Ver de Novo",u=14,l=17):n>=17&&n<19?(i="Vale a Pena Ver de Novo",c="Novela das Seis",u=17,l=19):n>=19&&n<21?(i="Novela das Sete / Jornal Nacional",c="Novela das Nove",u=19,l=21):n>=21&&n<23?(i="Novela das Nove",c="Tela Quente",u=21,l=23):(i="Cinema Especial / Madrugada",c="Hora Um",u=23,l=6):s.includes("RECORD")?n>=6&&n<10?(i="Balanço Geral Manhã / Fala Brasil",c="Hoje em Dia",u=6,l=10):n>=10&&n<12?(i="Hoje em Dia",c="Balanço Geral",u=10,l=12):n>=12&&n<15?(i="Balanço Geral & Hora da Venenosa",c="Novela da Tarde",u=12,l=15):n>=15&&n<17?(i="Novela da Tarde",c="Cidade Alerta",u=15,l=17):n>=17&&n<20?(i="Cidade Alerta com Helicóptero",c="Jornal da Record",u=17,l=20):n>=20&&n<22?(i="Jornal da Record & Novela Bíblica",c="A Fazenda",u=20,l=22):(i="Super Tela / A Fazenda",c="Programação Religiosa",u=22,l=6):s.includes("SBT")?n>=6&&n<10?(i="Primeiro Impacto",c="Bom Dia & Cia",u=6,l=10):n>=10&&n<13?(i="Bom Dia & Cia / Séries",c="Chaves",u=10,l=13):n>=13&&n<15?(i="Chaves & Seriados Amados",c="Fofocalizando",u=13,l=15):n>=15&&n<17?(i="Fofocalizando & Fofocas",c="Novelas Mexicanas",u=15,l=17):n>=17&&n<20?(i="Novelas da Tarde",c="SBT Brasil",u=17,l=20):n>=20&&n<22?(i="SBT Brasil & Romeu e Julieta",c="Programa do Ratinho",u=20,l=22):(i="Programa do Ratinho / A Praça é Nossa",c="The Noite",u=22,l=6):s.includes("SPORT")||s.includes("ESPN")||s.includes("PREMIERE")?n>=8&&n<12?(i="SportsCenter / Redação SporTV",c="Futebol 360",u=8,l=12):n>=12&&n<16?(i="Futebol Ao Vivo - Pré-Jogo",c="Transmissão Ao Vivo",u=12,l=16):n>=16&&n<19?(i="Campeonato Ao Vivo - 1º Tempo",c="Troca de Passes",u=16,l=19):n>=19&&n<22?(i="Jogo da Noite Ao Vivo",c="Gols da Rodada",u=19,l=22):(i="Linha de Passe & Melhores Momentos",c="Giro do Esporte",u=22,l=8):s.includes("TELE")||s.includes("HBO")||s.includes("WARNER")||s.includes("UNIVERSAL")?n>=10&&n<13?(i="Sessão Aventura: Os Vingadores",c="Comédia em Alta",u=10,l=13):n>=13&&n<16?(i="Batman - O Cavaleiro das Trevas",c="Superestreia",u=13,l=16):n>=16&&n<19?(i="Superestreia: Duna Parte 2",c="Série do Ano",u=16,l=19):n>=19&&n<22?(i="House of the Dragon",c="Blockbuster",u=19,l=22):(i="Blockbuster do Ano: Oppenheimer",c="Sessão Noturna",u=22,l=10):(i=`Programação ao Vivo (${n}:00 - ${n+1}:00)`,c=`Transmissão Especial (${n+1}:00)`,u=n,l=n+1);let p=u*60+d,m=l*60+f,y=Math.max(1,m-p),g=Math.max(0,a-p),h=Math.min(100,Math.max(0,Math.round(g/y*100))),b=`${String(u).padStart(2,"0")}:${String(d).padStart(2,"0")} - ${String(l).padStart(2,"0")}:${String(f).padStart(2,"0")}`;return{title:i,nextTitle:c,timeStr:b,progressPercent:h}}async function Fl(e){if(!e)return;let t=ms(e.name);r("epgTitle")&&(r("epgTitle").textContent=(G.has(e.url)?"⭐ ":"▶ ")+e.name),r("fsHudTitle")&&(r("fsHudTitle").textContent=(G.has(e.url)?"⭐ ":"")+e.name),r("epgGroup")&&(r("epgGroup").textContent=`Categoria: ${e.group} • ${t.timeStr}`),r("previewEpgNow")&&(r("previewEpgNow").textContent=`NO AR: ${t.title}`),r("previewEpgNext")&&(r("previewEpgNext").textContent=`A SEGUIR: ${t.nextTitle}`),r("epgStatus")&&(r("epgStatus").innerHTML=`🔴 NO AR: <b>${q(t.title)}</b>`);let n=document.querySelector(".epg-progress");if(n&&(n.style.width=`${t.progressPercent}%`),r("epgNext")&&(r("epgNext").textContent=`A seguir: ${t.nextTitle}`),r("fsEpgCurrentTitle")&&(r("fsEpgCurrentTitle").textContent=t.title),r("fsEpgTimeBadge")&&(r("fsEpgTimeBadge").textContent=t.timeStr),r("fsEpgFill")&&(r("fsEpgFill").style.width=`${t.progressPercent}%`),r("fsEpgNextTitle")&&(r("fsEpgNextTitle").textContent=t.nextTitle),e.contentType!=="LIVE"){r("epgStatus")&&(r("epgStatus").textContent="▶ Reproduzindo VOD"),localStorage.getItem("mk21_use_tmdb")!=="false"&&Vl(e);return}let o=L[N]||rt[0],a=e.streamId;if(!a&&e.url){let l=e.url.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);l&&l[1]&&(a=l[1])}let s=Wt()||{user:"",pass:""},i=s.user,c=s.pass;if(!o||!o.url||!a||!i||!c)return;let u=`epg_${a}`;if(Bn.has(u)){Tr(e,Bn.get(u));return}let d=await gs(e,a,o,i,c,6);d&&d.length>0&&v===e&&Tr(e,d)}async function gs(e,t,n,o,a,s){if(!n||!n.url||!t||!o||!a)return null;let i=`epg_${t}`;if(Bn.has(i))return Bn.get(i);try{let u=`${n.url.replace(/\/+$/,"")}/player_api.php?username=${encodeURIComponent(o)}&password=${encodeURIComponent(a)}&action=get_short_epg&stream_id=${t}&limit=${s||6}`,d=await fetch(u);if(d.ok){let l=await d.json();if(l&&Array.isArray(l.epg_listings)&&l.epg_listings.length>0)return Bn.set(i,l.epg_listings),l.epg_listings}}catch(c){console.debug("[SmartTV EPG] Falha ao carregar metadados reais, mantendo simulação:",c)}return null}function kr(e){if(!e)return NaN;if(typeof e=="number")return e>1e11?e:e*1e3;let t=String(e).trim();if(/^\d{10}$/.test(t))return parseInt(t,10)*1e3;if(/^\d{13}$/.test(t))return parseInt(t,10);let n=t.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);if(n){let a=n[1],s=n[2],i=n[3],c=n[4],u=n[5],d=n[6],l=n[7],f;l?f=`${a}-${s}-${i}T${c}:${u}:${d}${l.slice(0,3)}:${l.slice(3)}`:f=`${a}-${s}-${i}T${c}:${u}:${d}Z`;let p=new Date(f).getTime();if(!isNaN(p))return p}let o=new Date(t.replace(" ","T")).getTime();return isNaN(o)?NaN:o}function Tr(e,t){if(!t||t.length===0||v!==e)return;let n=Date.now(),o=t.map(m=>{let y=kr(m.start_timestamp||m.start),g=kr(m.stop_timestamp||m.end||m.stop);return{title:wr(m.title),desc:wr(m.description),start:y,stop:g}}).filter(m=>!isNaN(m.start)&&!isNaN(m.stop));if(o.length===0)return;mo=o;let a=o.find(m=>m.start<=n&&m.stop>n)||o[0],s=o.indexOf(a),i=s>=0&&s+1<o.length?o[s+1]:null,c=new Date(a.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}),u=new Date(a.stop).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}),d=Math.max(1,a.stop-a.start),l=Math.max(0,n-a.start),f=Math.min(100,Math.max(0,Math.round(l/d*100)));if(r("epgTitle")&&(r("epgTitle").textContent=`▶ ${e.name}`),r("epgGroup")&&(r("epgGroup").textContent=`Categoria: ${e.group} • ${c} - ${u}`),r("previewEpgNow")&&(r("previewEpgNow").innerHTML=`NO AR: <b>${q(a.title)}</b>`),r("previewEpgNext")){let m=i?new Date(i.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"}):"--:--";r("previewEpgNext").textContent=`A SEGUIR (${m}): ${i?i.title:"Programação Normal"}`}r("epgStatus")&&(r("epgStatus").innerHTML=`🔴 NO AR: <b>${q(a.title)}</b> <span style="font-size:10px; background:rgba(16,185,129,0.25); color:#10b981; border:1px solid rgba(16,185,129,0.4); border-radius:4px; padding:1px 5px; margin-left:6px; font-weight:800;">📡 XMLTV REAL</span>`);let p=document.querySelector(".epg-progress");if(p&&(p.style.width=`${f}%`),r("epgNext")&&i){let m=new Date(i.start).toLocaleTimeString(void 0,{hour:"2-digit",minute:"2-digit"});r("epgNext").textContent=`A seguir (${m}): ${i.title}`}r("fsEpgCurrentTitle")&&(r("fsEpgCurrentTitle").textContent=a.title),r("fsEpgTimeBadge")&&(r("fsEpgTimeBadge").textContent=`${c} - ${u}`),r("fsEpgFill")&&(r("fsEpgFill").style.width=`${f}%`),r("fsEpgNextTitle")&&(r("fsEpgNextTitle").textContent=i?i.title:"Programação Normal")}var Xo=new Map;async function Vl(e){if(!e||!e.name)return;let t=e.name.replace(/\b(1080p|720p|4k|fhd|hd|dublado|legendado|dual|audio|h264|hevc)\b.*$/i,"").trim();if(Xo.has(t)){Ar(e,Xo.get(t));return}try{let n="https://api.themoviedb.org/3/search/multi?api_key=b41249b6754020a656799015bc2301f2&language=pt-BR&query="+encodeURIComponent(t),o=await fetch(n);if(o.ok){let a=await o.json();if(a&&a.results&&a.results.length>0){let s=a.results[0];Xo.set(t,s),v===e&&Ar(e,s)}}}catch{}}function Ar(e,t){if(!t||v!==e)return;let n=t.overview||t.title||t.name;if(r("epgNext")&&n&&(r("epgNext").textContent="Sinopse: "+(n.length>120?n.substring(0,120)+"...":n)),r("epgGroup")){let a=t.release_date||t.first_air_date||"";r("epgGroup").textContent=`${e.group}${a?" • "+a.substring(0,4):""}`}let o=document.querySelector(".badge-quality");o&&t.vote_average&&(o.textContent="★ "+t.vote_average.toFixed(1)+" TMDB")}var oo=null,_t=new Map;function $l(e){if(!e||!e.url||!D||D.length<=1||S!=="LIVE")return;if(oo)try{oo.abort()}catch{}oo=new AbortController;let t=oo.signal,n=D.findIndex(i=>i.url===e.url);if(n===-1)return;let o=(n-1+D.length)%D.length,a=(n+1)%D.length,s=[D[a],D[o]];setTimeout(()=>{s.forEach(i=>{if(!(!i||!i.url||i.url===e.url))try{i.url.toLowerCase().includes(".m3u8")&&!_t.has(i.url)?fetch(i.url,{signal:t,headers:{Range:"bytes=0-32767"},cache:"default",priority:"low"}).then(u=>u.ok?u.text():null).then(u=>{if(u&&(u.includes("#EXTM3U")||u.includes("#EXT-X-STREAM-INF"))&&(_t.set(i.url,{text:u,timestamp:Date.now()}),_t.size>12)){let d=_t.keys().next().value;_t.delete(d)}}).catch(()=>{}):fetch(i.url,{method:"HEAD",signal:t,priority:"low"}).catch(()=>{})}catch{}})},400)}var Ir=null;function Hl(e){clearInterval(Ir),!(!e||e.contentType==="LIVE")&&(Ir=setInterval(()=>{let t=r("tvPlayer");!t||t.paused||!t.duration||isNaN(t.duration)||zl(e.url,t.currentTime,t.duration)},1e4))}function zl(e,t,n){if(!v||v.contentType==="LIVE"||!t||t<3)return;let o=Math.min(100,Math.max(1,Math.round(t/n*100))),a=K.findIndex(i=>i.url===e),s={name:v.name,group:v.group,logo:v.logo,url:e,contentType:v.contentType,currentTime:Math.round(t),duration:Math.round(n),progressPct:o,timestamp:Date.now()};a>=0&&K.splice(a,1),K.unshift(s),K.length>50&&(K=K.slice(0,50));try{localStorage.setItem("mk21_continue_watching",JSON.stringify(K))}catch{}}var ia=[{id:"contain",label:"Proporção: Original (16:9)",short:"Normal",objectFit:"contain"},{id:"cover",label:"Proporção: Zoom (Preencher)",short:"Zoom",objectFit:"cover"},{id:"fill",label:"Proporção: Esticar (Distorcer)",short:"Esticar",objectFit:"fill"}],po=0;function hs(){let e=r("tvPlayer");if(!e)return;po=(po+1)%ia.length;let t=ia[po];e.style.objectFit=t.objectFit,E(t.label);let n="📐 "+t.short;r("fsBtnAspect")&&(r("fsBtnAspect").textContent=n),r("btnAspect")&&(r("btnAspect").textContent=n),r("diagAspect")&&(r("diagAspect").textContent=t.label)}function Ul(){let e=r("tvPlayer");e&&(e.muted=!e.muted,E(e.muted?"🔇 Áudio Mudo":"🔊 Áudio Ativado"))}function Mr(){let e=r("modalStreamDiagnostics");if(!e)return;if(!e.classList.contains("hidden")){e.classList.add("hidden"),T="channels",Le();return}Gl(),e.classList.remove("hidden"),T="modalStreamDiagnostics";let t=r("btnCloseDiagnostics");t&&t.focus()}function Gl(){let e=r("tvPlayer");if(r("diagTitle")&&(r("diagTitle").textContent=v?v.name:"Nenhum canal ativo"),e){let t=e.videoWidth||0,n=e.videoHeight||0;r("diagResolution")&&(r("diagResolution").textContent=t&&n?`${t}x${n} (${t>=1920?"FHD":t>=1280?"HD":"SD"})`:"Detectando resolução...");let o=0;if(e.buffered&&e.buffered.length>0){for(let a=0;a<e.buffered.length;a++)if(e.currentTime>=e.buffered.start(a)&&e.currentTime<=e.buffered.end(a)){o=Math.round(e.buffered.end(a)-e.currentTime);break}}if(r("diagBuffer")&&(r("diagBuffer").textContent=`${o}s de buffer`),k&&k.levels&&k.currentLevel>=0){let a=k.levels[k.currentLevel];if(a){let s=a.bitrate?(a.bitrate/1e6).toFixed(2)+" Mbps":"Automático (~3.5 Mbps)";r("diagBitrate")&&(r("diagBitrate").textContent=s),r("diagCodec")&&(r("diagCodec").textContent=a.codecs||(v&&v.url.includes(".m3u8")?"HLS (AVC/AAC)":"MPEG-TS"))}}else r("diagBitrate")&&(r("diagBitrate").textContent="Stream Direto (~4.0 Mbps)"),r("diagCodec")&&(r("diagCodec").textContent=v&&v.url.includes(".m3u8")?"HLS (H.264/AAC)":"MPEG-TS/MP4");r("diagAspect")&&(r("diagAspect").textContent=ia[po].label)}}var $t="",Pr=null;function ql(e){$t+=String(e);let t=r("channelNumberOverlay"),n=r("channelNumberDigits");n&&(n.textContent=$t),t&&t.classList.remove("hidden"),clearTimeout(Pr),Pr=setTimeout(()=>{vs()},1800)}function vs(){let e=r("channelNumberOverlay");if(e&&e.classList.add("hidden"),!$t)return;let t=parseInt($t,10);if($t="",!D||D.length===0)return;let n=t-1;n>=0&&n<D.length?(fe(D[n]),E(`Canal ${t}: ${D[n].name}`),_o(D[n])):E(`Canal ${t} não encontrado`)}var Dn=new Map;function Wl(e){let n=`<svg xmlns="http://www.w3.org/2000/svg" width="60" height="40" viewBox="0 0 60 40"><rect width="60" height="40" rx="6" fill="#141828" stroke="rgba(255,255,255,0.1)"/><text x="50%" y="54%" font-family="sans-serif" font-weight="bold" font-size="12" fill="#ffd54f" dominant-baseline="middle" text-anchor="middle">${String(e||"MK21").trim().slice(0,4).toUpperCase()}</text></svg>`;return"data:image/svg+xml;utf8,"+encodeURIComponent(n)}function jl(e,t=""){return!e||typeof e!="string"||e.length<5?Wl(t):Dn.has(e)?Dn.get(e):(Dn.set(e,e),e)}function Kl(){try{_t.size>5&&_t.clear(),Dn.size>150&&Dn.clear(),window.gc&&typeof window.gc=="function"&&window.gc()}catch(e){console.debug("[MemoryShield]",e)}}var ys="ALL",Ot={ALL:[],LIVE:[],MOVIE:[],SERIES:[]},Rr=null;function Nr(e=""){let t=r("modalOmnisearch");if(!t)return;t.classList.remove("hidden"),T="modalOmnisearch";let n=r("inputOmnisearch");n&&(e&&(n.value=e),setTimeout(()=>{n.focus()},100)),n&&n.value.trim().length>=2?Ea(n.value.trim()):So()}function bs(){let e=r("modalOmnisearch");e&&e.classList.add("hidden"),T="channels",Le()}async function Ea(e){if(!e||e.trim().length<2){Ot={ALL:[],LIVE:[],MOVIE:[],SERIES:[]},So();return}let t=e.toLowerCase().trim(),n=allContent.LIVE||[],o=allContent.MOVIE||[],a=allContent.SERIES||[],s=d=>{if(!d||!Ke&&(d.isAdult||re(d.name)||re(d.group)))return!1;let l=d.name&&d.name.toLowerCase().includes(t),f=d.normalizedName&&d.normalizedName.includes(t),p=d.group&&d.group.toLowerCase().includes(t);return l||f||p},i=n.filter(s).slice(0,80),c=o.filter(s).slice(0,80),u=a.filter(s).slice(0,80);if(i.length===0&&c.length===0&&u.length===0)try{let d=await es(t);d&&d.length>0&&d.forEach(l=>{l.contentType==="LIVE"?i.push(l):l.contentType==="MOVIE"?c.push(l):l.contentType==="SERIES"&&u.push(l)})}catch{}Ot={ALL:[...i,...c,...u],LIVE:i,MOVIE:c,SERIES:u},So()}function So(){r("countOmniAll")&&(r("countOmniAll").textContent=Ot.ALL.length),r("countOmniLive")&&(r("countOmniLive").textContent=Ot.LIVE.length),r("countOmniMovies")&&(r("countOmniMovies").textContent=Ot.MOVIE.length),r("countOmniSeries")&&(r("countOmniSeries").textContent=Ot.SERIES.length);let e=r("omnisearchResultsGrid"),t=r("omnisearchEmptyState");if(!e)return;e.innerHTML="";let n=Ot[ys]||[];if(n.length===0){if(t){t.classList.remove("hidden");let s=r("inputOmnisearch");t.textContent=s&&s.value.trim().length>=2?'Nenhum resultado encontrado para "'+s.value.trim()+'" nesta categoria.':"Digite uma palavra-chave para buscar instantaneamente em todo o catálogo."}return}t&&t.classList.add("hidden");let o=document.createDocumentFragment(),a=Math.min(n.length,60);for(let s=0;s<a;s++){let i=n[s],c=document.createElement("button");c.className="omnisearch-item-btn",c.setAttribute("tabindex","0");let u=i.contentType==="LIVE"?"badge-live":i.contentType==="MOVIE"?"badge-movie":"badge-series",d=i.contentType==="LIVE"?"Ao Vivo":i.contentType==="MOVIE"?"Filme":"Série";c.innerHTML=`
       <div class="omnisearch-item-header">
-        <span class="omnisearch-type-badge ${u}">${K(d)}</span>
-        <span style="font-size:12px; color:#ffd54f;">${s.isAdult?"+18":""}</span>
+        <span class="omnisearch-type-badge ${u}">${q(d)}</span>
+        <span style="font-size:12px; color:#ffd54f;">${i.isAdult?"+18":""}</span>
       </div>
-      <div class="omnisearch-item-name">${K(s.name)}</div>
-      <div class="omnisearch-item-group">${K(s.group||"Geral")}</div>
-    `,c.onclick=()=>{if(Ai(),s.contentType==="SERIES"&&typeof Ba=="function"){let l={title:s.name,episodes:[s]};Ba(l)}else typeof qe=="function"&&S!==s.contentType&&qe(s.contentType),de(s)},o.appendChild(c)}e.appendChild(o)}var jn=[];function $n(){if(!v){E("Sintonize um canal para ver a grade");return}let e=a("modalEpgGuide");if(!e)return;e.classList.remove("hidden"),A="modalEpgGuide",a("txtEpgGuideTitle")&&(a("txtEpgGuideTitle").textContent="🔵 Programação: "+v.name);let t=a("epgScheduleList");if(t)if(jn&&jn.length>0){let n=Date.now(),o="";jn.forEach(i=>{let r=i.start<=n&&i.stop>n,s=new Date(i.start).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),c=new Date(i.stop).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"});o+=`
-        <div class="epg-schedule-row ${r?"active-now":""}">
-          <div class="epg-time-col">${K(s)} - ${K(c)} ${r?'<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span>':""}</div>
+      <div class="omnisearch-item-name">${q(i.name)}</div>
+      <div class="omnisearch-item-group">${q(i.group||"Geral")}</div>
+    `,c.onclick=()=>{if(bs(),i.contentType==="SERIES"&&typeof sa=="function"){let l={title:i.name,episodes:[i]};sa(l)}else typeof Pe=="function"&&S!==i.contentType&&Pe(i.contentType),fe(i)},o.appendChild(c)}e.appendChild(o)}var mo=[];function ao(){if(!v){E("Sintonize um canal para ver a grade");return}let e=r("modalEpgGuide");if(!e)return;e.classList.remove("hidden"),T="modalEpgGuide",r("txtEpgGuideTitle")&&(r("txtEpgGuideTitle").textContent="🔵 Programação: "+v.name);let t=r("epgScheduleList");if(t)if(mo&&mo.length>0){let n=Date.now(),o="";mo.forEach(a=>{let s=a.start<=n&&a.stop>n,i=new Date(a.start).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),c=new Date(a.stop).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"});o+=`
+        <div class="epg-schedule-row ${s?"active-now":""}">
+          <div class="epg-time-col">${q(i)} - ${q(c)} ${s?'<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span>':""}</div>
           <div class="epg-details-col">
-            <div class="epg-sched-title">${K(i.title)}</div>
-            <div class="epg-sched-desc">${K(i.desc||"Sem sinopse disponível.")}</div>
+            <div class="epg-sched-title">${q(a.title)}</div>
+            <div class="epg-sched-desc">${q(a.desc||"Sem sinopse disponível.")}</div>
           </div>
         </div>
-      `}),t.innerHTML=o}else{let n=new Date,o=n.toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),r=new Date(n.getTime()+50*6e4).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),c=new Date(n.getTime()+110*6e4).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"});t.innerHTML=`
+      `}),t.innerHTML=o}else{let n=new Date,o=n.toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),s=new Date(n.getTime()+50*6e4).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}),c=new Date(n.getTime()+110*6e4).toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"});t.innerHTML=`
       <div class="epg-schedule-row active-now">
-        <div class="epg-time-col">${K(o)} - ${K(r)}<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span></div>
+        <div class="epg-time-col">${q(o)} - ${q(s)}<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span></div>
         <div class="epg-details-col">
-          <div class="epg-sched-title">${K(v.name)} - Ao Vivo</div>
-          <div class="epg-sched-desc">Transmissão em tempo real da categoria ${K(v.group||"Geral")}.</div>
+          <div class="epg-sched-title">${q(v.name)} - Ao Vivo</div>
+          <div class="epg-sched-desc">Transmissão em tempo real da categoria ${q(v.group||"Geral")}.</div>
         </div>
       </div>
       <div class="epg-schedule-row">
-        <div class="epg-time-col">${K(r)} - ${K(c)}</div>
+        <div class="epg-time-col">${q(s)} - ${q(c)}</div>
         <div class="epg-details-col">
           <div class="epg-sched-title">Programação Sequencial</div>
           <div class="epg-sched-desc">Continuação da grade diária da emissora.</div>
         </div>
       </div>
-    `}}var Kn=null,Hn=-1,Le=0,Ve=!1,Je=0,Ca=5;function Xt(e){let t=a("playerReconnectBadge");if(!t){t=document.createElement("div"),t.id="playerReconnectBadge",t.className="player-reconnect-badge";let n=a("videoContainer")||a("tvPlayer")&&a("tvPlayer").parentElement;n&&n.appendChild(t)}t.textContent=e,t.classList.remove("hidden"),t.style.display="block"}function et(){let e=a("playerReconnectBadge");e&&(e.classList.add("hidden"),e.style.display="none")}function Ts(){Kn&&(clearInterval(Kn),Kn=null)}function _t(e){if(Ve||!v)return;if(Ve=!0,Je++,Je>Ca){if(console.error("[SmartPlayer Watchdog] Limite máximo de reconexão atingido:",e),Ve=!1,typeof ii=="function"){ii().then(n=>{n||(Xt("Falha de conexão. Tente outro canal."),a("epgStatus")&&(a("epgStatus").textContent="⚠️ FORA DO AR"))}).catch(()=>{Xt("Falha de conexão. Tente outro canal."),a("epgStatus")&&(a("epgStatus").textContent="⚠️ FORA DO AR")});return}Xt("Falha de conexão. Tente outro canal."),a("epgStatus")&&(a("epgStatus").textContent="⚠️ FORA DO AR");return}let t=Math.min(1e3*Math.pow(1.5,Je-1),6e3);Xt(`Reconectando (${Je}/${Ca})...`),a("epgStatus")&&(a("epgStatus").textContent=`🔄 RECONECTANDO (${Je}/${Ca})...`),setTimeout(()=>{if(!v){Ve=!1;return}if(console.log(`[SmartPlayer Watchdog] Executando reconexão tentativa ${Je} para: ${v.name}`),C)try{C.recoverMediaError()}catch{typeof de=="function"&&de(v)}else typeof de=="function"&&de(v);Ve=!1},t)}var Oa="",Wo=0,ka=-1,Ct=0,wa=0;function jo(){if(!v)return!1;let e=v.contentType||v.type||S;if(e==="MOVIE"||e==="SERIES")return!0;let t=String(v.url||"").toLowerCase();return t.includes("/movie/")||t.includes("/series/")||t.includes(".mp4")||t.includes(".mkv")||t.includes(".avi")}function As(e){Ts();let t=String(e||v&&v.url||"");Oa!==t&&(Oa=t,Hn=-1,Wo=0,Ct=Date.now(),wa=0,ka=-1,Le=0,Je=0,Ve=!1,et());let n=a("tvPlayer");if(n){if(!n._mk21EventsBound){n._mk21EventsBound=!0;let o=()=>{Wo=Date.now(),Le<1&&(Le=1)};n.addEventListener("waiting",o),n.addEventListener("stalled",o),n.addEventListener("seeking",o),n.addEventListener("playing",()=>{Le=0,Je=0,Ve=!1,et()}),n.addEventListener("progress",()=>{Ct=Date.now()}),n.addEventListener("error",i=>{!n.currentSrc&&!n.src||(console.warn("[SmartPlayer] Erro nativo no vídeo:",i),jo()||_t("Erro nativo de reprodução"))})}Kn=setInterval(()=>{let o=window.FBG2PlayerService;if(o&&o.isActive()){if(Ve||!v)return;let d=o.player&&o.player.getState?o.player.getState():"playing";if(d==="paused"||d==="buffering"||d==="preparing")return;let l=o.getCurrentTimeSec();if(Math.abs(l-ka)>=.25){ka=l,Le=0,Ct=Date.now();return}if(Date.now()-Ct<3e3)return;Le++,Le>=Ko()&&(console.warn("[SmartPlayer Watchdog] Travamento real detectado (factory)."),Le=0,_t("Stream congelado (factory)"));return}if(!n||n.paused||Ve||!v)return;let r=jo()?30:6,s=n.currentTime;if(Math.abs(s-Hn)>=.25){Le=0,Hn=s;return}let c=0;try{n.buffered&&n.buffered.length&&(c=n.buffered.end(n.buffered.length-1))}catch{}if(c>wa+.05&&(wa=c,Ct=Date.now()),Date.now()-Ct<3e3&&Le<3){Hn=s;return}Le++,Le>=Ko()&&(console.warn("[SmartPlayer Watchdog] Travamento real detectado no stream."),Le=0,_t("Stream congelado"))},1e3)}}var Bt=[],Is=400,dt=!1,Ps={error:"#ef4444",warn:"#eab308",success:"#22c55e",debug:"#94a3b8",info:"#93c5fd"};function Xa(){return a("devLogsContainer")||document.querySelector(".devlogs-screen")}function Ii(e){let t=Ps[e.type]||"#cbd5e1",n=document.createElement("div");return n.className="log-line",n.innerHTML='<span class="log-time">['+e.time+']</span> <span class="log-type" style="color:'+t+'">'+String(e.type||"info").toUpperCase()+'</span><span style="color:'+t+'">'+K(e.msg)+"</span>",n}function Ja(){let e=a("devLogsCountBadge");e&&(e.textContent=Bt.length+" entradas")}function Ms(){let e=Xa();e&&(e.replaceChildren(),Bt.forEach(t=>e.appendChild(Ii(t))),e.scrollTop=e.scrollHeight,Ja())}function x(e,t="info"){let o={time:new Date().toLocaleTimeString("pt-BR",{hour12:!1}),msg:e,type:t};Bt.push(o),Bt.length>Is&&Bt.shift();try{let i=Xa();i&&(i.querySelector('div[style*="64748b"]')&&i.replaceChildren(),i.appendChild(Ii(o)),dt||(i.scrollTop=i.scrollHeight)),Ja()}catch{}t==="error"?console.error("[MK21-TV]",e):t==="warn"?console.warn("[MK21-TV]",e):console.log("[MK21-TV]",e)}function K(e){return String(e||"").replace(/[&<>"']/g,t=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[t])}function Pi(){let e=a("btnCloseDevLogs");e&&(e.onclick=()=>{let i=a("modalDevLogs");i&&!i.classList.contains("hidden")&&Qa()});let t=a("btnTogglePauseDevLogs");t&&(t.textContent=dt?"▶️ Retomar rolagem":"⏸ Pausar rolagem",t.classList.toggle("primary",dt),t.onclick=()=>{dt=!dt;let i=a("btnTogglePauseDevLogs");i&&(i.textContent=dt?"▶️ Retomar rolagem":"⏸ Pausar rolagem",i.classList.toggle("primary",dt));let r=a("devLogsHint");r&&(r.textContent=dt?"Rolagem automática pausada: a tela fica parada para você ler (ou fotografar) sem os logs fugirem.":"Use o botão de rolagem do Magic Remote (ou CH + / CH -) para percorrer o histórico.")});let n=a("btnClearDevLogs");n&&(n.onclick=()=>{Bt=[];let i=Xa();i&&i.replaceChildren(),Ja(),x("[Logs] Histórico limpo.","info")});let o=a("btnExportDevLogs");o&&(o.onclick=()=>{try{let i=["MK21 Smart TV - Logs de diagnostico","Data: "+new Date().toISOString(),"Versao: "+ht,"Tela: "+innerWidth+"x"+innerHeight,"Credenciais e caminhos autenticados foram mascarados.","",...Bt.map(c=>"["+c.time+"] ["+String(c.type).toUpperCase()+"] "+Ri(c.msg))].join(`
-`);localStorage.setItem("mk21_last_exported_logs",i);let r=new Blob([i],{type:"text/plain;charset=utf-8"}),s=document.createElement("a");s.href=URL.createObjectURL(r),s.download="mk21-tv-logs-"+new Date().toISOString().replace(/[:.]/g,"-")+".txt",document.body.appendChild(s),s.click(),document.body.removeChild(s),x("[Logs] Arquivo de log exportado e salvo com sucesso!","success"),E("✅ Logs exportados com sucesso!")}catch(i){x("[Logs] Erro ao exportar logs: "+i.message,"error")}})}function Mi(){let e=a("modalDevLogs");return e||(e=document.createElement("div"),e.id="modalDevLogs",document.body.appendChild(e)),e.dataset.mk21LogsReady!=="1"&&(e.className="app-modal devlogs-modal hidden",e.innerHTML='<div class="modal-content devlogs-card"><div class="devlogs-head"><span class="devlogs-title">🖥️ Diagnóstico em tempo real</span><div class="devlogs-actions"><button type="button" id="btnTogglePauseDevLogs" class="ctrl-btn" tabindex="0">⏸ Pausar rolagem</button><button type="button" id="btnClearDevLogs" class="ctrl-btn" tabindex="0">🗑️ Limpar</button><button type="button" id="btnExportDevLogs" class="ctrl-btn primary" tabindex="0">💾 Salvar arquivo</button><button type="button" id="btnCloseDevLogs" class="ctrl-btn" tabindex="0">✕ Fechar</button></div></div><div class="devlogs-info" id="devLogsInfo"></div><div id="devLogsContainer" class="devlogs-screen scrollable-container" tabindex="0"></div><div class="devlogs-foot"><span id="devLogsHint">Use o botão de rolagem do Magic Remote (ou CH + / CH -) para percorrer o histórico.</span><span id="devLogsCountBadge">0 entradas</span></div></div>',e.dataset.mk21LogsReady="1"),Pi(),e}function Rs(){let e=a("devLogsInfo");if(!e)return;let t="--";try{t=X((L[_]||{}).name||"--")}catch{}let n=a("tvPlayer"),o="--";try{if(n&&n.buffered&&n.buffered.length>0){let u=n.buffered.end(n.buffered.length-1);o=Math.max(0,Math.round(u-(n.currentTime||0)))+"s"}}catch{}let i=typeof C!="undefined"&&C?"hls.js":typeof U!="undefined"&&U?"mpegts.js":"nativo",r=n&&n.videoHeight?n.videoWidth+"x"+n.videoHeight:"--",s="--";try{s=ht}catch{}let c=[["Versão","v"+s],["Fonte",t],["Canal",v?v.name:"nenhum"],["Engine",i],["Imagem",r],["Buffer",o],["Rede",navigator.onLine?"online":"offline"],["Tela",innerWidth+"x"+innerHeight],["Buffer pref",(Ge[vt()]||{}).label||"--"]];e.innerHTML=c.map(u=>"<span>"+u[0]+": <b>"+K(String(u[1]))+"</b></span>").join("")}function Qa(){let e=Mi();if(pt=null,e.classList.contains("hidden"))e.classList.remove("hidden"),A="modalDevLogs",Rs(),Ms(),window.__mk21WebOSScrollFixActive&&a("devLogsContainer")&&An(a("devLogsContainer")),x("[UI] Console de logs aberto","info"),setTimeout(()=>{let n=a("btnTogglePauseDevLogs")||a("btnCloseDevLogs");n&&n.focus()},30);else{e.classList.add("hidden"),A=S==="SETTINGS"?"settings":"channels";let n=a("btnHeaderLogs");n&&Me(n)?n.focus():Se()}}function _s(){Mi(),a("btnHeaderLogs")&&(a("btnHeaderLogs").onclick=Qa),Pi()}function Ri(e){return String(e).replace(/(https?:\/\/)[^/@\s:]+:[^/@\s]+@/gi,"$1[REDACTED]@").replace(/([?&](?:username|user|password|pass|token|auth|api[_-]?key)=)[^&#\s]*/gi,"$1[REDACTED]").replace(/\/(live|movie|series)\/[^/\s?]+\/[^/\s?]+/gi,"/$1/[REDACTED]/[REDACTED]")}var U=null,Pt=null;function kn(e){if(!e)return Promise.resolve();try{let t=e.play();if(t&&typeof t.then=="function")return t.catch(n=>{if(n&&(n.name==="AbortError"||n.message&&n.message.includes("interrupted"))){console.debug("[Player] Play request interrupted cleanly by zapping.");return}console.warn("[Player Play Error]:",n&&n.message)})}catch(t){console.warn("[Player Exception]:",t)}return Promise.resolve()}function wn(e=!0){if(window.FBG2PlayerService&&window.FBG2PlayerService.stop(),e&&Pt){try{Pt.abort()}catch{}Pt=null}if(U){try{U.pause(),U.unload(),U.detachMediaElement(),U.destroy()}catch{}U=null}if(C){try{C.destroy()}catch{}C=null}let t=a("tvPlayer");if(t)try{t.pause(),t.src="",t.removeAttribute("src"),t.innerHTML="",t.load()}catch{}}var Ge={auto:{label:"Automático (recomendado)",hint:"Equilíbrio entre zapping rápido e estabilidade.",hls:{maxBufferLength:20,maxMaxBufferLength:40,liveSyncDurationCount:3,backBufferLength:10,maxBufferSize:20*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:30,stashInitialSize:256*1024},stallTolerance:10},fast:{label:"Rápido / Zapping veloz",hint:"Troca de canal quase instantânea. Usa internet boa.",hls:{maxBufferLength:10,maxMaxBufferLength:20,liveSyncDurationCount:2,backBufferLength:4,maxBufferSize:12*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:20,stashInitialSize:96*1024},stallTolerance:6},stable:{label:"Estável / Internet fraca",hint:"Buffer maior: demora um pouco a abrir, mas quase não trava.",hls:{maxBufferLength:30,maxMaxBufferLength:60,liveSyncDurationCount:4,backBufferLength:20,maxBufferSize:30*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:45,stashInitialSize:512*1024},stallTolerance:16}};function Jt(e,t){let n=window.FBG2PlayerService;if(n&&n.isActive()){n.seekMs(Math.round(n.getCurrentTimeSec()*1e3)+e);return}let o=t||a("tvPlayer");if(!o)return;let i=Math.max(0,(o.currentTime||0)*1e3+e)/1e3;o.currentTime=o.duration?Math.min(o.duration,i):i}function Ko(){let e=Ge[vt()];return e&&e.stallTolerance||10}function vt(){let e=localStorage.getItem("mk21_buffer_mode")||"auto";return Ge[e]?e:"auto"}function _i(){let e=Ge[vt()];return Object.assign({enableWorker:!0,lowLatencyMode:!1,startFragPrefetch:!0,maxBufferSize:20*1e3*1e3,maxStarvationDelay:6,maxLoadingDelay:6,abrEwmaDefaultEstimate:12e5,fragLoadingTimeOut:2e4,manifestLoadingTimeOut:15e3,levelLoadingTimeOut:15e3,fragLoadingMaxRetry:5,manifestLoadingMaxRetry:4,levelLoadingMaxRetry:4,fragLoadingRetryDelay:800,manifestLoadingRetryDelay:1e3,fragLoadingMaxRetryTimeout:12e3,levelLoadingMaxRetryTimeout:8e3},e.hls)}function Bs(){let e=Ge[vt()];return Object.assign({enableWorker:!0,lazyLoad:!1,autoCleanupSourceBuffer:!0},e.mpegts)}var bn={url:"",at:0,stage:0},zn=new Set;function Bi(e,t){if(!e||!e.url)return!1;let n=Date.now();return bn.url===e.url&&n-bn.at<6e3&&bn.stage>=t?!1:(bn={url:e.url,at:n,stage:t},!0)}function Ni(e){let t=e&&(e.message||e.msg||"")||"";return(e&&e.name||"")==="AbortError"||/interrupted|abort/i.test(t)}function Yo(e){if(!e||!e.url||!Bi(e,1))return!1;let t=a("tvPlayer");if(!t)return!1;x("[Player Fallback] Tentando MPEG-TS nativo no hardware da TV...","info");try{if(C&&(C.destroy(),C=null),U){try{U.unload(),U.detachMediaElement(),U.destroy()}catch{}U=null}t.innerHTML="";try{t.src=e.url}catch{t.src=""}if(!t.src){let n=document.createElement("source");n.src=e.url,n.type="video/mp2t",t.appendChild(n)}return t.load(),kn(t).then(()=>{x("[Player Engine] MPEG-TS nativo reproduzindo!","success"),et(),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}).catch(n=>{Ni(n)||(x(`[Player Fallback] Nativo falhou (${n&&n.message}). Tentando HLS...`,"warn"),Qt(e))}),!0}catch{return x("[Player Fallback] Nativo indisponível. Tentando HLS...","warn"),Qt(e),!0}}function Qt(e){if(!e||!e.url||!Bi(e,2))return;let t=e.url.replace(/\.ts($|\?)/i,".m3u8$1");x(`[Player Fallback] Rotacionando formato para HLS (.m3u8): ${t}`,"info"),wn(!1);let n=a("tvPlayer");if(n)if(window.Hls&&Hls.isSupported()){if(C){try{C.destroy()}catch{}C=null}C=new Hls(_i()),C.loadSource(t),C.attachMedia(n),C.on(Hls.Events.MANIFEST_PARSED,()=>{x("[Player Engine] Fallback HLS reproduzindo com sucesso!","success"),typeof In=="function"&&In(C);try{n.preservesPitch=!0,n.playbackRate=Dt}catch{}kn(n),et(),Oi(!0),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}),C.on(Hls.Events.ERROR,(o,i)=>{x(`[HLS Error] Tipo: ${i.type} | Detalhe: ${i.details}`,"error"),i.fatal&&_t("Erro HLS Fatal")})}else n.src=t,n.load(),kn(n).then(()=>{x("[Player Engine] Fallback HLS nativo reproduzindo!","success"),et(),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}).catch(o=>{x(`[Player Error] Falha no fallback: ${o&&o.message}`,"error"),_t("Falha no fallback")})}var Xo=Promise.resolve();function de(e,t){if(!e||!e.url)return;if(Pt)try{Pt.abort()}catch{}Pt=new AbortController;let n=Pt.signal;Xo=Xo.catch(()=>{}).then(async()=>{if(n.aborted)return;wn(!1);let o=a("tvPlayer");if(o)try{o.pause(),o.src="",o.removeAttribute("src"),o.innerHTML="",o.load()}catch{}if(await new Promise(d=>setTimeout(d,20)),n.aborted||(v=e,typeof oi=="function"&&oi(e),ss(e),Jr(e),ys(e),bs(e),As(t||e.url),!o))return;let i=localStorage.getItem("mk21_stream_format")||"auto";if(x(`[Player] Iniciando carregamento: "${e.name}" (Modo: ${S})`,"info"),x(`[Player] URL: ${e.url}`,"info"),x(`[Player] Formato preferido: ${i}`,"debug"),e.currentTime&&e.currentTime>5&&(e.contentType==="MOVIE"||e.contentType==="SERIES"||S==="MOVIE"||S==="SERIES")){let d=e.currentTime,l=function(){o.removeEventListener("loadedmetadata",l);try{o.currentTime=d,E("▶ Continuando em "+Sn(d))}catch{}};o.addEventListener("loadedmetadata",l)}a("epgStatus")&&(a("epgStatus").textContent="⏳ Sintonizando..."),a("channelOverlay")&&(Za()!=="off"?(a("channelOverlay").textContent="▶ "+e.name,a("channelOverlay").classList.remove("hidden")):a("channelOverlay").classList.add("hidden")),a("listItems").querySelectorAll(".list-item-btn").forEach(d=>{d.classList.remove("active");let l=(d.textContent||"").replace(/^[⭐★▶]\s*/,"");e.name&&l.indexOf(e.name)===0&&(d.classList.add("active"),lo(d))}),a("epgTitle").textContent=(z.has(e.url)?"⭐ ":"▶ ")+e.name,a("fsHudTitle")&&(a("fsHudTitle").textContent=(z.has(e.url)?"⭐ ":"")+e.name),a("epgGroup").textContent="Categoria: "+e.group,hs(e),ze(e),z.has(e.url)?typeof gt=="function"&&gt(!0):typeof gt=="function"&&gt(!1),E(e.name);let s=t||e.url;if(i==="hls"&&s.toLowerCase().includes(".ts")&&(s=s.replace(/\.ts($|\?)/i,".m3u8$1"),x(`[Player] Preferência HLS forçada: ${s}`,"info")),window.FBG2PlayerService&&window.FBG2PlayerService.tryPlay(e,s))return;let c=s.toLowerCase().includes(".m3u8"),u=s.toLowerCase().includes(".ts");if(c&&window.Hls&&Hls.isSupported())x("[Player Engine] Usando decodificador Hls.js Low-Latency...","info"),C=new Hls(Object.assign(_i(),{startLevel:-1})),C.loadSource(s),C.attachMedia(o),a("epgStatus")&&(a("epgStatus").textContent="⏳ CARREGANDO..."),C.on(Hls.Events.MANIFEST_PARSED,()=>{x("[Player Engine] Manifesto HLS decodificado com sucesso!","success"),typeof In=="function"&&In(C);try{o.preservesPitch=!0,o.webkitPreservesPitch=!0,o.playbackRate=Dt}catch{}kn(o),et(),typeof se=="function"&&se(!0),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}),C.on(Hls.Events.ERROR,(d,l)=>{if(x(`[HLS Error] Tipo: ${l.type} | Detalhe: ${l.details}`,"warn"),l.fatal)switch(l.type){case Hls.ErrorTypes.NETWORK_ERROR:x("[HLS Error] Recuperando erro de rede...","warn"),a("epgStatus")&&(a("epgStatus").textContent="🔄 RECONECTANDO..."),Xt("Reconectando rede..."),C.startLoad();break;case Hls.ErrorTypes.MEDIA_ERROR:x("[HLS Error] Recuperando decodificação de mídia...","warn"),a("epgStatus")&&(a("epgStatus").textContent="🔄 RECUPERANDO MÍDIA..."),Xt("Recuperando decodificação..."),C.recoverMediaError();break;default:x("[HLS Fatal] Erro irrecuperável HLS, tentando auto-reconexão...","error"),_t("Erro HLS Fatal");break}});else if(u&&window.mpegts&&mpegts.isSupported()&&!zn.has(e.url)){x("[Player Engine] Canal MPEG-TS identificado. Construindo mpegts.js player...","info");try{if(U){try{U.unload(),U.detachMediaElement(),U.destroy()}catch{}U=null}bn={url:e.url,at:0,stage:0},U=mpegts.createPlayer({type:"mse",isLive:S==="LIVE",url:s},Bs()),U.attachMediaElement(o),U.load(),U.play().then(()=>{x("[Player Engine] MPEG-TS reproduzindo!","success"),et(),typeof se=="function"&&se(!0),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}).catch(d=>{Ni(d)||(x(`[MPEGTS Error] ${d&&d.message}. Tentando MPEG-TS nativo...`,"warn"),Yo(e)||Qt(e))}),U.on(mpegts.Events.ERROR,(d,l,f)=>{let p=String(l||"")+" "+String(f&&(f.msg||f.reason)||"");/interrupt|abort/i.test(p)||(x(`[MPEGTS Error] ${d}: ${l}`,"warn"),/HttpStatus|NetworkError|NETWORK_/i.test(p)&&(zn.size>400&&zn.clear(),zn.add(e.url),x("[Player Engine] Fonte recusou o mpegts.js; usando decodificador nativo da TV.","warn")),Yo(e)||Qt(e))})}catch(d){x(`[MPEGTS Exception] ${d.message}. Fallback HLS...`,"warn"),Qt(e)}}else{x("[Player Engine] Usando decodificação nativa acelerada por hardware...","info"),o.innerHTML="",o.src=s;try{o.preservesPitch=!0,o.webkitPreservesPitch=!0,o.playbackRate=Dt}catch{}o.load(),kn(o).then(()=>{x("[Player Engine] Playback nativo iniciado com sucesso!","success"),et(),typeof se=="function"&&se(!0),a("epgStatus")&&(a("epgStatus").textContent="🔴 NO AR"),ze(e)}).catch(d=>{x(`[Player Fallback] Falha no playback nativo (${d&&d.message}). Tentando fallback HLS...`,"warn"),Qt(e)})}})}function ze(e){let t=document.querySelector(".badge-quality")||a("badgeStreamResolution");if(!t)return;let n=e||v,o=n&&n.name?n.name.toUpperCase():"",i="HD 720p";o.includes("4K")||o.includes("UHD")||o.includes("2160")?i="4K 2160p":o.includes("FHD")||o.includes("1080")?i="FHD 1080p":o.includes("HD")||o.includes("720")?i="HD 720p":(o.includes("SD")||o.includes("480"))&&(i="SD 480p");let r=a("tvPlayer");if(r&&r.videoHeight>0){let s=r.videoHeight,c=r.videoWidth;s>=2160||c>=3800?i="4K 2160p":s>=1080||c>=1900?i="FHD 1080p":s>=720||c>=1200?i="HD 720p":i=`SD ${s}p`}t.textContent=i}var Un=a("tvPlayer");Un&&(Un.addEventListener("loadedmetadata",()=>ze(v)),Un.addEventListener("resize",()=>ze(v)),Un.addEventListener("timeupdate",mt));function Sn(e){if(!e||!isFinite(e)||isNaN(e)||e<=0)return"00:00:00";let t=Math.floor(e),n=Math.floor(t/3600),o=Math.floor(t%3600/60),i=t%60,r=s=>(s<10?"0":"")+s;return n>0?`${r(n)}:${r(o)}:${r(i)}`:`00:${r(o)}:${r(i)}`}function mt(){let e=a("tvPlayer");if(!e)return;let t=S==="MOVIE"||S==="SERIES"||v&&v.contentType!=="LIVE",n=a("fsVodProgressBox"),o=a("vodTimeRow"),i=a("epgBarBox");if(!t){n&&n.classList.add("hidden"),o&&o.classList.add("hidden");return}n&&!a("fsHudBar").classList.contains("hidden")&&n.classList.remove("hidden"),o&&o.classList.remove("hidden");let r=window.FBG2PlayerService,s=!!(r&&r.isActive()),c=s?r.getCurrentTimeSec():e.currentTime||0,u=s?r.getDurationSec():e.duration||0,d=u>0?Math.min(100,Math.max(0,c/u*100)):0;a("fsProgressFill")&&(a("fsProgressFill").style.width=d+"%"),a("fsTimeCurrent")&&(a("fsTimeCurrent").textContent=Sn(c)),a("fsTimeDuration")&&(a("fsTimeDuration").textContent=Sn(u));let l=a("epgProgressBar")||document.querySelector(".epg-progress");l&&(l.style.width=d+"%"),a("vodTimeCurrent")&&(a("vodTimeCurrent").textContent=Sn(c)),a("vodTimeDuration")&&(a("vodTimeDuration").textContent=Sn(u))}function Oi(e){if(typeof se=="function"){se(!!e);return}let t=a("fsBtnPlay");t&&(t.textContent=e?"⏸️ Pausar":"▶️ Reproduzir")}function Za(){try{let e=localStorage.getItem("mk21_toast_mode");if(e==="off"||e==="player"||e==="all")return e;if(localStorage.getItem("mk21_show_toasts")==="true")return"all"}catch{}return"off"}function Ns(e){let t=e==="player"||e==="all"?e:"off";try{localStorage.setItem("mk21_toast_mode",t),localStorage.setItem("mk21_show_toasts",t==="off"?"false":"true")}catch{}if(t==="off"){let n=a("channelOverlay");n&&n.classList.add("hidden")}if(t!=="all"){let n=a("mk21-toast-banner");n&&n.classList.remove("visible")}return t}var Jo=null;function E(e){let t=Za();if(!Z||!Z.isLoggedIn||t==="off"){let i=a("mk21-toast-banner");i&&i.classList.remove("visible");let r=a("channelOverlay");r&&r.classList.add("hidden");return}let n=a("channelOverlay");if(n&&(n.textContent=e,n.classList.remove("hidden"),clearTimeout(n._timer),n._timer=setTimeout(()=>n.classList.add("hidden"),3500)),t!=="all"){let i=a("mk21-toast-banner");i&&i.classList.remove("visible");return}let o=a("mk21-toast-banner");o||(o=document.createElement("div"),o.id="mk21-toast-banner",document.body.appendChild(o)),o.textContent=e,o.classList.add("visible"),clearTimeout(Jo),Jo=setTimeout(()=>{o&&o.classList.remove("visible")},2500)}function Da(){if(!V||V.length===0)return;let t=(V.findIndex(n=>v&&n.url===v.url)+1)%V.length;de(V[t]),E("▲ Próximo: "+V[t].name)}function Fa(){if(!V||V.length===0)return;let t=(V.findIndex(n=>v&&n.url===v.url)-1+V.length)%V.length;de(V[t]),E("▼ Anterior: "+V[t].name)}function le(){let e=a("videoContainer");return!!(e&&e.classList.contains("is-fullscreen"))||!!(document.fullscreenElement||document.webkitFullscreenElement)}function ea(e){let t=a("fsHudBar");if(t)if(e){t.classList.remove("hidden"),v&&a("fsHudTitle")&&(a("fsHudTitle").textContent=(z.has(v.url)?"⭐ ":"")+v.name);let n=a("tvPlayer");a("fsBtnPlay")&&(a("fsBtnPlay").textContent=n&&n.paused?"▶️ Reproduzir":"⏸️ Pausar"),a("fsBtnSpeed")&&(a("fsBtnSpeed").textContent="⚡ "+Dt+"x"),a("fsBtnFav")&&v&&(a("fsBtnFav").textContent=z.has(v.url)?"★ Favoritado":"⭐ Favoritar");let o=S==="MOVIE"||S==="SERIES"||v&&v.contentType!=="LIVE";a("fsVodProgressBox")&&(o?a("fsVodProgressBox").classList.remove("hidden"):a("fsVodProgressBox").classList.add("hidden")),a("fsEpgOverlayBox")&&(o?a("fsEpgOverlayBox").classList.add("hidden"):a("fsEpgOverlayBox").classList.remove("hidden")),mt()}else{let n=document.activeElement;if(n&&t.contains(n)){let o=a("videoContainer")||document.body;try{o&&typeof o.focus=="function"&&o.focus()}catch{}}t.classList.add("hidden")}}function ye(e,t){if(!e)return;let n=a("videoContainer");t&&le()&&n?e.parentElement!==n&&n.appendChild(e):(!t||!le())&&e.parentElement!==document.body&&document.body.appendChild(e)}function cn(e){let t=a("videoContainer"),n=a("tvPlayer");if(!t)return;let o=le();if(typeof e=="boolean"?e:!o){t.classList.add("is-fullscreen"),document.body.classList.add("is-fullscreen");let r=a("remoteColorBar");r&&(r.classList.add("hidden"),r.style.setProperty("display","none","important")),A="player",ea(!0),oe(),x("[Player] Modo Tela Cheia ATIVADO","info");try{let s=document.documentElement;s.requestFullscreen?s.requestFullscreen().catch(()=>{}):s.webkitRequestFullscreen?s.webkitRequestFullscreen():t.requestFullscreen?t.requestFullscreen().catch(()=>{}):t.webkitRequestFullscreen&&t.webkitRequestFullscreen()}catch(s){x("[Player] Fullscreen API fallback ativo (CSS is-fullscreen aplicado): "+s.message,"debug")}setTimeout(function(){a("fsBtnPlay")&&a("fsBtnPlay").focus()},200)}else{["modalPlayerOptions","modalAudioSubs","modalServerPicker","modalPin","modalSeriesEpisodes","modalDevLogs","modalStreamDiagnostics"].forEach(s=>{let c=a(s);c&&c.parentElement!==document.body&&document.body.appendChild(c)}),t.classList.remove("is-fullscreen"),document.body.classList.remove("is-fullscreen");let r=a("remoteColorBar");r&&(r.classList.remove("hidden"),r.style.removeProperty("display")),ea(!1),x("[Player] Modo Tela Cheia DESATIVADO","info");try{document.exitFullscreen?document.exitFullscreen().catch(()=>{}):document.webkitExitFullscreen&&document.webkitExitFullscreen()}catch{}A="channels",Se()}}function Os(){a("fsBtnPrev")&&(a("fsBtnPrev").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),S==="LIVE"?Fa():(Jt(-1e4),E("⏪ -10s")),oe()}),a("fsBtnNext")&&(a("fsBtnNext").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),S==="LIVE"?Da():(Jt(1e4),E("⏩ +10s")),oe()}),a("fsBtnPlay")&&(a("fsBtnPlay").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),aa(),oe()}),a("fsBtnSpeed")&&(a("fsBtnSpeed").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),Fi(),oe()}),a("fsBtnAspect")&&(a("fsBtnAspect").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),ki(),oe()}),a("fsBtnFav")&&(a("fsBtnFav").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),v&&ra(v.url),oe()}),a("fsBtnQuality")&&(a("fsBtnQuality").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof ia=="function"&&ia(!0)}),a("fsBtnBuffer")&&(a("fsBtnBuffer").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),Di(),oe()}),a("fsBtnServer")&&(a("fsBtnServer").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof ot=="function"&&ot()}),a("fsBtnAudio")&&(a("fsBtnAudio").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof Be=="function"&&Be()}),a("fsBtnBack")&&(a("fsBtnBack").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),cn(!1)});let e=a("videoContainer");if(e&&!e.dataset.mk21FsActivityBound){e.dataset.mk21FsActivityBound="1";let t=0,n=function(){if(!le())return;let o=Date.now();o-t<250||(t=o,oe())};e.addEventListener("mousemove",n,{passive:!0}),e.addEventListener("pointermove",n,{passive:!0}),e.addEventListener("click",function(o){if(!le())return;let i=a("fsHudBar");if(i&&o.target&&i.contains(o.target)){oe();return}oe()})}}Os();function se(e){let t=a("btnPlayPause");t&&(t.title=e?"Pausar":"Reproduzir",t.innerHTML=e?'<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>':'<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>');let n=a("fsBtnPlay");n&&(n.textContent=e?"⏸️ Pausar":"▶️ Reproduzir")}function gt(e){let t=a("btnFavorite");t&&(t.title=e?"Remover dos favoritos":"Adicionar aos favoritos",t.innerHTML=e?'<svg style="width:22px;height:22px;fill:#ffd54f;" viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>':'<svg style="width:22px;height:22px;fill:rgba(255,255,255,0.7);" viewBox="0 0 24 24"><path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"/></svg>');let n=a("fsBtnFav");n&&(n.textContent=e?"★ Favoritado":"⭐ Favoritar")}function Di(){let e=["auto","fast","stable"],t=vt(),n=e[(e.indexOf(t)+1)%e.length];localStorage.setItem("mk21_buffer_mode",n);let o=Ge[n],i=window.FBG2PlayerService;i&&typeof i.setBufferMs=="function"&&o&&o.hls&&i.setBufferMs((o.hls.maxBufferLength||4)*1e3);let r="⚡ Fluidez: "+(o?o.label:n);typeof showToast=="function"?showToast(r):typeof E=="function"&&E(r)}a("videoContainer").ondblclick=()=>cn();a("btnFullscreen")&&(a("btnFullscreen").onclick=()=>cn());function Ds(e){let t=a("tvPlayer");if(!t)return;Dt=e;try{t.playbackRate=e,t.preservesPitch=!0,t.mozPreservesPitch=!0,t.webkitPreservesPitch=!0,t.defaultPlaybackRate!==void 0&&(t.defaultPlaybackRate=e)}catch{}let n="⚡ "+e+"x";a("btnSpeed")&&(a("btnSpeed").textContent=n),a("fsBtnSpeed")&&(a("fsBtnSpeed").textContent=n)}function Fi(){let e=(Ea.indexOf(Dt)+1)%Ea.length;Ds(Ea[e]),E("⚡ Velocidade: "+Dt+"x (áudio normal)")}a("btnPlayPause")&&(a("btnPlayPause").onclick=aa);a("btnFavorite")&&(a("btnFavorite").onclick=()=>{v&&ra(v.url)});a("btnSpeed")&&(a("btnSpeed").onclick=Fi);a("btnQuality")&&(a("btnQuality").onclick=()=>{typeof ia=="function"&&ia(!1)});a("btnBufferMode")&&(a("btnBufferMode").onclick=Di);a("btnQuickServer")&&(a("btnQuickServer").onclick=()=>{typeof ot=="function"&&ot()});function Be(){let e=a("modalAudioSubs");if(!e)return;ye(e,!0),e.classList.remove("hidden"),A="modalAudioSubs";let t=a("audioTracksList"),n=a("subtitleTracksList");t.innerHTML="",n.innerHTML="";let o=a("tvPlayer"),i=!1,r=window.FBG2PlayerService;if(r&&r.isActive()&&r.getAudioTracks().length>0){i=!0;let l=r.getActiveAudioId();r.getAudioTracks().forEach(f=>{let p=document.createElement("button");p.className="track-item-btn"+(f.id===l?" active":""),p.setAttribute("tabindex","0");let m=f.name||f.language||"Faixa "+((f.index||0)+1);p.textContent="🔊 "+m+(f.id===l?" [Ativo]":""),p.onclick=()=>{r.setAudioTrack(f.id),E("Áudio: "+m),Be()},t.appendChild(p)})}else if(C&&C.audioTracks&&C.audioTracks.length>0)i=!0,C.audioTracks.forEach((l,f)=>{let p=document.createElement("button");p.className="track-item-btn"+(C.audioTrack===f?" active":""),p.setAttribute("tabindex","0"),p.textContent=`🔊 ${l.name||l.lang||"Faixa "+(f+1)}`+(C.audioTrack===f?" [Ativo]":""),p.onclick=()=>{C.audioTrack=f,E("Áudio: "+(l.name||l.lang||"Faixa "+(f+1))),Be()},t.appendChild(p)});else if(o&&o.audioTracks&&o.audioTracks.length>0){i=!0;for(let l=0;l<o.audioTracks.length;l++){let f=o.audioTracks[l],p=document.createElement("button");p.className="track-item-btn"+(f.enabled?" active":""),p.setAttribute("tabindex","0"),p.textContent=`🔊 ${f.label||f.language||"Faixa "+(l+1)}`+(f.enabled?" [Ativo]":""),p.onclick=()=>{for(let m=0;m<o.audioTracks.length;m++)o.audioTracks[m].enabled=m===l;E("Áudio: "+(f.label||"Faixa "+(l+1))),Be()},t.appendChild(p)}}if(!i){let l=document.createElement("p");l.style.color="#94a3b8",l.style.fontSize="15px",l.textContent="🔊 Áudio Principal Original da Emissora (Faixa única)",t.appendChild(l)}let s=document.createElement("button"),c=!C||C.subtitleTrack===-1;s.className="track-item-btn"+(c?" active":""),s.setAttribute("tabindex","0"),s.textContent="❌ Desativar Legendas",s.onclick=()=>{if(C&&(C.subtitleTrack=-1),o&&o.textTracks)for(let l=0;l<o.textTracks.length;l++)o.textTracks[l].mode="disabled";E("Legendas Desativadas"),Be()},n.appendChild(s);let u=!1;if(C&&C.subtitleTracks&&C.subtitleTracks.length>0)u=!0,C.subtitleTracks.forEach((l,f)=>{let p=document.createElement("button");p.className="track-item-btn"+(C.subtitleTrack===f?" active":""),p.setAttribute("tabindex","0"),p.textContent=`💬 ${l.name||l.lang||"Legenda "+(f+1)}`+(C.subtitleTrack===f?" [Ativo]":""),p.onclick=()=>{C.subtitleTrack=f,E("Legenda: "+(l.name||l.lang||"Faixa "+(f+1))),Be()},n.appendChild(p)});else if(o&&o.textTracks&&o.textTracks.length>0){u=!0;for(let l=0;l<o.textTracks.length;l++){let f=o.textTracks[l],p=document.createElement("button");p.className="track-item-btn"+(f.mode==="showing"?" active":""),p.setAttribute("tabindex","0"),p.textContent=`💬 ${f.label||f.language||"Legenda "+(l+1)}`+(f.mode==="showing"?" [Ativo]":""),p.onclick=()=>{for(let m=0;m<o.textTracks.length;m++)o.textTracks[m].mode=m===l?"showing":"disabled";E("Legenda: "+(f.label||"Faixa "+(l+1))),Be()},n.appendChild(p)}}if(!u){let l=document.createElement("p");l.style.color="#94a3b8",l.style.fontSize="14px",l.style.marginTop="8px",l.textContent="Legendas embutidas não disponíveis neste fluxo.",n.appendChild(l)}let d=e.querySelector(".track-item-btn");d&&d.focus()}a("btnCloseAudioSubs").onclick=()=>{let e=a("modalAudioSubs");e&&(e.classList.add("hidden"),ye(e,!1));let t=le();A=t?"player":"channels";let n=a(t?"fsBtnAudio":"btnAudioTrack");n&&n.focus&&n.focus(),t&&oe()};a("btnAudioTrack")&&(a("btnAudioTrack").onclick=Be);a("btnSubtitles")&&(a("btnSubtitles").onclick=Be);a("tabLive").onclick=()=>qe("LIVE");a("tabMovies").onclick=()=>qe("MOVIE");a("tabSeries").onclick=()=>qe("SERIES");a("tabContinue")&&(a("tabContinue").onclick=()=>qe("CONTINUE"));a("tabFavs").onclick=()=>qe("FAVORITES");a("tabSettings").onclick=()=>qe("SETTINGS");function qe(e){S=e;try{let o={LIVE:"📺 Ao Vivo",MOVIE:"🎬 Filmes",SERIES:"🍿 Séries",CONTINUE:"▶ Continuar",FAVORITES:"⭐ Favoritos",SETTINGS:"⚙️ Config"};o[e]&&E(o[e])}catch{}["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabSettings"].forEach(o=>{let i=a(o);i&&i.classList.remove("active")});let t=e==="LIVE"?"tabLive":e==="MOVIE"?"tabMovies":e==="SERIES"?"tabSeries":e==="CONTINUE"?"tabContinue":e==="FAVORITES"?"tabFavs":"tabSettings";if(a(t)&&a(t).classList.add("active"),e==="SETTINGS"){a("sectionUnified").classList.add("hidden"),a("sectionSettings").classList.remove("hidden"),Vi("info"),A="settings",setTimeout(()=>{let o=document.querySelector(".cfg-menu-item.active")||a("cfgBtnInfo");if(o)try{o.focus()}catch{}},40);return}if(typeof It=="function")try{It(!0)}catch{}let n=a(t)||a("tabLive");if(n)try{n.focus()}catch{}a("sectionSettings").classList.add("hidden"),a("sectionUnified").classList.remove("hidden"),Q(),te(),Ie("ALL"),A="channels",e==="FAVORITES"&&qr(),setTimeout(()=>{["#listCategories","#listItems"].forEach(s=>{let c=document.querySelector(s);c&&typeof q=="function"&&q(c)});let o=a("listItems")?a("listItems").querySelector(".list-item-btn:not([disabled])"):null,i=a("listCategories")?a("listCategories").querySelector(".cat-item-btn.active"):null,r=o||i||n;r&&Oe(r)},40)}var Va=[["cfgBtnInfo","⚙️ Informação geral"],["cfgBtnFonte","🔤 Tamanho da fonte"],["cfgBtnFiltro","📦 Filtrar conteúdo"],["cfgBtnSpeedTest","🚀 Teste de velocidade"],["cfgBtnLimpar","🗑️ Limpar armazenamento"],["cfgBtnTempo","⏱️ Configurações de tempo"],["cfgBtnCategorias","📁 Gerenciar categorias"],["cfgBtnFluxo","📺 Alterar formato de fluxo"],["cfgBtnQualidade","📡 Servidor / Fonte"],["cfgBtnQualidadeVideo","🎞️ Qualidade de vídeo"],["cfgBtnBuffer","⚡ Buffer e fluidez"],["cfgBtnAdulto","🔞 Restrição adulto"],["cfgBtnNotificacoes","🔔 Avisos na tela"],["cfgBtnPin","🔒 Alterar código pin"],["cfgBtnLogout","🚪 Sair da Conta (Logout)"]],Qo=Va.map(e=>e[0]);function Fs(){let e=document.querySelector(".settings-left-menu");if(!e)return;let t=Va.map(r=>r[0]).join("|");if(e.dataset.mk21Menu===t)return;let n=e.querySelector(".cfg-tmdb-toggle"),o=e.querySelector(".cfg-menu-item.active"),i=o?o.id:"cfgBtnInfo";if(e.innerHTML="",Va.forEach(r=>{let s=document.createElement("button");s.id=r[0],s.type="button",s.className="cfg-menu-item",s.tabIndex=0,s.textContent=r[1],r[0]===i&&s.classList.add("active"),e.appendChild(s)}),n)e.appendChild(n);else{let r=document.createElement("div");r.className="cfg-tmdb-toggle",r.innerHTML="<span>🎬 Usar API TMDB</span>";let s=document.createElement("input");s.type="checkbox",s.id="cfgToggleTmdb",s.checked=!0,s.style.width="22px",s.style.height="22px",r.appendChild(s),e.appendChild(r)}e.dataset.mk21Menu=t}Fs();Qo.forEach(e=>{let t=a(e);t&&(t.onclick=()=>{Qo.forEach(n=>{let o=a(n);o&&o.classList.remove("active")}),t.classList.add("active"),Vi(e.replace("cfgBtn","").toLowerCase())})});function Vi(e){if(e!=="speedtest"&&window.__mk21SpeedTestController){try{window.__mk21SpeedTestController.abort()}catch{}window.__mk21SpeedTestController=null}if(e==="logout"){sn();return}let t=a("settingsDetailBox");if(e==="info"){let n=Pn(),o=n&&n.user?n.user:"Sessão Offline / Visitante";t.innerHTML=`
+    `}}var go=null,ro=-1,Te=0,Ge=!1,tt=0,Yo=5;function sn(e){let t=r("playerReconnectBadge");if(!t){t=document.createElement("div"),t.id="playerReconnectBadge",t.className="player-reconnect-badge";let n=r("videoContainer")||r("tvPlayer")&&r("tvPlayer").parentElement;n&&n.appendChild(t)}t.textContent=e,t.classList.remove("hidden"),t.style.display="block"}function at(){let e=r("playerReconnectBadge");e&&(e.classList.add("hidden"),e.style.display="none")}function Xl(){go&&(clearInterval(go),go=null)}function Ht(e){if(Ge||!v)return;if(Ge=!0,tt++,tt>Yo){if(console.error("[SmartPlayer Watchdog] Limite máximo de reconexão atingido:",e),Ge=!1,typeof jr=="function"){jr().then(n=>{n||(sn("Falha de conexão. Tente outro canal."),r("epgStatus")&&(r("epgStatus").textContent="⚠️ FORA DO AR"))}).catch(()=>{sn("Falha de conexão. Tente outro canal."),r("epgStatus")&&(r("epgStatus").textContent="⚠️ FORA DO AR")});return}sn("Falha de conexão. Tente outro canal."),r("epgStatus")&&(r("epgStatus").textContent="⚠️ FORA DO AR");return}let t=Math.min(1e3*Math.pow(1.5,tt-1),6e3);sn(`Reconectando (${tt}/${Yo})...`),r("epgStatus")&&(r("epgStatus").textContent=`🔄 RECONECTANDO (${tt}/${Yo})...`),setTimeout(()=>{if(!v){Ge=!1;return}if(console.log(`[SmartPlayer Watchdog] Executando reconexão tentativa ${tt} para: ${v.name}`),k)try{k.recoverMediaError()}catch{typeof fe=="function"&&fe(v)}else typeof fe=="function"&&fe(v);Ge=!1},t)}var la="",_r=0,Jo=-1,Rt=0,Qo=0;function Or(){if(!v)return!1;let e=v.contentType||v.type||S;if(e==="MOVIE"||e==="SERIES")return!0;let t=String(v.url||"").toLowerCase();return t.includes("/movie/")||t.includes("/series/")||t.includes(".mp4")||t.includes(".mkv")||t.includes(".avi")}function Yl(e){Xl();let t=String(e||v&&v.url||"");la!==t&&(la=t,ro=-1,_r=0,Rt=Date.now(),Qo=0,Jo=-1,Te=0,tt=0,Ge=!1,at());let n=r("tvPlayer");if(n){if(!n._mk21EventsBound){n._mk21EventsBound=!0;let o=()=>{_r=Date.now(),Te<1&&(Te=1)};n.addEventListener("waiting",o),n.addEventListener("stalled",o),n.addEventListener("seeking",o),n.addEventListener("playing",()=>{Te=0,tt=0,Ge=!1,at()}),n.addEventListener("progress",()=>{Rt=Date.now()}),n.addEventListener("error",a=>{!n.currentSrc&&!n.src||(console.warn("[SmartPlayer] Erro nativo no vídeo:",a),Or()||Ht("Erro nativo de reprodução"))})}go=setInterval(()=>{let o=window.FBG2PlayerService;if(o&&o.isActive()){if(Ge||!v)return;let d=o.player&&o.player.getState?o.player.getState():"playing";if(d==="paused"||d==="buffering"||d==="preparing")return;let l=o.getCurrentTimeSec();if(Math.abs(l-Jo)>=.25){Jo=l,Te=0,Rt=Date.now();return}if(Date.now()-Rt<3e3)return;Te++,Te>=Br()&&(console.warn("[SmartPlayer Watchdog] Travamento real detectado (factory)."),Te=0,Ht("Stream congelado (factory)"));return}if(!n||n.paused||Ge||!v)return;let s=Or()?30:6,i=n.currentTime;if(Math.abs(i-ro)>=.25){Te=0,ro=i;return}let c=0;try{n.buffered&&n.buffered.length&&(c=n.buffered.end(n.buffered.length-1))}catch{}if(c>Qo+.05&&(Qo=c,Rt=Date.now()),Date.now()-Rt<3e3&&Te<3){ro=i;return}Te++,Te>=Br()&&(console.warn("[SmartPlayer Watchdog] Travamento real detectado no stream."),Te=0,Ht("Stream congelado"))},1e3)}}var zt=[],Jl=400,gt=!1,Ql={error:"#ef4444",warn:"#eab308",success:"#22c55e",debug:"#94a3b8",info:"#93c5fd"};function xa(){return r("devLogsContainer")||document.querySelector(".devlogs-screen")}function Ss(e){let t=Ql[e.type]||"#cbd5e1",n=document.createElement("div");return n.className="log-line",n.innerHTML='<span class="log-time">['+e.time+']</span> <span class="log-type" style="color:'+t+'">'+String(e.type||"info").toUpperCase()+'</span><span style="color:'+t+'">'+q(e.msg)+"</span>",n}function Ca(){let e=r("devLogsCountBadge");e&&(e.textContent=zt.length+" entradas")}function Zl(){let e=xa();e&&(e.replaceChildren(),zt.forEach(t=>e.appendChild(Ss(t))),e.scrollTop=e.scrollHeight,Ca())}function x(e,t="info"){let o={time:new Date().toLocaleTimeString("pt-BR",{hour12:!1}),msg:e,type:t};zt.push(o),zt.length>Jl&&zt.shift();try{let a=xa();a&&(a.querySelector('div[style*="64748b"]')&&a.replaceChildren(),a.appendChild(Ss(o)),gt||(a.scrollTop=a.scrollHeight)),Ca()}catch{}t==="error"?console.error("[MK21-TV]",e):t==="warn"?console.warn("[MK21-TV]",e):console.log("[MK21-TV]",e)}function q(e){return String(e||"").replace(/[&<>"']/g,t=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[t])}function Es(){let e=r("btnCloseDevLogs");e&&(e.onclick=()=>{let a=r("modalDevLogs");a&&!a.classList.contains("hidden")&&La()});let t=r("btnTogglePauseDevLogs");t&&(t.textContent=gt?"▶️ Retomar rolagem":"⏸ Pausar rolagem",t.classList.toggle("primary",gt),t.onclick=()=>{gt=!gt;let a=r("btnTogglePauseDevLogs");a&&(a.textContent=gt?"▶️ Retomar rolagem":"⏸ Pausar rolagem",a.classList.toggle("primary",gt));let s=r("devLogsHint");s&&(s.textContent=gt?"Rolagem automática pausada: a tela fica parada para você ler (ou fotografar) sem os logs fugirem.":"Use o botão de rolagem do Magic Remote (ou CH + / CH -) para percorrer o histórico.")});let n=r("btnClearDevLogs");n&&(n.onclick=()=>{zt=[];let a=xa();a&&a.replaceChildren(),Ca(),x("[Logs] Histórico limpo.","info")});let o=r("btnExportDevLogs");o&&(o.onclick=()=>{try{let a=["MK21 Smart TV - Logs de diagnostico","Data: "+new Date().toISOString(),"Versao: "+Ct,"Tela: "+innerWidth+"x"+innerHeight,"Credenciais e caminhos autenticados foram mascarados.","",...zt.map(c=>"["+c.time+"] ["+String(c.type).toUpperCase()+"] "+Cs(c.msg))].join(`
+`);localStorage.setItem("mk21_last_exported_logs",a);let s=new Blob([a],{type:"text/plain;charset=utf-8"}),i=document.createElement("a");i.href=URL.createObjectURL(s),i.download="mk21-tv-logs-"+new Date().toISOString().replace(/[:.]/g,"-")+".txt",document.body.appendChild(i),i.click(),document.body.removeChild(i),x("[Logs] Arquivo de log exportado e salvo com sucesso!","success"),E("✅ Logs exportados com sucesso!")}catch(a){x("[Logs] Erro ao exportar logs: "+a.message,"error")}})}function xs(){let e=r("modalDevLogs");return e||(e=document.createElement("div"),e.id="modalDevLogs",document.body.appendChild(e)),e.dataset.mk21LogsReady!=="1"&&(e.className="app-modal devlogs-modal hidden",e.innerHTML='<div class="modal-content devlogs-card"><div class="devlogs-head"><span class="devlogs-title">🖥️ Diagnóstico em tempo real</span><div class="devlogs-actions"><button type="button" id="btnTogglePauseDevLogs" class="ctrl-btn" tabindex="0">⏸ Pausar rolagem</button><button type="button" id="btnClearDevLogs" class="ctrl-btn" tabindex="0">🗑️ Limpar</button><button type="button" id="btnExportDevLogs" class="ctrl-btn primary" tabindex="0">💾 Salvar arquivo</button><button type="button" id="btnCloseDevLogs" class="ctrl-btn" tabindex="0">✕ Fechar</button></div></div><div class="devlogs-info" id="devLogsInfo"></div><div id="devLogsContainer" class="devlogs-screen scrollable-container" tabindex="0"></div><div class="devlogs-foot"><span id="devLogsHint">Use o botão de rolagem do Magic Remote (ou CH + / CH -) para percorrer o histórico.</span><span id="devLogsCountBadge">0 entradas</span></div></div>',e.dataset.mk21LogsReady="1"),Es(),e}function ec(){let e=r("devLogsInfo");if(!e)return;let t="--";try{t=Z((L[N]||{}).name||"--")}catch{}let n=r("tvPlayer"),o="--";try{if(n&&n.buffered&&n.buffered.length>0){let u=n.buffered.end(n.buffered.length-1);o=Math.max(0,Math.round(u-(n.currentTime||0)))+"s"}}catch{}let a=typeof k!="undefined"&&k?"hls.js":typeof W!="undefined"&&W?"mpegts.js":"nativo",s=n&&n.videoHeight?n.videoWidth+"x"+n.videoHeight:"--",i="--";try{i=Ct}catch{}let c=[["Versão","v"+i],["Fonte",t],["Canal",v?v.name:"nenhum"],["Engine",a],["Imagem",s],["Buffer",o],["Rede",navigator.onLine?"online":"offline"],["Tela",innerWidth+"x"+innerHeight],["Buffer pref",(Xe[Lt()]||{}).label||"--"]];e.innerHTML=c.map(u=>"<span>"+u[0]+": <b>"+q(String(u[1]))+"</b></span>").join("")}function La(){let e=xs();if(yt=null,e.classList.contains("hidden"))e.classList.remove("hidden"),T="modalDevLogs",ec(),Zl(),window.__mk21WebOSScrollFixActive&&r("devLogsContainer")&&zn(r("devLogsContainer")),x("[UI] Console de logs aberto","info"),setTimeout(()=>{let n=r("btnTogglePauseDevLogs")||r("btnCloseDevLogs");n&&n.focus()},30);else{e.classList.add("hidden"),T=S==="SETTINGS"?"settings":"channels";let n=r("btnHeaderLogs");n&&De(n)?n.focus():Le()}}function tc(){xs(),r("btnHeaderLogs")&&(r("btnHeaderLogs").onclick=La),Es()}function Cs(e){return String(e).replace(/(https?:\/\/)[^/@\s:]+:[^/@\s]+@/gi,"$1[REDACTED]@").replace(/([?&](?:username|user|password|pass|token|auth|api[_-]?key)=)[^&#\s]*/gi,"$1[REDACTED]").replace(/\/(live|movie|series)\/[^/\s?]+\/[^/\s?]+/gi,"/$1/[REDACTED]/[REDACTED]")}var W=null,Ft=null;function Vn(e){if(!e)return Promise.resolve();try{let t=e.play();if(t&&typeof t.then=="function")return t.catch(n=>{if(n&&(n.name==="AbortError"||n.message&&n.message.includes("interrupted"))){console.debug("[Player] Play request interrupted cleanly by zapping.");return}console.warn("[Player Play Error]:",n&&n.message)})}catch(t){console.warn("[Player Exception]:",t)}return Promise.resolve()}function $n(e=!0){if(window.FBG2PlayerService&&window.FBG2PlayerService.stop(),e&&Ft){try{Ft.abort()}catch{}Ft=null}if(W){try{W.pause(),W.unload(),W.detachMediaElement(),W.destroy()}catch{}W=null}if(k){try{k.destroy()}catch{}k=null}let t=r("tvPlayer");if(t)try{t.pause(),t.src="",t.removeAttribute("src"),t.innerHTML="",t.load()}catch{}}var Xe={auto:{label:"Automático (recomendado)",hint:"Equilíbrio entre zapping rápido e estabilidade.",hls:{maxBufferLength:20,maxMaxBufferLength:40,liveSyncDurationCount:3,backBufferLength:10,maxBufferSize:20*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:30,stashInitialSize:256*1024},stallTolerance:10},fast:{label:"Rápido / Zapping veloz",hint:"Troca de canal quase instantânea. Usa internet boa.",hls:{maxBufferLength:10,maxMaxBufferLength:20,liveSyncDurationCount:2,backBufferLength:4,maxBufferSize:12*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:20,stashInitialSize:96*1024},stallTolerance:6},stable:{label:"Estável / Internet fraca",hint:"Buffer maior: demora um pouco a abrir, mas quase não trava.",hls:{maxBufferLength:30,maxMaxBufferLength:60,liveSyncDurationCount:4,backBufferLength:20,maxBufferSize:30*1e3*1e3},mpegts:{liveBufferLatencyChasing:!1,liveBufferLatencyMaxLatency:45,stashInitialSize:512*1024},stallTolerance:16}};function ln(e,t){let n=window.FBG2PlayerService;if(n&&n.isActive()){n.seekMs(Math.round(n.getCurrentTimeSec()*1e3)+e);return}let o=t||r("tvPlayer");if(!o)return;let a=Math.max(0,(o.currentTime||0)*1e3+e)/1e3;o.currentTime=o.duration?Math.min(o.duration,a):a}function Br(){let e=Xe[Lt()];return e&&e.stallTolerance||10}function Lt(){let e=localStorage.getItem("mk21_buffer_mode")||"auto";return Xe[e]?e:"auto"}function Ls(){let e=Xe[Lt()];return Object.assign({enableWorker:!0,lowLatencyMode:!1,startFragPrefetch:!0,maxBufferSize:20*1e3*1e3,maxStarvationDelay:6,maxLoadingDelay:6,abrEwmaDefaultEstimate:12e5,fragLoadingTimeOut:2e4,manifestLoadingTimeOut:15e3,levelLoadingTimeOut:15e3,fragLoadingMaxRetry:5,manifestLoadingMaxRetry:4,levelLoadingMaxRetry:4,fragLoadingRetryDelay:800,manifestLoadingRetryDelay:1e3,fragLoadingMaxRetryTimeout:12e3,levelLoadingMaxRetryTimeout:8e3},e.hls)}function nc(){let e=Xe[Lt()];return Object.assign({enableWorker:!0,lazyLoad:!1,autoCleanupSourceBuffer:!0},e.mpegts)}var Pn={url:"",at:0,stage:0},so=new Set;function ws(e,t){if(!e||!e.url)return!1;let n=Date.now();return Pn.url===e.url&&n-Pn.at<6e3&&Pn.stage>=t?!1:(Pn={url:e.url,at:n,stage:t},!0)}function ks(e){let t=e&&(e.message||e.msg||"")||"";return(e&&e.name||"")==="AbortError"||/interrupted|abort/i.test(t)}function Dr(e){if(!e||!e.url||!ws(e,1))return!1;let t=r("tvPlayer");if(!t)return!1;x("[Player Fallback] Tentando MPEG-TS nativo no hardware da TV...","info");try{if(k&&(k.destroy(),k=null),W){try{W.unload(),W.detachMediaElement(),W.destroy()}catch{}W=null}t.innerHTML="";try{t.src=e.url}catch{t.src=""}if(!t.src){let n=document.createElement("source");n.src=e.url,n.type="video/mp2t",t.appendChild(n)}return t.load(),Vn(t).then(()=>{x("[Player Engine] MPEG-TS nativo reproduzindo!","success"),at(),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}).catch(n=>{ks(n)||(x(`[Player Fallback] Nativo falhou (${n&&n.message}). Tentando HLS...`,"warn"),cn(e))}),!0}catch{return x("[Player Fallback] Nativo indisponível. Tentando HLS...","warn"),cn(e),!0}}function cn(e){if(!e||!e.url||!ws(e,2))return;let t=e.url.replace(/\.ts($|\?)/i,".m3u8$1");x(`[Player Fallback] Rotacionando formato para HLS (.m3u8): ${t}`,"info"),$n(!1);let n=r("tvPlayer");if(n)if(window.Hls&&Hls.isSupported()){if(k){try{k.destroy()}catch{}k=null}k=new Hls(Ls()),k.loadSource(t),k.attachMedia(n),k.on(Hls.Events.MANIFEST_PARSED,()=>{x("[Player Engine] Fallback HLS reproduzindo com sucesso!","success"),typeof Un=="function"&&Un(k);try{n.preservesPitch=!0,n.playbackRate=Gt}catch{}Vn(n),at(),Ts(!0),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}),k.on(Hls.Events.ERROR,(o,a)=>{x(`[HLS Error] Tipo: ${a.type} | Detalhe: ${a.details}`,"error"),a.fatal&&Ht("Erro HLS Fatal")})}else n.src=t,n.load(),Vn(n).then(()=>{x("[Player Engine] Fallback HLS nativo reproduzindo!","success"),at(),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}).catch(o=>{x(`[Player Error] Falha no fallback: ${o&&o.message}`,"error"),Ht("Falha no fallback")})}var Fr=Promise.resolve();function fe(e,t){if(!e||!e.url)return;if(Ft)try{Ft.abort()}catch{}Ft=new AbortController;let n=Ft.signal;Fr=Fr.catch(()=>{}).then(async()=>{if(n.aborted)return;$n(!1);let o=r("tvPlayer");if(o)try{o.pause(),o.src="",o.removeAttribute("src"),o.innerHTML="",o.load()}catch{}if(await new Promise(d=>setTimeout(d,20)),n.aborted||(v=e,typeof Wr=="function"&&Wr(e),Ml(e),Sl(e),$l(e),Hl(e),Yl(t||e.url),!o))return;let a=localStorage.getItem("mk21_stream_format")||"auto";if(x(`[Player] Iniciando carregamento: "${e.name}" (Modo: ${S})`,"info"),x(`[Player] URL: ${e.url}`,"info"),x(`[Player] Formato preferido: ${a}`,"debug"),e.currentTime&&e.currentTime>5&&(e.contentType==="MOVIE"||e.contentType==="SERIES"||S==="MOVIE"||S==="SERIES")){let d=e.currentTime,l=function(){o.removeEventListener("loadedmetadata",l);try{o.currentTime=d,E("▶ Continuando em "+Rn(d))}catch{}};o.addEventListener("loadedmetadata",l)}r("epgStatus")&&(r("epgStatus").textContent="⏳ Sintonizando..."),r("channelOverlay")&&(wa()!=="off"?(r("channelOverlay").textContent="▶ "+e.name,r("channelOverlay").classList.remove("hidden")):r("channelOverlay").classList.add("hidden")),r("listItems").querySelectorAll(".list-item-btn").forEach(d=>{d.classList.remove("active");let l=(d.textContent||"").replace(/^[⭐★▶]\s*/,"");e.name&&l.indexOf(e.name)===0&&(d.classList.add("active"),_a(d))}),r("epgTitle").textContent=(G.has(e.url)?"⭐ ":"▶ ")+e.name,r("fsHudTitle")&&(r("fsHudTitle").textContent=(G.has(e.url)?"⭐ ":"")+e.name),r("epgGroup").textContent="Categoria: "+e.group,Fl(e),je(e),G.has(e.url)?typeof Et=="function"&&Et(!0):typeof Et=="function"&&Et(!1),E(e.name);let i=t||e.url;if(a==="hls"&&i.toLowerCase().includes(".ts")&&(i=i.replace(/\.ts($|\?)/i,".m3u8$1"),x(`[Player] Preferência HLS forçada: ${i}`,"info")),window.FBG2PlayerService&&window.FBG2PlayerService.tryPlay(e,i))return;let c=i.toLowerCase().includes(".m3u8"),u=i.toLowerCase().includes(".ts");if(c&&window.Hls&&Hls.isSupported())x("[Player Engine] Usando decodificador Hls.js Low-Latency...","info"),k=new Hls(Object.assign(Ls(),{startLevel:-1})),k.loadSource(i),k.attachMedia(o),r("epgStatus")&&(r("epgStatus").textContent="⏳ CARREGANDO..."),k.on(Hls.Events.MANIFEST_PARSED,()=>{x("[Player Engine] Manifesto HLS decodificado com sucesso!","success"),typeof Un=="function"&&Un(k);try{o.preservesPitch=!0,o.webkitPreservesPitch=!0,o.playbackRate=Gt}catch{}Vn(o),at(),typeof de=="function"&&de(!0),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}),k.on(Hls.Events.ERROR,(d,l)=>{if(x(`[HLS Error] Tipo: ${l.type} | Detalhe: ${l.details}`,"warn"),l.fatal)switch(l.type){case Hls.ErrorTypes.NETWORK_ERROR:x("[HLS Error] Recuperando erro de rede...","warn"),r("epgStatus")&&(r("epgStatus").textContent="🔄 RECONECTANDO..."),sn("Reconectando rede..."),k.startLoad();break;case Hls.ErrorTypes.MEDIA_ERROR:x("[HLS Error] Recuperando decodificação de mídia...","warn"),r("epgStatus")&&(r("epgStatus").textContent="🔄 RECUPERANDO MÍDIA..."),sn("Recuperando decodificação..."),k.recoverMediaError();break;default:x("[HLS Fatal] Erro irrecuperável HLS, tentando auto-reconexão...","error"),Ht("Erro HLS Fatal");break}});else if(u&&window.mpegts&&mpegts.isSupported()&&!so.has(e.url)){x("[Player Engine] Canal MPEG-TS identificado. Construindo mpegts.js player...","info");try{if(W){try{W.unload(),W.detachMediaElement(),W.destroy()}catch{}W=null}Pn={url:e.url,at:0,stage:0},W=mpegts.createPlayer({type:"mse",isLive:S==="LIVE",url:i},nc()),W.attachMediaElement(o),W.load(),W.play().then(()=>{x("[Player Engine] MPEG-TS reproduzindo!","success"),at(),typeof de=="function"&&de(!0),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}).catch(d=>{ks(d)||(x(`[MPEGTS Error] ${d&&d.message}. Tentando MPEG-TS nativo...`,"warn"),Dr(e)||cn(e))}),W.on(mpegts.Events.ERROR,(d,l,f)=>{let p=String(l||"")+" "+String(f&&(f.msg||f.reason)||"");/interrupt|abort/i.test(p)||(x(`[MPEGTS Error] ${d}: ${l}`,"warn"),/HttpStatus|NetworkError|NETWORK_/i.test(p)&&(so.size>400&&so.clear(),so.add(e.url),x("[Player Engine] Fonte recusou o mpegts.js; usando decodificador nativo da TV.","warn")),Dr(e)||cn(e))})}catch(d){x(`[MPEGTS Exception] ${d.message}. Fallback HLS...`,"warn"),cn(e)}}else{x("[Player Engine] Usando decodificação nativa acelerada por hardware...","info"),o.innerHTML="",o.src=i;try{o.preservesPitch=!0,o.webkitPreservesPitch=!0,o.playbackRate=Gt}catch{}o.load(),Vn(o).then(()=>{x("[Player Engine] Playback nativo iniciado com sucesso!","success"),at(),typeof de=="function"&&de(!0),r("epgStatus")&&(r("epgStatus").textContent="🔴 NO AR"),je(e)}).catch(d=>{x(`[Player Fallback] Falha no playback nativo (${d&&d.message}). Tentando fallback HLS...`,"warn"),cn(e)})}})}function je(e){let t=document.querySelector(".badge-quality")||r("badgeStreamResolution");if(!t)return;let n=e||v,o=n&&n.name?n.name.toUpperCase():"",a="HD 720p";o.includes("4K")||o.includes("UHD")||o.includes("2160")?a="4K 2160p":o.includes("FHD")||o.includes("1080")?a="FHD 1080p":o.includes("HD")||o.includes("720")?a="HD 720p":(o.includes("SD")||o.includes("480"))&&(a="SD 480p");let s=r("tvPlayer");if(s&&s.videoHeight>0){let i=s.videoHeight,c=s.videoWidth;i>=2160||c>=3800?a="4K 2160p":i>=1080||c>=1900?a="FHD 1080p":i>=720||c>=1200?a="HD 720p":a=`SD ${i}p`}t.textContent=a}var io=r("tvPlayer");io&&(io.addEventListener("loadedmetadata",()=>je(v)),io.addEventListener("resize",()=>je(v)),io.addEventListener("timeupdate",St));function Rn(e){if(!e||!isFinite(e)||isNaN(e)||e<=0)return"00:00:00";let t=Math.floor(e),n=Math.floor(t/3600),o=Math.floor(t%3600/60),a=t%60,s=i=>(i<10?"0":"")+i;return n>0?`${s(n)}:${s(o)}:${s(a)}`:`00:${s(o)}:${s(a)}`}function St(){let e=r("tvPlayer");if(!e)return;let t=S==="MOVIE"||S==="SERIES"||v&&v.contentType!=="LIVE",n=r("fsVodProgressBox"),o=r("vodTimeRow"),a=r("epgBarBox");if(!t){n&&n.classList.add("hidden"),o&&o.classList.add("hidden");return}n&&!r("fsHudBar").classList.contains("hidden")&&n.classList.remove("hidden"),o&&o.classList.remove("hidden");let s=window.FBG2PlayerService,i=!!(s&&s.isActive()),c=i?s.getCurrentTimeSec():e.currentTime||0,u=i?s.getDurationSec():e.duration||0,d=u>0?Math.min(100,Math.max(0,c/u*100)):0;r("fsProgressFill")&&(r("fsProgressFill").style.width=d+"%"),r("fsTimeCurrent")&&(r("fsTimeCurrent").textContent=Rn(c)),r("fsTimeDuration")&&(r("fsTimeDuration").textContent=Rn(u));let l=r("epgProgressBar")||document.querySelector(".epg-progress");l&&(l.style.width=d+"%"),r("vodTimeCurrent")&&(r("vodTimeCurrent").textContent=Rn(c)),r("vodTimeDuration")&&(r("vodTimeDuration").textContent=Rn(u))}function Ts(e){if(typeof de=="function"){de(!!e);return}let t=r("fsBtnPlay");t&&(t.textContent=e?"⏸️ Pausar":"▶️ Reproduzir")}function wa(){try{let e=localStorage.getItem("mk21_toast_mode");if(e==="off"||e==="player"||e==="all")return e;if(localStorage.getItem("mk21_show_toasts")==="true")return"all"}catch{}return"off"}function oc(e){let t=e==="player"||e==="all"?e:"off";try{localStorage.setItem("mk21_toast_mode",t),localStorage.setItem("mk21_show_toasts",t==="off"?"false":"true")}catch{}if(t==="off"){let n=r("channelOverlay");n&&n.classList.add("hidden")}if(t!=="all"){let n=r("mk21-toast-banner");n&&n.classList.remove("visible")}return t}var Vr=null;function E(e){let t=wa();if(!ne||!ne.isLoggedIn||t==="off"){let a=r("mk21-toast-banner");a&&a.classList.remove("visible");let s=r("channelOverlay");s&&s.classList.add("hidden");return}let n=r("channelOverlay");if(n&&(n.textContent=e,n.classList.remove("hidden"),clearTimeout(n._timer),n._timer=setTimeout(()=>n.classList.add("hidden"),3500)),t!=="all"){let a=r("mk21-toast-banner");a&&a.classList.remove("visible");return}let o=r("mk21-toast-banner");o||(o=document.createElement("div"),o.id="mk21-toast-banner",document.body.appendChild(o)),o.textContent=e,o.classList.add("visible"),clearTimeout(Vr),Vr=setTimeout(()=>{o&&o.classList.remove("visible")},2500)}function ca(){if(!D||D.length===0)return;let t=(D.findIndex(n=>v&&n.url===v.url)+1)%D.length;fe(D[t]),E("▲ Próximo: "+D[t].name),_o(D[t])}function da(){if(!D||D.length===0)return;let t=(D.findIndex(n=>v&&n.url===v.url)-1+D.length)%D.length;fe(D[t]),E("▼ Anterior: "+D[t].name),_o(D[t])}function ue(){let e=r("videoContainer");return!!(e&&e.classList.contains("is-fullscreen"))||!!(document.fullscreenElement||document.webkitFullscreenElement)}function Eo(e){let t=r("fsHudBar");if(t)if(e){t.classList.remove("hidden"),v&&r("fsHudTitle")&&(r("fsHudTitle").textContent=(G.has(v.url)?"⭐ ":"")+v.name);let n=r("tvPlayer");r("fsBtnPlay")&&(r("fsBtnPlay").textContent=n&&n.paused?"▶️ Reproduzir":"⏸️ Pausar"),r("fsBtnSpeed")&&(r("fsBtnSpeed").textContent="⚡ "+Gt+"x"),r("fsBtnFav")&&v&&(r("fsBtnFav").textContent=G.has(v.url)?"★ Favoritado":"⭐ Favoritar");let o=S==="MOVIE"||S==="SERIES"||v&&v.contentType!=="LIVE";r("fsVodProgressBox")&&(o?r("fsVodProgressBox").classList.remove("hidden"):r("fsVodProgressBox").classList.add("hidden")),r("fsEpgOverlayBox")&&(o?r("fsEpgOverlayBox").classList.add("hidden"):r("fsEpgOverlayBox").classList.remove("hidden")),St()}else{let n=document.activeElement;if(n&&t.contains(n)){let o=r("videoContainer")||document.body;try{o&&typeof o.focus=="function"&&o.focus()}catch{}}t.classList.add("hidden")}}function xe(e,t){if(!e)return;let n=r("videoContainer");t&&ue()&&n?e.parentElement!==n&&n.appendChild(e):(!t||!ue())&&e.parentElement!==document.body&&document.body.appendChild(e)}function En(e){let t=r("videoContainer"),n=r("tvPlayer");if(!t)return;let o=ue();if(typeof e=="boolean"?e:!o){t.classList.add("is-fullscreen"),document.body.classList.add("is-fullscreen");let s=r("remoteColorBar");s&&(s.classList.add("hidden"),s.style.setProperty("display","none","important")),T="player",Eo(!0),ie(),x("[Player] Modo Tela Cheia ATIVADO","info");try{let i=document.documentElement;i.requestFullscreen?i.requestFullscreen().catch(()=>{}):i.webkitRequestFullscreen?i.webkitRequestFullscreen():t.requestFullscreen?t.requestFullscreen().catch(()=>{}):t.webkitRequestFullscreen&&t.webkitRequestFullscreen()}catch(i){x("[Player] Fullscreen API fallback ativo (CSS is-fullscreen aplicado): "+i.message,"debug")}setTimeout(function(){r("fsBtnPlay")&&r("fsBtnPlay").focus()},200)}else{["modalPlayerOptions","modalAudioSubs","modalServerPicker","modalPin","modalSeriesEpisodes","modalDevLogs","modalStreamDiagnostics"].forEach(i=>{let c=r(i);c&&c.parentElement!==document.body&&document.body.appendChild(c)}),t.classList.remove("is-fullscreen"),document.body.classList.remove("is-fullscreen");let s=r("remoteColorBar");s&&(s.classList.remove("hidden"),s.style.removeProperty("display")),Eo(!1),x("[Player] Modo Tela Cheia DESATIVADO","info");try{document.exitFullscreen?document.exitFullscreen().catch(()=>{}):document.webkitExitFullscreen&&document.webkitExitFullscreen()}catch{}T="channels",Le()}}function ac(){r("fsBtnPrev")&&(r("fsBtnPrev").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),S==="LIVE"?da():(ln(-1e4),E("⏪ -10s")),ie()}),r("fsBtnNext")&&(r("fsBtnNext").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),S==="LIVE"?ca():(ln(1e4),E("⏩ +10s")),ie()}),r("fsBtnPlay")&&(r("fsBtnPlay").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),Lo(),ie()}),r("fsBtnSpeed")&&(r("fsBtnSpeed").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),Is(),ie()}),r("fsBtnAspect")&&(r("fsBtnAspect").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),hs(),ie()}),r("fsBtnFav")&&(r("fsBtnFav").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),v&&Io(v.url),ie()}),r("fsBtnQuality")&&(r("fsBtnQuality").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof ko=="function"&&ko(!0)}),r("fsBtnBuffer")&&(r("fsBtnBuffer").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),As(),ie()}),r("fsBtnServer")&&(r("fsBtnServer").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof ct=="function"&&ct()}),r("fsBtnAudio")&&(r("fsBtnAudio").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),typeof $e=="function"&&$e()}),r("fsBtnBack")&&(r("fsBtnBack").onclick=function(t){t&&(t.preventDefault(),t.stopPropagation()),En(!1)});let e=r("videoContainer");if(e&&!e.dataset.mk21FsActivityBound){e.dataset.mk21FsActivityBound="1";let t=0,n=function(){if(!ue())return;let o=Date.now();o-t<250||(t=o,ie())};e.addEventListener("mousemove",n,{passive:!0}),e.addEventListener("pointermove",n,{passive:!0}),e.addEventListener("click",function(o){if(!ue())return;let a=r("fsHudBar");if(a&&o.target&&a.contains(o.target)){ie();return}ie()})}}ac();function de(e){let t=r("btnPlayPause");t&&(t.title=e?"Pausar":"Reproduzir",t.innerHTML=e?'<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>':'<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>');let n=r("fsBtnPlay");n&&(n.textContent=e?"⏸️ Pausar":"▶️ Reproduzir")}function Et(e){let t=r("btnFavorite");t&&(t.title=e?"Remover dos favoritos":"Adicionar aos favoritos",t.innerHTML=e?'<svg style="width:22px;height:22px;fill:#ffd54f;" viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>':'<svg style="width:22px;height:22px;fill:rgba(255,255,255,0.7);" viewBox="0 0 24 24"><path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"/></svg>');let n=r("fsBtnFav");n&&(n.textContent=e?"★ Favoritado":"⭐ Favoritar")}function As(){let e=["auto","fast","stable"],t=Lt(),n=e[(e.indexOf(t)+1)%e.length];localStorage.setItem("mk21_buffer_mode",n);let o=Xe[n],a=window.FBG2PlayerService;a&&typeof a.setBufferMs=="function"&&o&&o.hls&&a.setBufferMs((o.hls.maxBufferLength||4)*1e3);let s="⚡ Fluidez: "+(o?o.label:n);typeof showToast=="function"?showToast(s):typeof E=="function"&&E(s)}r("videoContainer").ondblclick=()=>En();r("btnFullscreen")&&(r("btnFullscreen").onclick=()=>En());function rc(e){let t=r("tvPlayer");if(!t)return;Gt=e;try{t.playbackRate=e,t.preservesPitch=!0,t.mozPreservesPitch=!0,t.webkitPreservesPitch=!0,t.defaultPlaybackRate!==void 0&&(t.defaultPlaybackRate=e)}catch{}let n="⚡ "+e+"x";r("btnSpeed")&&(r("btnSpeed").textContent=n),r("fsBtnSpeed")&&(r("fsBtnSpeed").textContent=n)}function Is(){let e=(Ko.indexOf(Gt)+1)%Ko.length;rc(Ko[e]),E("⚡ Velocidade: "+Gt+"x (áudio normal)")}r("btnPlayPause")&&(r("btnPlayPause").onclick=Lo);r("btnFavorite")&&(r("btnFavorite").onclick=()=>{v&&Io(v.url)});r("btnSpeed")&&(r("btnSpeed").onclick=Is);r("btnQuality")&&(r("btnQuality").onclick=()=>{typeof ko=="function"&&ko(!1)});r("btnBufferMode")&&(r("btnBufferMode").onclick=As);r("btnQuickServer")&&(r("btnQuickServer").onclick=()=>{typeof ct=="function"&&ct()});function $e(){let e=r("modalAudioSubs");if(!e)return;xe(e,!0),e.classList.remove("hidden"),T="modalAudioSubs";let t=r("audioTracksList"),n=r("subtitleTracksList");t.innerHTML="",n.innerHTML="";let o=r("tvPlayer"),a=!1,s=window.FBG2PlayerService;if(s&&s.isActive()&&s.getAudioTracks().length>0){a=!0;let l=s.getActiveAudioId();s.getAudioTracks().forEach(f=>{let p=document.createElement("button");p.className="track-item-btn"+(f.id===l?" active":""),p.setAttribute("tabindex","0");let m=f.name||f.language||"Faixa "+((f.index||0)+1);p.textContent="🔊 "+m+(f.id===l?" [Ativo]":""),p.onclick=()=>{s.setAudioTrack(f.id),E("Áudio: "+m),$e()},t.appendChild(p)})}else if(k&&k.audioTracks&&k.audioTracks.length>0)a=!0,k.audioTracks.forEach((l,f)=>{let p=document.createElement("button");p.className="track-item-btn"+(k.audioTrack===f?" active":""),p.setAttribute("tabindex","0"),p.textContent=`🔊 ${l.name||l.lang||"Faixa "+(f+1)}`+(k.audioTrack===f?" [Ativo]":""),p.onclick=()=>{k.audioTrack=f,E("Áudio: "+(l.name||l.lang||"Faixa "+(f+1))),$e()},t.appendChild(p)});else if(o&&o.audioTracks&&o.audioTracks.length>0){a=!0;for(let l=0;l<o.audioTracks.length;l++){let f=o.audioTracks[l],p=document.createElement("button");p.className="track-item-btn"+(f.enabled?" active":""),p.setAttribute("tabindex","0"),p.textContent=`🔊 ${f.label||f.language||"Faixa "+(l+1)}`+(f.enabled?" [Ativo]":""),p.onclick=()=>{for(let m=0;m<o.audioTracks.length;m++)o.audioTracks[m].enabled=m===l;E("Áudio: "+(f.label||"Faixa "+(l+1))),$e()},t.appendChild(p)}}if(!a){let l=document.createElement("p");l.style.color="#94a3b8",l.style.fontSize="15px",l.textContent="🔊 Áudio Principal Original da Emissora (Faixa única)",t.appendChild(l)}let i=document.createElement("button"),c=!k||k.subtitleTrack===-1;i.className="track-item-btn"+(c?" active":""),i.setAttribute("tabindex","0"),i.textContent="❌ Desativar Legendas",i.onclick=()=>{if(k&&(k.subtitleTrack=-1),o&&o.textTracks)for(let l=0;l<o.textTracks.length;l++)o.textTracks[l].mode="disabled";E("Legendas Desativadas"),$e()},n.appendChild(i);let u=!1;if(k&&k.subtitleTracks&&k.subtitleTracks.length>0)u=!0,k.subtitleTracks.forEach((l,f)=>{let p=document.createElement("button");p.className="track-item-btn"+(k.subtitleTrack===f?" active":""),p.setAttribute("tabindex","0"),p.textContent=`💬 ${l.name||l.lang||"Legenda "+(f+1)}`+(k.subtitleTrack===f?" [Ativo]":""),p.onclick=()=>{k.subtitleTrack=f,E("Legenda: "+(l.name||l.lang||"Faixa "+(f+1))),$e()},n.appendChild(p)});else if(o&&o.textTracks&&o.textTracks.length>0){u=!0;for(let l=0;l<o.textTracks.length;l++){let f=o.textTracks[l],p=document.createElement("button");p.className="track-item-btn"+(f.mode==="showing"?" active":""),p.setAttribute("tabindex","0"),p.textContent=`💬 ${f.label||f.language||"Legenda "+(l+1)}`+(f.mode==="showing"?" [Ativo]":""),p.onclick=()=>{for(let m=0;m<o.textTracks.length;m++)o.textTracks[m].mode=m===l?"showing":"disabled";E("Legenda: "+(f.label||"Faixa "+(l+1))),$e()},n.appendChild(p)}}if(!u){let l=document.createElement("p");l.style.color="#94a3b8",l.style.fontSize="14px",l.style.marginTop="8px",l.textContent="Legendas embutidas não disponíveis neste fluxo.",n.appendChild(l)}let d=e.querySelector(".track-item-btn");d&&d.focus()}r("btnCloseAudioSubs").onclick=()=>{let e=r("modalAudioSubs");e&&(e.classList.add("hidden"),xe(e,!1));let t=ue();T=t?"player":"channels";let n=r(t?"fsBtnAudio":"btnAudioTrack");n&&n.focus&&n.focus(),t&&ie()};r("btnAudioTrack")&&(r("btnAudioTrack").onclick=$e);r("btnSubtitles")&&(r("btnSubtitles").onclick=$e);r("tabLive").onclick=()=>Pe("LIVE");r("tabMovies").onclick=()=>Pe("MOVIE");r("tabSeries").onclick=()=>Pe("SERIES");r("tabContinue")&&(r("tabContinue").onclick=()=>Pe("CONTINUE"));r("tabGuide")&&(r("tabGuide").onclick=()=>Pe("GUIDE"));r("tabFavs").onclick=()=>Pe("FAVORITES");r("tabSettings").onclick=()=>Pe("SETTINGS");r("btnOpenOsk")&&(r("btnOpenOsk").onclick=()=>{if(F.open){$a(!0);return}pi(r("inputSearch"))});r("listGuideChannels")&&r("listGuideChannels").addEventListener("click",e=>{let t=e.target.closest?e.target.closest(".guide-channel-btn"):null;if(!t)return;let n=parseInt(t.getAttribute("data-idx")||"0",10);Number.isFinite(n)&&Fa(n)});function Pe(e){e==="GUIDE"&&typeof P!="undefined"&&(P.previousType=S&&S!=="GUIDE"?S:"LIVE"),S=e;try{let o={LIVE:"📺 Ao Vivo",MOVIE:"🎬 Filmes",SERIES:"🍿 Séries",CONTINUE:"▶ Continuar",FAVORITES:"⭐ Favoritos",SETTINGS:"⚙️ Config"};o[e]&&E(o[e])}catch{}["tabLive","tabMovies","tabSeries","tabContinue","tabFavs","tabGuide","tabSettings"].forEach(o=>{let a=r(o);a&&a.classList.remove("active")});let t=e==="LIVE"?"tabLive":e==="MOVIE"?"tabMovies":e==="SERIES"?"tabSeries":e==="CONTINUE"?"tabContinue":e==="FAVORITES"?"tabFavs":e==="GUIDE"?"tabGuide":"tabSettings";if(r(t)&&r(t).classList.add("active"),e==="GUIDE"){r("sectionUnified").classList.add("hidden"),r("sectionSettings").classList.add("hidden"),li();return}if(e==="SETTINGS"){r("sectionUnified").classList.add("hidden"),r("sectionGuide").classList.add("hidden"),r("sectionSettings").classList.remove("hidden"),Ms("info"),T="settings",setTimeout(()=>{let o=document.querySelector(".cfg-menu-item.active")||r("cfgBtnInfo");if(o)try{o.focus()}catch{}},40);return}if(typeof Dt=="function")try{Dt(!0)}catch{}let n=r(t)||r("tabLive");if(n)try{n.focus()}catch{}r("sectionSettings").classList.add("hidden"),r("sectionGuide").classList.add("hidden"),r("sectionUnified").classList.remove("hidden"),te(),ae(),Oe("ALL"),T="channels",e==="FAVORITES"&&ml(),setTimeout(()=>{["#listCategories","#listItems"].forEach(i=>{let c=document.querySelector(i);c&&typeof X=="function"&&X(c)});let o=r("listItems")?r("listItems").querySelector(".list-item-btn:not([disabled])"):null,a=r("listCategories")?r("listCategories").querySelector(".cat-item-btn.active"):null,s=o||a||n;s&&Se(s)},40)}var ua=[["cfgBtnInfo","⚙️ Informação geral"],["cfgBtnFonte","🔤 Tamanho da fonte"],["cfgBtnFiltro","📦 Filtrar conteúdo"],["cfgBtnSpeedTest","🚀 Teste de velocidade"],["cfgBtnLimpar","🗑️ Limpar armazenamento"],["cfgBtnTempo","⏱️ Configurações de tempo"],["cfgBtnCategorias","📁 Gerenciar categorias"],["cfgBtnFluxo","📺 Alterar formato de fluxo"],["cfgBtnQualidade","📡 Servidor / Fonte"],["cfgBtnQualidadeVideo","🎞️ Qualidade de vídeo"],["cfgBtnBuffer","⚡ Buffer e fluidez"],["cfgBtnAdulto","🔞 Restrição adulto"],["cfgBtnNotificacoes","🔔 Avisos na tela"],["cfgBtnPin","🔒 Alterar código pin"],["cfgBtnLogout","🚪 Sair da Conta (Logout)"]],$r=ua.map(e=>e[0]);function sc(){let e=document.querySelector(".settings-left-menu");if(!e)return;let t=ua.map(s=>s[0]).join("|");if(e.dataset.mk21Menu===t)return;let n=e.querySelector(".cfg-tmdb-toggle"),o=e.querySelector(".cfg-menu-item.active"),a=o?o.id:"cfgBtnInfo";if(e.innerHTML="",ua.forEach(s=>{let i=document.createElement("button");i.id=s[0],i.type="button",i.className="cfg-menu-item",i.tabIndex=0,i.textContent=s[1],s[0]===a&&i.classList.add("active"),e.appendChild(i)}),n)e.appendChild(n);else{let s=document.createElement("div");s.className="cfg-tmdb-toggle",s.innerHTML="<span>🎬 Usar API TMDB</span>";let i=document.createElement("input");i.type="checkbox",i.id="cfgToggleTmdb",i.checked=!0,i.style.width="22px",i.style.height="22px",s.appendChild(i),e.appendChild(s)}e.dataset.mk21Menu=t}sc();$r.forEach(e=>{let t=r(e);t&&(t.onclick=()=>{$r.forEach(n=>{let o=r(n);o&&o.classList.remove("active")}),t.classList.add("active"),Ms(e.replace("cfgBtn","").toLowerCase())})});function Ms(e){if(e!=="speedtest"&&window.__mk21SpeedTestController){try{window.__mk21SpeedTestController.abort()}catch{}window.__mk21SpeedTestController=null}if(e==="logout"){bn();return}let t=r("settingsDetailBox");if(e==="info"){let n=Wt(),o=n&&n.user?n.user:"Sessão Offline / Visitante";t.innerHTML=`
       <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Informações da Conta</h3>
       <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:24px;">
         <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
           <div style="font-size:14px; color:#888;">Usuário Conectado</div>
-          <div style="font-size:18px; font-weight:700; color:#4caf50;">${K(o)}</div>
+          <div style="font-size:18px; font-weight:700; color:#4caf50;">${q(o)}</div>
         </div>
         <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px; display:flex; align-items:center; justify-content:space-between; gap:12px;">
           <div>
@@ -55,7 +55,7 @@
         </div>
         <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
           <div style="font-size:14px; color:#888;">Versão do Aplicativo</div>
-          <div style="font-size:18px; font-weight:700; color:#ffd54f;">MK21 Play v${ht} (LG webOS / Tizen)</div>
+          <div style="font-size:18px; font-weight:700; color:#ffd54f;">MK21 Play v${Ct} (LG webOS / Tizen)</div>
         </div>
       </div>
 
@@ -64,21 +64,21 @@
       <div style="background:#1a1e2d; border:1px solid rgba(255,213,79,0.35); border-radius:12px; padding:18px 22px; display:flex; justify-content:space-between; align-items:center;">
         <div>
           <div style="font-size:14px; color:#ffd54f; font-weight:bold; text-transform:uppercase; letter-spacing:0.5px;">Atualização Direta no Aplicativo</div>
-          <div style="font-size:16px; color:#fff; margin-top:4px;">Versão Instalada: <strong>v${ht}</strong> (LG webOS / Tizen)</div>
+          <div style="font-size:16px; color:#fff; margin-top:4px;">Versão Instalada: <strong>v${Ct}</strong> (LG webOS / Tizen)</div>
           <div id="txtSettingsUpdateStatus" style="font-size:13px; color:#94a3b8; margin-top:2px;">Verifique e instale novas versões diretamente pela TV sem pendrive.</div>
         </div>
         <button id="btnOpenUpdateModal" class="ctrl-btn primary" style="padding:14px 26px; font-size:16px;" tabindex="0">
           🚀 Atualizar Agora
         </button>
       </div>
-    `;let i=a("btnOpenUpdateModal");i&&(i.onclick=()=>so(!0));let r=a("btnAccountLogout");r&&(r.onclick=()=>sn())}else e==="speedtest"?Gi():e==="fonte"?qi():e==="filtro"?Yn():e==="limpar"?Yt():e==="tempo"?Vs():e==="categorias"?$a():e==="fluxo"?Hi():e==="pin"?zi():e==="qualidadevideo"?uo():e==="qualidade"?ft():e==="buffer"?ao():e==="adulto"?$i():e==="notificacoes"&&Ui()}var sa=null;function eo(){return sa||a("settingsDetailBox")}function to(e){if(!e||e<=0)return"Ainda não sinc.";try{let t=new Date(e),n=String(t.getDate()).padStart(2,"0"),o=String(t.getMonth()+1).padStart(2,"0"),i=String(t.getHours()).padStart(2,"0"),r=String(t.getMinutes()).padStart(2,"0");return`${n}/${o} ${i}:${r}`}catch{return"Sincronizado"}}function no(e){if(!e||e.totalChannels+e.totalVOD+e.totalSeries===0)return'<span class="srv-metric-pill is-empty">Sem cache local — clique em 🔄 Sync</span>';let t=Number(e.totalChannels||0).toLocaleString("pt-BR"),n=Number(e.totalVOD||0).toLocaleString("pt-BR"),o=Number(e.totalSeries||0).toLocaleString("pt-BR"),i=Number(e.totalEPG||e.totalGroups||0).toLocaleString("pt-BR");return`<span class="srv-metric-pill has-data">📺 ${t} Canais</span><span class="srv-metric-pill has-data">🎬 ${n} Filmes</span><span class="srv-metric-pill has-data">🍿 ${o} Séries</span><span class="srv-metric-pill">📡 ${i} ${e.totalEPG>0?"EPG":"Grupos"}</span>`}function ta(e,t){if(!e)return;let n=no(t);document.querySelectorAll(`[data-srv-metrics-id="${e}"]`).forEach(o=>{o.innerHTML=n})}function ft(){let e=eo(),t=L[_]||L[0]||{name:"Nenhum"},n=ja();t&&t.id&&n.totalItems>0&&nt(t.id,w,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl);let o=Vt(),i=Ce.BG_SYNC_LABELS[o]||"Uma vez ao dia",r=Rn()?"Todas em 2º plano":"Apenas lista ativa",s=tt()?"Ocultar (Silencioso)":"Mostrar na tela inicial",c=qa(),u=L.map((d,l)=>{let f=l===_,p=f&&n.totalItems>0?n:Wa(d.id),m=0;try{m=parseInt(localStorage.getItem("mk21_last_sync_"+d.id)||"0",10)||0}catch{}return`
+    `;let a=r("btnOpenUpdateModal");a&&(a.onclick=()=>Na(!0));let s=r("btnAccountLogout");s&&(s.onclick=()=>bn())}else e==="speedtest"?Os():e==="fonte"?Bs():e==="filtro"?ho():e==="limpar"?an():e==="tempo"?ic():e==="categorias"?fa():e==="fluxo"?Rs():e==="pin"?Ns():e==="qualidadevideo"?Ba():e==="qualidade"?vt():e==="buffer"?Ia():e==="adulto"?Ps():e==="notificacoes"&&_s()}var Mo=null;function ka(){return Mo||r("settingsDetailBox")}function Ta(e){if(!e||e<=0)return"Ainda não sinc.";try{let t=new Date(e),n=String(t.getDate()).padStart(2,"0"),o=String(t.getMonth()+1).padStart(2,"0"),a=String(t.getHours()).padStart(2,"0"),s=String(t.getMinutes()).padStart(2,"0");return`${n}/${o} ${a}:${s}`}catch{return"Sincronizado"}}function Aa(e){if(!e||e.totalChannels+e.totalVOD+e.totalSeries===0)return'<span class="srv-metric-pill is-empty">Sem cache local — clique em 🔄 Sync</span>';let t=Number(e.totalChannels||0).toLocaleString("pt-BR"),n=Number(e.totalVOD||0).toLocaleString("pt-BR"),o=Number(e.totalSeries||0).toLocaleString("pt-BR"),a=Number(e.totalEPG||e.totalGroups||0).toLocaleString("pt-BR");return`<span class="srv-metric-pill has-data">📺 ${t} Canais</span><span class="srv-metric-pill has-data">🎬 ${n} Filmes</span><span class="srv-metric-pill has-data">🍿 ${o} Séries</span><span class="srv-metric-pill">📡 ${a} ${e.totalEPG>0?"EPG":"Grupos"}</span>`}function xo(e,t){if(!e)return;let n=Aa(t);document.querySelectorAll(`[data-srv-metrics-id="${e}"]`).forEach(o=>{o.innerHTML=n})}function vt(){let e=ka(),t=L[N]||L[0]||{name:"Nenhum"},n=ba();t&&t.id&&n.totalItems>0&&it(t.id,C,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl);let o=jt(),a=Ae.BG_SYNC_LABELS[o]||"Uma vez ao dia",s=qn()?"Todas em 2º plano":"Apenas lista ativa",i=st()?"Ocultar (Silencioso)":"Mostrar na tela inicial",c=va(),u=L.map((d,l)=>{let f=l===N,p=f&&n.totalItems>0?n:ya(d.id),m=0;try{m=parseInt(localStorage.getItem("mk21_last_sync_"+d.id)||"0",10)||0}catch{}return`
       <div class="server-manager-row ${f?"active-server":""}" style="margin-bottom:10px;">
         <div class="server-info-col">
           <div class="server-info-top-line">
-            <span class="server-info-name">${f?"🟢":"⚪"} Fonte ${l+1}: ${K(X(d.name))}${f?" [✓ ATIVA]":""}</span>
-            <span class="server-row-sync-meta">• ${K(to(m))}</span>
+            <span class="server-info-name">${f?"🟢":"⚪"} Fonte ${l+1}: ${q(Z(d.name))}${f?" [✓ ATIVA]":""}</span>
+            <span class="server-row-sync-meta">• ${q(Ta(m))}</span>
           </div>
-          <div class="server-row-metrics" data-srv-metrics-id="${K(d.id)}">${no(p)}</div>
+          <div class="server-row-metrics" data-srv-metrics-id="${q(d.id)}">${Aa(p)}</div>
         </div>
         <div class="server-row-actions">
           <button type="button" class="btn-server-sync-one cfg-srv-sync-one" data-srv="${l}" tabindex="0">🔄 Sync</button>
@@ -86,7 +86,7 @@
         </div>
       </div>`}).join("");e.innerHTML=`
     <h3 style="margin:0 0 8px 0; font-size:24px;">📡 Servidor / Fonte</h3>
-    <p style="color:#aaa; margin:0 0 12px 0; font-size:15px;">Fonte ativa: <strong style="color:#4caf50;">${K(X(t.name))}</strong>. Configure a sincronização ou confira os totais individuais por servidor.</p>
+    <p style="color:#aaa; margin:0 0 12px 0; font-size:15px;">Fonte ativa: <strong style="color:#4caf50;">${q(Z(t.name))}</strong>. Configure a sincronização ou confira os totais individuais por servidor.</p>
     <div class="server-metrics-grid" style="max-width:860px;">
       <div class="server-metric-card"><span class="server-metric-label">📺 Canais (Geral)</span><strong class="server-metric-value">${n.totalChannels.toLocaleString("pt-BR")}</strong></div>
       <div class="server-metric-card"><span class="server-metric-label">🎬 Filmes VOD (Geral)</span><strong class="server-metric-value">${n.totalVOD.toLocaleString("pt-BR")}</strong></div>
@@ -99,7 +99,7 @@
           <span class="apk-setting-icon">🔄</span>
           <span class="apk-setting-texts">
             <strong class="apk-setting-title">Frequência</strong>
-            <span class="apk-setting-sub">${i}</span>
+            <span class="apk-setting-sub">${a}</span>
           </span>
         </button>
       </div>
@@ -108,7 +108,7 @@
           <span class="apk-setting-icon">🌐</span>
           <span class="apk-setting-texts">
             <strong class="apk-setting-title">Sincronizar todas</strong>
-            <span class="apk-setting-sub">${r}</span>
+            <span class="apk-setting-sub">${s}</span>
           </span>
         </button>
       </div>
@@ -117,7 +117,7 @@
           <span class="apk-setting-icon">👁️</span>
           <span class="apk-setting-texts">
             <strong class="apk-setting-title">Progresso de carga</strong>
-            <span class="apk-setting-sub">${s}</span>
+            <span class="apk-setting-sub">${i}</span>
           </span>
         </button>
       </div>
@@ -138,17 +138,17 @@
       <button id="cfgRefreshServer" class="ctrl-btn" style="padding:13px 20px; margin-bottom:8px;" tabindex="0">↻ Recarregar Fonte Ativa</button>
     </div>
     <div id="msgServerChanged" style="margin-top:12px; color:#4caf50; font-weight:bold; font-size:15px; display:none;"></div>
-  `,a("cfgBtnSyncFreq")&&(a("cfgBtnSyncFreq").onclick=()=>Wi(a("cfgBtnSyncFreq"),ft)),a("cfgBtnSyncAll")&&(a("cfgBtnSyncAll").onclick=()=>ji(a("cfgBtnSyncAll"),ft)),a("cfgBtnHideProgress")&&(a("cfgBtnHideProgress").onclick=()=>Ki(a("cfgBtnHideProgress"),ft)),a("cfgBtnStagedLoad")&&(a("cfgBtnStagedLoad").onclick=()=>Yi(a("cfgBtnStagedLoad"),ft)),e.querySelectorAll(".cfg-srv-sync-one").forEach(d=>{d.onclick=()=>{let l=parseInt(d.getAttribute("data-srv"),10)||0;ro(l,d)}}),e.querySelectorAll(".cfg-srv-choice").forEach(d=>{d.onclick=()=>{let l=parseInt(d.getAttribute("data-srv"),10)||0;if(l===_)return;_=l;try{localStorage.setItem("mk21_last_server",_)}catch{}Ft();let f=a("msgServerChanged");f&&(f.textContent="✅ Conectando à fonte "+(l+1)+"...",f.style.display="block"),ft(),ve(!0)}}),a("cfgOpenServerManager")&&(a("cfgOpenServerManager").onclick=()=>ot()),a("cfgSyncAllServersNow")&&(a("cfgSyncAllServersNow").onclick=()=>la(!0)),a("cfgRefreshServer")&&(a("cfgRefreshServer").onclick=()=>{$t({preserveCatalog:!0}),ve(!0)}),Xi()}function ao(){let e=eo(),t=vt(),n=Object.keys(Ge).map(o=>{let i=Ge[o],r=o===t;return`
-      <button class="list-item-btn cfg-buf-choice ${r?"active":""}" data-buf="${o}" style="padding:16px 20px; display:flex; flex-direction:column; align-items:flex-start; gap:4px;" tabindex="0">
-        <span style="font-size:17px; font-weight:700; color:${r?"#4caf50":"#fff"};">${r?"✅":"⚪"} ${i.label}</span>
-        <span style="font-size:14px; color:#94a3b8;">${i.hint}</span>
+  `,r("cfgBtnSyncFreq")&&(r("cfgBtnSyncFreq").onclick=()=>Ds(r("cfgBtnSyncFreq"),vt)),r("cfgBtnSyncAll")&&(r("cfgBtnSyncAll").onclick=()=>Fs(r("cfgBtnSyncAll"),vt)),r("cfgBtnHideProgress")&&(r("cfgBtnHideProgress").onclick=()=>Vs(r("cfgBtnHideProgress"),vt)),r("cfgBtnStagedLoad")&&(r("cfgBtnStagedLoad").onclick=()=>$s(r("cfgBtnStagedLoad"),vt)),e.querySelectorAll(".cfg-srv-sync-one").forEach(d=>{d.onclick=()=>{let l=parseInt(d.getAttribute("data-srv"),10)||0;Ra(l,d)}}),e.querySelectorAll(".cfg-srv-choice").forEach(d=>{d.onclick=()=>{let l=parseInt(d.getAttribute("data-srv"),10)||0;if(l===N)return;N=l;try{localStorage.setItem("mk21_last_server",N)}catch{}qt();let f=r("msgServerChanged");f&&(f.textContent="✅ Conectando à fonte "+(l+1)+"...",f.style.display="block"),vt(),Ee(!0)}}),r("cfgOpenServerManager")&&(r("cfgOpenServerManager").onclick=()=>ct()),r("cfgSyncAllServersNow")&&(r("cfgSyncAllServersNow").onclick=()=>Po(!0)),r("cfgRefreshServer")&&(r("cfgRefreshServer").onclick=()=>{Kt({preserveCatalog:!0}),Ee(!0)}),Hs()}function Ia(){let e=ka(),t=Lt(),n=Object.keys(Xe).map(o=>{let a=Xe[o],s=o===t;return`
+      <button class="list-item-btn cfg-buf-choice ${s?"active":""}" data-buf="${o}" style="padding:16px 20px; display:flex; flex-direction:column; align-items:flex-start; gap:4px;" tabindex="0">
+        <span style="font-size:17px; font-weight:700; color:${s?"#4caf50":"#fff"};">${s?"✅":"⚪"} ${a.label}</span>
+        <span style="font-size:14px; color:#94a3b8;">${a.hint}</span>
       </button>`}).join("");e.innerHTML=`
     <h3 style="margin:0 0 10px 0; font-size:24px;">⚡ Buffer e Fluidez</h3>
     <p style="color:#aaa; margin:0 0 18px 0;">Quanto de vídeo a TV guarda antes de exibir. Buffer maior = menos travamento; buffer menor = troca de canal mais rápida.</p>
     <div style="display:flex; flex-direction:column; gap:10px; max-width:620px;">${n}</div>
     <div style="margin-top:18px;"><button id="cfgApplyBuffer" class="ctrl-btn primary" style="padding:14px 22px;" tabindex="0">🔁 Aplicar no canal atual</button></div>
     <div id="msgBufferSaved" style="margin-top:14px; color:#4caf50; font-weight:bold; font-size:15px; display:none;"></div>
-  `,e.querySelectorAll(".cfg-buf-choice").forEach(o=>{o.onclick=()=>{localStorage.setItem("mk21_buffer_mode",o.getAttribute("data-buf")),ao();let i=a("msgBufferSaved");i&&(i.textContent="✅ Buffer salvo: "+Ge[vt()].label+". Vale ao sintonizar o próximo canal.",i.style.display="block")}}),a("cfgApplyBuffer")&&(a("cfgApplyBuffer").onclick=()=>{let o=a("msgBufferSaved");o&&(o.textContent="✅ Aplicando no canal atual...",o.style.display="block"),v?de(v):o&&(o.textContent="Selecione um canal para aplicar.")})}function $i(){let e=a("settingsDetailBox"),t=Ue;e.innerHTML=`
+  `,e.querySelectorAll(".cfg-buf-choice").forEach(o=>{o.onclick=()=>{localStorage.setItem("mk21_buffer_mode",o.getAttribute("data-buf")),Ia();let a=r("msgBufferSaved");a&&(a.textContent="✅ Buffer salvo: "+Xe[Lt()].label+". Vale ao sintonizar o próximo canal.",a.style.display="block")}}),r("cfgApplyBuffer")&&(r("cfgApplyBuffer").onclick=()=>{let o=r("msgBufferSaved");o&&(o.textContent="✅ Aplicando no canal atual...",o.style.display="block"),v?fe(v):o&&(o.textContent="Selecione um canal para aplicar.")})}function Ps(){let e=r("settingsDetailBox"),t=Ke;e.innerHTML=`
     <h3 style="margin:0 0 10px 0; font-size:24px;">🔞 Restrição Adulto</h3>
     <p style="color:#aaa; margin:0 0 18px 0;">Com a restrição ativa, categorias e canais 18+ ficam ocultos. Para liberar, a TV pede o PIN de 4 dígitos.</p>
     <div style="background:#1a1e2d; border:1px solid ${t?"rgba(76,175,80,0.5)":"rgba(255,213,79,0.35)"}; border-radius:12px; padding:18px 22px; max-width:620px; display:flex; justify-content:space-between; align-items:center; gap:16px;">
@@ -161,10 +161,10 @@
       </button>
     </div>
     <div style="display:flex; gap:12px; margin-top:18px; flex-wrap:wrap;">
-      <button id="cfgChangeAdultPin" class="ctrl-btn" style="padding:14px 22px;" tabindex="0">🔑 Alterar PIN (atual: ${Ke})</button>
+      <button id="cfgChangeAdultPin" class="ctrl-btn" style="padding:14px 22px;" tabindex="0">🔑 Alterar PIN (atual: ${Qe})</button>
     </div>
     <p style="margin-top:18px; color:#64748b; font-size:14px;">Dica: o PIN padrão de fábrica é 0000. Troque depois de instalar.</p>
-  `,a("cfgToggleAdult")&&(a("cfgToggleAdult").onclick=()=>{Ue?(Ue=!1,$i(),Q(),te(),Y()):js()}),a("cfgChangeAdultPin")&&(a("cfgChangeAdultPin").onclick=()=>zi())}function Yn(){let e=a("settingsDetailBox"),t=localStorage.getItem("mk21_filter_load_live")!=="false",n=localStorage.getItem("mk21_filter_load_movies")!=="false",o=localStorage.getItem("mk21_filter_load_series")!=="false";e.innerHTML=`
+  `,r("cfgToggleAdult")&&(r("cfgToggleAdult").onclick=()=>{Ke?(Ke=!1,Ps(),te(),ae(),Q()):gc()}),r("cfgChangeAdultPin")&&(r("cfgChangeAdultPin").onclick=()=>Ns())}function ho(){let e=r("settingsDetailBox"),t=localStorage.getItem("mk21_filter_load_live")!=="false",n=localStorage.getItem("mk21_filter_load_movies")!=="false",o=localStorage.getItem("mk21_filter_load_series")!=="false";e.innerHTML=`
     <h3 style="margin:0 0 16px 0; font-size:24px;">📦 Filtrar Conteúdo ao Carregar</h3>
     <p style="color:#aaa;">Selecione quais tipos de conteúdo serão baixados do servidor para economizar memória e acelerar o app:</p>
     <div style="display:flex; flex-direction:column; gap:14px; max-width:560px; margin-top:20px;">
@@ -184,7 +184,7 @@
         💾 Salvar e Recarregar Servidor Agora
       </button>
     </div>
-  `,a("toggleFilterLive").onclick=()=>{localStorage.setItem("mk21_filter_load_live",!t),Yn()},a("toggleFilterMovies").onclick=()=>{localStorage.setItem("mk21_filter_load_movies",!n),Yn()},a("toggleFilterSeries").onclick=()=>{localStorage.setItem("mk21_filter_load_series",!o),Yn()},a("btnApplyLoadFilter").onclick=()=>{alert("Filtros salvos! Recarregando lista..."),ve(!0)}}function Hi(){let e=localStorage.getItem("mk21_stream_format")||"auto",t=a("settingsDetailBox");t.innerHTML=`
+  `,r("toggleFilterLive").onclick=()=>{localStorage.setItem("mk21_filter_load_live",!t),ho()},r("toggleFilterMovies").onclick=()=>{localStorage.setItem("mk21_filter_load_movies",!n),ho()},r("toggleFilterSeries").onclick=()=>{localStorage.setItem("mk21_filter_load_series",!o),ho()},r("btnApplyLoadFilter").onclick=()=>{alert("Filtros salvos! Recarregando lista..."),Ee(!0)}}function Rs(){let e=localStorage.getItem("mk21_stream_format")||"auto",t=r("settingsDetailBox");t.innerHTML=`
     <h3 style="margin:0 0 16px 0; font-size:24px;">📺 Alterar Formato de Fluxo</h3>
     <p style="color:#aaa;">Selecione o decodificador padrão para sua Smart TV LG:</p>
     <div style="display:flex; flex-direction:column; gap:12px; max-width:540px; margin-top:16px;">
@@ -201,9 +201,9 @@
     <div id="msgStreamFormatSaved" style="margin-top:16px; font-size:16px; font-weight:bold; color:#4caf50; display:none;">
       ✅ Formato de fluxo salvo com sucesso!
     </div>
-  `,a("btnFmtAuto").onclick=()=>Ta("auto"),a("btnFmtTs").onclick=()=>Ta("ts"),a("btnFmtHls").onclick=()=>Ta("hls")}function Ta(e){localStorage.setItem("mk21_stream_format",e),Hi();let t=a("msgStreamFormatSaved");t&&(t.style.display="block",setTimeout(()=>{t&&(t.style.display="none")},3e3))}function zi(){let e=a("settingsDetailBox");e.innerHTML=`
+  `,r("btnFmtAuto").onclick=()=>Zo("auto"),r("btnFmtTs").onclick=()=>Zo("ts"),r("btnFmtHls").onclick=()=>Zo("hls")}function Zo(e){localStorage.setItem("mk21_stream_format",e),Rs();let t=r("msgStreamFormatSaved");t&&(t.style.display="block",setTimeout(()=>{t&&(t.style.display="none")},3e3))}function Ns(){let e=r("settingsDetailBox");e.innerHTML=`
     <h3 style="margin:0 0 16px 0; font-size:24px;">🔒 Alterar Senha PIN de Adultos</h3>
-    <p style="color:#aaa;">Senha atual: <strong style="color:#ffd54f; font-size:18px;">${Ke}</strong></p>
+    <p style="color:#aaa;">Senha atual: <strong style="color:#ffd54f; font-size:18px;">${Qe}</strong></p>
     <div style="background:#1a1e2d; border-radius:12px; padding:20px; max-width:440px; margin-top:16px; text-align:center;">
       <div style="font-size:16px; color:#cbd5e1; margin-bottom:8px;">Digite o Novo PIN de 4 Dígitos:</div>
       <div id="newPinDisplay" class="pin-display-box" style="margin:8px auto; width:220px; font-size:32px;">----</div>
@@ -223,16 +223,16 @@
       </div>
       <div id="msgPinSaved" style="margin-top:14px; font-size:16px; font-weight:bold; color:#4caf50; display:none;"></div>
     </div>
-  `;let t="";e.querySelectorAll(".new-pin-k").forEach(n=>{n.onclick=()=>{let o=n.getAttribute("data-k");if(o==="C")t="",a("newPinDisplay").textContent="----";else if(o==="OK")if(t.length===4){Ke=t,localStorage.setItem("mk21_adult_pin",Ke);let i=a("msgPinSaved");i&&(i.textContent="✅ Novo PIN salvo com sucesso: "+Ke,i.style.display="block"),t=""}else alert("Digite os 4 dígitos antes de confirmar.");else if(t.length<4){t+=o;let i="";for(let r=0;r<4;r++)i+=r<t.length?"●":"-";if(a("newPinDisplay").textContent=i,t.length===4){Ke=t,localStorage.setItem("mk21_adult_pin",Ke);let r=a("msgPinSaved");r&&(r.textContent="✅ Novo PIN salvo com sucesso: "+Ke,r.style.display="block")}}}})}function Ui(){let e=a("settingsDetailBox");if(!e)return;let t=Za(),o=[{id:"off",title:"🔕 Desativado (Padrão)",desc:"Não exibe banners Toast flutuantes nem avisos sobrepostos ao trocar de canal."},{id:"player",title:"📺 Apenas no canto do player",desc:"Exibe somente a etiqueta discreta no canto superior do vídeo por alguns segundos."},{id:"all",title:"🔔 Completo (Player + Banner flutuante)",desc:"Exibe etiqueta no player e banner Toast flutuante para ações e troca de canal."}].map(i=>{let r=t===i.id;return`
-      <button type="button" class="list-item-btn cfg-toast-choice ${r?"active":""}" data-toast-mode="${i.id}" style="padding:16px 20px; display:flex; flex-direction:column; align-items:flex-start; gap:4px;" tabindex="0">
-        <span style="font-size:17px; font-weight:700; color:${r?"#4caf50":"#fff"};">${r?"✅":"⚪"} ${i.title}</span>
-        <span style="font-size:14px; color:#94a3b8; white-space:normal;">${i.desc}</span>
+  `;let t="";e.querySelectorAll(".new-pin-k").forEach(n=>{n.onclick=()=>{let o=n.getAttribute("data-k");if(o==="C")t="",r("newPinDisplay").textContent="----";else if(o==="OK")if(t.length===4){Qe=t,localStorage.setItem("mk21_adult_pin",Qe);let a=r("msgPinSaved");a&&(a.textContent="✅ Novo PIN salvo com sucesso: "+Qe,a.style.display="block"),t=""}else alert("Digite os 4 dígitos antes de confirmar.");else if(t.length<4){t+=o;let a="";for(let s=0;s<4;s++)a+=s<t.length?"●":"-";if(r("newPinDisplay").textContent=a,t.length===4){Qe=t,localStorage.setItem("mk21_adult_pin",Qe);let s=r("msgPinSaved");s&&(s.textContent="✅ Novo PIN salvo com sucesso: "+Qe,s.style.display="block")}}}})}function _s(){let e=r("settingsDetailBox");if(!e)return;let t=wa(),o=[{id:"off",title:"🔕 Desativado (Padrão)",desc:"Não exibe banners Toast flutuantes nem avisos sobrepostos ao trocar de canal."},{id:"player",title:"📺 Apenas no canto do player",desc:"Exibe somente a etiqueta discreta no canto superior do vídeo por alguns segundos."},{id:"all",title:"🔔 Completo (Player + Banner flutuante)",desc:"Exibe etiqueta no player e banner Toast flutuante para ações e troca de canal."}].map(a=>{let s=t===a.id;return`
+      <button type="button" class="list-item-btn cfg-toast-choice ${s?"active":""}" data-toast-mode="${a.id}" style="padding:16px 20px; display:flex; flex-direction:column; align-items:flex-start; gap:4px;" tabindex="0">
+        <span style="font-size:17px; font-weight:700; color:${s?"#4caf50":"#fff"};">${s?"✅":"⚪"} ${a.title}</span>
+        <span style="font-size:14px; color:#94a3b8; white-space:normal;">${a.desc}</span>
       </button>`}).join("");e.innerHTML=`
     <h3 style="margin:0 0 10px 0; font-size:24px;">🔔 Avisos na Tela (Toast)</h3>
     <p style="color:#aaa; margin:0 0 18px 0;">Controle se os banners de aviso (troca de canal, favoritos, ordenação e busca) aparecem sobre a tela.</p>
     <div style="display:flex; flex-direction:column; gap:10px; max-width:640px;">${o}</div>
     <div id="msgToastSaved" style="margin-top:16px; color:#4caf50; font-weight:bold; font-size:15px; display:none;"></div>
-  `,e.querySelectorAll(".cfg-toast-choice").forEach(i=>{i.onclick=()=>{let r=Ns(i.getAttribute("data-toast-mode"));Ui();let s=a("msgToastSaved");if(s){let c={off:"✅ Avisos Toast desativados na tela.",player:"✅ Modo discreto ativado (apenas no canto do player).",all:"✅ Avisos Toast completos ativados."};s.textContent=c[r]||"✅ Preferência salva.",s.style.display="block"}r==="all"&&E("🔔 Avisos Toast ativados")}})}async function Gi(){if(window.__mk21SpeedTestController)try{window.__mk21SpeedTestController.abort()}catch{}let e=typeof AbortController!="undefined"?new AbortController:null;window.__mk21SpeedTestController=e;let t=e?e.signal:void 0,n=a("settingsDetailBox");n.innerHTML=`
+  `,e.querySelectorAll(".cfg-toast-choice").forEach(a=>{a.onclick=()=>{let s=oc(a.getAttribute("data-toast-mode"));_s();let i=r("msgToastSaved");if(i){let c={off:"✅ Avisos Toast desativados na tela.",player:"✅ Modo discreto ativado (apenas no canto do player).",all:"✅ Avisos Toast completos ativados."};i.textContent=c[s]||"✅ Preferência salva.",i.style.display="block"}s==="all"&&E("🔔 Avisos Toast ativados")}})}async function Os(){if(window.__mk21SpeedTestController)try{window.__mk21SpeedTestController.abort()}catch{}let e=typeof AbortController!="undefined"?new AbortController:null;window.__mk21SpeedTestController=e;let t=e?e.signal:void 0,n=r("settingsDetailBox");n.innerHTML=`
     <h3 style="margin-top:0; font-size:24px;">🚀 Teste de Velocidade da Conexão</h3>
     <p style="color:#aaa;">Medição multi-conexão progressiva (padrão Fast.com / Open Connect CDN).</p>
     <div class="speed-meter-box">
@@ -246,7 +246,7 @@
         <div class="speed-metric-card"><div style="font-size:13px;color:#888;">⏱️ Latência</div><div id="speedMeterPing" style="font-size:20px;font-weight:bold;color:#ffd54f;">-- ms</div></div>
         <div class="speed-metric-card"><div style="font-size:13px;color:#888;">📶 Jitter</div><div id="speedMeterJitter" style="font-size:20px;font-weight:bold;color:#ff8a65;">-- ms</div></div>
       </div><div id="speedQualityRating" style="margin-top:16px;font-size:16px;font-weight:bold;color:#fff;"></div>
-    </div><button id="btnStartSpeedTest" class="ctrl-btn primary" style="padding:12px 28px;" tabindex="0">🔄 Iniciar Novo Teste</button>`,a("btnStartSpeedTest").onclick=Gi;let o=()=>window.__mk21SpeedTestController!==e||t&&t.aborted,i=h=>{!o()&&a("speedMeterStatus")&&(a("speedMeterStatus").textContent=h)},r=h=>{!o()&&a("speedProgressBar")&&(a("speedProgressBar").style.width=Math.max(0,Math.min(100,h))+"%")},s=h=>{if(o()||!a("speedGaugeArc"))return;let y=Math.max(0,Number(h)||0),M=Math.min(1,Math.log10(1+y)/Math.log10(1001));a("speedGaugeArc").style.transform=`rotate(${(-45+M*180).toFixed(1)}deg)`},c=h=>Number.isFinite(h)&&h>=0?h>=100?Math.round(h)+" Mbps":h.toFixed(1)+" Mbps":"Indisponível",u=h=>Number.isFinite(h)?h>=100?String(Math.round(h)):h.toFixed(1):"0.0",d=(localStorage.getItem("mk21_speed_test_endpoint")||"").trim().replace(/\/+$/,""),l=d||"https://speed.cloudflare.com";function f(h,y,M){let T=h.indexOf("?")!==-1?"&":"?";return`${h}${T}range=${y}-${M}&_=${Date.now()}_${Math.random().toString(36).slice(2,7)}`}async function p(){if(!d){let h="https://api.fast.com/netflix/speedtest/v2?https=true&token=YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm&urlCount=5";try{let y=typeof AbortController!="undefined"?new AbortController:null,M=y?setTimeout(()=>y.abort(),3500):null,T=()=>{y&&y.abort()};t&&y&&t.addEventListener("abort",T,{once:!0});try{let I=await fetch(h,{cache:"no-store",signal:y?y.signal:t});if(I.ok){let P=await I.json(),R=(Array.isArray(P&&P.targets)?P.targets:[]).map(k=>k&&typeof k.url=="string"?k.url.trim():"").filter(k=>/^https?:\/\//i.test(k));if(R.length>0)return{provider:"Fast.com (Netflix Open Connect)",mode:"fast",urls:R}}}finally{M&&clearTimeout(M),t&&y&&t.removeEventListener("abort",T)}}catch{}}return{provider:d?"Endpoint Personalizado":"Cloudflare CDN Multi-Conexão",mode:"cloudflare",urls:[l+"/__down"]}}async function m(h){let y=h.urls[0],M=h.mode==="fast"?f(y,0,0):`${y}?bytes=1&_=${Date.now()}_${Math.random().toString(36).slice(2,7)}`,T=performance.now(),I=await fetch(M,{cache:"no-store",signal:t});if(!I.ok)throw new Error("HTTP "+I.status);return await I.arrayBuffer(),performance.now()-T}try{let fo=function(D){let ee=Math.max(J+k,D-N),re=0,Ae=D;for(let pe=ue.length-1;pe>=0;pe--){let ce=ue[pe];if(ce.t<ee)break;re+=ce.b,ce.t<Ae&&(Ae=ce.t)}let W=(D-Ae)/1e3;return re<=0||W<.35?0:re*8/W/1e6},ua=function(D){let ee=J+k;if(D<=ee+500){let W=Math.max(.25,(D-J)/1e3);return Ee*8/W/1e6}let re=0;for(let W=0;W<ue.length;W++)ue[W].t>=ee&&(re+=ue[W].b);let Ae=(D-ee)/1e3;return Ae>0?re*8/Ae/1e6:0};var b=fo,g=ua;let h=await p();if(o())return;i(`1/4 - Medindo latência (${h.provider})...`),await m(h);let y=[];for(let D=0;D<5;D++){if(o())return;y.push(await m(h))}let M=y.slice().sort((D,ee)=>D-ee),T=M[Math.floor(M.length/2)],I=0;for(let D=1;D<y.length;D++)I+=Math.abs(y[D]-y[D-1]);let P=y.length>1?I/(y.length-1):0;a("speedMeterPing")&&(a("speedMeterPing").textContent=Math.round(T)+" ms"),a("speedMeterJitter")&&(a("speedMeterJitter").textContent=P.toFixed(1)+" ms"),r(12),i(`2/4 - Medindo download multi-conexão (${h.provider})...`);let B=Math.min(6,Math.max(4,h.urls.length)),R=8500,k=1e3,N=2500,O=typeof AbortController!="undefined"?new AbortController:null,Te=()=>{O&&O.abort()};t&&O&&t.addEventListener("abort",Te,{once:!0});let J=0,Ee=0,ue=[],fe=!1,Ht=0,mr=setInterval(()=>{if(o()||fe)return;let D=performance.now();if(J<=0)return;let ee=D-J,re=fo(D)||ua(D);re>Ht&&ee>k+800&&(Ht=re),a("speedMeterNumber")&&(a("speedMeterNumber").textContent=u(re)),a("speedMeterDownload")&&(a("speedMeterDownload").textContent=c(re)),s(re),r(12+Math.min(53,Math.round(ee/R*53)))},200),gr=setTimeout(()=>{if(fe=!0,O)try{O.abort()}catch{}},R);async function hr(D){let ee=0;for(;!fe&&!o();){let re=h.urls[(D+ee)%h.urls.length];ee++;let Ae=h.mode==="fast"?f(re,0,26214399):`${re}?bytes=25000000&_=${Date.now()}_${D}_${ee}`;try{let W=await fetch(Ae,{cache:"no-store",signal:O?O.signal:t});if(!W.ok)throw new Error("HTTP "+W.status);if(W.body&&typeof W.body.getReader=="function"){let pe=W.body.getReader();for(;!fe&&!o();){let ce=await pe.read();if(ce.done)break;let it=ce.value?ce.value.byteLength:0;if(it>0){let Ut=performance.now();J===0&&(J=Ut),Ee+=it,ue.push({t:Ut,b:it})}}try{await pe.cancel()}catch{}}else{let pe=await W.arrayBuffer(),ce=performance.now();J===0&&(J=ce),Ee+=pe.byteLength,ue.push({t:ce,b:pe.byteLength})}}catch(W){if(fe||o()||W&&W.name==="AbortError")break;await new Promise(pe=>setTimeout(pe,120))}}}try{let D=[];for(let ee=0;ee<B;ee++)D.push(hr(ee));await Promise.all(D)}finally{fe=!0,clearInterval(mr),clearTimeout(gr),t&&O&&t.removeEventListener("abort",Te)}if(o())return;if(Ee<=0||J<=0)throw new Error("Não foi possível baixar dados dos servidores de teste");let vr=performance.now(),yr=ua(vr),zt=Math.max(yr,Ht*.92);a("speedMeterNumber")&&(a("speedMeterNumber").textContent=u(zt)),a("speedMeterDownload")&&(a("speedMeterDownload").textContent=c(zt)),s(zt),r(68),i("3/4 - Medindo upload multi-conexão...");let dn=NaN;try{let D=typeof AbortController!="undefined"?new AbortController:null,ee=()=>{D&&D.abort()};t&&D&&t.addEventListener("abort",ee,{once:!0});let re=15e5,Ae=new Uint8Array(re);window.crypto&&crypto.getRandomValues&&crypto.getRandomValues(Ae.subarray(0,65536));let W=await fetch(`${l}/__up?_=${Date.now()}`,{method:"POST",body:Ae.subarray(0,16384),cache:"no-store",signal:D?D.signal:t,headers:{"Content-Type":"application/octet-stream"}});if(!W.ok)throw new Error("Upload bloqueado (HTTP "+W.status+")");try{await W.arrayBuffer()}catch{}let pe=!1,ce=0,it=performance.now(),Ut=4500,br=setTimeout(()=>{if(pe=!0,D)try{D.abort()}catch{}},Ut);async function fa(yt){let po=0;for(;!pe&&!o()&&performance.now()-it<Ut;){po++;let pa=await fetch(`${l}/__up?_=${Date.now()}_${yt}_${po}`,{method:"POST",body:Ae,cache:"no-store",signal:D?D.signal:t,headers:{"Content-Type":"application/octet-stream"}});if(!pa.ok)throw new Error("Upload HTTP "+pa.status);try{await pa.arrayBuffer()}catch{}ce+=re;let Er=Math.max(.25,(performance.now()-it)/1e3),xr=ce*8/Er/1e6;a("speedMeterUpload")&&(a("speedMeterUpload").textContent=c(xr)),r(68+Math.min(28,Math.round((performance.now()-it)/Ut*28)))}}try{await Promise.all([fa(0),fa(1),fa(2)])}catch(yt){if(!pe&&(!yt||yt.name!=="AbortError"))throw yt}finally{pe=!0,clearTimeout(br),t&&D&&t.removeEventListener("abort",ee)}let Sr=Math.max(.25,(performance.now()-it)/1e3);ce>0&&(dn=ce*8/Sr/1e6)}catch{dn=NaN}if(o())return;if(a("speedMeterUpload")&&(a("speedMeterUpload").textContent=c(dn)),a("speedMeterNumber")&&(a("speedMeterNumber").textContent=u(zt)),s(zt),r(100),i(`4/4 - ✅ Concluído (${h.provider})`),a("speedQualityRating")){let D=Number.isFinite(dn)?c(dn):"Indisponível";a("speedQualityRating").textContent=`Download ${c(zt)} • Upload ${D} • Latência ${Math.round(T)} ms • Jitter ${P.toFixed(1)} ms`}}catch(h){if(o())return;r(0),i("Teste não disponível nesta rede/TV"),a("speedQualityRating")&&(a("speedQualityRating").textContent="Não foi possível medir: "+(h&&h.message?h.message:"endpoint bloqueado")+".")}}function qi(){let e=localStorage.getItem("mk21_font_size")||"normal",t=a("settingsDetailBox");t.innerHTML=`
+    </div><button id="btnStartSpeedTest" class="ctrl-btn primary" style="padding:12px 28px;" tabindex="0">🔄 Iniciar Novo Teste</button>`,r("btnStartSpeedTest").onclick=Os;let o=()=>window.__mk21SpeedTestController!==e||t&&t.aborted,a=h=>{!o()&&r("speedMeterStatus")&&(r("speedMeterStatus").textContent=h)},s=h=>{!o()&&r("speedProgressBar")&&(r("speedProgressBar").style.width=Math.max(0,Math.min(100,h))+"%")},i=h=>{if(o()||!r("speedGaugeArc"))return;let b=Math.max(0,Number(h)||0),R=Math.min(1,Math.log10(1+b)/Math.log10(1001));r("speedGaugeArc").style.transform=`rotate(${(-45+R*180).toFixed(1)}deg)`},c=h=>Number.isFinite(h)&&h>=0?h>=100?Math.round(h)+" Mbps":h.toFixed(1)+" Mbps":"Indisponível",u=h=>Number.isFinite(h)?h>=100?String(Math.round(h)):h.toFixed(1):"0.0",d=(localStorage.getItem("mk21_speed_test_endpoint")||"").trim().replace(/\/+$/,""),l=d||"https://speed.cloudflare.com";function f(h,b,R){let w=h.indexOf("?")!==-1?"&":"?";return`${h}${w}range=${b}-${R}&_=${Date.now()}_${Math.random().toString(36).slice(2,7)}`}async function p(){if(!d){let h="https://api.fast.com/netflix/speedtest/v2?https=true&token=YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm&urlCount=5";try{let b=typeof AbortController!="undefined"?new AbortController:null,R=b?setTimeout(()=>b.abort(),3500):null,w=()=>{b&&b.abort()};t&&b&&t.addEventListener("abort",w,{once:!0});try{let M=await fetch(h,{cache:"no-store",signal:b?b.signal:t});if(M.ok){let A=await M.json(),_=(Array.isArray(A&&A.targets)?A.targets:[]).map(I=>I&&typeof I.url=="string"?I.url.trim():"").filter(I=>/^https?:\/\//i.test(I));if(_.length>0)return{provider:"Fast.com (Netflix Open Connect)",mode:"fast",urls:_}}}finally{R&&clearTimeout(R),t&&b&&t.removeEventListener("abort",w)}}catch{}}return{provider:d?"Endpoint Personalizado":"Cloudflare CDN Multi-Conexão",mode:"cloudflare",urls:[l+"/__down"]}}async function m(h){let b=h.urls[0],R=h.mode==="fast"?f(b,0,0):`${b}?bytes=1&_=${Date.now()}_${Math.random().toString(36).slice(2,7)}`,w=performance.now(),M=await fetch(R,{cache:"no-store",signal:t});if(!M.ok)throw new Error("HTTP "+M.status);return await M.arrayBuffer(),performance.now()-w}try{let Ha=function($){let oe=Math.max(ee+I,$-B),ce=0,Ne=$;for(let he=me.length-1;he>=0;he--){let pe=me[he];if(pe.t<oe)break;ce+=pe.b,pe.t<Ne&&(Ne=pe.t)}let Y=($-Ne)/1e3;return ce<=0||Y<.35?0:ce*8/Y/1e6},Bo=function($){let oe=ee+I;if($<=oe+500){let Y=Math.max(.25,($-ee)/1e3);return we*8/Y/1e6}let ce=0;for(let Y=0;Y<me.length;Y++)me[Y].t>=oe&&(ce+=me[Y].b);let Ne=($-oe)/1e3;return Ne>0?ce*8/Ne/1e6:0};var y=Ha,g=Bo;let h=await p();if(o())return;a(`1/4 - Medindo latência (${h.provider})...`),await m(h);let b=[];for(let $=0;$<5;$++){if(o())return;b.push(await m(h))}let R=b.slice().sort(($,oe)=>$-oe),w=R[Math.floor(R.length/2)],M=0;for(let $=1;$<b.length;$++)M+=Math.abs(b[$]-b[$-1]);let A=b.length>1?M/(b.length-1):0;r("speedMeterPing")&&(r("speedMeterPing").textContent=Math.round(w)+" ms"),r("speedMeterJitter")&&(r("speedMeterJitter").textContent=A.toFixed(1)+" ms"),s(12),a(`2/4 - Medindo download multi-conexão (${h.provider})...`);let O=Math.min(6,Math.max(4,h.urls.length)),_=8500,I=1e3,B=2500,V=typeof AbortController!="undefined"?new AbortController:null,Re=()=>{V&&V.abort()};t&&V&&t.addEventListener("abort",Re,{once:!0});let ee=0,we=0,me=[],ge=!1,Xt=0,mi=setInterval(()=>{if(o()||ge)return;let $=performance.now();if(ee<=0)return;let oe=$-ee,ce=Ha($)||Bo($);ce>Xt&&oe>I+800&&(Xt=ce),r("speedMeterNumber")&&(r("speedMeterNumber").textContent=u(ce)),r("speedMeterDownload")&&(r("speedMeterDownload").textContent=c(ce)),i(ce),s(12+Math.min(53,Math.round(oe/_*53)))},200),gi=setTimeout(()=>{if(ge=!0,V)try{V.abort()}catch{}},_);async function hi($){let oe=0;for(;!ge&&!o();){let ce=h.urls[($+oe)%h.urls.length];oe++;let Ne=h.mode==="fast"?f(ce,0,26214399):`${ce}?bytes=25000000&_=${Date.now()}_${$}_${oe}`;try{let Y=await fetch(Ne,{cache:"no-store",signal:V?V.signal:t});if(!Y.ok)throw new Error("HTTP "+Y.status);if(Y.body&&typeof Y.body.getReader=="function"){let he=Y.body.getReader();for(;!ge&&!o();){let pe=await he.read();if(pe.done)break;let dt=pe.value?pe.value.byteLength:0;if(dt>0){let Jt=performance.now();ee===0&&(ee=Jt),we+=dt,me.push({t:Jt,b:dt})}}try{await he.cancel()}catch{}}else{let he=await Y.arrayBuffer(),pe=performance.now();ee===0&&(ee=pe),we+=he.byteLength,me.push({t:pe,b:he.byteLength})}}catch(Y){if(ge||o()||Y&&Y.name==="AbortError")break;await new Promise(he=>setTimeout(he,120))}}}try{let $=[];for(let oe=0;oe<O;oe++)$.push(hi(oe));await Promise.all($)}finally{ge=!0,clearInterval(mi),clearTimeout(gi),t&&V&&t.removeEventListener("abort",Re)}if(o())return;if(we<=0||ee<=0)throw new Error("Não foi possível baixar dados dos servidores de teste");let vi=performance.now(),yi=Bo(vi),Yt=Math.max(yi,Xt*.92);r("speedMeterNumber")&&(r("speedMeterNumber").textContent=u(Yt)),r("speedMeterDownload")&&(r("speedMeterDownload").textContent=c(Yt)),i(Yt),s(68),a("3/4 - Medindo upload multi-conexão...");let xn=NaN;try{let $=typeof AbortController!="undefined"?new AbortController:null,oe=()=>{$&&$.abort()};t&&$&&t.addEventListener("abort",oe,{once:!0});let ce=15e5,Ne=new Uint8Array(ce);window.crypto&&crypto.getRandomValues&&crypto.getRandomValues(Ne.subarray(0,65536));let Y=await fetch(`${l}/__up?_=${Date.now()}`,{method:"POST",body:Ne.subarray(0,16384),cache:"no-store",signal:$?$.signal:t,headers:{"Content-Type":"application/octet-stream"}});if(!Y.ok)throw new Error("Upload bloqueado (HTTP "+Y.status+")");try{await Y.arrayBuffer()}catch{}let he=!1,pe=0,dt=performance.now(),Jt=4500,bi=setTimeout(()=>{if(he=!0,$)try{$.abort()}catch{}},Jt);async function Do(wt){let za=0;for(;!he&&!o()&&performance.now()-dt<Jt;){za++;let Fo=await fetch(`${l}/__up?_=${Date.now()}_${wt}_${za}`,{method:"POST",body:Ne,cache:"no-store",signal:$?$.signal:t,headers:{"Content-Type":"application/octet-stream"}});if(!Fo.ok)throw new Error("Upload HTTP "+Fo.status);try{await Fo.arrayBuffer()}catch{}pe+=ce;let Ei=Math.max(.25,(performance.now()-dt)/1e3),xi=pe*8/Ei/1e6;r("speedMeterUpload")&&(r("speedMeterUpload").textContent=c(xi)),s(68+Math.min(28,Math.round((performance.now()-dt)/Jt*28)))}}try{await Promise.all([Do(0),Do(1),Do(2)])}catch(wt){if(!he&&(!wt||wt.name!=="AbortError"))throw wt}finally{he=!0,clearTimeout(bi),t&&$&&t.removeEventListener("abort",oe)}let Si=Math.max(.25,(performance.now()-dt)/1e3);pe>0&&(xn=pe*8/Si/1e6)}catch{xn=NaN}if(o())return;if(r("speedMeterUpload")&&(r("speedMeterUpload").textContent=c(xn)),r("speedMeterNumber")&&(r("speedMeterNumber").textContent=u(Yt)),i(Yt),s(100),a(`4/4 - ✅ Concluído (${h.provider})`),r("speedQualityRating")){let $=Number.isFinite(xn)?c(xn):"Indisponível";r("speedQualityRating").textContent=`Download ${c(Yt)} • Upload ${$} • Latência ${Math.round(w)} ms • Jitter ${A.toFixed(1)} ms`}}catch(h){if(o())return;s(0),a("Teste não disponível nesta rede/TV"),r("speedQualityRating")&&(r("speedQualityRating").textContent="Não foi possível medir: "+(h&&h.message?h.message:"endpoint bloqueado")+".")}}function Bs(){let e=localStorage.getItem("mk21_font_size")||"normal",t=r("settingsDetailBox");t.innerHTML=`
     <h3 style="margin-top:0;">🔤 Tamanho da Fonte de Todos os Menus</h3>
     <p style="color:#aaa;">Ajusta o tamanho das fontes em toda a interface (canais, EPG, categorias e botões):</p>
     <div style="display:flex; flex-direction:column; gap:12px; max-width:480px; margin-top:16px;">
@@ -269,7 +269,7 @@
         6. Máxima Sofá Distante (+125% TV Grande)
       </button>
     </div>
-  `,a("btnFontNormal").onclick=()=>kt("normal"),a("btnFontLarge").onclick=()=>kt("large"),a("btnFontXLarge").onclick=()=>kt("xlarge"),a("btnFontXXLarge").onclick=()=>kt("xxlarge"),a("btnFontHuge").onclick=()=>kt("huge"),a("btnFontUltra").onclick=()=>kt("ultra")}function kt(e){document.body.classList.remove("font-large","font-xlarge","font-xxlarge","font-huge","font-ultra"),e==="large"&&document.body.classList.add("font-large"),e==="xlarge"&&document.body.classList.add("font-xlarge"),e==="xxlarge"&&document.body.classList.add("font-xxlarge"),e==="huge"&&document.body.classList.add("font-huge"),e==="ultra"&&document.body.classList.add("font-ultra"),localStorage.setItem("mk21_font_size",e),qi();let n=a({normal:"btnFontNormal",large:"btnFontLarge",xlarge:"btnFontXLarge",xxlarge:"btnFontXXLarge",huge:"btnFontHuge",ultra:"btnFontUltra"}[e]||"btnFontNormal");if(n)try{n.focus()}catch{}}function Yt(){let e=a("settingsDetailBox"),t=Array.from(z).filter(r=>w.LIVE.some(s=>s.url===r)).length,n=Array.from(z).filter(r=>w.MOVIE.some(s=>s.url===r)).length,o=Array.from(z).filter(r=>w.SERIES.some(s=>s.url===r)).length,i=G.length;e.innerHTML=`
+  `,r("btnFontNormal").onclick=()=>Nt("normal"),r("btnFontLarge").onclick=()=>Nt("large"),r("btnFontXLarge").onclick=()=>Nt("xlarge"),r("btnFontXXLarge").onclick=()=>Nt("xxlarge"),r("btnFontHuge").onclick=()=>Nt("huge"),r("btnFontUltra").onclick=()=>Nt("ultra")}function Nt(e){document.body.classList.remove("font-large","font-xlarge","font-xxlarge","font-huge","font-ultra"),e==="large"&&document.body.classList.add("font-large"),e==="xlarge"&&document.body.classList.add("font-xlarge"),e==="xxlarge"&&document.body.classList.add("font-xxlarge"),e==="huge"&&document.body.classList.add("font-huge"),e==="ultra"&&document.body.classList.add("font-ultra"),localStorage.setItem("mk21_font_size",e),Bs();let n=r({normal:"btnFontNormal",large:"btnFontLarge",xlarge:"btnFontXLarge",xxlarge:"btnFontXXLarge",huge:"btnFontHuge",ultra:"btnFontUltra"}[e]||"btnFontNormal");if(n)try{n.focus()}catch{}}function an(){let e=r("settingsDetailBox"),t=Array.from(G).filter(s=>C.LIVE.some(i=>i.url===s)).length,n=Array.from(G).filter(s=>C.MOVIE.some(i=>i.url===s)).length,o=Array.from(G).filter(s=>C.SERIES.some(i=>i.url===s)).length,a=K.length;e.innerHTML=`
     <h3 style="margin-top:0; font-size:24px;">🗑️ Limpar Armazenamento</h3>
     <p style="color:#aaa;">Selecione os dados armazenados que deseja apagar da TV:</p>
     <div style="display:flex; flex-direction:column; gap:14px; max-width:620px; margin-top:16px;">
@@ -305,9 +305,9 @@
       </div>
 
     </div>
-  `,a("btnClearFavChannels").onclick=()=>{z=new Set(Array.from(z).filter(r=>!w.LIVE.some(s=>s.url===r)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(z)))}catch{}Yt(),alert("Canais favoritos removidos com sucesso.")},a("btnClearFavMovies").onclick=()=>{z=new Set(Array.from(z).filter(r=>!w.MOVIE.some(s=>s.url===r)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(z)))}catch{}Yt(),alert("Filmes favoritos removidos com sucesso.")},a("btnClearFavSeries").onclick=()=>{z=new Set(Array.from(z).filter(r=>!w.SERIES.some(s=>s.url===r)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(z)))}catch{}Yt(),alert("Séries favoritas removidas com sucesso.")},a("btnClearWatchedMovies").onclick=()=>{G=G.filter(r=>r.contentType!=="MOVIE");try{localStorage.setItem("mk21_continue_watching",JSON.stringify(G))}catch{}Yt(),alert("Histórico de filmes assistidos esvaziado.")},a("btnClearWatchedSeries").onclick=()=>{G=G.filter(r=>r.contentType!=="SERIES");try{localStorage.setItem("mk21_continue_watching",JSON.stringify(G))}catch{}Yt(),alert("Histórico de séries assistidas esvaziado.")},a("btnClearAllStorage").onclick=async()=>{if(confirm(`Apagar cache da lista, favoritos e históricos?
-(As credenciais de login serão mantidas.)`))try{let r={mk21_credentials:localStorage.getItem("mk21_credentials"),mk21_username:localStorage.getItem("mk21_username"),mk21_password:localStorage.getItem("mk21_password"),mk21_user:localStorage.getItem("mk21_user"),mk21_pass:localStorage.getItem("mk21_pass"),mk21_servers_list:localStorage.getItem("mk21_servers_list"),mk21_last_server:localStorage.getItem("mk21_last_server")},s=[];for(let c=0;c<localStorage.length;c++)s.push(localStorage.key(c));s.forEach(function(c){c&&(c.indexOf("mk21_")===0||c.indexOf("iptv_")===0)&&localStorage.removeItem(c)}),Object.keys(r).forEach(function(c){r[c]!=null&&localStorage.setItem(c,r[c])});try{es()}catch{}localStorage.removeItem("mk21_ota_app_js"),localStorage.removeItem("mk21_ota_styles_css"),await new Promise(function(c){try{let u=[ri,ci],d=u.length,l=function(){d--,d<=0&&c()};u.forEach(function(f){try{let p=indexedDB.deleteDatabase(f);p.onsuccess=l,p.onerror=l,p.onblocked=l}catch{l()}})}catch{c()}}),w={LIVE:[],MOVIE:[],SERIES:[]},z=new Set,G=[],alert(`Cache e históricos limpos. Credenciais mantidas.
-Recarregando...`),location.reload()}catch(r){alert("Erro ao limpar: "+(r&&r.message?r.message:r))}}}function Vs(){let e=localStorage.getItem("mk21_timezone")||"America/Sao_Paulo",t=localStorage.getItem("mk21_24h")!=="false",n=localStorage.getItem("mk21_autosync")!=="false",o=a("settingsDetailBox");o.innerHTML=`
+  `,r("btnClearFavChannels").onclick=()=>{G=new Set(Array.from(G).filter(s=>!C.LIVE.some(i=>i.url===s)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(G)))}catch{}an(),alert("Canais favoritos removidos com sucesso.")},r("btnClearFavMovies").onclick=()=>{G=new Set(Array.from(G).filter(s=>!C.MOVIE.some(i=>i.url===s)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(G)))}catch{}an(),alert("Filmes favoritos removidos com sucesso.")},r("btnClearFavSeries").onclick=()=>{G=new Set(Array.from(G).filter(s=>!C.SERIES.some(i=>i.url===s)));try{localStorage.setItem("mk21_favs",JSON.stringify(Array.from(G)))}catch{}an(),alert("Séries favoritas removidas com sucesso.")},r("btnClearWatchedMovies").onclick=()=>{K=K.filter(s=>s.contentType!=="MOVIE");try{localStorage.setItem("mk21_continue_watching",JSON.stringify(K))}catch{}an(),alert("Histórico de filmes assistidos esvaziado.")},r("btnClearWatchedSeries").onclick=()=>{K=K.filter(s=>s.contentType!=="SERIES");try{localStorage.setItem("mk21_continue_watching",JSON.stringify(K))}catch{}an(),alert("Histórico de séries assistidas esvaziado.")},r("btnClearAllStorage").onclick=async()=>{if(confirm(`Apagar cache da lista, favoritos e históricos?
+(As credenciais de login serão mantidas.)`))try{let s={mk21_credentials:localStorage.getItem("mk21_credentials"),mk21_username:localStorage.getItem("mk21_username"),mk21_password:localStorage.getItem("mk21_password"),mk21_user:localStorage.getItem("mk21_user"),mk21_pass:localStorage.getItem("mk21_pass"),mk21_servers_list:localStorage.getItem("mk21_servers_list"),mk21_last_server:localStorage.getItem("mk21_last_server")},i=[];for(let c=0;c<localStorage.length;c++)i.push(localStorage.key(c));i.forEach(function(c){c&&(c.indexOf("mk21_")===0||c.indexOf("iptv_")===0)&&localStorage.removeItem(c)}),Object.keys(s).forEach(function(c){s[c]!=null&&localStorage.setItem(c,s[c])});try{Cl()}catch{}localStorage.removeItem("mk21_ota_app_js"),localStorage.removeItem("mk21_ota_styles_css"),await new Promise(function(c){try{let u=[Xr,Qr],d=u.length,l=function(){d--,d<=0&&c()};u.forEach(function(f){try{let p=indexedDB.deleteDatabase(f);p.onsuccess=l,p.onerror=l,p.onblocked=l}catch{l()}})}catch{c()}}),C={LIVE:[],MOVIE:[],SERIES:[]},G=new Set,K=[],alert(`Cache e históricos limpos. Credenciais mantidas.
+Recarregando...`),location.reload()}catch(s){alert("Erro ao limpar: "+(s&&s.message?s.message:s))}}}function ic(){let e=localStorage.getItem("mk21_timezone")||"America/Sao_Paulo",t=localStorage.getItem("mk21_24h")!=="false",n=localStorage.getItem("mk21_autosync")!=="false",o=r("settingsDetailBox");o.innerHTML=`
     <h3 style="margin-top:0; font-size:24px;">⏱️ Configurações de Tempo e Fuso Horário</h3>
     <p style="color:#aaa;">Ajuste o relógio e fuso para sincronização precisa da programação EPG:</p>
     <div style="display:flex; flex-direction:column; gap:16px; max-width:550px; margin-top:20px;">
@@ -334,7 +334,7 @@ Recarregando...`),location.reload()}catch(r){alert("Erro ao limpar: "+(r&&r.mess
       <div id="msgTimeSaved" style="font-size:16px; font-weight:bold; color:#4caf50; display:none;">✅ Configurações de tempo salvas com sucesso!</div>
 
     </div>
-  `,a("btnSaveTimeSettings").onclick=()=>{localStorage.setItem("mk21_timezone",a("selTimezone").value),localStorage.setItem("mk21_24h",a("chk24Hours").checked);let i=a("msgTimeSaved");i&&(i.style.display="block",setTimeout(()=>{i&&(i.style.display="none")},3e3))}}function $a(){let e=a("settingsDetailBox"),t=Ze.filter(o=>o!=="ALL"),n=[];try{n=JSON.parse(localStorage.getItem("mk21_hidden_categories")||"[]")}catch{}e.innerHTML=`
+  `,r("btnSaveTimeSettings").onclick=()=>{localStorage.setItem("mk21_timezone",r("selTimezone").value),localStorage.setItem("mk21_24h",r("chk24Hours").checked);let a=r("msgTimeSaved");a&&(a.style.display="block",setTimeout(()=>{a&&(a.style.display="none")},3e3))}}function fa(){let e=r("settingsDetailBox"),t=ot.filter(o=>o!=="ALL"),n=[];try{n=JSON.parse(localStorage.getItem("mk21_hidden_categories")||"[]")}catch{}e.innerHTML=`
     <h3 style="margin-top:0; font-size:24px;">📁 Gerenciar Categorias</h3>
     <p style="color:#aaa;">Ative ou desative as categorias que deseja visualizar no menu lateral:</p>
     <div style="display:flex; gap:12px; margin-bottom:12px;">
@@ -342,21 +342,21 @@ Recarregando...`),location.reload()}catch(r){alert("Erro ao limpar: "+(r&&r.mess
       <button id="btnHideEmptyCats" class="ctrl-btn" style="padding:8px 16px; font-size:14px;" tabindex="0">🙈 Ocultar Vazias</button>
     </div>
     <div id="catItemsListWrap" style="max-height:380px; overflow-y:auto; display:flex; flex-direction:column; gap:8px;">
-      ${t.map((o,i)=>{let r=!n.includes(o),s=(he[o]||[]).length;return`
+      ${t.map((o,a)=>{let s=!n.includes(o),i=(be[o]||[]).length;return`
           <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:12px 18px; border-radius:8px;">
-            <span style="font-size:16px; color:#fff;">${o} <span style="color:#ffd54f; font-size:13px;">(${s} canais)</span></span>
-            <input type="checkbox" class="chk-cat-toggle" data-cat="${encodeURIComponent(o)}" ${r?"checked":""} style="width:22px; height:22px;">
+            <span style="font-size:16px; color:#fff;">${o} <span style="color:#ffd54f; font-size:13px;">(${i} canais)</span></span>
+            <input type="checkbox" class="chk-cat-toggle" data-cat="${encodeURIComponent(o)}" ${s?"checked":""} style="width:22px; height:22px;">
           </div>
         `}).join("")}
     </div>
     <div id="msgCatSaved" style="margin-top:12px; font-size:15px; font-weight:bold; color:#4caf50; display:none;">✅ Categorias atualizadas no menu lateral!</div>
-  `,e.querySelectorAll(".chk-cat-toggle").forEach(o=>{o.onchange=()=>{let i=decodeURIComponent(o.getAttribute("data-cat"));o.checked?n=n.filter(s=>s!==i):n.includes(i)||n.push(i),localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),Q(),te();let r=a("msgCatSaved");r&&(r.style.display="block",setTimeout(()=>{r&&(r.style.display="none")},2500))}}),a("btnShowAllCats").onclick=()=>{n=[],localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),$a(),Q(),te()},a("btnHideEmptyCats").onclick=()=>{n=t.filter(o=>(he[o]||[]).length===0),localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),$a(),Q(),te()}}function Ft(){try{localStorage.setItem("mk21_servers_list",JSON.stringify(L))}catch{}}function oo(){if(!a("modalApkRadioPicker")){let e=document.createElement("div");e.id="modalApkRadioPicker",e.className="app-modal hidden",e.innerHTML='<div class="apk-dialog-card"><h3 id="apkRadioTitle" class="apk-dialog-title">Selecionar Opção</h3><div id="apkRadioOptions" class="apk-radio-list"></div><div class="apk-dialog-footer"><button type="button" id="btnCancelApkRadio" class="apk-btn-cancel" tabindex="0">CANCELAR</button></div></div>',document.body.appendChild(e)}if(!a("modalStagedLoading")){let e=document.createElement("div");e.id="modalStagedLoading",e.className="app-modal hidden",e.innerHTML='<div class="apk-dialog-card"><h3 class="apk-dialog-title">Carregamento em Etapas</h3><p class="apk-dialog-desc">Selecione quais conteúdos carregar em Primeiro Plano. Os demais serão carregados em Segundo Plano de forma silenciosa e incremental para não travar o aplicativo.</p><div class="apk-staged-list"><button type="button" id="btnStageLive" class="apk-staged-row" data-stage="live" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Canais (Ao Vivo)</strong><span class="apk-staged-sub">Carga instantânea de canais de TV</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button><button type="button" id="btnStageMovies" class="apk-staged-row" data-stage="movies" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Filmes (VOD)</strong><span class="apk-staged-sub">Carregar catálogo de filmes na inicialização</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button><button type="button" id="btnStageSeries" class="apk-staged-row" data-stage="series" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Séries (VOD)</strong><span class="apk-staged-sub">Carregar catálogo de séries na inicialização</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button></div><div class="apk-dialog-actions-row"><button type="button" id="btnCancelStagedLoading" class="apk-btn-cancel" tabindex="0">CANCELAR</button><button type="button" id="btnSaveStagedLoading" class="apk-btn-save" tabindex="0">SALVAR</button></div></div>',document.body.appendChild(e)}}function at(){let e=Vt(),t=Ce.BG_SYNC_LABELS[e]||"Uma vez ao dia",n=Rn()?"Todas em 2º plano":"Apenas lista ativa",o=tt()?"Ocultar (Silencioso)":"Mostrar na tela inicial",i=qa();a("txtSrvSyncFreqVal")&&(a("txtSrvSyncFreqVal").textContent=t),a("txtSrvSyncScopeVal")&&(a("txtSrvSyncScopeVal").textContent=n),a("txtSrvSyncProgressVal")&&(a("txtSrvSyncProgressVal").textContent=o),a("txtSrvStagedLoadVal")&&(a("txtSrvStagedLoadVal").textContent=i);let r=L[_]||L[0];a("txtServerManagerActiveBadge")&&r&&(a("txtServerManagerActiveBadge").textContent="Ativa: "+X(r.name))}function na(e,t){if(e){if(e.classList.add("hidden"),ye(e,!1),e._onCloseCallback=null,t&&typeof t.focus=="function"&&document.body.contains(t))setTimeout(()=>{try{t.focus()}catch{}},20);else if(a("modalServerPicker")&&!a("modalServerPicker").classList.contains("hidden")){A="modalServerPicker";let n=a("modalServerPicker").querySelector("button:not([disabled])");if(n)try{n.focus()}catch{}}}}function io({title:e,options:t,currentValue:n,onSelect:o,returnFocusEl:i}){oo();let r=a("modalApkRadioPicker");if(!r)return;ye(r,!0),a("apkRadioTitle")&&(a("apkRadioTitle").textContent=e||"Selecionar Opção");let s=a("apkRadioOptions");s&&(s.innerHTML="",t.forEach(d=>{let l=document.createElement("button");l.type="button",l.className="apk-radio-option"+(d.value===n?" active":""),l.setAttribute("tabindex","0");let f=document.createElement("span");f.className="apk-radio-dot",f.setAttribute("aria-hidden","true");let p=document.createElement("span");p.className="apk-radio-label",p.textContent=d.label,l.appendChild(f),l.appendChild(p),l.onclick=()=>{typeof o=="function"&&o(d.value,d.label),na(r,i)},s.appendChild(l)}));let c=()=>na(r,i);r._onCloseCallback=c,a("btnCancelApkRadio")&&(a("btnCancelApkRadio").onclick=c),r.classList.remove("hidden");let u=s?s.querySelector(".apk-radio-option.active")||s.querySelector(".apk-radio-option"):null;u&&setTimeout(()=>{try{u.focus()}catch{}},20)}function Wi(e,t){io({title:"Frequência de Sincronização",currentValue:Vt(),returnFocusEl:e,options:[{value:"startup",label:"A cada inicialização"},{value:"daily",label:"Uma vez ao dia"},{value:"weekly",label:"Uma vez por semana"},{value:"manual",label:"Desativado (Apenas manual)"}],onSelect:(n,o)=>{Wr(n),We(),typeof t=="function"&&t(),E("🔄 Frequência salva: "+o)}})}function ji(e,t){io({title:"Sincronizar Todas as Listas",currentValue:Rn()?"all":"active",returnFocusEl:e,options:[{value:"all",label:"Sincronizar Todas em 2º Plano"},{value:"active",label:"Sincronizar Apenas Lista Ativa"}],onSelect:(n,o)=>{jr(n==="all"),We(),typeof t=="function"&&t(),E("🌐 Modo de sincronização: "+o)}})}function Ki(e,t){io({title:"Progresso em Segundo Plano",currentValue:tt()?"hide":"show",returnFocusEl:e,options:[{value:"show",label:"Mostrar Progresso na Tela Inicial"},{value:"hide",label:"Ocultar Progresso (Silencioso)"}],onSelect:(n,o)=>{Kr(n==="hide"),We(),typeof t=="function"&&t(),E("👁️ Progresso: "+o)}})}function Yi(e,t){oo();let n=a("modalStagedLoading");if(!n)return;ye(n,!0);let o={...Ga()},i=a("btnStageLive"),r=a("btnStageMovies"),s=a("btnStageSeries"),c=()=>{i&&i.classList.toggle("checked",!!o.live),r&&r.classList.toggle("checked",!!o.movies),s&&s.classList.toggle("checked",!!o.series)},u=l=>{let f=!o[l];if(!((l==="live"?f:o.live)||(l==="movies"?f:o.movies)||(l==="series"?f:o.series))){E("⚠️ Mantenha ao menos um tipo selecionado em 1º Plano");return}o[l]=f,c()};i&&(i.onclick=()=>u("live")),r&&(r.onclick=()=>u("movies")),s&&(s.onclick=()=>u("series")),c();let d=()=>na(n,e);n._onCloseCallback=d,a("btnCancelStagedLoading")&&(a("btnCancelStagedLoading").onclick=d),a("btnSaveStagedLoading")&&(a("btnSaveStagedLoading").onclick=()=>{let l=Yr(o);We(),na(n,e),typeof t=="function"&&t(),E("📑 Carregamento em etapas salvo ("+qa(l)+")")}),n.classList.remove("hidden"),i&&setTimeout(()=>{try{i.focus()}catch{}},20)}async function Xi(){for(let e=0;e<L.length;e++){let t=L[e];if(!t||!t.id)continue;if(e===_&&w.LIVE.length+w.MOVIE.length+w.SERIES.length>0){nt(t.id,w,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl);continue}let n=Wa(t.id);if(n&&n.totalChannels+n.totalVOD+n.totalSeries>0){ta(t.id,n);continue}try{let o=await an(t.id);o&&(o.LIVE&&o.LIVE.length||o.MOVIE&&o.MOVIE.length||o.SERIES&&o.SERIES.length)&&nt(t.id,o,null,!1)}catch{}}}async function ro(e,t){let n=L[e];if(!n)return!1;if(e===_){t&&(t.textContent="⏳ Sync..."),E("🔄 Sincronizando fonte ativa ("+X(n.name)+")...");try{await ve(!0,{silent:!0})}finally{t&&document.body.contains(t)&&(t.textContent="🔄 Sync")}return!0}t&&(t.disabled=!0,t.textContent="⏳ ...");let o=typeof AbortController!="undefined"?new AbortController:null,i=o?setTimeout(()=>o.abort(),5e4):null;try{let r={LIVE:[],MOVIE:[],SERIES:[]},s=await Ma(n.id),c=window.MK21AlternativeSources?MK21AlternativeSources.createGrouper():null;await Li(n.url,o&&o.signal,d=>{let l=en(d),f=l.contentType;if(l.isAdult=!!(l.isAdult||ne(l.name)||ne(l.group)),s.add(l),r[f])if(f==="LIVE"&&c){let p=c.add(l);p.isNew&&r[f].push(p.item)}else r[f].push(l)},null);let u=r.LIVE.length+r.MOVIE.length+r.SERIES.length;if(u>0){await s.finish(),En(n.id,r.LIVE,!0),await li(n.id,r);try{localStorage.setItem("mk21_last_sync_"+n.id,String(Date.now()))}catch{}let d=nt(n.id,r,null,!1);ta(n.id,d),x("[Sync Lista] "+X(n.name)+": "+u+" itens salvos.","success"),tt()||E("✓ "+X(n.name)+": "+u.toLocaleString("pt-BR")+" itens sincronizados")}return!0}catch(r){return x("[Sync Lista] Falha em "+X(n.name)+": "+Ce.formatFriendlyLoadError(r),"warn"),t&&!tt()&&E("⚠️ Falha ao sincronizar "+X(n.name)),!1}finally{i&&clearTimeout(i),t&&document.body.contains(t)&&(t.disabled=!1,t.textContent="🔄 Sync")}}async function la(e=!1){if(xn||!Z.isLoggedIn)return;let t=Vt();if(!e&&t==="manual")return;xn=!0;let n=a("btnSyncAllServersNow")||a("cfgSyncAllServersNow");n&&(n.disabled=!0,n.textContent="⏳ Sincronizando...");try{let o=e||Rn(),i=[_];if(o)for(let r=0;r<L.length;r++)r!==_&&i.push(r);e&&!tt()&&E("🔄 Sincronizando "+i.length+" servidor(es) em segundo plano...");for(let r=0;r<i.length;r++){let s=i[r],c=L[s];if(!c)continue;let u=0;try{u=parseInt(localStorage.getItem("mk21_last_sync_"+c.id)||"0",10)||0}catch{}!e&&!Ce.shouldSyncServer(u,t)||(await ro(s,null),r<i.length-1&&await new Promise(d=>setTimeout(d,2500)))}a("modalServerPicker")&&!a("modalServerPicker").classList.contains("hidden")&&ca(),S==="SETTINGS"&&a("cfgOpenServerManager")&&ft()}finally{xn=!1,a("btnSyncAllServersNow")&&(a("btnSyncAllServersNow").disabled=!1,a("btnSyncAllServersNow").textContent="🔄 Sincronizar Todas Agora"),a("cfgSyncAllServersNow")&&(a("cfgSyncAllServersNow").disabled=!1,a("cfgSyncAllServersNow").textContent="🔄 Sincronizar Todas Agora")}}function $s(){oo();let e=a("modalServerPicker");if(!e)return;let t=e.querySelector(".server-manager-modal");t&&!t.classList.contains("server-manager-maximized")&&t.classList.add("server-manager-maximized");let n=e.querySelector(".server-manager-body");if(!n)return;if(!a("serverMetricsSummary")){let i=document.createElement("div");i.id="serverMetricsSummary",i.className="server-metrics-grid",i.setAttribute("aria-live","polite"),i.innerHTML='<div class="server-metric-card"><span class="server-metric-label">📺 Canais (Geral)</span><strong id="metricTotalChannels" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">🎬 Filmes VOD (Geral)</span><strong id="metricTotalVod" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">🍿 Séries (Geral)</span><strong id="metricTotalSeries" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">📡 EPG / Grupos</span><strong id="metricTotalEpg" class="server-metric-value">0</strong></div>',n.insertBefore(i,n.firstChild)}let o=a("serverBgSyncControl");if(!o||!o.classList.contains("server-apk-sync-grid")){let i=o||document.createElement("div");if(i.id="serverBgSyncControl",i.className="server-apk-sync-grid",i.innerHTML='<button type="button" id="btnSrvCfgSyncFreq" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">🔄</span><span class="apk-setting-texts"><strong class="apk-setting-title">Frequência de Sincronização</strong><span id="txtSrvSyncFreqVal" class="apk-setting-sub">Uma vez ao dia</span></span></button><button type="button" id="btnSrvCfgSyncScope" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">🌐</span><span class="apk-setting-texts"><strong class="apk-setting-title">Sincronizar Todas as Listas</strong><span id="txtSrvSyncScopeVal" class="apk-setting-sub">Todas em 2º plano</span></span></button><button type="button" id="btnSrvCfgSyncProgress" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">👁️</span><span class="apk-setting-texts"><strong class="apk-setting-title">Progresso em Segundo Plano</strong><span id="txtSrvSyncProgressVal" class="apk-setting-sub">Mostrar na tela inicial</span></span></button><button type="button" id="btnSrvCfgStagedLoad" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">📑</span><span class="apk-setting-texts"><strong class="apk-setting-title">Carregamento em Etapas</strong><span id="txtSrvStagedLoadVal" class="apk-setting-sub">1º Plano: Canais</span></span></button>',!o){let r=a("serverMetricsSummary");r&&r.nextSibling?n.insertBefore(i,r.nextSibling):n.insertBefore(i,n.firstChild)}}a("btnSrvCfgSyncFreq")&&!a("btnSrvCfgSyncFreq").dataset.mk21Bound&&(a("btnSrvCfgSyncFreq").dataset.mk21Bound="1",a("btnSrvCfgSyncFreq").onclick=()=>Wi(a("btnSrvCfgSyncFreq"))),a("btnSrvCfgSyncScope")&&!a("btnSrvCfgSyncScope").dataset.mk21Bound&&(a("btnSrvCfgSyncScope").dataset.mk21Bound="1",a("btnSrvCfgSyncScope").onclick=()=>ji(a("btnSrvCfgSyncScope"))),a("btnSrvCfgSyncProgress")&&!a("btnSrvCfgSyncProgress").dataset.mk21Bound&&(a("btnSrvCfgSyncProgress").dataset.mk21Bound="1",a("btnSrvCfgSyncProgress").onclick=()=>Ki(a("btnSrvCfgSyncProgress"))),a("btnSrvCfgStagedLoad")&&!a("btnSrvCfgStagedLoad").dataset.mk21Bound&&(a("btnSrvCfgStagedLoad").dataset.mk21Bound="1",a("btnSrvCfgStagedLoad").onclick=()=>Yi(a("btnSrvCfgStagedLoad"))),a("btnSyncAllServersNow")&&!a("btnSyncAllServersNow").dataset.mk21Bound&&(a("btnSyncAllServersNow").dataset.mk21Bound="1",a("btnSyncAllServersNow").onclick=()=>la(!0))}function We(){$s();let e=ja();a("metricTotalChannels")&&(a("metricTotalChannels").textContent=e.totalChannels.toLocaleString("pt-BR")),a("metricTotalVod")&&(a("metricTotalVod").textContent=e.totalVOD.toLocaleString("pt-BR")),a("metricTotalSeries")&&(a("metricTotalSeries").textContent=e.totalSeries.toLocaleString("pt-BR")),a("metricTotalEpg")&&(a("metricTotalEpg").textContent=e.totalEPG>0?e.totalEPG.toLocaleString("pt-BR")+" EPG":e.totalGroups.toLocaleString("pt-BR")+" grupos"),at();let t=L[_],n=a("txtServerSyncLast");if(n){let o=0;try{t&&(o=parseInt(localStorage.getItem("mk21_last_sync_"+t.id)||"0",10)||0)}catch{}o>0?n.textContent="Totais por servidor • Ativa sinc.: "+to(o):n.textContent="Totais individuais por servidor"}}function ot(){let e=a("modalServerPicker");if(!e)return;ye(e,!0),We(),ca(),e.classList.remove("hidden"),A="modalServerPicker",as=0;let t=a("serverItemsGrid");t&&typeof q=="function"&&setTimeout(()=>q(t),40),Xi();let n=t?t.querySelector(".btn-server-connect"):null;n&&n.focus()}function ca(){We();let e=a("serverItemsGrid");e.innerHTML="";let t=ja();L.forEach((n,o)=>{let i=o===_,r=document.createElement("div");r.className="server-manager-row"+(i?" active-server":"");let s=document.createElement("div");s.className="server-info-col";let c=document.createElement("div");c.className="server-info-top-line";let u=document.createElement("span");u.className="server-info-name",u.textContent=X(n.name)+(i?"  [✓ ATIVO]":""),c.appendChild(u);let d=0;try{d=parseInt(localStorage.getItem("mk21_last_sync_"+n.id)||"0",10)||0}catch{}let l=document.createElement("span");l.className="server-row-sync-meta",l.textContent="• "+to(d),c.appendChild(l);let f=document.createElement("div");f.className="server-info-url",f.textContent=n.url;let p=document.createElement("div");p.className="server-row-metrics",p.setAttribute("data-srv-metrics-id",n.id);let m=i&&t.totalItems>0?t:Wa(n.id);p.innerHTML=no(m),s.appendChild(c),s.appendChild(f),s.appendChild(p);let b=document.createElement("div");b.className="server-row-actions";let g=document.createElement("button");g.type="button",g.className="btn-server-sync-one",g.setAttribute("tabindex","0"),g.textContent="🔄 Sync",g.onclick=()=>ro(o,g);let h=document.createElement("button");h.type="button",h.className="btn-server-connect"+(i?" active":""),h.setAttribute("tabindex","0"),h.textContent=i?"✓ Ativo":"🔗 Conectar",h.onclick=()=>{_=o;try{localStorage.setItem("mk21_last_server",o)}catch{}$t({preserveCatalog:!0}),h.textContent="25%...",setTimeout(()=>{h.textContent="65%..."},200),setTimeout(()=>{a("modalServerPicker").classList.add("hidden"),ye(a("modalServerPicker"),!1),A="channels",v=null,ve(!1)},450)};let y=document.createElement("button");y.type="button",y.className="btn-server-edit",y.setAttribute("tabindex","0"),y.textContent="✏️",y.title="Alterar servidor",y.onclick=()=>Hs(o);let M=document.createElement("button");M.type="button",M.className="btn-server-delete",M.setAttribute("tabindex","0"),M.textContent="🗑️",M.title="Excluir servidor",M.onclick=()=>zs(o),b.appendChild(g),b.appendChild(h),b.appendChild(y),b.appendChild(M),r.appendChild(s),r.appendChild(b),e.appendChild(r)}),typeof q=="function"&&q(e)}function Hs(e){let t=L[e];t&&(a("txtAddServerTitle").textContent=`✏️ Alterar Servidor: ${t.name.replace(/^⭐\s*/,"")}`,a("inputEditServerIndex").value=e,a("inputNewServerName").value=t.name.replace(/^⭐\s*/,""),a("inputNewServerUrl").value=t.url,a("btnAddServerSubmit").textContent="💾 Salvar Alterações",a("btnCancelEditServer").classList.remove("hidden"),a("inputNewServerName").focus(),a("inputNewServerName").scrollIntoView({behavior:"smooth",block:"center"}))}a("btnCancelEditServer").onclick=()=>{a("txtAddServerTitle").textContent="➕ Adicionar Novo Servidor / Lista Manual",a("inputEditServerIndex").value="-1",a("inputNewServerName").value="",a("inputNewServerUrl").value="",a("btnAddServerSubmit").textContent="💾 Salvar e Conectar",a("btnCancelEditServer").classList.add("hidden")};function zs(e){if(L.length<=1){alert("Você deve manter pelo menos um servidor cadastrado.");return}let t=L[e].name;L.splice(e,1),_>=L.length&&(_=0),Ft(),ca(),alert(`Servidor "${t}" excluído.`)}a("btnAddServerSubmit").onclick=()=>{let e=parseInt(a("inputEditServerIndex").value,10),t=a("inputNewServerName").value.trim(),n=a("inputNewServerUrl").value.trim();if(!t){alert("Informe o nome do servidor.");return}if(!n.startsWith("http")){alert("URL inválida. Deve iniciar com http:// ou https://");return}if(e>=0&&e<L.length){L[e].name="⭐ "+t,L[e].url=n,L[e].isCustomOverride=!0,_=e,Ft();try{localStorage.setItem("mk21_last_server",_)}catch{}a("btnCancelEditServer").click(),a("modalServerPicker").classList.add("hidden"),ye(a("modalServerPicker"),!1),A="channels",v=null,ve(!0),alert(`Servidor "${t}" atualizado e conectado!`);return}let o={id:"custom_"+Date.now(),name:"⭐ "+t,url:n,isCustomOverride:!0};L.push(o),_=L.length-1,Ft();try{localStorage.setItem("mk21_last_server",_)}catch{}a("inputNewServerName").value="",a("inputNewServerUrl").value="",a("modalServerPicker").classList.add("hidden"),ye(a("modalServerPicker"),!1),A="channels",v=null,ve(!0),alert(`Servidor "${t}" adicionado e conectado com sucesso!`)};a("btnRestoreDefaultServers").onclick=()=>{L=Ot.map(e=>({...e})),_=0,Ft();try{localStorage.setItem("mk21_last_server",0)}catch{}ca(),alert("Os 7 servidores padrão foram restaurados com sucesso.")};a("btnLoadOfflineCache")&&(a("btnLoadOfflineCache").onclick=async()=>{let e=L[_]||L[0],t=await an(e.id),n=t&&t.LIVE?t.LIVE.length:0,o=t&&t.MOVIE?t.MOVIE.length:0,i=t&&t.SERIES?t.SERIES.length:0;t&&n+o+i>0?(w={LIVE:t.LIVE||[],MOVIE:t.MOVIE||[],SERIES:t.SERIES||[]},nt(e.id,w,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),buildCategoryIndex(w[S]||[]),te(),Y(),a("modalServerPicker").classList.add("hidden"),ye(a("modalServerPicker"),!1),A="channels",x(`[Cache] Catálogo restaurado do cache local (${n} canais, ${o} filmes, ${i} séries).`,"success"),alert(`Catálogo offline carregado com sucesso!
-${n} Canais, ${o} Filmes, ${i} Séries.`)):alert("Nenhum catálogo em cache offline encontrado para este servidor.")});a("btnHeaderServer").onclick=ot;a("btnCloseServerPicker").onclick=()=>{let e=a("modalServerPicker");e&&(e.classList.add("hidden"),ye(e,!1));let t=le();A=t?"player":S==="SETTINGS"?"settings":"channels";let n=a(t?"fsBtnServer":S==="SETTINGS"?"cfgBtnQualidade":"btnHeaderServer");n&&n.focus&&n.focus(),t&&oe()};function Ha(e,t){let n=(e||"0.0.0").replace(/[^0-9.]/g,"").split(".").map(i=>parseInt(i,10)||0),o=(t||"0.0.0").replace(/[^0-9.]/g,"").split(".").map(i=>parseInt(i,10)||0);for(let i=0;i<3;i++){let r=n[i]||0,s=o[i]||0;if(r>s)return 1;if(r<s)return-1}return 0}var Tn="2.8.0",Xn=null;try{Xn=localStorage.getItem("mk21_ota_app_version")}catch{}var ht=Xn&&Ha(Xn,Tn)>0?Xn:Tn,Ji=null;async function so(e=!0){let t=a("modalAppUpdate");if(t){t.classList.remove("hidden"),A="modalAppUpdate",a("txtInstalledVer").textContent="v"+ht,a("txtLatestVer").textContent="Consultando atualizações...",a("txtLatestVer").style.color="#94a3b8",a("txtReleaseNotes").textContent="Verificando dados de versão oficial...",a("updateProgressBox").classList.add("hidden"),a("qrCodeBox").classList.add("hidden");try{let n=null,o=Date.now(),i=["https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json?t="+o,"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/api/version.json?t="+o,"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/web/version.json?t="+o,"https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/version.json?t="+o,"https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/api/version.json?t="+o,"https://bgstreaming.vercel.app/smart-tv/version.json?t="+o,"https://bgstreaming.vercel.app/api/version.json?t="+o,"version.json?t="+o,"./version.json?t="+o];for(let u of i)try{let d=await fetch(u);if(d.ok){let l=await d.json();if(l&&l.version){n=l;break}}}catch{}(!n||Ha(n.version,"1.4.1")<0)&&(n={version:"1.4.1",versionCode:141,title:"MK21 Play v1.4.1",releaseNotes:`• Sinergia APK-to-IPK: Phased loading, DNS failover e Omnisearch na Smart TV
+  `,e.querySelectorAll(".chk-cat-toggle").forEach(o=>{o.onchange=()=>{let a=decodeURIComponent(o.getAttribute("data-cat"));o.checked?n=n.filter(i=>i!==a):n.includes(a)||n.push(a),localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),te(),ae();let s=r("msgCatSaved");s&&(s.style.display="block",setTimeout(()=>{s&&(s.style.display="none")},2500))}}),r("btnShowAllCats").onclick=()=>{n=[],localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),fa(),te(),ae()},r("btnHideEmptyCats").onclick=()=>{n=t.filter(o=>(be[o]||[]).length===0),localStorage.setItem("mk21_hidden_categories",JSON.stringify(n)),fa(),te(),ae()}}function qt(){try{localStorage.setItem("mk21_servers_list",JSON.stringify(L))}catch{}}function Ma(){if(!r("modalApkRadioPicker")){let e=document.createElement("div");e.id="modalApkRadioPicker",e.className="app-modal hidden",e.innerHTML='<div class="apk-dialog-card"><h3 id="apkRadioTitle" class="apk-dialog-title">Selecionar Opção</h3><div id="apkRadioOptions" class="apk-radio-list"></div><div class="apk-dialog-footer"><button type="button" id="btnCancelApkRadio" class="apk-btn-cancel" tabindex="0">CANCELAR</button></div></div>',document.body.appendChild(e)}if(!r("modalStagedLoading")){let e=document.createElement("div");e.id="modalStagedLoading",e.className="app-modal hidden",e.innerHTML='<div class="apk-dialog-card"><h3 class="apk-dialog-title">Carregamento em Etapas</h3><p class="apk-dialog-desc">Selecione quais conteúdos carregar em Primeiro Plano. Os demais serão carregados em Segundo Plano de forma silenciosa e incremental para não travar o aplicativo.</p><div class="apk-staged-list"><button type="button" id="btnStageLive" class="apk-staged-row" data-stage="live" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Canais (Ao Vivo)</strong><span class="apk-staged-sub">Carga instantânea de canais de TV</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button><button type="button" id="btnStageMovies" class="apk-staged-row" data-stage="movies" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Filmes (VOD)</strong><span class="apk-staged-sub">Carregar catálogo de filmes na inicialização</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button><button type="button" id="btnStageSeries" class="apk-staged-row" data-stage="series" tabindex="0"><span class="apk-staged-texts"><strong class="apk-staged-title">Séries (VOD)</strong><span class="apk-staged-sub">Carregar catálogo de séries na inicialização</span></span><span class="apk-checkbox-box" aria-hidden="true">✓</span></button></div><div class="apk-dialog-actions-row"><button type="button" id="btnCancelStagedLoading" class="apk-btn-cancel" tabindex="0">CANCELAR</button><button type="button" id="btnSaveStagedLoading" class="apk-btn-save" tabindex="0">SALVAR</button></div></div>',document.body.appendChild(e)}}function lt(){let e=jt(),t=Ae.BG_SYNC_LABELS[e]||"Uma vez ao dia",n=qn()?"Todas em 2º plano":"Apenas lista ativa",o=st()?"Ocultar (Silencioso)":"Mostrar na tela inicial",a=va();r("txtSrvSyncFreqVal")&&(r("txtSrvSyncFreqVal").textContent=t),r("txtSrvSyncScopeVal")&&(r("txtSrvSyncScopeVal").textContent=n),r("txtSrvSyncProgressVal")&&(r("txtSrvSyncProgressVal").textContent=o),r("txtSrvStagedLoadVal")&&(r("txtSrvStagedLoadVal").textContent=a);let s=L[N]||L[0];r("txtServerManagerActiveBadge")&&s&&(r("txtServerManagerActiveBadge").textContent="Ativa: "+Z(s.name))}function Co(e,t){if(e){if(e.classList.add("hidden"),xe(e,!1),e._onCloseCallback=null,t&&typeof t.focus=="function"&&document.body.contains(t))setTimeout(()=>{try{t.focus()}catch{}},20);else if(r("modalServerPicker")&&!r("modalServerPicker").classList.contains("hidden")){T="modalServerPicker";let n=r("modalServerPicker").querySelector("button:not([disabled])");if(n)try{n.focus()}catch{}}}}function Pa({title:e,options:t,currentValue:n,onSelect:o,returnFocusEl:a}){Ma();let s=r("modalApkRadioPicker");if(!s)return;xe(s,!0),r("apkRadioTitle")&&(r("apkRadioTitle").textContent=e||"Selecionar Opção");let i=r("apkRadioOptions");i&&(i.innerHTML="",t.forEach(d=>{let l=document.createElement("button");l.type="button",l.className="apk-radio-option"+(d.value===n?" active":""),l.setAttribute("tabindex","0");let f=document.createElement("span");f.className="apk-radio-dot",f.setAttribute("aria-hidden","true");let p=document.createElement("span");p.className="apk-radio-label",p.textContent=d.label,l.appendChild(f),l.appendChild(p),l.onclick=()=>{typeof o=="function"&&o(d.value,d.label),Co(s,a)},i.appendChild(l)}));let c=()=>Co(s,a);s._onCloseCallback=c,r("btnCancelApkRadio")&&(r("btnCancelApkRadio").onclick=c),s.classList.remove("hidden");let u=i?i.querySelector(".apk-radio-option.active")||i.querySelector(".apk-radio-option"):null;u&&setTimeout(()=>{try{u.focus()}catch{}},20)}function Ds(e,t){Pa({title:"Frequência de Sincronização",currentValue:jt(),returnFocusEl:e,options:[{value:"startup",label:"A cada inicialização"},{value:"daily",label:"Uma vez ao dia"},{value:"weekly",label:"Uma vez por semana"},{value:"manual",label:"Desativado (Apenas manual)"}],onSelect:(n,o)=>{gl(n),Ye(),typeof t=="function"&&t(),E("🔄 Frequência salva: "+o)}})}function Fs(e,t){Pa({title:"Sincronizar Todas as Listas",currentValue:qn()?"all":"active",returnFocusEl:e,options:[{value:"all",label:"Sincronizar Todas em 2º Plano"},{value:"active",label:"Sincronizar Apenas Lista Ativa"}],onSelect:(n,o)=>{hl(n==="all"),Ye(),typeof t=="function"&&t(),E("🌐 Modo de sincronização: "+o)}})}function Vs(e,t){Pa({title:"Progresso em Segundo Plano",currentValue:st()?"hide":"show",returnFocusEl:e,options:[{value:"show",label:"Mostrar Progresso na Tela Inicial"},{value:"hide",label:"Ocultar Progresso (Silencioso)"}],onSelect:(n,o)=>{vl(n==="hide"),Ye(),typeof t=="function"&&t(),E("👁️ Progresso: "+o)}})}function $s(e,t){Ma();let n=r("modalStagedLoading");if(!n)return;xe(n,!0);let o={...ha()},a=r("btnStageLive"),s=r("btnStageMovies"),i=r("btnStageSeries"),c=()=>{a&&a.classList.toggle("checked",!!o.live),s&&s.classList.toggle("checked",!!o.movies),i&&i.classList.toggle("checked",!!o.series)},u=l=>{let f=!o[l];if(!((l==="live"?f:o.live)||(l==="movies"?f:o.movies)||(l==="series"?f:o.series))){E("⚠️ Mantenha ao menos um tipo selecionado em 1º Plano");return}o[l]=f,c()};a&&(a.onclick=()=>u("live")),s&&(s.onclick=()=>u("movies")),i&&(i.onclick=()=>u("series")),c();let d=()=>Co(n,e);n._onCloseCallback=d,r("btnCancelStagedLoading")&&(r("btnCancelStagedLoading").onclick=d),r("btnSaveStagedLoading")&&(r("btnSaveStagedLoading").onclick=()=>{let l=yl(o);Ye(),Co(n,e),typeof t=="function"&&t(),E("📑 Carregamento em etapas salvo ("+va(l)+")")}),n.classList.remove("hidden"),a&&setTimeout(()=>{try{a.focus()}catch{}},20)}async function Hs(){for(let e=0;e<L.length;e++){let t=L[e];if(!t||!t.id)continue;if(e===N&&C.LIVE.length+C.MOVIE.length+C.SERIES.length>0){it(t.id,C,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl);continue}let n=ya(t.id);if(n&&n.totalChannels+n.totalVOD+n.totalSeries>0){xo(t.id,n);continue}try{let o=await hn(t.id);o&&(o.LIVE&&o.LIVE.length||o.MOVIE&&o.MOVIE.length||o.SERIES&&o.SERIES.length)&&it(t.id,o,null,!1)}catch{}}}async function Ra(e,t){let n=L[e];if(!n)return!1;if(e===N){t&&(t.textContent="⏳ Sync..."),E("🔄 Sincronizando fonte ativa ("+Z(n.name)+")...");try{await Ee(!0,{silent:!0})}finally{t&&document.body.contains(t)&&(t.textContent="🔄 Sync")}return!0}t&&(t.disabled=!0,t.textContent="⏳ ...");let o=typeof AbortController!="undefined"?new AbortController:null,a=o?setTimeout(()=>o.abort(),5e4):null;try{let s={LIVE:[],MOVIE:[],SERIES:[]},i=await oa(n.id),c=window.MK21AlternativeSources?MK21AlternativeSources.createGrouper():null;await fs(n.url,o&&o.signal,d=>{let l=un(d),f=l.contentType;if(l.isAdult=!!(l.isAdult||re(l.name)||re(l.group)),i.add(l),s[f])if(f==="LIVE"&&c){let p=c.add(l);p.isNew&&s[f].push(p.item)}else s[f].push(l)},null);let u=s.LIVE.length+s.MOVIE.length+s.SERIES.length;if(u>0){await i.finish(),_n(n.id,s.LIVE,!0),await Jr(n.id,s);try{localStorage.setItem("mk21_last_sync_"+n.id,String(Date.now()))}catch{}let d=it(n.id,s,null,!1);xo(n.id,d),x("[Sync Lista] "+Z(n.name)+": "+u+" itens salvos.","success"),st()||E("✓ "+Z(n.name)+": "+u.toLocaleString("pt-BR")+" itens sincronizados")}return!0}catch(s){return x("[Sync Lista] Falha em "+Z(n.name)+": "+Ae.formatFriendlyLoadError(s),"warn"),t&&!st()&&E("⚠️ Falha ao sincronizar "+Z(n.name)),!1}finally{a&&clearTimeout(a),t&&document.body.contains(t)&&(t.disabled=!1,t.textContent="🔄 Sync")}}async function Po(e=!1){if(On||!ne.isLoggedIn)return;let t=jt();if(!e&&t==="manual")return;On=!0;let n=r("btnSyncAllServersNow")||r("cfgSyncAllServersNow");n&&(n.disabled=!0,n.textContent="⏳ Sincronizando...");try{let o=e||qn(),a=[N];if(o)for(let s=0;s<L.length;s++)s!==N&&a.push(s);e&&!st()&&E("🔄 Sincronizando "+a.length+" servidor(es) em segundo plano...");for(let s=0;s<a.length;s++){let i=a[s],c=L[i];if(!c)continue;let u=0;try{u=parseInt(localStorage.getItem("mk21_last_sync_"+c.id)||"0",10)||0}catch{}!e&&!Ae.shouldSyncServer(u,t)||(await Ra(i,null),s<a.length-1&&await new Promise(d=>setTimeout(d,2500)))}r("modalServerPicker")&&!r("modalServerPicker").classList.contains("hidden")&&Ro(),S==="SETTINGS"&&r("cfgOpenServerManager")&&vt()}finally{On=!1,r("btnSyncAllServersNow")&&(r("btnSyncAllServersNow").disabled=!1,r("btnSyncAllServersNow").textContent="🔄 Sincronizar Todas Agora"),r("cfgSyncAllServersNow")&&(r("cfgSyncAllServersNow").disabled=!1,r("cfgSyncAllServersNow").textContent="🔄 Sincronizar Todas Agora")}}function lc(){Ma();let e=r("modalServerPicker");if(!e)return;let t=e.querySelector(".server-manager-modal");t&&!t.classList.contains("server-manager-maximized")&&t.classList.add("server-manager-maximized");let n=e.querySelector(".server-manager-body");if(!n)return;if(!r("serverMetricsSummary")){let a=document.createElement("div");a.id="serverMetricsSummary",a.className="server-metrics-grid",a.setAttribute("aria-live","polite"),a.innerHTML='<div class="server-metric-card"><span class="server-metric-label">📺 Canais (Geral)</span><strong id="metricTotalChannels" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">🎬 Filmes VOD (Geral)</span><strong id="metricTotalVod" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">🍿 Séries (Geral)</span><strong id="metricTotalSeries" class="server-metric-value">0</strong></div><div class="server-metric-card"><span class="server-metric-label">📡 EPG / Grupos</span><strong id="metricTotalEpg" class="server-metric-value">0</strong></div>',n.insertBefore(a,n.firstChild)}let o=r("serverBgSyncControl");if(!o||!o.classList.contains("server-apk-sync-grid")){let a=o||document.createElement("div");if(a.id="serverBgSyncControl",a.className="server-apk-sync-grid",a.innerHTML='<button type="button" id="btnSrvCfgSyncFreq" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">🔄</span><span class="apk-setting-texts"><strong class="apk-setting-title">Frequência de Sincronização</strong><span id="txtSrvSyncFreqVal" class="apk-setting-sub">Uma vez ao dia</span></span></button><button type="button" id="btnSrvCfgSyncScope" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">🌐</span><span class="apk-setting-texts"><strong class="apk-setting-title">Sincronizar Todas as Listas</strong><span id="txtSrvSyncScopeVal" class="apk-setting-sub">Todas em 2º plano</span></span></button><button type="button" id="btnSrvCfgSyncProgress" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">👁️</span><span class="apk-setting-texts"><strong class="apk-setting-title">Progresso em Segundo Plano</strong><span id="txtSrvSyncProgressVal" class="apk-setting-sub">Mostrar na tela inicial</span></span></button><button type="button" id="btnSrvCfgStagedLoad" class="apk-setting-card" tabindex="0"><span class="apk-setting-icon">📑</span><span class="apk-setting-texts"><strong class="apk-setting-title">Carregamento em Etapas</strong><span id="txtSrvStagedLoadVal" class="apk-setting-sub">1º Plano: Canais</span></span></button>',!o){let s=r("serverMetricsSummary");s&&s.nextSibling?n.insertBefore(a,s.nextSibling):n.insertBefore(a,n.firstChild)}}r("btnSrvCfgSyncFreq")&&!r("btnSrvCfgSyncFreq").dataset.mk21Bound&&(r("btnSrvCfgSyncFreq").dataset.mk21Bound="1",r("btnSrvCfgSyncFreq").onclick=()=>Ds(r("btnSrvCfgSyncFreq"))),r("btnSrvCfgSyncScope")&&!r("btnSrvCfgSyncScope").dataset.mk21Bound&&(r("btnSrvCfgSyncScope").dataset.mk21Bound="1",r("btnSrvCfgSyncScope").onclick=()=>Fs(r("btnSrvCfgSyncScope"))),r("btnSrvCfgSyncProgress")&&!r("btnSrvCfgSyncProgress").dataset.mk21Bound&&(r("btnSrvCfgSyncProgress").dataset.mk21Bound="1",r("btnSrvCfgSyncProgress").onclick=()=>Vs(r("btnSrvCfgSyncProgress"))),r("btnSrvCfgStagedLoad")&&!r("btnSrvCfgStagedLoad").dataset.mk21Bound&&(r("btnSrvCfgStagedLoad").dataset.mk21Bound="1",r("btnSrvCfgStagedLoad").onclick=()=>$s(r("btnSrvCfgStagedLoad"))),r("btnSyncAllServersNow")&&!r("btnSyncAllServersNow").dataset.mk21Bound&&(r("btnSyncAllServersNow").dataset.mk21Bound="1",r("btnSyncAllServersNow").onclick=()=>Po(!0))}function Ye(){lc();let e=ba();r("metricTotalChannels")&&(r("metricTotalChannels").textContent=e.totalChannels.toLocaleString("pt-BR")),r("metricTotalVod")&&(r("metricTotalVod").textContent=e.totalVOD.toLocaleString("pt-BR")),r("metricTotalSeries")&&(r("metricTotalSeries").textContent=e.totalSeries.toLocaleString("pt-BR")),r("metricTotalEpg")&&(r("metricTotalEpg").textContent=e.totalEPG>0?e.totalEPG.toLocaleString("pt-BR")+" EPG":e.totalGroups.toLocaleString("pt-BR")+" grupos"),lt();let t=L[N],n=r("txtServerSyncLast");if(n){let o=0;try{t&&(o=parseInt(localStorage.getItem("mk21_last_sync_"+t.id)||"0",10)||0)}catch{}o>0?n.textContent="Totais por servidor • Ativa sinc.: "+Ta(o):n.textContent="Totais individuais por servidor"}}function ct(){let e=r("modalServerPicker");if(!e)return;xe(e,!0),Ye(),Ro(),e.classList.remove("hidden"),T="modalServerPicker",kl=0;let t=r("serverItemsGrid");t&&typeof X=="function"&&setTimeout(()=>X(t),40),Hs();let n=t?t.querySelector(".btn-server-connect"):null;n&&n.focus()}function Ro(){Ye();let e=r("serverItemsGrid");e.innerHTML="";let t=ba();L.forEach((n,o)=>{let a=o===N,s=document.createElement("div");s.className="server-manager-row"+(a?" active-server":"");let i=document.createElement("div");i.className="server-info-col";let c=document.createElement("div");c.className="server-info-top-line";let u=document.createElement("span");u.className="server-info-name",u.textContent=Z(n.name)+(a?"  [✓ ATIVO]":""),c.appendChild(u);let d=0;try{d=parseInt(localStorage.getItem("mk21_last_sync_"+n.id)||"0",10)||0}catch{}let l=document.createElement("span");l.className="server-row-sync-meta",l.textContent="• "+Ta(d),c.appendChild(l);let f=document.createElement("div");f.className="server-info-url",f.textContent=n.url;let p=document.createElement("div");p.className="server-row-metrics",p.setAttribute("data-srv-metrics-id",n.id);let m=a&&t.totalItems>0?t:ya(n.id);p.innerHTML=Aa(m),i.appendChild(c),i.appendChild(f),i.appendChild(p);let y=document.createElement("div");y.className="server-row-actions";let g=document.createElement("button");g.type="button",g.className="btn-server-sync-one",g.setAttribute("tabindex","0"),g.textContent="🔄 Sync",g.onclick=()=>Ra(o,g);let h=document.createElement("button");h.type="button",h.className="btn-server-connect"+(a?" active":""),h.setAttribute("tabindex","0"),h.textContent=a?"✓ Ativo":"🔗 Conectar",h.onclick=()=>{N=o;try{localStorage.setItem("mk21_last_server",o)}catch{}Kt({preserveCatalog:!0}),h.textContent="25%...",setTimeout(()=>{h.textContent="65%..."},200),setTimeout(()=>{r("modalServerPicker").classList.add("hidden"),xe(r("modalServerPicker"),!1),T="channels",v=null,Ee(!1)},450)};let b=document.createElement("button");b.type="button",b.className="btn-server-edit",b.setAttribute("tabindex","0"),b.textContent="✏️",b.title="Alterar servidor",b.onclick=()=>cc(o);let R=document.createElement("button");R.type="button",R.className="btn-server-delete",R.setAttribute("tabindex","0"),R.textContent="🗑️",R.title="Excluir servidor",R.onclick=()=>dc(o),y.appendChild(g),y.appendChild(h),y.appendChild(b),y.appendChild(R),s.appendChild(i),s.appendChild(y),e.appendChild(s)}),typeof X=="function"&&X(e)}function cc(e){let t=L[e];t&&(r("txtAddServerTitle").textContent=`✏️ Alterar Servidor: ${t.name.replace(/^⭐\s*/,"")}`,r("inputEditServerIndex").value=e,r("inputNewServerName").value=t.name.replace(/^⭐\s*/,""),r("inputNewServerUrl").value=t.url,r("btnAddServerSubmit").textContent="💾 Salvar Alterações",r("btnCancelEditServer").classList.remove("hidden"),r("inputNewServerName").focus(),r("inputNewServerName").scrollIntoView({behavior:"smooth",block:"center"}))}r("btnCancelEditServer").onclick=()=>{r("txtAddServerTitle").textContent="➕ Adicionar Novo Servidor / Lista Manual",r("inputEditServerIndex").value="-1",r("inputNewServerName").value="",r("inputNewServerUrl").value="",r("btnAddServerSubmit").textContent="💾 Salvar e Conectar",r("btnCancelEditServer").classList.add("hidden")};function dc(e){if(L.length<=1){alert("Você deve manter pelo menos um servidor cadastrado.");return}let t=L[e].name;L.splice(e,1),N>=L.length&&(N=0),qt(),Ro(),alert(`Servidor "${t}" excluído.`)}r("btnAddServerSubmit").onclick=()=>{let e=parseInt(r("inputEditServerIndex").value,10),t=r("inputNewServerName").value.trim(),n=r("inputNewServerUrl").value.trim();if(!t){alert("Informe o nome do servidor.");return}if(!n.startsWith("http")){alert("URL inválida. Deve iniciar com http:// ou https://");return}if(e>=0&&e<L.length){L[e].name="⭐ "+t,L[e].url=n,L[e].isCustomOverride=!0,N=e,qt();try{localStorage.setItem("mk21_last_server",N)}catch{}r("btnCancelEditServer").click(),r("modalServerPicker").classList.add("hidden"),xe(r("modalServerPicker"),!1),T="channels",v=null,Ee(!0),alert(`Servidor "${t}" atualizado e conectado!`);return}let o={id:"custom_"+Date.now(),name:"⭐ "+t,url:n,isCustomOverride:!0};L.push(o),N=L.length-1,qt();try{localStorage.setItem("mk21_last_server",N)}catch{}r("inputNewServerName").value="",r("inputNewServerUrl").value="",r("modalServerPicker").classList.add("hidden"),xe(r("modalServerPicker"),!1),T="channels",v=null,Ee(!0),alert(`Servidor "${t}" adicionado e conectado com sucesso!`)};r("btnRestoreDefaultServers").onclick=()=>{L=rt.map(e=>({...e})),N=0,qt();try{localStorage.setItem("mk21_last_server",0)}catch{}Ro(),alert("Os 7 servidores padrão foram restaurados com sucesso.")};r("btnLoadOfflineCache")&&(r("btnLoadOfflineCache").onclick=async()=>{let e=L[N]||L[0],t=await hn(e.id),n=t&&t.LIVE?t.LIVE.length:0,o=t&&t.MOVIE?t.MOVIE.length:0,a=t&&t.SERIES?t.SERIES.length:0;t&&n+o+a>0?(C={LIVE:t.LIVE||[],MOVIE:t.MOVIE||[],SERIES:t.SERIES||[]},it(e.id,C,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),buildCategoryIndex(C[S]||[]),ae(),Q(),r("modalServerPicker").classList.add("hidden"),xe(r("modalServerPicker"),!1),T="channels",x(`[Cache] Catálogo restaurado do cache local (${n} canais, ${o} filmes, ${a} séries).`,"success"),alert(`Catálogo offline carregado com sucesso!
+${n} Canais, ${o} Filmes, ${a} Séries.`)):alert("Nenhum catálogo em cache offline encontrado para este servidor.")});r("btnHeaderServer").onclick=ct;r("btnCloseServerPicker").onclick=()=>{let e=r("modalServerPicker");e&&(e.classList.add("hidden"),xe(e,!1));let t=ue();T=t?"player":S==="SETTINGS"?"settings":"channels";let n=r(t?"fsBtnServer":S==="SETTINGS"?"cfgBtnQualidade":"btnHeaderServer");n&&n.focus&&n.focus(),t&&ie()};function pa(e,t){let n=(e||"0.0.0").replace(/[^0-9.]/g,"").split(".").map(a=>parseInt(a,10)||0),o=(t||"0.0.0").replace(/[^0-9.]/g,"").split(".").map(a=>parseInt(a,10)||0);for(let a=0;a<3;a++){let s=n[a]||0,i=o[a]||0;if(s>i)return 1;if(s<i)return-1}return 0}var Hn="2.8.0",vo=null;try{vo=localStorage.getItem("mk21_ota_app_version")}catch{}var Ct=vo&&pa(vo,Hn)>0?vo:Hn,zs=null;async function Na(e=!0){let t=r("modalAppUpdate");if(t){t.classList.remove("hidden"),T="modalAppUpdate",r("txtInstalledVer").textContent="v"+Ct,r("txtLatestVer").textContent="Consultando atualizações...",r("txtLatestVer").style.color="#94a3b8",r("txtReleaseNotes").textContent="Verificando dados de versão oficial...",r("updateProgressBox").classList.add("hidden"),r("qrCodeBox").classList.add("hidden");try{let n=null,o=Date.now(),a=["https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json?t="+o,"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/api/version.json?t="+o,"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/web/version.json?t="+o,"https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/version.json?t="+o,"https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/api/version.json?t="+o,"https://bgstreaming.vercel.app/smart-tv/version.json?t="+o,"https://bgstreaming.vercel.app/api/version.json?t="+o,"version.json?t="+o,"./version.json?t="+o];for(let u of a)try{let d=await fetch(u);if(d.ok){let l=await d.json();if(l&&l.version){n=l;break}}}catch{}(!n||pa(n.version,"1.4.1")<0)&&(n={version:"1.4.1",versionCode:141,title:"MK21 Play v1.4.1",releaseNotes:`• Sinergia APK-to-IPK: Phased loading, DNS failover e Omnisearch na Smart TV
 • Multitarefa: Picture-in-Picture (PiP) nativo com auto-enter no Android
 • Observabilidade: Telemetria silenciosa de stream (logStreamError)
 • Resiliência de Rede: DNS Failover ativo via Mk21DnsResolver
-• Zapping de Canal Zero-Delay e Pré-carregamento de canais adjacentes`,ipkUrl:"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_1.4.1_all.ipk",apkUrl:"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",isPendingPush:!1}),Ji=n,Ha(n.version,ht)>0?(a("txtLatestVer").textContent=`v${n.version} 🎉 (Disponível!)`,a("txtLatestVer").style.color="#4caf50",a("btnStartDirectUpdate").textContent=`⬇️ Instalar v${n.version} Diretamente na TV (OTA)`):(a("txtLatestVer").textContent=`v${n.version} ✅ (Versão Mais Recente)`,a("txtLatestVer").style.color="#ffd54f",a("btnStartDirectUpdate").textContent=`🔄 Sincronizar Arquivos v${n.version}`);let s=a("titleUpdateNotes");s&&(s.textContent=`Novidades da Versão v${n.version}:`);let c=n.releaseNotes||"Melhorias de desempenho, navegação fluida e correções gerais.";n.isPendingPush&&(c+=`
+• Zapping de Canal Zero-Delay e Pré-carregamento de canais adjacentes`,ipkUrl:"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_1.4.1_all.ipk",apkUrl:"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",isPendingPush:!1}),zs=n,pa(n.version,Ct)>0?(r("txtLatestVer").textContent=`v${n.version} 🎉 (Disponível!)`,r("txtLatestVer").style.color="#4caf50",r("btnStartDirectUpdate").textContent=`⬇️ Instalar v${n.version} Diretamente na TV (OTA)`):(r("txtLatestVer").textContent=`v${n.version} ✅ (Versão Mais Recente)`,r("txtLatestVer").style.color="#ffd54f",r("btnStartDirectUpdate").textContent=`🔄 Sincronizar Arquivos v${n.version}`);let i=r("titleUpdateNotes");i&&(i.textContent=`Novidades da Versão v${n.version}:`);let c=n.releaseNotes||"Melhorias de desempenho, navegação fluida e correções gerais.";n.isPendingPush&&(c+=`
 
-💡 Dica: No menu do AI Studio no seu navegador, clique em "Push to GitHub" para atualizar os repositórios remotos oficiais.`),a("txtReleaseNotes").textContent=c,n.ipkUrl&&(a("imgUpdateQr").src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data="+encodeURIComponent(n.ipkUrl),a("qrCodeBox").classList.remove("hidden")),a("btnStartDirectUpdate").onclick=Qi,a("btnCheckAgainUpdate")&&(a("btnCheckAgainUpdate").onclick=function(){so(!0)}),a("btnCloseUpdateModal")&&(a("btnCloseUpdateModal").onclick=function(){a("modalAppUpdate").classList.add("hidden"),A="settings"}),a("btnStartDirectUpdate").focus()}catch(n){console.error("Update check failed:",n),a("txtLatestVer").textContent="v"+ht+" (Offline)",a("txtReleaseNotes").textContent="Não foi possível contatar o servidor de atualizações no momento: "+n.message,a("btnStartDirectUpdate").textContent="🔄 Tentar Novamente",a("btnCheckAgainUpdate").focus()}}}async function Qi(){let e=Ji||{},t=e.version||Tn,n=a("updateProgressBox"),o=a("btnStartDirectUpdate");o&&(o.disabled=!0),a("btnCheckAgainUpdate")&&(a("btnCheckAgainUpdate").disabled=!0),n&&n.classList.remove("hidden");let i=(r,s)=>{a("txtUpdateStep")&&(a("txtUpdateStep").textContent=r),a("txtUpdatePct")&&(a("txtUpdatePct").textContent=s+"%"),a("barUpdateProgress")&&(a("barUpdateProgress").style.width=s+"%")};try{i("Conectando ao repositório oficial de atualizações...",15);let r=Date.now(),s=[`https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/app.js?t=${r}`,`https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/app.js?t=${r}`,`https://bgstreaming.vercel.app/smart-tv/mk21-tv/app.js?t=${r}`],c=[`https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=${r}`,`https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/styles.css?t=${r}`,`https://bgstreaming.vercel.app/smart-tv/mk21-tv/styles.css?t=${r}`],u=null,d=null;i(`Baixando código atualizado v${t}...`,35);for(let l of s)try{let f=await fetch(l,{cache:"no-store"});if(f.ok){let p=await f.text();if(p&&p.length>5e3&&p.includes("CURRENT_APP_VERSION")){u=p;break}}}catch{}i("Baixando folhas de estilo e layout...",65);for(let l of c)try{let f=await fetch(l,{cache:"no-store"});if(f.ok){let p=await f.text();if(p&&p.length>500){d=p;break}}}catch{}if(u){i("Salvando e ativando nova versão na TV...",85);try{localStorage.setItem("mk21_ota_app_js",u),localStorage.setItem("mk21_ota_app_version",t),d&&localStorage.setItem("mk21_ota_styles_css",d)}catch(l){console.warn("Erro ao gravar localStorage OTA:",l)}if(window.webOS&&webOS.service&&e.ipkUrl)try{webOS.service.request("luna://org.webosbrew.hbchannel.service/install",{ipkUrl:e.ipkUrl})}catch{console.log("Serviço Homebrew opcional não acionado, atualização OTA já garantida.")}i(`✅ Atualizado para v${t} com sucesso! Reiniciando...`,100),setTimeout(()=>{window.location.reload()},1200);return}throw new Error(`Servidor de arquivos temporariamente indisponível. Conecte a TV à internet ou instale o pacote .ipk v${t} pelo webOS Dev Manager.`)}catch(r){console.error("Update failed:",r),i("Falha na atualização automática",0),alert("Aviso: "+(r.message||r)+`
+💡 Dica: No menu do AI Studio no seu navegador, clique em "Push to GitHub" para atualizar os repositórios remotos oficiais.`),r("txtReleaseNotes").textContent=c,n.ipkUrl&&(r("imgUpdateQr").src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data="+encodeURIComponent(n.ipkUrl),r("qrCodeBox").classList.remove("hidden")),r("btnStartDirectUpdate").onclick=Us,r("btnCheckAgainUpdate")&&(r("btnCheckAgainUpdate").onclick=function(){Na(!0)}),r("btnCloseUpdateModal")&&(r("btnCloseUpdateModal").onclick=function(){r("modalAppUpdate").classList.add("hidden"),T="settings"}),r("btnStartDirectUpdate").focus()}catch(n){console.error("Update check failed:",n),r("txtLatestVer").textContent="v"+Ct+" (Offline)",r("txtReleaseNotes").textContent="Não foi possível contatar o servidor de atualizações no momento: "+n.message,r("btnStartDirectUpdate").textContent="🔄 Tentar Novamente",r("btnCheckAgainUpdate").focus()}}}async function Us(){let e=zs||{},t=e.version||Hn,n=r("updateProgressBox"),o=r("btnStartDirectUpdate");o&&(o.disabled=!0),r("btnCheckAgainUpdate")&&(r("btnCheckAgainUpdate").disabled=!0),n&&n.classList.remove("hidden");let a=(s,i)=>{r("txtUpdateStep")&&(r("txtUpdateStep").textContent=s),r("txtUpdatePct")&&(r("txtUpdatePct").textContent=i+"%"),r("barUpdateProgress")&&(r("barUpdateProgress").style.width=i+"%")};try{a("Conectando ao repositório oficial de atualizações...",15);let s=Date.now(),i=[`https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/app.js?t=${s}`,`https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/app.js?t=${s}`,`https://bgstreaming.vercel.app/smart-tv/mk21-tv/app.js?t=${s}`],c=[`https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=${s}`,`https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/styles.css?t=${s}`,`https://bgstreaming.vercel.app/smart-tv/mk21-tv/styles.css?t=${s}`],u=null,d=null;a(`Baixando código atualizado v${t}...`,35);for(let l of i)try{let f=await fetch(l,{cache:"no-store"});if(f.ok){let p=await f.text();if(p&&p.length>5e3&&p.includes("CURRENT_APP_VERSION")){u=p;break}}}catch{}a("Baixando folhas de estilo e layout...",65);for(let l of c)try{let f=await fetch(l,{cache:"no-store"});if(f.ok){let p=await f.text();if(p&&p.length>500){d=p;break}}}catch{}if(u){a("Salvando e ativando nova versão na TV...",85);try{localStorage.setItem("mk21_ota_app_js",u),localStorage.setItem("mk21_ota_app_version",t),d&&localStorage.setItem("mk21_ota_styles_css",d)}catch(l){console.warn("Erro ao gravar localStorage OTA:",l)}if(window.webOS&&webOS.service&&e.ipkUrl)try{webOS.service.request("luna://org.webosbrew.hbchannel.service/install",{ipkUrl:e.ipkUrl})}catch{console.log("Serviço Homebrew opcional não acionado, atualização OTA já garantida.")}a(`✅ Atualizado para v${t} com sucesso! Reiniciando...`,100),setTimeout(()=>{window.location.reload()},1200);return}throw new Error(`Servidor de arquivos temporariamente indisponível. Conecte a TV à internet ou instale o pacote .ipk v${t} pelo webOS Dev Manager.`)}catch(s){console.error("Update failed:",s),a("Falha na atualização automática",0),alert("Aviso: "+(s.message||s)+`
 
-💡 Alternativa: Você pode baixar o pacote IPK v`+t+" apontando a câmera do celular para o QR Code abaixo e instalá-lo via webOS Dev Manager."),o&&(o.disabled=!1),a("btnCheckAgainUpdate")&&(a("btnCheckAgainUpdate").disabled=!1)}}function Us(){if(confirm("Limpar cache de atualização OTA e voltar à versão do pacote instalado (v"+Tn+")?")){try{localStorage.removeItem("mk21_ota_app_js"),localStorage.removeItem("mk21_ota_styles_css"),localStorage.removeItem("mk21_ota_app_version")}catch{}alert("Cache OTA limpo. Reiniciando na versão base v"+Tn),window.location.reload()}}a("btnCloseUpdateModal").onclick=()=>{a("modalAppUpdate").classList.add("hidden"),A="settings"};a("btnStartDirectUpdate").onclick=Qi;a("btnCheckAgainUpdate").onclick=()=>so(!0);a("btnForceResetCache")&&(a("btnForceResetCache").onclick=Us);function aa(){let e=a("tvPlayer"),t=window.FBG2PlayerService;if(t&&t.isActive()){let o=(t.player&&t.player.getState?t.player.getState():"playing")==="paused";t.toggle(),typeof se=="function"&&se(o),E(o?"▶ Reproduzindo":"⏸ Pausado");return}e&&(e.paused?(e.play().catch(()=>{}),typeof se=="function"&&se(!0),E("▶ Reproduzindo")):(e.pause(),typeof se=="function"&&se(!1),E("⏸ Pausado")))}function Me(e){if(!e||e.disabled||e.closest&&e.closest(".hidden"))return!1;try{let t=window.getComputedStyle(e);if(t.display==="none"||t.visibility==="hidden"||t.opacity==="0")return!1}catch{}if(e.offsetParent!==null||e===document.activeElement)return!0;try{return window.getComputedStyle(e).position==="fixed"}catch{}return!1}function rn(){let e=["modalApkRadioPicker","modalStagedLoading","modalDevLogs","hudLoadingOverlay","modalStreamDiagnostics","modalOmnisearch","modalEpgGuide","modalSortPicker","modalAudioSubs","modalServerPicker","modalPin","modalExitConfirm","modalSeriesEpisodes","modalAppUpdate"];for(let n=0;n<e.length;n++){let o=a(e[n]);if(o&&!o.classList.contains("hidden")&&Me(o))return o}let t=document.querySelector('.app-modal:not(.hidden), .modal-overlay:not(.hidden), [role="dialog"]:not(.hidden), .modal-container:not(.hidden)');return t&&Me(t)?t:null}function Zi(){let t=rn()||document,n=["button:not([disabled])","input:not([disabled])","select:not([disabled])","textarea:not([disabled])",'[tabindex="0"]',".modal-btn",".modal-button",".header-focusable",".list-item-btn",".cat-item-btn",".ctrl-btn",".cfg-menu-item",".hud-ctrl-btn",".pin-key",".season-tab-btn",".episode-item-btn",".btn-server-connect",".btn-server-sync-one",".btn-server-edit",".btn-server-delete",".apk-setting-card",".apk-radio-option",".apk-staged-row",".apk-btn-cancel",".apk-btn-save",".sort-choice-btn"].join(",");return Array.from(t.querySelectorAll(n)).filter(function(o){if(!Me(o))return!1;if(o.classList&&o.classList.contains("scrollable-container")){let i=o.querySelector(".list-item-btn:not([disabled]), .cat-item-btn:not([disabled]), .cfg-menu-item:not([disabled]), .btn-server-connect:not([disabled])");if(i&&Me(i))return!1}return!0})}function Zo(e){let t=e.getBoundingClientRect();return{x:t.left+t.width/2,y:t.top+t.height/2,r:t}}function oa(){try{(window.scrollX!==0||window.scrollY!==0)&&window.scrollTo(0,0),document.documentElement&&(document.documentElement.scrollTop=0,document.documentElement.scrollLeft=0),document.body&&(document.body.scrollTop=0,document.body.scrollLeft=0);let e=["#home-container",".app-body",".app-header","#sectionUnified","#sectionSettings",".col-categories",".col-channels",".col-player"];for(let t=0;t<e.length;t++){let n=document.querySelector(e[t]);n&&(n.scrollTop!==0&&(n.scrollTop=0),n.scrollLeft!==0&&(n.scrollLeft=0))}}catch{}}function lo(e){if(!(!e||typeof e.closest!="function"))try{let t=e.closest("#listCategories, #listItems, .settings-left-menu, .settings-detail-panel, .pane-scroll, .scrollable-container, #serverItemsGrid, #devLogsContainer, #epgScheduleList, .omnisearch-body");if(!t||t===e)return;let n=t.getBoundingClientRect(),o=e.getBoundingClientRect(),i=8;o.top<n.top+i?t.scrollTop+=o.top-(n.top+i):o.bottom>n.bottom-i&&(t.scrollTop+=o.bottom-(n.bottom-i)),typeof q=="function"&&q(t)}catch{}}function Oe(e){if(!(!e||typeof e.focus!="function")){try{e.focus({preventScroll:!0})}catch{try{e.focus()}catch{}}lo(e),oa()}}function xe(e){let t=Zi();if(!t.length)return;let n=document.activeElement;if(!n||t.indexOf(n)===-1){Oe(t[0]);return}let o=Zo(n),i=null,r=1/0;for(let s=0;s<t.length;s++){let c=t[s];if(c===n)continue;let u=Zo(c),d=u.x-o.x,l=u.y-o.y,f=!1,p=0,m=0;if(e==="left"?(f=d<-8,p=-d,m=Math.abs(l)):e==="right"?(f=d>8,p=d,m=Math.abs(l)):e==="up"?(f=l<-8,p=-l,m=Math.abs(d)):e==="down"&&(f=l>8,p=l,m=Math.abs(d)),!f)continue;let b=p+m*2.5;b<r&&(r=b,i=c)}if(i){Oe(i);try{if(i.id&&typeof tn!="undefined"&&tn.indexOf(i.id)!==-1)A="header",Ka=tn.indexOf(i.id);else if(i.classList.contains("cat-item-btn")){A="categories";let c=Array.from((a("listCategories")||document).querySelectorAll(".cat-item-btn")).indexOf(i);c>=0&&(Si=c)}else if(i.classList.contains("list-item-btn")){A="channels";let s=Array.from((a("listItems")||document).querySelectorAll(".list-item-btn")),c=s.indexOf(i);if(c>=0){Ei=c;let u=a("badgeItemsCount");u&&s.length>0&&(u.textContent=c+1+" / "+s.length)}}else i.classList.contains("cfg-menu-item")&&(A="settings")}catch{}}}function Se(){try{let e=Zi();if(!e.length)return;let t=null;if(A==="header"&&typeof tn!="undefined")t=a(tn[Ka]||tn[0]);else if(A==="categories"){let n=a("listCategories")?a("listCategories").querySelectorAll(".cat-item-btn"):[];t=n[Si]||n[0]}else if(A==="channels"){let n=a("listItems")?a("listItems").querySelectorAll(".list-item-btn"):[];t=n[Ei]||n[0]}else A==="settings"&&(t=document.querySelector(".cfg-menu-item.active")||a("cfgBtnInfo"));(!t||!Me(t))&&(t=e[0]),t&&Oe(t)}catch(e){console.warn("[MK21] focusActiveElement",e)}}var er=!1;function tr(e){if(e){if(typeof e._onCloseCallback=="function"){let t=e._onCloseCallback;t();return}if(e.classList.add("hidden"),ye(e,!1),pt=null,(e.id==="modalPlayerOptions"||e.id==="modalQuality")&&(sa=null),le()){A="player",oe();let t=a("fsBtnPlay")&&Me(a("fsBtnPlay"))?a("fsBtnPlay"):a("videoContainer");try{t&&t.focus()}catch{}return}A=typeof S!="undefined"&&S==="SETTINGS"?"settings":"channels";try{Se()}catch{}}}function Gs(){let e=a("modalExitConfirm");if(!e){nr();return}e.classList.remove("hidden");let t=a("btnExitCancel");if(t)try{t.focus()}catch{}}function nr(){er=!0;try{if(window.webOS&&typeof webOS.platformBack=="function"){webOS.platformBack();return}}catch{}try{window.close()}catch{}}function ar(){let e=a("hudLoadingOverlay");if(e&&!e.classList.contains("hidden")){if(ge)try{ge.abort()}catch{}return e.classList.add("hidden"),De(),E("⚠️ Carregamento cancelado"),!0}let t=a("modalExitConfirm");if(t&&!t.classList.contains("hidden")){t.classList.add("hidden"),A="header";try{Se()}catch{}return!0}let n=rn();if(n)return tr(n),!0;let o=a("inputSearch");if(document.activeElement&&document.activeElement.id==="inputSearch"||o&&o.value.trim().length>0||A==="search"){try{It(!0)}catch{}return!0}if(le()){try{cn(!1)}catch{}return!0}if(typeof S!="undefined"&&S!=="LIVE"){try{qe("LIVE")}catch{}A="channels";try{Se()}catch{}return!0}return Gs(),!0}function or(){try{(!history.state||!history.state.mk21)&&history.pushState({mk21:1},"")}catch{}}typeof window!="undefined"&&window.addEventListener("popstate",function(){er||(ar(),or())});var pt=null;function Aa(e){let t=e;for(;t&&t!==document.body&&t.nodeType===1;){let n=null;try{n=window.getComputedStyle(t)}catch{}if(n&&/(auto|scroll)/.test(n.overflowY)&&t.scrollHeight>t.clientHeight+4)return t;t=t.parentElement}return null}function qs(e){if(!e)return null;let t=null,n=e.querySelectorAll("div, ul, ol, section, .modal-content");for(let o=0;o<n.length;o++){let i=n[o],r=null;try{r=window.getComputedStyle(i)}catch{}!r||!/(auto|scroll)/.test(r.overflowY)||i.scrollHeight<=i.clientHeight+4||(!t||i.clientHeight>t.clientHeight)&&(t=i)}return t}function ir(){let e=document.activeElement,t=e?Aa(e):null;if(t)return pt=t,t;let n=rn();if(n){let o=Aa(n)||qs(n);if(o)return o}return pt&&document.body.contains(pt)&&!pt.closest(".hidden")?pt:Aa(a("listItems"))||null}function Ws(e,t){let n=ir();if(!n)return!1;let o=t||Math.max(120,Math.round(n.clientHeight*.7)),i=n.scrollTop;return n.scrollTop=i+e*o,n.scrollTop!==i}function rr(e){if(document.body.classList.contains("is-fullscreen"))return;let t=ir();if(!t)return;let n=(e.deltaY||0)*3.2;if(!n)return;let o=t.scrollTop;t.scrollTop=o+n,t.scrollTop!==o&&(pt=t,e.preventDefault())}window.addEventListener("wheel",rr,{passive:!1});window.addEventListener("mousewheel",rr,{passive:!1});document.addEventListener("keydown",function(e){let t=e.key||"",n=e.code||"",o={ArrowLeft:37,ArrowUp:38,ArrowRight:39,ArrowDown:40,Enter:13,Escape:27,Backspace:8,GoBack:461},i=e.keyCode||e.which||o[t]||o[n]||0,r=document.activeElement;if(typeof ae!="undefined"&&ae){if(!document.body.contains(ae)||!Me(ae))Ia(ae,!1);else if(i===38||t==="ArrowUp"){e.preventDefault(),e.stopPropagation(),ae.scrollTop=Math.max(0,ae.scrollTop-96),typeof q=="function"&&q(ae);return}else if(i===40||t==="ArrowDown"){e.preventDefault(),e.stopPropagation(),ae.scrollTop+=96,typeof q=="function"&&q(ae);return}else if(i===13||t==="Enter"||i===461||i===10009||i===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),Ia(ae,!1);return}}let s=rn();if(le()&&!s&&oe(),s){let l=Array.from(s.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"]), .modal-btn, .modal-button, .ctrl-btn, .pin-key, .btn-server-connect, .btn-server-sync-one, .btn-server-edit, .btn-server-delete, .hud-ctrl-btn, .apk-setting-card, .apk-radio-option, .apk-staged-row, .apk-btn-cancel, .apk-btn-save, .sort-choice-btn')).filter(Me);if(l.length>0){let f=l[0],p=l[l.length-1];if((!s.contains(document.activeElement)||l.indexOf(document.activeElement)===-1)&&f.focus(),i===461||i===10009||i===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),tr(s);return}let m=r?r.tagName:"",b=m==="INPUT"||m==="TEXTAREA"||m==="SELECT",g=t==="Tab"||i===9,h=t==="ArrowDown"||i===40,y=t==="ArrowUp"||i===38;if(g){if(e.shiftKey&&document.activeElement===f){e.preventDefault(),p.focus();return}else if(!e.shiftKey&&document.activeElement===p){e.preventDefault(),f.focus();return}}if(h&&document.activeElement===p){e.preventDefault(),f.focus();return}else if(y&&document.activeElement===f){e.preventDefault(),p.focus();return}if(i===37||t==="ArrowLeft"){if(b)return;e.preventDefault(),xe("left");return}if(i===39||t==="ArrowRight"){if(b)return;e.preventDefault(),xe("right");return}if(i===38||t==="ArrowUp"){e.preventDefault(),xe("up");return}if(i===40||t==="ArrowDown"){e.preventDefault(),xe("down");return}}}if(r&&r.id==="inputSearch"){if(i===40||t==="ArrowDown"){e.preventDefault(),It(!1);let l=a("listItems")?a("listItems").querySelector(".list-item-btn:not([disabled])"):null;if(l)Oe(l),A="channels";else{let f=a("listCategories")?a("listCategories").querySelector(".cat-item-btn.active")||a("listCategories").querySelector(".cat-item-btn"):null;f&&(Oe(f),A="categories")}return}if(i===38||t==="ArrowUp"){e.preventDefault(),It(!1);let l=document.querySelector(".main-tabs .tab-btn.active")||a("tabLive");l&&(Oe(l),A="header");return}if((i===37||t==="ArrowLeft")&&(!r.value||r.selectionStart===0)){e.preventDefault(),It(!1);let l=a("listCategories")?a("listCategories").querySelector(".cat-item-btn.active")||a("listCategories").querySelector(".cat-item-btn"):null;l&&(Oe(l),A="categories");return}if((i===39||t==="ArrowRight")&&(!r.value||r.selectionStart===r.value.length)){e.preventDefault();let l=a("btnClearSearch")||a("btnFullscreen")||a("btnPlayPause");l&&Oe(l);return}}let c=r&&/^(INPUT|TEXTAREA|SELECT)$/.test(r.tagName);if(c&&r.id!=="inputSearch"&&["ArrowLeft","ArrowUp","ArrowRight","ArrowDown"].includes(t))return;if(t==="Voice"||t==="VoiceCommand"||i===1022||i===1016||i===166){e.preventDefault(),typeof ei=="function"&&ei();return}if(i===179||i===10252||i===415||i===250||i===19||i===413||t==="MediaPlayPause"||t==="MediaPlay"||t==="MediaPause"||t==="Play"||t==="Pause"){e.preventDefault(),typeof aa=="function"&&aa();return}if(i===461||i===10009||i===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),ar();return}if(i===33||i===34){e.preventDefault(),Ws(i===33?-1:1);return}if(i===427||t==="ChannelUp"||n==="ChannelUp"){e.preventDefault(),e.stopPropagation(),S==="LIVE"?Da():(Jt(3e4),E("⏩ +30s"),mt()),le()&&oe();return}if(i===428||t==="ChannelDown"||n==="ChannelDown"){e.preventDefault(),e.stopPropagation(),S==="LIVE"?Fa():(Jt(-3e4),E("⏪ -30s"),mt()),le()&&oe();return}if(le()){oe();let l=a("fsHudBar"),f=l&&!l.classList.contains("hidden"),p=f&&r&&r.classList&&r.classList.contains("hud-ctrl-btn");if(i===38||t==="ArrowUp"){if(e.preventDefault(),p){let m=r.getBoundingClientRect();if(Array.from(l.querySelectorAll(".hud-ctrl-btn")).some(function(g){return g!==r&&Me(g)&&g.getBoundingClientRect().top+g.getBoundingClientRect().height/2<m.top+m.height/2-14})){xe("up");return}}S==="LIVE"&&Fa();return}if(i===40||t==="ArrowDown"){if(e.preventDefault(),p){let m=r.getBoundingClientRect();if(Array.from(l.querySelectorAll(".hud-ctrl-btn")).some(function(g){return g!==r&&Me(g)&&g.getBoundingClientRect().top+g.getBoundingClientRect().height/2>m.top+m.height/2+14})){xe("down");return}}S==="LIVE"&&Da();return}if(i===37||t==="ArrowLeft"){e.preventDefault(),f&&r&&r.classList.contains("hud-ctrl-btn")?xe("left"):S!=="LIVE"?(Jt(-1e4),E("⏪ -10s"),mt()):xe("left");return}if(i===39||t==="ArrowRight"){e.preventDefault(),f&&r&&r.classList.contains("hud-ctrl-btn")?xe("right"):S!=="LIVE"?(Jt(1e4),E("⏩ +10s"),mt()):xe("right");return}}if(i===37||t==="ArrowLeft"){e.preventDefault(),xe("left");return}if(i===39||t==="ArrowRight"){e.preventDefault(),xe("right");return}if(i===38||t==="ArrowUp"){e.preventDefault(),xe("up");return}if(i===40||t==="ArrowDown"){e.preventDefault(),xe("down");return}if(i===13||i===32||t==="Enter"||t===" "){if(le()&&(!r||r===document.body||r.id==="tvPlayer"||r.id==="videoContainer")){e.preventDefault(),oe();let l=a("fsHudBar")?a("fsHudBar").querySelector(".hud-ctrl-btn"):null;l&&l.focus();return}if(Rt&&Rt.length>0){e.preventDefault(),wi();return}if(r&&r.tagName==="INPUT"&&(i===32||t===" "))return;if(r&&r.id==="inputSearch"){e.preventDefault(),e.stopPropagation(),It(!1);let l=a("listItems")?a("listItems").querySelector(".list-item-btn:not([disabled])"):null;l&&(l.focus(),A="channels");return}if(r&&r.classList&&r.classList.contains("scrollable-container")){e.preventDefault(),e.stopPropagation(),Ia(r);return}if(e.preventDefault(),r&&r!==document.body&&typeof r.click=="function")r.click();else{Se();let l=document.activeElement;l&&typeof l.click=="function"&&l.click()}return}if((i>=48&&i<=57||i>=96&&i<=105)&&!c){e.preventDefault();let l=i>=96&&i<=105?i-96:i-48;Ls(l);return}if(i===403||t==="Red"||t==="ColorF0Red"){if(e.preventDefault(),a("hudLoadingOverlay")&&!a("hudLoadingOverlay").classList.contains("hidden")){if(ge)try{ge.abort()}catch{}a("hudLoadingOverlay").classList.add("hidden"),De()}typeof ot=="function"?ot():Es();return}if(i===404||t==="Green"||t==="ColorF1Green"){if(e.preventDefault(),v){ra(v.url);let l=z.has(v.url);E(l?"⭐ Adicionado aos Favoritos":"Removido dos Favoritos")}else E("Selecione um canal para favoritar");return}if(i===405||t==="Yellow"||t==="ColorF2Yellow"){e.preventDefault(),typeof qo=="function"?qo():ki();return}if(i===406||t==="Blue"||t==="ColorF3Blue"||t==="b"||t==="B"){e.preventDefault(),typeof $n=="function"?$n():Qa();return}if(i===458||t==="Guide"||t==="EPG"){e.preventDefault(),typeof $n=="function"&&$n();return}if(i===457||t==="Info"){e.preventDefault(),typeof zo=="function"?zo():v&&E(v.name+" - "+(v.group||""));return}},!0);function ei(){function e(n){if(n){var o=a("inputSearch");if(o){o.value=n;try{o.dispatchEvent(new Event("input",{bubbles:!0}))}catch{he={ALL:[]},V=[],Lt=[],typeof Q=="function"&&Q(),typeof Y=="function"&&Y()}}typeof E=="function"&&E("Busca: "+n)}}try{if(typeof webOS!="undefined"&&webOS.service&&webOS.service.request){webOS.service.request("luna://com.webos.service.voice",{method:"start",parameters:{},onSuccess:function(n){e(n&&(n.transcript||n.text||n.result)||"")},onFailure:function(){t()}});return}}catch{}t();function t(){var n=window.SpeechRecognition||window.webkitSpeechRecognition;if(!n){typeof E=="function"&&E("Voz indisponível");var o=a("inputSearch");o&&o.focus();return}try{var i=new n;i.lang="pt-BR",i.onresult=function(r){e(r.results&&r.results[0]&&r.results[0][0]?r.results[0][0].transcript:"")},i.start(),typeof E=="function"&&E("Ouvindo...")}catch{}}}a("btnRefreshList").onclick=()=>{$t({preserveCatalog:!0}),ve(!0)};function js(){Ue?(Ue=!1,a("txtLockIcon")&&(a("txtLockIcon").textContent="🔒"),a("txtLockText")&&(a("txtLockText").textContent="Adulto (Bloqueado)"),Q(),te(),Ie("ALL"),alert("Canais adultos foram bloqueados com sucesso.")):(Fe="",a("pinScreen").textContent="----",a("modalPin").classList.remove("hidden"),A="modalPin")}document.querySelectorAll(".pin-key").forEach(e=>{e.onclick=()=>{let t=e.getAttribute("data-k");if(t==="C")Fe="",a("pinScreen").textContent="----";else if(t==="OK")ti();else if(Fe.length<4){Fe+=t;let n="";for(let o=0;o<4;o++)n+=o<Fe.length?"●":"-";a("pinScreen").textContent=n,Fe.length===4&&ti()}}});function ti(){if(Fe===Ke||Fe==="0000"||Fe==="8208"){Ue=!0,a("txtLockIcon")&&(a("txtLockIcon").textContent="🔓"),a("txtLockText")&&(a("txtLockText").textContent="Adulto (Liberado)"),a("modalPin").classList.add("hidden"),A="channels",Q(),te();let e=Ze.find(t=>ne(t));Ie(e||"ALL"),alert("Canais adultos liberados com sucesso!")}else alert("Senha incorreta! Digite novamente."),Fe="",a("pinScreen").textContent="----"}a("btnClosePin").onclick=()=>{a("modalPin").classList.add("hidden"),A="channels"};a("btnExitCancel").onclick=()=>{a("modalExitConfirm").classList.add("hidden"),A="header";try{Se()}catch{}};a("btnExitConfirm").onclick=nr;a("btnCloseOmnisearch")&&(a("btnCloseOmnisearch").onclick=Ai);a("btnClearOmnisearch")&&(a("btnClearOmnisearch").onclick=()=>{a("inputOmnisearch")&&(a("inputOmnisearch").value="",a("inputOmnisearch").focus(),Ya(""))});a("inputOmnisearch")&&(a("inputOmnisearch").addEventListener("input",e=>{clearTimeout(Go),Go=setTimeout(()=>{Ya(e.target.value.trim())},250)}),a("inputOmnisearch").addEventListener("keydown",e=>{if(e.key==="Enter"){let t=a("omnisearchResultsGrid")?a("omnisearchResultsGrid").querySelector(".omnisearch-item-btn"):null;t&&t.focus()}}));["tabOmniAll","tabOmniLive","tabOmniMovies","tabOmniSeries"].forEach(e=>{let t=a(e);t&&(t.onclick=()=>{document.querySelectorAll(".omnisearch-tab-btn").forEach(n=>n.classList.remove("active")),t.classList.add("active"),Ti=e.replace("tabOmni","").toUpperCase(),Zn()})});a("btnCloseEpgGuide")&&(a("btnCloseEpgGuide").onclick=()=>{let e=a("modalEpgGuide");e&&e.classList.add("hidden"),A="channels",Se()});a("btnCloseDiagnostics")&&(a("btnCloseDiagnostics").onclick=()=>{let e=a("modalStreamDiagnostics");e&&e.classList.add("hidden"),A="channels",Se()});typeof document!="undefined"&&document.addEventListener("webOSLowMemory",function(){console.warn("[MK21] webOSLowMemory — liberando buffers");try{if(ws(),typeof C!="undefined"&&C)try{C.destroy(),C=null}catch{}let e=document.getElementById("tvPlayer");if(e)try{e.pause(),e.removeAttribute("src"),e.load()}catch{}Qe=40,typeof Y=="function"&&Y()}catch{}},!1);window.addEventListener("load",()=>{try{let e=localStorage.getItem("mk21_font_size");e&&kt(e)}catch{}try{let e=localStorage.getItem("mk21_last_server");e!==null&&L[e]&&(_=parseInt(e,10))}catch{}try{let e=a("cfgToggleTmdb");e&&(e.checked=localStorage.getItem("mk21_use_tmdb")!=="false",e.onchange=()=>{localStorage.setItem("mk21_use_tmdb",e.checked)})}catch{}try{a("btnHeaderLogout")&&(a("btnHeaderLogout").onclick=()=>sn())}catch{}try{_s(),x("[App] MK21 Play v"+ht+" inicializado","success")}catch{}});var Z={isLoggedIn:!1,activeFocusIndex:0,focusableElements:[]},lt={UP:[38,"ArrowUp"],DOWN:[40,"ArrowDown"],LEFT:[37,"ArrowLeft"],RIGHT:[39,"ArrowRight"],ENTER:[13,"Enter"],BACK:[461,10009,27,"Escape","GoBack"]},vn=[".scrollable-container",".pane-scroll","#listItems","#listCategories","#serverList","#serverItemsGrid","#devLogsContainer",".settings-left-menu",".settings-detail-panel",".servers-manager-list",".omnisearch-body","#epgScheduleList"].join(", "),ae=null;function Ia(e,t){if(!e)return!1;let n=typeof t=="boolean"?t:ae!==e;return ae&&ae!==e&&(ae.classList.remove("scroll-mode-active"),ae._mk21ScrollRail&&ae._mk21ScrollRail.classList.remove("is-active")),n?(ae=e,e.classList.add("scroll-mode-active"),e._mk21ScrollRail&&e._mk21ScrollRail.classList.add("is-active"),An(e),typeof E=="function"&&E("📜 Modo Rolagem ATIVO (▲/▼ rolam · OK sai)"),!0):(e.classList.remove("scroll-mode-active"),e._mk21ScrollRail&&e._mk21ScrollRail.classList.remove("is-active"),ae===e&&(ae=null),!1)}function Ks(e){if(!e||typeof document=="undefined")return null;if(e._mk21ScrollRail&&document.body.contains(e._mk21ScrollRail))return e._mk21ScrollRail;let t=e.closest&&e.closest(".col-pane")||e;if(!t)return null;let n=document.createElement("div");n.className="mk21-vscroll-rail is-hidden",n.setAttribute("aria-hidden","true");let o=document.createElement("div");return o.className="mk21-vscroll-thumb",n.appendChild(o),t.appendChild(n),e._mk21ScrollRail=n,e._mk21ScrollThumb=o,e._mk21ScrollHost=t,e.dataset.mk21VisualScrollBound||(e.dataset.mk21VisualScrollBound="1",e.addEventListener("scroll",function(){q(e)},{passive:!0})),n}function q(e){if(!(!e||typeof window=="undefined"))try{let t=Ks(e),n=e._mk21ScrollThumb,o=e._mk21ScrollHost;if(!t||!n||!o)return;let i=e.clientHeight||0,r=e.scrollHeight||0;if(i<=0||r<=i+4){t.classList.add("is-hidden");return}if(o!==e){let p=Math.max(8,e.offsetTop+4);t.style.top=p+"px",t.style.bottom="8px"}else{let p=e.scrollTop||0;t.style.top=p+6+"px",t.style.height=Math.max(24,i-12)+"px",t.style.bottom="auto"}let s=t.clientHeight||Math.max(40,i-12),c=Math.max(44,Math.round(i/r*s)),u=Math.max(1,r-i),d=Math.max(0,s-c),l=Math.min(1,Math.max(0,(e.scrollTop||0)/u)),f=Math.round(l*d);n.style.height=c+"px",n.style.transform="translate3d(0, "+f+"px, 0)",t.classList.remove("is-hidden")}catch{}}function An(e){if(e)try{let t=e.scrollTop;e.classList.remove("force-scroll-visible"),e.style.setProperty("overflow-y","hidden","important"),e.offsetHeight,e.style.setProperty("overflow-y","scroll","important"),e.classList.add("force-scroll-visible"),e.scrollTop!==t&&(e.scrollTop=t),q(e)}catch{}}function sr(){if(typeof navigator=="undefined"||typeof document=="undefined")return;let e=navigator.userAgent||"",t=/webos|web0s|netcast/i.test(e)||typeof window.webOS!="undefined";if(window.__mk21VisualScrollInit||(window.__mk21VisualScrollInit=!0,["#home-container",".app-body","#sectionUnified","#sectionSettings",".col-categories",".col-channels",".col-player"].forEach(function(i){let r=document.querySelector(i);r&&r.addEventListener("scroll",function(){r.scrollTop!==0&&(r.scrollTop=0),r.scrollLeft!==0&&(r.scrollLeft=0)},{passive:!0})}),window.addEventListener("scroll",function(){(window.scrollX!==0||window.scrollY!==0)&&window.scrollTo(0,0)},{passive:!0}),document.addEventListener("focusin",function(i){let r=i.target;if(!r||typeof r.closest!="function")return;oa();let s=r.closest(vn);document.querySelectorAll(".mk21-vscroll-rail.is-active").forEach(function(c){c.classList.remove("is-active")}),s&&(lo(r),q(s),s._mk21ScrollRail&&s._mk21ScrollRail.classList.add("is-active"))},!0),window.addEventListener("resize",function(){oa(),document.querySelectorAll(vn).forEach(function(i){i.offsetParent!==null&&q(i)})},{passive:!0}),document.querySelectorAll(vn).forEach(function(i){i.offsetParent!==null&&q(i)})),!t||window.__mk21WebOSScrollFixActive)return;window.__mk21WebOSScrollFixActive=!0;let n=null;document.addEventListener("focusin",function(i){let r=i.target;if(!r||typeof r.closest!="function")return;let s=r.closest(vn);s&&(n&&n!==s&&n.classList.remove("force-scroll-visible"),n=s,An(s))},!0),document.querySelectorAll(vn).forEach(function(i){i.offsetParent!==null&&An(i)})}function ni(){sr(),or(),Js(),Qs(),a("btnHeaderLogout")&&(a("btnHeaderLogout").onclick=()=>sn());let e=Pn();if(e&&e.user&&e.pass){if(typeof e.serverIndex=="number"&&L[e.serverIndex]){_=e.serverIndex;try{localStorage.setItem("mk21_last_server",_)}catch{}}co(!0)}else lr()}function Ys(){let e=document.getElementById("server-select");e&&(e.innerHTML="",L.forEach((t,n)=>{let o=document.createElement("option");o.value=n,o.textContent=`Fonte ${n+1}: ${X(t.name)}`,n===_&&(o.selected=!0),e.appendChild(o)}),e.onchange=()=>{_=parseInt(e.value,10)||0;try{localStorage.setItem("mk21_last_server",_)}catch{}})}function lr(e,t){Z.isLoggedIn=!1;let n=a("mk21-toast-banner");n&&n.classList.remove("visible");let o=document.getElementById("home-container"),i=document.getElementById("login-container"),r=document.getElementById("login-feedback");o&&(o.style.display="none"),i&&(i.style.display="flex"),r&&(r.textContent="",r.style.display="none",r.classList.remove("is-error","is-info","is-success")),e&&Ye(e,!!t),Ys();let s=document.getElementById("user-input"),c=document.getElementById("pass-input");s&&(s.value=""),c&&(c.value=""),Xs(),setTimeout(()=>{$e(1)},120);let u=document.getElementById("btn-login");u&&(u.onclick=Zs);let d=document.getElementById("btn-offline");d&&(d.onclick=el)}function sn(){try{typeof wn=="function"&&wn(!0)}catch{}At&&(clearTimeout(At),At=null),$t(),Fr(),v=null,Z.isLoggedIn=!1;let e=a("hudLoadingOverlay");e&&e.classList.add("hidden"),document.querySelectorAll(".app-modal, .modal-overlay").forEach(t=>t.classList.add("hidden")),lr("Sessão encerrada. Informe suas credenciais para entrar.",!1)}function Xs(){Z.focusableElements=[document.getElementById("server-select"),document.getElementById("user-input"),document.getElementById("pass-input"),document.getElementById("btn-login"),document.getElementById("btn-offline")].filter(Boolean)}function $e(e){if(!Z.focusableElements.length)return;e<0&&(e=Z.focusableElements.length-1),e>=Z.focusableElements.length&&(e=0),Z.activeFocusIndex=e;let t=Z.focusableElements[e];Z.focusableElements.forEach(n=>n.classList.remove("focused")),t.classList.add("focused"),t.focus()}function Js(){window.addEventListener("keydown",e=>{if(lt.BACK.includes(e.keyCode)||lt.BACK.includes(e.key)){e.preventDefault(),document.activeElement&&(document.activeElement.tagName==="INPUT"||document.activeElement.tagName==="SELECT")&&(document.activeElement.blur(),$e(Z.activeFocusIndex));return}if(!Z.isLoggedIn){if(lt.DOWN.includes(e.keyCode)||lt.DOWN.includes(e.key))e.preventDefault(),$e(Z.activeFocusIndex+1);else if(lt.UP.includes(e.keyCode)||lt.UP.includes(e.key))e.preventDefault(),$e(Z.activeFocusIndex-1);else if(lt.ENTER.includes(e.keyCode)||lt.ENTER.includes(e.key)){let t=Z.focusableElements[Z.activeFocusIndex];t&&t.tagName==="BUTTON"&&(e.preventDefault(),t.click())}}})}function Qs(){document.querySelectorAll(".input-field").forEach(t=>{t.addEventListener("focus",()=>document.body.classList.add("osk-open")),t.addEventListener("blur",()=>document.body.classList.remove("osk-open"))})}async function Zs(){let e=document.getElementById("server-select");if(e){_=parseInt(e.value,10)||0;try{localStorage.setItem("mk21_last_server",_)}catch{}}let t=document.getElementById("user-input"),n=document.getElementById("pass-input"),o=t?t.value.trim():"",i=n?n.value.trim():"";if(!o||!i){Ye("Informe o usuário e a senha / token.",!0),$e(o?2:1);return}$t({preserveCatalog:!1}),Xe=typeof AbortController!="undefined"?new AbortController:null;let r=Xe,s=r?setTimeout(()=>r.abort(),7e3):null;Ye("Autenticando e validando credenciais...",!1);let c=L[_]||L[0],u="mk21_session_ok";if(c&&c.url)try{let p=`${c.url.replace(/\/+$/,"")}/player_api.php?username=${encodeURIComponent(o)}&password=${encodeURIComponent(i)}`,m=await fetch(p,{method:"GET",headers:{Accept:"application/json"},signal:r?r.signal:void 0});if(s&&clearTimeout(s),Xe!==r)return;let b=await m.text();if(m.status===401||m.status===403){Ye("Credenciais inválidas neste servidor. Verifique usuário e senha.",!0),$e(1);return}if(m.ok){let g=Ce.parseSafeJsonResponse(b,m.status);if(g&&g.user_info){if(Number(g.user_info.auth)===0){Ye("Usuário ou senha inválidos no servidor.",!0),$e(1);return}let h=String(g.user_info.status||"").toLowerCase();if(h==="expired"||h==="disabled"||h==="banned"){Ye("Acesso "+g.user_info.status+" neste servidor. Verifique seu plano.",!0),$e(1);return}Number(g.user_info.auth)===1&&(u=g.user_info.token||"mk21_auth_ok")}}}catch(f){if(s&&clearTimeout(s),f&&f.name==="AbortError"&&Xe!==r)return;if(f&&f.isAuthError){Ye(f.message||"Usuário ou senha inválidos no servidor.",!0),$e(1);return}console.warn("[Login] Validação online indisponível (M3U direto / CORS), seguindo para validação de lista:",f)}finally{s&&clearTimeout(s),Xe===r&&(Xe=null)}else s&&clearTimeout(s);let d=Pn();Jn=!d||d.user!==o||d.pass!==i||d.serverIndex!==_||!!(a("hudLoadingError")&&!a("hudLoadingError").classList.contains("hidden")),Dr(o,i,_);try{localStorage.setItem("mk21_auth_token",u)}catch{}Ye("Credenciais salvas! Carregando catálogo...",!1,"success"),co(!0)}async function el(){let e=L[_]||L[0],t=e?await cr(e.id):null;if(!t||!t.LIVE||t.LIVE.length===0){Ye("Nenhum catálogo em cache local. Informe usuário e senha para conectar.",!0),$e(1);return}Jn=!1,co(!0)}async function cr(e){try{let t=await an(e);if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{let t=Zt(e);if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{let t=Zt("universal");if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{for(let t of L)if(t.id&&t.id!==e){let n=await an(t.id);if(n&&n.LIVE&&n.LIVE.length>0)return n;let o=Zt(t.id);if(o&&o.LIVE&&o.LIVE.length>0)return o}}catch{}return null}function tl(){if(!Z.isLoggedIn||ut||xn||Vt()==="manual")return;let t=()=>{!Z.isLoggedIn||ut||xn||la(!1).catch(()=>{})};typeof window!="undefined"&&typeof window.requestIdleCallback=="function"?window.requestIdleCallback(t,{timeout:5e3}):setTimeout(t,1200)}function da(){if(At&&(clearTimeout(At),At=null),!Z.isLoggedIn)return;let e=Vt();if(e==="manual"||e==="startup")return;let t=Ce.getBgSyncIntervalMs(e);if(!t||t<=0)return;let n=L[_],o=0;try{n&&(o=parseInt(localStorage.getItem("mk21_last_sync_"+n.id)||"0",10)||0)}catch{}let i=o>0?Math.max(0,Date.now()-o):0,r=Math.max(6e4,Math.min(t,t-i));At=setTimeout(()=>{tl()},r)}async function nl(){L.length===0&&(L=[...Ot]),_>=L.length&&(_=0);let e=L[_];a("txtActiveServer")&&(a("txtActiveServer").textContent=X(e.name)),a("hudLoadingOverlay")&&a("hudLoadingOverlay").classList.add("hidden"),De();let t=Jn;Jn=!1;let n=t?null:await cr(e.id);if(n&&n.LIVE&&n.LIVE.length>0&&!t){w={LIVE:n.LIVE||[],MOVIE:n.MOVIE||[],SERIES:n.SERIES||[]},nt(e.id,w,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),Q(),te(),Ie("ALL"),Y(),ct(100,!0),typeof We=="function"&&We(),setTimeout(()=>{bi()},100),setTimeout(()=>{A="channels";let r=a("listItems")?a("listItems").querySelector(".list-item-btn"):null;if(r)try{r.focus()}catch{}},200);let o=Vt(),i=0;try{i=parseInt(localStorage.getItem("mk21_last_sync_"+e.id)||"0",10)||0}catch{}(o==="startup"||Ce.shouldSyncServer(i,o))&&setTimeout(()=>{Rn()?la(!1).catch(()=>{}):ve(!0,{silent:!0,backgroundLowPriority:!0}).catch(()=>{})},1500),da();return}ve(t)}function co(e=!0){Z.isLoggedIn=!0;let t=document.getElementById("login-container");t&&(t.style.display="none");let n=document.getElementById("home-container");if(n&&(n.style.display="flex",n.classList.remove("hidden")),a("sectionSettings")&&a("sectionSettings").classList.add("hidden"),a("sectionUnified")&&a("sectionUnified").classList.remove("hidden"),typeof window.initHomeGrid=="function")try{window.initHomeGrid()}catch{}if((window.requestAnimationFrame||function(i){return setTimeout(i,0)})(function(){oa(),["#listCategories","#listItems"].forEach(function(i){let r=document.querySelector(i);r&&(window.__mk21WebOSScrollFixActive?An(r):q(r))}),A="header",Ka=0,a("tabLive")?Oe(a("tabLive")):typeof Se=="function"&&Se()}),e)try{nl()}catch(i){console.error("Erro ao inicializar cache:",i);try{ve(!1)}catch{}}da()}function Ye(e,t,n){let o=document.getElementById("login-feedback");if(o){o.textContent=e,o.style.display="block",o.classList.remove("is-error","is-info","is-success");let i=n||(t?"error":"info");o.classList.add("is-"+i),o.style.color=""}}document.readyState==="loading"?document.addEventListener("DOMContentLoaded",ni):ni();var Nt=0,Cn=new Set,Pa=!1;function dr(){return localStorage.getItem("mk21_playback_quality")||"auto"}function al(){let e=typeof C!="undefined"?C:null,t=new Set;e&&Array.isArray(e.levels)&&e.levels.forEach(o=>{o&&o.height&&t.add(o.height)});let n=Array.from(t).sort((o,i)=>i-o);return n.length||[1080,720,480].forEach(o=>n.push(o)),n}function In(e){let t=e||(typeof C!="undefined"?C:null);if(!t||!Array.isArray(t.levels)||!t.levels.length)return;let n=dr();try{if(n==="auto"){t.autoLevelCapping=-1,t.currentLevel=-1;return}let o=parseInt(n,10)||1080,i=-1;t.levels.forEach((r,s)=>{!r||!r.height||r.height<=o&&(i<0||r.height>t.levels[i].height)&&(i=s)}),i<0&&(i=0),t.autoLevelCapping=i,t.currentLevel=i,x("[Qualidade] Fixada em "+t.levels[i].height+"p","info")}catch(o){x("[Qualidade] Não foi possível aplicar: "+(o&&o.message),"warn")}}var ol=[["qualidade","🎞️ Qualidade"],["buffer","⚡ Buffer"],["fonte","📡 Fonte"]],ln="qualidade";function il(){let e=a("modalPlayerOptions");return e||(e=document.createElement("div"),e.id="modalPlayerOptions",e.className="app-modal hidden",e.innerHTML='<div class="modal-content" style="width: 920px; max-width: 94vw; max-height: 86vh; display: flex; flex-direction: column;"><div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; gap: 16px;"><span style="font-size: 22px; font-weight: 800; color: #ffd54f;">⚙️ Opções do player</span><button id="btnClosePlayerOptions" class="ctrl-btn" tabindex="0" style="padding: 6px 14px;">✕ Fechar</button></div><div class="player-options-tabs" id="playerOptionsTabs"></div><div id="playerOptionsBody"></div></div>',document.body.appendChild(e),a("btnClosePlayerOptions")&&(a("btnClosePlayerOptions").onclick=rl),e)}function ur(){let e=a("playerOptionsTabs");e&&(e.replaceChildren(),ol.forEach(t=>{let n=document.createElement("button");n.type="button",n.className="player-options-tab"+(ln===t[0]?" active":""),n.tabIndex=0,n.textContent=t[1],n.onclick=()=>{ln=t[0],ur(),fr();let o=document.querySelector(".player-options-tab.active");o&&o.focus()},e.appendChild(n)}))}function fr(){let e=a("playerOptionsBody");if(e){sa=e;try{ln==="buffer"?ao():ln==="fonte"?ft():uo()}catch(t){x("[UI] Falha ao montar opções do player: "+(t&&t.message),"error")}}}function pr(e){let t=il();ln=e||"qualidade",ye(t,!0),t.classList.remove("hidden"),A="modalPlayerOptions",ur(),fr(),x("[UI] Opções do player: "+ln,"info"),setTimeout(()=>{let n=t.querySelector(".player-options-tab.active")||t.querySelector("button");n&&n.focus&&n.focus()},50)}function rl(){let e=a("modalPlayerOptions");e&&(e.classList.add("hidden"),ye(e,!1)),sa=null;let t=typeof le=="function"&&le(),n=a(t?"fsBtnQuality":"btnQuality");n&&n.focus&&n.focus(),t&&oe()}function ia(){pr("qualidade")}function uo(){let e=eo();if(!e)return;let t=dr(),n=al(),i=[["auto","Automática (recomendada)","O player escolhe a melhor faixa conforme a internet."]].concat(n.map(r=>[String(r),"Até "+r+"p","Limita o canal em "+r+"p."])).map(r=>{let s=t===r[0];return'<button class="track-item-btn quality-option'+(s?" active":"")+'" data-quality="'+r[0]+'" tabindex="0" style="text-align:left; padding: 16px 20px; font-size: 20px; display: flex; flex-direction: column; gap: 4px;"><span style="font-weight: 800;">'+(s?"✓ ":"")+r[1]+'</span><span style="font-size: 14px; color: #94a3b8; font-weight: 500;">'+r[2]+"</span></button>"}).join("");e.innerHTML='<h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">🎞️ Qualidade de vídeo</h3><p style="margin:0 0 18px 0; font-size:16px; color:#94a3b8;">Vale para todos os canais e fica salva no aparelho.</p><div style="display:flex; flex-direction:column; gap:10px;">'+i+"</div>",e.querySelectorAll(".quality-option").forEach(r=>{r.onclick=()=>{localStorage.setItem("mk21_playback_quality",r.getAttribute("data-quality")),In(),uo(),x("[Qualidade] "+r.textContent.trim(),"success"),E("Qualidade: "+r.textContent.trim());let s=e.querySelector(".quality-option.active");s&&s.focus()}})}function sl(e,t,n){if(e&&e.isAuthError||!Array.isArray(L)||L.length<2)return!1;if(Nt>=L.length-1)return Nt=0,x("[AutoServidor] Todos os servidores falharam","error"),!1;let o=(_+1)%L.length,i=L[o];Nt+=1;let r=t&&t.name?X(t.name):"Servidor atual";x("[AutoServidor] "+r+" falhou ("+(e&&e.message)+"); tentando "+X(i.name),"warn"),E("🔄 "+r+" falhou. Trocando para "+X(i.name)+"..."),_=o,a("txtActiveServer")&&(a("txtActiveServer").textContent=X(i.name));try{localStorage.setItem("mk21_last_server",_)}catch{}if(typeof Ft=="function")try{Ft()}catch{}return He&&clearTimeout(He),He=setTimeout(()=>{He=null,!(typeof n=="number"&&n!==me)&&typeof ve=="function"&&ve(!0,{fromAutoAdvance:!0})},1500),!0}var ai="";function oi(e){let t=e&&e.name?String(e.name):"";if(t&&t===ai){e&&e.url&&Cn.add(e.url);return}ai=t,Cn=new Set,e&&e.url&&Cn.add(e.url)}function ll(e,t,n){if(!n)return!1;try{return n.matchesAcrossServers(e,t)}catch{return!1}}async function ii(){if(!v||Pa)return!1;Pa=!0;let e=window.MK21AlternativeSources||null;try{let t=[];e&&e.sourceUrls(v).forEach(o=>t.push({url:o,origem:"mesma lista"}));for(let o=0;o<L.length;o++){let i=L[o];if(!i||L[_]&&i.id===L[_].id)continue;let r=null;try{r=Zt(i.id)||await an(i.id)}catch{r=null}let c=(r&&Array.isArray(r.LIVE)?r.LIVE:[]).find(u=>ll(v,u,e));c&&c.url&&t.push({url:c.url,origem:X(i.name)})}let n=t.find(o=>o.url&&!Cn.has(o.url));return n?(Cn.add(n.url),Oa="",x("[AutoFonte] Alternando para "+n.origem,"warn"),E("🔄 Fonte alternativa: "+n.origem),Je=0,Ve=!1,de(v,n.url),!0):(x("[AutoFonte] Nenhuma fonte alternativa disponível para "+v.name,"warn"),!1)}finally{Pa=!1}}function za(){[["btnQuality","qualidade"],["btnBufferMode","buffer"],["btnQuickServer","fonte"],["fsBtnQuality","qualidade"],["fsBtnBuffer","buffer"],["fsBtnServer","fonte"]].forEach(t=>{let n=a(t[0]);n&&(n.onclick=()=>pr(t[1]))})}document.readyState==="loading"?document.addEventListener("DOMContentLoaded",za):za();setTimeout(za,1500);typeof window!="undefined"&&(window.FBG2PlayerService=Fn,setTimeout(()=>{try{Fn.hydrateRemote()}catch(e){console.warn("[PlayerService] hydrateRemote falhou:",e)}},1500));})();
+💡 Alternativa: Você pode baixar o pacote IPK v`+t+" apontando a câmera do celular para o QR Code abaixo e instalá-lo via webOS Dev Manager."),o&&(o.disabled=!1),r("btnCheckAgainUpdate")&&(r("btnCheckAgainUpdate").disabled=!1)}}function uc(){if(confirm("Limpar cache de atualização OTA e voltar à versão do pacote instalado (v"+Hn+")?")){try{localStorage.removeItem("mk21_ota_app_js"),localStorage.removeItem("mk21_ota_styles_css"),localStorage.removeItem("mk21_ota_app_version")}catch{}alert("Cache OTA limpo. Reiniciando na versão base v"+Hn),window.location.reload()}}r("btnCloseUpdateModal").onclick=()=>{r("modalAppUpdate").classList.add("hidden"),T="settings"};r("btnStartDirectUpdate").onclick=Us;r("btnCheckAgainUpdate").onclick=()=>Na(!0);r("btnForceResetCache")&&(r("btnForceResetCache").onclick=uc);function Lo(){let e=r("tvPlayer"),t=window.FBG2PlayerService;if(t&&t.isActive()){let o=(t.player&&t.player.getState?t.player.getState():"playing")==="paused";t.toggle(),typeof de=="function"&&de(o),E(o?"▶ Reproduzindo":"⏸ Pausado");return}e&&(e.paused?(e.play().catch(()=>{}),typeof de=="function"&&de(!0),E("▶ Reproduzindo")):(e.pause(),typeof de=="function"&&de(!1),E("⏸ Pausado")))}function De(e){if(!e||e.disabled||e.closest&&e.closest(".hidden"))return!1;try{let t=window.getComputedStyle(e);if(t.display==="none"||t.visibility==="hidden"||t.opacity==="0")return!1}catch{}if(e.offsetParent!==null||e===document.activeElement)return!0;try{return window.getComputedStyle(e).position==="fixed"}catch{}return!1}function yn(){let e=["modalApkRadioPicker","modalStagedLoading","modalDevLogs","hudLoadingOverlay","modalStreamDiagnostics","modalOmnisearch","modalEpgGuide","modalSortPicker","modalAudioSubs","modalServerPicker","modalPin","modalExitConfirm","modalSeriesEpisodes","modalAppUpdate"];for(let n=0;n<e.length;n++){let o=r(e[n]);if(o&&!o.classList.contains("hidden")&&De(o))return o}let t=document.querySelector('.app-modal:not(.hidden), .modal-overlay:not(.hidden), [role="dialog"]:not(.hidden), .modal-container:not(.hidden)');return t&&De(t)?t:null}function Gs(){let t=yn()||document,n=["button:not([disabled])","input:not([disabled])","select:not([disabled])","textarea:not([disabled])",'[tabindex="0"]',".modal-btn",".modal-button",".header-focusable",".list-item-btn",".cat-item-btn",".ctrl-btn",".cfg-menu-item",".hud-ctrl-btn",".pin-key",".season-tab-btn",".episode-item-btn",".btn-server-connect",".btn-server-sync-one",".btn-server-edit",".btn-server-delete",".apk-setting-card",".apk-radio-option",".apk-staged-row",".apk-btn-cancel",".apk-btn-save",".sort-choice-btn"].join(",");return Array.from(t.querySelectorAll(n)).filter(function(o){if(!De(o))return!1;if(o.classList&&o.classList.contains("scrollable-container")){let a=o.querySelector(".list-item-btn:not([disabled]), .cat-item-btn:not([disabled]), .cfg-menu-item:not([disabled]), .btn-server-connect:not([disabled])");if(a&&De(a))return!1}return!0})}function Hr(e){let t=e.getBoundingClientRect();return{x:t.left+t.width/2,y:t.top+t.height/2,r:t}}function wo(){try{(window.scrollX!==0||window.scrollY!==0)&&window.scrollTo(0,0),document.documentElement&&(document.documentElement.scrollTop=0,document.documentElement.scrollLeft=0),document.body&&(document.body.scrollTop=0,document.body.scrollLeft=0);let e=["#home-container",".app-body",".app-header","#sectionUnified","#sectionSettings",".col-categories",".col-channels",".col-player"];for(let t=0;t<e.length;t++){let n=document.querySelector(e[t]);n&&(n.scrollTop!==0&&(n.scrollTop=0),n.scrollLeft!==0&&(n.scrollLeft=0))}}catch{}}function _a(e){if(!(!e||typeof e.closest!="function"))try{let t=e.closest("#listCategories, #listItems, .settings-left-menu, .settings-detail-panel, .pane-scroll, .scrollable-container, #serverItemsGrid, #devLogsContainer, #epgScheduleList, .omnisearch-body");if(!t||t===e)return;let n=t.getBoundingClientRect(),o=e.getBoundingClientRect(),a=8;o.top<n.top+a?t.scrollTop+=o.top-(n.top+a):o.bottom>n.bottom-a&&(t.scrollTop+=o.bottom-(n.bottom-a)),typeof X=="function"&&X(t)}catch{}}function Se(e){if(!(!e||typeof e.focus!="function")){try{e.focus({preventScroll:!0})}catch{try{e.focus()}catch{}}_a(e),wo()}}function ke(e){let t=Gs();if(!t.length)return;let n=document.activeElement;if(!n||t.indexOf(n)===-1){Se(t[0]);return}let o=Hr(n),a=null,s=1/0;for(let i=0;i<t.length;i++){let c=t[i];if(c===n)continue;let u=Hr(c),d=u.x-o.x,l=u.y-o.y,f=!1,p=0,m=0;if(e==="left"?(f=d<-8,p=-d,m=Math.abs(l)):e==="right"?(f=d>8,p=d,m=Math.abs(l)):e==="up"?(f=l<-8,p=-l,m=Math.abs(d)):e==="down"&&(f=l>8,p=l,m=Math.abs(d)),!f)continue;let y=p+m*2.5;y<s&&(s=y,a=c)}if(a){Se(a);try{if(a.id&&typeof fn!="undefined"&&fn.indexOf(a.id)!==-1)T="header",Sa=fn.indexOf(a.id);else if(a.classList.contains("cat-item-btn")){T="categories";let c=Array.from((r("listCategories")||document).querySelectorAll(".cat-item-btn")).indexOf(a);c>=0&&(cs=c)}else if(a.classList.contains("list-item-btn")){T="channels";let i=Array.from((r("listItems")||document).querySelectorAll(".list-item-btn")),c=i.indexOf(a);if(c>=0){ds=c;let u=r("badgeItemsCount");u&&i.length>0&&(u.textContent=c+1+" / "+i.length)}}else a.classList.contains("cfg-menu-item")&&(T="settings")}catch{}}}function Le(){try{let e=Gs();if(!e.length)return;let t=null;if(T==="header"&&typeof fn!="undefined")t=r(fn[Sa]||fn[0]);else if(T==="categories"){let n=r("listCategories")?r("listCategories").querySelectorAll(".cat-item-btn"):[];t=n[cs]||n[0]}else if(T==="channels"){let n=r("listItems")?r("listItems").querySelectorAll(".list-item-btn"):[];t=n[ds]||n[0]}else T==="settings"&&(t=document.querySelector(".cfg-menu-item.active")||r("cfgBtnInfo"));(!t||!De(t))&&(t=e[0]),t&&Se(t)}catch(e){console.warn("[MK21] focusActiveElement",e)}}var qs=!1;function Ws(e){if(e){if(typeof e._onCloseCallback=="function"){let t=e._onCloseCallback;t();return}if(e.classList.add("hidden"),xe(e,!1),yt=null,(e.id==="modalPlayerOptions"||e.id==="modalQuality")&&(Mo=null),ue()){T="player",ie();let t=r("fsBtnPlay")&&De(r("fsBtnPlay"))?r("fsBtnPlay"):r("videoContainer");try{t&&t.focus()}catch{}return}T=typeof S!="undefined"&&S==="SETTINGS"?"settings":"channels";try{Le()}catch{}}}function fc(){let e=r("modalExitConfirm");if(!e){js();return}e.classList.remove("hidden");let t=r("btnExitCancel");if(t)try{t.focus()}catch{}}function js(){qs=!0;try{if(window.webOS&&typeof webOS.platformBack=="function"){webOS.platformBack();return}}catch{}try{window.close()}catch{}}function Ks(){let e=r("hudLoadingOverlay");if(e&&!e.classList.contains("hidden")){if(ye)try{ye.abort()}catch{}return e.classList.add("hidden"),ze(),E("⚠️ Carregamento cancelado"),!0}let t=r("modalExitConfirm");if(t&&!t.classList.contains("hidden")){t.classList.add("hidden"),T="header";try{Le()}catch{}return!0}let n=yn();if(n)return Ws(n),!0;let o=r("inputSearch");if(document.activeElement&&document.activeElement.id==="inputSearch"||o&&o.value.trim().length>0||T==="search"){try{Dt(!0)}catch{}return!0}if(ue()){try{En(!1)}catch{}return!0}if(typeof S!="undefined"&&S!=="LIVE"){try{Pe("LIVE")}catch{}T="channels";try{Le()}catch{}return!0}return fc(),!0}function Xs(){try{(!history.state||!history.state.mk21)&&history.pushState({mk21:1},"")}catch{}}typeof window!="undefined"&&window.addEventListener("popstate",function(){qs||(Ks(),Xs())});var yt=null;function ea(e){let t=e;for(;t&&t!==document.body&&t.nodeType===1;){let n=null;try{n=window.getComputedStyle(t)}catch{}if(n&&/(auto|scroll)/.test(n.overflowY)&&t.scrollHeight>t.clientHeight+4)return t;t=t.parentElement}return null}function pc(e){if(!e)return null;let t=null,n=e.querySelectorAll("div, ul, ol, section, .modal-content");for(let o=0;o<n.length;o++){let a=n[o],s=null;try{s=window.getComputedStyle(a)}catch{}!s||!/(auto|scroll)/.test(s.overflowY)||a.scrollHeight<=a.clientHeight+4||(!t||a.clientHeight>t.clientHeight)&&(t=a)}return t}function Ys(){let e=document.activeElement,t=e?ea(e):null;if(t)return yt=t,t;let n=yn();if(n){let o=ea(n)||pc(n);if(o)return o}return yt&&document.body.contains(yt)&&!yt.closest(".hidden")?yt:ea(r("listItems"))||null}function mc(e,t){let n=Ys();if(!n)return!1;let o=t||Math.max(120,Math.round(n.clientHeight*.7)),a=n.scrollTop;return n.scrollTop=a+e*o,n.scrollTop!==a}function Js(e){if(document.body.classList.contains("is-fullscreen"))return;let t=Ys();if(!t)return;let n=(e.deltaY||0)*3.2;if(!n)return;let o=t.scrollTop;t.scrollTop=o+n,t.scrollTop!==o&&(yt=t,e.preventDefault())}window.addEventListener("wheel",Js,{passive:!1});window.addEventListener("mousewheel",Js,{passive:!1});document.addEventListener("keydown",function(e){let t=e.key||"",n=e.code||"",o={ArrowLeft:37,ArrowUp:38,ArrowRight:39,ArrowDown:40,Enter:13,Escape:27,Backspace:8,GoBack:461},a=e.keyCode||e.which||o[t]||o[n]||0,s=document.activeElement;if(typeof se!="undefined"&&se){if(!document.body.contains(se)||!De(se))ta(se,!1);else if(a===38||t==="ArrowUp"){e.preventDefault(),e.stopPropagation(),se.scrollTop=Math.max(0,se.scrollTop-96),typeof X=="function"&&X(se);return}else if(a===40||t==="ArrowDown"){e.preventDefault(),e.stopPropagation(),se.scrollTop+=96,typeof X=="function"&&X(se);return}else if(a===13||t==="Enter"||a===461||a===10009||a===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),ta(se,!1);return}}if(typeof F!="undefined"&&F.open){if(a===461||a===10009||a===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),$a(!1);return}if(a===37||t==="ArrowLeft"){e.preventDefault(),co("left");return}if(a===39||t==="ArrowRight"){e.preventDefault(),co("right");return}if(a===38||t==="ArrowUp"){e.preventDefault(),co("up");return}if(a===40||t==="ArrowDown"){e.preventDefault(),co("down");return}if(a===13||t==="Enter"){e.preventDefault(),_c();return}if(a===8||t==="Backspace"){e.preventDefault(),Oc();return}return}if(typeof P!="undefined"&&P.open){if(a===461||a===10009||a===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),Va();return}if(a===37||t==="ArrowLeft"){e.preventDefault(),lo("left");return}if(a===39||t==="ArrowRight"){e.preventDefault(),lo("right");return}if(a===38||t==="ArrowUp"){e.preventDefault(),lo("up");return}if(a===40||t==="ArrowDown"){e.preventDefault(),lo("down");return}if(a===13||t==="Enter"){e.preventDefault(),Nc();return}return}let i=yn();if(ue()&&!i&&ie(),i){let l=Array.from(i.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"]), .modal-btn, .modal-button, .ctrl-btn, .pin-key, .btn-server-connect, .btn-server-sync-one, .btn-server-edit, .btn-server-delete, .hud-ctrl-btn, .apk-setting-card, .apk-radio-option, .apk-staged-row, .apk-btn-cancel, .apk-btn-save, .sort-choice-btn')).filter(De);if(l.length>0){let f=l[0],p=l[l.length-1];if((!i.contains(document.activeElement)||l.indexOf(document.activeElement)===-1)&&f.focus(),a===461||a===10009||a===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),Ws(i);return}let m=s?s.tagName:"",y=m==="INPUT"||m==="TEXTAREA"||m==="SELECT",g=t==="Tab"||a===9,h=t==="ArrowDown"||a===40,b=t==="ArrowUp"||a===38;if(g){if(e.shiftKey&&document.activeElement===f){e.preventDefault(),p.focus();return}else if(!e.shiftKey&&document.activeElement===p){e.preventDefault(),f.focus();return}}if(h&&document.activeElement===p){e.preventDefault(),f.focus();return}else if(b&&document.activeElement===f){e.preventDefault(),p.focus();return}if(a===37||t==="ArrowLeft"){if(y)return;e.preventDefault(),ke("left");return}if(a===39||t==="ArrowRight"){if(y)return;e.preventDefault(),ke("right");return}if(a===38||t==="ArrowUp"){e.preventDefault(),ke("up");return}if(a===40||t==="ArrowDown"){e.preventDefault(),ke("down");return}}}if(s&&s.id==="inputSearch"){if(a===40||t==="ArrowDown"){e.preventDefault(),Dt(!1);let l=r("listItems")?r("listItems").querySelector(".list-item-btn:not([disabled])"):null;if(l)Se(l),T="channels";else{let f=r("listCategories")?r("listCategories").querySelector(".cat-item-btn.active")||r("listCategories").querySelector(".cat-item-btn"):null;f&&(Se(f),T="categories")}return}if(a===38||t==="ArrowUp"){e.preventDefault(),Dt(!1);let l=document.querySelector(".main-tabs .tab-btn.active")||r("tabLive");l&&(Se(l),T="header");return}if((a===37||t==="ArrowLeft")&&(!s.value||s.selectionStart===0)){e.preventDefault(),Dt(!1);let l=r("listCategories")?r("listCategories").querySelector(".cat-item-btn.active")||r("listCategories").querySelector(".cat-item-btn"):null;l&&(Se(l),T="categories");return}if((a===39||t==="ArrowRight")&&(!s.value||s.selectionStart===s.value.length)){e.preventDefault();let l=r("btnClearSearch")||r("btnFullscreen")||r("btnPlayPause");l&&Se(l);return}}let c=s&&/^(INPUT|TEXTAREA|SELECT)$/.test(s.tagName);if(c&&s.id!=="inputSearch"&&["ArrowLeft","ArrowUp","ArrowRight","ArrowDown"].includes(t))return;if(t==="Voice"||t==="VoiceCommand"||a===1022||a===1016||a===166){e.preventDefault(),typeof zr=="function"&&zr();return}if(a===179||a===10252||a===415||a===250||a===19||a===413||t==="MediaPlayPause"||t==="MediaPlay"||t==="MediaPause"||t==="Play"||t==="Pause"){e.preventDefault(),typeof Lo=="function"&&Lo();return}if(a===461||a===10009||a===27||t==="Escape"||t==="Back"){e.preventDefault(),e.stopPropagation(),Ks();return}if(a===33||a===34){e.preventDefault(),mc(a===33?-1:1);return}if(a===427||t==="ChannelUp"||n==="ChannelUp"){e.preventDefault(),e.stopPropagation(),S==="LIVE"?ca():(ln(3e4),E("⏩ +30s"),St()),ue()&&ie();return}if(a===428||t==="ChannelDown"||n==="ChannelDown"){e.preventDefault(),e.stopPropagation(),S==="LIVE"?da():(ln(-3e4),E("⏪ -30s"),St()),ue()&&ie();return}if(ue()){ie();let l=r("fsHudBar"),f=l&&!l.classList.contains("hidden"),p=f&&s&&s.classList&&s.classList.contains("hud-ctrl-btn");if(a===38||t==="ArrowUp"){if(e.preventDefault(),p){let m=s.getBoundingClientRect();if(Array.from(l.querySelectorAll(".hud-ctrl-btn")).some(function(g){return g!==s&&De(g)&&g.getBoundingClientRect().top+g.getBoundingClientRect().height/2<m.top+m.height/2-14})){ke("up");return}}S==="LIVE"&&da();return}if(a===40||t==="ArrowDown"){if(e.preventDefault(),p){let m=s.getBoundingClientRect();if(Array.from(l.querySelectorAll(".hud-ctrl-btn")).some(function(g){return g!==s&&De(g)&&g.getBoundingClientRect().top+g.getBoundingClientRect().height/2>m.top+m.height/2+14})){ke("down");return}}S==="LIVE"&&ca();return}if(a===37||t==="ArrowLeft"){e.preventDefault(),f&&s&&s.classList.contains("hud-ctrl-btn")?ke("left"):S!=="LIVE"?(ln(-1e4),E("⏪ -10s"),St()):ke("left");return}if(a===39||t==="ArrowRight"){e.preventDefault(),f&&s&&s.classList.contains("hud-ctrl-btn")?ke("right"):S!=="LIVE"?(ln(1e4),E("⏩ +10s"),St()):ke("right");return}}if(a===37||t==="ArrowLeft"){e.preventDefault(),ke("left");return}if(a===39||t==="ArrowRight"){e.preventDefault(),ke("right");return}if(a===38||t==="ArrowUp"){e.preventDefault(),ke("up");return}if(a===40||t==="ArrowDown"){e.preventDefault(),ke("down");return}if(a===13||a===32||t==="Enter"||t===" "){if(ue()&&(!s||s===document.body||s.id==="tvPlayer"||s.id==="videoContainer")){e.preventDefault(),ie();let l=r("fsHudBar")?r("fsHudBar").querySelector(".hud-ctrl-btn"):null;l&&l.focus();return}if($t&&$t.length>0){e.preventDefault(),vs();return}if(s&&s.tagName==="INPUT"&&(a===32||t===" "))return;if(s&&s.id==="inputSearch"){e.preventDefault(),e.stopPropagation(),Dt(!1);let l=r("listItems")?r("listItems").querySelector(".list-item-btn:not([disabled])"):null;l&&(l.focus(),T="channels");return}if(s&&s.classList&&s.classList.contains("scrollable-container")){e.preventDefault(),e.stopPropagation(),ta(s);return}if(e.preventDefault(),s&&s!==document.body&&typeof s.click=="function")s.click();else{Le();let l=document.activeElement;l&&typeof l.click=="function"&&l.click()}return}if((a>=48&&a<=57||a>=96&&a<=105)&&!c){e.preventDefault();let l=a>=96&&a<=105?a-96:a-48;ql(l);return}if(a===403||t==="Red"||t==="ColorF0Red"){if(e.preventDefault(),r("hudLoadingOverlay")&&!r("hudLoadingOverlay").classList.contains("hidden")){if(ye)try{ye.abort()}catch{}r("hudLoadingOverlay").classList.add("hidden"),ze()}typeof ct=="function"?ct():Ul();return}if(a===404||t==="Green"||t==="ColorF1Green"){if(e.preventDefault(),v){Io(v.url);let l=G.has(v.url);E(l?"⭐ Adicionado aos Favoritos":"Removido dos Favoritos")}else E("Selecione um canal para favoritar");return}if(a===405||t==="Yellow"||t==="ColorF2Yellow"){e.preventDefault(),typeof Nr=="function"?Nr():hs();return}if(a===406||t==="Blue"||t==="ColorF3Blue"||t==="b"||t==="B"){e.preventDefault(),typeof ao=="function"?ao():La();return}if(a===458||t==="Guide"||t==="EPG"){e.preventDefault(),typeof ao=="function"&&ao();return}if(a===457||t==="Info"){e.preventDefault(),typeof Mr=="function"?Mr():v&&E(v.name+" - "+(v.group||""));return}},!0);function zr(){function e(n){if(n){var o=r("inputSearch");if(o){o.value=n;try{o.dispatchEvent(new Event("input",{bubbles:!0}))}catch{be={ALL:[]},D=[],Pt=[],typeof te=="function"&&te(),typeof Q=="function"&&Q()}}typeof E=="function"&&E("Busca: "+n)}}try{if(typeof webOS!="undefined"&&webOS.service&&webOS.service.request){webOS.service.request("luna://com.webos.service.voice",{method:"start",parameters:{},onSuccess:function(n){e(n&&(n.transcript||n.text||n.result)||"")},onFailure:function(){t()}});return}}catch{}t();function t(){var n=window.SpeechRecognition||window.webkitSpeechRecognition;if(!n){typeof E=="function"&&E("Voz indisponível");var o=r("inputSearch");o&&o.focus();return}try{var a=new n;a.lang="pt-BR",a.onresult=function(s){e(s.results&&s.results[0]&&s.results[0][0]?s.results[0][0].transcript:"")},a.start(),typeof E=="function"&&E("Ouvindo...")}catch{}}}r("btnRefreshList").onclick=()=>{Kt({preserveCatalog:!0}),Ee(!0)};function gc(){Ke?(Ke=!1,r("txtLockIcon")&&(r("txtLockIcon").textContent="🔒"),r("txtLockText")&&(r("txtLockText").textContent="Adulto (Bloqueado)"),te(),ae(),Oe("ALL"),alert("Canais adultos foram bloqueados com sucesso.")):(Ue="",r("pinScreen").textContent="----",r("modalPin").classList.remove("hidden"),T="modalPin")}document.querySelectorAll(".pin-key").forEach(e=>{e.onclick=()=>{let t=e.getAttribute("data-k");if(t==="C")Ue="",r("pinScreen").textContent="----";else if(t==="OK")Ur();else if(Ue.length<4){Ue+=t;let n="";for(let o=0;o<4;o++)n+=o<Ue.length?"●":"-";r("pinScreen").textContent=n,Ue.length===4&&Ur()}}});function Ur(){if(Ue===Qe||Ue==="0000"||Ue==="8208"){Ke=!0,r("txtLockIcon")&&(r("txtLockIcon").textContent="🔓"),r("txtLockText")&&(r("txtLockText").textContent="Adulto (Liberado)"),r("modalPin").classList.add("hidden"),T="channels",te(),ae();let e=ot.find(t=>re(t));Oe(e||"ALL"),alert("Canais adultos liberados com sucesso!")}else alert("Senha incorreta! Digite novamente."),Ue="",r("pinScreen").textContent="----"}r("btnClosePin").onclick=()=>{r("modalPin").classList.add("hidden"),T="channels"};r("btnExitCancel").onclick=()=>{r("modalExitConfirm").classList.add("hidden"),T="header";try{Le()}catch{}};r("btnExitConfirm").onclick=js;r("btnCloseOmnisearch")&&(r("btnCloseOmnisearch").onclick=bs);r("btnClearOmnisearch")&&(r("btnClearOmnisearch").onclick=()=>{r("inputOmnisearch")&&(r("inputOmnisearch").value="",r("inputOmnisearch").focus(),Ea(""))});r("inputOmnisearch")&&(r("inputOmnisearch").addEventListener("input",e=>{clearTimeout(Rr),Rr=setTimeout(()=>{Ea(e.target.value.trim())},250)}),r("inputOmnisearch").addEventListener("keydown",e=>{if(e.key==="Enter"){let t=r("omnisearchResultsGrid")?r("omnisearchResultsGrid").querySelector(".omnisearch-item-btn"):null;t&&t.focus()}}));["tabOmniAll","tabOmniLive","tabOmniMovies","tabOmniSeries"].forEach(e=>{let t=r(e);t&&(t.onclick=()=>{document.querySelectorAll(".omnisearch-tab-btn").forEach(n=>n.classList.remove("active")),t.classList.add("active"),ys=e.replace("tabOmni","").toUpperCase(),So()})});r("btnCloseEpgGuide")&&(r("btnCloseEpgGuide").onclick=()=>{let e=r("modalEpgGuide");e&&e.classList.add("hidden"),T="channels",Le()});r("btnCloseDiagnostics")&&(r("btnCloseDiagnostics").onclick=()=>{let e=r("modalStreamDiagnostics");e&&e.classList.add("hidden"),T="channels",Le()});typeof document!="undefined"&&document.addEventListener("webOSLowMemory",function(){console.warn("[MK21] webOSLowMemory — liberando buffers");try{if(Kl(),typeof k!="undefined"&&k)try{k.destroy(),k=null}catch{}let e=document.getElementById("tvPlayer");if(e)try{e.pause(),e.removeAttribute("src"),e.load()}catch{}nt=40,typeof Q=="function"&&Q()}catch{}},!1);window.addEventListener("load",()=>{try{let e=localStorage.getItem("mk21_font_size");e&&Nt(e)}catch{}try{let e=localStorage.getItem("mk21_last_server");e!==null&&L[e]&&(N=parseInt(e,10))}catch{}try{let e=r("cfgToggleTmdb");e&&(e.checked=localStorage.getItem("mk21_use_tmdb")!=="false",e.onchange=()=>{localStorage.setItem("mk21_use_tmdb",e.checked)})}catch{}try{r("btnHeaderLogout")&&(r("btnHeaderLogout").onclick=()=>bn())}catch{}try{tc(),x("[App] MK21 Play v"+Ct+" inicializado","success")}catch{}});var ne={isLoggedIn:!1,activeFocusIndex:0,focusableElements:[]},pt={UP:[38,"ArrowUp"],DOWN:[40,"ArrowDown"],LEFT:[37,"ArrowLeft"],RIGHT:[39,"ArrowRight"],ENTER:[13,"Enter"],BACK:[461,10009,27,"Escape","GoBack"]},In=[".scrollable-container",".pane-scroll","#listItems","#listCategories","#serverList","#serverItemsGrid","#devLogsContainer",".settings-left-menu",".settings-detail-panel",".servers-manager-list",".omnisearch-body","#epgScheduleList"].join(", "),se=null;function ta(e,t){if(!e)return!1;let n=typeof t=="boolean"?t:se!==e;return se&&se!==e&&(se.classList.remove("scroll-mode-active"),se._mk21ScrollRail&&se._mk21ScrollRail.classList.remove("is-active")),n?(se=e,e.classList.add("scroll-mode-active"),e._mk21ScrollRail&&e._mk21ScrollRail.classList.add("is-active"),zn(e),typeof E=="function"&&E("📜 Modo Rolagem ATIVO (▲/▼ rolam · OK sai)"),!0):(e.classList.remove("scroll-mode-active"),e._mk21ScrollRail&&e._mk21ScrollRail.classList.remove("is-active"),se===e&&(se=null),!1)}function hc(e){if(!e||typeof document=="undefined")return null;if(e._mk21ScrollRail&&document.body.contains(e._mk21ScrollRail))return e._mk21ScrollRail;let t=e.closest&&e.closest(".col-pane")||e;if(!t)return null;let n=document.createElement("div");n.className="mk21-vscroll-rail is-hidden",n.setAttribute("aria-hidden","true");let o=document.createElement("div");return o.className="mk21-vscroll-thumb",n.appendChild(o),t.appendChild(n),e._mk21ScrollRail=n,e._mk21ScrollThumb=o,e._mk21ScrollHost=t,e.dataset.mk21VisualScrollBound||(e.dataset.mk21VisualScrollBound="1",e.addEventListener("scroll",function(){X(e)},{passive:!0})),n}function X(e){if(!(!e||typeof window=="undefined"))try{let t=hc(e),n=e._mk21ScrollThumb,o=e._mk21ScrollHost;if(!t||!n||!o)return;let a=e.clientHeight||0,s=e.scrollHeight||0;if(a<=0||s<=a+4){t.classList.add("is-hidden");return}if(o!==e){let p=Math.max(8,e.offsetTop+4);t.style.top=p+"px",t.style.bottom="8px"}else{let p=e.scrollTop||0;t.style.top=p+6+"px",t.style.height=Math.max(24,a-12)+"px",t.style.bottom="auto"}let i=t.clientHeight||Math.max(40,a-12),c=Math.max(44,Math.round(a/s*i)),u=Math.max(1,s-a),d=Math.max(0,i-c),l=Math.min(1,Math.max(0,(e.scrollTop||0)/u)),f=Math.round(l*d);n.style.height=c+"px",n.style.transform="translate3d(0, "+f+"px, 0)",t.classList.remove("is-hidden")}catch{}}function zn(e){if(e)try{let t=e.scrollTop;e.classList.remove("force-scroll-visible"),e.style.setProperty("overflow-y","hidden","important"),e.offsetHeight,e.style.setProperty("overflow-y","scroll","important"),e.classList.add("force-scroll-visible"),e.scrollTop!==t&&(e.scrollTop=t),X(e)}catch{}}function Qs(){if(typeof navigator=="undefined"||typeof document=="undefined")return;let e=navigator.userAgent||"",t=/webos|web0s|netcast/i.test(e)||typeof window.webOS!="undefined";if(window.__mk21VisualScrollInit||(window.__mk21VisualScrollInit=!0,["#home-container",".app-body","#sectionUnified","#sectionSettings",".col-categories",".col-channels",".col-player"].forEach(function(a){let s=document.querySelector(a);s&&s.addEventListener("scroll",function(){s.scrollTop!==0&&(s.scrollTop=0),s.scrollLeft!==0&&(s.scrollLeft=0)},{passive:!0})}),window.addEventListener("scroll",function(){(window.scrollX!==0||window.scrollY!==0)&&window.scrollTo(0,0)},{passive:!0}),document.addEventListener("focusin",function(a){let s=a.target;if(!s||typeof s.closest!="function")return;wo();let i=s.closest(In);document.querySelectorAll(".mk21-vscroll-rail.is-active").forEach(function(c){c.classList.remove("is-active")}),i&&(_a(s),X(i),i._mk21ScrollRail&&i._mk21ScrollRail.classList.add("is-active"))},!0),window.addEventListener("resize",function(){wo(),document.querySelectorAll(In).forEach(function(a){a.offsetParent!==null&&X(a)})},{passive:!0}),document.querySelectorAll(In).forEach(function(a){a.offsetParent!==null&&X(a)})),!t||window.__mk21WebOSScrollFixActive)return;window.__mk21WebOSScrollFixActive=!0;let n=null;document.addEventListener("focusin",function(a){let s=a.target;if(!s||typeof s.closest!="function")return;let i=s.closest(In);i&&(n&&n!==i&&n.classList.remove("force-scroll-visible"),n=i,zn(i))},!0),document.querySelectorAll(In).forEach(function(a){a.offsetParent!==null&&zn(a)})}function Gr(){Qs(),Xs(),bc(),Sc(),r("btnHeaderLogout")&&(r("btnHeaderLogout").onclick=()=>bn());let e=Wt();if(e&&e.user&&e.pass){if(typeof e.serverIndex=="number"&&L[e.serverIndex]){N=e.serverIndex;try{localStorage.setItem("mk21_last_server",N)}catch{}}Oa(!0)}else Zs()}function vc(){let e=document.getElementById("server-select");e&&(e.innerHTML="",L.forEach((t,n)=>{let o=document.createElement("option");o.value=n,o.textContent=`Fonte ${n+1}: ${Z(t.name)}`,n===N&&(o.selected=!0),e.appendChild(o)}),e.onchange=()=>{N=parseInt(e.value,10)||0;try{localStorage.setItem("mk21_last_server",N)}catch{}})}function Zs(e,t){ne.isLoggedIn=!1;let n=r("mk21-toast-banner");n&&n.classList.remove("visible");let o=document.getElementById("home-container"),a=document.getElementById("login-container"),s=document.getElementById("login-feedback");o&&(o.style.display="none"),a&&(a.style.display="flex"),s&&(s.textContent="",s.style.display="none",s.classList.remove("is-error","is-info","is-success")),e&&Ze(e,!!t),vc();let i=document.getElementById("user-input"),c=document.getElementById("pass-input");i&&(i.value=""),c&&(c.value=""),yc(),setTimeout(()=>{qe(1)},120);let u=document.getElementById("btn-login");u&&(u.onclick=Ec);let d=document.getElementById("btn-offline");d&&(d.onclick=xc)}function bn(){try{typeof $n=="function"&&$n(!0)}catch{}Bt&&(clearTimeout(Bt),Bt=null),Kt(),il(),v=null,ne.isLoggedIn=!1;let e=r("hudLoadingOverlay");e&&e.classList.add("hidden"),document.querySelectorAll(".app-modal, .modal-overlay").forEach(t=>t.classList.add("hidden")),Zs("Sessão encerrada. Informe suas credenciais para entrar.",!1)}function yc(){ne.focusableElements=[document.getElementById("server-select"),document.getElementById("user-input"),document.getElementById("pass-input"),document.getElementById("btn-login"),document.getElementById("btn-offline")].filter(Boolean)}function qe(e){if(!ne.focusableElements.length)return;e<0&&(e=ne.focusableElements.length-1),e>=ne.focusableElements.length&&(e=0),ne.activeFocusIndex=e;let t=ne.focusableElements[e];ne.focusableElements.forEach(n=>n.classList.remove("focused")),t.classList.add("focused"),t.focus()}function bc(){window.addEventListener("keydown",e=>{if(pt.BACK.includes(e.keyCode)||pt.BACK.includes(e.key)){e.preventDefault(),document.activeElement&&(document.activeElement.tagName==="INPUT"||document.activeElement.tagName==="SELECT")&&(document.activeElement.blur(),qe(ne.activeFocusIndex));return}if(!ne.isLoggedIn){if(pt.DOWN.includes(e.keyCode)||pt.DOWN.includes(e.key))e.preventDefault(),qe(ne.activeFocusIndex+1);else if(pt.UP.includes(e.keyCode)||pt.UP.includes(e.key))e.preventDefault(),qe(ne.activeFocusIndex-1);else if(pt.ENTER.includes(e.keyCode)||pt.ENTER.includes(e.key)){let t=ne.focusableElements[ne.activeFocusIndex];t&&t.tagName==="BUTTON"&&(e.preventDefault(),t.click())}}})}function Sc(){document.querySelectorAll(".input-field").forEach(t=>{t.addEventListener("focus",()=>document.body.classList.add("osk-open")),t.addEventListener("blur",()=>document.body.classList.remove("osk-open"))})}async function Ec(){let e=document.getElementById("server-select");if(e){N=parseInt(e.value,10)||0;try{localStorage.setItem("mk21_last_server",N)}catch{}}let t=document.getElementById("user-input"),n=document.getElementById("pass-input"),o=t?t.value.trim():"",a=n?n.value.trim():"";if(!o||!a){Ze("Informe o usuário e a senha / token.",!0),qe(o?2:1);return}Kt({preserveCatalog:!1}),et=typeof AbortController!="undefined"?new AbortController:null;let s=et,i=s?setTimeout(()=>s.abort(),7e3):null;Ze("Autenticando e validando credenciais...",!1);let c=L[N]||L[0],u="mk21_session_ok";if(c&&c.url)try{let p=`${c.url.replace(/\/+$/,"")}/player_api.php?username=${encodeURIComponent(o)}&password=${encodeURIComponent(a)}`,m=await fetch(p,{method:"GET",headers:{Accept:"application/json"},signal:s?s.signal:void 0});if(i&&clearTimeout(i),et!==s)return;let y=await m.text();if(m.status===401||m.status===403){Ze("Credenciais inválidas neste servidor. Verifique usuário e senha.",!0),qe(1);return}if(m.ok){let g=Ae.parseSafeJsonResponse(y,m.status);if(g&&g.user_info){if(Number(g.user_info.auth)===0){Ze("Usuário ou senha inválidos no servidor.",!0),qe(1);return}let h=String(g.user_info.status||"").toLowerCase();if(h==="expired"||h==="disabled"||h==="banned"){Ze("Acesso "+g.user_info.status+" neste servidor. Verifique seu plano.",!0),qe(1);return}Number(g.user_info.auth)===1&&(u=g.user_info.token||"mk21_auth_ok")}}}catch(f){if(i&&clearTimeout(i),f&&f.name==="AbortError"&&et!==s)return;if(f&&f.isAuthError){Ze(f.message||"Usuário ou senha inválidos no servidor.",!0),qe(1);return}console.warn("[Login] Validação online indisponível (M3U direto / CORS), seguindo para validação de lista:",f)}finally{i&&clearTimeout(i),et===s&&(et=null)}else i&&clearTimeout(i);let d=Wt();yo=!d||d.user!==o||d.pass!==a||d.serverIndex!==N||!!(r("hudLoadingError")&&!r("hudLoadingError").classList.contains("hidden")),sl(o,a,N);try{localStorage.setItem("mk21_auth_token",u)}catch{}Ze("Credenciais salvas! Carregando catálogo...",!1,"success"),Oa(!0)}async function xc(){let e=L[N]||L[0],t=e?await ei(e.id):null;if(!t||!t.LIVE||t.LIVE.length===0){Ze("Nenhum catálogo em cache local. Informe usuário e senha para conectar.",!0),qe(1);return}yo=!1,Oa(!0)}async function ei(e){try{let t=await hn(e);if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{let t=dn(e);if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{let t=dn("universal");if(t&&t.LIVE&&t.LIVE.length>0)return t}catch{}try{for(let t of L)if(t.id&&t.id!==e){let n=await hn(t.id);if(n&&n.LIVE&&n.LIVE.length>0)return n;let o=dn(t.id);if(o&&o.LIVE&&o.LIVE.length>0)return o}}catch{}return null}function Cc(){if(!ne.isLoggedIn||ht||On||jt()==="manual")return;let t=()=>{!ne.isLoggedIn||ht||On||Po(!1).catch(()=>{})};typeof window!="undefined"&&typeof window.requestIdleCallback=="function"?window.requestIdleCallback(t,{timeout:5e3}):setTimeout(t,1200)}function No(){if(Bt&&(clearTimeout(Bt),Bt=null),!ne.isLoggedIn)return;let e=jt();if(e==="manual"||e==="startup")return;let t=Ae.getBgSyncIntervalMs(e);if(!t||t<=0)return;let n=L[N],o=0;try{n&&(o=parseInt(localStorage.getItem("mk21_last_sync_"+n.id)||"0",10)||0)}catch{}let a=o>0?Math.max(0,Date.now()-o):0,s=Math.max(6e4,Math.min(t,t-a));Bt=setTimeout(()=>{Cc()},s)}async function Lc(){L.length===0&&(L=[...rt]),N>=L.length&&(N=0);let e=L[N];r("txtActiveServer")&&(r("txtActiveServer").textContent=Z(e.name)),r("hudLoadingOverlay")&&r("hudLoadingOverlay").classList.add("hidden"),ze();let t=yo;yo=!1;let n=t?null:await ei(e.id);if(n&&n.LIVE&&n.LIVE.length>0&&!t){C={LIVE:n.LIVE||[],MOVIE:n.MOVIE||[],SERIES:n.SERIES||[]},it(e.id,C,typeof epgCache!="undefined"?epgCache:null,!!window.serverXmltvUrl),te(),ae(),Oe("ALL"),Q(),mt(100,!0),typeof Ye=="function"&&Ye(),setTimeout(()=>{ls()},100),setTimeout(()=>{T="channels";let s=r("listItems")?r("listItems").querySelector(".list-item-btn"):null;if(s)try{s.focus()}catch{}},200);let o=jt(),a=0;try{a=parseInt(localStorage.getItem("mk21_last_sync_"+e.id)||"0",10)||0}catch{}(o==="startup"||Ae.shouldSyncServer(a,o))&&setTimeout(()=>{qn()?Po(!1).catch(()=>{}):Ee(!0,{silent:!0,backgroundLowPriority:!0}).catch(()=>{})},1500),No();return}Ee(t)}function Oa(e=!0){ne.isLoggedIn=!0;let t=document.getElementById("login-container");t&&(t.style.display="none");let n=document.getElementById("home-container");if(n&&(n.style.display="flex",n.classList.remove("hidden")),r("sectionSettings")&&r("sectionSettings").classList.add("hidden"),r("sectionUnified")&&r("sectionUnified").classList.remove("hidden"),typeof window.initHomeGrid=="function")try{window.initHomeGrid()}catch{}if((window.requestAnimationFrame||function(a){return setTimeout(a,0)})(function(){wo(),["#listCategories","#listItems"].forEach(function(a){let s=document.querySelector(a);s&&(window.__mk21WebOSScrollFixActive?zn(s):X(s))}),T="header",Sa=0,r("tabLive")?Se(r("tabLive")):typeof Le=="function"&&Le()}),e)try{Lc()}catch(a){console.error("Erro ao inicializar cache:",a);try{Ee(!1)}catch{}}No()}function Ze(e,t,n){let o=document.getElementById("login-feedback");if(o){o.textContent=e,o.style.display="block",o.classList.remove("is-error","is-info","is-success");let a=n||(t?"error":"info");o.classList.add("is-"+a),o.style.color=""}}document.readyState==="loading"?document.addEventListener("DOMContentLoaded",Gr):Gr();var Ut=0,Fn=new Set,na=!1;function ti(){return localStorage.getItem("mk21_playback_quality")||"auto"}function wc(){let e=typeof k!="undefined"?k:null,t=new Set;e&&Array.isArray(e.levels)&&e.levels.forEach(o=>{o&&o.height&&t.add(o.height)});let n=Array.from(t).sort((o,a)=>a-o);return n.length||[1080,720,480].forEach(o=>n.push(o)),n}function Un(e){let t=e||(typeof k!="undefined"?k:null);if(!t||!Array.isArray(t.levels)||!t.levels.length)return;let n=ti();try{if(n==="auto"){t.autoLevelCapping=-1,t.currentLevel=-1;return}let o=parseInt(n,10)||1080,a=-1;t.levels.forEach((s,i)=>{!s||!s.height||s.height<=o&&(a<0||s.height>t.levels[a].height)&&(a=i)}),a<0&&(a=0),t.autoLevelCapping=a,t.currentLevel=a,x("[Qualidade] Fixada em "+t.levels[a].height+"p","info")}catch(o){x("[Qualidade] Não foi possível aplicar: "+(o&&o.message),"warn")}}var kc=[["qualidade","🎞️ Qualidade"],["buffer","⚡ Buffer"],["fonte","📡 Fonte"]],Sn="qualidade";function Tc(){let e=r("modalPlayerOptions");return e||(e=document.createElement("div"),e.id="modalPlayerOptions",e.className="app-modal hidden",e.innerHTML='<div class="modal-content" style="width: 920px; max-width: 94vw; max-height: 86vh; display: flex; flex-direction: column;"><div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; gap: 16px;"><span style="font-size: 22px; font-weight: 800; color: #ffd54f;">⚙️ Opções do player</span><button id="btnClosePlayerOptions" class="ctrl-btn" tabindex="0" style="padding: 6px 14px;">✕ Fechar</button></div><div class="player-options-tabs" id="playerOptionsTabs"></div><div id="playerOptionsBody"></div></div>',document.body.appendChild(e),r("btnClosePlayerOptions")&&(r("btnClosePlayerOptions").onclick=Ac),e)}function ni(){let e=r("playerOptionsTabs");e&&(e.replaceChildren(),kc.forEach(t=>{let n=document.createElement("button");n.type="button",n.className="player-options-tab"+(Sn===t[0]?" active":""),n.tabIndex=0,n.textContent=t[1],n.onclick=()=>{Sn=t[0],ni(),oi();let o=document.querySelector(".player-options-tab.active");o&&o.focus()},e.appendChild(n)}))}function oi(){let e=r("playerOptionsBody");if(e){Mo=e;try{Sn==="buffer"?Ia():Sn==="fonte"?vt():Ba()}catch(t){x("[UI] Falha ao montar opções do player: "+(t&&t.message),"error")}}}function ai(e){let t=Tc();Sn=e||"qualidade",xe(t,!0),t.classList.remove("hidden"),T="modalPlayerOptions",ni(),oi(),x("[UI] Opções do player: "+Sn,"info"),setTimeout(()=>{let n=t.querySelector(".player-options-tab.active")||t.querySelector("button");n&&n.focus&&n.focus()},50)}function Ac(){let e=r("modalPlayerOptions");e&&(e.classList.add("hidden"),xe(e,!1)),Mo=null;let t=typeof ue=="function"&&ue(),n=r(t?"fsBtnQuality":"btnQuality");n&&n.focus&&n.focus(),t&&ie()}function ko(){ai("qualidade")}function Ba(){let e=ka();if(!e)return;let t=ti(),n=wc(),a=[["auto","Automática (recomendada)","O player escolhe a melhor faixa conforme a internet."]].concat(n.map(s=>[String(s),"Até "+s+"p","Limita o canal em "+s+"p."])).map(s=>{let i=t===s[0];return'<button class="track-item-btn quality-option'+(i?" active":"")+'" data-quality="'+s[0]+'" tabindex="0" style="text-align:left; padding: 16px 20px; font-size: 20px; display: flex; flex-direction: column; gap: 4px;"><span style="font-weight: 800;">'+(i?"✓ ":"")+s[1]+'</span><span style="font-size: 14px; color: #94a3b8; font-weight: 500;">'+s[2]+"</span></button>"}).join("");e.innerHTML='<h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">🎞️ Qualidade de vídeo</h3><p style="margin:0 0 18px 0; font-size:16px; color:#94a3b8;">Vale para todos os canais e fica salva no aparelho.</p><div style="display:flex; flex-direction:column; gap:10px;">'+a+"</div>",e.querySelectorAll(".quality-option").forEach(s=>{s.onclick=()=>{localStorage.setItem("mk21_playback_quality",s.getAttribute("data-quality")),Un(),Ba(),x("[Qualidade] "+s.textContent.trim(),"success"),E("Qualidade: "+s.textContent.trim());let i=e.querySelector(".quality-option.active");i&&i.focus()}})}function Ic(e,t,n){if(e&&e.isAuthError||!Array.isArray(L)||L.length<2)return!1;if(Ut>=L.length-1)return Ut=0,x("[AutoServidor] Todos os servidores falharam","error"),!1;let o=(N+1)%L.length,a=L[o];Ut+=1;let s=t&&t.name?Z(t.name):"Servidor atual";x("[AutoServidor] "+s+" falhou ("+(e&&e.message)+"); tentando "+Z(a.name),"warn"),E("🔄 "+s+" falhou. Trocando para "+Z(a.name)+"..."),N=o,r("txtActiveServer")&&(r("txtActiveServer").textContent=Z(a.name));try{localStorage.setItem("mk21_last_server",N)}catch{}if(typeof qt=="function")try{qt()}catch{}return We&&clearTimeout(We),We=setTimeout(()=>{We=null,!(typeof n=="number"&&n!==ve)&&typeof Ee=="function"&&Ee(!0,{fromAutoAdvance:!0})},1500),!0}var qr="";function Wr(e){let t=e&&e.name?String(e.name):"";if(t&&t===qr){e&&e.url&&Fn.add(e.url);return}qr=t,Fn=new Set,e&&e.url&&Fn.add(e.url)}function Mc(e,t,n){if(!n)return!1;try{return n.matchesAcrossServers(e,t)}catch{return!1}}async function jr(){if(!v||na)return!1;na=!0;let e=window.MK21AlternativeSources||null;try{let t=[];e&&e.sourceUrls(v).forEach(o=>t.push({url:o,origem:"mesma lista"}));for(let o=0;o<L.length;o++){let a=L[o];if(!a||L[N]&&a.id===L[N].id)continue;let s=null;try{s=dn(a.id)||await hn(a.id)}catch{s=null}let c=(s&&Array.isArray(s.LIVE)?s.LIVE:[]).find(u=>Mc(v,u,e));c&&c.url&&t.push({url:c.url,origem:Z(a.name)})}let n=t.find(o=>o.url&&!Fn.has(o.url));return n?(Fn.add(n.url),la="",x("[AutoFonte] Alternando para "+n.origem,"warn"),E("🔄 Fonte alternativa: "+n.origem),tt=0,Ge=!1,fe(v,n.url),!0):(x("[AutoFonte] Nenhuma fonte alternativa disponível para "+v.name,"warn"),!1)}finally{na=!1}}var j={url:"",promise:null,byChannel:new Map,byName:new Map,programCount:0,channelCount:0,loadedAt:0,lastError:"",maxProgrammes:25e4};function ri(e){return String(e||"").toLowerCase().replace(/\b(fhd|uhd|hd|sd|4k|2k|1080p|720p|480p|hevc|h265|br|brasil)\b/g," ").replace(/[^a-z0-9]+/g," ").trim()}function si(){if(window.serverXmltvUrl)return window.serverXmltvUrl;let e=L[N]||rt[0],t=Wt()||{};return e&&e.url&&t.user&&t.pass?`${e.url.replace(/\/+$/,"")}/xmltv.php?username=${encodeURIComponent(t.user)}&password=${encodeURIComponent(t.pass)}`:""}function Pc(e){let t=new Map,n=new Map;if(!e||typeof e!="string")return{byChannel:t,byName:n};if(e.indexOf("<programme")<0)return{byChannel:t,byName:n};let o=/<channel\s+id="([^"]+)"[^>]*>([\s\S]*?)<\/channel>/gi,a=/<display-name[^>]*>([\s\S]*?)<\/display-name>/i,s;for(;(s=o.exec(e))!==null;){let p=String(s[1]||"").trim().toLowerCase();if(!p)continue;let m=s[2].match(a);if(!m)continue;let y=String(m[1]).replace(/<!\[CDATA\[|\]\]>/g,"").trim(),g=ri(y);g&&!n.has(g)&&n.set(g,p)}let i=/<programme\s+[^>]*start="([^"]+)"[^>]*stop="([^"]+)"[^>]*channel="([^"]+)"[^>]*>([\s\S]*?)<\/programme>/gi,c=/<title[^>]*>([\s\S]*?)<\/title>/i,u=/<desc[^>]*>([\s\S]*?)<\/desc>/i,d=/<category[^>]*>([\s\S]*?)<\/category>/i,l,f=0;for(;(l=i.exec(e))!==null&&!(f>=j.maxProgrammes);){let p=String(l[3]||"").trim().toLowerCase(),m=xt.toTimestampMs(l[1]),y=xt.toTimestampMs(l[2]);if(!p||!Number.isFinite(m)||!Number.isFinite(y)||y<=m)continue;let g=l[4]||"",h=g.match(c),b=g.match(u),R=g.match(d),w=O=>O?String(O[1]).replace(/<!\[CDATA\[|\]\]>/g,"").trim():"",M={title:w(h)||"Programação",desc:w(b),category:w(R),start:m,stop:y},A=t.get(p);A||(A=[],t.set(p,A)),A.push(M),f++}return t.forEach(p=>p.sort((m,y)=>m.start-y.start)),{byChannel:t,byName:n}}async function Da(e){let t=si();return t?!e&&j.promise&&j.url===t?j.promise:!e&&j.loadedAt&&j.url===t?j.byChannel:(j.url=t,j.promise=(async()=>{try{let n=await fetch(t);if(!n.ok)return j.lastError="HTTP "+n.status,null;let o=await n.text(),a=Pc(o),s=a.byChannel;if(!s||s.size===0)return j.lastError="XMLTV sem programas.",null;j.byChannel=s,j.byName=a.byName,j.channelCount=s.size,j.programCount=Array.from(s.values()).reduce((i,c)=>i+c.length,0),j.loadedAt=Date.now(),j.lastError="",x("[Guia] XMLTV carregado: "+j.programCount+" programas em "+j.channelCount+" canais.","info");try{window.dispatchEvent(new CustomEvent("mk21:epg-updated"))}catch{}return s}catch(n){return j.lastError=n&&n.message?n.message:"falha de rede",console.debug("[Guia] Falha ao carregar XMLTV:",n),null}})(),j.promise):(j.lastError="Servidor não expõe XMLTV.",null)}function To(e){if(!e)return null;let t=String(e.tvgId||e["tvg-id"]||"").trim().toLowerCase();if(t){let a=j.byChannel.get(t);if(a&&a.length)return a}let n=String(e.name||e.tvgName||"").trim().toLowerCase();if(n){let a=j.byChannel.get(n);if(a&&a.length)return a}let o=ri(e.name||e.tvgName);if(o){let a=j.byName.get(o);if(a){let s=j.byChannel.get(a);if(s&&s.length)return s}}return null}async function Rc(e,t){let n=t||{},o=Date.now(),a=[],s=To(e);if(s&&a.push(s),!s&&n.allowFetch!==!1){let u=si();u&&j.url!==u&&Da(!1).then(()=>{P.open&&Ao()}).catch(()=>{})}if(n.withShortEpg){let u=L[N]||rt[0],d=Wt()||{},l=e&&(e.streamId||e.stream_id);if(!l&&e&&e.url){let f=e.url.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);f&&f[1]&&(l=f[1])}if(u&&d.user&&d.pass&&l){let f=await gs(e,l,u,d.user,d.pass,12);f&&f.length&&a.push(f.map(p=>({title:p.title,desc:p.description,start:p.start_timestamp!==void 0?p.start_timestamp:p.start,stop:p.stop_timestamp!==void 0?p.stop_timestamp:p.end!==void 0?p.end:p.stop})))}}let i=xt.mergeProgramSources.apply(null,a);if(i.length)return i;let c=ms(e&&e.name||"");if(c&&c.title){let u=new Date(o),d=u.getTime()-u.getMinutes()%60*6e4;return xt.normalizePrograms([{title:c.title,desc:"",start:d,stop:d+36e5}])}return[]}var P={open:!1,channels:[],channelIndex:0,programs:[],programIndex:0,pane:"channels",loading:!1};function ii(){return(C&&Array.isArray(C.LIVE)&&C.LIVE.length?C.LIVE:Array.isArray(D)?D.filter(t=>!t.contentType||t.contentType==="LIVE"):[]).filter(t=>t&&(t.name||t.url))}function li(){let e=r("sectionGuide");if(!e)return;let t=r("sectionUnified");t&&t.classList.add("hidden");let n=r("sectionSettings");n&&n.classList.add("hidden"),P.channels=ii(),P.open=!0,P.programIndex=0;let o=v?bt.channelKey(v):"",a=0;o&&(a=P.channels.findIndex(s=>bt.channelKey(s)===o),a<0&&(a=0)),P.channelIndex=a,e.classList.remove("hidden"),di(),Fa(a,!0),Da(!1).then(()=>{P.open&&Ao()}).catch(()=>{});try{Se(r("listGuideChannels"))}catch{}T="guide",E("📅 Guia de Programação")}function ci(){let e=r("sectionGuide");e&&e.classList.add("hidden"),P.open=!1,P.programs=[]}function di(){let e=r("listGuideChannels");if(!e)return;let t=r("badgeGuideChannels");if(t&&(t.textContent=String(P.channels.length)),!P.channels.length){e.innerHTML='<div class="guide-empty">Nenhum canal carregado.<br>Conecte-se a um servidor para ver a grade.</div>';return}let n=Date.now(),o="";for(let a=0;a<P.channels.length;a++){let s=P.channels[a],i=To(s),c="";if(i){let p=xt.pickNowNext(i,n);c=p.current?p.current.title:""}let u=a+1,d=q(s.name||"Canal"),l=String(s.logo||"").trim(),f=/^https?:\/\//i.test(l)?'<img class="guide-channel-logo" src="'+q(l)+`" alt="" onerror="this.style.display='none'">`:'<div class="guide-channel-logo-placeholder">'+q((s.name||"?").trim().charAt(0).toUpperCase())+"</div>";o+='<li><button class="guide-channel-btn'+(a===P.channelIndex?" active":"")+'" type="button" tabindex="0" data-idx="'+a+'"><span class="guide-channel-number">'+u+"</span>"+f+'<span class="guide-channel-meta"><span class="guide-channel-name">'+d+'</span><span class="guide-channel-now'+(c?" is-live":"")+'">'+(c?"● "+q(c):"— sem programação")+"</span></span></button></li>"}e.innerHTML=o}function Ao(){let e=r("listGuidePrograms");if(!e)return;let t=P.channels[P.channelIndex],n=r("txtGuideProgramsTitle"),o=r("badgeGuidePrograms");if(!t){n&&(n.textContent="🔵 Programação"),o&&(o.textContent="0"),e.innerHTML='<div class="guide-empty">Selecione um canal para ver a grade.</div>';return}if(n&&(n.textContent="🔵 "+(t.name||"Canal")),o&&(o.textContent=String(P.programs.length)),P.loading){e.innerHTML='<div class="guide-empty">Carregando programação…</div>';return}if(!P.programs.length){e.innerHTML='<div class="guide-empty">Sem programação disponível para este canal.<br>O servidor pode não expor EPG (XMLTV) nem guia por canal.</div>';return}let a=xt.buildTimeline(P.programs,Date.now()),s="";for(let i=0;i<a.length;i++){let c=a[i];s+='<div class="guide-prog-row'+(c.isLive?" is-live":"")+(c.isPast?" is-past":"")+'"><div class="guide-prog-time">'+q(xt.formatClock(c.start))+" - "+q(xt.formatClock(c.stop))+(c.isLive?'<span class="guide-live-badge">NO AR</span>':"")+'</div><div class="guide-prog-body"><div class="guide-prog-title">'+q(c.title)+"</div>"+(c.desc?'<div class="guide-prog-desc">'+q(c.desc)+"</div>":"")+(c.isLive?'<div class="guide-prog-bar"><div class="guide-prog-bar-fill" style="width:'+c.progressPct+'%"></div></div>':"")+"</div></div>"}e.innerHTML=s}async function Fa(e,t){if(!P.channels.length)return;let n=Math.max(0,Math.min(P.channels.length-1,e));P.channelIndex=n,P.programIndex=0,P.pane="channels";let o=document.querySelectorAll("#listGuideChannels .guide-channel-btn");for(let s=0;s<o.length;s++)o[s].classList.toggle("active",s===n);let a=P.channels[n];P.loading=!0,Ao();try{P.programs=await Rc(a,{withShortEpg:!0})}catch{P.programs=[]}if(P.loading=!1,Ao(),di(),!t)try{let s=document.querySelector("#listGuideChannels .guide-channel-btn.active");s&&Se(s)}catch{}}function lo(e){if(P.open)if(P.pane==="channels"){if(e==="up"||e==="down"){let t=e==="up"?-1:1,n=Math.max(0,Math.min(P.channels.length-1,P.channelIndex+t));n!==P.channelIndex?Fa(n):Nn();return}if(e==="right"){P.pane="programs",Nn();return}if(e==="left"){Va();return}}else{if(e==="up"||e==="down"){let t=e==="up"?-1:1,n=Math.max(0,P.programs.length-1);P.programIndex=Math.max(0,Math.min(n,P.programIndex+t)),Nn();return}if(e==="left"){P.pane="channels",Nn();return}if(e==="right"){ui();return}}}function Nn(){if(P.pane==="channels"){let e=document.querySelector("#listGuideChannels .guide-channel-btn.active");e&&typeof X=="function"&&X(e.closest("ul")||e.parentElement)}else{let e=r("listGuidePrograms");if(e){let t=e.querySelectorAll(".guide-prog-row")[P.programIndex];t&&t.scrollIntoView&&t.scrollIntoView({block:"nearest"})}}}function Nc(){if(P.open){if(P.pane==="channels"){P.pane="programs",Nn();return}ui()}}function ui(){let e=P.channels[P.channelIndex];if(!e)return;let t=P.previousType&&P.previousType!=="GUIDE"?P.previousType:"LIVE";ci();try{Pe(t==="GUIDE"?"LIVE":t)}catch{}try{fe(e)}catch{}E("▶ "+(e.name||"Canal"))}function Va(){let e=P.previousType&&P.previousType!=="GUIDE"?P.previousType:"LIVE";ci();try{Pe(e)}catch{}T="channels";try{let t=r("tabGuide");t&&Se(t)}catch{}}var pn=null,mn=null,rn=null;function _o(e){let t=r("zapHud");if(!t||!e)return;let n=Kr(e),o=To(e)||[],a=bt.buildZapInfo({channel:e,channelNumber:n,programs:o,nowMs:Date.now()});rn=e;let s=r("zapHudNumber");s&&(s.textContent=a.hasNumber?String(a.number):"",s.style.display=a.hasNumber?"":"none");let i=r("zapHudName");i&&(i.textContent=a.name);let c=r("zapHudLogo");if(c){let m=String(a.logo||"").trim(),y=/^https?:\/\//i.test(m)?m:"";c.src=y,c.style.display=y?"":"none"}let u=r("zapHudNow");u&&(u.textContent=a.currentTitle||"Sem programação disponível");let d=r("zapHudProgressFill");d&&(d.style.width=a.progressPct+"%");let l=r("zapHudNext");l&&(l.textContent=a.nextTitle||"—");let f=r("zapHudCountdown");f&&(f.textContent=a.countdownText),t.classList.remove("hidden"),pn&&clearTimeout(pn),mn&&clearInterval(mn);let p=Date.now();pn=setTimeout(()=>{bt.shouldAutoHide(Date.now()-p)&&fi()},bt.ZAP_AUTO_HIDE_MS+120),mn=setInterval(()=>{if(!rn)return;let m=To(rn)||[],y=bt.buildZapInfo({channel:rn,channelNumber:Kr(rn),programs:m,nowMs:Date.now()}),g=r("zapHudCountdown");g&&(g.textContent=y.countdownText);let h=r("zapHudProgressFill");h&&(h.style.width=y.progressPct+"%");let b=r("zapHudNow");b&&y.currentTitle&&(b.textContent=y.currentTitle)},1e3)}function Kr(e){let t=ii(),n=bt.channelKey(e);for(let o=0;o<t.length;o++)if(bt.channelKey(t[o])===n)return o+1;return null}function fi(){let e=r("zapHud");e&&e.classList.add("hidden"),pn&&(clearTimeout(pn),pn=null),mn&&(clearInterval(mn),mn=null),rn=null}var F={open:!1,state:null,target:null};function pi(e){let t=r("oskOverlay");if(!t)return;let n=e||r("inputSearch");F.target=n,F.state=_e.createState(n?n.value:"","lower"),F.open=!0,t.classList.remove("hidden"),Oo();try{Se(t)}catch{}}function $a(e){let t=r("oskOverlay");if(t&&t.classList.add("hidden"),e&&F.target){F.target.value=F.state?F.state.value:"";try{F.target.dispatchEvent(new Event("input",{bubbles:!0}))}catch{}}if(F.open=!1,F.state=null,F.target)try{Se(F.target)}catch{}T="channels"}function Oo(){let e=r("oskGrid"),t=r("oskPreview"),n=r("oskMode");if(!e||!F.state)return;t&&(t.textContent=F.state.value||""),n&&(n.textContent=_e.getModeLabel(F.state));let o=_e.getRows(F.state.mode),a="";for(let s=0;s<o.length;s++){a+='<div class="osk-row">';for(let i=0;i<o[s].length;i++){let c=o[s][i],u=typeof c=="object"&&c!==null,d=u?c.label:c,l=s===F.state.row&&i===F.state.col;a+='<button type="button" tabindex="-1" class="osk-key'+(u?" control":"")+(l?" focused":"")+'" data-row="'+s+'" data-col="'+i+'">'+q(d)+"</button>"}a+="</div>"}e.innerHTML=a}function co(e){!F.open||!F.state||(F.state=_e.moveCursor(F.state,e),Oo())}function _c(){if(!F.open||!F.state)return;let e=_e.getFocusedKey(F.state);e&&(typeof e=="object"&&e.action?e.action==="MODE"?F.state=_e.toggleMode(F.state):e.action==="SPACE"?F.state=_e.insertChar(F.state," "):e.action==="BACKSPACE"?F.state=_e.backspace(F.state):e.action==="CLEAR"&&(F.state=_e.clearText(F.state)):(F.state=_e.insertChar(F.state,e),F.state=_e.advanceCursor(F.state)),Oo())}function Oc(){!F.open||!F.state||(F.state=_e.backspace(F.state),Oo())}function ma(){[["btnQuality","qualidade"],["btnBufferMode","buffer"],["btnQuickServer","fonte"],["fsBtnQuality","qualidade"],["fsBtnBuffer","buffer"],["fsBtnServer","fonte"]].forEach(t=>{let n=r(t[0]);n&&(n.onclick=()=>ai(t[1]))})}document.readyState==="loading"?document.addEventListener("DOMContentLoaded",ma):ma();setTimeout(ma,1500);typeof window!="undefined"&&(window.FBG2PlayerService=Qn,setTimeout(()=>{try{Qn.hydrateRemote()}catch(e){console.warn("[PlayerService] hydrateRemote falhou:",e)}},1500));})();
diff --git a/smart-tv/mk21-tv/core/EpgGuide.js b/smart-tv/mk21-tv/core/EpgGuide.js
new file mode 100644
index 0000000..95d62e5
--- /dev/null
+++ b/smart-tv/mk21-tv/core/EpgGuide.js
@@ -0,0 +1,306 @@
+/**
+ * MK21 Play — Motor de Guia de Programação (EPG) da Smart TV.
+ *
+ * Portão das funcionalidades do Saimo TV (LG webOS):
+ *  - "Guia" dedicado com lista de canais + programação do canal selecionado;
+ *  - HUD de zapping com "NO AR" / "A SEGUIR" e contagem regressiva.
+ *
+ * Este módulo é PURO (sem DOM, sem fetch, sem timer) para poder ser testado
+ * em Node (`smart-tv/tests/epg-guide.test.js`) e reusado pelo app legado.
+ *
+ * Convenção de tempo: TUDO em milissegundos epoch. As fontes do servidor
+ * (Xtream `get_short_epg`) entregam epoch em SEGUNDOS; o XMLTV entrega
+ * "YYYYMMDDHHmmss +ZZZZ". `toTimestampMs` normaliza os três formatos.
+ */
+
+const MS_PER_MINUTE = 60000;
+const MS_PER_SECOND = 1000;
+
+/**
+ * Converte um timestamp heterogêneo (epoch s, epoch ms, XMLTV, ISO) em ms.
+ * Retorna NaN quando o valor não é interpretável.
+ */
+function toTimestampMs(value) {
+  if (value === null || value === undefined || value === '') return NaN;
+
+  if (value instanceof Date) {
+    const t = value.getTime();
+    return Number.isFinite(t) ? t : NaN;
+  }
+
+  if (typeof value === 'number') {
+    if (!Number.isFinite(value)) return NaN;
+    // Epoch em segundos cabe abaixo de ~1e11 até o ano 5138; ms passa disso.
+    return value > 1e11 ? value : value * 1000;
+  }
+
+  const s = String(value).trim();
+  if (!s) return NaN;
+
+  if (/^\d{10}$/.test(s)) return parseInt(s, 10) * 1000;
+  if (/^\d{13}$/.test(s)) return parseInt(s, 10);
+
+  // XMLTV: 20261021020000 +0000
+  const xmltv = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);
+  if (xmltv) {
+    const [, y, mo, d, h, mi, sec, tz] = xmltv;
+    const tzPart = tz ? `${tz.slice(0, 3)}:${tz.slice(3)}` : 'Z';
+    const iso = `${y}-${mo}-${d}T${h}:${mi}:${sec}${tzPart}`;
+    const parsed = new Date(iso).getTime();
+    if (!Number.isNaN(parsed)) return parsed;
+  }
+
+  // ISO / SQL: 2026-10-21 02:00:00
+  const isoTry = new Date(s.replace(' ', 'T')).getTime();
+  if (!Number.isNaN(isoTry)) return isoTry;
+
+  return NaN;
+}
+
+/** Lê um título/descrição vindo em base64 (Xtream often base64-encoda) sem quebrar. */
+function decodeMaybeBase64(text) {
+  if (typeof text !== 'string') return '';
+  const trimmed = text.trim();
+  if (!trimmed || !/^[A-Za-z0-9+/=]+$/.test(trimmed) || trimmed.length < 4 || trimmed.length % 4 !== 0) {
+    return trimmed;
+  }
+  try {
+    const bin = atob(trimmed);
+    if (!/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF]+$/.test(bin)) return trimmed;
+    try {
+      return decodeURIComponent(escape(bin));
+    } catch (e) {
+      return bin;
+    }
+  } catch (e) {
+    return trimmed;
+  }
+}
+
+/**
+ * Normaliza um programa bruto para o formato interno do guia.
+ * Acepta tanto o payload do Xtream (`start_timestamp`, `stop_timestamp`,
+ * `title`, `description`) quanto entradas já no formato do XMLTV.
+ */
+function normalizeProgram(raw) {
+  if (!raw) return null;
+
+  const title = decodeMaybeBase64(raw.title || raw.name || raw.programme_title || '');
+  const desc = decodeMaybeBase64(raw.desc || raw.description || raw.sub_title || '');
+  const category = decodeMaybeBase64(raw.category || raw.genre || '');
+
+  const start = toTimestampMs(raw.start !== undefined ? raw.start : (raw.start_timestamp !== undefined ? raw.start_timestamp : raw.startTs));
+  const stop = toTimestampMs(raw.stop !== undefined ? raw.stop : (raw.stop_timestamp !== undefined ? raw.stop_timestamp : (raw.end !== undefined ? raw.end : raw.stopTs)));
+
+  if (!Number.isFinite(start) || !Number.isFinite(stop) || stop <= start) return null;
+
+  return {
+    title: title || 'Programação',
+    desc: desc || '',
+    category: category || '',
+    start,
+    stop,
+    durationMs: stop - start
+  };
+}
+
+/** Normaliza uma lista inteira de programas, descartando os inválidos. */
+function normalizePrograms(list) {
+  if (!Array.isArray(list)) return [];
+  const out = [];
+  for (let i = 0; i < list.length; i++) {
+    const p = normalizeProgram(list[i]);
+    if (p) out.push(p);
+  }
+  out.sort((a, b) => a.start - b.start);
+  return out;
+}
+
+/**
+ * Junta programas de várias fontes (XMLTV do servidor + short EPG do Xtream +
+ * qualquer fonte externa) sem duplicar o mesmo horário.
+ *
+ * A primeira fonte listada tem prioridade no título: o XMLTV costuma vir com
+ * sinopse mais rica, o short EPG do Xtream às vezes traz o título "ao vivo".
+ */
+function mergeProgramSources() {
+  const seen = new Set();
+  const merged = [];
+
+  for (let s = 0; s < arguments.length; s++) {
+    const source = normalizePrograms(arguments[s]);
+    for (let i = 0; i < source.length; i++) {
+      const p = source[i];
+      // Janela de 60s: mesmo programa com start slightly diferente = duplicata.
+      let duplicate = false;
+      for (let j = 0; j < merged.length; j++) {
+        const other = merged[j];
+        if (Math.abs(other.start - p.start) < MS_PER_MINUTE && Math.abs(other.stop - p.stop) < MS_PER_MINUTE) {
+          duplicate = true;
+          // Mantém a sinopse mais longa entre as duas fontes.
+          if (!other.desc && p.desc) other.desc = p.desc;
+          if (!other.category && p.category) other.category = p.category;
+          break;
+        }
+      }
+      if (!duplicate) {
+        seen.add(p.start);
+        merged.push(p);
+      }
+    }
+  }
+
+  merged.sort((a, b) => a.start - b.start);
+  return merged;
+}
+
+/**
+ * Monta a linha do tempo do guia: ordena, marca o programa "no ar" e calcula
+ * o progresso (0-100) de cada programa.
+ */
+function buildTimeline(programs, nowMs) {
+  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
+  const list = normalizePrograms(programs);
+
+  return list.map((p) => {
+    const isLive = p.start <= now && p.stop > now;
+    const elapsed = Math.max(0, now - p.start);
+    const progressPct = Math.min(100, Math.max(0, Math.round((elapsed / p.durationMs) * 100)));
+    const remainingMs = Math.max(0, p.stop - now);
+    return {
+      title: p.title,
+      desc: p.desc,
+      category: p.category,
+      start: p.start,
+      stop: p.stop,
+      durationMs: p.durationMs,
+      isLive,
+      isPast: p.stop <= now,
+      progressPct,
+      remainingMs
+    };
+  });
+}
+
+/**
+ * Escolhe o programa "no ar" e o próximo a partir de uma lista (normalizada ou não).
+ * Se nenhum estiver no ar, devolve o primeiro ainda por passar; se a lista acabou,
+ * devolve o último como "no ar" para não deixar a tela vazia.
+ */
+function pickNowNext(programs, nowMs) {
+  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
+  const list = normalizePrograms(programs);
+
+  if (!list.length) return { current: null, next: null };
+
+  let current = null;
+  for (let i = 0; i < list.length; i++) {
+    if (list[i].start <= now && list[i].stop > now) {
+      current = list[i];
+      break;
+    }
+  }
+
+  if (!current) {
+    // Nenhum no ar: usa o primeiro programa futuro; se todos passaram, o último.
+    const upcoming = list.find((p) => p.start > now);
+    current = upcoming || list[list.length - 1];
+  }
+
+  const idx = list.indexOf(current);
+  const next = idx >= 0 && idx + 1 < list.length ? list[idx + 1] : null;
+
+  return { current, next };
+}
+
+/** Minutos que faltam para o fim do programa (0 quando não está no ar). */
+function minutesRemaining(program, nowMs) {
+  if (!program) return 0;
+  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
+  if (!(program.start <= now && program.stop > now)) return 0;
+  return Math.max(0, Math.round((program.stop - now) / MS_PER_MINUTE));
+}
+
+/** "HH:MM" em horário local. */
+function formatClock(ms) {
+  const t = toTimestampMs(ms);
+  if (!Number.isFinite(t)) return '--:--';
+  const d = new Date(t);
+  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
+}
+
+/** "HH:MM" a partir de uma data já pronta (evita passar epoch). */
+function formatClockDate(date) {
+  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return '--:--';
+  return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
+}
+
+/**
+ * Contagem regressiva formatada: "1:05:23" (hh:mm:ss) ou "05:23" (mm:ss).
+ * Valores negativos ou inválidos viram "--:--".
+ */
+function formatCountdown(ms) {
+  const total = Math.floor(Number(ms) / MS_PER_SECOND);
+  if (!Number.isFinite(total) || total < 0) return '--:--';
+  const hours = Math.floor(total / 3600);
+  const minutes = Math.floor((total % 3600) / 60);
+  const seconds = total % 60;
+  const mm = String(minutes).padStart(2, '0');
+  const ss = String(seconds).padStart(2, '0');
+  if (hours > 0) return hours + ':' + mm + ':' + ss;
+  return mm + ':' + ss;
+}
+
+/**
+ * Resumo compacto do canal para a coluna esquerda do guia e para o HUD de
+ * zapping: o que está no ar agora e o que vem a seguir.
+ */
+function summarizeChannel(channel, programs, nowMs) {
+  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
+  const { current, next } = pickNowNext(programs, now);
+
+  let currentTitle = '';
+  let nextTitle = '';
+  let countdownMs = 0;
+  let progressPct = 0;
+  let isLive = false;
+
+  if (current) {
+    isLive = current.start <= now && current.stop > now;
+    currentTitle = current.title;
+    progressPct = Math.min(100, Math.max(0, Math.round(((now - current.start) / current.durationMs) * 100)));
+  }
+  if (next) {
+    nextTitle = next.title;
+    countdownMs = Math.max(0, next.start - now);
+  }
+
+  return {
+    name: (channel && (channel.name || channel.tvgName)) || 'Canal',
+    logo: (channel && channel.logo) || '',
+    group: (channel && channel.group) || '',
+    currentTitle,
+    nextTitle,
+    countdownMs,
+    countdownText: next ? formatCountdown(countdownMs) : '--:--',
+    progressPct,
+    isLive
+  };
+}
+
+module.exports = {
+  MS_PER_MINUTE,
+  MS_PER_SECOND,
+  toTimestampMs,
+  decodeMaybeBase64,
+  normalizeProgram,
+  normalizePrograms,
+  mergeProgramSources,
+  buildTimeline,
+  pickNowNext,
+  minutesRemaining,
+  formatClock,
+  formatClockDate,
+  formatCountdown,
+  summarizeChannel
+};
diff --git a/smart-tv/mk21-tv/core/OnScreenKeyboard.js b/smart-tv/mk21-tv/core/OnScreenKeyboard.js
new file mode 100644
index 0000000..c227c2a
--- /dev/null
+++ b/smart-tv/mk21-tv/core/OnScreenKeyboard.js
@@ -0,0 +1,231 @@
+/**
+ * MK21 Play — Teclado virtual (on-screen keyboard) da Smart TV.
+ *
+ * Portão da funcionalidade do Saimo TV (LG webOS): na TV não existe teclado
+ * físico, e o teclado do sistema (OSK) em webOS/Tizen é lento e ruins para
+ * busca. Este módulo implementa um teclado QWERTY navegável 100% pelo D-pad.
+ *
+ * Módulo PURO: guarda só o estado (texto + cursor + modo + posição no grid).
+ * A camada de DOM fica no app legado (`src/legacy/app-legacy.js`).
+ */
+
+/** Modos de teclado disponíveis, na ordem de alternância do botão "⇧". */
+const KEYBOARD_MODES = ['lower', 'upper', 'numbers', 'symbols'];
+
+/** Rótulo do botão que alterna o modo. */
+const MODE_LABELS = {
+  lower: 'abc',
+  upper: 'ABC',
+  numbers: '123',
+  symbols: '#+='
+};
+
+const LETTER_ROWS = [
+  ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
+  ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ç'],
+  ['z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '?'],
+  ['á', 'é', 'í', 'ó', 'ú', 'â', 'ê', 'ô', 'à', 'õ']
+];
+
+const NUMBER_ROWS = [
+  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
+  ['-', '.', ',', '?', '!', "'", '@', '#', '/', ':'],
+  ['(', ')', '+', '=', '%', '*', '$', '&', '_', '"']
+];
+
+/** Linha de controle fixa no rodapé do teclado. */
+const CONTROL_ROW = [
+  { action: 'MODE', label: '⇧' },
+  { action: 'SPACE', label: '␣' },
+  { action: 'BACKSPACE', label: '⌫' },
+  { action: 'CLEAR', label: '⟲' }
+];
+
+/**
+ * Monta o grid completo de teclas do modo pedido.
+ * @returns {Array<Array<string|{action:string,label:string}>>}
+ */
+function getRows(mode) {
+  const normalized = KEYBOARD_MODES.indexOf(mode) >= 0 ? mode : 'lower';
+
+  let base;
+  if (normalized === 'numbers' || normalized === 'symbols') {
+    base = NUMBER_ROWS.map((row) => row.slice());
+  } else {
+    base = LETTER_ROWS.map((row) => row.slice());
+    if (normalized === 'upper') {
+      base = base.map((row) => row.map((ch) => ch.toUpperCase()));
+    }
+  }
+
+  // No modo "símbolos" as letras continuam minúsculas — os símbolos ficam
+  // na segunda e terceira linhas, junto com os números.
+  return base.concat([CONTROL_ROW.slice()]);
+}
+
+/** Cria o estado inicial do teclado. */
+function createState(value, mode) {
+  const text = String(value === null || value === undefined ? '' : value);
+  return {
+    value: text,
+    cursor: text.length,
+    mode: KEYBOARD_MODES.indexOf(mode) >= 0 ? mode : 'lower',
+    row: 0,
+    col: 0
+  };
+}
+
+/** Número de linhas do grid do modo atual. */
+function getRowCount(state) {
+  return getRows(state && state.mode).length;
+}
+
+/** Número de colunas da linha (as linhas de controle são menores). */
+function getColCount(state, row) {
+  const rows = getRows(state && state.mode);
+  const idx = typeof row === 'number' ? row : (state ? state.row : 0);
+  const line = rows[idx] || rows[0] || [];
+  return line.length;
+}
+
+/**
+ * Move a seleção do grid no D-pad.
+ *
+ * Ao contrário de um grid de rolagem, o teclado virtual **prende** a seleção
+ * nas bordas: no D-pad da TV é muito mais previsível do que um salto da última
+ * letra para a primeira.
+ *
+ * @param {object} state
+ * @param {'up'|'down'|'left'|'right'} direction
+ */
+function moveCursor(state, direction) {
+  if (!state) return state;
+  const rows = getRows(state.mode);
+  const maxRow = rows.length - 1;
+
+  let row = state.row;
+  let col = state.col;
+
+  if (direction === 'up') {
+    row = row > 0 ? row - 1 : 0;
+  } else if (direction === 'down') {
+    row = row < maxRow ? row + 1 : maxRow;
+  } else if (direction === 'left') {
+    col = col > 0 ? col - 1 : 0;
+  } else if (direction === 'right') {
+    const width = getColCount(state, row);
+    col = col < width - 1 ? col + 1 : width - 1;
+  }
+
+  // Ao trocar de linha, garante que a coluna exista naquela linha.
+  const width = getColCount(state, row);
+  if (col >= width) col = width - 1;
+  if (col < 0) col = 0;
+
+  return Object.assign({}, state, { row, col });
+}
+
+/** Devolve a tecla focada no grid (ou null). */
+function getFocusedKey(state) {
+  if (!state) return null;
+  const rows = getRows(state.mode);
+  const line = rows[state.row];
+  if (!line) return null;
+  return line[state.col] !== undefined ? line[state.col] : null;
+}
+
+/** Insere um caractere na posição do cursor e avança o cursor. */
+function insertChar(state, char) {
+  if (!state || !char) return state;
+  const text = String(state.value || '');
+  const cursor = Math.max(0, Math.min(text.length, state.cursor));
+  const next = text.slice(0, cursor) + char + text.slice(cursor);
+  return Object.assign({}, state, { value: next, cursor: cursor + char.length });
+}
+
+/** Apaga o caractere anterior ao cursor (backspace). */
+function backspace(state) {
+  if (!state) return state;
+  const text = String(state.value || '');
+  const cursor = Math.max(0, Math.min(text.length, state.cursor));
+  if (cursor === 0) return state;
+  const next = text.slice(0, cursor - 1) + text.slice(cursor);
+  return Object.assign({}, state, { value: next, cursor: cursor - 1 });
+}
+
+/** Limpa todo o texto. */
+function clearText(state) {
+  if (!state) return state;
+  return Object.assign({}, state, { value: '', cursor: 0 });
+}
+
+/** Alterna o modo do teclado (lower -> upper -> numbers -> symbols -> lower). */
+function toggleMode(state) {
+  if (!state) return state;
+  const idx = KEYBOARD_MODES.indexOf(state.mode);
+  const nextMode = KEYBOARD_MODES[(idx + 1) % KEYBOARD_MODES.length];
+  const rows = getRows(nextMode);
+  return Object.assign({}, state, {
+    mode: nextMode,
+    row: Math.min(state.row, rows.length - 1),
+    col: 0
+  });
+}
+
+/**
+ * Avança a seleção para a próxima tecla, como um teclado de celular.
+ * Usado depois de digitar: sem isso, o OK seguinte repetiria a mesma letra.
+ */
+function advanceCursor(state) {
+  if (!state) return state;
+  const rows = getRows(state.mode);
+  let row = state.row;
+  let col = state.col;
+
+  const width = getColCount(state, row);
+  if (col < width - 1) {
+    col++;
+  } else if (row < rows.length - 1) {
+    row++;
+    col = 0;
+  } else {
+    row = 0;
+    col = 0;
+  }
+
+  return Object.assign({}, state, { row, col });
+}
+
+/** Move o cursor de texto (usado pelas setas esquerda/direita quando em modo texto). */
+function moveTextCursor(state, delta) {
+  if (!state) return state;
+  const text = String(state.value || '');
+  const cursor = Math.max(0, Math.min(text.length, (state.cursor || 0) + delta));
+  return Object.assign({}, state, { cursor });
+}
+
+/** Rótulo do botão de modo, para desenhar na tela. */
+function getModeLabel(state) {
+  return MODE_LABELS[(state && state.mode) || 'lower'] || 'abc';
+}
+
+module.exports = {
+  KEYBOARD_MODES,
+  MODE_LABELS,
+  LETTER_ROWS,
+  NUMBER_ROWS,
+  CONTROL_ROW,
+  getRows,
+  createState,
+  getRowCount,
+  getColCount,
+  moveCursor,
+  advanceCursor,
+  getFocusedKey,
+  insertChar,
+  backspace,
+  clearText,
+  toggleMode,
+  moveTextCursor,
+  getModeLabel
+};
diff --git a/smart-tv/mk21-tv/core/ZapHud.js b/smart-tv/mk21-tv/core/ZapHud.js
new file mode 100644
index 0000000..f218c1d
--- /dev/null
+++ b/smart-tv/mk21-tv/core/ZapHud.js
@@ -0,0 +1,126 @@
+/**
+ * MK21 Play — HUD de zapping (overlay de troca de canal) da Smart TV.
+ *
+ * Portão da funcionalidade do Saimo TV (LG webOS): ao trocar de canal o app
+ * mostra um cartão grande com o logo do canal, o que está "NO AR", o que vem
+ * "A SEGUIR" e a contagem regressiva até o próximo programa.
+ *
+ * Módulo PURO: só monta payloads e decide o auto-hide. A camada de DOM fica
+ * no app legado (`src/legacy/app-legacy.js`).
+ */
+
+const EpgGuide = require('./EpgGuide.js');
+
+/** Tempo que o HUD fica visível antes de se esconder sozinho. */
+const ZAP_AUTO_HIDE_MS = 7000;
+
+/** Tempo mínimo entre dois flaps do HUD, para não piscar durante o zapping. */
+const ZAP_MIN_INTERVAL_MS = 400;
+
+/**
+ * Monta o payload completo do HUD de zapping.
+ *
+ * @param {object} options
+ * @param {object} options.channel      Item do catálogo (name, logo, group).
+ * @param {number} [options.channelNumber] Número do canal (LCN), quando conhecido.
+ * @param {Array}  [options.programs]   Programas do canal (XMLTV/short EPG).
+ * @param {number} [options.nowMs]      Relógio de referência.
+ */
+function buildZapInfo(options) {
+  const opts = options || {};
+  const nowMs = Number.isFinite(opts.nowMs) ? opts.nowMs : Date.now();
+  const channel = opts.channel || null;
+
+  const summary = EpgGuide.summarizeChannel(channel, opts.programs, nowMs);
+
+  const number = Number.isFinite(opts.channelNumber) && opts.channelNumber > 0
+    ? opts.channelNumber
+    : '';
+
+  return {
+    number,
+    hasNumber: number !== '',
+    name: summary.name,
+    logo: summary.logo,
+    group: summary.group,
+    currentTitle: summary.currentTitle,
+    nextTitle: summary.nextTitle,
+    countdownMs: summary.countdownMs,
+    countdownText: summary.countdownText,
+    progressPct: summary.progressPct,
+    isLive: summary.isLive,
+    hasEpg: Boolean(summary.currentTitle)
+  };
+}
+
+/**
+ * Decide se o HUD já pode se esconder sozinho.
+ * `elapsedMs` é o tempo desde que o HUD apareceu.
+ */
+function shouldAutoHide(elapsedMs, thresholdMs) {
+  const elapsed = Number(elapsedMs);
+  if (!Number.isFinite(elapsed) || elapsed < 0) return false;
+  const limit = Number.isFinite(thresholdMs) && thresholdMs > 0 ? thresholdMs : ZAP_AUTO_HIDE_MS;
+  return elapsed >= limit;
+}
+
+/**
+ * Normaliza o número do canal digitado pelo usuário (overlay de canais).
+ * Devolve null quando não há número válido.
+ */
+function parseChannelNumber(raw) {
+  const digits = String(raw === null || raw === undefined ? '' : raw).replace(/\D+/g, '');
+  if (!digits) return null;
+  const value = parseInt(digits, 10);
+  if (!Number.isFinite(value) || value <= 0) return null;
+  return value;
+}
+
+/**
+ * Monta o mapa canal -> número (LCN) a partir de uma lista de canais.
+ * A numeração segue a ordem da lista, começando em 1 — é o mesmo criterio
+ * usado pelo "digite o número do canal" do MK21.
+ */
+function buildChannelNumberMap(channels) {
+  const map = new Map();
+  if (!Array.isArray(channels)) return map;
+  for (let i = 0; i < channels.length; i++) {
+    const ch = channels[i];
+    if (!ch) continue;
+    const key = channelKey(ch);
+    if (!key || map.has(key)) continue;
+    map.set(key, i + 1);
+  }
+  return map;
+}
+
+/** Chave estável de um canal (tvg-id quando existe, senão o nome normalizado). */
+function channelKey(channel) {
+  if (!channel) return '';
+  const tvgId = String(channel.tvgId || channel['tvg-id'] || '').trim().toLowerCase();
+  if (tvgId) return 'id:' + tvgId;
+  const name = String(channel.name || '').trim().toLowerCase();
+  return name ? 'name:' + name : '';
+}
+
+/** Resolve o número do canal a partir do mapa, com fallback para o índice+1. */
+function resolveChannelNumber(map, channel, fallbackIndex) {
+  const key = channelKey(channel);
+  if (key && map && typeof map.get === 'function') {
+    const found = map.get(key);
+    if (Number.isFinite(found)) return found;
+  }
+  if (Number.isFinite(fallbackIndex) && fallbackIndex >= 0) return fallbackIndex + 1;
+  return null;
+}
+
+module.exports = {
+  ZAP_AUTO_HIDE_MS,
+  ZAP_MIN_INTERVAL_MS,
+  buildZapInfo,
+  shouldAutoHide,
+  parseChannelNumber,
+  buildChannelNumberMap,
+  channelKey,
+  resolveChannelNumber
+};
diff --git a/smart-tv/mk21-tv/index.html b/smart-tv/mk21-tv/index.html
index a7ab151..ccca1fe 100644
--- a/smart-tv/mk21-tv/index.html
+++ b/smart-tv/mk21-tv/index.html
@@ -120,6 +120,7 @@
         <button id="tabMovies" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-film"></i> FILMES</button>
         <button id="tabSeries" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-clapperboard"></i> SÉRIES</button>
         <button id="tabContinue" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-clock-rotate-left"></i> CONTINUAR</button>
+        <button id="tabGuide" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-calendar-days"></i> GUIA</button>
         <button id="tabFavs" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-star"></i> FAVORITOS</button>
         <button id="tabSettings" class="tab-btn header-focusable" tabindex="0"><i class="fa-solid fa-gear"></i> CONFIGURAÇÕES</button>
       </nav>
@@ -165,6 +166,7 @@
       <div class="col-pane col-channels">
         <div class="search-box">
           <input id="inputSearch" type="text" placeholder="🔍 Buscar canal, filme ou série..." tabindex="0">
+          <button id="btnOpenOsk" class="search-osk-btn" tabindex="0" title="Teclado virtual">⌨️</button>
         </div>
         <div class="pane-header">
           <span id="txtCurrentCategoryTitle">📺 Todos os Canais</span>
@@ -255,6 +257,26 @@
 
     </section>
 
+    <!-- SEÇÃO GUIA DE PROGRAMAÇÃO (canais + grade, no estilo Saimo TV) -->
+    <section id="sectionGuide" class="content-section hidden">
+      <div class="guide-layout">
+        <div class="col-pane col-guide-channels">
+          <div class="pane-header">
+            <span id="txtGuideChannelsTitle">📺 Canais</span>
+            <span id="badgeGuideChannels" class="counter-badge">0</span>
+          </div>
+          <ul id="listGuideChannels" class="pane-scroll scrollable-container" tabindex="0"></ul>
+        </div>
+        <div class="col-pane col-guide-programs">
+          <div class="pane-header">
+            <span id="txtGuideProgramsTitle">🔵 Programação</span>
+            <span id="badgeGuidePrograms" class="counter-badge">0</span>
+          </div>
+          <div id="listGuidePrograms" class="pane-scroll scrollable-container" tabindex="0"></div>
+        </div>
+      </div>
+    </section>
+
     <!-- SEÇÃO CONFIGURAÇÕES (CLONE VIZZION PLAY DAS FOTOS) -->
     <section id="sectionSettings" class="content-section hidden">
       <div class="settings-left-menu scrollable-container" tabindex="0">
@@ -281,8 +303,44 @@
       <div id="settingsDetailBox" class="settings-detail-panel scrollable-container" tabindex="0"></div>
     </section>
 
+    <!-- HUD DE ZAPPING (cartão do canal durante a troca de canal) -->
+    <div id="zapHud" class="zap-hud hidden" aria-live="polite">
+      <div class="zap-hud-logo-wrap">
+        <img id="zapHudLogo" class="zap-hud-logo" alt="" onerror="this.style.display='none'">
+      </div>
+      <div class="zap-hud-body">
+        <div class="zap-hud-title-row">
+          <span id="zapHudNumber" class="zap-hud-number"></span>
+          <span id="zapHudName" class="zap-hud-name">Canal</span>
+        </div>
+        <div class="zap-hud-now">
+          <span class="zap-hud-badge">NO AR</span>
+          <span id="zapHudNow" class="zap-hud-now-title">—</span>
+        </div>
+        <div class="zap-hud-progress"><div id="zapHudProgressFill" class="zap-hud-progress-fill"></div></div>
+        <div class="zap-hud-next">
+          <span class="zap-hud-next-label">A SEGUIR</span>
+          <span id="zapHudNext" class="zap-hud-next-title">—</span>
+          <span id="zapHudCountdown" class="zap-hud-countdown">--:--</span>
+        </div>
+      </div>
+    </div>
+
   </main>
 
+  <!-- ================= TECLADO VIRTUAL (D-PAD) ================= -->
+  <div id="oskOverlay" class="osk-overlay hidden">
+    <div class="osk-panel">
+      <div class="osk-head">
+        <span class="osk-title">⌨️ Teclado virtual</span>
+        <span id="oskMode" class="osk-mode">abc</span>
+      </div>
+      <div id="oskPreview" class="osk-preview"></div>
+      <div id="oskGrid" class="osk-grid"></div>
+      <div class="osk-hint">◀ ▶ ▲ ▼ navegar &nbsp;•&nbsp; OK confirmar &nbsp;•&nbsp; VOLTAR fechar</div>
+    </div>
+  </div>
+
   <!-- ================= MODAIS ================= -->
   <!-- Modal de Faixas de Áudio e Legendas -->
   <div id="modalAudioSubs" class="app-modal hidden">
diff --git a/smart-tv/mk21-tv/src/legacy/app-legacy.js b/smart-tv/mk21-tv/src/legacy/app-legacy.js
index d1a3ad6..715fdc3 100644
--- a/smart-tv/mk21-tv/src/legacy/app-legacy.js
+++ b/smart-tv/mk21-tv/src/legacy/app-legacy.js
@@ -19,6 +19,12 @@ const Mk21DnsResolver = {
 // MK21 PLAY v2.8.0 — Navegação espacial DPAD — Motor Otimizado para Smart TV LG webOS/Tizen
 // Prioridade Máxima no Ao Vivo, Carga em Etapas/Segundo Plano, Totais por Servidor, Diálogos Estilo APK e D-Pad Total
 const CatalogUtils = require('../../core/CatalogUtils.js');
+// Modulos puros das funcionalidades portadas do Saimo TV (Guia de Programação,
+// HUD de zapping e teclado virtual). Ficam fora deste arquivo para poderem ser
+// testados em Node — ver smart-tv/tests/.
+const EpgGuide = require('../../core/EpgGuide.js');
+const ZapHud = require('../../core/ZapHud.js');
+const OnScreenKeyboard = require('../../core/OnScreenKeyboard.js');
 const $ = id => document.getElementById(id);
 
 // Fase 2: o legado deixou de viver no escopo global (agora e um IIFE bundlado).
@@ -32,7 +38,17 @@ window.mk21App = {
   logout: function () { return logout(); },
   initWebOSScrollFix: function () { return initWebOSScrollFix(); },
   get activeItem() { return activeItem; },
-  get playerService() { return window.FBG2PlayerService; }
+  get playerService() { return window.FBG2PlayerService; },
+  // Funcionalidades portadas do Saimo TV
+  openGuide: function () { return mk21OpenGuide(); },
+  closeGuide: function () { return mk21GuideClose(); },
+  guideState: function () { return mk21Guide; },
+  loadXmltv: function (force) { return mk21LoadXmltv(force); },
+  showZapHud: function (item) { return mk21ShowZapHud(item); },
+  hideZapHud: function () { return mk21HideZapHud(); },
+  openOsk: function (target) { return mk21OpenOsk(target); },
+  closeOsk: function (commit) { return mk21CloseOsk(commit); },
+  oskState: function () { return mk21Osk; }
 };
 
 // ==================== FASE 2: LIGACAO COM O PLAYERSERVICE ====================
@@ -835,7 +851,7 @@ function resumeLastChannelIfPossible() {
   if (toPlay) {
     currentContentType = 'LIVE';
     try {
-      ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings'].forEach(t => {
+      ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabGuide', 'tabSettings'].forEach(t => {
         if ($(t)) $(t).classList.remove('active');
       });
       if ($('tabLive')) $('tabLive').classList.add('active');
@@ -960,7 +976,7 @@ let focusedSeriesEpIdx = 0;
 let activeModalSeriesData = null;
 
 const headerElements = [
-  'tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings',
+  'tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabGuide', 'tabSettings',
   'btnHeaderServer', 'btnHeaderLogs', 'btnRefreshList', 'btnHeaderLogout'
 ];
 
@@ -2858,22 +2874,37 @@ async function updateSmartTvEpg(item) {
     return;
   }
 
+  const listings = await mk21FetchShortEpg(item, streamId, srv, user, pass, 6);
+  if (listings && listings.length > 0 && activeItem === item) {
+    applyRealSmartTvEpg(item, listings);
+  }
+}
+
+/**
+ * Busca a grade curta de um canal no Xtream (`get_short_epg`) e guarda no cache.
+ * Extraido de `updateSmartTvEpg` para ser reaproveitado pelo Guia de Programação
+ * (que precisa da grade de canais que o usuario nao esta assistindo).
+ */
+async function mk21FetchShortEpg(item, streamId, srv, user, pass, limit) {
+  if (!srv || !srv.url || !streamId || !user || !pass) return null;
+  const cacheKey = `epg_${streamId}`;
+  if (smartTvEpgCache.has(cacheKey)) return smartTvEpgCache.get(cacheKey);
+
   try {
     const cleanBase = srv.url.replace(/\/+$/, '');
-    const apiUrl = `${cleanBase}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_short_epg&stream_id=${streamId}&limit=6`;
+    const apiUrl = `${cleanBase}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_short_epg&stream_id=${streamId}&limit=${limit || 6}`;
     const res = await fetch(apiUrl);
     if (res.ok) {
       const data = await res.json();
       if (data && Array.isArray(data.epg_listings) && data.epg_listings.length > 0) {
         smartTvEpgCache.set(cacheKey, data.epg_listings);
-        if (activeItem === item) {
-          applyRealSmartTvEpg(item, data.epg_listings);
-        }
+        return data.epg_listings;
       }
     }
   } catch (err) {
     console.debug('[SmartTV EPG] Falha ao carregar metadados reais, mantendo simulação:', err);
   }
+  return null;
 }
 
 function parseXmltvTimestamp(val) {
@@ -3239,6 +3270,7 @@ function commitChannelNumber() {
   if (targetIdx >= 0 && targetIdx < currentFilteredItems.length) {
     playStream(currentFilteredItems[targetIdx]);
     showChannelBanner(`Canal ${channelNum}: ${currentFilteredItems[targetIdx].name}`);
+    mk21ShowZapHud(currentFilteredItems[targetIdx]);
   } else {
     showChannelBanner(`Canal ${channelNum} não encontrado`);
   }
@@ -4638,6 +4670,7 @@ function playNextChannel() {
   const nextIdx = (currentIdx + 1) % currentFilteredItems.length;
   playStream(currentFilteredItems[nextIdx]);
   showChannelBanner('▲ Próximo: ' + currentFilteredItems[nextIdx].name);
+  mk21ShowZapHud(currentFilteredItems[nextIdx]);
 }
 
 function playPreviousChannel() {
@@ -4646,6 +4679,7 @@ function playPreviousChannel() {
   const prevIdx = (currentIdx - 1 + currentFilteredItems.length) % currentFilteredItems.length;
   playStream(currentFilteredItems[prevIdx]);
   showChannelBanner('▼ Anterior: ' + currentFilteredItems[prevIdx].name);
+  mk21ShowZapHud(currentFilteredItems[prevIdx]);
 }
 
 function isAppFullscreen() {
@@ -5124,10 +5158,34 @@ $('tabLive').onclick = () => switchContentType('LIVE');
 $('tabMovies').onclick = () => switchContentType('MOVIE');
 $('tabSeries').onclick = () => switchContentType('SERIES');
 if ($('tabContinue')) $('tabContinue').onclick = () => switchContentType('CONTINUE');
+if ($('tabGuide')) $('tabGuide').onclick = () => switchContentType('GUIDE');
 $('tabFavs').onclick = () => switchContentType('FAVORITES');
 $('tabSettings').onclick = () => switchContentType('SETTINGS');
 
+// Teclado virtual: botão ao lado do campo de busca.
+if ($('btnOpenOsk')) {
+  $('btnOpenOsk').onclick = () => {
+    if (mk21Osk.open) { mk21CloseOsk(true); return; }
+    mk21OpenOsk($('inputSearch'));
+  };
+}
+
+// Guia: clique/toque em um canal da coluna esquerda.
+if ($('listGuideChannels')) {
+  $('listGuideChannels').addEventListener('click', (ev) => {
+    const btn = ev.target.closest ? ev.target.closest('.guide-channel-btn') : null;
+    if (!btn) return;
+    const idx = parseInt(btn.getAttribute('data-idx') || '0', 10);
+    if (!Number.isFinite(idx)) return;
+    mk21SelectGuideChannel(idx);
+  });
+}
+
 function switchContentType(type) {
+  // Guarda a aba de origem para o VOLTAR do Guia voltar para ela.
+  if (type === 'GUIDE' && typeof mk21Guide !== 'undefined') {
+    mk21Guide.previousType = (currentContentType && currentContentType !== 'GUIDE') ? currentContentType : 'LIVE';
+  }
   currentContentType = type;
   // Feedback visual imediato ao trocar de aba
   try {
@@ -5135,15 +5193,23 @@ function switchContentType(type) {
     if (labels[type]) showChannelBanner(labels[type]);
   } catch (e) {}
 
-  ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings'].forEach(t => {
+  ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabGuide', 'tabSettings'].forEach(t => {
     const el = $(t);
     if (el) el.classList.remove('active');
   });
-  const activeTabId = type === 'LIVE' ? 'tabLive' : (type === 'MOVIE' ? 'tabMovies' : (type === 'SERIES' ? 'tabSeries' : (type === 'CONTINUE' ? 'tabContinue' : (type === 'FAVORITES' ? 'tabFavs' : 'tabSettings'))));
+  const activeTabId = type === 'LIVE' ? 'tabLive' : (type === 'MOVIE' ? 'tabMovies' : (type === 'SERIES' ? 'tabSeries' : (type === 'CONTINUE' ? 'tabContinue' : (type === 'FAVORITES' ? 'tabFavs' : (type === 'GUIDE' ? 'tabGuide' : 'tabSettings')))));
   if ($(activeTabId)) $(activeTabId).classList.add('active');
 
+  if (type === 'GUIDE') {
+    $('sectionUnified').classList.add('hidden');
+    $('sectionSettings').classList.add('hidden');
+    mk21OpenGuide();
+    return;
+  }
+
   if (type === 'SETTINGS') {
     $('sectionUnified').classList.add('hidden');
+    $('sectionGuide').classList.add('hidden');
     $('sectionSettings').classList.remove('hidden');
     renderSettings('info');
     activeZone = 'settings';
@@ -5166,6 +5232,7 @@ function switchContentType(type) {
   }
 
   $('sectionSettings').classList.add('hidden');
+  $('sectionGuide').classList.add('hidden');
   $('sectionUnified').classList.remove('hidden');
 
   buildCurrentCategories();
@@ -7889,6 +7956,40 @@ document.addEventListener('keydown', function (e) {
     }
   }
 
+  // TECLADO VIRTUAL (D-pad) — tem prioridade sobre tudo: quando está aberto,
+  // as setas movem a seleção das teclas e o OK confirma a tecla focada.
+  if (typeof mk21Osk !== 'undefined' && mk21Osk.open) {
+    if (k === 461 || k === 10009 || k === 27 || key === 'Escape' || key === 'Back') {
+      e.preventDefault();
+      e.stopPropagation();
+      mk21CloseOsk(false);
+      return;
+    }
+    if (k === 37 || key === 'ArrowLeft') { e.preventDefault(); mk21OskMove('left'); return; }
+    if (k === 39 || key === 'ArrowRight') { e.preventDefault(); mk21OskMove('right'); return; }
+    if (k === 38 || key === 'ArrowUp') { e.preventDefault(); mk21OskMove('up'); return; }
+    if (k === 40 || key === 'ArrowDown') { e.preventDefault(); mk21OskMove('down'); return; }
+    if (k === 13 || key === 'Enter') { e.preventDefault(); mk21OskActivate(); return; }
+    if (k === 8 || key === 'Backspace') { e.preventDefault(); mk21OskBackspace(); return; }
+    return;
+  }
+
+  // GUIA DE PROGRAMAÇÃO — enquanto aberto, o D-pad navega nas duas colunas.
+  if (typeof mk21Guide !== 'undefined' && mk21Guide.open) {
+    if (k === 461 || k === 10009 || k === 27 || key === 'Escape' || key === 'Back') {
+      e.preventDefault();
+      e.stopPropagation();
+      mk21GuideClose();
+      return;
+    }
+    if (k === 37 || key === 'ArrowLeft') { e.preventDefault(); mk21GuideMove('left'); return; }
+    if (k === 39 || key === 'ArrowRight') { e.preventDefault(); mk21GuideMove('right'); return; }
+    if (k === 38 || key === 'ArrowUp') { e.preventDefault(); mk21GuideMove('up'); return; }
+    if (k === 40 || key === 'ArrowDown') { e.preventDefault(); mk21GuideMove('down'); return; }
+    if (k === 13 || key === 'Enter') { e.preventDefault(); mk21GuideActivate(); return; }
+    return;
+  }
+
   // 1.1 Focus Trap & Gestão de Foco para Smart TVs (webOS, Tizen, Android TV)
   const activeModal = mk21GetOpenModal();
   if (isAppFullscreen() && !activeModal) {
@@ -9592,6 +9693,774 @@ async function mk21TryAlternativeSource() {
   }
 }
 
+/* ==========================================================================
+   GUIA DE PROGRAMAÇÃO + HUD DE ZAPPING + TECLADO VIRTUAL
+   --------------------------------------------------------------------------
+   Funcionalidades portadas do Saimo TV (LG webOS) para o MK21 Play:
+
+     1. Guia de Programação dedicado (canais à esquerda + grade à direita);
+     2. HUD de zapping (cartão do canal com NO AR / A SEGUIR e contagem);
+     3. Teclado virtual navegável pelo D-pad (a TV não tem teclado físico).
+
+   Até a v2.9.0 a TV só conhecia o EPG do canal QUE ESTÁ TOCANDO e completava o
+   resto com uma grade simulada. Aqui passamos a baixar o XMLTV do servidor
+   (`xmltv.php`), indexar por canal e usar a grade real nas três telas.
+   ========================================================================== */
+
+// --------------------------------------------------------------------------
+// 1. EPG XMLTV — carga, indexação por canal e consulta
+// --------------------------------------------------------------------------
+const mk21Xmltv = {
+  url: '',
+  promise: null,
+  byChannel: new Map(),   // id do XMLTV -> [programas]
+  byName: new Map(),      // nome exibido normalizado -> id do XMLTV
+  programCount: 0,
+  channelCount: 0,
+  loadedAt: 0,
+  lastError: '',
+  maxProgrammes: 250000   // teto de memória: TVs antigas não aguentam milhões
+};
+
+/**
+ * Normaliza um nome de canal para comparar com o `<display-name>` do XMLTV.
+ * Remove qualidade e sufixos que o painel costuma acrescentar ("Globo FHD").
+ */
+function mk21NormalizeChannelName(name) {
+  return String(name || '')
+    .toLowerCase()
+    .replace(/\b(fhd|uhd|hd|sd|4k|2k|1080p|720p|480p|hevc|h265|br|brasil)\b/g, ' ')
+    .replace(/[^a-z0-9]+/g, ' ')
+    .trim();
+}
+
+/** URL do XMLTV: `url-tvg=` do header da playlist ou `xmltv.php` do Xtream. */
+function mk21XmltvUrl() {
+  if (window.serverXmltvUrl) return window.serverXmltvUrl;
+
+  const srv = SERVERS[currentServerIndex] || DEFAULT_SERVERS[0];
+  const cred = getSavedCredentials() || {};
+  if (srv && srv.url && cred.user && cred.pass) {
+    return `${srv.url.replace(/\/+$/, '')}/xmltv.php?username=${encodeURIComponent(cred.user)}&password=${encodeURIComponent(cred.pass)}`;
+  }
+  return '';
+}
+
+/**
+ * Parser do XMLTV. Usa regex (e não DOMParser) porque o arquivo pode passar de
+ * 50 MB e a árvore DOM estouraria a memória das TVs antigas — o mesmo motivo
+ * que levou o M3U a ir para Web Worker.
+ */
+function mk21ParseXmltv(xmlText) {
+  const map = new Map();
+  const names = new Map();
+  if (!xmlText || typeof xmlText !== 'string') return { byChannel: map, byName: names };
+  if (xmlText.indexOf('<programme') < 0) return { byChannel: map, byName: names };
+
+  // <channel id="..."><display-name>...</display-name></channel>
+  // O nome exibido é o que amarra o XMLTV ao canal quando o painel não
+  // devolve o `epg_channel_id` (campo do Xtream).
+  const channelRe = /<channel\s+id="([^"]+)"[^>]*>([\s\S]*?)<\/channel>/gi;
+  const displayNameRe = /<display-name[^>]*>([\s\S]*?)<\/display-name>/i;
+  let chMatch;
+  while ((chMatch = channelRe.exec(xmlText)) !== null) {
+    const id = String(chMatch[1] || '').trim().toLowerCase();
+    if (!id) continue;
+    const dn = chMatch[2].match(displayNameRe);
+    if (!dn) continue;
+    const display = String(dn[1]).replace(/<!\[CDATA\[|\]\]>/g, '').trim();
+    const key = mk21NormalizeChannelName(display);
+    if (key && !names.has(key)) names.set(key, id);
+  }
+
+  const programmeRe = /<programme\s+[^>]*start="([^"]+)"[^>]*stop="([^"]+)"[^>]*channel="([^"]+)"[^>]*>([\s\S]*?)<\/programme>/gi;
+  const titleRe = /<title[^>]*>([\s\S]*?)<\/title>/i;
+  const descRe = /<desc[^>]*>([\s\S]*?)<\/desc>/i;
+  const catRe = /<category[^>]*>([\s\S]*?)<\/category>/i;
+
+  let match;
+  let count = 0;
+  while ((match = programmeRe.exec(xmlText)) !== null) {
+    if (count >= mk21Xmltv.maxProgrammes) break;
+
+    const channelId = String(match[3] || '').trim().toLowerCase();
+    const start = EpgGuide.toTimestampMs(match[1]);
+    const stop = EpgGuide.toTimestampMs(match[2]);
+    if (!channelId || !Number.isFinite(start) || !Number.isFinite(stop) || stop <= start) continue;
+
+    const inner = match[4] || '';
+    const titleMatch = inner.match(titleRe);
+    const descMatch = inner.match(descRe);
+    const catMatch = inner.match(catRe);
+
+    const clean = (m) => (m ? String(m[1]).replace(/<!\[CDATA\[|\]\]>/g, '').trim() : '');
+    const programme = {
+      title: clean(titleMatch) || 'Programação',
+      desc: clean(descMatch),
+      category: clean(catMatch),
+      start,
+      stop
+    };
+
+    let list = map.get(channelId);
+    if (!list) {
+      list = [];
+      map.set(channelId, list);
+    }
+    list.push(programme);
+    count++;
+  }
+
+  // Ordena a grade de cada canal por horário de início.
+  map.forEach((list) => list.sort((a, b) => a.start - b.start));
+  return { byChannel: map, byName: names };
+}
+
+/** Baixa e indexa o XMLTV (uma única promessa em voo por URL). */
+async function mk21LoadXmltv(force) {
+  const url = mk21XmltvUrl();
+  if (!url) {
+    mk21Xmltv.lastError = 'Servidor não expõe XMLTV.';
+    return null;
+  }
+
+  if (!force && mk21Xmltv.promise && mk21Xmltv.url === url) return mk21Xmltv.promise;
+  if (!force && mk21Xmltv.loadedAt && mk21Xmltv.url === url) return mk21Xmltv.byChannel;
+
+  mk21Xmltv.url = url;
+  mk21Xmltv.promise = (async () => {
+    try {
+      const res = await fetch(url);
+      if (!res.ok) {
+        mk21Xmltv.lastError = 'HTTP ' + res.status;
+        return null;
+      }
+      const text = await res.text();
+      const parsed = mk21ParseXmltv(text);
+      const map = parsed.byChannel;
+      if (!map || map.size === 0) {
+        mk21Xmltv.lastError = 'XMLTV sem programas.';
+        return null;
+      }
+      mk21Xmltv.byChannel = map;
+      mk21Xmltv.byName = parsed.byName;
+      mk21Xmltv.channelCount = map.size;
+      mk21Xmltv.programCount = Array.from(map.values()).reduce((acc, l) => acc + l.length, 0);
+      mk21Xmltv.loadedAt = Date.now();
+      mk21Xmltv.lastError = '';
+      addDevLog('[Guia] XMLTV carregado: ' + mk21Xmltv.programCount + ' programas em ' + mk21Xmltv.channelCount + ' canais.', 'info');
+      try { window.dispatchEvent(new CustomEvent('mk21:epg-updated')); } catch (e) {}
+      return map;
+    } catch (err) {
+      mk21Xmltv.lastError = (err && err.message) ? err.message : 'falha de rede';
+      console.debug('[Guia] Falha ao carregar XMLTV:', err);
+      return null;
+    }
+  })();
+
+  return mk21Xmltv.promise;
+}
+
+/**
+ * Programas do XMLTV já em cache para um canal (null quando não há).
+ *
+ * Ordem de tentativa:
+ *   1. `tvg-id` / `epg_channel_id` — o id exato do canal no XMLTV;
+ *   2. id do XMLTV igual ao nome normalizado do canal;
+ *   3. `<display-name>` do XMLTV igual ao nome do canal (quando o painel não
+ *      devolve o `epg_channel_id`).
+ */
+function mk21XmltvProgramsFor(channel) {
+  if (!channel) return null;
+
+  const tvgId = String(channel.tvgId || channel['tvg-id'] || '').trim().toLowerCase();
+  if (tvgId) {
+    const list = mk21Xmltv.byChannel.get(tvgId);
+    if (list && list.length) return list;
+  }
+
+  const name = String(channel.name || channel.tvgName || '').trim().toLowerCase();
+  if (name) {
+    const list = mk21Xmltv.byChannel.get(name);
+    if (list && list.length) return list;
+  }
+
+  const normalized = mk21NormalizeChannelName(channel.name || channel.tvgName);
+  if (normalized) {
+    const xmltvId = mk21Xmltv.byName.get(normalized);
+    if (xmltvId) {
+      const list = mk21Xmltv.byChannel.get(xmltvId);
+      if (list && list.length) return list;
+    }
+  }
+
+  return null;
+}
+
+/**
+ * Grade completa de um canal: XMLTV (servidor) + short EPG (Xtream) + simulada.
+ * Devolve sempre uma lista normalizada e ordenada.
+ */
+async function mk21ProgramsForChannel(channel, options) {
+  const opts = options || {};
+  const nowMs = Date.now();
+  const sources = [];
+
+  const xmltv = mk21XmltvProgramsFor(channel);
+  if (xmltv) sources.push(xmltv);
+
+  // Se ainda não temos XMLTV, tenta carregar em segundo plano.
+  if (!xmltv && opts.allowFetch !== false) {
+    const url = mk21XmltvUrl();
+    if (url && mk21Xmltv.url !== url) {
+      mk21LoadXmltv(false).then(() => {
+        if (mk21Guide.open) mk21RenderGuidePrograms();
+      }).catch(() => {});
+    }
+  }
+
+  // Short EPG do Xtream (6 programas) como complemento do canal em foco.
+  if (opts.withShortEpg) {
+    const srv = SERVERS[currentServerIndex] || DEFAULT_SERVERS[0];
+    const cred = getSavedCredentials() || {};
+    let streamId = channel && (channel.streamId || channel.stream_id);
+    if (!streamId && channel && channel.url) {
+      const m = channel.url.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);
+      if (m && m[1]) streamId = m[1];
+    }
+    if (srv && cred.user && cred.pass && streamId) {
+      const listings = await mk21FetchShortEpg(channel, streamId, srv, cred.user, cred.pass, 12);
+      if (listings && listings.length) {
+        sources.push(listings.map((l) => ({
+          title: l.title,
+          desc: l.description,
+          start: l.start_timestamp !== undefined ? l.start_timestamp : l.start,
+          stop: l.stop_timestamp !== undefined ? l.stop_timestamp : (l.end !== undefined ? l.end : l.stop)
+        })));
+      }
+    }
+  }
+
+  const merged = EpgGuide.mergeProgramSources.apply(null, sources);
+  if (merged.length) return merged;
+
+  // Sem EPG real: usa a grade simulada já existente no app legado.
+  const sim = getSimulatedTvProgram((channel && channel.name) || '');
+  if (sim && sim.title) {
+    const now = new Date(nowMs);
+    const startMs = now.getTime() - (now.getMinutes() % 60) * 60000;
+    return EpgGuide.normalizePrograms([
+      { title: sim.title, desc: '', start: startMs, stop: startMs + 3600000 }
+    ]);
+  }
+  return [];
+}
+
+// --------------------------------------------------------------------------
+// 2. GUIA DE PROGRAMAÇÃO (tela dedicada)
+// --------------------------------------------------------------------------
+const mk21Guide = {
+  open: false,
+  channels: [],
+  channelIndex: 0,
+  programs: [],
+  programIndex: 0,
+  pane: 'channels',   // 'channels' | 'programs'
+  loading: false
+};
+
+/** Canais exibidos no guia (todos os canais ao vivo do catálogo). */
+function mk21GuideChannels() {
+  const live = (allCatalog && Array.isArray(allCatalog.LIVE) && allCatalog.LIVE.length)
+    ? allCatalog.LIVE
+    : (Array.isArray(currentFilteredItems)
+      ? currentFilteredItems.filter((it) => !it.contentType || it.contentType === 'LIVE')
+      : []);
+  return live.filter((it) => it && (it.name || it.url));
+}
+
+/** Abre a tela do guia. */
+function mk21OpenGuide() {
+  const section = $('sectionGuide');
+  if (!section) return;
+
+  // Garante que só o Guia fique visível, mesmo quando ele é aberto por atalho
+  // (e não pela aba do cabeçalho).
+  const unified = $('sectionUnified');
+  if (unified) unified.classList.add('hidden');
+  const settings = $('sectionSettings');
+  if (settings) settings.classList.add('hidden');
+
+  mk21Guide.channels = mk21GuideChannels();
+  mk21Guide.open = true;
+  mk21Guide.programIndex = 0;
+
+  // Destaca o canal que está no ar (ou o primeiro da lista).
+  const activeKey = activeItem ? ZapHud.channelKey(activeItem) : '';
+  let idx = 0;
+  if (activeKey) {
+    idx = mk21Guide.channels.findIndex((ch) => ZapHud.channelKey(ch) === activeKey);
+    if (idx < 0) idx = 0;
+  }
+  mk21Guide.channelIndex = idx;
+
+  section.classList.remove('hidden');
+  mk21RenderGuideChannels();
+  mk21SelectGuideChannel(idx, true);
+
+  // Carrega o XMLTV em segundo plano (a primeira abertura demora).
+  mk21LoadXmltv(false).then(() => {
+    if (mk21Guide.open) mk21RenderGuidePrograms();
+  }).catch(() => {});
+
+  try { mk21FocusSafe($('listGuideChannels')); } catch (e) {}
+  activeZone = 'guide';
+  showChannelBanner('📅 Guia de Programação');
+}
+
+/** Fecha a tela do guia. */
+function mk21CloseGuide() {
+  const section = $('sectionGuide');
+  if (section) section.classList.add('hidden');
+  mk21Guide.open = false;
+  mk21Guide.programs = [];
+}
+
+/** Renderiza a coluna de canais (com o que está no ar em cada um). */
+function mk21RenderGuideChannels() {
+  const list = $('listGuideChannels');
+  if (!list) return;
+
+  const badge = $('badgeGuideChannels');
+  if (badge) badge.textContent = String(mk21Guide.channels.length);
+
+  if (!mk21Guide.channels.length) {
+    list.innerHTML = '<div class="guide-empty">Nenhum canal carregado.<br>Conecte-se a um servidor para ver a grade.</div>';
+    return;
+  }
+
+  const nowMs = Date.now();
+  let html = '';
+  for (let i = 0; i < mk21Guide.channels.length; i++) {
+    const ch = mk21Guide.channels[i];
+    const programmes = mk21XmltvProgramsFor(ch);
+    let nowTitle = '';
+    if (programmes) {
+      const pick = EpgGuide.pickNowNext(programmes, nowMs);
+      nowTitle = pick.current ? pick.current.title : '';
+    }
+
+    const number = i + 1;
+    const safeName = escapeHtml(ch.name || 'Canal');
+    const logoUrl = String(ch.logo || '').trim();
+    const logo = /^https?:\/\//i.test(logoUrl)
+      ? '<img class="guide-channel-logo" src="' + escapeHtml(logoUrl) + '" alt="" onerror="this.style.display=\'none\'">'
+      : '<div class="guide-channel-logo-placeholder">' + escapeHtml((ch.name || '?').trim().charAt(0).toUpperCase()) + '</div>';
+
+    html += '<li>'
+      + '<button class="guide-channel-btn' + (i === mk21Guide.channelIndex ? ' active' : '') + '" type="button" tabindex="0" data-idx="' + i + '">'
+      + '<span class="guide-channel-number">' + number + '</span>'
+      + logo
+      + '<span class="guide-channel-meta">'
+      + '<span class="guide-channel-name">' + safeName + '</span>'
+      + '<span class="guide-channel-now' + (nowTitle ? ' is-live' : '') + '">' + (nowTitle ? '● ' + escapeHtml(nowTitle) : '— sem programação') + '</span>'
+      + '</span>'
+      + '</button>'
+      + '</li>';
+  }
+  list.innerHTML = html;
+}
+
+/** Renderiza a coluna de programação do canal selecionado. */
+function mk21RenderGuidePrograms() {
+  const box = $('listGuidePrograms');
+  if (!box) return;
+
+  const channel = mk21Guide.channels[mk21Guide.channelIndex];
+  const titleEl = $('txtGuideProgramsTitle');
+  const badge = $('badgeGuidePrograms');
+
+  if (!channel) {
+    if (titleEl) titleEl.textContent = '🔵 Programação';
+    if (badge) badge.textContent = '0';
+    box.innerHTML = '<div class="guide-empty">Selecione um canal para ver a grade.</div>';
+    return;
+  }
+
+  if (titleEl) titleEl.textContent = '🔵 ' + (channel.name || 'Canal');
+  if (badge) badge.textContent = String(mk21Guide.programs.length);
+
+  if (mk21Guide.loading) {
+    box.innerHTML = '<div class="guide-empty">Carregando programação…</div>';
+    return;
+  }
+
+  if (!mk21Guide.programs.length) {
+    box.innerHTML = '<div class="guide-empty">Sem programação disponível para este canal.<br>'
+      + 'O servidor pode não expor EPG (XMLTV) nem guia por canal.</div>';
+    return;
+  }
+
+  const timeline = EpgGuide.buildTimeline(mk21Guide.programs, Date.now());
+  let html = '';
+  for (let i = 0; i < timeline.length; i++) {
+    const p = timeline[i];
+    html += '<div class="guide-prog-row' + (p.isLive ? ' is-live' : '') + (p.isPast ? ' is-past' : '') + '">'
+      + '<div class="guide-prog-time">' + escapeHtml(EpgGuide.formatClock(p.start)) + ' - ' + escapeHtml(EpgGuide.formatClock(p.stop))
+      + (p.isLive ? '<span class="guide-live-badge">NO AR</span>' : '')
+      + '</div>'
+      + '<div class="guide-prog-body">'
+      + '<div class="guide-prog-title">' + escapeHtml(p.title) + '</div>'
+      + (p.desc ? '<div class="guide-prog-desc">' + escapeHtml(p.desc) + '</div>' : '')
+      + (p.isLive ? '<div class="guide-prog-bar"><div class="guide-prog-bar-fill" style="width:' + p.progressPct + '%"></div></div>' : '')
+      + '</div>'
+      + '</div>';
+  }
+  box.innerHTML = html;
+}
+
+/** Seleciona um canal do guia e carrega a grade dele. */
+async function mk21SelectGuideChannel(index, keepFocus) {
+  if (!mk21Guide.channels.length) return;
+  const clamped = Math.max(0, Math.min(mk21Guide.channels.length - 1, index));
+  mk21Guide.channelIndex = clamped;
+  mk21Guide.programIndex = 0;
+  mk21Guide.pane = 'channels';
+
+  // Destaque visual do canal selecionado.
+  const buttons = document.querySelectorAll('#listGuideChannels .guide-channel-btn');
+  for (let i = 0; i < buttons.length; i++) {
+    buttons[i].classList.toggle('active', i === clamped);
+  }
+
+  const channel = mk21Guide.channels[clamped];
+  mk21Guide.loading = true;
+  mk21RenderGuidePrograms();
+
+  try {
+    mk21Guide.programs = await mk21ProgramsForChannel(channel, { withShortEpg: true });
+  } catch (e) {
+    mk21Guide.programs = [];
+  }
+  mk21Guide.loading = false;
+  mk21RenderGuidePrograms();
+  mk21RenderGuideChannels();
+
+  if (!keepFocus) {
+    try {
+      const btn = document.querySelector('#listGuideChannels .guide-channel-btn.active');
+      if (btn) mk21FocusSafe(btn);
+    } catch (e) {}
+  }
+}
+
+/** Move a seleção do guia (setas do D-pad). */
+function mk21GuideMove(direction) {
+  if (!mk21Guide.open) return;
+
+  if (mk21Guide.pane === 'channels') {
+    if (direction === 'up' || direction === 'down') {
+      const delta = direction === 'up' ? -1 : 1;
+      const next = Math.max(0, Math.min(mk21Guide.channels.length - 1, mk21Guide.channelIndex + delta));
+      if (next !== mk21Guide.channelIndex) {
+        mk21SelectGuideChannel(next);
+      } else {
+        mk21GuideScrollFocus();
+      }
+      return;
+    }
+    if (direction === 'right') {
+      mk21Guide.pane = 'programs';
+      mk21GuideScrollFocus();
+      return;
+    }
+    if (direction === 'left') {
+      mk21GuideClose();
+      return;
+    }
+  } else {
+    if (direction === 'up' || direction === 'down') {
+      const delta = direction === 'up' ? -1 : 1;
+      const max = Math.max(0, mk21Guide.programs.length - 1);
+      mk21Guide.programIndex = Math.max(0, Math.min(max, mk21Guide.programIndex + delta));
+      mk21GuideScrollFocus();
+      return;
+    }
+    if (direction === 'left') {
+      mk21Guide.pane = 'channels';
+      mk21GuideScrollFocus();
+      return;
+    }
+    if (direction === 'right') {
+      // Sintoniza o programa em destaque.
+      mk21GuideTune();
+      return;
+    }
+  }
+}
+
+/** Rola a coluna ativa para manter o item selecionado visível. */
+function mk21GuideScrollFocus() {
+  if (mk21Guide.pane === 'channels') {
+    const btn = document.querySelector('#listGuideChannels .guide-channel-btn.active');
+    if (btn && typeof mk21UpdateVisualScrollbarFor === 'function') {
+      mk21UpdateVisualScrollbarFor(btn.closest('ul') || btn.parentElement);
+    }
+  } else {
+    const box = $('listGuidePrograms');
+    if (box) {
+      const row = box.querySelectorAll('.guide-prog-row')[mk21Guide.programIndex];
+      if (row && row.scrollIntoView) {
+        row.scrollIntoView({ block: 'nearest' });
+      }
+    }
+  }
+}
+
+/** OK na coluna de canais: abre a grade; OK na grade: sintoniza o canal. */
+function mk21GuideActivate() {
+  if (!mk21Guide.open) return;
+  if (mk21Guide.pane === 'channels') {
+    mk21Guide.pane = 'programs';
+    mk21GuideScrollFocus();
+    return;
+  }
+  mk21GuideTune();
+}
+
+/** Sintoniza o canal selecionado no guia. */
+function mk21GuideTune() {
+  const channel = mk21Guide.channels[mk21Guide.channelIndex];
+  if (!channel) return;
+  const previous = (mk21Guide.previousType && mk21Guide.previousType !== 'GUIDE') ? mk21Guide.previousType : 'LIVE';
+  mk21CloseGuide();
+  try { switchContentType(previous === 'GUIDE' ? 'LIVE' : previous); } catch (e) {}
+  try { playStream(channel); } catch (e) {}
+  showChannelBanner('▶ ' + (channel.name || 'Canal'));
+}
+
+/** Fecha o guia e devolve o foco para a aba de origem. */
+function mk21GuideClose() {
+  const previous = (mk21Guide.previousType && mk21Guide.previousType !== 'GUIDE') ? mk21Guide.previousType : 'LIVE';
+  mk21CloseGuide();
+  try { switchContentType(previous); } catch (e) {}
+  activeZone = 'channels';
+  try {
+    const tab = $('tabGuide');
+    if (tab) mk21FocusSafe(tab);
+  } catch (e) {}
+}
+
+// --------------------------------------------------------------------------
+// 3. HUD DE ZAPPING (cartão do canal durante a troca)
+// --------------------------------------------------------------------------
+let mk21ZapTimer = null;
+let mk21ZapTickTimer = null;
+let mk21ZapItem = null;
+
+/** Mostra o cartão do canal com a programação atual e a contagem regressiva. */
+function mk21ShowZapHud(item) {
+  const hud = $('zapHud');
+  if (!hud || !item) return;
+
+  const channelNumber = mk21ZapChannelNumber(item);
+  const programmes = mk21XmltvProgramsFor(item) || [];
+  const info = ZapHud.buildZapInfo({
+    channel: item,
+    channelNumber,
+    programs: programmes,
+    nowMs: Date.now()
+  });
+
+  mk21ZapItem = item;
+
+  const numEl = $('zapHudNumber');
+  if (numEl) {
+    numEl.textContent = info.hasNumber ? String(info.number) : '';
+    numEl.style.display = info.hasNumber ? '' : 'none';
+  }
+  const nameEl = $('zapHudName');
+  if (nameEl) nameEl.textContent = info.name;
+
+  const logoEl = $('zapHudLogo');
+  if (logoEl) {
+    const logoUrl = String(info.logo || '').trim();
+    const safe = /^https?:\/\//i.test(logoUrl) ? logoUrl : '';
+    logoEl.src = safe;
+    logoEl.style.display = safe ? '' : 'none';
+  }
+
+  const nowEl = $('zapHudNow');
+  if (nowEl) nowEl.textContent = info.currentTitle || 'Sem programação disponível';
+
+  const fillEl = $('zapHudProgressFill');
+  if (fillEl) fillEl.style.width = info.progressPct + '%';
+
+  const nextEl = $('zapHudNext');
+  if (nextEl) nextEl.textContent = info.nextTitle || '—';
+
+  const cdEl = $('zapHudCountdown');
+  if (cdEl) cdEl.textContent = info.countdownText;
+
+  hud.classList.remove('hidden');
+
+  if (mk21ZapTimer) clearTimeout(mk21ZapTimer);
+  if (mk21ZapTickTimer) clearInterval(mk21ZapTickTimer);
+
+  const shownAt = Date.now();
+  mk21ZapTimer = setTimeout(() => {
+    if (ZapHud.shouldAutoHide(Date.now() - shownAt)) mk21HideZapHud();
+  }, ZapHud.ZAP_AUTO_HIDE_MS + 120);
+
+  // Atualiza a contagem regressiva e a barra de progresso a cada segundo.
+  mk21ZapTickTimer = setInterval(() => {
+    if (!mk21ZapItem) return;
+    const programmesNow = mk21XmltvProgramsFor(mk21ZapItem) || [];
+    const tick = ZapHud.buildZapInfo({
+      channel: mk21ZapItem,
+      channelNumber: mk21ZapChannelNumber(mk21ZapItem),
+      programs: programmesNow,
+      nowMs: Date.now()
+    });
+    const cd = $('zapHudCountdown');
+    if (cd) cd.textContent = tick.countdownText;
+    const fill = $('zapHudProgressFill');
+    if (fill) fill.style.width = tick.progressPct + '%';
+    const nowTitle = $('zapHudNow');
+    if (nowTitle && tick.currentTitle) nowTitle.textContent = tick.currentTitle;
+  }, 1000);
+}
+
+/** Número do canal (LCN) — segue a ordem do catálogo ao vivo. */
+function mk21ZapChannelNumber(item) {
+  const channels = mk21GuideChannels();
+  const key = ZapHud.channelKey(item);
+  for (let i = 0; i < channels.length; i++) {
+    if (ZapHud.channelKey(channels[i]) === key) return i + 1;
+  }
+  return null;
+}
+
+/** Esconde o cartão de zapping. */
+function mk21HideZapHud() {
+  const hud = $('zapHud');
+  if (hud) hud.classList.add('hidden');
+  if (mk21ZapTimer) { clearTimeout(mk21ZapTimer); mk21ZapTimer = null; }
+  if (mk21ZapTickTimer) { clearInterval(mk21ZapTickTimer); mk21ZapTickTimer = null; }
+  mk21ZapItem = null;
+}
+
+// --------------------------------------------------------------------------
+// 4. TECLADO VIRTUAL (D-pad)
+// --------------------------------------------------------------------------
+const mk21Osk = {
+  open: false,
+  state: null,
+  target: null
+};
+
+/** Abre o teclado virtual sobre um campo de texto. */
+function mk21OpenOsk(target) {
+  const overlay = $('oskOverlay');
+  if (!overlay) return;
+
+  const input = target || $('inputSearch');
+  mk21Osk.target = input;
+  mk21Osk.state = OnScreenKeyboard.createState(input ? input.value : '', 'lower');
+  mk21Osk.open = true;
+
+  overlay.classList.remove('hidden');
+  mk21RenderOsk();
+  try { mk21FocusSafe(overlay); } catch (e) {}
+}
+
+/** Fecha o teclado virtual (com ou sem confirmar o texto). */
+function mk21CloseOsk(commit) {
+  const overlay = $('oskOverlay');
+  if (overlay) overlay.classList.add('hidden');
+
+  if (commit && mk21Osk.target) {
+    mk21Osk.target.value = mk21Osk.state ? mk21Osk.state.value : '';
+    try {
+      mk21Osk.target.dispatchEvent(new Event('input', { bubbles: true }));
+    } catch (e) {}
+  }
+
+  mk21Osk.open = false;
+  mk21Osk.state = null;
+
+  if (mk21Osk.target) {
+    try { mk21FocusSafe(mk21Osk.target); } catch (e) {}
+  }
+  activeZone = 'channels';
+}
+
+/** Desenha o teclado na tela. */
+function mk21RenderOsk() {
+  const grid = $('oskGrid');
+  const preview = $('oskPreview');
+  const modeEl = $('oskMode');
+  if (!grid || !mk21Osk.state) return;
+
+  if (preview) preview.textContent = mk21Osk.state.value || '';
+  if (modeEl) modeEl.textContent = OnScreenKeyboard.getModeLabel(mk21Osk.state);
+
+  const rows = OnScreenKeyboard.getRows(mk21Osk.state.mode);
+  let html = '';
+  for (let r = 0; r < rows.length; r++) {
+    html += '<div class="osk-row">';
+    for (let c = 0; c < rows[r].length; c++) {
+      const key = rows[r][c];
+      const isControl = (typeof key === 'object' && key !== null);
+      const label = isControl ? key.label : key;
+      const focused = (r === mk21Osk.state.row && c === mk21Osk.state.col);
+      html += '<button type="button" tabindex="-1" class="osk-key' + (isControl ? ' control' : '') + (focused ? ' focused' : '') + '"'
+        + ' data-row="' + r + '" data-col="' + c + '">' + escapeHtml(label) + '</button>';
+    }
+    html += '</div>';
+  }
+  grid.innerHTML = html;
+}
+
+/** Move a seleção do teclado. */
+function mk21OskMove(direction) {
+  if (!mk21Osk.open || !mk21Osk.state) return;
+  mk21Osk.state = OnScreenKeyboard.moveCursor(mk21Osk.state, direction);
+  mk21RenderOsk();
+}
+
+/** OK: confirma a tecla focada. */
+function mk21OskActivate() {
+  if (!mk21Osk.open || !mk21Osk.state) return;
+  const key = OnScreenKeyboard.getFocusedKey(mk21Osk.state);
+  if (!key) return;
+
+  if (typeof key === 'object' && key.action) {
+    if (key.action === 'MODE') {
+      mk21Osk.state = OnScreenKeyboard.toggleMode(mk21Osk.state);
+    } else if (key.action === 'SPACE') {
+      mk21Osk.state = OnScreenKeyboard.insertChar(mk21Osk.state, ' ');
+    } else if (key.action === 'BACKSPACE') {
+      mk21Osk.state = OnScreenKeyboard.backspace(mk21Osk.state);
+    } else if (key.action === 'CLEAR') {
+      mk21Osk.state = OnScreenKeyboard.clearText(mk21Osk.state);
+    }
+  } else {
+    mk21Osk.state = OnScreenKeyboard.insertChar(mk21Osk.state, key);
+    // Avança para a próxima tecla (como um teclado de celular): sem isso o
+    // próximo OK repetiria a mesma letra.
+    mk21Osk.state = OnScreenKeyboard.advanceCursor(mk21Osk.state);
+  }
+  mk21RenderOsk();
+}
+
+/** Apaga um caractere (tecla Backspace do controle remoto). */
+function mk21OskBackspace() {
+  if (!mk21Osk.open || !mk21Osk.state) return;
+  mk21Osk.state = OnScreenKeyboard.backspace(mk21Osk.state);
+  mk21RenderOsk();
+}
+
 /* ------------------------------------------------------------- inicialização */
 function mk21BindQualityButtons() {
   // 2.6.0: cada botao do player abre o painel de opcoes na aba certa.
diff --git a/smart-tv/mk21-tv/styles.css b/smart-tv/mk21-tv/styles.css
index cb28e31..76d7d15 100644
--- a/smart-tv/mk21-tv/styles.css
+++ b/smart-tv/mk21-tv/styles.css
@@ -199,6 +199,8 @@ html, body {
   height: calc(100vh - 128px);
   min-height: 0;
   background: #090b12;
+  /* ancora do HUD de zapping e do teclado virtual dentro da area de conteudo */
+  position: relative;
 }
 
 .content-section {
@@ -4612,3 +4614,485 @@ div[style*="overflow"], .pane-scroll, .list-container,
   min-height: 68px;
 }
 
+
+/* ==========================================================================
+   GUIA DE PROGRAMAÇÃO (tela dedicada — paridade com o Saimo TV na LG webOS)
+   ========================================================================== */
+#sectionGuide {
+  display: block;
+  padding: 10px 14px;
+  background: #000000;
+  height: 100%;
+  max-height: 100%;
+  overflow: hidden;
+  box-sizing: border-box;
+  width: 100%;
+  max-width: 100%;
+  min-width: 0;
+}
+
+#sectionGuide.hidden {
+  display: none !important;
+}
+
+.guide-layout {
+  display: grid;
+  grid-template-columns: minmax(320px, 1fr) minmax(380px, 1.4fr);
+  gap: 12px;
+  width: 100%;
+  height: 100%;
+  min-height: 0;
+}
+
+.col-guide-channels,
+.col-guide-programs {
+  display: flex;
+  flex-direction: column;
+  min-height: 0;
+  overflow: hidden;
+}
+
+/* Item da coluna de canais */
+.guide-channel-btn {
+  display: flex;
+  align-items: center;
+  gap: 12px;
+  width: 100%;
+  padding: 10px 12px;
+  margin-bottom: 6px;
+  background: #10121a;
+  border: 1px solid var(--border-color);
+  border-left: 4px solid transparent;
+  border-radius: 10px;
+  color: #fff;
+  cursor: pointer;
+  text-align: left;
+  font-family: inherit;
+}
+
+.guide-channel-btn.active {
+  border-left-color: var(--primary-red);
+  background: #1b1520;
+}
+
+.guide-channel-btn:focus,
+.guide-channel-btn.mk21-focus {
+  outline: 3px solid var(--focus-ring);
+  outline-offset: -2px;
+}
+
+.guide-channel-logo {
+  width: 44px;
+  height: 44px;
+  flex-shrink: 0;
+  object-fit: contain;
+  border-radius: 8px;
+  background: #05060a;
+}
+
+.guide-channel-logo-placeholder {
+  width: 44px;
+  height: 44px;
+  flex-shrink: 0;
+  border-radius: 8px;
+  background: #1a1e2c;
+  display: flex;
+  align-items: center;
+  justify-content: center;
+  font-size: 18px;
+  font-weight: 800;
+  color: var(--text-muted);
+}
+
+.guide-channel-meta {
+  min-width: 0;
+  flex: 1;
+}
+
+.guide-channel-name {
+  font-size: 16px;
+  font-weight: 700;
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.guide-channel-now {
+  font-size: 13px;
+  color: var(--text-muted);
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.guide-channel-now.is-live {
+  color: #22c55e;
+}
+
+/* Item da coluna de programação */
+.guide-prog-row {
+  display: flex;
+  gap: 14px;
+  padding: 12px 14px;
+  margin-bottom: 8px;
+  background: #10121a;
+  border: 1px solid var(--border-color);
+  border-left: 4px solid transparent;
+  border-radius: 10px;
+}
+
+.guide-prog-row.is-live {
+  border-left-color: #22c55e;
+  background: #0f1a14;
+}
+
+.guide-prog-row.is-past {
+  opacity: 0.55;
+}
+
+.guide-prog-time {
+  flex-shrink: 0;
+  width: 118px;
+  font-size: 13px;
+  font-weight: 700;
+  color: var(--focus-ring);
+  line-height: 1.4;
+}
+
+.guide-prog-body {
+  min-width: 0;
+  flex: 1;
+}
+
+.guide-prog-title {
+  font-size: 16px;
+  font-weight: 700;
+  margin-bottom: 4px;
+}
+
+.guide-prog-desc {
+  font-size: 13px;
+  color: var(--text-muted);
+  line-height: 1.4;
+  max-height: 2.8em;
+  overflow: hidden;
+}
+
+.guide-prog-bar {
+  margin-top: 8px;
+  height: 5px;
+  border-radius: 3px;
+  background: rgba(255, 255, 255, 0.12);
+  overflow: hidden;
+}
+
+.guide-prog-bar-fill {
+  height: 100%;
+  border-radius: 3px;
+  background: linear-gradient(90deg, #22c55e, #a3e635);
+}
+
+.guide-live-badge {
+  display: inline-block;
+  margin-left: 8px;
+  padding: 1px 7px;
+  border-radius: 5px;
+  background: rgba(34, 197, 94, 0.2);
+  border: 1px solid rgba(34, 197, 94, 0.45);
+  color: #4ade80;
+  font-size: 11px;
+  font-weight: 800;
+  letter-spacing: 0.5px;
+}
+
+.guide-empty {
+  padding: 26px 16px;
+  text-align: center;
+  color: var(--text-muted);
+  font-size: 15px;
+  line-height: 1.5;
+}
+
+/* ==========================================================================
+   HUD DE ZAPPING (cartão mostrado durante a troca de canal)
+   ========================================================================== */
+.zap-hud {
+  position: absolute;
+  top: 16px;
+  left: 16px;
+  right: 16px;
+  z-index: 40;
+  display: flex;
+  align-items: center;
+  gap: 18px;
+  padding: 16px 20px;
+  background: rgba(6, 7, 12, 0.92);
+  border: 1px solid rgba(229, 9, 20, 0.45);
+  border-radius: 14px;
+  box-shadow: 0 10px 34px rgba(0, 0, 0, 0.65);
+  backdrop-filter: blur(6px);
+  -webkit-backdrop-filter: blur(6px);
+  animation: zapHudIn 0.18s ease-out;
+}
+
+.zap-hud.hidden {
+  display: none !important;
+}
+
+@keyframes zapHudIn {
+  from { opacity: 0; transform: translateY(-10px); }
+  to { opacity: 1; transform: translateY(0); }
+}
+
+.zap-hud-logo-wrap {
+  width: 76px;
+  height: 76px;
+  flex-shrink: 0;
+  border-radius: 12px;
+  background: #05060a;
+  border: 1px solid var(--border-color);
+  display: flex;
+  align-items: center;
+  justify-content: center;
+  overflow: hidden;
+}
+
+.zap-hud-logo {
+  max-width: 100%;
+  max-height: 100%;
+  object-fit: contain;
+}
+
+.zap-hud-body {
+  min-width: 0;
+  flex: 1;
+}
+
+.zap-hud-title-row {
+  display: flex;
+  align-items: baseline;
+  gap: 10px;
+  margin-bottom: 6px;
+}
+
+.zap-hud-number {
+  font-size: 18px;
+  font-weight: 800;
+  color: var(--primary-red);
+  font-variant-numeric: tabular-nums;
+}
+
+.zap-hud-name {
+  font-size: 22px;
+  font-weight: 800;
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.zap-hud-now {
+  display: flex;
+  align-items: center;
+  gap: 8px;
+  font-size: 15px;
+  margin-bottom: 8px;
+}
+
+.zap-hud-badge {
+  padding: 2px 8px;
+  border-radius: 5px;
+  background: rgba(34, 197, 94, 0.22);
+  border: 1px solid rgba(34, 197, 94, 0.5);
+  color: #4ade80;
+  font-size: 11px;
+  font-weight: 800;
+  letter-spacing: 0.6px;
+  flex-shrink: 0;
+}
+
+.zap-hud-now-title {
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.zap-hud-progress {
+  height: 6px;
+  border-radius: 3px;
+  background: rgba(255, 255, 255, 0.14);
+  overflow: hidden;
+  margin-bottom: 8px;
+}
+
+.zap-hud-progress-fill {
+  height: 100%;
+  width: 0;
+  border-radius: 3px;
+  background: linear-gradient(90deg, #e50914, #ff7a45);
+  transition: width 0.4s linear;
+}
+
+.zap-hud-next {
+  display: flex;
+  align-items: center;
+  gap: 10px;
+  font-size: 14px;
+  color: var(--text-muted);
+}
+
+.zap-hud-next-label {
+  font-size: 11px;
+  font-weight: 800;
+  letter-spacing: 0.6px;
+  color: var(--focus-ring);
+  flex-shrink: 0;
+}
+
+.zap-hud-next-title {
+  flex: 1;
+  min-width: 0;
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.zap-hud-countdown {
+  flex-shrink: 0;
+  font-variant-numeric: tabular-nums;
+  font-weight: 700;
+  color: #fff;
+}
+
+/* ==========================================================================
+   TECLADO VIRTUAL (D-PAD)
+   ========================================================================== */
+.search-osk-btn {
+  flex-shrink: 0;
+  margin-left: 8px;
+  width: 46px;
+  height: 46px;
+  border-radius: 8px;
+  background: #1a1e2c;
+  border: 1px solid rgba(255, 255, 255, 0.15);
+  color: #fff;
+  font-size: 20px;
+  cursor: pointer;
+}
+
+.search-osk-btn:focus {
+  outline: 3px solid var(--focus-ring);
+  outline-offset: -2px;
+}
+
+.osk-overlay {
+  position: fixed;
+  inset: 0;
+  z-index: 1200;
+  display: flex;
+  align-items: center;
+  justify-content: center;
+  background: rgba(0, 0, 0, 0.82);
+}
+
+.osk-overlay.hidden {
+  display: none !important;
+}
+
+.osk-panel {
+  width: min(1180px, 92vw);
+  padding: 20px 24px 16px;
+  background: #0b0d14;
+  border: 1px solid rgba(229, 9, 20, 0.4);
+  border-radius: 16px;
+  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.7);
+}
+
+.osk-head {
+  display: flex;
+  align-items: center;
+  justify-content: space-between;
+  margin-bottom: 12px;
+}
+
+.osk-title {
+  font-size: 19px;
+  font-weight: 800;
+}
+
+.osk-mode {
+  padding: 4px 12px;
+  border-radius: 8px;
+  background: rgba(229, 9, 20, 0.18);
+  border: 1px solid rgba(229, 9, 20, 0.45);
+  color: #ff8a8a;
+  font-size: 14px;
+  font-weight: 800;
+  text-transform: uppercase;
+}
+
+.osk-preview {
+  min-height: 46px;
+  margin-bottom: 14px;
+  padding: 10px 14px;
+  background: #10121a;
+  border: 1px solid var(--border-color);
+  border-radius: 10px;
+  color: #fff;
+  font-size: 19px;
+  white-space: nowrap;
+  overflow: hidden;
+  text-overflow: ellipsis;
+}
+
+.osk-preview:empty::before {
+  content: 'Digite para buscar...';
+  color: var(--text-muted);
+}
+
+.osk-grid {
+  display: flex;
+  flex-direction: column;
+  gap: 8px;
+}
+
+.osk-row {
+  display: flex;
+  gap: 8px;
+  justify-content: center;
+}
+
+.osk-key {
+  width: 92px;
+  height: 62px;
+  flex: 0 0 auto;
+  display: flex;
+  align-items: center;
+  justify-content: center;
+  background: #171a26;
+  border: 1px solid rgba(255, 255, 255, 0.1);
+  border-radius: 10px;
+  color: #fff;
+  font-size: 21px;
+  font-weight: 600;
+  cursor: pointer;
+  font-family: inherit;
+}
+
+.osk-key.control {
+  background: #20242f;
+  color: var(--focus-ring);
+  font-size: 19px;
+}
+
+.osk-key.focused {
+  outline: 3px solid var(--focus-ring);
+  outline-offset: -2px;
+  background: #2a1a1f;
+}
+
+.osk-hint {
+  margin-top: 12px;
+  text-align: center;
+  font-size: 13px;
+  color: var(--text-muted);
+}
diff --git a/smart-tv/tests/epg-guide.test.js b/smart-tv/tests/epg-guide.test.js
new file mode 100644
index 0000000..8201fe7
--- /dev/null
+++ b/smart-tv/tests/epg-guide.test.js
@@ -0,0 +1,197 @@
+const test = require('node:test');
+const assert = require('node:assert/strict');
+const EpgGuide = require('../mk21-tv/core/EpgGuide.js');
+
+const HOUR = 3600000;
+const MIN = 60000;
+
+// 2026-03-10 20:00:00 -0300 -> epoch ms (referência fixa dos testes)
+const T20 = Date.UTC(2026, 2, 10, 23, 0, 0);
+
+test('toTimestampMs converte epoch em segundos, epoch em ms, XMLTV e ISO', () => {
+  assert.equal(EpgGuide.toTimestampMs(1773200000), 1773200000000);
+  assert.equal(EpgGuide.toTimestampMs(1773200000000), 1773200000000);
+  assert.equal(EpgGuide.toTimestampMs('1773200000'), 1773200000000);
+  assert.equal(EpgGuide.toTimestampMs('1773200000000'), 1773200000000);
+
+  // XMLTV com fuso explicito (-0300) -> 20:00 local = 23:00 UTC
+  assert.equal(EpgGuide.toTimestampMs('20260310200000 -0300'), T20);
+  // ISO sem fuso e assumido como UTC
+  assert.equal(EpgGuide.toTimestampMs('2026-03-10T23:00:00'), T20);
+
+  assert.ok(Number.isNaN(EpgGuide.toTimestampMs('')));
+  assert.ok(Number.isNaN(EpgGuide.toTimestampMs(null)));
+  assert.ok(Number.isNaN(EpgGuide.toTimestampMs('lixo')));
+});
+
+test('normalizeProgram aceita payload Xtream e devolve start/stop em ms', () => {
+  const p = EpgGuide.normalizeProgram({
+    title: 'Jornal Nacional',
+    description: 'Principal jornal da noite',
+    start_timestamp: String(T20 / 1000),
+    stop_timestamp: String((T20 + HOUR) / 1000)
+  });
+
+  assert.ok(p);
+  assert.equal(p.title, 'Jornal Nacional');
+  assert.equal(p.desc, 'Principal jornal da noite');
+  assert.equal(p.start, T20);
+  assert.equal(p.stop, T20 + HOUR);
+  assert.equal(p.durationMs, HOUR);
+});
+
+test('normalizeProgram rejeita programas sem janela válida', () => {
+  assert.equal(EpgGuide.normalizeProgram(null), null);
+  assert.equal(EpgGuide.normalizeProgram({ title: 'x' }), null);
+  // stop antes do start
+  assert.equal(EpgGuide.normalizeProgram({ title: 'x', start: T20, stop: T20 - 1000 }), null);
+  // stop igual ao start
+  assert.equal(EpgGuide.normalizeProgram({ title: 'x', start: T20, stop: T20 }), null);
+});
+
+test('normalizePrograms ordena por horário e descarta os inválidos', () => {
+  const list = EpgGuide.normalizePrograms([
+    { title: 'B', start: T20 + HOUR, stop: T20 + 2 * HOUR },
+    { title: 'invalido' },
+    { title: 'A', start: T20, stop: T20 + HOUR }
+  ]);
+
+  assert.equal(list.length, 2);
+  assert.equal(list[0].title, 'A');
+  assert.equal(list[1].title, 'B');
+});
+
+test('buildTimeline marca o programa no ar e calcula o progresso', () => {
+  const now = T20 + 30 * MIN;
+  const timeline = EpgGuide.buildTimeline([
+    { title: 'Anterior', start: T20 - HOUR, stop: T20 },
+    { title: 'Agora', start: T20, stop: T20 + HOUR },
+    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+  ], now);
+
+  assert.equal(timeline.length, 3);
+  assert.equal(timeline[0].isPast, true);
+  assert.equal(timeline[0].isLive, false);
+  assert.equal(timeline[1].isLive, true);
+  assert.equal(timeline[1].progressPct, 50);
+  assert.equal(timeline[2].isLive, false);
+  assert.equal(timeline[2].isPast, false);
+});
+
+test('pickNowNext devolve o programa atual e o seguinte', () => {
+  const now = T20 + 30 * MIN;
+  const { current, next } = EpgGuide.pickNowNext([
+    { title: 'Agora', start: T20, stop: T20 + HOUR },
+    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+  ], now);
+
+  assert.equal(current.title, 'Agora');
+  assert.equal(next.title, 'Depois');
+});
+
+test('pickNowNext sem nada no ar devolve o próximo futuro', () => {
+  const now = T20 - 2 * HOUR;
+  const { current, next } = EpgGuide.pickNowNext([
+    { title: 'Agora', start: T20, stop: T20 + HOUR },
+    { title: 'Depois', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+  ], now);
+
+  assert.equal(current.title, 'Agora');
+  assert.equal(next.title, 'Depois');
+});
+
+test('pickNowNext com grade toda vencida devolve o ultimo e nenhum proximo', () => {
+  const now = T20 + 10 * HOUR;
+  const { current, next } = EpgGuide.pickNowNext([
+    { title: 'Antigo', start: T20 - 3 * HOUR, stop: T20 - 2 * HOUR }
+  ], now);
+
+  assert.equal(current.title, 'Antigo');
+  assert.equal(next, null);
+});
+
+test('pickNowNext com lista vazia devolve null nos dois lados', () => {
+  const { current, next } = EpgGuide.pickNowNext([], T20);
+  assert.equal(current, null);
+  assert.equal(next, null);
+});
+
+test('mergeProgramSources junta XMLTV + Xtream sem duplicar o mesmo programa', () => {
+  const xmltv = [{ title: 'Jornal', start: T20, stop: T20 + HOUR }];
+  const xtream = [
+    { title: 'Jornal', start: T20 + 1000, stop: T20 + HOUR + 1000 }, // mesmo programa
+    { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+  ];
+
+  const merged = EpgGuide.mergeProgramSources(xmltv, xtream);
+  assert.equal(merged.length, 2);
+  assert.equal(merged[0].title, 'Jornal');
+  assert.equal(merged[1].title, 'Esporte');
+});
+
+test('mergeProgramSources completa a sinopse ausente com a de outra fonte', () => {
+  const merged = EpgGuide.mergeProgramSources(
+    [{ title: 'Jornal', start: T20, stop: T20 + HOUR }],
+    [{ title: 'Jornal', start: T20, stop: T20 + HOUR, description: 'Com detalhes' }]
+  );
+
+  assert.equal(merged.length, 1);
+  assert.equal(merged[0].desc, 'Com detalhes');
+});
+
+test('mergeProgramSources aceita fontes vazias e ignore nao-listas', () => {
+  const merged = EpgGuide.mergeProgramSources(null, undefined, [], [
+    { title: 'A', start: T20, stop: T20 + HOUR }
+  ]);
+  assert.equal(merged.length, 1);
+});
+
+test('minutesRemaining conta os minutos que faltam para o fim', () => {
+  const now = T20 + 30 * MIN;
+  assert.equal(EpgGuide.minutesRemaining({ start: T20, stop: T20 + HOUR }, now), 30);
+  // fora da janela -> 0
+  assert.equal(EpgGuide.minutesRemaining({ start: T20 - 2 * HOUR, stop: T20 - HOUR }, now), 0);
+  assert.equal(EpgGuide.minutesRemaining(null, now), 0);
+});
+
+test('formatCountdown formata mm:ss e hh:mm:ss', () => {
+  assert.equal(EpgGuide.formatCountdown(65 * 1000), '01:05');
+  assert.equal(EpgGuide.formatCountdown(3725 * 1000), '1:02:05');
+  assert.equal(EpgGuide.formatCountdown(-1), '--:--');
+  assert.equal(EpgGuide.formatCountdown(NaN), '--:--');
+});
+
+test('formatClock devolve HH:MM do horario local', () => {
+  const d = new Date(2026, 2, 10, 20, 15, 0);
+  assert.equal(EpgGuide.formatClockDate(d), '20:15');
+  assert.equal(EpgGuide.formatClock(NaN), '--:--');
+});
+
+test('summarizeChannel monta o resumo NO AR / A SEGUIR com contagem regressiva', () => {
+  const now = T20 + 30 * MIN;
+  const summary = EpgGuide.summarizeChannel(
+    { name: 'Globo', logo: 'https://exemplo/globo.png', group: 'Abertos' },
+    [
+      { title: 'Jornal', start: T20, stop: T20 + HOUR },
+      { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+    ],
+    now
+  );
+
+  assert.equal(summary.name, 'Globo');
+  assert.equal(summary.currentTitle, 'Jornal');
+  assert.equal(summary.nextTitle, 'Esporte');
+  assert.equal(summary.isLive, true);
+  assert.equal(summary.progressPct, 50);
+  assert.equal(summary.countdownMs, 30 * MIN);
+  assert.equal(summary.countdownText, '30:00');
+});
+
+test('summarizeChannel sem EPG devolve campos vazios sem quebrar', () => {
+  const summary = EpgGuide.summarizeChannel({ name: 'Canal X' }, [], T20);
+  assert.equal(summary.name, 'Canal X');
+  assert.equal(summary.currentTitle, '');
+  assert.equal(summary.nextTitle, '');
+  assert.equal(summary.countdownText, '--:--');
+  assert.equal(summary.isLive, false);
+});
diff --git a/smart-tv/tests/on-screen-keyboard.test.js b/smart-tv/tests/on-screen-keyboard.test.js
new file mode 100644
index 0000000..939e768
--- /dev/null
+++ b/smart-tv/tests/on-screen-keyboard.test.js
@@ -0,0 +1,184 @@
+const test = require('node:test');
+const assert = require('node:assert/strict');
+const Keyboard = require('../mk21-tv/core/OnScreenKeyboard.js');
+
+test('getRows devolve o grid do modo pedido com a linha de controle no fim', () => {
+  const lower = Keyboard.getRows('lower');
+  assert.equal(lower.length, 5);
+  assert.equal(lower[0][0], 'q');
+  assert.equal(lower[0][9], 'p');
+  assert.equal(lower[1][9], 'ç');
+  // ultima linha = controles (MODE, SPACE, BACKSPACE, CLEAR)
+  assert.equal(lower[4][0].action, 'MODE');
+  assert.equal(lower[4][1].action, 'SPACE');
+  assert.equal(lower[4][2].action, 'BACKSPACE');
+  assert.equal(lower[4][3].action, 'CLEAR');
+});
+
+test('getRows em modo upper devolve as letras maiusculas', () => {
+  const upper = Keyboard.getRows('upper');
+  assert.equal(upper[0][0], 'Q');
+  assert.equal(upper[1][9], 'Ç');
+  // os acentos tambem ganham caixa alta (busca PT-BR precisa de "Á")
+  assert.equal(upper[3][0], 'Á');
+});
+
+test('getRows com modo invalido cai no lower', () => {
+  assert.equal(Keyboard.getRows('inexistente')[0][0], 'q');
+});
+
+test('createState guarda texto, cursor e modo', () => {
+  const state = Keyboard.createState('abc');
+  assert.equal(state.value, 'abc');
+  assert.equal(state.cursor, 3);
+  assert.equal(state.mode, 'lower');
+
+  const empty = Keyboard.createState(null, 'upper');
+  assert.equal(empty.value, '');
+  assert.equal(empty.cursor, 0);
+  assert.equal(empty.mode, 'upper');
+});
+
+test('moveCursor navega no grid e nao deixa a coluna fora da linha', () => {
+  let state = Keyboard.createState('');
+  state = Keyboard.moveCursor(state, 'right');
+  assert.equal(state.col, 1);
+
+  // desce 4 linhas -> vai para a linha de controle (que tem 4 colunas)
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  assert.equal(state.row, 4);
+  // a coluna e preservada, mas nunca passa da largura da linha
+  assert.equal(state.col, 1);
+
+  // empurra a coluna alem do fim da linha de controle -> trava na ultima
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  assert.equal(state.col, 3);
+
+  // desce de novo -> fica na ultima linha (nao ha wrap)
+  state = Keyboard.moveCursor(state, 'down');
+  assert.equal(state.row, 4);
+  // e volta para a primeira linha quando sobe ate o topo
+  state = Keyboard.moveCursor(state, 'up');
+  state = Keyboard.moveCursor(state, 'up');
+  state = Keyboard.moveCursor(state, 'up');
+  state = Keyboard.moveCursor(state, 'up');
+  assert.equal(state.row, 0);
+});
+
+test('moveCursor com esquerda na primeira coluna trava no zero', () => {
+  const state = Keyboard.moveCursor(Keyboard.createState(''), 'left');
+  assert.equal(state.col, 0);
+});
+
+test('getFocusedKey devolve a tecla focada e null fora do grid', () => {
+  const state = Keyboard.createState('');
+  assert.equal(Keyboard.getFocusedKey(state), 'q');
+  const onControl = Keyboard.moveCursor(state, 'down');
+  assert.equal(typeof Keyboard.getFocusedKey(onControl), 'string');
+  assert.equal(Keyboard.getFocusedKey(null), null);
+});
+
+test('insertChar insere na posicao do cursor e avanca', () => {
+  let state = Keyboard.createState('ac');
+  state = Keyboard.moveTextCursor(state, -1); // cursor entre "a" e "c"
+  state = Keyboard.insertChar(state, 'b');
+  assert.equal(state.value, 'abc');
+  assert.equal(state.cursor, 2);
+});
+
+test('backspace apaga o caractere anterior ao cursor', () => {
+  let state = Keyboard.createState('abc');
+  state = Keyboard.backspace(state);
+  assert.equal(state.value, 'ab');
+  assert.equal(state.cursor, 2);
+
+  const empty = Keyboard.backspace(Keyboard.createState(''));
+  assert.equal(empty.value, '');
+  assert.equal(empty.cursor, 0);
+});
+
+test('clearText apaga tudo', () => {
+  const state = Keyboard.clearText(Keyboard.createState('abc'));
+  assert.equal(state.value, '');
+  assert.equal(state.cursor, 0);
+});
+
+test('toggleMode alterna lower -> upper -> numbers -> symbols -> lower', () => {
+  let state = Keyboard.createState('');
+  state = Keyboard.toggleMode(state);
+  assert.equal(state.mode, 'upper');
+  state = Keyboard.toggleMode(state);
+  assert.equal(state.mode, 'numbers');
+  state = Keyboard.toggleMode(state);
+  assert.equal(state.mode, 'symbols');
+  state = Keyboard.toggleMode(state);
+  assert.equal(state.mode, 'lower');
+});
+
+test('toggleMode mantem o cursor de texto e reinicia a coluna', () => {
+  let state = Keyboard.createState('abc');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.toggleMode(state);
+  assert.equal(state.value, 'abc');
+  assert.equal(state.cursor, 3);
+  assert.equal(state.col, 0);
+});
+
+test('moveTextCursor respeita os limites do texto', () => {
+  let state = Keyboard.createState('ab');
+  state = Keyboard.moveTextCursor(state, -10);
+  assert.equal(state.cursor, 0);
+  state = Keyboard.moveTextCursor(state, 10);
+  assert.equal(state.cursor, 2);
+});
+
+test('getModeLabel devolve o rotulo do botao de modo', () => {
+  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'lower')), 'abc');
+  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'upper')), 'ABC');
+  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'numbers')), '123');
+  assert.equal(Keyboard.getModeLabel(Keyboard.createState('', 'symbols')), '#+=');
+  assert.equal(Keyboard.getModeLabel(null), 'abc');
+});
+
+test('advanceCursor anda para a proxima tecla e quebra a linha no fim da fileira', () => {
+  let state = Keyboard.createState('');
+  state = Keyboard.advanceCursor(state);
+  assert.equal(state.col, 1);
+  assert.equal(state.row, 0);
+
+  // ultima coluna da 1a linha -> proxima linha, primeira coluna
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  assert.equal(state.col, 9);
+  state = Keyboard.advanceCursor(state);
+  assert.equal(state.row, 1);
+  assert.equal(state.col, 0);
+});
+
+test('advanceCursor na ultima linha volta para o inicio do teclado', () => {
+  let state = Keyboard.createState('');
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  state = Keyboard.moveCursor(state, 'down');
+  assert.equal(state.row, 4);
+  // vai ate a ultima tecla da linha de controle (a de CLEAR)
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  state = Keyboard.moveCursor(state, 'right');
+  assert.equal(state.col, 3);
+  state = Keyboard.advanceCursor(state);
+  assert.equal(state.row, 0);
+  assert.equal(state.col, 0);
+});
diff --git a/smart-tv/tests/tv-guide-integration.test.js b/smart-tv/tests/tv-guide-integration.test.js
new file mode 100644
index 0000000..ff4f8d6
--- /dev/null
+++ b/smart-tv/tests/tv-guide-integration.test.js
@@ -0,0 +1,355 @@
+#!/usr/bin/env node
+/**
+ * MK21 Play — Teste de integração (Chromium/Playwright) das funcionalidades
+ * portadas do Saimo TV na Smart TV:
+ *
+ *   1. login contra um servidor IPTV simulado (Xtream + XMLTV);
+ *   2. Guia de Programação com grade REAL vinda do XMLTV;
+ *   3. HUD de zapping com "NO AR" / "A SEGUIR" e contagem regressiva;
+ *   4. teclado virtual navegável pelo D-pad.
+ *
+ * Uso: node smart-tv/tests/tv-guide-integration.test.js
+ * Requer: npx playwright install chromium (já instalado no CI da imagem).
+ */
+
+const test = require('node:test');
+const assert = require('node:assert/strict');
+const http = require('node:http');
+const path = require('node:path');
+const fs = require('node:fs');
+
+// O Playwright é opcional: o CI não instala navegador. Sem ele, o teste é
+// ignorado (e não quebrado). Para rodar localmente:
+//   npm i -D playwright && npx playwright install chromium
+let chromium = null;
+try {
+  chromium = require('playwright').chromium;
+} catch (e) {
+  chromium = null;
+}
+
+const TV_DIR = path.join(__dirname, '..', 'mk21-tv');
+const BASE_URL = 'http://tocombox21.cyou';
+
+const MIME = {
+  '.html': 'text/html; charset=utf-8',
+  '.js': 'text/javascript; charset=utf-8',
+  '.css': 'text/css; charset=utf-8',
+  '.json': 'application/json; charset=utf-8',
+  '.png': 'image/png',
+  '.svg': 'image/svg+xml'
+};
+
+// --- Grade EPG simulada (formato XMLTV) ------------------------------------
+// A grade é montada RELATIVA ao agora: o primeiro programa está no ar e o
+// segundo começa na próxima hora — é o que o HUD de zapping espera encontrar.
+function buildXmltv() {
+  const base = Math.floor(Date.now() / 3600000) * 3600000;
+  const fmt = (ms) => {
+    const d = new Date(ms);
+    const p = (n, w = 2) => String(n).padStart(w, '0');
+    return `${p(d.getUTCFullYear(), 4)}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())} +0000`;
+  };
+  const programme = (ch, startMs, stopMs, title, desc) =>
+    `  <programme start="${fmt(startMs)}" stop="${fmt(stopMs)}" channel="${ch}">` +
+    `<title lang="pt">${title}</title><desc>${desc}</desc><category>Variedades</category></programme>`;
+
+  return `<?xml version="1.0" encoding="UTF-8"?>
+<tv generator-info-name="mock">
+  <channel id="globo.br"><display-name>Globo</display-name></channel>
+  <channel id="sbt.br"><display-name>SBT</display-name></channel>
+${programme('globo.br', base, base + 3600000, 'Jornal Nacional', 'O principal jornal da noite')}
+${programme('globo.br', base + 3600000, base + 7200000, 'Globo Esporte', 'Futebol ao vivo')}
+${programme('sbt.br', base, base + 1800000, 'Programa do Ratinho', ' humor da noite')}
+</tv>`;
+}
+
+// --- Servidor IPTV simulado ------------------------------------------------
+function startMockServer() {
+  const xmltv = buildXmltv();
+  const server = http.createServer((req, res) => {
+    const url = new URL(req.url, BASE_URL);
+    const action = url.searchParams.get('action') || '';
+
+    const json = (obj) => {
+      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
+      res.end(JSON.stringify(obj));
+    };
+
+    if (url.pathname.endsWith('/xmltv.php')) {
+      res.writeHead(200, { 'Content-Type': 'application/xml', 'Access-Control-Allow-Origin': '*' });
+      res.end(xmltv);
+      return;
+    }
+
+    if (url.pathname.endsWith('/player_api.php')) {
+      if (action === 'get_live_categories') {
+        json([{ category_id: '1', category_name: 'Abertos' }, { category_id: '2', category_name: 'Variedades' }]);
+        return;
+      }
+      if (action === 'get_live_streams') {
+        json([
+          // Globo sem epg_channel_id: o app amarra pelo <display-name> do XMLTV.
+          { stream_id: 101, name: 'Globo', category_id: '1', stream_icon: 'http://logo/globo.png', tv_archive: 0 },
+          // SBT com epg_channel_id: o app amarra pelo id exato do XMLTV.
+          { stream_id: 102, name: 'SBT', category_id: '2', stream_icon: 'http://logo/sbt.png', epg_channel_id: 'sbt.br', tv_archive: 0 }
+        ]);
+        return;
+      }
+      if (action === 'get_vod_streams') { json([]); return; }
+      if (action === 'get_series') { json([]); return; }
+      if (action === 'get_short_epg') {
+        json({ epg_listings: [] });
+        return;
+      }
+      json({ user_info: { auth: 1, status: 'Active', username: 'demo', exp_date: '2030-01-01 00:00:00' }, server_info: { url: BASE_URL } });
+      return;
+    }
+
+    if (url.pathname.endsWith('/get.php')) {
+      res.writeHead(200, { 'Content-Type': 'audio/x-mpegurl', 'Access-Control-Allow-Origin': '*' });
+      res.end('#EXTM3U\n');
+      return;
+    }
+
+    res.writeHead(404, { 'Access-Control-Allow-Origin': '*' });
+    res.end('not found');
+  });
+  return new Promise((resolve) => {
+    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port }));
+  });
+}
+
+// --- Servidor de arquivos estáticos (app da TV) ----------------------------
+function startStaticServer() {
+  const server = http.createServer((req, res) => {
+    let rel = decodeURIComponent(req.url.split('?')[0]);
+    if (rel === '/') rel = '/index.html';
+    const file = path.join(TV_DIR, rel);
+    if (!file.startsWith(TV_DIR) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
+      res.writeHead(404);
+      res.end('not found');
+      return;
+    }
+    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
+    fs.createReadStream(file).pipe(res);
+  });
+  return new Promise((resolve) => {
+    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port }));
+  });
+}
+
+test('Guia de Programação, HUD de zapping e teclado virtual funcionam na Smart TV', async (t) => {
+  if (!chromium) {
+    t.skip('Playwright não instalado (opcional): npm i -D playwright && npx playwright install chromium');
+    return;
+  }
+
+  const mock = await startMockServer();
+  const statics = await startStaticServer();
+  const browser = await chromium.launch({ args: ['--no-sandbox'] });
+  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
+  const page = await context.newPage();
+
+  const errors = [];
+  page.on('pageerror', (err) => errors.push(String(err)));
+  page.setDefaultTimeout(25000);
+
+  // Redireciona o domínio do servidor padrão para o mock local.
+  await page.route('**://tocombox21.cyou/**', async (route) => {
+    const target = new URL(route.request().url());
+    const rewritten = `http://127.0.0.1:${mock.port}${target.pathname}${target.search}`;
+    try {
+      const response = await route.fetch({ url: rewritten });
+      await route.fulfill({ response });
+    } catch (e) {
+      await route.fulfill({ status: 502, body: 'mock offline' });
+    }
+  });
+
+  // Assets externos (FontAwesome / CDN): o app usa document.write no <head>,
+  // então abortar a requisição quebraria o parse do documento. Servimos stubs.
+  await page.route('**://cdnjs.cloudflare.com/**', (route) =>
+    route.fulfill({ status: 200, contentType: 'text/css', body: '/* stub */' }));
+  await page.route('**://cdn.jsdelivr.net/**', (route) =>
+    route.fulfill({ status: 200, contentType: 'text/javascript', body: '/* stub */' }));
+
+  await page.goto(`http://127.0.0.1:${statics.port}/index.html`, { waitUntil: 'domcontentloaded' });
+
+  // --- 1. Login contra o servidor simulado --------------------------------
+  await page.fill('#user-input', 'demo');
+  await page.fill('#pass-input', 'demo');
+  await page.click('#btn-login');
+
+  await page.waitForFunction(() => {
+    const el = document.getElementById('home-container');
+    return el && el.style.display !== 'none';
+  }, { timeout: 20000 });
+
+  // Espera o catálogo ao vivo terminar de carregar (a tela principal abre antes).
+  await page.waitForFunction(() => {
+    const list = document.getElementById('listItems');
+    return list && list.querySelectorAll('.list-item-btn').length >= 2;
+  }, { timeout: 20000 });
+
+  // --- 2. Guia de Programação com grade real ------------------------------
+  const guide = await page.evaluate(async () => {
+    await window.mk21App.loadXmltv(true);
+    window.mk21App.openGuide();
+    await new Promise((r) => setTimeout(r, 900));
+
+    const channelList = document.getElementById('listGuideChannels');
+    const programList = document.getElementById('listGuidePrograms');
+    return {
+      guideVisible: !document.getElementById('sectionGuide').classList.contains('hidden'),
+      channelCount: channelList ? channelList.querySelectorAll('.guide-channel-btn').length : 0,
+      channelNames: channelList ? Array.from(channelList.querySelectorAll('.guide-channel-name')).map((n) => n.textContent.trim()) : [],
+      nowLabels: channelList ? Array.from(channelList.querySelectorAll('.guide-channel-now')).map((n) => n.textContent.trim()) : [],
+      programTitles: programList ? Array.from(programList.querySelectorAll('.guide-prog-title')).map((n) => n.textContent.trim()) : [],
+      liveRows: programList ? programList.querySelectorAll('.guide-prog-row.is-live').length : 0
+    };
+  });
+
+  assert.equal(guide.guideVisible, true, 'a seção do Guia deve ficar visível');
+  assert.ok(guide.channelCount >= 2, `esperava 2+ canais no guia, veio ${guide.channelCount}`);
+  assert.ok(guide.channelNames.includes('Globo'), 'o canal Globo deve aparecer na coluna de canais');
+  assert.ok(
+    guide.nowLabels.some((t) => t.includes('Jornal Nacional')),
+    `a coluna de canais deve mostrar o que está no ar, veio: ${JSON.stringify(guide.nowLabels)}`
+  );
+  assert.ok(
+    guide.programTitles.includes('Jornal Nacional') && guide.programTitles.includes('Globo Esporte'),
+    `a grade do canal deve vir do XMLTV real, veio: ${JSON.stringify(guide.programTitles)}`
+  );
+  assert.ok(guide.liveRows >= 1, 'o programa no ar deve receber o destaque "is-live"');
+
+  // --- 3. Navegação D-pad dentro do Guia ----------------------------------
+  const guideNav = await page.evaluate(async () => {
+    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
+    press('ArrowRight', 39);   // vai para a coluna de programação
+    await new Promise((r) => setTimeout(r, 120));
+    const pane = window.mk21App.guideState().pane;
+    press('ArrowDown', 40);
+    await new Promise((r) => setTimeout(r, 120));
+    const programIndex = window.mk21App.guideState().programIndex;
+    return { pane, programIndex };
+  });
+
+  assert.equal(guideNav.pane, 'programs', 'a seta para a direita deve trocar de coluna');
+  assert.equal(guideNav.programIndex, 1, 'a seta para baixo deve andar na grade de programas');
+
+  // --- 4. VOLTAR fecha o Guia ---------------------------------------------
+  const closed = await page.evaluate(async () => {
+    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));
+    await new Promise((r) => setTimeout(r, 250));
+    return {
+      guideOpen: window.mk21App.guideState().open,
+      guideHidden: document.getElementById('sectionGuide').classList.contains('hidden'),
+      unifiedVisible: !document.getElementById('sectionUnified').classList.contains('hidden')
+    };
+  });
+
+  assert.equal(closed.guideOpen, false, 'o Guia deve fechar no VOLTAR');
+  assert.equal(closed.guideHidden, true, 'a seção do Guia deve ficar oculta');
+  assert.equal(closed.unifiedVisible, true, 'a tela principal deve voltar');
+
+  // --- 4b. Sanidade: a navegação normal do app continua funcionando -------
+  const afterClose = await page.evaluate(async () => {
+    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
+    const items = document.getElementById('listItems');
+    const before = items ? items.querySelectorAll('.list-item-btn').length : 0;
+    press('ArrowDown', 40);
+    await new Promise((r) => setTimeout(r, 150));
+    press('Enter', 13);
+    await new Promise((r) => setTimeout(r, 400));
+    return {
+      items: before,
+      guideStillClosed: !window.mk21App.guideState().open,
+      oskClosed: document.getElementById('oskOverlay').classList.contains('hidden')
+    };
+  });
+
+  assert.ok(afterClose.items >= 2, 'a lista de itens deve continuar populada');
+  assert.equal(afterClose.guideStillClosed, true, 'o Guia não pode reabrir sozinho');
+  assert.equal(afterClose.oskClosed, true, 'o teclado virtual não pode abrir sozinho');
+
+  // --- 5. HUD de zapping ---------------------------------------------------
+  const zap = await page.evaluate(async () => {
+    const channel = window.mk21App.guideState().channels[0];
+    window.mk21App.showZapHud(channel);
+    await new Promise((r) => setTimeout(r, 250));
+    const hud = document.getElementById('zapHud');
+    return {
+      visible: !hud.classList.contains('hidden'),
+      name: document.getElementById('zapHudName').textContent.trim(),
+      now: document.getElementById('zapHudNow').textContent.trim(),
+      next: document.getElementById('zapHudNext').textContent.trim(),
+      countdown: document.getElementById('zapHudCountdown').textContent.trim(),
+      progress: document.getElementById('zapHudProgressFill').style.width
+    };
+  });
+
+  assert.equal(zap.visible, true, 'o HUD de zapping deve aparecer');
+  assert.equal(zap.name, 'Globo', 'o HUD deve mostrar o nome do canal');
+  assert.ok(zap.now.includes('Jornal Nacional'), `o HUD deve mostrar o NO AR, veio: ${zap.now}`);
+  assert.ok(zap.next.includes('Globo Esporte'), `o HUD deve mostrar o A SEGUIR, veio: ${zap.next}`);
+  assert.notEqual(zap.countdown, '--:--', 'o HUD deve mostrar a contagem regressiva');
+  assert.notEqual(zap.progress, '', 'o HUD deve mostrar a barra de progresso');
+
+  // --- 6. Teclado virtual (D-pad) ----------------------------------------
+  const osk = await page.evaluate(async () => {
+    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
+    window.mk21App.openOsk(document.getElementById('inputSearch'));
+    await new Promise((r) => setTimeout(r, 200));
+
+    const keys = document.querySelectorAll('#oskGrid .osk-key');
+    const open = !document.getElementById('oskOverlay').classList.contains('hidden');
+
+    // "g" fica na 2a linha, 5a coluna (q w e r t y u i o p / a s d f g ...)
+    press('ArrowDown', 40);
+    press('ArrowRight', 39);
+    press('ArrowRight', 39);
+    press('ArrowRight', 39);
+    press('ArrowRight', 39);
+    press('Enter', 13);
+    await new Promise((r) => setTimeout(r, 120));
+    const afterG = document.getElementById('oskPreview').textContent.trim();
+
+    press('Enter', 13); // agora a tecla seguinte ("h") -> "gh"
+    await new Promise((r) => setTimeout(r, 120));
+    const afterH = document.getElementById('oskPreview').textContent.trim();
+
+    // Backspace apaga o último caractere
+    press('Backspace', 8);
+    await new Promise((r) => setTimeout(r, 120));
+    const afterBackspace = document.getElementById('oskPreview').textContent.trim();
+
+    // Fecha com VOLTAR sem confirmar
+    press('Escape', 27);
+    await new Promise((r) => setTimeout(r, 150));
+
+    return {
+      open,
+      keyCount: keys.length,
+      afterG,
+      afterH,
+      afterBackspace,
+      closed: document.getElementById('oskOverlay').classList.contains('hidden'),
+      searchValue: document.getElementById('inputSearch').value
+    };
+  });
+
+  assert.equal(osk.open, true, 'o teclado virtual deve abrir');
+  assert.ok(osk.keyCount >= 40, `o teclado deve ter todas as teclas, veio ${osk.keyCount}`);
+  assert.equal(osk.afterG, 'g', `a tecla "g" deve ser digitada, veio "${osk.afterG}"`);
+  assert.equal(osk.afterH, 'gh', `a segunda tecla deve ser digitada em sequência, veio "${osk.afterH}"`);
+  assert.equal(osk.afterBackspace, 'g', 'o backspace deve apagar o último caractere digitado');
+  assert.equal(osk.closed, true, 'o VOLTAR deve fechar o teclado virtual');
+  assert.equal(osk.searchValue, '', 'sem confirmar, o campo de busca não deve ser alterado');
+
+  assert.deepEqual(errors, [], 'nenhum erro de JS deve ocorrer durante o fluxo');
+
+  await browser.close();
+  mock.server.close();
+  statics.server.close();
+});
diff --git a/smart-tv/tests/zap-hud.test.js b/smart-tv/tests/zap-hud.test.js
new file mode 100644
index 0000000..437d274
--- /dev/null
+++ b/smart-tv/tests/zap-hud.test.js
@@ -0,0 +1,91 @@
+const test = require('node:test');
+const assert = require('node:assert/strict');
+const ZapHud = require('../mk21-tv/core/ZapHud.js');
+
+const HOUR = 3600000;
+const MIN = 60000;
+const T20 = Date.UTC(2026, 2, 10, 23, 0, 0);
+
+test('buildZapInfo monta o cartao do HUD com NO AR e A SEGUIR', () => {
+  const now = T20 + 30 * MIN;
+  const info = ZapHud.buildZapInfo({
+    channel: { name: 'Globo', logo: 'https://exemplo/g.png', group: 'Abertos' },
+    channelNumber: 12,
+    programs: [
+      { title: 'Jornal', start: T20, stop: T20 + HOUR },
+      { title: 'Esporte', start: T20 + HOUR, stop: T20 + 2 * HOUR }
+    ],
+    nowMs: now
+  });
+
+  assert.equal(info.name, 'Globo');
+  assert.equal(info.number, 12);
+  assert.equal(info.hasNumber, true);
+  assert.equal(info.currentTitle, 'Jornal');
+  assert.equal(info.nextTitle, 'Esporte');
+  assert.equal(info.isLive, true);
+  assert.equal(info.hasEpg, true);
+  assert.equal(info.progressPct, 50);
+  assert.equal(info.countdownMs, 30 * MIN);
+  assert.equal(info.countdownText, '30:00');
+});
+
+test('buildZapInfo sem numero de canal deixa o campo vazio', () => {
+  const info = ZapHud.buildZapInfo({ channel: { name: 'SBT' }, nowMs: T20 });
+  assert.equal(info.hasNumber, false);
+  assert.equal(info.number, '');
+  assert.equal(info.hasEpg, false);
+});
+
+test('buildZapInfo sem canal nenhum devolve valores seguros', () => {
+  const info = ZapHud.buildZapInfo({ nowMs: T20 });
+  assert.equal(info.name, 'Canal');
+  assert.equal(info.currentTitle, '');
+  assert.equal(info.countdownText, '--:--');
+});
+
+test('shouldAutoHide fecha o HUD depois do limite e antes nao', () => {
+  assert.equal(ZapHud.shouldAutoHide(1000), false);
+  assert.equal(ZapHud.shouldAutoHide(ZapHud.ZAP_AUTO_HIDE_MS), true);
+  assert.equal(ZapHud.shouldAutoHide(ZapHud.ZAP_AUTO_HIDE_MS + 1), true);
+  assert.equal(ZapHud.shouldAutoHide(-5), false);
+  assert.equal(ZapHud.shouldAutoHide(NaN), false);
+});
+
+test('shouldAutoHide respeita um limite customizado', () => {
+  assert.equal(ZapHud.shouldAutoHide(2000, 1000), true);
+  assert.equal(ZapHud.shouldAutoHide(500, 1000), false);
+});
+
+test('parseChannelNumber aceita apenas digitos validos', () => {
+  assert.equal(ZapHud.parseChannelNumber('12'), 12);
+  assert.equal(ZapHud.parseChannelNumber('007'), 7);
+  assert.equal(ZapHud.parseChannelNumber(''), null);
+  assert.equal(ZapHud.parseChannelNumber('abc'), null);
+  assert.equal(ZapHud.parseChannelNumber('0'), null);
+  assert.equal(ZapHud.parseChannelNumber(null), null);
+});
+
+test('buildChannelNumberMap numeracao segue a ordem da lista e ignora duplicados', () => {
+  const map = ZapHud.buildChannelNumberMap([
+    { name: 'Globo', tvgId: 'globo.br' },
+    { name: 'SBT', tvgId: 'sbt.br' },
+    { name: 'Globo HD', tvgId: 'globo.br' }
+  ]);
+
+  assert.equal(map.size, 2);
+  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'Globo', tvgId: 'globo.br' }), 1);
+  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'SBT', tvgId: 'sbt.br' }), 2);
+});
+
+test('resolveChannelNumber cai no indice quando o canal nao esta no mapa', () => {
+  const map = ZapHud.buildChannelNumberMap([{ name: 'Globo' }]);
+  assert.equal(ZapHud.resolveChannelNumber(map, { name: 'Desconhecido' }, 4), 5);
+  assert.equal(ZapHud.resolveChannelNumber(map, null, null), null);
+});
+
+test('channelKey prioriza tvg-id e normaliza o nome', () => {
+  assert.equal(ZapHud.channelKey({ tvgId: ' Globo.BR ', name: 'x' }), 'id:globo.br');
+  assert.equal(ZapHud.channelKey({ name: '  Globo  ' }), 'name:globo');
+  assert.equal(ZapHud.channelKey(null), '');
+});
