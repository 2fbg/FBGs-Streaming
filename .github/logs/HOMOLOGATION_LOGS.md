# Relatório e Registro de Logs de Homologação — MK21 Play OTT / Smart TV

Este diretório contém os logs de diagnóstico técnico e validações de homologação do aplicativo **MK21 Play (Web, Android e LG webOS Smart TV)**.

## 1. Como Capturar e Salvar Logs

### Na Smart TV (webOS / IPK):
1. **Pelo Controle Remoto:** Pressione a **Tecla Azul** do controle Smart TV para abrir o console de diagnóstico e logs em tempo real.
2. **Pelo Cabeçalho:** Clique no botão **"Logs"** (`#btnHeaderLogs`) com ícone de terminal no canto superior direito.
3. **Exportação:** Clique em **"Salvar / Exportar"** (`#btnExportDevLogs`) para gerar o arquivo `.txt` contendo todos os registros de rede, inicialização do player, troca de formatos (`.ts` / `.m3u8`), codecs e eventos de streaming.
4. **Persistência:** Os logs também são preservados automaticamente na chave `mk21_last_exported_logs` do `localStorage` para inspeção pós-sessão.

### Na Aplicação Web:
1. Abra o painel lateral de diagnóstico técnico localizado sob o player.
2. É exibido o console interativo com color-coding (Verde: Sucesso, Amarelo: Aviso, Vermelho: Erro, Azul: Info).
3. Utilize os botões de exportar ou inspecione via DevTools (`console.log('[MK21-TV]', ...)`).

## 2. Eventos Monitorados em Tempo Real

| Categoria | Evento Monitorado | Ação Automática do Sistema |
|-----------|-------------------|----------------------------|
| **Rede** | Timeout de requisição M3U / EPG | Exponential Backoff com rotação de proxy / fallback direto |
| **Player (HLS)** | Manifest Error / Buffer Stall | Recuperação automática (`recoverMediaError` / `startLoad`) |
| **Player (MPEG-TS)** | Falha no decodificador MSE | Fallback instantâneo para transcodificação HLS (`.m3u8`) |
| **Player (Nativo)** | Codec não suportado | Redirecionamento com MIME types (`application/x-mpegURL`, `video/mp2t`) |
| **Interface** | D-Pad navigation, Fullscreen | Preservação das 3 colunas e ajuste dinâmico de foco |

## 3. Remoção Pós-Homologação

Quando a fase de testes e homologação for concluída, o modal `#modalDevLogs` e o botão `#btnHeaderLogs` podem ser desativados simplesmente ajustando o atributo `DEBUG_MODE = false` em `smart-tv/mk21-tv/app.js`.
