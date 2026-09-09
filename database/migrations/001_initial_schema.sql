-- Cab Operations Management System
-- PostgreSQL / Supabase initial schema

create extension if not exists pgcrypto;

create type user_role as enum ('DRIVER','MANAGER','OWNER');
create type session_status as enum ('NOT_STARTED','OPEN','CLOSED');
create type vehicle_status as enum ('ACTIVE','IN_SERVICE','MAINTENANCE','INACTIVE','SOLD');
create type trip_status as enum ('COMPLETED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_DRIVER','CUSTOMER_NO_SHOW');
create type payment_method as enum ('CASH','UPI','CARD','BANK','OTHER');
create type exception_severity as enum ('INFO','WARNING','CRITICAL');
create type exception_status as enum ('OPEN','RESOLVED','IGNORED');

create table app_users (
  id uuid primary key references auth.users(id) on delete cascade,
  role user_role not null default 'DRIVER',
  display_name text not null,
  phone text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table drivers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references app_users(id),
  employee_code text unique,
  license_number text,
  license_expiry date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table vehicles (
  id uuid primary key default gen_random_uuid(),
  registration_number text not null unique,
  make text not null,
  model text not null,
  variant text,
  fuel_type text not null,
  purchase_date date,
  current_odometer numeric(12,2) not null default 0,
  status vehicle_status not null default 'ACTIVE',
  insurance_expiry date,
  puc_expiry date,
  permit_expiry date,
  fitness_expiry date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table driver_vehicle_assignments (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references drivers(id),
  vehicle_id uuid not null references vehicles(id),
  assigned_from timestamptz not null default now(),
  assigned_to timestamptz,
  created_at timestamptz not null default now(),
  constraint valid_assignment_period check (assigned_to is null or assigned_to > assigned_from)
);
create index idx_assignments_active on driver_vehicle_assignments(driver_id, vehicle_id) where assigned_to is null;

create table platforms (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table expense_categories (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references app_users(id),
  device_key text not null unique,
  model text,
  os_version text,
  app_version text,
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);

create table sessions (
  id uuid primary key default gen_random_uuid(),
  client_transaction_id uuid not null unique,
  driver_id uuid not null references drivers(id),
  vehicle_id uuid not null references vehicles(id),
  device_id uuid references devices(id),
  session_date date not null,
  status session_status not null default 'OPEN',
  started_at timestamptz not null,
  start_odometer numeric(12,2) not null,
  start_lat numeric(10,7),
  start_lng numeric(10,7),
  start_accuracy_m numeric(8,2),
  start_gps_at timestamptz,
  start_odometer_file_id uuid,
  closed_at timestamptz,
  close_odometer numeric(12,2),
  close_lat numeric(10,7),
  close_lng numeric(10,7),
  close_accuracy_m numeric(8,2),
  close_gps_at timestamptz,
  close_odometer_file_id uuid,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint session_odometer_nonnegative check (start_odometer >= 0 and (close_odometer is null or close_odometer >= 0)),
  constraint session_close_after_start check (closed_at is null or closed_at >= started_at)
);
create unique index uq_driver_vehicle_open_session on sessions(driver_id, vehicle_id) where status = 'OPEN';
create index idx_sessions_driver_status on sessions(driver_id, status);
create index idx_sessions_started_at on sessions(started_at);

create table files (
  id uuid primary key default gen_random_uuid(),
  bucket text not null,
  object_path text not null unique,
  mime_type text not null,
  size_bytes bigint,
  sha256 text,
  captured_at timestamptz,
  created_by uuid references app_users(id),
  created_at timestamptz not null default now()
);

alter table sessions
  add constraint fk_sessions_start_file foreign key (start_odometer_file_id) references files(id),
  add constraint fk_sessions_close_file foreign key (close_odometer_file_id) references files(id);

create table trips (
  id uuid primary key default gen_random_uuid(),
  client_transaction_id uuid not null unique,
  session_id uuid not null references sessions(id),
  driver_id uuid not null references drivers(id),
  vehicle_id uuid not null references vehicles(id),
  platform_id uuid references platforms(id),
  started_at timestamptz not null,
  ended_at timestamptz,
  pickup text,
  dropoff text,
  start_odometer numeric(12,2) not null,
  end_odometer numeric(12,2),
  gross_fare numeric(12,2) not null default 0,
  payment_method payment_method,
  additional_charges numeric(12,2) not null default 0,
  status trip_status not null default 'COMPLETED',
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint trip_odo_order check (end_odometer is null or end_odometer >= start_odometer),
  constraint trip_money_nonnegative check (gross_fare >= 0 and additional_charges >= 0)
);
create index idx_trips_session on trips(session_id);
create index idx_trips_started_at on trips(started_at);

create table fuel_transactions (
  id uuid primary key default gen_random_uuid(),
  client_transaction_id uuid not null unique,
  session_id uuid not null references sessions(id),
  driver_id uuid not null references drivers(id),
  vehicle_id uuid not null references vehicles(id),
  fuel_type text not null,
  odometer numeric(12,2) not null,
  quantity numeric(12,3) not null,
  unit text not null,
  rate numeric(12,2) not null,
  amount numeric(12,2) not null,
  expected_amount numeric(12,2) generated always as (quantity * rate) stored,
  payment_method payment_method,
  receipt_file_id uuid references files(id),
  latitude numeric(10,7),
  longitude numeric(10,7),
  gps_accuracy_m numeric(8,2),
  recorded_at timestamptz not null default now(),
  notes text,
  created_at timestamptz not null default now(),
  constraint fuel_positive check (quantity > 0 and rate >= 0 and amount >= 0)
);

create table expenses (
  id uuid primary key default gen_random_uuid(),
  client_transaction_id uuid not null unique,
  session_id uuid not null references sessions(id),
  driver_id uuid not null references drivers(id),
  vehicle_id uuid not null references vehicles(id),
  category_id uuid references expense_categories(id),
  amount numeric(12,2) not null,
  payment_method payment_method,
  proof_file_id uuid references files(id),
  odometer numeric(12,2),
  latitude numeric(10,7),
  longitude numeric(10,7),
  gps_accuracy_m numeric(8,2),
  recorded_at timestamptz not null default now(),
  notes text,
  created_at timestamptz not null default now(),
  constraint expense_positive check (amount >= 0)
);

create table reconciliations (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null unique references sessions(id),
  reported_trip_count integer not null default 0,
  reported_income numeric(12,2) not null default 0,
  system_trip_count integer not null default 0,
  system_income numeric(12,2) not null default 0,
  trip_count_difference integer not null default 0,
  income_difference numeric(12,2) not null default 0,
  running_km numeric(12,2) not null default 0,
  completed_trip_km numeric(12,2) not null default 0,
  cancelled_no_show_km numeric(12,2) not null default 0,
  unallocated_km numeric(12,2) not null default 0,
  status text not null default 'REVIEW',
  notes text,
  created_at timestamptz not null default now()
);

create table exceptions (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  entity_id uuid not null,
  code text not null,
  severity exception_severity not null,
  status exception_status not null default 'OPEN',
  message text not null,
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by uuid references app_users(id)
);
create index idx_exceptions_status on exceptions(status, severity);

create table vehicle_services (
  id uuid primary key default gen_random_uuid(),
  vehicle_id uuid not null references vehicles(id),
  service_date date not null,
  odometer numeric(12,2) not null,
  service_type text not null,
  workshop text,
  invoice_number text,
  total_amount numeric(12,2) not null default 0,
  parts_amount numeric(12,2) not null default 0,
  labour_amount numeric(12,2) not null default 0,
  description text,
  next_service_date date,
  next_service_odometer numeric(12,2),
  invoice_file_id uuid references files(id),
  notes text,
  created_at timestamptz not null default now()
);

create table vehicle_documents (
  id uuid primary key default gen_random_uuid(),
  vehicle_id uuid not null references vehicles(id),
  document_type text not null,
  document_number text,
  issued_on date,
  expires_on date,
  file_id uuid references files(id),
  created_at timestamptz not null default now()
);

create table audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references app_users(id),
  entity_type text not null,
  entity_id uuid not null,
  action text not null,
  old_data jsonb,
  new_data jsonb,
  reason text,
  device_id uuid references devices(id),
  ip_address inet,
  created_at timestamptz not null default now()
);
create index idx_audit_entity on audit_logs(entity_type, entity_id, created_at desc);

insert into platforms (name) values ('Uber'), ('Ola'), ('Rapido'), ('Other') on conflict do nothing;
insert into expense_categories (name) values ('Toll'), ('Parking'), ('Car Wash'), ('Repair'), ('Maintenance'), ('Fine'), ('Permit'), ('Other') on conflict do nothing;

alter table app_users enable row level security;
alter table drivers enable row level security;
alter table vehicles enable row level security;
alter table driver_vehicle_assignments enable row level security;
alter table sessions enable row level security;
alter table trips enable row level security;
alter table fuel_transactions enable row level security;
alter table expenses enable row level security;

-- Backend service role performs authoritative writes. Client policies can be added when direct Supabase access is enabled.
