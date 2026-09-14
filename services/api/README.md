# Cab Application API

Backend service for the cab operations platform.

## Current endpoints

- `GET /health` — service health check
- `POST /v1/sync/validate` — validates the mobile transaction envelope

## QA/test reset

The repository includes a deliberately manual reset command for the dedicated test driver and vehicle (`GJ01NT0088`). It is **not** an API endpoint and is not scheduled or automatic.

Requirements:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `--since=<ISO timestamp>` — only sessions created on/after this boundary are eligible
- `--confirm-reset` — explicit confirmation

Run from `services/api`:

`npm run test:reset -- --since=2026-09-15T00:00:00Z --confirm-reset`

The database function independently validates both the driver/vehicle assignment and the exact `GJ01NT0088` registration before doing anything. It records an audit entry for every deleted session, removes session-linked trips/fuel/expenses/attachments and returns storage object references; the script then removes those storage objects. No other vehicle, driver, or session is targeted.

This reset facility is **QA-only** and must not be exposed as a production driver feature. Keep it for a future staging/QA environment or remove it before production release.
