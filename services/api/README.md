# Cab Application API

Backend service for the cab operations platform.

## Current endpoints

- `GET /health` — service health check
- `POST /v1/sync/validate` — validates the mobile transaction envelope

The API is intentionally small at this stage; domain endpoints will be added behind server-side validation and Supabase persistence.
