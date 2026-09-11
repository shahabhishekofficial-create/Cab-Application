begin;

-- Historical assignments remain unrestricted, but an active driver/vehicle can
-- only have one current assignment. This prevents ambiguous driver context.
create unique index if not exists uq_active_driver_assignment
  on public.driver_vehicle_assignments(driver_id)
  where assigned_to is null;

create unique index if not exists uq_active_vehicle_assignment
  on public.driver_vehicle_assignments(vehicle_id)
  where assigned_to is null;

commit;
