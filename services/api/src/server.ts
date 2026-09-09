import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { z } from 'zod';

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

const port = Number(process.env.PORT ?? 3000);

const transactionSchema = z.object({
  clientTransactionId: z.string().uuid(),
  sessionId: z.string().uuid().optional(),
});

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'cab-operations-api', version: '0.1.0' });
});

app.post('/api/sync/validate', (req, res) => {
  const parsed = transactionSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ ok: false, error: 'INVALID_TRANSACTION', details: parsed.error.flatten() });
  }
  return res.json({ ok: true, accepted: true, clientTransactionId: parsed.data.clientTransactionId });
});

app.use((_req, res) => res.status(404).json({ ok: false, error: 'NOT_FOUND' }));

app.listen(port, () => {
  console.log(`Cab Operations API listening on :${port}`);
});
