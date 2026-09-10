begin;

-- Remove obsolete overloaded RPC signatures that can make PostgREST resolve
-- the wrong function. Keep only the current idempotent session RPCs.
drop function if exists public.start_session(uuid,uuid,uuid,uuid,uuid,timestamptz,double precision,double precision,double precision,double precision,timestamptz,uuid,text);
drop function if exists public.close_session(uuid,uuid,uuid,timestamptz,numeric,numeric,numeric,numeric,timestamptz,uuid,integer,numeric,text);

-- Align transaction RPC parameter mapping with the actual persisted column names.
create or replace function public.create_fuel_transaction(
  p_client_transaction_id uuid,p_session_id uuid,p_driver_id uuid,p_vehicle_id uuid,
  p_fuel_type text,p_odometer numeric,p_quantity numeric,p_unit text,p_rate numeric,
  p_amount numeric,p_payment_method payment_method,p_receipt_file_id uuid,
  p_latitude numeric,p_longitude numeric,p_accuracy_meters numeric,p_recorded_at timestamptz,p_notes text
) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_id uuid;
begin
  select id into v_id from fuel_transactions where client_transaction_id=p_client_transaction_id;
  if found then return jsonb_build_object('id',v_id,'status','IDEMPOTENT'); end if;
  if not exists(select 1 from sessions where id=p_session_id and driver_id=p_driver_id and vehicle_id=p_vehicle_id) then raise exception 'SESSION_IDENTITY_MISMATCH' using errcode='P0001'; end if;
  if not exists(select 1 from sessions where id=p_session_id and status='OPEN') then raise exception 'SESSION_NOT_OPEN' using errcode='P0001'; end if;
  insert into fuel_transactions(client_transaction_id,session_id,driver_id,vehicle_id,fuel_type,odometer,quantity,unit,rate,amount,payment_method,receipt_file_id,latitude,longitude,gps_accuracy_m,recorded_at,notes)
  values(p_client_transaction_id,p_session_id,p_driver_id,p_vehicle_id,p_fuel_type,p_odometer,p_quantity,p_unit,p_rate,p_amount,p_payment_method,p_receipt_file_id,p_latitude,p_longitude,p_accuracy_meters,p_recorded_at,p_notes)
  returning id into v_id;
  return jsonb_build_object('id',v_id,'status','CREATED');
end; $$;

create or replace function public.create_expense(
  p_client_transaction_id uuid,p_session_id uuid,p_driver_id uuid,p_vehicle_id uuid,
  p_category_id uuid,p_amount numeric,p_payment_method payment_method,p_proof_file_id uuid,
  p_odometer numeric,p_latitude numeric,p_longitude numeric,p_accuracy_meters numeric,p_recorded_at timestamptz,p_notes text
) returns jsonb language plpgsql security definer set search_path=public as $$
declare v_id uuid;
begin
  select id into v_id from expenses where client_transaction_id=p_client_transaction_id;
  if found then return jsonb_build_object('id',v_id,'status','IDEMPOTENT'); end if;
  if not exists(select 1 from sessions where id=p_session_id and driver_id=p_driver_id and vehicle_id=p_vehicle_id) then raise exception 'SESSION_IDENTITY_MISMATCH' using errcode='P0001'; end if;
  if not exists(select 1 from sessions where id=p_session_id and status='OPEN') then raise exception 'SESSION_NOT_OPEN' using errcode='P0001'; end if;
  insert into expenses(client_transaction_id,session_id,driver_id,vehicle_id,category_id,amount,payment_method,proof_file_id,odometer,latitude,longitude,gps_accuracy_m,recorded_at,notes)
  values(p_client_transaction_id,p_session_id,p_driver_id,p_vehicle_id,p_category_id,p_amount,p_payment_method,p_proof_file_id,p_odometer,p_latitude,p_longitude,p_accuracy_meters,p_recorded_at,p_notes)
  returning id into v_id;
  return jsonb_build_object('id',v_id,'status','CREATED');
end; $$;

commit;
