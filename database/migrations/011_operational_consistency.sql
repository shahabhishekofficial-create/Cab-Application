-- Operational consistency safeguards.
-- Keep financial amounts and odometers non-negative at the database boundary.
-- These are additive constraints and do not alter the session workflow.

alter table public.trips
  add constraint trips_start_odometer_nonnegative check (start_odometer >= 0),
  add constraint trips_end_odometer_nonnegative check (end_odometer is null or end_odometer >= 0),
  add constraint trips_gross_fare_nonnegative check (gross_fare >= 0),
  add constraint trips_additional_charges_nonnegative check (additional_charges >= 0);

alter table public.fuel_transactions
  add constraint fuel_odometer_nonnegative check (odometer >= 0),
  add constraint fuel_quantity_positive check (quantity > 0),
  add constraint fuel_rate_nonnegative check (rate >= 0),
  add constraint fuel_amount_nonnegative check (amount >= 0);

alter table public.expenses
  add constraint expenses_amount_nonnegative check (amount >= 0);
