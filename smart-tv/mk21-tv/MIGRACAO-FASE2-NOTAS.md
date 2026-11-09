# Fase 2 — leitura obrigatória (gate da Seção 2)

Inventário levantado no código real em `smart-tv/mk21-tv/app.js` (7.058 linhas) **antes**
de qualquer edição. Números de linha referentes ao `app.js` 2.7.0 (commit `8d7b17f`);
depois da migração esse arquivo passa a ser gerado por `npm run build:tv` e o fonte
legado vive em `src/legacy/app-legacy.js`.

## Play

| Item | Valor real |
|---|---|
| Função de play | `function playStream(item, forcedUrl)` — linha 3397 |
| Enfileiramento | `zappingLockPromise = zappingLockPromise.catch(() => {}).then(async () => { ... })` — zapping serializado, com `playAbortController.signal` para abortar o anterior |
| Stop antes de tocar | `stopCurrentPlayer(false)` (linha 3186), que destrói `mpegtsPlayer`, `hlsInstance` e limpa o `<video>` |
| Seleção de engine (dentro de `playStream`) | `isM3U8 && window.Hls` → hls.js; `isTS && window.mpegts` → mpegts.js; senão `<video src>` nativo; com fallbacks `fallbackToNativeTs()` e `fallbackToHls()` |
| Elemento | `$('tvPlayer')` (`<video id="tvPlayer">`), criado no HTML |
| Call sites de `playStream` | `playNextChannel` (3737), `playPreviousChannel` (3746), player de lista/E PG, `triggerAutoReconnect` (2838), `mk21TryAlternativeSource` (core/AlternativeSources.js) |

## Watchdog

| Item | Valor real |
|---|---|
| Armar | `startStallWatchdog(streamKey)` — linha 2878 |
| Desarmar | `stopStallWatchdog()` — linha 2809 (`stallWatchdogInterval`) |
| Timer | `stallWatchdogInterval = setInterval(..., 1000)` — linha 2926 |
| Sinais | listeners `waiting`/`stalled`/`seeking` (`marcarEngasgo`), `progress` (crescimento de buffer), `playing` (reset) |
| Limite | `mk21StallTolerance()` (linha 3244) → `MK21_BUFFER_MODES[modo].stallTolerance` (auto 10, fast?, stable?) |
| Reação | `triggerAutoReconnect(cause)` — linha 2815, com `MAX_AUTO_RECONNECT_ATTEMPTS = 5` (linha 2785) e backoff `1000 * 1.5^(n-1)` até 6 s; esgotado → `mk21TryAlternativeSource()` |

O watchdog lê **apenas** `video.currentTime`, `video.paused` e `video.buffered` — no Tizen
com AVPlay o `<video>` é inerte, então ele precisa ler o relógio da factory quando o
PlayerService estiver ativo (feito na Seção 5).

## Seek (todos em SEGUNDOS)

| Linha | Contexto | Código |
|---|---|---|
| 3444 | retomar VOD salvo (`item.currentTime`, segundos) no `loadedmetadata` | `video.currentTime = resumeAt` |
| 3803 | botão ⏪ do HUD tela cheia | `v.currentTime = Math.max(0, (v.currentTime \|\| 0) - 10)` |
| 3813 | botão ⏩ do HUD tela cheia | `v.currentTime = Math.min(v.duration, (v.currentTime \|\| 0) + 10)` |
| 5904 | seta esquerda no player (VOD) | idem −10 s |
| 5918 | seta direita no player (VOD) | idem +10 s |

Conversão adotada: helper `mk21SeekBy(deltaMs)` no legado → `±10000` ms; com a factory
ativa vai para `PlayerService.seekMs()`, sem factory converte de volta para segundos no
`<video>`.

## Teclas e controles do player

- Teclado/D-pad: handler único em `mk21HandlePlayerKeys` (a partir de ~5860) — setas,
  Enter, Voltar.
- Play/Pause tem **duas** declarações: `togglePlayPause()` em 3874 e outra em 5358 (a
  segunda vence em script clássico; em módulo ES também). A migração altera a versão
  efetiva (5358) para alternar via service.
- Áudio/legendas: `openAudioSubsModal()` (3926) lê `hlsInstance.audioTracks` e depois
  `video.audioTracks`; ganhou um terceiro ramo para as faixas da factory.
- Velocidade: `applyPlaybackSpeed(rate)` / `cyclePlaybackSpeed()` → `video.playbackRate`
  (não se aplica ao AVPlay; documentado como limitação).

## index.html e empacotamento

- Scripts no `<head>`: `libs/hls.min.js`, `libs/mpegts.min.js`, `core/AlternativeSources.js`
  (globais `Hls`/`mpegts`) e um `document.write` de CDN como plano B.
- `app.js` **não** tem `<script>` fixo: o bootloader OTA no fim do `<body>` injeta
  `app.js?v=2.7.0` (ou o blob do cache OTA). Por isso o bundle precisa ser um IIFE
  clássico, sem `import`/`import.meta`.
- Nenhum handler inline do HTML chama função do app (só `onerror`/`onsubmit`), então
  envolver o legado num IIFE não quebra o HTML.
- `package_ipk.py` empacota `os.walk(src_dir)` inteiro → `src/` e arquivos de build
  precisam ser excluídos do IPK.

## Decisões de escopo tomadas a partir do código real

1. A factory assume **HLS (`.m3u8`), DASH (`.mpd`) e progressivo (`.mp4/.m4v/.webm/.mov`)**.
   `.ts` continua no pipeline mpegts legado: é o caminho mais testado em produção e o
   AVPlay não abre MPEG-TS cru com a mesma confiabilidade.
2. `hls.js` **não** é embutido no bundle: `esbuild` aponta `hls.js` para um shim que
   reexporta o `window.Hls` já carregado por `libs/hls.min.js` (mantém o app.js < 350 KB e
   o caminho de CDN existente).
3. O watchdog legado continua sendo o dono da reconexão; quando a factory está ativa ele
   passa a ler o relógio dela e a UI legada (HUD, badges, banner) continua igual.
4. Flag tripla: `FBG2_PLAYER_LEGACY=1` (localStorage) > `playerEngine` do `version.json`
   (cacheado) > default `true`.

## Riscos conhecidos para a Seção 6 (TV real)

- AVPlay não suporta `playbackRate` nem legendas WebVTT — velocidade/legendas ficam sem
  efeito no Tizen.
- `setDisplayRect` depende de o `<video>` ter área real; PiP/multi-tela precisa de teste.
- Sem Tizen Studio/emulador nesta VM, nenhum cenário da Seção 6 pode ser executado aqui.

---

# Execução (Seções 3 a 8)

## Build (Seção 3)

- `esbuild.config.mjs` na raiz + script `npm run build:tv`.
- `src/main.js` (entry) → `src/player-service.js` (adapta a factory) → `src/legacy/app-legacy.js`
  (o antigo `app.js` inteiro, sem refatoração de módulos — decisão da Seção 3.3).
- `hls.js` resolve para `src/shims/hls-global.js`, que reexporta o `window.Hls` já carregado por
  `libs/hls.min.js`. Isso mantém `app.js` em **177,8 KB** (limite 350 KB) e preserva o fallback de CDN.
- Saída sem sourcemap, `target es2019`, `format iife` — o bootloader OTA consegue rodar por blob.
- `package_ipk.py` passou a excluir `src/`, `tests`, `node_modules` e estas notas do IPK.

## PlayerService (Seção 4)

`src/player-service.js` é a versão final do adaptador do documento, com quatro ajustes necessários
para o código real:

1. não importa a UI legada: o app registra `setHooks({...})` (evita import circular e mantém a UI
   dona do HUD/badge/watchdog);
2. a factory só assume HLS/DASH/progressivo (`FACTORY_URL_RE`), `.ts` fica no mpegts legado;
3. `tryPlay()` devolve `false` em vez de `void` — o `playStream` legado continua o fluxo quando a
   factory não assume;
4. guarda de zapping por token (`#playToken`): `play()` abortado por troca de canal não vira erro.

O failover do serviço não foi usado: quem reconecta é o `triggerAutoReconnect` legado (já testado em
produção), chamado pelos hooks `onError`. Menos caminho novo, menos regressão.

## Migração do playStream (Seção 5)

| Ponto | O que foi feito |
|---|---|
| `playStream` | depois de toda a papelada (activeItem, banner, faixas, watchdog) chama `FBG2PlayerService.tryPlay(item, streamUrl)` e sai se assumiu |
| `stopCurrentPlayer` | chama `service.stop()` antes de mexer no `<video>` (AVPlay é singleton) |
| Watchdog | quando a factory está ativa, lê `getCurrentTimeSec()`/`getState()` em vez de `video.currentTime`; respeita `paused/buffering/preparing` |
| Seeks (5) | helper `mk21SeekBy(deltaMs)`; call sites passaram a usar **ms** (`±10000`) |
| Teclas | `togglePlayPause` usa `service.toggle()` quando a factory está ativa |
| Áudio | `openAudioSubsModal` ganhou ramo com as faixas normalizadas do serviço |
| Linha do tempo VOD | `updateVodProgressTimeline` usa o relógio do serviço (AVPlay não emite `timeupdate` no `<video>`) |
| Buffer | `cycleBufferMode` reaplica o modo no serviço (`setBufferMs`) |
| `index.html` | carrega `$WEBAPIS/webapis/webapis.js` só quando o UA é Tizen, antes do bundle |
| Paridade | `window.mk21App` expõe `playStream/stopCurrentPlayer/switchContentType/openAudioSubsModal` (o IIFE esconde o escopo global) |

## Flag e rollback (Seções 1 e 7)

Precedência: `localStorage.FBG2_PLAYER_LEGACY=1` → `localStorage.FBG2_PLAYER_ENGINE`
(`factory`/`legacy`) → `playerEngine` do `version.json` (buscado no boot, em memória) →
default `true` do código.

O `version.json` publicado vai com `"playerEngine": "legacy"` **até a Seção 6 passar** (8/8 em TV
real). Para validar no aparelho antes do go-live, rode uma vez no console do app:

```js
localStorage.setItem('FBG2_PLAYER_ENGINE', 'factory'); // e recarregue
```

Depois de 8/8, basta trocar o campo para `"factory"` — o app troca de motor na próxima checagem,
sem novo deploy.

## Versão

`2.7.1` em `appinfo.json` (webOS e Tizen), `config.xml`, `index.html` (`?v=2.7.1`, corte de OTA),
`BASE_PACKAGE_VERSION`, `package.json`/lock e nos sete `version.json`. IPK:
`smart-tv/mk21play_2.7.1_all.ipk`.

## Seção 6 — pendente (sem TV/emulador nesta máquina)

Nada aqui substitui TV real. O que foi verificado localmente:

| Verificação local | Resultado |
|---|---|
| `npm run build:tv` | 177,8 KB, `node --check` OK |
| `tsc --noEmit` | OK |
| `jest src/players` | 28/28 |
| app 2.7.1 no navegador (HLS) | toca, buffering/playing/erro → hooks da UI, seek em ms, pause/resume |
| `.mp4` VOD | decisão de engine coberta por teste (nativo, sem hls.js); streaming real bloqueado (hosts externos 403 nesta VM) |
| Tizen AVPlay | só com mock nos testes — falta aparelho |
