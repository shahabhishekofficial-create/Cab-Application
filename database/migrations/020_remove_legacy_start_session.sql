begin;

-- Remove the legacy start_session overload left by an earlier API revision.
-- PostgREST must have one authoritative signature for this RPC.
drop function if exists public.start_session(uuid,uuid,uuid,uuid,uuid,timestamptz,double precision,double precision,double precision,double precision,timestamptz,uuid,text);

commit;
