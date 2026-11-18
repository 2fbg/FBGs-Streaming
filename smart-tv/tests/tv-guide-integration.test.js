#!/usr/bin/env node
/**
 * MK21 Play — Teste de integração (Chromium/Playwright) das funcionalidades
 * portadas do Saimo TV na Smart TV:
 *
 *   1. login contra um servidor IPTV simulado (Xtream + XMLTV);
 *   2. Guia de Programação com grade REAL vinda do XMLTV;
 *   3. HUD de zapping com "NO AR" / "A SEGUIR" e contagem regressiva;
 *   4. teclado virtual navegável pelo D-pad.
 *
 * Uso: node smart-tv/tests/tv-guide-integration.test.js
 * Requer: npx playwright install chromium (já instalado no CI da imagem).
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const path = require('node:path');
const fs = require('node:fs');

// O Playwright é opcional: o CI não instala navegador. Sem ele, o teste é
// ignorado (e não quebrado). Para rodar localmente:
//   npm i -D playwright && npx playwright install chromium
let chromium = null;
try {
  chromium = require('playwright').chromium;
} catch (e) {
  chromium = null;
}

const TV_DIR = path.join(__dirname, '..', 'mk21-tv');
const BASE_URL = 'http://tocombox21.cyou';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml'
};

// --- Grade EPG simulada (formato XMLTV) ------------------------------------
// A grade é montada RELATIVA ao agora: o primeiro programa está no ar e o
// segundo começa na próxima hora — é o que o HUD de zapping espera encontrar.
function buildXmltv() {
  const base = Math.floor(Date.now() / 3600000) * 3600000;
  const fmt = (ms) => {
    const d = new Date(ms);
    const p = (n, w = 2) => String(n).padStart(w, '0');
    return `${p(d.getUTCFullYear(), 4)}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())} +0000`;
  };
  const programme = (ch, startMs, stopMs, title, desc) =>
    `  <programme start="${fmt(startMs)}" stop="${fmt(stopMs)}" channel="${ch}">` +
    `<title lang="pt">${title}</title><desc>${desc}</desc><category>Variedades</category></programme>`;

  return `<?xml version="1.0" encoding="UTF-8"?>
<tv generator-info-name="mock">
  <channel id="globo.br"><display-name>Globo</display-name></channel>
  <channel id="sbt.br"><display-name>SBT</display-name></channel>
${programme('globo.br', base, base + 3600000, 'Jornal Nacional', 'O principal jornal da noite')}
${programme('globo.br', base + 3600000, base + 7200000, 'Globo Esporte', 'Futebol ao vivo')}
${programme('sbt.br', base, base + 1800000, 'Programa do Ratinho', ' humor da noite')}
</tv>`;
}

// --- Servidor IPTV simulado ------------------------------------------------
function startMockServer() {
  const xmltv = buildXmltv();
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, BASE_URL);
    const action = url.searchParams.get('action') || '';

    const json = (obj) => {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      res.end(JSON.stringify(obj));
    };

    if (url.pathname.endsWith('/xmltv.php')) {
      res.writeHead(200, { 'Content-Type': 'application/xml', 'Access-Control-Allow-Origin': '*' });
      res.end(xmltv);
      return;
    }

    if (url.pathname.endsWith('/player_api.php')) {
      if (action === 'get_live_categories') {
        json([{ category_id: '1', category_name: 'Abertos' }, { category_id: '2', category_name: 'Variedades' }]);
        return;
      }
      if (action === 'get_live_streams') {
        json([
          // Globo sem epg_channel_id: o app amarra pelo <display-name> do XMLTV.
          { stream_id: 101, name: 'Globo', category_id: '1', stream_icon: 'http://logo/globo.png', tv_archive: 0 },
          // SBT com epg_channel_id: o app amarra pelo id exato do XMLTV.
          { stream_id: 102, name: 'SBT', category_id: '2', stream_icon: 'http://logo/sbt.png', epg_channel_id: 'sbt.br', tv_archive: 0 }
        ]);
        return;
      }
      if (action === 'get_vod_streams') { json([]); return; }
      if (action === 'get_series') { json([]); return; }
      if (action === 'get_short_epg') {
        json({ epg_listings: [] });
        return;
      }
      json({ user_info: { auth: 1, status: 'Active', username: 'demo', exp_date: '2030-01-01 00:00:00' }, server_info: { url: BASE_URL } });
      return;
    }

    if (url.pathname.endsWith('/get.php')) {
      res.writeHead(200, { 'Content-Type': 'audio/x-mpegurl', 'Access-Control-Allow-Origin': '*' });
      res.end('#EXTM3U\n');
      return;
    }

    res.writeHead(404, { 'Access-Control-Allow-Origin': '*' });
    res.end('not found');
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port }));
  });
}

// --- Servidor de arquivos estáticos (app da TV) ----------------------------
function startStaticServer() {
  const server = http.createServer((req, res) => {
    let rel = decodeURIComponent(req.url.split('?')[0]);
    if (rel === '/') rel = '/index.html';
    const file = path.join(TV_DIR, rel);
    if (!file.startsWith(TV_DIR) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404);
      res.end('not found');
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port }));
  });
}

test('Guia de Programação, HUD de zapping e teclado virtual funcionam na Smart TV', async (t) => {
  if (!chromium) {
    t.skip('Playwright não instalado (opcional): npm i -D playwright && npx playwright install chromium');
    return;
  }

  const mock = await startMockServer();
  const statics = await startStaticServer();
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();

  const errors = [];
  page.on('pageerror', (err) => errors.push(String(err)));
  page.setDefaultTimeout(25000);

  // Redireciona o domínio do servidor padrão para o mock local.
  await page.route('**://tocombox21.cyou/**', async (route) => {
    const target = new URL(route.request().url());
    const rewritten = `http://127.0.0.1:${mock.port}${target.pathname}${target.search}`;
    try {
      const response = await route.fetch({ url: rewritten });
      await route.fulfill({ response });
    } catch (e) {
      await route.fulfill({ status: 502, body: 'mock offline' });
    }
  });

  // Assets externos (FontAwesome / CDN): o app usa document.write no <head>,
  // então abortar a requisição quebraria o parse do documento. Servimos stubs.
  await page.route('**://cdnjs.cloudflare.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'text/css', body: '/* stub */' }));
  await page.route('**://cdn.jsdelivr.net/**', (route) =>
    route.fulfill({ status: 200, contentType: 'text/javascript', body: '/* stub */' }));

  await page.goto(`http://127.0.0.1:${statics.port}/index.html`, { waitUntil: 'domcontentloaded' });

  // --- 1. Login contra o servidor simulado --------------------------------
  await page.fill('#user-input', 'demo');
  await page.fill('#pass-input', 'demo');
  await page.click('#btn-login');

  await page.waitForFunction(() => {
    const el = document.getElementById('home-container');
    return el && el.style.display !== 'none';
  }, { timeout: 20000 });

  // Espera o catálogo ao vivo terminar de carregar (a tela principal abre antes).
  await page.waitForFunction(() => {
    const list = document.getElementById('listItems');
    return list && list.querySelectorAll('.list-item-btn').length >= 2;
  }, { timeout: 20000 });

  // --- 2. Guia de Programação com grade real ------------------------------
  const guide = await page.evaluate(async () => {
    await window.mk21App.loadXmltv(true);
    window.mk21App.openGuide();
    await new Promise((r) => setTimeout(r, 900));

    const channelList = document.getElementById('listGuideChannels');
    const programList = document.getElementById('listGuidePrograms');
    return {
      guideVisible: !document.getElementById('sectionGuide').classList.contains('hidden'),
      channelCount: channelList ? channelList.querySelectorAll('.guide-channel-btn').length : 0,
      channelNames: channelList ? Array.from(channelList.querySelectorAll('.guide-channel-name')).map((n) => n.textContent.trim()) : [],
      nowLabels: channelList ? Array.from(channelList.querySelectorAll('.guide-channel-now')).map((n) => n.textContent.trim()) : [],
      programTitles: programList ? Array.from(programList.querySelectorAll('.guide-prog-title')).map((n) => n.textContent.trim()) : [],
      liveRows: programList ? programList.querySelectorAll('.guide-prog-row.is-live').length : 0
    };
  });

  assert.equal(guide.guideVisible, true, 'a seção do Guia deve ficar visível');
  assert.ok(guide.channelCount >= 2, `esperava 2+ canais no guia, veio ${guide.channelCount}`);
  assert.ok(guide.channelNames.includes('Globo'), 'o canal Globo deve aparecer na coluna de canais');
  assert.ok(
    guide.nowLabels.some((t) => t.includes('Jornal Nacional')),
    `a coluna de canais deve mostrar o que está no ar, veio: ${JSON.stringify(guide.nowLabels)}`
  );
  assert.ok(
    guide.programTitles.includes('Jornal Nacional') && guide.programTitles.includes('Globo Esporte'),
    `a grade do canal deve vir do XMLTV real, veio: ${JSON.stringify(guide.programTitles)}`
  );
  assert.ok(guide.liveRows >= 1, 'o programa no ar deve receber o destaque "is-live"');

  // --- 3. Navegação D-pad dentro do Guia ----------------------------------
  const guideNav = await page.evaluate(async () => {
    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
    press('ArrowRight', 39);   // vai para a coluna de programação
    await new Promise((r) => setTimeout(r, 120));
    const pane = window.mk21App.guideState().pane;
    press('ArrowDown', 40);
    await new Promise((r) => setTimeout(r, 120));
    const programIndex = window.mk21App.guideState().programIndex;
    return { pane, programIndex };
  });

  assert.equal(guideNav.pane, 'programs', 'a seta para a direita deve trocar de coluna');
  assert.equal(guideNav.programIndex, 1, 'a seta para baixo deve andar na grade de programas');

  // --- 4. VOLTAR fecha o Guia ---------------------------------------------
  const closed = await page.evaluate(async () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));
    await new Promise((r) => setTimeout(r, 250));
    return {
      guideOpen: window.mk21App.guideState().open,
      guideHidden: document.getElementById('sectionGuide').classList.contains('hidden'),
      unifiedVisible: !document.getElementById('sectionUnified').classList.contains('hidden')
    };
  });

  assert.equal(closed.guideOpen, false, 'o Guia deve fechar no VOLTAR');
  assert.equal(closed.guideHidden, true, 'a seção do Guia deve ficar oculta');
  assert.equal(closed.unifiedVisible, true, 'a tela principal deve voltar');

  // --- 4b. Sanidade: a navegação normal do app continua funcionando -------
  const afterClose = await page.evaluate(async () => {
    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
    const items = document.getElementById('listItems');
    const before = items ? items.querySelectorAll('.list-item-btn').length : 0;
    press('ArrowDown', 40);
    await new Promise((r) => setTimeout(r, 150));
    press('Enter', 13);
    await new Promise((r) => setTimeout(r, 400));
    return {
      items: before,
      guideStillClosed: !window.mk21App.guideState().open,
      oskClosed: document.getElementById('oskOverlay').classList.contains('hidden')
    };
  });

  assert.ok(afterClose.items >= 2, 'a lista de itens deve continuar populada');
  assert.equal(afterClose.guideStillClosed, true, 'o Guia não pode reabrir sozinho');
  assert.equal(afterClose.oskClosed, true, 'o teclado virtual não pode abrir sozinho');

  // --- 5. HUD de zapping ---------------------------------------------------
  const zap = await page.evaluate(async () => {
    const channel = window.mk21App.guideState().channels[0];
    window.mk21App.showZapHud(channel);
    await new Promise((r) => setTimeout(r, 250));
    const hud = document.getElementById('zapHud');
    return {
      visible: !hud.classList.contains('hidden'),
      name: document.getElementById('zapHudName').textContent.trim(),
      now: document.getElementById('zapHudNow').textContent.trim(),
      next: document.getElementById('zapHudNext').textContent.trim(),
      countdown: document.getElementById('zapHudCountdown').textContent.trim(),
      progress: document.getElementById('zapHudProgressFill').style.width
    };
  });

  assert.equal(zap.visible, true, 'o HUD de zapping deve aparecer');
  assert.equal(zap.name, 'Globo', 'o HUD deve mostrar o nome do canal');
  assert.ok(zap.now.includes('Jornal Nacional'), `o HUD deve mostrar o NO AR, veio: ${zap.now}`);
  assert.ok(zap.next.includes('Globo Esporte'), `o HUD deve mostrar o A SEGUIR, veio: ${zap.next}`);
  assert.notEqual(zap.countdown, '--:--', 'o HUD deve mostrar a contagem regressiva');
  assert.notEqual(zap.progress, '', 'o HUD deve mostrar a barra de progresso');

  // --- 6. Teclado virtual (D-pad) ----------------------------------------
  const osk = await page.evaluate(async () => {
    const press = (key, code) => document.dispatchEvent(new KeyboardEvent('keydown', { key, code, keyCode: code, which: code, bubbles: true }));
    window.mk21App.openOsk(document.getElementById('inputSearch'));
    await new Promise((r) => setTimeout(r, 200));

    const keys = document.querySelectorAll('#oskGrid .osk-key');
    const open = !document.getElementById('oskOverlay').classList.contains('hidden');

    // "g" fica na 2a linha, 5a coluna (q w e r t y u i o p / a s d f g ...)
    press('ArrowDown', 40);
    press('ArrowRight', 39);
    press('ArrowRight', 39);
    press('ArrowRight', 39);
    press('ArrowRight', 39);
    press('Enter', 13);
    await new Promise((r) => setTimeout(r, 120));
    const afterG = document.getElementById('oskPreview').textContent.trim();

    press('Enter', 13); // agora a tecla seguinte ("h") -> "gh"
    await new Promise((r) => setTimeout(r, 120));
    const afterH = document.getElementById('oskPreview').textContent.trim();

    // Backspace apaga o último caractere
    press('Backspace', 8);
    await new Promise((r) => setTimeout(r, 120));
    const afterBackspace = document.getElementById('oskPreview').textContent.trim();

    // Fecha com VOLTAR sem confirmar
    press('Escape', 27);
    await new Promise((r) => setTimeout(r, 150));

    return {
      open,
      keyCount: keys.length,
      afterG,
      afterH,
      afterBackspace,
      closed: document.getElementById('oskOverlay').classList.contains('hidden'),
      searchValue: document.getElementById('inputSearch').value
    };
  });

  assert.equal(osk.open, true, 'o teclado virtual deve abrir');
  assert.ok(osk.keyCount >= 40, `o teclado deve ter todas as teclas, veio ${osk.keyCount}`);
  assert.equal(osk.afterG, 'g', `a tecla "g" deve ser digitada, veio "${osk.afterG}"`);
  assert.equal(osk.afterH, 'gh', `a segunda tecla deve ser digitada em sequência, veio "${osk.afterH}"`);
  assert.equal(osk.afterBackspace, 'g', 'o backspace deve apagar o último caractere digitado');
  assert.equal(osk.closed, true, 'o VOLTAR deve fechar o teclado virtual');
  assert.equal(osk.searchValue, '', 'sem confirmar, o campo de busca não deve ser alterado');

  assert.deepEqual(errors, [], 'nenhum erro de JS deve ocorrer durante o fluxo');

  await browser.close();
  mock.server.close();
  statics.server.close();
});
