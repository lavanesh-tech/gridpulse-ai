# Shared by e2e.sh and load-test.sh: a throwaway pgvector Postgres + the API, isolated from your normal data.
# Usage: start_stack <db-port> <app-port> [extra spring args]; sets BASE and ADMIN_PASSWORD; stop_stack in a trap.
start_stack() {
  DB_PORT=$1; APP_PORT=$2; shift 2
  docker info >/dev/null 2>&1 || { echo "Docker is not running. Open Docker Desktop and re-run."; exit 1; }
  for p in $DB_PORT $APP_PORT; do
    lsof -ti tcp:$p >/dev/null 2>&1 && { echo "Port $p is busy - stop whatever uses it and re-run."; exit 1; }
  done
  DB_NAME="gridpulse-throwaway-$APP_PORT"
  docker rm -f "$DB_NAME" >/dev/null 2>&1 || true
  docker run -d --rm --name "$DB_NAME" -e POSTGRES_DB=gridpulse -e POSTGRES_USER=gridpulse -e POSTGRES_PASSWORD=throwaway \
    -p "$DB_PORT:5432" pgvector/pgvector:pg16 >/dev/null
  until docker exec "$DB_NAME" pg_isready -U gridpulse -d gridpulse >/dev/null 2>&1; do sleep 1; done
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=\n')"
  BASE="http://localhost:$APP_PORT"
  mkdir -p build
  # Only throwaway settings - .env (and your OpenAI key) is deliberately not loaded, so results are reproducible and free.
  env -i PATH="$PATH" HOME="$HOME" JAVA_HOME="${JAVA_HOME:-}" \
    POSTGRES_PORT=$DB_PORT POSTGRES_DB=gridpulse POSTGRES_USER=gridpulse POSTGRES_PASSWORD=throwaway \
    GRIDPULSE_JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')" GRIDPULSE_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    SPRING_AI_MODEL_CHAT=none SPRING_AI_MODEL_EMBEDDING=none \
    ./gradlew -q bootRun --args="--server.port=$APP_PORT $*" > "build/app-$APP_PORT.log" 2>&1 &
  APP_PID=$!
  echo "Starting a throwaway GridPulse on port $APP_PORT (log: build/app-$APP_PORT.log)..."
  for i in $(seq 1 120); do
    kill -0 $APP_PID 2>/dev/null || { echo "App stopped during startup:"; tail -40 "build/app-$APP_PORT.log"; exit 1; }
    curl -sf "$BASE/actuator/health/readiness" >/dev/null 2>&1 && { echo "Ready."; return 0; }
    sleep 2
  done
  echo "App did not become ready in 4 minutes:"; tail -40 "build/app-$APP_PORT.log"; exit 1
}

stop_stack() {
  [ -n "${APP_PID:-}" ] && kill "$APP_PID" 2>/dev/null || true
  [ -n "${APP_PORT:-}" ] && { lsof -ti tcp:"$APP_PORT" | xargs kill 2>/dev/null || true; }
  [ -n "${DB_NAME:-}" ] && docker rm -f "$DB_NAME" >/dev/null 2>&1 || true
}
