begin;

create or replace function public.close_session(
  p_client_transaction_id uuid,
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
) returns jsonb
language plpgsql security definer set search_path=public
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
  select * into s from sessions where close_client_transaction_id=p_client_transaction_id for update;
  if found then
    return jsonb_build_object('sessionId',s.id,'status','CLOSED','idempotent',true,'runningKm',coalesce(s.close_odometer,0)-s.start_odometer);
  end if;

  select * into s from sessions where id=p_session_id for update;
  if not found then raise exception using errcode='P0001',message='NO_OPEN_SESSION'; end if;
  if s.status<>'OPEN' then raise exception using errcode='P0001',message='SESSION_CLOSED'; end if;
  if s.driver_id<>p_driver_id then raise exception using errcode='P0001',message='SESSION_DRIVER_MISMATCH'; end if;
  if s.vehicle_id<>p_vehicle_id then raise exception using errcode='P0001',message='SESSION_VEHICLE_MISMATCH'; end if;
  if exists (select 1 from trips where session_id=s.id and status='IN_PROGRESS') then
    raise exception using errcode='P0001',message='ACTIVE_TRIP_MUST_BE_ENDED';
  end if;
  if p_close_odometer<s.start_odometer then raise exception using errcode='P0001',message='INVALID_CLOSE_ODOMETER'; end if;
  if p_closed_at<s.started_at then raise exception using errcode='P0001',message='CLOSE_TIME_BEFORE_START'; end if;

  select count(*),coalesce(sum(gross_fare),0),
    coalesce(sum(case when status='COMPLETED' and end_odometer is not null then end_odometer-start_odometer else 0 end),0),
    coalesce(sum(case when status<>'COMPLETED' and end_odometer is not null then end_odometer-start_odometer else 0 end),0)
  into system_trip_count,system_income,completed_km,cancelled_km
  from trips where session_id=s.id;

  running_km:=p_close_odometer-s.start_odometer;
  unallocated_km:=running_km-completed_km-cancelled_km;
  trip_count_difference:=p_reported_trip_count-system_trip_count;
  income_difference:=p_reported_income-system_income;
  if running_km<0 or unallocated_km<0 then reconciliation_status:='CRITICAL';
  elsif trip_count_difference<>0 or abs(income_difference)>=1 then reconciliation_status:='REVIEW';
  else reconciliation_status:='PASS'; end if;

  update sessions set status='CLOSED',close_client_transaction_id=p_client_transaction_id,closed_at=p_closed_at,close_odometer=p_close_odometer,close_lat=p_close_lat,close_lng=p_close_lng,close_accuracy_m=p_close_accuracy_m,close_gps_at=p_close_gps_at,close_odometer_file_id=p_close_odometer_file_id,notes=p_notes,updated_at=now() where id=s.id;
  update vehicles set current_odometer=greatest(current_odometer,p_close_odometer),updated_at=now() where id=s.vehicle_id;
  insert into reconciliations(session_id,reported_trip_count,reported_income,system_trip_count,system_income,trip_count_difference,income_difference,running_km,completed_trip_km,cancelled_no_show_km,unallocated_km,status,notes)
  values(s.id,p_reported_trip_count,p_reported_income,system_trip_count,system_income,trip_count_difference,income_difference,running_km,completed_km,cancelled_km,unallocated_km,reconciliation_status,p_notes)
  on conflict(session_id) do update set reported_trip_count=excluded.reported_trip_count,reported_income=excluded.reported_income,system_trip_count=excluded.system_trip_count,system_income=excluded.system_income,trip_count_difference=excluded.trip_count_difference,income_difference=excluded.income_difference,running_km=excluded.running_km,completed_trip_km=excluded.completed_trip_km,cancelled_no_show_km=excluded.cancelled_no_show_km,unallocated_km=excluded.unallocated_km,status=excluded.status,notes=excluded.notes;
  return jsonb_build_object('sessionId',s.id,'status','CLOSED','idempotent',false,'reconciliationStatus',reconciliation_status,'runningKm',running_km,'systemTripCount',system_trip_count,'systemIncome',system_income,'unallocatedKm',unallocated_km);
end;
$$;

revoke all on function public.close_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,integer,numeric,text) from public;
grant execute on function public.close_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,integer,numeric,text) to service_role;
commit;
