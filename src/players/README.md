# Player Factory multiplataforma (v2.7.0)

Implementação da especificação `FBG2-PLAYER-FACTORY-ESPECIFICACAO.md`, com as
correções apontadas na revisão técnica da API Samsung.

```
src/players/
├── index.ts                      # exports públicos
├── PlayerFactory.ts              # detecção + instanciação
├── types/player.ts               # IPlayer, eventos, estados, tipos
├── utils/logger.ts               # log com gating (FBG2_DEBUG)
├── players/
│   ├── BasePlayer.ts             # emitter + estado + buffer
│   ├── TizenAVPlayPlayer.ts      # Samsung AVPlay (hardware)
│   ├── WebOSVideoPlayer.ts       # LG webOS (<video> nativo)
│   └── HLSFallbackPlayer.ts      # hls.js / HLS nativo
└── __tests__/
    ├── PlayerFactory.test.ts
    ├── TizenAVPlayPlayer.test.ts
    └── HLSFallbackPlayer.test.ts
```

## Comandos

```bash
npm ci                     # instala deps (hls.js + toolchain)
npm run typecheck          # tsc --noEmit (strict)
npm test                   # jest src/players
```

## Uso

```ts
import { PlayerFactory } from './players';
import type { ErrorPayload } from './players';

const player = PlayerFactory.create(document.getElementById('tv-video') as HTMLVideoElement);

await player.play('https://example.com/stream.m3u8', { bufferSizeMs: 4000 });

player.on('error', (payload) => {
  const err = payload as ErrorPayload;
  if (!err.fatal) return;
  player.play(proximaFonte()).catch(mostrarErro);
});

player.on('tracksReady', (payload) => {
  const { audioTracks, subtitleTracks } = payload as TracksReadyPayload;
  renderAudioMenu(audioTracks, subtitleTracks);
});
```

## Correções aplicadas sobre a especificação

| Item | Especificação | Implementado |
|---|---|---|
| Área de vídeo no Tizen | `avplay.setDisplay(rect)` | `setDisplayRect(l, t, w, h)`; reaplicado em `resize`/`fullscreenchange` e ignorado quando o container está `0×0` (evita tela preta) |
| Buffer no Tizen | `setStreamingProperty('BUFFER_SIZE', ms)` | mantido por compatibilidade **e** `setBufferingParam('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', s)` — a API oficial, em segundos, só em `IDLE` |
| Ciclo de vida do AVPlay | `stop()` + `close()` no mesmo `try` | `safeClose()` com dois `try`: falha no `stop` não impede o `close` (senão o próximo `open()` dá `InvalidStateError` para sempre) |
| Faixas de áudio | `s.info.track_num` | `extra_info` é **JSON string** na API real: parse + `index`, com fallback para `info` e `getTotalTrackInfo()` |
| Falha de `prepare` | ficava com o AVPlay aberto | fecha, emite `error` fatal e permite novo `play()` |
| `play()` no hls.js | `await element.play()` antes do manifesto | espera `MANIFEST_PARSED` (timeout 15s) — antes rejeitava com `NotSupportedError` e virava erro fatal |
| Faixas no hls.js | `levels` mapeados como "audioTracks" | `hls.audioTracks` (faixas reais) + `AUDIO_TRACKS_UPDATED`; `levels` são qualidades |
| `destroyed` | `teardownListeners()` antes do `emit` | `finishDestroy()` emite e só depois limpa |
| `create(null)` | sem validação | lança `elemento <video> obrigatório` |
| AVPlay singleton | duas instâncias colidiam | factory reaproveita a instância do mesmo `<video>`; `releaseTizen()` libera |
| Estado/buffer | ausentes na interface | `getState()`, `getBufferedMs()`, `setVolume()`, `setSubtitleTrack()` |

## Estado da migração

**Fase 2 concluída no build (2.7.1).** O app da TV consome a factory através de
`smart-tv/mk21-tv/src/player-service.js`; `app.js` é gerado por `npm run build:tv`
(IIFE, ~178 KB) e a OTA continua entregando só `app.js` + `styles.css`.

O motor entra por flag — `localStorage.FBG2_PLAYER_LEGACY=1` → `FBG2_PLAYER_ENGINE`
→ `playerEngine` do `version.json` → default. Até a validação em TV real (8/8), o
`version.json` publicado fica em `"playerEngine": "legacy"`; no aparelho, para
testar a factory: `localStorage.setItem('FBG2_PLAYER_ENGINE','factory')` e recarregar.

Detalhes, mapa do legado e checklist: `smart-tv/mk21-tv/MIGRACAO-FASE2-NOTAS.md`.
