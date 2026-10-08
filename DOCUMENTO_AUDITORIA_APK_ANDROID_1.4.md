# Auditoria do APK Android MK21

## Resultado

O APK `web/mk21.apk` é um pacote Android estruturalmente íntegro. O teste ZIP foi concluído sem erros e o arquivo contém `AndroidManifest.xml`, `resources.arsc` e múltiplos arquivos DEX. A cópia `mk21.apk` na raiz é byte a byte idêntica à cópia em `web/mk21.apk`.

SHA-256 dos dois APKs:

```text
ec92f4b6c827a3c29e2d23e905516105953b2f2e704237f07e832aa6d1644f2c
```

Tamanho: 25.415.775 bytes.

## Versão identificada

O projeto Android está configurado como:

```text
applicationId: com.aistudio.multiservidor.iptvmk
versionName: 1.4
versionCode: 5
minSdk: 24
targetSdk: 36
```

As strings do APK também indicam a linha 1.4. Portanto, ele é uma aplicação Android nativa separada da linha webOS 3.8.4 e da camada web/Vercel 3.8.5. As otimizações IndexedDB do webOS não são automaticamente incorporadas ao APK.

## Pontos positivos do código Android

O Android já usa Room como persistência local do catálogo. O DAO consulta por tipo e categoria com `LIMIT 1500`, a busca tem `LIMIT 100`, e o ViewModel expõe listas reativas com limite visual. A importação de listas grandes é feita em chunks de 1.500 itens dentro de transações, o que é adequado para listas extensas e evita uma transação única gigantesca.

Também existem testes unitários para o parser M3U e para o parser de servidores.

## Problemas encontrados

A validação de build não pôde ser concluída neste sandbox porque o Android SDK não está instalado nem configurado. O Gradle Wrapper estava corrompido; ele foi restaurado para a versão oficial 9.3.1, mas o build parou depois com `SDK location not found`. Assim, não foi gerado um APK novo nesta auditoria.

As ferramentas `apksigner`, `aapt` e `jarsigner` não estão disponíveis neste ambiente. Foi possível validar a estrutura ZIP, mas a assinatura criptográfica do APK não foi confirmada.

O manifesto permite cleartext HTTP globalmente e declara `REQUEST_INSTALL_PACKAGES`. Isso pode ser necessário para provedores IPTV HTTP e atualização direta, mas aumenta a superfície de segurança. O arquivo `network_security_config.xml` também contém uma lista ampla de domínios e hosts legados.

O build de release usa o keystore de debug como fallback quando as credenciais de release não existem. Isso é aceitável para preview, mas não deve ser usado para distribuição de produção. O código também contém segredos de fallback e códigos administrativos hardcoded que devem ser removidos ou transferidos para configuração segura antes de uma publicação ampla.

`largeHeap=true` pode aliviar pressão em alguns aparelhos, mas não substitui paginação e pode mascarar vazamentos. A arquitetura Room atual é o caminho correto para evitar depender apenas desse recurso.

## Recomendações

1. Instalar Android SDK e executar `./gradlew testDebugUnitTest assembleDebug` em CI.
2. Gerar APK release somente com keystore de produção configurado; falhar o build se houver fallback para debug.
3. Publicar versão Android própria, por exemplo 1.5, após validar mudanças nativas; não alterar apenas a string para 3.8.4 sem uma decisão de versionamento.
4. Restringir cleartext a domínios legados estritamente necessários.
5. Remover códigos administrativos e segredos hardcoded do código-fonte.
6. Instalar `apksigner` no pipeline e publicar o certificado/fingerprint da versão oficial.
7. Adicionar testes de volume com listas de 50 mil, 150 mil e 300 mil itens.

## Conclusão

O APK entregue está íntegro e sua arquitetura Android já utiliza Room e inserção em lotes, que é equivalente ao objetivo de persistência paginada para Smart TV. Ele não está na mesma numeração das versões 3.8.x porque pertence a uma linha Android nativa separada. Antes de uma nova publicação Android, os pontos de assinatura, SDK, HTTP global, segredos hardcoded e fallback de keystore devem ser tratados.
