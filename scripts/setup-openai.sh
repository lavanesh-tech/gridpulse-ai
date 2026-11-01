#!/usr/bin/env bash
# Adds your OpenAI key to .env (git-ignored) and switches the advisor to OpenAI models.
# The key is typed hidden and never printed. Usage: ./scripts/setup-openai.sh
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || cp .env.example .env
git check-ignore -q .env || { echo ".env is not git-ignored - aborting"; exit 1; }

printf "Paste your OpenAI API key (hidden), then press Enter: "
read -rs KEY; echo
KEY="$(printf '%s' "$KEY" | tr -d '[:space:]')"
case "$KEY" in sk-*) ;; *) echo "That does not look like an OpenAI key (should start with sk-). Nothing changed."; exit 1;; esac

echo "Checking the key with OpenAI..."
CODE=$(curl -s -o /dev/null -w '%{http_code}' https://api.openai.com/v1/models -H "Authorization: Bearer $KEY" || true)
[ "$CODE" = "200" ] || { echo "OpenAI rejected the key (HTTP $CODE). Nothing changed."; exit 1; }
echo "Key works."

get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }
JWT="$(get GRIDPULSE_JWT_SECRET)"; ADMIN="$(get GRIDPULSE_ADMIN_PASSWORD)"
if [ ${#JWT} -lt 32 ] || [ "$JWT" = "replace-with-output-of-openssl-rand-base64-48" ]; then JWT="$(openssl rand -base64 48 | tr -d '\n')"; fi
[ -n "$ADMIN" ] || ADMIN="$(openssl rand -base64 18 | tr -d '/+=\n')"

TMP="$(mktemp)"
grep -vE '^(OPENAI_API_KEY|SPRING_AI_MODEL_CHAT|SPRING_AI_MODEL_EMBEDDING|GRIDPULSE_JWT_SECRET|GRIDPULSE_ADMIN_PASSWORD)=' .env > "$TMP" || true
{ cat "$TMP"
  echo "GRIDPULSE_JWT_SECRET=$JWT"
  echo "GRIDPULSE_ADMIN_PASSWORD=$ADMIN"
  echo "OPENAI_API_KEY=$KEY"
  echo "SPRING_AI_MODEL_CHAT=openai"
  echo "SPRING_AI_MODEL_EMBEDDING=openai"; } > .env
rm -f "$TMP"; chmod 600 .env
echo "Saved to .env (only readable by you, never committed)."
echo "Demo login: admin / $ADMIN   (also stored in .env)"
echo "Next: ./scripts/evaluate-openai.sh"
