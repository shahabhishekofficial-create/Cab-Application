insert into public.platforms (id, name, is_active)
select gen_random_uuid(), v.name, true
from (values
  ('Ola'),
  ('Uber'),
  ('Bharat Taxi'),
  ('Rapido'),
  ('Bigly'),
  ('Personal Use'),
  ('Private Ride')
) as v(name)
where not exists (select 1 from public.platforms p where lower(p.name)=lower(v.name));
