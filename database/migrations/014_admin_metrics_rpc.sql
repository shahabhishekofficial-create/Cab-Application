-- Keep admin metrics aggregation inside PostgreSQL so the API does not load
-- entire operational tables into memory for a dashboard request.
create or replace function public.get_admin_metrics(
  p_from date default null,
  p_to date default null
)
returns jsonb
language sql
security definer
set search_path = public
as $$
  with session_stats as (
    select
      count(*)::bigint as sessions,
      count(*) filter (where status = 'OPEN')::bigint as open_sessions,
      coalesce(sum(
        case
          when close_odometer is null then 0
          else greatest(0, close_odometer - start_odometer)
        end
      ), 0)::numeric as running_km
    from sessions
    where (p_from is null or session_date >= p_from)
      and (p_to is null or session_date <= p_to)
  ),
  trip_stats as (
    select
      count(*)::bigint as trips,
      coalesce(sum(gross_fare), 0)::numeric as revenue
    from trips
    where (p_from is null or created_at >= p_from::timestamptz)
      and (p_to is null or created_at < (p_to + 1)::timestamptz)
  ),
  fuel_stats as (
    select coalesce(sum(amount), 0)::numeric as fuel_cost
    from fuel_transactions
    where (p_from is null or created_at >= p_from::timestamptz)
      and (p_to is null or created_at < (p_to + 1)::timestamptz)
  ),
  expense_stats as (
    select coalesce(sum(amount), 0)::numeric as expenses
    from expenses
    where (p_from is null or created_at >= p_from::timestamptz)
      and (p_to is null or created_at < (p_to + 1)::timestamptz)
  )
  select jsonb_build_object(
    'sessions', session_stats.sessions,
    'openSessions', session_stats.open_sessions,
    'trips', trip_stats.trips,
    'revenue', trip_stats.revenue,
    'runningKm', session_stats.running_km,
    'fuelCost', fuel_stats.fuel_cost,
    'expenses', expense_stats.expenses,
    'netOperatingResult', trip_stats.revenue - fuel_stats.fuel_cost - expense_stats.expenses
  )
  from session_stats, trip_stats, fuel_stats, expense_stats;
$$;

revoke execute on function public.get_admin_metrics(date, date) from public;
grant execute on function public.get_admin_metrics(date, date) to service_role;
