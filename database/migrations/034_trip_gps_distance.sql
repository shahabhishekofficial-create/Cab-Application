-- Store the GPS start-to-end distance for completed trips.
-- This is straight-line (great-circle) distance, not road-route distance.
ALTER TABLE public.trips
  ADD COLUMN IF NOT EXISTS gps_distance_km numeric(12,3);

CREATE OR REPLACE FUNCTION public.calculate_gps_distance_km(
  p_start_lat numeric,
  p_start_lng numeric,
  p_end_lat numeric,
  p_end_lng numeric
) RETURNS numeric
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $function$
declare
  v_lat1 double precision := radians(p_start_lat::double precision);
  v_lat2 double precision := radians(p_end_lat::double precision);
  v_dlat double precision := radians((p_end_lat-p_start_lat)::double precision);
  v_dlng double precision := radians((p_end_lng-p_start_lng)::double precision);
  v_a double precision;
begin
  v_a := sin(v_dlat/2)^2 + cos(v_lat1) * cos(v_lat2) * sin(v_dlng/2)^2;
  return round((6371.0088 * 2 * atan2(sqrt(v_a), sqrt(greatest(0.0, 1.0-v_a))))::numeric, 3);
end;
$function$;

-- Backfill any already-completed trips that have both GPS endpoints.
UPDATE public.trips
SET gps_distance_km = public.calculate_gps_distance_km(latitude, longitude, end_latitude, end_longitude)
WHERE gps_distance_km IS NULL
  AND latitude IS NOT NULL AND longitude IS NOT NULL
  AND end_latitude IS NOT NULL AND end_longitude IS NOT NULL;

-- Calculate the distance atomically when a trip is ended. The server never trusts
-- a client-supplied distance.
CREATE OR REPLACE FUNCTION public.end_trip(
  p_client_transaction_id uuid,
  p_session_id uuid,
  p_driver_id uuid,
  p_vehicle_id uuid,
  p_ended_at timestamptz,
  p_end_odometer numeric,
  p_gross_fare numeric,
  p_payment_method payment_method,
  p_additional_charges numeric,
  p_status trip_status,
  p_latitude numeric,
  p_longitude numeric,
  p_gps_accuracy_m numeric,
  p_gps_at timestamptz,
  p_pickup text,
  p_dropoff text,
  p_platform_id uuid,
  p_notes text
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare v_trip trips%rowtype;
begin
  select * into v_trip from trips where client_transaction_id=p_client_transaction_id for update;
  if not found then raise exception 'TRIP_NOT_FOUND'; end if;
  if v_trip.session_id<>p_session_id or v_trip.driver_id<>p_driver_id or v_trip.vehicle_id<>p_vehicle_id then raise exception 'TRIP_IDENTITY_MISMATCH'; end if;
  if v_trip.status<>'IN_PROGRESS' then return jsonb_build_object('id',v_trip.id,'status','IDEMPOTENT'); end if;
  if not exists(select 1 from sessions where id=p_session_id and status='OPEN') then raise exception 'SESSION_NOT_OPEN'; end if;
  if p_ended_at<v_trip.started_at then raise exception 'TRIP_END_TIME_BEFORE_START'; end if;
  if p_end_odometer<v_trip.start_odometer then raise exception 'ODOMETER_REGRESSION'; end if;
  if p_latitude is null or p_longitude is null or p_gps_accuracy_m is null or p_gps_at is null then raise exception 'GPS_REQUIRED'; end if;
  if p_latitude<-90 or p_latitude>90 or p_longitude<-180 or p_longitude>180 then raise exception 'GPS_INVALID_COORDINATES'; end if;
  if p_gps_accuracy_m<0 or p_gps_accuracy_m>50 then raise exception 'GPS_ACCURACY_TOO_LOW'; end if;
  if p_gps_at<p_ended_at-interval '2 minutes' or p_gps_at>p_ended_at+interval '2 minutes' then raise exception 'GPS_STALE'; end if;
  update trips
  set ended_at=p_ended_at,
      end_odometer=p_end_odometer,
      gross_fare=p_gross_fare,
      payment_method=p_payment_method,
      additional_charges=p_additional_charges,
      status=p_status,
      end_latitude=p_latitude,
      end_longitude=p_longitude,
      end_gps_accuracy_m=p_gps_accuracy_m,
      end_gps_at=p_gps_at,
      gps_distance_km=public.calculate_gps_distance_km(v_trip.latitude,v_trip.longitude,p_latitude,p_longitude),
      pickup=coalesce(p_pickup,pickup),
      dropoff=coalesce(p_dropoff,dropoff),
      platform_id=coalesce(p_platform_id,platform_id),
      notes=coalesce(p_notes,notes),
      updated_at=now()
  where id=v_trip.id
  returning * into v_trip;
  return jsonb_build_object('id',v_trip.id,'status','ENDED','gpsDistanceKm',v_trip.gps_distance_km);
end;
$function$;

REVOKE EXECUTE ON FUNCTION public.calculate_gps_distance_km(numeric,numeric,numeric,numeric) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.calculate_gps_distance_km(numeric,numeric,numeric,numeric) TO service_role;
REVOKE EXECUTE ON FUNCTION public.end_trip(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text,text,uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.end_trip(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text,text,uuid,text) TO service_role;
