import Fastify from 'fastify';
import { z } from 'zod';
import { registerSessionRoutes } from './routes/sessions.js';

const app = Fastify({ logger: true });

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

await registerSessionRoutes(app);

const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: '0.0.0.0' }).catch((error) => {
  app.log.error(error);
  process.exit(1);
});
