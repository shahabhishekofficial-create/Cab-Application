import { FastifyInstance } from 'fastify';
import { expenseSchema, fuelSchema, tripSchema } from '../domain/schemas.js';
import { createExpense, createFuel, createTrip } from '../db/transaction-repository.js';

function identity(request: any) {
  return { driverId: request.headers['x-driver-id'], vehicleId: request.headers['x-vehicle-id'] };
}
function errorResponse(reply: any, error: any) {
  const message = String(error?.message ?? 'TRANSACTION_FAILED');
  if (message.includes('SESSION_NOT_OPEN') || message.includes('SESSION_IDENTITY_MISMATCH')) return reply.code(409).send({ error: message });
  return reply.code(500).send({ error: 'TRANSACTION_FAILED', message });
}

export async function registerTransactionRoutes(app: FastifyInstance) {
  app.post('/v1/trips', async (request, reply) => {
    const parsed = tripSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { return await createTrip({ ...parsed.data, ...identity(request) }); } catch (e) { return errorResponse(reply, e); }
  });
  app.post('/v1/fuel', async (request, reply) => {
    const parsed = fuelSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { return await createFuel({ ...parsed.data, ...identity(request) }); } catch (e) { return errorResponse(reply, e); }
  });
  app.post('/v1/expenses', async (request, reply) => {
    const parsed = expenseSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { return await createExpense({ ...parsed.data, ...identity(request) }); } catch (e) { return errorResponse(reply, e); }
  });
}
