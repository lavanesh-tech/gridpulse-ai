#!/usr/bin/env bash
# Starts the app with OpenAI enabled, embeds the sample documents with OpenAI, measures retrieval
# (hit@3, MRR) and asks one real question. Saves benchmarks/results/retrieval-openai.json. Stops the app at the end.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
[ "${SPRING_AI_MODEL_EMBEDDING:-none}" = "openai" ] || { echo "Run ./scripts/setup-openai.sh first."; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker is not running. Open Docker Desktop and re-run."; exit 1; }
docker compose up -d --wait
PORT=18080   # separate port so it never collides with anything else you run on 8080
BASE="http://localhost:$PORT"
lsof -ti tcp:$PORT >/dev/null 2>&1 && { echo "Port $PORT is busy - stop whatever uses it and re-run."; exit 1; }
mkdir -p build
./gradlew -q bootRun --args="--server.port=$PORT" > build/app-run.log 2>&1 &
APP=$!
trap 'kill $APP 2>/dev/null || true; lsof -ti tcp:'"$PORT"' | xargs kill 2>/dev/null || true' EXIT
echo "Starting the app on port $PORT (log: build/app-run.log)..."
J() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
LOGIN="{\"username\":\"admin\",\"password\":\"$GRIDPULSE_ADMIN_PASSWORD\"}"
TOKEN=""
for i in $(seq 1 120); do
  kill -0 $APP 2>/dev/null || { echo "App stopped during startup:"; tail -40 build/app-run.log; exit 1; }
  CODE=$(curl -s -o build/login.json -w '%{http_code}' -X POST "$BASE/api/v1/auth/token" -H 'Content-Type: application/json' -d "$LOGIN" || true)
  if [ "$CODE" = "200" ]; then TOKEN=$(J "d['accessToken']" < build/login.json); break; fi
  if [ "$CODE" = "401" ]; then echo "App is up but admin login was rejected (an older admin may exist in the local database)."; exit 1; fi
  sleep 2
done
rm -f build/login.json
[ -n "$TOKEN" ] || { echo "App did not become ready in 4 minutes:"; tail -40 build/app-run.log; exit 1; }
H=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
echo "== Status =="; curl -sf "${H[@]}" $BASE/api/v1/advisor/status; echo
echo "== Embedding sample documents with OpenAI =="
curl -sf -X POST "${H[@]}" $BASE/api/v1/advisor/documents/samples | J "'\n'.join(f\"{r['title']}: {r['chunks']} chunks ({r['embeddingModel']})\" for r in d)"
echo "== Retrieval evaluation =="
mkdir -p benchmarks/results
curl -sf -X POST "${H[@]}" $BASE/api/v1/advisor/evaluate -d '{"k":3}' > benchmarks/results/retrieval-openai.json
python3 -m json.tool benchmarks/results/retrieval-openai.json | head -20
echo "== Sample question =="
curl -sf -X POST "${H[@]}" $BASE/api/v1/advisor/ask -d '{"question":"What should a depot operator do when a charger goes offline overnight?"}' | python3 -m json.tool | head -40
echo "EVALUATION DONE"
