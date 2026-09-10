import { FastifyInstance } from 'fastify';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';
import { getSupabaseAdmin } from '../db/supabase.js';

const datasets = {
  sessions: { table: 'sessions', columns: 'id,session_date,status,started_at,closed_at,start_odometer,close_odometer,driver_id,vehicle_id', dateColumn: 'session_date' },
  trips: { table: 'trips', columns: 'id,session_id,driver_id,vehicle_id,platform_id,start_odometer,end_odometer,gross_fare,payment_method,status,created_at', dateColumn: 'created_at' },
  fuel: { table: 'fuel_transactions', columns: 'id,session_id,driver_id,vehicle_id,fuel_type,odometer,quantity,unit,rate,amount,payment_method,created_at', dateColumn: 'created_at' },
  expenses: { table: 'expenses', columns: 'id,session_id,driver_id,vehicle_id,category_id,amount,payment_method,created_at', dateColumn: 'created_at' },
  reconciliation: { table: 'reconciliations', columns: 'id,session_id,status,reported_trip_count,reported_income,system_trip_count,system_income,unallocated_km,created_at', dateColumn: 'created_at' },
  exceptions: { table: 'exceptions', columns: 'id,session_id,type,severity,status,message,created_at', dateColumn: 'created_at' },
} as const;

type Dataset = keyof typeof datasets;
type ExportConfig = { table: string; columns: string; dateColumn: string };
const PAGE_SIZE = 1000;

function csvValue(value: unknown): string {
  const text = value == null ? '' : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function parseDateFilter(value: string | undefined, field: string): string | undefined {
  if (value === undefined || value.trim() === '') return undefined;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error(`INVALID_DATE_FILTER:${field}`);
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    throw new Error(`INVALID_DATE_FILTER:${field}`);
  }
  return value;
}

function nextDate(value: string): string {
  const date = new Date(`${value}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

export async function registerAdminExportRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/exports/:dataset', async (request, reply) => {
    try {
      await requireAdmin(request);
      const dataset = (request.params as { dataset: string }).dataset as Dataset;
      if (!(dataset in datasets)) return reply.code(404).send({ error: 'EXPORT_DATASET_NOT_FOUND' });
      const config = datasets[dataset] as ExportConfig;
      const query = request.query as Record<string, string | undefined>;
      const from = parseDateFilter(query.from, 'from');
      const to = parseDateFilter(query.to, 'to');
      if (from && to && from > to) return reply.code(400).send({ error: 'INVALID_DATE_RANGE' });

      const rows: unknown[] = [];
      for (let offset = 0; ; offset += PAGE_SIZE) {
        let builder = getSupabaseAdmin()
          .from(config.table)
          .select(config.columns)
          .range(offset, offset + PAGE_SIZE - 1);
        if (from) builder = builder.gte(config.dateColumn, from);
        if (to) builder = builder.lt(config.dateColumn, nextDate(to));
        const { data, error } = await builder;
        if (error) throw error;
        const page = Array.isArray(data) ? data as unknown[] : [];
        rows.push(...page);
        if (page.length < PAGE_SIZE) break;
      }

      const headers = config.columns.split(',');
      const csv = [headers.join(','), ...rows.map(row => {
        const record = row !== null && typeof row === 'object' ? row as Record<string, unknown> : {};
        return headers.map(header => csvValue(record[header])).join(',');
      })].join('\n') + '\n';
      reply.header('Content-Type', 'text/csv; charset=utf-8');
      reply.header('Content-Disposition', `attachment; filename="cab-${dataset}.csv"`);
      return reply.send(csv);
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      if (error instanceof Error && error.message.startsWith('INVALID_DATE_FILTER:')) {
        return reply.code(400).send({ error: 'INVALID_DATE_FILTER' });
      }
      throw error;
    }
  });
}
