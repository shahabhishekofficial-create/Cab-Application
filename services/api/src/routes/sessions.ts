import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { closeSession as persistCloseSession, startSession as persistStartSession } from '../db/session-repository.js';
import { closeSessionSchema, startSessionSchema } from '../domain/schemas.js';
import { authErrorResponse, requireDriver } from '../auth/driver-auth.js';

function mapDatabaseError(error: unknown): { status: number; body: Record<string, unknown> } | null {
  const auth = authErrorResponse(error); if (auth) return auth;
  const message = error instanceof Error ? error.message : String(error);
  const known: Record<string, string> = {
    DRIVER_VEHICLE_NOT_ASSIGNED:'DRIVER_VEHICLE_NOT_ASSIGNED', VEHICLE_NOT_ACTIVE:'VEHICLE_NOT_ACTIVE', SESSION_ALREADY_OPEN:'SESSION_ALREADY_OPEN',
    NO_OPEN_SESSION:'NO_OPEN_SESSION', SESSION_CLOSED:'SESSION_CLOSED', SESSION_DRIVER_MISMATCH:'SESSION_DRIVER_MISMATCH', SESSION_VEHICLE_MISMATCH:'SESSION_VEHICLE_MISMATCH',
    INVALID_CLOSE_ODOMETER:'INVALID_CLOSE_ODOMETER', CLOSE_TIME_BEFORE_START:'CLOSE_TIME_BEFORE_START', ODOMETER_REGRESSION:'ODOMETER_REGRESSION',
  };
  const code=Object.keys(known).find(key=>message.includes(key)); return code?{status:409,body:{error:known[code],message}}:null;
}

export async function registerSessionRoutes(app: FastifyInstance): Promise<void> {
  app.post('/v1/sessions', async (request, reply) => {
    const parsed=startSessionSchema.safeParse(request.body); if(!parsed.success)return reply.code(400).send({error:'VALIDATION_ERROR',details:parsed.error.issues});
    try { const driver=await requireDriver(request); const result=await persistStartSession({...parsed.data,driverId:driver.driverId,vehicleId:driver.vehicleId}); return reply.code(201).send({accepted:true,...result}); }
    catch(error){const mapped=mapDatabaseError(error);if(mapped)return reply.code(mapped.status).send(mapped.body);throw error;}
  });

  app.post('/v1/sessions/:sessionId/close', async (request, reply) => {
    const params=z.object({sessionId:z.string().uuid()}).safeParse(request.params); const body=closeSessionSchema.safeParse(request.body);
    if(!params.success||!body.success)return reply.code(400).send({error:'VALIDATION_ERROR',details:[...(params.success?[]:params.error.issues),...(body.success?[]:body.error.issues)]});
    if(body.data.sessionId!==params.data.sessionId)return reply.code(400).send({error:'SESSION_ID_MISMATCH'});
    try {const driver=await requireDriver(request);const result=await persistCloseSession({...body.data,sessionId:params.data.sessionId,driverId:driver.driverId,vehicleId:driver.vehicleId});return reply.send({accepted:true,...result});}
    catch(error){const mapped=mapDatabaseError(error);if(mapped)return reply.code(mapped.status).send(mapped.body);throw error;}
  });

  app.post('/v1/sessions/:sessionId/odometer-file', async (request, reply) => {
    const params=z.object({sessionId:z.string().uuid()}).safeParse(request.params);
    const body=z.object({fileId:z.string().uuid(),objectPath:z.string().min(1).max(500).regex(/^[A-Za-z0-9_./-]+$/)}).safeParse(request.body);
    if(!params.success||!body.success)return reply.code(400).send({error:'VALIDATION_ERROR'});
    try {
      const driver=await requireDriver(request); const supabase=(await import('../db/supabase.js')).getSupabaseAdmin();
      const {data:session,error:sessionError}=await supabase.from('sessions').select('id,driver_id,vehicle_id').eq('id',params.data.sessionId).maybeSingle();
      if(sessionError)throw sessionError; if(!session)return reply.code(404).send({error:'SESSION_NOT_FOUND'});
      if(session.driver_id!==driver.driverId||session.vehicle_id!==driver.vehicleId)return reply.code(403).send({error:'SESSION_FORBIDDEN'});
      const isStart=body.data.objectPath.includes('/start-odometer-'); const isClose=body.data.objectPath.includes('/close-odometer-');
      if(!isStart&&!isClose)return reply.code(400).send({error:'INVALID_ODOMETER_FILE_PATH'});
      const {data:file,error:fileError}=await supabase.from('files').select('id,object_path').eq('id',body.data.fileId).maybeSingle();
      if(fileError)throw fileError; if(!file||file.object_path!==body.data.objectPath)return reply.code(409).send({error:'FILE_METADATA_MISMATCH'});
      const update=isStart?{start_odometer_file_id:body.data.fileId,updated_at:new Date().toISOString()}:{close_odometer_file_id:body.data.fileId,updated_at:new Date().toISOString()};
      const {error:updateError}=await supabase.from('sessions').update(update).eq('id',params.data.sessionId); if(updateError)throw updateError;
      return reply.send({accepted:true,fileId:body.data.fileId});
    } catch(error){const mapped=authErrorResponse(error);if(mapped)return reply.code(mapped.status).send(mapped.body);throw error;}
  });
}
