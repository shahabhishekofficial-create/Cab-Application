ALTER TABLE public.trips
  ADD COLUMN IF NOT EXISTS route_distance_km numeric(12,3),
  ADD COLUMN IF NOT EXISTS route_duration_seconds integer,
  ADD COLUMN IF NOT EXISTS route_polyline text,
  ADD COLUMN IF NOT EXISTS route_provider text,
  ADD COLUMN IF NOT EXISTS route_status text;

UPDATE public.trips
SET route_distance_km = gps_distance_km,
    route_status = CASE WHEN gps_distance_km IS NULL THEN NULL ELSE 'LEGACY_STRAIGHT_LINE' END
WHERE route_distance_km IS NULL AND gps_distance_km IS NOT NULL;

DROP FUNCTION IF EXISTS public.end_trip(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text,text,uuid,text);

CREATE OR REPLACE FUNCTION public.end_trip(
  p_client_transaction_id uuid,p_session_id uuid,p_driver_id uuid,p_vehicle_id uuid,
  p_ended_at timestamptz,p_end_odometer numeric,p_gross_fare numeric,p_payment_method payment_method,
  p_additional_charges numeric,p_status trip_status,p_latitude numeric,p_longitude numeric,
  p_gps_accuracy_m numeric,p_gps_at timestamptz,p_pickup text,p_dropoff text,p_platform_id uuid,p_notes text,
  p_route_distance_m numeric,p_route_duration_seconds integer,p_route_polyline text,p_route_provider text
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
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
 if p_route_distance_m is not null and p_route_distance_m<0 then raise exception 'ROUTE_DISTANCE_INVALID'; end if;
 update trips set ended_at=p_ended_at,end_odometer=p_end_odometer,gross_fare=p_gross_fare,payment_method=p_payment_method,additional_charges=p_additional_charges,status=p_status,end_latitude=p_latitude,end_longitude=p_longitude,end_gps_accuracy_m=p_gps_accuracy_m,end_gps_at=p_gps_at,route_distance_km=case when p_route_distance_m is null then null else round((p_route_distance_m/1000)::numeric,3) end,route_duration_seconds=p_route_duration_seconds,route_polyline=p_route_polyline,route_provider=p_route_provider,route_status=case when p_route_distance_m is null then 'UNAVAILABLE' else 'CALCULATED' end,pickup=coalesce(p_pickup,pickup),dropoff=coalesce(p_dropoff,dropoff),platform_id=coalesce(p_platform_id,platform_id),notes=coalesce(p_notes,notes),updated_at=now() where id=v_trip.id returning * into v_trip;
 return jsonb_build_object('id',v_trip.id,'status','ENDED','routeDistanceKm',v_trip.route_distance_km,'routeDurationSeconds',v_trip.route_duration_seconds,'routeProvider',v_trip.route_provider);
end;
$function$;

REVOKE EXECUTE ON FUNCTION public.end_trip(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text,text,uuid,text,numeric,integer,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.end_trip(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text,text,uuid,text,numeric,integer,text,text) TO service_role;
