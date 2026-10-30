# FBGs-Streaming (MK21 MultiServidor) — v2.3.7

<div align="center">
  <img src="web/icon-192.png" alt="MK21 Streaming Logo" width="120" height="120" style="border-radius: 20%;" />
  
  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v2.3.7-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

## 📦 Downloads Oficiais & Pacotes da Release v2.3.7

| Plataforma | Pacote / Binário | SHA-256 |
|---|---|---|
| **Android APK** | [`mk21.apk`](mk21.apk) | `3d74531ee39f2ae7b53c9105bd3e2091d01136a10af04229b8d675aec5cc5fce` |
| **LG webOS Smart TV** | [`mk21.ipk`](smart-tv/mk21play_2.3.7_all.ipk) | `3303ded24f2a830f509e270d91bffc31ec715ccc04efa2a8079de4a9b7c55917` |
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
- **Fontes alternativas (Android):** URLs M3U do mesmo canal/conteúdo podem ser selecionadas manualmente ou tentadas automaticamente após falhas; filmes e episódios retomam a posição ao trocar de fonte.
- **Persistência Local**: Room Database com Room KSP.

### Smart TV (`/smart-tv`)
- **LG webOS**: Pacote empacotado `.ipk` otimizado para webOS 3.0+ a webOS 24 via `package_ipk.py`.
- **Samsung Tizen**: Compatível com Tizen WGT 3.0+.
- **Navegação 10-foot UI**: Suporte total ao D-Pad do controle remoto com focus visual claro.

### Web PWA (`/web` e `/`)
- **Single Page Application**: HTML5 + Vanilla JS moderno com suporte PWA, Service Worker (`sw.js`) e IndexedDB.
- **Proxy Anti-CORS**: `/api/proxy.js` com streaming chunked e cabeçalhos CORS completos.
