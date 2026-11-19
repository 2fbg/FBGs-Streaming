# Changelog — FBGs-Streaming (MK21 MultiServidor)

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.

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
