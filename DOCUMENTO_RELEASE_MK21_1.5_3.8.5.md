# Release MK21 — Android 1.5 e Smart TV webOS 3.8.5

## Resumo

Esta release corrige o fluxo de atualização do Android e reduz a pressão de memória da aplicação webOS para listas muito grandes. O catálogo completo continua sendo indexado no IndexedDB, mas a interface mantém somente uma janela operacional pequena no heap JavaScript.

## Android v1.5

A versão Android foi atualizada para `versionCode 6` e `versionName 1.5`. O atualizador consulta o manifesto Vercel, usa o GitHub Raw como fallback e só depois consulta a API de releases correta. A comparação de versões passou a ser numérica por componentes, evitando erros como comparar `1.10` e `1.9` como texto.

Os testes `testDebugUnitTest` e a compilação `assembleRelease` foram concluídos com sucesso. O APK foi validado pelo APK Signature Scheme v2. SHA-256 do APK: `03a19c6c168da267db212e6e6ca249c188a4fd847a341a399d1a891e9f7601e9`. SHA-256 do certificado: `04f59f3b091230dc7f31bd9dae94b70f678f6245b4d8f4a460e72c344580a593`.

A chave privada que assinou o APK Android v1.4 anterior não foi encontrada no repositório. Por isso, o Android pode exigir desinstalar o v1.4 antes de instalar o v1.5. Para as próximas releases, a chave de produção deve ser guardada em um keystore seguro e reutilizada.

## Smart TV webOS v3.8.5

A janela RAM foi reduzida para 2.000 canais ao vivo, 3.000 filmes e 3.000 séries. O restante do catálogo não é descartado: permanece no índice persistente e é carregado por páginas sob demanda. Isso evita manter 29 mil filmes ou 255 mil episódios em arrays e referências simultâneas na interface.

O cache serializado foi migrado para `mk21_play_db_v6`, invalidando o cache antigo que podia desserializar até 39 mil objetos na abertura. Quando o cache resumido não está disponível, a aplicação tenta mostrar imediatamente os primeiros 2.000 canais a partir do índice IndexedDB antes de concluir o download da lista.

O botão de limpeza total agora remove tanto o cache resumido quanto o índice persistente do catálogo. Os botões individuais de limpeza receberam tamanho padrão, sem padding inline excessivo. A proteção de evento `webOSLowMemory` também reduz a janela de filmes e séries e libera referências de categorias antes de redesenhar a tela.

## Validação

O JavaScript da Smart TV passou em `node --check`. O IPK foi gerado e extraído novamente para validar sua estrutura, versão `3.8.5`, código `385` e o `app.js` instalado. O SHA-256 do IPK é `34b6d4ff697aeec50ed10318e0ea27c57966302c2c4a015c1beb74ce70a1eded`.

## Procedimento recomendado na TV

Instale o IPK 3.8.5. Como o banco de dados mudou para a versão v6, o cache antigo não será reutilizado; a primeira abertura após a instalação poderá fazer uma carga inicial. Depois disso, a abertura deve usar o cache resumido ou a primeira página do IndexedDB. Se a TV ainda reiniciar, use **Configurações → Limpar armazenamento → Limpar tudo** uma única vez e aguarde a reconstrução do índice.


## Ajuste final do atualizador Android

O atualizador passou a consultar todos os manifestos disponíveis e escolher a maior versão numérica, em vez de parar no primeiro endpoint que responde. Isso evita que um manifesto Vercel temporariamente desatualizado impeça a descoberta do v1.5 no GitHub Raw.
