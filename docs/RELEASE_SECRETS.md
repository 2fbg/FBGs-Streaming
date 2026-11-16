# Secrets, variáveis e checklist de release

Este guia descreve o que precisa estar configurado em **GitHub → Settings → Secrets and variables → Actions**
(no repositório `2fbg/FBGs-Streaming`, nível *Repository*, não *Environment*) para publicar um release assinado.

---

## Arquitetura de licenciamento (ECDSA P-256)

O sistema usa um par de chaves assimétricas P-256:

- **Chave privada (`LICENSE_SIGNING_KEY`)**: fica exclusivamente no servidor Vercel. Nunca toca o APK.
- **Chave pública (`LICENSE_PUBKEY_B64`)**: embutida no APK via `BuildConfig.LICENSE_PUBKEY_B64`. Permite verificação offline sem risco de falsificação de tokens, pois assinar requer a chave privada.

Formato do token: `MKL1.<payload_b64url>.<assinatura_der_b64url>`

---

## Pré-requisito: gerar o par de chaves P-256

Execute localmente (Linux, macOS ou WSL — requer OpenSSL 1.1+):

```bash
# 1. Gera a chave privada EC P-256
openssl ecparam -name prime256v1 -genkey -noout -out license-private.pem

# 2. Extrai a chave pública no formato PEM padrão
openssl ec -in license-private.pem -pubout -out license-public.pem

# 3. LICENSE_SIGNING_KEY (valor para a Vercel): PEM com \n escapados
#    Cole a saída diretamente como valor da variável de ambiente na Vercel.
awk 'NR>1 && /END/{printf "\\n"} {printf "%s%s", (NR>1 && !/END/ ? "\\n" : ""), $0} END{printf "\\n"}' license-private.pem

# 4. LICENSE_PUBKEY_B64 (valor para o GitHub Secret): DER da chave pública em Base64 numa linha
openssl ec -in license-private.pem -pubout -outform DER | base64 -w0
```

> ⚠️ **NUNCA** faça commit de `license-private.pem`. Adicione ao `.gitignore` e guarde em local seguro offline.

---

## Secrets (aba *Secrets*)

| Nome | Obrigatório | Descrição |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | sim | Keystore de upload em base64 numa linha só: `base64 -w0 upload.jks` |
| `STORE_PASSWORD` | sim | Senha do keystore |
| `KEY_PASSWORD` | não | Senha da chave (se vazia, usa `STORE_PASSWORD`) |
| `KEY_ALIAS` | não | Deve ser `upload` (é o alias lido por `app/build.gradle.kts`) |
| `EXPECTED_CERT_SHA256` | recomendado | SHA-256 do certificado (pin do updater). Se vazio, o release deriva do keystore |
| `LICENSE_SECRET` | **sim** | Segredo HMAC legado (mantido para compatibilidade interna), mín. 16 caracteres. Gere com `openssl rand -base64 36` |
| `LICENSE_PUBKEY_B64` | **sim** | Chave pública ECDSA P-256 em DER+Base64 (`openssl ec -in license-private.pem -pubout -outform DER \| base64 -w0`). Usada em `BuildConfig.LICENSE_PUBKEY_B64` para verificação offline de tokens MKL1. |

## Variáveis (aba *Variables*)

| Nome | Padrão | Descrição |
|---|---|---|
| `ALLOW_LEGACY_LICENSE` | `false` | `true` volta a aceitar chaves legadas (MD5 + salt fixo). Use só em migração temporária |

---

## Vercel (servidor de licenciamento)

Acesse: **Vercel → projeto `bgstreaming` → Settings → Environment Variables**

| Variável | Escopo | Descrição |
|---|---|---|
| `DATABASE_URL` | Production | String de conexão Neon Postgres (pooled, ex.: `postgresql://user:pass@ep-xxx.neon.tech/neondb?sslmode=require`) |
| `LICENSE_SIGNING_KEY` | Production | PEM da chave privada com `\n` escapados (saída do comando `awk` acima). **Nunca** ative em Preview/Development com a chave real — use uma chave separada de testes. |

---

## Banco de dados (Neon Postgres)

Execute `api/license/schema.sql` no console SQL do Neon antes do primeiro deploy:

```bash
# Via psql (substitua pela sua connection string):
psql "$DATABASE_URL" -f api/license/schema.sql
```

Ou cole o conteúdo diretamente no SQL Editor do console Neon.

---

## Continuidade da chave de assinatura Android

O Android só atualiza um app por cima de outro se o certificado for o mesmo, e o updater do app também
valida o certificado. Antes de cadastrar a keystore:

1. Se existir o `.jks` original usado na release `v2.3.7`, use-o.
2. Se não existir, uma keystore nova funciona, mas quem já tem o app instalado precisa desinstalar e instalar de novo.
3. Guarde o `.jks` e as senhas em local seguro fora do repositório. Nunca faça commit de `.jks`, `secrets.properties` ou `.env`.

---

## Validar sem publicar (dry run)

```bash
gh workflow run release.yml --ref main -f tag=v2.9.0 -f dry_run=true
```

Para exigir build assinado no workflow de validação: *Actions → Validate → Run workflow* com `require_signed = true`.

## Publicar

```bash
git tag v2.9.0 && git push origin v2.9.0
```

---

## Teste rápido da API de licença (pós-deploy Vercel)

```bash
# Ativar licença
curl -X POST https://bgstreaming.vercel.app/api/license/activate \
  -H "Content-Type: application/json" \
  -d '{"code":"TESTE-001","deviceId":"device-abc-123"}'

# Resposta esperada:
# {"token":"MKL1.<payload>.<assinatura>","tier":"pro","expiresAt":"..."}

# Renovar token
curl -X POST https://bgstreaming.vercel.app/api/license/refresh \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"device-abc-123"}'
```

---

## Trade-offs de segurança

| Ameaça | Mitigação atual | Mitigação adicional (opcional) |
|---|---|---|
| Falsificação de token offline | ✅ Chave privada nunca no APK | — |
| Reutilização de token em outro dispositivo | ✅ `deviceId` validado na assinatura | — |
| Token expirado | ✅ Campo `exp` verificado no servidor e no cliente | — |
| APK reempacotado sem checagem | ⚠️ Dificulta, mas não impede | Play Integrity API em `/activate` |
| Vazamento da chave pública | ✅ Apenas assimetria — sem risco (chave pública é pública) | — |
| Revogação em massa | ✅ Campo `revoked` na tabela `licenses` | — |
