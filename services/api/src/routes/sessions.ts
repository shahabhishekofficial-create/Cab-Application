import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { assertCanStartSession, requireOpenSession, validateCloseOdometer, SessionDomainError } from '../domain/session.js';
import { startSessionSchema, closeSessionSchema } from '../domain/schemas.js';

const sessionLookupSchema = z.object({
  sessionId: z.string().uuid(),
  driverId: z.string().uuid(),
  vehicleId: z.string().uuid(),
});

export async function registerSessionRoutes(app: FastifyInstance): Promise<void> {
  app.post('/v1/sessions/validate-start', async (request, reply) => {
    const parsed = startSessionSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    // Persistence is intentionally kept behind this boundary; the database layer will supply open sessions.
    try {
      assertCanStartSession({ driverId: parsed.data.driverId, vehicleId: parsed.data.vehicleId, openSessions: [] });
      return { accepted: true, clientTransactionId: parsed.data.clientTransactionId };
    } catch (error) {
      if (error instanceof SessionDomainError) return reply.code(409).send({ error: error.code, message: error.message });
      throw error;
    }
  });

  app.post('/v1/sessions/validate-close', async (request, reply) => {
    const parsed = closeSessionSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    const query = sessionLookupSchema.safeParse({
      sessionId: parsed.data.sessionId,
      driverId: request.headers['x-driver-id'],
      vehicleId: request.headers['x-vehicle-id'],
    });
    if (!query.success) return reply.code(400).send({ error: 'IDENTITY_CONTEXT_REQUIRED', details: query.error.issues });

    try {
      // Persistence lookup is the next adapter layer. This endpoint establishes the authoritative domain boundary.
      const session = requireOpenSession({ ...query.data, sessions: [] });
      validateCloseOdometer(session, parsed.data.closeOdometer);
      return { accepted: true, sessionId: parsed.data.sessionId };
    } catch (error) {
      if (error instanceof SessionDomainError) return reply.code(409).send({ error: error.code, message: error.message });
      throw error;
    }
  });
}
