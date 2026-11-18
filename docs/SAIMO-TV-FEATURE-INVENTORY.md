# Saimo TV (Web) — Exhaustive Feature Inventory

**Repo:** `/workspace/work/Saimo-Site` · **Version:** `3.2.3` (`package.json`) · **Locale:** pt-BR
**Scope of this report:** every user-visible behaviour of the React web app (`src/`), the serverless
functions (`functions/`, `api/`), the TV packaging (`tv/`, `tv-native/`) and the PWA shell.

> Terminology follows the codebase (Portuguese). File references are `path:line`.

---

## A. Architecture summary

### A.1 Stack

| Layer | Choice | Notes |
|---|---|---|
| UI | React **19.3** + TypeScript **~6.0** | Function components, `memo`, `createPortal`, `useSyncExternalStore` |
| Build | **Vite 8** | Two build modes: normal (web) and `--mode tv` (TV package) |
| Router | **react-router-dom 7.18** | `BrowserRouter` on the site, `HashRouter` inside the desktop shell |
| Players | **hls.js 1.7.3**, **mpegts.js 1.8.2**, **shaka-player 5.2.12** (lazy chunk) | HLS / MPEG-TS / DASH-ClearKey |
| Desktop shell | `@tauri-apps/api` 2.11 (+ updater, process) | Tauri app reuses the same `src/` |
| Backend | **Cloudflare Pages Functions** (`functions/`) + **Vercel** (`api/proxy.ts`) | `/api/proxy`, `/api/resolve-vod`, `/api/stream`, `/api/events`, `/api/online/*`, download shortcuts |
| Tests | `node:test` + `node:assert/strict` (`.mjs`, run against `.ts` sources) | 8 test files under `tests/` |
| Styling | Plain CSS files per component (`*.css` imported from `.tsx`) | No CSS-in-JS, no Tailwind |

### A.2 Entry points

1. **`src/main.tsx` — the web/PWA app.** Order of operations is deliberate:
   `import './perfis/instalar'` (profile key swap **before any module reads storage**) →
   `migrarUmaVez()` (one-shot wipe of stale list caches) → `vigiarExtensoes()` (capture-extension blocker) →
   `iniciarTv()` → `iniciarBrilho()` → `sondarProxy()` probe (repeats every 10 min) →
   `iniciarTelemetria()` → **render gated on the probe resolving, max 1.5 s** → `createRoot`.
   Service worker (`/sw.js`) registers only when `PROD && !import.meta.env.VITE_TV && location.protocol === 'https:'`.

2. **`src/entrada-tv.ts` — the TV package entry.** Imports `core-js/stable`, `whatwg-fetch`,
   `abortcontroller-polyfill`, `intersection-observer`, `./plataforma/polyfills-tv`, then `./main`.
   Built by `configTv` in `vite.config.ts:52` into a single **IIFE** (`saimo.js`, `saimo.css`),
   `target: 'chrome68'`, `cssTarget: 'chrome61'`, `define: VITE_TV = "1"`, `outDir: dist-tv`.

3. **`tv-native/src/main.ts` — a *separate*, framework-free native TV app** (Tizen/webOS), written in
   TypeScript with its own `SpatialNav` engine, `State` store, `Api` layer and screens
   (`LiveScreen`, `HomeScreen`, `OnDemandScreen`, `FichaScreen`, `PlayerScreen`, `GuideScreen`,
   `EventsScreen`, `RadiosScreen`, `SearchScreen`, `SettingsScreen`, `FavoritesScreen`,
   `MinhasListasScreen`, `AddonsScreen`, `DoacaoScreen`, `NovelinhaScreen`, `PerfisScreen`).
   It **shares** `src/perfis/*` and `src/plataforma/brilho.ts` with the web app.
   Packaged by `scripts/montar_tv.sh` → `SaimoTV-Samsung.wgt` (Tizen) and `SaimoTV-LG.ipk` (webOS),
   transpiled to ES5 by `scripts/tv_es5.mjs`.

4. **`tv/comum/carregador.js`** — ES5 loader shipped inside the TV package. On boot it asks the site
   for `tv/manifesto.json`; if a newer build exists it downloads and runs that code **inside the
   installed app** (keeping the installed privileges: `http` video, no CORS, remote keys); if the
   network fails or the new code errors it falls back to the copy inside the package. Reports
   crashes to `/api/online/v1/event` as `text/plain`.

### A.3 Routing / screen structure

Router selection (`src/App.tsx:741-745`): `BASE_URL === '/'` → `BrowserRouter` (clean URLs);
otherwise (Tauri `tauri://`) → `HashRouter`. A legacy `#/path` URL arriving on the site is rewritten
to a clean path at boot.

| Route | Component | Purpose |
|---|---|---|
| `/` | `RaizDoSite` → `Landing` **or** `Inicio` | Landing unless `raizAbreOApp()` (TV, TV app, installed PWA, `VITE_TV`) |
| `/home` | `Inicio` | Home screen (hero + rails) |
| `/sobre` | `Landing` | Institutional page (also the old home of the landing) |
| `/privacidade`, `/termos`, `/conteudo`, `/exclusao-de-dados`, `/suporte` | `Legal` | Legal pages |
| `/formato` | `Formato` | "Saimo list format" documentation |
| `/busca` | `Busca` | Universal search |
| `/eventos` | `Eventos` | Sports agenda |
| `/favoritos` | `Favoritos` | Channels + VOD favourites |
| `/radios` | `Radios` | Radio screen |
| `/ajustes` | `Ajustes` | Settings |
| `/tv`, `/tv/:slug` | `TVPage` | Live TV (`:slug` deep-links a channel) |
| `/movies` | `MoviesPage` | On Demand catalogue + player overlay |
| `/app` | `AppDownload` | App downloads |
| `/apoie` | `ApoiePagina` | Pix donation page (no bottom bar, no floating donation button) |
| `/novidades` | `Novidades` | Catalogue release rounds |
| `/teste` | `StreamTester` | **Only if `localStorage['saimo-dev'] === '1'`** |

Cross-cutting mounts in `AppLayout`:
- `EscolhaPerfil` — profile chooser overlay, rendered **only on app screens**
  (`TELAS_DO_APP = ['/home','/tv','/movies','/busca','/eventos','/favoritos','/radios','/ajustes']`).
- `NavegacaoMobile` — bottom tab bar (Início / Ao vivo / Eventos / Filmes e séries / Baixar app).
- `DoacaoModal` — floating draggable donation button + modal (both hidden on `/apoie`).
- Every data screen is keyed by `` `${canais}|${filmes}` `` so changing a list address **remounts**
  the screen and drops the previous list's titles/channels from memory (`src/App.tsx:647-648`).

### A.4 State model

No global store library. Four patterns coexist:

1. **React state in the screen** (`TVPage`, `MoviesPage`, `VodCatalog` — the latter has 44 `useState`s).
2. **Service-level external stores** consumed with `useSyncExternalStore`:
   `useListas()` (list addresses), `useAddons()`, `useTemAddons()`, `useDebridAccounts()`,
   `useHasDebrid()`, `assinar/versaoAtual` (profiles), `radioPlayer.assinar`.
   Events: `saimo-listas-mudaram`, `saimo-addons-mudou`, `saimo-debrid-mudou`,
   `saimo-minhas-listas-mudou`, `saimo-canais-personalizados-mudou`, `saimo-vod-favoritos-mudou`,
   `saimo-zero-delay-mudou`, `saimo-audio-inteligente-mudou`, `saimo-upscaling-mudou`,
   `saimo-abrir-modal-doacao`, `saimo-abrir-perfis`.
3. **Caches**: `localStorage` (text/JSON), **IndexedDB** (`saimo-minhas-listas` → store `canais`, for
   user M3U channel payloads that exceed the 5 MB localStorage quota), in-memory `Map`s.
4. **Module singletons** for side-effecting subsystems: `castService`, `zeroDelayService`,
   `audioInteligenteService`, `radioPlayer`, `epgService` (worker + `porCanal` map).

### A.5 TV vs mobile / desktop code paths

**Mobile vs desktop layout** (`src/App.tsx:40-43`): `ehCelular()` = `innerWidth <= 768` **or**
(`pointer: coarse` **and** `innerHeight <= 500`). Mobile: the player is on top and the channel list
scrolls below it (`.mobile-content` / `.mobile-player` / `.mobile-canais`); a floating channel drawer
appears in theater mode or landscape. Desktop: a macOS-style stage (`.stage-aovivo-mac`) with a
glass channel panel on the side.

**TV detection** (`src/plataforma/tv.ts`):
- `marcaTv()` → `'tizen' | 'webos' | null` — from `window.__SAIMO_TV__` (set by the package loader)
  or UA (`Tizen`, `Web0S|webOS|NetCast`) or vendor globals (`window.tizen`, `window.webOS`, `window.PalmSystem`).
- `ehTv()` — any TV (browser or package). `ehAppDaTv()` — **only the installed package**, which is
  what unlocks `http` video and no-CORS playback.
- `iniciarTv()` adds `modo-tv tv-tizen|tv-webos` classes, applies 4K zoom
  (`1.6` at ≥3200 device px, `1.3` at ≥2200, else `1`) to `document.documentElement`,
  registers Samsung media/colour keys (`tizen.tvinputdevice.registerKey`) and installs the key translator.
- **Remote key translation** (`src/plataforma/tv.ts:60-115`): keyCodes `37/38/39/40` → arrows, `13` →
  Enter; `10009|461|27` → Escape (and, if nothing handles it, exit the app — with a
  "press Back again to exit" toast, 2.5 s window); `415|19|10252|413` → play/pause/play-pause/stop
  mapped onto the current `<video>`. Translated keys are re-dispatched as synthetic `KeyboardEvent`s
  so the whole app sees one key vocabulary.
- `hlsNativo()` — true when the TV's own decoder can play HLS (`canPlayType('application/vnd.apple.mpegurl')`).

**D-pad / remote navigation model:**
- `src/contexts/dpadContexto.ts` — context, `useDpad()`, `useFocusable()` (marks
  `data-focusable="true"` + `tabindex`).
- `src/contexts/DpadContext.tsx` — `DpadNavigationProvider`. Key behaviours:
  - **Focusable selector differs by platform**: on TV everything focusable counts
    (`button`, `a[href]`, `[role=button|tab|menuitem]`, `input`, `select`, `textarea`,
    `[tabindex]:not([tabindex="-1"])`, plus `[data-focusable="true"]`); on desktop only
    `[data-focusable="true"]` (and not disabled / `aria-hidden`).
  - **Spatial movement**: Euclidean distance with the main axis weight 1 and the cross axis weight 3,
    plus a −1000 alignment bonus proportional to the overlap in the perpendicular axis.
  - **Navigation zones** (`NAVIGATION_ZONES`): sidebar ↔ player ↔ mobile; crossing a zone boundary
    fires a `dpad-enter` custom event on `.video-player-container` so the player's controls appear.
  - **Dialog scoping**: `janelaAberta()` finds the topmost visible `[role=dialog]`/`[aria-modal=true]`;
    on TV focus is confined to it, and a `MutationObserver` moves focus into a newly opened dialog
    (`[data-autofocus]`, `.vod-btn-assistir`, `button.primario`, else first focusable) and restores
    the previous element on close (a focus stack).
  - **Auto-focus safety net on TV**: 1.5 s after each route change, if the focused element is gone,
    focus the first focusable.
  - `rolarAteVer()` scrolls the *nearest horizontally-scrolling ancestor* first (rows of posters),
    then the page — keeping the focused card inside the row's own scroll box.
  - **Keys**: Arrows move (100 ms debounce), `Enter`/`Space` click the focused element, `Esc`/`Backspace`
    trigger the **back-handler stack** (newest first); if no handler consumes it, `navigate(-1)`
    unless already on `/` or `/home`.
  - **Gamepad support**: `requestAnimationFrame` polling of `navigator.getGamepads()`, D-pad buttons
    12–15, A=0 (click), B=1 (back), left analog with a 0.6 deadzone, 150 ms navigation debounce.
    `navigator.getGamepads()` is always coerced to a plain array (old TV Chromium returns a
    `GamepadList` without `.some`).
- `src/hooks/useDpadNavigation.ts` — a **second, standalone** implementation of the same idea
  (not used by the provider; kept as a reusable hook: `moveFocus`, `focusFirst`, `focusLast`,
  `focusElement`, optional `enableLoop`, `registerGlobalBackHandler`).
- `src/hooks/useControleTv.ts` — TV-only remote semantics inside "Ao vivo":
  - `CH+` (Samsung 427 / LG PageUp 33) → next channel; `CH−` (428 / 34) → previous channel.
  - From a channel row, `ArrowRight` jumps directly to the player buttons (skips the row's tabs).
  - From the first player button, `ArrowLeft` returns to the channel list.
  - When the list is closed and controls are hidden: `ArrowUp/Down` = next/previous channel,
    `ArrowLeft/Right/Enter` = open the channel list (focus jumps to the on-air channel).
  - Back handler: close the channel panel → else leave multi-screen → else `navigate(-1)` if there is
    history, else `/home` (never exits the app).
  - When multi-screen is enabled, focus jumps to the channel list after 300 ms so the user can pick
    the channel for Screen 2.
- `src/hooks/useVoltarFecha.ts` — **layered back**: each open overlay pushes a history entry tagged
  `saimoCamada: <n>`; a `popstate` closes the layer; closing via X/Esc pops the entry
  (several simultaneous closes are batched into one `history.go(-n)`).
- `src/hooks/useMediaSession.ts` — Media Session metadata (`title`, `artist`, `artwork`) and actions
  `play/pause/seekbackward/seekforward/nexttrack/previoustrack`. Live TV maps **pause → resume**
  (live never pauses).
- `src/hooks/useKeyboardShortcuts.ts` — deliberately **empty**: desktop keyboard shortcuts are
  disabled on the site (the `?` help overlay in `src/components/AtalhosAjuda.tsx` documents them but
  the hook is a no-op).
- `src/styles/dpad-navigation.css` — the visual focus ring (`.dpad-focused`).

---

## B. Feature inventory

> `TV` = only/primarily for the Smart-TV path; `Shared` = web + TV; `Web` = web/browser only.

### Live TV (Ao vivo)

**1. Live channel grid with categories, search and progressive rendering** — Shared
`src/components/PainelDeCanais.tsx`. Left glass panel listing channels: category pills
(`Todos`, `Favoritos`, then `categoryOrder` then extras alphabetically), free-text search over
name + category, grouped by category with uppercase headers, per-row channel number, logo tile with
initials fallback, star (favourite) button. Renders in chunks of **40** with an
`IntersectionObserver` sentinel (`rootMargin: 350px`) + scroll handler to grow the list — keeps weak
TVs fluid. On open it scrolls to the on-air channel (`scrollIntoView`, 150 ms delay) and shows
`active channel index` + total count. Right-click opens a context menu: **Escolher Fonte** submenu,
**Favoritar/Remover dos favoritos**, **Abrir na Tela 1/2** (multi-screen).
`src/data/categorias.ts` defines the fixed section order (TV Aberta, Filmes, Series, Esportes,
Noticias, Infantil, Documentarios, Entretenimento, Pluto TV, 24 Horas, Internacionais, Adulto).

**2. Multiple sources per channel + automatic failover** — Shared
`src/types/channel.ts` (`ChannelSource`: `url`, `quality`, `name`, `referer`, `userAgent`, `keyId`,
`key`), `src/utils/streamUrl.ts` (`buildAttempts`), `src/components/VideoPlayer.tsx:197-647`.
Each published source can yield up to two attempts (direct, then via `/api/proxy`). On failure the
player walks to the next attempt; a source that already played and dies is reopened once before
switching. The failure policy is **"the clock decides, not the error counter"**: an error only kills
the source if the video clock stopped advancing; otherwise the same player is resumed
(`falhar()` vs `descer()`).

**3. 12-second "no picture" watchdog and 25-second freeze watchdog** — Shared
`src/components/VideoPlayer.tsx:374-490`. A `timeupdate` listener marks the source as "alive" only
when the clock advances (`readyState >= 2 && t > 0.1 && t > ultimoTempo + 0.2`). If nothing has
advanced for **12 s** the attempt is considered dead; if it had played and the picture freezes for
**25 s** the source is dropped. Rationale in code: viewers prefer a long stutter over a source swap
that restarts at zero.

**4. Manual source selection ("Fontes do canal")** — Shared
`src/components/VideoPlayer.tsx:224-281, 1511-1556`. A modal lists the **published sources** (not the
internal attempts): shows `source.name` (e.g. "Temporada 3") or `Fonte N`, the quality, and badges
`DRM` / `com Referer` / `HTTP` — **never the server or the raw URL**. The badge in the control bar
shows the active source; right-click in the channel list offers the same list.
`src/services/castService.ts` / `src/components/PainelDeCanais.tsx` (context menu) share it.

**5. Channel navigation: number, previous/next** — Shared
`src/App.tsx:222-234` (`handleNextChannel` / `handlePrevChannel`, wrap-around), channel numbers from
the index in the channel list, `channelNumber` badge in the player.

**6. Channel favourites** — Shared
`localStorage['tv-favorites']` (`string[]` of channel ids) via `useLocalStorage`; toggled from the
channel list star, the context menu, `ChannelCard`, and the Favorites screen. Toast feedback
("… adicionado aos favoritos" / "… removido dos favoritos").

**7. Multi-screen (two live channels, one with audio)** — Shared (Web full; TV simplified)
`src/App.tsx:102-197, 318-477`. Desktop renders two `VideoPlayer`s side by side; the inactive side is
`forceMuted`; each side shows a glass overlay with a volume icon, the channel name and an
"Áudio ativo" badge; clicking a side activates its audio; each side has a close (✕) button.
`PainelDeCanais` shows a **Tela 1 / Tela 2** selector at the top and "Abrir na Tela 2" in the
context menu. Toasts: "Multi-Tela ativada: Escolha o canal da Tela 2 na lista".

**8. "Now on air" overlay on the player** — Shared
`src/components/ProgramInfo.tsx`. Floating panel over the video: progress bar of the current
programme, "AGORA" badge with a pulsing dot, title, time range `HH:MM - HH:MM`, duration
(`45min` / `2h15min`), category, rating, description, "A SEGUIR" next programme, an expand chevron
revealing the next 5 programmes, and a **Guia** button. If there is no data: "CARREGANDO… /
Buscando programação…" or "AO VIVO / Transmissão ao vivo / Sem informação de programação".
Polls every 30 s and re-renders on every EPG update event.

**9. On-air info in the channel list** — Shared
`PainelDeCanais` row shows the current programme title + a thin progress bar; updated through
`onEPGUpdate`. `src/components/CartaoNoAr.tsx` is the floating "on air" card used by the macOS-style
layout (channel logo, number, title, time range, "faltam N min" / "terminando", "A seguir: …").

**10. EPG grid (Guia de programação)** — Shared
`src/components/ProgramGuide.tsx`. Full-screen modal: one scrollable element for both axes, the
channel column and the time ruler are `position: sticky` (no JS scroll-sync). Range = **1 h in the
past → 25 h ahead**, starting at a half-hour boundary. Ruler ticks every 30 min with the day label at
midnight; a vertical "now" line and label. Channel column shows number, logo (initials fallback,
broken logos cached in a module-level `Set`) and name. Programme blocks are absolutely positioned
(`left`/`width` in px at 4 px/min desktop, 3 px/min mobile) with classes `no-ar`, `passou`,
`estreito` (<44 px), `mini` (<84), `curto` (<128) so short blocks shed the time then the "Ao vivo"
word. The on-air block has an internal progress bar. Clicking a block opens a **detail panel**
(poster, channel, title, day + time range, duration, "Ao vivo"/"Encerrado" tags, progress bar,
category and episode tags, synopsis) with an **"Assistir {canal}"** button. A search field filters
channels by normalised name. "Agora" button re-centres on the current time. Only the visible window
(quantised to 4-line / 60-minute margins) is rendered.

**11. Zero Delay (low-latency live)** — Shared
`src/services/zeroDelay.ts`, `src/utils/vigiaAoVivo.ts`, `src/components/VideoPlayer.tsx:1340-1373`.
Three modes cycled from the player button or Ajustes:
- `off` — default hls.js (`backBufferLength: 90`).
- `equilibrado` — `liveSyncDurationCount 2.8`, `liveMaxLatencyDurationCount 5.5`,
  `maxLiveSyncPlaybackRate 1.03`, `maxBufferLength 20`, stall increase 1.5 s.
- `agressivo` (Ultra) — `liveSyncDurationCount 2.0`, `liveMaxLatencyDurationCount 4.0`,
  `maxLiveSyncPlaybackRate 1.04`, `maxBufferLength 12`, `backBufferLength 15`.
mpegts.js gets the equivalent `liveSync*` / `liveBufferLatencyChasing*` config; Shaka gets a
`streaming.liveSync` block. A floating "Ao vivo" badge shows the estimated delay (`-3s PULAR`) and
turns yellow when off the live edge; clicking it **jumps to the edge** (`pularParaBorda`, with a
margin of 8.0/4.5/2.8 s by mode). The watchdog only acts on native (`video.src`) playback — with MSE
the specialised player owns latency. It also re-syncs when the tab becomes visible again.
Persisted in `localStorage['saimo-zero-delay']`.

**12. "Áudio Inteligente" (dynamic range normalisation)** — Shared
`src/services/audioInteligente.ts`. Web Audio graph: `MediaElementSource → DynamicsCompressor →
GainNode → destination`. Enabled by default, toggle in Ajustes, persisted in
`localStorage['saimo-audio-inteligente']`; connected to the video element in both players.

**13. Multi-format playback: HLS / MPEG-TS / DASH + native HLS** — Shared
`src/components/VideoPlayer.tsx:508-636`. Decision order:
1. `nativo` (TV package with native HLS) → `video.src = url`;
2. DASH (`.mpd`) → `playDash()` (`src/services/dashPlayer.ts`) with **Shaka Player loaded on demand**
   (`shaka-player.compiled.js`), configuring ClearKey DRM from the catalog's `keyId`/`key`
   (hex → base64url, `hexToBase64Url` in `src/utils/streamUrl.ts`);
3. raw MPEG-TS (`.ts`) → **mpegts.js** (`{type:'mpegts', isLive:true, cors:true}`);
4. HLS → **hls.js** with `enableWorker`, zero-delay config, and either
   `fLoader: criarCarregadorDireto()` (proxy path) or `loader: makeNestedPathLoader(url)` (direct path,
   fixes the "segments live in the `url=` folder" problem);
5. Safari native HLS (`canPlayType('application/vnd.apple.mpegurl')`);
6. otherwise "Seu navegador não suporta reprodução de vídeo."
Fatal hls.js manifest errors (`MANIFEST_LOAD_ERROR/TIMEOUT/PARSING_ERROR`, `LEVEL_LOAD_ERROR`,
`LEVEL_EMPTY_ERROR`) skip straight to the next source instead of retrying.

**14. Audio-track and quality selection (live)** — Shared
`src/components/VideoPlayer.tsx:564-600, 1375-1399, 1559-1621`. After `MANIFEST_PARSED`, hls.js
`audioTracks` (>1) and `levels` (>1) are turned into menus. Quality list = "Automática" + one entry
per level (`1080p (4500k)`). Audio list uses `track.name || track.lang || "Faixa N"`. A single gear
button opens whichever menu applies; the active one is highlighted with "Em uso".

**15. Resolution badge** — Shared
`video.videoHeight` → `4K / 2K / 1080p / 720p / 480p / 360p / {h}p`, refreshed on `loadedmetadata`,
`resize` and every 2 s.

**16. Cast / "Transmitir para dispositivo"** — Shared
`src/services/castService.ts` (889 lines) + the modal in both players. Methods are **filtered by the
URL scheme**: only `http://` sources may leave the site — copy link, share, open in an external player,
open in a new tab. HTTPS never leaves. Supported methods: Google Cast (Chromecast, SDK loaded lazily),
AirPlay (Safari `webkitShowPlaybackTargetPicker`), Remote Playback API, Presentation API (smart TVs),
Web Share API, copy URL, external players (VLC, IINA, PotPlayer, MX Player). A persistent
"Transmitindo para {device}" indicator with a **Parar** button appears while casting.

**17. PiP, fullscreen and theater mode** — Shared
`src/utils/telaCheia.ts` handles prefixed APIs (`webkitRequestFullscreen`, `webkitEnterFullscreen`),
locks the orientation to landscape on coarse-pointer devices and releases it on exit. PiP is toggled
with `requestPictureInPicture` and only rendered when `document.pictureInPictureEnabled`.
Theater mode enlarges the player (`.theater-mode`).

**18. "This channel only plays over http" notice** — Shared
`src/components/VideoPlayer.tsx:1115-1159`. When every attempt is `http://` (blocked as mixed content
inside an https page), the app shows a dedicated overlay: "Este canal toca numa página separada",
the explanation, a button **"Abrir {canal} numa página separada"** (`window.open`), a hint to use VLC
if the browser downloads the file, an app-download promo, and "Tentar de novo aqui".

**19. "Play it in the free app" hint** — Shared
When a channel plays through the site proxy, a dismissible floating note appears
("Este canal toca mais leve no app grátis" + "Baixar app"). Dismissing hides it for **7 days**
(`localStorage['saimo-dica-app-fechada']`).

**20. Capture-extension blocker** — Shared
`src/plataforma/bloqueioExtensoes.ts` detects the FlowPick sniffer (`script[data-m3u8-injected]`,
`window.__flowpickPageHook`, `FLOWPICK_*` messages). When detected, all video/radio loading stops and
nothing else is fetched — **silently**, no on-screen warning.

**21. Resume last channel + deep links** — Shared
`localStorage['tv-last-channel']`; `/tv/:slug` resolves a channel by normalised id or by name
(`src/App.tsx:128-147`). The channel chosen in a given `/tv/:slug` link is remembered with its slug,
so opening another link switches the channel. Ajustes → "Ao iniciar" can open the last channel
instead of the home screen (first entry of the visit only, guarded by `sessionStorage['saimo-entrada']`).

**22. Media Session / lock-screen / Bluetooth controls** — Shared
`src/hooks/useMediaSession.ts` — title, subtitle ("Ao vivo · Saimo TV"), channel logo as artwork;
play/pause/seek handlers. For live TV, pause resumes playback.

**23. Live TV never pauses** — Shared
`src/components/VideoPlayer.tsx:658-719`. A `pause` event immediately calls `play()` again; if that
fails it retries muted and arms `pendingUnmute`. Autoplay-with-sound is blocked by browsers: the
video starts muted and is unmuted on the first click/keypress/touch (`pendingUnmute`).

**24. Stream tester (internal tool)** — Web (dev-gated)
`src/components/StreamTester.tsx`, route `/teste` only when `localStorage['saimo-dev'] === '1'`.
Paste an M3U8/MP4 URL → detects the format (`.mp4` / `.ts` / HLS / Safari native) → plays it with
hls.js or mpegts.js. Features: **auto-refresh with invisible double-buffering** (a second `<video>`
+ second HLS instance is loaded in the background and swapped in when ready — volume/mute preserved),
configurable interval (default 15 s) with a live countdown, a statistics panel (play time, refresh
count, last refresh), and a timestamped log console (last 100 entries). MP4 is excluded from refresh.

### On Demand / VOD

**25. Catalogue in "rails" mode (rows) and "Explore" mode (A–Z grid)** — Shared
`src/components/VodCatalog.tsx` (2800 lines). Rails mode: a **hero** at the top (background art +
title + badges + synopsis, driven by the item under the mouse, debounced 350 ms), a "Categorias" row
of big collage cards (Filmes / Séries / Animes / Doramas / Novelas / Kids / Novelinhas / 18+ with
counts), a **Continue assistindo** row with progress bars, the "Destaques" rails from
`destaques.txt`, and one rail per genre ending in a **"Ver todos"** card that opens that genre's grid.
Explore mode: a letter filter `#A–Z`, a genre filter, a free-text search and the filter grid.

**26. Filters: year, age rating, minimum rating, sort order** — Shared
`src/services/infoDosTitulos.ts` (`detalhes.txt`: `ano`, `nota`, `idade`, `popularidade` per title),
applied in `VodCatalog.tsx:1473-1517`. Sort options: **A–Z**, **mais novos**, **mais populares**
(by `popularidade`), **melhores avaliados** (by `nota`). Year ranges come from `faixasDeAno()`.
The "Filtrar" button toggles Explore mode; a filter chip lights up when active.

**27. Title detail sheet (ficha)** — Shared
`VodCatalog.tsx` — modal with poster, backdrop, title, year, rating, genres, synopsis, cast photos
(TMDB), season pills, a horizontal 16:9 episode strip with stills and progress, a favourite toggle,
and the source/version picker. Data comes from `src/services/tmdbService.ts` (ficha, creditos, perfil,
episodiosDaTemporada) fetched **on demand** (the "ficha sob o ponteiro" hook waits 280 ms for the
mouse to settle before asking TMDB — `src/hooks/useFichaDe.ts`).

**28. Continue assistindo** — Shared
`src/services/continuar.ts` — `localStorage['saimo-continuar']`, max 20 entries, sorted by recency,
stores the catalogue coordinates (`ItemDestaque`), the label, the fraction and the timestamp.
Finished movies (≥95 %) are removed; series stay so the next episode continues. Each card has an **×**
to remove it from the row.

**29. VOD favourites** — Shared
`src/services/vodFavoritos.ts` — `localStorage['saimo-vod-favoritos']`, stores
`{tipo, titulo, letra, ano, capa}`; the newest first. Shown on Home and on the Favorites screen.

**30. Series episode browser + next/previous episode** — Shared
`src/components/MoviePlayer.tsx:1669-1769` — a side drawer listing seasons as pills
(`Temporada N` + episode count) and the episodes of the selected season with TMDB title, still image
and duration. The player keeps the whole series in `SeriesEpisodeInfo.episodes`. Next/previous episode
buttons in the control bar, and a **"Próximo episódio" card** that appears when the credits start
(TheIntroDB credits marker) or 30 s before the end, with a **Reproduzir** button and a dismiss (×).

**31. Automatic next-episode hand-off + source stickiness** — Shared
`src/utils/fontePreferida.ts` — when the episode changes, the new source list is matched against the
one that was playing: same `bingeGroup` first, then the same host, then the same index **within the
same audio version** (dub/leg never switches). `lembrarFonte()` stores the choice per series.

**32. Skip intro / recap / credits** — Shared
`src/services/pulos.ts` (TheIntroDB `api.theintrodb.org/v3/media?tmdb_id=…`, with season/episode for
series). Returns `Trecho[]` of type `abertura | recapitulacao | creditos | previa` with start/end in
seconds; chunks shorter than 3 s are discarded; a segment without an end is only valid for credits.
The player shows a floating button ("Pular abertura" / recap / preview / credits) with a close (×),
auto-hides after 8 s when the controls are hidden, and skips to the segment end.

**33. Subtitles: embedded tracks + OpenSubtitles, with sync adjustment** — Shared
`src/services/legendas.ts` uses the public Stremio OpenSubtitles addon
(`opensubtitles-v3.strem.io/subtitles`) — no key, open CORS — plus a TMDB→IMDb bridge
(`vod/imdb/` fragments, ~8 KB) and the `imdbId` already present on catalogue titles.
Offered languages: Portuguese (BR) up to 5 versions, Portuguese (PT) 3, English 3, Spanish 2.
Only the *list* is fetched when a title opens; the .srt (~40 KB) is downloaded only when a version is
chosen. External subtitles are rendered into a synthetic `<track>` via `VTTCue`s, and the **sync
offset** selector offers −5 s…+5 s in 0.25 s steps (41 options) and re-draws the cues without
re-downloading. Embedded HLS subtitle tracks are merged with the external ones in one dropdown
("Desligadas" / tracks / "OpenSubtitles" optgroup); choosing an external one disables the embedded
display. The chosen language is remembered (`legenda-idioma`).

**34. Resume position for VOD** — Shared
`localStorage['movie-progress-<movieId>']` stores the seconds; saved on unload and when the source
changes, **only after 30 s** of playback and **removed above 95 %** progress; restored on load if the
saved point is >5 s from the end.

**35. Player settings menu** — Shared
`src/components/MoviePlayer.tsx:2182-2349`: **Qualidade** (select, "Automática" + levels),
**Idioma do áudio** (hls.js audio tracks), **Legendas** (merged list), **Sincronia da legenda**,
**Velocidade** (0.25× → 3×, 10 options, with a speed indicator badge when ≠ 1),
**Pular (segundos)** (5/10/15/30/60 — persisted in `localStorage['movie-skip-time']`),
**Proporção** (auto / 16:9 / 4:3 / 21:9), **Super Resolução** (off / Nitidez HD / Super Resolução
Ultra) and **Brilho** (50–150 %). The upscaling choice is stored in `localStorage['saimo-upscaling']`
and applied as an SVG `feConvolveMatrix` filter on `document.documentElement.dataset.upscaling`
(HD kernel `0 -0.45 0 / -0.45 2.8 / 0 -0.45 0`; 4K kernel `-0.15 -0.55 -0.15 / -0.55 3.8 / …`).
Videos whose own resolution already meets the target are tagged `data-nativo="1"` and skipped by
the filter.

**36. "Trocar Fonte" while loading / source modal for a title** — Shared
While loading, a "Trocar Fonte" button cycles through the sources. The source modal lists each
source with `Fonte N`/`leg`/`dub`, the provider label ("Servidor Alternativo (HTTP)"),
and badges `Debrid | HLS | MP4/Direto | HTTP | HTTPS | Dublado | Legendado` + the addon's own
warning text — **never the raw URL**.

**37. Novelinhas — vertical short-form player** — Shared
`src/components/Novelinhas.tsx` + `src/services/novelinhas.ts` (`novelinhas.txt`: id, title, folder,
episode count, dub/leg, cover, genres, synopsis; episode URL = `base + folder + "/episode-" + NNN.mp4"`).
A dedicated section with: a "Continue assistindo" row, a search box, audio chips
(Todas/Dubladas/Legendadas), genre chips (genres with ≥5 titles, top 14), and a grid of portrait
covers with a DUB/LEG badge and a progress bar. The player is a **fullscreen portrait portal**:
swipe up → next episode, swipe down → previous, tap → play/pause, wheel → change episode
(desktop), arrow keys / PageUp / PageDown → change episode, `Space` → pause, `Esc` → close.
The next episode is **preloaded** with `<link rel="preload" as="video">` while the current one plays,
and auto-advance happens at `ended`. Progress is saved every 5 s in
`localStorage['saimo-novelinhas-progresso']`.

**38. Addons — Stremio protocol** — Shared
`src/services/addons.ts`, `src/utils/addonProtocol.ts`, `src/utils/addonClient.ts`,
`src/components/AddonsModal.tsx`. **Zero addons pre-installed.** The user pastes an addon URL;
the manifest is fetched, normalised and stored (`localStorage['saimo_addons_v1']` as `AddonRecord`
= id, baseUrl, manifestUrl, queryString, manifest, enabled, addedAt, order). The user can
enable/disable, **test** and remove addons. Resources supported: `catalog`, `meta`, `stream`,
`subtitles` (with types and idPrefixes). Addons supply catalogue rails, metadata, streams and
subtitles; streams are mapped to `MovieSource` with `addonId`, `infoHash`, `fileIdx`, `trackers`,
`behaviorHints` and `webAviso`. All traffic goes **directly from the browser to the addon** — never
through the Saimo servers. Addons are hidden on the Kids profile.

**39. Debrid accounts (TorBox, Real-Debrid, AllDebrid, Premiumize)** — Shared
`src/services/debrid.ts` + `src/utils/debridProtocol.ts`. Keys live only in this browser
(`localStorage['saimo_debrid_v1']`), never proxied. The modal lets the user add a key per provider,
test the account, toggle `syncWithConnect`, and remove it. Torrent sources are resolved through the
provider at play time (`resolveDebridStream(infoHash, fileIdx, trackers)`); unresolved torrents show
"Não foi possível resolver este torrent no Debrid." and sources flagged "Requer Debrid" show
"Esta fonte torrent requer uma chave Debrid em Ajustes > Addons."

**40. "Saimo Connect" data sync** — Shared
`aplicarDadosConnect()` / `exportConnectDadosPayload()` implement the retro-compatible payload
`{ t: "dados", addons?, debrid? }` — the Addons modal has a **Connect** tab with JSON import/export.

### Other screens and features

**41. Home (Início)** — Shared
`src/telas/Inicio.tsx`. Animated logo intro (`Abertura.tsx`, SVG layers + canvas particles, skipped
on TV), the hero with the title under the mouse (TMDB backdrop, rating, seasons, genres), then the
rails: **Continue assistindo**, **Ao vivo agora** (up to 30 wide cards with the on-air programme and
progress), **Canais favoritos**, **Filmes e séries favoritos**, and the catalogue highlight rails
(`destaques.txt`) plus, when addons are installed, addon catalogue rails and TMDB "popular" rails.
Refreshes the on-air data every 60 s and on every EPG event.

**42. Events / sports agenda** — Shared
`src/services/eventos.ts` + `src/telas/Eventos.tsx` + `src/components/EventsModal.tsx`.
Two sources: the Saimo agenda (`eventos-extra.json`) says **which channel** carries each game, and
ESPN scoreboards (23 leagues: Brazilian football, Libertadores, Sudamericana, Champions, Europa,
Premier, La Liga, Serie A, Bundesliga, Ligue 1, Portuguese, Eredivisie, Argentina, Saudi, World Cup,
NBA, NFL, MLB, NHL, UFC) provide the live score, minute and better badges. Games are matched by team
name (accent/prefix-insensitive, club-name tokens removed) and start time (±4 h); a reversed
home/away ordering swaps the badges and the score. Only games that exist on a channel in the user's
list are shown (adult channels excluded). Grouped into **Ao vivo agora / Começa em breve / Hoje mais
tarde / Amanhã / Próximos dias**, with a hero for the hovered game. Refreshes every 3 minutes.

**43. Radios** — Shared
`src/telas/Radios.tsx` + `src/services/radios.ts` (`radios.txt` → `nome|endereço|logo`, cached in
`localStorage['saimo-radios-lista']`) + `src/services/radioPlayer.ts` (a single site-wide
`<audio>` element + hls.js, so audio keeps playing across screens). Rows: "Ouvidas por último"
(`localStorage['saimo-radios-recentes']`) and "Todas, de A a Z". The playing radio is shown at the
top with an animated equalizer and a **Parar** button; the top bar shows a mini player with a stop
button; opening the Live TV screen stops the radio (live takes the sound).

**44. Universal search** — Shared
`src/telas/Busca.tsx`. Searches channels (accent-insensitive, max 40), films, series, animes,
doramas — the catalogue index (`busca.txt`, ~30 k titles in <1 MB) plus addon catalogue search and
TMDB multi-search, merged and de-duplicated. Debounced 220 ms, min 2 characters. Recent searches
(`localStorage['saimo-buscas-recentes']`, max 8) with a "Limpar" button. Empty state:
"Nada encontrado / Nada para '{termo}'. Tente outro nome."

**45. Favorites screen** — Shared
`src/telas/Favoritos.tsx` — filter pills (Tudo / Canais / Filmes e séries), counts, and a remove
(♥) button per item; empty state explains how to add favourites.

**46. Profiles (up to 10)** — Shared
`src/perfis/perfis.ts`, `EscolhaPerfil.tsx`. Each profile has an id, a name (max 20 chars), one of
**21 avatars** (SVG on the site, PNG in the TV package) and its own favourites, "continue watching",
recents and last channel. Storage trick: the **main profile uses the legacy keys unchanged**, the
others use `key@<id>`; `instalarChavesPorPerfil()` monkey-patches `Storage.prototype.getItem/
setItem/removeItem` **before any module reads storage** (imported first in `main.tsx`), so no module
needs to know about profiles. Switching profile reloads the page. With 2+ profiles the chooser opens
automatically once per tab (`sessionStorage['saimo-perfil-escolhido']`); it can also be disabled
("Entrar no último perfil"). Profile metadata: `saimo-perfis`, `saimo-perfil-ativo`,
`saimo-perfil-perguntar`, `saimo-perfis-removidos`.

**47. Kids profile** — Shared
`src/perfis/kids.ts`. Only titles with a **known** age rating between 0 and 12 are shown; unrated
titles are hidden. The filter lives inside the catalogue reads (`vodService`), so rails, grids,
search and sheets all receive the cleaned list. Adult content never unlocks. Leaving the Kids
profile, managing it, editing it, or opening Ajustes asks for the 4-digit code.

**48. Parental control (PIN)** — Shared
`src/perfis/parental.ts` + `src/perfis/ControleParental.tsx`. Device-wide (not per profile).
Default PIN **0000**, changeable in Ajustes (new PIN must be 4 digits). Stored as
`sha256('saimo-parental:' + pin)` in `localStorage['saimo-parental-senha']`. Never unlocks on the
Kids profile. The section sits at the **end** of Ajustes, deliberately discreet. A second entry point:
**20 clicks on the logo in the top bar** (within 3 s) opens the "+18" PIN modal, and clicking the logo
again while unlocked re-locks.

**49. Adult content gating** — Shared
`src/contexts/adultoContexto.ts` (`localStorage['adult-mode-global']`). When unlocked, the
`+18` badge appears on the logo, the 18+ catalogue section becomes available, and the
`restritos.txt` channel list is fetched and merged (channels get the `restrito-` id prefix).

**50. "Minhas listas" — user M3U / file / Xtream** — Shared
`src/telas/MinhasListas.tsx`, `src/services/minhasListas.ts`, `src/utils/leitorM3u.ts`,
`src/utils/xtream.ts`. Up to **10 lists**; each may be:
- an **M3U link** (downloaded directly; falls back to `/api/proxy?raw=1` when the browser is blocked),
- an **M3U file** chosen from disk,
- a **server + user + password** (Xtream Codes): `player_api.php` is tried first
  (`get_live_categories` + `get_live_streams`, each stream offered as `.m3u8` **and** `.ts`);
  if the API is unavailable the `get.php` M3U is used. Passwords never appear in logs or error
  messages.
The M3U reader (`leitorM3u.ts`) supports `#EXTINF` (`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`),
`#EXTGRP`, `#EXTVLCOPT` (`http-referrer`, `http-user-agent`), `#KODIPROP`
(`inputstream.adaptive.license_type`/`license_key` → ClearKey `KID:KEY`), Kodi-style
`url|User-Agent=…&Referer=…` lines, resolution tags (`720p`/`1080p`), and strips "noise" tags
(`Not 24/7`, `geo-blocked`, `backup`, `4K`, `HD`…). Limit 20 000 channels; adult content is filtered
by name/group regex (with "Adult Swim" explicitly excluded) until the parental PIN is given.
Lists auto-refresh after **12 h** (`atualizarVencidas`), one at a time, silently.

**51. Ajustes (Settings)** — Shared
`src/telas/Ajustes.tsx`. Sections: **Listas** (the two addresses with a live network test — the
address must return 200 for `catalogo.txt` / `indice.txt`; failures offer "Tentar de novo" or
"Salvar mesmo assim"; "Apagar" disables a list and wipes its caches), **Minhas listas**,
**Addons & Debrid**, **Perfis** (switch / manage / "perguntar quem está assistindo"), **Ao iniciar**
(Home vs last channel), **Super Resolução (Upscaling)**, **Áudio Inteligente**, **Zero Delay**
(off / equilibrado / agressivo, with an explanatory note per mode), **Exportar/importar channels**
(export all or favourites to `.m3u`, import a Saimo `.txt` catalogue), **Tela** (app brightness
slider 0–100 %), **Dados deste perfil** (clear Continue-watching / recent searches / recent radios /
favourites), **Mais** (download the app, Novidades, "Apoiar o projeto", Discord, link to the channel
list as text). On the Kids profile the whole screen is replaced by a PIN prompt.

**52. Import / export of channels** — Shared
`src/services/canaisPersonalizados.ts` — imported channels are stored in
`localStorage['saimo-canais-personalizados']` and merged into the grid after the published list;
export writes a standard `.m3u` (`saimo-tv-canais.m3u` / `saimo-tv-favoritos.m3u`).

**53. Donations (Pix)** — Shared
`src/components/DoacaoModal.tsx` + `DoacaoConteudo.tsx` + `src/services/doacaoService.ts`.
A **draggable floating button** (mouse and touch) whose position is remembered
(`localStorage['saimo-doacao-pos-v2']`). Clicking opens a modal with the explanation, a **Pix
copia-e-cola** string (static BR Code for Gabriel Saimo Ferreira) with copy-to-clipboard (2.5 s
"copiado" feedback, with a DOM fallback when the Clipboard API is blocked), a QR code, and the
preferred name. The button **hides completely** in fullscreen, theater mode, the movie overlay and
landscape playback (detected via fullscreen events, a `MutationObserver` on body classes and a
resize listener). Also a standalone `/apoie` page and a global `saimo-abrir-modal-doacao` event
("Apoiar o projeto ❤️" in Ajustes).

**54. Novidades (catalogue release rounds)** — Shared
`src/components/Novidades.tsx` — reads the published `novidades.json` (rounds at 11 h and 18 h) and
shows, per round, the date/time, the turno label ("Rodada das 11h" / "das 18h" / "manual"), and the
titles added/updated with covers; missing covers are looked up in TMDB. Read-only page, no link
points to it.

**55. App download page** — Shared
`src/components/AppDownload.tsx` + `src/services/versao.ts`. Tabs per platform (iPhone "em breve",
TV Box, Roku, Samsung, LG, Windows, Linux, Linux ARM, Mac) with requirements, step-by-step install
instructions, the Downloader code `1506768`, and short URLs (`/tvbox`, `/celular`, `/mac`,
`/windows`, `/linux`, `/linux-arm`, `/samsung`, `/lg`, `/roku`) served by `functions/*.ts` which
read the latest GitHub release of `SaimoTV/saimo-downloads` and redirect.

**56. Institutional / legal pages** — Web
`src/landing/` — `Landing.tsx` (1387 lines): hero, "Como funciona" with a live demo, an interactive
**MultiTelaSom** section (click a half to move the TV audio to the phone; the phone chooses screen
1, 2 or both, with a Bluetooth-headset note), a tabbed **Recursos** section with 8 animated
scenarios, **Tudo que ele faz** (a filterable list of every feature, grouped by Ao vivo / Filmes e
séries / Som e imagem / Entre aparelhos / Sua lista e sua casa), **App por app**, **Feito para o
controle remoto** (key tables), **Leve de verdade** (1 GB device measurements), **Aparelhos**,
**Saimo Connect**, and a privacy section. Plus `TelaDoApp.tsx` (a replica of the real UI),
`Moldura.tsx` (header/footer), `Formato.tsx` + `formato-saimo.md` (the list format), `Legal.tsx`
(Privacy, Terms, Content, Data deletion, Support) and `config.ts` (contact e-mail, legal
responsible, last revision date).

**57. PWA installability** — Web
`public/manifest.webmanifest` (`start_url: /home`, `display: standalone`, `lang: pt-BR`, 192/512 +
maskable icons) and `public/sw.js` (caches only the app shell — HTML, JS, CSS, icons; **never**
video, playlists, catalogues or API responses; network-first for navigations, cache-first for hashed
assets; `/api/*` always bypasses the cache).

**58. App brightness veil** — Shared
`src/plataforma/brilho.ts` — a black overlay (`opacity = 1 - v/100`) over the whole page, 0–100 %.
Below **5 %** the app "swallows" key/pointer events so the first press only restores the brightness
(ramping back over 1.5 s) — otherwise a user who cannot see the screen would never find the control.
Shared with the native TV app.

**59. Server kill-switch ("fontes desativadas")** — Shared
`src/services/fontesDesativadas.ts` — a public JSON list of server names that are down, downloaded
without a secret and cached for **1 h** (`localStorage['saimo-fontes-desativadas']`), plus a
hard-coded set of permanently dead servers. Disabled servers are removed from every channel and
every film source; the channel grid rebuilds without a page reload (polled at the cache TTL).

**60. Toasts** — Shared
`src/components/Toast.tsx` — success / error / info variants with distinct icons, auto-dismissed
after 3 s, dismissed on click.

**61. Telemetry (disabled by default)** — Shared
`src/services/telemetria.ts` — device UUID in `localStorage['saimo-telemetria-id']`, no account, no
IP storage (the city is derived at the Cloudflare edge), `sendBeacon` with `text/plain` so there is
no CORS preflight. Reports open/start/played/failed/dropped/stopped and searches. `DESLIGADA = true`
in the current code — the sampling percentage comes from `public/telemetria.json` (currently 0 %).

---

## C. EPG implementation details

### C.1 Sources and priority

The EPG is assembled in a **Web Worker** (`src/workers/epgWorker.ts`, 720 lines) so that the ~18 MB
of XML never touches the main thread. The order is deliberate:

1. **`guiadetv.com`** — HTML per channel (`https://www.guiadetv.com/canal/<slug>`); covers ~121
   channels with exact Brasília times, Portuguese synopses, no 429 blocking. Fetched through
   `/api/proxy`, concurrency **6**. Channels are grouped by slug (regional variants share a page).
2. **`meuguia.tv`** — HTML per channel (`https://meuguia.tv/programacao/canal/<code>`), only for
   channels guiadetv did not cover (e.g. Canal Brasil); concurrency **4**, **one retry** after 1.5 s.
3. **XMLTV feeds** — listed in `guia.txt`; parsed with a streaming regex over `response.body`
   (`<(channel|programme)…</\1>`) so the 15 MB feed is never held as one string. These *enrich*
   (poster, synopsis, episode, category) but **do not overwrite the times** of meuguia/guiadetv.
4. **Pluto TV guide** (i.mjh.nz, fetched directly without the proxy because of CORS) — matched by the
   Pluto id embedded in the channel URL (`plu-<24 hex>` / `images.pluto.tv/channels/<24 hex>`).
   If Pluto is the channel's **principal** source, only its guide is used; if it is a fallback
   (TV Cultura, CNBC…), its generic schedule is used only when nothing else exists.

**Server-side pre-built guide**: `epg.json` at the channels address is fetched first
(`{ g: {slug: [[start, stop, title, synopsis?]]}, m: {code: [[start, stop, title, genre?]]} }`,
times in seconds) — it exists to avoid routing every page through the paid `/api/proxy`.

### C.2 Channel matching

The EPG key is the **normalised channel name**, never the site id
(`src/utils/nomes.ts`: decode entities → strip a leading `BR - ` → NFD + remove diacritics →
lowercase → keep `a-z0-9` → collapse spaces → strip trailing noise tokens `hd|sd|fhd|uhd|4k|br`).
`epgKey(id)` reverses the id hyphenation to recover the key.

Matching channels to XMLTV ids (`resolverIds`):
1. **exact** normalised `display-name` match → all ids of that name are claimed;
2. **alias** match (from `alias` lines in `guia.txt`);
3. only then a **loose prefix** match (`k.startsWith(chave) || chave.startsWith(k)`) — and only if
   **exactly one** candidate exists, and never over an id already claimed (so "HBO2" cannot steal the
   id of "HBO 2").

### C.3 Source tables (`guia.txt`)

`src/utils/guiaTabelas.ts` parses a TAB-separated file (lines starting with `#` are comments):

| Type | Fields | Meaning |
|---|---|---|
| `meuguia` | `<channel name>` `<code>` | channel → meuguia.tv code |
| `guiadetv` | `<normalised name>` `<slug or URL>` | channel → guiadetv.com page |
| `alias` | `<normalised name>` `<feed name>` | channel → name inside the XMLTV feed |
| `feed` | `-` `<XMLTV URL>` | feed to parse (order matters) |
| `pluto` | `-` `<Pluto guide URL>` | Pluto TV guide |

The file is fetched from the channels address and cached in `localStorage['saimo-guia-tabelas']`.
**No per-channel table lives in the code** — this file is the single source of truth.

### C.4 Parsing details

- **meuguia** (`parseMeuGuia`): iterates `<li>` items. A `subheader` item is a day header
  (`dd/mm`); a programme item has the start time in `lileft time'>`, the title in `<h2>` and the
  genre in `<h3>`. Times are converted to São Paulo time with the **browser's own** offset
  (`Intl.DateTimeFormat('en-US', {timeZone:'America/Sao_Paulo'})` → `saoPauloOffset`), so the grid
  is correct regardless of the visitor's timezone. The end time is not published: a programme runs
  until the next one starts (last item: +1 h). The list is re-sorted chronologically afterwards
  (the page order is a layout artefact, not a guarantee).
- **guiadetv** (`parseGuiaDeTv`): `<li class="flex gap-4 …">` items with
  `<time datetime="AAAA-MM-DDTHH:MM:SS-03:00">`, a duration span (`1h30` / `45min`), an
  `<a href="/programa/…">` title and a `<p>` synopsis. Duplicate start times are dropped; if the
  computed end passes the next start (or is within 60 s of it) the end is clamped to the next start.
- **XMLTV** (`parseFeed`): attribute extraction is boundary-aware (a space/tab/newline/`<` must
  precede `name="` so `channel` never matches `xchannel`). Date format `20260902020000 -0300`
  (offset optional). Each programme yields title, category, start, stop, poster from `<icon src>`,
  episode from `<episode-num>` (values containing `original-air-date` are discarded — Pluto uses
  that field for the air date) and description from `<desc>`.

### C.5 Overlap handling

`arrumar()` normalises each channel's list: drop zero-length programmes → sort by start →
**drop a programme whose start equals the previous one's start** → **clamp the previous programme's
stop to the new start** if it overlapped. Result: the grid never has overlapping blocks and
"now on air" always picks the right programme. `firstEndingAfter(programs, t)` is a **binary search**
for the first programme that has not ended yet (used hundreds of times per minute by the grid and the
cards).

### C.6 Caching, windowing and notifications

- Window kept: **3 h in the past → 30 h in the future**.
- Cache: `localStorage['saimo-epg-v6']` = `{at, sig, grade}` with a **6 h TTL**; the signature is
  `linkCanais() + '#' + sorted normalised channel names`, so a new channel invalidates the cache
  immediately. Old cache keys (`v3`…`v5`) are deleted on read.
- The cache is written inside `requestIdleCallback` (fallback `setTimeout`) so serialising the grade
  never happens during a scroll.
- Programmes older than **3 h** are dropped when converting to the UI model; titles matching
  `"No Data"` / `"Sem informação"` / empty are discarded.
- The worker posts **partial** messages (only the channels that changed); the service merges them
  into the running grade and notifies listeners with the set of changed channel keys.
  `isEPGReady()` tells the UI "no data now means no data", so the grid can show
  "Sem programação" instead of "Carregando".
- `registerChannels()` restarts the whole worker when the definitive channel list arrives later than
  the first build (this is what previously left GloboNews/Record/Warner without a schedule).
- TV builds cannot use a module worker over `file://` — `criarWorker()` imports
  `../workers/epgWorker?worker&inline` (a blob) when `import.meta.env.VITE_TV` is set.

---

## D. Data / format details

### D.1 Channel catalogue (`catalogo.txt` / `canais.txt`)

`src/services/catalogService.ts` parses **two** files from the channels address:
`catalogo.txt` (authoritative) and `canais.txt` (fallback/extra). Format is line-based
`key: value`:

```
canal:     Nome do Canal
logo:      https://…/logo.png
categoria: TV Aberta
fonte:     https://cdn/x/index.m3u8
referer:   https://cdn/x/
nome:      Temporada 3
qualidade: 1080p
agente:    Mozilla/5.0 …
chave:     <KID hex>:<KEY hex>
```

- `categoria:` becomes the section; `canonizar()` maps free-form labels onto the fixed sections
  (esporte/ppv/futsal → Esportes, filme/serie/cinema → Filmes, infanti/kids/desenho → Infantil,
  document → Documentarios, notic/news → Noticias, novela → Series, aberto → TV Aberta,
  adulto → Adulto, internacion/legendado → Internacionais, `pluto tv` → Pluto TV,
  `24 horas` → 24 Horas, variedade/estilo de vida/entretenimento/religios/4k → Entretenimento);
  unknown labels become their own section; a channel with no category lands in "Outros".
- `fonte:` starts a new source; `referer:`, `nome:`, `qualidade:` and `agente:` apply to the **last**
  source. `chave:` must be `KID:KEY` — otherwise the source is **dropped** (a source that would play
  DRM is worse than one source less).
- If the text starts with `#EXTM3U`, the file is parsed as **M3U** instead (`parseM3u`): `tvg-id` /
  name (a trailing `(ST)` suffix is stripped), `tvg-logo`, `group-title`; duplicate names merge their
  sources.
- `mergeCatalogs()` merges the two files **by normalised name**: the catalogue's channels keep their
  sources first, the extra file's new sources are appended, and channels that exist only in the
  extras are added at the end.
- `toChannels()` sorts by `categoryOrder` (unknown categories last, alphabetical) then by name
  (`localeCompare('pt-BR')`), assigns a **stable id** = slugified name with a `-2`, `-3` suffix for
  collisions ("SporTV HD" vs "SporTV"), and puts **HTTPS sources first** (`httpsFirst`).
- Channel `url` = first source's URL (always HTTPS-preferred).
- Cache: `localStorage['saimo-catalogo-v2']` = `{at, base, catalogo, canais}`, TTL **6 h**, and the
  cache is **invalidated when the address changes** (`base` mismatch). The previous visit's list is
  shown immediately while the new one downloads in the background.
- `restritos.txt` (adult channels) is fetched **only after the parental unlock**, cached in
  `localStorage['saimo-restritos-v1']`, and its channels get the id prefix `restrito-`.

### D.2 User M3U lists

`src/utils/leitorM3u.ts` — see feature 50 for the supported tags. Output shape:

```ts
CanalLido { nome: string; grupo: string | null; logo: string | null; fontes: FonteLida[] }
FonteLida { url; referer; ua; keyId; key; qualidade }
```
Stored in IndexedDB (`saimo-minhas-listas` → store `canais`, keyed by list id); metadata in
`localStorage['saimo-minhas-listas']` = `{id, nome, link|null, atualizadaEm, ativa, total}[]`.
Converted to site channels with id `minha-<listId>-<normalisedName>` and category **"Minhas listas"**.

### D.3 Xtream (server + user + password)

`src/utils/xtream.ts`:
- `normalizarServidor()` → `http://host[:port][/path]` (no scheme → `http://`; panel paths
  `/get.php`, `/player_api.php`, `/panel_api.php`, `/c` are stripped).
- `deLink()` recognises `get.php`/`player_api.php` links with `username` & `password`.
- `verificarEntrada()` → `''` (valid) | `SEM_API` | "Usuário ou senha incorretos" |
  "Essa conta não está ativa (status)" | "Essa conta venceu" (from `user_info.auth/status/exp_date`).
- URLs: `…/get.php?username=…&password=…&type=m3u_plus&output=ts`,
  `…/player_api.php?username=…&password=…[&action=…]`,
  `…/live|/|movie|series/<user>/<pass>/<id>.<ext>`.
- Each live stream is offered as **two** sources: `.m3u8` and `.ts`.

### D.4 VOD catalogue (film/series address)

`src/services/vodService.ts` — the published source list is ~30 MB, so it ships **pre-digested** in
alphabetical shards:

| File | Contents |
|---|---|
| `indice.txt` | `base:<n> <url>` lines (the address prefixes) + one line per letter: `letra\tfilmes\tseries\treservados` |
| `busca.txt` | the search index: `titulo\ttipo(f|s)\tletra\tano` (one line per title, ~30 k titles in <1 MB) |
| `filmes-<L>.txt` / `reservado-<L>.txt` | `titulo\t<versao>=<url>,<url>…` (TAB-separated, `versao` ∈ dub/leg) |
| `series-<L>.txt` | `titulo\tano\tpedaco\tepisodios` |
| `series-<L>-<pedaco>.txt` | `@<titulo>\t<ano>` block header, then `temporada\tnumero\tversao\t<url>,<url>` lines |
| `destaques.txt` | `capa:<base>` + `fila\t<Título>` + `tipo\ttitulo\tletra\tano\tposter` |
| `fichas.txt` | `capa:<base>` + `tipo\ttitulo\ttmdbId\tposter\tgenero1,genero2,…` |
| `categorias.txt` | `tipo\ttitulo\tanime,dorama,novela,kids` |
| `detalhes.txt` | `tipo\ttitulo\tano\tnota\tidade(L\|n)\tpopularidade` |
| `novelinhas.txt` | `base: …` + `id\ttitulo\tpasta\tepisodios\taudio(dub\|leg)\tcapa\tgeneros\tsinopse` |
| `redeflix/links-animes.txt`, `links-doramas.txt` | `@<titulo>\t<ano>\t<tmdbId>` + `temporada\tnumero\tversao\t<urls>` |

URLs inside the catalogue are stored as `baseIndex:rest` (e.g. `0:19927`) and expanded at read time
(the remainder is appended to the base, with `.mp4` added when the remainder has no dot).
`filmes-<L>.txt` and `series-<L>-<n>.txt` are fetched **per letter** (each ≤ ~100 KB) and the
navigation follows the finger. `busca.txt` gives the whole catalogue without downloading it.
`fichas.txt` provides genres, posters and TMDB ids (used for the ficha, the genre rails, the Kids
filter and the year/rating filters). `detalhes.txt` provides the On Demand filters.
`novelinhas.txt` episode URL = `base + pasta + "/episode-" + NNN + ".mp4"`.
All reads are memoised per visit and dropped when the film address changes.

### D.5 EPG XMLTV

Standard XMLTV (`<tv><channel id="…"><display-name>…</display-name></channel><programme start="20260902020000 -0300" stop="…" channel="…"><title>…</title><desc>…</desc><category>…</category><icon src="…"/><episode-num>…</episode-num></programme></tv>`),
parsed by streaming regex, not by a DOM parser. `EPG_README.md` documents the iptv-org feeds
(`meuguia.tv`, `claro.com.br`, `mi.tv` — `…/epg/guides/pt/<site>.epg.xml.gz`) and an old
`channelToXmltvId` map in `epgService.ts`; **that map no longer exists** in the code — matching is now
by name (see C.2). `EPG_README.md` is therefore stale.

### D.6 Addons / Debrid formats

`src/utils/addonProtocol.ts` (ES5-safe, no DOM/Node deps):
`SAIMO_ADDONS_KEY='saimo_addons_v1'`, `SAIMO_DEBRID_KEY='saimo_debrid_v1'`,
`SAIMO_IMDB_CACHE_KEY='saimo_imdb_cache_v1'`.
`AddonRecord = {id, baseUrl, manifestUrl, queryString, manifest, enabled, addedAt, order}`;
`StremioManifest = {id, name, version, description, logo, background, types[], resources[], catalogs[], idPrefixes?, behaviorHints?}`.
Sync payload `{t:"dados", addons?, debrid?}`.

### D.7 `localStorage` / storage keys

| Key | Content |
|---|---|
| `tv-favorites` | `string[]` of channel ids |
| `tv-last-channel` | last channel id |
| `tv-volume` | live volume (0–1) |
| `movie-volume` | VOD volume (0–1) |
| `movie-skip-time` | skip seconds (5/10/15/30/60) |
| `movie-progress-<movieId>` | resume seconds |
| `current-movie-id` | currently playing title |
| `adult-mode-global` | `true` when the adult mode is unlocked |
| `saimo-link-canais`, `saimo-link-filmes` | the two list addresses (base with trailing `/`) |
| `saimo-sem-listas-v1` | one-shot migration marker |
| `saimo-catalogo-v2` | `{at, base, catalogo, canais}` |
| `saimo-restritos-v1` | `{base, texto}` (adult channels) |
| `saimo-epg-v6` (v3–v5 deleted) | `{at, sig, grade}` |
| `saimo-guia-tabelas` | `{base, texto}` (guia.txt) |
| `saimo-radios-lista`, `saimo-radios-recentes` | radio list cache / recents |
| `saimo-buscas-recentes` | recent searches (max 8) |
| `saimo-vod-favoritos` | `ItemDestaque[]` |
| `saimo-continuar` | `Andamento[]` (max 20) |
| `saimo-canais-personalizados` | imported channels |
| `saimo-minhas-listas` | `MinhaLista[]` (metadata; payloads in IndexedDB) |
| `saimo-zero-delay` | `off \| equilibrado \| agressivo` |
| `saimo-audio-inteligente` | `true/false` |
| `saimo-upscaling` | `off \| hd \| 4k` |
| `saimo-ao-iniciar` | `inicio \| ultimo` |
| `saimo-brilho` | 0–100 |
| `saimo-parental-senha` | sha256 hash |
| `saimo-perfis`, `saimo-perfil-ativo`, `saimo-perfil-perguntar`, `saimo-perfis-removidos` | profiles |
| `saimo-perfil-escolhido` | sessionStorage flag |
| `saimo-fontes-desativadas` | disabled servers (1 h) |
| `saimo-sem-cors` | hosts that need the proxy |
| `saimo-segmento-direto` | per-host direct/proxy decision (6 h) |
| `saimo-novelinhas-progresso` | vertical player resume points |
| `saimo-doacao-pos-v2` | floating donation button position |
| `saimo-dica-app-fechada` | timestamp of the last "use the app" dismissal (7 days) |
| `saimo-extensao-bloqueada` | extension blocker flag |
| `saimo-telemetria-id`, `saimo-telemetria-amostra` | telemetry |
| `saimo_addons_v1`, `saimo_debrid_v1`, `saimo_imdb_cache_v1` | addons / debrid / IMDb bridge |
| `legenda-idioma` | preferred subtitle language |
| `saimo-entrada`, `saimo-entrou` | session/visit flags |
| `saimo-dev` | enables the internal `/teste` route |
| `movie-progress-*`, and `<key>@<profileId>` | per-profile suffixing (feature 46) |

**IndexedDB**: database `saimo-minhas-listas`, object store `canais`.
**Cache Storage**: only the app shell (`saimo-casca-v4`).

### D.8 Proxy contract

`/api/proxy?url=<encoded>[&referer=][&ua=][&raw=1]` (Cloudflare Pages Function, mirrored at
`api/proxy.ts` for Vercel on the Node runtime):
- sends `User-Agent` (default Chrome UA) and `Referer` (default `scheme//host/`), `Origin`, `Accept`,
  `Accept-Language: pt-BR`; forwards `Range`;
- follows up to 15 redirects manually, restoring the original Referer/Origin and accumulating
  `Set-Cookie`;
- **rewrites HLS playlists** (`rewriteM3u8`) and DASH manifests (`rewriteMpd`) so every child URL
  goes through the proxy, and fixes the "segments live in the `url=` folder" case
  (`fixNestedPath`);
- `raw=1` returns the bytes untouched — required for downloading a user M3U (otherwise `#EXTM3U`
  would be treated as a playlist and every channel URL rewritten);
- retries 5xx twice with a 300 ms gap; falls back to other headers on 403/404;
- special-cases `*.77zzhf54vdll71.com` by upgrading to https and dropping the port;
- `sondarProxy()` probes `/api/proxy` **without** `url`: a live proxy answers 400 JSON, a quota-cut
  proxy answers the site HTML → the app switches to the mirror (`saimo-tv2.pages.dev`).

`/api/resolve-vod?url=` — a **strict allowlist** resolver (not a generic proxy): only
`dbonline.tech-cdn.top` and `hubby-dmca.com` `/movie/` and `/series/` paths; it follows up to 3
redirects, only accepting `zjo.lat` as an intermediary and only the known final hosts; the final URL
must be HTTPS and start with `/t/` (DBOnline) or carry a `token` (Hubby). Returns `{url}` or `{url:null}`.

`/api/stream?tipo=movie&id=…[&tipo=series&id=…&s=&e=&versao=]` — the Saimo relay: resolves FenixFlix,
Magnetflix, FrostStream, HollyPlayer, RedeFlix and NixPlay, picks the **highest-bandwidth** variant
from the HLS master, and 302-redirects to the fresh token URL. This is why `/api/stream` cannot be
used as a static source URL: the player must resolve it **at open time**.

---

## E. Notable UX details worth porting

**Loading / empty / error states**
- Distinct loading visuals: `LogoCarregando` (the channel/movie logo greyscale that fills in with
  colour as the video opens, never completing before playback) for players, a "Carregando…" spinner
  for lazy routes (`LoadingFallback` with a TV glyph), "Carregando mais canais…" for the channel
  list sentinel, "Buscando a programação…" / "Carregando a programação…" for the EPG.
- Empty states are **actionable**, not apologetic: `SemLista` ("Bem-vindo ao Saimo!") explains that a
  list M3U, a Stremio addon or a Saimo address is needed and offers the exact button
  ("Adicionar lista" → `/ajustes#minhas-listas`; "Configurar Addons" → `/ajustes#addons`; "Endereços
  do Saimo"). It is shown per area (canais / filmes / radios / eventos / guia) and **no network
  request is made while it is displayed**.
- "Sem programação" vs "Carregando…" are distinguished once the EPG is ready (`isEPGReady()`).
- "Sem canal encontrado" in the channel list; "Nada encontrado" in the search.

**Toasts and inline notices**
- 3-second toasts for every state change (favourite added/removed, source changed, multi-screen
  toggled, list saved, data cleared, donation unlocked).
- Inline, dismissible notices inside the player (the app-download hint, the Zero Delay mode toast,
  the extension-blocked state) and inline form errors (`listas-erro` with an alert role).

**Audio / language handling**
- Audio-track list only appears when the stream has more than one track; the label falls back to
  `lang` and then to "Faixa N". The chosen track is highlighted "Em uso".
- Live and VOD volumes are stored **separately** (`tv-volume`, `movie-volume`).
- The subtitle language is remembered across titles and auto-enabled when a matching version exists.
- The audio version (dub/leg) is preserved across episodes of a series.
- "Áudio Inteligente" makes quiet channels audible without clipping loud ones.

**Resume / continuity**
- Resume position for VOD (>30 s, removed at ≥95 %, restored only if ≥5 s from the end).
- "Continue assistindo" row on Home with a progress bar and an × to remove.
- Series continue across episodes; the next-episode card appears at the credits.
- Live TV remembers the last channel and can open on it.
- The radio keeps playing while you navigate, and the top bar shows it.

**Failure handling**
- Failures are graded: a transient error while the picture still advances is recovered in place
  (nobody notices); only a long silence (12 s with no picture, 25 s of frozen picture) triggers a
  source switch; only the exhaustion of all sources becomes a user-visible error.
- The VOD player walks the source list silently and only shows an error when the last one fails —
  and even then it offers "Trocar fonte / servidor (N)".
- HTTP-only sources get a **dedicated** explanation with a one-click "open in a separate page",
  rather than a generic error.
- The address of a source is never shown to the user (only "Fonte N", the name and the quality) —
  a deliberate privacy decision repeated in the live sources modal, the VOD sources modal and the
  cast modal.

**Performance techniques worth keeping**
- Channel list and EPG grid render only what is on screen (40-row chunks + IntersectionObserver;
  a 4-line/60-minute quantised viewport in the grid).
- The EPG is built in a worker and published in partial batches; the cache is written in
  `requestIdleCallback`.
- The EPG "current programme" lookup is a binary search.
- Lazy routes (`React.lazy`) for the players, the catalogue, the guide, the tester, the download page
  and the donation page; `manualChunks` splits `vendor-react`, `vendor-hls`, `vendor-shaka`, `player`,
  `catalog` and `tv` — Shaka is only downloaded when a DASH channel is opened.
- `ImagemLazy` uses `IntersectionObserver` on TVs (where `loading="lazy"` does not exist) and the
  native attribute elsewhere.
- Broken channel logos are remembered per visit so the row does not flicker.
- The app waits at most 1.5 s for the proxy probe before rendering.

**Accessibility / input**
- Every interactive element has `data-focusable`, `data-focus-key` and `aria-label`.
- Dialogs are `role="dialog" aria-modal="true"`; the TV focus is trapped in the topmost dialog and
  restored on close.
- The remote-control hint overlay (↑↓←→ Navigate / Enter Select / Esc Back) appears the first time
  D-pad keys are used.
- Focus is scrolled into view along the *row* that scrolls, not only the page.
- `Esc` always closes the top layer, in the capture phase, before the D-pad back handler sees it.

**Mobile specifics**
- Bottom tab bar with labels (Início / Ao vivo / Eventos / Filmes e séries / Baixar app); tapping the
  active tab scrolls the page back to the top; the Eventos tab opens a modal because the desktop
  top bar is hidden on phones.
- Landscape phone: the video fills the screen and the channel list becomes a floating drawer.
- Fullscreen locks the orientation to landscape on touch devices and releases it on exit; iPhone uses
  `webkitEnterFullscreen` on the `<video>`.
- The vertical (Novelinha) player requests fullscreen + portrait lock on small screens.

---

## F. Hard to port / platform-specific

| Item | Why it is hard |
|---|---|
| **`http` video on an https page** | Browsers block mixed content; the Cloudflare IPs are also refused by several CDNs (403). The site therefore cannot play a large part of the catalogue and shows the "opens in a separate page" notice. Porting to a different stack must decide: keep the paid proxy, ship a native app, or accept the limitation. `buildAttempts` encodes this whole policy. |
| **Cloudflare Pages Functions budget** | `/api/proxy` is a *paid* invocation per request (millions/day). The design counters it with: direct-first attempts, `segmentoDireto.ts` (segments fetched straight from the origin, with a per-host 6 h memory), `saimo-sem-cors` (hosts that must start at the proxy), and `proxyDeVideoLiberado()` (the site disables video proxying entirely; only the TV/desktop apps keep it). The quota probe + mirror (`saimo-tv2.pages.dev`) is also part of this. |
| **`/api/stream` and `/api/resolve-vod`** | Signed, short-lived URLs: the destination changes per request and may be HLS. The player cannot use the catalogue URL statically. The allowlist resolver is provider-specific and brittle (host + path + token validation, manual redirect following). |
| **Profile storage monkey-patch** | `instalarChavesPorPerfil()` overrides `Storage.prototype.getItem/setItem/removeItem` **before any module loads**. It works but is fragile: it must run first in the entry point, and any code that captures `localStorage.getItem` before the patch (or uses `localStorage` from another realm) bypasses it. |
| **TV package (Tizen/webOS)** | `file://` origin, ES5-only output, no module workers, no `loading="lazy"`, no service worker, limited memory. Needs `core-js`/`whatwg-fetch`/`abortcontroller`/`intersection-observer` polyfills, a blob-URL worker, `__SAIMO_TV__` brand detection, `tizen.tvinputdevice.registerKey`, `webOS.platformBack`, and its own 4K zoom. The native TV app (`tv-native/`) is a **separate codebase** — porting the web app to the TV means porting it twice. |
| **Remote-control key translation** | TV remotes are not keyboards: the app re-dispatches synthetic `KeyboardEvent`s with a forced `keyCode`. Some TVs deliver keys with `key === 'Unidentified'`. Back/Exit semantics differ per brand, hence the double-press-to-exit toast. |
| **Native TV HLS playback** | `hlsNativo()` uses the TV's own decoder (no MSE, no CORS) — but the same URL may fail on one TV and work on another ("The element has no supported sources"), hence the three-step attempt order (hls.js → native → proxy). |
| **Eme / ClearKey (DASH)** | Shaka Player is loaded as a **compiled** bundle at runtime and configured with `keySystem: 'org.w3.clearkey'` + the catalog's KID/KEY. The license is not a server call — it is the key in the catalogue — but EME support varies by browser and the Shaka bundle is ~1 MB. |
| **`mpegts.js`** | Raw MPEG-TS over MSE is only supported where MediaSource exists; the config uses `liveSync*` options that are mpegts-specific. |
| **Chromecast / AirPlay** | The Cast SDK is loaded lazily from Google; AirPlay needs Safari's non-standard API; the Remote Playback and Presentation APIs need their own feature detection. The URL-policy rule (only `http://` may leave the site) is intertwined with the security model. |
| **`crypto.subtle` / SHA-256** | The Kids PIN and the parental PIN are hashed with a **pure-JS SHA-256** (`src/perfis/sha256.ts`) because the TV runs from `file://` where `crypto.subtle` is unavailable in some engines. Porting to a modern-only stack could use the WebCrypto API, but then the TV build must keep a fallback. |
| **`useVoltarFecha` history juggling** | Layered back is implemented by pushing real history entries and batching `history.go(-n)`. It fights with react-router's own index and with browser back gestures; porting it to a different router needs care. |
| **Extension blocker** | Detecting a third-party extension (FlowPick) relies on injected DOM/globals/messages. It is inherently best-effort. |
| **The `catalogo.txt` / `guia.txt` / `detalhes.txt` formats** | These are **not** part of any public standard; they are the Saimo project's private formats. A port must either reimplement the parsers exactly (they are documented above) or negotiate new endpoints. `EPG_README.md` and several files under `docs/` are **stale** (they describe an older AllOrigins-based EPG, `src/assets/ListaBR0*.m3u8` inputs and a `src/data/channels.ts` that no longer exist) — treat the code, not the docs, as the specification. |
| **TMDB API key baked into the client** | `TMDB_API_KEY = '15d2ea6d0dc1d476efbca3eba2b9bbfb'` is shipped in the browser bundle (also `TMDB_CLIENT_KEY` in `addonClient.ts`). Public keys are fine, but the port must decide on its own. |
| **`versao.json` / GitHub release coupling** | The download page and the short links read the latest release of `SaimoTV/saimo-downloads` at runtime. Porting requires keeping that repository or replacing the mechanism. |
| **Third-party content providers** | `functions/api/stream.ts` hard-codes FenixFlix / Magnetflix / FrostStream / HollyPlayer / RedeFlix / NixPlay base URLs and credentials (`NIXPLAY_USER/PASS`). These rot; the code is provider-coupled. |

---

## Appendix — quick file map

| Area | Files |
|---|---|
| Boot / routing | `src/main.tsx`, `src/entrada-tv.ts`, `src/App.tsx` |
| Live TV | `src/components/VideoPlayer.tsx`, `PainelDeCanais.tsx`, `ProgramInfo.tsx`, `ProgramGuide.tsx`, `CartaoNoAr.tsx`, `ChannelCard.tsx`, `Sidebar.tsx` |
| On Demand | `src/components/VodCatalog.tsx`, `MoviePlayer.tsx`, `Novelinhas.tsx`, `src/telas/Inicio.tsx` |
| Screens | `src/telas/{Inicio,Busca,Eventos,Favoritos,Radios,Ajustes,MinhasListas}.tsx` |
| Shared UI | `src/components/{BarraTopo,Telas,Toast,Abertura,AppHeader,NavegacaoMobile,ChannelIcon,ImagemLazy,LogoCarregando,IconeMaterial,AtalhosAjuda,FormListas,DoacaoModal,DoacaoConteudo,AddonsModal,EventsModal,Novidades,AppDownload,ApoiePagina,ChuvaApoiadores}.tsx` |
| TV / D-pad | `src/contexts/{DpadContext.tsx,dpadContexto.ts}`, `src/hooks/{useDpadNavigation,useControleTv,useVoltarFecha,useMediaSession,useKeyboardShortcuts}.ts`, `src/plataforma/tv.ts`, `src/styles/dpad-navigation.css` |
| Profiles | `src/perfis/{perfis,kids,parental,instalar,sha256}.ts`, `EscolhaPerfil.tsx`, `ControleParental.tsx`, `PedirCodigo.tsx` |
| Data | `src/services/{catalogService,vodService,epgService,listas,minhasListas,radios,novelinhas,infoDosTitulos,addons,debrid,fontesDesativadas}.ts`, `src/workers/epgWorker.ts` |
| Playback helpers | `src/utils/{streamUrl,proxyUrl,segmentoDireto,hlsLoader,vigiaAoVivo,sourceOrder,fontePreferida,resolveVodSources,telaCheia,rotas,enderecoLista,guiaTabelas,nomes}.ts`, `src/services/{zeroDelay,audioInteligente,dashPlayer,castService}.ts` |
| Server | `functions/api/{proxy,resolve-vod,stream,events}.ts`, `api/proxy.ts`, `functions/*.ts` (download shortcuts), `functions/_lib/baixar.ts` |
| Packaging | `scripts/{montar_tv.sh,tv_para_site.sh,tv_codigo.sh,tv_es5.mjs,publicar_site.sh}`, `tv/comum/carregador.js`, `tv/tizen/config.xml`, `tv/webos/appinfo.json`, `tv-native/**` |
| Tests | `tests/{minhasListas,xtream,enderecoLista,guiaTabelas,fontePreferida,sourceOrder,modoTv,resolveVod}.test.mjs`, `tests/dados/edge.golden.json` |
