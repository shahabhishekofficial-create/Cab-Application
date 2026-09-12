-- Trip lifecycle: start now, end later. This removes the need for drivers to remember start odometer/time.
ALTER TYPE trip_status ADD VALUE IF NOT EXISTS 'IN_PROGRESS';

ALTER TABLE public.trips
  ADD COLUMN IF NOT EXISTS end_latitude numeric(10,7),
  ADD COLUMN IF NOT EXISTS end_longitude numeric(10,7),
  ADD COLUMN IF NOT EXISTS end_gps_accuracy_m numeric(8,2),
  ADD COLUMN IF NOT EXISTS end_gps_at timestamptz;

-- The authoritative RPCs are applied to production by the matching Supabase migration.
