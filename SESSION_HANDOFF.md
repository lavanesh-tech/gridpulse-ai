# Session Handoff

Read in this order to resume: `PROJECT_CONTEXT.md` → `ROADMAP.md` → `ARCHITECTURE.md` → `DECISIONS.md`.

## Working agreement
- The author (repo owner) runs builds/tests locally and makes **all** commits and pushes from their own
  Git identity. No co-author trailers, no other identities, no history rewriting.
- The assistant writes code/docs, then provides a verify command and a suggested commit message.
- Never mark anything as done without verified output. Never invent metrics.
- Never commit secrets; config comes from environment variables (`.env` is git-ignored).

## Resume point
See "Next steps" in PROJECT_CONTEXT.md.
