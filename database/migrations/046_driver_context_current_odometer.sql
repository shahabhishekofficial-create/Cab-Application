create or replace function public.get_my_driver_context(p_user_id uuid) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_driver_id uuid; v_display_name text; v_vehicle_id uuid; v_registration text; v_current_odometer numeric; v_trips integer; v_distance numeric; v_earnings numeric; v_fuel numeric; v_hours numeric;
begin
 if p_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
 select d.id,u.display_name into v_driver_id,v_display_name from public.drivers d join public.app_users u on u.id=d.user_id where d.user_id=p_user_id and u.is_active=true;
 if v_driver_id is null then raise exception 'DRIVER_PROFILE_NOT_FOUND'; end if;
 select v.id,v.registration_number,v.current_odometer into v_vehicle_id,v_registration,v_current_odometer from public.driver_vehicle_assignments a join public.vehicles v on v.id=a.vehicle_id where a.driver_id=v_driver_id and a.assigned_to is null and v.status='ACTIVE' order by a.assigned_from desc limit 1;
 select count(*)::int,coalesce(sum(coalesce(t.route_distance_km,greatest(coalesce(t.end_odometer,t.start_odometer)-t.start_odometer,0))),0),coalesce(sum(t.gross_fare),0) into v_trips,v_distance,v_earnings from trips t where t.driver_id=v_driver_id and t.started_at>=date_trunc('day',now()) and t.started_at<date_trunc('day',now())+interval '1 day';
 select coalesce(sum(f.amount),0) into v_fuel from fuel_transactions f where f.driver_id=v_driver_id and f.recorded_at>=date_trunc('day',now()) and f.recorded_at<date_trunc('day',now())+interval '1 day';
 select coalesce(sum(extract(epoch from (coalesce(s.closed_at,now())-s.started_at))/3600.0),0) into v_hours from sessions s where s.driver_id=v_driver_id and s.started_at>=date_trunc('day',now()) and s.started_at<date_trunc('day',now())+interval '1 day';
 return jsonb_build_object('userId',p_user_id,'driverId',v_driver_id,'displayName',v_display_name,'vehicleId',v_vehicle_id,'registrationNumber',v_registration,'currentOdometer',v_current_odometer,'today',jsonb_build_object('trips',v_trips,'distanceKm',round(v_distance,2),'earnings',round(v_earnings,2),'fuelSpend',round(v_fuel,2),'hoursActive',round(v_hours,2)));
end; $$;
