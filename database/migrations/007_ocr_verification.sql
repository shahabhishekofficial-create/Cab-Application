-- 007_ocr_verification.sql
-- Persist the on-device odometer OCR result separately from the operational session.

create table if not exists public.ocr_verifications (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.sessions(id),
  context text not null check (context in ('START','CLOSE')),
  manual_reading numeric(12,2) not null check (manual_reading >= 0),
  ocr_reading numeric(12,2),
  confidence numeric(5,4) not null check (confidence >= 0 and confidence <= 1),
  decision text not null check (decision in ('PASS','REVIEW','FAIL')),
  raw_text text,
  created_at timestamptz not null default now(),
  constraint uq_ocr_session_context unique(session_id, context)
);

create index if not exists idx_ocr_verifications_session on public.ocr_verifications(session_id);
create index if not exists idx_ocr_verifications_decision on public.ocr_verifications(decision);

alter table public.ocr_verifications enable row level security;

create or replace function public.record_ocr_verification(
  p_session_id uuid,
  p_context text,
  p_manual_reading numeric,
  p_ocr_reading numeric,
  p_confidence numeric,
  p_decision text,
  p_raw_text text default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare v_row public.ocr_verifications%rowtype;
begin
  if p_context not in ('START','CLOSE') then raise exception using message = 'INVALID_OCR_CONTEXT'; end if;
  if p_decision not in ('PASS','REVIEW','FAIL') then raise exception using message = 'INVALID_OCR_DECISION'; end if;
  if p_manual_reading < 0 or p_confidence < 0 or p_confidence > 1 then raise exception using message = 'INVALID_OCR_VALUES'; end if;

  insert into public.ocr_verifications(session_id, context, manual_reading, ocr_reading, confidence, decision, raw_text)
  values (p_session_id, p_context, p_manual_reading, p_ocr_reading, p_confidence, p_decision, p_raw_text)
  on conflict (session_id, context) do update set
    manual_reading = excluded.manual_reading,
    ocr_reading = excluded.ocr_reading,
    confidence = excluded.confidence,
    decision = excluded.decision,
    raw_text = excluded.raw_text;

  select * into v_row from public.ocr_verifications where session_id = p_session_id and context = p_context;
  return to_jsonb(v_row);
end;
$$;

revoke all on function public.record_ocr_verification(uuid, text, numeric, numeric, numeric, text, text) from public;
grant execute on function public.record_ocr_verification(uuid, text, numeric, numeric, numeric, text, text) to service_role;
