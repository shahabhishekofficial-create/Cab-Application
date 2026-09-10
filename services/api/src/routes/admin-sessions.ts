import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

function boundedInteger(value: string | undefined, fallback: number, min: number, max: number): number {
  if (value === undefined || value.trim() === '') return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) throw new Error('INVALID_PAGINATION');
  return Math.min(Math.max(parsed, min), max);
}

export async function registerAdminSessionRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/sessions', async (request, reply) => {
    try {
      await requireAdmin(request);
      const query = request.query as Record<string, string | undefined>;
      const limit = boundedInteger(query.limit, 50, 1, 200);
      const offset = boundedInteger(query.offset, 0, 0, Number.MAX_SAFE_INTEGER);

      let builder = getSupabaseAdmin()
        .from('sessions')
        .select('id,session_date,status,started_at,closed_at,start_odometer,close_odometer,driver_id,vehicle_id,reconciliations(status,reported_trip_count,reported_income,system_trip_count,system_income,unallocated_km)', { count: 'exact' })
        .order('started_at', { ascending: false })
        .range(offset, offset + limit - 1);

      if (query.status) builder = builder.eq('status', query.status);
      if (query.driverId) builder = builder.eq('driver_id', query.driverId);
      if (query.vehicleId) builder = builder.eq('vehicle_id', query.vehicleId);
      if (query.from) builder = builder.gte('session_date', query.from);
      if (query.to) builder = builder.lte('session_date', query.to);

      const { data, error } = await builder;
      if (error) throw error;
      return { sessions: data ?? [], total: data?.length ?? 0, limit, offset };
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      if (error instanceof Error && error.message === 'INVALID_PAGINATION') return reply.code(400).send({ error: 'INVALID_PAGINATION' });
      throw error;
    }
  });
}
