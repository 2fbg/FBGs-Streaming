// smart-tv/mk21-tv/workers/playlistWorker.js - Parser Assíncrono Isolado
self.onmessage = async (e) => {
  const { m3uText, batchSize = 500 } = e.data;
  const lines = m3uText.split(/\r?\n/);
  const totalLines = lines.length;
  let currentItem = null;
  let batch = [];

  for (let i = 0; i < totalLines; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    if (line.startsWith('#EXTINF:')) {
      currentItem = parseExtInf(line);
    } else if (line.startsWith('http://') || line.startsWith('https://')) {
      if (currentItem) {
        currentItem.url = line;
        batch.push(currentItem);
        currentItem = null;

        if (batch.length >= batchSize) {
          self.postMessage({ type: 'BATCH_READY', items: batch });
          batch = [];
          await new Promise(r => setTimeout(r, 0));
        }
      }
    }
    if (i % 2000 === 0) {
      self.postMessage({ type: 'PROGRESS', percent: Math.round((i / totalLines) * 100) });
    }
  }

  if (batch.length > 0) self.postMessage({ type: 'BATCH_READY', items: batch });
  self.postMessage({ type: 'COMPLETE' });
};

function parseExtInf(line) {
  const nameMatch = line.match(/,(.+)$/);
  const logoMatch = line.match(/tvg-logo="([^"]+)"/);
  const groupMatch = line.match(/group-title="([^"]+)"/);
  const idMatch = line.match(/tvg-id="([^"]+)"/);

  return {
    name: nameMatch ? nameMatch[1].trim() : 'Sem Nome',
    logo: logoMatch ? logoMatch[1] : null,
    category: groupMatch ? groupMatch[1].trim() : 'Geral',
    tvgId: idMatch ? idMatch[1] : null,
    isAdult: /adult|xxx|erotic|18\+/i.test(groupMatch ? groupMatch[1] : '')
  };
}
