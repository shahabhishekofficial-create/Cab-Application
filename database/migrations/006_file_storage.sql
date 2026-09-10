-- File storage bucket and server-side metadata support.
-- The application uses the Supabase service role for authoritative uploads.

insert into storage.buckets (id, name, public)
values ('cab-files', 'cab-files', false)
on conflict (id) do nothing;

create index if not exists idx_files_created_at on files(created_at desc);
create index if not exists idx_files_bucket_path on files(bucket, object_path);
