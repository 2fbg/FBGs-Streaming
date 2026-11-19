const test = require('node:test');
const assert = require('node:assert/strict');
const PublicEpg = require('../../api/epg.js');

test('resolveChannelSlug normaliza acentos, sufixos HD/FHD/4K/H265 e resolve canais brasileiros', () => {
  assert.equal(PublicEpg.resolveChannelSlug('TV Globo SP FHD H265'), 'GRD');
  assert.equal(PublicEpg.resolveChannelSlug('SBT 4K HEVC'), 'SBT');
  assert.equal(PublicEpg.resolveChannelSlug('ESPN Brasil HD'), 'ESP');
  assert.equal(PublicEpg.resolveChannelSlug('SporTV 2 FHD'), 'SP2');
  assert.equal(PublicEpg.resolveChannelSlug('Premiere Clubes'), 'PRE');
  assert.equal(PublicEpg.resolveChannelSlug('Canal Desconhecido XYZ 999'), null);
});

test('parseMeuGuiaHtml extrai lista de programas com horarios e vira o dia apos meia-noite', () => {
  const sampleHtml = `
    <html><body>
      <ul>
        <li>
          <a title="Jornal Nacional">
            <div class="time">20:30</div>
            <h2>Jornal Nacional</h2>
            <h3>Jornalismo</h3>
          </a>
        </li>
        <li>
          <a title="Novela das Nove">
            <div class="time">21:20</div>
            <h2>Novela das Nove</h2>
            <h3>Novela</h3>
          </a>
        </li>
        <li>
          <a title="Jornal da Globo">
            <div class="time">00:15</div>
            <h2>Jornal da Globo</h2>
            <h3>Jornalismo</h3>
          </a>
        </li>
      </ul>
    </body></html>
  `;

  // Referencia: 2026-11-18 20:45 BRT (23:45 UTC)
  const nowMs = Date.UTC(2026, 10, 18, 23, 45, 0);
  const list = PublicEpg.parseMeuGuiaHtml(sampleHtml, nowMs);

  assert.equal(list.length, 3);
  assert.equal(list[0].title, 'Jornal Nacional');
  assert.equal(list[0].desc, 'Categoria: Jornalismo');
  assert.equal(list[1].title, 'Novela das Nove');
  assert.equal(list[2].title, 'Jornal da Globo');

  // Garante ordenacao crescente mesmo passando pela meia-noite (21:20 -> 00:15)
  assert.ok(list[0].stop === list[1].start);
  assert.ok(list[1].stop === list[2].start);
  assert.ok(list[2].start > list[1].start);
  assert.ok(list[2].stop > list[2].start);
});

test('parseMeuGuiaHtml ignora HTML vazio ou malformado sem lancar excecao', () => {
  assert.deepEqual(PublicEpg.parseMeuGuiaHtml('', Date.now()), []);
  assert.deepEqual(PublicEpg.parseMeuGuiaHtml('<div>sem horarios</div>', Date.now()), []);
  assert.deepEqual(PublicEpg.parseMeuGuiaHtml(null, Date.now()), []);
});
