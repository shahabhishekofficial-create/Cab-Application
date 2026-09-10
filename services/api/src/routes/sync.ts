import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { expenseSchema, fuelSchema, tripSchema, startSessionSchema, closeSessionSchema } from '../domain/schemas.js';
import { closeSession, startSession } from '../db/session-repository.js';
import { createExpense, createFuel, createTrip } from '../db/transaction-repository.js';

const syncItemSchema = z.object({
  type: z.enum(['SESSION_START', 'TRIP', 'FUEL', 'EXPENSE', 'SESSION_CLOSE']),
  payload: z.unknown(),
});

const syncSchema = z.object({
  transactions: z.array(syncItemSchema).min(1).max(100),
});

function identity(request: any) {
  return { driverId: request.headers['x-driver-id'], vehicleId: request.headers['x-vehicle-id'] };
}

export async function registerSyncRoutes(app: FastifyInstance) {
  app.post('/v1/sync', async (request, reply) => {
    const parsed = syncSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    const results = [];
    for (const item of parsed.data.transactions) {
      try {
        const id = identity(request);
        let result: Record<string, unknown>;
        switch (item.type) {
          case 'SESSION_START': {
            const body = startSessionSchema.parse(item.payload);
            result = await startSession({ ...body, ...id });
            break;
          }
          case 'TRIP': {
            const body = tripSchema.parse(item.payload);
            result = await createTrip({ ...body, ...id });
            break;
          }
          case 'FUEL': {
            const body = fuelSchema.parse(item.payload);
            result = await createFuel({ ...body, ...id });
            break;
          }
          case 'EXPENSE': {
            const body = expenseSchema.parse(item.payload);
            result = await createExpense({ ...body, ...id });
            break;
          }
          case 'SESSION_CLOSE': {
            const body = closeSessionSchema.parse(item.payload);
            result = await closeSession({ ...body, ...id });
            break;
          }
        }
        results.push({ type: item.type, accepted: true, result });
      } catch (error) {
        results.push({ type: item.type, accepted: false, error: String((error as Error)?.message ?? 'SYNC_FAILED') });
      }
    }

    return { accepted: results.filter((r) => r.accepted).length, rejected: results.filter((r) => !r.accepted).length, results };
  });
}
