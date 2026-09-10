begin;

-- Keep database invariants aligned with API/local validation.
alter table public.trips
  add constraint trips_odometer_order check (end_odometer is null or end_odometer >= start_odometer);

alter table public.fuel_transactions
  add constraint fuel_amount_matches_quantity_rate check (abs(amount - (quantity * rate)) <= 0.01);

alter table public.expenses
  add constraint expenses_amount_positive check (amount > 0);

commit;
