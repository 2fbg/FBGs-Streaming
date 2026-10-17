#!/usr/bin/env bash
set -e

echo "=== Aplicando Release v2.0.0 no repositório FBGs-Streaming ==="

# 1. Validação de sintaxe JS
node --check smart-tv/mk21-tv/core/PlayerController.js
node --check api/proxy.js

# 2. Adicionar arquivos modificados
git add CHANGELOG.md RELEASE_NOTES.md
git add smart-tv/version.json smart-tv/mk21-tv/version.json smart-tv/mk21-tv/appinfo.json smart-tv/webos/appinfo.json smart-tv/tizen/config.xml
git add web/version.json manifest.json api/version.json
git add api/proxy.js smart-tv/mk21-tv/workers/playlistWorker.js smart-tv/mk21-tv/components/PlayerOverlay.js smart-tv/mk21-tv/core/PlayerController.js
git add app/build.gradle.kts app/src/main/java/com/example/player/PlayerFactory.kt

# 3. Commit semântico
git commit -m "feat(release): FBGs-Streaming v2.0.0 - security hardening and fast-start playback"

# 4. Criar Tag anotada
git tag -a v2.0.0 -m "Release v2.0.0: major security, architecture and zapping performance upgrade"

# 5. Push para o GitHub
git push origin main
git push origin v2.0.0

echo "✅ Release v2.0.0 enviada com sucesso! O workflow do GitHub Actions será acionado automaticamente."
