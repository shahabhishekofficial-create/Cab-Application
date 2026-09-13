import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

export async function registerAdminDashboardRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/dashboard', async (request, reply) => {
    try {
      await requireAdmin(request);
      const { data, error } = await getSupabaseAdmin().rpc('get_admin_dashboard');
      if (error) throw error;
      return data;
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
