-- Driver authentication/assignment read model.
-- Supabase Auth is the identity provider; app_users/assignments remain business authority.
-- The API validates the JWT before calling this service-role RPC, so the user id is explicit.

create or replace function public.get_my_driver_context(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_driver_id uuid;
  v_display_name text;
  v_vehicle_id uuid;
  v_registration text;
begin
  if p_user_id is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select d.id, u.display_name
    into v_driver_id, v_display_name
  from public.drivers d
  join public.app_users u on u.id = d.user_id
  where d.user_id = p_user_id and u.is_active = true;

  if v_driver_id is null then
    raise exception 'DRIVER_PROFILE_NOT_FOUND';
  end if;

  select v.id, v.registration_number
    into v_vehicle_id, v_registration
  from public.driver_vehicle_assignments a
  join public.vehicles v on v.id = a.vehicle_id
  where a.driver_id = v_driver_id
    and a.assigned_to is null
    and v.status = 'ACTIVE'
  order by a.assigned_from desc
  limit 1;

  return jsonb_build_object(
    'userId', p_user_id,
    'driverId', v_driver_id,
    'displayName', v_display_name,
    'vehicleId', v_vehicle_id,
    'registrationNumber', v_registration
  );
end;
$$;

revoke all on function public.get_my_driver_context(uuid) from public;
grant execute on function public.get_my_driver_context(uuid) to service_role;
