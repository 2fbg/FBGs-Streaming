# Changelog — FBGs-Streaming (MK21 MultiServidor)

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.

## [2.10.1] - 2026-11-22

- **Tamanho da fonte só afeta o texto:** a escala (`--font-scale`) parou de redimensionar a barra lateral, a coluna de configurações, paddings, alturas mínimas dos botões e a grade de 3 colunas. Agora as telas mantêm o mesmo layout em qualquer um dos 6 tamanhos; só a tipografia cresce.
- **Player principal sem moldura amarela:** o foco do D-Pad desenhava `outline` amarelo de 4 px nas bordas do vídeo (regras `:focus` globais). O container do player (`#videoContainer`, `.video-box`, `#tvPlayer`) foi excluído do anel de foco.
- **Memória no webOS:** a sincronização de catálogos em segundo plano passou a rodar em fila única (antes, conectar numa fonte + "Sincronizar Todas" mantinha dois catálogos grandes no heap ao mesmo tempo), o JSON do catálogo não duplica mais o texto de 30–40 MB no `join` dos chunks, e cada sync registra o heap em uso/limite. O log de homologação ganhou as linhas `Memória:` e `Sessão:` (a segunda detecta encerramento inesperado da execução anterior, indício de OOM).

## [2.9.6] - 2026-11-20

- **Catálogo JSON como fonte principal na Smart TV:** em vez de baixar a lista M3U inteira (77–108 MB nos servidores atuais), o app passou a montar o catálogo por `player_api.php` (`get_live_streams`, `get_vod_streams`, `get_series` + categorias), com fallback automático para o M3U. Medido: ~16 MB/4 s no VLOG e ~46 MB/8 s no MK21 PRÓ, contra 77 MB/13 s e 108 MB/>20 s do M3U. Preferência ajustável em `localStorage.mk21_catalog_mode` (`json`, `m3u`, `auto`).
- **Carga de listas na Smart TV (IPK):** o timeout de 45 s por formato derrubava listas XUI grandes (50–108 MB) no meio do streaming; agora são 20 s para a primeira resposta, 30 s de tolerância sem receber dados e teto de 5 min, com watchdog reiniciado a cada chunk. O sync em segundo plano do servidor saiu de 50 s para 4 min. O diagnóstico em tempo real passou a registrar host, HTTP, tamanho, bytes recebidos, tempo, velocidade, itens parseados e o motivo exato do abort (sem dados / sem resposta / limite).

- **Busca de legendas por IMDb ID (`/api/subtitles`):** o proxy agora consulta o OpenSubtitles pelo ID do IMDb (`imdb_id`) em vez do título, evitando legenda de outro filme. Se o app não mandar `imdb_id`, o proxy resolve o título no catálogo do IMDb (catálogo com cache de 24h) e usa o ID exato; episódios `SxxEyy` passam a buscar por `parent_imdb_id` + `season_number`/`episode_number`. Quando a resolução falha, mantém a busca por título. A resposta inclui `imdbId` e `matchedBy`. Android, Smart TV e Web não precisam de mudança: continuam chamando o mesmo endpoint.

## [2.9.1] - 2026-11-19

- **Sincronização em Nuvem Multiplataforma (`/api/sync`):** sincronização de **Favoritos** e **Continuar Assistindo (VOD)** entre **Android ↔ Smart TV ↔ Web** usando código de pareamento `MK21-XXXX-XXXX`, banco Neon Postgres (`user_sync_state`) e merge determinístico Last-Write-Wins (LWW) sem sobrescrever edições entre aparelhos.
- **OpenSubtitles + Ajuste de Sincronia (`±0.5s`) + Pular Abertura/Créditos:** novo proxy `/api/subtitles` (conversão SRT → WebVTT + deslocamento de timestamps), busca de legendas online no diálogo Áudio/Legendas (Android e Smart TV) e botão flutuante **Pular Abertura / Pular Créditos** em filmes e séries.
- **Guia de Programação & EPG Público (`/api/epg`):** nova aba **GUIA** em duas colunas e grade **Timeline** na Smart TV (D-pad 100%), carregador XMLTV por regex sem DOMParser, fallback público `/api/epg` (meuguia.tv) sem credenciais Xtream (Smart TV e Android), busca **No Ar Agora**, **LCN editável**, importação local de `.m3u`, mascaramento de URLs/credenciais e áudio independente Live/VOD + Smart Audio.
- **HUD de zapping & Teclado Virtual D-pad:** cartão de zapping com contagem regressiva e teclado QWERTY PT-BR (4 modos) na Smart TV.
- **Estabilidade Android Compose & CI:** `SafeDialog` + `SafeAndroidTextToolbar` eliminando crashes de `FloatingActionMode` no `DecorView`; guarda no `validate.yml` para bumps de versão antes da publicação do `release.yml`; bump multiplataforma sincronizado para **v2.9.1 (Code 291)**.

## [2.9.0] - 2026-11-15

- **MK21 AI Copilot (Android):** Redimensionamento da janela do diálogo para teclado virtual (`SOFT_INPUT_ADJUST_RESIZE` + `.imePadding()` / `.navigationBarsPadding()`), caixa de entrada expansível até 5 linhas (`132.dp`) com envio alinhado à base e fechamento automático do teclado.
- **Respostas dinâmicas no Copilot:** Indicador de digitação animado com 3 pontos pulsantes, cursor piscante durante streaming, renderização leve de Markdown (`**negrito**`, listas `- ` / `• `, URLs destacadas), auto-scroll que respeita rolagem manual com botão flutuante `⤓ Ir para o fim` e chips contextuais de continuação após cada resposta.
- **Sincronização Multiplataforma v2.9.0 (Code 290):** Atualização coordenada dos manifestos Android, Web/PWA, LG webOS e Samsung Tizen.

## [2.8.0] - 2026-11-14

- **Hardening OTA & Assinatura Android:** `ApkSignatureVerifier` e `Mk21UpdateManager` passaram a validar SHA-256 em modo *fail-closed*, redirecionamentos HTTPS restritos a hosts GitHub oficiais e assinatura de certificado do APK antes da instalação; `validate.yml` exige assinatura de upload e checa sincronismo contra `app/build.gradle.kts` (`versionCode = 280`).
- **Segurança de Proxies & Rede:** Proteção anti-SSRF/DNS-rebinding e CORS restrito no proxy Vercel (`api/proxy.js`), autenticação por token de sessão aleatório (`SecureRandom`) no `Mk21ProxyStation` e `LocalHttpServer`, sanitização contra XSS no receiver local e remoção de CAs de usuário do `network_security_config.xml` de release.
- **Fonte Única Web & Limpeza:** Unificação de `index.html`, `sw.js` (`mk21-cache-v2.8.0`), `api/proxy.js` e manifestos a partir da raiz com `scripts/sync-web.mjs --check` no CI; remoção de `backups/`, `builds/archives/` e dezenas de `.ipk`/`.apk` históricos duplicados.

## [2.5.0] - 2026-11-06

- **Identidade Visual:** Atualização da marca oficial MK21 ("Mais que um número é resultado") com tipografia metálica, glow rubi e restrição de altura máxima (50px).
- **Smart TV (LG webOS / Tizen):** Restauração do fluxo de autenticação com modal fullscreen de login, navegação D-pad estrita e compensação de teclado virtual (OSK `translateY(-160px)`).
- **Smart TV:** Mapeamento da tecla BACK (webOS 461 / Tizen 10009 / Escape) com interceptação de blur de input para evitar o encerramento acidental do app.
- **Web / PWA:** Invalidação de cache com Service Worker `mk21-cache-v2.5.0`.
- **Android:** Sincronização de versão `2.5.0` (Build `250`) mantendo chaves de assinatura e identificadores legados intactos.

## [2.3.7] - 2026-10-30

- **Android:** agrupa URLs alternativas do mesmo canal/conteúdo M3U por `tvg-id` ou, sem ID, por nome, categoria e tipo normalizados.
- **Android:** adiciona seleção manual e fallback automático entre URLs da playlist, mantendo a troca já existente entre servidores.
- **Android:** preserva a posição de filmes e episódios ao trocar de fonte e migra o catálogo Room sem apagar dados.

## [2.1.2] - 2026-10-21

- Sincronização das versões Android, Web/PWA, webOS e Tizen para `2.1.2` / `212`.
- Atualização dos manifests de deploy e dos metadados de servidores.
- Pacote webOS gerado e validado; o APK Android de release aguarda assinatura de produção.

## [2.0.0] - 2026-10-17

### 🚀 Destaques da Release Major (v2.0.0)
Esta versão marca uma reestruturação arquitetural completa do ecossistema FBGs-Streaming / MK21 MultiServidor, focando em segurança corporativa (OWASP), eliminação definitiva de gargalos de memória em Smart TVs e redução drástica da latência de troca de canal (*zapping*).

---

### 🔒 Segurança & Hardening (OWASP)
- **Correção Crítica de SSRF no Proxy (`api/proxy.js`)**:
  - Implementada validação de DNS reverso e bloqueio de IPs locais, privados (RFC 1918) e metadados de nuvem (`169.254.169.254`, `localhost`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
  - Restrição de protocolos aceitos estritamente para `http:` e `https:`.
  - Controle de timeout de 12 segundos via `AbortController` com respostas HTTP 504 adequadas.
- **Prevenção Estrita contra Stored XSS**:
  - Erradicação de `innerHTML` na renderização de nomes de canais, categorias e EPG (`PlayerOverlay.js`).
  - Adoção uniforme de `textContent` e validação de URLs seguras para logotipos.

---

### ⚡ Performance & Escalabilidade
- **Parser M3U Não-Bloqueante via Web Worker (`workers/playlistWorker.js`)**:
  - Processamento em background de listas com mais de 100.000 itens em lotes de 500 registros.
  - Consumo de heap V8 reduzido de ~480 MB para ~42 MB, eliminando travamentos por falta de memória (*OOM crash*) em TVs LG webOS e Samsung Tizen legadas.
- **Fast-Start Buffer & Zero Latency Zapping (<500ms)**:
  - Android: `DefaultLoadControl` configurado com `minBufferMs = 5000` e `bufferForPlaybackMs = 500`.
  - Web & Smart TV: `Hls.js` configurado com `lowLatencyMode = true`, `startFragPrefetch = true` e back-buffer de 10s.
  - Pré-carregamento dos manifestos master do próximo canal e canal anterior no `PlayerController`.

---

### 🏗️ Arquitetura & Player
- **Prevenção de Áudio Fantasma (*Audio Leak*)**:
  - Desanexação atômica de mídia (`stopLoad()`, `detachMedia()`, `video.removeAttribute('src')`, `video.load()`) antes de inicializar novas streams.
- **Desacoplamento de Estado Global & Focus Guard**:
  - Estado de busca isolado em `currentFilteredItems`, prevenindo a destruição do catálogo base em memória.
  - Gerenciador de histórico por pilha (*Stack-based FSM*) integrado aos keycodes de retorno de controles remotos (461, 10009, Escape).

---

### 📦 Sincronização de Metadados Multiplataforma
- `app/build.gradle.kts`: `versionName = "2.0.0"`, `versionCode = 200`
- `smart-tv/version.json`: `2.0.0` (Código 200)
- `smart-tv/mk21-tv/version.json`: `2.0.0`
- `smart-tv/mk21-tv/appinfo.json`: `2.0.0`
- `smart-tv/webos/appinfo.json`: `2.0.0`
- `smart-tv/tizen/config.xml`: `2.0.0`
- `web/version.json`: `2.0.0`
- `manifest.json`: `2.0.0`
- `api/version.json`: `2.0.0`
