# Relatório de Auditoria — MK21 Streaming v2.9.5

**Escopo:** rastreabilidade entre tag, commit carimbado e binários publicados (APK/IPK/SHA256SUMS).
**Modo:** somente leitura. Nenhuma tag foi movida e nenhum asset foi reenviado/sobrescrito durante a auditoria.
**Prompt aplicado:** `Prompt_Auditor_MK21_Streaming_v2.9.4.md` (estrutura do relatório final), adaptado ao recorte pedido.

---

## 1. Resumo executivo

```text
- versão auditada: 2.9.5 (versionCode 295)
- tag: v2.9.5 (tag leve) -> ff3325ef90901fba3ac17ab3428b7faacaecc39c
- commit carimbado: ff3325e "chore(release): stamp v2.9.5 manifests and binaries [skip ci]"
- data da auditoria: 2026-11-20 (~16:55 UTC)
- conclusão geral: tag, commit carimbado, manifesto e assets publicados são o MESMO estado
- classificação: aprovado (rastreabilidade completa)
```

Diferente da 2.9.4 — em que a tag `c3dc8fb` apontava para o commit **pré-carimbo** e o APK da tag (`89fecb19…`, 4.268.696 B) divergia do APK publicado (`972f5483…`, 4.285.068 B) —, a 2.9.5 publica a tag **depois** do commit que contém os binários finais.

## 2. Evidências verificadas

| # | Afirmação | Fonte / comando | Resultado | Confiança |
|---|---|---|---|---|
| E1 | A tag `v2.9.5` é leve e aponta para o commit carimbado | `git cat-file -t refs/tags/v2.9.5` / `git rev-parse` | `commit`; `ff3325ef90901fba3ac17ab3428b7faacaecc39c` | alta |
| E2 | `main` e a tag são o mesmo commit | `git ls-remote origin main`, `git ls-remote --tags origin v2.9.5` | `ff3325ef…` nos dois refs | alta |
| E3 | Não há diferença de árvore entre tag e `main` | `git diff --stat FETCH_HEAD refs/tags/v2.9.5` | saída vazia | alta |
| E4 | O commit carimbado é filho direto do bump | `git log --oneline -3 refs/tags/v2.9.5` | `ff3325e` ← `9d394e9 chore(release): bump para 2.9.5/295` | alta |
| E5 | O APK dentro da árvore da tag é o binário publicado | `git show FETCH_HEAD:mk21.apk \| sha256sum` | `d9c7c4289811c7b4542d73be7455be258f289d0e0c7abc2e9a3008a3a0131873` | alta |
| E6 | O IPK dentro da árvore da tag é o binário publicado | `git show FETCH_HEAD:smart-tv/mk21play_2.9.5_all.ipk \| sha256sum` | `3f78e3d0fd9aff7d44b90a2ed39973ca216dd0f4c0710f04c0510a47453de522` | alta |
| E7 | Os assets publicados têm exatamente esses hashes e tamanhos | download dos 3 assets + `sha256sum` | APK `d9c7…1873` / 4.285.079 B; IPK `3f78…e522` / 527.986 B | alta |
| E8 | O `SHA256SUMS` publicado confere com os downloads | `sha256sum -c SHA256SUMS` (arquivos renomeados como no download) | `mk21.apk: OK`, `mk21play_2.9.5_all.ipk: OK` | alta |
| E9 | Manifestos e espelhos apontam para os assets reais e estão em sincronia | `node scripts/verify-release-manifests.mjs --apk … --ipk … --require-release-url --remote`; `node scripts/sync-web.mjs --check` | `OK: v2.9.5 (295) validado`; `REMOTE OK: apkUrl HTTP 200`, `ipkUrl HTTP 200`; 25 arquivos sincronizados | alta |
| E10 | O APK publicado é da versão 2.9.5/295 e do app correto | `aapt2 dump badging mk21.apk` | `package: name='com.aistudio.multiservidor.iptvmk' versionCode='295' versionName='2.9.5'` | alta |
| E11 | O APK não é debug e mantém a continuidade de assinatura | `apksigner verify --verbose --print-certs mk21.apk` | v2 `true`; DN `CN=FBGs Streaming, O=FBGs Streaming, C=BR`; SHA-256 `8ca07de4…486d7c`; RSA 3072; sem `CN=Android Debug` | alta |
| E12 | O IPK publicado carrega o manifesto interno da versão | extração `ar x` + `data.tar.gz` → `usr/palm/applications/com.mk21.tv/version.json` | `2.9.5` / `295` / `apkSha256 d9c7…1873` / `ipkSha256` vazio (correto: pacote não carrega hash de si) / URLs `v2.9.5` | alta |
| E13 | Catálogos de loja (webOS) batem com o IPK | `verify-release-manifests` (apps.json + smart-tv/apps.json) | `2.9.5`, `ipkHash 3f78e3d0…`, URL da release | alta |
| E14 | O workflow publica na ordem commit → release/tag → verificação | `release.yml` no commit da tag | `:371` commit carimbado (`release_sha` em `:420`) → `:422` Release com `--target "$TARGET_SHA"` (`:440`) → `:453` verificação remota | alta |
| E15 | Todos os passos do job `publish-release` da run 44773587054 terminaram em sucesso, incluindo o passo de testes | HTML público do job (`/actions/runs/44773587054/job/136417400661`), marcadores `complete`/`success` por passo | `Test and build signed release APK`, `Rewrite release URLs…`, `Commit stamped manifests…`, `Create or update GitHub Release`, `Verify published remote release assets` = success | alta |
| E16 | Nada foi republicado depois da criação | `curl -sIL` dos 3 assets + HTML da release | `x-ms-creation-time` `2026-11-20T16:33:08Z` nos três; release publicada `16:33:09Z`; hashes idênticos em duas coletas (16:30 e ~16:55 UTC) | alta |

## 3. Tabela de inconsistências

| ID | Severidade | Área | Evidência | Impacto | Correção exigida | Bloqueia release? |
|---|---|---|---|---|---|---|
| I1 | INFORMATIVA | Documentação | `Prompt_Auditor_MK21_Streaming_v2.9.4.md` e `RELATORIO_AUDITORIA_MK21_v2.9.4.md` citam 2.9.4/294 | auditor reexecuta o prompt com versão antiga | gerar prompt/relatório 2.9.5 quando for auditar a próxima | não |
| I2 | INFORMATIVA | Assinatura | keystore do CI = `CN=FBGs Streaming` RSA 3072 (`8ca07de4…`), **não** a keystore local `my-upload-key.jks` (RSA 4096, `CN=Fabio Guarniere`) | nenhum: mantém continuidade com 2.9.3/2.9.4 e OTA | decisão explícita se houver rotação de chave | não |
| I3 | BAIXA | Repositório | IPKs antigos versionados em `smart-tv/` (2.9.1 → 2.9.5) | crescimento do repositório | arquivar/limpar versões antigas | não |
| I4 | BAIXA | Testes | `app/src/androidTest/.../ExampleInstrumentedTest.kt` (1 teste) não roda no CI | cobertura instrumentada nula | job com emulador, se desejado | não |
| I5 | BAIXA | OTA/Telemetria | pendências herdadas do relatório 2.9.4 (magic bytes no OTA, contrato de telemetria, medição de performance) | robustez do updater não comprovada em dispositivo real | tratar no próximo ciclo | não |

Nenhuma inconsistência de rastreabilidade: os quatro elos (tag → commit → manifesto → assets) coincidem.

## 4. Tabela de versões e hashes

| Origem | Versão | Code | APK tamanho | APK SHA-256 | IPK tamanho | IPK SHA-256 | Status |
|---|---:|---:|---:|---|---:|---|---|
| Árvore do commit da tag (`ff3325e`) | 2.9.5 | 295 | 4.285.079 | `d9c7c428…a0131873` | 527.986 | `3f78e3d0…53de522` | OK |
| `version.json` da tag (manifesto do OTA) | 2.9.5 | 295 | 4.285.079 | `d9c7c428…a0131873` | 527.986 | `3f78e3d0…53de522` | OK |
| Assets publicados (download) | 2.9.5 | 295 | 4.285.079 | `d9c7c428…a0131873` | 527.986 | `3f78e3d0…53de522` | OK |
| `SHA256SUMS` publicado | — | — | 4.285.079 | `d9c7c428…a0131873` | 527.986 | `3f78e3d0…53de522` | OK |
| (contraste) v2.9.4 na tag `c3dc8fb` | 2.9.4 | 294 | 4.268.696 | `89fecb19…` | 528.002 | `6253c8db…` | divergia do asset |

## 5. Testes

```text
- testes locais (auditoria, sobre a árvore da tag):
  node --test smart-tv/tests/*.test.js -> 95 testes, 94 pass, 1 skip, 0 fail
  npm test (jest, src/players)        -> 3 suítes, 28 testes, 0 fail
  npm run typecheck (tsc --noEmit)    -> limpo
- testes executados pelo CI no Release da 2.9.5:
  ./gradlew testDebugUnitTest --no-daemon  (release.yml:254, dentro do passo
  "Test and build signed release APK", antes de assembleRelease em :255)
  70 métodos @Test em app/src/test (LicenseTokenVerifierTest, LocalServerSecurityTest,
  Mk21UpdateSecurityTest, ObservabilityLoggerTest e demais) => rodaram nesse job;
  o job seguiu para build/assinatura/carimbo/publicação, logo o passo terminou verde.
- testes fora do CI:
  app/src/androidTest/.../ExampleInstrumentedTest.kt (1 @Test instrumentado, 71ª anotação
  @Test do repositório) - exige emulador/dispositivo.
- testes ignorados: 1 skip no runner Node (smart-tv), nenhum @Ignore/@Disabled encontrado.
- testes físicos: não executados (sem dispositivo Android/TV neste ambiente).
- limitações de ambiente: a API REST do GitHub estourou a cota anônima (60 req/h) durante a
  auditoria, então o status dos passos foi lido do HTML público do job
  (/actions/runs/44773587054/job/136417400661), não da API. A contagem exata de asserções
  por classe de teste só aparece no log da run, que exige autenticação: o que está provado é
  que o passo que executa `testDebugUnitTest` terminou em success antes do build/assinatura/
  carimbo/publicação.
```

## 6. Cobertura de observabilidade

Não reavaliada nesta auditoria (recorte de rastreabilidade). Mantém-se o quadro do relatório 2.9.4; nenhum arquivo de observabilidade mudou entre 2.9.4 e 2.9.5 (o bump só alterou defaults de versão/build).

## 7. Auditoria de segurança OTA

- Pin de certificado existe e é obrigatório no código: `BuildConfig.EXPECTED_CERT_SHA256` (`app/build.gradle.kts:151-157`, vindo do pin derivado da keystore no CI); sem pin, `AppViewModel.kt` e `Mk21UpdateManager.kt` recusam instalação automática.
- Certificado do APK publicado (`8ca07de4…`) é o mesmo usado em 2.9.3 e 2.9.4, portanto a cadeia de atualização continua válida.
- Não validado aqui: instalação real de OTA em dispositivo, magic bytes de pacote e rollback — depende de dispositivo.

## 8. Performance e memória

Não medida nesta auditoria. Nenhuma alegação de ganho de desempenho é feita neste relatório.

## 9. Decisão

```text
PRONTO PARA PUBLICAR
```

Interpretação: release publicada e com rastreabilidade completa (tag = commit carimbado = manifesto = assets). Não se aplica a ressalva de rastreabilidade da 2.9.4, porque não há divergência entre tag, `main` e assets.

## 10. Próximos passos

1. `.github/workflows/release.yml` — manter a ordem atual e adicionar um teste de regressão que compare `git rev-parse vX.Y.Z` com o commit gravado em `version.json`/assets (evita regressão silenciosa).
2. `app/src/androidTest` — decidir entre job com emulador ou remoção do teste instrumentado órfão.
3. `Prompt_Auditor_MK21_Streaming_v2.9.4.md` — atualizar o prompt para 2.9.5 antes da próxima auditoria de produto.
4. `smart-tv/` — política de retenção para IPKs antigos.
5. `Mk21UpdateManager.kt` — pendências de magic bytes/rollback herdadas do relatório 2.9.4.
