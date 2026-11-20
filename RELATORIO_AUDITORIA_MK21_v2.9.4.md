# Relatório de Auditoria Independente — MK21 Streaming v2.9.4

Auditoria somente leitura. Nada foi publicado, tagueado, sobrescrito ou apagado durante a verificação.

---

## 1. Resumo executivo

| Item | Valor |
|---|---|
| Versão auditada | `2.9.4` / `versionCode 294` |
| Repositório | `https://github.com/2fbg/FBGs-Streaming` |
| Branch auditada | `main` |
| Commit de `main` | `b0628346f2b9eb8f123c20cafa08d67d51a49381` (2026-11-20T14:57:32Z) |
| Tag `v2.9.4` | `c3dc8fbcc4ffd2e7122258c3368daf4f2d19e002` (1 commit **antes** de `main`) |
| Data da auditoria | 2026-11-20 |
| Conclusão | Release publicada, binários íntegros e assinados, mas com **divergência estrutural entre a tag e os assets publicados** |

**Classificação: `PUBLICADO COM RESSALVAS DE RASTREABILIDADE`.**

O que foi comprovado: os assets publicados são APK/IPK reais, íntegros, assinados com certificado de produção (não debug), com hash e tamanho idênticos aos manifestos, ao `SHA256SUMS` e ao binário versionado na `main`. A suíte Node passa (94 aprovados, 1 ignorado) e o `license-api.test.js` **está** dentro da lista explícita do CI.

O que impede a classificação sem ressalvas: a tag `v2.9.4` aponta para um commit cujo APK **não é** o APK publicado (`89fecb19…`, 4.268.696 bytes contra `972f5483…`, 4.285.068 bytes). Os manifestos e binários finais só existem no commit `b062834`, criado pelo próprio pipeline **depois** de publicar a release, com `[skip ci]`. A release é operacional; a tag não é reproduzível.

---

## 2. Evidências verificadas

| # | Afirmação | Fonte / comando | Resultado | Confiança |
|---|---|---|---|---|
| E1 | `main` está em `b062834` | `git log --oneline -3` | Confirmado | alta |
| E2 | Tag `v2.9.4` = `c3dc8fb`, 1 commit antes de `main` | `git rev-parse v2.9.4`; `git rev-list --count v2.9.4..HEAD` | Confirmado (1) | alta |
| E3 | Manifestos em 2.9.4 / 294 em todas as cópias | leitura de `version.json`, `api/`, `public/`, `public/api/`, `web/`, `web/api/`, `smart-tv/`, `smart-tv/mk21-tv/` | 8/8 idênticos | alta |
| E4 | `app/build.gradle.kts` em 294 / "2.9.4" | `grep versionCode/versionName` | `versionCode = 294`, `versionName = "2.9.4"` | alta |
| E5 | Binário local == manifesto da `main` | `sha256sum mk21.apk` | `972f5483…77c`, 4.285.068 B — igual | alta |
| E6 | IPK local == manifesto da `main` | `sha256sum smart-tv/mk21play_2.9.4_all.ipk` | `4814b5ca…150`, 528.000 B — igual | alta |
| E7 | Asset publicado == binário versionado | download do asset + `cmp` | **idêntico** byte a byte | alta |
| E8 | `SHA256SUMS` publicado corresponde aos assets | download do asset | 2 linhas, ambos os hashes batem | alta |
| E9 | `digest` da API do GitHub == hash local | `GET /releases/tags/v2.9.4` | `sha256:972f5483…` / `sha256:4814b5ca…` | alta |
| E10 | APK é pacote Android válido, não HTML | `file mk21.apk` | `Android package (APK)` | alta |
| E11 | `versionCode`/`versionName` reais dentro do APK | `aapt2 dump badging` | `versionCode='294' versionName='2.9.4'` | alta |
| E12 | `applicationId` do APK | `aapt2 dump badging` | `com.aistudio.multiservidor.iptvmk` | alta |
| E13 | SDKs | `aapt2 dump badging` | minSdk 24, targetSdk 36, compileSdk 36 | alta |
| E14 | Assinatura válida e de produção | `apksigner verify --print-certs` | `CN=FBGs Streaming, O=FBGs Streaming, C=BR`, SHA-256 `8ca07de4…d7c` | alta |
| E15 | Assinatura NÃO é debug | `apksigner` + busca por "Android Debug" | ausente | alta |
| E16 | Assinatura compatível com a versão anterior | `apksigner` no asset `v2.9.3` | mesmo certificado `8ca07de4…` | alta |
| E17 | Esquemas de assinatura | `apksigner verify --verbose` | v1 `false`, **v2 `true`**, v3 `false` | alta |
| E18 | IPK é pacote Debian/webOS válido | `file` + `dpkg-deb --info` | `Debian binary package (format 2.0)` | alta |
| E19 | Metadados do IPK | `dpkg-deb --info` | `Package: com.mk21.tv`, `Version: 2.9.4`, `Architecture: all` | alta |
| E20 | Conteúdo do IPK | `dpkg-deb --contents` | 28 entradas, caminho `usr/palm/applications/com.mk21.tv/` | alta |
| E21 | `version.json` interno do IPK | `tar -xO` do payload | `2.9.4` / 294 / `apkSha256 972f5483…` | alta |
| E22 | `ipkSha256` interno vazio | idem | `""` e `ipkSizeBytes: 0` (auto-hash impossível) | alta |
| E23 | Manifestos verificam contra binários | `node scripts/verify-release-manifests.mjs --apk --ipk --require-release-url` | `OK: v2.9.4 (294) validado` | alta |
| E24 | Revalidação remota pós-publicação | mesmo script com `--remote` | APK HTTP 200 / hash igual; IPK HTTP 200 / hash igual | alta |
| E25 | Workflows verdes no commit auditado | API `/actions/runs` | `Validate 44756013617` success; `Release 44757225630` success | alta |
| E26 | `license-api.test.js` está no CI | `.github/workflows/validate.yml:363` | presente na lista `node --test` | alta |
| E27 | Suíte Node passa | `node --test smart-tv/tests/*.test.js` | 95 testes, 94 pass, 1 skip, **0 fail** | alta |
| E28 | Suíte Jest passa | `npm test` | 3 suites, 28 testes aprovados | alta |
| E29 | `release.yml` valida assinatura antes de publicar | linhas 262-266 | `apksigner verify --verbose` + `--print-certs` | alta |
| E30 | `release.yml` valida manifestos antes e depois de publicar | linhas 358 e 397 | `verify-release-manifests` com e sem `--remote` | alta |
| E31 | `release.yml` valida assets remotos | passo "Verify published remote release assets" (389) | presente | alta |
| E32 | APK na tag `v2.9.4` | `git show v2.9.4:mk21.apk | sha256sum` | `89fecb19…419`, 4.268.696 B | alta |
| E33 | Manifesto na tag aponta para esse APK | `git show v2.9.4:version.json` | `apkSha256 89fecb19…`, `sizeBytes 4268696` | alta |
| E34 | IPK `2.9.4` não existe na árvore da tag | `git diff --name-only v2.9.4..HEAD` | `smart-tv/mk21play_2.9.4_all.ipk` aparece como adicionado depois | alta |
| E35 | Sanitização cobre credenciais | `app/src/main/java/com/example/utils/LogSanitizer.kt` | regex para user/pass/token/auth/api_key/pin/cookie, Basic/Bearer, `MKL1.*`, corpo de playlist e caminho Xtream | alta |
| E36 | Logger sanitiza ao gravar | `ObservabilityLogger.kt:241-246` | `LogSanitizer.sanitizeText` em screen/event/phase/correlationId | alta |
| E37 | OTA tem pin de certificado e recusa sem pin | `AppViewModel.kt:2587,2619,2733,2780` | usa `BuildConfig.EXPECTED_CERT_SHA256`; sem pin, instalação automática recusada | alta |
| E38 | OTA limita redirects e valida hash/mínimo | `Mk21UpdateManager.kt:60,74,165,179` | `MAX_REDIRECTS = 5`, regex SHA-256, `minApkSizeBytes` 1 MB | alta |
| E39 | Cobertura de eventos de observabilidade | varredura literal por nome de evento nos fontes | muito abaixo do especificado (ver §6) | média |
| E40 | Testes Android | `grep -c @Test` | 70 casos em 14 arquivos — **não executados** nesta auditoria | alta (contagem) |

---

## 3. Tabela de inconsistências

| ID | Severidade | Área | Evidência | Impacto | Correção exigida | Bloqueia release? |
|---|---|---|---|---|---|---|
| I-01 | **ALTA** | Rastreabilidade | Tag `v2.9.4` (`c3dc8fb`) contém APK `89fecb19…` (4.268.696 B); asset publicado é `972f5483…` (4.285.068 B) | Quem buildar a partir da tag obtém binário diferente do publicado; auditoria por tag não reproduz a release | Publicar os assets a partir do commit que contém os hashes finais, ou mover a tag para o commit de publicação; nunca deixar a tag atrás do binário publicado | Não (assets publicados são consistentes entre si) |
| I-02 | MÉDIA | Rastreabilidade | `smart-tv/mk21play_2.9.4_all.ipk` só existe em `b062834`, não na tag | IPK de 2.9.4 não é reconstruível pela tag | Mesma correção de I-01 | Não |
| I-03 | MÉDIA | Pipeline | `release.yml` publica (368) e depois commita manifestos/binário com `[skip ci]` (408) | Divergência é **estrutural**: toda release futura nascerá com tag atrás do artefato | Reordenar: gerar binário final → commitar → taguear → publicar; ou taguear o commit de publicação automaticamente | Não |
| I-04 | **ALTA** | Produto / OTA | `applicationId` = `com.aistudio.multiservidor.iptvmk`; assinatura `8ca07de4…` (FBGs Streaming) difere da usada em 2.3.7 (`Fabio Guarniere`, `9a5a4bb2…`) | Instalações antigas (2.3.x) não recebem atualização: package id e certificado diferentes. Conviverão dois apps no aparelho | Declarar a mudança no release notes, definir caminho de migração (aviso in-app + exportar/importar servidores) | Não para 2.9.4; sim para prometer "update" a usuários antigos |
| I-05 | MÉDIA | Observabilidade | Cobertura literal por evento muito abaixo do exigido (§6): Android boot 2/9, catalog 3/18, ota 2/10; Web login 0/9, ota 0/10 | Não é possível reconstruir boot, login, indexação de catálogo nem OTA a partir dos logs, que é justamente o motivo da telemetria | Implementar os eventos ausentes mantendo os nomes acordados, ou documentar oficialmente o conjunto real | Não |
| I-06 | BAIXA | webOS | `smart-tv/mk21-tv/version.json` tem `ipkSha256: ""` e `ipkSizeBytes: 0` | Se algum cliente ler esse campo, recebe valor vazio | Documentar que o campo é intencionalmente vazio no arquivo embarcado | Não |
| I-07 | BAIXA | Assinatura | APK assinado apenas com v2 (`v3 false`) | Sem rotação de chave e sem prova de linhagem v3 | Considerar habilitar v3 no `signingConfig` (não urgente; minSdk 24 aceita v2) | Não |
| I-08 | INFORMATIVA | Alinhamento | O prompt de auditoria fornecido descreve v2.9.3/293 | Comparações históricas precisam ser relidas como 2.9.4/294 | Atualizar o prompt para 2.9.4/294 e "não criar v2.9.5" | Não |

---

## 4. Tabela de versões e hashes

| Origem | Versão | Code | APK tamanho | APK SHA-256 | IPK tamanho | IPK SHA-256 | Status |
|---|---|---:|---:|---|---:|---|---|
| Tag `v2.9.4` (`c3dc8fb`) | 2.9.4 | 294 | 4.268.696 | `89fecb19a1f172c84195eb1f396369d1ffaa3b628620ce9086077e3655139419` | — (ausente) | `6253c8db8e809d708ac2e55a6bf7cc6eaab3819f2787bbd2cd715420a0dfbd9e` (declarado) | Divergente do publicado |
| `main` `b062834` / repo local | 2.9.4 | 294 | 4.285.068 | `972f548320d5d7e9b0d9c5eeb1d8baaa50c486f4ef89ca7f9f0b514974f9477c` | 528.000 | `4814b5ca2b071b81fd581a89107aec63b2a843be25ebb95a190bf3c357fec150` | Fonte operacional |
| GitHub Release `v2.9.4` (assets) | 2.9.4 | 294 | 4.285.068 | `972f5483…77c` (digest da API) | 528.000 | `4814b5ca…150` | Publicado |
| `SHA256SUMS` publicado | — | — | — | igual ao asset | — | igual ao asset | Consistente |
| IPK embarcado (`version.json` interno) | 2.9.4 | 294 | 4.285.068 | `972f5483…77c` | 0 | `""` (intencional) | Consistente |
| Tag `v2.9.3` (`326d0ee`) | 2.9.3 | 293 | — | certificado `8ca07de4…` | — | — | Assinatura igual à 2.9.4 |
| 2.3.7 (histórico) | 2.3.7 | 237 | — | certificado `9a5a4bb2…` (CN=Fabio Guarniere) | — | — | **Incompatível** com 2.9.x |

---

## 5. Testes

| Conjunto | Origem | Resultado |
|---|---|---|
| Node `smart-tv/tests/*.test.js` (16 arquivos) | local, `node --test` | **95 testes: 94 aprovados, 1 ignorado, 0 falhas** |
| Jest (`npm test`) | local | 3 suites, **28 aprovados** |
| Android unit tests | local | 70 `@Test` em 14 arquivos — **não executados** (sem rodar Gradle nesta auditoria) |
| CI `Validate` | GitHub Actions, `44756013617` | success |
| CI `Release` | GitHub Actions, `44757225630` | success |

Observações de honestidade:

- Uma primeira execução local de `license-api.test.js` e `cloud-sync.test.js` **falhou** com `MODULE_NOT_FOUND: @neondatabase/serverless`. A causa era ausência de `node_modules`, não regressão: após `npm ci`, ambos passaram (8/8). Registro isso porque uma falha de ambiente não deve ser confundida com defeito de código nem escondida.
- Os 70 testes Android são contagem estática; afirmar "69/70 testes aprovados" exige executar `./gradlew test`, o que não foi feito aqui.
- Nenhum teste físico foi realizado: não há TV, Android TV ou celular neste ambiente.

---

## 6. Cobertura de observabilidade

Varredura literal pelos nomes de evento acordados nos fontes de cada plataforma (Android `.kt`, Web `js/` + `web/js/`, Smart TV `smart-tv/mk21-tv/**/*.js`).

| Grupo | Android | Web | Smart TV |
|---|---|---|---|
| boot (9) | 2/9 | 3/9 | 3/9 |
| login (9) | 2/9 | 0/9 | 3/9 |
| navegação (10) | 1/10 | 3/10 | 6/10 |
| catálogo/sync (18) | 3/18 | 2/18 | 2/18 |
| player (14) | 8/14 | 3/14 | 5/14 |
| OTA (10) | 2/10 | 0/10 | 0/10 |

Classificação: `implementado sem teste` onde o nome existe; `não encontrado` nos demais. Confiança **média**: a busca é por nome literal, e um evento pode existir com nome diferente ou ser montado dinamicamente — nesse caso o evento é, na prática, não consultável pela nomenclatura acordada, o que já é um problema de contrato de telemetria.

Ponto específico do pedido: `channel_change_complete` existe, mas a auditoria **não** confirmou que ele é emitido no primeiro frame renderizado; a presença do nome não prova a semântica.

---

## 7. Auditoria de segurança OTA

Validado por código:

- Limite de redirects (`MAX_REDIRECTS = 5`) e execução com redirects validados.
- `apkUrl` passa por allowlist (`isAllowedUpdateUrl`) e `apkSha256` por regex hexadecimal de 64 caracteres.
- Tamanho mínimo do APK (1 MB) para descartar payload truncado.
- Verificação de assinatura do APK baixado (`ApkSignatureVerifier.verifyApkArchiveSignature`) com pin em `BuildConfig.EXPECTED_CERT_SHA256`.
- **Sem pin configurado, a instalação automática é recusada** e o app orienta baixar pelo navegador — comportamento conservador e correto.
- `probeApkReachable` evita instalar quando o manifesto já aponta para uma versão cujo asset ainda não subiu.
- `FileProvider` presente para conceder leitura do arquivo.

Não comprovado:

- Rejeição explícita de HTML/JSON no lugar do APK por **magic bytes** antes da verificação de assinatura — não encontrei checagem de magic bytes por busca textual; a defesa efetiva é a assinatura + tamanho mínimo + `ZipFile`, o que é aceitável, mas não é o mesmo que o critério "payload sem magic bytes APK/ZIP" do prompt.
- Instalação real em dispositivo, inclusive com hash adulterado e certificado estranho.

---

## 8. Performance e memória

Não auditado empiricamente. Nenhuma medição de PSS, native heap, graphics PSS, tempo de parsing de catálogo grande ou tamanho de Room/IndexedDB foi executada nesta sessão. Qualquer número de performance nesta release permanece **alegação**, não evidência. O único dado de tamanho verificável é o do binário: APK 4,29 MB e IPK 528 KB — ambos menores que nas versões 2.3.x (APK ~5,7 MB), o que é consistente com a reestruturação, mas não prova desempenho.

---

## 9. Decisão

**`PUBLICADO COM RESSALVAS DE RASTREABILIDADE`**

A release v2.9.4 é publicável e operacional: binários válidos, assinados com certificado de produção consistente com a 2.9.3, hashes e tamanhos conferindo em todas as camadas (binário local, manifesto, `SHA256SUMS`, `digest` da API, verificação remota), suítes Node e Jest verdes e `license-api.test.js` presente no CI.

As ressalvas não são sobre o que foi publicado, e sim sobre **como**: a tag `v2.9.4` não reproduz o artifact publicado, e a estrutura do `release.yml` garante que isso se repita na próxima versão. Não reescrevi a tag nem publiquei nada — a correção pertence ao pipeline, a partir da 2.9.5.

---

## 10. Próximos passos

1. **Corrigir a ordem do pipeline** (`.github/workflows/release.yml`): gerar binário final → commitar manifestos → criar tag no commit final → publicar assets. Enquanto isso não muda, toda release terá tag divergente.
2. **Documentar a quebra de continuidade 2.3.x → 2.9.x** (package id `com.aistudio.multiservidor.iptvmk` + novo certificado) no release notes e no app, com orientação de migração de servidores.
3. **Fechar o contrato de telemetria**: implementar ou renomear oficialmente os eventos ausentes de boot, catálogo/sync, player e OTA; hoje não é possível reconstruir o boot nem o zapping a partir dos logs.
4. **Adicionar checagem explícita de magic bytes** no OTA antes da assinatura, para alinhar com o critério de segurança declarado.
5. **Executar os testes Android** (`./gradlew test`) em ambiente com SDK para converter a contagem estática de 70 casos em evidência de execução.
6. **Medir** performance em aparelho real (PSS/native/graphics, catálogo de 300 mil itens, troca rápida de canal) antes de qualquer alegação de estabilidade.
