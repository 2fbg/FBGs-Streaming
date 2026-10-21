# Changelog — FBGs-Streaming (MK21 MultiServidor)

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.

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
