-- Driver actions remain usable when a fresh/accurate GPS fix is temporarily unavailable.
-- GPS is still stored whenever available; session start/close keep their explicit GPS barriers.

create or replace function public.create_trip(
  p_client_transaction_id uuid,p_session_id uuid,p_driver_id uuid,p_vehicle_id uuid,p_platform_id uuid,
  p_started_at timestamptz,p_ended_at timestamptz,p_pickup text,p_dropoff text,p_start_odometer numeric,
  p_end_odometer numeric,p_gross_fare numeric,p_payment_method payment_method,p_additional_charges numeric,
  p_status trip_status,p_latitude numeric,p_longitude numeric,p_gps_accuracy_m numeric,p_gps_at timestamptz,p_notes text
) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_trip trips%rowtype;
begin
  select * into v_trip from trips where client_transaction_id=p_client_transaction_id;
  if found then return jsonb_build_object('id',v_trip.id,'status','IDEMPOTENT'); end if;
  if not exists(select 1 from sessions where id=p_session_id and driver_id=p_driver_id and vehicle_id=p_vehicle_id) then raise exception 'SESSION_IDENTITY_MISMATCH'; end if;
  if not exists(select 1 from sessions where id=p_session_id and status='OPEN') then raise exception 'SESSION_NOT_OPEN'; end if;
  if p_latitude is not null and (p_latitude < -90 or p_latitude > 90) then raise exception 'GPS_INVALID_COORDINATES'; end if;
  if p_longitude is not null and (p_longitude < -180 or p_longitude > 180) then raise exception 'GPS_INVALID_COORDINATES'; end if;
  if p_gps_accuracy_m is not null and (p_gps_accuracy_m < 0 or p_gps_accuracy_m > 50) then raise exception 'GPS_ACCURACY_TOO_LOW'; end if;
  insert into trips(client_transaction_id,session_id,driver_id,vehicle_id,platform_id,started_at,ended_at,pickup,dropoff,start_odometer,end_odometer,gross_fare,payment_method,additional_charges,status,latitude,longitude,gps_accuracy_m,gps_at,notes)
  values(p_client_transaction_id,p_session_id,p_driver_id,p_vehicle_id,p_platform_id,p_started_at,p_ended_at,p_pickup,p_dropoff,p_start_odometer,p_end_odometer,p_gross_fare,p_payment_method,p_additional_charges,p_status,p_latitude,p_longitude,p_gps_accuracy_m,p_gps_at,p_notes)
  returning * into v_trip;
  return jsonb_build_object('id',v_trip.id,'status','CREATED');
exception when unique_violation then
  select * into v_trip from trips where client_transaction_id=p_client_transaction_id;
  if found then return jsonb_build_object('id',v_trip.id,'status','IDEMPOTENT'); end if;
  raise;
end; $$;

create or replace function public.end_trip(
  p_client_transaction_id uuid,p_session_id uuid,p_driver_id uuid,p_vehicle_id uuid,p_ended_at timestamptz,
  p_end_odometer numeric,p_gross_fare numeric,p_payment_method payment_method,p_additional_charges numeric,
  p_status trip_status,p_latitude numeric,p_longitude numeric,p_gps_accuracy_m numeric,p_gps_at timestamptz,
  p_pickup text,p_dropoff text,p_platform_id uuid,p_notes text,p_route_distance_m numeric default null,
  p_route_duration_seconds integer default null,p_route_polyline text default null,p_route_provider text default null
) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_trip trips%rowtype;
begin
  select * into v_trip from trips where client_transaction_id=p_client_transaction_id for update;
  if not found then raise exception 'TRIP_NOT_FOUND'; end if;
  if v_trip.session_id<>p_session_id or v_trip.driver_id<>p_driver_id or v_trip.vehicle_id<>p_vehicle_id then raise exception 'TRIP_IDENTITY_MISMATCH'; end if;
  if v_trip.status<>'IN_PROGRESS' then return jsonb_build_object('id',v_trip.id,'status','IDEMPOTENT'); end if;
  if not exists(select 1 from sessions where id=p_session_id and status='OPEN') then raise exception 'SESSION_NOT_OPEN'; end if;
  if p_ended_at<v_trip.started_at then raise exception 'TRIP_END_TIME_BEFORE_START'; end if;
  if p_end_odometer<v_trip.start_odometer then raise exception 'ODOMETER_REGRESSION'; end if;
  if p_latitude is not null and (p_latitude < -90 or p_latitude > 90) then raise exception 'GPS_INVALID_COORDINATES'; end if;
  if p_longitude is not null and (p_longitude < -180 or p_longitude > 180) then raise exception 'GPS_INVALID_COORDINATES'; end if;
  if p_gps_accuracy_m is not null and (p_gps_accuracy_m < 0 or p_gps_accuracy_m > 50) then raise exception 'GPS_ACCURACY_TOO_LOW'; end if;
  update trips set ended_at=p_ended_at,end_odometer=p_end_odometer,gross_fare=p_gross_fare,payment_method=p_payment_method,additional_charges=p_additional_charges,status=p_status,
    end_latitude=p_latitude,end_longitude=p_longitude,end_gps_accuracy_m=p_gps_accuracy_m,end_gps_at=p_gps_at,
    gps_distance_km=case when v_trip.latitude is not null and v_trip.longitude is not null and p_latitude is not null and p_longitude is not null then public.calculate_gps_distance_km(v_trip.latitude,v_trip.longitude,p_latitude,p_longitude) else null end,
    pickup=coalesce(p_pickup,pickup),dropoff=coalesce(p_dropoff,dropoff),platform_id=coalesce(p_platform_id,platform_id),notes=coalesce(p_notes,notes),
    route_distance_km=coalesce(p_route_distance_m/1000.0,route_distance_km),route_duration_seconds=coalesce(p_route_duration_seconds,route_duration_seconds),route_polyline=coalesce(p_route_polyline,route_polyline),route_provider=coalesce(p_route_provider,route_provider),updated_at=now()
  where id=v_trip.id returning * into v_trip;
  return jsonb_build_object('id',v_trip.id,'status','ENDED','gpsDistanceKm',v_trip.gps_distance_km);
end; $$;

revoke execute on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text) from public,anon,authenticated;
grant execute on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text) to service_role;
