-- Backup/export and audit foundations.
-- Operational records are never hard-deleted by application code.

create or replace function public.audit_row_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  actor uuid;
  entity_id uuid;
begin
  begin
    actor := nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
  exception when others then
    actor := null;
  end;

  entity_id := coalesce((case when TG_OP = 'DELETE' then OLD.id else NEW.id end), null);

  insert into public.audit_logs (actor_user_id, action, entity_type, entity_id, old_values, new_values, created_at)
  values (
    actor,
    TG_OP,
    TG_TABLE_NAME,
    entity_id,
    case when TG_OP in ('UPDATE','DELETE') then to_jsonb(OLD) else null end,
    case when TG_OP in ('INSERT','UPDATE') then to_jsonb(NEW) else null end,
    now()
  );
  return coalesce(NEW, OLD);
end;
$$;

-- Audit the operational entities whose changes affect financial/vehicle history.
drop trigger if exists audit_trips on public.trips;
create trigger audit_trips after insert or update or delete on public.trips
for each row execute function public.audit_row_change();

drop trigger if exists audit_fuel_transactions on public.fuel_transactions;
create trigger audit_fuel_transactions after insert or update or delete on public.fuel_transactions
for each row execute function public.audit_row_change();

drop trigger if exists audit_expenses on public.expenses;
create trigger audit_expenses after insert or update or delete on public.expenses
for each row execute function public.audit_row_change();

drop trigger if exists audit_sessions on public.sessions;
create trigger audit_sessions after insert or update or delete on public.sessions
for each row execute function public.audit_row_change();

revoke all on function public.audit_row_change() from public;
grant execute on function public.audit_row_change() to service_role;

-- Export views provide stable, flat datasets for Excel/CSV generation.
create or replace view public.export_sessions as
select * from public.sessions;

create or replace view public.export_trips as
select * from public.trips;

create or replace view public.export_fuel as
select * from public.fuel_transactions;

create or replace view public.export_expenses as
select * from public.expenses;

create or replace view public.export_reconciliation as
select * from public.reconciliations;

create or replace view public.export_exceptions as
select * from public.exceptions;
