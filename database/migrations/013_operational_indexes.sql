-- Query-performance indexes for the operational register and session drill-downs.
create index if not exists idx_sessions_session_date on public.sessions(session_date);
create index if not exists idx_sessions_status on public.sessions(status);
create index if not exists idx_trips_session_id on public.trips(session_id);
create index if not exists idx_fuel_transactions_session_id on public.fuel_transactions(session_id);
create index if not exists idx_expenses_session_id on public.expenses(session_id);
