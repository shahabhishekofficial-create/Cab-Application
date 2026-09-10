import Fastify from 'fastify';
import { z } from 'zod';
import { registerSessionRoutes } from './routes/sessions.js';
import { registerTransactionRoutes } from './routes/transactions.js';
import { registerSyncRoutes } from './routes/sync.js';
import { registerFileRoutes } from './routes/files.js';
import { registerDriverContextRoutes } from './routes/driver-context.js';
import { registerPlatformRoutes } from './routes/platforms.js';
import { registerExpenseCategoryRoutes } from './routes/expense-categories.js';
import { registerAdminSessionRoutes } from './routes/admin-sessions.js';
import { registerAdminMetricsRoutes } from './routes/admin-metrics.js';
import { registerAdminExportRoutes } from './routes/admin-exports.js';
import { registerAdminDriverRoutes } from './routes/admin-drivers.js';

const app = Fastify({ logger: true, bodyLimit: 10 * 1024 * 1024 });

const allowedOrigins = new Set(
  (process.env.CORS_ALLOWED_ORIGINS ?? 'https://cab-application-admin.onrender.com,http://localhost:3000')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean),
);

app.addHook('onRequest', async (request, reply) => {
  const origin = request.headers.origin;
  if (origin && allowedOrigins.has(origin)) {
    reply.header('Access-Control-Allow-Origin', origin);
    reply.header('Vary', 'Origin');
    reply.header('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
    reply.header('Access-Control-Allow-Headers', 'Authorization, Content-Type');
  }
  if (request.method === 'OPTIONS') {
    if (!origin || !allowedOrigins.has(origin)) return reply.code(403).send();
    return reply.code(204).send();
  }
});

app.setErrorHandler((error, request, reply) => {
  request.log.error(error);
  if (error instanceof z.ZodError) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: error.issues });
  return reply.code(500).send({ error: 'INTERNAL_SERVER_ERROR' });
});

app.get('/health', async () => ({ status: 'ok', service: 'cab-api' }));

const transactionSchema = z.object({
  clientTransactionId: z.string().uuid(),
  vehicleId: z.string().uuid(),
  sessionId: z.string().uuid(),
});

app.post('/v1/sync/validate', async (request, reply) => {
  const parsed = transactionSchema.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
  return { accepted: true, clientTransactionId: parsed.data.clientTransactionId };
});

await registerFileRoutes(app);
await registerDriverContextRoutes(app);
await registerPlatformRoutes(app);
await registerExpenseCategoryRoutes(app);
await registerAdminSessionRoutes(app);
await registerAdminMetricsRoutes(app);
await registerAdminExportRoutes(app);
await registerAdminDriverRoutes(app);
await registerSessionRoutes(app);
await registerTransactionRoutes(app);
await registerSyncRoutes(app);

const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: '0.0.0.0' }).catch((error) => {
  app.log.error(error);
  process.exit(1);
});
