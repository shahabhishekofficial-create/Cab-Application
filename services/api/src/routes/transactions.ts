import { FastifyInstance } from 'fastify';
import { expenseSchema, fuelSchema, tripSchema } from '../domain/schemas.js';
import { createExpense, createFuel, createTrip } from '../db/transaction-repository.js';
import { authErrorResponse, requireDriver } from '../auth/driver-auth.js';

function errorResponse(reply: any, error: unknown) {
  const auth = authErrorResponse(error);
  if (auth) return reply.code(auth.status).send(auth.body);

  const message = error instanceof Error ? error.message : String(error);
  const known = [
    'DRIVER_VEHICLE_NOT_ASSIGNED', 'VEHICLE_NOT_ACTIVE', 'SESSION_NOT_OPEN', 'SESSION_CLOSED',
    'SESSION_DRIVER_MISMATCH', 'SESSION_VEHICLE_MISMATCH', 'SESSION_IDENTITY_MISMATCH',
    'ODOMETER_REGRESSION', 'FUEL_AMOUNT_MISMATCH', 'SESSION_NOT_FOUND',
  ];
  const code = known.find((value) => message.includes(value));
  if (code) return reply.code(409).send({ error: code, message });

  return reply.code(500).send({ error: 'TRANSACTION_FAILED' });
}

export async function registerTransactionRoutes(app: FastifyInstance) {
  app.post('/v1/trips', async (request, reply) => {
    const parsed = tripSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { const driver = await requireDriver(request); return await createTrip({ ...parsed.data, driverId: driver.driverId, vehicleId: driver.vehicleId }); } catch (e) { return errorResponse(reply, e); }
  });
  app.post('/v1/fuel', async (request, reply) => {
    const parsed = fuelSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { const driver = await requireDriver(request); return await createFuel({ ...parsed.data, driverId: driver.driverId, vehicleId: driver.vehicleId }); } catch (e) { return errorResponse(reply, e); }
  });
  app.post('/v1/expenses', async (request, reply) => {
    const parsed = expenseSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
    try { const driver = await requireDriver(request); return await createExpense({ ...parsed.data, driverId: driver.driverId, vehicleId: driver.vehicleId }); } catch (e) { return errorResponse(reply, e); }
  });
}
