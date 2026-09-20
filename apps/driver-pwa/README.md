# Cab Ops Driver PWA

Offline-first web driver client for the existing Cab-Application API/Supabase backend.

## Environment
- VITE_API_BASE_URL: existing Render API URL
- VITE_SUPABASE_URL: existing Supabase project URL
- VITE_SUPABASE_ANON_KEY: existing Supabase public anon key

Never put the Supabase service-role key in this app.

## Run
npm install
npm run dev

## Current scope
Authentication restoration, cached driver context, offline transaction queue, session start/close, trip capture, service-worker caching and installable PWA shell. Server-side API remains authoritative for driver/vehicle authorization, GPS, odometer, session and financial validation.
