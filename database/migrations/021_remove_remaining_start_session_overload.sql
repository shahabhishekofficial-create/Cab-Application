begin;

-- Remove the remaining legacy start_session overload so PostgREST has one
-- unambiguous RPC contract.
drop function if exists public.start_session(uuid,uuid,uuid,uuid,uuid,timestamptz,numeric,double precision,double precision,double precision,timestamptz,uuid,text);

commit;
