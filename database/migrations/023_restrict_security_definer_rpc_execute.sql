begin;

-- These functions are backend implementation details. The API authenticates the
-- caller first and invokes them with the service role. Leaving EXECUTE granted
-- to PUBLIC would allow direct PostgREST calls to security-definer functions.
revoke execute on function public.start_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,text) from public, anon, authenticated;
revoke execute on function public.close_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,integer,numeric,text) from public, anon, authenticated;
revoke execute on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,text) from public, anon, authenticated;
revoke execute on function public.create_fuel_transaction(uuid,uuid,uuid,uuid,text,numeric,numeric,text,numeric,numeric,payment_method,uuid,numeric,numeric,numeric,timestamptz,text) from public, anon, authenticated;
revoke execute on function public.create_expense(uuid,uuid,uuid,uuid,uuid,numeric,payment_method,uuid,numeric,numeric,numeric,numeric,timestamptz,text) from public, anon, authenticated;
revoke execute on function public.get_my_driver_context(uuid) from public, anon, authenticated;
revoke execute on function public.get_admin_metrics(date,date) from public, anon, authenticated;
revoke execute on function public.record_ocr_verification(uuid,text,numeric,numeric,numeric,text,text) from public, anon, authenticated;
revoke execute on function public.audit_row_change() from public, anon, authenticated;

grant execute on function public.start_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,text) to service_role;
grant execute on function public.close_session(uuid,uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,integer,numeric,text) to service_role;
grant execute on function public.create_trip(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,text,text,numeric,numeric,numeric,payment_method,numeric,trip_status,text) to service_role;
grant execute on function public.create_fuel_transaction(uuid,uuid,uuid,uuid,text,numeric,numeric,text,numeric,numeric,payment_method,uuid,numeric,numeric,numeric,timestamptz,text) to service_role;
grant execute on function public.create_expense(uuid,uuid,uuid,uuid,uuid,numeric,payment_method,uuid,numeric,numeric,numeric,numeric,timestamptz,text) to service_role;
grant execute on function public.get_my_driver_context(uuid) to service_role;
grant execute on function public.get_admin_metrics(date,date) to service_role;
grant execute on function public.record_ocr_verification(uuid,text,numeric,numeric,numeric,text,text) to service_role;

commit;
