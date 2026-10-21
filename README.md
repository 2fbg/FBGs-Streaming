# FBGs-Streaming (MK21 MultiServidor) — v2.1.3

<div align="center">
  <img src="web/icon-192.png" alt="MK21 Streaming Logo" width="120" height="120" style="border-radius: 20%;" />
  
  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v2.1.3-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

## 🚀 Últimas Atualizações (Release v2.1.3 - Code 213)

- 📺 **Smart TV UI & Engine Polish**:
  - **Zapping Instantâneo (<200ms)**: Implementado `AbortController` e Promise de bloqueio assíncrono (`zappingLockPromise`). Limpeza garantida de instâncias anteriores (`pause() -> src = "" -> load()`) eliminando o erro `play() request interrupted by pause()`.
  - **EPG & XMLTV em Tempo Real**: `matchEPG()` otimizado com correspondência precisa (`tvg-id`, `streamId` e nome sanitizado). Conversão rigorosa de timezone do XMLTV para a hora local da TV nos campos "NO AR" e "A SEGUIR".
  - **Padronização Visual**: Tipografia unificada em 600 com espaçamento de 0.5px, contadores em 1.1rem, container de logo ampliado em 25% com `object-fit: contain`, e barra de rolagem visível e estilizada.
  - **Controles em Círculo Perfeito**: Botões do rodapé circulares (`border-radius: 50%`) com ícones SVG nativos, eliminando placeholders e emojis quebrados.

- 🌐 **Web PWA & Combo de Servidores**:
  - **Fonte Única na Raiz (`servers.json`)**: Sincronização automatizada para Web (`web/servers.json`, `public/servers.json`), Smart TV (`smart-tv/mk21-tv/servers.json`) e Android (`app/src/main/assets/servers.json`).
  - **Resolução de Dropdown em Branco**: Algoritmo defensivo em `populateServers()` com sincronização dinâmica e fallback garantido (seleção padrão nunca nula).
  - **Modo Offline 100% Clicável**: Botão destacado com gradiente e borda, debounce contra duplo toque em mobile e fallback imediato com grade de canais essenciais demonstrativos caso o IndexedDB ainda não possua listas salvas.

- 📱 **Android APK**:
  - Remoção de qualquer limite artificial (`take(60)` / `take(1500)`) em categorias, entregando catálogo completo virtualizado.
  - Layout ergonômico de 2 colunas fixas no modo retrato com badge nativo e monometadados.

- 🧹 **Higienização do Repositório**:
  - Binários e versões obsoletas movidos para a pasta `builds/archives/`, mantendo a raiz limpa e leve apenas com `mk21.apk` e `mk21.ipk`.

---

## 📦 Downloads Oficiais & Pacotes da Release v2.1.3

| Plataforma | Pacote / Binário | SHA-256 |
|---|---|---|
| **Android APK** | [`mk21.apk`](mk21.apk) | `bcf13f47e3cb0e47ee1f30ad329bea36a01b11caa32ec3b5d7010caa8d25b497` |
| **LG webOS Smart TV** | [`mk21.ipk`](smart-tv/mk21play_2.1.3_all.ipk) | `64c9e5d8c11faf583314a2edaa7e72afbd219d9ec68d3ed6fa808190c8aa8e18` |
| **Web / PWA** | [Deploy Vercel / PWA](index.html) | Multi-servidor + Modo Offline PWA |

---

## 📋 Como Adicionar e Gerenciar Listas

### 1. API Xtream Codes (Recomendado)
1. Abra o aplicativo (Android, Web ou Smart TV).
2. Na tela de login, selecione o servidor predefinido no combo ou digite o código/URL.
3. Digite seu **Usuário** e **Senha**.
4. Toque em **Entrar no MK21**. O catálogo será baixado em segundo plano e indexado no cache local de alta velocidade.

### 2. Modo Offline / Cache
- Se estiver sem conexão ou quiser navegar diretamente sem requisição de rede, clique em **Prosseguir para Home (Modo Offline / Cache)** na tela inicial.
- O aplicativo carrega imediatamente o último catálogo armazenado no banco local (IndexedDB no Web/TV, Room no Android).

---

## 🏗️ Arquitetura por Plataforma

### Android / Android TV (`/app`)
- **Linguagem & Framework**: Kotlin 2.1.10 + Jetpack Compose (Material 3).
- **Player**: AndroidX Media3 ExoPlayer 1.4.1 com `DefaultRenderersFactory` (Aceleração por Hardware).
- **Persistência Local**: Room Database com Room KSP.

### Smart TV (`/smart-tv`)
- **LG webOS**: Pacote empacotado `.ipk` otimizado para webOS 3.0+ a webOS 24 via `package_ipk.py`.
- **Samsung Tizen**: Compatível com Tizen WGT 3.0+.
- **Navegação 10-foot UI**: Suporte total ao D-Pad do controle remoto com focus visual claro.

### Web PWA (`/web` e `/`)
- **Single Page Application**: HTML5 + Vanilla JS moderno com suporte PWA, Service Worker (`sw.js`) e IndexedDB.
- **Proxy Anti-CORS**: `/api/proxy.js` com streaming chunked e cabeçalhos CORS completos.
