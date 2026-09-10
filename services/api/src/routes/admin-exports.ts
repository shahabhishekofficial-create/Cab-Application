import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

const datasets = {
  sessions: { table: 'sessions', columns: 'id,session_date,status,started_at,closed_at,start_odometer,close_odometer,driver_id,vehicle_id' },
  trips: { table: 'trips', columns: 'id,session_id,driver_id,vehicle_id,platform_id,start_odometer,end_odometer,gross_fare,payment_method,status,created_at' },
  fuel: { table: 'fuel_transactions', columns: 'id,session_id,driver_id,vehicle_id,fuel_type,odometer,quantity,unit,rate,amount,payment_method,created_at' },
  expenses: { table: 'expenses', columns: 'id,session_id,driver_id,vehicle_id,category_id,amount,payment_method,created_at' },
  reconciliation: { table: 'reconciliations', columns: 'id,session_id,status,reported_trip_count,reported_income,system_trip_count,system_income,unallocated_km,created_at' },
  exceptions: { table: 'exceptions', columns: 'id,session_id,type,severity,status,message,created_at' },
} as const;

type Dataset = keyof typeof datasets;
function csvValue(value: unknown): string { const text = value == null ? '' : String(value); return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text; }

export async function registerAdminExportRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/exports/:dataset', async (request, reply) => {
    try {
      await requireAdmin(request);
      const dataset = (request.params as { dataset: string }).dataset as Dataset;
      if (!(dataset in datasets)) return reply.code(404).send({ error: 'EXPORT_DATASET_NOT_FOUND' });
      const config = datasets[dataset];
      const { data, error } = await getSupabaseAdmin().from(config.table).select(config.columns).limit(10000);
      if (error) throw error;
      const rows = (data ?? []) as Record<string, unknown>[];
      const headers = config.columns.split(',');
      const csv = [headers.join(','), ...rows.map(row => headers.map(header => csvValue(row[header])).join(','))].join('\n') + '\n';
      reply.header('Content-Type', 'text/csv; charset=utf-8');
      reply.header('Content-Disposition', `attachment; filename="cab-${dataset}.csv"`);
      return reply.send(csv);
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
