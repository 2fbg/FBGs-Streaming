# Changelog - FBGs-Streaming / MK21 Play

Todas as alterações notáveis deste projeto serão documentadas neste arquivo.

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
