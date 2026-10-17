# BGs Streaming (MK21 MultiServidor) — v1.4.0

<div align="center">
  <img src="web/icon-192.png" alt="BGs Streaming Logo" width="120" height="120" style="border-radius: 20%;" />
  
  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v1.4.0-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Hls.js](https://img.shields.io/badge/Hls.js-Low_Latency-purple.svg)](https://github.com/video-dev/hls.js)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

## 📌 Visão Geral & Foco do Cliente (Player para Listas de Terceiros)

O **BGs Streaming (MK21 Play)** é uma plataforma unificada e otimizada com foco exclusivo no **Cliente (Player)** para reprodução de listas IPTV de terceiros (M3U, M3U8 e APIs Xtream Codes). Projetado para máxima velocidade de inicialização de vídeo (**Fast Start**), resiliência contra oscilações de rede (**Auto-Retry Policy**, **Mk21DnsResolver** e **DNS Failover DoH**) e experiência fluida com catálogos de milhares de canais em **Android**, **Web PWA** e **Smart TVs (LG webOS e Samsung Tizen)**.

---

## 🚀 Novidades da Versão v1.4.0 (Multitask & Observability)

- 📺 **Picture-in-Picture (PiP) Nativo com Auto-Enter**:
  - Habilitação de `setAutoEnterEnabled(true)` no Android 12+ e suporte a `onUserLeaveHint`, permitindo transição contínua para janela flutuante ao sair do app durante a reprodução.
- 📡 **Telemetria Silenciosa de Stream (`logStreamError`)**:
  - Módulo de observabilidade integrado no `Mk21DnsResolver` e no `AppViewModel` capturando silenciosamente erros 4xx/5xx de listas e falhas de reprodução no ExoPlayer sem interferir na UI.
- ⚡ **Network Layer Fonte da Verdade (`Mk21DnsResolver`)**:
  - Resolução robusta de URLs e failover automático integrado no OkHttp e Media3.
- 🔍 **Busca Global Omnisearch**:
  - Busca unificada categorizada com contadores em tempo real para Ao Vivo, Filmes e Séries.
- 📺 **Picture-in-Picture (PiP) Nativo & AV1**:
  - Suporte completo a PiP no Android via `onUserLeaveHint` e decodificação acelerada por hardware de AV1/HEVC/H.264.
- 🧼 **Limpeza e Otimização Multiplataforma**:
  - Repositório enxuto e padronizado: Web, Android APK (`mk21.apk`) e Smart TV webOS (`mk21play_1.3.0_all.ipk`) sincronizados em v1.3.0.

- ⚡ **Fast Start Buffer (Início Instantâneo)**:
  - **Android (Media3 ExoPlayer)**: `minBufferMs` reduzido para 5.000ms e `bufferForPlaybackMs` ajustado para 500ms via `DefaultLoadControl`.
  - **Web & Smart TV (Hls.js)**: Configuração otimizada com `lowLatencyMode: true`, `startFragPrefetch: true` e `maxBufferLength: 15s`.
- 🔄 **Auto-Retry & Reconnection Policy**:
  - Reconexão inteligente com exponential backoff (até 5 tentativas) caso o link da transmissão oscile ou caia momentaneamente.
  - Recuperação automática de erros de mídia (`recoverMediaError`) e rede (`startLoad`).
- 📜 **Virtualização e Alta Performance em Listas**:
  - `LazyColumn` com chaves de estabilidade no Android e paginação virtual (`IntersectionObserver`) no Web, garantindo 60 FPS estáveis mesmo com mais de 10.000 canais.
  - Shimmer UI esqueleto elegante durante trocas de categoria e carregamentos.
- 📺 **Decodificação por Hardware**:
  - Suporte total a codecs modernos (H.264, H.265/HEVC) com decodificação por hardware nativo e baixo consumo de bateria.
- 💡 **Feedback Visual em Tempo Real**:
  - HUD integrado com status explícitos: *"Conectando ao servidor..."*, *"Carregando canal..."*, *"Reconectando stream (Tentativa X/5)..."* e *"Erro de reprodução"*.

---

## 📋 Como Adicionar e Gerenciar Listas

O aplicativo suporta os principais formatos de listas de terceiros:

### 1. API Xtream Codes (Recomendado)
Para carregar canais, filmes e séries categorizados com EPG e logos automáticos:
1. Abra o aplicativo (Android, Web ou Smart TV).
2. Na tela de login, selecione o servidor predefinido ou informe a URL base do seu provedor (ex: `http://servidor.xyz:8080`).
3. Digite seu **Usuário** e **Senha**.
4. Toque em **Entrar**. O catálogo será baixado em segundo plano e indexado no cache local de alta velocidade.

### 2. Listas M3U / M3U8 (URL Remota ou Arquivo Local)
1. Vá até a tela de configuração ou gerenciamento de servidores.
2. Cole o link M3U completo da sua lista (ex: `http://provedor.com/get.php?username=...&type=m3u_plus&output=m3u8`).
3. O parser assíncrono processará os canais ao vivo, filmes e séries separadamente em lote (*batch processing*), sem travar a interface.

---

## 🏗️ Arquitetura por Plataforma

### Android / Android TV (`/app`)
- **Linguagem & Framework**: Kotlin 2.1.10 + Jetpack Compose (Material 3).
- **Player**: AndroidX Media3 ExoPlayer 1.4.1 com `DefaultRenderersFactory` (Aceleração por Hardware) e `DefaultLoadControl` customizado.
- **Banco de Dados Local**: Room Database 2.7.0 (SQLite) com DAOs otimizados para busca instantânea.
- **Picture-in-Picture (PiP)**: Suporte completo em smartphones com botões de play/pause e próximo canal no PiP.

### Web & PWA (`/web` e `index.html`)
- **Engine**: Hls.js v1.5.8 + MPEG-TS.js + HTML5 Video nativo.
- **Proxy Inteligente**: Rotação transparente de proxies CORS/HTTPS para contornar restrições de Mixed Content em listas HTTP.
- **Cache Local**: IndexedDB (`mk21_cache_db`) para armazenamento persistente das playlists.

### Smart TV (`/smart-tv`)
- **LG webOS**: Pacote `.ipk` empacotado para navegadores webOS 3.0+ com navegação espacial via controle remoto (D-Pad).
- **Samsung Tizen**: Pacote `.wgt` configurado com `config.xml` para Tizen 4.0+.

---

## 🛠️ Compilação e Verificação

### Android (Gradle)
```bash
# Compilar APK de desenvolvimento
gradle assembleDebug

# Executar testes unitários locais
gradle testDebugUnitTest
```

### Smart TV (webOS / Tizen)
```bash
# Empacotar IPK para LG webOS
python3 smart-tv/package_ipk.py
```

---

## 📄 Licença e Uso

Este reprodutor é uma ferramenta de mídia para uso pessoal com listas e conteúdos autorizados de terceiros.
