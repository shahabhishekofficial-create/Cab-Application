# Cab Operations Management System

Production-oriented cab operations platform built from scratch.

## Stack
- Driver Android: Kotlin + Jetpack Compose + Room
- API: TypeScript + Node.js
- Database/Auth/Storage: Supabase + PostgreSQL
- Admin Web: Next.js + React
- GPS: Android Fused Location Provider
- Camera: CameraX
- OCR: modular ML Kit-ready verification layer

## Core flow
LOGIN → HOME → START SESSION → ODOMETER PHOTO → GPS → SESSION OPEN → ADD TRIP/FUEL/EXPENSE → CLOSE SESSION → ODOMETER PHOTO → GPS → SYNC → RECONCILIATION → SESSION CLOSED

## Rules
- Sessions are continuous work periods, not calendar-day records.
- Every transaction uses a client-generated UUID for idempotency.
- Revenue and expenses remain separate.
- Mobile is offline-first.
- Important corrections are audited; operational/financial data is not hard-deleted.
- Development/test data and production data are separated.
