# RUNBOOK: PUBLICAÇÃO DAS CORREÇÕES v3.0.1 (MK21 PLAY / SMART TV IPK)
**Repositório:** `https://github.com/2fbg/FBGs-Streaming.git`  
**Branch:** `main`  
**Alvo:** Correção de foco no webOS 5, enxugamento do pacote IPK e pinagem de OTA na tag `v3.0.1`.

---

## 🎯 OBJETIVOS DESTA EXECUÇÃO
1. Aplicar as 4 correções de compatibilidade e usabilidade da Smart TV LG.
2. Reconstruir o pacote `mk21play_3.0.1_all.ipk` via script oficial (reduzindo de ~553 KB para ~362 KB).
3. Efetuar o commit e push na branch `main`.
4. **Mover a tag `v3.0.1`** para o novo commit (essencial para que o OTA das TVs baixe o código novo).
5. Substituir o asset do IPK na GitHub Release oficial preservando o `mk21.apk`.

---

## 📋 ETAPA 1: VERIFICAÇÃO DO ESTADO DE PARTIDA

Execute os comandos abaixo para certificar que o workspace está limpo:

```bash
cd FBGs-Streaming
git fetch origin
git status --short
git log -2 --oneline
```

*Esperado:* Árvore limpa em sincronia com o remoto.

---

## 🛠️ ETAPA 2: APLICAÇÃO DAS 4 CORREÇÕES DE CÓDIGO

### 1. `smart-tv/mk21-tv/styles.css` (Correção de Foco no Chromium 68 / webOS 5)
Remova o seletor `:focus-visible` dos blocos de seletores agrupados por vírgula para evitar o descarte da regra inteira pelo motor legado da TV LG:

* **No seletor de botões de modal (~linha 951):**
```css
/* Correção webOS 5: :focus-visible descartava o bloco inteiro */
.modal-btn:focus,
.modal-btn.focused,
.modal-button:focus,
.modal-button.focused {
  outline: none;
  border-color: #FFFFFF !important;
  box-shadow: 0 0 18px rgba(255, 0, 0, 0.85);
  transform: scale(1.04);
}
```

* **Nos cards de configurações APK (~linha 4098):**
```css
.apk-setting-card:focus,
.apk-setting-card.focused {
  border-color: #FFFFFF !important;
  box-shadow: 0 0 18px rgba(255, 0, 0, 0.85);
}
```

* **Nas opções de rádio APK (~linha 4482):**
```css
.apk-radio-option:focus,
.apk-radio-option.focused {
  border-color: #FFFFFF !important;
  background: rgba(255, 255, 255, 0.1);
}
```

* **Nas linhas de APK em estágio (~linha 4546):**
```css
.apk-staged-row:focus,
.apk-staged-row.focused {
  border-color: #FFFFFF !important;
  box-shadow: 0 0 14px rgba(255, 0, 0, 0.85);
}
```

---

### 2. `smart-tv/mk21-tv/index.html` (Substituição de Logo e Remoção de CSS Vazio)
* Substitua `assets/logo.png` por `assets/mk21-logo.svg` nas linhas de cabeçalho e login:
```html
<img src="assets/mk21-logo.svg" alt="MK21" class="brand-logo" onerror="this.onerror=null; this.src='icon.png';">
```
* Exclua a tag de folha de estilo vazia:
```html
<!-- REMOVER ESTA LINHA: <link rel="stylesheet" href="css/style.css?v=3.0.1"> -->
```

---

### 3. `smart-tv/package_ipk.py` (Redução do Peso do Pacote IPK)
Assegure que o filtro de inclusão exclua pastas e arquivos que já foram embutidos pelo esbuild:

```python
excluded_dirs = {
    'src',
    'components',
    'css',
    '.git',
    'node_modules',
    'tests',
    '__pycache__'
}

excluded_files = {
    'MIGRACAO-FASE2-NOTAS.md',
    '.DS_Store',
    'package_ipk.py'
}

excluded_rel_paths = {
    'assets/logo.png',
}

kept_core_files = {"AlternativeSources.js"}
```

---

### 4. `smart-tv/mk21-tv/src/legacy/app-legacy.js` (OTA Pinado na Tag da Release)
Na função `startDirectUpdate`, aponte as URLs de atualização prioritariamente para a tag semântica:

```javascript
const tag = `v${String(targetVer).replace(/^v/i, '')}`;

const appJsCandidates = [
  `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/${tag}/smart-tv/mk21-tv/app.js?t=${now}`,
  `https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@${tag}/smart-tv/mk21-tv/app.js?t=${now}`,
  `https://bgstreaming.vercel.app/smart-tv/mk21-tv/app.js?t=${now}`,
  `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/app.js?t=${now}`
];

const stylesCssCandidates = [
  `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/${tag}/smart-tv/mk21-tv/styles.css?t=${now}`,
  `https://cdn.jsdelivr.net/gh/2fbg/FBGs-Streaming@${tag}/smart-tv/mk21-tv/styles.css?t=${now}`,
  `https://bgstreaming.vercel.app/smart-tv/mk21-tv/styles.css?t=${now}`,
  `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/smart-tv/mk21-tv/styles.css?t=${now}`
];
```

---

## ⚡ ETAPA 3: REBUILD E TESTES AUTOMATIZADOS

Execute em sequência no terminal:

```bash
# 1. Instalação de dependências limpas
npm ci

# 2. Compilação do JS (esperado ~339 KB)
npm run build:tv

# 3. Empacotamento do IPK limpo (atualiza os hashes de version.json e apps.json automaticamente)
python3 smart-tv/package_ipk.py

# 4. Execução dos testes de validação (todos devem passar)
npm run verify:release -- --ipk smart-tv/mk21play_3.0.1_all.ipk
npm run check:web
python3 -m unittest discover -s smart-tv/tests
npx jest
```

Confira o tamanho do artefato construído:
```bash
ls -lh smart-tv/mk21play_3.0.1_all.ipk
```

---

## 📦 ETAPA 4: COMMIT E PUSH NA MAIN

```bash
git add \
  smart-tv/mk21-tv/styles.css \
  smart-tv/mk21-tv/index.html \
  smart-tv/mk21-tv/src/legacy/app-legacy.js \
  smart-tv/package_ipk.py \
  smart-tv/tests/test_package_ipk.py \
  smart-tv/mk21-tv/app.js \
  smart-tv/mk21play_3.0.1_all.ipk \
  version.json \
  api/version.json \
  web/version.json \
  web/api/version.json \
  public/version.json \
  public/api/version.json \
  smart-tv/version.json \
  apps.json \
  smart-tv/apps.json \
  web/apps.json \
  public/apps.json

git commit -m "fix(smart-tv): corrige foco no webOS 5, enxuga o IPK e pina o OTA na release"
git push origin main
```

---

## 🏷️ ETAPA 5: MOVER A TAG v3.0.1 (MANDATÓRIO)

Como o auto-update da TV faz download direto de `raw.githubusercontent.com/.../v3.0.1/...`, a tag deve apontar para o novo commit:

```bash
git tag -f v3.0.1
git push --force origin refs/tags/v3.0.1
```

Validação do código servido pela tag remota:
```bash
curl -fsSL https://raw.githubusercontent.com/2fbg/FBGs-Streaming/v3.0.1/smart-tv/mk21-tv/app.js | wc -c
```

---

## 🚀 ETAPA 6: ATUALIZAÇÃO DOS ASSETS DA GITHUB RELEASE

Substitua o IPK na release existente sem alterar o arquivo `mk21.apk`:

```bash
# 1. Substituição do pacote IPK
gh release delete-asset v3.0.1 mk21play_3.0.1_all.ipk --repo 2fbg/FBGs-Streaming --yes
gh release upload v3.0.1 smart-tv/mk21play_3.0.1_all.ipk --repo 2fbg/FBGs-Streaming

# 2. Recálculo e reenvio do SHA256SUMS
{ sha256sum mk21.apk; (cd smart-tv && sha256sum mk21play_3.0.1_all.ipk); } > /tmp/SHA256SUMS
gh release delete-asset v3.0.1 SHA256SUMS --repo 2fbg/FBGs-Streaming --yes
gh release upload v3.0.1 /tmp/SHA256SUMS --repo 2fbg/FBGs-Streaming
```

---

## ✅ ETAPA 7: AUDITORIA PÚBLICA FINAL

Execute as checagens finais:
```bash
# 1. Verifica tamanho e download do asset público
curl -sI https://github.com/2fbg/FBGs-Streaming/releases/download/v3.0.1/mk21play_3.0.1_all.ipk | grep -i content-length

# 2. Confirmação dos arquivos anexados à release
gh release view v3.0.1 --repo 2fbg/FBGs-Streaming

# 3. Validação do hash publicado no version.json da main
curl -fsSL https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/version.json | grep ipkSha256
```
