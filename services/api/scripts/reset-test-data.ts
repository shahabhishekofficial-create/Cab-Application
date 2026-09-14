import { createClient } from '@supabase/supabase-js';

const TEST_DRIVER_ID = '117674b6-7ec1-4943-9989-3dc297e7bda5';
const TEST_VEHICLE_ID = 'd39c52a4-2e74-4ced-9f28-ea9d593f01b5';
const TEST_REGISTRATION = 'GJ01NT0088';
const CONFIRMATION = 'RESET_GJ01NT0088';

const url = process.env.SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!url || !serviceKey) throw new Error('SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required');

const sinceArg = process.argv.find((arg) => arg.startsWith('--since='))?.slice('--since='.length);
const confirm = process.argv.includes('--confirm-reset');
if (!sinceArg || Number.isNaN(Date.parse(sinceArg))) {
  throw new Error('Refusing reset: provide --since=<ISO timestamp>');
}
if (!confirm) throw new Error(`Refusing reset: add --confirm-reset for ${TEST_REGISTRATION}`);

const supabase = createClient(url, serviceKey, { auth: { persistSession: false } });
console.log(`TEST RESET: driver=${TEST_DRIVER_ID} vehicle=${TEST_REGISTRATION} since=${sinceArg}`);

const { data, error } = await supabase.rpc('reset_test_driver_data', {
  p_driver_id: TEST_DRIVER_ID,
  p_vehicle_id: TEST_VEHICLE_ID,
  p_since: sinceArg,
  p_confirmation: CONFIRMATION,
});
if (error) throw new Error(`Database reset failed: ${error.message}`);

const result = data as { storage_files?: Array<{ bucket: string; object_path: string }> };
for (const file of result.storage_files ?? []) {
  const { error: storageError } = await supabase.storage.from(file.bucket).remove([file.object_path]);
  if (storageError) console.warn(`Storage cleanup failed for ${file.bucket}/${file.object_path}: ${storageError.message}`);
}

console.log(JSON.stringify(data, null, 2));
console.log('TEST RESET COMPLETE. No tables outside the validated test driver + GJ01NT0088 scope were targeted.');
