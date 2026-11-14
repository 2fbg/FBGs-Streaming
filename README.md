# FBGs-Streaming (MK21 MultiServidor)

<div align="center">
  <img src="assets/logo.png" alt="MK21 Streaming Logo" width="220" style="object-fit: contain;" />

  ### Player & Client IPTV Multiplataforma de Alta Performance (Android, Web & Smart TV)

  [![Version](https://img.shields.io/badge/Version-v2.8.0-red.svg)](docs/CHANGELOG.md)
  [![Android](https://img.shields.io/badge/Platform-Android_24%2B-green.svg)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg)](https://kotlinlang.org)
  [![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.1-orange.svg)](https://developer.android.com/guide/topics/media/media3)
  [![Smart TV](https://img.shields.io/badge/Smart_TV-webOS_%26_Tizen-black.svg)](#smart-tv--package-ipk)
</div>

---

## 📦 Downloads Oficiais & Pacotes da Release v2.8.0

| Plataforma | Pacote / Binário | Notas / Verificação |
|---|---|---|
| **Android APK** | [`mk21.apk`](mk21.apk) | `versionCode 280` • Validação SHA-256 + assinatura de certificado no updater |
| **LG webOS Smart TV** | [`mk21play_2.8.0_all.ipk`](smart-tv/mk21play_2.8.0_all.ipk) | SHA-256: `d18177bdf0fe64c45ea23d9079a02210f4fd7d60471b83388840bd63ff1e277d` |
| **Web / PWA** | [Deploy Vercel / PWA](index.html) | Fonte única na raiz + espelhos `web/` e `public/` sincronizados via CI |

> Os artefatos assinados e o arquivo `SHA256SUMS` de cada build também são publicados no workflow [Validate](.github/workflows/validate.yml).

---

## 🏗️ Arquitetura por Plataforma

### Android / Android TV (`/app`)
- **Linguagem & Framework:** Kotlin + Jetpack Compose (Material 3) com `applicationId = "com.aistudio.multiservidor.iptvmk"` (`minSdk 24`, `targetSdk 36`).
- **Organização de Pacotes:** O módulo Android combina o legado em `com.example` (`ui/`, `viewmodel/`, `data/`) com os novos módulos em `com.fbg.streaming` (`update/`, `security/`, `network/proxy/`).
- **Player:** AndroidX Media3 ExoPlayer `1.4.1` com aceleração por hardware, fontes alternativas por `tvg-id`/nome normalizado e fallback automático.
- **Segurança & OTA:**
  - `Mk21UpdateManager` e `ApkSignatureVerifier` exigem SHA-256 obrigatório (fail-closed), download somente via HTTPS em hosts GitHub permitidos (validando redirecionamentos `3xx`) e verificação de assinatura do APK antes de abrir o instalador.
  - `Mk21ProxyStation` e `LocalHttpServer` protegem endpoints locais com token aleatório por sessão (`SecureRandom` + `MessageDigest.isEqual`), sem `Access-Control-Allow-Origin: *` e com sanitização de HTML/URLs.
  - `network_security_config.xml` mantém `<certificates src="user" />` apenas no build de `debug` (`src/debug/res/xml/`), bloqueando HTTP em texto claro para domínios OTA/API em release.

### Smart TV (`/smart-tv`) {#smart-tv--package-ipk}
- **LG webOS:** Pacote reproduzível `.ipk` (`smart-tv/package_ipk.py`) para webOS 3.0+ a webOS 24.
- **Samsung Tizen:** Manifesto em `smart-tv/tizen/config.xml` (`v2.8.0`) compatível com Tizen 3.0+.
- **Bundle & Performance 10-foot UI:** Entrada em `smart-tv/mk21-tv/src/main.js` empacotada para `smart-tv/mk21-tv/app.js` (< 350 KB), navegação estrita via D-Pad, worker não-bloqueante e cache local.

### Web PWA (Fonte Única na Raiz -> `/web` e `/public`)
- **Fonte Canônica:** Os arquivos na **raiz** (`index.html`, `sw.js`, `manifest.json`, `apps.json`, `servers.json`, `version.json`, `api/proxy.js`, `css/`, `js/`) são a única fonte de verdade.
- **Sincronização Determinística:** O script `scripts/sync-web.mjs` replica a raiz para `web/` e `public/` (preservando `public/assets/aistudio/`). O CI falha automaticamente se qualquer espelho divergir.
- **Proxy Serverless (`/api/proxy.js`):** Streaming chunked com proteção anti-SSRF (IPv4/IPv6 privados, loopback, link-local, CGNAT, 6to4, NAT64), resolução DNS prévia com pino anti-DNS-rebinding e CORS restrito por allowlist.

---

## 🛠️ Build, Sincronização e Testes

### Android (JVM / Robolectric + APK)
```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
```
> Para gerar release assinado no GitHub Actions, configure os secrets `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD` e opcionalmente `KEY_PASSWORD` (`KEY_ALIAS=upload`).

### Smart TV (.IPK) & Web Single-Source
```bash
# Sincronizar espelhos web/ e public/ a partir da raiz
npm run sync:web

# Verificar sincronismo (executado no CI)
npm run check:web

# Empacotar Smart TV webOS .ipk e carimbar manifests
python3 smart-tv/package_ipk.py
```

### Suítes Node & Python
```bash
node --check smart-tv/mk21-tv/app.js
node --check api/proxy.js && node --check web/api/proxy.js
node --test smart-tv/tests/proxy-security.test.js smart-tv/tests/alternative-sources.test.js smart-tv/tests/catalog-utils.test.js
python3 -m unittest discover -s smart-tv/tests -v
```

---

## 🔍 Troubleshooting Rápido

- **Loop ou falha no Auto-Update Android:** Verifique se `versionCode` e `versionName` em `app/build.gradle.kts` coincidem com `version.json` e se `apkSha256` corresponde exatamente ao `mk21.apk` publicado.
- **Erro de sincronismo Web no CI (`sync-web`):** Edite sempre os arquivos Web na **raiz** e execute `node scripts/sync-web.mjs` antes do commit.
- **Erro `HTTP 403` no `/api/proxy`:** Confirme se o domínio de origem está na allowlist (`bgstreaming*.vercel.app`, `localhost`, `127.0.0.1` ou variável `PROXY_ALLOWED_ORIGINS`) e se o destino da stream não resolve para IP privado/reservado.
- **Pacote `.ipk` rejeitado na TV LG (`-5: ipk verified failed`):** Gere sempre via `python3 smart-tv/package_ipk.py`, que garante timestamps válidos e valida dimensões de `icon.png` (80x80), `largeIcon.png` (130x130) e `splash.png` (1920x1080).
