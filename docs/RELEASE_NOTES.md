# Release v2.0.0 — FBGs-Streaming (MK21 MultiServidor)

### 🌟 Principais Inovações
1. **SSRF Guard no Proxy Serverless**: Bloqueio ativo de IPs privados e endpoints de nuvem.
2. **Web Worker para Ingestão M3U**: Parsing assíncrono em segundo plano sem congelar a interface em Smart TVs.
3. **Fast-Start Playback (<500ms)**: Redução expressiva na latência de zapping de canais ao vivo.
4. **Proteção Total contra XSS**: Renderização segura via `textContent`.
5. **Zero Audio-Leak**: Destruição atômica de streams anteriores.

### 📋 Checklist de Validação CI/CD
- [x] Sincronização uniforme de versão `2.0.0` nos 8 manifestos do ecossistema.
- [x] Sintaxe validada via `node --check`.
- [x] Compatibilidade com LG webOS (IPK) e Samsung Tizen (WGT).
- [x] Compatibilidade com Android TV / Mobile (versionCode 200).

---

# Release v2.8.0 (versionCode 280) — Hardening OTA & Pipeline de Release

### Estado Atual (Saída B ativa até a publicação assinada)
- **URL publicada em `version.json` (`apkUrl` / `downloadUrl`)**: `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk` (HTTP 200, SHA-256 `84f821a2cd99d132374f926cf9b590f6ac2a4cc8c3f88315ca0446e36fef91f9`). Motivo: a tag/release `v2.8.0` e o APK 2.8.0 assinado com a keystore oficial ainda aguardam os secrets de assinatura no GitHub Actions (`ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) e o disparo da tag (`git tag v2.8.0 && git push origin v2.8.0`).
- **Auto-updater (`AppViewModel` -> `Mk21UpdateManager`)**: falha fechado para instalação automática enquanto `BuildConfig.EXPECTED_CERT_SHA256` estiver vazio, evitando instalação de binário sem pin de certificado e direcionando para download manual pelo navegador.
- **Compatibilidade com TV legada (`smart-tv/mk21-tv/src/legacy/app-legacy.js` e `app.js`)**: o fallback legado mantém `apkUrl` fixo para `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk`; portanto `mk21.apk` permanece na raiz do repositório e é atualizado automaticamente no commit de pós-release em `.github/workflows/release.yml`.

### Checklist para concluir Saída A (Release assinada `v2.8.0`)
- [ ] Cadastrar nos Secrets do GitHub: `ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` e `EXPECTED_CERT_SHA256`.
- [ ] Executar `git tag v2.8.0 && git push origin v2.8.0` para o workflow `Release` compilar, assinar o APK 2.8.0, subir na GitHub Release, validar com `--remote` e atualizar os manifestos e `mk21.apk` da raiz.
