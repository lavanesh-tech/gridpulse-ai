#!/usr/bin/env bash
# Verify, commit and push the current milestone using YOUR git identity.
# Usage: ./scripts/publish-milestone.sh "commit message"
set -euo pipefail
cd "$(dirname "$0")/.."
MSG="${1:?commit message required}"
REMOTE="https://github.com/lavanesh-tech/gridpulse-ai.git"

[ -f .env ] || cp .env.example .env
bash scripts/verify.sh 2>&1 | tee build-verify.log
grep -q "ALL CHECKS PASSED" build-verify.log || { echo "Verification failed - nothing committed."; exit 1; }
rm -f build-verify.log

echo "== Benchmark (seeded synthetic data) =="
./gradlew -q benchmark

[ -d .git ] || git init -b main
echo "Committing as: $(git config user.name) <$(git config user.email)>"
git add .
if git diff --cached --name-only | grep -E '(^|/)\.env$'; then echo ".env staged - aborting"; exit 1; fi
if [ -z "$(git diff --cached --name-only | grep -v '^benchmarks/results/')" ]; then
  echo "Only benchmark timings changed - nothing new to publish. Skipping commit."
  git reset -q
  exit 0
fi
git commit -m "$MSG"

git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REMOTE"
if command -v gh >/dev/null && ! gh repo view lavanesh-tech/gridpulse-ai >/dev/null 2>&1; then
  gh repo create lavanesh-tech/gridpulse-ai --public --description "Intelligent EV fleet charging coordination platform (Java 21, Spring Boot, Spring AI)"
fi
git push -u origin main
echo "== Pushed =="; git log --oneline -1; git status -sb | head -1
