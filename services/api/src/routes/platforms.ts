import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { authErrorResponse, requireDriver } from '../auth/driver-auth.js';

export async function registerPlatformRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/platforms', async (request, reply) => {
    try {
      await requireDriver(request);
      const { data, error } = await getSupabaseAdmin()
        .from('platforms')
        .select('id,name,code,is_active')
        .eq('is_active', true)
        .order('name');
      if (error) throw error;
      return { platforms: data ?? [] };
    } catch (error) {
      const mapped = authErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
