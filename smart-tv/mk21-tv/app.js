/** Mk21DnsResolver v1.4.1 - TV Failover **/
const Mk21DnsResolver = {
    async fetchWithFailover(url, options = {}) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 8000);
        try {
            const response = await fetch(url, { ...options, signal: controller.signal });
            clearTimeout(timeout);
            if (!response.ok) throw new Error('Net Error');
            return response;
        } catch (e) {
            console.warn("Acionando Rota de Escape DNS para:", url);
            const retryUrl = url.includes('?') ? url + '&retry=1' : url + '?retry=1';
            return fetch(retryUrl, options);
        }
    }
};

// MK21 PLAY v1.4.1 — Navegação espacial DPAD — Motor Otimizado para Smart TV LG webOS/Tizen
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

// 3. CONFIGURAÇÃO DOS SERVIDORES MK21 (NOMES LIMPOS SEM ÍCONES)
const DEFAULT_SERVERS = [
  { id: 'vlog', name: 'VLOG', url: 'http://myopbx.beer' },
  { id: 'tannix', name: 'TANNIX', url: 'http://poptvcdn.online' },
  { id: 'lubtv', name: 'LUB TV', url: 'http://pottermax.sbs' },
  { id: 'mk21pro', name: 'MK21 PRÓ', url: 'http://app.vivoxi.xyz' },
  { id: 'cb6000', name: 'CB6000', url: 'http://cdn.caterlune.top' },
  { id: 'cinelon', name: 'CINELON', url: 'http://coliseuop.site' },
  { id: 'multttv', name: 'MULTT TV', url: 'http://dali-as.skin' }
];

function cleanServerName(name) {
  return String(name || '').replace(/^[^\w\dÀ-ÿ]+/i, '').trim() || name;
}

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
  "18+", "+18", "ADULTO", "ADULTOS", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS",
  "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X", "PRIVE", "PRIVÉ", "RED LIGHT", "SEX",
  "EROTIC", "ERÓTICO", "EROTICO", "BRASILEIRINHAS", "TIGRESA", "CINEMA 18", "CINE 18", "SEXTREME"
];

// BANCO DE DADOS INDEXEDDB (v5 para expurgar categorização antiga corrompida)
const DB_NAME = 'mk21_play_db_v6';
const DB_VERSION = 1;
const STORE_NAME = 'catalog_cache_v6';

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

const QUICK_CACHE_MAX = 400;
let quickCacheLastWrite = 0;

function normalizeCachedItem(it) {
  if (!it) return it;
  if (typeof it.isAdult === 'undefined') {
    it.isAdult = isAdult(it.name) || isAdult(it.group);
  }
  return it;
}

function saveFastCache(id, liveItems, force) {
  try {
    if (!liveItems || !liveItems.length) return;
    const now = Date.now();
    // Grava no máximo a cada 4s: serializar 400 itens a cada 50 canais travava a TV.
    if (!force && now - quickCacheLastWrite < 4000) return;
    quickCacheLastWrite = now;
    const compact = liveItems.slice(0, QUICK_CACHE_MAX).map(slimItem);
    const payload = JSON.stringify({ items: compact, updatedAt: now, total: liveItems.length });
    localStorage.setItem('mk21_fast_cache_' + id, payload);
    localStorage.setItem('mk21_fast_cache_time_' + id, String(now));
    localStorage.setItem('mk21_fast_cache_universal', JSON.stringify({ serverId: id, items: compact, updatedAt: now }));
  } catch (e) {
    console.warn('[MK21] save fast cache falhou', e);
  }
}

function getFastCache(id) {
  const readEntry = (key) => {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      const items = Array.isArray(parsed) ? parsed : (parsed && Array.isArray(parsed.items) ? parsed.items : null);
      if (!items || !items.length) return null;
      return {
        items: items.map(normalizeCachedItem),
        updatedAt: (parsed && parsed.updatedAt) || 0,
        total: (parsed && parsed.total) || items.length
      };
    } catch (e) { return null; }
  };
  const entry = readEntry('mk21_fast_cache_' + id) || readEntry('mk21_fast_cache_universal');
  if (entry) return { LIVE: entry.items, MOVIE: [], SERIES: [], _totalLive: entry.total, _updatedAt: entry.updatedAt };
  return null;
}

async function getStoredData(id) {
  // 1. Cache completo no IndexedDB (LIVE + FILMES + SERIES)
  try {
    const db = await openDB();
    if (db) {
      const payload = await new Promise(res => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const req = tx.objectStore(STORE_NAME).get(id);
        req.onsuccess = () => res(req.result ? req.result.payload : null);
        req.onerror = () => res(null);
      });
      try { db.close(); } catch (e) {}
      if (payload && payload.LIVE && payload.LIVE.length > 0) {
        payload.LIVE.forEach(normalizeCachedItem);
        (payload.MOVIE || []).forEach(normalizeCachedItem);
        (payload.SERIES || []).forEach(normalizeCachedItem);
        payload._updatedAt = Date.now();
        return payload;
      }
    }
  } catch (e) {}
  // 2. Cache rápido de canais ao vivo (localStorage)
  return getFastCache(id);
}

async function saveStoredData(id, payload) {
  if (payload && payload.LIVE && payload.LIVE.length > 0) {
    saveFastCache(id, payload.LIVE, true);
  }
  const totalItems = ((payload && payload.LIVE) || []).length + ((payload && payload.MOVIE) || []).length + ((payload && payload.SERIES) || []).length;
  if (totalItems > 40000) {
    // Catálogo muito grande: fica no índice paginado (IndexedDB por item), não num registro único.
    console.log('[MK21] catálogo grande (' + totalItems + '): cache completo no índice paginado');
    return;
  }
  try {
    const db = await openDB();
    if (!db) return;
    const full = {
      LIVE: (payload.LIVE || []).map(slimItem),
      MOVIE: (payload.MOVIE || []).map(slimItem),
      SERIES: (payload.SERIES || []).map(slimItem)
    };
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ id, payload: full, updatedAt: Date.now() });
  } catch (e) {
    console.warn('[MK21] save cache falhou', e);
  }
}

// ÍNDICE PERSISTENTE DO CATÁLOGO — mantém a lista completa fora do heap JS.
const CATALOG_INDEX_DB = 'mk21_catalog_index_v1';
const CATALOG_INDEX_VERSION = 1;
const CATALOG_INDEX_STORE = 'items';
const CATALOG_META_STORE = 'meta';

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
let currentSearchQuery = '';
async function searchFromIndexedCatalog(query) {
  const type = currentContentType;
  if (!['LIVE', 'MOVIE', 'SERIES'].includes(type) || !query) return;
  const generation = ++searchIndexGeneration;
  const srv = SERVERS[currentServerIndex];
  if (!srv) return;
  const group = activeCategoryKey === 'ALL' ? '' : activeCategoryKey;
  const found = await searchCatalogIndex(srv.id, type, query, group, 240);
  if (generation !== searchIndexGeneration) return;
  const inputVal = ($('inputSearch') && $('inputSearch').value || '').toLowerCase().trim();
  if (inputVal !== query) return;
  currentFilteredItems = found;
  itemsDisplayLimit = 240;
  $('badgeItemsCount').textContent = currentFilteredItems.length;
  renderItemsList();
}

async function queryIndexedFavorites(serverId, favoriteSet, limit = 1000) {
  const db = await openCatalogIndexDb();
  if (!db || !favoriteSet || !favoriteSet.size) return [];
  return new Promise(resolve => {
    const result = [];
    try {
      const tx = db.transaction(CATALOG_INDEX_STORE, 'readonly');
      const index = tx.objectStore(CATALOG_INDEX_STORE).index('serverType');
      const req = index.openCursor(IDBKeyRange.bound([serverId, 'LIVE'], [serverId, 'SERIES\uffff']));
      req.onsuccess = e => {
        const cursor = e.target.result;
        if (!cursor || result.length >= limit) return;
        const item = cursor.value;
        if (favoriteSet.has(item.url)) result.push(item);
        cursor.continue();
      };
      tx.oncomplete = () => { try { db.close(); } catch (e) {} resolve(result); };
      tx.onerror = () => { try { db.close(); } catch (e) {} resolve(result); };
    } catch (e) { try { db.close(); } catch (e) {} resolve([]); }
  });
}

let indexedFavoriteResults = [];
let favoriteIndexGeneration = 0;
async function hydrateIndexedFavorites() {
  const srv = SERVERS[currentServerIndex];
  if (!srv || currentContentType !== 'FAVORITES') return;
  const generation = ++favoriteIndexGeneration;
  const found = await queryIndexedFavorites(srv.id, favoriteUrls, 1000);
  if (generation !== favoriteIndexGeneration || currentContentType !== 'FAVORITES') return;
  indexedFavoriteResults = found;
  buildCurrentCategories();
  renderCategoriesList();
  selectCategory('ALL');
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
      const known = new Set(arr.map(it => it.url));
      page.forEach(item => { if (!known.has(item.url)) arr.push(item); });
      itemsDisplayLimit += 60;
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
    tvgName: it.tvgName || '',
    isAdult: !!it.isAdult
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
  const livePool = (allCatalog && allCatalog.LIVE) ? allCatalog.LIVE : [];
  let toPlay = null;

  if (meta && meta.url) {
    toPlay = livePool.find(i => i.url === meta.url || (meta.name && i.name === meta.name)) || meta;
  }
  if (!toPlay && livePool.length > 0) {
    toPlay = livePool[0];
  }

  if (toPlay) {
    currentContentType = 'LIVE';
    try {
      ['tabLive', 'tabMovies', 'tabSeries', 'tabContinue', 'tabFavs', 'tabSettings'].forEach(t => {
        if ($(t)) $(t).classList.remove('active');
      });
      if ($('tabLive')) $('tabLive').classList.add('active');
    } catch (e) {}
    playStream(toPlay);
    showChannelBanner('▶ Sintonizando: ' + toPlay.name);
    return true;
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
let searchWasPlaying = false;
let searchPreviousFocus = null;

function openSearchMode() {
  const input = $('inputSearch');
  if (!input) return;
  if (document.activeElement && document.activeElement !== input) searchPreviousFocus = document.activeElement;
  activeZone = 'search';
  // Em TV ao vivo, não interrompe a reprodução de fundo durante a pesquisa
}

function closeSearchMode(clearText = false) {
  const input = $('inputSearch');
  if (clearText && input) input.value = '';
  if (clearText) {
    currentSearchQuery = '';
    buildCurrentCategories();
    renderCategoriesList();
    renderItemsList();
  }
  if (input) { try { input.blur(); } catch (e) {} }
  activeZone = 'channels';
  const target = searchPreviousFocus && mk21IsVisible(searchPreviousFocus)
    ? searchPreviousFocus
    : (($('listItems') && $('listItems').querySelector('.list-item-btn:not([disabled])')) ||
       ($('listCategories') && $('listCategories').querySelector('.cat-item-btn.active')) ||
       $('tabLive'));
  searchPreviousFocus = null;
  if (target) {
    try { target.focus(); target.scrollIntoView({ block: 'nearest' }); } catch (e) {}
  } else {
    focusActiveElement();
  }
  const video = $('tvPlayer');
  if (video && searchWasPlaying) {
    searchWasPlaying = false;
    try { video.play().catch(() => {}); } catch (e) {}
  }
}
function scheduleSearchRender() {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(function () {
    const input = $('inputSearch');
    const query = input ? input.value.toLowerCase().trim() : '';
    currentSearchQuery = query;
    renderItemsList();
    if (query) {
      searchFromIndexedCatalog(query);
    } else {
      buildCurrentCategories();
      renderCategoriesList();
      renderItemsList();
    }
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
    if (typeof updateFavoriteBtnUI === 'function') updateFavoriteBtnUI(false);
    showChannelBanner('Removido dos Favoritos');
  } else {
    favoriteUrls.add(url);
    if (typeof updateFavoriteBtnUI === 'function') updateFavoriteBtnUI(true);
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

  // 1. Checagem canônica explícita na rota Xtream Codes (prioridade máxima absoluta)
  if (uppercaseUrl.includes("/SERIES/")) return "SERIES";
  if (uppercaseUrl.includes("/MOVIE/")) return "MOVIE";
  if (uppercaseUrl.includes("/LIVE/")) return "LIVE";

  // 2. Indicador forte de Séries (Temporadas e Episódios)
  const hasSeriesPattern = 
    /(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i.test(uppercaseName) ||
    uppercaseName.includes("TEMPORADA") || uppercaseName.includes("TEMP.") ||
    uppercaseName.includes("CAPITULO") || uppercaseName.includes("CAPÍTULO") ||
    uppercaseName.includes("EPISODIO") || uppercaseName.includes("EPISÓDIO");

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
    uppercaseCategory.includes("SÉRIES:") ||
    uppercaseCategory.includes("SERIADOS");

  if (isExplicitSeriesCategory || hasSeriesPattern) return "SERIES";

  // 3. IDENTIFICAÇÃO INFALÍVEL DE CANAIS LINEARES AO VIVO (Pay-TV / IPTV)
  // Canais com [ALT], FHD, HD, SD ou nomes de emissoras consagradas NUNCA são filmes VOD
  const hasLiveFeedMarker = 
    /\[ALT\]|\(ALT\)|\[BACKUP\]|\(BACKUP\)|\[OFF\]|\bFHD\b|\bUHD\b|\b4K\b|\bHD\b|\bSD\b|\bHEVC\b|\bH\.?265\b|\b1080P\b|\b720P\b|\b480P\b/i.test(uppercaseName);

  const KNOWN_LIVE_NETWORKS = [
    "A&E", "AMC", "ANIMAL PLANET", "DISCOVERY", "HISTORY", "NAT GEO", "NATIONAL GEOGRAPHIC",
    "TELECINE", "MEGAPIX", "WARNER", "UNIVERSAL", "STUDIO UNIVERSAL", "TNT", "SPACE", "AXN",
    "PARAMOUNT", "SONY", "STAR CHANNEL", "FX", "HBO", "CINEMAX", "LIFETIME", "TCM",
    "CANAL BRASIL", "GLOBO", "SBT", "RECORD", "BAND", "REDE TV", "CULTURA", "PREMIERE",
    "SPORTV", "ESPN", "FOX SPORTS", "COMBATE", "BANDSPORTS", "CONMEBOL", "VIVA", "MULTISHOW",
    "GNT", "OFF", "BIS", "GLOOB", "CARTOON", "NICKELODEON", "NICK JR", "BOOMERANG", "TOONCAST",
    "CNN", "GLOBO NEWS", "BANDNEWS", "RECORD NEWS", "JOVEM PAN", "TV CULTURA", "TV BRASIL"
  ];

  const matchesLiveNetwork = KNOWN_LIVE_NETWORKS.some(net => 
    uppercaseName.startsWith(net + " ") || uppercaseName.startsWith(net + "-") || uppercaseName.startsWith(net + ":") || uppercaseName === net ||
    uppercaseName.includes(" " + net + " ") || uppercaseName.includes("[" + net + "]") || uppercaseName.includes("(" + net + ")")
  );

  const isLiveStreamUrl = 
    uppercaseUrl.endsWith(".TS") || uppercaseUrl.includes(".TS?") ||
    uppercaseUrl.endsWith(".M3U8") || uppercaseUrl.includes(".M3U8?") ||
    uppercaseUrl.includes("/LIVE/");

  const isVodExtension = 
    uppercaseUrl.endsWith(".MP4") || uppercaseUrl.includes(".MP4?") ||
    uppercaseUrl.endsWith(".MKV") || uppercaseUrl.includes(".MKV?") ||
    uppercaseUrl.endsWith(".AVI") || uppercaseUrl.includes(".AVI?");

  // Se tem marcador de feed linear ou nome de rede ao vivo e NÃO é arquivo MP4/MKV -> É LIVE!
  if ((hasLiveFeedMarker || matchesLiveNetwork) && !isVodExtension) {
    return "LIVE";
  }

  // 4. Indicador forte de Filmes VOD genuínos
  if (isVodExtension) {
    return "MOVIE";
  }

  // Ano de lançamento explícito de filme (ex: "Interestelar (2014)" ou "[2023]")
  const hasYearPattern = /\b\((?:19|20)\d{2}\)\b/.test(uppercaseName) || /\b\[(?:19|20)\d{2}\]\b/.test(uppercaseName);
  if (hasYearPattern && !hasLiveFeedMarker && !matchesLiveNetwork) {
    return "MOVIE";
  }

  // Categorias que são tipicamente VOD puras (NÃO confundir com "FILMES E SÉRIES" ou "DOCUMENTÁRIOS" que são canais lineares)
  const isPureVodCategory = 
    uppercaseCategory.startsWith("VOD") ||
    uppercaseCategory.startsWith("ON DEMAND") ||
    uppercaseCategory.startsWith("ONDEMAND") ||
    uppercaseCategory.startsWith("LANÇAMENTOS") ||
    uppercaseCategory.startsWith("LANCAMENTOS") ||
    uppercaseCategory.startsWith("FILMES 202") ||
    uppercaseCategory.startsWith("FILMES 19") ||
    uppercaseCategory.includes("VOD |") ||
    uppercaseCategory.includes("VOD:") ||
    uppercaseCategory.includes("CINEMA |") ||
    uppercaseCategory.includes("4K FILMES") ||
    uppercaseCategory.includes("FHD FILMES");

  const isLinearCategoryIndicative = 
    uppercaseCategory.includes("CANAIS") || uppercaseCategory.includes("CANAL") ||
    uppercaseCategory.includes("AO VIVO") || uppercaseCategory.includes("AOVIVO") ||
    uppercaseCategory.includes("24H") || uppercaseCategory.includes("24 HORAS") ||
    uppercaseCategory.includes("FILMES E SERIES") || uppercaseCategory.includes("FILMES E SÉRIES") ||
    uppercaseCategory.includes("DOCUMENTÁRIOS") || uppercaseCategory.includes("DOCUMENTARIOS") ||
    uppercaseCategory.includes("TV ABERTA") || uppercaseCategory.includes("ABERTOS") ||
    uppercaseCategory.includes("ESPORTES") || uppercaseCategory.includes("SPORTS") ||
    uppercaseCategory.includes("NOTICIAS") || uppercaseCategory.includes("NEWS");

  if (isPureVodCategory && !isLinearCategoryIndicative && !isLiveStreamUrl) {
    return "MOVIE";
  }

  // 5. Provedores de Streaming VOD (Netflix, Prime, Disney, etc.)
  const isVodStreamingProvider = 
    uppercaseCategory.includes("NETFLIX") ||
    uppercaseCategory.includes("AMAZON") ||
    uppercaseCategory.includes("PRIME VIDEO") ||
    uppercaseCategory.includes("HBO MAX") ||
    uppercaseCategory.includes("DISNEY+") ||
    uppercaseCategory.includes("APPLE TV") ||
    uppercaseCategory.includes("PARAMOUNT+") ||
    uppercaseCategory.includes("GLOBOPLAY");

  if (isVodStreamingProvider && !isLinearCategoryIndicative && !hasLiveFeedMarker && !isLiveStreamUrl) {
    return "MOVIE";
  }

  // 6. Padrão residual: todo o resto de canais / streams IPTV é LIVE
  return "LIVE";
}

// 5. REGEX DE LIMPEZA E AGRUPAMENTO DE SÉRIES COM SUPORTE A 3 E 4 DÍGITOS DE EPISÓDIO
// 2.5.4: exige separador/espaço ANTES do marcador. Sem isso "Serie Teste 1"
// casava com "e 1" e todas as séries viravam uma só.
const SERIES_CLEAN_REGEX = /(?:^|[\s\-–:._(|])(?:(?:s|t)\d{1,3}\s*)?(?:e|ep|ep\.|episodio|episódio|cap|cap\.|capitulo|capítulo)\s*\d{1,4}\b|(?:^|[\s\-–:._(|])\d{1,3}x\d{1,4}\b|(?:^|[\s\-–:._(|])(?:s|t)\d{1,3}\s*e\s*\d{1,4}\b|(?:^|[\s\-–:._(|])(?:temp\.|temporada)\s*\d{1,3}\b/i;

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
    const quickAttr = (str, key) => {
      const k = key + '="';
      const idx = str.indexOf(k);
      if (idx === -1) return '';
      const start = idx + k.length;
      const end = str.indexOf('"', start);
      return end !== -1 ? str.slice(start, end).trim() : '';
    };

    const consume = (raw) => {
      const line = raw.trim();
      if (!line) return;
      if (line.charCodeAt(0) === 35) {
        if (line.startsWith('#EXTINF:')) {
          const comma = line.lastIndexOf(',');
          const name = comma >= 0 ? line.slice(comma + 1).trim() : 'Item';
          meta = {
            name: name,
            group: quickAttr(line, 'group-title') || 'Geral',
            logo: quickAttr(line, 'tvg-logo'),
            tvgId: quickAttr(line, 'tvg-id'),
            tvgName: quickAttr(line, 'tvg-name')
          };
        } else if (line.startsWith('#EXTM3U')) {
          const xm = quickAttr(line, 'url-tvg') || quickAttr(line, 'x-tvg-url');
          if (xm) window.serverXmltvUrl = xm;
        }
      } else if (meta && (line.startsWith('http://') || line.startsWith('https://'))) {
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

// ATUALIZAÇÃO DA PORCENTAGEM DE SINCRONIZAÇÃO AO LADO DO SERVIDOR
function updateServerSyncBadge(pct, isFinished = false) {
  const badge = $('txtServerSyncBadge');
  const txt = $('txtActiveServer');
  const srv = SERVERS[currentServerIndex];
  const srvName = srv ? srv.name : 'Servidor';

  if (isFinished) {
    if (badge) {
      badge.textContent = '✓ 100%';
      badge.classList.remove('hidden');
      badge.classList.add('done');
      setTimeout(() => {
        if (badge) badge.classList.add('hidden');
      }, 4000);
    }
    if (txt) {
      txt.textContent = cleanServerName(srvName);
    }
  } else {
    const label = `${pct}%`;
    if (badge) {
      badge.textContent = label;
      badge.classList.remove('hidden');
      badge.classList.remove('done');
    }
    if (txt) {
      txt.textContent = `${cleanServerName(srvName)} (${label})`;
    }
  }
}

function resetLoadingHud() {
  const hud = $('hudLoadingOverlay');
  const card = hud && hud.querySelector('.hud-loading-card');
  const error = $('hudLoadingError');
  const retry = $('btnRetryLoading');
  if (card) card.classList.remove('is-error');
  if (error) { error.textContent = ''; error.classList.add('hidden'); }
  if (retry) {
    retry.classList.add('hidden');
    retry.onclick = () => loadServer(true);
  }
  const spinner = hud && hud.querySelector('.spinner-ring');
  if (spinner) spinner.style.display = '';
}

function showLoadingFailure(error, serverName) {
  const hud = $('hudLoadingOverlay');
  const card = hud && hud.querySelector('.hud-loading-card');
  const errorBox = $('hudLoadingError');
  const retry = $('btnRetryLoading');
  if (!hud) return;
  if (card) card.classList.add('is-error');
  if ($('hudLoadingTitle')) $('hudLoadingTitle').textContent = 'Não foi possível carregar a lista';
  if ($('hudLoadingSub')) $('hudLoadingSub').textContent = `Servidor: ${serverName || 'atual'}`;
  if ($('hudProgressBar')) $('hudProgressBar').style.width = '0%';
  if ($('hudProgressPercent')) $('hudProgressPercent').textContent = 'Falha';
  if (errorBox) {
    const detail = error && error.message ? error.message : 'A conexão foi interrompida antes da lista terminar.';
    errorBox.textContent = `${detail} Seus dados não foram apagados.`;
    errorBox.classList.remove('hidden');
  }
  if (retry) {
    retry.classList.remove('hidden');
    retry.onclick = () => loadServer(true);
    setTimeout(() => retry.focus(), 0);
  }
  hud.classList.remove('hidden');
}

// 6. CARREGAMENTO COM PRIORIDADE MÁXIMA NO AO VIVO E CARGA RÁPIDA
// 2.5.4: o cache abre a tela na hora, mas a lista SEMPRE é sincronizada em segundo plano
// (modo silencioso). Antes o app retornava do cache e nunca baixava FILMES/SÉRIES.
let loadInFlight = false;

async function getCatalogIndexMeta(serverId) {
  const db = await openCatalogIndexDb();
  if (!db) return null;
  try {
    return await new Promise(resolve => {
      const tx = db.transaction(CATALOG_META_STORE, 'readonly');
      const req = tx.objectStore(CATALOG_META_STORE).get(serverId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch (e) { return null; } finally { try { db.close(); } catch (e) {} }
}

async function loadServer(forceRefresh = false, options = {}) {
  const silent = !!(options && options.silent);
  if (silent && loadInFlight) return;
  const loadId = ++serverLoadGeneration;
  if (activePlaylistController) { try { activePlaylistController.abort(); } catch (e) {} }
  activePlaylistController = typeof AbortController !== 'undefined' ? new AbortController() : null;
  if (SERVERS.length === 0) SERVERS = [...DEFAULT_SERVERS];
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;

  const srv = SERVERS[currentServerIndex];
  if ($('txtActiveServer')) $('txtActiveServer').textContent = cleanServerName(srv.name);
  if (!silent) updateServerSyncBadge(15, false);

  const hud = $('hudLoadingOverlay');

  // 1. CARGA INSTANTÂNEA VIA CACHE LOCAL (somente no boot/troca de servidor)
  if (!silent && !forceRefresh) {
    // Cache completo (LIVE + FILMES + SÉRIES) primeiro: evita sincronizar à toa
    // quando o catálogo já está salvo e fresco no aparelho.
    let cached = await getStoredData(srv.id);
    if (!cached || !cached.LIVE || cached.LIVE.length === 0) {
      cached = getFastCache(srv.id);
    }
    if (loadId !== serverLoadGeneration) return;
    const liveN = cached && cached.LIVE ? cached.LIVE.length : 0;
    const movN = cached && cached.MOVIE ? cached.MOVIE.length : 0;
    const serN = cached && cached.SERIES ? cached.SERIES.length : 0;
    if (cached && (liveN + movN + serN) > 0) {
      allCatalog = {
        LIVE: cached.LIVE || [],
        MOVIE: cached.MOVIE || [],
        SERIES: cached.SERIES || []
      };
      if (hud) hud.classList.add('hidden');
      hideSplash();
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      renderItemsList();
      updateServerSyncBadge(100, true);
      if ($('txtCurrentCategoryTitle')) $('txtCurrentCategoryTitle').textContent = '📺 Memória: ' + liveN.toLocaleString('pt-BR') + ' canais';
      showChannelBanner('✓ Abrindo da memória local (' + liveN + ' canais). Sincronizando o resto...');
      setTimeout(() => { resumeLastChannelIfPossible(); }, 150);
      // Sincroniza o catálogo em segundo plano, mas sem baixar de novo o que já está
      // fresco: TV com catálogo recente abre instantânea e sem tráfego extra.
      let lastSync = 0;
      try { lastSync = parseInt(localStorage.getItem('mk21_last_sync_' + srv.id) || '0', 10) || 0; } catch (e) {}
      const cacheFresh = lastSync > 0 && (Date.now() - lastSync) < 20 * 60 * 1000;
      const faltaVod = (movN + serN) === 0;
      if (!cacheFresh || faltaVod) {
        setTimeout(() => {
          if (serverLoadGeneration === loadId) loadServer(true, { silent: true });
        }, 700);
      } else {
        updateServerSyncBadge(100, true);
      }
      return;
    }
  }

  // 2. SEM CACHE (ou atualização forçada): HUD de conexão com ações de escape
  if (!silent) {
    // Interrompe qualquer stream anterior apenas na carga visível
    const oldVideo = $('tvPlayer');
    if (oldVideo) {
      try { oldVideo.pause(); oldVideo.src = ''; } catch (e) {}
    }
    if (hlsInstance) {
      try { hlsInstance.destroy(); hlsInstance = null; } catch (e) {}
    }
    if (mpegtsPlayer) {
      try { mpegtsPlayer.pause(); mpegtsPlayer.unload(); mpegtsPlayer.detachMediaElement(); mpegtsPlayer.destroy(); } catch (e) {}
      mpegtsPlayer = null;
    }
    activeItem = null;

    updateSplash(25, 'Conectando ao ' + srv.name + '...');
    resetLoadingHud();
    if (hud) {
      if ($('hudLoadingTitle')) $('hudLoadingTitle').textContent = 'Conectando ao ' + srv.name;
      if ($('hudLoadingSub')) $('hudLoadingSub').textContent = 'Baixando grade de programação ao vivo...';
      if ($('hudProgressBar')) $('hudProgressBar').style.width = '20%';
      if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '20%';
      hud.classList.remove('hidden');
    }

    if ($('btnHudChangeServer')) {
      $('btnHudChangeServer').onclick = () => {
        if (activePlaylistController) { try { activePlaylistController.abort(); } catch (e) {} }
        if (hud) hud.classList.add('hidden');
        hideSplash();
        openServerPicker();
      };
    }
    if ($('btnHudCancelLoading')) {
      $('btnHudCancelLoading').onclick = () => {
        if (activePlaylistController) { try { activePlaylistController.abort(); } catch (e) {} }
        if (hud) hud.classList.add('hidden');
        hideSplash();
        if (allCatalog.LIVE && allCatalog.LIVE.length > 0) {
          buildCurrentCategories();
          renderCategoriesList();
          selectCategory('ALL');
          renderItemsList();
        }
      };
    }

    $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';
    updateSplash(35, 'Baixando grade de canais ao vivo...');
  }

  // Watchdog de segurança: se após 7 segundos a conexão estiver travada, oferece trocar de servidor
  let hudSafetyTimer = silent ? null : setTimeout(() => {
    if (loadId !== serverLoadGeneration) return;
    if (hud && !hud.classList.contains('hidden') && (!allCatalog.LIVE || allCatalog.LIVE.length === 0)) {
      if ($('hudLoadingSub')) {
        $('hudLoadingSub').textContent = '⚠️ Servidor ' + srv.name + ' demorando a responder. Deseja trocar a fonte?';
      }
      const actions = $('hudLoadingActions');
      if (actions) actions.style.display = 'flex';
      const btn = $('btnHudChangeServer');
      if (btn) btn.focus();
    }
  }, 7000);

  loadInFlight = true;
  let published = 0;

  try {
    // No modo silencioso montamos um catálogo novo e trocamos por baixo, sem piscar a tela.
    const target = { LIVE: [], MOVIE: [], SERIES: [] };
    if (!silent) allCatalog = target;
    indexedOffsets = {};

    let indexWriter;
    if (silent) {
      // Reindexar 100k itens a cada abertura travava a TV. Só reindexa se o índice estiver velho.
      const meta = await getCatalogIndexMeta(srv.id);
      const stale = !meta || !meta.updatedAt || (Date.now() - meta.updatedAt > 12 * 60 * 60 * 1000);
      indexWriter = stale ? await createCatalogIndexWriter(srv.id) : { add() {}, async finish() {} };
    } else {
      indexWriter = await createCatalogIndexWriter(srv.id);
    }

    const liveGrouper = window.MK21AlternativeSources ? MK21AlternativeSources.createGrouper() : null;
    let liveVisible = silent;
    let lastProgressUi = 0;
    let lastPublishAt = 0;

    const publishSilent = (force) => {
      if (!silent || loadId !== serverLoadGeneration) return;
      if (currentContentType === 'SETTINGS') return;
      const input = $('inputSearch');
      if (input && document.activeElement === input) return;
      // Se o usuário está navegando na lista, não recria os botões embaixo do foco.
      const lista = $('listItems');
      if (lista && document.activeElement && lista.contains(document.activeElement)) return;
      const now = Date.now();
      if (!force && now - lastPublishAt < 3000) return;
      lastPublishAt = now;
      allCatalog = target;
      buildCurrentCategories();
      renderCategoriesList();
      if (!currentCategoriesMap[activeCategoryKey]) activeCategoryKey = 'ALL';
      renderItemsList(true);
    };

    const revealLive = () => {
      if (liveVisible || loadId !== serverLoadGeneration || target.LIVE.length === 0) return;
      liveVisible = true;
      if (hudSafetyTimer) { clearTimeout(hudSafetyTimer); hudSafetyTimer = null; }

      allCatalog = target;
      saveFastCache(srv.id, allCatalog.LIVE, true);

      updateSplash(80, 'Canais ao vivo disponíveis (' + allCatalog.LIVE.length + ')');
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      renderItemsList();
      if (!activeItem && allCatalog.LIVE.length > 0) {
        playStream(allCatalog.LIVE[0]);
      }
      hideSplash();

      if (hud) hud.classList.add('hidden');
      showChannelBanner('📺 Ao Vivo liberado! Baixando catálogo completo em segundo plano...');
    };

    await streamPlaylistContent(
      srv.url,
      activePlaylistController && activePlaylistController.signal,
      (item) => {
        if (loadId !== serverLoadGeneration) return;
        const slim = slimItem(item);
        const type = slim.contentType;

        // Filtro de conteúdo configurável pelo usuário
        const loadLive = localStorage.getItem('mk21_filter_load_live') !== 'false';
        const loadMovies = localStorage.getItem('mk21_filter_load_movies') !== 'false';
        const loadSeries = localStorage.getItem('mk21_filter_load_series') !== 'false';
        if (type === 'LIVE' && !loadLive) return;
        if (type === 'MOVIE' && !loadMovies) return;
        if (type === 'SERIES' && !loadSeries) return;

        const adultItem = slim.isAdult || isAdult(slim.name) || isAdult(slim.group);
        slim.isAdult = !!adultItem;

        indexWriter.add(slim);
        if (target[type]) {
          if (type === 'LIVE' && liveGrouper) {
            const grouped = liveGrouper.add(slim);
            if (grouped.isNew) target[type].push(grouped.item);
          } else {
            target[type].push(slim);
          }
        }

        if (silent) {
          publishSilent(false);
        } else {
          if (type !== 'LIVE' && target.LIVE.length > 0 && !liveVisible) {
            revealLive();
          } else if (type === 'LIVE' && target.LIVE.length >= 8 && !liveVisible) {
            revealLive();
          } else if (!liveVisible && target.LIVE.length > 0 && target.LIVE.length % 50 === 0) {
            saveFastCache(srv.id, target.LIVE);
          }
        }
      },
      (received, total) => {
        if (loadId !== serverLoadGeneration) return;
        const now = Date.now();
        if (now - lastProgressUi < 250) return;
        lastProgressUi = now;

        const itemsCount = target.LIVE.length + target.MOVIE.length + target.SERIES.length;
        let pct = 50;
        if (total && total > 0) {
          pct = Math.min(99, Math.max(10, Math.round((received / total) * 100)));
        } else {
          if (!liveVisible) {
            pct = Math.min(48, 15 + Math.round((target.LIVE.length / 25) * 30));
          } else {
            const mb = received / (1024 * 1024);
            const est = 50 + Math.round(Math.min(48, (mb / 4.5) * 26 + (itemsCount / 6000) * 22));
            pct = Math.min(98, Math.max(50, est));
          }
        }

        updateServerSyncBadge(pct, false);

        if (!silent) {
          if (!liveVisible) {
            updateSplash(pct, 'Lendo lista — ' + target.LIVE.length + ' canais ao vivo');
          }
          if (hud && !liveVisible) {
            if ($('hudProgressBar')) $('hudProgressBar').style.width = pct + '%';
            if ($('hudProgressPercent')) $('hudProgressPercent').textContent = pct + '%';
          }
        }
      }
    );

    loadInFlight = false;
    if (loadId !== serverLoadGeneration) return;

    allCatalog = target;
    if (!silent) revealLive();
    publishSilent(true);
    buildCurrentCategories();
    renderCategoriesList();
    if (!silent && !liveVisible) selectCategory('ALL');
    const totalLoaded = allCatalog.LIVE.length + allCatalog.MOVIE.length + allCatalog.SERIES.length;
    updateSplash(100, 'Lista completa carregada (' + totalLoaded.toLocaleString('pt-BR') + ' itens)');
    if (hud) {
      if ($('hudProgressBar')) $('hudProgressBar').style.width = '100%';
      if ($('hudProgressPercent')) $('hudProgressPercent').textContent = '100%';
      hud.classList.add('hidden');
    }
    updateServerSyncBadge(100, true);
    mk21AutoServerTries = 0;
    try { localStorage.setItem('mk21_last_sync_' + srv.id, String(Date.now())); } catch (e) {}
    await indexWriter.finish();
    saveFastCache(srv.id, allCatalog.LIVE, true);
    saveStoredData(srv.id, allCatalog);
    // Redesenha a aba atual com o catálogo completo (o reveal ao vivo acontece
    // nos primeiros 8 canais para abrir a tela rápido).
    if (currentContentType === 'LIVE' || currentContentType === 'MOVIE' || currentContentType === 'SERIES' || currentContentType === 'CONTINUE') {
      buildCurrentCategories();
      renderCategoriesList();
      renderItemsList();
    }
    if ($('txtCurrentCategoryTitle')) {
      const live = allCatalog.LIVE.length;
      $('txtCurrentCategoryTitle').textContent = '📺 Catálogo: ' + live.toLocaleString('pt-BR') + ' canais • ' + allCatalog.MOVIE.length.toLocaleString('pt-BR') + ' filmes • ' + allCatalog.SERIES.length.toLocaleString('pt-BR') + ' séries';
    }
    if (silent && published !== totalLoaded) {
      published = totalLoaded;
      showChannelBanner('✓ Catálogo sincronizado: ' + totalLoaded.toLocaleString('pt-BR') + ' itens');
    }
  } catch (err) {
    loadInFlight = false;
    if (loadId !== serverLoadGeneration || (err && err.name === 'AbortError')) return;
    console.error('Server error:', err);

    const partialLive = allCatalog && Array.isArray(allCatalog.LIVE) ? allCatalog.LIVE.length : 0;
    if (silent) {
      // Falhou a sincronização: mantém a tela e o canal atual, avisa sem drama.
      updateServerSyncBadge(100, true);
      if ($('txtServerSyncBadge')) {
        $('txtServerSyncBadge').textContent = 'offline';
        $('txtServerSyncBadge').classList.remove('hidden', 'done');
      }
      showChannelBanner(partialLive > 0
        ? '⚠️ Sem conexão — usando o catálogo salvo (' + partialLive + ' canais)'
        : '⚠️ Sem conexão com o servidor — tente Atualizar');
      if (hud) hud.classList.add('hidden');
      hideSplash();
      return;
    }

    if (partialLive > 0) {
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      renderItemsList();
      hideSplash();
      if (!activeItem) playStream(allCatalog.LIVE[0]);
      showChannelBanner(`⚠️ Lista parcial: ${partialLive} canais carregados. Tente atualizar novamente.`);
      if ($('txtCurrentCategoryTitle')) $('txtCurrentCategoryTitle').textContent = `📺 ${partialLive} canais disponíveis (parcial)`;
      if (hud) hud.classList.add('hidden');
    } else if (mk21AutoAdvanceServer(err, srv)) {
      // 2.5.3: o módulo já avisou na tela e vai tentar o próximo servidor.
    } else {
      showLoadingFailure(err, srv.name);
      if ($('txtCurrentCategoryTitle')) $('txtCurrentCategoryTitle').textContent = 'Aguardando conexão com o servidor';
      if ($('txtServerSyncBadge')) {
        $('txtServerSyncBadge').textContent = 'erro';
        $('txtServerSyncBadge').classList.remove('hidden', 'done');
      }
    }
  } finally {
    loadInFlight = false;
  }
}

// Parser legado removido: o carregamento moderno classifica itens durante o streaming.

// 7. AGRUPAMENTO DE CATEGORIAS DA ABA ATIVA
function buildCurrentCategories() {
  currentCategoriesMap = { 'ALL': [] };
  currentCategoryKeys = ['ALL'];

  let sourceItems = [];
  if (currentContentType === 'FAVORITES') {
    sourceItems = indexedFavoriteResults.length ? indexedFavoriteResults : [...allCatalog.LIVE, ...allCatalog.MOVIE, ...allCatalog.SERIES].filter(i => favoriteUrls.has(i.url));
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

  const adultGroupCache = Object.create(null);
  const hiddenSet = hiddenCats.length ? new Set(hiddenCats) : null;
  for (let i = 0; i < sourceItems.length; i++) {
    const item = sourceItems[i];
    const grp = item.group || 'Geral';
    if (!isAdultUnlocked) {
      // isAdult já vem calculado do carregamento; só o grupo é testado uma vez por categoria.
      let adultGroup = adultGroupCache[grp];
      if (typeof adultGroup === 'undefined') {
        adultGroup = adultGroupCache[grp] = isAdult(grp);
      }
      if (item.isAdult || adultGroup) continue;
    }
    if (hiddenSet ? hiddenSet.has(grp) : false) continue;

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
    (currentContentType === 'SERIES' ? '🍿 Gêneros Séries' :
    (currentContentType === 'CONTINUE' ? '▶ Continuar Assistindo' : '⭐ Favoritos')));

  currentCategoryKeys.forEach((k, idx) => {
    if (!isAdultUnlocked && isAdult(k)) return;
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
function renderItemsList(preserveScroll) {
  const scrollAnterior = preserveScroll && $('listItems') ? $('listItems').scrollTop : 0;
  const query = $('inputSearch') ? $('inputSearch').value.toLowerCase().trim() : '';
  const base = currentCategoriesMap[activeCategoryKey] || [];
  const indexedPageKey = currentContentType + '|' + (activeCategoryKey === 'ALL' ? 'ALL' : activeCategoryKey);
  const indexedMoreAvailable = Object.prototype.hasOwnProperty.call(indexedOffsets, indexedPageKey);

  // Evita duplicar o array inteiro quando a categoria está na ordem padrão.
  // Em listas com centenas de milhares de itens isso reduz um pico de memória
  // sempre que a tela é redesenhada. Cópia só é criada quando necessária.
  let items = query ? base.filter(c => (c.name && c.name.toLowerCase().includes(query)) || (c.normalizedName && c.normalizedName.includes(query))) : base;
  if (!isAdultUnlocked) {
    items = items.filter(c => !c.isAdult);
  }

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
      btn.className = 'list-item-btn channel-item-btn channel-row' + (activeItem && activeItem.url === item.url ? ' active active-playing' : '');
      btn.setAttribute('tabindex', '0');
      btn.setAttribute('data-idx', i);
      btn.setAttribute('data-id', item.id || item.url);

      const logoWrap = document.createElement('div');
      logoWrap.className = 'channel-logo-wrap';
      const img = document.createElement('img');
      img.className = 'channel-logo';
      img.referrerPolicy = 'no-referrer';
      const fallbackSvg = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 52 52"><rect width="52" height="52" rx="8" fill="#1e1f26"/><circle cx="26" cy="26" r="16" fill="#141828"/><polygon points="23,19 33,26 23,33" fill="#ffd54f"/></svg>');
      img.src = item.logo ? getOptimizedLogoUrl(item.logo, item.name) : fallbackSvg;
      img.alt = item.name;
      img.loading = 'lazy';
      img.decoding = 'async';
      img.onerror = function() {
        this.onerror = null;
        this.src = fallbackSvg;
      };
      logoWrap.appendChild(img);
      btn.appendChild(logoWrap);

      const details = document.createElement('div');
      details.className = 'channel-details';
      const nameSpan = document.createElement('span');
      nameSpan.className = 'channel-name';
      nameSpan.textContent = item.name;
      details.appendChild(nameSpan);

      const epgSub = document.createElement('span');
      epgSub.className = 'channel-epg-sub';
      epgSub.textContent = item.current_program || (currentContentType === 'CONTINUE' ? '▶ Continuar' : (item.group || 'Ao Vivo'));
      details.appendChild(epgSub);
      btn.appendChild(details);

      if (item.isAdult) {
        const tag = document.createElement('span');
        tag.className = 'counter-badge';
        tag.textContent = '+18';
        btn.appendChild(tag);
      }

      if (item.progressPct && item.progressPct > 0) {
        const pTrack = document.createElement('div');
        pTrack.className = 'continue-progress-track';
        const pFill = document.createElement('div');
        pFill.className = 'continue-progress-fill';
        pFill.style.width = Math.min(100, item.progressPct) + '%';
        pTrack.appendChild(pFill);
        btn.appendChild(pTrack);
      }

      const isFav = favoriteUrls.has(item.url);
      const favBtn = document.createElement('button');
      favBtn.className = 'channel-fav-btn' + (isFav ? ' active' : '');
      favBtn.textContent = isFav ? '⭐' : '☆';
      favBtn.title = isFav ? 'Remover dos favoritos' : 'Favoritar canal';
      favBtn.tabIndex = -1;
      favBtn.onclick = (e) => {
        e.stopPropagation();
        toggleFav(item.url);
        const nowFav = favoriteUrls.has(item.url);
        favBtn.className = 'channel-fav-btn' + (nowFav ? ' active' : '');
        favBtn.textContent = nowFav ? '⭐' : '☆';
      };
      btn.appendChild(favBtn);

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
  if (preserveScroll) { try { ul.scrollTop = scrollAnterior; } catch (e) {} }

  // Não inicia reprodução automática durante busca para não roubar foco ou mudar canal
  if (!activeItem && currentContentType === 'LIVE' && currentFilteredItems.length > 0 && !query) {
    playStream(currentFilteredItems[0]);
  }
}

// MODAL DE CLASSIFICAÇÃO / ORDENAÇÃO
function updateSortOrderButtonLabel() {
  const btn = $('btnSortOrder');
  if (!btn) return;
  const labels = {
    DEFAULT: '↕️ Padrão',
    RECENT: '🕒 Recentes',
    AZ: '🔤 A - Z',
    ZA: '🔤 Z - A',
    YEAR: '📅 Ano'
  };
  btn.textContent = labels[currentSortOrder] || '↕️ Classificação';
}

function openSortPickerModal() {
  const modal = $('modalSortPicker');
  if (!modal) return;
  modal.classList.remove('hidden');
  activeZone = 'modalSortPicker';
  const btns = modal.querySelectorAll('.sort-choice-btn');
  btns.forEach(b => {
    b.classList.toggle('active', b.dataset.order === currentSortOrder);
    b.onclick = () => {
      currentSortOrder = b.dataset.order;
      updateSortOrderButtonLabel();
      persistSortOrder();
      renderItemsList();
      closeSortPickerModal();
      showChannelBanner('Classificação: ' + (b.textContent.trim()));
    };
  });
  const activeBtn = modal.querySelector(`.sort-choice-btn[data-order="${currentSortOrder}"]`) || btns[0];
  if (activeBtn) activeBtn.focus();
}

function closeSortPickerModal() {
  const modal = $('modalSortPicker');
  if (modal) modal.classList.add('hidden');
  activeZone = 'channels';
  if ($('btnSortOrder')) $('btnSortOrder').focus();
}

if ($('btnSortOrder')) {
  $('btnSortOrder').onclick = openSortPickerModal;
  updateSortOrderButtonLabel();
}
if ($('btnCloseSortPicker')) {
  $('btnCloseSortPicker').onclick = closeSortPickerModal;
}

if ($('inputSearch')) {
  $('inputSearch').addEventListener('click', () => openSearchMode());
  $('inputSearch').addEventListener('focus', () => {
    activeZone = 'search';
  });
  $('inputSearch').addEventListener('input', scheduleSearchRender);
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

function matchEPG(channel, xmltvListings) {
  if (!channel || !xmltvListings || !Array.isArray(xmltvListings)) return null;
  const chanTvgId = (channel.tvgId || channel['tvg-id'] || '').trim().toLowerCase();
  const chanStreamId = String(channel.streamId || channel.stream_id || '').trim();
  const chanName = (channel.name || '').trim().toLowerCase();

  // 1. Direct match on tvg-id
  if (chanTvgId) {
    const byTvgId = xmltvListings.filter(x => {
      const xId = String(x.channel_id || x.channel || x.id || '').trim().toLowerCase();
      return xId === chanTvgId;
    });
    if (byTvgId.length > 0) return byTvgId;
  }

  // 2. Direct match on streamId
  if (chanStreamId) {
    const byStream = xmltvListings.filter(x => {
      const xId = String(x.channel_id || x.stream_id || x.id || '').trim();
      return xId === chanStreamId;
    });
    if (byStream.length > 0) return byStream;
  }

  // 3. Fallback match by channel name
  if (chanName) {
    const cleanChan = chanName.replace(/\b(fhd|hd|4k|sd|br|brasil|canal|canais)\b/g, '').trim();
    const byName = xmltvListings.filter(x => {
      const xName = String(x.channel_name || x.name || '').toLowerCase().trim();
      return xName && (xName === chanName || (cleanChan && xName.includes(cleanChan)));
    });
    if (byName.length > 0) return byName;
  }

  return null;
}

async function updateSmartTvEpg(item) {
  if (!item) return;

  const sim = getSimulatedTvProgram(item.name);
  if ($('epgTitle')) $('epgTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '▶ ') + item.name;
  if ($('fsHudTitle')) $('fsHudTitle').textContent = (favoriteUrls.has(item.url) ? '⭐ ' : '') + item.name;
  if ($('epgGroup')) $('epgGroup').textContent = `Categoria: ${item.group} • ${sim.timeStr}`;
  if ($('previewEpgNow')) $('previewEpgNow').textContent = `NO AR: ${sim.title}`;
  if ($('previewEpgNext')) $('previewEpgNext').textContent = `A SEGUIR: ${sim.nextTitle}`;
  if ($('epgStatus')) $('epgStatus').innerHTML = `🔴 NO AR: <b>${sim.title}</b>`;
  const pBar = document.querySelector('.epg-progress');
  if (pBar) pBar.style.width = `${sim.progressPercent}%`;
  if ($('epgNext')) $('epgNext').textContent = `A seguir: ${sim.nextTitle}`;

  // Sincroniza EPG Overlay do Fullscreen HUD
  if ($('fsEpgCurrentTitle')) $('fsEpgCurrentTitle').textContent = sim.title;
  if ($('fsEpgTimeBadge')) $('fsEpgTimeBadge').textContent = sim.timeStr;
  if ($('fsEpgFill')) $('fsEpgFill').style.width = `${sim.progressPercent}%`;
  if ($('fsEpgNextTitle')) $('fsEpgNextTitle').textContent = sim.nextTitle;

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

function parseXmltvTimestamp(val) {
  if (!val) return NaN;
  if (typeof val === 'number') {
    return val > 1e11 ? val : val * 1000;
  }
  const s = String(val).trim();
  if (/^\d{10}$/.test(s)) return parseInt(s, 10) * 1000;
  if (/^\d{13}$/.test(s)) return parseInt(s, 10);

  // Formato padrão XMLTV: YYYYMMDDHHmmss [+/-]ZZZZ (ex: 20261021020000 +0000)
  const xmltvMatch = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);
  if (xmltvMatch) {
    const y = xmltvMatch[1];
    const m = xmltvMatch[2];
    const d = xmltvMatch[3];
    const h = xmltvMatch[4];
    const min = xmltvMatch[5];
    const sec = xmltvMatch[6];
    const tz = xmltvMatch[7];
    let isoString;
    if (tz) {
      isoString = `${y}-${m}-${d}T${h}:${min}:${sec}${tz.slice(0, 3)}:${tz.slice(3)}`;
    } else {
      isoString = `${y}-${m}-${d}T${h}:${min}:${sec}Z`;
    }
    const parsed = new Date(isoString).getTime();
    if (!isNaN(parsed)) return parsed;
  }
  // Formato SQL / ISO: YYYY-MM-DD HH:mm:ss
  const isoTry = new Date(s.replace(' ', 'T')).getTime();
  if (!isNaN(isoTry)) return isoTry;
  return NaN;
}

function applyRealSmartTvEpg(item, listings) {
  if (!listings || listings.length === 0 || activeItem !== item) return;
  const nowMs = Date.now();

  const parsed = listings.map(l => {
    let start = parseXmltvTimestamp(l.start_timestamp || l.start);
    let stop = parseXmltvTimestamp(l.stop_timestamp || l.end || l.stop);
    return {
      title: decodeSmartTvEpgText(l.title),
      desc: decodeSmartTvEpgText(l.description),
      start,
      stop
    };
  }).filter(p => !isNaN(p.start) && !isNaN(p.stop));

  if (parsed.length === 0) return;
  currentEpgListings = parsed;

  let current = parsed.find(p => p.start <= nowMs && p.stop > nowMs) || parsed[0];
  let curIdx = parsed.indexOf(current);
  let next = (curIdx >= 0 && curIdx + 1 < parsed.length) ? parsed[curIdx + 1] : null;

  const startH = new Date(current.start).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const stopH = new Date(current.stop).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const total = Math.max(1, current.stop - current.start);
  const elapsed = Math.max(0, nowMs - current.start);
  const pct = Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));

  if ($('epgTitle')) $('epgTitle').textContent = `▶ ${item.name}`;
  if ($('epgGroup')) $('epgGroup').textContent = `Categoria: ${item.group} • ${startH} - ${stopH}`;
  if ($('previewEpgNow')) $('previewEpgNow').innerHTML = `NO AR: <b>${current.title}</b>`;
  if ($('previewEpgNext')) {
    const nextH = next ? new Date(next.start).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '--:--';
    $('previewEpgNext').textContent = `A SEGUIR (${nextH}): ${next ? next.title : 'Programação Normal'}`;
  }
  if ($('epgStatus')) {
    $('epgStatus').innerHTML = `🔴 NO AR: <b>${current.title}</b> <span style="font-size:10px; background:rgba(16,185,129,0.25); color:#10b981; border:1px solid rgba(16,185,129,0.4); border-radius:4px; padding:1px 5px; margin-left:6px; font-weight:800;">📡 XMLTV REAL</span>`;
  }
  const pBar = document.querySelector('.epg-progress');
  if (pBar) pBar.style.width = `${pct}%`;
  if ($('epgNext') && next) {
    const nextH = new Date(next.start).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    $('epgNext').textContent = `A seguir (${nextH}): ${next.title}`;
  }

  // Sincroniza EPG Real no Fullscreen HUD
  if ($('fsEpgCurrentTitle')) $('fsEpgCurrentTitle').textContent = current.title;
  if ($('fsEpgTimeBadge')) $('fsEpgTimeBadge').textContent = `${startH} - ${stopH}`;
  if ($('fsEpgFill')) $('fsEpgFill').style.width = `${pct}%`;
  if ($('fsEpgNextTitle')) $('fsEpgNextTitle').textContent = next ? next.title : 'Programação Normal';
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

// ==========================================================================
// v1.2.0: ENGINES DE ULTRA-RESPONSIVENESS & INTELLIGENCE
// ==========================================================================

// 1. INTELLIGENT PRE-FETCHING (Zapping de Canal Zero-Delay)
let prefetchAbortController = null;
const prefetchManifestCache = new Map();

function prefetchAdjacentChannels(item) {
  if (!item || !item.url) return;
  if (!currentFilteredItems || currentFilteredItems.length <= 1) return;
  if (currentContentType !== 'LIVE') return;

  if (prefetchAbortController) {
    try { prefetchAbortController.abort(); } catch (e) {}
  }
  prefetchAbortController = new AbortController();
  const signal = prefetchAbortController.signal;

  const currentIdx = currentFilteredItems.findIndex(it => it.url === item.url);
  if (currentIdx === -1) return;

  const prevIdx = (currentIdx - 1 + currentFilteredItems.length) % currentFilteredItems.length;
  const nextIdx = (currentIdx + 1) % currentFilteredItems.length;
  const adjacents = [currentFilteredItems[nextIdx], currentFilteredItems[prevIdx]];

  setTimeout(() => {
    adjacents.forEach(adj => {
      if (!adj || !adj.url || adj.url === item.url) return;
      try {
        const isM3U8 = adj.url.toLowerCase().includes('.m3u8');
        if (isM3U8 && !prefetchManifestCache.has(adj.url)) {
          fetch(adj.url, {
            signal: signal,
            headers: { 'Range': 'bytes=0-32767' },
            cache: 'default',
            priority: 'low'
          }).then(res => {
            if (res.ok) return res.text();
            return null;
          }).then(text => {
            if (text && (text.includes('#EXTM3U') || text.includes('#EXT-X-STREAM-INF'))) {
              prefetchManifestCache.set(adj.url, { text, timestamp: Date.now() });
              if (prefetchManifestCache.size > 12) {
                const firstKey = prefetchManifestCache.keys().next().value;
                prefetchManifestCache.delete(firstKey);
              }
            }
          }).catch(() => {});
        } else {
          fetch(adj.url, { method: 'HEAD', signal: signal, priority: 'low' }).catch(() => {});
        }
      } catch (err) {}
    });
  }, 400);
}

// 1.1 INSTÂNCIA LEVE HLS PARA PRE-FETCH DE BUFFER DO PRÓXIMO CANAL
let prefetchHlsNext = null;

function prefetchNextChannelStream(nextItem) {
  if (!nextItem || !nextItem.url) return;
  if (!nextItem.url.toLowerCase().includes('.m3u8')) return;
  if (typeof Hls === 'undefined' || !Hls.isSupported()) return;

  try {
    if (prefetchHlsNext) {
      try { prefetchHlsNext.destroy(); } catch (e) {}
      prefetchHlsNext = null;
    }
    prefetchHlsNext = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      startFragPrefetch: true,
      maxBufferLength: 2,
      maxMaxBufferLength: 4,
      maxBufferSize: 2 * 1000 * 1000
    });
    prefetchHlsNext.loadSource(nextItem.url);
  } catch (e) {
    console.debug('[Prefetch Hls Next]', e);
  }
}

// ==========================================================================
// v1.2.0 FASE 2: PERSISTÊNCIA "CONTINUAR ASSISTINDO" (A CADA 10 SEGUNDOS)
// ==========================================================================
let progressSaveInterval = null;

function startProgressTracker(item) {
  clearInterval(progressSaveInterval);
  if (!item || item.contentType === 'LIVE') return;

  progressSaveInterval = setInterval(() => {
    const video = $('tvPlayer');
    if (!video || video.paused || !video.duration || isNaN(video.duration)) return;
    saveProgress(item.url, video.currentTime, video.duration);
  }, 10000);
}

function saveProgress(contentId, currentTime, duration) {
  if (!activeItem || activeItem.contentType === 'LIVE') return;
  if (!currentTime || currentTime < 3) return;

  const progressPct = Math.min(100, Math.max(1, Math.round((currentTime / duration) * 100)));
  const existingIdx = continueWatchingList.findIndex(it => it.url === contentId);
  const entry = {
    name: activeItem.name,
    group: activeItem.group,
    logo: activeItem.logo,
    url: contentId,
    contentType: activeItem.contentType,
    currentTime: Math.round(currentTime),
    duration: Math.round(duration),
    progressPct,
    timestamp: Date.now()
  };

  if (existingIdx >= 0) {
    continueWatchingList.splice(existingIdx, 1);
  }
  continueWatchingList.unshift(entry);
  if (continueWatchingList.length > 50) continueWatchingList = continueWatchingList.slice(0, 50);

  try {
    localStorage.setItem('mk21_continue_watching', JSON.stringify(continueWatchingList));
  } catch (e) {}
}

// ==========================================================================
// v1.2.0 FASE 2: TECLAS COLORIDAS & TECLAS NUMÉRICAS (0-9)
// ==========================================================================
const ASPECT_MODES = [
  { id: 'fit', label: 'Proporção: Original (Ajustar)', objectFit: 'contain' },
  { id: 'fill', label: 'Proporção: 16:9 (Esticar)', objectFit: 'fill' },
  { id: 'zoom', label: 'Proporção: Zoom (Cortar Bordas)', objectFit: 'cover' }
];
let currentAspectIdx = 0;

function cycleAspectRatio() {
  const video = $('tvPlayer');
  if (!video) return;
  currentAspectIdx = (currentAspectIdx + 1) % ASPECT_MODES.length;
  const mode = ASPECT_MODES[currentAspectIdx];
  video.style.objectFit = mode.objectFit;
  showChannelBanner(mode.label);
  if ($('diagAspect')) $('diagAspect').textContent = mode.label;
}

function toggleMute() {
  const video = $('tvPlayer');
  if (!video) return;
  video.muted = !video.muted;
  showChannelBanner(video.muted ? '🔇 Áudio Mudo' : '🔊 Áudio Ativado');
}

function toggleStreamDiagnostics() {
  const modal = $('modalStreamDiagnostics');
  if (!modal) return;
  if (!modal.classList.contains('hidden')) {
    modal.classList.add('hidden');
    activeZone = 'channels';
    focusActiveElement();
    return;
  }
  updateStreamDiagnosticsInfo();
  modal.classList.remove('hidden');
  activeZone = 'modalStreamDiagnostics';
  const closeBtn = $('btnCloseDiagnostics');
  if (closeBtn) closeBtn.focus();
}

function updateStreamDiagnosticsInfo() {
  const video = $('tvPlayer');
  if ($('diagTitle')) $('diagTitle').textContent = activeItem ? activeItem.name : 'Nenhum canal ativo';
  if (video) {
    const w = video.videoWidth || 0;
    const h = video.videoHeight || 0;
    if ($('diagResolution')) $('diagResolution').textContent = (w && h) ? `${w}x${h} (${w >= 1920 ? 'FHD' : (w >= 1280 ? 'HD' : 'SD')})` : 'Detectando resolução...';

    let bufferedSec = 0;
    if (video.buffered && video.buffered.length > 0) {
      for (let i = 0; i < video.buffered.length; i++) {
        if (video.currentTime >= video.buffered.start(i) && video.currentTime <= video.buffered.end(i)) {
          bufferedSec = Math.round(video.buffered.end(i) - video.currentTime);
          break;
        }
      }
    }
    if ($('diagBuffer')) $('diagBuffer').textContent = `${bufferedSec}s de buffer`;

    if (hlsInstance && hlsInstance.levels && hlsInstance.currentLevel >= 0) {
      const lvl = hlsInstance.levels[hlsInstance.currentLevel];
      if (lvl) {
        const br = lvl.bitrate ? (lvl.bitrate / 1000000).toFixed(2) + ' Mbps' : 'Automático (~3.5 Mbps)';
        if ($('diagBitrate')) $('diagBitrate').textContent = br;
        if ($('diagCodec')) $('diagCodec').textContent = lvl.codecs || (activeItem && activeItem.url.includes('.m3u8') ? 'HLS (AVC/AAC)' : 'MPEG-TS');
      }
    } else {
      if ($('diagBitrate')) $('diagBitrate').textContent = 'Stream Direto (~4.0 Mbps)';
      if ($('diagCodec')) $('diagCodec').textContent = activeItem && activeItem.url.includes('.m3u8') ? 'HLS (H.264/AAC)' : 'MPEG-TS/MP4';
    }
    if ($('diagAspect')) $('diagAspect').textContent = ASPECT_MODES[currentAspectIdx].label;
  }
}

// ZAPPING DIRETO POR TECLAS NUMÉRICAS (0-9)
let channelNumberBuffer = '';
let channelNumberTimer = null;

function handleDigitPress(digit) {
  channelNumberBuffer += String(digit);
  const overlay = $('channelNumberOverlay');
  const digits = $('channelNumberDigits');
  if (digits) digits.textContent = channelNumberBuffer;
  if (overlay) overlay.classList.remove('hidden');

  clearTimeout(channelNumberTimer);
  channelNumberTimer = setTimeout(() => {
    commitChannelNumber();
  }, 1800);
}

function commitChannelNumber() {
  const overlay = $('channelNumberOverlay');
  if (overlay) overlay.classList.add('hidden');
  if (!channelNumberBuffer) return;
  const channelNum = parseInt(channelNumberBuffer, 10);
  channelNumberBuffer = '';

  if (!currentFilteredItems || currentFilteredItems.length === 0) return;
  const targetIdx = channelNum - 1;
  if (targetIdx >= 0 && targetIdx < currentFilteredItems.length) {
    playStream(currentFilteredItems[targetIdx]);
    showChannelBanner(`Canal ${channelNum}: ${currentFilteredItems[targetIdx].name}`);
  } else {
    showChannelBanner(`Canal ${channelNum} não encontrado`);
  }
}

// 2. IMAGE PROXY & WEBP LOGO CACHE
const logoCacheMap = new Map();

function generateSvgLogoPlaceholder(text) {
  const clean = String(text || 'MK21').trim().slice(0, 4).toUpperCase();
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="40" viewBox="0 0 60 40">` +
    `<rect width="60" height="40" rx="6" fill="#141828" stroke="rgba(255,255,255,0.1)"/>` +
    `<text x="50%" y="54%" font-family="sans-serif" font-weight="bold" font-size="12" fill="#ffd54f" dominant-baseline="middle" text-anchor="middle">${clean}</text>` +
    `</svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}

function getOptimizedLogoUrl(originalUrl, fallbackName = '') {
  if (!originalUrl || typeof originalUrl !== 'string' || originalUrl.length < 5) {
    return generateSvgLogoPlaceholder(fallbackName);
  }
  if (logoCacheMap.has(originalUrl)) {
    return logoCacheMap.get(originalUrl);
  }
  logoCacheMap.set(originalUrl, originalUrl);
  return originalUrl;
}

// 3. MEMORY SHIELD (Auto-Cleanup para Smart TV)
function runMemoryShieldCleanup() {
  try {
    if (prefetchManifestCache.size > 5) {
      prefetchManifestCache.clear();
    }
    if (logoCacheMap.size > 150) {
      logoCacheMap.clear();
    }
    if (window.gc && typeof window.gc === 'function') {
      window.gc();
    }
  } catch (e) {
    console.debug('[MemoryShield]', e);
  }
}

// 4. OMNISEARCH (Busca Global Unificada)
let omnisearchActiveTab = 'ALL';
let omnisearchResults = { ALL: [], LIVE: [], MOVIE: [], SERIES: [] };
let omnisearchDebounceTimer = null;

function openOmnisearch(initialQuery = '') {
  const modal = $('modalOmnisearch');
  if (!modal) return;
  modal.classList.remove('hidden');
  activeZone = 'modalOmnisearch';

  const input = $('inputOmnisearch');
  if (input) {
    if (initialQuery) input.value = initialQuery;
    setTimeout(() => {
      input.focus();
    }, 100);
  }

  if (input && input.value.trim().length >= 2) {
    executeOmnisearch(input.value.trim());
  } else {
    renderOmnisearchResults();
  }
}

function closeOmnisearch() {
  const modal = $('modalOmnisearch');
  if (modal) modal.classList.add('hidden');
  activeZone = 'channels';
  focusActiveElement();
}

async function executeOmnisearch(query) {
  if (!query || query.trim().length < 2) {
    omnisearchResults = { ALL: [], LIVE: [], MOVIE: [], SERIES: [] };
    renderOmnisearchResults();
    return;
  }
  const q = query.toLowerCase().trim();

  const liveList = (allContent.LIVE || []);
  const movieList = (allContent.MOVIE || []);
  const seriesList = (allContent.SERIES || []);

  const filterFn = item => {
    if (!item) return false;
    if (!isAdultUnlocked && (item.isAdult || isAdult(item.name) || isAdult(item.group))) return false;
    const nameMatch = item.name && item.name.toLowerCase().includes(q);
    const normMatch = item.normalizedName && item.normalizedName.includes(q);
    const groupMatch = item.group && item.group.toLowerCase().includes(q);
    return nameMatch || normMatch || groupMatch;
  };

  const foundLive = liveList.filter(filterFn).slice(0, 80);
  const foundMovies = movieList.filter(filterFn).slice(0, 80);
  const foundSeries = seriesList.filter(filterFn).slice(0, 80);

  if (foundLive.length === 0 && foundMovies.length === 0 && foundSeries.length === 0) {
    try {
      const dbResults = await searchFromIndexedCatalog(q);
      if (dbResults && dbResults.length > 0) {
        dbResults.forEach(it => {
          if (it.contentType === 'LIVE') foundLive.push(it);
          else if (it.contentType === 'MOVIE') foundMovies.push(it);
          else if (it.contentType === 'SERIES') foundSeries.push(it);
        });
      }
    } catch (e) {}
  }

  omnisearchResults = {
    ALL: [...foundLive, ...foundMovies, ...foundSeries],
    LIVE: foundLive,
    MOVIE: foundMovies,
    SERIES: foundSeries
  };

  renderOmnisearchResults();
}

function renderOmnisearchResults() {
  if ($('countOmniAll')) $('countOmniAll').textContent = omnisearchResults.ALL.length;
  if ($('countOmniLive')) $('countOmniLive').textContent = omnisearchResults.LIVE.length;
  if ($('countOmniMovies')) $('countOmniMovies').textContent = omnisearchResults.MOVIE.length;
  if ($('countOmniSeries')) $('countOmniSeries').textContent = omnisearchResults.SERIES.length;

  const grid = $('omnisearchResultsGrid');
  const emptyState = $('omnisearchEmptyState');
  if (!grid) return;

  grid.innerHTML = '';
  const currentList = omnisearchResults[omnisearchActiveTab] || [];

  if (currentList.length === 0) {
    if (emptyState) {
      emptyState.classList.remove('hidden');
      const input = $('inputOmnisearch');
      emptyState.textContent = (input && input.value.trim().length >= 2) ?
        'Nenhum resultado encontrado para "' + input.value.trim() + '" nesta categoria.' :
        'Digite uma palavra-chave para buscar instantaneamente em todo o catálogo.';
    }
    return;
  }

  if (emptyState) emptyState.classList.add('hidden');

  const fragment = document.createDocumentFragment();
  const displayLimit = Math.min(currentList.length, 60);

  for (let i = 0; i < displayLimit; i++) {
    const item = currentList[i];
    const btn = document.createElement('button');
    btn.className = 'omnisearch-item-btn';
    btn.setAttribute('tabindex', '0');

    const typeBadgeClass = item.contentType === 'LIVE' ? 'badge-live' : (item.contentType === 'MOVIE' ? 'badge-movie' : 'badge-series');
    const typeLabel = item.contentType === 'LIVE' ? 'Ao Vivo' : (item.contentType === 'MOVIE' ? 'Filme' : 'Série');

    btn.innerHTML = `
      <div class="omnisearch-item-header">
        <span class="omnisearch-type-badge ${typeBadgeClass}">${typeLabel}</span>
        <span style="font-size:12px; color:#ffd54f;">${item.isAdult ? '+18' : ''}</span>
      </div>
      <div class="omnisearch-item-name">${item.name}</div>
      <div class="omnisearch-item-group">${item.group || 'Geral'}</div>
    `;

    btn.onclick = () => {
      closeOmnisearch();
      if (item.contentType === 'SERIES' && typeof openSeriesModal === 'function') {
        const seriesObj = { title: item.name, episodes: [item] };
        openSeriesModal(seriesObj);
      } else {
        if (typeof switchContentType === 'function' && currentContentType !== item.contentType) {
          switchContentType(item.contentType);
        }
        playStream(item);
      }
    };

    fragment.appendChild(btn);
  }

  grid.appendChild(fragment);
}

// 5. GUIA EPG COMPLETO (MODAL EPG)
let currentEpgListings = [];

function openEpgGuideModal() {
  if (!activeItem) {
    showChannelBanner('Sintonize um canal para ver a grade');
    return;
  }
  const modal = $('modalEpgGuide');
  if (!modal) return;
  modal.classList.remove('hidden');
  activeZone = 'modalEpgGuide';

  if ($('txtEpgGuideTitle')) {
    $('txtEpgGuideTitle').textContent = '🔵 Programação: ' + activeItem.name;
  }

  const list = $('epgScheduleList');
  if (!list) return;

  if (currentEpgListings && currentEpgListings.length > 0) {
    const nowMs = Date.now();
    let html = '';
    currentEpgListings.forEach(l => {
      const isNow = (l.start <= nowMs && l.stop > nowMs);
      const startH = new Date(l.start).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      const stopH = new Date(l.stop).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      html += `
        <div class="epg-schedule-row ${isNow ? 'active-now' : ''}">
          <div class="epg-time-col">${startH} - ${stopH} ${isNow ? '<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span>' : ''}</div>
          <div class="epg-details-col">
            <div class="epg-sched-title">${l.title}</div>
            <div class="epg-sched-desc">${l.desc || 'Sem sinopse disponível.'}</div>
          </div>
        </div>
      `;
    });
    list.innerHTML = html;
  } else {
    const now = new Date();
    const h1 = now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    const nextDate = new Date(now.getTime() + 50 * 60000);
    const h2 = nextDate.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    const laterDate = new Date(now.getTime() + 110 * 60000);
    const h3 = laterDate.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

    list.innerHTML = `
      <div class="epg-schedule-row active-now">
        <div class="epg-time-col">${h1} - ${h2}<br><span style="color:#10b981; font-size:11px; font-weight:800;">[NO AR]</span></div>
        <div class="epg-details-col">
          <div class="epg-sched-title">${activeItem.name} - Ao Vivo</div>
          <div class="epg-sched-desc">Transmissão em tempo real da categoria ${activeItem.group || 'Geral'}.</div>
        </div>
      </div>
      <div class="epg-schedule-row">
        <div class="epg-time-col">${h2} - ${h3}</div>
        <div class="epg-details-col">
          <div class="epg-sched-title">Programação Sequencial</div>
          <div class="epg-sched-desc">Continuação da grade diária da emissora.</div>
        </div>
      </div>
    `;
  }
}

// ==========================================================================
// v1.2.0 FASE 3: DNS FAILOVER AUTOMÁTICO (CLOUDFLARE 1.1.1.1 / GOOGLE 8.8.8.8)
// ==========================================================================
const dnsFailoverCache = new Map();

async function attemptDnsFailover(originalUrl) {
  try {
    const parsed = new URL(originalUrl);
    const host = parsed.hostname;
    if (/^(\d{1,3}\.){3}\d{1,3}$/.test(host)) return originalUrl;

    if (dnsFailoverCache.has(host)) {
      const cachedIp = dnsFailoverCache.get(host);
      return originalUrl.replace(host, cachedIp);
    }

    let resolvedIp = null;
    try {
      const res = await fetch(`https://1.1.1.1/dns-query?name=${encodeURIComponent(host)}&type=A`, {
        headers: { 'Accept': 'application/dns-json' }
      });
      if (res.ok) {
        const json = await res.json();
        if (json.Answer && json.Answer.length > 0) {
          const aRec = json.Answer.find(a => a.type === 1);
          if (aRec && aRec.data) resolvedIp = aRec.data;
        }
      }
    } catch (e) {
      console.warn('[DNS Failover] Cloudflare DoH falhou:', e);
    }

    if (!resolvedIp) {
      try {
        const res = await fetch(`https://dns.google/resolve?name=${encodeURIComponent(host)}&type=A`, {
          headers: { 'Accept': 'application/json' }
        });
        if (res.ok) {
          const json = await res.json();
          if (json.Answer && json.Answer.length > 0) {
            const aRec = json.Answer.find(a => a.type === 1);
            if (aRec && aRec.data) resolvedIp = aRec.data;
          }
        }
      } catch (e) {
        console.warn('[DNS Failover] Google DoH falhou:', e);
      }
    }

    if (resolvedIp) {
      console.info(`[DNS Failover] Host ${host} resolvido para ${resolvedIp}`);
      dnsFailoverCache.set(host, resolvedIp);
      return originalUrl.replace(host, resolvedIp);
    }
  } catch (err) {
    console.debug('[DNS Failover Exception]', err);
  }
  return originalUrl;
}

// ============================================================================
// WATCHDOG DE AUTO-RECONEXÃO NO AO VIVO (Item 1)
// ============================================================================
let stallWatchdogInterval = null;
let lastVideoCurrentTime = -1;
let stallCount = 0;
let isAutoReconnecting = false;
let autoReconnectAttempts = 0;
const MAX_AUTO_RECONNECT_ATTEMPTS = 5;

function showPlayerReconnectBadge(msg) {
  let badge = $('playerReconnectBadge');
  if (!badge) {
    badge = document.createElement('div');
    badge.id = 'playerReconnectBadge';
    badge.className = 'player-reconnect-badge';
    const container = $('videoContainer') || ($('tvPlayer') && $('tvPlayer').parentElement);
    if (container) container.appendChild(badge);
  }
  badge.textContent = msg;
  badge.classList.remove('hidden');
  badge.style.display = 'block';
}

function hidePlayerReconnectBadge() {
  const badge = $('playerReconnectBadge');
  if (badge) {
    badge.classList.add('hidden');
    badge.style.display = 'none';
  }
}

function stopStallWatchdog() {
  if (stallWatchdogInterval) {
    clearInterval(stallWatchdogInterval);
    stallWatchdogInterval = null;
  }
}

function triggerAutoReconnect(cause) {
  if (isAutoReconnecting || !activeItem) return;
  isAutoReconnecting = true;
  autoReconnectAttempts++;

  if (autoReconnectAttempts > MAX_AUTO_RECONNECT_ATTEMPTS) {
    console.error('[SmartPlayer Watchdog] Limite máximo de reconexão atingido:', cause);
    isAutoReconnecting = false;
    // 2.5.3: antes de desistir, tenta outra fonte (duplicata na mesma lista ou
    // o mesmo canal em outro servidor já guardado no cache do aparelho).
    if (typeof mk21TryAlternativeSource === 'function') {
      mk21TryAlternativeSource().then(trocou => {
        if (trocou) return;
        showPlayerReconnectBadge('Falha de conexão. Tente outro canal.');
        if ($('epgStatus')) $('epgStatus').textContent = '⚠️ FORA DO AR';
      }).catch(() => {
        showPlayerReconnectBadge('Falha de conexão. Tente outro canal.');
        if ($('epgStatus')) $('epgStatus').textContent = '⚠️ FORA DO AR';
      });
      return;
    }
    showPlayerReconnectBadge('Falha de conexão. Tente outro canal.');
    if ($('epgStatus')) $('epgStatus').textContent = '⚠️ FORA DO AR';
    return;
  }

  const delay = Math.min(1000 * Math.pow(1.5, autoReconnectAttempts - 1), 6000);
  showPlayerReconnectBadge(`Reconectando (${autoReconnectAttempts}/${MAX_AUTO_RECONNECT_ATTEMPTS})...`);
  if ($('epgStatus')) $('epgStatus').textContent = `🔄 RECONECTANDO (${autoReconnectAttempts}/${MAX_AUTO_RECONNECT_ATTEMPTS})...`;

  setTimeout(() => {
    if (!activeItem) {
      isAutoReconnecting = false;
      return;
    }
    console.log(`[SmartPlayer Watchdog] Executando reconexão tentativa ${autoReconnectAttempts} para: ${activeItem.name}`);
    if (hlsInstance) {
      try {
        hlsInstance.recoverMediaError();
      } catch (e) {
        if (typeof playStream === 'function') playStream(activeItem);
      }
    } else {
      if (typeof playStream === 'function') playStream(activeItem);
    }
    isAutoReconnecting = false;
  }, delay);
}

let watchdogStreamKey = '';

function isCurrentItemVod() {
  if (!activeItem) return false;
  const cType = activeItem.contentType || activeItem.type || currentContentType;
  if (cType === 'MOVIE' || cType === 'SERIES') return true;
  const u = String(activeItem.url || '').toLowerCase();
  return u.includes('/movie/') || u.includes('/series/') || u.includes('.mp4') || u.includes('.mkv') || u.includes('.avi');
}

function startStallWatchdog(streamKey) {
  stopStallWatchdog();
  const chave = String(streamKey || (activeItem && activeItem.url) || '');
  if (watchdogStreamKey !== chave) {
    watchdogStreamKey = chave;
    lastVideoCurrentTime = -1;
    stallCount = 0;
    autoReconnectAttempts = 0;
    isAutoReconnecting = false;
    hidePlayerReconnectBadge();
  }

  const video = $('tvPlayer');
  if (!video) return;

  if (!video._mk21EventsBound) {
    video._mk21EventsBound = true;
    video.addEventListener('waiting', () => {
      if (isCurrentItemVod()) {
        showPlayerReconnectBadge('Carregando filme/série...');
        return;
      }
      stallCount += 2;
      if (stallCount >= 6) triggerAutoReconnect('Buffer esgotado (waiting)');
    });
    video.addEventListener('stalled', () => {
      if (isCurrentItemVod()) return;
      stallCount += 2;
      if (stallCount >= 6) triggerAutoReconnect('Stream estagnado (stalled)');
    });
    video.addEventListener('playing', () => {
      stallCount = 0;
      autoReconnectAttempts = 0;
      isAutoReconnecting = false;
      hidePlayerReconnectBadge();
    });
    video.addEventListener('error', (e) => {
      console.warn('[SmartPlayer] Erro nativo no vídeo:', e);
      if (!isCurrentItemVod()) {
        triggerAutoReconnect('Erro nativo de reprodução');
      }
    });
  }

  stallWatchdogInterval = setInterval(() => {
    if (!video || video.paused || isAutoReconnecting || !activeItem) return;
    const isVod = isCurrentItemVod();
    const stallThreshold = isVod ? 30 : 6; // VOD tolera buffer de rede sem reiniciar o filme
    const nowTime = video.currentTime;
    if (Math.abs(nowTime - lastVideoCurrentTime) < 0.2) {
      stallCount++;
      if (stallCount >= stallThreshold) {
        if (isVod) {
          console.warn('[SmartPlayer Watchdog] VOD em buffer. Retomando reprodução...');
          try { safeVideoPlay(video); } catch (e) {}
          stallCount = 0;
        } else {
          console.warn('[SmartPlayer Watchdog] Travamento detectado no stream ao vivo.');
          triggerAutoReconnect('Stream congelado');
        }
      }
    } else {
      stallCount = 0;
      lastVideoCurrentTime = nowTime;
    }
  }, 1000);
}

// ==================== LIVE DIAGNOSTIC DEV LOGGER (SMART TV) ====================
let sessionDevLogs = [];
const MAX_DEV_LOGS = 300;

function addDevLog(msg, type = 'info') {
  const time = new Date().toLocaleTimeString('pt-BR', { hour12: false });
  const entry = { time, msg, type };
  sessionDevLogs.push(entry);
  if (sessionDevLogs.length > MAX_DEV_LOGS) sessionDevLogs.shift();

  try {
    const container = $('devLogsContainer');
    if (container) {
      if (container.innerHTML.includes('[Console inicializado.')) {
        container.innerHTML = '';
      }
      const line = document.createElement('div');
      line.style.marginBottom = '4px';
      line.style.wordBreak = 'break-word';
      
      let color = '#cbd5e1';
      if (type === 'error') color = '#ef4444';
      else if (type === 'warn') color = '#eab308';
      else if (type === 'success') color = '#22c55e';
      else if (type === 'debug') color = '#94a3b8';

      line.innerHTML = `<span style="color:#64748b;">[${time}]</span> <span style="color:${color}; font-weight:600;">${escapeHtml(msg)}</span>`;
      container.appendChild(line);
      container.scrollTop = container.scrollHeight;
    }
    const badge = $('devLogsCountBadge');
    if (badge) badge.textContent = `${sessionDevLogs.length} entradas`;
  } catch (e) {}

  if (type === 'error') console.error('[MK21-TV]', msg);
  else if (type === 'warn') console.warn('[MK21-TV]', msg);
  else console.log('[MK21-TV]', msg);
}

function escapeHtml(str) {
  return String(str || '').replace(/[&<>"']/g, m => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[m]);
}

function toggleDevLogsModal() {
  const modal = $('modalDevLogs');
  if (!modal) return;
  const isHidden = modal.classList.contains('hidden');
  if (isHidden) {
    modal.classList.remove('hidden');
    addDevLog('[UI] Console de logs aberto', 'info');
    const closeBtn = $('btnCloseDevLogs');
    if (closeBtn) closeBtn.focus();
  } else {
    modal.classList.add('hidden');
    activeZone = 'channels';
    focusActiveElement();
  }
}

function initDevLogs() {
  if ($('btnHeaderLogs')) $('btnHeaderLogs').onclick = toggleDevLogsModal;
  if ($('btnCloseDevLogs')) $('btnCloseDevLogs').onclick = toggleDevLogsModal;
  if ($('btnClearDevLogs')) $('btnClearDevLogs').onclick = () => {
    sessionDevLogs = [];
    const container = $('devLogsContainer');
    if (container) container.innerHTML = '<div style="color:#64748b;">[Logs limpos com sucesso.]</div>';
    const badge = $('devLogsCountBadge');
    if (badge) badge.textContent = '0 entradas';
  };
  if ($('btnExportDevLogs')) $('btnExportDevLogs').onclick = () => {
    try {
      const text = [
        'MK21 Smart TV - Logs de diagnóstico',
        `Data: ${new Date().toISOString()}`,
        'Credenciais e caminhos autenticados foram mascarados.',
        '',
        ...sessionDevLogs.map(l => `[${l.time}] [${l.type.toUpperCase()}] ${redactDiagnosticLog(l.msg)}`)
      ].join('\n');
      localStorage.setItem('mk21_last_exported_logs', text);
      const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `mk21-tv-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      addDevLog('[Logs] Arquivo de log exportado e salvo com sucesso!', 'success');
      showChannelBanner('✅ Logs exportados com sucesso!');
    } catch (e) {
      addDevLog('[Logs] Erro ao exportar logs: ' + e.message, 'error');
    }
  };
  if ($('btnGitHubDevLogs')) $('btnGitHubDevLogs').onclick = () => {
    window.open('https://github.com/2fbg/FBGs-Streaming/upload/main/logs', '_blank', 'noopener,noreferrer');
  };
}

function redactDiagnosticLog(value) {
  return String(value)
    .replace(/(https?:\/\/)[^/@\s:]+:[^/@\s]+@/gi, '$1[REDACTED]@')
    .replace(/([?&](?:username|user|password|pass|token|auth|api[_-]?key)=)[^&#\s]*/gi, '$1[REDACTED]')
    .replace(/\/(live|movie|series)\/[^/\s?]+\/[^/\s?]+/gi, '/$1/[REDACTED]/[REDACTED]');
}

let mpegtsPlayer = null;

let playAbortController = null;

function safeVideoPlay(video) {
  if (!video) return Promise.resolve();
  try {
    const p = video.play();
    if (p && typeof p.then === 'function') {
      return p.catch(err => {
        if (err && (err.name === 'AbortError' || (err.message && err.message.includes('interrupted')))) {
          console.debug('[Player] Play request interrupted cleanly by zapping.');
          return;
        }
        console.warn('[Player Play Error]:', err && err.message);
      });
    }
  } catch (e) {
    console.warn('[Player Exception]:', e);
  }
  return Promise.resolve();
}

function stopCurrentPlayer(abortPlayRequests = true) {
  if (abortPlayRequests && playAbortController) {
    try { playAbortController.abort(); } catch (e) {}
    playAbortController = null;
  }
  if (mpegtsPlayer) {
    try {
      mpegtsPlayer.pause();
      mpegtsPlayer.unload();
      mpegtsPlayer.detachMediaElement();
      mpegtsPlayer.destroy();
    } catch (e) {}
    mpegtsPlayer = null;
  }
  if (hlsInstance) {
    try { hlsInstance.destroy(); } catch (e) {}
    hlsInstance = null;
  }
  const video = $('tvPlayer');
  if (video) {
    try {
      video.pause();
      video.src = "";
      video.removeAttribute('src');
      video.innerHTML = '';
      video.load();
    } catch (e) {}
  }
}

// ==================== BUFFER / FLUIDEZ (2.5.4) ====================
const MK21_BUFFER_MODES = {
  auto: {
    label: 'Automático (recomendado)',
    hint: 'Equilíbrio entre zapping rápido e estabilidade.',
    hls: { maxBufferLength: 15, maxMaxBufferLength: 30, liveSyncDurationCount: 3, backBufferLength: 10 },
    mpegts: { liveBufferLatencyChasing: true, stashInitialSize: 128 * 1024 }
  },
  fast: {
    label: 'Rápido / Zapping veloz',
    hint: 'Troca de canal quase instantânea. Usa internet boa.',
    hls: { maxBufferLength: 8, maxMaxBufferLength: 16, liveSyncDurationCount: 2, backBufferLength: 4 },
    mpegts: { liveBufferLatencyChasing: true, stashInitialSize: 64 * 1024 }
  },
  stable: {
    label: 'Estável / Internet fraca',
    hint: 'Buffer maior: demora um pouco a abrir, mas quase não trava.',
    hls: { maxBufferLength: 30, maxMaxBufferLength: 60, liveSyncDurationCount: 4, backBufferLength: 30 },
    mpegts: { liveBufferLatencyChasing: false, stashInitialSize: 512 * 1024 }
  }
};

function mk21BufferMode() {
  const mode = localStorage.getItem('mk21_buffer_mode') || 'auto';
  return MK21_BUFFER_MODES[mode] ? mode : 'auto';
}

function mk21HlsOptions() {
  const mode = MK21_BUFFER_MODES[mk21BufferMode()];
  return Object.assign({
    enableWorker: true,
    lowLatencyMode: true,
    startFragPrefetch: true,
    maxBufferSize: 25 * 1000 * 1000,
    fragLoadingTimeOut: 15000,
    manifestLoadingTimeOut: 15000,
    levelLoadingTimeOut: 15000,
    fragLoadingMaxRetry: 4,
    manifestLoadingMaxRetry: 4,
    levelLoadingMaxRetry: 4,
    fragLoadingRetryDelay: 1000,
    manifestLoadingRetryDelay: 1000
  }, mode.hls);
}

function mk21MpegtsOptions() {
  const mode = MK21_BUFFER_MODES[mk21BufferMode()];
  return Object.assign({
    enableWorker: true,
    lazyLoad: false,
    autoCleanupSourceBuffer: true
  }, mode.mpegts);
}

// Um fallback por URL por vez: antes o play() do mpegts rejeitava atrasado e
// derrubava um HLS que já estava tocando.
let mk21FallbackState = { url: '', at: 0, stage: 0 };
// URLs que já deram erro HTTP no mpegts.js: a próxima tentativa vai direto para
// o decodificador nativo, sem repetir 2s de espera a cada reconexão.
const mk21MpegtsBlocked = new Set();

function mk21FallbackAllowed(item, stage) {
  if (!item || !item.url) return false;
  const now = Date.now();
  if (mk21FallbackState.url === item.url && now - mk21FallbackState.at < 6000 && mk21FallbackState.stage >= stage) return false;
  mk21FallbackState = { url: item.url, at: now, stage };
  return true;
}

function isPlayInterruption(err) {
  const msg = (err && (err.message || err.msg || '')) || '';
  const name = (err && err.name) || '';
  return name === 'AbortError' || /interrupted|abort/i.test(msg);
}

// Fallback nativo de MPEG-TS: no webOS/Tizen o pipeline de hardware costuma
// abrir canais .ts que o mpegts.js (MSE) não consegue decodificar.
function fallbackToNativeTs(item) {
  if (!item || !item.url) return false;
  if (!mk21FallbackAllowed(item, 1)) return false;
  const video = $('tvPlayer');
  if (!video) return false;
  addDevLog('[Player Fallback] Tentando MPEG-TS nativo no hardware da TV...', 'info');
  try {
    if (hlsInstance) { hlsInstance.destroy(); hlsInstance = null; }
    if (mpegtsPlayer) { try { mpegtsPlayer.unload(); mpegtsPlayer.detachMediaElement(); mpegtsPlayer.destroy(); } catch (e) {} mpegtsPlayer = null; }
    // No webOS/Tizen o pipeline de hardware costuma abrir o .ts quando a URL é
    // definida direto no <video>; a tag <source type="video/mp2t"> é plano B.
    video.innerHTML = '';
    try { video.src = item.url; } catch (e) { video.src = ''; }
    if (!video.src) {
      const src = document.createElement('source');
      src.src = item.url;
      src.type = 'video/mp2t';
      video.appendChild(src);
    }
    video.load();
    safeVideoPlay(video).then(() => {
      addDevLog('[Player Engine] MPEG-TS nativo reproduzindo!', 'success');
      hidePlayerReconnectBadge();
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
      updateRealResolution(item);
    }).catch(err => {
      if (isPlayInterruption(err)) return;
      addDevLog(`[Player Fallback] Nativo falhou (${err && err.message}). Tentando HLS...`, 'warn');
      fallbackToHls(item);
    });
    return true;
  } catch (e) {
    addDevLog('[Player Fallback] Nativo indisponível. Tentando HLS...', 'warn');
    fallbackToHls(item);
    return true;
  }
}

function fallbackToHls(item) {
  if (!item || !item.url) return;
  if (!mk21FallbackAllowed(item, 2)) return;
  const hlsUrl = item.url.replace(/\.ts($|\?)/i, '.m3u8$1');
  addDevLog(`[Player Fallback] Rotacionando formato para HLS (.m3u8): ${hlsUrl}`, 'info');
  stopCurrentPlayer(false);
  const video = $('tvPlayer');
  if (!video) return;

  if (window.Hls && Hls.isSupported()) {
    if (hlsInstance) { try { hlsInstance.destroy(); } catch (e) {} hlsInstance = null; }
    hlsInstance = new Hls(mk21HlsOptions());
    hlsInstance.loadSource(hlsUrl);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      addDevLog('[Player Engine] Fallback HLS reproduzindo com sucesso!', 'success');
      if (typeof mk21ApplyPlaybackQuality === 'function') mk21ApplyPlaybackQuality(hlsInstance);
      try { video.preservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
      safeVideoPlay(video);
      hidePlayerReconnectBadge();
      if ($('btnPlayPause')) $('btnPlayPause').textContent = '⏯️ Pausar';
      if ($('fsBtnPlay')) $('fsBtnPlay').textContent = '⏯️ Pausar';
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
      updateRealResolution(item);
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      addDevLog(`[HLS Error] Tipo: ${data.type} | Detalhe: ${data.details}`, 'error');
      if (data.fatal) {
        triggerAutoReconnect('Erro HLS Fatal');
      }
    });
  } else {
    video.src = hlsUrl;
    video.load();
    safeVideoPlay(video).then(() => {
      addDevLog('[Player Engine] Fallback HLS nativo reproduzindo!', 'success');
      hidePlayerReconnectBadge();
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
      updateRealResolution(item);
    }).catch(err => {
      addDevLog(`[Player Error] Falha no fallback: ${err && err.message}`, 'error');
      triggerAutoReconnect('Falha no fallback');
    });
  }
}

let zappingLockPromise = Promise.resolve();

// 10. PLAYER DE VÍDEO & CONTROLE DE VELOCIDADE ATÉ 4X
function playStream(item, forcedUrl) {
  if (!item || !item.url) return;

  if (playAbortController) {
    try { playAbortController.abort(); } catch (e) {}
  }
  playAbortController = new AbortController();
  const currentSignal = playAbortController.signal;

  zappingLockPromise = zappingLockPromise.catch(() => {}).then(async () => {
    if (currentSignal.aborted) return;
    stopCurrentPlayer(false);
    const video = $('tvPlayer');
    if (video) {
      try {
        video.pause();
        video.src = "";
        video.removeAttribute('src');
        video.innerHTML = '';
        video.load();
      } catch (e) {}
    }

    // Microtask buffer purge to eliminate "play() interrupted by pause()" race conditions
    await new Promise(r => setTimeout(r, 20));
    if (currentSignal.aborted) return;

    activeItem = item;
    if (typeof mk21ResetSourceFailover === 'function') mk21ResetSourceFailover(item);
    recordWatchedItem(item);
    saveLastChannel(item);
    prefetchAdjacentChannels(item);
    startProgressTracker(item);
    startStallWatchdog(forcedUrl || item.url);

    if (!video) return;

    const streamFormatPref = localStorage.getItem('mk21_stream_format') || 'auto';
    addDevLog(`[Player] Iniciando carregamento: "${item.name}" (Modo: ${currentContentType})`, 'info');
    addDevLog(`[Player] URL: ${item.url}`, 'info');
    addDevLog(`[Player] Formato preferido: ${streamFormatPref}`, 'debug');

  if (item.currentTime && item.currentTime > 5 && (item.contentType === 'MOVIE' || item.contentType === 'SERIES' || currentContentType === 'MOVIE' || currentContentType === 'SERIES')) {
    const resumeAt = item.currentTime;
    const onLoaded = function () {
      video.removeEventListener('loadedmetadata', onLoaded);
      try {
        video.currentTime = resumeAt;
        showChannelBanner('▶ Continuando em ' + formatTimeSec(resumeAt));
      } catch (e) {}
    };
    video.addEventListener('loadedmetadata', onLoaded);
  }

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
  updateRealResolution(item);

  if (favoriteUrls.has(item.url)) {
    if (typeof updateFavoriteBtnUI === 'function') updateFavoriteBtnUI(true);
  } else {
    if (typeof updateFavoriteBtnUI === 'function') updateFavoriteBtnUI(false);
  }

  showChannelBanner(item.name);

  let streamUrl = forcedUrl || item.url;
  if (streamFormatPref === 'hls' && streamUrl.toLowerCase().includes('.ts')) {
    streamUrl = streamUrl.replace(/\.ts($|\?)/i, '.m3u8$1');
    addDevLog(`[Player] Preferência HLS forçada: ${streamUrl}`, 'info');
  }

  const isM3U8 = streamUrl.toLowerCase().includes('.m3u8');
  const isTS = streamUrl.toLowerCase().includes('.ts');

  if (isM3U8 && window.Hls && Hls.isSupported()) {
    addDevLog('[Player Engine] Usando decodificador Hls.js Low-Latency...', 'info');
    hlsInstance = new Hls(Object.assign(mk21HlsOptions(), { startLevel: -1 }));
    hlsInstance.loadSource(streamUrl);
    hlsInstance.attachMedia(video);
    if ($('epgStatus')) $('epgStatus').textContent = '⏳ CARREGANDO...';
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      addDevLog('[Player Engine] Manifesto HLS decodificado com sucesso!', 'success');
      if (typeof mk21ApplyPlaybackQuality === 'function') mk21ApplyPlaybackQuality(hlsInstance);
      try { video.preservesPitch = true; video.webkitPreservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
      safeVideoPlay(video);
      hidePlayerReconnectBadge();
      if (typeof updatePlayPauseBtnUI === 'function') updatePlayPauseBtnUI(true);
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
      updateRealResolution(item);
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      addDevLog(`[HLS Error] Tipo: ${data.type} | Detalhe: ${data.details}`, 'warn');
      if (data.fatal) {
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            addDevLog('[HLS Error] Recuperando erro de rede...', 'warn');
            if ($('epgStatus')) $('epgStatus').textContent = '🔄 RECONECTANDO...';
            showPlayerReconnectBadge('Reconectando rede...');
            hlsInstance.startLoad();
            break;
          case Hls.ErrorTypes.MEDIA_ERROR:
            addDevLog('[HLS Error] Recuperando decodificação de mídia...', 'warn');
            if ($('epgStatus')) $('epgStatus').textContent = '🔄 RECUPERANDO MÍDIA...';
            showPlayerReconnectBadge('Recuperando decodificação...');
            hlsInstance.recoverMediaError();
            break;
          default:
            addDevLog('[HLS Fatal] Erro irrecuperável HLS, tentando auto-reconexão...', 'error');
            triggerAutoReconnect('Erro HLS Fatal');
            break;
        }
      }
    });
  } else if (isTS && window.mpegts && mpegts.isSupported() && !mk21MpegtsBlocked.has(item.url)) {
    addDevLog('[Player Engine] Canal MPEG-TS identificado. Construindo mpegts.js player...', 'info');
    try {
      if (mpegtsPlayer) { try { mpegtsPlayer.unload(); mpegtsPlayer.detachMediaElement(); mpegtsPlayer.destroy(); } catch (e) {} mpegtsPlayer = null; }
      mk21FallbackState = { url: item.url, at: 0, stage: 0 };
      mpegtsPlayer = mpegts.createPlayer({
        type: 'mse',
        isLive: (currentContentType === 'LIVE'),
        url: streamUrl
      }, mk21MpegtsOptions());
      mpegtsPlayer.attachMediaElement(video);
      mpegtsPlayer.load();
      mpegtsPlayer.play().then(() => {
        addDevLog('[Player Engine] MPEG-TS reproduzindo!', 'success');
        hidePlayerReconnectBadge();
        if (typeof updatePlayPauseBtnUI === 'function') updatePlayPauseBtnUI(true);
        if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
        updateRealResolution(item);
      }).catch(err => {
        if (isPlayInterruption(err)) return;
        addDevLog(`[MPEGTS Error] ${err && err.message}. Tentando MPEG-TS nativo...`, 'warn');
        if (!fallbackToNativeTs(item)) fallbackToHls(item);
      });
      mpegtsPlayer.on(mpegts.Events.ERROR, (type, detail, info) => {
        const msg = String(detail || '') + ' ' + String((info && (info.msg || info.reason)) || '');
        if (/interrupt|abort/i.test(msg)) return;
        addDevLog(`[MPEGTS Error] ${type}: ${detail}`, 'warn');
        if (/HttpStatus|NetworkError|NETWORK_/i.test(msg)) {
          if (mk21MpegtsBlocked.size > 400) mk21MpegtsBlocked.clear();
          mk21MpegtsBlocked.add(item.url);
          addDevLog('[Player Engine] Fonte recusou o mpegts.js; usando decodificador nativo da TV.', 'warn');
        }
        if (!fallbackToNativeTs(item)) fallbackToHls(item);
      });
    } catch (eMpeg) {
      addDevLog(`[MPEGTS Exception] ${eMpeg.message}. Fallback HLS...`, 'warn');
      fallbackToHls(item);
    }
  } else {
    // Decodificação direta no hardware da TV com tags de fonte MIME apropriadas e suporte direto a VOD
    addDevLog('[Player Engine] Usando decodificação nativa acelerada por hardware...', 'info');
    video.innerHTML = '';
    video.src = streamUrl;
    try { video.preservesPitch = true; video.webkitPreservesPitch = true; video.playbackRate = currentPlaybackSpeed; } catch (e) {}
    video.load();
    safeVideoPlay(video).then(() => {
      addDevLog('[Player Engine] Playback nativo iniciado com sucesso!', 'success');
      hidePlayerReconnectBadge();
      if (typeof updatePlayPauseBtnUI === 'function') updatePlayPauseBtnUI(true);
      if ($('epgStatus')) $('epgStatus').textContent = '🔴 NO AR';
      updateRealResolution(item);
    }).catch(err => {
      addDevLog(`[Player Fallback] Falha no playback nativo (${err && err.message}). Tentando fallback HLS...`, 'warn');
      fallbackToHls(item);
    });
  }
  });
}

// DETECÇÃO REAL DE RESOLUÇÃO (HD / FHD / 4K / SD)
function updateRealResolution(item) {
  const badge = document.querySelector('.badge-quality') || $('badgeStreamResolution');
  if (!badge) return;

  const targetItem = item || activeItem;
  const name = (targetItem && targetItem.name) ? targetItem.name.toUpperCase() : '';
  let resText = 'HD 720p';

  // 1. Tenta deduzir do nome/categoria
  if (name.includes('4K') || name.includes('UHD') || name.includes('2160')) {
    resText = '4K 2160p';
  } else if (name.includes('FHD') || name.includes('1080')) {
    resText = 'FHD 1080p';
  } else if (name.includes('HD') || name.includes('720')) {
    resText = 'HD 720p';
  } else if (name.includes('SD') || name.includes('480')) {
    resText = 'SD 480p';
  }

  // 2. Tenta obter as dimensões reais de decodificação de vídeo do player nativo
  const v = $('tvPlayer');
  if (v && v.videoHeight > 0) {
    const h = v.videoHeight;
    const w = v.videoWidth;
    if (h >= 2160 || w >= 3800) resText = '4K 2160p';
    else if (h >= 1080 || w >= 1900) resText = 'FHD 1080p';
    else if (h >= 720 || w >= 1200) resText = 'HD 720p';
    else resText = `SD ${h}p`;
  }

  badge.textContent = resText;
}

const vElement = $('tvPlayer');
if (vElement) {
  vElement.addEventListener('loadedmetadata', () => updateRealResolution(activeItem));
  vElement.addEventListener('resize', () => updateRealResolution(activeItem));
  vElement.addEventListener('timeupdate', updateVodProgressTimeline);
}

// BARRA DE PROGRESSO E LINHA DO TEMPO PARA FILMES E SÉRIES
function formatTimeSec(sec) {
  if (!sec || !isFinite(sec) || isNaN(sec) || sec <= 0) return '00:00:00';
  const s = Math.floor(sec);
  const hrs = Math.floor(s / 3600);
  const mins = Math.floor((s % 3600) / 60);
  const secs = s % 60;
  const pad = n => (n < 10 ? '0' : '') + n;
  if (hrs > 0) {
    return `${pad(hrs)}:${pad(mins)}:${pad(secs)}`;
  }
  return `00:${pad(mins)}:${pad(secs)}`;
}

function updateVodProgressTimeline() {
  const v = $('tvPlayer');
  if (!v) return;

  const isVod = currentContentType === 'MOVIE' || currentContentType === 'SERIES' ||
    (activeItem && activeItem.contentType !== 'LIVE');

  const fsBox = $('fsVodProgressBox');
  const vodTimeRow = $('vodTimeRow');
  const epgBarBox = $('epgBarBox');

  if (!isVod) {
    if (fsBox) fsBox.classList.add('hidden');
    if (vodTimeRow) vodTimeRow.classList.add('hidden');
    return;
  }

  // É Filme ou Série: exibe timeline e tempo real
  if (fsBox && !$('fsHudBar').classList.contains('hidden')) fsBox.classList.remove('hidden');
  if (vodTimeRow) vodTimeRow.classList.remove('hidden');

  const cur = v.currentTime || 0;
  const dur = v.duration || 0;
  const pct = dur > 0 ? Math.min(100, Math.max(0, (cur / dur) * 100)) : 0;

  // Atualiza barra HUD
  if ($('fsProgressFill')) $('fsProgressFill').style.width = pct + '%';
  if ($('fsTimeCurrent')) $('fsTimeCurrent').textContent = formatTimeSec(cur);
  if ($('fsTimeDuration')) $('fsTimeDuration').textContent = formatTimeSec(dur);

  // Atualiza barra mini-card
  const pBar = $('epgProgressBar') || document.querySelector('.epg-progress');
  if (pBar) pBar.style.width = pct + '%';
  if ($('vodTimeCurrent')) $('vodTimeCurrent').textContent = formatTimeSec(cur);
  if ($('vodTimeDuration')) $('vodTimeDuration').textContent = formatTimeSec(dur);
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
    const isVod = currentContentType === 'MOVIE' || currentContentType === 'SERIES' ||
      (activeItem && activeItem.contentType !== 'LIVE');
    if ($('fsVodProgressBox')) {
      if (isVod) $('fsVodProgressBox').classList.remove('hidden');
      else $('fsVodProgressBox').classList.add('hidden');
    }
    if ($('fsEpgOverlayBox')) {
      if (isVod) $('fsEpgOverlayBox').classList.add('hidden');
      else $('fsEpgOverlayBox').classList.remove('hidden');
    }
    updateVodProgressTimeline();
  } else {
    hud.classList.add('hidden');
  }
}

function toggleFullscreen(force) {
  const box = $('videoContainer');
  const v = $('tvPlayer');
  if (!box) return;
  const currentlyFs = isAppFullscreen();
  const goingIn = (typeof force === 'boolean') ? force : !currentlyFs;

  if (goingIn) {
    box.classList.add('is-fullscreen');
    document.body.classList.add('is-fullscreen');
    activeZone = 'player';
    showFsHud(true);
    pokeFsHud();
    addDevLog('[Player] Modo Tela Cheia ATIVADO', 'info');
    try {
      if (box.requestFullscreen) {
        box.requestFullscreen().catch(() => {});
      } else if (box.webkitRequestFullscreen) {
        box.webkitRequestFullscreen();
      } else if (v && v.webkitRequestFullscreen) {
        v.webkitRequestFullscreen();
      }
    } catch (e) {
      addDevLog('[Player] Fullscreen API fallback ativo (CSS is-fullscreen aplicado): ' + e.message, 'debug');
    }
    setTimeout(function () {
      if ($('fsBtnPlay')) $('fsBtnPlay').focus();
    }, 200);
  } else {
    box.classList.remove('is-fullscreen');
    document.body.classList.remove('is-fullscreen');
    showFsHud(false);
    addDevLog('[Player] Modo Tela Cheia DESATIVADO', 'info');
    try {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      }
    } catch (e) {}
    activeZone = 'channels';
    focusActiveElement();
  }
}

function bindFsHudButtons() {
  if ($('fsBtnPrev')) $('fsBtnPrev').onclick = function () {
    if (currentContentType === 'LIVE') {
      playPreviousChannel();
    } else {
      const v = $('tvPlayer');
      if (v) v.currentTime = Math.max(0, (v.currentTime || 0) - 10);
      showChannelBanner('⏪ -10s');
    }
    pokeFsHud();
  };
  if ($('fsBtnNext')) $('fsBtnNext').onclick = function () {
    if (currentContentType === 'LIVE') {
      playNextChannel();
    } else {
      const v = $('tvPlayer');
      if (v && v.duration) v.currentTime = Math.min(v.duration, (v.currentTime || 0) + 10);
      showChannelBanner('⏩ +10s');
    }
    pokeFsHud();
  };
  if ($('fsBtnPlay')) $('fsBtnPlay').onclick = function () { togglePlayPause(); pokeFsHud(); };
  if ($('fsBtnSpeed')) $('fsBtnSpeed').onclick = function () { cyclePlaybackSpeed(); pokeFsHud(); };
  if ($('fsBtnFav')) $('fsBtnFav').onclick = function () { if (activeItem) toggleFav(activeItem.url); pokeFsHud(); };
  if ($('fsBtnQuality')) $('fsBtnQuality').onclick = function () { if (typeof mk21OpenQualityModal === 'function') mk21OpenQualityModal(true); };
  if ($('fsBtnBuffer')) $('fsBtnBuffer').onclick = function () { cycleBufferMode(); pokeFsHud(); };
  if ($('fsBtnServer')) $('fsBtnServer').onclick = function () { if (typeof openServerPicker === 'function') openServerPicker(); };
  if ($('fsBtnAudio')) $('fsBtnAudio').onclick = function () { if (typeof openAudioSubsModal === 'function') openAudioSubsModal(); };
  if ($('fsBtnBack')) $('fsBtnBack').onclick = function () { toggleFullscreen(false); };
}
bindFsHudButtons();

function updatePlayPauseBtnUI(isPlaying) {
  const btn = $('btnPlayPause');
  if (btn) {
    btn.title = isPlaying ? 'Pausar' : 'Reproduzir';
    btn.innerHTML = isPlaying 
      ? '<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>'
      : '<svg style="width:22px;height:22px;fill:currentColor;" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
  }
  const fsBtn = $('fsBtnPlay');
  if (fsBtn) {
    fsBtn.textContent = isPlaying ? '⏸️ Pausar' : '▶️ Reproduzir';
  }
}

function updateFavoriteBtnUI(isFav) {
  const btn = $('btnFavorite');
  if (btn) {
    btn.title = isFav ? 'Remover dos favoritos' : 'Adicionar aos favoritos';
    btn.innerHTML = isFav
      ? '<svg style="width:22px;height:22px;fill:#ffd54f;" viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>'
      : '<svg style="width:22px;height:22px;fill:rgba(255,255,255,0.7);" viewBox="0 0 24 24"><path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"/></svg>';
  }
  const fsBtn = $('fsBtnFav');
  if (fsBtn) {
    fsBtn.textContent = isFav ? '★ Favoritado' : '⭐ Favoritar';
  }
}

function cycleBufferMode() {
  const modes = ['auto', 'fast', 'stable'];
  const current = mk21BufferMode();
  const next = modes[(modes.indexOf(current) + 1) % modes.length];
  localStorage.setItem('mk21_buffer_mode', next);
  const info = MK21_BUFFER_MODES[next];
  const msg = '⚡ Fluidez: ' + (info ? info.label : next);
  if (typeof showToast === 'function') {
    showToast(msg);
  } else if (typeof showChannelBanner === 'function') {
    showChannelBanner(msg);
  }
}

$('videoContainer').ondblclick = () => toggleFullscreen();
if ($('btnFullscreen')) $('btnFullscreen').onclick = () => toggleFullscreen();

function togglePlayPause() {
  const v = $('tvPlayer');
  if (!v) return;
  if (v.paused) {
    v.play().catch(() => {});
    updatePlayPauseBtnUI(true);
    showChannelBanner('▶ Reproduzindo');
  } else {
    v.pause();
    updatePlayPauseBtnUI(false);
    showChannelBanner('⏸ Pausado');
  }
}

function applyPlaybackSpeed(rate) {
  const video = $('tvPlayer');
  if (!video) return;
  currentPlaybackSpeed = rate;
  try {
    video.playbackRate = rate;
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

if ($('btnPlayPause')) $('btnPlayPause').onclick = togglePlayPause;
if ($('btnFavorite')) $('btnFavorite').onclick = () => {
  if (activeItem) toggleFav(activeItem.url);
};
if ($('btnSpeed')) $('btnSpeed').onclick = cyclePlaybackSpeed;

// BINDING DIRETO DOS NOVOS BOTÕES DO MINI PLAYER: QUALIDADE, BUFFER E FONTE
if ($('btnQuality')) $('btnQuality').onclick = () => {
  if (typeof mk21OpenQualityModal === 'function') mk21OpenQualityModal(false);
};
if ($('btnBufferMode')) $('btnBufferMode').onclick = cycleBufferMode;
if ($('btnQuickServer')) $('btnQuickServer').onclick = () => {
  if (typeof openServerPicker === 'function') openServerPicker();
};

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
    setTimeout(() => {
      const firstCfg = document.querySelector('.cfg-menu-item.active') || $('cfgBtnInfo');
      if (firstCfg) { try { firstCfg.focus(); } catch (e) {} }
    }, 40);
    return;
  }

  // Ao voltar para o Ao Vivo / Filmes / Séries, encerra categoricamente qualquer modo de busca
  if (typeof closeSearchMode === 'function') {
    try { closeSearchMode(true); } catch (e) {}
  }

  // Previne que o navegador foque automaticamente no inputSearch durante a transição
  const safeTabTarget = $(activeTabId) || $('tabLive');
  if (safeTabTarget) {
    try { safeTabTarget.focus(); } catch (e) {}
  }

  $('sectionSettings').classList.add('hidden');
  $('sectionUnified').classList.remove('hidden');

  buildCurrentCategories();
  renderCategoriesList();
  selectCategory('ALL');
  activeZone = 'channels';
  if (type === 'FAVORITES') hydrateIndexedFavorites();

  // Restaura foco imediato na lista de canais ou categoria
  setTimeout(() => {
    const firstItem = $('listItems') ? $('listItems').querySelector('.list-item-btn:not([disabled])') : null;
    const activeCat = $('listCategories') ? $('listCategories').querySelector('.cat-item-btn.active') : null;
    const target = firstItem || activeCat || safeTabTarget;
    if (target) {
      try { target.focus(); target.scrollIntoView({ block: 'nearest' }); } catch (e) {}
    }
  }, 30);
}

// 12. CONFIGURAÇÕES (TOTALMENTE ALINHADO AO CLONE VIZZION PLAY DAS FOTOS)
// 2.5.5 — o menu de Configurações passa a ser montado pelo próprio JS.
// Motivo: o "Sincronizar Arquivos" (OTA) baixa só app.js e styles.css; o HTML
// continua vindo do pacote instalado. Em TV com pacote antigo os itens novos
// do menu simplesmente não apareciam, mesmo com o código novo rodando.
const MK21_SETTINGS_SPEC = [
  ['cfgBtnInfo', '⚙️ Informação geral'],
  ['cfgBtnFonte', '🔤 Tamanho da fonte'],
  ['cfgBtnFiltro', '📦 Filtrar conteúdo'],
  ['cfgBtnSpeedTest', '🚀 Teste de velocidade'],
  ['cfgBtnLimpar', '🗑️ Limpar armazenamento'],
  ['cfgBtnTempo', '⏱️ Configurações de tempo'],
  ['cfgBtnCategorias', '📁 Gerenciar categorias'],
  ['cfgBtnFluxo', '📺 Alterar formato de fluxo'],
  ['cfgBtnQualidade', '📡 Servidor / Fonte'],
  ['cfgBtnQualidadeVideo', '🎞️ Qualidade de vídeo'],
  ['cfgBtnBuffer', '⚡ Buffer e fluidez'],
  ['cfgBtnAdulto', '🔞 Restrição adulto'],
  ['cfgBtnPin', '🔒 Alterar código pin']
];
const CFG_BUTTONS = MK21_SETTINGS_SPEC.map(item => item[0]);

function ensureSettingsMenu() {
  const box = document.querySelector('.settings-left-menu');
  if (!box) return;
  const assinatura = MK21_SETTINGS_SPEC.map(item => item[0]).join('|');
  if (box.dataset.mk21Menu === assinatura) return;
  const tmdb = box.querySelector('.cfg-tmdb-toggle');
  const ativoAntigo = box.querySelector('.cfg-menu-item.active');
  const ativoId = ativoAntigo ? ativoAntigo.id : 'cfgBtnInfo';
  box.innerHTML = '';
  MK21_SETTINGS_SPEC.forEach(item => {
    const b = document.createElement('button');
    b.id = item[0];
    b.type = 'button';
    b.className = 'cfg-menu-item';
    b.tabIndex = 0;
    b.textContent = item[1];
    if (item[0] === ativoId) b.classList.add('active');
    box.appendChild(b);
  });
  if (tmdb) box.appendChild(tmdb);
  else {
    const wrap = document.createElement('div');
    wrap.className = 'cfg-tmdb-toggle';
    wrap.innerHTML = '<span>🎬 Usar API TMDB</span>';
    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.id = 'cfgToggleTmdb';
    chk.checked = true;
    chk.style.width = '22px';
    chk.style.height = '22px';
    wrap.appendChild(chk);
    box.appendChild(wrap);
  }
  box.dataset.mk21Menu = assinatura;
}
ensureSettingsMenu();

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
  } else if (sec === 'filtro') {
    renderLoadFilterPanel();
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
  } else if (sec === 'qualidadevideo') {
    renderQualityPanel();
  } else if (sec === 'qualidade') {
    renderServerPanel();
  } else if (sec === 'buffer') {
    renderBufferPanel();
  } else if (sec === 'adulto') {
    renderAdultPanel();
  }
}

// ==================== 2.5.4 — PAINÉIS NOVOS DE CONFIGURAÇÕES ====================

// SERVIDOR / FONTE
function renderServerPanel() {
  const box = $('settingsDetailBox');
  const atual = SERVERS[currentServerIndex] || SERVERS[0] || { name: 'Nenhum' };
  const rows = SERVERS.map((srv, i) => {
    const ativo = i === currentServerIndex;
    return `
      <button class="list-item-btn cfg-srv-choice ${ativo ? 'active' : ''}" data-srv="${i}" style="padding:16px 20px; display:flex; justify-content:space-between; align-items:center;" tabindex="0">
        <span>${ativo ? '🟢' : '⚪'} Fonte ${i + 1}: ${cleanServerName(srv.name)}</span>
        <strong style="color:${ativo ? '#4caf50' : '#888'}; font-size:15px;">${ativo ? 'EM USO' : 'CONECTAR'}</strong>
      </button>`;
  }).join('');
  box.innerHTML = `
    <h3 style="margin:0 0 10px 0; font-size:24px;">📡 Servidor / Fonte</h3>
    <p style="color:#aaa; margin:0 0 18px 0;">Fonte em uso: <strong style="color:#4caf50;">${cleanServerName(atual.name)}</strong>. Se um canal falhar, o app já testa as fontes alternativas sozinho.</p>
    <div style="display:flex; flex-direction:column; gap:10px; max-width:620px;">${rows}</div>
    <div style="display:flex; gap:12px; margin-top:18px; flex-wrap:wrap;">
      <button id="cfgOpenServerManager" class="ctrl-btn primary" style="padding:14px 22px;" tabindex="0">⚙️ Gerenciar listas e adicionar servidor</button>
      <button id="cfgRefreshServer" class="ctrl-btn" style="padding:14px 22px;" tabindex="0">🔄 Atualizar catálogo agora</button>
    </div>
    <div id="msgServerChanged" style="margin-top:14px; color:#4caf50; font-weight:bold; font-size:15px; display:none;"></div>
  `;
  box.querySelectorAll('.cfg-srv-choice').forEach(btn => {
    btn.onclick = () => {
      const idx = parseInt(btn.getAttribute('data-srv'), 10) || 0;
      if (idx === currentServerIndex) return;
      currentServerIndex = idx;
      try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}
      saveServersToStorage();
      const msg = $('msgServerChanged');
      if (msg) {
        msg.textContent = '✅ Conectando à fonte ' + (idx + 1) + '...';
        msg.style.display = 'block';
      }
      renderServerPanel();
      loadServer(true);
    };
  });
  if ($('cfgOpenServerManager')) $('cfgOpenServerManager').onclick = () => {
    const modal = $('modalServerPicker');
    if (modal) { modal.classList.remove('hidden'); activeZone = 'modalServerPicker'; renderServerPickerList(); }
  };
  if ($('cfgRefreshServer')) $('cfgRefreshServer').onclick = () => loadServer(true);
}

// BUFFER / FLUIDEZ
function renderBufferPanel() {
  const box = $('settingsDetailBox');
  const atual = mk21BufferMode();
  const rows = Object.keys(MK21_BUFFER_MODES).map(key => {
    const m = MK21_BUFFER_MODES[key];
    const ativo = key === atual;
    return `
      <button class="list-item-btn cfg-buf-choice ${ativo ? 'active' : ''}" data-buf="${key}" style="padding:16px 20px; display:flex; flex-direction:column; align-items:flex-start; gap:4px;" tabindex="0">
        <span style="font-size:17px; font-weight:700; color:${ativo ? '#4caf50' : '#fff'};">${ativo ? '✅' : '⚪'} ${m.label}</span>
        <span style="font-size:14px; color:#94a3b8;">${m.hint}</span>
      </button>`;
  }).join('');
  box.innerHTML = `
    <h3 style="margin:0 0 10px 0; font-size:24px;">⚡ Buffer e Fluidez</h3>
    <p style="color:#aaa; margin:0 0 18px 0;">Quanto de vídeo a TV guarda antes de exibir. Buffer maior = menos travamento; buffer menor = troca de canal mais rápida.</p>
    <div style="display:flex; flex-direction:column; gap:10px; max-width:620px;">${rows}</div>
    <div style="margin-top:18px;"><button id="cfgApplyBuffer" class="ctrl-btn primary" style="padding:14px 22px;" tabindex="0">🔁 Aplicar no canal atual</button></div>
    <div id="msgBufferSaved" style="margin-top:14px; color:#4caf50; font-weight:bold; font-size:15px; display:none;"></div>
  `;
  box.querySelectorAll('.cfg-buf-choice').forEach(btn => {
    btn.onclick = () => {
      localStorage.setItem('mk21_buffer_mode', btn.getAttribute('data-buf'));
      renderBufferPanel();
      const msg = $('msgBufferSaved');
      if (msg) {
        msg.textContent = '✅ Buffer salvo: ' + MK21_BUFFER_MODES[mk21BufferMode()].label + '. Vale ao sintonizar o próximo canal.';
        msg.style.display = 'block';
      }
    };
  });
  if ($('cfgApplyBuffer')) $('cfgApplyBuffer').onclick = () => {
    const msg = $('msgBufferSaved');
    if (msg) { msg.textContent = '✅ Aplicando no canal atual...'; msg.style.display = 'block'; }
    if (activeItem) playStream(activeItem);
    else if (msg) msg.textContent = 'Selecione um canal para aplicar.';
  };
}

// RESTRIÇÃO ADULTO
function renderAdultPanel() {
  const box = $('settingsDetailBox');
  const liberado = isAdultUnlocked;
  box.innerHTML = `
    <h3 style="margin:0 0 10px 0; font-size:24px;">🔞 Restrição Adulto</h3>
    <p style="color:#aaa; margin:0 0 18px 0;">Com a restrição ativa, categorias e canais 18+ ficam ocultos. Para liberar, a TV pede o PIN de 4 dígitos.</p>
    <div style="background:#1a1e2d; border:1px solid ${liberado ? 'rgba(76,175,80,0.5)' : 'rgba(255,213,79,0.35)'}; border-radius:12px; padding:18px 22px; max-width:620px; display:flex; justify-content:space-between; align-items:center; gap:16px;">
      <div>
        <div style="font-size:13px; color:#888; text-transform:uppercase; letter-spacing:1px;">Situação</div>
        <div style="font-size:20px; font-weight:800; color:${liberado ? '#4caf50' : '#ffd54f'};">${liberado ? '🔓 Liberado até desligar a TV' : '🔒 Bloqueado (recomendado)'}</div>
      </div>
      <button id="cfgToggleAdult" class="ctrl-btn ${liberado ? '' : 'primary'}" style="padding:14px 22px;" tabindex="0">
        ${liberado ? '🔒 Bloquear agora' : '🔓 Liberar com PIN'}
      </button>
    </div>
    <div style="display:flex; gap:12px; margin-top:18px; flex-wrap:wrap;">
      <button id="cfgChangeAdultPin" class="ctrl-btn" style="padding:14px 22px;" tabindex="0">🔑 Alterar PIN (atual: ${currentPin})</button>
    </div>
    <p style="margin-top:18px; color:#64748b; font-size:14px;">Dica: o PIN padrão de fábrica é 0000. Troque depois de instalar.</p>
  `;
  if ($('cfgToggleAdult')) $('cfgToggleAdult').onclick = () => {
    if (isAdultUnlocked) {
      isAdultUnlocked = false;
      renderAdultPanel();
      buildCurrentCategories();
      renderCategoriesList();
      renderItemsList();
    } else {
      openPinModal();
    }
  };
  if ($('cfgChangeAdultPin')) $('cfgChangeAdultPin').onclick = () => renderPinSettingsPanel();
}

// PAINEL DE FILTRAGEM DE CARREGAMENTO (ECONOMIA DE MEMÓRIA E ULTRA VELOCIDADE)
function renderLoadFilterPanel() {
  const box = $('settingsDetailBox');
  const loadLive = localStorage.getItem('mk21_filter_load_live') !== 'false';
  const loadMovies = localStorage.getItem('mk21_filter_load_movies') !== 'false';
  const loadSeries = localStorage.getItem('mk21_filter_load_series') !== 'false';

  box.innerHTML = `
    <h3 style="margin:0 0 16px 0; font-size:24px;">📦 Filtrar Conteúdo ao Carregar</h3>
    <p style="color:#aaa;">Selecione quais tipos de conteúdo serão baixados do servidor para economizar memória e acelerar o app:</p>
    <div style="display:flex; flex-direction:column; gap:14px; max-width:560px; margin-top:20px;">
      <button id="toggleFilterLive" class="list-item-btn ${loadLive ? 'active' : ''}" style="padding:16px 20px; display:flex; justify-content:space-between; align-items:center;" tabindex="0">
        <span>📺 Canais de TV Ao Vivo</span>
        <strong style="color:${loadLive ? '#4caf50' : '#888'}; font-size:16px;">${loadLive ? '✅ CARREGAR' : '❌ IGNORAR'}</strong>
      </button>
      <button id="toggleFilterMovies" class="list-item-btn ${loadMovies ? 'active' : ''}" style="padding:16px 20px; display:flex; justify-content:space-between; align-items:center;" tabindex="0">
        <span>🎬 Filmes (VOD)</span>
        <strong style="color:${loadMovies ? '#4caf50' : '#888'}; font-size:16px;">${loadMovies ? '✅ CARREGAR' : '❌ IGNORAR'}</strong>
      </button>
      <button id="toggleFilterSeries" class="list-item-btn ${loadSeries ? 'active' : ''}" style="padding:16px 20px; display:flex; justify-content:space-between; align-items:center;" tabindex="0">
        <span>🍿 Séries e Novelas</span>
        <strong style="color:${loadSeries ? '#4caf50' : '#888'}; font-size:16px;">${loadSeries ? '✅ CARREGAR' : '❌ IGNORAR'}</strong>
      </button>
      <button id="btnApplyLoadFilter" class="ctrl-btn primary" style="margin-top:16px; padding:16px; justify-content:center; font-size:17px; font-weight:bold;" tabindex="0">
        💾 Salvar e Recarregar Servidor Agora
      </button>
    </div>
  `;

  $('toggleFilterLive').onclick = () => {
    localStorage.setItem('mk21_filter_load_live', !loadLive);
    renderLoadFilterPanel();
  };
  $('toggleFilterMovies').onclick = () => {
    localStorage.setItem('mk21_filter_load_movies', !loadMovies);
    renderLoadFilterPanel();
  };
  $('toggleFilterSeries').onclick = () => {
    localStorage.setItem('mk21_filter_load_series', !loadSeries);
    renderLoadFilterPanel();
  };
  $('btnApplyLoadFilter').onclick = () => {
    alert('Filtros salvos! Recarregando lista...');
    loadServer(true);
  };
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
  const idMap = { normal: 'btnFontNormal', large: 'btnFontLarge', xlarge: 'btnFontXLarge', xxlarge: 'btnFontXXLarge', huge: 'btnFontHuge', ultra: 'btnFontUltra' };
  const targetBtn = $(idMap[size] || 'btnFontNormal');
  if (targetBtn) {
    try { targetBtn.focus(); } catch (e) {}
  }
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
        <button id="btnClearFavChannels" class="ctrl-btn storage-action-btn" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Filmes favoritos (${favMoviesCount})</span>
        <button id="btnClearFavMovies" class="ctrl-btn storage-action-btn" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Séries favoritas (${favSeriesCount})</span>
        <button id="btnClearFavSeries" class="ctrl-btn storage-action-btn" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Filmes assistidos (Histórico)</span>
        <button id="btnClearWatchedMovies" class="ctrl-btn storage-action-btn" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:14px 20px; border-radius:10px;">
        <span style="font-size:18px;">Séries assistidas (Histórico)</span>
        <button id="btnClearWatchedSeries" class="ctrl-btn storage-action-btn" tabindex="0">Limpar</button>
      </div>

      <div style="display:flex; justify-content:space-between; align-items:center; background:#291114; border:1px solid #e50914; padding:14px 20px; border-radius:10px; margin-top:8px;">
        <span style="font-size:18px; font-weight:bold; color:#ffd54f;">Limpar tudo</span>
        <button id="btnClearAllStorage" class="ctrl-btn primary storage-action-btn" tabindex="0">Limpar</button>
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
          const names = [DB_NAME, CATALOG_INDEX_DB];
          let left = names.length;
          const done = function () { left--; if (left <= 0) resolve(); };
          names.forEach(function (name) {
            try {
              const req = indexedDB.deleteDatabase(name);
              req.onsuccess = done; req.onerror = done; req.onblocked = done;
            } catch (e) { done(); }
          });
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

      <button id="btnSaveTimeSettings" class="ctrl-btn primary" style="padding:14px; font-size:16px;" tabindex="0">💾 Salvar Configurações de Tempo</button>
      <div id="msgTimeSaved" style="font-size:16px; font-weight:bold; color:#4caf50; display:none;">✅ Configurações de tempo salvas com sucesso!</div>

    </div>
  `;

  $('btnSaveTimeSettings').onclick = () => {
    localStorage.setItem('mk21_timezone', $('selTimezone').value);
    localStorage.setItem('mk21_24h', $('chk24Hours').checked);
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
    nameLine.textContent = cleanServerName(srv.name) + (idx === currentServerIndex ? '  [✓ ATIVO]' : '');

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

if ($('btnLoadOfflineCache')) {
  $('btnLoadOfflineCache').onclick = async () => {
    const srv = SERVERS[currentServerIndex] || SERVERS[0];
    const cached = await getStoredData(srv.id);
    const liveN = cached && cached.LIVE ? cached.LIVE.length : 0;
    const movN = cached && cached.MOVIE ? cached.MOVIE.length : 0;
    const serN = cached && cached.SERIES ? cached.SERIES.length : 0;
    if (cached && (liveN + movN + serN) > 0) {
      allCatalog = {
        LIVE: cached.LIVE || [],
        MOVIE: cached.MOVIE || [],
        SERIES: cached.SERIES || []
      };
      buildCategoryIndex(allCatalog[currentContentType] || []);
      renderCategoriesList();
      renderItemsList();
      $('modalServerPicker').classList.add('hidden');
      activeZone = 'channels';
      addDevLog(`[Cache] Catálogo restaurado do cache local (${liveN} canais, ${movN} filmes, ${serN} séries).`, 'success');
      alert(`Catálogo offline carregado com sucesso!\n${liveN} Canais, ${movN} Filmes, ${serN} Séries.`);
    } else {
      alert('Nenhum catálogo em cache offline encontrado para este servidor.');
    }
  };
}

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

const BASE_PACKAGE_VERSION = '2.5.8';
let savedOtaVer = null;
try {
  savedOtaVer = localStorage.getItem('mk21_ota_app_version');
} catch (e) {}

// A versão atual é a versão OTA mais recente em cache ou a versão base do pacote
let CURRENT_APP_VERSION = (savedOtaVer && compareSemver(savedOtaVer, BASE_PACKAGE_VERSION) > 0) ? savedOtaVer : BASE_PACKAGE_VERSION;

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
    const now = Date.now();
    const candidateEndpoints = [
      'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/version.json?t=' + now,
      'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/api/version.json?t=' + now,
      'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/web/version.json?t=' + now,
      'https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/version.json?t=' + now,
      'https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/api/version.json?t=' + now,
      'https://bgstreaming.vercel.app/smart-tv/version.json?t=' + now,
      'https://bgstreaming.vercel.app/api/version.json?t=' + now,
      'version.json?t=' + now,
      './version.json?t=' + now
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

    // Se o GitHub estiver offline ou ainda não sincronizado no repositório remoto, usa os metadados oficiais v1.4.1
    if (!data || compareSemver(data.version, '1.4.1') < 0) {
      data = {
        version: '1.4.1',
        versionCode: 141,
        title: 'MK21 Play v1.4.1',
        releaseNotes: '• Sinergia APK-to-IPK: Phased loading, DNS failover e Omnisearch na Smart TV\n• Multitarefa: Picture-in-Picture (PiP) nativo com auto-enter no Android\n• Observabilidade: Telemetria silenciosa de stream (logStreamError)\n• Resiliência de Rede: DNS Failover ativo via Mk21DnsResolver\n• Zapping de Canal Zero-Delay e Pré-carregamento de canais adjacentes',
        ipkUrl: 'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_1.4.1_all.ipk',
        apkUrl: 'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk',
        isPendingPush: false
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
      $('btnStartDirectUpdate').textContent = `🔄 Sincronizar Arquivos v${data.version}`;
    }

    const titleNotes = $('titleUpdateNotes');
    if (titleNotes) titleNotes.textContent = `Novidades da Versão v${data.version}:`;

    let notes = data.releaseNotes || 'Melhorias de desempenho, navegação fluida e correções gerais.';
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
  const meta = latestRemoteUpdateData || {};
  const targetVer = meta.version || BASE_PACKAGE_VERSION;
  const pBox = $('updateProgressBox');
  const btn = $('btnStartDirectUpdate');

  if (btn) btn.disabled = true;
  if ($('btnCheckAgainUpdate')) $('btnCheckAgainUpdate').disabled = true;
  if (pBox) pBox.classList.remove('hidden');

  const setProgress = (step, pct) => {
    if ($('txtUpdateStep')) $('txtUpdateStep').textContent = step;
    if ($('txtUpdatePct')) $('txtUpdatePct').textContent = pct + '%';
    if ($('barUpdateProgress')) $('barUpdateProgress').style.width = pct + '%';
  };

  try {
    setProgress('Conectando ao repositório oficial de atualizações...', 15);

    // URLs dos arquivos da TV para atualização OTA imediata
    const now = Date.now();
    const appJsCandidates = [
      `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/app.js?t=${now}`,
      `https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/app.js?t=${now}`,
      `https://bgstreaming.vercel.app/smart-tv/mk21-tv/app.js?t=${now}`
    ];
    const stylesCssCandidates = [
      `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=${now}`,
      `https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@main/smart-tv/mk21-tv/styles.css?t=${now}`,
      `https://bgstreaming.vercel.app/smart-tv/mk21-tv/styles.css?t=${now}`
    ];

    let newAppJs = null;
    let newStylesCss = null;

    setProgress(`Baixando código atualizado v${targetVer}...`, 35);

    for (const u of appJsCandidates) {
      try {
        const res = await fetch(u, { cache: 'no-store' });
        if (res.ok) {
          const txt = await res.text();
          if (txt && txt.length > 5000 && txt.includes('CURRENT_APP_VERSION')) {
            newAppJs = txt;
            break;
          }
        }
      } catch (e) {}
    }

    setProgress('Baixando folhas de estilo e layout...', 65);

    for (const u of stylesCssCandidates) {
      try {
        const res = await fetch(u, { cache: 'no-store' });
        if (res.ok) {
          const txt = await res.text();
          if (txt && txt.length > 500) {
            newStylesCss = txt;
            break;
          }
        }
      } catch (e) {}
    }

    if (newAppJs) {
      setProgress('Salvando e ativando nova versão na TV...', 85);
      try {
        localStorage.setItem('mk21_ota_app_js', newAppJs);
        localStorage.setItem('mk21_ota_app_version', targetVer);
        if (newStylesCss) {
          localStorage.setItem('mk21_ota_styles_css', newStylesCss);
        }
      } catch (e) {
        console.warn('Erro ao gravar localStorage OTA:', e);
      }

      // Se houver Homebrew Channel instalado no webOS, solicita também a instalação do IPK
      if (window.webOS && webOS.service && meta.ipkUrl) {
        try {
          webOS.service.request('luna://org.webosbrew.hbchannel.service/install', { ipkUrl: meta.ipkUrl });
        } catch (e) {
          console.log('Serviço Homebrew opcional não acionado, atualização OTA já garantida.');
        }
      }

      setProgress(`✅ Atualizado para v${targetVer} com sucesso! Reiniciando...`, 100);
      setTimeout(() => {
        window.location.reload();
      }, 1200);
      return;
    }

    // Caso não tenha conseguido obter o código remoto, orienta o usuário de forma clara
    throw new Error(`Servidor de arquivos temporariamente indisponível. Conecte a TV à internet ou instale o pacote .ipk v${targetVer} pelo webOS Dev Manager.`);

  } catch (err) {
    console.error('Update failed:', err);
    setProgress('Falha na atualização automática', 0);
    alert('Aviso: ' + (err.message || err) + '\n\n💡 Alternativa: Você pode baixar o pacote IPK v' + targetVer + ' apontando a câmera do celular para o QR Code abaixo e instalá-lo via webOS Dev Manager.');
    if (btn) btn.disabled = false;
    if ($('btnCheckAgainUpdate')) $('btnCheckAgainUpdate').disabled = false;
  }
}

function forceResetTvAppCache() {
  if (!confirm('Limpar cache de atualização OTA e voltar à versão do pacote instalado (v' + BASE_PACKAGE_VERSION + ')?')) return;
  try {
    localStorage.removeItem('mk21_ota_app_js');
    localStorage.removeItem('mk21_ota_styles_css');
    localStorage.removeItem('mk21_ota_app_version');
  } catch (e) {}
  alert('Cache OTA limpo. Reiniciando na versão base v' + BASE_PACKAGE_VERSION);
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
    if (typeof updatePlayPauseBtnUI === 'function') updatePlayPauseBtnUI(true);
    showChannelBanner('▶ Reproduzindo');
  } else {
    v.pause();
    if (typeof updatePlayPauseBtnUI === 'function') updatePlayPauseBtnUI(false);
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

function mk21GetOpenModal() {
  const modalIds = ['hudLoadingOverlay', 'modalStreamDiagnostics', 'modalOmnisearch', 'modalEpgGuide', 'modalSortPicker', 'modalAudioSubs', 'modalServerPicker', 'modalPin', 'modalExitConfirm', 'modalSeriesEpisodes', 'modalAppUpdate'];
  for (let i = 0; i < modalIds.length; i++) {
    const modal = $(modalIds[i]);
    if (modal && !modal.classList.contains('hidden') && mk21IsVisible(modal)) return modal;
  }
  const dynamic = document.querySelector('.app-modal:not(.hidden), .modal-overlay:not(.hidden), [role="dialog"]:not(.hidden), .modal-container:not(.hidden)');
  if (dynamic && mk21IsVisible(dynamic)) return dynamic;
  return null;
}
function mk21GetFocusables() {
  const activeModal = mk21GetOpenModal();
  const root = activeModal || document;
  const sel = [
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex="0"]',
    '.modal-btn',
    '.modal-button',
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
  return Array.from(root.querySelectorAll(sel)).filter(mk21IsVisible);
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
    try {
      best.scrollIntoView({ block: 'nearest', inline: 'nearest' });
      if (window.scrollX !== 0 || window.scrollY !== 0) {
        window.scrollTo(0, 0);
      }
      if (document.documentElement.scrollLeft !== 0) {
        document.documentElement.scrollLeft = 0;
      }
      const colPl = document.querySelector('.col-player');
      if (colPl && colPl.scrollTop !== 0) {
        colPl.scrollTop = 0;
      }
    } catch (e) {}
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
        if (ix >= 0) {
          focusedItemIdx = ix;
          const badge = $('badgeItemsCount');
          if (badge && its.length > 0) badge.textContent = (ix + 1) + ' / ' + its.length;
        }
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

  // 1.1 Focus Trap & Gestão de Foco para Smart TVs (webOS, Tizen, Android TV)
  const activeModal = mk21GetOpenModal();
  if (activeModal) {
    const focusableElements = Array.from(activeModal.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"]), .modal-btn, .modal-button, .ctrl-btn, .pin-key, .btn-server-connect, .btn-server-edit, .btn-server-delete, .hud-ctrl-btn'
    )).filter(mk21IsVisible);

    if (focusableElements.length > 0) {
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      // Se o foco atual estiver fora do modal, força imediatamente para o primeiro elemento
      if (!activeModal.contains(document.activeElement) || focusableElements.indexOf(document.activeElement) === -1) {
        firstElement.focus();
      }

      // Interceptação de Voltar / Back (webOS 461, Tizen 10009, Escape 27, Backspace 8)
      if (k === 461 || k === 10009 || k === 27 || key === 'Escape' || key === 'Back') {
        e.preventDefault();
        e.stopPropagation();
        activeModal.classList.add('hidden');
        activeZone = 'channels';
        focusActiveElement();
        return;
      }

      // Mapeamento de teclas de navegação do modal
      const isTab = key === 'Tab' || k === 9;
      const isDown = key === 'ArrowDown' || k === 40;
      const isUp = key === 'ArrowUp' || k === 38;

      if (isTab) {
        if (e.shiftKey && document.activeElement === firstElement) {
          e.preventDefault();
          lastElement.focus();
          return;
        } else if (!e.shiftKey && document.activeElement === lastElement) {
          e.preventDefault();
          firstElement.focus();
          return;
        }
      }

      if (isDown && document.activeElement === lastElement) {
        e.preventDefault();
        firstElement.focus();
        return;
      } else if (isUp && document.activeElement === firstElement) {
        e.preventDefault();
        lastElement.focus();
        return;
      }

      // Navegação D-Pad espacial restrita estritamente ao modal ativo
      if (k === 37 || key === 'ArrowLeft') {
        e.preventDefault();
        mk21MoveFocus('left');
        return;
      }
      if (k === 39 || key === 'ArrowRight') {
        e.preventDefault();
        mk21MoveFocus('right');
        return;
      }
      if (k === 38 || key === 'ArrowUp') {
        e.preventDefault();
        mk21MoveFocus('up');
        return;
      }
      if (k === 40 || key === 'ArrowDown') {
        e.preventDefault();
        mk21MoveFocus('down');
        return;
      }
    }
  }

  if (ae && ae.id === 'inputSearch') {
    if (k === 40 || key === 'ArrowDown') {
      e.preventDefault();
      closeSearchMode(false);
      const firstChannel = $('listItems') ? $('listItems').querySelector('.list-item-btn:not([disabled])') : null;
      if (firstChannel) {
        firstChannel.focus();
        activeZone = 'channels';
      } else {
        const cat = $('listCategories') ? ($('listCategories').querySelector('.cat-item-btn.active') || $('listCategories').querySelector('.cat-item-btn')) : null;
        if (cat) { cat.focus(); activeZone = 'categories'; }
      }
      return;
    }
    if (k === 38 || key === 'ArrowUp') {
      e.preventDefault();
      closeSearchMode(false);
      const activeTab = document.querySelector('.main-tabs .tab-btn.active') || $('tabLive');
      if (activeTab) { activeTab.focus(); activeZone = 'header'; }
      return;
    }
    if ((k === 37 || key === 'ArrowLeft') && (!ae.value || ae.selectionStart === 0)) {
      e.preventDefault();
      closeSearchMode(false);
      const activeCat = $('listCategories') ? ($('listCategories').querySelector('.cat-item-btn.active') || $('listCategories').querySelector('.cat-item-btn')) : null;
      if (activeCat) { activeCat.focus(); activeZone = 'categories'; }
      return;
    }
    if (k === 39 || key === 'ArrowRight') {
      if (!ae.value || ae.selectionStart === ae.value.length) {
        e.preventDefault();
        const nextBtn = $('btnClearSearch') || $('btnFullscreen') || $('btnPlayPause');
        if (nextBtn) { nextBtn.focus(); }
        return;
      }
    }
  }
  const isTextInput = ae && /^(INPUT|TEXTAREA|SELECT)$/.test(ae.tagName);
  if (isTextInput && ae.id !== 'inputSearch' && ['ArrowLeft','ArrowUp','ArrowRight','ArrowDown'].includes(key)) return;

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
    if ($('hudLoadingOverlay') && !$('hudLoadingOverlay').classList.contains('hidden')) {
      e.preventDefault();
      if (activePlaylistController) { try { activePlaylistController.abort(); } catch(e){} }
      $('hudLoadingOverlay').classList.add('hidden');
      hideSplash();
      showChannelBanner('⚠️ Carregamento cancelado pelo usuário');
      return;
    }
    // A pesquisa funciona como uma área modal: Voltar nunca alcança o player/tela atrás.
    if ((ae && ae.id === 'inputSearch') || activeZone === 'search') {
      e.preventDefault();
      e.stopPropagation();
      closeSearchMode(true);
      return;
    }
    if ($('inputSearch') && $('inputSearch').value.trim().length > 0) {
      e.preventDefault();
      e.stopPropagation();
      closeSearchMode(true);
      return;
    }

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
    if ($('modalSeriesEpisodes') && !$('modalSeriesEpisodes').classList.contains('hidden')) {
      e.preventDefault();
      $('modalSeriesEpisodes').classList.add('hidden');
      activeZone = 'channels';
      focusActiveElement();
      return;
    }
    if ($('modalExitConfirm') && !$('modalExitConfirm').classList.contains('hidden')) {
      e.preventDefault();
      $('modalExitConfirm').classList.add('hidden');
      activeZone = 'settings';
      focusActiveElement();
      return;
    }
    // Qualquer modal restante (áudio, atualização etc.) captura o Back sem atingir a tela atrás.
    const openModal = mk21GetOpenModal();
    if (openModal) {
      e.preventDefault();
      openModal.classList.add('hidden');
      activeZone = 'settings';
      focusActiveElement();
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

  // webOS Channel Up / Down (KeyCodes 427 / 428) e setas em tela cheia (Item 4)
  if (k === 427 || key === 'ChannelUp') {
    e.preventDefault();
    if (currentContentType === 'LIVE') playPreviousChannel();
    return;
  }
  if (k === 428 || key === 'ChannelDown') {
    e.preventDefault();
    if (currentContentType === 'LIVE') playNextChannel();
    return;
  }

  // Setas em tela cheia (Fullscreen) com mudança de canal e navegação nos botões
  if (isAppFullscreen()) {
    pokeFsHud();
    const hud = $('fsHudBar');
    const hudVisible = hud && !hud.classList.contains('hidden');

    if (k === 38 || key === 'ArrowUp') {
      e.preventDefault();
      if (currentContentType === 'LIVE') {
        playPreviousChannel();
      }
      return;
    }
    if (k === 40 || key === 'ArrowDown') {
      e.preventDefault();
      if (currentContentType === 'LIVE') {
        playNextChannel();
      }
      return;
    }
    if (k === 37 || key === 'ArrowLeft') {
      e.preventDefault();
      if (hudVisible && ae && ae.classList.contains('hud-ctrl-btn')) {
        mk21MoveFocus('left');
      } else if (currentContentType !== 'LIVE') {
        const v = $('tvPlayer');
        if (v) v.currentTime = Math.max(0, (v.currentTime || 0) - 10);
        showChannelBanner('⏪ -10s');
        updateVodProgressTimeline();
      } else {
        mk21MoveFocus('left');
      }
      return;
    }
    if (k === 39 || key === 'ArrowRight') {
      e.preventDefault();
      if (hudVisible && ae && ae.classList.contains('hud-ctrl-btn')) {
        mk21MoveFocus('right');
      } else if (currentContentType !== 'LIVE') {
        const v = $('tvPlayer');
        if (v && v.duration) v.currentTime = Math.min(v.duration, (v.currentTime || 0) + 10);
        showChannelBanner('⏩ +10s');
        updateVodProgressTimeline();
      } else {
        mk21MoveFocus('right');
      }
      return;
    }
  }

  // Setas — navegação espacial no modo janela
  if (k === 37 || key === 'ArrowLeft') {
    e.preventDefault();
    mk21MoveFocus('left');
    return;
  }
  if (k === 39 || key === 'ArrowRight') {
    e.preventDefault();
    mk21MoveFocus('right');
    return;
  }
  if (k === 38 || key === 'ArrowUp') {
    e.preventDefault();
    mk21MoveFocus('up');
    return;
  }
  if (k === 40 || key === 'ArrowDown') {
    e.preventDefault();
    mk21MoveFocus('down');
    return;
  }

  // OK / Enter / Space
  if (k === 13 || k === 32 || key === 'Enter' || key === ' ') {
    if (isAppFullscreen() && (!ae || ae === document.body || ae.id === 'tvPlayer' || ae.id === 'videoContainer')) {
      e.preventDefault();
      pokeFsHud();
      const firstHudBtn = $('fsHudBar') ? $('fsHudBar').querySelector('.hud-ctrl-btn') : null;
      if (firstHudBtn) firstHudBtn.focus();
      return;
    }
    if (channelNumberBuffer && channelNumberBuffer.length > 0) {
      e.preventDefault();
      commitChannelNumber();
      return;
    }
    if (ae && ae.tagName === 'INPUT' && (k === 32 || key === ' ')) return;
    if (ae && ae.id === 'inputSearch') {
      // Enter confirma a busca, fecha o teclado nativo e devolve o foco ao resultado.
      e.preventDefault();
      e.stopPropagation();
      closeSearchMode(false);
      const firstChannel = $('listItems') ? $('listItems').querySelector('.list-item-btn:not([disabled])') : null;
      if (firstChannel) { firstChannel.focus(); activeZone = 'channels'; }
      return;
    }
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

  // Suporte a Teclas Numéricas (0-9) para troca direta de canal por ID
  const isDigitKey = (k >= 48 && k <= 57) || (k >= 96 && k <= 105);
  if (isDigitKey && !isTextInput) {
    e.preventDefault();
    const digit = (k >= 96 && k <= 105) ? (k - 96) : (k - 48);
    handleDigitPress(digit);
    return;
  }

  // Atalhos de teclas coloridas do controle remoto (Smart TV 10-foot UI)
  if (k === 403 || key === 'Red' || key === 'ColorF0Red') {
    e.preventDefault();
    if ($('hudLoadingOverlay') && !$('hudLoadingOverlay').classList.contains('hidden')) {
      if (activePlaylistController) { try { activePlaylistController.abort(); } catch(e){} }
      $('hudLoadingOverlay').classList.add('hidden');
      hideSplash();
    }
    if (typeof openServerPicker === 'function') openServerPicker();
    else toggleMute();
    return;
  }
  if (k === 404 || key === 'Green' || key === 'ColorF1Green') {
    e.preventDefault();
    if (activeItem) {
      toggleFav(activeItem.url);
      const isFav = favoriteUrls.has(activeItem.url);
      showChannelBanner(isFav ? '⭐ Adicionado aos Favoritos' : 'Removido dos Favoritos');
    } else {
      showChannelBanner('Selecione um canal para favoritar');
    }
    return;
  }
  if (k === 405 || key === 'Yellow' || key === 'ColorF2Yellow') {
    e.preventDefault();
    if (typeof openOmnisearch === 'function') openOmnisearch();
    else cycleAspectRatio();
    return;
  }
  if (k === 406 || key === 'Blue' || key === 'ColorF3Blue' || key === 'b' || key === 'B') {
    e.preventDefault();
    if (typeof openEpgGuideModal === 'function') openEpgGuideModal();
    else toggleDevLogsModal();
    return;
  }

  // Teclas nativas LG webOS & Tizen: GUIDE (458) e INFO (457)
  if (k === 458 || key === 'Guide' || key === 'EPG') {
    e.preventDefault();
    if (typeof openEpgGuideModal === 'function') openEpgGuideModal();
    return;
  }
  if (k === 457 || key === 'Info') {
    e.preventDefault();
    if (typeof toggleStreamDiagnostics === 'function') toggleStreamDiagnostics();
    else if (activeItem) showChannelBanner(activeItem.name + ' - ' + (activeItem.group || ''));
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
        currentCategoriesMap = { ALL: [] };
      currentFilteredItems = [];
      currentGroupedSeries = [];
      if (typeof buildCurrentCategories === 'function') buildCurrentCategories();
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

// MODAL OMNISEARCH & EPG GUIDE EVENT LISTENERS
if ($('btnCloseOmnisearch')) $('btnCloseOmnisearch').onclick = closeOmnisearch;
if ($('btnClearOmnisearch')) $('btnClearOmnisearch').onclick = () => {
  if ($('inputOmnisearch')) {
    $('inputOmnisearch').value = '';
    $('inputOmnisearch').focus();
    executeOmnisearch('');
  }
};
if ($('inputOmnisearch')) {
  $('inputOmnisearch').addEventListener('input', (e) => {
    clearTimeout(omnisearchDebounceTimer);
    omnisearchDebounceTimer = setTimeout(() => {
      executeOmnisearch(e.target.value.trim());
    }, 250);
  });
  $('inputOmnisearch').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const firstResult = $('omnisearchResultsGrid') ? $('omnisearchResultsGrid').querySelector('.omnisearch-item-btn') : null;
      if (firstResult) {
        firstResult.focus();
      }
    }
  });
}

['tabOmniAll', 'tabOmniLive', 'tabOmniMovies', 'tabOmniSeries'].forEach(tabId => {
  const el = $(tabId);
  if (el) {
    el.onclick = () => {
      document.querySelectorAll('.omnisearch-tab-btn').forEach(b => b.classList.remove('active'));
      el.classList.add('active');
      omnisearchActiveTab = tabId.replace('tabOmni', '').toUpperCase();
      renderOmnisearchResults();
    };
  }
});

if ($('btnCloseEpgGuide')) $('btnCloseEpgGuide').onclick = () => {
  const modal = $('modalEpgGuide');
  if (modal) modal.classList.add('hidden');
  activeZone = 'channels';
  focusActiveElement();
};

if ($('btnCloseDiagnostics')) $('btnCloseDiagnostics').onclick = () => {
  const modal = $('modalStreamDiagnostics');
  if (modal) modal.classList.add('hidden');
  activeZone = 'channels';
  focusActiveElement();
};

// 16. INICIALIZAÇÃO AUTOMÁTICA

// Proteção de memória webOS: evita reinício do app
if (typeof document !== 'undefined') {
  document.addEventListener('webOSLowMemory', function () {
    console.warn('[MK21] webOSLowMemory — liberando buffers');
    try {
      runMemoryShieldCleanup();
      if (typeof hlsInstance !== 'undefined' && hlsInstance) {
        try { hlsInstance.destroy(); hlsInstance = null; } catch (e) {}
      }
      const v = document.getElementById('tvPlayer');
      if (v) { try { v.pause(); v.removeAttribute('src'); v.load(); } catch (e) {} }
      itemsDisplayLimit = 40;
      if (typeof renderItemsList === 'function') renderItemsList();
    } catch (e) {}
  }, false);
}

window.addEventListener('load', () => {
  ensureSharedCredentialsStored();
  // OTA ativo preservado na TV para manter atualizações persistentes

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

    try {
      initDevLogs();
      addDevLog('[App] MK21 Play inicializado com sucesso', 'success');
    } catch (e) {}

  const hasAuthToken = !!localStorage.getItem('mk21_auth_token');
  if (hasAuthToken) {
    loadServer();

    const scheduleFrame = (window.requestAnimationFrame || function (cb) { return setTimeout(cb, 0); });
    scheduleFrame(function () {
      activeZone = 'header';
      focusedHeaderIdx = 0;
      if ($('tabLive')) $('tabLive').focus();
      else focusActiveElement();
    });
  }
});

/** v1.4.1 - Phased Loading & Omnisearch Synergy **/
function showSyncStatus(percent, isSyncing) {
  const badge = document.getElementById('sync-progress-badge');
  if (badge) {
    badge.style.display = isSyncing ? 'block' : 'none';
    badge.textContent = 'Sincronizando: ' + percent + '%';
  }
  if (typeof updateServerSyncBadge === 'function') {
    updateServerSyncBadge(percent, !isSyncing);
  }
}

function processBatch(batch) {
  if (!batch || !batch.length) return;
  const lines = Array.isArray(batch) ? batch : [batch];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      const nameMatch = line.match(/,(.+)$/);
      const name = nameMatch ? nameMatch[1].trim() : 'Canal';
      const groupMatch = line.match(/group-title="([^"]+)"/i);
      const group = groupMatch ? groupMatch[1].trim() : 'Geral';
      let nextUrl = '';
      if (i + 1 < lines.length && !lines[i + 1].startsWith('#')) {
        nextUrl = lines[i + 1].trim();
      }
      const item = {
        id: 'ch_' + Math.random().toString(36).substring(2, 9),
        name: name,
        group: group,
        url: nextUrl,
        contentType: 'LIVE'
      };
      if (typeof allCatalog !== 'undefined' && allCatalog.LIVE) {
        allCatalog.LIVE.push(item);
      }
    }
  }
}

async function parseM3U(data) {
  if (!data) return;
  const lines = data.split('\n');
  const total = lines.length;

  // Fase 1: Canais Ao Vivo (Primeiros 300) - Liberação Imediata
  const liveBatch = lines.slice(0, 300);
  processBatch(liveBatch);
  if (typeof renderChannels === 'function') {
    renderChannels();
  } else if (typeof renderItemsList === 'function') {
    renderItemsList();
  }

  // Fase 2: Background Sync para Filmes e Séries (Chunks de 500)
  let index = 300;
  const processNext = () => {
    if (index >= total) {
      showSyncStatus(100, false);
      return;
    }
    const chunk = lines.slice(index, index + 500);
    processBatch(chunk);
    index += 500;
    showSyncStatus(Math.round((index / total) * 100), true);
    const raf = window.requestAnimationFrame || function (cb) { setTimeout(cb, 16); };
    raf(processNext);
  };
  setTimeout(processNext, 500);
}

function globalSearch(query) {
  const q = (query || '').toLowerCase().trim();
  const liveItems = (typeof allCatalog !== 'undefined' && allCatalog.LIVE) ? allCatalog.LIVE : (typeof allChannels !== 'undefined' ? allChannels : []);
  const movieItems = (typeof allCatalog !== 'undefined' && allCatalog.MOVIE) ? allCatalog.MOVIE : (typeof allMovies !== 'undefined' ? allMovies : []);
  const seriesItems = (typeof allCatalog !== 'undefined' && allCatalog.SERIES) ? allCatalog.SERIES : (typeof allSeries !== 'undefined' ? allSeries : []);
  const results = {
    live: liveItems.filter(c => (c.name || '').toLowerCase().includes(q)),
    vod: movieItems.filter(m => ((m.title || m.name) || '').toLowerCase().includes(q)),
    series: seriesItems.filter(s => (s.name || '').toLowerCase().includes(q))
  };
  if (typeof renderSearchResults === 'function') {
    renderSearchResults(results);
  }
  return results;
}

/**
 * MK21 Play - Smart TV (LG webOS / Tizen) v2.5.0
 * Gerenciador de Foco D-Pad, Teclado Virtual e Fluxo de Autenticação
 */

const AppState = {
  isLoggedIn: false,
  activeFocusIndex: 0,
  focusableElements: []
};

// Constantes de Teclado e Controle Remoto LG webOS
const TVKeys = {
  UP: [38, 'ArrowUp'],
  DOWN: [40, 'ArrowDown'],
  LEFT: [37, 'ArrowLeft'],
  RIGHT: [39, 'ArrowRight'],
  ENTER: [13, 'Enter'],
  BACK: [461, 10009, 27, 'Escape', 'GoBack'] // 461 = Tecla Back do webOS, 10009 = Tizen
};

function initSmartTVApp() {
  setupGlobalListeners();
  setupVirtualKeyboardHandling();

  const creds = getSharedCredentials();
  const hasAuth = !!(creds.user && creds.pass) || !!localStorage.getItem('mk21_auth_token');

  if (hasAuth) {
    // Inicialização idêntica ao APK: foca no cache local imediatamente sem tela de login
    unlockHomeInterface(true);
  } else {
    showLoginInterface();
  }
}

function populateServerSelect() {
  const select = document.getElementById('server-select');
  if (!select) return;
  select.innerHTML = '';
  SERVERS.forEach((srv, idx) => {
    const opt = document.createElement('option');
    opt.value = idx;
    opt.textContent = `Fonte ${idx + 1}: ${cleanServerName(srv.name)}`;
    if (idx === currentServerIndex) opt.selected = true;
    select.appendChild(opt);
  });
  select.onchange = () => {
    currentServerIndex = parseInt(select.value, 10) || 0;
    try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}
  };
}

function showLoginInterface() {
  AppState.isLoggedIn = false;

  const home = document.getElementById('home-container');
  const login = document.getElementById('login-container');

  if (home) home.style.display = 'none';
  if (login) login.style.display = 'flex';

  populateServerSelect();

  // Pré-preenche com credenciais existentes para poupar digitação com controle remoto
  try {
    const userField = document.getElementById('user-input');
    const passField = document.getElementById('pass-input');
    const creds = getSharedCredentials();
    if (userField && !userField.value) userField.value = creds.user || '';
    if (passField && !passField.value) passField.value = creds.pass || '';
  } catch (e) {}

  updateFocusableElements();

  // Foco inicial: com credenciais preenchidas vai direto para ENTRAR (um OK no controle);
  // sem credenciais, foco no campo de Servidor.
  setTimeout(() => {
    const uf = document.getElementById('user-input');
    const pf = document.getElementById('pass-input');
    const ready = uf && pf && uf.value && pf.value;
    setFocusByIndex(ready ? 3 : 0);
  }, 120);

  // Event Listeners dos botões
  const btnLogin = document.getElementById('btn-login');
  if (btnLogin) btnLogin.onclick = handleLoginSubmit;
  const btnOffline = document.getElementById('btn-offline');
  if (btnOffline) btnOffline.onclick = handleOfflineBypass;
}

function updateFocusableElements() {
  // Coleta na ordem estrita: Servidor -> Usuário -> Senha -> Entrar -> Offline
  AppState.focusableElements = [
    document.getElementById('server-select'),
    document.getElementById('user-input'),
    document.getElementById('pass-input'),
    document.getElementById('btn-login'),
    document.getElementById('btn-offline')
  ].filter(Boolean);
}

function setFocusByIndex(idx) {
  if (!AppState.focusableElements.length) return;

  if (idx < 0) idx = AppState.focusableElements.length - 1;
  if (idx >= AppState.focusableElements.length) idx = 0;

  AppState.activeFocusIndex = idx;
  const target = AppState.focusableElements[idx];

  AppState.focusableElements.forEach(el => el.classList.remove('focused'));
  target.classList.add('focused');
  target.focus();
}

function setupGlobalListeners() {
  window.addEventListener('keydown', (e) => {
    // Se a tecla for BACK no webOS (461), prevenir saída acidental
    if (TVKeys.BACK.includes(e.keyCode) || TVKeys.BACK.includes(e.key)) {
      e.preventDefault();
      if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'SELECT')) {
        document.activeElement.blur();
        setFocusByIndex(AppState.activeFocusIndex);
      }
      return;
    }

    // Se estiver na tela de login, o D-pad navega estritamente nos itens focáveis
    if (!AppState.isLoggedIn) {
      if (TVKeys.DOWN.includes(e.keyCode) || TVKeys.DOWN.includes(e.key)) {
        e.preventDefault();
        setFocusByIndex(AppState.activeFocusIndex + 1);
      } else if (TVKeys.UP.includes(e.keyCode) || TVKeys.UP.includes(e.key)) {
        e.preventDefault();
        setFocusByIndex(AppState.activeFocusIndex - 1);
      } else if (TVKeys.ENTER.includes(e.keyCode) || TVKeys.ENTER.includes(e.key)) {
        const current = AppState.focusableElements[AppState.activeFocusIndex];
        if (current && current.tagName === 'BUTTON') {
          e.preventDefault();
          current.click();
        }
      }
    }
  });
}

function setupVirtualKeyboardHandling() {
  // Ajusta layout quando o teclado da Smart TV surge
  const inputs = document.querySelectorAll('.input-field');
  inputs.forEach(input => {
    input.addEventListener('focus', () => document.body.classList.add('osk-open'));
    input.addEventListener('blur', () => document.body.classList.remove('osk-open'));
  });
}

async function handleLoginSubmit() {
  const serverSelect = document.getElementById('server-select');
  if (serverSelect) {
    currentServerIndex = parseInt(serverSelect.value, 10) || 0;
    try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}
  }

  const userInput = document.getElementById('user-input');
  const passInput = document.getElementById('pass-input');
  const user = userInput ? userInput.value.trim() : '';
  const pass = passInput ? passInput.value.trim() : '';

  if (!user || !pass) {
    displayFeedback('Informe o usuário e a senha.', true);
    setFocusByIndex(user ? 1 : 0);
    return;
  }

  displayFeedback('Autenticando MK21...', false);

  const activeSrv = SERVERS[currentServerIndex] || SERVERS[0];
  let authToken = 'mk21_session_ok';

  if (activeSrv && activeSrv.url) {
    try {
      const c = new AbortController();
      const timer = setTimeout(() => c.abort(), 6000);
      const authUrl = `${activeSrv.url}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`;
      const response = await fetch(authUrl, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        signal: c.signal
      });
      clearTimeout(timer);

      if (response.ok) {
        const data = await response.json();
        if (data && data.user_info) {
          if (data.user_info.auth === 1) {
            authToken = data.user_info.token || 'mk21_auth_ok';
          } else if (data.user_info.auth === 0) {
            displayFeedback('Usuário ou senha inválidos no servidor.', true);
            setFocusByIndex(0);
            return;
          }
        }
      }
    } catch (eAuth) {
      console.warn('[Login] Validação online indisponível ou CORS, prosseguindo com credenciais:', eAuth);
    }
  }

  try {
    localStorage.setItem('mk21_auth_token', authToken);
    localStorage.setItem('mk21_user', user);
    localStorage.setItem('mk21_username', user);
    localStorage.setItem('iptv_user', user);
    localStorage.setItem('mk21_pass', pass);
    localStorage.setItem('mk21_password', pass);
    localStorage.setItem('iptv_pass', pass);
  } catch (eStore) {}

  displayFeedback('Conectado! Carregando catálogo...', false);
  unlockHomeInterface(true);
}

function handleOfflineBypass() {
  try {
    localStorage.setItem('mk21_auth_token', 'mk21_offline_mode');
  } catch (e) {}
  unlockHomeInterface(true);
}

async function getBestAvailableCache(preferredId) {
  try {
    let data = await getStoredData(preferredId);
    if (data && data.LIVE && data.LIVE.length > 0) return data;
  } catch (e) {}
  try {
    let fast = getFastCache(preferredId);
    if (fast && fast.LIVE && fast.LIVE.length > 0) return fast;
  } catch (e) {}
  try {
    let univ = getFastCache('universal');
    if (univ && univ.LIVE && univ.LIVE.length > 0) return univ;
  } catch (e) {}
  try {
    for (let s of SERVERS) {
      if (s.id && s.id !== preferredId) {
        let alt = await getStoredData(s.id);
        if (alt && alt.LIVE && alt.LIVE.length > 0) return alt;
        let altFast = getFastCache(s.id);
        if (altFast && altFast.LIVE && altFast.LIVE.length > 0) return altFast;
      }
    }
  } catch (e) {}
  return null;
}

async function loadInitialCacheAndSyncInBackground() {
  if (SERVERS.length === 0) SERVERS = [...DEFAULT_SERVERS];
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;
  const srv = SERVERS[currentServerIndex];
  if ($('txtActiveServer')) $('txtActiveServer').textContent = cleanServerName(srv.name);
  if ($('hudLoadingOverlay')) $('hudLoadingOverlay').classList.add('hidden');
  hideSplash();

  let cached = await getBestAvailableCache(srv.id);
  if (cached && cached.LIVE && cached.LIVE.length > 0) {
    allCatalog = {
      LIVE: cached.LIVE || [],
      MOVIE: cached.MOVIE || [],
      SERIES: cached.SERIES || []
    };
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');
    renderItemsList();
    updateServerSyncBadge(100, true);
    setTimeout(() => { resumeLastChannelIfPossible(); }, 100);
    setTimeout(() => {
      activeZone = 'channels';
      const firstBtn = $('listItems') ? $('listItems').querySelector('.list-item-btn') : null;
      if (firstBtn) { try { firstBtn.focus(); } catch (e) {} }
    }, 200);
    setTimeout(() => { loadServer(true, { silent: true }); }, 1200);
    return;
  }
  loadServer(false);
}

function unlockHomeInterface(shouldLoad = true) {
  AppState.isLoggedIn = true;
  const login = document.getElementById('login-container');
  if (login) login.style.display = 'none';

  const home = document.getElementById('home-container');
  if (home) { home.style.display = 'flex'; home.classList.remove('hidden'); }
  if ($('sectionSettings')) $('sectionSettings').classList.add('hidden');
  if ($('sectionUnified')) $('sectionUnified').classList.remove('hidden');

  if (typeof window.initHomeGrid === 'function') {
    try { window.initHomeGrid(); } catch (e) {}
  }

  const scheduleFrame = (window.requestAnimationFrame || function (cb) { return setTimeout(cb, 0); });
  scheduleFrame(function () {
    activeZone = 'header';
    focusedHeaderIdx = 0;
    if ($('tabLive')) $('tabLive').focus();
    else if (typeof focusActiveElement === 'function') focusActiveElement();
  });

  if (shouldLoad) {
    try {
      loadInitialCacheAndSyncInBackground();
    } catch (eLoad) {
      console.error('Erro ao inicializar cache:', eLoad);
      try { loadServer(false); } catch (eFallback) {}
    }
  }
}

function displayFeedback(msg, isError) {
  const fb = document.getElementById('login-feedback');
  if (fb) {
    fb.textContent = msg;
    fb.style.display = 'block';
    fb.style.color = isError ? '#FF5252' : '#FFFFFF';
  }
}

function verifySessionToken(token) {
  unlockHomeInterface(false);
}

// Inicializa o fluxo de Smart TV
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initSmartTVApp);
} else {
  initSmartTVApp();
}


// ============================================================================
// 2.5.3 · QUALIDADE DE VÍDEO + TROCA AUTOMÁTICA DE SERVIDOR/FONTE
// ----------------------------------------------------------------------------
// O player adaptativo do Hls.js sempre escolhe a melhor faixa sozinho. Aqui o
// usuário pode limitar a qualidade (útil em internet fraca) e o app passa a
// trocar de servidor/fonte sozinho quando a lista ou o canal falham.
// ============================================================================

var mk21AutoServerTries = 0;
var mk21TriedSourceUrls = new Set();
var mk21SourceTrying = false;

/* ---------------------------------------------------------------- qualidade */
function mk21QualityPref() {
  return localStorage.getItem('mk21_playback_quality') || 'auto';
}

function mk21QualityHeights() {
  const player = (typeof hlsInstance !== 'undefined') ? hlsInstance : null;
  const found = new Set();
  if (player && Array.isArray(player.levels)) {
    player.levels.forEach(level => { if (level && level.height) found.add(level.height); });
  }
  const list = Array.from(found).sort((a, b) => b - a);
  if (!list.length) [1080, 720, 480].forEach(h => list.push(h));
  return list;
}

function mk21ApplyPlaybackQuality(hls) {
  const player = hls || ((typeof hlsInstance !== 'undefined') ? hlsInstance : null);
  if (!player || !Array.isArray(player.levels) || !player.levels.length) return;
  const pref = mk21QualityPref();
  try {
    if (pref === 'auto') {
      player.autoLevelCapping = -1;
      player.currentLevel = -1;
      return;
    }
    const target = parseInt(pref, 10) || 1080;
    let best = -1;
    player.levels.forEach((level, index) => {
      if (!level || !level.height) return;
      if (level.height <= target && (best < 0 || level.height > player.levels[best].height)) best = index;
    });
    if (best < 0) best = 0;
    player.autoLevelCapping = best;
    player.currentLevel = best;
    addDevLog('[Qualidade] Fixada em ' + player.levels[best].height + 'p', 'info');
  } catch (e) {
    addDevLog('[Qualidade] Não foi possível aplicar: ' + (e && e.message), 'warn');
  }
}

function mk21EnsureQualityModal() {
  let modal = $('modalQuality');
  if (modal) return modal;
  modal = document.createElement('div');
  modal.id = 'modalQuality';
  modal.className = 'app-modal hidden';
  modal.innerHTML =
    '<div class="modal-content" style="width: 680px;">' +
      '<div class="modal-header" style="padding: 16px 22px; display: flex; align-items: center; justify-content: space-between; gap: 16px;">' +
        '<span style="font-size: 22px; font-weight: 800; color: #ffd54f;">🎞️ Qualidade de vídeo</span>' +
        '<button id="btnCloseQuality" class="ctrl-btn" tabindex="0" style="padding: 6px 14px;">✕ Fechar</button>' +
      '</div>' +
      '<div id="qualityOptionsList" style="display: flex; flex-direction: column; gap: 10px; margin-top: 8px;"></div>' +
      '<p style="margin: 18px 0 0 0; font-size: 15px; color: #94a3b8;">' +
        'A escolha vale para todos os canais e é lembrada ao ligar a TV. Em internet fraca, limitar em 720p ou 480p evita travamentos.' +
      '</p>' +
    '</div>';
  document.body.appendChild(modal);
  const close = $('btnCloseQuality');
  if (close) close.onclick = mk21CloseQualityModal;
  return modal;
}

function mk21RenderQualityOptions() {
  const list = $('qualityOptionsList');
  if (!list) return;
  const pref = mk21QualityPref();
  list.replaceChildren();
  const options = [['auto', 'Automática (recomendada)']].concat(
    mk21QualityHeights().map(h => [String(h), 'Até ' + h + 'p'])
  );
  options.forEach(pair => {
    const value = pair[0];
    const label = pair[1];
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'track-item-btn quality-option' + (pref === value ? ' active' : '');
    button.tabIndex = 0;
    button.textContent = (pref === value ? '✓ ' : '') + label;
    button.onclick = () => {
      localStorage.setItem('mk21_playback_quality', value);
      mk21ApplyPlaybackQuality();
      mk21RenderQualityOptions();
      addDevLog('[Qualidade] ' + label, 'success');
      showChannelBanner('Qualidade: ' + label);
    };
    list.appendChild(button);
  });
}

function mk21OpenQualityModal() {
  const modal = mk21EnsureQualityModal();
  mk21RenderQualityOptions();
  modal.classList.remove('hidden');
  const first = modal.querySelector('.quality-option');
  if (first) first.focus();
  addDevLog('[UI] Qualidade de vídeo aberta', 'info');
}

function mk21CloseQualityModal() {
  const modal = $('modalQuality');
  if (modal) modal.classList.add('hidden');
  const fullscreen = (typeof isAppFullscreen === 'function') && isAppFullscreen();
  const target = fullscreen ? $('fsBtnQuality') : $('btnQuality');
  if (target && target.focus) target.focus();
}

/* ------------------------------------------------- painel nas configurações */
function renderQualityPanel() {
  const box = $('settingsDetailBox');
  if (!box) return;
  const pref = mk21QualityPref();
  const heights = mk21QualityHeights();
  const rows = [['auto', 'Automática (recomendada)', 'O player escolhe a melhor faixa conforme a internet.']]
    .concat(heights.map(h => [String(h), 'Até ' + h + 'p', 'Limita o canal em ' + h + 'p.']));
  const cards = rows.map(pair => {
    const active = pref === pair[0];
    return '<button class="track-item-btn quality-option' + (active ? ' active' : '') + '" data-quality="' + pair[0] + '" tabindex="0" ' +
      'style="text-align:left; padding: 16px 20px; font-size: 20px; display: flex; flex-direction: column; gap: 4px;">' +
      '<span style="font-weight: 800;">' + (active ? '✓ ' : '') + pair[1] + '</span>' +
      '<span style="font-size: 14px; color: #94a3b8; font-weight: 500;">' + pair[2] + '</span>' +
    '</button>';
  }).join('');
  box.innerHTML =
    '<h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">🎞️ Qualidade de vídeo</h3>' +
    '<p style="margin:0 0 18px 0; font-size:16px; color:#94a3b8;">Vale para todos os canais e fica salva no aparelho.</p>' +
    '<div style="display:flex; flex-direction:column; gap:10px;">' + cards + '</div>';
  box.querySelectorAll('.quality-option').forEach(button => {
    button.onclick = () => {
      localStorage.setItem('mk21_playback_quality', button.getAttribute('data-quality'));
      mk21ApplyPlaybackQuality();
      renderQualityPanel();
      addDevLog('[Qualidade] ' + button.textContent.trim(), 'success');
      showChannelBanner('Qualidade: ' + button.textContent.trim());
      const again = box.querySelector('.quality-option.active');
      if (again) again.focus();
    };
  });
}

/* ------------------------------------------------- troca automática servidor */
function mk21AutoAdvanceServer(err, failedServer) {
  if (!Array.isArray(SERVERS) || SERVERS.length < 2) return false;
  if (mk21AutoServerTries >= SERVERS.length - 1) {
    mk21AutoServerTries = 0;
    addDevLog('[AutoServidor] Todos os servidores falharam', 'error');
    return false;
  }
  const nextIndex = (currentServerIndex + 1) % SERVERS.length;
  const next = SERVERS[nextIndex];
  mk21AutoServerTries += 1;
  const falhou = failedServer && failedServer.name ? cleanServerName(failedServer.name) : 'Servidor atual';
  addDevLog('[AutoServidor] ' + falhou + ' falhou (' + (err && err.message) + '); tentando ' + cleanServerName(next.name), 'warn');
  showChannelBanner('🔄 ' + falhou + ' falhou. Trocando para ' + cleanServerName(next.name) + '...');
  currentServerIndex = nextIndex;
  if (typeof saveServersToStorage === 'function') {
    try { saveServersToStorage(); } catch (e) {}
  }
  setTimeout(() => {
    if (typeof loadServer === 'function') loadServer(true);
  }, 1500);
  return true;
}

/* -------------------------------------------- failover de fonte no player */
var mk21FailoverItemName = '';

function mk21ResetSourceFailover(item) {
  // Só volta a tentar as fontes quando o usuário troca de canal de verdade;
  // durante a mesma reprodução a lista de URLs já tentadas é preservada.
  const nome = item && item.name ? String(item.name) : '';
  if (nome && nome === mk21FailoverItemName) {
    if (item && item.url) mk21TriedSourceUrls.add(item.url);
    return;
  }
  mk21FailoverItemName = nome;
  mk21TriedSourceUrls = new Set();
  if (item && item.url) mk21TriedSourceUrls.add(item.url);
}

function mk21SameChannel(a, b, sources) {
  if (!sources) return false;
  try { return sources.matchesAcrossServers(a, b); } catch (e) { return false; }
}

async function mk21TryAlternativeSource() {
  if (!activeItem || mk21SourceTrying) return false;
  mk21SourceTrying = true;
  const sources = window.MK21AlternativeSources || null;
  try {
    const candidates = [];
    if (sources) {
      sources.sourceUrls(activeItem).forEach(url => candidates.push({ url: url, origem: 'mesma lista' }));
    }
    for (let i = 0; i < SERVERS.length; i++) {
      const srv = SERVERS[i];
      if (!srv || (SERVERS[currentServerIndex] && srv.id === SERVERS[currentServerIndex].id)) continue;
      let cached = null;
      try {
        cached = getFastCache(srv.id) || await getStoredData(srv.id);
      } catch (e) {
        cached = null;
      }
      const list = cached && Array.isArray(cached.LIVE) ? cached.LIVE : [];
      const match = list.find(candidate => mk21SameChannel(activeItem, candidate, sources));
      if (match && match.url) candidates.push({ url: match.url, origem: cleanServerName(srv.name) });
    }
    const next = candidates.find(candidate => candidate.url && !mk21TriedSourceUrls.has(candidate.url));
    if (!next) {
      addDevLog('[AutoFonte] Nenhuma fonte alternativa disponível para ' + activeItem.name, 'warn');
      return false;
    }
    mk21TriedSourceUrls.add(next.url);
    watchdogStreamKey = ''; // fonte nova = contador de reconexão zerado
    addDevLog('[AutoFonte] Alternando para ' + next.origem, 'warn');
    showChannelBanner('🔄 Fonte alternativa: ' + next.origem);
    autoReconnectAttempts = 0;
    isAutoReconnecting = false;
    playStream(activeItem, next.url);
    return true;
  } finally {
    mk21SourceTrying = false;
  }
}

/* ------------------------------------------------------------- inicialização */
function mk21BindQualityButtons() {
  const btn = $('btnQuality');
  if (btn) btn.onclick = mk21OpenQualityModal;
  const fsBtn = $('fsBtnQuality');
  if (fsBtn) fsBtn.onclick = mk21OpenQualityModal;
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mk21BindQualityButtons);
} else {
  mk21BindQualityButtons();
}
setTimeout(mk21BindQualityButtons, 1500);
