# Secrets, variáveis e checklist de release

Este guia descreve o que precisa estar configurado em **GitHub → Settings → Secrets and variables → Actions**
(no repositório `2fbg/FBGs-Streaming`, nível *Repository*, não *Environment*) para publicar um release assinado.

## Secrets (aba *Secrets*)

| Nome | Obrigatório | Descrição |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | sim | Keystore de upload em base64 numa linha só: `base64 -w0 upload.jks` |
| `STORE_PASSWORD` | sim | Senha do keystore |
| `KEY_PASSWORD` | não | Senha da chave (se vazia, usa `STORE_PASSWORD`) |
| `KEY_ALIAS` | não | Deve ser `upload` (é o alias lido por `app/build.gradle.kts`) |
| `EXPECTED_CERT_SHA256` | recomendado | SHA-256 do certificado (pin do updater). Se vazio, o release deriva do keystore |
| `LICENSE_SECRET` | **sim** | Segredo HMAC das chaves de ativação, mín. 16 caracteres. Gere com `openssl rand -base64 36` |

## Variáveis (aba *Variables*)

| Nome | Padrão | Descrição |
|---|---|---|
| `ALLOW_LEGACY_LICENSE` | `false` | `true` volta a aceitar chaves legadas (MD5 + salt fixo). Use só em migração temporária |

## Continuidade da chave de assinatura

O Android só atualiza um app por cima de outro se o certificado for o mesmo, e o updater do app também
valida o certificado. Antes de cadastrar a keystore:

1. Se existir o `.jks` original usado na release `v2.3.7`, use-o.
2. Se não existir, uma keystore nova funciona, mas quem já tem o app instalado precisa desinstalar e instalar de novo.
3. Guarde o `.jks` e as senhas em local seguro fora do repositório. Nunca faça commit de `.jks`, `secrets.properties` ou `.env`.

## Validar sem publicar (dry run)

```bash
gh workflow run release.yml --ref main -f tag=v2.8.0 -f dry_run=true
```

Para exigir build assinado no workflow de validação: *Actions → Validate → Run workflow* com `require_signed = true`.

## Publicar

```bash
git tag v2.8.0 && git push origin v2.8.0
```

## Licenciamento

- As chaves novas são HMAC-SHA256 do MAC virtual do dispositivo com `LICENSE_SECRET`.
- O painel admin (gerador de chaves) só abre em builds `debug`; em release ele não é acessível.
- Como o segredo fica dentro do APK, isso dificulta mas não impede um ataque por engenharia reversa.
  Para proteção forte, valide a licença em um servidor.
