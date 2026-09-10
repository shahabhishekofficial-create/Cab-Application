import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getSupabaseAdmin } from '../db/supabase.js';

const tokenSchema = z.string().min(1);

export async function registerDriverContextRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/me/driver-context', async (request, reply) => {
    const authorization = request.headers.authorization;
    const token = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
    if (!tokenSchema.safeParse(token).success) return reply.code(401).send({ error: 'AUTH_REQUIRED' });

    const supabase = getSupabaseAdmin();
    const { data: user, error: userError } = await supabase.auth.getUser(token);
    if (userError || !user.user) return reply.code(401).send({ error: 'INVALID_AUTH_TOKEN' });

    const { data, error } = await supabase.rpc('get_my_driver_context', { p_user_id: user.user.id });
    if (error) {
      const code = error.message.includes('DRIVER_PROFILE_NOT_FOUND') ? 'DRIVER_PROFILE_NOT_FOUND' : error.message.includes('AUTH_REQUIRED') ? 'AUTH_REQUIRED' : null;
      if (code) return reply.code(code === 'AUTH_REQUIRED' ? 401 : 403).send({ error: code });
      throw error;
    }
    return reply.send({ authenticated: true, user: { id: user.user.id, email: user.user.email }, context: data });
  });
}
