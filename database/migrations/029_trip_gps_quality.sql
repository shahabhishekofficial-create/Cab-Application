begin;

alter table public.trips
  add column if not exists latitude numeric(10,7),
  add column if not exists longitude numeric(10,7),
  add column if not exists gps_accuracy_m numeric(8,2),
  add column if not exists gps_at timestamptz;

drop function if exists public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,text);

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
  if p_latitude is null or p_longitude is null or p_gps_accuracy_m is null or p_gps_at is null then raise exception 'GPS_REQUIRED'; end if;
  if p_gps_accuracy_m < 0 or p_gps_accuracy_m > 50 then raise exception 'GPS_ACCURACY_TOO_LOW'; end if;
  if p_gps_at < p_started_at - interval '10 minutes' or p_gps_at > p_started_at + interval '2 minutes' then raise exception 'GPS_STALE'; end if;
  insert into trips(client_transaction_id,session_id,driver_id,vehicle_id,platform_id,started_at,ended_at,pickup,dropoff,start_odometer,end_odometer,gross_fare,payment_method,additional_charges,status,latitude,longitude,gps_accuracy_m,gps_at,notes)
  values(p_client_transaction_id,p_session_id,p_driver_id,p_vehicle_id,p_platform_id,p_started_at,p_ended_at,p_pickup,p_dropoff,p_start_odometer,p_end_odometer,p_gross_fare,p_payment_method,p_additional_charges,p_status,p_latitude,p_longitude,p_gps_accuracy_m,p_gps_at,p_notes)
  returning * into v_trip;
  return jsonb_build_object('id',v_trip.id,'status','CREATED');
exception when unique_violation then
  select * into v_trip from trips where client_transaction_id=p_client_transaction_id;
  if found then return jsonb_build_object('id',v_trip.id,'status','IDEMPOTENT'); end if;
  raise;
end; $$;

revoke all on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text) from public, anon, authenticated;
grant execute on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,numeric,numeric,numeric,timestamptz,text) to service_role;

commit;
