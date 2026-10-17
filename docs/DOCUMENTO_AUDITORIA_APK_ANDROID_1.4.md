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

Na auditoria inicial o build não pôde ser concluído porque o Android SDK não estava instalado. Depois disso, o SDK/JDK foram configurados e a versão v1.5 foi compilada com sucesso.

As ferramentas `apksigner` e `aapt2` foram instaladas e utilizadas para validar assinatura, certificado e metadados do APK v1.5.

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

## Correção do atualizador após erro de conexão

Foi corrigido o atualizador em `app/src/main/java/com/example/viewmodel/AppViewModel.kt`. A ordem atual de consulta é:

1. Manifesto Vercel: `https://bgstreaming.vercel.app/app/applet/api/version.json`;
2. Manifesto bruto do GitHub: `https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/app/applet/api/version.json`;
3. API de releases correta: `https://api.github.com/repos/2fbg/FBGs-Streaming/releases/latest`.

Também foi corrigida a URL antiga do arquivo `servers.json`, removida a comparação lexicográfica de versões e adicionada comparação numérica por componentes. O manifesto Android foi alinhado para a versão instalada 1.4.

A correção exige uma nova compilação/instalação do APK. O APK 1.4 já instalado não pode receber alterações de código remotamente.

## APK Android v1.5 compilado

A versão Android v1.5 foi compilada com `versionCode 6`. Os testes unitários `testDebugUnitTest` e a tarefa `assembleRelease` foram concluídos com sucesso.

O APK v1.5 foi validado pelo APK Signature Scheme v2 e possui:

- SHA-256 do APK: `03a19c6c168da267db212e6e6ca249c188a4fd847a341a399d1a891e9f7601e9`
- SHA-256 do certificado: `04f59f3b091230dc7f31bd9dae94b70f678f6245b4d8f4a460e72c344580a593`
- `applicationId`: `com.aistudio.multiservidor.iptvmk`
- `versionName`: `1.5`
- `versionCode`: `6`

A comparação com o APK v1.4 anterior mostrou certificados diferentes. Como a chave privada original do v1.4 não está disponível no repositório, o Android pode rejeitar a instalação por cima com erro de assinatura. Nesse caso, é necessário exportar dados/backup, desinstalar o v1.4 e instalar o v1.5 manualmente. Para atualizações futuras sem desinstalação, a mesma chave de produção deve ser mantida em um armazenamento seguro.
