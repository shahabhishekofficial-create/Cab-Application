begin;

create or replace function public.enforce_gps_quality()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_lat numeric; v_lng numeric; v_accuracy numeric; v_gps_at timestamptz; v_event_at timestamptz;
begin
  if TG_TABLE_NAME = 'sessions' then
    if TG_OP = 'INSERT' then
      v_lat := new.start_lat; v_lng := new.start_lng; v_accuracy := new.start_accuracy_m; v_gps_at := new.start_gps_at; v_event_at := new.started_at;
    elsif new.status = 'CLOSED' and (old.status is distinct from 'CLOSED' or new.close_odometer is distinct from old.close_odometer or new.close_gps_at is distinct from old.close_gps_at) then
      v_lat := new.close_lat; v_lng := new.close_lng; v_accuracy := new.close_accuracy_m; v_gps_at := new.close_gps_at; v_event_at := new.closed_at;
    else
      return new;
    end if;
  elsif TG_TABLE_NAME = 'fuel_transactions' then
    v_lat := new.latitude; v_lng := new.longitude; v_accuracy := new.gps_accuracy_m; v_gps_at := new.recorded_at; v_event_at := new.recorded_at;
  elsif TG_TABLE_NAME = 'expenses' then
    v_lat := new.latitude; v_lng := new.longitude; v_accuracy := new.gps_accuracy_m; v_gps_at := new.recorded_at; v_event_at := new.recorded_at;
  else return new;
  end if;
  if v_lat is null or v_lng is null or v_accuracy is null or v_gps_at is null then raise exception 'GPS_REQUIRED' using errcode='P0001'; end if;
  if v_lat < -90 or v_lat > 90 or v_lng < -180 or v_lng > 180 then raise exception 'GPS_INVALID_COORDINATES' using errcode='P0001'; end if;
  if v_accuracy < 0 or v_accuracy > 50 then raise exception 'GPS_ACCURACY_TOO_LOW' using errcode='P0001'; end if;
  if v_gps_at < v_event_at - interval '10 minutes' or v_gps_at > v_event_at + interval '2 minutes' then raise exception 'GPS_STALE' using errcode='P0001'; end if;
  return new;
end; $$;

commit;
