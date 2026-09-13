create or replace function public.get_admin_dashboard()
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_today date := current_date;
  v_yesterday date := current_date - 1;
  v_today_trips bigint := 0;
  v_yesterday_trips bigint := 0;
  v_today_revenue numeric := 0;
  v_yesterday_revenue numeric := 0;
  v_today_fuel numeric := 0;
  v_yesterday_fuel numeric := 0;
  v_today_open bigint := 0;
  v_yesterday_open bigint := 0;
  v_attention jsonb := '[]'::jsonb;
begin
  select count(*) into v_today_trips from trips where created_at >= v_today and created_at < v_today + 1;
  select count(*) into v_yesterday_trips from trips where created_at >= v_yesterday and created_at < v_today;
  select coalesce(sum(gross_fare),0) into v_today_revenue from trips where created_at >= v_today and created_at < v_today + 1;
  select coalesce(sum(gross_fare),0) into v_yesterday_revenue from trips where created_at >= v_yesterday and created_at < v_today;
  select coalesce(sum(amount),0) into v_today_fuel from fuel_transactions where created_at >= v_today and created_at < v_today + 1;
  select coalesce(sum(amount),0) into v_yesterday_fuel from fuel_transactions where created_at >= v_yesterday and created_at < v_today;
  select count(*) into v_today_open from sessions where status='OPEN';
  select count(*) into v_yesterday_open from sessions where status='OPEN' and created_at < v_today;

  select coalesce(jsonb_agg(x order by x.created_at desc), '[]'::jsonb) into v_attention
  from (
    select id, code, severity::text as severity, status::text as status, message, created_at
    from exceptions
    where status::text not in ('RESOLVED','CLOSED')
    order by created_at desc
    limit 20
  ) x;

  return jsonb_build_object(
    'today', jsonb_build_object(
      'activeSessions', v_today_open,
      'trips', v_today_trips,
      'revenue', v_today_revenue,
      'fuelSpend', v_today_fuel,
      'trends', jsonb_build_object(
        'activeSessions', v_today_open - v_yesterday_open,
        'trips', v_today_trips - v_yesterday_trips,
        'revenue', v_today_revenue - v_yesterday_revenue,
        'fuelSpend', v_today_fuel - v_yesterday_fuel
      )
    ),
    'leaderboard', coalesce((select jsonb_agg(x order by x.trips desc, x.driver_name) from (
      select d.id as driver_id, coalesce(au.display_name,d.employee_code,'Driver') as driver_name,
             count(t.id) filter (where t.status::text not in ('CANCELLED','NO_SHOW')) as trips,
             round(coalesce(avg(extract(epoch from (t.ended_at-t.started_at))/60) filter (where t.ended_at is not null),0),1) as avg_trip_minutes,
             round(coalesce(sum(coalesce(t.route_distance_km,t.gps_distance_km,greatest(t.end_odometer-t.start_odometer,0))),0),1) as distance_km
      from drivers d join app_users au on au.id=d.user_id left join trips t on t.driver_id=d.id and t.created_at >= v_today
      group by d.id, au.display_name, d.employee_code
      having count(t.id)>0
      limit 10
    ) x),'[]'::jsonb),
    'fuelEfficiency', coalesce((select jsonb_agg(x order by x.day, x.vehicle_name) from (
      select date_trunc('day', f.created_at)::date as day, v.id as vehicle_id,
             coalesce(v.registration_number,v.model,'Vehicle') as vehicle_name,
             round(coalesce(sum(f.amount)/nullif(sum(coalesce(t.route_distance_km,t.gps_distance_km,greatest(t.end_odometer-t.start_odometer,0))),0),0),2) as cost_per_km,
             round(coalesce(sum(f.amount),0),2) as fuel_cost
      from fuel_transactions f join vehicles v on v.id=f.vehicle_id left join trips t on t.vehicle_id=f.vehicle_id and t.created_at between f.created_at - interval '7 days' and f.created_at + interval '1 day'
      where f.created_at >= current_date - 30
      group by 1,v.id,v.registration_number,v.model
    ) x),'[]'::jsonb),
    'sessionTimeline', coalesce((select jsonb_agg(x order by x.started_at desc) from (
      select s.id, coalesce(au.display_name,d.employee_code,'Driver') as driver_name,
             coalesce(v.registration_number,v.model,'Vehicle') as vehicle_name,
             s.status::text as status, s.started_at, s.closed_at,
             round(greatest(coalesce(s.close_odometer,v.current_odometer)-s.start_odometer,0),1) as distance_km
      from sessions s join drivers d on d.id=s.driver_id join app_users au on au.id=d.user_id join vehicles v on v.id=s.vehicle_id
      where s.created_at >= current_date - 7
      order by s.started_at desc limit 50
    ) x),'[]'::jsonb),
    'expenseBreakdown', coalesce((select jsonb_agg(x order by x.amount desc) from (
      select coalesce(ec.name,'Uncategorized') as category, round(sum(e.amount),2) as amount
      from expenses e left join expense_categories ec on ec.id=e.category_id
      where e.created_at >= v_today
      group by ec.name
    ) x),'[]'::jsonb),
    'attention', v_attention,
    'generatedAt', now()
  );
end;
$function$;

grant execute on function public.get_admin_dashboard() to service_role;
