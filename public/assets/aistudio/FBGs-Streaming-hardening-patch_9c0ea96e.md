# FBGs-Streaming — Patch de endurecimento (auditoria P0/P1/P2)

Base: `main` @ `9eb31f8` · Branch sugerida: `hardening/p1-audit` · Abra um **Pull Request** (não faça push direto em `main`).

## 1. O que este patch faz

| # | Área | Mudança |
|---|---|---|
| 1 | **Segurança (novo, crítico)** | O painel **admin / gerador de chaves** abria com 5 toques ou digitando senhas fixas no código, em qualquer build. Agora só abre em `BuildConfig.DEBUG` (4 pontos em `Screens.kt`). |
| 2 | **Licença** | `LICENSE_SECRET` deixa de ter fallback no release: `assembleRelease` falha se o segredo faltar, tiver menos de 16 caracteres ou for o valor antigo. O valor é escapado antes de ir para o `BuildConfig`. |
| 3 | **Licença** | Chaves legadas (MD5 + salt fixo) ficam **desligadas por padrão** (`BuildConfig.LEGACY_LICENSE_ENABLED`, controlado por `ALLOW_LEGACY_LICENSE`). O gerador do app passa a emitir chave **HMAC-SHA256**. Comparação da chave HMAC em tempo constante. |
| 4 | **CI** | `validate.yml` passa a rodar `npm ci`, `npm run typecheck` e `npm test` (Jest), com cache do npm. |
| 5 | **CI** | `LICENSE_SECRET` e `ALLOW_LEGACY_LICENSE` são repassados ao build; `validate.yml` (caminho assinado) e `release.yml` falham cedo com mensagem clara se o segredo faltar. |
| 6 | **Manifestos** | `verify-release-manifests.mjs` agora audita `apps.json`, `smart-tv/apps.json`, `web/apps.json` e `public/apps.json` (versão, `sourceUrl`/`ipkUrl`, `ipkHash.sha256` e, com `--remote`, a URL no ar). 4 testes novos. |
| 7 | **Release** | `release.yml` grava em `apps.json` a URL da **GitHub Release** (no ar logo após `gh release create`), em vez do `raw` da `main`. |
| 8 | **Higiene** | Remove os arquivos vazios `main` e `FETCH_HEAD` e `app/build.gradle.kts.backup`. Ignora `FETCH_HEAD` e `*.backup`. `.env.example` sem segredo de aparência real. |
| 9 | **Docs** | Novo `docs/RELEASE_SECRETS.md` (secrets, variável, dry run, publicação) e link no README. |

## 2. Instruções para o Google AI Studio

Cole o bloco abaixo no chat do Studio, junto com este arquivo:

> Aplique ao repositório `2fbg/FBGs-Streaming` exatamente o patch da seção 7 deste arquivo.
> 1. Crie a branch `hardening/p1-audit` a partir de `main`.
> 2. Aplique o diff arquivo por arquivo, sem alterar nada além dele. Se o patch não casar com algum arquivo, **pare e me mostre o trecho divergente**; não improvise.
> 3. Apague os arquivos `main`, `FETCH_HEAD` e `app/build.gradle.kts.backup` (a seção 7 já marca `main` e `FETCH_HEAD` como removidos; o `.backup` está fora do diff para ele não ficar enorme, então apague-o à mão).
> 4. Não commite secrets, `.jks`, `.env` nem `secrets.properties`.
> 5. Faça um único commit: `chore(security,ci): harden licensing, gate admin panel, add npm tests and apps.json verification` e abra um Pull Request para `main`.
> 6. Não faça merge. Espere o workflow **Validate** ficar verde e me mostre o resultado.

Alternativa por terminal (o arquivo `FBGs-Streaming-hardening.patch` entregue junto aplica limpo em `9eb31f8`):

```bash
git checkout -b hardening/p1-audit
git apply --index FBGs-Streaming-hardening.patch
git commit -m "chore(security,ci): harden licensing, gate admin panel, add npm tests and apps.json verification"
git push -u origin hardening/p1-audit
```

## 3. Passos manuais no GitHub (nesta ordem)

1. **Settings → Secrets and variables → Actions → Secrets** (nível *Repository*): cadastre `LICENSE_SECRET` (`openssl rand -base64 36`) **antes ou junto** de `ANDROID_KEYSTORE_BASE64` e `STORE_PASSWORD`. Com a keystore cadastrada e sem `LICENSE_SECRET`, o Validate falha de propósito.
2. Complete os demais secrets: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `STORE_PASSWORD`, `KEY_PASSWORD` (opcional), `KEY_ALIAS=upload` (opcional) e `EXPECTED_CERT_SHA256` (opcional, o release deriva da keystore).
3. Opcional, aba *Variables*: `ALLOW_LEGACY_LICENSE=true` **somente** se quiser uma migração temporária das chaves antigas.
4. *Actions → Validate → Run workflow* com `require_signed = true`. Se os secrets não chegarem ao job, ele falha em vez de gerar um APK debug em silêncio.
5. `gh workflow run release.yml --ref main -f tag=v2.8.0 -f dry_run=true`
6. Com o dry run verde: `git tag v2.8.0 && git push origin v2.8.0`.

## 4. O que foi verificado e o que não foi

**Verificado no sandbox, sobre o `main` atual (`9eb31f8`):**
- `git apply --check` limpo; o patch aplicado em um clone novo passa nos testes.
- `npm run typecheck` sem erros; Jest 28/28.
- Testes Node 23/23 (19 originais + 4 novos); Python 6/6.
- `sync-web.mjs --check` OK (20 arquivos).
- `verify-release-manifests.mjs` local e `--remote` OK contra o repositório real (APK e IPK com HTTP 200 e SHA-256 batendo).
- Sintaxe YAML dos dois workflows.

**Não verificado (faça no PR):**
- **Kotlin e Gradle não foram compilados aqui**, nem os testes Android (`testDebugUnitTest`). As edições em `build.gradle.kts`, `PreferencesService.kt`, `AppViewModel.kt`, `Screens.kt` e `ExampleRobolectricTest.kt` são pequenas, mas o primeiro run do Validate é quem confirma.
- Nenhum workflow rodou no GitHub com este patch.

## 5. Riscos e decisões que você precisa tomar

1. **Chaves legadas deixam de funcionar.** Com `ALLOW_LEGACY_LICENSE` desligado, quem ativou com chave MD5 (`MK-XXXX-XXXX-XXXX` gerada pelo gerador antigo) perde a ativação na versão nova. Escolha: reemitir chaves HMAC (recomendado) ou ligar a variável temporariamente. Como o algoritmo MD5 é público no APK, mantê-lo ligado mantém o buraco.
2. **O APK publicado hoje é de debug** (`CN=Android Debug`), e em build debug o painel admin continua acessível. Até sair um release assinado com este patch, o painel segue aberto para quem baixar o `mk21.apk` da `main`.
3. **Continuidade da assinatura.** Uma keystore nova não atualiza por cima de quem tem o app instalado com outra chave, e o updater do app também valida o certificado. Use o `.jks` original da `v2.3.7` se o tiver; senão, avise os usuários de que será preciso reinstalar. Detalhes em `docs/RELEASE_SECRETS.md`.
4. **Segurança de licença tem limite.** Segredo HMAC dentro do APK é extraível por engenharia reversa. O patch eleva o custo do ataque, mas só validação em servidor protege de verdade.
5. **`apps.json` aponta para a URL da Release.** Confirme que a loja webOS (Homebrew Channel) que você usa segue o redirecionamento da URL de release. Se não seguir, reverta só a mudança nº 7 do `release.yml`; o verificador aceita as duas URLs fora do modo `--require-release-url`.

## 6. Fora do patch (recomendações)

- **`mk21.apk` e os `.ipk` no git são intencionais**: o `release.yml` os commita como fallback. **Não os remova**, ao contrário do que sugeri antes.
- `google-services.json` gerado inline em 3 lugares (Gradle e dois workflows): centralize num único script.
- `Mk21ProxyStation`: bloqueia só loopback na própria porta. Se listas M3U de terceiros puderem injetar URLs no player, bloqueie também `169.254.169.254` e faixas privadas que você não use.
- Se alguma senha da keystore ou do Firebase apareceu em capturas de tela ou chats, troque-a.

## 7. Patch completo

Marcações `deleted file mode` (arquivos `main` e `FETCH_HEAD`, vazios) fazem parte do patch. A remoção de `app/build.gradle.kts.backup` está fora do diff (passo 3 da seção 2).

````diff
diff --git a/.env.example b/.env.example
index 9a7cb20..a673e44 100644
--- a/.env.example
+++ b/.env.example
@@ -8,5 +8,10 @@ KEYSTORE_PATH=my-upload-key.jks
 DEBUG_STORE_PASSWORD=android
 
 # Licenciamento HMAC-SHA256
-# Segredo utilizado para gerar e verificar chaves de ativação de dispositivos
-LICENSE_SECRET=MK21_SUPER_SECURE_HMAC_SECRET_2026
+# Segredo utilizado para gerar e verificar chaves de ativação de dispositivos.
+# Obrigatório para build de release (mín. 16 caracteres). Gere com: openssl rand -base64 36
+# NUNCA reutilize o valor de exemplo e nunca faça commit do .env real.
+LICENSE_SECRET=troque_por_um_segredo_aleatorio_de_32_ou_mais_caracteres
+
+# Aceitar chaves legadas (MD5 + salt fixo)? Deixe false; use true só em migração temporária.
+ALLOW_LEGACY_LICENSE=false
diff --git a/.github/workflows/release.yml b/.github/workflows/release.yml
index 12428da..0679e35 100644
--- a/.github/workflows/release.yml
+++ b/.github/workflows/release.yml
@@ -47,6 +47,7 @@ jobs:
           KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
           KEY_ALIAS: ${{ secrets.KEY_ALIAS || 'upload' }}
           SECRET_CERT_SHA256: ${{ secrets.EXPECTED_CERT_SHA256 }}
+          LICENSE_SECRET: ${{ secrets.LICENSE_SECRET }}
           INPUT_TAG: ${{ github.event.inputs.tag }}
           DRY_RUN: ${{ github.event.inputs.dry_run || 'false' }}
         run: |
@@ -69,6 +70,10 @@ jobs:
             echo "::error::KEY_ALIAS está definido como '$KEY_ALIAS', mas app/build.gradle.kts exige alias 'upload'."
             exit 1
           fi
+          if [ -z "${LICENSE_SECRET:-}" ] || [ "${#LICENSE_SECRET}" -lt 16 ] || [ "$LICENSE_SECRET" = "MK21_DEFAULT_HMAC_SECRET" ]; then
+            echo "::error::Secret LICENSE_SECRET ausente, curto (<16) ou igual ao fallback antigo: obrigatório para publicar release."
+            exit 1
+          fi
 
           KS="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/mk21-upload.jks"
           printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$KS"
@@ -118,6 +123,8 @@ jobs:
           KEY_ALIAS: ${{ secrets.KEY_ALIAS || 'upload' }}
           KEYSTORE_PATH: ${{ steps.release-meta.outputs.keystore_path }}
           EXPECTED_CERT_SHA256: ${{ steps.release-meta.outputs.cert_pin }}
+          LICENSE_SECRET: ${{ secrets.LICENSE_SECRET }}
+          ALLOW_LEGACY_LICENSE: ${{ vars.ALLOW_LEGACY_LICENSE || 'false' }}
         run: |
           set -euo pipefail
           chmod +x gradlew
@@ -211,10 +218,10 @@ jobs:
 
           python3 smart-tv/package_ipk.py
 
-          python3 - "$VER" "$IPK_PATH" <<'PY'
+          python3 - "$VER" "$IPK_PATH" "$RELEASE_IPK_URL" <<'PY'
           import hashlib, json, sys
 
-          ver, ipk_path = sys.argv[1:3]
+          ver, ipk_path, release_ipk_url = sys.argv[1:4]
           with open(ipk_path, "rb") as f:
               ipk_sha = hashlib.sha256(f.read()).hexdigest()
 
@@ -223,7 +230,9 @@ jobs:
                   apps = json.load(f)
               pkg = apps["packages"][0]
               pkg["version"] = ver
-              pkg["sourceUrl"] = f"https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/{ipk_path}"
+              # URL da GitHub Release: fica no ar assim que `gh release create` termina, sem depender
+              # do commit em main (que pode exigir PR se houver proteção de branch).
+              pkg["sourceUrl"] = release_ipk_url
               pkg["manifest"]["version"] = ver
               pkg["manifest"]["ipkUrl"] = pkg["sourceUrl"]
               pkg["manifest"]["ipkHash"]["sha256"] = ipk_sha
diff --git a/.github/workflows/validate.yml b/.github/workflows/validate.yml
index 285227d..024d625 100644
--- a/.github/workflows/validate.yml
+++ b/.github/workflows/validate.yml
@@ -34,6 +34,14 @@ jobs:
       - uses: actions/setup-node@v4
         with:
           node-version: '20'
+          cache: npm
+
+      - name: TypeScript typecheck and Jest unit tests (players)
+        run: |
+          set -euo pipefail
+          npm ci --no-audit --no-fund
+          npm run typecheck
+          npm test
 
       - name: Verify published remote manifest URLs (against committed manifests)
         env:
@@ -60,6 +68,7 @@ jobs:
           KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
           STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
           KEY_ALIAS: ${{ secrets.KEY_ALIAS || 'upload' }}
+          LICENSE_SECRET: ${{ secrets.LICENSE_SECRET }}
           REQUIRE_SIGNED: ${{ github.event.inputs.require_signed || 'false' }}
         run: |
           set -euo pipefail
@@ -79,6 +88,12 @@ jobs:
             exit 1
           fi
 
+          # Build de release exige o segredo de licença (sem fallback embutido no APK).
+          if [ -z "${LICENSE_SECRET:-}" ] || [ "${#LICENSE_SECRET}" -lt 16 ]; then
+            echo "::error::Secret LICENSE_SECRET ausente ou curto (mín. 16 caracteres): obrigatório para build de release assinado."
+            exit 1
+          fi
+
           KS="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/mk21-upload.jks"
           printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$KS"
           chmod 600 "$KS"
@@ -97,6 +112,8 @@ jobs:
           KEY_ALIAS: ${{ secrets.KEY_ALIAS || 'upload' }}
           KEYSTORE_PATH: ${{ steps.release-keystore.outputs.path }}
           SIGNED: ${{ steps.release-keystore.outputs.signed }}
+          LICENSE_SECRET: ${{ secrets.LICENSE_SECRET }}
+          ALLOW_LEGACY_LICENSE: ${{ vars.ALLOW_LEGACY_LICENSE || 'false' }}
         run: |
           set -euo pipefail
           chmod +x gradlew
diff --git a/.gitignore b/.gitignore
index 5a0e611..1d76b8e 100644
--- a/.gitignore
+++ b/.gitignore
@@ -40,3 +40,6 @@ SHA256SUMS
 node_modules/
 coverage/
 dist/
+FETCH_HEAD
+*.backup
+/main
diff --git a/FETCH_HEAD b/FETCH_HEAD
deleted file mode 100644
index e69de29..0000000
diff --git a/README.md b/README.md
index 1a2757e..c9c545e 100644
--- a/README.md
+++ b/README.md
@@ -49,6 +49,8 @@
 
 ---
 
+> **Release assinado:** veja [`docs/RELEASE_SECRETS.md`](docs/RELEASE_SECRETS.md) para os secrets (`ANDROID_KEYSTORE_BASE64`, `STORE_PASSWORD`, `LICENSE_SECRET`, `EXPECTED_CERT_SHA256`) e o checklist de publicação.
+
 ## 🛠️ Build, Sincronização e Testes
 
 ### Android (JVM / Robolectric + APK)
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index 68e56f4..c544f45 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -125,8 +125,27 @@ android {
 
     testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 
-    val licenseSecret = System.getenv("LICENSE_SECRET") ?: "MK21_DEFAULT_HMAC_SECRET"
-    buildConfigField("String", "LICENSE_SECRET", "\"$licenseSecret\"")
+    // Segredo de licença: OBRIGATÓRIO em tarefas de release (sem fallback embutido no APK publicado).
+    // Em debug/testes unitários usa um valor local fixo, que nunca vai para um build de release.
+    val licenseSecretEnv = System.getenv("LICENSE_SECRET")?.trim().orEmpty()
+    val isReleaseBuild = project.gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
+    if (isReleaseBuild && (licenseSecretEnv.length < 16 || licenseSecretEnv == "MK21_DEFAULT_HMAC_SECRET")) {
+      throw GradleException(
+        "LICENSE_SECRET ausente, curto (<16 caracteres) ou igual ao fallback antigo. " +
+          "Defina a variável de ambiente LICENSE_SECRET antes de compilar o release."
+      )
+    }
+    val licenseSecret = licenseSecretEnv.ifEmpty { "MK21_DEFAULT_HMAC_SECRET" }
+    buildConfigField(
+      "String",
+      "LICENSE_SECRET",
+      "\"" + licenseSecret.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
+    )
+
+    // Chaves legadas (MD5 + salt fixo) ficam DESLIGADAS por padrão. Para uma migração gradual,
+    // defina ALLOW_LEGACY_LICENSE=true no ambiente (no CI: variável de repositório).
+    val legacyLicenseEnabled = System.getenv("ALLOW_LEGACY_LICENSE")?.trim().equals("true", ignoreCase = true)
+    buildConfigField("boolean", "LEGACY_LICENSE_ENABLED", legacyLicenseEnabled.toString())
 
     val expectedCertSha256 = (
       project.findProperty("EXPECTED_CERT_SHA256")?.toString()
diff --git a/app/src/main/java/com/example/data/service/PreferencesService.kt b/app/src/main/java/com/example/data/service/PreferencesService.kt
index 16926dd..d063f2e 100644
--- a/app/src/main/java/com/example/data/service/PreferencesService.kt
+++ b/app/src/main/java/com/example/data/service/PreferencesService.kt
@@ -181,8 +181,10 @@ class PreferencesService(context: Context) {
     }
 
     /**
-     * Generates valid autonomous key for a device matching the built-in admin key generator.
+     * LEGADO: gera a chave autônoma (MD5 + salt fixo). Só é aceita por [isLicenseValid] quando
+     * BuildConfig.LEGACY_LICENSE_ENABLED for true. Para emitir chaves novas use [generateValidKeyForDevice].
      */
+    @Deprecated("Algoritmo legado e previsível; use generateValidKeyForDevice (HMAC-SHA256).")
     fun generateAutonomousKeyForDevice(deviceMac: String): String {
         val cleanMac = deviceMac.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
         val paddedMac = cleanMac.padEnd(12, 'F')
@@ -203,21 +205,26 @@ class PreferencesService(context: Context) {
     }
 
     /**
-     * Verifies license key validity against both HMAC-SHA256 and Autonomous MD5 keys.
+     * Verifica a chave de ativação (HMAC-SHA256 com o segredo do BuildConfig).
+     * As chaves legadas (MD5 + salt fixo) só são aceitas quando BuildConfig.LEGACY_LICENSE_ENABLED
+     * for true (ALLOW_LEGACY_LICENSE=true no build): o algoritmo é público no APK, então qualquer
+     * pessoa consegue gerá-las offline.
      */
     fun isLicenseValid(): Boolean {
         val key = activationKey.uppercase().trim()
         if (key.isEmpty()) return false
 
-        // 1. Standard 3-segment HMAC-SHA256 check
+        // 1. HMAC-SHA256 de 3 segmentos (comparação em tempo constante)
         val expectedHmac = generateValidKeyForDevice(virtualMac).uppercase().trim()
-        if (key == expectedHmac) return true
+        if (MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), expectedHmac.toByteArray(Charsets.UTF_8))) return true
 
-        // 2. Autonomous Generator MD5 check (MK21_GOLDEN_SALT_2026)
+        if (!BuildConfig.LEGACY_LICENSE_ENABLED) return false
+
+        // 2. Legado: chave autônoma MD5 de 3 segmentos
         val expectedAutonomous = generateAutonomousKeyForDevice(virtualMac).uppercase().trim()
         if (key == expectedAutonomous) return true
 
-        // 3. Backward compatibility for 2-segment autonomous key if used
+        // 3. Legado: chave autônoma de 2 segmentos
         try {
             val cleanMac = virtualMac.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
             val paddedMac = cleanMac.padEnd(12, 'F')
diff --git a/app/src/main/java/com/example/ui/Screens.kt b/app/src/main/java/com/example/ui/Screens.kt
index a9401a4..68cc6c1 100644
--- a/app/src/main/java/com/example/ui/Screens.kt
+++ b/app/src/main/java/com/example/ui/Screens.kt
@@ -1459,7 +1459,7 @@ fun ServerConfigScreen(viewModel: AppViewModel, onNavigateToHome: () -> Unit) {
                                     indication = null
                                 ) {
                                     tapCount++
-                                    if (tapCount >= 5) {
+                                    if (com.example.BuildConfig.DEBUG && tapCount >= 5) {
                                         isAdminMode = true
                                         android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                     }
@@ -1655,7 +1655,7 @@ fun ServerConfigScreen(viewModel: AppViewModel, onNavigateToHome: () -> Unit) {
                             Button(
                                 onClick = {
                                     val trimmedInput = localKeyInput.trim()
-                                    if (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin") {
+                                    if (com.example.BuildConfig.DEBUG && (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin")) {
                                         isAdminMode = true
                                         licenseStatusMsg = "Modo Admin Liberado!"
                                         android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
@@ -8190,7 +8190,7 @@ fun SettingsScreen(viewModel: AppViewModel, onNavigateBack: () -> Unit) {
                                     indication = null
                                 ) {
                                     tapCount++
-                                    if (tapCount >= 5) {
+                                    if (com.example.BuildConfig.DEBUG && tapCount >= 5) {
                                         isAdminMode = true
                                         android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                     }
@@ -8412,7 +8412,7 @@ fun SettingsScreen(viewModel: AppViewModel, onNavigateBack: () -> Unit) {
                             Button(
                                 onClick = {
                                     val trimmedInput = localKeyInput.trim()
-                                    if (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin") {
+                                    if (com.example.BuildConfig.DEBUG && (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin")) {
                                         isAdminMode = true
                                         licenseStatusMsg = "Modo Admin Liberado!"
                                         android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
diff --git a/app/src/main/java/com/example/viewmodel/AppViewModel.kt b/app/src/main/java/com/example/viewmodel/AppViewModel.kt
index 6f94a46..fe5ef99 100644
--- a/app/src/main/java/com/example/viewmodel/AppViewModel.kt
+++ b/app/src/main/java/com/example/viewmodel/AppViewModel.kt
@@ -96,7 +96,8 @@ class AppViewModel(application: Application) : AndroidViewModel(application) {
         get() = preferencesService.virtualMac
 
     fun generateAutonomousKey(mac: String): String {
-        return preferencesService.generateAutonomousKeyForDevice(mac)
+        // Emite chave HMAC-SHA256 (o gerador MD5 legado foi descontinuado).
+        return preferencesService.generateValidKeyForDevice(mac)
     }
 
     fun activateLicense(key: String): Boolean {
diff --git a/app/src/test/java/com/example/ExampleRobolectricTest.kt b/app/src/test/java/com/example/ExampleRobolectricTest.kt
index cc15bfb..6cc6b88 100644
--- a/app/src/test/java/com/example/ExampleRobolectricTest.kt
+++ b/app/src/test/java/com/example/ExampleRobolectricTest.kt
@@ -82,13 +82,19 @@ class ExampleRobolectricTest {
     prefs.activationKey = validKey
     org.junit.Assert.assertTrue(prefs.isLicenseValid())
 
-    // With autonomous generator key, license is also valid
+    // Legacy MD5 key is only accepted when ALLOW_LEGACY_LICENSE=true at build time (default: off)
+    @Suppress("DEPRECATION")
     val autonomousKey = prefs.generateAutonomousKeyForDevice(mac)
     prefs.activationKey = autonomousKey
-    org.junit.Assert.assertTrue(prefs.isLicenseValid())
+    if (BuildConfig.LEGACY_LICENSE_ENABLED) {
+      org.junit.Assert.assertTrue(prefs.isLicenseValid())
+    } else {
+      org.junit.Assert.assertFalse(prefs.isLicenseValid())
+    }
 
     // Verify key from user's device MAC matches autonomous generator
     val sampleMac = "9D:59:2E:00:6A:1F"
+    @Suppress("DEPRECATION")
     val sampleGeneratedKey = prefs.generateAutonomousKeyForDevice(sampleMac)
     org.junit.Assert.assertEquals("MK-9F83-B09E-F4D4", sampleGeneratedKey)
   }
diff --git a/docs/RELEASE_SECRETS.md b/docs/RELEASE_SECRETS.md
new file mode 100644
index 0000000..ab2d6a0
--- /dev/null
+++ b/docs/RELEASE_SECRETS.md
@@ -0,0 +1,51 @@
+# Secrets, variáveis e checklist de release
+
+Este guia descreve o que precisa estar configurado em **GitHub → Settings → Secrets and variables → Actions**
+(no repositório `2fbg/FBGs-Streaming`, nível *Repository*, não *Environment*) para publicar um release assinado.
+
+## Secrets (aba *Secrets*)
+
+| Nome | Obrigatório | Descrição |
+|---|---|---|
+| `ANDROID_KEYSTORE_BASE64` | sim | Keystore de upload em base64 numa linha só: `base64 -w0 upload.jks` |
+| `STORE_PASSWORD` | sim | Senha do keystore |
+| `KEY_PASSWORD` | não | Senha da chave (se vazia, usa `STORE_PASSWORD`) |
+| `KEY_ALIAS` | não | Deve ser `upload` (é o alias lido por `app/build.gradle.kts`) |
+| `EXPECTED_CERT_SHA256` | recomendado | SHA-256 do certificado (pin do updater). Se vazio, o release deriva do keystore |
+| `LICENSE_SECRET` | **sim** | Segredo HMAC das chaves de ativação, mín. 16 caracteres. Gere com `openssl rand -base64 36` |
+
+## Variáveis (aba *Variables*)
+
+| Nome | Padrão | Descrição |
+|---|---|---|
+| `ALLOW_LEGACY_LICENSE` | `false` | `true` volta a aceitar chaves legadas (MD5 + salt fixo). Use só em migração temporária |
+
+## Continuidade da chave de assinatura
+
+O Android só atualiza um app por cima de outro se o certificado for o mesmo, e o updater do app também
+valida o certificado. Antes de cadastrar a keystore:
+
+1. Se existir o `.jks` original usado na release `v2.3.7`, use-o.
+2. Se não existir, uma keystore nova funciona, mas quem já tem o app instalado precisa desinstalar e instalar de novo.
+3. Guarde o `.jks` e as senhas em local seguro fora do repositório. Nunca faça commit de `.jks`, `secrets.properties` ou `.env`.
+
+## Validar sem publicar (dry run)
+
+```bash
+gh workflow run release.yml --ref main -f tag=v2.8.0 -f dry_run=true
+```
+
+Para exigir build assinado no workflow de validação: *Actions → Validate → Run workflow* com `require_signed = true`.
+
+## Publicar
+
+```bash
+git tag v2.8.0 && git push origin v2.8.0
+```
+
+## Licenciamento
+
+- As chaves novas são HMAC-SHA256 do MAC virtual do dispositivo com `LICENSE_SECRET`.
+- O painel admin (gerador de chaves) só abre em builds `debug`; em release ele não é acessível.
+- Como o segredo fica dentro do APK, isso dificulta mas não impede um ataque por engenharia reversa.
+  Para proteção forte, valide a licença em um servidor.
diff --git a/main b/main
deleted file mode 100644
index e69de29..0000000
diff --git a/scripts/verify-release-manifests.mjs b/scripts/verify-release-manifests.mjs
index 6084ce3..8fc802f 100644
--- a/scripts/verify-release-manifests.mjs
+++ b/scripts/verify-release-manifests.mjs
@@ -5,6 +5,8 @@
  *  - version.json e espelhos (api/, web/, public/, smart-tv/)
  *  - SHA-256 e tamanho do binário APK (e opcionalmente IPK)
  *  - URL da release do APK quando --require-release-url for informado
+ *  - apps.json e smart-tv/apps.json (e espelhos web/ e public/): versão, ipkUrl/sourceUrl e
+ *    ipkHash.sha256 consistentes com version.json e com o IPK (loja webOS / Homebrew Channel)
  */
 import crypto from 'node:crypto';
 import fs from 'node:fs';
@@ -34,6 +36,75 @@ export function parseGradleVersions(gradlePath) {
   };
 }
 
+const APPS_MANIFEST_PATHS = ['apps.json', 'smart-tv/apps.json', 'web/apps.json', 'public/apps.json'];
+const REPO_SLUG = '2fbg/FBGs-Streaming';
+
+function readJsonSafe(absPath) {
+  try {
+    return { data: JSON.parse(fs.readFileSync(absPath, 'utf8')), error: null };
+  } catch (err) {
+    return { data: null, error: err.message };
+  }
+}
+
+/**
+ * Valida os catálogos apps.json usados por lojas webOS (ex.: Homebrew Channel).
+ * Aceita sourceUrl/ipkUrl apontando para a release OU para o raw da main (mesmo arquivo),
+ * exceto quando requireReleaseUrl=true: aí exige a URL da GitHub Release.
+ */
+export function verifyAppsManifests({ rootDir = DEFAULT_ROOT, rootManifest, requireReleaseUrl = false, ipkPath = null } = {}) {
+  const errors = [];
+  const version = String(rootManifest.version || '');
+  const ipkName = `mk21play_${version}_all.ipk`;
+  const releaseIpkUrl = `https://github.com/${REPO_SLUG}/releases/download/v${version}/${ipkName}`;
+  const rawIpkUrl = `https://raw.githubusercontent.com/${REPO_SLUG}/main/smart-tv/${ipkName}`;
+  const expectedIpkSha = String(rootManifest.ipkSha256 || '').trim().toLowerCase();
+  const actualIpkSha = ipkPath && fs.existsSync(ipkPath) ? sha256File(ipkPath) : null;
+
+  for (const rel of APPS_MANIFEST_PATHS) {
+    const abs = path.join(rootDir, rel);
+    if (!fs.existsSync(abs)) {
+      errors.push(`Catálogo ausente: ${rel}`);
+      continue;
+    }
+    const { data, error } = readJsonSafe(abs);
+    if (error) {
+      errors.push(`${rel}: JSON inválido (${error})`);
+      continue;
+    }
+    const pkg = Array.isArray(data?.packages) ? data.packages[0] : null;
+    if (!pkg || !pkg.manifest) {
+      errors.push(`${rel}: packages[0].manifest ausente`);
+      continue;
+    }
+    if (String(pkg.version || '') !== version) {
+      errors.push(`${rel}: packages[0].version (${pkg.version}) != version.json (${version})`);
+    }
+    if (String(pkg.manifest.version || '') !== version) {
+      errors.push(`${rel}: manifest.version (${pkg.manifest.version}) != version.json (${version})`);
+    }
+    if (!pkg.sourceUrl || pkg.sourceUrl !== pkg.manifest.ipkUrl) {
+      errors.push(`${rel}: sourceUrl e manifest.ipkUrl devem ser iguais (${pkg.sourceUrl} vs ${pkg.manifest.ipkUrl})`);
+    }
+    const allowed = requireReleaseUrl ? [releaseIpkUrl] : [releaseIpkUrl, rawIpkUrl];
+    if (!allowed.includes(String(pkg.manifest.ipkUrl || ''))) {
+      errors.push(`${rel}: manifest.ipkUrl deve ser ${allowed.join(' ou ')} (atual: ${pkg.manifest.ipkUrl})`);
+    }
+    const declared = String(pkg.manifest.ipkHash?.sha256 || '').trim().toLowerCase();
+    if (!/^[a-f0-9]{64}$/.test(declared)) {
+      errors.push(`${rel}: manifest.ipkHash.sha256 inválido ("${declared}")`);
+    } else {
+      if (expectedIpkSha && declared !== expectedIpkSha) {
+        errors.push(`${rel}: ipkHash.sha256 (${declared}) != ipkSha256 do version.json (${expectedIpkSha})`);
+      }
+      if (actualIpkSha && declared !== actualIpkSha) {
+        errors.push(`${rel}: ipkHash.sha256 (${declared}) não bate com ${ipkPath} (${actualIpkSha})`);
+      }
+    }
+  }
+  return errors;
+}
+
 export function verifyReleaseManifests({
   rootDir = DEFAULT_ROOT,
   apkPath = path.join(rootDir, 'mk21.apk'),
@@ -125,6 +196,15 @@ export function verifyReleaseManifests({
     }
   }
 
+  errors.push(
+    ...verifyAppsManifests({
+      rootDir,
+      rootManifest,
+      requireReleaseUrl,
+      ipkPath: ipkPath && fs.existsSync(ipkPath) ? ipkPath : null,
+    })
+  );
+
   return { ok: errors.length === 0, errors, version: targetVersion, versionCode: targetCode };
 }
 
@@ -288,6 +368,25 @@ export async function verifyRemoteAssets({
     });
   }
 
+  // apps.json / smart-tv/apps.json: a URL que a loja webOS resolve também precisa estar no ar
+  // com o hash declarado em manifest.ipkHash.sha256 (deduplicado por URL).
+  const seenUrls = new Set(targets.map((t) => t.url));
+  for (const rel of ['apps.json', 'smart-tv/apps.json']) {
+    const abs = path.join(rootDir, rel);
+    if (!fs.existsSync(abs)) continue;
+    const { data } = readJsonSafe(abs);
+    const pkgManifest = Array.isArray(data?.packages) ? data.packages[0]?.manifest : null;
+    const appsIpkUrl = String(pkgManifest?.ipkUrl || '').trim();
+    if (!appsIpkUrl || seenUrls.has(appsIpkUrl)) continue;
+    seenUrls.add(appsIpkUrl);
+    targets.push({
+      label: `${rel} ipkUrl`,
+      url: appsIpkUrl,
+      expectedSha256: String(pkgManifest?.ipkHash?.sha256 || '').trim().toLowerCase(),
+      expectedSizeBytes: Number(rootManifest.ipkSizeBytes || 0),
+    });
+  }
+
   for (const target of targets) {
     const res = await verifyRemoteAsset({
       ...target,
diff --git a/smart-tv/tests/release-manifest.test.js b/smart-tv/tests/release-manifest.test.js
index 36e9476..c35d475 100644
--- a/smart-tv/tests/release-manifest.test.js
+++ b/smart-tv/tests/release-manifest.test.js
@@ -4,15 +4,26 @@ import crypto from 'node:crypto';
 import fs from 'node:fs';
 import os from 'node:os';
 import path from 'node:path';
-import { verifyReleaseManifests, verifyRemoteAssets } from '../../scripts/verify-release-manifests.mjs';
+import { verifyAppsManifests, verifyReleaseManifests, verifyRemoteAssets } from '../../scripts/verify-release-manifests.mjs';
 
-function setupTempReleaseFixture({ version = '2.8.0', versionCode = 280, gradleCode = 280, apkContent = 'signed-apk-bytes', manifestApkSha = null } = {}) {
+function setupTempReleaseFixture({
+  version = '2.8.0',
+  versionCode = 280,
+  gradleCode = 280,
+  apkContent = 'signed-apk-bytes',
+  manifestApkSha = null,
+  appsIpkUrl = null,
+  appsIpkSha = null,
+  appsVersion = null,
+} = {}) {
   const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mk21-release-test-'));
   fs.mkdirSync(path.join(dir, 'app'), { recursive: true });
   fs.mkdirSync(path.join(dir, 'api'), { recursive: true });
   fs.mkdirSync(path.join(dir, 'web/api'), { recursive: true });
   fs.mkdirSync(path.join(dir, 'public/api'), { recursive: true });
   fs.mkdirSync(path.join(dir, 'smart-tv'), { recursive: true });
+  fs.mkdirSync(path.join(dir, 'web'), { recursive: true });
+  fs.mkdirSync(path.join(dir, 'public'), { recursive: true });
 
   const apkPath = path.join(dir, 'mk21.apk');
   fs.writeFileSync(apkPath, apkContent);
@@ -54,6 +65,27 @@ function setupTempReleaseFixture({ version = '2.8.0', versionCode = 280, gradleC
     fs.writeFileSync(path.join(dir, rel), JSON.stringify(manifest, null, 2));
   }
 
+  const releaseIpkUrl = `https://github.com/2fbg/FBGs-Streaming/releases/download/v${version}/mk21play_${version}_all.ipk`;
+  const apps = {
+    name: 'MK21 Streaming Repository',
+    packages: [
+      {
+        id: 'com.mk21.tv',
+        version: appsVersion || version,
+        sourceUrl: appsIpkUrl || releaseIpkUrl,
+        manifest: {
+          id: 'com.mk21.tv',
+          version: appsVersion || version,
+          ipkUrl: appsIpkUrl || releaseIpkUrl,
+          ipkHash: { sha256: appsIpkSha || ipkSha },
+        },
+      },
+    ],
+  };
+  for (const rel of ['apps.json', 'smart-tv/apps.json', 'web/apps.json', 'public/apps.json']) {
+    fs.writeFileSync(path.join(dir, rel), JSON.stringify(apps, null, 2));
+  }
+
   return { dir, apkPath, ipkPath };
 }
 
@@ -156,3 +188,50 @@ test('verifyRemoteAssets falls back from HEAD 405 to Range GET, catches SHA-256
     fs.rmSync(dir, { recursive: true, force: true });
   }
 });
+
+test('verifyReleaseManifests fails when apps.json ipkHash diverges from the IPK binary', () => {
+  const { dir, apkPath, ipkPath } = setupTempReleaseFixture({ appsIpkSha: 'a'.repeat(64) });
+  try {
+    const res = verifyReleaseManifests({ rootDir: dir, apkPath, ipkPath, requireApkHashMatch: true });
+    assert.equal(res.ok, false);
+    assert.ok(res.errors.some((e) => e.includes('apps.json') && e.includes('ipkHash.sha256')));
+  } finally {
+    fs.rmSync(dir, { recursive: true, force: true });
+  }
+});
+
+test('verifyReleaseManifests fails when apps.json version diverges from version.json', () => {
+  const { dir, apkPath, ipkPath } = setupTempReleaseFixture({ appsVersion: '2.7.0' });
+  try {
+    const res = verifyReleaseManifests({ rootDir: dir, apkPath, ipkPath, requireApkHashMatch: true });
+    assert.equal(res.ok, false);
+    assert.ok(res.errors.some((e) => e.includes('apps.json') && e.includes('2.7.0')));
+  } finally {
+    fs.rmSync(dir, { recursive: true, force: true });
+  }
+});
+
+test('verifyAppsManifests accepts raw main URL normally but requires the GitHub Release URL with requireReleaseUrl', () => {
+  const rawUrl = 'https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21play_2.8.0_all.ipk';
+  const { dir } = setupTempReleaseFixture({ appsIpkUrl: rawUrl });
+  try {
+    const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'version.json'), 'utf8'));
+    assert.deepEqual(verifyAppsManifests({ rootDir: dir, rootManifest: manifest }), []);
+    const strict = verifyAppsManifests({ rootDir: dir, rootManifest: manifest, requireReleaseUrl: true });
+    assert.ok(strict.length > 0 && strict.every((e) => e.includes('releases/download')));
+  } finally {
+    fs.rmSync(dir, { recursive: true, force: true });
+  }
+});
+
+test('verifyAppsManifests reports a missing apps.json catalog', () => {
+  const { dir } = setupTempReleaseFixture();
+  try {
+    fs.rmSync(path.join(dir, 'smart-tv/apps.json'));
+    const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'version.json'), 'utf8'));
+    const errors = verifyAppsManifests({ rootDir: dir, rootManifest: manifest });
+    assert.ok(errors.some((e) => e.includes('Catálogo ausente: smart-tv/apps.json')));
+  } finally {
+    fs.rmSync(dir, { recursive: true, force: true });
+  }
+});
````
