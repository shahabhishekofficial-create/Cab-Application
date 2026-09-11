begin;

create or replace function public.update_driver_profile(
  p_driver_id uuid,
  p_display_name text,
  p_phone text,
  p_employee_code text,
  p_license_number text,
  p_license_expiry date,
  p_is_active boolean,
  p_actor_user_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid;
  v_old_user jsonb;
  v_old_driver jsonb;
begin
  select user_id into v_user_id from drivers where id = p_driver_id for update;
  if v_user_id is null then raise exception 'DRIVER_NOT_FOUND' using errcode='P0001'; end if;

  select to_jsonb(u) - 'created_at' - 'updated_at' into v_old_user
  from app_users u where id = v_user_id for update;
  select to_jsonb(d) - 'created_at' - 'updated_at' into v_old_driver
  from drivers d where id = p_driver_id;

  update app_users
  set display_name = btrim(p_display_name), phone = nullif(btrim(p_phone), ''), is_active = p_is_active, updated_at = now()
  where id = v_user_id;

  update drivers
  set employee_code = nullif(btrim(p_employee_code), ''), license_number = nullif(btrim(p_license_number), ''), license_expiry = p_license_expiry, updated_at = now()
  where id = p_driver_id;

  insert into audit_logs(actor_user_id, entity_type, entity_id, action, old_data, new_data, reason)
  values (
    p_actor_user_id,
    'DRIVER',
    p_driver_id,
    'UPDATE_PROFILE',
    jsonb_build_object('user', v_old_user, 'driver', v_old_driver),
    jsonb_build_object('display_name', btrim(p_display_name), 'phone', nullif(btrim(p_phone), ''), 'employee_code', nullif(btrim(p_employee_code), ''), 'license_number', nullif(btrim(p_license_number), ''), 'license_expiry', p_license_expiry, 'is_active', p_is_active),
    'Admin driver profile edit'
  );

  return jsonb_build_object('updated', true, 'driverId', p_driver_id);
end;
$$;

revoke execute on function public.update_driver_profile(uuid,text,text,text,text,date,boolean,uuid) from public, anon, authenticated;
grant execute on function public.update_driver_profile(uuid,text,text,text,text,date,boolean,uuid) to service_role;

commit;
