# Cab Operations Management System

Production-oriented cab operations platform built from scratch.

## Stack
- Driver Android: Kotlin + Jetpack Compose + Room
- API: TypeScript + Node.js
- Database/Auth/Storage: Supabase + PostgreSQL
- Admin Web: Next.js + React
- GPS: Android Fused Location Provider
- Camera: CameraX
- OCR: ML Kit verification layer

## Core flow
LOGIN → HOME → START SESSION → ODOMETER PHOTO → GPS → SESSION OPEN → ADD TRIP/FUEL/EXPENSE → CLOSE SESSION → ODOMETER PHOTO → GPS → SYNC → RECONCILIATION → SESSION CLOSED

## Authentication
The driver app uses Supabase Auth email/password login. The API validates the Supabase access token and derives the driver and active vehicle assignment server-side. Driver/vehicle headers are not trusted for identity.

## Android local configuration
Create `apps/driver-android/gradle.properties` locally (do not commit it):

```properties
API_BASE_URL=http://10.0.2.2:3000
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

For a physical Android phone, replace `10.0.2.2` with the LAN-reachable API URL of the development machine. Never put the Supabase service-role key in the Android app.

## Supabase setup
1. Create separate development/test and production Supabase projects.
2. Apply migrations in `database/migrations` in order.
3. Enable Email/Password authentication.
4. Create the driver Auth user in Supabase Auth.
5. Create the matching `app_users` row using the Auth user's UUID as `app_users.id`.
6. Create the matching `drivers` row and an active `driver_vehicle_assignments` row.
7. Keep the production service-role key only on the API server.

## Rules
- Sessions are continuous work periods, not calendar-day records.
- Every transaction uses a client-generated UUID for idempotency.
- Revenue and expenses remain separate.
- Mobile is offline-first.
- Important corrections are audited; operational/financial data is not hard-deleted.
- Development/test data and production data are separated.
- API identity is derived from the authenticated user, never from client-supplied driver/vehicle identity.

## CI
GitHub Actions builds/tests the API and builds the admin web app on pushes and pull requests to `main`.
