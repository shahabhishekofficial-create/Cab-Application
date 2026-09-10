import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

export async function registerAdminSessionRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/sessions', async (request, reply) => {
    try {
      await requireAdmin(request);
      const query = request.query as Record<string, string | undefined>;
      const limit = Math.min(Math.max(Number(query.limit ?? 50), 1), 200);
      const offset = Math.max(Number(query.offset ?? 0), 0);

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

      const { data, error, count } = await builder;
      if (error) throw error;
      return { sessions: data ?? [], total: count ?? 0, limit, offset };
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
