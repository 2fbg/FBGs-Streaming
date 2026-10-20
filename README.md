# FBGs-Streaming v1.4.1

## 🚀 Novidades da Versão
- Sincronização Única: Servidores centralizados na raiz.
- Resiliência Web: Correção do Combo de Servidores e Modo Offline.
- Performance TV: Phased Loading para listas gigantes.

  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v2.0.0-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Hls.js](https://img.shields.io/badge/Hls.js-Low_Latency-purple.svg)](https://github.com/video-dev/hls.js)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

> **Versões por plataforma:** a release do ecossistema Web/Smart TV é `2.0.0`. O módulo Android ainda declara `1.4.2` (`versionCode` 142) em `app/build.gradle.kts`.

## 📌 Visão Geral & Foco do Cliente (Player para Listas de Terceiros)

O **BGs Streaming (MK21 Play)** é uma plataforma unificada e otimizada com foco exclusivo no **Cliente (Player)** para reprodução de listas IPTV de terceiros (M3U, M3U8 e APIs Xtream Codes). Projetado para máxima velocidade de inicialização de vídeo (**Fast Start**), resiliência contra oscilações de rede (**Auto-Retry Policy**, **Mk21DnsResolver** e **DNS Failover DoH**) e experiência fluida com catálogos de milhares de canais em **Android**, **Web PWA** e **Smart TVs (LG webOS e Samsung Tizen)**.

---

## 🚀 Novidades da Release v2.0.0

- 🔒 **Proxy com proteção contra SSRF**: restringe protocolos a HTTP/HTTPS e bloqueia destinos locais, privados e endpoints de metadados de nuvem.
- 🛡️ **Renderização mais segura**: nomes e categorias são inseridos como texto, evitando interpretar conteúdo de playlists como HTML.
- ⚙️ **Parsing M3U em segundo plano**: um Web Worker processa listas em lotes para manter a interface responsiva em Smart TVs.
- ⚡ **Início e troca de canais mais rápidos**: ajustes de buffer e pré-carregamento reduzem a espera para iniciar a reprodução.
- 🔇 **Encerramento consistente de streams**: a mídia anterior é desconectada antes de iniciar outra, evitando áudio residual.

Consulte o [changelog completo](docs/CHANGELOG.md) e as [notas da release](docs/RELEASE_NOTES.md).

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
