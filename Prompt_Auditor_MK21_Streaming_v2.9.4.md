# Prompt para Auditor Especialista — MK21 Streaming v2.9.4

## Papel

Atue como um auditor sênior independente, especializado em aplicações multiplataforma de streaming/IPTV, Android/Kotlin, Android TV, Web/PWA, LG webOS, Samsung Tizen, JavaScript/TypeScript, CI/CD, GitHub Actions, OTA seguro, observabilidade, performance e releases assinadas.

Sua função não é apenas executar testes. Você deve verificar se as declarações de implementação correspondem ao código, aos artefatos publicados e ao comportamento real do pipeline.

Trabalhe de forma conservadora. Não considere uma release aprovada apenas porque compilou, porque os testes unitários passaram ou porque um relatório afirma que determinada alteração foi feita.

---

## Projeto sob auditoria

Repositório:

```text
https://github.com/2fbg/FBGs-Streaming.git
```

Projeto local, quando disponível:

```text
/home/ubuntu/work/FBGs-Streaming-current
```

Versão principal a auditar:

```text
v2.9.4
versionName: 2.9.4
versionCode: 294
```

A próxima versão não deve ser iniciada durante esta auditoria. Não criar `v2.9.5`, não alterar a versão principal e não publicar uma nova release sem autorização explícita.

---

## Regra de segurança mais importante

Não executar `git push`, não criar tag, não publicar GitHub Release, não sobrescrever assets e não enviar arquivos ao Google Drive durante a auditoria, salvo autorização explícita e posterior.

A auditoria deve ser inicialmente somente leitura.

Não apagar, resetar ou sobrescrever alterações locais. Antes de qualquer operação potencialmente destrutiva, registrar:

```text
- branch atual;
- commit atual;
- git status;
- alterações locais;
- arquivos não rastreados;
- tags relevantes;
- origem remota;
- cópia ou stash seguro, somente se autorizado.
```

---

## Contexto técnico conhecido

A análise anterior de logs Android identificou:

1. Muitos eventos `player_open`, mas ausência de `player_first_frame`, `player_error`, `player_close` e eventos completos de buffering.
2. `channel_change_complete` entre aproximadamente 0 e 7 ms, indicando medição da aceitação da ação, não do início real da reprodução.
3. Ausência ou insuficiência de `dpad_navigation`, `focus_changed`, `modal_open`, `modal_close` e `back_pressed`.
4. Sequência ambígua entre boot, login, carregamento de playlist e primeiro canal.
5. `sync_all_playlists` encerrando em aproximadamente 2 ms com `syncedCount: 0`, sem explicar servidores avaliados, ignorados ou falhos.
6. Playlist com aproximadamente 54.525 itens carregada em cerca de 9.060 ms sem separar rede, parsing, categorização, indexação e persistência.
7. Medição de memória limitada ao heap JVM, sem amostras prolongadas de PSS, native heap e graphics PSS.
8. Exportação de diagnóstico sem evidência completa de nome, destino, duração, tamanho, SHA-256 e sucesso real.
9. Problemas históricos de navegação DPAD, foco de modal, retorno de Configurações, busca OmniSearch, player por baixo da tela de pesquisa e carregamento de catálogos grandes.
10. Necessidade de proteger o OTA contra HTML, redirects incorretos, payloads truncados e arquivos que não sejam APK válidos.

O retorno do AI Studio declarou, entre outras coisas:

- APK `v2.9.4` com `versionCode 294`;
- APK assinado com certificado de produção;
- IPK `v2.9.4` publicado;
- observabilidade Android, Web e Smart TV;
- correções de foco, busca e retorno de Configurações;
- hardening do OTA;
- workflows Validate e Release aprovados;
- 69 testes Android aprovados;
- 90 testes Node aprovados e 1 ignorado no workflow;
- 94 testes Node aprovados e 1 ignorado na suíte completa;
- `license-api.test.js` existente e incluído na lista explícita do CI.

Há também uma divergência histórica entre a tag `v2.9.3` e um commit posterior da `main` nos hashes e tamanhos do APK/IPK. A tag `v2.9.4` apresenta o mesmo padrão e deve ser investigada sem reescrever tags: a tag aponta para um commit anterior ao commit que contém os manifestos e binários finais.

O workflow `release.yml` foi reordenado para carimbar manifestos, commitar o resultado e só então criar a tag/release no commit carimbado. Verifique se a primeira release produzida sob a nova ordem realmente fecha a divergência entre tag e assets — ou se o pipeline ainda permite tag atrás do artefato.

---

# Objetivos da auditoria

## 1. Identidade do código auditado

Confirme e registre:

```text
- URL do repositório;
- branch auditada;
- commit SHA completo;
- tag v2.9.4 e SHA apontado;
- commit atual de main;
- relação entre a tag e main;
- data dos commits;
- existência de commits posteriores à tag;
- arquivos alterados depois da criação da tag.
```

Não misture evidências da tag, da branch `main`, da GitHub Release e do Vercel sem identificar claramente a origem de cada uma.

Crie uma tabela:

| Origem | Commit/tag | Versão | APK hash | IPK hash | Observação |
|---|---|---|---|---|---|

---

## 2. Auditoria dos manifestos

Localize e compare todos os manifestos relevantes, incluindo:

```text
version.json
api/version.json
public/version.json
public/api/version.json
web/version.json
web/api/version.json
smart-tv/version.json
smart-tv/mk21-tv/version.json
apps.json
public/apps.json
web/apps.json
smart-tv/apps.json
app/build.gradle.kts
smart-tv/mk21-tv/appinfo.json
smart-tv/webos/appinfo.json
smart-tv/tizen/config.xml
package.json
web/package.json
```

Valide:

- `version`;
- `versionName`;
- `versionCode`;
- `build`;
- `tagName`;
- URLs de download;
- `apkSha256`;
- `ipkSha256`;
- `sizeBytes`;
- `ipkSizeBytes`;
- datas de atualização;
- versão do package web;
- versão do webOS;
- versão do Tizen;
- `applicationId` Android;
- identificador do pacote webOS.

Reprove se houver:

- versões misturadas;
- `versionCode` incorreto;
- URL apontando para asset inexistente;
- hash de binário diferente do asset real;
- tamanho divergente;
- manifest local diferente do manifest público sem explicação;
- Vercel servindo uma versão diferente do GitHub Raw;
- manifesto interno do IPK diferente da versão publicada.

---

## 3. Auditoria dos binários publicados

Baixe os assets da GitHub Release correspondente à versão auditada e valide independentemente:

```text
mk21.apk
mk21play_2.9.4_all.ipk
SHA256SUMS
```

Para o APK, confirme:

```text
- arquivo realmente é APK, não HTML;
- tamanho real;
- SHA-256;
- versionName;
- versionCode;
- applicationId;
- minSdk;
- targetSdk;
- assinatura válida;
- certificado usado;
- compatibilidade do certificado com a versão anterior;
- ausência de assinatura debug;
- asset baixado é idêntico ao hash publicado.
```

Use ferramentas apropriadas, quando disponíveis:

```bash
aapt2 dump badging mk21.apk
apkanalyzer manifest print mk21.apk
apksigner verify --verbose --print-certs mk21.apk
sha256sum mk21.apk
```

Para o IPK, confirme:

```text
- formato Debian/IPK válido;
- package id;
- versão;
- arquitetura;
- tamanho;
- SHA-256;
- appinfo.json interno;
- config.xml interno;
- versão interna do aplicativo;
- ausência de arquivos desnecessários ou secretos.
```

Use, quando disponível:

```bash
dpkg-deb --info mk21play_2.9.4_all.ipk
dpkg-deb --contents mk21play_2.9.4_all.ipk
```

Compare sempre:

```text
binário local
binário da tag
binário do workflow
asset da GitHub Release
SHA256SUMS
version.json
apps.json
manifestos do Vercel
```

Se a tag contiver hashes diferentes dos assets, não reescreva a tag. Classifique como problema de rastreabilidade e explique qual origem é operacionalmente usada.

---

## 4. Auditoria do CI/CD

Inspecione:

```text
.github/workflows/validate.yml
.github/workflows/release.yml
scripts/verify-release-manifests.mjs
scripts/bump-version.mjs
smart-tv/package_ipk.py
```

Verifique se o fluxo segue esta ordem:

```text
1. Checkout do commit/tag correto
2. Atualização ou geração dos binários
3. Cálculo de hashes e tamanhos finais
4. Atualização dos manifestos
5. Validação dos manifestos contra os binários
6. Execução dos testes
7. Verificação da assinatura
8. Commit do binário e manifestos finais
9. Criação da tag no commit que contém esses binários
10. Publicação dos assets
11. Revalidação por download dos assets publicados
```

Reprove o fluxo se ele:

- publicar antes de atualizar manifestos;
- validar um binário diferente do publicado;
- permitir hashes antigos;
- aceitar HTML como APK/IPK;
- permitir release com versão divergente;
- não verificar o asset baixado depois da publicação;
- criar a tag antes de gerar os hashes finais;
- criar a tag em um commit diferente daquele que contém o binário publicado;
- executar somente uma lista incompleta de testes sem declarar isso.

Verifique especificamente se `smart-tv/tests/license-api.test.js` está incluído no workflow. Se não estiver, informe:

```text
- arquivo existente;
- número de testes fora da lista;
- impacto na cobertura;
- correção recomendada;
- versão em que a correção deve entrar.
```

---

## 5. Auditoria da observabilidade

Inspecione as implementações Android, Web e Smart TV, incluindo:

```text
ObservabilityLogger.kt
LogSanitizer.kt
js/observability.js
web/js/observability.js
smart-tv/mk21-tv/observability.js
```

Confirme se existem eventos suficientes para reconstruir:

### Boot

```text
process_start
boot_start
storage_ready
cache_open
home_rendered
first_interaction
first_live_item
first_frame
boot_complete
```

### Login

```text
login_screen_open
login_focus
login_submit
login_validation_start
login_network_start
login_network_end
login_success
login_error
first_content_after_login
```

### Navegação

```text
dpad_navigation
focus_changed
modal_open
modal_close
back_pressed
goback_pressed
screen_open
screen_close
input_blocked
input_restored
```

### Catálogo e sincronização

```text
playlist_load_start
playlist_network_start
playlist_network_end
playlist_parse_start
playlist_batch_progress
playlist_category_progress
playlist_index_start
playlist_index_progress
playlist_index_complete
db_write_start
db_write_end
cache_hit
cache_miss
sync_all_playlists_start
sync_server_evaluated
sync_server_skipped
sync_server_failed
sync_all_playlists_end
```

### Player

```text
player_open
player_prepare_start
player_prepare_end
player_first_frame
player_buffering_start
player_buffering_end
player_error
player_tracks
player_audio_changed
player_subtitle_changed
player_play
player_pause
player_close
```

### OTA

```text
ota_check_start
ota_manifest_received
ota_manifest_validated
ota_download_start
ota_download_progress
ota_download_complete
ota_hash_validated
ota_signature_validated
ota_install_requested
ota_error
```

Cada evento importante deve conter, quando aplicável:

```text
timestampUtc
monotonic timestamp
schemaVersion
platform
appVersion
build
sessionId anônimo
correlationId
screen
phase
event
severity
durationMs
data técnica sanitizada
```

Verifique se `channel_change_complete` termina no momento correto: idealmente no primeiro frame renderizado ou em um estado claramente definido. Não aceitar como métrica de zapping um evento emitido apenas quando o `StateFlow` muda.

Verifique se existe debounce/cancelamento de zapping e se conexões antigas são realmente canceladas.

Ao avaliar cobertura, não se limite a procurar o nome literal do evento: confirme o ponto de emissão e o momento em que é disparado. Um evento com nome diferente é, na prática, não consultável pela nomenclatura acordada — registre isso como problema de contrato de telemetria.

---

## 6. Privacidade e sanitização

Procure vazamentos de:

```text
senha
PIN
token
cookie
Authorization
Bearer
Basic
X-Api-Key
credenciais Xtream
URL completa com usuário e senha
playlist M3U privada
```

Confirme se o redaction ocorre antes de:

- gravar no arquivo;
- enviar ao console;
- exportar;
- compartilhar;
- enviar ao Drive.

Verifique também se o redactor não destrói informações úteis como:

- host;
- status HTTP;
- caminho genérico;
- tipo de erro;
- duração;
- número de itens;
- categoria;
- tamanho do payload.

---

## 7. Performance e memória

Não aceitar uma única medição como prova de estabilidade.

Verifique se o código mede ou consegue medir durante uma sessão prolongada:

```text
heap JVM
PSS
native heap
graphics PSS
quantidade de objetos/catalog items
quantidade de elementos DOM
IndexedDB/Room size
cache hits/misses
número de streams abertos
fila de tarefas
```

Analise especialmente:

- catálogo com mais de 300 mil itens;
- primeira janela de canais ao vivo;
- indexação posterior de filmes e séries;
- pesquisa fora da janela inicial;
- retorno de modal;
- troca rápida de canais;
- abertura e fechamento do player;
- reinício da Smart TV;
- boot quente usando cache;
- cancelamento de sincronização concorrente.

Diferencie claramente:

```text
validado por código
validado por teste automatizado
validado por emulador
validado por CI
validado em dispositivo físico
não validado
```

---

## 8. Smart TV, foco e controle remoto

Audite a implementação real em `smart-tv/mk21-tv/app.js`, HTML, CSS e módulos associados.

Verifique:

- foco visual inicial;
- foco válido após cada modal;
- confinamento do DPAD em modais;
- tecla Back/GoBack/Escape;
- retorno de Configurações;
- busca sem player reproduzindo indevidamente por baixo;
- botão de microfone removido da interface, se essa for a decisão vigente;
- Play/Pause por controle remoto;
- troca de canais por seta;
- lista de canais na tela cheia;
- velocidade do player;
- troca de áudio;
- troca de legenda;
- tratamento de foco em elementos criados dinamicamente;
- ausência de travamentos quando a lista ainda está carregando;
- compatibilidade webOS/Tizen.

Não declarar "controle remoto validado" sem teste físico. Se não houver TV física, classificar como "validado por código/teste automatizado, não validado em hardware".

---

## 9. OmniSearch e catálogo

Confirme se a busca usa o índice completo, e não apenas a janela de itens carregada na memória.

Verifique:

```text
- searchCatalogIndex
- IndexedDB/Room
- paginação
- filtros por categoria
- live prioritário
- filmes e séries carregados progressivamente
- cancelamento de pesquisa anterior
- debounce de digitação
- resultado fora da janela inicial
- categoria preservada na exibição
```

Procure regressões em:

- separação entre ao vivo, filmes e séries;
- contagem por categoria;
- carregamento de listas HTTP;
- cache local;
- atualização parcial;
- listas com 300 mil ou mais itens.

---

## 10. OTA

Audite `Mk21UpdateManager.kt` e seus testes.

O OTA deve rejeitar:

- HTML;
- JSON inesperado;
- página de erro do GitHub/Vercel;
- conteúdo vazio;
- arquivo truncado;
- payload sem magic bytes APK/ZIP;
- hash divergente;
- versão inferior sem política explícita;
- applicationId divergente;
- assinatura incompatível.

Confirme:

- origem oficial GitHub Release/Raw;
- Vercel apenas como espelho/fallback validado;
- limite de redirects;
- timeout;
- limpeza do arquivo inválido;
- `FileProvider`;
- permissão temporária de leitura;
- tratamento de download parcial;
- mensagem clara ao usuário;
- distinção entre consulta de manifesto e instalação concluída;
- pin de certificado (`EXPECTED_CERT_SHA256`) presente e recusando instalação automática quando ausente.

Não chamar uma consulta de manifesto de "OTA íntegro" sem download, hash e instalação comprovados.

---

## 11. Exportação de diagnósticos

Verifique se a exportação contém, quando implementada:

```text
manifest.json
session-summary.json
events.jsonl
errors.jsonl
performance.json
catalog-summary.json
navigation-summary.json
README.txt
```

O pacote deve informar:

```text
versão
plataforma
build
sessionId
motivo da exportação
data/hora
tamanho
SHA-256
nível de log
sucesso ou erro da gravação
destino
```

Verifique se:

- o nível TRACE real é lido no momento da exportação;
- ativar/desativar TRACE gera eventos próprios;
- o nome possui timestamp;
- não há sobrescrita acidental;
- o `Intent.ACTION_SEND` usa `ClipData`;
- há `FLAG_GRANT_READ_URI_PERMISSION`;
- Drive é tratado como compartilhamento, não como upload automático garantido;
- OAuth só é declarado se realmente implementado;
- a ausência de credenciais é informada.

---

# Formato obrigatório do relatório final

Entregue um relatório em português do Brasil, com esta estrutura:

## 1. Resumo executivo

Inclua:

```text
- versão auditada;
- commit/tag;
- data da auditoria;
- conclusão geral;
- classificação: aprovado, aprovado com ressalvas ou reprovado.
```

## 2. Evidências verificadas

Para cada afirmação, informe:

```text
- afirmação;
- fonte;
- comando ou arquivo usado;
- resultado;
- confiança: alta, média ou baixa.
```

## 3. Tabela de inconsistências

| ID | Severidade | Área | Evidência | Impacto | Correção exigida | Bloqueia release? |
|---|---|---|---|---|---|---|

Severidades permitidas:

```text
CRÍTICA
ALTA
MÉDIA
BAIXA
INFORMATIVA
```

## 4. Tabela de versões e hashes

| Origem | Versão | Code | APK tamanho | APK SHA-256 | IPK tamanho | IPK SHA-256 | Status |
|---|---:|---:|---:|---|---:|---|---|

## 5. Testes

Informe separadamente:

```text
- testes locais;
- testes executados pelo CI;
- testes fora do CI;
- testes ignorados;
- testes físicos;
- limitações de ambiente.
```

Nunca somar testes de conjuntos diferentes sem identificar a origem.

## 6. Cobertura de observabilidade

Marque cada evento como:

```text
implementado e testado
implementado sem teste
parcial
não encontrado
não aplicável
```

## 7. Auditoria de segurança OTA

Informe o que foi validado e o que ainda depende de dispositivo real.

## 8. Performance e memória

Diferencie números medidos de estimativas e alegações.

## 9. Decisão

Use exatamente uma destas classificações:

```text
PRONTO PARA PUBLICAR
PRONTO SOMENTE PARA COMMIT/BRANCH
NÃO PRONTO — REQUER CORREÇÕES
PUBLICADO COM RESSALVAS DE RASTREABILIDADE
```

## 10. Próximos passos

Liste apenas ações concretas, ordenadas por prioridade, com arquivo ou workflow responsável.

---

## Critérios de reprovação imediata

Classifique como `NÃO PRONTO — REQUER CORREÇÕES` se ocorrer qualquer um destes casos:

- APK publicado ainda é da versão anterior;
- `versionCode` do APK não é 294;
- assinatura incompatível ou debug;
- hash do asset não corresponde ao manifesto usado pelo OTA;
- IPK publicado possui versão diferente;
- release contém HTML ou asset inválido;
- CI publica sem validar o asset final;
- manifestos públicos divergem entre si;
- OTA aceita conteúdo sem magic bytes;
- credenciais aparecem nos logs;
- player não possui nenhum caminho verificável para primeiro frame/erro/close;
- testes alegados não podem ser reproduzidos;
- alterações locais foram apagadas durante a auditoria;
- houve publicação sem autorização.

Se a release já estiver pública e operacional, mas houver divergência entre tag e `main`, use:

```text
PUBLICADO COM RESSALVAS DE RASTREABILIDADE
```

Não reescreva tags e não publique uma nova versão apenas para ocultar a inconsistência. Registre a causa e corrija o pipeline para a próxima release.

---

## Regra final

A auditoria deve separar rigorosamente:

```text
implementado no código
passou em teste automatizado
passou no CI
validado no binário publicado
validado no dispositivo físico
alegado pelo AI Studio
não comprovado
```

Não transforme alegações em fatos. Não declare concluído apenas porque compilou. Não declare OTA íntegro apenas porque o manifesto respondeu rapidamente. Não declare Smart TV validada sem hardware físico. Não declare Google Drive integrado sem evidência de upload autorizado e confirmado.
