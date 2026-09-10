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

  const { data: driver, error: driverError } = await supabase
    .from('drivers').select('id, app_users!inner(is_active)').eq('user_id', authData.user.id).eq('app_users.is_active', true).maybeSingle();
  if (driverError) throw driverError;
  if (!driver) throw new Error('DRIVER_PROFILE_NOT_FOUND');

  const { data: assignment, error: assignmentError } = await supabase
    .from('driver_vehicle_assignments').select('vehicle_id, vehicles!inner(status)').eq('driver_id', driver.id).is('assigned_to', null).eq('vehicles.status', 'ACTIVE').order('assigned_from', { ascending: false }).limit(1).maybeSingle();
  if (assignmentError) throw assignmentError;
  if (!assignment) throw new Error('NO_ACTIVE_VEHICLE_ASSIGNMENT');

  return { userId: authData.user.id, driverId: driver.id, vehicleId: assignment.vehicle_id };
}

export function authErrorResponse(error: unknown): { status: number; body: Record<string, unknown> } | null {
  const message = String(error instanceof Error ? error.message : error);
  const known: Record<string, number> = {
    AUTH_REQUIRED: 401,
    INVALID_AUTH_TOKEN: 401,
    DRIVER_PROFILE_NOT_FOUND: 403,
    NO_ACTIVE_VEHICLE_ASSIGNMENT: 403,
  };
  return known[message] ? { status: known[message], body: { error: message } } : null;
}
