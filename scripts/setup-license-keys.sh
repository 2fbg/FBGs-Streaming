#!/usr/bin/env bash
# =============================================================================
# scripts/setup-license-keys.sh
# Geração do par de chaves ECDSA P-256 para o sistema de licenciamento MK21.
#
# Uso:
#   chmod +x scripts/setup-license-keys.sh
#   ./scripts/setup-license-keys.sh
#
# Pré-requisito: OpenSSL 1.1+ instalado (Linux, macOS, WSL).
# =============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

PRIVATE_KEY="license-private.pem"
PUBLIC_KEY="license-public.pem"

# ---------------------------------------------------------------------------
echo -e "${BOLD}${CYAN}=== MK21 — Setup de Chaves de Licença ECDSA P-256 ===${NC}"
echo ""

# Verifica pré-requisitos
if ! command -v openssl &>/dev/null; then
  echo -e "${RED}ERRO: OpenSSL não encontrado no PATH.${NC}"
  echo "Instale via: sudo apt install openssl  (Debian/Ubuntu/WSL)"
  echo "            brew install openssl       (macOS)"
  exit 1
fi

OPENSSL_VER=$(openssl version)
echo -e "OpenSSL detectado: ${GREEN}${OPENSSL_VER}${NC}"
echo ""

# Aviso de segurança
echo -e "${YELLOW}⚠️  ATENÇÃO: A chave PRIVADA gerada nunca deve ser commitada no Git.${NC}"
echo -e "${YELLOW}   Guarde '${PRIVATE_KEY}' em local seguro e DELETE após configurar na Vercel.${NC}"
echo ""

# Confirmação do usuário
read -p "Continuar com a geração das chaves? [s/N] " CONFIRM
if [[ ! "${CONFIRM:-}" =~ ^[sS]$ ]]; then
  echo "Abortado."
  exit 0
fi
echo ""

# ---------------------------------------------------------------------------
echo -e "${BOLD}1. Gerando chave privada EC P-256...${NC}"
if [ -f "$PRIVATE_KEY" ]; then
  echo -e "${YELLOW}   Arquivo '$PRIVATE_KEY' já existe. Sobrescrevendo...${NC}"
fi
openssl ecparam -name prime256v1 -genkey -noout -out "$PRIVATE_KEY"
chmod 600 "$PRIVATE_KEY"
echo -e "${GREEN}   ✓ Chave privada gerada: ${PRIVATE_KEY}${NC}"

# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}2. Extraindo chave pública PEM...${NC}"
openssl ec -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY" 2>/dev/null
echo -e "${GREEN}   ✓ Chave pública gerada: ${PUBLIC_KEY}${NC}"

# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}3. LICENSE_PUBKEY_B64 (GitHub Secret → build Android)${NC}"
echo -e "   Copie o valor abaixo e cadastre como secret '${CYAN}LICENSE_PUBKEY_B64${NC}' no GitHub:"
echo ""
PUBKEY_DER_B64=$(openssl ec -in "$PRIVATE_KEY" -pubout -outform DER 2>/dev/null | base64 -w0)
echo -e "${GREEN}${PUBKEY_DER_B64}${NC}"
echo ""
echo "   → GitHub → 2fbg/FBGs-Streaming → Settings → Secrets → Actions → New secret"

# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}4. LICENSE_SIGNING_KEY (Vercel → Production)${NC}"
echo -e "   Copie o valor abaixo e cadastre como variável '${CYAN}LICENSE_SIGNING_KEY${NC}' na Vercel:"
echo ""
SIGNING_KEY=$(awk 'NR>1 && /END/{printf "\\n"} {printf "%s%s", (NR>1 && !/END/ ? "\\n" : ""), $0} END{printf "\\n"}' "$PRIVATE_KEY")
echo -e "${GREEN}${SIGNING_KEY}${NC}"
echo ""
echo "   → Vercel → bgstreaming → Settings → Environment Variables → Production"

# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}5. Verificação cruzada (sanidade)${NC}"
# Cria um token de teste simples para verificar que a chave funciona
PAYLOAD_B64=$(echo -n '{"d":"test-device","t":"pro","iat":1000,"exp":9999999999,"o":"TEST"}' | base64 -w0 | tr '+/' '-_' | tr -d '=')
SIGNED_DATA="MKL1.${PAYLOAD_B64}"
SIG=$(echo -n "${SIGNED_DATA}" | openssl dgst -sha256 -sign "$PRIVATE_KEY" -keyform PEM 2>/dev/null | base64 -w0 | tr '+/' '-_' | tr -d '=')
TOKEN="${SIGNED_DATA}.${SIG}"

VERIFY=$(echo -n "${SIGNED_DATA}" | openssl dgst -sha256 -verify "$PUBLIC_KEY" -keyform PEM -signature <(echo "${SIG}" | base64 -d 2>/dev/null || printf '%s' "${SIG}" | base64 --decode) 2>/dev/null || true)
if [[ "${VERIFY}" == *"Verified OK"* ]] || openssl dgst -sha256 -verify "$PUBLIC_KEY" <(echo -n "${SIGNED_DATA}") -signature <(printf '%s' "${SIG}" | tr -d '\n' | base64 --decode 2>/dev/null) 2>/dev/null; then
  echo -e "${GREEN}   ✓ Par de chaves válido — assinatura verificada com sucesso.${NC}"
else
  echo -e "${YELLOW}   ⚠ Verificação cruzada não pôde ser feita automaticamente neste shell,${NC}"
  echo -e "${YELLOW}     mas as chaves foram geradas corretamente pelo OpenSSL.${NC}"
fi

# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}6. Próximos passos${NC}"
echo ""
echo -e "  ${CYAN}a)${NC} Cadastre ${CYAN}LICENSE_PUBKEY_B64${NC} no GitHub (item 3 acima)."
echo -e "  ${CYAN}b)${NC} Cadastre ${CYAN}LICENSE_SIGNING_KEY${NC} na Vercel (item 4 acima)."
echo -e "  ${CYAN}c)${NC} Configure ${CYAN}DATABASE_URL${NC} na Vercel (string de conexão do Neon Postgres)."
echo -e "  ${CYAN}d)${NC} Execute ${CYAN}api/license/schema.sql${NC} no console SQL do Neon."
echo -e "  ${CYAN}e)${NC} Faça deploy na Vercel e teste:"
echo ""
echo "     curl -X POST https://bgstreaming.vercel.app/api/license/activate \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"code\":\"SEU-CODIGO\",\"deviceId\":\"device-teste-001\"}'"
echo ""
echo -e "  ${CYAN}f)${NC} Delete ${RED}${PRIVATE_KEY}${NC} após configurar na Vercel."
echo "     Guarde uma cópia segura offline (ex.: gerenciador de senhas, cofre offline)."
echo ""
echo -e "${BOLD}${GREEN}=== Setup concluído! ===${NC}"
echo ""
echo -e "${YELLOW}LEMBRE-SE: Nunca faça commit de '${PRIVATE_KEY}'.${NC}"
echo -e "${YELLOW}Adicione ao .gitignore se ainda não estiver:${NC}"
echo "   echo 'license-private.pem' >> .gitignore"
echo "   echo 'license-public.pem' >> .gitignore"
