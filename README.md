# FBGs-Streaming (MK21 MultiServidor) — v2.2.0

<div align="center">
  <img src="web/icon-192.png" alt="MK21 Streaming Logo" width="120" height="120" style="border-radius: 20%;" />
  
  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v2.2.0-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

## 🚀 Últimas Atualizações (Release v2.2.0 - Code 220 - Saimo-Inspired Overhaul)

- 🎨 **UI Redesign: Canal Grid Dinâmico (Saimo-Inspired)**:
  - Segunda coluna de canais reestruturada com Grid Dinâmico responsivo (`.canal-grid` com `display: grid`).
  - Cards quadrados com Logo centralizada em alta definição e nome do canal em overlay semi-transparente no rodapé.
  - **Efeito de Foco Premium**: Ao passar o mouse ou focar via D-Pad do controle remoto, o card escala com suavidade (`transform: scale(1.1)`) e ganha borda brilhante (`box-shadow: 0 0 15px #ff9800`).

- 🧠 **Persistência de Estado (`MK21_CONFIG` LocalStorage Engine)**:
  - Objeto unificado `MK21_CONFIG` gerenciando `last_channel_id`, `volume_level`, `favorites_list`, `upscaling_active` e `zapping_mode`.
  - Ao carregar a aplicação (`window.onload`), sintoniza automaticamente no último canal assistido e aplica o volume salvo.

- ⚡ **Recursos Avançados (Expert Settings)**:
  - **Modo Zero Delay**: Pre-fetching assíncrono em segundo plano dos manifestos HLS para os 2 canais seguintes, eliminando travamentos de buffer na troca de canais.
  - **Upscaling CSS (HD Enhance)**: Filtro dinâmico `.upscale-filter` (`filter: contrast(1.1) saturate(1.1) brightness(1.05)`) com botão na barra do player e atalho de teclado `[U]`.
  - **Busca Fixa On-Top**: Barra de pesquisa com `position: sticky; top: 0; z-index: 28` e fundo blur, garantindo visibilidade total durante a rolagem do catálogo.

- 📁 **Combo de Servidores e Modo Offline Aperfeiçoado**:
  - Sincronização centralizada com `servers.json` da raiz com os 7 servidores oficiais (VLOG, TANNIX, LUB TV, MK21 PRÓ, CB6000, CINELON, MULTT TV).
  - Suporte completo ao carregamento de arquivos M3U locais (`.m3u` / `.m3u8`) diretamente do dispositivo via input de arquivo com parser client-side ultrarrápido.

- 🧹 **Higienização e Versionamento Limpo**:
  - Remoção de badges de "Sincronização Automática" obsoletas.
  - Versões anteriores arquivadas em `builds/archives/`.

---

## 📦 Downloads Oficiais & Pacotes da Release v2.2.0

| Plataforma | Pacote / Binário | SHA-256 |
|---|---|---|
| **Android APK** | [`mk21.apk`](mk21.apk) | `4699c0eadb5c20f9e80cfcbd942aa31325861d4972c802fda9adb43f871c457a` |
| **LG webOS Smart TV** | [`mk21.ipk`](smart-tv/mk21play_2.2.0_all.ipk) | `1a8cdfe9b939673459939ed567b9afa9b060e58c6b3893e0a1e5112ed142cd7e` |
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
