#!/usr/bin/env bash
# Load test with k6 (runs in Docker, nothing to install) against a throwaway stack.
# Usage: ./scripts/load-test.sh            (60 s at 50 reads/s + 5 plans/s)
#        DURATION=120s READ_RPS=100 PLAN_RPS=10 ./scripts/load-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib-throwaway.sh
trap stop_stack EXIT
# One synthetic admin drives all traffic, so the per-user rate limit is raised for this run only.
start_stack 5435 18082 --gridpulse.security.requests-per-minute=1000000
mkdir -p benchmarks/results
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "$PWD/loadtest:/scripts:ro" -v "$PWD/benchmarks/results:/out" \
  -e BASE="http://host.docker.internal:18082" -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  -e DURATION="${DURATION:-60s}" -e READ_RPS="${READ_RPS:-50}" -e PLAN_RPS="${PLAN_RPS:-5}" \
  grafana/k6:1.0.0 run --quiet /scripts/k6-api.js
echo "Machine: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), $(sysctl -n hw.ncpu 2>/dev/null || nproc) cores"
echo "LOAD TEST DONE (results: benchmarks/results/load-test-latest.json)"
