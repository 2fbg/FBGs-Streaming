(function () {
  function normalize(value) {
    return String(value || '').trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function identity(item) {
    const type = String(item.contentType || 'LIVE').toUpperCase();
    const name = normalize(item.name);
    const tvgId = normalize(item.tvgId || item['tvg-id']);
    if (tvgId) {
      return `id:${tvgId}|${type}${type === 'LIVE' ? '' : `|${name}`}`;
    }
    return `name:${name}|category:${normalize(item.group)}|${type}`;
  }

  function sourceUrls(item) {
    return Array.from(new Set(
      [item && item.url].concat(item && Array.isArray(item.alternateUrls) ? item.alternateUrls : [])
        .map(url => String(url || '').trim())
        .filter(Boolean)
    ));
  }

  function matchesAcrossServers(source, candidate) {
    if (!source || !candidate) return false;
    const sourceType = String(source.contentType || '').toUpperCase();
    const candidateType = String(candidate.contentType || '').toUpperCase();
    if (!sourceType || sourceType !== candidateType) return false;

    const sourceId = normalize(source.tvgId || source['tvg-id']);
    const candidateId = normalize(candidate.tvgId || candidate['tvg-id']);
    if (sourceId && candidateId) return sourceId === candidateId;

    const cleanName = item => normalize(item.tvgName || item.name)
      .replace(/\b(fhd|uhd|4k|2k|1080p|720p|hd|sd|brasil|br)\b/g, '')
      .replace(/[^\p{L}\p{N}]+/gu, ' ')
      .trim();
    const sourceName = cleanName(source);
    const candidateName = cleanName(candidate);
    return !!sourceName && sourceName === candidateName;
  }

  function merge(existing, incoming) {
    const urls = sourceUrls(existing).concat(sourceUrls(incoming));
    const uniqueUrls = Array.from(new Set(urls));
    return Object.assign({}, existing, {
      alternateUrls: uniqueUrls.slice(1)
    });
  }

  function createGrouper() {
    const itemsByIdentity = new Map();
    return {
      add(item) {
        const key = identity(item);
        const existing = itemsByIdentity.get(key);
        if (!existing) {
          const canonical = Object.assign({}, item, { alternateUrls: [] });
          itemsByIdentity.set(key, canonical);
          return { item: canonical, isNew: true };
        }
        const merged = merge(existing, item);
        existing.alternateUrls = merged.alternateUrls;
        return { item: existing, isNew: false };
      },
      clear() {
        itemsByIdentity.clear();
      }
    };
  }

  const api = { createGrouper, identity, matchesAcrossServers, merge, sourceUrls };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.MK21AlternativeSources = api;
})();
