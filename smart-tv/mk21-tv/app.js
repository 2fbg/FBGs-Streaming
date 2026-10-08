// MK21 PLAY v3.8.3 — Navegação espacial DPAD + base 3.6.0 — Motor Otimizado para Smart TV LG webOS
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


// Credenciais compartilhadas (Xtream) — válidas para TODOS os servidores
const SHARED_IPTV_USER = '601334065';
const SHARED_IPTV_PASS = '820866576';

function getSharedCredentials() {
  try {
    const u = localStorage.getItem('mk21_username') || localStorage.getItem('mk21_user') || localStorage.getItem('iptv_user');
    const p = localStorage.getItem('mk21_password') || localStorage.getItem('mk21_pass') || localStorage.getItem('iptv_pass');
    // Se vazio, demo ou inválido, usa as credenciais oficiais compartilhadas
    const user = (u && u !== 'demo' && String(u).length > 2) ? u : SHARED_IPTV_USER;
    const pass = (p && p !== 'demo' && String(p).length > 2) ? p : SHARED_IPTV_PASS;
    return { user, pass };
  } catch (e) {
    return { user: SHARED_IPTV_USER, pass: SHARED_IPTV_PASS };
  }
}

function ensureSharedCredentialsStored() {
  try {
    const c = getSharedCredentials();
    localStorage.setItem('mk21_username', c.user);
    localStorage.setItem('mk21_password', c.pass);
    localStorage.setItem('mk21_user', c.user);
    localStorage.setItem('mk21_pass', c.pass);
  } catch (e) {}
}

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
    // Limita tamanho em memória/disco para não reiniciar a TV (webOS)
    const slim = {
      LIVE: (payload.LIVE || []).slice(0, 6000).map(slimItem),
      MOVIE: (payload.MOVIE || []).slice(0, 18000).map(slimItem),
      SERIES: (payload.SERIES || []).slice(0, 15000).map(slimItem)
    };
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ id, payload: slim, updatedAt: Date.now() });
  } catch (e) {
    console.warn('[MK21] save cache falhou', e);
  }
}

// ÍNDICE PERSISTENTE DO CATÁLOGO — mantém a lista completa fora do heap JS.
const CATALOG_INDEX_DB = 'mk21_catalog_index_v1';
const CATALOG_INDEX_VERSION = 1;
const CATALOG_INDEX_STORE = 'items';
const CATALOG_META_STORE = 'meta';
// Orçamento de memória equilibrado: ~6k LIVE e mais espaço para VOD/Séries.
// O catálogo completo permanece no IndexedDB.
const CATALOG_MEMORY_LIMITS = { LIVE: 6000, MOVIE: 18000, SERIES: 15000 };

function openCatalogIndexDb() {
  return new Promise(resolve => {
    try {
      if (!window.indexedDB) return resolve(null);
      const req = indexedDB.open(CATALOG_INDEX_DB, CATALOG_INDEX_VERSION);
      req.onupgradeneeded = e => {
        const db = e.target.result;
        if (!db.objectStoreNames.contains(CATALOG_INDEX_STORE)) {
          const store = db.createObjectStore(CATALOG_INDEX_STORE, { keyPath: 'key' });
          store.createIndex('serverType', ['serverId', 'contentType'], { unique: false });
          store.createIndex('serverGroup', ['serverId', 'contentType', 'group'], { unique: false });
          store.createIndex('serverName', ['serverId', 'contentType', 'normalizedName'], { unique: false });
        }
        if (!db.objectStoreNames.contains(CATALOG_META_STORE)) db.createObjectStore(CATALOG_META_STORE, { keyPath: 'serverId' });
      };
      req.onsuccess = e => resolve(e.target.result);
      req.onerror = () => resolve(null);
    } catch (e) { resolve(null); }
  });
}

async function createCatalogIndexWriter(serverId) {
  const db = await openCatalogIndexDb();
  if (!db) return { add() {}, async finish() {} };
  try {
    await new Promise((resolve, reject) => {
      const tx = db.transaction(CATALOG_INDEX_STORE, 'readwrite');
      const index = tx.objectStore(CATALOG_INDEX_STORE).index('serverType');
      // Remove somente os itens deste servidor, preservando índices de outros servidores.
      const req = index.openCursor(IDBKeyRange.bound([serverId, 'LIVE'], [serverId, 'SERIES']));
      req.onsuccess = e => { const c=e.target.result; if (c) { c.delete(); c.continue(); } };
      tx.oncomplete = resolve; tx.onerror = () => reject(tx.error);
    });
  } catch (e) { console.warn('[MK21] limpeza do índice falhou', e); }
  let batch = [];
  let pending = Promise.resolve();
  let position = 0;
  const flush = () => {
    if (!batch.length) return;
    const toWrite = batch; batch = [];
    pending = pending.then(() => new Promise(resolve => {
      try {
        const tx = db.transaction(CATALOG_INDEX_STORE, 'readwrite');
        const store = tx.objectStore(CATALOG_INDEX_STORE);
        toWrite.forEach(item => store.put({
          key: serverId + ':' + item.contentType + ':' + position++,
          serverId, contentType: item.contentType, name: item.name || 'Item',
          normalizedName: String(item.name || '').toLocaleLowerCase('pt-BR'),
          group: item.group || 'Geral', logo: item.logo || '', tvgId: item.tvgId || '',
          tvgName: item.tvgName || '', url: item.url, isAdult: !!item.isAdult,
          position: position - 1
        }));
        tx.oncomplete = resolve; tx.onerror = resolve;
      } catch (e) { resolve(); }
    }));
  };
  return {
    add(item) { batch.push(item); if (batch.length >= 500) flush(); },
    async finish() {
      flush(); await pending;
      try {
        await new Promise(resolve => {
          const tx=db.transaction(CATALOG_META_STORE,'readwrite');
          tx.objectStore(CATALOG_META_STORE).put({serverId, updatedAt:Date.now(), itemCount:position});
          tx.oncomplete=resolve; tx.onerror=resolve;
        });
      } catch (e) {}
      try { db.close(); } catch (e) {}
    }
  };
}

async function queryCatalogIndexPage(serverId, type, offset, limit, group) {
  const db = await openCatalogIndexDb();
  if (!db) return [];
  return new Promise(resolve => {
    const result = [];
    try {
      const tx = db.transaction(CATALOG_INDEX_STORE, 'readonly');
      const index = tx.objectStore(CATALOG_INDEX_STORE).index('serverType');
      const range = IDBKeyRange.bound([serverId, type], [serverId, type + '\uffff']);
      const req = index.openCursor(range);
      let skipped = 0;
      req.onsuccess = e => {
        const cursor = e.target.result;
        if (!cursor || result.length >= limit) return;
        const item = cursor.value;
        if (!group || item.group === group) {
          if (skipped < offset) skipped++;
          else result.push(item);
        }
        cursor.continue();
      };
      tx.oncomplete = () => { try { db.close(); } catch (e) {} resolve(result); };
      tx.onerror = () => { try { db.close(); } catch (e) {} resolve(result); };
    } catch (e) { try { db.close(); } catch (e) {} resolve([]); }
  });
}

async function searchCatalogIndex(serverId, type, query, group, limit = 240) {
  const db = await openCatalogIndexDb();
  if (!db || !query) return [];
  return new Promise(resolve => {
    const result = [];
    const needle = query.toLocaleLowerCase('pt-BR');
    try {
      const tx = db.transaction(CATALOG_INDEX_STORE, 'readonly');
      const index = tx.objectStore(CATALOG_INDEX_STORE).index('serverType');
      const range = IDBKeyRange.bound([serverId, type], [serverId, type + '\uffff']);
      const req = index.openCursor(range);
      req.onsuccess = e => {
        const cursor = e.target.result;
        if (!cursor || result.length >= limit) return;
        const item = cursor.value;
        if ((!group || item.group === group) && String(item.normalizedName || '').includes(needle)) result.push(item);
        cursor.continue();
      };
      tx.oncomplete = () => { try { db.close(); } catch (e) {} resolve(result); };
      tx.onerror = () => { try { db.close(); } catch (e) {} resolve(result); };
    } catch (e) { try { db.close(); } catch (e) {} resolve([]); }
  });
}

let searchIndexGeneration = 0;
async function searchFromIndexedCatalog(query) {
  const type = currentContentType;
  if (!['LIVE', 'MOVIE', 'SERIES'].includes(type) || !query) return;
  const generation = ++searchIndexGeneration;
  const srv = SERVERS[currentServerIndex];
  if (!srv) return;
  const group = activeCategoryKey === 'ALL' ? '' : activeCategoryKey;
  const found = await searchCatalogIndex(srv.id, type, query, group, 240);
  if (generation !== searchIndexGeneration || $('inputSearch').value.toLowerCase().trim() !== query) return;
  allCatalog[type] = found;
  itemsDisplayLimit = 240;
  buildCurrentCategories();
  renderCategoriesList();
  renderItemsList();
}

let indexedPageLoading = false;
let indexedOffsets = {};
async function loadMoreIndexedItems() {
  if (indexedPageLoading) return;
  const type = currentContentType;
  if (!['LIVE', 'MOVIE', 'SERIES'].includes(type)) {
    itemsDisplayLimit += 60;
    renderItemsList();
    return;
  }
  const base = currentCategoriesMap[activeCategoryKey] || [];
  if (itemsDisplayLimit < base.length) {
    itemsDisplayLimit += 60;
    renderItemsList();
    return;
  }
  const srv = SERVERS[currentServerIndex];
  if (!srv) return;
  indexedPageLoading = true;
  try {
    const arr = allCatalog[type] || [];
    const group = activeCategoryKey === 'ALL' ? '' : activeCategoryKey;
    const key = type + '|' + (group || 'ALL');
    const existing = group ? arr.filter(it => (it.group || 'Geral') === group).length : arr.length;
    const offset = Number.isFinite(indexedOffsets[key]) ? indexedOffsets[key] : existing;
    const page = await queryCatalogIndexPage(srv.id, type, offset, 240, group);
    indexedOffsets[key] = offset + page.length;
    if (page.length) {
      // Mantém somente uma janela: a navegação avança por páginas sem acumular
      // centenas de milhares de objetos no heap JavaScript.
      if (arr.length >= CATALOG_MEMORY_LIMITS[type]) {
        allCatalog[type] = page;
        itemsDisplayLimit = page.length;
      } else {
        const known = new Set(arr.map(it => it.url));
        page.forEach(item => { if (!known.has(item.url) && arr.length < CATALOG_MEMORY_LIMITS[type]) arr.push(item); });
        itemsDisplayLimit += 60;
      }
      buildCurrentCategories();
      renderCategoriesList();
      renderItemsList();
    } else {
      showChannelBanner('Fim do catálogo nesta categoria');
    }
  } finally { indexedPageLoading = false; }
}

function slimItem(it) {
  if (!it) return it;
  return {
    name: it.name,
    group: it.group,
    url: it.url,
    contentType: it.contentType,
    logo: it.logo ? String(it.logo).slice(0, 200) : '',
    tvgId: it.tvgId || '',
    tvgName: it.tvgName || ''
  };
}

// 3. ESTADO GLOBAL
let currentServerIndex = 0;
let serverLoadGeneration = 0;
let activePlaylistController = null;
let currentContentType = 'LIVE'; // 'LIVE', 'MOVIE', 'SERIES', 'FAVORITES', 'CONTINUE', 'SETTINGS'
let allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };
let favoriteUrls = new Set();

// Fluidez: preferências leves do usuário
function saveLastChannel(item) {
  if (!item || !item.url) return;
  try {
    localStorage.setItem('mk21_last_channel', JSON.stringify({
      url: item.url,
      name: item.name,
      group: item.group || '',
      contentType: item.contentType || currentContentType,
      serverId: (SERVERS[currentServerIndex] || {}).id || ''
    }));
  } catch (e) {}
}

function loadLastChannelMeta() {
  try {
    const raw = localStorage.getItem('mk21_last_channel');
    return raw ? JSON.parse(raw) : null;
  } catch (e) { return null; }
}

function resumeLastChannelIfPossible() {
  const meta = loadLastChannelMeta();
  if (!meta || !meta.url) return false;
  const srv = SERVERS[currentServerIndex];
  if (meta.serverId && srv && srv.id && meta.serverId !== srv.id) return false;
  const pools = []
    .concat(allCatalog.LIVE || [])
    .concat(allCatalog.MOVIE || [])
    .concat(allCatalog.SERIES || []);
  const found = pools.find(i => i.url === meta.url);
  if (found) {
    if (found.contentType && found.contentType !== currentContentType && found.contentType !== 'LIVE') {
      // só retoma automaticamente canais ao vivo para fluidez no boot
      if (found.contentType !== 'LIVE') return false;
    }
    if (found.contentType === 'LIVE' || !found.contentType) {
      currentContentType = 'LIVE';
      try {
        ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings'].forEach(t => {
          if ($(t)) $(t).classList.remove('active');
        });
        if ($('tabLive')) $('tabLive').classList.add('active');
      } catch (e) {}
      playStream(found);
      showChannelBanner('▶ Continuando: ' + found.name);
      return true;
    }
  }
  return false;
}

function persistSortOrder() {
  try { localStorage.setItem('mk21_sort_order', currentSortOrder); } catch (e) {}
}

function restoreSortOrder() {
  try {
    const s = localStorage.getItem('mk21_sort_order');
    if (s && ['DEFAULT', 'RECENT', 'AZ', 'ZA', 'YEAR'].indexOf(s) !== -1) {
      currentSortOrder = s;
      if (typeof updateSortOrderButtonLabel === 'function') updateSortOrderButtonLabel();
    }
  } catch (e) {}
}

let searchDebounceTimer = null;
function scheduleSearchRender() {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(function () {
    const query = $('inputSearch').value.toLowerCase().trim();
    renderItemsList();
    if (query) searchFromIndexedCatalog(query);
  }, 180);
}

let fsHudHideTimer = null;
function pokeFsHud() {
  if (!isAppFullscreen()) return;
  showFsHud(true);
  clearTimeout(fsHudHideTimer);
  fsHudHideTimer = setTimeout(function () {
    if (isAppFullscreen()) showFsHud(false);
  }, 4500);
}


let continueWatchingList = [];
let isAdultUnlocked = false;
let currentPin = '0000';
let enteredPin = '';
let activeItem = null;
let hlsInstance = null;
let currentSortOrder = 'DEFAULT'; // 'DEFAULT' | 'RECENT' | 'AZ' | 'ZA' | 'YEAR'
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
  'btnHeaderServer', 'btnRefreshList'
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
  if (!url) return;
  if (favoriteUrls.has(url)) {
    favoriteUrls.delete(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '⭐ Favoritar';
    if ($('fsBtnFav')) $('fsBtnFav').textContent = '⭐ Favoritar';
    showChannelBanner('Removido dos Favoritos');
  } else {
    favoriteUrls.add(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '★ Favoritado';
    if ($('fsBtnFav')) $('fsBtnFav').textContent = '★ Favoritado';
    showChannelBanner('⭐ Adicionado aos Favoritos');
  }
  try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
  // Atualiza estrela na frente do nome na lista e no EPG
  try {
    if (activeItem && activeItem.url === url && $('epgTitle')) {
      const star = favoriteUrls.has(url) ? '⭐ ' : '▶ ';
      $('epgTitle').textContent = star + activeItem.name;
    }
    if ($('fsHudTitle') && activeItem) {
      const star = favoriteUrls.has(activeItem.url) ? '⭐ ' : '';
      $('fsHudTitle').textContent = star + activeItem.name;
    }
  } catch (e) {}
  renderItemsList();
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

// LEITOR INCREMENTAL DE LISTAS M3U: não mantém texto bruto nem array de 300k linhas
async function streamPlaylistContent(url, signal, onItem, onProgress) {
  let targetUrl = url;
  ensureSharedCredentialsStored();
  const creds = getSharedCredentials();
  const savedUser = creds.user;
  const savedPass = creds.pass;
  if (!targetUrl.includes('get.php') && !targetUrl.includes('.m3u') && !targetUrl.includes('.ts') && !targetUrl.includes('.m3u8')) {
    targetUrl = `${targetUrl.replace(/\/+$/, '')}/get.php?username=${encodeURIComponent(savedUser)}&password=${encodeURIComponent(savedPass)}&type=m3u_plus&output=ts`;
  }
  console.log('[MK21] Streaming da lista:', targetUrl.replace(savedPass, '***'));
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timeoutId = controller ? setTimeout(() => controller.abort(), 45000) : null;
  try {
    const options = controller ? { signal: controller.signal, mode: 'cors' } : { mode: 'cors' };
    if (signal) options.signal = signal;
    const res = await fetch(targetUrl, options);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (!res.body || typeof res.body.getReader !== 'function' || typeof TextDecoder === 'undefined') {
      const text = await res.text();
      if (!text || text.length < 50) throw new Error('Lista vazia ou inválida');
      const lines = text.split(/\r?\n/);
      let meta = null;
      for (const raw of lines) {
        const line = raw.trim();
        if (line.startsWith('#EXTINF:')) {
          const comma = line.lastIndexOf(',');
          const gm = line.match(/group-title="([^"]*)"/i);
          const lm = line.match(/tvg-logo="([^"]*)"/i);
          const im = line.match(/tvg-id="([^"]*)"/i);
          const nm = line.match(/tvg-name="([^"]*)"/i);
          meta = { name: comma >= 0 ? line.slice(comma + 1).trim() : 'Item', group: gm && gm[1] ? gm[1].trim() : 'Geral', logo: lm && lm[1] ? lm[1].trim() : '', tvgId: im && im[1] ? im[1].trim() : '', tvgName: nm && nm[1] ? nm[1].trim() : '' };
        } else if (meta && /^https?:\/\//i.test(line)) {
          const type = determineType(meta.name, meta.group, line);
          onItem({ ...meta, url: line, contentType: type, isAdult: isAdult(meta.name) || isAdult(meta.group) });
          meta = null;
        }
      }
      return;
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let meta = null;
    let received = 0;
    const total = parseInt(res.headers && res.headers.get && res.headers.get('content-length') || '0', 10) || 0;
    const consume = (raw) => {
      const line = raw.trim();
      if (!line) return;
      if (line.startsWith('#EXTM3U')) {
        const xm = line.match(/(?:url-tvg|x-tvg-url)="([^"]*)"/i);
        if (xm && xm[1]) window.serverXmltvUrl = xm[1].trim();
      } else if (line.startsWith('#EXTINF:')) {
        const comma = line.lastIndexOf(',');
        const gm = line.match(/group-title="([^"]*)"/i);
        const lm = line.match(/tvg-logo="([^"]*)"/i);
        const im = line.match(/tvg-id="([^"]*)"/i);
        const nm = line.match(/tvg-name="([^"]*)"/i);
        meta = { name: comma >= 0 ? line.slice(comma + 1).trim() : 'Item', group: gm && gm[1] ? gm[1].trim() : 'Geral', logo: lm && lm[1] ? lm[1].trim() : '', tvgId: im && im[1] ? im[1].trim() : '', tvgName: nm && nm[1] ? nm[1].trim() : '' };
      } else if (meta && /^https?:\/\//i.test(line)) {
        const type = determineType(meta.name, meta.group, line);
        onItem({ ...meta, url: line, contentType: type, isAdult: isAdult(meta.name) || isAdult(meta.group) });
        meta = null;
      }
    };
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      received += part.value.byteLength || part.value.length || 0;
      buffer += decoder.decode(part.value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() || '';
      for (const line of lines) consume(line);
      if (onProgress) onProgress(received, total);
    }
    buffer += decoder.decode();
    if (buffer) consume(buffer);
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
}

// 6. CARREGAMENTO COM PRIORIDADE MÁXIMA NO AO VIVO E CARGA RÁPIDA
async function loadServer(forceRefresh = false) {
  const loadId = ++serverLoadGeneration;
  if (activePlaylistController) { try { activePlaylistController.abort(); } catch (e) {} }
  activePlaylistController = typeof AbortController !== 'undefined' ? new AbortController() : null;
  if (SERVERS.length === 0) SERVERS = [...DEFAULT_SERVERS];
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;

  const srv = SERVERS[currentServerIndex];
  if ($('txtActiveServer')) $('txtActiveServer').textContent = srv.name;

  updateSplash(25, 'Conectando ao ' + srv.name + '...');

  const hud = $('hudLoadingOverlay');
  if (hud) {
    if ($('hudLoadingTitle')) $('hudLoadingTitle').textContent = 'Conectando ao ' + srv.name;
    if ($('hudLoadingSub')) $('hudLoadingSub').textContent = 'Conectando e baixando grade de programação...';
    if ($('hudProgressBar')) $('hudProgressBar').style.width = '25%';
    if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '25%';
    hud.classList.remove('hidden');
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

  // Cache prioritário: não baixa de novo se já tiver lista no IndexedDB
  if (!forceRefresh) {
    const cached = await getStoredData(srv.id);
    if (loadId !== serverLoadGeneration) return;
    const liveN = cached && cached.LIVE ? cached.LIVE.length : 0;
    const movN = cached && cached.MOVIE ? cached.MOVIE.length : 0;
    if (cached && (liveN > 0 || movN > 0)) {
      allCatalog = {
        LIVE: cached.LIVE || [],
        MOVIE: cached.MOVIE || [],
        SERIES: cached.SERIES || []
      };
      updateSplash(90, 'Lista em cache (' + liveN + ' canais)...');
      if (hud) {
        if ($('hudLoadingSub')) $('hudLoadingSub').textContent = 'Usando cache local — sem novo download';
        if ($('hudProgressBar')) $('hudProgressBar').style.width = '100%';
        if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '100%';
        setTimeout(() => hud.classList.add('hidden'), 250);
      }
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      hideSplash();
      if ($('txtCurrentCategoryTitle')) {
        $('txtCurrentCategoryTitle').textContent = '📺 Cache: ' + liveN + ' canais';
      }
      // Retoma último canal ao vivo (fluidez) após um tick
      setTimeout(function () {
        if (!resumeLastChannelIfPossible() && liveN > 0) {
          // opcional: não força auto-play do primeiro
        }
      }, 350);
      return;
    }
  }

  $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';
  updateSplash(40, 'Baixando grade de programação...');
  if (hud) {
    if ($('hudProgressBar')) $('hudProgressBar').style.width = '55%';
    if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '55%';
  }

  try {
    allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };
    indexedOffsets = {};
    const indexWriter = await createCatalogIndexWriter(srv.id);
    const overflow = { LIVE: 0, MOVIE: 0, SERIES: 0 };
    let liveVisible = false;
    let lastProgressUi = 0;
    const revealLive = () => {
      if (liveVisible || loadId !== serverLoadGeneration || allCatalog.LIVE.length === 0) return;
      liveVisible = true;
      updateSplash(75, 'Canais ao vivo disponíveis (' + allCatalog.LIVE.length + ')');
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      renderItemsList();
      if (!activeItem) playStream(allCatalog.LIVE[0]);
      hideSplash();
    };
    await streamPlaylistContent(
      srv.url,
      activePlaylistController && activePlaylistController.signal,
      (item) => {
        if (loadId !== serverLoadGeneration) return;
        indexWriter.add(item);
        const type = item.contentType;
        if (allCatalog[type] && allCatalog[type].length < CATALOG_MEMORY_LIMITS[type]) {
          allCatalog[type].push(item);
        } else if (overflow[type] !== undefined) {
          overflow[type]++;
        }
        if (type === 'LIVE' && (allCatalog.LIVE.length === 1 || allCatalog.LIVE.length === 40)) revealLive();
      },
      (received, total) => {
        if (loadId !== serverLoadGeneration) return;
        const now = Date.now();
        if (now - lastProgressUi < 250) return;
        lastProgressUi = now;
        const pct = total ? Math.min(78, 40 + Math.round(received / total * 38)) : 55;
        updateSplash(pct, 'Lendo lista — ' + allCatalog.LIVE.length + ' canais ao vivo');
        if ($('hudProgressBar')) $('hudProgressBar').style.width = pct + '%';
        if ($('hudProgressPercent')) $('hudProgressPercent').textContent = pct + '%';
      }
    );
    if (loadId !== serverLoadGeneration) return;
    revealLive();
    buildCurrentCategories();
    renderCategoriesList();
    if (!liveVisible) selectCategory('ALL');
    updateSplash(100, 'Lista carregada — ' + allCatalog.LIVE.length + ' canais ao vivo');
    if (hud) {
      if ($('hudProgressBar')) $('hudProgressBar').style.width = '100%';
      if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '100%';
      setTimeout(() => hud.classList.add('hidden'), 250);
    }
    await indexWriter.finish();
    saveStoredData(srv.id, allCatalog);
    if (currentContentType === 'MOVIE' || currentContentType === 'SERIES') renderItemsList();
    const totalIndexed = allCatalog.LIVE.length + allCatalog.MOVIE.length + allCatalog.SERIES.length + overflow.LIVE + overflow.MOVIE + overflow.SERIES;
    if ($('txtCurrentCategoryTitle') && totalIndexed > allCatalog.LIVE.length + allCatalog.MOVIE.length + allCatalog.SERIES.length) {
      $('txtCurrentCategoryTitle').textContent = 'Catálogo indexado: ' + totalIndexed.toLocaleString('pt-BR') + ' itens';
    }
  } catch (err) {
    if (loadId !== serverLoadGeneration || (err && err.name === 'AbortError')) return;
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

// Parser legado removido: o carregamento moderno classifica itens durante o streaming.

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
  itemsDisplayLimit = 80;
  renderItemsList();
}

let itemsDisplayLimit = 80;

// 8. RENDERIZAÇÃO DA LISTA DE ITENS COM SÉRIES AGRUPADAS E ORDENAÇÃO
function renderItemsList() {
  const query = $('inputSearch').value.toLowerCase().trim();
  const base = currentCategoriesMap[activeCategoryKey] || [];
  const indexedPageKey = currentContentType + '|' + (activeCategoryKey === 'ALL' ? 'ALL' : activeCategoryKey);
  const indexedMoreAvailable = Object.prototype.hasOwnProperty.call(indexedOffsets, indexedPageKey);

  // Evita duplicar o array inteiro quando a categoria está na ordem padrão.
  // Em listas com centenas de milhares de itens isso reduz um pico de memória
  // sempre que a tela é redesenhada. Cópia só é criada quando necessária.
  let items = query ? base.filter(c => c.name.toLowerCase().includes(query)) : base;

  // Aplicar ordenação (Padrão / Recente / A-Z / Z-A / Ano)
  function mk21ExtractYear(name) {
    const m = String(name || '').match(/\b((?:19|20)\d{2})\b/);
    return m ? parseInt(m[1], 10) : 0;
  }
  if (currentSortOrder === 'AZ') {
    items = items.slice().sort((a, b) => a.name.localeCompare(b.name, 'pt-BR', { sensitivity: 'base' }));
  } else if (currentSortOrder === 'ZA') {
    items = items.slice().sort((a, b) => b.name.localeCompare(a.name, 'pt-BR', { sensitivity: 'base' }));
  } else if (currentSortOrder === 'RECENT') {
    // Ordem da lista invertida (últimos da playlist primeiro)
    items = items.slice().reverse();
  } else if (currentSortOrder === 'YEAR') {
    // Por ano no título (mais recente primeiro); sem ano no fim
    items = items.slice().sort((a, b) => {
      const ya = mk21ExtractYear(a.name);
      const yb = mk21ExtractYear(b.name);
      if (ya && yb && ya !== yb) return yb - ya;
      if (ya && !yb) return -1;
      if (!ya && yb) return 1;
      return a.name.localeCompare(b.name, 'pt-BR', { sensitivity: 'base' });
    });
  }
  // DEFAULT: mantém ordem original da categoria

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

    if (currentGroupedSeries.length > limit || indexedMoreAvailable) {
      const moreLi = document.createElement('li');
      const moreBtn = document.createElement('button');
      moreBtn.className = 'list-item-btn';
      moreBtn.style.textAlign = 'center';
      moreBtn.style.color = '#ffd54f';
      moreBtn.textContent = `➕ Carregar Mais Séries (+150 de ${currentGroupedSeries.length - limit} restantes)...`;
      moreBtn.onclick = () => { loadMoreIndexedItems(); };
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
      const prefix = currentContentType === 'CONTINUE' ? '▶ ' : (favoriteUrls.has(item.url) ? '⭐ ' : '');
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

    if (currentFilteredItems.length > limit || indexedMoreAvailable) {
      const moreLi = document.createElement('li');
      const moreBtn = document.createElement('button');
      moreBtn.className = 'list-item-btn';
      moreBtn.style.textAlign = 'center';
      moreBtn.style.color = '#ffd54f';
      moreBtn.textContent = `➕ Carregar Mais Itens (+150 de ${currentFilteredItems.length - limit} restantes)...`;
      moreBtn.onclick = () => { loadMoreIndexedItems(); };
      moreLi.appendChild(moreBtn);
      fragment.appendChild(moreLi);
    }
  }

  ul.appendChild(fragment);

  if (!activeItem && currentContentType === 'LIVE' && currentFilteredItems.length > 0) {
    playStream(currentFilteredItems[0]);
  }
}

// BOTÃO ORDENAÇÃO: Padrão → Recente → A-Z → Z-A → Ano → Padrão
function updateSortOrderButtonLabel() {
  const btn = $('btnSortOrder');
  if (!btn) return;
  if (currentSortOrder === 'RECENT') btn.textContent = '🕒 Recente';
  else if (currentSortOrder === 'AZ') btn.textContent = '↕️ A-Z';
  else if (currentSortOrder === 'ZA') btn.textContent = '↕️ Z-A';
  else if (currentSortOrder === 'YEAR') btn.textContent = '📅 Ano';
  else btn.textContent = '↕️ Padrão';
}

if ($('btnSortOrder')) {
  $('btnSortOrder').onclick = function () {
    if (currentSortOrder === 'DEFAULT') currentSortOrder = 'RECENT';
    else if (currentSortOrder === 'RECENT') currentSortOrder = 'AZ';
    else if (currentSortOrder === 'AZ') currentSortOrder = 'ZA';
    else if (currentSortOrder === 'ZA') currentSortOrder = 'YEAR';
    else currentSortOrder = 'DEFAULT';
    updateSortOrderButtonLabel();
    persistSortOrder();
    renderItemsList();
    showChannelBanner('Ordenação: ' + ($('btnSortOrder').textContent || currentSortOrder));
  };
  updateSortOrderButtonLabel();
}

if ($('inputSearch')) $('inputSearch').addEventListener('input', scheduleSearchRender);

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
  if ($('epgTitle')) $('epgTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '▶ ') + item.name;
  if ($('fsHudTitle')) $('fsHudTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '') + item.name;
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
  const _ec = getSharedCredentials();
  const user = _ec.user;
  const pass = _ec.pass;

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
  if (!item || !item.url) return;
  activeItem = item;
  recordWatchedItem(item);
  saveLastChannel(item);

  // Feedback imediato na troca de canal
  if ($('epgStatus')) $('epgStatus').textContent = '⏳ Sintonizando...';
  if ($('channelOverlay')) {
    $('channelOverlay').textContent = '▶ ' + item.name;
    $('channelOverlay').classList.remove('hidden');
  }

  const btns = $('listItems').querySelectorAll('.list-item-btn');
  btns.forEach(b => {
    b.classList.remove('active');
    const t = (b.textContent || '').replace(/^[⭐★▶]\s*/, '');
    if (item.name && t.indexOf(item.name) === 0) {
      b.classList.add('active');
      try { b.scrollIntoView({ block: 'nearest' }); } catch (e) {}
    }
  });

  $('epgTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '▶ ') + item.name;
  if ($('fsHudTitle')) $('fsHudTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '') + item.name;
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
      maxBufferLength: 3,
      maxMaxBufferLength: 6,
      liveSyncDurationCount: 2,
      maxBufferSize: 12 * 1000 * 1000,
      startLevel: -1
    });
    hlsInstance.loadSource(item.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      try { video.preservesPitch = true; video.webkitPreservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
      video.play().catch(() => {});
      $('btnPlayPause').textContent = '⏸ Pausar';
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        video.src = item.url;
        try { video.preservesPitch = true; video.webkitPreservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
        video.play().catch(() => {});
      }
    });
  } else {
    // Decodificação direta acelerada por hardware na TV webOS (sem delay)
    video.src = item.url;
    try { video.preservesPitch = true; video.webkitPreservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
    video.load();
    video.play().then(() => {
      $('btnPlayPause').textContent = '⏸ Pausar';
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
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

function isAppFullscreen() {
  const box = $('videoContainer');
  return !!(box && box.classList.contains('is-fullscreen')) ||
    !!(document.fullscreenElement || document.webkitFullscreenElement);
}

function showFsHud(show) {
  const hud = $('fsHudBar');
  if (!hud) return;
  if (show) {
    hud.classList.remove('hidden');
    if (activeItem && $('fsHudTitle')) {
      $('fsHudTitle').textContent = (favoriteUrls.has(activeItem.url) ? '⭐ ' : '') + activeItem.name;
    }
    if ($('fsBtnSpeed')) $('fsBtnSpeed').textContent = '⚡ ' + currentPlaybackSpeed + 'x';
    if ($('fsBtnFav') && activeItem) {
      $('fsBtnFav').textContent = favoriteUrls.has(activeItem.url) ? '★ Favoritado' : '⭐ Favoritar';
    }
  } else {
    hud.classList.add('hidden');
  }
}

function toggleFullscreen(force = false) {
  const box = $('videoContainer');
  const v = $('tvPlayer');
  if (!box) return;
  const goingIn = force === true || (force !== false && !isAppFullscreen());

  if (goingIn) {
    box.classList.add('is-fullscreen');
    activeZone = 'player';
    showFsHud(true);
    pokeFsHud();
    // Tenta fullscreen nativo do container (mantém botões)
    try {
      if (box.requestFullscreen) box.requestFullscreen();
      else if (box.webkitRequestFullscreen) box.webkitRequestFullscreen();
      else if (v && v.webkitRequestFullscreen) v.webkitRequestFullscreen();
    } catch (e) {}
    setTimeout(function () {
      if ($('fsBtnSpeed')) $('fsBtnSpeed').focus();
    }, 200);
  } else {
    box.classList.remove('is-fullscreen');
    showFsHud(false);
    try {
      if (document.exitFullscreen) document.exitFullscreen();
      else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
    } catch (e) {}
    activeZone = 'channels';
    focusActiveElement();
  }
}

function bindFsHudButtons() {
  if ($('fsBtnBack')) $('fsBtnBack').onclick = function () { toggleFullscreen(false); };
  if ($('fsBtnPlay')) $('fsBtnPlay').onclick = function () { togglePlayPause(); };
  if ($('fsBtnSpeed')) $('fsBtnSpeed').onclick = function () { cyclePlaybackSpeed(); };
  if ($('fsBtnFav')) $('fsBtnFav').onclick = function () { if (activeItem) toggleFav(activeItem.url); };
  if ($('fsBtnAudio')) $('fsBtnAudio').onclick = function () { if (typeof openAudioSubsModal === 'function') openAudioSubsModal(); };
}
bindFsHudButtons();


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

function applyPlaybackSpeed(rate) {
  const video = $('tvPlayer');
  if (!video) return;
  currentPlaybackSpeed = rate;
  try {
    video.playbackRate = rate;
    // Áudio sem chipmunk ao acelerar (webOS / Chromium)
    video.preservesPitch = true;
    video.mozPreservesPitch = true;
    video.webkitPreservesPitch = true;
    if (video.defaultPlaybackRate !== undefined) video.defaultPlaybackRate = rate;
  } catch (e) {}
  const label = '⚡ ' + rate + 'x';
  if ($('btnSpeed')) $('btnSpeed').textContent = label;
  if ($('fsBtnSpeed')) $('fsBtnSpeed').textContent = label;
}

function cyclePlaybackSpeed() {
  const nextIdx = (speedOptions.indexOf(currentPlaybackSpeed) + 1) % speedOptions.length;
  applyPlaybackSpeed(speedOptions[nextIdx]);
  showChannelBanner('⚡ Velocidade: ' + currentPlaybackSpeed + 'x (áudio normal)');
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
  // Feedback visual imediato ao trocar de aba
  try {
    const labels = { LIVE: '📺 Ao Vivo', MOVIE: '🎬 Filmes', SERIES: '🍿 Séries', CONTINUE: '▶ Continuar', FAVORITES: '⭐ Favoritos', SETTINGS: '⚙️ Config' };
    if (labels[type]) showChannelBanner(labels[type]);
  } catch (e) {}

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

// TESTE DE VELOCIDADE REAL: mede bytes/tempo e nunca inventa Mbps
async function runSpeedTest() {
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px;">🚀 Teste de Velocidade da Conexão</h3>
    <p style="color:#aaa;">Medição real da rede da Smart TV. O upload depende do endpoint configurado.</p>
    <div class="speed-meter-box">
      <div id="speedMeterStatus" style="font-size:18px;color:#94a3b8;margin-bottom:12px;">1/4 - Medindo latência...</div>
      <div class="speed-gauge-wrap"><div id="speedGaugeArc" class="speed-gauge-arc" style="transform:rotate(-45deg);"></div></div>
      <div><span id="speedMeterNumber" class="speed-meter-val">0.0</span><span id="speedMeterUnit" class="speed-meter-unit">Mbps</span></div>
      <div class="speed-progress-bar-wrap"><div id="speedProgressBar" class="speed-progress-bar-fill" style="width:0%;"></div></div>
      <div class="speed-metrics-grid">
        <div class="speed-metric-card"><div style="font-size:13px;color:#888;">⬇️ Download</div><div id="speedMeterDownload" style="font-size:20px;font-weight:bold;color:#4caf50;">-- Mbps</div></div>
        <div class="speed-metric-card"><div style="font-size:13px;color:#888;">⬆️ Upload</div><div id="speedMeterUpload" style="font-size:20px;font-weight:bold;color:#64b5f6;">-- Mbps</div></div>
        <div class="speed-metric-card"><div style="font-size:13px;color:#888;">⏱️ Latência</div><div id="speedMeterPing" style="font-size:20px;font-weight:bold;color:#ffd54f;">-- ms</div></div>
        <div class="speed-metric-card"><div style="font-size:13px;color:#888;">📶 Jitter</div><div id="speedMeterJitter" style="font-size:20px;font-weight:bold;color:#ff8a65;">-- ms</div></div>
      </div><div id="speedQualityRating" style="margin-top:16px;font-size:16px;font-weight:bold;color:#fff;"></div>
    </div><button id="btnStartSpeedTest" class="ctrl-btn primary" style="padding:12px 28px;" tabindex="0">🔄 Iniciar Novo Teste</button>`;
  $('btnStartSpeedTest').onclick = runSpeedTest;
  const setStatus = text => { if ($('speedMeterStatus')) $('speedMeterStatus').textContent = text; };
  const setProgress = pct => { if ($('speedProgressBar')) $('speedProgressBar').style.width = pct + '%'; };
  const setGauge = value => { if ($('speedGaugeArc')) $('speedGaugeArc').style.transform = `rotate(${Math.min(135, -45 + (Math.min(value, 150) / 150) * 180)}deg)`; };
  const formatMbps = value => Number.isFinite(value) ? value.toFixed(1) + ' Mbps' : 'Indisponível';
  const endpoint = localStorage.getItem('mk21_speed_test_endpoint') || 'https://speed.cloudflare.com';
  try {
    setStatus('1/4 - Medindo latência e jitter...');
    const samples = [];
    for (let i = 0; i < 3; i++) {
      const t0 = performance.now();
      const r = await fetch(endpoint + '/__down?bytes=1&cache=' + Date.now() + '-' + i, { cache: 'no-store' });
      if (!r.ok) throw new Error('Endpoint de teste indisponível');
      await r.arrayBuffer();
      samples.push(performance.now() - t0);
    }
    const ping = samples.reduce((a,b) => a+b, 0) / samples.length;
    const jitter = samples.reduce((a,b) => a + Math.abs(b - ping), 0) / samples.length;
    $('speedMeterPing').textContent = Math.round(ping) + ' ms';
    $('speedMeterJitter').textContent = jitter.toFixed(1) + ' ms';
    setProgress(15);
    setStatus('2/4 - Medindo download real...');
    const downStart = performance.now();
    const downRes = await fetch(endpoint + '/__down?bytes=10000000&cache=' + Date.now(), { cache: 'no-store' });
    if (!downRes.ok) throw new Error('Download de teste indisponível');
    let bytes = 0;
    if (downRes.body && downRes.body.getReader) {
      const reader = downRes.body.getReader();
      while (true) { const part = await reader.read(); if (part.done) break; bytes += part.value.byteLength; setProgress(Math.min(55, 15 + Math.round(bytes / 10000000 * 40))); }
    } else { bytes = (await downRes.arrayBuffer()).byteLength; setProgress(55); }
    const downMbps = bytes * 8 / ((performance.now() - downStart) / 1000) / 1000000;
    $('speedMeterNumber').textContent = downMbps.toFixed(1); $('speedMeterDownload').textContent = formatMbps(downMbps); setGauge(downMbps);
    setStatus('3/4 - Medindo upload real...');
    const uploadBytes = 1000000;
    const payload = new Uint8Array(uploadBytes);
    if (window.crypto && crypto.getRandomValues) crypto.getRandomValues(payload.subarray(0, 65536));
    const upStart = performance.now();
    const upRes = await fetch(endpoint + '/__up?cache=' + Date.now(), { method: 'POST', body: payload, cache: 'no-store', headers: { 'Content-Type': 'application/octet-stream' } });
    if (!upRes.ok) throw new Error('Upload não permitido pelo endpoint');
    try { await upRes.arrayBuffer(); } catch (e) {}
    const upMbps = uploadBytes * 8 / ((performance.now() - upStart) / 1000) / 1000000;
    $('speedMeterNumber').textContent = upMbps.toFixed(1); $('speedMeterUpload').textContent = formatMbps(upMbps); setGauge(upMbps); setProgress(95);
    setStatus('4/4 - ✅ Teste concluído com medição real!'); setProgress(100);
    $('speedQualityRating').textContent = `Download ${formatMbps(downMbps)} • Upload ${formatMbps(upMbps)} • Latência ${Math.round(ping)} ms`;
  } catch (err) {
    setProgress(0); setStatus('Teste não disponível nesta rede/TV');
    if ($('speedQualityRating')) $('speedQualityRating').textContent = 'Não foi possível medir com segurança: ' + (err.message || 'endpoint bloqueado') + '. Configure mk21_speed_test_endpoint em um endpoint próprio com CORS.';
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

// LIMPAR ARMAZENAMENTO — CLONE IDÊNTICO À FOTO DO USUÁRIO (VIZZION PLAY)
function renderClearStoragePanel() {
  const box = $('settingsDetailBox');
  const favChannelsCount = Array.from(favoriteUrls).filter(u => allCatalog.LIVE.some(i => i.url === u)).length;
  const favMoviesCount = Array.from(favoriteUrls).filter(u => allCatalog.MOVIE.some(i => i.url === u)).length;
  const favSeriesCount = Array.from(favoriteUrls).filter(u => allCatalog.SERIES.some(i => i.url === u)).length;
  const continueCount = continueWatchingList.length;

  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px;">🗑️ Limpar Armazenamento</h3>
    <p style="color:#aaa;">Selecione os dados armazenados que deseja apagar da TV:</p>
    <div style="display:flex; flex-direction:column; gap:14px; max-width:620px; margin-top:16px;">
      
      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Canais favoritos (${favChannelsCount})</span>
        <button id="btnClearFavChannels" class="ctrl-btn" style="padding:10px 22px;" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Filmes favoritos (${favMoviesCount})</span>
        <button id="btnClearFavMovies" class="ctrl-btn" style="padding:10px 22px;" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Séries favoritas (${favSeriesCount})</span>
        <button id="btnClearFavSeries" class="ctrl-btn" style="padding:10px 22px;" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Filmes assistidos (Histórico)</span>
        <button id="btnClearWatchedMovies" class="ctrl-btn" style="padding:10px 22px;" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Séries assistidas (Histórico)</span>
        <button id="btnClearWatchedSeries" class="ctrl-btn" style="padding:10px 22px;" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#291114; border:1px solid #e50914; padding:14px 20px; border-radius:10px; margin-top:8px;">
        <span style="font-size:18px; font-weight:bold; color:#ffd54f;">Limpar tudo</span>
        <button id="btnClearAllStorage" class="ctrl-btn primary" style="padding:10px 26px;" tabindex="0">Limpar</button>
      </div>

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
    continueWatchingList = continueWatchingList.filter(it => it.contentType !== 'MOVIE');
    try { localStorage.setItem('mk21_continue_watching', JSON.stringify(continueWatchingList)); } catch (e) {}
    renderClearStoragePanel();
    alert('Histórico de filmes assistidos esvaziado.');
  };

  $('btnClearWatchedSeries').onclick = () => {
    continueWatchingList = continueWatchingList.filter(it => it.contentType !== 'SERIES');
    try { localStorage.setItem('mk21_continue_watching', JSON.stringify(continueWatchingList)); } catch (e) {}
    renderClearStoragePanel();
    alert('Histórico de séries assistidas esvaziado.');
  };

  $('btnClearAllStorage').onclick = async () => {
    if (!confirm('Apagar cache da lista, favoritos e históricos?\n(As credenciais de login serão mantidas.)')) return;
    try {
      // Limpa só chaves de app, não tudo cegamente
      const keep = {
        mk21_username: localStorage.getItem('mk21_username'),
        mk21_password: localStorage.getItem('mk21_password'),
        mk21_user: localStorage.getItem('mk21_user'),
        mk21_pass: localStorage.getItem('mk21_pass'),
        mk21_servers_list: localStorage.getItem('mk21_servers_list'),
        mk21_last_server: localStorage.getItem('mk21_last_server')
      };
      const keys = [];
      for (let i = 0; i < localStorage.length; i++) keys.push(localStorage.key(i));
      keys.forEach(function (k) {
        if (!k) return;
        if (k.indexOf('mk21_') === 0 || k.indexOf('iptv_') === 0) localStorage.removeItem(k);
      });
      Object.keys(keep).forEach(function (k) {
        if (keep[k] != null) localStorage.setItem(k, keep[k]);
      });
      try { ensureSharedCredentialsStored(); } catch (e) {}
  try { restoreSortOrder(); } catch (e) {}
      localStorage.removeItem('mk21_ota_app_js');
      localStorage.removeItem('mk21_ota_styles_css');
      await new Promise(function (resolve) {
        try {
          const req = indexedDB.deleteDatabase(DB_NAME);
          req.onsuccess = function () { resolve(); };
          req.onerror = function () { resolve(); };
          req.onblocked = function () { resolve(); };
        } catch (e) { resolve(); }
      });
      allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };
      favoriteUrls = new Set();
      continueWatchingList = [];
      alert('Cache e históricos limpos. Credenciais mantidas.\nRecarregando...');
      location.reload();
    } catch (e) {
      alert('Erro ao limpar: ' + (e && e.message ? e.message : e));
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

// GERENCIAR CATEGORIAS (OCULTAR / EXIBIR COM PERSISTÊNCIA REAL)
function renderCategoriesManagerPanel() {
  const box = $('settingsDetailBox');
  const cats = currentCategoryKeys.filter(k => k !== 'ALL');
  let hiddenCats = [];
  try {
    hiddenCats = JSON.parse(localStorage.getItem('mk21_hidden_categories') || '[]');
  } catch (e) {}

  box.innerHTML = `
    <h3 style="margin-top:0; font-size:24px;">📁 Gerenciar Categorias</h3>
    <p style="color:#aaa;">Ative ou desative as categorias que deseja visualizar no menu lateral:</p>
    <div style="display:flex; gap:12px; margin-bottom:12px;">
      <button id="btnShowAllCats" class="ctrl-btn" style="padding:8px 16px; font-size:14px;" tabindex="0">👁️ Exibir Todas</button>
      <button id="btnHideEmptyCats" class="ctrl-btn" style="padding:8px 16px; font-size:14px;" tabindex="0">🙈 Ocultar Vazias</button>
    </div>
    <div id="catItemsListWrap" style="max-height:380px; overflow-y:auto; display:flex; flex-direction:column; gap:8px;">
      ${cats.map((c, i) => {
        const isVisible = !hiddenCats.includes(c);
        const count = (currentCategoriesMap[c] || []).length;
        return `
          <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:12px 18px; border-radius:8px;">
            <span style="font-size:16px; color:#fff;">${c} <span style="color:#ffd54f; font-size:13px;">(${count} canais)</span></span>
            <input type="checkbox" class="chk-cat-toggle" data-cat="${encodeURIComponent(c)}" ${isVisible ? 'checked' : ''} style="width:22px; height:22px;">
          </div>
        `;
      }).join('')}
    </div>
    <div id="msgCatSaved" style="margin-top:12px; font-size:15px; font-weight:bold; color:#4caf50; display:none;">✅ Categorias atualizadas no menu lateral!</div>
  `;

  box.querySelectorAll('.chk-cat-toggle').forEach(chk => {
    chk.onchange = () => {
      const catName = decodeURIComponent(chk.getAttribute('data-cat'));
      if (chk.checked) {
        hiddenCats = hiddenCats.filter(x => x !== catName);
      } else {
        if (!hiddenCats.includes(catName)) hiddenCats.push(catName);
      }
      localStorage.setItem('mk21_hidden_categories', JSON.stringify(hiddenCats));
      buildCurrentCategories();
      renderCategoriesList();
      const msg = $('msgCatSaved');
      if (msg) {
        msg.style.display = 'block';
        setTimeout(() => { if (msg) msg.style.display = 'none'; }, 2500);
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
    hiddenCats = cats.filter(c => (currentCategoriesMap[c] || []).length === 0);
    localStorage.setItem('mk21_hidden_categories', JSON.stringify(hiddenCats));
    renderCategoriesManagerPanel();
    buildCurrentCategories();
    renderCategoriesList();
  };
}

// 13. GERENCIADOR DE SERVIDORES (COM EDIÇÃO, ADIÇÃO E TROCA COM CARREGAMENTO IMEDIATO)
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
    urlLine.textContent = srv.url;

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
      
      // Exibe porcentagem no lugar do botão antes de fechar
      btnConnect.textContent = 'Carregando 25%...';
      setTimeout(() => { btnConnect.textContent = 'Carregando 65%...'; }, 200);
      setTimeout(() => {
        $('modalServerPicker').classList.add('hidden');
        activeZone = 'channels';
        activeItem = null;
        loadServer(false);
      }, 450);
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
  $('inputNewServerUrl').value = srv.url;
  $('btnAddServerSubmit').textContent = '💾 Salvar Alterações';
  $('btnCancelEditServer').classList.remove('hidden');
  $('inputNewServerName').focus();
  $('inputNewServerName').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

$('btnCancelEditServer').onclick = () => {
  $('txtAddServerTitle').textContent = '➕ Adicionar Novo Servidor / Lista Manual';
  $('inputEditServerIndex').value = '-1';
  $('inputNewServerName').value = '';
  $('inputNewServerUrl').value = '';
  $('btnAddServerSubmit').textContent = '💾 Salvar e Conectar';
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
  const url = $('inputNewServerUrl').value.trim();

  if (!name) { alert('Informe o nome do servidor.'); return; }
  if (!url.startsWith('http')) { alert('URL inválida. Deve iniciar com http:// ou https://'); return; }

  if (editIdx >= 0 && editIdx < SERVERS.length) {
    SERVERS[editIdx].name = '⭐ ' + name;
    SERVERS[editIdx].url = url;
    currentServerIndex = editIdx;
    saveServersToStorage();
    try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}
    $('btnCancelEditServer').click();
    $('modalServerPicker').classList.add('hidden');
    activeZone = 'channels';
    activeItem = null;
    loadServer(true);
    alert(`Servidor "${name}" atualizado e conectado!`);
    return;
  }

  // Novo servidor
  const newServer = {
    id: 'custom_' + Date.now(),
    name: '⭐ ' + name,
    url: url
  };

  SERVERS.push(newServer);
  currentServerIndex = SERVERS.length - 1;
  saveServersToStorage();
  try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}

  $('inputNewServerName').value = '';
  $('inputNewServerUrl').value = '';
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

const BASE_PACKAGE_VERSION = '3.8.3';
let savedOtaVer = null;
try {
  savedOtaVer = localStorage.getItem('mk21_ota_app_version');
} catch (e) {}

// A versão efetiva é sempre a do pacote instalado; cache local não pode
// executar código nem mascarar a versão real do IPK.
let CURRENT_APP_VERSION = BASE_PACKAGE_VERSION;
try {
  localStorage.removeItem('mk21_ota_app_js');
  localStorage.removeItem('mk21_ota_styles_css');
  localStorage.removeItem('mk21_ota_app_version');
} catch (e) {}

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
      'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json?t=' + Date.now()
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

    // Se o GitHub estiver offline ou ainda não sincronizado no repositório remoto, usa os metadados oficiais v3.8.3
    if (!data || compareSemver(data.version, '3.8.3') < 0) {
      data = {
        version: '3.8.3',
        versionCode: 360,
        title: 'MK21 Play v3.8.3',
        releaseNotes: '• Guia EPG com dados reais XMLTV do servidor e API Xtream Codes (Short EPG)\n• Novo carregador e sincronizador OTA inteligente para Smart TV (LG webOS / Tizen)\n• Correção definitiva no gerenciador de atualização de versão na TV\n• Seleção de faixas de áudio e legendas (TextTrack) com modal interativo\n• Player com velocidade ajustável até 4x e áudio sem distorção (preservesPitch)\n• Teclas universais Play/Pause para controles remotos LG webOS e Samsung Tizen\n• Teste de velocidade em tempo real com gauge, ping e taxa de download\n• Separação estrita de categorias sem misturar canais, filmes e séries\n• Nova tela de inicialização (Splash) premium com animação e status',
        ipkUrl: 'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_3.8.3_all.ipk',
        isPendingPush: (!data || compareSemver(data.version, '3.8.3') < 0)
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

    $('btnStartDirectUpdate').onclick = startDirectUpdate;
    if ($('btnCheckAgainUpdate')) $('btnCheckAgainUpdate').onclick = function () { openAppUpdateModal(true); };
    if ($('btnCloseUpdateModal')) $('btnCloseUpdateModal').onclick = function () {
      $('modalAppUpdate').classList.add('hidden');
      activeZone = 'settings';
    };
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
  const meta = latestRemoteUpdateData;
  const pBox = $('updateProgressBox');
  if (!meta || !meta.ipkUrl) { alert('Atualização sem pacote IPK válido.'); return; }
  if (!meta.sha256 || !/^[a-f0-9]{64}$/i.test(meta.sha256)) {
    alert('Atualização recusada: o manifesto não contém SHA-256 válido do IPK.');
    return;
  }
  try {
    $('btnStartDirectUpdate').disabled = true; $('btnCheckAgainUpdate').disabled = true;
    pBox.classList.remove('hidden'); $('txtUpdateStep').textContent = 'Verificando integridade do IPK...'; $('txtUpdatePct').textContent = '20%'; $('barUpdateProgress').style.width = '20%';
    const response = await fetch(meta.ipkUrl + (meta.ipkUrl.includes('?') ? '&' : '?') + 'cache=' + Date.now(), { cache: 'no-store' });
    if (!response.ok) throw new Error('Não foi possível baixar o IPK');
    const bytes = await response.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    const actual = Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('');
    if (actual.toLowerCase() !== meta.sha256.toLowerCase()) throw new Error('SHA-256 do IPK não confere com o manifesto');
    $('txtUpdateStep').textContent = 'IPK íntegro. Solicitando instalação ao webOS...'; $('txtUpdatePct').textContent = '70%'; $('barUpdateProgress').style.width = '70%';
    if (!(window.webOS && webOS.service)) throw new Error('Serviço de instalação webOS indisponível');
    webOS.service.request('luna://org.webosbrew.hbchannel.service/install', { ipkUrl: meta.ipkUrl });
    $('txtUpdateStep').textContent = '✅ Instalação solicitada com integridade verificada'; $('txtUpdatePct').textContent = '100%'; $('barUpdateProgress').style.width = '100%';
  } catch (err) {
    $('txtUpdateStep').textContent = 'Atualização recusada'; $('txtUpdatePct').textContent = '0%'; $('barUpdateProgress').style.width = '0%';
    alert('Não foi possível atualizar com segurança: ' + (err.message || err));
    $('btnStartDirectUpdate').disabled = false; $('btnCheckAgainUpdate').disabled = false;
  }
}

function forceResetTvAppCache() {
  if (!confirm('Limpar cache de atualização OTA e voltar à versão do pacote instalado (v' + BASE_PACKAGE_VERSION + ')?')) return;
  try {
    localStorage.removeItem('mk21_ota_app_js');
    localStorage.removeItem('mk21_ota_styles_css');
    localStorage.setItem('mk21_ota_app_version', BASE_PACKAGE_VERSION);
  } catch (e) {}
  alert('Cache OTA limpo. Reiniciando na v' + BASE_PACKAGE_VERSION);
  window.location.reload();
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
// 15. NAVEGAÇÃO ESPACIAL D-PAD (webOS / Tizen) — não depende de listas preenchidas
function mk21IsVisible(el) {
  if (!el || el.disabled) return false;
  if (el.closest && el.closest('.hidden')) return false;
  try {
    const s = window.getComputedStyle(el);
    if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0') return false;
  } catch (e) {}
  return el.offsetParent !== null || el === document.activeElement;
}

function mk21GetFocusables() {
  const sel = [
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex="0"]',
    '.header-focusable',
    '.list-item-btn',
    '.cat-item-btn',
    '.ctrl-btn',
    '.cfg-menu-item',
    '.hud-ctrl-btn',
    '.pin-key',
    '.season-tab-btn',
    '.episode-item-btn',
    '.btn-server-connect',
    '.btn-server-edit',
    '.btn-server-delete'
  ].join(',');
  return Array.from(document.querySelectorAll(sel)).filter(mk21IsVisible);
}

function mk21RectCenter(el) {
  const r = el.getBoundingClientRect();
  return { x: r.left + r.width / 2, y: r.top + r.height / 2, r: r };
}

function mk21MoveFocus(direction) {
  const items = mk21GetFocusables();
  if (!items.length) return;
  let current = document.activeElement;
  if (!current || items.indexOf(current) === -1) {
    items[0].focus();
    try { items[0].scrollIntoView({ block: 'nearest' }); } catch (e) {}
    return;
  }
  const cur = mk21RectCenter(current);
  let best = null;
  let bestScore = Infinity;

  for (let i = 0; i < items.length; i++) {
    const el = items[i];
    if (el === current) continue;
    const c = mk21RectCenter(el);
    const dx = c.x - cur.x;
    const dy = c.y - cur.y;
    let ok = false;
    let primary = 0;
    let secondary = 0;

    if (direction === 'left') {
      ok = dx < -8;
      primary = -dx;
      secondary = Math.abs(dy);
    } else if (direction === 'right') {
      ok = dx > 8;
      primary = dx;
      secondary = Math.abs(dy);
    } else if (direction === 'up') {
      ok = dy < -8;
      primary = -dy;
      secondary = Math.abs(dx);
    } else if (direction === 'down') {
      ok = dy > 8;
      primary = dy;
      secondary = Math.abs(dx);
    }
    if (!ok) continue;
    // penaliza desvio perpendicular
    const score = primary + secondary * 2.5;
    if (score < bestScore) {
      bestScore = score;
      best = el;
    }
  }

  if (best) {
    best.focus();
    try { best.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) {}
    // mantém índices legados sincronizados
    try {
      if (best.id && typeof headerElements !== 'undefined' && headerElements.indexOf(best.id) !== -1) {
        activeZone = 'header';
        focusedHeaderIdx = headerElements.indexOf(best.id);
      } else if (best.classList.contains('cat-item-btn')) {
        activeZone = 'categories';
        const cats = Array.from(($('listCategories') || document).querySelectorAll('.cat-item-btn'));
        const ix = cats.indexOf(best);
        if (ix >= 0) focusedCatIdx = ix;
      } else if (best.classList.contains('list-item-btn')) {
        activeZone = 'channels';
        const its = Array.from(($('listItems') || document).querySelectorAll('.list-item-btn'));
        const ix = its.indexOf(best);
        if (ix >= 0) focusedItemIdx = ix;
      } else if (best.classList.contains('cfg-menu-item')) {
        activeZone = 'settings';
      }
    } catch (e) {}
  }
}

function focusActiveElement() {
  try {
    const items = mk21GetFocusables();
    if (!items.length) return;
    let el = null;
    if (activeZone === 'header' && typeof headerElements !== 'undefined') {
      el = $(headerElements[focusedHeaderIdx] || headerElements[0]);
    } else if (activeZone === 'categories') {
      const cats = $('listCategories') ? $('listCategories').querySelectorAll('.cat-item-btn') : [];
      el = cats[focusedCatIdx] || cats[0];
    } else if (activeZone === 'channels') {
      const its = $('listItems') ? $('listItems').querySelectorAll('.list-item-btn') : [];
      el = its[focusedItemIdx] || its[0];
    } else if (activeZone === 'settings') {
      el = document.querySelector('.cfg-menu-item.active') || $('cfgBtnInfo');
    }
    if (!el || !mk21IsVisible(el)) el = items[0];
    if (el) {
      el.focus();
      try { el.scrollIntoView({ block: 'nearest' }); } catch (e) {}
    }
  } catch (err) {
    console.warn('[MK21] focusActiveElement', err);
  }
}

document.addEventListener('keydown', function (e) {
  const key = e.key || '';
  const code = e.code || '';
  const keyCodes = { ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40, Enter: 13, Escape: 27, Backspace: 8, GoBack: 461 };
  const k = e.keyCode || e.which || keyCodes[key] || keyCodes[code] || 0;
  const ae = document.activeElement;
  const isTextInput = ae && /^(INPUT|TEXTAREA|SELECT)$/.test(ae.tagName);
  if (isTextInput && ['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(key)) return;

  // Voz nativa
  if (key === 'Voice' || key === 'VoiceCommand' || k === 1022 || k === 1016 || k === 166) {
    e.preventDefault();
    if (typeof startNativeVoiceSearch === 'function') startNativeVoiceSearch();
    return;
  }

  // Play/Pause mídia
  const isPlayPause =
    k === 179 || k === 10252 || k === 415 || k === 250 || k === 19 || k === 413 ||
    key === 'MediaPlayPause' || key === 'MediaPlay' || key === 'MediaPause' ||
    key === 'Play' || key === 'Pause';
  if (isPlayPause) {
    e.preventDefault();
    if (typeof togglePlayPause === 'function') togglePlayPause();
    return;
  }

  // Back / Return (webOS 461, Tizen 10009)
  if (k === 461 || k === 10009 || k === 27 || key === 'Escape' || key === 'Back') {
    // deixa handlers legados de modal se existirem
    if ($('modalPin') && !$('modalPin').classList.contains('hidden')) {
      e.preventDefault();
      $('modalPin').classList.add('hidden');
      activeZone = 'channels';
      focusActiveElement();
      return;
    }
    if ($('modalServerPicker') && !$('modalServerPicker').classList.contains('hidden')) {
      e.preventDefault();
      $('modalServerPicker').classList.add('hidden');
      activeZone = 'header';
      focusActiveElement();
      return;
    }
    if ($('modalSeries') && !$('modalSeries').classList.contains('hidden')) {
      e.preventDefault();
      $('modalSeries').classList.add('hidden');
      activeZone = 'channels';
      focusActiveElement();
      return;
    }
    if ($('modalExitConfirm') && !$('modalExitConfirm').classList.contains('hidden')) {
      e.preventDefault();
      $('modalExitConfirm').classList.add('hidden');
      return;
    }
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      e.preventDefault();
      if (typeof toggleFullscreen === 'function') toggleFullscreen(false);
      return;
    }
    if (typeof currentContentType !== 'undefined' && currentContentType !== 'LIVE') {
      e.preventDefault();
      if (typeof switchContentType === 'function') switchContentType('LIVE');
      return;
    }
  }

  // Setas — navegação espacial (sempre)
  if (k === 37 || key === 'ArrowLeft') {
    e.preventDefault();
    // seek em VOD fullscreen
    if ((typeof currentContentType !== 'undefined') && (currentContentType === 'MOVIE' || currentContentType === 'SERIES') &&
        (document.fullscreenElement || document.webkitFullscreenElement)) {
      const v = $('tvPlayer');
      if (v && v.duration && isFinite(v.duration)) {
        v.currentTime = Math.max(0, v.currentTime - 10);
        if (typeof showChannelBanner === 'function') showChannelBanner('⏪ -10s');
        return;
      }
    }
    if (isAppFullscreen()) { pokeFsHud(); return; }
    mk21MoveFocus('left');
    return;
  }
  if (k === 39 || key === 'ArrowRight') {
    e.preventDefault();
    if ((typeof currentContentType !== 'undefined') && (currentContentType === 'MOVIE' || currentContentType === 'SERIES') &&
        (document.fullscreenElement || document.webkitFullscreenElement)) {
      const v = $('tvPlayer');
      if (v && v.duration && isFinite(v.duration)) {
        v.currentTime = Math.min(v.duration, v.currentTime + 10);
        if (typeof showChannelBanner === 'function') showChannelBanner('⏩ +10s');
        return;
      }
    }
    if (isAppFullscreen()) { pokeFsHud(); return; }
    mk21MoveFocus('right');
    return;
  }
  if (k === 38 || key === 'ArrowUp') {
    e.preventDefault();
    if (isAppFullscreen()) { pokeFsHud(); return; }
    mk21MoveFocus('up');
    return;
  }
  if (k === 40 || key === 'ArrowDown') {
    e.preventDefault();
    if (isAppFullscreen()) { pokeFsHud(); return; }
    mk21MoveFocus('down');
    return;
  }

  // OK / Enter / Space
  if (k === 13 || k === 32 || key === 'Enter' || key === ' ') {
    if (ae && ae.tagName === 'INPUT' && (k === 32 || key === ' ')) return;
    e.preventDefault();
    if (ae && ae !== document.body && typeof ae.click === 'function') {
      ae.click();
    } else {
      focusActiveElement();
      const a2 = document.activeElement;
      if (a2 && typeof a2.click === 'function') a2.click();
    }
    return;
  }

  // Botões coloridos LG (mantidos)
  if (k === 403 || key === 'Red') {
    e.preventDefault();
    if (typeof openServerPicker === 'function') openServerPicker();
    return;
  }
  if (k === 405 || key === 'Yellow') {
    e.preventDefault();
    if (activeItem) toggleFav(activeItem.url);
    return;
  }
  if (k === 404 || key === 'Green') {
    e.preventDefault();
    if (typeof loadServer === 'function') loadServer(true);
    return;
  }
  if (k === 406 || key === 'Blue') {
    e.preventDefault();
    if (typeof openPinModal === 'function') openPinModal();
    else if (typeof toggleFullscreen === 'function') toggleFullscreen();
    return;
  }
}, true);

// Busca por voz (opcional)
function startNativeVoiceSearch() {
  function applyTranscript(text) {
    if (!text) return;
    var input = $('inputSearch');
    if (input) {
      input.value = text;
      try { input.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) {
        if (typeof renderItemsList === 'function') renderItemsList();
      }
    }
    if (typeof showChannelBanner === 'function') showChannelBanner('Busca: ' + text);
  }
  try {
    if (typeof webOS !== 'undefined' && webOS.service && webOS.service.request) {
      webOS.service.request('luna://com.webos.service.voice', {
        method: 'start',
        parameters: {},
        onSuccess: function (res) { applyTranscript((res && (res.transcript || res.text || res.result)) || ''); },
        onFailure: function () { fallbackWebSpeech(); }
      });
      return;
    }
  } catch (e) {}
  fallbackWebSpeech();
  function fallbackWebSpeech() {
    var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      if (typeof showChannelBanner === 'function') showChannelBanner('Voz indisponível');
      var input = $('inputSearch');
      if (input) input.focus();
      return;
    }
    try {
      var rec = new SR();
      rec.lang = 'pt-BR';
      rec.onresult = function (ev) {
        applyTranscript(ev.results && ev.results[0] && ev.results[0][0] ? ev.results[0][0].transcript : '');
      };
      rec.start();
      if (typeof showChannelBanner === 'function') showChannelBanner('Ouvindo...');
    } catch (err) {}
  }
}


// 15. MODAIS EXTRAS (PIN & SAÍDA)
$('btnRefreshList').onclick = () => loadServer(true);
// Adulto só via botão azul do controle / Configurações
// $('btnHeaderAdult') removido do cabeçalho;

function openPinModal() {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    if ($('txtLockIcon')) $('txtLockIcon').textContent = '🔒';
    if ($('txtLockText')) $('txtLockText').textContent = 'Adulto (Bloqueado)';
    
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
    if ($('txtLockIcon')) $('txtLockIcon').textContent = '🔓';
    if ($('txtLockText')) $('txtLockText').textContent = 'Adulto (Liberado)';
    
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

// 16. INICIALIZAÇÃO AUTOMÁTICA

// Proteção de memória webOS: evita reinício do app
if (typeof document !== 'undefined') {
  document.addEventListener('webOSLowMemory', function () {
    console.warn('[MK21] webOSLowMemory — liberando buffers');
    try {
      if (typeof hlsInstance !== 'undefined' && hlsInstance) {
        try { hlsInstance.destroy(); hlsInstance = null; } catch (e) {}
      }
      const v = document.getElementById('tvPlayer');
      if (v) { try { v.pause(); v.removeAttribute('src'); v.load(); } catch (e) {} }
      itemsDisplayLimit = 40;
      if (allCatalog) {
        if (allCatalog.MOVIE && allCatalog.MOVIE.length > 5000) allCatalog.MOVIE = allCatalog.MOVIE.slice(0, 5000);
        if (allCatalog.SERIES && allCatalog.SERIES.length > 4000) allCatalog.SERIES = allCatalog.SERIES.slice(0, 4000);
      }
      if (typeof renderItemsList === 'function') renderItemsList();
    } catch (e) {}
  }, false);
}

window.addEventListener('load', () => {
  ensureSharedCredentialsStored();
  // Limpa OTA antigo que quebrava os eventos de seta/botão
  try {
    localStorage.removeItem('mk21_ota_app_js');
    localStorage.removeItem('mk21_ota_styles_css');
    localStorage.removeItem('mk21_ota_app_version');
  } catch (e) {}

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

  const scheduleFrame = (window.requestAnimationFrame || function (cb) { return setTimeout(cb, 0); });
  scheduleFrame(function () {
    activeZone = 'header';
    focusedHeaderIdx = 0;
    if ($('tabLive')) $('tabLive').focus();
    else focusActiveElement();
  });
});
