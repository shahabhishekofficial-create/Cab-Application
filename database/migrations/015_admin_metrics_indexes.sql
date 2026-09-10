-- Support date-scoped admin metrics and exports without full-table scans.
create index if not exists idx_trips_created_at on public.trips(created_at);
create index if not exists idx_fuel_transactions_created_at on public.fuel_transactions(created_at);
create index if not exists idx_expenses_created_at on public.expenses(created_at);
