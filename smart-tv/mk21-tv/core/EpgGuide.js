/**
 * MK21 Play — Motor de Guia de Programação (EPG) da Smart TV.
 *
 * Portão das funcionalidades do Saimo TV (LG webOS):
 *  - "Guia" dedicado com lista de canais + programação do canal selecionado;
 *  - HUD de zapping com "NO AR" / "A SEGUIR" e contagem regressiva.
 *
 * Este módulo é PURO (sem DOM, sem fetch, sem timer) para poder ser testado
 * em Node (`smart-tv/tests/epg-guide.test.js`) e reusado pelo app legado.
 *
 * Convenção de tempo: TUDO em milissegundos epoch. As fontes do servidor
 * (Xtream `get_short_epg`) entregam epoch em SEGUNDOS; o XMLTV entrega
 * "YYYYMMDDHHmmss +ZZZZ". `toTimestampMs` normaliza os três formatos.
 */

const MS_PER_MINUTE = 60000;
const MS_PER_SECOND = 1000;

/**
 * Converte um timestamp heterogêneo (epoch s, epoch ms, XMLTV, ISO) em ms.
 * Retorna NaN quando o valor não é interpretável.
 */
function toTimestampMs(value) {
  if (value === null || value === undefined || value === '') return NaN;

  if (value instanceof Date) {
    const t = value.getTime();
    return Number.isFinite(t) ? t : NaN;
  }

  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return NaN;
    // Epoch em segundos cabe abaixo de ~1e11 até o ano 5138; ms passa disso.
    return value > 1e11 ? value : value * 1000;
  }

  const s = String(value).trim();
  if (!s) return NaN;

  if (/^\d{10}$/.test(s)) return parseInt(s, 10) * 1000;
  if (/^\d{13}$/.test(s)) return parseInt(s, 10);

  // XMLTV: 20261021020000 +0000
  const xmltv = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);
  if (xmltv) {
    const [, y, mo, d, h, mi, sec, tz] = xmltv;
    const tzPart = tz ? `${tz.slice(0, 3)}:${tz.slice(3)}` : 'Z';
    const iso = `${y}-${mo}-${d}T${h}:${mi}:${sec}${tzPart}`;
    const parsed = new Date(iso).getTime();
    if (!Number.isNaN(parsed)) return parsed;
  }

  // ISO / SQL: 2026-10-21 02:00:00
  const isoTry = new Date(s.replace(' ', 'T')).getTime();
  if (!Number.isNaN(isoTry)) return isoTry;

  return NaN;
}

/** Lê um título/descrição vindo em base64 (Xtream often base64-encoda) sem quebrar. */
function decodeMaybeBase64(text) {
  if (typeof text !== 'string') return '';
  const trimmed = text.trim();
  if (!trimmed || !/^[A-Za-z0-9+/=]+$/.test(trimmed) || trimmed.length < 4 || trimmed.length % 4 !== 0) {
    return trimmed;
  }
  try {
    const bin = atob(trimmed);
    if (!/^[\x09\x0A\x0D\x20-\x7E\xA0-\xFF]+$/.test(bin)) return trimmed;
    try {
      return decodeURIComponent(escape(bin));
    } catch (e) {
      return bin;
    }
  } catch (e) {
    return trimmed;
  }
}

/**
 * Normaliza um programa bruto para o formato interno do guia.
 * Aceita tanto o payload do Xtream (`start_timestamp`, `stop_timestamp`,
 * `title`, `description`) quanto entradas já no formato do XMLTV.
 */
function normalizeProgram(raw) {
  if (!raw) return null;

  const title = decodeMaybeBase64(raw.title || raw.name || raw.programme_title || '');
  const desc = decodeMaybeBase64(raw.desc || raw.description || raw.sub_title || '');
  const category = decodeMaybeBase64(raw.category || raw.genre || '');

  const start = toTimestampMs(raw.start !== undefined ? raw.start : (raw.start_timestamp !== undefined ? raw.start_timestamp : raw.startTs));
  const stop = toTimestampMs(raw.stop !== undefined ? raw.stop : (raw.stop_timestamp !== undefined ? raw.stop_timestamp : (raw.end !== undefined ? raw.end : raw.stopTs)));

  if (!Number.isFinite(start) || !Number.isFinite(stop) || stop <= start) return null;

  return {
    title: title || 'Programação',
    desc: desc || '',
    category: category || '',
    start,
    stop,
    durationMs: stop - start
  };
}

/** Normaliza uma lista inteira de programas, descartando os inválidos. */
function normalizePrograms(list) {
  if (!Array.isArray(list)) return [];
  const out = [];
  for (let i = 0; i < list.length; i++) {
    const p = normalizeProgram(list[i]);
    if (p) out.push(p);
  }
  out.sort((a, b) => a.start - b.start);
  return out;
}

/**
 * Junta programas de várias fontes (XMLTV do servidor + short EPG do Xtream +
 * qualquer fonte externa) sem duplicar o mesmo horário.
 *
 * A primeira fonte listada tem prioridade no título: o XMLTV costuma vir com
 * sinopse mais rica, o short EPG do Xtream às vezes traz o título "ao vivo".
 */
function mergeProgramSources() {
  const seen = new Set();
  const merged = [];

  for (let s = 0; s < arguments.length; s++) {
    const source = normalizePrograms(arguments[s]);
    for (let i = 0; i < source.length; i++) {
      const p = source[i];
      // Janela de 60s: mesmo programa com start slightly diferente = duplicata.
      let duplicate = false;
      for (let j = 0; j < merged.length; j++) {
        const other = merged[j];
        if (Math.abs(other.start - p.start) < MS_PER_MINUTE && Math.abs(other.stop - p.stop) < MS_PER_MINUTE) {
          duplicate = true;
          // Mantém a sinopse mais longa entre as duas fontes.
          if (!other.desc && p.desc) other.desc = p.desc;
          if (!other.category && p.category) other.category = p.category;
          break;
        }
      }
      if (!duplicate) {
        seen.add(p.start);
        merged.push(p);
      }
    }
  }

  merged.sort((a, b) => a.start - b.start);
  return merged;
}

/**
 * Monta a linha do tempo do guia: ordena, marca o programa "no ar" e calcula
 * o progresso (0-100) de cada programa.
 */
function buildTimeline(programs, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const list = normalizePrograms(programs);

  return list.map((p) => {
    const isLive = p.start <= now && p.stop > now;
    const elapsed = Math.max(0, now - p.start);
    const progressPct = Math.min(100, Math.max(0, Math.round((elapsed / p.durationMs) * 100)));
    const remainingMs = Math.max(0, p.stop - now);
    return {
      title: p.title,
      desc: p.desc,
      category: p.category,
      start: p.start,
      stop: p.stop,
      durationMs: p.durationMs,
      isLive,
      isPast: p.stop <= now,
      progressPct,
      remainingMs
    };
  });
}

/**
 * Escolhe o programa "no ar" e o próximo a partir de uma lista (normalizada ou não).
 * Se nenhum estiver no ar, devolve o primeiro ainda por passar; se a lista acabou,
 * devolve o último como "no ar" para não deixar a tela vazia.
 */
function pickNowNext(programs, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const list = normalizePrograms(programs);

  if (!list.length) return { current: null, next: null };

  let current = null;
  for (let i = 0; i < list.length; i++) {
    if (list[i].start <= now && list[i].stop > now) {
      current = list[i];
      break;
    }
  }

  if (!current) {
    // Nenhum no ar: usa o primeiro programa futuro; se todos passaram, o último.
    const upcoming = list.find((p) => p.start > now);
    current = upcoming || list[list.length - 1];
  }

  const idx = list.indexOf(current);
  const next = idx >= 0 && idx + 1 < list.length ? list[idx + 1] : null;

  return { current, next };
}

/** Minutos que faltam para o fim do programa (0 quando não está no ar). */
function minutesRemaining(program, nowMs) {
  if (!program) return 0;
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  if (!(program.start <= now && program.stop > now)) return 0;
  return Math.max(0, Math.round((program.stop - now) / MS_PER_MINUTE));
}

/** "HH:MM" em horário local. */
function formatClock(ms) {
  const t = toTimestampMs(ms);
  if (!Number.isFinite(t)) return '--:--';
  const d = new Date(t);
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}

/** "HH:MM" a partir de uma data já pronta (evita passar epoch). */
function formatClockDate(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return '--:--';
  return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
}

/**
 * Contagem regressiva formatada: "1:05:23" (hh:mm:ss) ou "05:23" (mm:ss).
 * Valores negativos ou inválidos viram "--:--".
 */
function formatCountdown(ms) {
  const total = Math.floor(Number(ms) / MS_PER_SECOND);
  if (!Number.isFinite(total) || total < 0) return '--:--';
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');
  if (hours > 0) return hours + ':' + mm + ':' + ss;
  return mm + ':' + ss;
}

/**
 * Resumo compacto do canal para a coluna esquerda do guia e para o HUD de
 * zapping: o que está no ar agora e o que vem a seguir.
 */
function summarizeChannel(channel, programs, nowMs) {
  const now = Number.isFinite(nowMs) ? nowMs : Date.now();
  const { current, next } = pickNowNext(programs, now);

  let currentTitle = '';
  let nextTitle = '';
  let countdownMs = 0;
  let progressPct = 0;
  let isLive = false;

  if (current) {
    isLive = current.start <= now && current.stop > now;
    currentTitle = current.title;
    progressPct = Math.min(100, Math.max(0, Math.round(((now - current.start) / current.durationMs) * 100)));
  }
  if (next) {
    nextTitle = next.title;
    countdownMs = Math.max(0, next.start - now);
  }

  return {
    name: (channel && (channel.name || channel.tvgName)) || 'Canal',
    logo: (channel && channel.logo) || '',
    group: (channel && channel.group) || '',
    currentTitle,
    nextTitle,
    countdownMs,
    countdownText: next ? formatCountdown(countdownMs) : '--:--',
    progressPct,
    isLive
  };
}

module.exports = {
  MS_PER_MINUTE,
  MS_PER_SECOND,
  toTimestampMs,
  decodeMaybeBase64,
  normalizeProgram,
  normalizePrograms,
  mergeProgramSources,
  buildTimeline,
  pickNowNext,
  minutesRemaining,
  formatClock,
  formatClockDate,
  formatCountdown,
  summarizeChannel
};
