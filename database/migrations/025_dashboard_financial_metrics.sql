begin;

create or replace function public.get_admin_metrics(p_from date default null, p_to date default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_from timestamptz;
  v_to timestamptz;
  v_revenue numeric := 0;
  v_trip_charges numeric := 0;
  v_fuel_cost numeric := 0;
  v_expenses numeric := 0;
  v_sessions bigint := 0;
  v_open_sessions bigint := 0;
  v_trips bigint := 0;
  v_fuel_count bigint := 0;
  v_expense_count bigint := 0;
  v_running_km numeric := 0;
begin
  if p_from is not null then v_from := p_from::timestamptz; end if;
  if p_to is not null then v_to := (p_to + 1)::timestamptz; end if;
  select count(*) into v_sessions from sessions where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select count(*) into v_open_sessions from sessions where status='OPEN' and (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select count(*) into v_trips from trips where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select count(*) into v_fuel_count from fuel_transactions where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select count(*) into v_expense_count from expenses where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select coalesce(sum(gross_fare),0), coalesce(sum(additional_charges),0) into v_revenue,v_trip_charges from trips where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select coalesce(sum(amount),0) into v_fuel_cost from fuel_transactions where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select coalesce(sum(amount),0) into v_expenses from expenses where (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  select coalesce(sum(greatest(close_odometer-start_odometer,0)),0) into v_running_km from sessions where status='CLOSED' and close_odometer is not null and (v_from is null or created_at >= v_from) and (v_to is null or created_at < v_to);
  return jsonb_build_object('sessions',v_sessions,'openSessions',v_open_sessions,'trips',v_trips,'fuelCount',v_fuel_count,'expenseCount',v_expense_count,'revenue',v_revenue,'tripAdditionalCharges',v_trip_charges,'fuelCost',v_fuel_cost,'expenses',v_expenses,'totalCosts',v_trip_charges+v_fuel_cost+v_expenses,'netOperatingResult',v_revenue-v_trip_charges-v_fuel_cost-v_expenses,'runningKm',v_running_km);
end;
$$;

revoke execute on function public.get_admin_metrics(date,date) from public, anon, authenticated;
grant execute on function public.get_admin_metrics(date,date) to service_role;

commit;
