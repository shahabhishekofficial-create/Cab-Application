-- Prevent conflicting active trips and prevent closing a session with an unfinished trip.
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_one_active_trip ON public.trips(session_id) WHERE status='IN_PROGRESS';
CREATE OR REPLACE FUNCTION public.prevent_session_close_with_active_trip() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $function$
begin
  if new.status='CLOSED' and old.status<>'CLOSED' and exists(select 1 from trips where session_id=new.id and status='IN_PROGRESS') then raise exception 'TRIP_IN_PROGRESS'; end if;
  return new;
end;
$function$;
DROP TRIGGER IF EXISTS trg_session_close_active_trip ON public.sessions;
CREATE TRIGGER trg_session_close_active_trip BEFORE UPDATE ON public.sessions FOR EACH ROW EXECUTE FUNCTION public.prevent_session_close_with_active_trip();
REVOKE EXECUTE ON FUNCTION public.prevent_session_close_with_active_trip() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.prevent_session_close_with_active_trip() TO service_role;
