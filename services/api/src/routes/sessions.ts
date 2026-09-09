import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { closeSession as persistCloseSession, startSession as persistStartSession } from '../db/session-repository.js';
import { closeSessionSchema, startSessionSchema } from '../domain/schemas.js';

const identitySchema = z.object({
  driverId: z.string().uuid(),
  vehicleId: z.string().uuid(),
});

function mapDatabaseError(error: unknown): { status: number; body: Record<string, unknown> } | null {
  const message = error instanceof Error ? error.message : String(error);
  const known: Record<string, string> = {
    DRIVER_VEHICLE_NOT_ASSIGNED: 'DRIVER_VEHICLE_NOT_ASSIGNED',
    VEHICLE_NOT_ACTIVE: 'VEHICLE_NOT_ACTIVE',
    SESSION_ALREADY_OPEN: 'SESSION_ALREADY_OPEN',
    NO_OPEN_SESSION: 'NO_OPEN_SESSION',
    SESSION_CLOSED: 'SESSION_CLOSED',
    SESSION_DRIVER_MISMATCH: 'SESSION_DRIVER_MISMATCH',
    SESSION_VEHICLE_MISMATCH: 'SESSION_VEHICLE_MISMATCH',
    INVALID_CLOSE_ODOMETER: 'INVALID_CLOSE_ODOMETER',
    CLOSE_TIME_BEFORE_START: 'CLOSE_TIME_BEFORE_START',
  };
  const code = Object.keys(known).find((key) => message.includes(key));
  return code ? { status: 409, body: { error: known[code], message: code } } : null;
}

export async function registerSessionRoutes(app: FastifyInstance): Promise<void> {
  app.post('/v1/sessions', async (request, reply) => {
    const parsed = startSessionSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    try {
      const result = await persistStartSession(parsed.data);
      return reply.code(201).send({ accepted: true, ...result });
    } catch (error) {
      const mapped = mapDatabaseError(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });

  app.post('/v1/sessions/:sessionId/close', async (request, reply) => {
    const params = z.object({ sessionId: z.string().uuid() }).safeParse(request.params);
    const body = closeSessionSchema.safeParse(request.body);
    const identity = identitySchema.safeParse({
      driverId: request.headers['x-driver-id'],
      vehicleId: request.headers['x-vehicle-id'],
    });
    if (!params.success || !body.success || !identity.success) {
      return reply.code(400).send({ error: 'VALIDATION_ERROR', details: [
        ...(params.success ? [] : params.error.issues),
        ...(body.success ? [] : body.error.issues),
        ...(identity.success ? [] : identity.error.issues),
      ] });
    }
    if (body.data.sessionId !== params.data.sessionId) {
      return reply.code(400).send({ error: 'SESSION_ID_MISMATCH' });
    }

    try {
      const result = await persistCloseSession({
        ...body.data,
        sessionId: params.data.sessionId,
        driverId: identity.data.driverId,
        vehicleId: identity.data.vehicleId,
      });
      return reply.send({ accepted: true, ...result });
    } catch (error) {
      const mapped = mapDatabaseError(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
