-- 005_client_session_id.sql
-- Stable client-generated session IDs allow the entire session to be created offline
-- and let trips/fuel/expenses reference that session before the device reconnects.

create or replace function public.start_session(
  p_session_id uuid,
  p_client_transaction_id uuid,
  p_driver_id uuid,
  p_vehicle_id uuid,
  p_device_id uuid default null,
  p_started_at timestamptz default now(),
  p_start_odometer numeric default 0,
  p_start_lat double precision default null,
  p_start_lng double precision default null,
  p_start_accuracy_m double precision default null,
  p_start_gps_at timestamptz default null,
  p_start_odometer_file_id uuid default null,
  p_notes text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_session sessions%rowtype;
  v_existing sessions%rowtype;
begin
  select * into v_session from sessions where client_transaction_id = p_client_transaction_id;
  if found then
    return to_jsonb(v_session);
  end if;

  select * into v_session from sessions where id = p_session_id;
  if found then
    if v_session.client_transaction_id = p_client_transaction_id then
      return to_jsonb(v_session);
    end if;
    raise exception using errcode = '23505', message = 'SESSION_ID_ALREADY_EXISTS';
  end if;

  if not exists (
    select 1 from driver_vehicle_assignments
    where driver_id = p_driver_id and vehicle_id = p_vehicle_id
      and active_from <= p_started_at
      and (active_to is null or active_to >= p_started_at)
  ) then
    raise exception using message = 'DRIVER_VEHICLE_NOT_ASSIGNED';
  end if;

  if not exists (select 1 from vehicles where id = p_vehicle_id and status = 'ACTIVE') then
    raise exception using message = 'VEHICLE_NOT_ACTIVE';
  end if;

  select * into v_existing from sessions
  where driver_id = p_driver_id and vehicle_id = p_vehicle_id and status = 'OPEN'
  limit 1;
  if found then
    raise exception using message = 'SESSION_ALREADY_OPEN';
  end if;

  insert into sessions (
    id, client_transaction_id, driver_id, vehicle_id, device_id, session_date,
    status, started_at, start_odometer, start_lat, start_lng, start_accuracy_m,
    start_gps_at, start_odometer_file_id, notes
  ) values (
    p_session_id, p_client_transaction_id, p_driver_id, p_vehicle_id, p_device_id,
    (p_started_at at time zone 'Asia/Kolkata')::date, 'OPEN', p_started_at,
    p_start_odometer, p_start_lat, p_start_lng, p_start_accuracy_m,
    p_start_gps_at, p_start_odometer_file_id, p_notes
  ) returning * into v_session;

  update vehicles set current_odometer = greatest(coalesce(current_odometer, 0), p_start_odometer)
  where id = p_vehicle_id;

  return to_jsonb(v_session);
exception
  when unique_violation then
    select * into v_session from sessions where client_transaction_id = p_client_transaction_id;
    if found then return to_jsonb(v_session); end if;
    raise;
end;
$$;

grant execute on function public.start_session(uuid, uuid, uuid, uuid, uuid, timestamptz, numeric, double precision, double precision, double precision, timestamptz, uuid, text) to service_role;
