# Cab Project — PROJECT_CONTEXT

## Purpose
Cab-Application is a driver cab-operations app for a Hyundai Aura CNG cab service. The system records driver/session identity, odometer/GPS evidence, trips, fuel, expenses, reconciliation and audit data, with an admin dashboard.

## Architecture
- Android driver app: Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Fused Location Provider, CameraX, ML Kit OCR.
- API: TypeScript + Node.js, deployed on Render.
- Database/backend: Supabase PostgreSQL, Auth, Storage, RPC/functions.
- Admin: Next.js web app.
- Routing: Google Routes API for road distance/duration; start/end GPS points retained for audit.
- Core flow: LOGIN -> HOME -> START SESSION -> ODOMETER PHOTO -> GPS -> SESSION OPEN -> ADD TRIP/FUEL/EXPENSE -> CLOSE SESSION -> ODOMETER PHOTO -> GPS -> SYNC -> RECONCILIATION -> SESSION CLOSED.

## Non-negotiable architecture/security decisions
- Server derives driver and vehicle identity from authenticated user; client must not supply trusted driverId/vehicleId for authorization.
- GPS is mandatory, target accuracy <=50m, with fresh timestamp.
- Odometer must never regress.
- Session close is blocked while an active/in-progress trip exists.
- Fuel amount must equal quantity * rate within Rs 0.01.
- Client UUIDs provide idempotency.
- No hard deletes; corrections remain auditable.
- Supabase migrations are append-only and uniquely numbered.
- Dependency ordering: SESSION_START -> TRIP_START -> TRIP_END/TRIP/FUEL/EXPENSE -> SESSION_CLOSE -> FILE_UPLOAD/attachment metadata.
- Offline-first: local Room save is immediate; network sync is attempted; WorkManager provides durable retry; failed records are never silently discarded.
- Active trip/session state must survive app kill/reopen.
- Test mode must avoid mandatory recurring paid services; testing and production data remain separate.

## Approved field classifications
Live schema was inventoried and classified. Driver-facing fields are limited to operationally useful data; admin-only/audit/internal fields stay off the driver UI. Approved addition: `driver_vehicle_assignments.assigned_from` is driver-facing and should be shown as “assigned since” on the driver profile.

## Live schema inventory
Base tables: app_users, drivers, vehicles, driver_vehicle_assignments, platforms, expense_categories, sessions, trips, fuel_transactions, expenses, reconciliations, exceptions, files, devices, ocr_verifications, vehicle_services, vehicle_documents, audit_logs.
Views: export_sessions, export_trips, export_fuel, export_expenses, export_reconciliation, export_exceptions.

Important fields include:
- app_users: id, role, display_name, phone, is_active, created_at, updated_at
- drivers: id, user_id, employee_code, license_number, license_expiry, created_at, updated_at
- vehicles: id, registration_number, make, model, variant, fuel_type, purchase_date, current_odometer, status, insurance_expiry, puc_expiry, permit_expiry, fitness_expiry, created_at, updated_at
- driver_vehicle_assignments: id, driver_id, vehicle_id, assigned_from, assigned_to, created_at
- sessions: id, client_transaction_id, driver_id, vehicle_id, device_id, session_date, status, started_at, start_odometer, start_lat, start_lng, start_accuracy_m, start_gps_at, start_odometer_file_id, closed_at, close_odometer, close_lat, close_lng, close_accuracy_m, close_gps_at, close_odometer_file_id, notes, created_at, updated_at, close_client_transaction_id
- trips: id, client_transaction_id, session_id, driver_id, vehicle_id, platform_id, started_at, ended_at, pickup, dropoff, start_odometer, end_odometer, gross_fare, payment_method, additional_charges, status, notes, created_at, updated_at, latitude, longitude, gps_accuracy_m, gps_at, end_latitude, end_longitude, end_gps_accuracy_m, end_gps_at, gps_distance_km, route_distance_km, route_duration_seconds, route_polyline, route_provider, route_status
- fuel_transactions: id, client_transaction_id, session_id, driver_id, vehicle_id, fuel_type, odometer, quantity, unit, rate, amount, expected_amount, payment_method, receipt_file_id, latitude, longitude, gps_accuracy_m, recorded_at, notes, created_at, updated_at
- expenses: id, client_transaction_id, session_id, driver_id, vehicle_id, category_id, amount, payment_method, proof_file_id, odometer, latitude, longitude, gps_accuracy_m, recorded_at, notes, created_at, updated_at
- reconciliations: id, session_id, reported_trip_count, reported_income, system_trip_count, system_income, trip_count_difference, income_difference, running_km, completed_trip_km, cancelled_no_show_km, unallocated_km, status, notes, created_at
- exceptions: id, entity_type, entity_id, code, severity, status, message, created_at, resolved_at, resolved_by
- files: id, bucket, object_path, mime_type, size_bytes, sha256, captured_at, created_by, created_at
- devices: id, user_id, device_key, model, os_version, app_version, last_seen_at, created_at
- ocr_verifications: id, session_id, context, manual_reading, ocr_reading, confidence, decision, raw_text, created_at
- vehicle_services: id, vehicle_id, service_date, odometer, service_type, workshop, invoice_number, total_amount, parts_amount, labour_amount, description, next_service_date, next_service_odometer, invoice_file_id, notes, created_at
- vehicle_documents: id, vehicle_id, document_type, document_number, issued_on, expires_on, file_id, created_at
- audit_logs: id, actor_user_id, entity_type, entity_id, action, old_data, new_data, reason, device_id, ip_address, created_at

## Resolved implementation issues
1. Sync retry policy reduced from 10 to 5 attempts; Room pending query excludes exhausted transactions, with explicit failed/exhausted counts.
2. SyncEngine uses a Mutex, cached token plus forced refresh, durable retry handling, and dependency priority ordering. Legacy SESSION_START file FK fields are stripped from business payloads.
3. SESSION_START no longer requires odometer file FK at transaction creation; file upload occurs first/later and attaches via `/v1/sessions/:sessionId/odometer-file`.
4. Server request schemas no longer trust client driverId/vehicleId. Legacy device identifiers are tolerated/sanitized.
5. `start_session` was made authoritative/idempotent on client transaction + session UUID, including returning an already-open matching session when exact session ID and driver/vehicle match.
6. Session close has local and live-server active-trip guards (`ACTIVE_TRIP_MUST_BE_ENDED`), plus odometer progression validation.
7. Odometer trigger enum comparison bug was fixed using text comparison (`new.status::text` / `old.status::text`).
8. Failed transaction recovery includes unsynced `TRANSACTION_FAILED` records even when attempts are exhausted.
9. Android startup sync scheduling was moved out of MainActivity lifecycle into DriverAppClean LaunchedEffect to prevent startup crash.
10. CameraX repeated binding during Compose recomposition was fixed by binding in AndroidView factory instead of update; SessionStart/SessionClose camera paths must remain verified.
11. Material 3 experimental TopAppBar usage was fixed with the appropriate opt-in.
12. Driver Home was redesigned dark-first with one accent, large operational numbers, primary CTAs, pending sync indicator, top-right overflow menu, and back handling. Root Home/Login uses double-back exit; in-app screens pop back to Home; forms/modals should dismiss appropriately.
13. Sync rejection errors now create/upsert server-side `exceptions` rows using idempotent uniqueness `(entity_type, entity_id, code)` so admin Needs Attention can surface sync failures.

## Important commits / migrations
- `b243193b...`: retry max 10 -> 5.
- `0eae696...`: Room pending/exhausted handling.
- `d22c798...`: SyncEngine retry/auth/mutex.
- `0600f632...`: durable WorkManager fallback/scheduling.
- `d3d366643...`: legacy SESSION_START file field stripping.
- `dc610e27...`: remove client driver/vehicle identity from schemas.
- `c301789...`, `b568d015...`: legacy device identifier handling.
- `90c7db08...`: migration 036 authoritative session start RPC.
- `a3b3fd02...`: local active-trip close guard.
- `17a6a26...`: migration 037 mirrored in repo; Render deployment at that point.
- `ee8bbfa...`: migration 038 odometer enum trigger fix.
- `85ba2f80...`: failed transaction recovery.
- `ab75cde6...`: idempotent open-session matching fix.
- `96bbf363...`: Android startup sync crash fix.
- `8636b074d796f744a047ab5905da8169478a63d5`: latest known CI-green driver navigation/Material3 fix.
- Live migrations added through 041; 039 admin dashboard metrics, 040 repo numbering for dashboard migration, 041 sync exception uniqueness.

## Admin dashboard (Task 5)
Live Supabase RPC: `public.get_admin_dashboard()` (security definer, search_path public, service_role execute) returns today KPIs/trends, driver leaderboard, 30-day fuel efficiency, 7-day session timeline, today expense breakdown, unresolved exceptions, and generatedAt. API route GET `/v1/admin/dashboard` is admin-protected. Admin UI has KPI cards, leaderboard, Needs Attention, fuel efficiency drill-in, session timeline, expense breakdown, and live refresh. Known follow-up candidates: clickable expense filter, true common-time-axis Gantt, and exact historical active-session trend.

## Render / Supabase
- Render API service: `srv-dahgrop5efls73bshrhg`; workspace `tea-dahgkqijnfac738rvlag`.
- Render URL: `https://cab-application-z4ow.onrender.com`.
- Auto deploy is enabled from `main`.
- Supabase project ref: `msjkwsrwzqtuqupirpym`.
- Live assignment observed: driver `117674b6-7ec1-4943-9989-3dc297e7bda5` -> vehicle `d39c52a4-2e74-4ced-9f28-ea9d593f01b5`, ACTIVE.
- Earlier live audit found RLS disabled on platforms, expense_categories, devices, files, reconciliations, exceptions, vehicle_services, vehicle_documents, audit_logs. Do not blindly enable RLS without policies.

## Latest verified build
CI run `34772381231` / run 356, head `8636b074d796f744a047ab5905da8169478a63d5` was CI-green with Android APK artifact id `10322217720`, previously extracted to `/mnt/data/cab_apk_extract/app-debug.apk` and delivered directly.

## Login Screen Punch List — 2026-09-13
Implemented dark-first theme, combined CAB/Cab Driver lockup, sans typography, accessible field colors, generic placeholder, dynamic version, accent offline banner, visible errors, and success-path busy reset. Google OAuth app-side wiring is verified; provider-side credentials/SHA/redirect settings are not inspectable through the available Supabase management surface. Live schema confirms `app_users.id` is auth-user UUID PK and `drivers.user_id` is unique; `app_users` has no email column, so dedupe is by auth identity.

## Session-wide Screen Punch List — 2026-09-13/14
- Removed obsolete `DriverApp.kt`, which was declaring duplicate `CabText`/`CabAccent` symbols and caused the login-theme CI build to fail. The active entrypoint is `DriverAppClean` from `MainActivity`.
- Permissions screen now uses the shared dark/sans UI and separately shows CAMERA and LOCATION as `GRANTED` or `NEEDED`, refreshing on resume so returning from App Settings reflects actual state.
- Session start no longer displays the raw vehicle/session UUID; subtitle is human-readable “Ready to work”. Cards use the shared neutral surface system and step badges use the established neon accent. Low-light black camera preview was recorded as a false alarm; no camera regression fix is being added for that report.
- Login now has a 15-second sign-in timeout and classifies timeout/network/credential/account-not-found failures into visible retryable messages; indefinite `Signing in…` is no longer possible from a hanging auth request.
- Added explicit `ENABLE_TEST_SYNC_DEPENDENCY_BYPASS`: true only in debug, false in release. A debug SESSION_CLOSE failure does not prevent later test records from being attempted; release retains strict dependency semantics.
- SyncScheduler durable WorkManager jobs are now eligible immediately (no artificial 15-second initial delay), retain network constraints, unique-work KEEP semantics, exponential backoff, and a `cab-sync` tag. SyncEngine now logs queue start, each transaction attempt/result, auth refresh, and completion under `CabSync` for real device evidence.
- Added driver Sync status/history screen showing actual Room pending/failed counts and actual WorkManager unique-work state. Overflow menu items now navigate to Settings, Sync status/history, Help & support, About/version, and Logout; logout clears local identity/session state and returns to login.
- Live Supabase audit immediately before this task: sessions=1, trips=0, fuel=0, expenses=0, devices=0, open exceptions=0. No new DB schema change was required for these UI/sync fixes.
- CI run `34775227867` / run 371 is the validation run for the above changes; migrations and API/admin jobs passed while Android build was still in progress at context update time. Do not call the APK ready until Android build/tests/artifact upload are green.
