# Paridade com o Saimo TV — o que já foi portado e o que falta

**Objetivo:** usar o Saimo TV (aplicativo do `github.com/SaimoTV`, pacote LG webOS
`SaimoTV-LG.ipk` v2.6.10 e o PWA `SaimoTV/Saimo-Site` v3.2.3) como referência de
funcionalidade para o MK21 Play.

Este documento tem três partes:

1. **O que foi implementado** nesta rodada (Smart TV);
2. **O inventário comparativo** — recurso do Saimo TV × estado do MK21;
3. **O roteiro do que ainda falta**, em ordem de prioridade.

> Os inventários completos usados como base estão em
> `docs/MK21-FEATURE-INVENTORY.md` (MK21: Android + Smart TV + Web, 165 recursos) e
> `docs/SAIMO-TV-FEATURE-INVENTORY.md` (Saimo TV: 61 recursos no app web +
> app nativo de TV).

---

## 1. O que foi implementado nesta rodada

Tudo abaixo é **Smart TV (LG webOS / Samsung Tizen)**, que é onde o MK21 tinha a
maior defasagem: o app de TV do Saimo (`tv-native/`) tem uma tela de guia, um HUD
de zapping e um teclado virtual próprios, e o MK21 não tinha nenhum dos três.

### 1.1 Guia de Programação (tela dedicada) ✅

Uma nova aba **GUIA** na lateral, com duas colunas:

- **Esquerda — canais:** número do canal, logo (com placeholder por inicial),
  nome e o que está **no ar** naquele canal, em verde.
- **Direita — programação do canal selecionado:** faixa de horário, selo
  **NO AR**, título, sinopse e barra de progresso do programa em andamento.

Navegação 100 % pelo controle remoto: `▲▼` anda na coluna ativa, `◀▶` troca de
coluna, `OK` abre a programação / sintoniza o canal, `VOLTAR` fecha e devolve o
foco para a aba de origem.

Arquivos: `smart-tv/mk21-tv/index.html` (`#sectionGuide`),
`smart-tv/mk21-tv/styles.css` (bloco *GUIA DE PROGRAMAÇÃO*),
`smart-tv/mk21-tv/src/legacy/app-legacy.js` (`mk21OpenGuide`,
`mk21RenderGuideChannels`, `mk21RenderGuidePrograms`, `mk21SelectGuideChannel`,
`mk21GuideMove`, `mk21GuideActivate`).

### 1.2 EPG real (XMLTV) na Smart TV ✅

**Este era o buraco mais fundo.** Antes desta rodada a TV:

- não tinha **nenhum** carregador de XMLTV — a função `matchEPG()` existia mas
  era código morto, porque `epgCache` nunca era preenchido;
- só conhecia o EPG do canal **que estava tocando** (`get_short_epg` do Xtream,
  6 programas);
- completava o resto com uma **grade simulada** (`getSimulatedTvProgram`, com
  programações fixas de Globo/Record/SBT).

Agora a TV baixa o `xmltv.php` do servidor (ou o `url-tvg=` do header da
playlist), indexa por canal e usa a grade real no Guia e no HUD de zapping.

Detalhes que importam na prática:

- **Parser por regex, não DOMParser** — o arquivo pode passar de 50 MB e a
  árvore DOM estouraria a memória das TVs antigas (mesmo motivo do M3U ir para
  Web Worker). Há teto de 250 000 programas.
- **Três formas de amarração canal ↔ EPG**, em ordem:
  1. `tvg-id` / `epg_channel_id` (id exato do XMLTV);
  2. id do XMLTV igual ao nome normalizado do canal;
  3. `<display-name>` do XMLTV — cobre os painéis que **não** devolvem
     `epg_channel_id`, que é a maioria.
- A normalização de nome remove sufixos de qualidade (`Globo FHD` → `Globo`).
- O `get_short_epg` do Xtream foi extraído para `mk21FetchShortEpg()` (preservando
  o cache IndexedDB `mk21_epg_tv_v1`) e passa a ser reaproveitado: o Guia usa os
  12 primeiros programas do canal em foco.

### 1.3 HUD de zapping ✅

Na troca de canal (`CH +/-`, digitação de número, guia), aparece um cartão no
topo com:

- número do canal (LCN, pela ordem do catálogo) e nome;
- logo do canal;
- selo **NO AR** + título do programa em exibição;
- barra de progresso do programa;
- **A SEGUIR** + contagem regressiva até o próximo programa (atualiza a cada
  segundo) e auto-hide em 7 s.

Arquivos: `core/ZapHud.js`, `#zapHud` no `index.html`, `mk21ShowZapHud()` /
`mk21HideZapHud()` no legado.

### 1.4 Teclado virtual (D-pad) ✅

A TV não tem teclado físico, e o teclado do sistema (OSK) do webOS/Tizen é
lento. Há agora um teclado QWERTY **PT-BR** (com `ç`, `á`, `é`, `í`, `ó`, `ú`,
`â`, `ê`, `ô`, `à`, `õ`), navegável só com o D-pad:

- botão `⌨️` ao lado do campo de busca;
- 4 modos (`abc` / `ABC` / `123` / `#+=`) e linha de controle (modo, espaço,
  backspace, limpar);
- a seleção **avança** para a próxima tecla depois de digitar (como teclado de
  celular) e a borda do grid **prende** o cursor, sem dar a volta;
- `VOLTAR` fecha sem confirmar; o campo de busca só é alterado ao confirmar.

Arquivos: `core/OnScreenKeyboard.js`, `#oskOverlay` no `index.html`,
`mk21OpenOsk()` / `mk21RenderOsk()` / `mk21OskMove()` / `mk21OskActivate()`.

### 1.5 Como foi feito (para não repetir trabalho)

A lógica fica em **módulos puros** em `smart-tv/mk21-tv/core/`, testáveis em
Node, e a camada de DOM fica no legado — o mesmo padrão de `core/CatalogUtils.js`:

| Módulo | Responsabilidade | Testes |
|---|---|---|
| `core/EpgGuide.js` | normalização de timestamps (epoch s/ms, XMLTV, ISO), merge de fontes, linha do tempo, "no ar / a seguir", contagem regressiva | `smart-tv/tests/epg-guide.test.js` (17) |
| `core/ZapHud.js` | payload do HUD, auto-hide, numeração de canal (LCN) | `smart-tv/tests/zap-hud.test.js` (9) |
| `core/OnScreenKeyboard.js` | grid de teclas, navegação D-pad, modos, cursor de texto | `smart-tv/tests/on-screen-keyboard.test.js` (16) |

Soma-se a isso um **teste de integração real em Chromium**
(`smart-tv/tests/tv-guide-integration.test.js`): sobe um servidor IPTV simulado
(Xtream + XMLTV), faz login no app de verdade e confere o login, a guia com
grade real, a navegação por D-pad, o VOLTAR, o HUD de zapping e o teclado
virtual. O Playwright é opcional — sem ele o teste é ignorado, não quebrado.

**Tamanho do bundle:** `app.js` permanece bem abaixo do teto de 350 KB.

---

## 2. Inventário comparativo

`✅` = no MK21 · `🆕` = implementado nesta rodada · `🟡` = existe, mas diferente
do Saimo TV · `⬜` = falta no MK21

### 2.1 Ao vivo / navegação

| Recurso do Saimo TV | Estado no MK21 (TV) | Observação |
|---|---|---|
| Grade de canais com categorias, busca e render progressivo | ✅ | `renderItemsList()` + índice IndexedDB |
| Múltiplas fontes por canal com failover automático | ✅ | `core/AlternativeSources.js` + failover entre servidores |
| Watchdog de 12 s sem imagem / 25 s de congelamento | ✅ | `startStallWatchdog()` |
| Seleção manual de fonte ("Fonte N", **sem expor a URL**) | 🆕 | `SmartTvPrefs.maskSourceLabel()` oculta domínios/URLs crus em `#modalServerPicker` |
| Canal anterior / próximo com número (LCN) editável | 🆕 | Número exibido no HUD de zapping e editável por servidor (`SmartTvPrefs.setChannelCustomLcn`) |
| Favoritos de canal | ✅ | `mk21_favs` |
| Multi-tela / Picture-in-Picture | ⬜ | Web e Android têm PiP; a TV não |
| Retomar o último canal ao abrir | ✅ | `resumeLastChannelIfPossible()` |
| Volume separado entre live e VOD | 🆕 | Persistido separadamente (`mk21_vol_live_v1` / `mk21_vol_vod_v1`) |
| "Áudio Inteligente" (normalizar canais fracos) | 🆕 | Modos Desligado / Normalizar / Boost +60% via Web Audio API (`DynamicsCompressorNode` + `GainNode`) |

### 2.2 Guia de programação (EPG)

| Recurso do Saimo TV | Estado no MK21 (TV) |
|---|---|
| Tela de guia com lista de canais + grade | ✅ |
| EPG real (XMLTV) indexado por canal | ✅ |
| Overlay de "no ar" e "a seguir" com contagem | ✅ |
| Guide com fonte pública (`meuguia.tv`), sem credencial | 🆕 (`/api/epg.js` + fallback em TV e Android) |
| Busca do "programa que está no ar agora" | 🆕 (`EpgGuide.searchNowPlaying` na TV + filtro no ar no Android) |
| Grade por eixo de tempo (canal × horário) | 🆕 (alternância `Lista` ↔ `Grade Horária` em `#guideTimelineGrid`) |

### 2.3 On Demand / VOD

| Recurso do Saimo TV | Estado no MK21 |
|---|---|
| Catálogo de filmes/séries com trilhas (rails) e TMDB | ✅ (opcional) |
| Filtros por ano, faixa etária, nota e ordenação | 🟡 (ordem e filtro por categoria) |
| Ficha do título (sinopse, elenco, relate) | ⬜ |
| Pular abertura / créditos | 🆕 (botão flutuante em VOD na Smart TV e Android) |
| Legendas via OpenSubtitles com ajuste de sincronia | 🆕 (`/api/subtitles` + busca e ajuste `±0.5s` em Smart TV e Android) |
| Retomar VOD de onde parou | ✅ (`mk21_vod_resume_progress` + sincronizado na nuvem) |
| "Continue assistindo" com barra de progresso | ✅ |
| Addons (Stremio) / Debrid | ⬜ |
| Download offline | ⬜ |

### 2.4 Telas e organização

| Recurso do Saimo TV | Estado no MK21 |
|---|---|
| "Minhas listas" (até 10 listas M3U por link/arquivo) | 🆕 (importação de arquivo `.m3u/.m3u8` + links M3U com extração automática de `url-tvg`, teto de 10) |
| Tela de **Radios** | ⬜ |
| Tela de **Eventos** / Novidades | ⬜ |
| Tela de **Doação** (Pix / QR) | ⬜ |
| Perfil de usuário (10 perfis) + perfil infantil | ⬜ (a TV, Android e Web têm PIN de adulto) |
| Tela de Ajustes com seções | ✅ (a TV já tem um menu de configurações grande) |
| Busca universal (canais, filmes, séries) | ✅ (Omnisearch) |
| Tela de "Ficha" do canal (detalhes) | 🟡 (a 3ª coluna da tela principal mostra EPG e controles) |

### 2.5 Android e Web

| Recurso / Lacuna | Plataforma | Estado |
|---|---|---|
| Tela de guia de EPG completa (`FullEpgGuideDialog`) + busca "No Ar Agora" | Android | ✅ |
| XMLTV em streaming (`XmltvEpgParser` + `AndroidEpgManager`) | Android | ✅ |
| Catálogo JSON do Xtream (`get_series` + episódios reais) | Android | ✅ |
| Fallback EPG público brasileiro sem credencial (`/api/epg`) | Android & TV | 🆕 |
| Controle parental (PIN + trava de categoria adulta) | Web | ✅ (`web/parental.js` + `parental.js`) |
| Numeração de canal (LCN) editável por servidor | Smart TV | 🆕 |
| Sincronização em nuvem (Favoritos + Continuar Assistindo via `MK21-XXXX-XXXX`) | Android, TV & Web | 🆕 (`/api/sync` + Neon Postgres LWW) |
| Legendas OpenSubtitles + ajuste de sincronia (`±0.5s`) + Pular Abertura/Créditos | Android & TV | 🆕 (`/api/subtitles` + `SubtitleAndSkip`) |
| Fluxo de licença/ativação unificado | Web e TV | ⬜ |
| Perfis de usuário (10 perfis) | Todas | ⬜ |
| Catch-up / timeshift / gravação | Todas | ⬜ |

---

## 3. Roteiro restante (baixa prioridade)

1. **Perfis de usuário** com prefixo de chave por perfil.
2. **Tela de Ficha do canal / Rádios / Eventos** na TV.
3. **Catch-up / Timeshift** nos servidores Xtream com suporte a archive.

---

## 4. Como rodar o que foi implementado

```bash
# Rebuild do bundle da TV (app.js) — obrigatória depois de mexer em src/
npm run build:tv

# Suíte da TV (o Playwright é opcional; sem ele o teste de integração é ignorado)
node --test smart-tv/tests/*.test.js
npm i -D playwright && npx playwright install chromium   # opcional, p/ o teste de UI

# Web: sincronizar os espelhos web/ e public/
npm run sync:web && npm run check:web
```

O pacote `.ipk` continua sendo gerado por `python3 smart-tv/package_ipk.py`.

