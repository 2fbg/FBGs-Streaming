/*
 * MK21 Streaming — Observabilidade Estruturada Multiplataforma (Smart TV webOS & Tizen)
 *
 * Mantido em sincronismo funcional com js/observability.js para empacotamento autônomo no IPK.
 * - Formato JSONL estruturado (schemaVersion, tsUtc, monoMs, sessionId, platform, version, build, screen, event, phase, severity, durationMs, correlationId).
 * - Buffer circular + persistência em lote com limite de tamanho; nunca bloqueia nem interrompe a TV.
 * - Níveis: OFF | NORMAL | DIAGNOSTICO | TRACE (expira em 45 min).
 * - Redaction aplicada ANTES da persistência e ANTES da exportação.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(root);
  } else {
    var api = factory(root);
    root.MK21Observability = api;
  }
}(typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : this), function (globalObj) {
  'use strict';

  var SCHEMA_VERSION = 1;
  var STORAGE_KEY_LOGS = 'mk21_obs_jsonl_v1';
  var STORAGE_KEY_LEVEL = 'mk21_obs_level_v1';
  var STORAGE_KEY_TRACE_UNTIL = 'mk21_obs_trace_until_v1';

  var MAX_MEMORY_EVENTS = 600;
  var MAX_STORAGE_BYTES = 220 * 1024;
  var FLUSH_INTERVAL_MS = 1500;
  var DEFAULT_TRACE_TTL_MS = 45 * 60 * 1000;
  var MIN_TRACE_TTL_MS = 30 * 60 * 1000;
  var MAX_TRACE_TTL_MS = 60 * 60 * 1000;

  var LEVELS = {
    OFF: 0,
    NORMAL: 1,
    DIAGNOSTICO: 2,
    TRACE: 3
  };

  var SEVERITY_RANK = {
    DEBUG: 3,
    INFO: 1,
    WARN: 1,
    ERROR: 1
  };

  var SENSITIVE_KEY_RE = /^(pass(word)?|pwd|pin|token|access[_-]?token|refresh[_-]?token|auth(orization)?|cookie|set-cookie|secret|api[_-]?key|credential|cred|session[_-]?token|private[_-]?key|license[_-]?key)$/i;
  var BASIC_AUTH_URL_RE = /(https?:\/\/)[^/@\s:]+:[^/@\s]+@/gi;
  var QUERY_CRED_RE = /((?:^|[?&\s,(])(?:username|user|password|pass|pwd|token|access[_-]?token|refresh[_-]?token|auth|api[_-]?key|pin|secret|cookie|signature|sig|key)=)[^&#\s"',;}\]]*/gi;
  var XTREAM_PATH_RE = /\/(live|movie|series)\/[^/\s?"']+\/[^/\s?"']+/gi;
  var LICENSE_TOKEN_RE = /MKL1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g;
  var BEARER_RE = /\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{6,}/gi;
  var HEADER_SECRET_RE = /\b(Authorization|Cookie|Set-Cookie|X-Api-Key)\s*[:=]\s*[^\r\n;,"}]+/gi;
  var JSON_SECRET_RE = /("(?:password|pass|pin|token|authorization|cookie|secret|apiKey|api_key)"\s*:\s*)"[^"]*"/gi;

  function safeNowMs() {
    try {
      if (globalObj && globalObj.performance && typeof globalObj.performance.now === 'function') {
        return Math.round(globalObj.performance.now() * 100) / 100;
      }
    } catch (_) {}
    return Date.now();
  }

  function generateAnonymousId(prefix) {
    var rnd = '';
    try {
      if (globalObj && globalObj.crypto && typeof globalObj.crypto.getRandomValues === 'function') {
        var bytes = new Uint8Array(8);
        globalObj.crypto.getRandomValues(bytes);
        for (var i = 0; i < bytes.length; i++) {
          rnd += ('0' + bytes[i].toString(16)).slice(-2);
        }
      }
    } catch (_) {}
    if (!rnd) {
      rnd = Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
    }
    return (prefix || 'id') + '_' + rnd;
  }

  function redactString(value) {
    if (value === null || value === undefined) return '';
    var text = String(value);
    if (!text) return '';
    if (text.indexOf('#EXTM3U') !== -1 || (text.indexOf('#EXTINF') !== -1 && text.length > 256)) {
      return '[REDACTED_PLAYLIST len=' + text.length + ']';
    }
    var out = text
      .replace(BASIC_AUTH_URL_RE, '$1[REDACTED]@')
      .replace(QUERY_CRED_RE, '$1[REDACTED]')
      .replace(XTREAM_PATH_RE, '/$1/[REDACTED]/[REDACTED]')
      .replace(LICENSE_TOKEN_RE, 'MKL1.[REDACTED]')
      .replace(BEARER_RE, '$1 [REDACTED]')
      .replace(HEADER_SECRET_RE, '$1: [REDACTED]')
      .replace(JSON_SECRET_RE, '$1"[REDACTED]"');
    if (out.length > 1024) {
      out = out.slice(0, 1024) + '...[TRUNCATED]';
    }
    return out;
  }

  function extractSafeHost(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') return 'unknown';
    try {
      var base = (globalObj && globalObj.location && globalObj.location.origin) ? globalObj.location.origin : 'https://localhost';
      var parsed = new URL(rawUrl, base);
      return parsed.host || 'unknown';
    } catch (_) {
      var m = String(rawUrl).match(/^https?:\/\/(?:[^/@\s]+@)?([^/?#\s]+)/i);
      return m ? m[1] : 'relative';
    }
  }

  function redactMeta(input, depth) {
    var d = depth || 0;
    if (d > 4) return '[MAX_DEPTH]';
    if (input === null || input === undefined) return undefined;
    var t = typeof input;
    if (t === 'number' || t === 'boolean') return input;
    if (t === 'string') return redactString(input);
    if (Array.isArray(input)) {
      var arr = [];
      var maxItems = Math.min(input.length, 25);
      for (var i = 0; i < maxItems; i++) {
        arr.push(redactMeta(input[i], d + 1));
      }
      return arr;
    }
    if (t === 'object') {
      var clean = {};
      var keys = Object.keys(input);
      for (var k = 0; k < keys.length; k++) {
        var key = keys[k];
        if (SENSITIVE_KEY_RE.test(key)) {
          clean[key] = '[REDACTED]';
          continue;
        }
        var lower = key.toLowerCase();
        if (lower === 'url' || lower === 'href' || lower === 'endpoint' || lower === 'streamurl') {
          clean.host = extractSafeHost(String(input[key] || ''));
          continue;
        }
        if (lower === 'playlist' || lower === 'm3u' || lower === 'rawbody' || lower === 'body') {
          clean[key] = '[REDACTED_PAYLOAD]';
          continue;
        }
        var v = redactMeta(input[key], d + 1);
        if (v !== undefined) clean[key] = v;
      }
      return clean;
    }
    return redactString(String(input));
  }

  function detectPlatform(explicit) {
    if (explicit) return String(explicit);
    try {
      var ua = (globalObj && globalObj.navigator && globalObj.navigator.userAgent) ? globalObj.navigator.userAgent : '';
      if (/web0s|webos|netcast/i.test(ua) || (globalObj && globalObj.webOS)) return 'webos';
      if (/tizen|samsung/i.test(ua) || (globalObj && globalObj.tizen)) return 'tizen';
    } catch (_) {}
    return 'smart-tv';
  }

  function createObservabilityLogger(options) {
    var opts = options || {};
    var storage = opts.storage || (function () {
      try { return globalObj && globalObj.localStorage ? globalObj.localStorage : null; } catch (_) { return null; }
    })();
    var clockFn = typeof opts.now === 'function' ? opts.now : function () { return Date.now(); };
    var monoFn = typeof opts.mono === 'function' ? opts.mono : safeNowMs;
    var maxMemory = typeof opts.maxMemoryEvents === 'number' && opts.maxMemoryEvents > 0 ? opts.maxMemoryEvents : MAX_MEMORY_EVENTS;
    var maxStorageBytes = typeof opts.maxStorageBytes === 'number' && opts.maxStorageBytes > 0 ? opts.maxStorageBytes : MAX_STORAGE_BYTES;

    var sessionId = generateAnonymousId('ses');
    var platform = detectPlatform(opts.platform);
    var version = String(opts.version || '2.9.4');
    var build = Number(opts.build || 294);
    var currentScreen = String(opts.initialScreen || 'boot');
    var buffer = [];
    var dirty = false;
    var flushTimer = null;
    var activeTimers = {};
    var metrics = {
      bootStartMonoMs: monoFn(),
      shellVisibleMs: null,
      firstFocusMs: null,
      firstListMs: null,
      firstChannelMs: null,
      firstFrameMs: null,
      fullIndexMs: null,
      cacheHits: 0,
      cacheMisses: 0,
      indexedItems: 0,
      batchesCommitted: 0,
      errorsCount: 0,
      categoryCounts: {}
    };

    var level = 'NORMAL';
    var traceExpiresAt = 0;

    function loadPersistedState() {
      if (!storage) return;
      try {
        var savedLevel = storage.getItem(STORAGE_KEY_LEVEL);
        var savedTraceUntil = Number(storage.getItem(STORAGE_KEY_TRACE_UNTIL) || '0');
        if (savedLevel && Object.prototype.hasOwnProperty.call(LEVELS, savedLevel)) {
          level = savedLevel;
        }
        if (Number.isFinite(savedTraceUntil) && savedTraceUntil > 0) {
          traceExpiresAt = savedTraceUntil;
        }
        enforceTraceExpiration();

        var raw = storage.getItem(STORAGE_KEY_LOGS);
        if (raw && typeof raw === 'string') {
          var lines = raw.split('\n');
          for (var i = 0; i < lines.length; i++) {
            var line = lines[i].trim();
            if (!line) continue;
            try {
              var parsed = JSON.parse(line);
              if (parsed && typeof parsed === 'object') {
                buffer.push(parsed);
              }
            } catch (_) {}
          }
          if (buffer.length > maxMemory) {
            buffer = buffer.slice(buffer.length - maxMemory);
          }
        }
      } catch (_) {}
    }

    function enforceTraceExpiration() {
      if (level === 'TRACE' && traceExpiresAt > 0 && clockFn() >= traceExpiresAt) {
        level = 'NORMAL';
        traceExpiresAt = 0;
        try {
          if (storage) {
            storage.setItem(STORAGE_KEY_LEVEL, 'NORMAL');
            storage.removeItem(STORAGE_KEY_TRACE_UNTIL);
          }
        } catch (_) {}
      }
    }

    function getLevel() {
      enforceTraceExpiration();
      return level;
    }

    function setLevel(nextLevel, traceTtlMs) {
      var normalized = String(nextLevel || 'NORMAL').toUpperCase();
      if (!Object.prototype.hasOwnProperty.call(LEVELS, normalized)) {
        normalized = 'NORMAL';
      }
      level = normalized;
      if (normalized === 'TRACE') {
        var ttl = typeof traceTtlMs === 'number' && traceTtlMs > 0 ? traceTtlMs : DEFAULT_TRACE_TTL_MS;
        ttl = Math.max(MIN_TRACE_TTL_MS, Math.min(MAX_TRACE_TTL_MS, ttl));
        traceExpiresAt = clockFn() + ttl;
      } else {
        traceExpiresAt = 0;
      }
      try {
        if (storage) {
          storage.setItem(STORAGE_KEY_LEVEL, level);
          if (traceExpiresAt > 0) {
            storage.setItem(STORAGE_KEY_TRACE_UNTIL, String(traceExpiresAt));
          } else {
            storage.removeItem(STORAGE_KEY_TRACE_UNTIL);
          }
        }
      } catch (_) {}
      return { level: level, traceExpiresAt: traceExpiresAt };
    }

    function enableTemporaryTrace(minutes) {
      var mins = typeof minutes === 'number' && minutes > 0 ? minutes : 45;
      return setLevel('TRACE', mins * 60 * 1000);
    }

    function shouldRecord(severity, minLevel) {
      enforceTraceExpiration();
      var currentRank = LEVELS[level];
      if (currentRank === 0) return false;
      var required = minLevel && Object.prototype.hasOwnProperty.call(LEVELS, minLevel)
        ? LEVELS[minLevel]
        : (SEVERITY_RANK[severity] || 1);
      return currentRank >= required;
    }

    function scheduleFlush() {
      if (!storage || flushTimer !== null) return;
      try {
        flushTimer = setTimeout(function () {
          flushTimer = null;
          flushNow();
        }, FLUSH_INTERVAL_MS);
      } catch (_) {
        flushNow();
      }
    }

    function flushNow() {
      if (!storage || !dirty) return;
      dirty = false;
      try {
        var lines = [];
        var totalBytes = 0;
        for (var i = buffer.length - 1; i >= 0; i--) {
          var serialized = JSON.stringify(buffer[i]);
          if (totalBytes + serialized.length + 1 > maxStorageBytes) break;
          lines.push(serialized);
          totalBytes += serialized.length + 1;
        }
        lines.reverse();
        storage.setItem(STORAGE_KEY_LOGS, lines.join('\n'));
      } catch (_) {}
    }

    function updateMetrics(eventName, durationMs, meta) {
      try {
        var relMs = Math.max(0, Math.round((monoFn() - metrics.bootStartMonoMs) * 100) / 100);
        if (eventName === 'dom_content_loaded' && metrics.shellVisibleMs === null) metrics.shellVisibleMs = relMs;
        if (eventName === 'first_interaction_ready' && metrics.firstFocusMs === null) metrics.firstFocusMs = relMs;
        if (eventName === 'playlist_batch_committed') {
          metrics.batchesCommitted += 1;
          if (metrics.firstListMs === null) metrics.firstListMs = relMs;
          if (meta && typeof meta.batchSize === 'number') metrics.indexedItems += meta.batchSize;
        }
        if (eventName === 'playlist_load_complete' && metrics.fullIndexMs === null) {
          metrics.fullIndexMs = typeof durationMs === 'number' ? durationMs : relMs;
          if (meta && meta.categoryCounts && typeof meta.categoryCounts === 'object') {
            metrics.categoryCounts = meta.categoryCounts;
          }
        }
        if (eventName === 'player_open' && metrics.firstChannelMs === null) metrics.firstChannelMs = relMs;
        if (eventName === 'player_first_frame' && metrics.firstFrameMs === null) metrics.firstFrameMs = relMs;
        if (eventName === 'cache_hit') metrics.cacheHits += 1;
        if (eventName === 'cache_miss') metrics.cacheMisses += 1;
        if (eventName === 'runtime_error' || eventName === 'unhandled_rejection' || eventName === 'player_error' || eventName === 'network_error') {
          metrics.errorsCount += 1;
        }
      } catch (_) {}
    }

    function logEvent(eventName, details) {
      try {
        var info = details || {};
        var severity = String(info.severity || 'INFO').toUpperCase();
        var minLevel = info.minLevel ? String(info.minLevel).toUpperCase() : null;
        var force = Boolean(info.force);
        if (!force && !shouldRecord(severity, minLevel)) {
          return null;
        }
        if (info.screen) {
          currentScreen = String(info.screen);
        }
        var safeMeta = info.meta ? redactMeta(info.meta, 0) : undefined;
        var durationMs = typeof info.durationMs === 'number' && Number.isFinite(info.durationMs)
          ? Math.round(info.durationMs * 100) / 100
          : undefined;
        var record = {
          schemaVersion: SCHEMA_VERSION,
          tsUtc: new Date(clockFn()).toISOString(),
          monoMs: monoFn(),
          sessionId: sessionId,
          platform: platform,
          version: version,
          build: build,
          screen: currentScreen,
          event: redactString(String(eventName || 'unknown_event')),
          phase: redactString(String(info.phase || 'runtime')),
          severity: severity
        };
        if (durationMs !== undefined) record.durationMs = durationMs;
        if (info.correlationId) record.correlationId = redactString(String(info.correlationId));
        if (info.message) record.message = redactString(String(info.message));
        if (safeMeta && Object.keys(safeMeta).length > 0) record.meta = safeMeta;

        buffer.push(record);
        if (buffer.length > maxMemory) {
          buffer.splice(0, buffer.length - maxMemory);
        }
        updateMetrics(record.event, durationMs, safeMeta);
        dirty = true;
        scheduleFlush();
        return record;
      } catch (_) {
        return null;
      }
    }

    function setScreen(screenName) {
      var prev = currentScreen;
      var next = String(screenName || 'unknown');
      if (prev && prev !== next) {
        logEvent('screen_leave', { screen: prev, phase: 'navigation', meta: { to: next } });
      }
      currentScreen = next;
      logEvent('screen_enter', { screen: next, phase: 'navigation', meta: { from: prev } });
    }

    function startTimer(key, correlationId) {
      var cid = correlationId || generateAnonymousId('cor');
      activeTimers[key] = { startMono: monoFn(), correlationId: cid };
      return cid;
    }

    function endTimer(key, eventName, details) {
      var entry = activeTimers[key];
      delete activeTimers[key];
      var durationMs = entry ? Math.max(0, monoFn() - entry.startMono) : undefined;
      var merged = Object.assign({}, details || {}, {
        durationMs: durationMs,
        correlationId: (details && details.correlationId) || (entry && entry.correlationId)
      });
      return logEvent(eventName, merged);
    }

    function logNetwork(info) {
      var data = info || {};
      var isError = Boolean(data.error) || (typeof data.status === 'number' && data.status >= 400);
      var meta = {
        host: extractSafeHost(data.url || data.host || ''),
        method: String(data.method || 'GET').toUpperCase(),
        status: typeof data.status === 'number' ? data.status : 0
      };
      if (typeof data.sizeBytes === 'number' && data.sizeBytes >= 0) {
        meta.sizeBytes = data.sizeBytes;
      }
      if (data.error) {
        meta.error = redactString(String(data.error));
      }
      return logEvent(isError ? 'network_error' : 'network_request', {
        phase: 'network',
        severity: isError ? 'WARN' : 'INFO',
        durationMs: data.durationMs,
        correlationId: data.correlationId || generateAnonymousId('net'),
        meta: meta
      });
    }

    function getMetricsSummary() {
      var domElements = null;
      var approxHeapMb = null;
      try {
        if (globalObj && globalObj.document && globalObj.document.getElementsByTagName) {
          domElements = globalObj.document.getElementsByTagName('*').length;
        }
        if (globalObj && globalObj.performance && globalObj.performance.memory && globalObj.performance.memory.usedJSHeapSize) {
          approxHeapMb = Math.round((globalObj.performance.memory.usedJSHeapSize / (1024 * 1024)) * 10) / 10;
        }
      } catch (_) {}
      return {
        schemaVersion: SCHEMA_VERSION,
        sessionId: sessionId,
        platform: platform,
        version: version,
        build: build,
        level: getLevel(),
        traceExpiresAtUtc: traceExpiresAt > 0 ? new Date(traceExpiresAt).toISOString() : null,
        bufferedEvents: buffer.length,
        shellVisibleMs: metrics.shellVisibleMs,
        firstFocusMs: metrics.firstFocusMs,
        firstListMs: metrics.firstListMs,
        firstChannelMs: metrics.firstChannelMs,
        firstFrameMs: metrics.firstFrameMs,
        fullIndexMs: metrics.fullIndexMs,
        approxMemoryMb: approxHeapMb,
        domElements: domElements,
        cacheHits: metrics.cacheHits,
        cacheMisses: metrics.cacheMisses,
        indexedItems: metrics.indexedItems,
        batchesCommitted: metrics.batchesCommitted,
        errorsCount: metrics.errorsCount,
        categoryCounts: metrics.categoryCounts
      };
    }

    function exportJsonl() {
      flushNow();
      var lines = [];
      for (var i = 0; i < buffer.length; i++) {
        lines.push(redactString(JSON.stringify(buffer[i])));
      }
      logEvent('diagnostics_exported', {
        phase: 'diagnostics',
        severity: 'INFO',
        force: true,
        meta: { exportedLines: lines.length }
      });
      return lines.join('\n');
    }

    function exportDiagnosticBundle() {
      var jsonl = exportJsonl();
      var errors = [];
      for (var i = 0; i < buffer.length; i++) {
        if (buffer[i].severity === 'ERROR' || buffer[i].severity === 'WARN') {
          errors.push(redactString(JSON.stringify(buffer[i])));
        }
      }
      return {
        'manifest.json': JSON.stringify({
          schemaVersion: SCHEMA_VERSION,
          platform: platform,
          version: version,
          build: build,
          sessionId: sessionId,
          exportedAtUtc: new Date(clockFn()).toISOString(),
          redacted: true
        }, null, 2),
        'session-summary.json': JSON.stringify(getMetricsSummary(), null, 2),
        'events.jsonl': jsonl,
        'errors.jsonl': errors.join('\n'),
        'performance.json': JSON.stringify(getMetricsSummary(), null, 2),
        'README.txt': 'MK21 Smart TV Diagnostic Bundle (Redacted)\nCredenciais, PINs, tokens, cookies e URLs autenticadas foram removidos antes da persistencia e exportacao.\n'
      };
    }

    function downloadDiagnosticJsonl(filenamePrefix) {
      try {
        var jsonl = exportJsonl();
        var blob = new Blob([jsonl + '\n'], { type: 'application/x-ndjson;charset=utf-8' });
        var a = globalObj.document.createElement('a');
        a.href = globalObj.URL.createObjectURL(blob);
        var stamp = new Date(clockFn()).toISOString().replace(/[:.]/g, '-');
        a.download = (filenamePrefix || ('mk21-' + platform + '-diagnostics')) + '-' + stamp + '.jsonl';
        globalObj.document.body.appendChild(a);
        a.click();
        globalObj.document.body.removeChild(a);
        return true;
      } catch (_) {
        return false;
      }
    }

    function clear() {
      buffer = [];
      dirty = false;
      try {
        if (storage) storage.removeItem(STORAGE_KEY_LOGS);
      } catch (_) {}
      logEvent('diagnostics_cleared', { phase: 'diagnostics', severity: 'INFO', force: true });
      flushNow();
    }

    function setVersionInfo(nextVersion, nextBuild, nextPlatform) {
      if (nextVersion) version = String(nextVersion);
      if (nextBuild) build = Number(nextBuild);
      if (nextPlatform) platform = String(nextPlatform);
    }

    function installAutoHooks() {
      if (!globalObj || !globalObj.addEventListener) return;
      logEvent('process_start', { phase: 'boot', severity: 'INFO', screen: 'boot' });

      if (globalObj.document) {
        if (globalObj.document.readyState === 'interactive' || globalObj.document.readyState === 'complete') {
          logEvent('dom_content_loaded', { phase: 'boot', severity: 'INFO' });
        } else {
          globalObj.document.addEventListener('DOMContentLoaded', function () {
            logEvent('dom_content_loaded', { phase: 'boot', severity: 'INFO' });
          }, { once: true });
        }

        var firstInteractionLogged = false;
        var markFirstInteraction = function () {
          if (firstInteractionLogged) return;
          firstInteractionLogged = true;
          logEvent('first_interaction_ready', { phase: 'boot', severity: 'INFO' });
        };

        globalObj.document.addEventListener('click', function (ev) {
          markFirstInteraction();
          var target = ev && ev.target && ev.target.closest ? ev.target.closest('button, a, [role="button"], .list-item-btn, .cat-item-btn') : (ev && ev.target);
          if (!target) return;
          logEvent('ui_click', {
            phase: 'interaction',
            severity: 'INFO',
            minLevel: 'DIAGNOSTICO',
            meta: { id: target.id || '', tag: target.tagName || '' }
          });
        }, true);

        globalObj.document.addEventListener('keydown', function (ev) {
          markFirstInteraction();
          var k = ev ? (ev.keyCode || ev.which || 0) : 0;
          var key = ev ? (ev.key || '') : '';
          if (k === 461 || k === 10009 || k === 27 || key === 'Escape' || key === 'Back' || key === 'GoBack') {
            logEvent('back_pressed', { phase: 'navigation', severity: 'INFO', meta: { key: key || String(k) } });
          } else if (k >= 37 && k <= 40 || k === 13 || key.indexOf('Arrow') === 0 || key === 'Enter') {
            logEvent('dpad_navigation', {
              phase: 'navigation',
              severity: 'DEBUG',
              minLevel: 'DIAGNOSTICO',
              meta: { key: key || String(k) }
            });
          }
        }, true);

        globalObj.document.addEventListener('focusin', function (ev) {
          var t = ev && ev.target;
          if (!t || !t.tagName) return;
          logEvent('focus_changed', {
            phase: 'navigation',
            severity: 'DEBUG',
            minLevel: 'TRACE',
            meta: { id: t.id || '', tag: t.tagName }
          });
        }, true);
      }

      globalObj.addEventListener('load', function () {
        logEvent('window_loaded', { phase: 'boot', severity: 'INFO' });
        setTimeout(function () {
          logEvent('first_interaction_ready', { phase: 'boot', severity: 'INFO', minLevel: 'NORMAL' });
        }, 0);
      }, { once: true });

      globalObj.addEventListener('error', function (ev) {
        logEvent('runtime_error', {
          phase: 'runtime',
          severity: 'ERROR',
          message: ev && ev.message ? ev.message : 'runtime_error',
          meta: {
            source: ev && ev.filename ? extractSafeHost(ev.filename) : '',
            line: ev && ev.lineno ? ev.lineno : 0
          }
        });
      });

      globalObj.addEventListener('unhandledrejection', function (ev) {
        var reason = ev && ev.reason;
        logEvent('unhandled_rejection', {
          phase: 'runtime',
          severity: 'ERROR',
          message: reason && reason.message ? reason.message : String(reason || 'unhandled_rejection')
        });
      });

      if (typeof globalObj.fetch === 'function' && !globalObj.fetch.__mk21ObsWrapped) {
        var origFetch = globalObj.fetch.bind(globalObj);
        var wrappedFetch = function (resource, init) {
          var urlStr = typeof resource === 'string' ? resource : (resource && resource.url ? resource.url : '');
          var method = (init && init.method) || (resource && resource.method) || 'GET';
          var cid = generateAnonymousId('net');
          var start = monoFn();
          return origFetch(resource, init).then(function (resp) {
            var contentLen = 0;
            try {
              var cl = resp && resp.headers && resp.headers.get ? resp.headers.get('content-length') : null;
              if (cl) contentLen = Number(cl) || 0;
            } catch (_) {}
            logNetwork({
              url: urlStr,
              method: method,
              status: resp ? resp.status : 0,
              durationMs: Math.max(0, monoFn() - start),
              sizeBytes: contentLen > 0 ? contentLen : undefined,
              correlationId: cid
            });
            return resp;
          }, function (err) {
            logNetwork({
              url: urlStr,
              method: method,
              status: 0,
              durationMs: Math.max(0, monoFn() - start),
              error: err && err.message ? err.message : String(err || 'fetch_failed'),
              correlationId: cid
            });
            throw err;
          });
        };
        wrappedFetch.__mk21ObsWrapped = true;
        try { globalObj.fetch = wrappedFetch; } catch (_) {}
      }

      globalObj.addEventListener('pagehide', flushNow);
      globalObj.addEventListener('beforeunload', flushNow);
    }

    loadPersistedState();

    return {
      SCHEMA_VERSION: SCHEMA_VERSION,
      LEVELS: LEVELS,
      redactString: redactString,
      redactMeta: redactMeta,
      extractSafeHost: extractSafeHost,
      getSessionId: function () { return sessionId; },
      getLevel: getLevel,
      setLevel: setLevel,
      enableTemporaryTrace: enableTemporaryTrace,
      setVersionInfo: setVersionInfo,
      setScreen: setScreen,
      startTimer: startTimer,
      endTimer: endTimer,
      logEvent: logEvent,
      logNetwork: logNetwork,
      getEvents: function () { return buffer.slice(); },
      getMetricsSummary: getMetricsSummary,
      exportJsonl: exportJsonl,
      exportDiagnosticBundle: exportDiagnosticBundle,
      downloadDiagnosticJsonl: downloadDiagnosticJsonl,
      flushNow: flushNow,
      clear: clear,
      installAutoHooks: installAutoHooks
    };
  }

  var singleton = createObservabilityLogger();
  if (globalObj && globalObj.window === globalObj) {
    singleton.installAutoHooks();
  }
  singleton.createLogger = createObservabilityLogger;
  return singleton;
}));
