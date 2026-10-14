# Changelog - FBGs-Streaming / MK21 Play

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.

## [v1.1.1] - 2026-10-14

### 🎯 Navegação Smart TV, Phased Loading & Controle Parental Reversível
- **[v1.1.1] - Focus Trap Rigoroso para Smart TV (D-Pad):**
  - Interceptação de eventos `keydown` com ciclo fechado entre o primeiro e último elemento focável dentro de modais, impedindo vazamento de foco para elementos de fundo.
  - Mapeamento completo dos keycodes de controle remoto: `37 (Left)`, `38 (Up)`, `39 (Right)`, `40 (Down)`, `13 (Enter)`, `9 (Tab)`, `461 (webOS Back)`, `10009 (Tizen Back)` e `27 (Escape)`.
  - Redesign do modal (`.modal-container`, `.modal-content`, `.modal-btn`) com foco destacado em laranja MK21 (`#ff9800`), `transform: scale(1.05)` e rolagem restrita ao `.modal-content-scroll`.
- **[v1.1.1] - Sincronização Phased Loading (Carregamento por Fases):**
  - **Fase 1 (Síncrona/Prioritária):** Extração e renderização imediata dos canais Ao Vivo (Live), liberando a interface do usuário e o player instantaneamente.
  - **Fase 2 (Assíncrona/Background):** Processamento contínuo de Filmes e Séries (VOD) em background sem congelar o Main Thread da TV ou Web.
  - Indicador dinâmico de porcentagem de sincronização exibido ao lado do nome do servidor no Header.
- **[v1.1.1] - Controle Parental Reversível via PIN (Sem Exclusão de Catálogo):**
  - Canais e categorias adultas são preservados integralmente na memória e indexador (`isAdult: true`), alternando visibilidade em tempo de execução via flag de sessão desbloqueada por senha (PIN).
- **[v1.1.1] - Player OSD & Mini Player Fixo:**
  - EPG Overlay integrado ao Fullscreen HUD com identificador de canal, programa atual (`AGORA`), barra de progresso em tempo real e próximo programa (`A SEGUIR`).
  - Mini Player fixo com painel estático (`col-player`) sem deslocamento de layout durante a rolagem de listas de canais.
  - Correção de z-index, classe `.btn-settings` e handler direto no botão de configurações.
- **[v1.1.1] - Versionamento Android & Smart TV:**
  - `versionCode: 111` e `versionName: "1.1.1"` no `app/build.gradle.kts`.
  - `appinfo.json`, `config.xml` e `version.json` atualizados para `v1.1.1`.
  - Pacote `.ipk` para LG webOS gerado com sucesso: `mk21play_1.1.1_all.ipk`.

---

## [v1.1.0] - 2026-10-13

### 🚀 Otimizações de Player e Usabilidade (Foco Cliente)
- **[v1.1.0] - Otimização de Buffer inicial para abertura rápida de canais (Fast Start):**
  - **Android (Media3/ExoPlayer):** Reconfiguração do `DefaultLoadControl` com `minBufferMs` reduzido para 5.000ms e `bufferForPlaybackMs` ajustado para 500ms, proporcionando início quase instantâneo de canais ao vivo e VODs HLS (`.m3u8`).
  - **Web & Smart TV (Hls.js):** Ativação de `lowLatencyMode: true`, `startFragPrefetch: true` e `maxBufferLength: 15` com teto seguro (`maxMaxBufferLength: 30`), eliminando buffering excessivo e travamentos em conexões instáveis.
- **[v1.1.0] - Implementação de Retry automático para streams instáveis:**
  - **Android:** Mecanismo resiliente de auto-reconexão com exponential backoff (até 5 tentativas graduais) e recuperação de falhas transitórias de decodificação.
  - **Web & Smart TV:** Tratamento com reconexão automática nos eventos fatais do `Hls.Events.ERROR` (`NETWORK_ERROR` via `startLoad()` e `MEDIA_ERROR` via `recoverMediaError()`), com fallback automático para stream nativo direto.
- **[v1.1.0] - Melhorias de performance na rolagem de listas de canais:**
  - **Android:** Virtualização total no `LazyColumn` com chaves de estabilidade (`key`), prevenindo recomposições desnecessárias durante rolagens rápidas em listas com milhares de canais.
  - **Web & Smart TV:** Paginação virtualizada acionada por `IntersectionObserver` e Shimmer UI esqueleto durante a transição de categorias e carregamento, mantendo 60 FPS contínuos na rolagem.
- **Feedback Visual Aprimorado:**
  - HUD visual elegante e centralizado no player: estados explícitos de `"Conectando ao servidor..."`, `"Carregando canal..."`, `"Reconectando canal (Tentativa X de 5)..."` e `"Erro de reprodução"` com opções de nova tentativa.
- **Aceleração por Hardware:**
  - Garantia de decodificação acelerada por hardware via `DefaultRenderersFactory` com fallback defensivo no Android e decodificação nativa nas TVs LG webOS e Samsung Tizen.
- **Sincronização de Versão:**
  - `versionName: 1.1.0` (versionCode 13) alinhado em todas as plataformas (Android, Web, LG webOS e Samsung Tizen).

---

## [v1.0.5] - 2026-10-12
- Controles modernos em tela cheia com avanço e retrocesso de canais pelas setas do controle.
- Timeline interativa e barra de progresso para filmes e séries (VOD).
- Detecção e exibição real da resolução do fluxo (HD 720p / FHD 1080p / 4K).
- Modal com opções completas de classificação e ordenação.
- Novo filtro de conteúdo ao carregar em configurações para ultra velocidade e economia de RAM.
- Ocultação total e rigorosa de canais e categorias no bloqueio 18+.
