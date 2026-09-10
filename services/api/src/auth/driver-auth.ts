import { FastifyRequest } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';

export type AuthenticatedDriver = { userId: string; driverId: string; vehicleId: string };

export async function requireDriver(request: FastifyRequest): Promise<AuthenticatedDriver> {
  const authorization = request.headers.authorization;
  const token = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!token) throw new Error('AUTH_REQUIRED');

  const supabase = getSupabaseAdmin();
  const { data: authData, error: authError } = await supabase.auth.getUser(token);
  if (authError || !authData.user) throw new Error('INVALID_AUTH_TOKEN');

  const { data, error } = await supabase.rpc('get_my_driver_context', { p_user_id: authData.user.id });
  if (error) throw error;
  if (!data?.driverId) throw new Error('DRIVER_PROFILE_NOT_FOUND');
  if (!data?.vehicleId) throw new Error('NO_ACTIVE_VEHICLE_ASSIGNMENT');

  return { userId: authData.user.id, driverId: data.driverId, vehicleId: data.vehicleId };
}

export function authErrorResponse(error: unknown): { status: number; body: Record<string, unknown> } | null {
  const message = String(error instanceof Error ? error.message : error);
  const known: Record<string, number> = { AUTH_REQUIRED: 401, INVALID_AUTH_TOKEN: 401, DRIVER_PROFILE_NOT_FOUND: 403, NO_ACTIVE_VEHICLE_ASSIGNMENT: 403 };
  return known[message] ? { status: known[message], body: { error: message } } : null;
}
