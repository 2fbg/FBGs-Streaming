# FBGs-Streaming (MK21 MultiServidor) — v2.3.13

<div align="center">
  <img src="web/icon-192.png" alt="MK21 Streaming Logo" width="120" height="120" style="border-radius: 20%;" />
  
  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)
  
  [![Version](https://img.shields.io/badge/Version-v2.3.13-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv)
</div>

---

## 📦 Downloads Oficiais & Pacotes

| Plataforma | Pacote / Binário | SHA-256 |
|---|---|---|
| **Android APK v2.3.13** | [`mk21.apk`](mk21.apk) | `195c7b9746a1c25ec72ee252a84c35547e1bf3d90bad84bae2279d4c4fafc0ae` |
| **LG webOS Smart TV v2.3.13** | [`mk21play_2.3.13_all.ipk`](smart-tv/mk21play_2.3.13_all.ipk) | `dd4a31d397da74f4fe602ed2d518ce62cf63ad929791c9a5c2950dbb59722983` |
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
- **Fontes alternativas (Android):** entradas M3U com o mesmo `tvg-id` (ou mesmo nome/categoria quando não há ID) compartilham URLs selecionáveis manualmente ou tentadas automaticamente após falhas; filmes e episódios retomam a posição ao trocar de fonte.
- **Persistência Local**: Room Database com Room KSP.

### Smart TV (`/smart-tv`)
- **LG webOS**: Pacote empacotado `.ipk` otimizado para webOS 3.0+ a webOS 24 via `package_ipk.py`.
- **Fontes alternativas (Smart TV):** agrupa URLs equivalentes da M3U, permite selecionar a fonte e tenta as alternativas automaticamente após falhas, preservando a posição de filmes e episódios.
- **Samsung Tizen**: Compatível com Tizen WGT 3.0+.
- **Navegação 10-foot UI**: Suporte total ao D-Pad do controle remoto com focus visual claro.

### Web PWA (`/web` e `/`)
- **Single Page Application**: HTML5 + Vanilla JS moderno com suporte PWA, Service Worker (`sw.js`) e IndexedDB.
- **Proxy Anti-CORS**: `/api/proxy.js` com streaming chunked e cabeçalhos CORS completos.

---

## 🔐 Assinatura do APK e Secrets do CI

O workflow [`.github/workflows/validate.yml`](.github/workflows/validate.yml) roda os testes, compila o Android, empacota o `.ipk` e carimba os hashes. Com os secrets abaixo cadastrados em **Settings → Secrets and variables → Actions**, o APK publicado no artifact sai como **release assinado**. Sem eles (por exemplo, em PRs vindos de forks, onde secrets não são expostos), o job continua verde e publica um APK **debug**, com o resumo identificando isso explicitamente.

| Secret | Obrigatório | Conteúdo |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | sim | keystore de upload codificada em base64 |
| `STORE_PASSWORD` | sim | senha do keystore |
| `KEY_PASSWORD` | não | senha da chave; se vazia, assume `STORE_PASSWORD` |
| `KEY_ALIAS` | não | alias da chave de upload; padrão `upload` |

### Como gerar o base64 da keystore

```bash
# Linux
base64 -w0 my-upload-key.jks > my-upload-key.jks.b64

# macOS
base64 -i my-upload-key.jks -o my-upload-key.jks.b64
```

Abra o `.b64`, copie todo o conteúdo e cole no valor do secret `ANDROID_KEYSTORE_BASE64`. Quebras de linha no meio do texto não atrapalham — o CI decodifica com `base64 --decode`. A keystore e o `.b64` **nunca** devem ser versionados; mantenha-os fora do repositório e guarde um backup seguro da chave.

### Conferir o caminho de assinatura localmente

```bash
STORE_PASSWORD='sua-senha' \
KEY_PASSWORD='sua-senha' \
KEY_ALIAS=upload \
KEYSTORE_PATH="$PWD/my-upload-key.jks" \
./gradlew assembleRelease
```

O APK resultante fica em `app/build/outputs/apk/release/app-release.apk`. Para conferir o certificado:

```bash
"$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### O que o CI valida

- A keystore é decodificada para um arquivo temporário (`chmod 600`) e testada com `keytool -list` **antes** do build: senha ou alias errados falham de imediato, em vez de gerar um APK assinado com uma chave nova a cada execução.
- Depois do carimbo, `apksigner` confere o certificado; se o APK sair com `CN=Android Debug` mesmo com os secrets configurados, o job falha.
- O resumo do job informa se o APK é `release assinado com a keystore de upload` ou `debug (sem keystore nos secrets)`.

> **Atualizações por cima:** use a **mesma keystore que assinou as versões anteriores** (até 2.3.7). Trocar a chave muda a assinatura e obriga o usuário a desinstalar o app antigo antes de instalar o novo.
