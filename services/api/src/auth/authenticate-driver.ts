import { FastifyRequest } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';

export type DriverAuth = {
  userId: string;
  driverId: string;
  vehicleId: string | null;
  displayName: string;
  registrationNumber: string | null;
};

export async function authenticateDriver(request: FastifyRequest): Promise<DriverAuth> {
  const authorization = request.headers.authorization;
  const token = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!token) throw Object.assign(new Error('AUTH_REQUIRED'), { statusCode: 401 });

  const supabase = getSupabaseAdmin();
  const { data: authData, error: authError } = await supabase.auth.getUser(token);
  if (authError || !authData.user) throw Object.assign(new Error('INVALID_AUTH_TOKEN'), { statusCode: 401 });

  const { data, error } = await supabase.rpc('get_my_driver_context', { p_user_id: authData.user.id });
  if (error) throw Object.assign(new Error(error.message), { statusCode: 403 });
  if (!data?.driverId) throw Object.assign(new Error('DRIVER_PROFILE_NOT_FOUND'), { statusCode: 403 });
  if (!data?.vehicleId) throw Object.assign(new Error('NO_ACTIVE_VEHICLE_ASSIGNMENT'), { statusCode: 403 });

  return {
    userId: authData.user.id,
    driverId: data.driverId,
    vehicleId: data.vehicleId,
    displayName: data.displayName ?? '',
    registrationNumber: data.registrationNumber ?? null,
  };
}

export function authError(reply: any, error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  const status = Number((error as any)?.statusCode) || (message.includes('DRIVER_PROFILE') || message.includes('VEHICLE_ASSIGNMENT') ? 403 : 500);
  return reply.code(status).send({ error: message });
}
