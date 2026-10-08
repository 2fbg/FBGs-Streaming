# MK21 Play — Documento consolidado das versões 3.4.0 a 3.8.5

## Resumo executivo

O projeto foi evoluído com foco em Smart TVs webOS, Android TV, Google TV, Tizen e na versão web publicada pelo Vercel. A arquitetura atual prioriza inicialização rápida, navegação por controle remoto, estabilidade em listas M3U muito grandes e redução de travamentos por memória.

A versão Smart TV deixou de depender exclusivamente de um catálogo inteiro mantido no heap JavaScript. A lista é lida em fluxo, indexada em lotes no IndexedDB e consultada por páginas. A interface mantém uma janela operacional limitada e substitui a página anterior quando o usuário avança.

## Problemas tratados

Foram corrigidos ou mitigados atrasos de foco no início, respostas antigas sobrescrevendo listas novas, troca de servidor com requisições concorrentes, processamento de listas de centenas de milhares de linhas, cópias desnecessárias durante renderização e ordenação, busca limitada à janela atual e favoritos incompletos quando o item estava fora da memória.

O fluxo do controle remoto foi preservado com normalização de `key`, `code` e códigos legados. A navegação não captura setas enquanto o usuário está editando campos. O foco inicial é agendado no próximo frame, com fallback compatível com TVs sem `requestAnimationFrame`.

## Carregamento da lista Smart TV

A resposta HTTP é consumida com `ReadableStream` e `TextDecoder` quando disponíveis. O parser processa blocos e mantém somente a sobra da última linha incompleta. Os canais ao vivo podem aparecer antes do final do download.

A troca de servidor usa `AbortController` e um identificador de geração. A resposta de uma carga anterior não pode alterar a tela atual. Em ambientes antigos sem streaming, permanece um fallback baseado em `response.text()`.

## Arquitetura IndexedDB Smart TV

A base `mk21_catalog_index_v1` possui uma store de itens e uma store de metadados. Os itens têm índices por servidor/tipo, servidor/tipo/categoria e servidor/tipo/nome normalizado.

A importação grava lotes de 500 itens, preserva o índice de outros servidores e remove apenas os dados do servidor que está sendo atualizado. O catálogo completo fica persistido localmente, sem depender de novo download para cada página.

## Memória operacional

A janela de memória foi distribuída considerando cerca de 5 mil canais ao vivo:

| Tipo | Janela operacional |
|---|---:|
| Ao vivo | 6.000 itens |
| Filmes | 18.000 itens |
| Séries | 15.000 itens |

A soma foi mantida controlada. Itens além da janela são indexados no IndexedDB. Ao avançar, a página anterior é substituída; as páginas não se acumulam indefinidamente no heap.

## Paginação e busca

O botão de carregamento consulta o índice por servidor, tipo, categoria e offset. A página tem 240 registros. Quando a janela atual chega ao limite, o aplicativo substitui a janela pela próxima página e mantém o uso de memória estável.

A busca usa debounce e consulta diretamente o índice IndexedDB pelo nome normalizado. O resultado é limitado a 240 itens. Cada consulta possui um identificador de geração; respostas antigas são descartadas quando o usuário altera o texto rapidamente.

## Favoritos e continuar assistindo

Favoritos são consultados no índice completo, e não apenas na janela de itens carregada. Isso corrige o caso em que um favorito estava além dos primeiros itens da lista.

O histórico de continuar assistindo permanece limitado a 50 itens, armazenado localmente, o que evita crescimento sem controle e mantém a retomada rápida.

## Segurança e OTA

O OTA não executa JavaScript ou CSS armazenado no `localStorage`. A versão efetiva é a do pacote instalado. O IPK é validado com SHA-256 antes de prosseguir. HLS.js está fixado para impedir alterações imprevisíveis de dependências remotas.

Listas HTTP continuam sendo acessadas diretamente quando necessário para compatibilidade com provedores legados. O aplicativo não envia credenciais para proxies públicos. Para produção, a recomendação é um proxy próprio HTTPS.

## Integração web e Vercel

A versão web utiliza IndexedDB para cache de playlists e carregamento progressivo em segundo plano. A interface web mais atual foi unificada com as cópias publicadas no Vercel: `index.html`, `web/index.html` e `web/web/index.html` são agora idênticas.

O Vercel publica a interface e executa o proxy serverless; não armazena o catálogo individual dos usuários. A persistência de cada catálogo ocorre no IndexedDB do navegador.

O proxy foi reforçado em `api/proxy.js` e `web/api/proxy.js`. Quando o navegador abandona a página ou fecha a conexão, o request upstream é destruído, evitando sockets e streams órfãos em funções serverless. Também foi adicionado `X-Content-Type-Options: nosniff`. As proteções anti-SSRF, bloqueio de hosts privados, limite de requisições, redirecionamentos limitados, suporte a Range e cache controlado foram preservados.

A configuração do Vercel mantém as rotas `/`, `/web`, `/web/`, `/web/index.html` e `/api/proxy`. A versão web de alinhamento foi registrada como 3.8.5.

## Repositório e entrega

O repositório foi limpo de artefatos intermediários e caches locais, recebeu regras de `.gitignore` e possui workflow CI para validar JavaScript, empacotador, versão e build Android.

A versão final Smart TV desta etapa é **3.8.4**, pacote `org.mk21.tv`, arquitetura `all`. A camada web/Vercel está registrada como **3.8.5**.

SHA-256 do IPK Smart TV:

```text
10486858b6b84acf67dad00056ffd4c081974e88f22df45297589a25630a5765
```

## Validações realizadas

`node --check` foi executado no código Smart TV e nos proxies Vercel. O empacotador Python passou por `py_compile`. O IPK foi extraído com `dpkg-deb`, seus metadados foram conferidos e `git diff --check` foi aprovado.

As três cópias da interface web possuem o mesmo SHA-256 após a unificação. O repositório foi verificado limpo após o commit final.

## Recomendações futuras

A arquitetura Smart TV está pronta para refinamentos de categorias. As melhorias de maior valor seriam gerar contadores de categorias durante a importação, carregar categorias diretamente do IndexedDB, criar índices de ordenação por nome e ano e migrar continuar assistindo para uma store própria.

Para a web, a próxima evolução ideal é utilizar uma store `catalog_items` no IndexedDB também no navegador, indexando itens em lotes durante a importação e fazendo a busca/paginação diretamente na store. Essa migração deve ser feita em uma etapa separada para não interromper o cache web atual nem aumentar o tempo de primeiro acesso.

Também é recomendável validar em TVs físicas com listas de 50 mil, 150 mil e 300 mil itens, pois o desempenho do IndexedDB varia conforme o modelo e a versão do webOS.
