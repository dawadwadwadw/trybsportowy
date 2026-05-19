# trybsportowy

Android readiness tracker with best-effort sync to the companion server
(see `CLAUDE.md` for the operating contract, `docs/phases/` for build phases).

## Server sync end-to-end smoke test

From the repo root, with the Android SDK installed and a device/emulator on the
tailnet (the Bearer secret is read from the environment, never committed):

```bash
ANDROID_API_SECRET=<secret> bash scripts/smoke-test.sh
```

Every line should print `OK`. Standalone guards:
`bash scripts/verify-no-secrets.sh` and `bash scripts/verify-no-polish-literals.sh`.
