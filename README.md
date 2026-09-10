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
The driver app uses Supabase Auth email/password plus Google OAuth. The API validates the Supabase access token and derives the driver and active vehicle assignment server-side. Driver/vehicle headers are not trusted for identity.

The mobile app refreshes an expiring access token before offline queue synchronization. A temporary API/network failure does not erase a previously cached driver/vehicle assignment; locally captured operational data remains available for later synchronization.

## Android local configuration
Create `apps/driver-android/gradle.properties` locally (do not commit it):

```properties
API_BASE_URL=http://10.0.2.2:3000
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

`SUPABASE_ANON_KEY` is still accepted as a backward-compatible Gradle property name, but new setups should use `SUPABASE_PUBLISHABLE_KEY`.

For a physical Android phone, replace `10.0.2.2` with the LAN-reachable API URL of the development machine. Never put the Supabase secret/service-role key in the Android app.

## Admin Web local configuration
Create `apps/admin-web/.env.local` locally (do not commit it):

```properties
NEXT_PUBLIC_API_BASE_URL=http://localhost:3000
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

`NEXT_PUBLIC_SUPABASE_ANON_KEY` remains accepted as a backward-compatible browser variable. Never use a Supabase secret/service-role key in the browser.

## Supabase setup
1. Create separate development/test and production Supabase projects.
2. Apply migrations in `database/migrations` in numeric filename order.
3. Do not edit migrations that have already been applied; add a new migration for corrections.
4. `009_driver_context_compatibility.sql` removes both obsolete and current driver-context function signatures before recreating the UUID-based function. This makes upgrades from earlier revisions deterministic.
5. Enable Email/Password authentication and configure Google OAuth for the Android deep-link callback documented by the app configuration.
6. Create the driver Auth user in Supabase Auth.
7. Create the matching `app_users` row using the Auth user's UUID as `app_users.id`.
8. Create the matching `drivers` row and an active `driver_vehicle_assignments` row.
9. Keep the production service-role key only on the API server.

## Rules
- Sessions are continuous work periods, not calendar-day records.
- Every transaction uses a client-generated UUID for idempotency.
- Revenue and expenses remain separate.
- Mobile is offline-first.
- Important corrections are audited; operational/financial data is not hard-deleted.
- Development/test data and production data are separated.
- API identity is derived from the authenticated user, never from client-supplied driver/vehicle identity.

## CI
GitHub Actions checks migration numbering, builds/tests the API, builds the admin web app, builds the Android debug APK, and runs Android unit tests on pushes and pull requests to `main`. Android CI reads `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from GitHub Actions secrets; no credential is committed to the repository.

For local Android verification from `apps/driver-android`, use Gradle 8.11.1 with Java 17 and run `assembleDebug` followed by `testDebugUnitTest`.
