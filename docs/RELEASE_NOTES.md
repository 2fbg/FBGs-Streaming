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
