-- Session persistence: authoritative atomic start/close operations.
-- Execute after 001_initial_schema.sql.

create or replace function start_session(
  p_client_transaction_id uuid,
  p_driver_id uuid,
  p_vehicle_id uuid,
  p_device_id uuid,
  p_started_at timestamptz,
  p_start_odometer numeric,
  p_start_lat numeric,
  p_start_lng numeric,
  p_start_accuracy_m numeric,
  p_start_gps_at timestamptz,
  p_start_odometer_file_id uuid,
  p_notes text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing sessions%rowtype;
  created sessions%rowtype;
begin
  select * into existing from sessions where client_transaction_id = p_client_transaction_id;
  if found then
    return jsonb_build_object('id', existing.id, 'clientTransactionId', existing.client_transaction_id, 'status', existing.status, 'idempotent', true);
  end if;

  if not exists (
    select 1 from driver_vehicle_assignments
    where driver_id = p_driver_id and vehicle_id = p_vehicle_id and assigned_from <= p_started_at and (assigned_to is null or assigned_to > p_started_at)
  ) then
    raise exception using errcode = 'P0001', message = 'DRIVER_VEHICLE_NOT_ASSIGNED';
  end if;

  if not exists (select 1 from vehicles where id = p_vehicle_id and status = 'ACTIVE') then
    raise exception using errcode = 'P0001', message = 'VEHICLE_NOT_ACTIVE';
  end if;

  insert into sessions (
    client_transaction_id, driver_id, vehicle_id, device_id, session_date, status,
    started_at, start_odometer, start_lat, start_lng, start_accuracy_m, start_gps_at,
    start_odometer_file_id, notes
  ) values (
    p_client_transaction_id, p_driver_id, p_vehicle_id, p_device_id, (p_started_at at time zone 'Asia/Kolkata')::date, 'OPEN',
    p_started_at, p_start_odometer, p_start_lat, p_start_lng, p_start_accuracy_m, p_start_gps_at,
    p_start_odometer_file_id, p_notes
  ) returning * into created;

  update vehicles set current_odometer = greatest(current_odometer, p_start_odometer), updated_at = now() where id = p_vehicle_id;

  return jsonb_build_object('id', created.id, 'clientTransactionId', created.client_transaction_id, 'status', created.status, 'idempotent', false);
exception
  when unique_violation then
    if sqlerrm like '%uq_driver_vehicle_open_session%' then
      raise exception using errcode = 'P0001', message = 'SESSION_ALREADY_OPEN';
    end if;
    raise;
end;
$$;

create or replace function close_session(
  p_session_id uuid,
  p_driver_id uuid,
  p_vehicle_id uuid,
  p_closed_at timestamptz,
  p_close_odometer numeric,
  p_close_lat numeric,
  p_close_lng numeric,
  p_close_accuracy_m numeric,
  p_close_gps_at timestamptz,
  p_close_odometer_file_id uuid,
  p_reported_trip_count integer,
  p_reported_income numeric,
  p_notes text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  s sessions%rowtype;
  system_trip_count integer;
  system_income numeric;
  running_km numeric;
  completed_km numeric;
  cancelled_km numeric;
  unallocated_km numeric;
  trip_count_difference integer;
  income_difference numeric;
  reconciliation_status text;
begin
  select * into s from sessions where id = p_session_id for update;
  if not found then raise exception using errcode = 'P0001', message = 'NO_OPEN_SESSION'; end if;
  if s.status <> 'OPEN' then raise exception using errcode = 'P0001', message = 'SESSION_CLOSED'; end if;
  if s.driver_id <> p_driver_id then raise exception using errcode = 'P0001', message = 'SESSION_DRIVER_MISMATCH'; end if;
  if s.vehicle_id <> p_vehicle_id then raise exception using errcode = 'P0001', message = 'SESSION_VEHICLE_MISMATCH'; end if;
  if p_close_odometer < s.start_odometer then raise exception using errcode = 'P0001', message = 'INVALID_CLOSE_ODOMETER'; end if;
  if p_closed_at < s.started_at then raise exception using errcode = 'P0001', message = 'CLOSE_TIME_BEFORE_START'; end if;

  select count(*), coalesce(sum(gross_fare), 0),
         coalesce(sum(case when status = 'COMPLETED' and end_odometer is not null then end_odometer - start_odometer else 0 end), 0),
         coalesce(sum(case when status <> 'COMPLETED' and end_odometer is not null then end_odometer - start_odometer else 0 end), 0)
    into system_trip_count, system_income, completed_km, cancelled_km
    from trips where session_id = s.id;

  running_km := p_close_odometer - s.start_odometer;
  unallocated_km := running_km - completed_km - cancelled_km;
  trip_count_difference := p_reported_trip_count - system_trip_count;
  income_difference := p_reported_income - system_income;

  if running_km < 0 or unallocated_km < 0 then reconciliation_status := 'CRITICAL';
  elsif trip_count_difference <> 0 or abs(income_difference) >= 1 then reconciliation_status := 'REVIEW';
  else reconciliation_status := 'PASS';
  end if;

  update sessions set status = 'CLOSED', closed_at = p_closed_at, close_odometer = p_close_odometer,
    close_lat = p_close_lat, close_lng = p_close_lng, close_accuracy_m = p_close_accuracy_m,
    close_gps_at = p_close_gps_at, close_odometer_file_id = p_close_odometer_file_id,
    notes = p_notes, updated_at = now() where id = s.id;

  update vehicles set current_odometer = greatest(current_odometer, p_close_odometer), updated_at = now() where id = s.vehicle_id;

  insert into reconciliations (
    session_id, reported_trip_count, reported_income, system_trip_count, system_income,
    trip_count_difference, income_difference, running_km, completed_trip_km,
    cancelled_no_show_km, unallocated_km, status, notes
  ) values (
    s.id, p_reported_trip_count, p_reported_income, system_trip_count, system_income,
    trip_count_difference, income_difference, running_km, completed_km,
    cancelled_km, unallocated_km, reconciliation_status, p_notes
  )
  on conflict (session_id) do update set
    reported_trip_count = excluded.reported_trip_count,
    reported_income = excluded.reported_income,
    system_trip_count = excluded.system_trip_count,
    system_income = excluded.system_income,
    trip_count_difference = excluded.trip_count_difference,
    income_difference = excluded.income_difference,
    running_km = excluded.running_km,
    completed_trip_km = excluded.completed_trip_km,
    cancelled_no_show_km = excluded.cancelled_no_show_km,
    unallocated_km = excluded.unallocated_km,
    status = excluded.status,
    notes = excluded.notes;

  if reconciliation_status = 'CRITICAL' then
    insert into exceptions(entity_type, entity_id, code, severity, message)
    values ('SESSION', s.id, 'SESSION_RECONCILIATION_CRITICAL', 'CRITICAL', 'Session close produced negative running or unallocated kilometres');
  elsif reconciliation_status = 'REVIEW' then
    insert into exceptions(entity_type, entity_id, code, severity, message)
    values ('SESSION', s.id, 'SESSION_RECONCILIATION_REVIEW', 'WARNING', 'Reported session totals differ from system totals');
  end if;

  return jsonb_build_object(
    'sessionId', s.id, 'status', 'CLOSED', 'reconciliationStatus', reconciliation_status,
    'runningKm', running_km, 'systemTripCount', system_trip_count, 'systemIncome', system_income,
    'unallocatedKm', unallocated_km
  );
end;
$$;

revoke all on function start_session(uuid, uuid, uuid, uuid, timestamptz, numeric, numeric, numeric, numeric, timestamptz, uuid, text) from public;
revoke all on function close_session(uuid, uuid, uuid, timestamptz, numeric, numeric, numeric, numeric, timestamptz, uuid, integer, numeric, text) from public;
