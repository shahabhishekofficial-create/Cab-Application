-- Recover an OPEN session when the original local SESSION_START transaction
-- is retried with a different client transaction UUID after the server already
-- created the requested session. Only the same session/driver/vehicle may be
-- reconciled; a different open session still fails closed.
create or replace function public.start_session(
  p_client_transaction_id uuid,
  p_session_id uuid,
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
  active_existing sessions%rowtype;
begin
  select * into existing from sessions where client_transaction_id = p_client_transaction_id;
  if found then
    if existing.id <> p_session_id then
      raise exception 'SESSION_ID_MISMATCH' using errcode='P0001';
    end if;
    return jsonb_build_object('id', existing.id, 'clientTransactionId', existing.client_transaction_id, 'status', existing.status, 'idempotent', true);
  end if;

  if not exists (
    select 1 from driver_vehicle_assignments
    where driver_id=p_driver_id and vehicle_id=p_vehicle_id
      and assigned_from<=p_started_at and (assigned_to is null or assigned_to>p_started_at)
  ) then
    raise exception using errcode='P0001', message='DRIVER_VEHICLE_NOT_ASSIGNED';
  end if;

  if not exists (select 1 from vehicles where id=p_vehicle_id and status='ACTIVE') then
    raise exception using errcode='P0001', message='VEHICLE_NOT_ACTIVE';
  end if;

  begin
    insert into sessions(
      id,client_transaction_id,driver_id,vehicle_id,device_id,session_date,status,
      started_at,start_odometer,start_lat,start_lng,start_accuracy_m,start_gps_at,
      start_odometer_file_id,notes
    ) values (
      p_session_id,p_client_transaction_id,p_driver_id,p_vehicle_id,p_device_id,
      (p_started_at at time zone 'Asia/Kolkata')::date,'OPEN',p_started_at,
      p_start_odometer,p_start_lat,p_start_lng,p_start_accuracy_m,p_start_gps_at,
      p_start_odometer_file_id,p_notes
    ) returning * into created;
  exception when unique_violation then
    select * into active_existing
    from sessions
    where driver_id=p_driver_id and vehicle_id=p_vehicle_id and status='OPEN'
    limit 1;

    if found and active_existing.id=p_session_id then
      return jsonb_build_object(
        'id',active_existing.id,
        'clientTransactionId',active_existing.client_transaction_id,
        'status',active_existing.status,
        'idempotent',true
      );
    end if;

    if sqlerrm like '%uq_driver_vehicle_open_session%' then
      raise exception using errcode='P0001', message='SESSION_ALREADY_OPEN';
    end if;
    raise;
  end;

  update vehicles
  set current_odometer=greatest(current_odometer,p_start_odometer), updated_at=now()
  where id=p_vehicle_id;

  return jsonb_build_object('id',created.id,'clientTransactionId',created.client_transaction_id,'status',created.status,'idempotent',false);
end;
$$;

revoke execute on function public.start_session(uuid,uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,text) from public, anon, authenticated;
grant execute on function public.start_session(uuid,uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,text) to service_role;
