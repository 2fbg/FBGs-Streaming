# MK21 Play Smart TV — correções 3.8.6

## Problemas corrigidos

### Versão e novidades

A versão instalada passa a ser `3.8.6`, com `versionCode 386`. O manifesto interno, o manifesto OTA, o `appinfo.json`, o HTML e o código de fallback usam a mesma versão. As novidades exibidas no modal também são as da 3.8.6, não mais as notas antigas da 3.6.0.

### Foco do controle remoto em modais

A navegação espacial agora identifica o modal visível e limita a lista de elementos focáveis aos controles dentro dele. As setas cima, baixo, esquerda e direita não podem mais selecionar botões da tela de trás enquanto um modal estiver aberto.

O botão Voltar também foi corrigido para fechar o modal ativo antes de tentar mudar de seção. O identificador da janela de episódios foi ajustado de `modalSeries` para `modalSeriesEpisodes`, evitando que o Back atravesse esse modal.

### Microfone

O botão visual de microfone foi removido. O reconhecimento nativo pelo botão de voz do controle remoto continua disponível quando a plataforma webOS fornece esse evento, mas não há mais botão ao lado da busca.

## Validação

- `node --check smart-tv/mk21-tv/app.js`: aprovado.
- IPK extraído novamente e validado.
- Pacote: `org.mk21.tv`.
- Versão: `3.8.6`.
- Código: `386`.
- SHA-256 do IPK: `fe9d2bff4b84842fb2e17bf931c4091780ab1a24437d5d91de09b62de3f2c8f5`.
