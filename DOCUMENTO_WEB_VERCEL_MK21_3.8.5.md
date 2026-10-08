# MK21 Play Web/Vercel — versão 3.8.5

## Sim, é aplicável

A arquitetura do catálogo IndexedDB funciona no navegador da versão web porque IndexedDB é uma API do cliente. O Vercel não mantém o catálogo de cada usuário; ele publica a interface e executa o proxy serverless. Portanto, a persistência do catálogo ocorre no navegador, enquanto o Vercel fornece a entrega da página e a conexão HTTP/HTTPS intermediária.

## O que foi aplicado

A cópia mais atual da interface, antes mantida em `web/index.html`, foi unificada com as cópias publicadas em `index.html` e `web/web/index.html`. O Vercel agora serve a mesma interface em todas as rotas previstas pelo `vercel.json`.

O cache IndexedDB da web e o carregamento em segundo plano existentes foram preservados. Eles continuam permitindo reabertura rápida, retenção temporária de playlists e renderização progressiva de filmes e séries.

O proxy foi atualizado tanto em `api/proxy.js` quanto em `web/api/proxy.js`. Quando o navegador abandona a página ou fecha a conexão, o request upstream é destruído. Isso evita sockets e streams órfãos nas funções serverless. Também foi adicionado `X-Content-Type-Options: nosniff` às respostas encaminhadas.

As proteções anti-SSRF, bloqueio de hosts privados, limite de requisições, redirecionamentos limitados, suporte a Range e cache controlado foram preservados.

## O que não deve ser confundido

O IndexedDB não fica no servidor Vercel e não é compartilhado entre usuários. Cada navegador mantém seu próprio cache local. O Vercel não deve ser usado para armazenar listas IPTV, credenciais ou estado individual de reprodução.

A versão web já possuía um cache IndexedDB de playlists e carregamento progressivo. A migração webOS 3.8.x usa um índice de itens mais agressivo para Smart TVs com memória limitada. A interface web possui um ambiente de navegador geralmente mais amplo e, nesta release, recebe o alinhamento de publicação e o reforço do proxy sem forçar uma mudança incompatível no cache existente.

## Deploy

O projeto está preparado para deploy Vercel a partir da raiz do repositório. O `vercel.json` encaminha `/api/proxy` para a função correspondente e mantém as rotas `/web`, `/web/` e `/web/index.html` compatíveis.

Depois do push, o Vercel deve gerar um novo deployment automaticamente se o projeto estiver conectado ao repositório e à branch `main`. A aplicação publicada deve ser validada em:

- `/`
- `/web`
- `/web/`
- `/api/proxy?url=...`

Não é possível confirmar um deployment externo sem acesso ao painel ou URL pública configurada do Vercel nesta sessão. O código e a configuração foram atualizados no GitHub.

## Validação local

- `node --check api/proxy.js`: aprovado.
- `node --check web/api/proxy.js`: aprovado.
- Cópias `index.html`, `web/index.html` e `web/web/index.html`: idênticas.
- `git diff --check`: aprovado.

## Sugestão para uma próxima versão web

A próxima evolução ideal é utilizar uma store `catalog_items` no IndexedDB também no navegador, indexando itens em lotes durante a importação e fazendo a busca/paginação diretamente na store. Essa migração deve ser feita em uma etapa separada para não interromper o cache web atual nem aumentar o tempo de primeiro acesso.
