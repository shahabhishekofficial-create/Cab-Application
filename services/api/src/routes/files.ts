import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getSupabaseAdmin } from '../db/supabase.js';
import { authErrorResponse, requireDriver } from '../auth/driver-auth.js';

const metadataSchema = z.object({
  fileId: z.string().uuid(),
  objectPath: z.string().min(1).max(500).regex(/^[A-Za-z0-9_./-]+$/).refine((value) => !value.startsWith('/') && !value.includes('\\') && !value.split('/').some((part) => part === '..' || part === ''), 'INVALID_OBJECT_PATH'),
  mimeType: z.enum(['image/jpeg', 'image/png', 'application/octet-stream']),
  capturedAt: z.string().datetime().nullable().optional(),
});

export async function registerFileRoutes(app: FastifyInstance): Promise<void> {
  app.addContentTypeParser('application/octet-stream', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));
  app.addContentTypeParser('image/jpeg', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));
  app.addContentTypeParser('image/png', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));

  app.post('/v1/files', async (request, reply) => {
    try { await requireDriver(request); } catch (error) {
      const response = authErrorResponse(error);
      if (response) return reply.code(response.status).send(response.body);
      throw error;
    }

    const parsed = metadataSchema.safeParse({
      fileId: request.headers['x-file-id'], objectPath: request.headers['x-object-path'],
      mimeType: request.headers['content-type'], capturedAt: request.headers['x-captured-at'] || null,
    });
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    const body = request.body;
    if (!Buffer.isBuffer(body) || body.length === 0) return reply.code(400).send({ error: 'EMPTY_FILE' });
    if (body.length > 10 * 1024 * 1024) return reply.code(413).send({ error: 'FILE_TOO_LARGE' });

    const supabase = getSupabaseAdmin();
    const { data: existing } = await supabase.from('files').select('id,object_path,mime_type').eq('id', parsed.data.fileId).maybeSingle();
    if (existing && (existing.object_path !== parsed.data.objectPath || existing.mime_type !== parsed.data.mimeType)) {
      return reply.code(409).send({ error: 'FILE_ID_CONFLICT' });
    }

    const { error: uploadError } = await supabase.storage.from('cab-files').upload(parsed.data.objectPath, body, {
      contentType: parsed.data.mimeType, upsert: true,
    });
    if (uploadError) throw uploadError;

    const { error: metadataError } = await supabase.from('files').upsert({
      id: parsed.data.fileId, bucket: 'cab-files', object_path: parsed.data.objectPath, mime_type: parsed.data.mimeType,
      size_bytes: body.length, captured_at: parsed.data.capturedAt ?? null,
    }, { onConflict: 'id' });
    if (metadataError) throw metadataError;

    return reply.code(201).send({ accepted: true, fileId: parsed.data.fileId, objectPath: parsed.data.objectPath });
  });
}
