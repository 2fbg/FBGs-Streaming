# Prompt de Continuação — MK21 Streaming no AI Studio

## Papel da IA

Atue como engenheiro sênior de release multiplataforma, Android/Kotlin, Web/PWA, webOS, Tizen, IPTV, performance, observabilidade, segurança de OTA e navegação por controle remoto.

Trabalhe diretamente no repositório:

```text
https://github.com/2fbg/FBGs-Streaming.git
```

O ambiente atual do AI Studio possui Android SDK, Gradle, Node.js, Python, GitHub e credenciais/conectividade necessárias. Use o AI Studio como ambiente principal de compilação e publicação.

Não faça alterações destrutivas, não apague dados do usuário, não revele secrets e não publique binários cujo hash não esteja refletido nos manifestos.

---

## Estado conhecido do projeto

A versão pública auditada é `v2.9.2`, com:

```text
Commit remoto: 1685e90
Version name: 2.9.2
Android versionCode: 292
Repositório: 2fbg/FBGs-Streaming
Branch principal: main
```

A validação Web executada no ambiente anterior apresentou:

```text
25 arquivos Web sincronizados entre raiz, web/ e public/
Manifestos v2.9.2 validados
28 testes JavaScript aprovados
Sintaxe Smart TV aprovada
IPK v2.9.2 estruturalmente válido
```

O build Android não foi concluído no ambiente anterior porque o Android SDK não estava configurado. No AI Studio, configure e valide o SDK antes de iniciar o build:

```bash
echo "$ANDROID_HOME"
./gradlew --version
sdkmanager --list | head -80
```

Não assumir que o APK está correto apenas porque existe um arquivo `mk21.apk`. Ele deve ser recompilado, inspecionado e comparado pelo SHA-256.

---

## Alterações pendentes que ainda não foram publicadas

As alterações abaixo foram preparadas localmente, mas ainda não foram commitadas no GitHub:

1. Correção de `web/package.json`, que ainda estava em `2.9.0`; deve ficar coerente com a release.
2. Logger estruturado de observabilidade para Web.
3. Logger equivalente carregado na Smart TV.
4. Inclusão do logger nas cópias Web raiz, `public/` e `web/`.
5. Prompt e documentação de continuidade.

Como essas alterações podem não existir no clone do AI Studio, recrie-as ou aplique uma implementação equivalente seguindo os requisitos abaixo. Antes de trabalhar, compare o estado real do GitHub e não presuma que qualquer arquivo local anterior esteja disponível.

---

## Objetivo desta continuação

Preparar e publicar uma nova release somente depois de:

- corrigir e validar observabilidade;
- confirmar que Web, Android, webOS e Tizen usam a mesma versão;
- regenerar APK e IPK no AI Studio;
- validar assinatura, `applicationId`, `versionCode`, hashes e tamanhos;
- validar CI e artefatos públicos;
- evitar que Vercel, GitHub Release, Raw GitHub e manifests apontem para binários diferentes.

Como o IPK reconstruído com observabilidade terá hash diferente do IPK publicado em `v2.9.2`, não reutilizar silenciosamente a mesma release. Preferir uma nova versão semântica, por exemplo `2.9.3`, caso as alterações sejam mantidas.

Não atualizar a versão para `2.9.3` antes de confirmar que o APK também poderá ser recompilado e publicado. A versão deve ser única em todo o conjunto.

---

# Fase 1 — Auditoria inicial obrigatória

Clone ou atualize o repositório:

```bash
git clone https://github.com/2fbg/FBGs-Streaming.git
cd FBGs-Streaming
git fetch --all --tags
git checkout main
git pull --ff-only origin main
git status --short
git log -5 --oneline --decorate
```

Confirme as versões efetivas:

```bash
cat version.json
sed -n '115,135p' app/build.gradle.kts
cat package.json
cat web/package.json
find . -name version.json -not -path './node_modules/*' -print
```

Localize referências residuais:

```bash
rg -n '3\.6\.0|3\.8\.[0-9]+|2\.9\.0|1\.0\.0' \
  --glob '!*.apk' \
  --glob '!*.ipk' \
  --glob '!*.map' \
  --glob '!package-lock.json' \
  .
```

Não tratar versões de dependências em `package-lock.json` como versão do aplicativo.

Verifique os artefatos:

```bash
find . -type f \( -name '*.apk' -o -name '*.ipk' \) -printf '%p %s bytes\n' | sort
sha256sum mk21.apk smart-tv/*.ipk 2>/dev/null || true
```

---

# Fase 2 — Observabilidade estruturada

## Requisitos gerais

Criar ou reutilizar um logger centralizado por plataforma. Ele deve:

- registrar JSONL estruturado;
- possuir `schemaVersion`;
- registrar timestamp UTC e relógio monotônico;
- registrar `sessionId` anônimo;
- registrar plataforma, versão, build e tela;
- registrar evento, fase, severidade, duração e correlação;
- não interromper o aplicativo se o logger falhar;
- usar buffer circular com limite de tamanho;
- persistir em lotes;
- evitar crescimento ilimitado de RAM ou DOM;
- possuir níveis `OFF`, `NORMAL`, `DIAGNOSTICO` e `TRACE`;
- expirar `TRACE` após 30–60 minutos;
- permitir exportação local.

Nunca registrar:

- senha;
- PIN;
- token;
- cookie;
- cabeçalho `Authorization`;
- URL com credenciais;
- playlist privada completa;
- dados pessoais desnecessários.

Aplicar redaction antes da persistência e antes da exportação.

## Eventos mínimos Web e Smart TV

Implementar ou confirmar estes eventos:

```text
process_start
dom_content_loaded
window_loaded
first_interaction_ready
login_screen_open
login_focus
login_submit_click
login_request_start
login_request_end
login_success
login_error
screen_enter
screen_leave
ui_click
dpad_navigation
modal_open
modal_close
focus_changed
back_pressed
network_request
network_error
cache_hit
cache_miss
playlist_load_start
playlist_batch_committed
playlist_load_progress
playlist_load_complete
playlist_load_error
indexeddb_open
indexeddb_error
player_open
player_first_frame
player_buffering_start
player_buffering_end
channel_change_start
channel_change_complete
player_error
background_task_start
background_task_end
runtime_error
unhandled_rejection
diagnostics_exported
diagnostics_cleared
```

Para cada evento de rede, registrar apenas host, método, status, duração, tamanho quando seguro e correlação. Não registrar URLs com usuário/senha/token.

## Eventos mínimos Android

Criar ou integrar um logger Android que registre:

- abertura da tela de login;
- início/fim de login;
- tempo até primeiro conteúdo;
- navegação entre telas;
- clique/seleção de controles;
- atualização OTA;
- manifest recebido;
- download iniciado/finalizado;
- bytes recebidos;
- SHA-256 calculado;
- validação de tamanho;
- validação de assinatura;
- erro do instalador;
- Room/cache;
- player, buffering e troca de canal;
- tarefas em segundo plano.

Persistir em arquivo interno ou banco local com rotação. Expor ações:

```text
Exportar logs
Compartilhar logs
Limpar logs
Ativar diagnóstico temporário
```

---

# Fase 3 — Correções funcionais de Smart TV

Revisar `smart-tv/mk21-tv/app.js`, `index.html` e `styles.css`.

Garantir:

1. Ao abrir modal, o DPAD permanece dentro dele.
2. A tecla Voltar fecha o modal mais interno primeiro.
3. Depois de fechar Configurações, o foco retorna à tela e ao elemento anterior.
4. Pesquisa e teclado nativo não deixam o player rodando indevidamente por baixo.
5. O áudio do canal anterior é parado quando necessário.
6. A tela cheia possui Play/Pause, canal anterior, próximo canal, lista, saída, velocidade, áudio e legenda.
7. Setas, Enter, Escape, Back e GoBack funcionam conforme webOS/Tizen.
8. Elementos ocultos ou removidos nunca recebem foco.
9. A busca alcança itens fora da janela inicial do catálogo.
10. Canais ao vivo podem ter prioridade sem limitar o catálogo completo.

Executar no mínimo:

```bash
node --check smart-tv/mk21-tv/app.js
python3 -m py_compile smart-tv/package_ipk.py
```

---

# Fase 4 — Catálogo e performance

Confirmar que o catálogo não é carregado integralmente na RAM nem no DOM.

Requisitos:

- parser M3U streaming/incremental;
- prioridade de canais ao vivo;
- janela limitada de cards renderizados;
- IndexedDB completo para Web/Smart TV;
- Room ou cache equivalente no Android;
- indexação por lotes;
- busca no índice completo;
- paginação por categoria;
- cancelamento e retomada;
- retry com backoff;
- prevenção de downloads duplicados;
- progresso somente quando houver total confiável;
- suporte a listas com mais de 300 mil itens;
- métricas de tempo por lote e itens indexados;
- boot quente usando cache válido;
- sincronização em segundo plano sem bloquear a navegação.

Medir separadamente:

```text
tempo até shell visível
tempo até foco inicial
tempo até primeira lista
tempo até primeiro canal
tempo até primeiro frame
tempo até índice completo
uso de memória aproximado
quantidade de elementos no DOM
cache hit/miss
quantidade de itens por categoria
```

Não declarar que a memória foi resolvida sem teste em dispositivo físico.

---

# Fase 5 — Versionamento e manifests

Se a nova release for `2.9.3`, atualizar de modo uniforme:

```text
version.json
api/version.json
public/version.json
public/api/version.json
web/version.json
web/api/version.json
smart-tv/version.json
smart-tv/mk21-tv/version.json
smart-tv/webos/appinfo.json
smart-tv/mk21-tv/appinfo.json
smart-tv/tizen/config.xml
manifest.json
web/manifest.json
public/manifest.json
package.json
web/package.json
apps.json
web/apps.json
public/apps.json
smart-tv/apps.json
app/build.gradle.kts
UI de versão e notas de release
```

Atualizar `versionCode` Android para um valor maior que `292`, preservando:

- `applicationId`;
- chave de assinatura;
- compatibilidade de atualização.

Se a chave de assinatura do novo APK for diferente, não prometer atualização sobre a instalação existente. Comparar certificados:

```bash
apksigner verify --print-certs mk21.apk
jarsigner -verify -verbose -certs mk21.apk
```

---

# Fase 6 — OTA Android seguro

Usar GitHub Release como origem oficial do APK. Vercel deve ser apenas espelho/fallback validado.

O manifesto deve conter:

```json
{
  "version": "2.9.3",
  "versionCode": 293,
  "downloadUrl": "https://github.com/2fbg/FBGs-Streaming/releases/download/v2.9.3/mk21.apk",
  "apkSha256": "sha256-do-apk",
  "sizeBytes": 1234567
}
```

O cliente deve:

1. consultar o manifesto oficial;
2. comparar versão e `versionCode`;
3. baixar com timeout e limite;
4. validar status HTTP;
5. validar que a resposta é APK, não HTML;
6. validar tamanho;
7. calcular SHA-256;
8. rejeitar hash divergente;
9. apagar download inválido;
10. usar `FileProvider`;
11. abrir o instalador somente depois das validações.

Testar também o fallback e respostas incompletas.

---

# Fase 7 — Build e testes no AI Studio

## Web

```bash
npm ci
npm test -- --runInBand
npm run check:web
node scripts/verify-release-manifests.mjs
```

## Android

```bash
chmod +x ./gradlew
./gradlew testDebugUnitTest assembleDebug --no-daemon
cp app/build/outputs/apk/debug/app-debug.apk mk21.apk
```

Se o projeto possuir build assinado de release, preferir o fluxo oficial configurado no CI. Não substituir um APK de produção por um debug APK sem declarar explicitamente.

## Smart TV

```bash
node --check smart-tv/mk21-tv/app.js
python3 -m py_compile smart-tv/package_ipk.py
python3 smart-tv/package_ipk.py smart-tv/mk21-tv smart-tv/mk21play_2.9.3_all.ipk
dpkg-deb --info smart-tv/mk21play_2.9.3_all.ipk
```

## Validação determinística

Usar o validador da habilidade, quando disponível:

```bash
python3 /home/ubuntu/skills/mk21-cross-platform-release/scripts/validate_release.py \
  . \
  --version 2.9.3 \
  --apk mk21.apk \
  --ipk smart-tv/mk21play_2.9.3_all.ipk
```

Confirmar também:

```bash
git diff --check
git status --short
sha256sum mk21.apk smart-tv/mk21play_2.9.3_all.ipk
```

---

# Fase 8 — Publicação segura

Antes do commit:

```bash
git diff --stat
git diff --check
git status --short
```

Não incluir:

- `google-services.json` gerado automaticamente;
- `debug.keystore` gerado localmente;
- logs com dados sensíveis;
- APK/IPK antigo com hash divergente;
- arquivos temporários;
- segredos;
- dependências instaladas em `node_modules`.

Fazer commit somente depois que Web, Android e Smart TV passarem:

```bash
git add <arquivos-intencionalmente-alterados>
git commit -m "feat: add cross-platform observability and release hardening"
git push origin main
```

Criar ou atualizar GitHub Release somente depois do push e dos checks locais:

```bash
gh release create v2.9.3 mk21.apk smart-tv/mk21play_2.9.3_all.ipk \
  --repo 2fbg/FBGs-Streaming \
  --title "MK21 MultiServidor v2.9.3" \
  --notes-file docs/RELEASE_NOTES.md
```

Se a release já existir, não sobrescrever silenciosamente. Confirmar asset, tamanho e hash antes de substituir.

Acompanhar CI:

```bash
gh run list --repo 2fbg/FBGs-Streaming --limit 10
gh run watch <RUN_ID> --repo 2fbg/FBGs-Streaming --exit-status
```

---

# Fase 9 — Verificação pública

Consultar os manifestos públicos do GitHub:

```bash
curl -fsSL https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/version.json
curl -fsSL https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/api/version.json
```

Consultar o manifesto Vercel somente nos caminhos realmente existentes. O endpoint histórico abaixo retornou 404 no ambiente anterior e não deve ser tratado como publicado sem confirmação:

```text
https://bgstreaming.vercel.app/app/applet/api/version.json
```

Testar também, se aplicável:

```text
https://bgstreaming.vercel.app/api/version.json
https://bgstreaming.vercel.app/version.json
```

Baixar os assets públicos e comparar:

```bash
curl -fL -o /tmp/public-mk21.apk <downloadUrl>
sha256sum /tmp/public-mk21.apk
stat -c '%s' /tmp/public-mk21.apk
```

Nunca declarar GitHub e Vercel sincronizados sem comparar:

- versão;
- `versionCode`;
- tamanho;
- SHA-256;
- status HTTP;
- conteúdo real do arquivo.

---

# Fase 10 — Google Drive

A exportação de logs deve ser explícita e autorizada.

Pasta de destino fornecida pelo usuário:

```text
https://drive.google.com/drive/folders/1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB
```

Se o conector Google Workspace estiver disponível:

```bash
gws drive files list --params '{"q":"\'1LqB3dl5ALzgVqUDlYaHzL9XWvv5_ZuGB\' in parents and trashed = false","pageSize":100}' --format json
```

Enviar somente um pacote de diagnóstico redigido, preferencialmente ZIP ou JSONL, contendo:

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

Depois do upload, retornar:

```text
Google Drive file ID
webViewLink
nome do arquivo
versão
plataforma
sessão
SHA-256
tamanho
horário UTC
```

Não apagar arquivos existentes. Se não houver autorização ou conector ativo, gerar o pacote localmente e informar a limitação.

---

# Critérios de aceite

Considerar a tarefa concluída somente quando:

- Web, Android, webOS e Tizen estiverem coerentes na versão escolhida;
- `web/package.json` não possuir versão antiga;
- o logger não registrar credenciais;
- logs reconstruírem login, boot, navegação, rede, cache, player e indexação;
- o logger não bloquear o aplicativo;
- pesquisa, modal e Voltar preservarem o foco correto;
- o player controlar Play/Pause, troca de canais, áudio e legendas;
- o catálogo puder pesquisar fora da janela inicial;
- listas grandes não forem renderizadas integralmente no DOM;
- APK compilar e os testes Android passarem;
- IPK for gerado novamente e inspecionado;
- hashes e tamanhos dos binários coincidirem com todos os manifests;
- assinatura e `applicationId` forem comparados;
- CI passar;
- GitHub Release e manifests públicos estiverem verificáveis;
- Vercel estiver sincronizado ou a divergência estiver explicitamente documentada;
- relatório final separar validação por código, teste automatizado, CI e dispositivo físico.

---

# Formato obrigatório do relatório final

Entregar:

1. Versão e commit auditados.
2. Problemas encontrados.
3. Correções por plataforma.
4. Eventos e métricas de observabilidade adicionados.
5. Testes executados e resultados.
6. APK/IPK gerados com SHA-256 e tamanho.
7. Certificados de assinatura e `applicationId`.
8. GitHub Release, CI e URLs públicas.
9. Upload do diagnóstico para o Drive, se realizado.
10. Limitações, especialmente testes que exigem TV física.
11. Comandos exatos para reproduzir a validação.

Não escrever “finalizado” apenas porque compilou. Indicar claramente qualquer item não testado em dispositivo real.
