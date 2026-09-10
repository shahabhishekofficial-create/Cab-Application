import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

const dateFilterSchema = z.object({
  from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
});

function isValidDate(value: string): boolean {
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export async function registerAdminMetricsRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/metrics', async (request, reply) => {
    try {
      await requireAdmin(request);
      const filters = dateFilterSchema.safeParse(request.query ?? {});
      if (!filters.success) return reply.code(400).send({ error: 'INVALID_DATE_FILTER' });

      const { from, to } = filters.data;
      if ((from && !isValidDate(from)) || (to && !isValidDate(to))) {
        return reply.code(400).send({ error: 'INVALID_DATE_FILTER' });
      }
      if (from && to && from > to) {
        return reply.code(400).send({ error: 'INVALID_DATE_RANGE' });
      }

      const { data, error } = await getSupabaseAdmin().rpc('get_admin_metrics', {
        p_from: from ?? null,
        p_to: to ?? null,
      });
      if (error) throw error;
      return data;
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
