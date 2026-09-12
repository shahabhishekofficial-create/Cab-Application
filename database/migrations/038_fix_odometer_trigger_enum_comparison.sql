-- Fix generic odometer trigger comparisons across tables with different enum types.
-- PostgreSQL evaluates the status comparison before the table-name branch can short-circuit;
-- casting status to text prevents trips.trip_status from being compared to the session status enum.

create or replace function public.enforce_odometer_progression()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_session sessions%rowtype;
  v_previous numeric;
begin
  if tg_table_name = 'sessions' and tg_op = 'INSERT' then
    select coalesce(max(close_odometer), 0) into v_previous from sessions where vehicle_id = new.vehicle_id and close_odometer is not null;
    select greatest(v_previous, coalesce(current_odometer, 0)) into v_previous from vehicles where id = new.vehicle_id;
    if new.start_odometer < v_previous then raise exception 'ODOMETER_REGRESSION:START_BELOW_VEHICLE_HISTORY'; end if;
    return new;
  end if;

  if tg_table_name = 'sessions' and tg_op = 'UPDATE' and new.status::text = 'CLOSED' and old.status::text <> 'CLOSED' then
    select greatest(old.start_odometer,
      coalesce((select max(end_odometer) from trips where session_id = old.id), 0),
      coalesce((select max(start_odometer) from trips where session_id = old.id), 0),
      coalesce((select max(odometer) from fuel_transactions where session_id = old.id), 0),
      coalesce((select max(odometer) from expenses where session_id = old.id), 0)) into v_previous;
    if new.close_odometer is null or new.close_odometer < v_previous then raise exception 'ODOMETER_REGRESSION:SESSION_CLOSE'; end if;
    return new;
  end if;

  select * into v_session from sessions where id = new.session_id;
  if not found then raise exception 'SESSION_NOT_FOUND'; end if;

  if tg_table_name = 'trips' and tg_op = 'INSERT' then
    select greatest(v_session.start_odometer,
      coalesce((select max(end_odometer) from trips where session_id = new.session_id), 0),
      coalesce((select max(start_odometer) from trips where session_id = new.session_id), 0),
      coalesce((select max(odometer) from fuel_transactions where session_id = new.session_id), 0),
      coalesce((select max(odometer) from expenses where session_id = new.session_id), 0)) into v_previous;
    if new.start_odometer < v_previous then raise exception 'ODOMETER_REGRESSION:TRIP_START'; end if;
    if new.end_odometer is not null and new.end_odometer < new.start_odometer then raise exception 'ODOMETER_REGRESSION:TRIP_END_BELOW_START'; end if;
    return new;
  end if;

  if tg_table_name = 'fuel_transactions' and tg_op = 'INSERT' then
    select greatest(v_session.start_odometer,
      coalesce((select max(end_odometer) from trips where session_id = new.session_id), 0),
      coalesce((select max(start_odometer) from trips where session_id = new.session_id), 0),
      coalesce((select max(odometer) from fuel_transactions where session_id = new.session_id), 0),
      coalesce((select max(odometer) from expenses where session_id = new.session_id), 0)) into v_previous;
    if new.odometer < v_previous then raise exception 'ODOMETER_REGRESSION:FUEL'; end if;
    if abs(new.amount - (new.quantity * new.rate)) > 0.01 then raise exception 'FUEL_AMOUNT_MISMATCH'; end if;
    return new;
  end if;

  if tg_table_name = 'expenses' and tg_op = 'INSERT' then
    if new.odometer is not null then
      select greatest(v_session.start_odometer,
        coalesce((select max(end_odometer) from trips where session_id = new.session_id), 0),
        coalesce((select max(start_odometer) from trips where session_id = new.session_id), 0),
        coalesce((select max(odometer) from fuel_transactions where session_id = new.session_id), 0),
        coalesce((select max(odometer) from expenses where session_id = new.session_id), 0)) into v_previous;
      if new.odometer < v_previous then raise exception 'ODOMETER_REGRESSION:EXPENSE'; end if;
    end if;
    return new;
  end if;
  return new;
end;
$function$;
