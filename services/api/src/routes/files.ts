import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getSupabaseAdmin } from '../db/supabase.js';
import { authErrorResponse, requireDriver } from '../auth/driver-auth.js';
import { isAllowedUploadMimeType, isSafeObjectPath, sessionIdFromObjectPath } from '../domain/file-policy.js';

const metadataSchema = z.object({
  fileId: z.string().uuid(),
  objectPath: z.string().min(1).max(500).regex(/^[A-Za-z0-9_./-]+$/).refine(isSafeObjectPath, 'INVALID_OBJECT_PATH'),
  mimeType: z.string().refine(isAllowedUploadMimeType, 'UNSUPPORTED_MIME_TYPE'),
  capturedAt: z.string().datetime().nullable().optional(),
});

export async function registerFileRoutes(app: FastifyInstance): Promise<void> {
  app.addContentTypeParser('application/octet-stream', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));
  app.addContentTypeParser('image/jpeg', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));
  app.addContentTypeParser('image/png', { parseAs: 'buffer' }, (_request, body, done) => done(null, body));

  app.post('/v1/files', async (request, reply) => {
    let driver;
    try {
      driver = await requireDriver(request);
    } catch (error) {
      const response = authErrorResponse(error);
      if (response) return reply.code(response.status).send(response.body);
      throw error;
    }

    const parsed = metadataSchema.safeParse({
      fileId: request.headers['x-file-id'],
      objectPath: request.headers['x-object-path'],
      mimeType: request.headers['content-type'],
      capturedAt: request.headers['x-captured-at'] || null,
    });
    if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });

    const sessionId = sessionIdFromObjectPath(parsed.data.objectPath);
    if (!sessionId) return reply.code(403).send({ error: 'FILE_SESSION_REQUIRED' });

    const supabase = getSupabaseAdmin();
    const { data: session, error: sessionError } = await supabase
      .from('sessions')
      .select('id,driver_id,vehicle_id')
      .eq('id', sessionId)
      .maybeSingle();
    if (sessionError) throw sessionError;
    if (!session) return reply.code(403).send({ error: 'SESSION_NOT_FOUND' });
    if (session.driver_id !== driver.driverId || session.vehicle_id !== driver.vehicleId) {
      return reply.code(403).send({ error: 'FILE_SESSION_FORBIDDEN' });
    }

    const body = request.body;
    if (!Buffer.isBuffer(body) || body.length === 0) return reply.code(400).send({ error: 'EMPTY_FILE' });
    if (body.length > 10 * 1024 * 1024) return reply.code(413).send({ error: 'FILE_TOO_LARGE' });

    const { data: existing, error: existingError } = await supabase
      .from('files')
      .select('id,object_path,mime_type')
      .eq('id', parsed.data.fileId)
      .maybeSingle();
    if (existingError) throw existingError;
    if (existing && (existing.object_path !== parsed.data.objectPath || existing.mime_type !== parsed.data.mimeType)) {
      return reply.code(409).send({ error: 'FILE_ID_CONFLICT' });
    }

    const { error: uploadError } = await supabase.storage
      .from('cab-files')
      .upload(parsed.data.objectPath, body, { contentType: parsed.data.mimeType, upsert: true });
    if (uploadError) throw uploadError;

    const { error: metadataError } = await supabase.from('files').upsert({
      id: parsed.data.fileId,
      bucket: 'cab-files',
      object_path: parsed.data.objectPath,
      mime_type: parsed.data.mimeType,
      size_bytes: body.length,
      captured_at: parsed.data.capturedAt ?? null,
    }, { onConflict: 'id' });
    if (metadataError) throw metadataError;

    return reply.code(201).send({ accepted: true, fileId: parsed.data.fileId, objectPath: parsed.data.objectPath });
  });
}
