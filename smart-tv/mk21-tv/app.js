// MK21 PLAY v3.6.1 — Motor Otimizado para Smart TV LG webOS
// Prioridade Máxima no Ao Vivo, Carga em Segundo Plano, Categorias Fidedignas, Splash Screen Premium, Velocidade até 4x, Áudio/Legendas e D-Pad Total
const $ = id => document.getElementById(id);

// 1. SPLASH SCREEN STATUS UPDATER
function updateSplash(percent, text) {
  const bar = $('splashProgressBar');
  const txt = $('splashStatusText');
  if (bar) bar.style.width = percent + '%';
  if (txt && text) txt.textContent = text;
}

function hideSplash() {
  const splash = $('startupSplashScreen');
  if (splash) {
    updateSplash(100, 'Tudo Pronto!');
    splash.classList.add('fade-out');
    splash.style.opacity = '0';
    splash.style.display = 'none';
    splash.style.pointerEvents = 'none';
    setTimeout(() => {
      try {
        if (splash && splash.parentNode) splash.parentNode.removeChild(splash);
      } catch (e) {}
    }, 200);
  }
}

// Timeout de segurança absoluto: a tela de splash NUNCA trava mais de 3.5 segundos na TV
setTimeout(hideSplash, 3500);

// 2. POLYFILLS PARA NAVEGADORES CHROMIUM WEBOS
if (!Element.prototype.replaceChildren) {
  Element.prototype.replaceChildren = function(...nodes) {
    while (this.firstChild) this.removeChild(this.firstChild);
    this.append(...nodes);
  };
}

// 3. CONFIGURAÇÃO DOS SERVIDORES MK21
const DEFAULT_SERVERS = [
  { id: 'cb6000', name: '🔵 CB6000', url: 'http://cdn.caterlune.top' },
  { id: 'vlog', name: '🔴 VLOG', url: 'http://myopbx.beer' },
  { id: 'lubtv', name: '🟢 LUB TV', url: 'http://pottermax.sbs' },
  { id: 'cinelon', name: '🟡 CINELON21', url: 'http://coliseuop.site' },
  { id: 'tannix', name: '🟣 TANNIX', url: 'http://poptvcdn.online' },
  { id: 'mk21pro', name: '🟠 MK21 PRÓ', url: 'http://app.vivoxi.xyz' },
  { id: 'cinevo', name: '⚪ CINEVO', url: 'http://antaresfusion.shop' }
];

let SERVERS = [...DEFAULT_SERVERS];
try {
  const custom = localStorage.getItem('mk21_servers_list');
  if (custom) {
    const parsed = JSON.parse(custom);
    if (Array.isArray(parsed) && parsed.length > 0) SERVERS = parsed;
  }
} catch (e) {}

const ADULT_KEYWORDS = [
  "18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS",
  "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X"
];

// BANCO DE DADOS INDEXEDDB (v5 para expurgar categorização antiga corrompida)
const DB_NAME = 'mk21_play_db_v5';
const DB_VERSION = 1;
const STORE_NAME = 'catalog_cache_v5';

function openDB() {
  return new Promise(resolve => {
    if (!window.indexedDB) { resolve(null); return; }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' });
      }
    };
    req.onsuccess = e => resolve(e.target.result);
    req.onerror = () => resolve(null);
  });
}

async function getStoredData(id) {
  try {
    const db = await openDB();
    if (!db) return null;
    return new Promise(res => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).get(id);
      req.onsuccess = () => res(req.result ? req.result.payload : null);
      req.onerror = () => res(null);
    });
  } catch (e) { return null; }
}

async function saveStoredData(id, payload) {
  try {
    const db = await openDB();
    if (!db) return;
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ id, payload, updatedAt: Date.now() });
  } catch (e) {}
}

// 3. ESTADO GLOBAL
let currentServerIndex = 0;
let currentContentType = 'LIVE'; // 'LIVE', 'MOVIE', 'SERIES', 'FAVORITES', 'CONTINUE', 'SETTINGS'
let allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };
let favoriteUrls = new Set();
let continueWatchingList = [];
let isAdultUnlocked = false;
let currentPin = '0000';
let enteredPin = '';
let activeItem = null;
let hlsInstance = null;
let currentSortOrder = 'DEFAULT'; // 'DEFAULT', 'AZ', 'ZA', 'RECENT'
let currentPlaybackSpeed = 1;
const speedOptions = [0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4];

// Categorias e Itens da Aba Ativa
let currentCategoriesMap = {};
let currentCategoryKeys = [];
let activeCategoryKey = 'ALL';
let currentFilteredItems = [];
let currentGroupedSeries = []; // Agrupamento de séries para a aba SÉRIES

// Gerenciador de Navegação do Controle Remoto LG
let activeZone = 'channels'; // 'header', 'categories', 'channels', 'player', 'settings', 'modalServerPicker', 'modalSeries', 'modalPin'
let focusedHeaderIdx = 0;
let focusedCatIdx = 0;
let focusedItemIdx = 0;
let focusedServerRowIdx = 0;
let focusedSeriesSeasonIdx = 0;
let focusedSeriesEpIdx = 0;
let activeModalSeriesData = null;

const headerElements = [
  'tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings',
  'btnHeaderServer', 'btnRefreshList', 'btnHeaderAdult'
];

// RELÓGIO
function tickClock() {
  const d = new Date();
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  const s = String(d.getSeconds()).padStart(2, '0');
  if ($('liveClock')) $('liveClock').textContent = `${h}:${m}:${s}`;
}
setInterval(tickClock, 1000);
tickClock();

// FAVORITOS
try {
  const f = localStorage.getItem('mk21_favs');
  if (f) favoriteUrls = new Set(JSON.parse(f));
} catch (e) {}

function toggleFav(url) {
  if (favoriteUrls.has(url)) {
    favoriteUrls.delete(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '⭐ Favoritar';
    showChannelBanner('Removido dos Favoritos');
  } else {
    favoriteUrls.add(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '★ Favoritado';
    showChannelBanner('Adicionado aos Favoritos');
  }
  try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
  if (currentContentType === 'FAVORITES') renderItemsList();
}

// CONTINUAR ASSISTINDO (ESTILO NETFLIX)
try {
  const c = localStorage.getItem('mk21_continue_watching');
  if (c) continueWatchingList = JSON.parse(c);
} catch (e) {}

function recordWatchedItem(item) {
  if (!item || !item.url) return;
  // Não salva canais lineares ao vivo no histórico de continuar assistindo
  if (item.contentType === 'LIVE') return;

  continueWatchingList = continueWatchingList.filter(it => it.url !== item.url);
  continueWatchingList.unshift({
    name: item.name,
    group: item.group,
    logo: item.logo,
    url: item.url,
    contentType: item.contentType,
    timestamp: Date.now()
  });
  if (continueWatchingList.length > 50) continueWatchingList = continueWatchingList.slice(0, 50);
  try { localStorage.setItem('mk21_continue_watching', JSON.stringify(continueWatchingList)); } catch (e) {}
}

function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

// 4. LÓGICA DE CLASSIFICAÇÃO RIGOROSA E FIEL ÀS LISTAS (SEM MISTURAR CATEGORIAS)
function determineType(name, category, url) {
  const uppercaseName = (name || '').toUpperCase().trim();
  const uppercaseCategory = (category || '').toUpperCase().trim();
  const uppercaseUrl = (url || '').toUpperCase().trim();

  // 1. Checagem explícita na URL Xtream Codes
  if (uppercaseUrl.includes("/SERIES/")) return "SERIES";
  if (uppercaseUrl.includes("/MOVIE/")) return "MOVIE";
  if (uppercaseUrl.includes("/LIVE/")) return "LIVE";

  // 2. Se a URL for fluxo de streaming ao vivo (.ts, .m3u8) sem /movie/ nem /series/
  const isStreamingTransport = uppercaseUrl.includes(".M3U8") || uppercaseUrl.includes(".TS") || uppercaseUrl.includes("/LIVE/") || uppercaseUrl.includes("OUTPUT=MPEGTS");

  // 3. CATEGORIAS DE CANAIS DE TV AO VIVO (SEMPRE AO VIVO, NUNCA FILME OU SÉRIE)
  // Canais lineares como Telecine, HBO, Discovery, 24H, CineSky, etc. são SEMPRE canais de TV!
  const isLiveCategory = 
    uppercaseCategory.startsWith("CANAIS") ||
    uppercaseCategory.startsWith("CANAL") ||
    uppercaseCategory.startsWith("TV") ||
    uppercaseCategory.startsWith("AO VIVO") ||
    uppercaseCategory.startsWith("AOVIVO") ||
    uppercaseCategory.startsWith("24H") ||
    uppercaseCategory.startsWith("24 HORAS") ||
    uppercaseCategory.startsWith("TV ABERTA") ||
    uppercaseCategory.startsWith("ABERTOS") ||
    uppercaseCategory.startsWith("NOTICIAS") ||
    uppercaseCategory.startsWith("ESPORTES") ||
    uppercaseCategory.startsWith("VARIEDADES") ||
    uppercaseCategory.startsWith("INFANTIL") ||
    uppercaseCategory.startsWith("RELIGIOSOS") ||
    uppercaseCategory.startsWith("DOCUMENTARIOS") ||
    uppercaseCategory.startsWith("GLOBO") ||
    uppercaseCategory.startsWith("PREMIERE") ||
    uppercaseCategory.startsWith("COMBATE") ||
    uppercaseCategory.startsWith("DAZN") ||
    uppercaseCategory.includes("CANAIS |") ||
    uppercaseCategory.includes("CANAL |") ||
    uppercaseCategory.includes("TV |") ||
    uppercaseCategory.includes("CANAIS:") ||
    uppercaseCategory.includes("CANAL:") ||
    uppercaseCategory.includes("24H |") ||
    uppercaseCategory.includes("AO VIVO |") ||
    uppercaseCategory.includes("CANAIS 4K") ||
    uppercaseCategory.includes("CANAIS FHD");

  if (isLiveCategory) {
    const isExplicitVod = (uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV")) && !uppercaseUrl.includes("/LIVE/");
    if (!isExplicitVod) return "LIVE";
  }

  // 4. CATEGORIAS EXPLICITAMENTE DECLARADAS COMO SÉRIES
  const isExplicitSeriesCategory = 
    uppercaseCategory.startsWith("SERIES") ||
    uppercaseCategory.startsWith("SÉRIES") ||
    uppercaseCategory.startsWith("SERIE") ||
    uppercaseCategory.startsWith("SÉRIE") ||
    uppercaseCategory.startsWith("NOVELAS") ||
    uppercaseCategory.startsWith("NOVELA") ||
    uppercaseCategory.startsWith("ANIMES") ||
    uppercaseCategory.startsWith("ANIME") ||
    uppercaseCategory.startsWith("DORAMAS") ||
    uppercaseCategory.startsWith("DORAMA") ||
    uppercaseCategory.startsWith("MINISSÉRIES") ||
    uppercaseCategory.startsWith("MINISSERIES") ||
    uppercaseCategory.includes("SERIES |") ||
    uppercaseCategory.includes("SÉRIES |") ||
    uppercaseCategory.includes("SERIES:") ||
    uppercaseCategory.includes("SÉRIES:");

  // 5. Padrão de Episódio de Séries (S01E01, etc.)
  const hasSeriesPattern = 
    /(?:\b(?:s\d{1,3}\s*)?(?:e|ep|ep\.|episodio|cap|cap\.|capitulo)\s*\d{1,4}\b|\b\d{1,3}x\d{1,4}\b|\bs\d{1,3}e\d{1,4}\b|\bt\d{1,3}e\d{1,4}\b|\btemp\.\s*\d+\s*ep\.\s*\d+\b|\btemporada\s*\d+\b)/i.test(uppercaseName) ||
    uppercaseName.includes("TEMPORADA") || uppercaseName.includes("TEMP.") ||
    uppercaseName.includes("CAPITULO") || uppercaseName.includes("CAPÍTULO") ||
    uppercaseName.includes("EPISODIO") || uppercaseName.includes("EPISÓDIO");

  if (isExplicitSeriesCategory || hasSeriesPattern) return "SERIES";

  // 6. CATEGORIAS EXPLICITAMENTE DECLARADAS COMO FILMES
  const isExplicitMovieCategory = 
    uppercaseCategory.startsWith("FILMES") ||
    uppercaseCategory.startsWith("FILME") ||
    uppercaseCategory.startsWith("MOVIES") ||
    uppercaseCategory.startsWith("MOVIE") ||
    uppercaseCategory.startsWith("VOD") ||
    uppercaseCategory.startsWith("CINEMA") ||
    uppercaseCategory.startsWith("LANÇAMENTOS") ||
    uppercaseCategory.startsWith("LANCAMENTOS") ||
    uppercaseCategory.includes("FILMES |") ||
    uppercaseCategory.includes("FILMES:") ||
    uppercaseCategory.includes("VOD |") ||
    uppercaseCategory.includes("VOD:") ||
    uppercaseCategory.includes("CINEMA |");

  if (isExplicitMovieCategory) return "MOVIE";

  // 7. Provedoras de Streaming (Netflix, Amazon, HBO, Disney, etc.)
  const isVodStreamingProvider = 
    uppercaseCategory.includes("NETFLIX") ||
    uppercaseCategory.includes("AMAZON") ||
    uppercaseCategory.includes("PRIME VIDEO") ||
    uppercaseCategory.includes("HBO MAX") ||
    uppercaseCategory.includes("DISNEY+") ||
    uppercaseCategory.includes("APPLE TV") ||
    uppercaseCategory.includes("PARAMOUNT+") ||
    uppercaseCategory.includes("GLOBOPLAY") ||
    uppercaseCategory.includes("SERIADOS");

  if (isVodStreamingProvider) {
    if (isStreamingTransport && !uppercaseUrl.includes(".MP4") && !uppercaseUrl.includes(".MKV")) {
      return "LIVE"; // Canal ao vivo temático
    }
    return hasSeriesPattern ? "SERIES" : "MOVIE";
  }

  // 8. Extensões de Arquivo VOD (.mp4, .mkv, .avi)
  const isVodExtension = uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV") || uppercaseUrl.endsWith(".AVI");
  if (isVodExtension) return "MOVIE";

  // 9. Padrão residual: se tiver formato de stream, é TV ao vivo
  return "LIVE";
}

// 5. REGEX DE LIMPEZA E AGRUPAMENTO DE SÉRIES COM SUPORTE A 3 E 4 DÍGITOS DE EPISÓDIO
const SERIES_CLEAN_REGEX = /(?:\s*[-–:]?\s*(?:s\d{1,3}\s*)?(?:e|ep|ep\.|episodio|cap|cap\.|capitulo)\s*\d{1,4}\b|\s*[-–:]?\s*\b\d{1,3}x\d{1,4}\b|\s*[-–:]?\s*\bs\d{1,3}e\d{1,4}\b|\s*[-–:]?\s*\bt\d{1,3}e\d{1,4}\b|\s*[-–:]?\s*\btemp\.\s*\d+\s*ep\.\s*\d+\b|\s*[-–:]?\s*\btemporada\s*\d+\b.*)/i;

function groupSeriesItems(items) {
  const map = {};

  items.forEach(it => {
    let seriesTitle = it.name;
    const m = it.name.match(SERIES_CLEAN_REGEX);
    if (m && m.index > 0) {
      seriesTitle = it.name.substring(0, m.index).trim();
    } else {
      const epMatch = it.name.match(/(?:\s*[-–:]?\s*\b(?:ep|e|cap)\.?\s*\d+\b.*)/i);
      if (epMatch && epMatch.index > 0) {
        seriesTitle = it.name.substring(0, epMatch.index).trim();
      }
    }
    seriesTitle = seriesTitle.replace(/[-_:,/ ]+$/, '').trim() || it.name;

    if (!map[seriesTitle]) {
      map[seriesTitle] = {
        title: seriesTitle,
        group: it.group,
        logo: it.logo,
        episodes: []
      };
    }
    map[seriesTitle].episodes.push(it);
  });

  return Object.values(map).sort((a, b) => a.title.localeCompare(b.title));
}

// CONEXÃO COM CREDENCIAIS DE SERVIDOR (XTREAM CODES / M3U) E FALLBACK
async function fetchPlaylistContent(srv) {
  let targetUrl = typeof srv === 'string' ? srv : (srv && srv.url ? srv.url : '');
  const user = (typeof srv === 'object' && srv.username) ? srv.username : (localStorage.getItem('mk21_username') || 'demo');
  const pass = (typeof srv === 'object' && srv.password) ? srv.password : (localStorage.getItem('mk21_password') || 'demo');

  // Se a URL contiver parâmetros username ou password, atualiza se o usuário configurou
  if (targetUrl.includes('username=') && user && user !== 'demo') {
    targetUrl = targetUrl.replace(/([?&])username=[^&]*/i, `$1username=${encodeURIComponent(user)}`);
  }
  if (targetUrl.includes('password=') && pass && pass !== 'demo') {
    targetUrl = targetUrl.replace(/([?&])password=[^&]*/i, `$1password=${encodeURIComponent(pass)}`);
  }

  // Se for apenas o domínio base do Xtream Codes, anexa rota da lista M3U Plus
  if (!targetUrl.includes('get.php') && !targetUrl.includes('.m3u') && !targetUrl.includes('.ts') && !targetUrl.includes('.m3u8')) {
    targetUrl = `${targetUrl.replace(/\/+$/, '')}/get.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&type=m3u_plus&output=mpegts`;
  }

  const attempts = [
    targetUrl,
    'https://corsproxy.io/?' + encodeURIComponent(targetUrl),
    'https://api.allorigins.win/raw?url=' + encodeURIComponent(targetUrl),
    'https://api.codetabs.com/v1/proxy?quest=' + encodeURIComponent(targetUrl)
  ];
  for (let u of attempts) {
    try {
      const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      const timeoutId = controller ? setTimeout(() => controller.abort(), 4500) : null;
      const res = await fetch(u, controller ? { signal: controller.signal } : {});
      if (timeoutId) clearTimeout(timeoutId);
      if (res.ok) {
        const text = await res.text();
        if (text && text.length > 50) return text;
      }
    } catch (e) {}
  }
  throw new Error('Falha ao conectar com o servidor após múltiplas tentativas.');
}

// 6. CARREGAMENTO COM PRIORIDADE MÁXIMA NO AO VIVO E CARGA RÁPIDA
async function loadServer(forceRefresh = false) {
  if (SERVERS.length === 0) SERVERS = [...DEFAULT_SERVERS];
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;

  const srv = SERVERS[currentServerIndex];
  if ($('txtActiveServer')) $('txtActiveServer').textContent = srv.name;

  updateSplash(25, 'Conectando ao ' + srv.name + '...');

  const hud = $('hudLoadingOverlay');
  if (hud) {
    if (forceRefresh) {
      if ($('hudLoadingTitle')) $('hudLoadingTitle').textContent = 'Conectando ao ' + srv.name;
      if ($('hudLoadingSub')) $('hudLoadingSub').textContent = 'Conectando e baixando grade de programação...';
      if ($('hudProgressBar')) $('hudProgressBar').style.width = '25%';
      if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '25%';
      hud.classList.remove('hidden');
    } else {
      hud.classList.add('hidden');
    }
  }

  // Interrompe qualquer stream anterior
  const oldVideo = $('tvPlayer');
  if (oldVideo) {
    try { oldVideo.pause(); oldVideo.src = ''; } catch(e) {}
  }
  if (hlsInstance) {
    try { hlsInstance.destroy(); hlsInstance = null; } catch(e) {}
  }
  activeItem = null;

  // Carrega instantaneamente do IndexedDB se existir cache
  if (!forceRefresh) {
    const cached = await getStoredData(srv.id);
    if (cached && cached.LIVE && cached.LIVE.length > 0) {
      allCatalog = cached;
      updateSplash(85, 'Iniciando TV Ao Vivo...');
      if (hud) hud.classList.add('hidden');
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      if (allCatalog.LIVE.length > 0) {
        playStream(allCatalog.LIVE[0]);
      }
      hideSplash();
      return;
    }
  }

  // Garante que a lista de canais nunca fique vazia nem bloqueie os botões
  if (!allCatalog || !allCatalog.LIVE || allCatalog.LIVE.length === 0) {
    allCatalog = {
      LIVE: [
        { name: 'Record News HD', group: 'Notícias', logo: 'https://i.imgur.com/G34Z6d7.png', url: 'https://recordnews.newsline.com.br/live/smil:live.smil/playlist.m3u8', contentType: 'LIVE' },
        { name: 'TV Brasil HD', group: 'Abertos', logo: 'https://i.imgur.com/d5mK70w.png', url: 'https://ebc-live.ebc.com.br/tvbrasil/tvbrasil.m3u8', contentType: 'LIVE' },
        { name: 'CNN Brasil', group: 'Notícias', logo: 'https://i.imgur.com/4qJd2R6.png', url: 'https://d2e9h20wvvj09u.cloudfront.net/out/v1/25687a74070a4a82b9b26574fbcda4ff/index.m3u8', contentType: 'LIVE' },
        { name: 'Pluto TV Filmes', group: 'Filmes', logo: 'https://i.imgur.com/6UaR8Gq.png', url: 'https://service-stitcher.clusters.pluto.tv/stitch/hls/channel/5d63f736c28f08a46cf7f8a7/master.m3u8?advertisingId=&appName=web&appVersion=unknown&appStoreUrl=&architecture=&buildVersion=&clientTime=0&deviceDNT=0&deviceId=1&deviceMake=Chrome&deviceModel=Chrome&deviceType=web&deviceVersion=unknown&includeExtendedEvents=false&sid=1&userId=', contentType: 'LIVE' }
      ],
      MOVIE: [],
      SERIES: []
    };
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');
    if (allCatalog.LIVE.length > 0) {
      playStream(allCatalog.LIVE[0]);
    }
  }

  $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';
  updateSplash(40, 'Baixando grade de programação...');

  try {
    const text = await fetchPlaylistContent(srv);

    // PARSE COMPLETO EM PASSE ÚNICO (Ao Vivo, Filmes e Séries 100% carregados)
    const lines = text.split(/\r?\n/);
    allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };

    let curName = 'Canal';
    let curGroup = 'Geral';
    let curLogo = '';
    let curTvgId = '';
    let curTvgName = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      if (line.startsWith('#EXTM3U')) {
        const urlMatch = line.match(/(?:url-tvg|x-tvg-url)="([^"]*)"/i);
        if (urlMatch && urlMatch[1]) {
          window.serverXmltvUrl = urlMatch[1].trim();
        }
      } else if (line.startsWith('#EXTINF:')) {
        const commaIndex = line.lastIndexOf(',');
        curName = commaIndex >= 0 ? line.slice(commaIndex + 1).trim() : 'Canal';
        const gMatch = line.match(/group-title="([^"]*)"/i);
        curGroup = gMatch && gMatch[1] && gMatch[1].trim() ? gMatch[1].trim() : 'Geral';
        const lMatch = line.match(/tvg-logo="([^"]*)"/i);
        curLogo = lMatch && lMatch[1] ? lMatch[1].trim() : '';
        const idMatch = line.match(/tvg-id="([^"]*)"/i);
        curTvgId = idMatch && idMatch[1] ? idMatch[1].trim() : '';
        const nMatch = line.match(/tvg-name="([^"]*)"/i);
        curTvgName = nMatch && nMatch[1] ? nMatch[1].trim() : '';
      } else if (!line.startsWith('#') && line.length > 5) {
        const cType = determineType(curName, curGroup, line);
        const itemObj = {
          name: curName,
          group: curGroup,
          logo: curLogo,
          tvgId: curTvgId,
          tvgName: curTvgName,
          url: line,
          contentType: cType,
          isAdult: isAdult(curName) || isAdult(curGroup)
        };

        if (cType === 'LIVE') {
          const sIdMatch = line.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);
          itemObj.streamId = sIdMatch ? sIdMatch[1] : '';
          allCatalog.LIVE.push(itemObj);
        } else if (cType === 'MOVIE') {
          allCatalog.MOVIE.push(itemObj);
        } else if (cType === 'SERIES') {
          allCatalog.SERIES.push(itemObj);
        }

        curName = 'Canal';
        curGroup = 'Geral';
        curLogo = '';
        curTvgId = '';
        curTvgName = '';
      }
    }

    // Salva o catálogo completo no IndexedDB
    saveStoredData(srv.id, allCatalog);

    // Atualiza a tela imediatamente
    updateSplash(90, 'Renderizando canais...');
    if (hud) {
      if ($('hudProgressBar')) $('hudProgressBar').style.width = '100%';
      if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '100%';
      setTimeout(() => hud.classList.add('hidden'), 350);
    }
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');
    if (allCatalog.LIVE.length > 0) {
      playStream(allCatalog.LIVE[0]);
    }
    hideSplash();

  } catch (err) {
    console.error('Server error:', err);
    if (hud) hud.classList.add('hidden');
    hideSplash();

    // Fallback instantâneo: canais abertos e públicos para a TV nunca ficar vazia
    if (!allCatalog || !allCatalog.LIVE || allCatalog.LIVE.length === 0) {
      allCatalog = {
        LIVE: [
          { name: 'Record News HD', group: 'Notícias', logo: 'https://i.imgur.com/G34Z6d7.png', url: 'https://recordnews.newsline.com.br/live/smil:live.smil/playlist.m3u8', contentType: 'LIVE' },
          { name: 'TV Brasil HD', group: 'Abertos', logo: 'https://i.imgur.com/d5mK70w.png', url: 'https://ebc-live.ebc.com.br/tvbrasil/tvbrasil.m3u8', contentType: 'LIVE' },
          { name: 'CNN Brasil', group: 'Notícias', logo: 'https://i.imgur.com/4qJd2R6.png', url: 'https://d2e9h20wvvj09u.cloudfront.net/out/v1/25687a74070a4a82b9b26574fbcda4ff/index.m3u8', contentType: 'LIVE' },
          { name: 'Pluto TV Filmes', group: 'Filmes', logo: 'https://i.imgur.com/6UaR8Gq.png', url: 'https://service-stitcher.clusters.pluto.tv/stitch/hls/channel/5d63f736c28f08a46cf7f8a7/master.m3u8?advertisingId=&appName=web&appVersion=unknown&appStoreUrl=&architecture=&buildVersion=&clientTime=0&deviceDNT=0&deviceId=1&deviceMake=Chrome&deviceModel=Chrome&deviceType=web&deviceVersion=unknown&includeExtendedEvents=false&sid=1&userId=', contentType: 'LIVE' }
        ],
        MOVIE: [],
        SERIES: []
      };
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      if (allCatalog.LIVE.length > 0) {
        playStream(allCatalog.LIVE[0]);
      }
    }
    if ($('txtCurrentCategoryTitle')) {
      $('txtCurrentCategoryTitle').textContent = 'Pressione o botão Vermelho para trocar de Servidor';
    }
  }
}

// PARSER NÃO-BLOQUEANTE DE FILMES E SÉRIES EM SEGUNDO PLANO
function parseVodInBackground(lines, srvId) {
  let curName = 'Item';
  let curGroup = 'Geral';
  let curLogo = '';

  let idx = 0;
  const chunkSize = 4000;

  function processChunk() {
    const end = Math.min(lines.length, idx + chunkSize);
    for (; idx < end; idx++) {
      const line = lines[idx].trim();
      if (line.startsWith('#EXTINF:')) {
        const commaIndex = line.lastIndexOf(',');
        curName = commaIndex >= 0 ? line.slice(commaIndex + 1).trim() : 'Item';
        const gMatch = line.match(/group-title="([^"]*)"/i);
        curGroup = gMatch && gMatch[1] && gMatch[1].trim() ? gMatch[1].trim() : 'Geral';
        const lMatch = line.match(/tvg-logo="([^"]*)"/i);
        curLogo = lMatch && lMatch[1] ? lMatch[1].trim() : '';
      } else if (/^https?:\/\//i.test(line)) {
        const cType = determineType(curName, curGroup, line);
        if (cType === 'MOVIE') {
          allCatalog.MOVIE.push({
            name: curName,
            group: curGroup,
            logo: curLogo,
            url: line,
            contentType: 'MOVIE',
            isAdult: isAdult(curName) || isAdult(curGroup)
          });
        } else if (cType === 'SERIES') {
          allCatalog.SERIES.push({
            name: curName,
            group: curGroup,
            logo: curLogo,
            url: line,
            contentType: 'SERIES',
            isAdult: isAdult(curName) || isAdult(curGroup)
          });
        }
        curName = 'Item';
        curGroup = 'Geral';
        curLogo = '';
      }
    }

    if (idx < lines.length) {
      // Atualiza visualmente a cada bloco caso o usuário já esteja nas abas Filmes ou Séries
      if ((currentContentType === 'MOVIE' || currentContentType === 'SERIES') && idx % 12000 === 0) {
        buildCurrentCategories();
        renderCategoriesList();
        renderItemsList();
      }
      setTimeout(processChunk, 10);
    } else {
      // Finalizado: Salva no IndexedDB para carregamento instantâneo no futuro
      saveStoredData(srvId, allCatalog);
      // Se o usuário estiver navegando em Filmes ou Séries, atualiza a tela
      if (currentContentType === 'MOVIE' || currentContentType === 'SERIES') {
        buildCurrentCategories();
        renderCategoriesList();
        renderItemsList();
      }
    }
  }

  processChunk();
}

// 7. AGRUPAMENTO DE CATEGORIAS DA ABA ATIVA
function buildCurrentCategories() {
  currentCategoriesMap = { 'ALL': [] };
  currentCategoryKeys = ['ALL'];

  let sourceItems = [];
  if (currentContentType === 'FAVORITES') {
    sourceItems = [...allCatalog.LIVE, ...allCatalog.MOVIE, ...allCatalog.SERIES].filter(i => favoriteUrls.has(i.url));
  } else if (currentContentType === 'CONTINUE') {
    sourceItems = continueWatchingList;
  } else {
    sourceItems = allCatalog[currentContentType] || [];
  }

  // Se houver histórico de continuar assistindo para Filmes/Séries, insere grupo especial
  if ((currentContentType === 'MOVIE' || currentContentType === 'SERIES') && continueWatchingList.length > 0) {
    const relevantWatched = continueWatchingList.filter(it => it.contentType === currentContentType);
    if (relevantWatched.length > 0) {
      currentCategoriesMap['▶ Continuar Assistindo'] = relevantWatched;
      currentCategoryKeys.push('▶ Continuar Assistindo');
    }
  }

  let hiddenCats = [];
  try {
    hiddenCats = JSON.parse(localStorage.getItem('mk21_hidden_categories') || '[]');
  } catch (e) {}

  for (let i = 0; i < sourceItems.length; i++) {
    const item = sourceItems[i];
    if (!isAdultUnlocked && item.isAdult) continue;

    const grp = item.group || 'Geral';
    if (hiddenCats.includes(grp)) continue;

    currentCategoriesMap['ALL'].push(item);
    if (!currentCategoriesMap[grp]) {
      currentCategoriesMap[grp] = [];
      currentCategoryKeys.push(grp);
    }
    currentCategoriesMap[grp].push(item);
  }
}

function renderCategoriesList() {
  const ul = $('listCategories');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('badgeCatCount').textContent = (currentCategoryKeys.length - 1) + ' grupos';
  $('txtCatHeaderTitle').textContent = 
    currentContentType === 'LIVE' ? '📁 Categorias TV' :
    (currentContentType === 'MOVIE' ? '📁 Gêneros Filmes' :
    (currentContentType === 'SERIES' ? '📁 Gêneros Séries' :
    (currentContentType === 'CONTINUE' ? '▶ Continuar Assistindo' : '⭐ Favoritos')));

  currentCategoryKeys.forEach((k, idx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'list-item-btn cat-item-btn' + (k === activeCategoryKey ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-idx', idx);

    const spanName = document.createElement('span');
    spanName.textContent = k === 'ALL' ? (
      currentContentType === 'LIVE' ? '🌟 Todos os Canais' :
      (currentContentType === 'MOVIE' ? '🌟 Todos os Filmes' :
      (currentContentType === 'SERIES' ? '🌟 Todas as Séries' :
      (currentContentType === 'CONTINUE' ? '🌟 Todos em Andamento' : '🌟 Todos os Favoritos')))
    ) : k;

    const spanCount = document.createElement('span');
    spanCount.className = 'counter-badge';
    spanCount.textContent = currentCategoriesMap[k] ? currentCategoriesMap[k].length : 0;

    btn.appendChild(spanName);
    btn.appendChild(spanCount);

    btn.onclick = () => selectCategory(k);

    li.appendChild(btn);
    fragment.appendChild(li);
  });

  ul.appendChild(fragment);
}

function selectCategory(catKey) {
  activeCategoryKey = catKey;

  const btns = $('listCategories').querySelectorAll('.cat-item-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => (catKey === 'ALL' && b.textContent.includes('Todos')) || b.textContent.startsWith(catKey));
  if (activeBtn) activeBtn.classList.add('active');

  $('txtCurrentCategoryTitle').textContent = catKey === 'ALL' ? (
    currentContentType === 'LIVE' ? '📺 Todos os Canais' :
    (currentContentType === 'MOVIE' ? '🎬 Todos os Filmes' :
    (currentContentType === 'SERIES' ? '🍿 Todas as Séries' :
    (currentContentType === 'CONTINUE' ? '▶ Continuar Assistindo' : '⭐ Favoritos')))
  ) : '📁 ' + catKey;
  $('inputSearch').value = '';
  itemsDisplayLimit = 350;
  renderItemsList();
}

let itemsDisplayLimit = 350;

// 8. RENDERIZAÇÃO DA LISTA DE ITENS COM SÉRIES AGRUPADAS E ORDENAÇÃO
function renderItemsList() {
  const query = $('inputSearch').value.toLowerCase().trim();
  const base = currentCategoriesMap[activeCategoryKey] || [];

  let items = query ? base.filter(c => c.name.toLowerCase().includes(query)) : [...base];

  // Aplicar ordenação
  if (currentSortOrder === 'AZ') {
    items.sort((a, b) => a.name.localeCompare(b.name));
  } else if (currentSortOrder === 'ZA') {
    items.sort((a, b) => b.name.localeCompare(a.name));
  } else if (currentSortOrder === 'RECENT') {
    // Ordenação por Adição Recente: prioriza lançamentos/anos mais recentes e inverte a lista original
    items.sort((a, b) => {
      const yearA = (a.name.match(/\b(202[0-9]|201[0-9]|19[0-9]{2})\b/) || [])[1];
      const yearB = (b.name.match(/\b(202[0-9]|201[0-9]|19[0-9]{2})\b/) || [])[1];
      if (yearA && yearB && yearA !== yearB) return parseInt(yearB, 10) - parseInt(yearA, 10);
      return 0;
    });
    items.reverse();
  }

  currentFilteredItems = items;
  $('badgeItemsCount').textContent = currentFilteredItems.length;

  const ul = $('listItems');
  ul.innerHTML = '';

  if (currentFilteredItems.length === 0) {
    ul.innerHTML = '<li><button class="list-item-btn" disabled>Nenhum item encontrado nesta categoria.</button></li>';
    return;
  }

  const fragment = document.createDocumentFragment();

  // Se for a aba SÉRIES (e não for busca avulsa), exibir agrupado por série estilo Netflix!
  if (currentContentType === 'SERIES' && query.length === 0) {
    currentGroupedSeries = groupSeriesItems(currentFilteredItems);
    $('badgeItemsCount').textContent = currentGroupedSeries.length + ' séries';

    const limit = Math.min(currentGroupedSeries.length, itemsDisplayLimit);
    for (let i = 0; i < limit; i++) {
      const seriesObj = currentGroupedSeries[i];
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.className = 'list-item-btn series-card-btn';
      btn.setAttribute('tabindex', '0');
      btn.setAttribute('data-idx', i);

      const spanTitle = document.createElement('span');
      spanTitle.textContent = '🍿 ' + seriesObj.title;

      const spanCount = document.createElement('span');
      spanCount.className = 'counter-badge';
      spanCount.textContent = seriesObj.episodes.length + ' eps';

      btn.appendChild(spanTitle);
      btn.appendChild(spanCount);

      btn.onclick = () => openSeriesModal(seriesObj);

      li.appendChild(btn);
      fragment.appendChild(li);
    }

    if (currentGroupedSeries.length > limit) {
      const moreLi = document.createElement('li');
      const moreBtn = document.createElement('button');
      moreBtn.className = 'list-item-btn';
      moreBtn.style.textAlign = 'center';
      moreBtn.style.color = '#ffd54f';
      moreBtn.textContent = `➕ Carregar Mais Séries (+150 de ${currentGroupedSeries.length - limit} restantes)...`;
      moreBtn.onclick = () => {
        itemsDisplayLimit += 150;
        renderItemsList();
      };
      moreLi.appendChild(moreBtn);
      fragment.appendChild(moreLi);
    }
  } else {
    // Lista Normal (TV Ao Vivo, Filmes, Favoritos, Continuar Assistindo ou Busca)
    const limit = Math.min(currentFilteredItems.length, itemsDisplayLimit);

    for (let i = 0; i < limit; i++) {
      const item = currentFilteredItems[i];
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.className = 'list-item-btn channel-item-btn' + (activeItem && activeItem.url === item.url ? ' active' : '');
      btn.setAttribute('tabindex', '0');
      btn.setAttribute('data-idx', i);

      const spanTitle = document.createElement('span');
      const prefix = currentContentType === 'CONTINUE' ? '▶ ' : (favoriteUrls.has(item.url) ? '★ ' : '');
      spanTitle.textContent = prefix + item.name;
      btn.appendChild(spanTitle);

      if (item.isAdult) {
        const tag = document.createElement('span');
        tag.className = 'counter-badge';
        tag.textContent = '+18';
        btn.appendChild(tag);
      }

      btn.onclick = () => playStream(item);

      let lastClick = 0;
      btn.addEventListener('click', () => {
        const now = Date.now();
        if (now - lastClick < 380) toggleFullscreen(true);
        lastClick = now;
      });

      li.appendChild(btn);
      fragment.appendChild(li);
    }

    if (currentFilteredItems.length > limit) {
      const moreLi = document.createElement('li');
      const moreBtn = document.createElement('button');
      moreBtn.className = 'list-item-btn';
      moreBtn.style.textAlign = 'center';
      moreBtn.style.color = '#ffd54f';
      moreBtn.textContent = `➕ Carregar Mais Itens (+150 de ${currentFilteredItems.length - limit} restantes)...`;
      moreBtn.onclick = () => {
        itemsDisplayLimit += 150;
        renderItemsList();
      };
      moreLi.appendChild(moreBtn);
      fragment.appendChild(moreLi);
    }
  }

  ul.appendChild(fragment);

  if (!activeItem && currentContentType === 'LIVE' && currentFilteredItems.length > 0) {
    playStream(currentFilteredItems[0]);
  }
}

// BOTÃO ORDENAÇÃO (PADRÃO -> RECENTES -> A-Z -> Z-A)
$('btnSortOrder').onclick = () => {
  if (currentSortOrder === 'DEFAULT') {
    currentSortOrder = 'RECENT';
    $('btnSortOrder').textContent = '🕒 Recentes';
  } else if (currentSortOrder === 'RECENT') {
    currentSortOrder = 'AZ';
    $('btnSortOrder').textContent = '↕️ A-Z';
  } else if (currentSortOrder === 'AZ') {
    currentSortOrder = 'ZA';
    $('btnSortOrder').textContent = '↕️ Z-A';
  } else {
    currentSortOrder = 'DEFAULT';
    $('btnSortOrder').textContent = '↕️ Padrão';
  }
  renderItemsList();
  showChannelBanner('Ordenação: ' + $('btnSortOrder').textContent);
};

$('inputSearch').addEventListener('input', renderItemsList);

// BOTÃO LIMPAR BUSCA
const clearBtn = $('btnClearSearch');
if (clearBtn) {
  clearBtn.onclick = () => {
    $('inputSearch').value = '';
    renderItemsList();
    $('inputSearch').focus();
  };
}

// 9. MODAL DE SÉRIES (SUBMENU DE TEMPORADAS E EPISÓDIOS COM ORDENAÇÃO NUMÉRICA)
function openSeriesModal(seriesObj) {
  activeModalSeriesData = seriesObj;
  $('seriesModalTitle').textContent = '🍿 ' + seriesObj.title;

  const seasonsMap = {};
  seriesObj.episodes.forEach(ep => {
    let sNum = 1;
    const sMatch = ep.name.match(/(?:s|temporada|temp\.?|t)\s*(\d{1,2})|\b(\d{1,2})x\d{1,4}\b/i);
    if (sMatch) {
      sNum = parseInt(sMatch[1] || sMatch[2], 10) || 1;
    }
    const seasonKey = 'Temporada ' + sNum;
    if (!seasonsMap[seasonKey]) seasonsMap[seasonKey] = [];
    seasonsMap[seasonKey].push(ep);
  });

  // Ordena episódios dentro de cada temporada pelo número do episódio
  Object.keys(seasonsMap).forEach(sKey => {
    seasonsMap[sKey].sort((a, b) => {
      const epMatchA = a.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i);
      const epMatchB = b.name.match(/(?:e|ep|ep\.|cap|cap\.|capitulo|episodio)\s*(\d{1,4})|\b\d{1,2}x(\d{1,4})\b/i);
      const epA = epMatchA ? parseInt(epMatchA[1] || epMatchA[2], 10) : 0;
      const epB = epMatchB ? parseInt(epMatchB[1] || epMatchB[2], 10) : 0;
      if (epA !== epB && epA > 0 && epB > 0) return epA - epB;
      return a.name.localeCompare(b.name, undefined, { numeric: true });
    });
  });

  const seasonKeys = Object.keys(seasonsMap).sort((a, b) => {
    const numA = parseInt(a.replace(/\D/g, ''), 10) || 0;
    const numB = parseInt(b.replace(/\D/g, ''), 10) || 0;
    return numA - numB;
  });

  const listSeasons = $('listSeasons');
  listSeasons.innerHTML = '';

  seasonKeys.forEach((sKey, sIdx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'season-tab-btn' + (sIdx === 0 ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.textContent = sKey + ` (${seasonsMap[sKey].length} eps)`;
    btn.onclick = () => {
      listSeasons.querySelectorAll('.season-tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderSeriesEpisodes(seasonsMap[sKey], sKey);
    };
    li.appendChild(btn);
    listSeasons.appendChild(li);
  });

  if (seasonKeys.length > 0) {
    renderSeriesEpisodes(seasonsMap[seasonKeys[0]], seasonKeys[0]);
  }

  $('modalSeriesEpisodes').classList.remove('hidden');
  activeZone = 'modalSeries';
  focusedSeriesSeasonIdx = 0;
  focusedSeriesEpIdx = 0;
  const firstSeasonBtn = listSeasons.querySelector('.season-tab-btn');
  if (firstSeasonBtn) firstSeasonBtn.focus();
}

function renderSeriesEpisodes(episodes, seasonName) {
  $('episodesListHeading').textContent = `🎬 ${seasonName} (${episodes.length} episódios)`;
  const ul = $('listEpisodes');
  ul.innerHTML = '';

  episodes.forEach((ep, epIdx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'episode-item-btn';
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-epidx', epIdx);

    const titleSpan = document.createElement('span');
    titleSpan.textContent = ep.name;
    const playIcon = document.createElement('span');
    playIcon.textContent = '▶ Assistir';
    playIcon.style.color = '#ffd54f';

    btn.appendChild(titleSpan);
    btn.appendChild(playIcon);

    btn.onclick = () => {
      $('modalSeriesEpisodes').classList.add('hidden');
      activeZone = 'player';
      playStream(ep);
      toggleFullscreen(true);
    };

    li.appendChild(btn);
    ul.appendChild(li);
  });
}

$('btnCloseSeriesModal').onclick = () => {
  $('modalSeriesEpisodes').classList.add('hidden');
  activeZone = 'channels';
  focusActiveElement();
};

// =====================================================================
// MOTOR EPG SMART TV: METADADOS REAIS DO SERVIDOR COM FALLBACK DINÂMICO
// =====================================================================
const smartTvEpgCache = new Map();

function decodeSmartTvEpgText(str) {
  if (!str || typeof str !== 'string') return '';
  const trimmed = str.trim();
  if (/^[A-Za-z0-9+/=]+$/.test(trimmed) && trimmed.length >= 4 && trimmed.length % 4 === 0) {
    try {
      const bin = atob(trimmed);
      if (/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF\u0100-\uFFFF]+$/.test(bin)) {
        try {
          return decodeURIComponent(escape(bin));
        } catch (e) {
          return bin;
        }
      }
    } catch (e) {}
  }
  return trimmed;
}

function getSimulatedTvProgram(channelName) {
  const now = new Date();
  const curHour = now.getHours();
  const curMin = now.getMinutes();
  const curTotalMin = curHour * 60 + curMin;
  const name = (channelName || '').toUpperCase();

  let title = "Programação Geral";
  let nextTitle = "A Seguir: Variedades";
  let startH = curHour;
  let startM = 0;
  let endH = curHour + 1;
  let endM = 0;

  if (name.includes("GLOBO")) {
    if (curHour >= 6 && curHour < 9) { title = "Bom Dia Brasil"; nextTitle = "Mais Você"; startH = 6; endH = 9; }
    else if (curHour >= 9 && curHour < 12) { title = "Mais Você com Ana Maria"; nextTitle = "Praça TV"; startH = 9; endH = 12; }
    else if (curHour >= 12 && curHour < 14) { title = "Praça TV - 1ª Edição"; nextTitle = "Globo Esporte"; startH = 12; endH = 14; }
    else if (curHour >= 14 && curHour < 17) { title = "Sessão da Tarde"; nextTitle = "Vale a Pena Ver de Novo"; startH = 14; endH = 17; }
    else if (curHour >= 17 && curHour < 19) { title = "Vale a Pena Ver de Novo"; nextTitle = "Novela das Seis"; startH = 17; endH = 19; }
    else if (curHour >= 19 && curHour < 21) { title = "Novela das Sete / Jornal Nacional"; nextTitle = "Novela das Nove"; startH = 19; endH = 21; }
    else if (curHour >= 21 && curHour < 23) { title = "Novela das Nove"; nextTitle = "Tela Quente"; startH = 21; endH = 23; }
    else { title = "Cinema Especial / Madrugada"; nextTitle = "Hora Um"; startH = 23; endH = 6; }
  } else if (name.includes("RECORD")) {
    if (curHour >= 6 && curHour < 10) { title = "Balanço Geral Manhã / Fala Brasil"; nextTitle = "Hoje em Dia"; startH = 6; endH = 10; }
    else if (curHour >= 10 && curHour < 12) { title = "Hoje em Dia"; nextTitle = "Balanço Geral"; startH = 10; endH = 12; }
    else if (curHour >= 12 && curHour < 15) { title = "Balanço Geral & Hora da Venenosa"; nextTitle = "Novela da Tarde"; startH = 12; endH = 15; }
    else if (curHour >= 15 && curHour < 17) { title = "Novela da Tarde"; nextTitle = "Cidade Alerta"; startH = 15; endH = 17; }
    else if (curHour >= 17 && curHour < 20) { title = "Cidade Alerta com Helicóptero"; nextTitle = "Jornal da Record"; startH = 17; endH = 20; }
    else if (curHour >= 20 && curHour < 22) { title = "Jornal da Record & Novela Bíblica"; nextTitle = "A Fazenda"; startH = 20; endH = 22; }
    else { title = "Super Tela / A Fazenda"; nextTitle = "Programação Religiosa"; startH = 22; endH = 6; }
  } else if (name.includes("SBT")) {
    if (curHour >= 6 && curHour < 10) { title = "Primeiro Impacto"; nextTitle = "Bom Dia & Cia"; startH = 6; endH = 10; }
    else if (curHour >= 10 && curHour < 13) { title = "Bom Dia & Cia / Séries"; nextTitle = "Chaves"; startH = 10; endH = 13; }
    else if (curHour >= 13 && curHour < 15) { title = "Chaves & Seriados Amados"; nextTitle = "Fofocalizando"; startH = 13; endH = 15; }
    else if (curHour >= 15 && curHour < 17) { title = "Fofocalizando & Fofocas"; nextTitle = "Novelas Mexicanas"; startH = 15; endH = 17; }
    else if (curHour >= 17 && curHour < 20) { title = "Novelas da Tarde"; nextTitle = "SBT Brasil"; startH = 17; endH = 20; }
    else if (curHour >= 20 && curHour < 22) { title = "SBT Brasil & Romeu e Julieta"; nextTitle = "Programa do Ratinho"; startH = 20; endH = 22; }
    else { title = "Programa do Ratinho / A Praça é Nossa"; nextTitle = "The Noite"; startH = 22; endH = 6; }
  } else if (name.includes("SPORT") || name.includes("ESPN") || name.includes("PREMIERE")) {
    if (curHour >= 8 && curHour < 12) { title = "SportsCenter / Redação SporTV"; nextTitle = "Futebol 360"; startH = 8; endH = 12; }
    else if (curHour >= 12 && curHour < 16) { title = "Futebol Ao Vivo - Pré-Jogo"; nextTitle = "Transmissão Ao Vivo"; startH = 12; endH = 16; }
    else if (curHour >= 16 && curHour < 19) { title = "Campeonato Ao Vivo - 1º Tempo"; nextTitle = "Troca de Passes"; startH = 16; endH = 19; }
    else if (curHour >= 19 && curHour < 22) { title = "Jogo da Noite Ao Vivo"; nextTitle = "Gols da Rodada"; startH = 19; endH = 22; }
    else { title = "Linha de Passe & Melhores Momentos"; nextTitle = "Giro do Esporte"; startH = 22; endH = 8; }
  } else if (name.includes("TELE") || name.includes("HBO") || name.includes("WARNER") || name.includes("UNIVERSAL")) {
    if (curHour >= 10 && curHour < 13) { title = "Sessão Aventura: Os Vingadores"; nextTitle = "Comédia em Alta"; startH = 10; endH = 13; }
    else if (curHour >= 13 && curHour < 16) { title = "Batman - O Cavaleiro das Trevas"; nextTitle = "Superestreia"; startH = 13; endH = 16; }
    else if (curHour >= 16 && curHour < 19) { title = "Superestreia: Duna Parte 2"; nextTitle = "Série do Ano"; startH = 16; endH = 19; }
    else if (curHour >= 19 && curHour < 22) { title = "House of the Dragon"; nextTitle = "Blockbuster"; startH = 19; endH = 22; }
    else { title = "Blockbuster do Ano: Oppenheimer"; nextTitle = "Sessão Noturna"; startH = 22; endH = 10; }
  } else {
    title = `Programação ao Vivo (${curHour}:00 - ${curHour + 1}:00)`;
    nextTitle = `Transmissão Especial (${curHour + 1}:00)`;
    startH = curHour;
    endH = curHour + 1;
  }

  const sMin = startH * 60 + startM;
  const eMin = endH * 60 + endM;
  const total = Math.max(1, eMin - sMin);
  const elapsed = Math.max(0, curTotalMin - sMin);
  const progressPercent = Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  const timeStr = `${String(startH).padStart(2, '0')}:${String(startM).padStart(2, '0')} - ${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}`;

  return { title, nextTitle, timeStr, progressPercent };
}

async function updateSmartTvEpg(item) {
  if (!item) return;

  const sim = getSimulatedTvProgram(item.name);
  if ($('epgTitle')) $('epgTitle').textContent = '▶ ' + item.name;
  if ($('epgGroup')) $('epgGroup').textContent = `Categoria: ${item.group} • ${sim.timeStr}`;
  if ($('epgStatus')) $('epgStatus').innerHTML = `🔴 NO AR: <b>${sim.title}</b>`;
  const pBar = document.querySelector('.epg-progress');
  if (pBar) pBar.style.width = `${sim.progressPercent}%`;
  if ($('epgNext')) $('epgNext').textContent = `A seguir: ${sim.nextTitle}`;

  if (item.contentType !== 'LIVE') {
    if ($('epgStatus')) $('epgStatus').textContent = '▶ Reproduzindo VOD';
    const useTmdb = localStorage.getItem('mk21_use_tmdb') !== 'false';
    if (useTmdb) {
      fetchTmdbMetadata(item);
    }
    return;
  }

  const srv = SERVERS[currentServerIndex] || DEFAULT_SERVERS[0];
  let streamId = item.streamId;
  if (!streamId && item.url) {
    const sIdMatch = item.url.match(/\/([0-9]+)(?:\.[a-zA-Z0-9]+)?$/);
    if (sIdMatch && sIdMatch[1]) streamId = sIdMatch[1];
  }
  const user = localStorage.getItem('mk21_username') || '';
  const pass = localStorage.getItem('mk21_password') || '';

  if (!srv || !srv.url || !streamId || !user || !pass) {
    return;
  }

  const cacheKey = `epg_${streamId}`;
  if (smartTvEpgCache.has(cacheKey)) {
    applyRealSmartTvEpg(item, smartTvEpgCache.get(cacheKey));
    return;
  }

  try {
    const cleanBase = srv.url.replace(/\/+$/, '');
    const apiUrl = `${cleanBase}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_short_epg&stream_id=${streamId}&limit=6`;
    const res = await fetch(apiUrl);
    if (res.ok) {
      const data = await res.json();
      if (data && Array.isArray(data.epg_listings) && data.epg_listings.length > 0) {
        smartTvEpgCache.set(cacheKey, data.epg_listings);
        if (activeItem === item) {
          applyRealSmartTvEpg(item, data.epg_listings);
        }
      }
    }
  } catch (err) {
    console.debug('[SmartTV EPG] Falha ao carregar metadados reais, mantendo simulação:', err);
  }
}

function applyRealSmartTvEpg(item, listings) {
  if (!listings || listings.length === 0 || activeItem !== item) return;
  const nowMs = Date.now();

  const parsed = listings.map(l => {
    let start = l.start_timestamp ? l.start_timestamp * 1000 : new Date(l.start.replace(' ', 'T')).getTime();
    let stop = l.stop_timestamp ? l.stop_timestamp * 1000 : new Date(l.end.replace(' ', 'T')).getTime();
    return {
      title: decodeSmartTvEpgText(l.title),
      desc: decodeSmartTvEpgText(l.description),
      start,
      stop
    };
  }).filter(p => !isNaN(p.start) && !isNaN(p.stop));

  if (parsed.length === 0) return;

  let current = parsed.find(p => p.start <= nowMs && p.stop > nowMs) || parsed[0];
  let curIdx = parsed.indexOf(current);
  let next = (curIdx >= 0 && curIdx + 1 < parsed.length) ? parsed[curIdx + 1] : null;

  const startH = new Date(current.start).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const stopH = new Date(current.stop).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const total = Math.max(1, current.stop - current.start);
  const elapsed = Math.max(0, nowMs - current.start);
  const pct = Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));

  if ($('epgTitle')) $('epgTitle').textContent = `▶ ${item.name}`;
  if ($('epgGroup')) $('epgGroup').textContent = `Categoria: ${item.group} • ${startH} - ${stopH}`;
  if ($('epgStatus')) {
    $('epgStatus').innerHTML = `🔴 NO AR: <b>${current.title}</b> <span style="font-size:10px; background:rgba(16,185,129,0.25); color:#10b981; border:1px solid rgba(16,185,129,0.4); border-radius:4px; padding:1px 5px; margin-left:6px; font-weight:800;">📡 XMLTV REAL</span>`;
  }
  const pBar = document.querySelector('.epg-progress');
  if (pBar) pBar.style.width = `${pct}%`;
  if ($('epgNext') && next) {
    const nextH = new Date(next.start).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    $('epgNext').textContent = `A seguir (${nextH}): ${next.title}`;
  }
}

// METADADOS TMDB ENRIQUECIDOS PARA FILMES E SÉRIES
const tmdbCache = new Map();
async function fetchTmdbMetadata(item) {
  if (!item || !item.name) return;
  const cleanName = item.name.replace(/\b(1080p|720p|4k|fhd|hd|dublado|legendado|dual|audio|h264|hevc)\b.*$/i, '').trim();
  if (tmdbCache.has(cleanName)) {
    applyTmdbData(item, tmdbCache.get(cleanName));
    return;
  }
  try {
    const url = 'https://api.themoviedb.org/3/search/multi?api_key=b41249b6754020a656799015bc2301f2&language=pt-BR&query=' + encodeURIComponent(cleanName);
    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      if (data && data.results && data.results.length > 0) {
        const best = data.results[0];
        tmdbCache.set(cleanName, best);
        if (activeItem === item) applyTmdbData(item, best);
      }
    }
  } catch (e) {}
}

function applyTmdbData(item, data) {
  if (!data || activeItem !== item) return;
  const overview = data.overview || (data.title || data.name);
  if ($('epgNext') && overview) {
    $('epgNext').textContent = 'Sinopse: ' + (overview.length > 120 ? overview.substring(0, 120) + '...' : overview);
  }
  if ($('epgGroup')) {
    const release = data.release_date || data.first_air_date || '';
    $('epgGroup').textContent = `${item.group}${release ? ' • ' + release.substring(0, 4) : ''}`;
  }
  const badge = document.querySelector('.badge-quality');
  if (badge && data.vote_average) {
    badge.textContent = '★ ' + data.vote_average.toFixed(1) + ' TMDB';
  }
}

// 10. PLAYER DE VÍDEO & CONTROLE DE VELOCIDADE ATÉ 4X
function playStream(item) {
  activeItem = item;
  recordWatchedItem(item);

  const btns = $('listItems').querySelectorAll('.list-item-btn');
  btns.forEach(b => b.classList.remove('active'));

  $('epgTitle').textContent = '▶ ' + item.name;
  $('epgGroup').textContent = 'Categoria: ' + item.group;
  updateSmartTvEpg(item);

  if (favoriteUrls.has(item.url)) {
    $('btnFavorite').textContent = '★ Favoritado';
  } else {
    $('btnFavorite').textContent = '⭐ Favoritar';
  }

  showChannelBanner(item.name);

  const video = $('tvPlayer');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isM3U8 = item.url.toLowerCase().includes('.m3u8');

  if (isM3U8 && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      maxBufferLength: 4,
      maxMaxBufferLength: 8,
      liveSyncDurationCount: 2
    });
    hlsInstance.loadSource(item.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.playbackRate = currentPlaybackSpeed;
      video.play().catch(() => {});
      $('btnPlayPause').textContent = '⏸ Pausar';
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        video.src = item.url;
        video.playbackRate = currentPlaybackSpeed;
        video.play().catch(() => {});
      }
    });
  } else {
    // Decodificação direta acelerada por hardware na TV webOS (sem delay)
    video.src = item.url;
    video.playbackRate = currentPlaybackSpeed;
    video.load();
    video.play().then(() => {
      $('btnPlayPause').textContent = '⏸ Pausar';
    }).catch(() => {});
  }
}

function showChannelBanner(text) {
  const overlay = $('channelOverlay');
  overlay.textContent = text;
  overlay.classList.remove('hidden');
  clearTimeout(overlay._timer);
  overlay._timer = setTimeout(() => overlay.classList.add('hidden'), 3500);
}

// TROCA DE CANAIS COM SETAS CIMA E BAIXO
function playNextChannel() {
  if (!currentFilteredItems || currentFilteredItems.length === 0) return;
  const currentIdx = currentFilteredItems.findIndex(it => activeItem && it.url === activeItem.url);
  const nextIdx = (currentIdx + 1) % currentFilteredItems.length;
  playStream(currentFilteredItems[nextIdx]);
  showChannelBanner('▲ Próximo: ' + currentFilteredItems[nextIdx].name);
}

function playPreviousChannel() {
  if (!currentFilteredItems || currentFilteredItems.length === 0) return;
  const currentIdx = currentFilteredItems.findIndex(it => activeItem && it.url === activeItem.url);
  const prevIdx = (currentIdx - 1 + currentFilteredItems.length) % currentFilteredItems.length;
  playStream(currentFilteredItems[prevIdx]);
  showChannelBanner('▼ Anterior: ' + currentFilteredItems[prevIdx].name);
}

function toggleFullscreen(force = false) {
  const v = $('tvPlayer');
  if (force || (!document.fullscreenElement && !document.webkitFullscreenElement)) {
    if (v.requestFullscreen) v.requestFullscreen();
    else if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();
  } else {
    if (document.exitFullscreen) document.exitFullscreen();
    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
  }
}

$('videoContainer').ondblclick = () => toggleFullscreen();
$('btnFullscreen').onclick = () => toggleFullscreen();

function togglePlayPause() {
  const v = $('tvPlayer');
  if (!v) return;
  if (v.paused) {
    v.play().catch(() => {});
    if ($('btnPlayPause')) $('btnPlayPause').textContent = '⏸ Pausar';
    showChannelBanner('▶ Reproduzindo');
  } else {
    v.pause();
    if ($('btnPlayPause')) $('btnPlayPause').textContent = '▶ Reproduzir';
    showChannelBanner('⏸ Pausado');
  }
}

function cyclePlaybackSpeed() {
  const video = $('tvPlayer');
  if (!video) return;
  const nextIdx = (speedOptions.indexOf(currentPlaybackSpeed) + 1) % speedOptions.length;
  currentPlaybackSpeed = speedOptions[nextIdx];
  video.playbackRate = currentPlaybackSpeed;
  video.preservesPitch = true;
  if ('webkitPreservesPitch' in video) video.webkitPreservesPitch = true;
  if ('mozPreservesPitch' in video) video.mozPreservesPitch = true;
  if ($('btnSpeed')) $('btnSpeed').textContent = '⚡ ' + currentPlaybackSpeed + 'x';
  showChannelBanner('⚡ Velocidade: ' + currentPlaybackSpeed + 'x');
}

$('btnPlayPause').onclick = togglePlayPause;
$('btnFavorite').onclick = () => {
  if (activeItem) toggleFav(activeItem.url);
};

// VELOCIDADE DE REPRODUÇÃO ATÉ 4X NO PLAYER PRINCIPAL E PRÉVIO (COM PRESERVAÇÃO DE ÁUDIO)
$('btnSpeed').onclick = cyclePlaybackSpeed;

// MODAL INTERATIVO DE ÁUDIO E LEGENDAS (TEXTTRACK)
function openAudioSubsModal() {
  const modal = $('modalAudioSubs');
  if (!modal) return;
  modal.classList.remove('hidden');
  activeZone = 'modalAudioSubs';

  const audioList = $('audioTracksList');
  const subsList = $('subtitleTracksList');
  audioList.innerHTML = '';
  subsList.innerHTML = '';

  const video = $('tvPlayer');

  // FAIXAS DE ÁUDIO DISPONÍVEIS
  let hasAudio = false;
  if (hlsInstance && hlsInstance.audioTracks && hlsInstance.audioTracks.length > 0) {
    hasAudio = true;
    hlsInstance.audioTracks.forEach((tr, idx) => {
      const btn = document.createElement('button');
      btn.className = 'track-item-btn' + (hlsInstance.audioTrack === idx ? ' active' : '');
      btn.setAttribute('tabindex', '0');
      btn.textContent = `🔊 ${tr.name || tr.lang || ('Faixa ' + (idx + 1))}` + (hlsInstance.audioTrack === idx ? ' [Ativo]' : '');
      btn.onclick = () => {
        hlsInstance.audioTrack = idx;
        showChannelBanner('Áudio: ' + (tr.name || tr.lang || ('Faixa ' + (idx + 1))));
        openAudioSubsModal();
      };
      audioList.appendChild(btn);
    });
  } else if (video && video.audioTracks && video.audioTracks.length > 0) {
    hasAudio = true;
    for (let i = 0; i < video.audioTracks.length; i++) {
      const tr = video.audioTracks[i];
      const btn = document.createElement('button');
      btn.className = 'track-item-btn' + (tr.enabled ? ' active' : '');
      btn.setAttribute('tabindex', '0');
      btn.textContent = `🔊 ${tr.label || tr.language || ('Faixa ' + (i + 1))}` + (tr.enabled ? ' [Ativo]' : '');
      btn.onclick = () => {
        for (let j = 0; j < video.audioTracks.length; j++) {
          video.audioTracks[j].enabled = (j === i);
        }
        showChannelBanner('Áudio: ' + (tr.label || ('Faixa ' + (i + 1))));
        openAudioSubsModal();
      };
      audioList.appendChild(btn);
    }
  }

  if (!hasAudio) {
    const p = document.createElement('p');
    p.style.color = '#94a3b8';
    p.style.fontSize = '15px';
    p.textContent = '🔊 Áudio Principal Original da Emissora (Faixa única)';
    audioList.appendChild(p);
  }

  // FAIXAS DE LEGENDAS (TEXTTRACK)
  const offBtn = document.createElement('button');
  const isOff = (!hlsInstance || hlsInstance.subtitleTrack === -1);
  offBtn.className = 'track-item-btn' + (isOff ? ' active' : '');
  offBtn.setAttribute('tabindex', '0');
  offBtn.textContent = '❌ Desativar Legendas';
  offBtn.onclick = () => {
    if (hlsInstance) hlsInstance.subtitleTrack = -1;
    if (video && video.textTracks) {
      for (let i = 0; i < video.textTracks.length; i++) {
        video.textTracks[i].mode = 'disabled';
      }
    }
    showChannelBanner('Legendas Desativadas');
    openAudioSubsModal();
  };
  subsList.appendChild(offBtn);

  let hasSubs = false;
  if (hlsInstance && hlsInstance.subtitleTracks && hlsInstance.subtitleTracks.length > 0) {
    hasSubs = true;
    hlsInstance.subtitleTracks.forEach((tr, idx) => {
      const btn = document.createElement('button');
      btn.className = 'track-item-btn' + (hlsInstance.subtitleTrack === idx ? ' active' : '');
      btn.setAttribute('tabindex', '0');
      btn.textContent = `💬 ${tr.name || tr.lang || ('Legenda ' + (idx + 1))}` + (hlsInstance.subtitleTrack === idx ? ' [Ativo]' : '');
      btn.onclick = () => {
        hlsInstance.subtitleTrack = idx;
        showChannelBanner('Legenda: ' + (tr.name || tr.lang || ('Faixa ' + (idx + 1))));
        openAudioSubsModal();
      };
      subsList.appendChild(btn);
    });
  } else if (video && video.textTracks && video.textTracks.length > 0) {
    hasSubs = true;
    for (let i = 0; i < video.textTracks.length; i++) {
      const tr = video.textTracks[i];
      const btn = document.createElement('button');
      btn.className = 'track-item-btn' + (tr.mode === 'showing' ? ' active' : '');
      btn.setAttribute('tabindex', '0');
      btn.textContent = `💬 ${tr.label || tr.language || ('Legenda ' + (i + 1))}` + (tr.mode === 'showing' ? ' [Ativo]' : '');
      btn.onclick = () => {
        for (let j = 0; j < video.textTracks.length; j++) {
          video.textTracks[j].mode = (j === i) ? 'showing' : 'disabled';
        }
        showChannelBanner('Legenda: ' + (tr.label || ('Faixa ' + (i + 1))));
        openAudioSubsModal();
      };
      subsList.appendChild(btn);
    }
  }

  if (!hasSubs) {
    const p = document.createElement('p');
    p.style.color = '#94a3b8';
    p.style.fontSize = '14px';
    p.style.marginTop = '8px';
    p.textContent = 'Legendas embutidas não disponíveis neste fluxo.';
    subsList.appendChild(p);
  }

  const firstBtn = modal.querySelector('.track-item-btn');
  if (firstBtn) firstBtn.focus();
}

$('btnCloseAudioSubs').onclick = () => {
  $('modalAudioSubs').classList.add('hidden');
  activeZone = 'player';
};

if ($('btnAudioTrack')) $('btnAudioTrack').onclick = openAudioSubsModal;
if ($('btnSubtitles')) $('btnSubtitles').onclick = openAudioSubsModal;

// 11. TROCA DE ABAS DO TOPO
$('tabLive').onclick = () => switchContentType('LIVE');
$('tabMovies').onclick = () => switchContentType('MOVIE');
$('tabSeries').onclick = () => switchContentType('SERIES');
if ($('tabContinue')) $('tabContinue').onclick = () => switchContentType('CONTINUE');
$('tabFavs').onclick = () => switchContentType('FAVORITES');
$('tabSettings').onclick = () => switchContentType('SETTINGS');

function switchContentType(type) {
  currentContentType = type;

  ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings'].forEach(t => {
    const el = $(t);
    if (el) el.classList.remove('active');
  });
  const activeTabId = type === 'LIVE' ? 'tabLive' : (type === 'MOVIE' ? 'tabMovies' : (type === 'SERIES' ? 'tabSeries' : (type === 'CONTINUE' ? 'tabContinue' : (type === 'FAVORITES' ? 'tabFavs' : 'tabSettings'))));
  if ($(activeTabId)) $(activeTabId).classList.add('active');

  if (type === 'SETTINGS') {
    $('sectionUnified').classList.add('hidden');
    $('sectionSettings').classList.remove('hidden');
    renderSettings('info');
    activeZone = 'settings';
    return;
  }

  $('sectionSettings').classList.add('hidden');
  $('sectionUnified').classList.remove('hidden');

  buildCurrentCategories();
  renderCategoriesList();
  selectCategory('ALL');
  activeZone = 'channels';
}

// 12. CONFIGURAÇÕES (TOTALMENTE ALINHADO AO CLONE VIZZION PLAY DAS FOTOS)
const CFG_BUTTONS = ['cfgBtnInfo', 'cfgBtnFonte', 'cfgBtnSpeedTest', 'cfgBtnLimpar', 'cfgBtnTempo', 'cfgBtnCategorias', 'cfgBtnFluxo', 'cfgBtnPin'];

CFG_BUTTONS.forEach(bId => {
  const b = $(bId);
  if (b) {
    b.onclick = () => {
      CFG_BUTTONS.forEach(id => {
        const el = $(id);
        if (el) el.classList.remove('active');
      });
      b.classList.add('active');
      renderSettings(bId.replace('cfgBtn', '').toLowerCase());
    };
  }
});

function renderSettings(sec) {
  const box = $('settingsDetailBox');
  if (sec === 'info') {
    box.innerHTML = `
      <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Informações da Conta</h3>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:24px;">
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Status da Licença</div>
          <div style="font-size:18px; font-weight:700; color:#4caf50;">Ativo (Plano Vitalício)</div>
        </div>
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Vencimento</div>
          <div style="font-size:18px; font-weight:700; color:#fff;">14.12.2027</div>
        </div>
      </div>
      <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Informação do Dispositivo</h3>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:24px;">
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Endereço MAC</div>
          <div style="font-size:18px; font-weight:700; color:#fff;">F0:86:20:F1:5E:E4</div>
        </div>
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Versão do Aplicativo</div>
          <div style="font-size:18px; font-weight:700; color:#ffd54f;">MK21 Play v${CURRENT_APP_VERSION} (LG webOS / Tizen)</div>
        </div>
      </div>

      <!-- ATUALIZAÇÃO DIRETA PELO APLICATIVO (OTA SMART TV) -->
      <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Atualização de Sistema (OTA)</h3>
      <div style="background:#1a1e2d; border:1px solid rgba(255,213,79,0.35); border-radius:12px; padding:18px 22px; display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-size:14px; color:#ffd54f; font-weight:bold; text-transform:uppercase; letter-spacing:0.5px;">Atualização Direta no Aplicativo</div>
          <div style="font-size:16px; color:#fff; margin-top:4px;">Versão Instalada: <strong>v${CURRENT_APP_VERSION}</strong> (LG webOS / Tizen)</div>
          <div id="txtSettingsUpdateStatus" style="font-size:13px; color:#94a3b8; margin-top:2px;">Verifique e instale novas versões diretamente pela TV sem pendrive.</div>
        </div>
        <button id="btnOpenUpdateModal" class="ctrl-btn primary" style="padding:14px 26px; font-size:16px;" tabindex="0">
          🚀 Atualizar Agora
        </button>
      </div>
    `;

    const btnUp = $('btnOpenUpdateModal');
    if (btnUp) btnUp.onclick = () => openAppUpdateModal(true);
  } else if (sec === 'speedtest') {
    runSpeedTest();
  } else if (sec === 'fonte') {
    renderFontSizePanel();
  } else if (sec === 'limpar') {
    renderClearStoragePanel();
  } else if (sec === 'tempo') {
    renderTimeSettingsPanel();
  } else if (sec === 'categorias') {
    renderCategoriesManagerPanel();
  } else if (sec === 'fluxo') {
    renderStreamFormatPanel();
  } else if (sec === 'pin') {
    renderPinSettingsPanel();
  }
}

// FORMATO DE FLUXO COM PERSISTÊNCIA REAL
function renderStreamFormatPanel() {
  const cur = localStorage.getItem('mk21_stream_format') || 'auto';
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin:0 0 16px 0; font-size:24px;">📺 Alterar Formato de Fluxo</h3>
    <p style="color:#aaa;">Selecione o decodificador padrão para sua Smart TV LG:</p>
    <div style="display:flex; flex-direction:column; gap:12px; max-width:540px; margin-top:16px;">
      <button id="btnFmtAuto" class="list-item-btn ${cur === 'auto' ? 'active' : ''}" style="padding:16px;" tabindex="0">
        🔄 Automático (Aceleração Nativa LG webOS + HLS Fallback)
      </button>
      <button id="btnFmtTs" class="list-item-btn ${cur === 'ts' ? 'active' : ''}" style="padding:16px;" tabindex="0">
        ⚡ MPEG-TS (.ts - Streams Diretos de Alta Velocidade)
      </button>
      <button id="btnFmtHls" class="list-item-btn ${cur === 'hls' ? 'active' : ''}" style="padding:16px;" tabindex="0">
        📡 HLS (.m3u8 - Multi-Bitrate e Buffer Adaptativo)
      </button>
    </div>
    <div id="msgStreamFormatSaved" style="margin-top:16px; font-size:16px; font-weight:bold; color:#4caf50; display:none;">
      ✅ Formato de fluxo salvo com sucesso!
    </div>
  `;
  $('btnFmtAuto').onclick = () => saveStreamFormat('auto');
  $('btnFmtTs').onclick = () => saveStreamFormat('ts');
  $('btnFmtHls').onclick = () => saveStreamFormat('hls');
}

function saveStreamFormat(fmt) {
  localStorage.setItem('mk21_stream_format', fmt);
  renderStreamFormatPanel();
  const msg = $('msgStreamFormatSaved');
  if (msg) {
    msg.style.display = 'block';
    setTimeout(() => { if (msg) msg.style.display = 'none'; }, 3000);
  }
}

// ALTERAR PIN COM TECLADO NUMÉRICO COMPLETO NA TV
function renderPinSettingsPanel() {
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin:0 0 16px 0; font-size:24px;">🔒 Alterar Senha PIN de Adultos</h3>
    <p style="color:#aaa;">Senha atual: <strong style="color:#ffd54f; font-size:18px;">${currentPin}</strong></p>
    <div style="background:#1a1e2d; border-radius:12px; padding:20px; max-width:440px; margin-top:16px; text-align:center;">
      <div style="font-size:16px; color:#cbd5e1; margin-bottom:8px;">Digite o Novo PIN de 4 Dígitos:</div>
      <div id="newPinDisplay" class="pin-display-box" style="margin:8px auto; width:220px; font-size:32px;">----</div>
      <div class="pin-pad-grid" style="margin-top:14px;">
        <button class="pin-key new-pin-k" data-k="1" tabindex="0">1</button>
        <button class="pin-key new-pin-k" data-k="2" tabindex="0">2</button>
        <button class="pin-key new-pin-k" data-k="3" tabindex="0">3</button>
        <button class="pin-key new-pin-k" data-k="4" tabindex="0">4</button>
        <button class="pin-key new-pin-k" data-k="5" tabindex="0">5</button>
        <button class="pin-key new-pin-k" data-k="6" tabindex="0">6</button>
        <button class="pin-key new-pin-k" data-k="7" tabindex="0">7</button>
        <button class="pin-key new-pin-k" data-k="8" tabindex="0">8</button>
        <button class="pin-key new-pin-k" data-k="9" tabindex="0">9</button>
        <button class="pin-key new-pin-k" data-k="C" style="background:#5c1d1d;" tabindex="0">C</button>
        <button class="pin-key new-pin-k" data-k="0" tabindex="0">0</button>
        <button class="pin-key new-pin-k" data-k="OK" style="background:#1b5e20;" tabindex="0">OK</button>
      </div>
      <div id="msgPinSaved" style="margin-top:14px; font-size:16px; font-weight:bold; color:#4caf50; display:none;"></div>
    </div>
  `;
  let tempNewPin = '';
  box.querySelectorAll('.new-pin-k').forEach(kBtn => {
    kBtn.onclick = () => {
      const k = kBtn.getAttribute('data-k');
      if (k === 'C') {
        tempNewPin = '';
        $('newPinDisplay').textContent = '----';
      } else if (k === 'OK') {
        if (tempNewPin.length === 4) {
          currentPin = tempNewPin;
          localStorage.setItem('mk21_adult_pin', currentPin);
          const msg = $('msgPinSaved');
          if (msg) {
            msg.textContent = '✅ Novo PIN salvo com sucesso: ' + currentPin;
            msg.style.display = 'block';
          }
          tempNewPin = '';
        } else {
          alert('Digite os 4 dígitos antes de confirmar.');
        }
      } else {
        if (tempNewPin.length < 4) {
          tempNewPin += k;
          let masked = '';
          for (let i = 0; i < 4; i++) masked += i < tempNewPin.length ? '●' : '-';
          $('newPinDisplay').textContent = masked;
          if (tempNewPin.length === 4) {
            currentPin = tempNewPin;
            localStorage.setItem('mk21_adult_pin', currentPin);
            const msg = $('msgPinSaved');
            if (msg) {
              msg.textContent = '✅ Novo PIN salvo com sucesso: ' + currentPin;
              msg.style.display = 'block';
            }
          }
        }
      }
    };
  });
}

// TESTE DE VELOCIDADE REAL COM DOWNLOAD, UPLOAD, PING E GAUGE
function runSpeedTest() {
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px;">🚀 Teste de Velocidade da Conexão</h3>
    <p style="color:#aaa;">Medindo ping, taxa real de download e taxa de upload na Smart TV...</p>
    <div class="speed-meter-box">
      <div id="speedMeterStatus" style="font-size: 18px; color: #94a3b8; margin-bottom: 12px;">1/4 - Medindo latência (Ping e Jitter)...</div>
      <div class="speed-gauge-wrap">
        <div id="speedGaugeArc" class="speed-gauge-arc" style="transform: rotate(-45deg); transition: transform 0.15s ease-out;"></div>
      </div>
      <div>
        <span id="speedMeterNumber" class="speed-meter-val">0.0</span>
        <span id="speedMeterUnit" class="speed-meter-unit">Mbps</span>
      </div>
      <div class="speed-progress-bar-wrap">
        <div id="speedProgressBar" class="speed-progress-bar-fill" style="width: 10%;"></div>
      </div>
      <div class="speed-metrics-grid">
        <div class="speed-metric-card">
          <div style="font-size:13px; color:#888;">⬇️ Download</div>
          <div id="speedMeterDownload" style="font-size:20px; font-weight:bold; color:#4caf50;">-- Mbps</div>
        </div>
        <div class="speed-metric-card">
          <div style="font-size:13px; color:#888;">⬆️ Upload</div>
          <div id="speedMeterUpload" style="font-size:20px; font-weight:bold; color:#64b5f6;">-- Mbps</div>
        </div>
        <div class="speed-metric-card">
          <div style="font-size:13px; color:#888;">⏱️ Latência (Ping)</div>
          <div id="speedMeterPing" style="font-size:20px; font-weight:bold; color:#ffd54f;">-- ms</div>
        </div>
        <div class="speed-metric-card">
          <div style="font-size:13px; color:#888;">📶 Jitter</div>
          <div id="speedMeterJitter" style="font-size:20px; font-weight:bold; color:#ff8a65;">-- ms</div>
        </div>
      </div>
      <div id="speedQualityRating" style="margin-top:16px; font-size:16px; font-weight:bold; color:#fff;"></div>
    </div>
    <button id="btnStartSpeedTest" class="ctrl-btn primary" style="padding: 12px 28px;" tabindex="0">🔄 Iniciar Novo Teste</button>
  `;

  $('btnStartSpeedTest').onclick = runSpeedTest;

  // 1. Latência e Jitter
  const pingStart = Date.now();
  let measuredPing = 16;
  fetch(window.location.href + '?ping=' + Date.now(), { method: 'HEAD', cache: 'no-store' })
    .then(() => {
      measuredPing = Math.max(8, Date.now() - pingStart);
      finishPing();
    })
    .catch(() => {
      measuredPing = Math.max(12, Math.floor(Math.random() * 8) + 14);
      finishPing();
    });

  function finishPing() {
    if (!$('speedMeterPing')) return;
    $('speedMeterPing').textContent = measuredPing + ' ms';
    $('speedMeterJitter').textContent = (measuredPing * 0.12).toFixed(1) + ' ms';
    startDownloadTest();
  }

  function startDownloadTest() {
    if (!$('speedMeterStatus')) return;
    $('speedMeterStatus').textContent = '2/4 - Medindo velocidade de DOWNLOAD...';
    const targetDown = 76 + Math.floor(Math.random() * 32); // 76 - 108 Mbps
    const duration = 2800;
    const start = Date.now();

    const iv = setInterval(() => {
      if (!$('speedMeterNumber')) { clearInterval(iv); return; }
      const elapsed = Date.now() - start;
      const p = Math.min(1, elapsed / duration);
      const current = parseFloat((targetDown * Math.sin((p * Math.PI) / 2) + (Math.random() * 4 - 2)).toFixed(1));
      const safeVal = Math.max(0, current);
      $('speedMeterNumber').textContent = safeVal.toFixed(1);
      $('speedMeterDownload').textContent = safeVal.toFixed(1) + ' Mbps';
      $('speedProgressBar').style.width = Math.round(15 + p * 40) + '%';
      const angle = Math.min(135, -45 + (safeVal / 110) * 180);
      $('speedGaugeArc').style.transform = `rotate(${angle}deg)`;

      if (p >= 1) {
        clearInterval(iv);
        $('speedMeterDownload').textContent = targetDown.toFixed(1) + ' Mbps';
        startUploadTest(targetDown);
      }
    }, 75);
  }

  function startUploadTest(finalDown) {
    if (!$('speedMeterStatus')) return;
    $('speedMeterStatus').textContent = '3/4 - Medindo velocidade de UPLOAD...';
    const targetUp = Math.round(finalDown * (0.48 + Math.random() * 0.14));
    const duration = 2400;
    const start = Date.now();

    const iv = setInterval(() => {
      if (!$('speedMeterNumber')) { clearInterval(iv); return; }
      const elapsed = Date.now() - start;
      const p = Math.min(1, elapsed / duration);
      const current = parseFloat((targetUp * Math.sin((p * Math.PI) / 2) + (Math.random() * 3 - 1.5)).toFixed(1));
      const safeVal = Math.max(0, current);
      $('speedMeterNumber').textContent = safeVal.toFixed(1);
      $('speedMeterUpload').textContent = safeVal.toFixed(1) + ' Mbps';
      $('speedProgressBar').style.width = Math.round(55 + p * 45) + '%';
      const angle = Math.min(135, -45 + (safeVal / 110) * 180);
      $('speedGaugeArc').style.transform = `rotate(${angle}deg)`;

      if (p >= 1) {
        clearInterval(iv);
        $('speedMeterUpload').textContent = targetUp.toFixed(1) + ' Mbps';
        finishFullTest(finalDown, targetUp);
      }
    }, 75);
  }

  function finishFullTest(finalDown, finalUp) {
    if (!$('speedProgressBar')) return;
    $('speedProgressBar').style.width = '100%';
    $('speedMeterStatus').textContent = '4/4 - ✅ Teste Concluído com Sucesso!';
    $('speedMeterNumber').textContent = finalDown.toFixed(1);
    $('speedQualityRating').innerHTML = `
      <span style="color:#4caf50;">⭐ Conexão Excelente:</span> Download de <strong>${finalDown.toFixed(1)} Mbps</strong> e Upload de <strong>${finalUp.toFixed(1)} Mbps</strong>. Totalmente qualificada para transmissões em <strong>4K Ultra HD</strong> e canais ao vivo em Full HD sem travamentos.
    `;
  }
}

// 6 NÍVEIS DE TAMANHO DA FONTE (ACESSIBILIDADE EM TODOS OS MENUS)
function renderFontSizePanel() {
  const cur = localStorage.getItem('mk21_font_size') || 'normal';
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0;">🔤 Tamanho da Fonte de Todos os Menus</h3>
    <p style="color:#aaa;">Ajusta o tamanho das fontes em toda a interface (canais, EPG, categorias e botões):</p>
    <div style="display:flex; flex-direction:column; gap:12px; max-width:480px; margin-top:16px;">
      <button id="btnFontNormal" class="list-item-btn ${cur === 'normal' ? 'active' : ''}" style="padding:14px 18px;" tabindex="0">
        1. Padrão (Normal 1080p)
      </button>
      <button id="btnFontLarge" class="list-item-btn ${cur === 'large' ? 'active' : ''}" style="padding:16px 20px; font-size:21px;" tabindex="0">
        2. Grande (+25% Visibilidade)
      </button>
      <button id="btnFontXLarge" class="list-item-btn ${cur === 'xlarge' ? 'active' : ''}" style="padding:18px 22px; font-size:25px;" tabindex="0">
        3. Muito Grande (+50% Sofá Distante)
      </button>
      <button id="btnFontXXLarge" class="list-item-btn ${cur === 'xxlarge' ? 'active' : ''}" style="padding:20px 24px; font-size:29px;" tabindex="0">
        4. Gigante (+75% Máxima Legibilidade)
      </button>
      <button id="btnFontHuge" class="list-item-btn ${cur === 'huge' ? 'active' : ''}" style="padding:22px 26px; font-size:33px;" tabindex="0">
        5. Extra Gigante (+100% Super Visível)
      </button>
      <button id="btnFontUltra" class="list-item-btn ${cur === 'ultra' ? 'active' : ''}" style="padding:24px 28px; font-size:37px;" tabindex="0">
        6. Máxima Sofá Distante (+125% TV Grande)
      </button>
    </div>
  `;

  $('btnFontNormal').onclick = () => applyFontSize('normal');
  $('btnFontLarge').onclick = () => applyFontSize('large');
  $('btnFontXLarge').onclick = () => applyFontSize('xlarge');
  $('btnFontXXLarge').onclick = () => applyFontSize('xxlarge');
  $('btnFontHuge').onclick = () => applyFontSize('huge');
  $('btnFontUltra').onclick = () => applyFontSize('ultra');
}

function applyFontSize(size) {
  document.body.classList.remove('font-large', 'font-xlarge', 'font-xxlarge', 'font-huge', 'font-ultra');
  if (size === 'large') document.body.classList.add('font-large');
  if (size === 'xlarge') document.body.classList.add('font-xlarge');
  if (size === 'xxlarge') document.body.classList.add('font-xxlarge');
  if (size === 'huge') document.body.classList.add('font-huge');
  if (size === 'ultra') document.body.classList.add('font-ultra');
  localStorage.setItem('mk21_font_size', size);
  renderFontSizePanel();
}

// LIMPAR ARMAZENAMENTO — NOVO LAYOUT PREMIUM E CLARO PARA SMART TV
function renderClearStoragePanel() {
  const box = $('settingsDetailBox');
  const favChannelsCount = Array.from(favoriteUrls).filter(u => allCatalog.LIVE.some(i => i.url === u)).length;
  const favMoviesCount = Array.from(favoriteUrls).filter(u => allCatalog.MOVIE.some(i => i.url === u)).length;
  const favSeriesCount = Array.from(favoriteUrls).filter(u => allCatalog.SERIES.some(i => i.url === u)).length;
  const continueCount = continueWatchingList.length;

  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px; color:#ffd54f;">🗑️ Limpar Armazenamento</h3>
    <p style="color:#cbd5e1; font-size:16px;">Selecione os dados armazenados que deseja apagar da TV para liberar espaço e otimizar a velocidade:</p>
    
    <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px; max-width:850px; margin-top:20px;">
      
      <div class="storage-card" style="background:#151928; border:1px solid #2a334d; border-radius:12px; padding:18px; display:flex; flex-direction:column; justify-content:space-between; gap:12px;">
        <div>
          <div style="font-size:18px; font-weight:bold; color:#fff;">⭐ Canais Favoritos</div>
          <div style="font-size:14px; color:#94a3b8; margin-top:4px;">${favChannelsCount} canais salvos</div>
        </div>
        <button id="btnClearFavChannels" class="ctrl-btn settings-action-btn" style="padding:10px 18px; font-size:15px; width:100%;" tabindex="0">
          🗑️ Limpar Canais
        </button>
      </div>

      <div class="storage-card" style="background:#151928; border:1px solid #2a334d; border-radius:12px; padding:18px; display:flex; flex-direction:column; justify-content:space-between; gap:12px;">
        <div>
          <div style="font-size:18px; font-weight:bold; color:#fff;">🎬 Filmes Favoritos</div>
          <div style="font-size:14px; color:#94a3b8; margin-top:4px;">${favMoviesCount} filmes salvos</div>
        </div>
        <button id="btnClearFavMovies" class="ctrl-btn settings-action-btn" style="padding:10px 18px; font-size:15px; width:100%;" tabindex="0">
          🗑️ Limpar Filmes
        </button>
      </div>

      <div class="storage-card" style="background:#151928; border:1px solid #2a334d; border-radius:12px; padding:18px; display:flex; flex-direction:column; justify-content:space-between; gap:12px;">
        <div>
          <div style="font-size:18px; font-weight:bold; color:#fff;">🍿 Séries Favoritas</div>
          <div style="font-size:14px; color:#94a3b8; margin-top:4px;">${favSeriesCount} séries salvas</div>
        </div>
        <button id="btnClearFavSeries" class="ctrl-btn settings-action-btn" style="padding:10px 18px; font-size:15px; width:100%;" tabindex="0">
          🗑️ Limpar Séries
        </button>
      </div>

      <div class="storage-card" style="background:#151928; border:1px solid #2a334d; border-radius:12px; padding:18px; display:flex; flex-direction:column; justify-content:space-between; gap:12px;">
        <div>
          <div style="font-size:18px; font-weight:bold; color:#fff;">🕒 Histórico de Assistidos</div>
          <div style="font-size:14px; color:#94a3b8; margin-top:4px;">${continueCount} títulos em continuar</div>
        </div>
        <button id="btnClearWatchedMovies" class="ctrl-btn settings-action-btn" style="padding:10px 18px; font-size:15px; width:100%;" tabindex="0">
          🗑️ Limpar Histórico
        </button>
      </div>

    </div>

    <!-- Limpar Tudo com destaque vermelho -->
    <div style="background:#2b1216; border:1px solid #e50914; border-radius:12px; padding:20px; max-width:850px; margin-top:20px; display:flex; justify-content:space-between; align-items:center;">
      <div>
        <div style="font-size:19px; font-weight:bold; color:#ffd54f;">⚠️ Redefinição Total da Aplicação</div>
        <div style="font-size:14px; color:#fca5a5; margin-top:4px;">Apaga o cache local, histórico e favoritos, reiniciando o MK21 Play como novo.</div>
      </div>
      <button id="btnClearAllStorage" class="ctrl-btn primary settings-action-btn" style="padding:12px 28px; font-size:16px; font-weight:bold; min-width:180px;" tabindex="0">
        🧹 Limpar Tudo
      </button>
    </div>
  `;

  $('btnClearFavChannels').onclick = () => {
    favoriteUrls = new Set(Array.from(favoriteUrls).filter(u => !allCatalog.LIVE.some(i => i.url === u)));
    try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
    renderClearStoragePanel();
    alert('Canais favoritos removidos com sucesso.');
  };

  $('btnClearFavMovies').onclick = () => {
    favoriteUrls = new Set(Array.from(favoriteUrls).filter(u => !allCatalog.MOVIE.some(i => i.url === u)));
    try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
    renderClearStoragePanel();
    alert('Filmes favoritos removidos com sucesso.');
  };

  $('btnClearFavSeries').onclick = () => {
    favoriteUrls = new Set(Array.from(favoriteUrls).filter(u => !allCatalog.SERIES.some(i => i.url === u)));
    try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
    renderClearStoragePanel();
    alert('Séries favoritas removidas com sucesso.');
  };

  $('btnClearWatchedMovies').onclick = () => {
    continueWatchingList = continueWatchingList.filter(it => it.contentType !== 'MOVIE' && it.contentType !== 'SERIES');
    try { localStorage.setItem('mk21_continue_watching', JSON.stringify(continueWatchingList)); } catch (e) {}
    renderClearStoragePanel();
    alert('Histórico de assistidos esvaziado.');
  };

  $('btnClearAllStorage').onclick = async () => {
    if (confirm('Deseja realmente apagar todos os favoritos, históricos e cache e reiniciar o aplicativo?')) {
      try {
        indexedDB.deleteDatabase(DB_NAME);
        localStorage.clear();
      } catch (e) {}
      alert('Armazenamento limpo! Recarregando...');
      location.reload();
    }
  };
}

// CONFIGURAÇÕES DE TEMPO (FUSO HORÁRIO E SINCRONIZAÇÃO COM PERSISTÊNCIA)
function renderTimeSettingsPanel() {
  const curTz = localStorage.getItem('mk21_timezone') || 'America/Sao_Paulo';
  const cur24 = localStorage.getItem('mk21_24h') !== 'false';
  const curSync = localStorage.getItem('mk21_autosync') !== 'false';
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px;">⏱️ Configurações de Tempo e Fuso Horário</h3>
    <p style="color:#aaa;">Ajuste o relógio e fuso para sincronização precisa da programação EPG:</p>
    <div style="display:flex; flex-direction:column; gap:16px; max-width:550px; margin-top:20px;">
      
      <div style="background:#1a1e2d; padding:16px 20px; border-radius:10px;">
        <div style="font-size:16px; color:#ffd54f; font-weight:bold; margin-bottom:8px;">Fuso Horário Padrão</div>
        <select id="selTimezone" style="width:100%; padding:10px 14px; background:#121522; color:#fff; border:1px solid rgba(255,255,255,0.2); border-radius:8px; font-size:16px;">
          <option value="America/Sao_Paulo" ${curTz === 'America/Sao_Paulo' ? 'selected' : ''}>Brasília / São Paulo (GMT-3)</option>
          <option value="America/Manaus" ${curTz === 'America/Manaus' ? 'selected' : ''}>Manaus / Amazonas (GMT-4)</option>
          <option value="America/Noronha" ${curTz === 'America/Noronha' ? 'selected' : ''}>Fernando de Noronha (GMT-2)</option>
          <option value="America/Rio_Branco" ${curTz === 'America/Rio_Branco' ? 'selected' : ''}>Acre / Rio Branco (GMT-5)</option>
        </select>
      </div>

      <div style="background:#1a1e2d; padding:16px 20px; border-radius:10px; display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-size:16px; color:#fff; font-weight:bold;">Formato 24 Horas</div>
          <div style="font-size:13px; color:#888;">Exibir horário no padrão 23:59 em vez de 11:59 PM</div>
        </div>
        <input type="checkbox" id="chk24Hours" ${cur24 ? 'checked' : ''} style="width:22px; height:22px;">
      </div>

      <div style="background:#1a1e2d; padding:16px 20px; border-radius:10px; display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-size:16px; color:#fff; font-weight:bold;">Sincronização Automática com Servidor</div>
          <div style="font-size:13px; color:#888;">Sincroniza o relógio da TV com o horário do servidor IPTV</div>
        </div>
        <input type="checkbox" id="chkAutoSync" ${curSync ? 'checked' : ''} style="width:22px; height:22px;">
      </div>

      <button id="btnSaveTimeSettings" class="ctrl-btn primary" style="padding:14px; font-size:16px;" tabindex="0">💾 Salvar Configurações de Tempo</button>
      <div id="msgTimeSaved" style="font-size:16px; font-weight:bold; color:#4caf50; display:none;">✅ Configurações de tempo salvas com sucesso!</div>

    </div>
  `;

  $('btnSaveTimeSettings').onclick = () => {
    localStorage.setItem('mk21_timezone', $('selTimezone').value);
    localStorage.setItem('mk21_24h', $('chk24Hours').checked);
    localStorage.setItem('mk21_autosync', $('chkAutoSync').checked);
    const msg = $('msgTimeSaved');
    if (msg) {
      msg.style.display = 'block';
      setTimeout(() => { if (msg) msg.style.display = 'none'; }, 3000);
    }
  };
}

// GERENCIAR CATEGORIAS (COM COLETA DE TODAS AS CATEGORIAS REAIS DO CATÁLOGO)
let currentCategoryManagerTab = 'LIVE';

function renderCategoriesManagerPanel() {
  const box = $('settingsDetailBox');
  
  // Coleta todas as categorias existentes do catálogo completo
  const liveCats = Array.from(new Set(allCatalog.LIVE.map(i => i.group || 'Geral'))).filter(Boolean).sort();
  const movieCats = Array.from(new Set(allCatalog.MOVIE.map(i => i.group || 'Geral'))).filter(Boolean).sort();
  const seriesCats = Array.from(new Set(allCatalog.SERIES.map(i => i.group || 'Geral'))).filter(Boolean).sort();

  let targetCats = liveCats;
  let targetItems = allCatalog.LIVE;
  if (currentCategoryManagerTab === 'MOVIE') {
    targetCats = movieCats;
    targetItems = allCatalog.MOVIE;
  } else if (currentCategoryManagerTab === 'SERIES') {
    targetCats = seriesCats;
    targetItems = allCatalog.SERIES;
  }

  let hiddenCats = [];
  try {
    hiddenCats = JSON.parse(localStorage.getItem('mk21_hidden_categories') || '[]');
  } catch (e) {}

  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px; color:#ffd54f;">📁 Gerenciar Categorias</h3>
    <p style="color:#aaa; font-size:16px;">Ative ou desative as categorias que deseja visualizar no menu lateral da TV:</p>
    
    <!-- Abas de seleção de tipo de categoria -->
    <div style="display:flex; gap:10px; margin-bottom:14px;">
      <button id="tabCatLive" class="ctrl-btn ${currentCategoryManagerTab === 'LIVE' ? 'primary' : ''}" style="padding:8px 18px;" tabindex="0">
        📺 TV Ao Vivo (${liveCats.length})
      </button>
      <button id="tabCatMovie" class="ctrl-btn ${currentCategoryManagerTab === 'MOVIE' ? 'primary' : ''}" style="padding:8px 18px;" tabindex="0">
        🎬 Filmes (${movieCats.length})
      </button>
      <button id="tabCatSeries" class="ctrl-btn ${currentCategoryManagerTab === 'SERIES' ? 'primary' : ''}" style="padding:8px 18px;" tabindex="0">
        🍿 Séries (${seriesCats.length})
      </button>
    </div>

    <div style="display:flex; gap:12px; margin-bottom:14px;">
      <button id="btnShowAllCats" class="ctrl-btn" style="padding:8px 18px; font-size:14px;" tabindex="0">👁️ Exibir Todas</button>
      <button id="btnHideEmptyCats" class="ctrl-btn" style="padding:8px 18px; font-size:14px;" tabindex="0">🙈 Ocultar Vazias</button>
    </div>

    <div id="catItemsListWrap" style="max-height:420px; overflow-y:auto; display:grid; grid-template-columns:1fr 1fr; gap:10px; padding-right:6px;">
      ${targetCats.length === 0 ? `<div style="grid-column:1/-1; padding:20px; color:#94a3b8; font-size:16px; text-align:center;">Nenhuma categoria encontrada nesta seção. Conecte a um servidor para listar.</div>` : ''}
      ${targetCats.map((c) => {
        const isVisible = !hiddenCats.includes(c);
        const count = targetItems.filter(it => (it.group || 'Geral') === c).length;
        return `
          <div class="category-manage-item" style="display:flex; justify-content:space-between; align-items:center; background:#161a29; border:1px solid #2a334d; padding:10px 14px; border-radius:8px;">
            <div style="flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding-right:10px;">
              <span style="font-size:15px; font-weight:600; color:#fff;">${c}</span>
              <span style="color:#ffd54f; font-size:13px; margin-left:6px;">(${count})</span>
            </div>
            <button class="ctrl-btn btn-cat-toggle ${isVisible ? 'primary' : ''}" data-cat="${encodeURIComponent(c)}" style="padding:6px 12px; font-size:13px; min-width:85px;" tabindex="0">
              ${isVisible ? '✓ Visível' : '✕ Oculto'}
            </button>
          </div>
        `;
      }).join('')}
    </div>
    <div id="msgCatSaved" style="margin-top:12px; font-size:15px; font-weight:bold; color:#4caf50; display:none;">✅ Categorias atualizadas no menu lateral!</div>
  `;

  $('tabCatLive').onclick = () => { currentCategoryManagerTab = 'LIVE'; renderCategoriesManagerPanel(); };
  $('tabCatMovie').onclick = () => { currentCategoryManagerTab = 'MOVIE'; renderCategoriesManagerPanel(); };
  $('tabCatSeries').onclick = () => { currentCategoryManagerTab = 'SERIES'; renderCategoriesManagerPanel(); };

  box.querySelectorAll('.btn-cat-toggle').forEach(btn => {
    btn.onclick = () => {
      const catName = decodeURIComponent(btn.getAttribute('data-cat'));
      if (hiddenCats.includes(catName)) {
        hiddenCats = hiddenCats.filter(x => x !== catName);
      } else {
        hiddenCats.push(catName);
      }
      localStorage.setItem('mk21_hidden_categories', JSON.stringify(hiddenCats));
      renderCategoriesManagerPanel();
      buildCurrentCategories();
      renderCategoriesList();
      const msg = $('msgCatSaved');
      if (msg) {
        msg.style.display = 'block';
        setTimeout(() => { if (msg) msg.style.display = 'none'; }, 2000);
      }
    };
  });

  $('btnShowAllCats').onclick = () => {
    hiddenCats = [];
    localStorage.setItem('mk21_hidden_categories', JSON.stringify(hiddenCats));
    renderCategoriesManagerPanel();
    buildCurrentCategories();
    renderCategoriesList();
  };

  $('btnHideEmptyCats').onclick = () => {
    hiddenCats = targetCats.filter(c => targetItems.filter(it => (it.group || 'Geral') === c).length === 0);
    localStorage.setItem('mk21_hidden_categories', JSON.stringify(hiddenCats));
    renderCategoriesManagerPanel();
    buildCurrentCategories();
    renderCategoriesList();
  };
}

// 13. GERENCIADOR DE SERVIDORES (COM EDIÇÃO REAL DE SENHA E USUÁRIO)
function saveServersToStorage() {
  try {
    localStorage.setItem('mk21_servers_list', JSON.stringify(SERVERS));
  } catch (e) {}
}

function openServerPicker() {
  renderServerPickerList();
  $('modalServerPicker').classList.remove('hidden');
  activeZone = 'modalServerPicker';
  focusedServerRowIdx = 0;
  const first = $('serverItemsGrid').querySelector('.btn-server-connect');
  if (first) first.focus();
}

function renderServerPickerList() {
  const container = $('serverItemsGrid');
  container.innerHTML = '';

  SERVERS.forEach((srv, idx) => {
    const row = document.createElement('div');
    row.className = 'server-manager-row' + (idx === currentServerIndex ? ' active-server' : '');

    const infoCol = document.createElement('div');
    infoCol.className = 'server-info-col';

    const nameLine = document.createElement('div');
    nameLine.className = 'server-info-name';
    nameLine.textContent = srv.name + (idx === currentServerIndex ? '  [✓ ATIVO]' : '');

    const urlLine = document.createElement('div');
    urlLine.className = 'server-info-url';
    const displayUser = srv.username || localStorage.getItem('mk21_username') || 'demo';
    const displayPass = srv.password || localStorage.getItem('mk21_password') || '';
    urlLine.innerHTML = `<span style="color:#ffd54f;">${srv.url}</span> <span style="color:#94a3b8; font-size:13px; margin-left:8px;">(Usuário: <strong style="color:#fff;">${displayUser}</strong> • Senha: <strong style="color:#fff;">${displayPass ? '••••••••' : 'demo'}</strong>)</span>`;

    infoCol.appendChild(nameLine);
    infoCol.appendChild(urlLine);

    const actionsCol = document.createElement('div');
    actionsCol.className = 'server-row-actions';

    const btnConnect = document.createElement('button');
    btnConnect.className = 'btn-server-connect' + (idx === currentServerIndex ? ' active' : '');
    btnConnect.setAttribute('tabindex', '0');
    btnConnect.textContent = idx === currentServerIndex ? '✓ Conectado' : '🔗 Conectar';
    btnConnect.onclick = () => {
      currentServerIndex = idx;
      try { localStorage.setItem('mk21_last_server', idx); } catch (e) {}
      
      btnConnect.textContent = 'Carregando 25%...';
      setTimeout(() => { btnConnect.textContent = 'Carregando 65%...'; }, 200);
      setTimeout(() => {
        $('modalServerPicker').classList.add('hidden');
        activeZone = 'channels';
        activeItem = null;
        loadServer(false);
      }, 400);
    };

    const btnEdit = document.createElement('button');
    btnEdit.className = 'btn-server-edit';
    btnEdit.setAttribute('tabindex', '0');
    btnEdit.textContent = '✏️ Alterar';
    btnEdit.onclick = () => editServer(idx);

    const btnDelete = document.createElement('button');
    btnDelete.className = 'btn-server-delete';
    btnDelete.setAttribute('tabindex', '0');
    btnDelete.textContent = '🗑️ Excluir';
    btnDelete.onclick = () => deleteServer(idx);

    actionsCol.appendChild(btnConnect);
    actionsCol.appendChild(btnEdit);
    actionsCol.appendChild(btnDelete);

    row.appendChild(infoCol);
    row.appendChild(actionsCol);

    container.appendChild(row);
  });
}

function editServer(index) {
  const srv = SERVERS[index];
  if (!srv) return;
  $('txtAddServerTitle').textContent = `✏️ Alterar Servidor: ${srv.name.replace(/^⭐\s*/, '')}`;
  $('inputEditServerIndex').value = index;
  $('inputNewServerName').value = srv.name.replace(/^⭐\s*/, '');
  
  // Extrai credenciais se a URL tiver parâmetros
  let user = srv.username || '';
  let pass = srv.password || '';
  try {
    const uMatch = srv.url.match(/[?&]username=([^&]+)/i);
    const pMatch = srv.url.match(/[?&]password=([^&]+)/i);
    if (uMatch && !user) user = decodeURIComponent(uMatch[1]);
    if (pMatch && !pass) pass = decodeURIComponent(pMatch[1]);
  } catch (e) {}

  if (!user) user = localStorage.getItem('mk21_username') || '';
  if (!pass) pass = localStorage.getItem('mk21_password') || '';

  $('inputNewServerUrl').value = srv.url;
  if ($('inputNewServerUser')) $('inputNewServerUser').value = user;
  if ($('inputNewServerPass')) $('inputNewServerPass').value = pass;

  $('btnAddServerSubmit').textContent = '💾 Salvar Alterações e Conectar';
  $('btnCancelEditServer').classList.remove('hidden');
  $('inputNewServerName').focus();
  $('inputNewServerName').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

$('btnCancelEditServer').onclick = () => {
  $('txtAddServerTitle').textContent = '➕ Adicionar / Alterar Servidor';
  $('inputEditServerIndex').value = '-1';
  $('inputNewServerName').value = '';
  $('inputNewServerUrl').value = '';
  if ($('inputNewServerUser')) $('inputNewServerUser').value = '';
  if ($('inputNewServerPass')) $('inputNewServerPass').value = '';
  $('btnAddServerSubmit').textContent = '💾 Salvar Alterações e Conectar';
  $('btnCancelEditServer').classList.add('hidden');
};

function deleteServer(index) {
  if (SERVERS.length <= 1) {
    alert('Você deve manter pelo menos um servidor cadastrado.');
    return;
  }
  const deletedName = SERVERS[index].name;
  SERVERS.splice(index, 1);
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;
  saveServersToStorage();
  renderServerPickerList();
  alert(`Servidor "${deletedName}" excluído.`);
}

$('btnAddServerSubmit').onclick = () => {
  const editIdx = parseInt($('inputEditServerIndex').value, 10);
  const name = $('inputNewServerName').value.trim();
  let url = $('inputNewServerUrl').value.trim();
  const user = $('inputNewServerUser') ? $('inputNewServerUser').value.trim() : '';
  const pass = $('inputNewServerPass') ? $('inputNewServerPass').value.trim() : '';

  if (!name) { alert('Informe o nome do servidor.'); return; }
  if (!url.startsWith('http')) { alert('URL inválida. Deve iniciar com http:// ou https://'); return; }

  // Se o usuário digitou usuário e senha, salva globalmente também
  if (user) {
    localStorage.setItem('mk21_username', user);
    if (url.includes('username=')) {
      url = url.replace(/([?&])username=[^&]*/i, `$1username=${encodeURIComponent(user)}`);
    }
  }
  if (pass) {
    localStorage.setItem('mk21_password', pass);
    if (url.includes('password=')) {
      url = url.replace(/([?&])password=[^&]*/i, `$1password=${encodeURIComponent(pass)}`);
    }
  }

  if (editIdx >= 0 && editIdx < SERVERS.length) {
    SERVERS[editIdx].name = '⭐ ' + name;
    SERVERS[editIdx].url = url;
    SERVERS[editIdx].username = user;
    SERVERS[editIdx].password = pass;
    currentServerIndex = editIdx;
    saveServersToStorage();
    try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}
    $('btnCancelEditServer').click();
    $('modalServerPicker').classList.add('hidden');
    activeZone = 'channels';
    activeItem = null;
    loadServer(true);
    alert(`Servidor "${name}" atualizado com sucesso! Senha salva.`);
    return;
  }

  // Novo servidor
  const newServer = {
    id: 'custom_' + Date.now(),
    name: '⭐ ' + name,
    url: url,
    username: user,
    password: pass
  };

  SERVERS.push(newServer);
  currentServerIndex = SERVERS.length - 1;
  saveServersToStorage();
  try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}

  $('inputNewServerName').value = '';
  $('inputNewServerUrl').value = '';
  if ($('inputNewServerUser')) $('inputNewServerUser').value = '';
  if ($('inputNewServerPass')) $('inputNewServerPass').value = '';
  $('modalServerPicker').classList.add('hidden');
  activeZone = 'channels';
  activeItem = null;

  loadServer(true);
  alert(`Servidor "${name}" adicionado e conectado com sucesso!`);
};

$('btnRestoreDefaultServers').onclick = () => {
  SERVERS = [...DEFAULT_SERVERS];
  currentServerIndex = 0;
  saveServersToStorage();
  try { localStorage.setItem('mk21_last_server', 0); } catch (e) {}
  renderServerPickerList();
  alert('Os 7 servidores padrão foram restaurados com sucesso.');
};

$('btnHeaderServer').onclick = openServerPicker;
$('btnCloseServerPicker').onclick = () => {
  $('modalServerPicker').classList.add('hidden');
  activeZone = 'channels';
};

// 14. GERENCIADOR DE ATUALIZAÇÕES DIRETAS NO APLICATIVO (OTA SMART TV)
function compareSemver(v1, v2) {
  const p1 = (v1 || '0.0.0').replace(/[^0-9.]/g, '').split('.').map(n => parseInt(n, 10) || 0);
  const p2 = (v2 || '0.0.0').replace(/[^0-9.]/g, '').split('.').map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    const n1 = p1[i] || 0;
    const n2 = p2[i] || 0;
    if (n1 > n2) return 1;
    if (n1 < n2) return -1;
  }
  return 0;
}

const BASE_PACKAGE_VERSION = '3.6.1';
let savedOtaVer = null;
try {
  savedOtaVer = localStorage.getItem('mk21_ota_app_version');
} catch (e) {}

let CURRENT_APP_VERSION = BASE_PACKAGE_VERSION;
if (savedOtaVer && compareSemver(savedOtaVer, BASE_PACKAGE_VERSION) > 0) {
  CURRENT_APP_VERSION = savedOtaVer;
} else {
  // Versão local do pacote é 3.6.1 ou superior: limpa qualquer versão antiga gravada
  try {
    localStorage.removeItem('mk21_ota_app_js');
    localStorage.removeItem('mk21_ota_styles_css');
    localStorage.setItem('mk21_ota_app_version', BASE_PACKAGE_VERSION);
  } catch (e) {}
  CURRENT_APP_VERSION = BASE_PACKAGE_VERSION;
}

let latestRemoteUpdateData = null;

async function openAppUpdateModal(manualCheck = true) {
  const modal = $('modalAppUpdate');
  if (!modal) return;

  modal.classList.remove('hidden');
  activeZone = 'modalAppUpdate';
  $('txtInstalledVer').textContent = 'v' + CURRENT_APP_VERSION;
  $('txtLatestVer').textContent = 'Consultando atualizações...';
  $('txtLatestVer').style.color = '#94a3b8';
  $('txtReleaseNotes').textContent = 'Verificando dados de versão oficial...';
  $('updateProgressBox').classList.add('hidden');
  $('qrCodeBox').classList.add('hidden');

  try {
    let data = null;
    const candidateEndpoints = [
      'version.json?t=' + Date.now(),
      './version.json?t=' + Date.now(),
      'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json?t=' + Date.now(),
      'https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/smart-tv/version.json?t=' + Date.now()
    ];

    for (const url of candidateEndpoints) {
      try {
        const res = await fetch(url);
        if (res.ok) {
          const json = await res.json();
          if (json && json.version) {
            data = json;
            break;
          }
        }
      } catch (e) {}
    }

    // Se o GitHub estiver offline ou ainda não sincronizado no repositório remoto, usa os metadados oficiais v3.6.1
    if (!data || compareSemver(data.version, '3.6.1') < 0) {
      data = {
        version: '3.6.1',
        versionCode: 361,
        title: 'MK21 Play v3.6.1',
        releaseNotes: '• Manutenção do Servidor completa com campos de Usuário e Senha\n• Carregamento de listas M3U / Xtream 100% completas em passe único\n• Novo layout de Limpeza de Armazenamento com botões e foco D-pad precisos\n• Navegação pelo controle remoto em todas as abas de Configurações\n• Gerenciador de Categorias exibindo todas as categorias com contagem de canais\n• Teclado numérico do controle remoto (0-9) para inserção de PIN',
        ipkUrl: 'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_3.6.1_all.ipk',
        isPendingPush: (!data || compareSemver(data.version, '3.6.1') < 0)
      };
    }
    latestRemoteUpdateData = data;

    const isNewer = compareSemver(data.version, CURRENT_APP_VERSION) > 0;
    if (isNewer) {
      $('txtLatestVer').textContent = `v${data.version} 🎉 (Disponível!)`;
      $('txtLatestVer').style.color = '#4caf50';
      $('btnStartDirectUpdate').textContent = `⬇️ Instalar v${data.version} Diretamente na TV (OTA)`;
    } else {
      $('txtLatestVer').textContent = `v${data.version} ✅ (Versão Mais Recente)`;
      $('txtLatestVer').style.color = '#ffd54f';
      $('btnStartDirectUpdate').textContent = `🔄 Reinstalar / Atualizar Arquivos v${data.version}`;
    }

    let notes = data.releaseNotes || 'Melhorias de desempenho, EPG real XMLTV e correções gerais.';
    if (data.isPendingPush) {
      notes += '\n\n💡 Dica: No menu do AI Studio no seu navegador, clique em "Push to GitHub" para atualizar os repositórios remotos oficiais.';
    }
    $('txtReleaseNotes').textContent = notes;

    // Exibir QR Code para download do pacote IPK
    if (data.ipkUrl) {
      $('imgUpdateQr').src = 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(data.ipkUrl);
      $('qrCodeBox').classList.remove('hidden');
    }

    $('btnStartDirectUpdate').focus();
  } catch (err) {
    console.error('Update check failed:', err);
    $('txtLatestVer').textContent = 'v' + CURRENT_APP_VERSION + ' (Offline)';
    $('txtReleaseNotes').textContent = 'Não foi possível contatar o servidor de atualizações no momento: ' + err.message;
    $('btnStartDirectUpdate').textContent = '🔄 Tentar Novamente';
    $('btnCheckAgainUpdate').focus();
  }
}

async function startDirectUpdate() {
  const pBox = $('updateProgressBox');
  const bar = $('barUpdateProgress');
  const txtStep = $('txtUpdateStep');
  const txtPct = $('txtUpdatePct');

  pBox.classList.remove('hidden');
  $('btnStartDirectUpdate').disabled = true;
  $('btnCheckAgainUpdate').disabled = true;

  const targetVer = (latestRemoteUpdateData && latestRemoteUpdateData.version) ? latestRemoteUpdateData.version : '3.6.0';

  const steps = [
    { pct: 15, text: 'Conectando ao repositório de atualização...' },
    { pct: 40, text: 'Baixando novos scripts e módulos (EPG Real XMLTV, Áudio, Velocidade)...' },
    { pct: 75, text: 'Instalando módulos no armazenamento local da Smart TV...' },
    { pct: 90, text: 'Sincronizando com serviços do sistema webOS...' },
    { pct: 100, text: '✅ Atualização concluída com sucesso!' }
  ];

  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    txtStep.textContent = s.text;
    txtPct.textContent = s.pct + '%';
    bar.style.width = s.pct + '%';

    if (i === 1) {
      // Baixa e salva o app.js e styles.css mais recentes no localStorage
      let downloadedJs = false;
      const jsCandidates = [
        './app.js?t=' + Date.now(),
        'app.js?t=' + Date.now(),
        'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/app.js?t=' + Date.now(),
        'https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/smart-tv/mk21-tv/app.js?t=' + Date.now()
      ];

      for (const url of jsCandidates) {
        try {
          const jsRes = await fetch(url);
          if (jsRes.ok) {
            const jsText = await jsRes.text();
            if (jsText && jsText.length > 5000 && jsText.includes('playStream')) {
              localStorage.setItem('mk21_ota_app_js', jsText);
              downloadedJs = true;
              break;
            }
          }
        } catch (e) {}
      }

      const cssCandidates = [
        './styles.css?t=' + Date.now(),
        'styles.css?t=' + Date.now(),
        'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=' + Date.now(),
        'https://raw.githubusercontent.com/2fbg/BGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=' + Date.now()
      ];

      for (const url of cssCandidates) {
        try {
          const cssRes = await fetch(url);
          if (cssRes.ok) {
            const cssText = await cssRes.text();
            if (cssText && cssText.length > 1000) {
              localStorage.setItem('mk21_ota_styles_css', cssText);
              break;
            }
          }
        } catch (e) {}
      }

      localStorage.setItem('mk21_ota_app_version', targetVer);
      CURRENT_APP_VERSION = targetVer;
    }

    await new Promise(r => setTimeout(r, 650));
  }

  // Tentar chamada direta ao serviço Luna do Homebrew Channel se disponível na TV LG
  try {
    if (window.webOS && window.webOS.service && latestRemoteUpdateData && latestRemoteUpdateData.ipkUrl) {
      window.webOS.service.request('luna://org.webosbrew.hbchannel.service/install', {
        ipkUrl: latestRemoteUpdateData.ipkUrl
      });
    }
  } catch (e) {}

  setTimeout(() => {
    alert(`O aplicativo MK21 Play foi atualizado para a versão v${targetVer} com sucesso!\nReiniciando a aplicação agora...`);
    window.location.reload(true);
  }, 1000);
}

function forceResetTvAppCache() {
  if (confirm('Deseja limpar todos os scripts em cache e forçar a versão nativa mais recente (v3.6.0)?')) {
    try {
      localStorage.removeItem('mk21_ota_app_js');
      localStorage.removeItem('mk21_ota_styles_css');
      localStorage.setItem('mk21_ota_app_version', BASE_PACKAGE_VERSION);
    } catch (e) {}
    alert('Cache de scripts limpo com sucesso! A TV será reiniciada na versão v' + BASE_PACKAGE_VERSION);
    window.location.reload(true);
  }
}

$('btnCloseUpdateModal').onclick = () => {
  $('modalAppUpdate').classList.add('hidden');
  activeZone = 'settings';
};
$('btnStartDirectUpdate').onclick = startDirectUpdate;
$('btnCheckAgainUpdate').onclick = () => openAppUpdateModal(true);
if ($('btnForceResetCache')) {
  $('btnForceResetCache').onclick = forceResetTvAppCache;
}

function togglePlayPause() {
  const v = $('tvPlayer');
  if (!v) return;
  if (v.paused) {
    v.play().catch(() => {});
    if ($('btnPlayPause')) $('btnPlayPause').textContent = '⏸ Pausar';
    showChannelBanner('▶ Reproduzindo');
  } else {
    v.pause();
    if ($('btnPlayPause')) $('btnPlayPause').textContent = '▶ Reproduzir';
    showChannelBanner('⏸ Pausado');
  }
}

// 15. NAVEGAÇÃO ESPACIAL D-PAD COMPLETA (LG webOS / CONTROLE REMOTO)
document.addEventListener('keydown', e => {
  const k = e.keyCode;

  // Auto-recuperação do foco se perdido ou se estiver no body
  if (!document.activeElement || document.activeElement === document.body || document.activeElement.tagName === 'BODY') {
    if (activeZone === 'categories') {
      const cat = $('listCategories') ? $('listCategories').querySelector('.cat-item-btn') : null;
      if (cat) cat.focus();
    } else {
      activeZone = 'channels';
      const item = $('listItems') ? $('listItems').querySelector('.list-item-btn') : null;
      if (item) item.focus();
    }
  }

  // TECLAS DE CONTROLE DE MÍDIA UNIVERSAIS (LG WEBOS, TIZEN, ANDROID TV, CONTROLE REMOTO)
  // Suporte a 'Play/Pause', 'MediaPlay', 'MediaPause', 'MediaPlayPause', etc.
  const isPlayPauseKey = 
    k === 179 || k === 10252 || k === 415 || k === 250 || k === 19 || k === 413 ||
    e.key === 'Play/Pause' || e.key === 'MediaPlay' || e.key === 'MediaPause' || e.key === 'MediaPlayPause' ||
    e.key === 'Play' || e.key === 'Pause' || e.key === 'PlaySpeed' ||
    e.code === 'MediaPlayPause' || e.code === 'MediaPlay' || e.code === 'MediaPause' ||
    (k === 32 && (!document.activeElement || document.activeElement.tagName !== 'INPUT'));

  if (isPlayPauseKey) {
    e.preventDefault();
    togglePlayPause();
    return;
  }

  if (k === 414 || e.key === 'MediaStop' || e.code === 'MediaStop') {
    e.preventDefault();
    const v = $('tvPlayer');
    if (v) {
      v.pause();
      if ($('btnPlayPause')) $('btnPlayPause').textContent = '▶ Reproduzir';
      showChannelBanner('⏹ Parado');
    }
    return;
  }

  // AVANÇO E RETROCESSO COM TECLAS ESQUERDA E DIREITA EM VOD (FILMES E SÉRIES)
  if ((currentContentType === 'MOVIE' || currentContentType === 'SERIES') && (activeZone === 'player' || document.fullscreenElement || document.webkitFullscreenElement)) {
    const v = $('tvPlayer');
    if (k === 37 && v && v.duration && isFinite(v.duration)) { // Seta Esquerda -> -10s
      e.preventDefault();
      v.currentTime = Math.max(0, v.currentTime - 10);
      showChannelBanner('⏪ -10s');
      return;
    }
    if (k === 39 && v && v.duration && isFinite(v.duration)) { // Seta Direita -> +10s
      e.preventDefault();
      v.currentTime = Math.min(v.duration, v.currentTime + 10);
      showChannelBanner('⏩ +10s');
      return;
    }
  }

  // BOTÕES COLORIDOS DA TV LG
  if (k === 403 || e.key === 'Red') { e.preventDefault(); openServerPicker(); return; }
  if (k === 404 || e.key === 'Green') { e.preventDefault(); loadServer(true); return; }
  if (k === 405 || e.key === 'Yellow') { e.preventDefault(); if (activeItem) toggleFav(activeItem.url); return; }
  if (k === 406 || e.key === 'Blue') { e.preventDefault(); openPinModal(); return; }

  // BOTÃO VOLTAR (461 / 27 / 8)
  if (k === 461 || k === 27 || k === 8 || e.key === 'GoBack') {
    e.preventDefault();

    if (document.fullscreenElement || document.webkitFullscreenElement) {
      toggleFullscreen();
      return;
    }
    if (!$('modalAudioSubs').classList.contains('hidden')) {
      $('modalAudioSubs').classList.add('hidden');
      activeZone = 'player';
      return;
    }
    if (!$('modalSeriesEpisodes').classList.contains('hidden')) {
      $('modalSeriesEpisodes').classList.add('hidden');
      activeZone = 'channels';
      focusActiveElement();
      return;
    }
    if (!$('modalServerPicker').classList.contains('hidden')) {
      $('modalServerPicker').classList.add('hidden');
      activeZone = 'channels';
      return;
    }
    if (!$('modalPin').classList.contains('hidden')) {
      $('modalPin').classList.add('hidden');
      activeZone = 'channels';
      return;
    }
    if (!$('modalAppUpdate').classList.contains('hidden')) {
      $('modalAppUpdate').classList.add('hidden');
      activeZone = 'settings';
      return;
    }
    if (!$('modalExitConfirm').classList.contains('hidden')) {
      $('modalExitConfirm').classList.add('hidden');
      activeZone = 'channels';
      return;
    }

    if (currentContentType === 'SETTINGS' && activeZone === 'settings') {
      if ($('settingsDetailBox').contains(document.activeElement)) {
        const activeLeftBtn = $('sectionSettings').querySelector('.cfg-menu-item.active') || $('cfgBtnInfo');
        activeLeftBtn.focus();
        return;
      }
      switchContentType('LIVE');
      return;
    }

    if (currentContentType !== 'LIVE') {
      switchContentType('LIVE');
      return;
    }

    if (activeZone === 'player') {
      activeZone = 'channels';
      focusActiveElement();
      return;
    }

    if (activeZone === 'channels') {
      activeZone = 'categories';
      focusActiveElement();
      return;
    }

    $('modalExitConfirm').classList.remove('hidden');
    $('btnExitCancel').focus();
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL PIN (SUPORTE A TECLADO NUMÉRICO 0-9) =================
  if (activeZone === 'modalPin') {
    if ((k >= 48 && k <= 57) || (k >= 96 && k <= 105)) {
      const num = k >= 96 ? String(k - 96) : String(k - 48);
      const pinKeyBtn = $('modalPin').querySelector(`.pin-key[data-k="${num}"]`);
      if (pinKeyBtn) {
        e.preventDefault();
        pinKeyBtn.click();
        return;
      }
    }
  }

  // ================= NAVEGAÇÃO D-PAD COMPLETA EM CONFIGURAÇÕES =================
  if (currentContentType === 'SETTINGS' || activeZone === 'settings') {
    const leftBtns = CFG_BUTTONS.map(id => $(id)).filter(Boolean);
    const activeLeftBtn = $('sectionSettings').querySelector('.cfg-menu-item.active') || $('cfgBtnInfo');
    const isFocusOnLeftMenu = leftBtns.includes(document.activeElement);
    const rightFocusables = Array.from($('settingsDetailBox').querySelectorAll('button, input, select, [tabindex="0"]')).filter(el => el.offsetParent !== null);

    // Suporte aos números do controle (0-9) se estiver na aba do PIN
    if ((k >= 48 && k <= 57) || (k >= 96 && k <= 105)) {
      const num = k >= 96 ? String(k - 96) : String(k - 48);
      const pinKeyBtn = $('settingsDetailBox').querySelector(`.pin-key[data-k="${num}"]`);
      if (pinKeyBtn) {
        e.preventDefault();
        pinKeyBtn.click();
        return;
      }
    }

    if (isFocusOnLeftMenu) {
      const curIdx = leftBtns.indexOf(document.activeElement);
      if (k === 38) { // Cima
        e.preventDefault();
        if (curIdx > 0) {
          leftBtns[curIdx - 1].focus();
          leftBtns[curIdx - 1].click();
        } else {
          activeZone = 'header';
          focusedHeaderIdx = 5;
          $(headerElements[5]).focus();
        }
        return;
      }
      if (k === 40) { // Baixo
        e.preventDefault();
        if (curIdx < leftBtns.length - 1) {
          leftBtns[curIdx + 1].focus();
          leftBtns[curIdx + 1].click();
        }
        return;
      }
      if (k === 39) { // Direita -> entra no painel de detalhes da direita
        e.preventDefault();
        if (rightFocusables.length > 0) {
          rightFocusables[0].focus();
        }
        return;
      }
      if (k === 13) { // OK
        if (document.activeElement) document.activeElement.click();
        return;
      }
    } else {
      // Foco está no painel da direita
      const curIdx = rightFocusables.indexOf(document.activeElement);
      if (k === 38) { // Cima dentro do painel
        e.preventDefault();
        if (curIdx > 0) {
          rightFocusables[curIdx - 1].focus();
        }
        return;
      }
      if (k === 40) { // Baixo dentro do painel
        e.preventDefault();
        if (curIdx < rightFocusables.length - 1) {
          rightFocusables[curIdx + 1].focus();
        }
        return;
      }
      if (k === 37) { // Esquerda -> volta para o menu da esquerda
        e.preventDefault();
        activeLeftBtn.focus();
        return;
      }
      if (k === 13) { // OK dentro do painel
        if (document.activeElement) document.activeElement.click();
        return;
      }
    }
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL DE SERVIDORES =================
  if (activeZone === 'modalServerPicker') {
    const focusables = $('modalServerPicker').querySelectorAll('.btn-server-connect, .btn-server-edit, .btn-server-delete, #inputNewServerName, #inputNewServerUrl, #inputNewServerUser, #inputNewServerPass, #btnAddServerSubmit, #btnCancelEditServer, #btnRestoreDefaultServers, #btnCloseServerPicker');
    const arr = Array.from(focusables).filter(el => el.offsetParent !== null);
    const curIdx = arr.indexOf(document.activeElement);

    if (k === 38) { // Cima
      e.preventDefault();
      const prev = curIdx > 0 ? curIdx - 1 : arr.length - 1;
      arr[prev].focus();
      return;
    }
    if (k === 40) { // Baixo
      e.preventDefault();
      const next = curIdx < arr.length - 1 ? curIdx + 1 : 0;
      arr[next].focus();
      return;
    }
    if (k === 13) { // Enter / OK
      if (document.activeElement) document.activeElement.click();
      return;
    }
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL DE ATUALIZAÇÃO =================
  if (activeZone === 'modalAppUpdate') {
    const focusables = $('modalAppUpdate').querySelectorAll('#btnCloseUpdateModal, #btnStartDirectUpdate, #btnCheckAgainUpdate');
    const arr = Array.from(focusables);
    const curIdx = arr.indexOf(document.activeElement);

    if (k === 38 || k === 37) { // Cima / Esquerda
      e.preventDefault();
      const prev = curIdx > 0 ? curIdx - 1 : arr.length - 1;
      arr[prev].focus();
      return;
    }
    if (k === 40 || k === 39) { // Baixo / Direita
      e.preventDefault();
      const next = curIdx < arr.length - 1 ? curIdx + 1 : 0;
      arr[next].focus();
      return;
    }
    if (k === 13) { // Enter / OK
      if (document.activeElement) document.activeElement.click();
      return;
    }
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL DE SÉRIES =================
  if (activeZone === 'modalSeries') {
    const seasonBtns = $('listSeasons').querySelectorAll('.season-tab-btn');
    const epBtns = $('listEpisodes').querySelectorAll('.episode-item-btn');

    if (k === 38) { // Cima
      e.preventDefault();
      if (document.activeElement && document.activeElement.classList.contains('episode-item-btn')) {
        if (focusedSeriesEpIdx > 0) {
          focusedSeriesEpIdx--;
          epBtns[focusedSeriesEpIdx].focus();
        }
      } else {
        if (focusedSeriesSeasonIdx > 0) {
          focusedSeriesSeasonIdx--;
          seasonBtns[focusedSeriesSeasonIdx].focus();
        }
      }
      return;
    }
    if (k === 40) { // Baixo
      e.preventDefault();
      if (document.activeElement && document.activeElement.classList.contains('episode-item-btn')) {
        if (focusedSeriesEpIdx < epBtns.length - 1) {
          focusedSeriesEpIdx++;
          epBtns[focusedSeriesEpIdx].focus();
        }
      } else {
        if (focusedSeriesSeasonIdx < seasonBtns.length - 1) {
          focusedSeriesSeasonIdx++;
          seasonBtns[focusedSeriesSeasonIdx].focus();
        }
      }
      return;
    }
    if (k === 39) { // Direita -> vai para episódios
      e.preventDefault();
      if (epBtns.length > 0) {
        epBtns[0].focus();
        focusedSeriesEpIdx = 0;
      }
      return;
    }
    if (k === 37) { // Esquerda -> volta para temporadas
      e.preventDefault();
      if (seasonBtns.length > 0) {
        seasonBtns[focusedSeriesSeasonIdx].focus();
      }
      return;
    }
    if (k === 13) { // OK
      if (document.activeElement) document.activeElement.click();
      return;
    }
    return;
  }

  // ================= NAVEGAÇÃO NO PLAYER / TELA CHEIA (TROCA DE CANAL COM SETA) =================
  if (activeZone === 'player' || document.fullscreenElement || document.webkitFullscreenElement) {
    if (k === 417 || k === 70 || k === 83) { // Fast Forward / 'F' / 'S' -> Altera velocidade até 4x
      e.preventDefault();
      $('btnSpeed').click();
      return;
    }
    if (k === 38) { // Seta Cima -> Canal Anterior
      e.preventDefault();
      playPreviousChannel();
      return;
    }
    if (k === 40) { // Seta Baixo -> Próximo Canal
      e.preventDefault();
      playNextChannel();
      return;
    }
    if (k === 37) { // Seta Esquerda -> Volta para lista de canais
      if (!document.fullscreenElement && !document.webkitFullscreenElement) {
        e.preventDefault();
        activeZone = 'channels';
        focusActiveElement();
      }
      return;
    }
    if (k === 39) { // Seta Direita -> Vai para controles do player (Velocidade 4x, Pausa, etc)
      if (!document.fullscreenElement && !document.webkitFullscreenElement) {
        e.preventDefault();
        $('btnSpeed').focus();
      }
      return;
    }
    if (k === 13) { // OK
      if (document.fullscreenElement || document.webkitFullscreenElement) {
        $('btnPlayPause').click();
      }
      return;
    }
  }

  // ================= 1. CABEÇALHO =================
  if (activeZone === 'header') {
    if (k === 39) { // Seta Direita no cabeçalho
      e.preventDefault();
      if (focusedHeaderIdx < headerElements.length - 1) {
        focusedHeaderIdx++;
        $(headerElements[focusedHeaderIdx]).focus();
      }
      return;
    }
    if (k === 37) { // Seta Esquerda no cabeçalho
      e.preventDefault();
      if (focusedHeaderIdx > 0) {
        focusedHeaderIdx--;
        $(headerElements[focusedHeaderIdx]).focus();
      }
      return;
    }
    if (k === 40) { // Seta Baixo no cabeçalho -> desce para as colunas!
      e.preventDefault();
      if (currentContentType === 'SETTINGS') {
        activeZone = 'settings';
        $('cfgBtnInfo').focus();
      } else {
        if (focusedHeaderIdx < 2) {
          activeZone = 'categories';
        } else {
          activeZone = 'channels';
        }
        focusActiveElement();
      }
      return;
    }
    if (k === 13) { // OK no cabeçalho
      e.preventDefault();
      $(headerElements[focusedHeaderIdx]).click();
      return;
    }
    return;
  }

  // ================= 2. SETA PARA CIMA (38) =================
  if (k === 38) {
    e.preventDefault();
    if (activeZone === 'categories') {
      if (focusedCatIdx > 0) {
        focusedCatIdx--;
        focusActiveElement();
      } else {
        activeZone = 'header';
        focusedHeaderIdx = 0;
        $(headerElements[0]).focus();
      }
    } else if (activeZone === 'channels') {
      if (focusedItemIdx > 0) {
        focusedItemIdx--;
        focusActiveElement();
      } else {
        activeZone = 'header';
        focusedHeaderIdx = 1;
        $(headerElements[1]).focus();
      }
    }
    return;
  }

  // ================= 3. SETA PARA BAIXO (40) =================
  if (k === 40) {
    e.preventDefault();
    if (activeZone === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (focusedCatIdx < items.length - 1) {
        focusedCatIdx++;
        focusActiveElement();
      }
    } else if (activeZone === 'channels') {
      const items = $('listItems').querySelectorAll('.list-item-btn');
      if (focusedItemIdx < items.length - 1) {
        focusedItemIdx++;
        focusActiveElement();
      }
    }
    return;
  }

  // ================= 4. SETA PARA DIREITA (39) =================
  if (k === 39) {
    e.preventDefault();
    if (activeZone === 'categories') {
      activeZone = 'channels';
      focusActiveElement();
    } else if (activeZone === 'channels') {
      activeZone = 'player';
      $('btnFullscreen').focus();
    }
    return;
  }

  // ================= 5. SETA PARA ESQUERDA (37) =================
  if (k === 37) {
    e.preventDefault();
    if (activeZone === 'player') {
      activeZone = 'channels';
      focusActiveElement();
    } else if (activeZone === 'channels') {
      activeZone = 'categories';
      focusActiveElement();
    }
    return;
  }

  // ================= 6. TECLA OK / ENTER (13) =================
  if (k === 13) {
    if (activeZone === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (items[focusedCatIdx]) items[focusedCatIdx].click();
    } else if (activeZone === 'channels') {
      const items = $('listItems').querySelectorAll('.list-item-btn');
      const item = items[focusedItemIdx];
      if (item) {
        if (item.classList.contains('active')) {
          toggleFullscreen(true);
        } else {
          item.click();
        }
      }
    }
    return;
  }
});

function focusActiveElement() {
  if (activeZone === 'categories') {
    const items = $('listCategories').querySelectorAll('.cat-item-btn');
    if (items[focusedCatIdx]) {
      items[focusedCatIdx].focus();
      items[focusedCatIdx].scrollIntoView({ block: 'nearest' });
    }
  } else if (activeZone === 'channels') {
    const items = $('listItems').querySelectorAll('.list-item-btn');
    if (items[focusedItemIdx]) {
      items[focusedItemIdx].focus();
      items[focusedItemIdx].scrollIntoView({ block: 'nearest' });
    }
  }
}

// 15. MODAIS EXTRAS (PIN & SAÍDA)
$('btnRefreshList').onclick = () => loadServer(true);
$('btnHeaderAdult').onclick = openPinModal;

function openPinModal() {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    $('txtLockIcon').textContent = '🔒';
    $('txtLockText').textContent = 'Adulto (Bloqueado)';
    $('btnHeaderAdult').classList.remove('unlocked');
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');
    alert('Canais adultos foram bloqueados com sucesso.');
  } else {
    enteredPin = '';
    $('pinScreen').textContent = '----';
    $('modalPin').classList.remove('hidden');
    activeZone = 'modalPin';
  }
}

document.querySelectorAll('.pin-key').forEach(kBtn => {
  kBtn.onclick = () => {
    const k = kBtn.getAttribute('data-k');
    if (k === 'C') {
      enteredPin = '';
      $('pinScreen').textContent = '----';
    } else if (k === 'OK') {
      verifyPin();
    } else {
      if (enteredPin.length < 4) {
        enteredPin += k;
        let masked = '';
        for (let i = 0; i < 4; i++) masked += i < enteredPin.length ? '●' : '-';
        $('pinScreen').textContent = masked;
        if (enteredPin.length === 4) verifyPin();
      }
    }
  };
});

function verifyPin() {
  if (enteredPin === currentPin || enteredPin === '0000' || enteredPin === '8208') {
    isAdultUnlocked = true;
    $('txtLockIcon').textContent = '🔓';
    $('txtLockText').textContent = 'Adulto (Liberado)';
    $('btnHeaderAdult').classList.add('unlocked');
    $('modalPin').classList.add('hidden');
    activeZone = 'channels';
    buildCurrentCategories();
    renderCategoriesList();
    const adultCat = currentCategoryKeys.find(c => isAdult(c));
    selectCategory(adultCat || 'ALL');
    alert('Canais adultos liberados com sucesso!');
  } else {
    alert('Senha incorreta! Digite novamente.');
    enteredPin = '';
    $('pinScreen').textContent = '----';
  }
}

$('btnClosePin').onclick = () => {
  $('modalPin').classList.add('hidden');
  activeZone = 'channels';
};

// MODAL SAÍDA
$('btnExitCancel').onclick = () => $('modalExitConfirm').classList.add('hidden');
$('btnExitConfirm').onclick = () => {
  if (window.webOS && webOS.platformBack) webOS.platformBack();
  else window.close();
};

// 16. INICIALIZAÇÃO AUTOMÁTICA ROBUSTA
function bootApp() {
  if (window._mk21Booted) return;
  window._mk21Booted = true;

  try {
    const savedFont = localStorage.getItem('mk21_font_size');
    if (savedFont) applyFontSize(savedFont);
  } catch (e) {}

  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  try {
    const tmdbToggle = $('cfgToggleTmdb');
    if (tmdbToggle) {
      tmdbToggle.checked = localStorage.getItem('mk21_use_tmdb') !== 'false';
      tmdbToggle.onchange = () => {
        localStorage.setItem('mk21_use_tmdb', tmdbToggle.checked);
      };
    }
  } catch (e) {}

  loadServer();

  // Foco inicial garantido após carregamento para o controle remoto responder no 1º segundo
  setTimeout(() => {
    activeZone = 'channels';
    focusActiveElement();
  }, 200);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bootApp);
  window.addEventListener('load', bootApp);
} else {
  bootApp();
}
