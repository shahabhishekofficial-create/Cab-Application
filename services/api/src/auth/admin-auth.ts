import { FastifyRequest } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';

export type AuthenticatedAdmin = { userId: string; role: 'OWNER' | 'MANAGER' };

export async function requireAdmin(request: FastifyRequest): Promise<AuthenticatedAdmin> {
  const authorization = request.headers.authorization;
  const token = authorization?.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!token) throw new Error('AUTH_REQUIRED');

  const supabase = getSupabaseAdmin();
  const { data: authData, error: authError } = await supabase.auth.getUser(token);
  if (authError || !authData.user) throw new Error('INVALID_AUTH_TOKEN');

  const { data, error } = await supabase
    .from('app_users')
    .select('role,is_active')
    .eq('id', authData.user.id)
    .maybeSingle();
  if (error) throw error;
  if (!data || !data.is_active) throw new Error('ADMIN_PROFILE_NOT_FOUND');
  if (data.role !== 'OWNER' && data.role !== 'MANAGER') throw new Error('ADMIN_FORBIDDEN');

  return { userId: authData.user.id, role: data.role };
}

export function adminAuthErrorResponse(error: unknown): { status: number; body: Record<string, unknown> } | null {
  const message = String(error instanceof Error ? error.message : error);
  const known: Record<string, number> = {
    AUTH_REQUIRED: 401,
    INVALID_AUTH_TOKEN: 401,
    ADMIN_PROFILE_NOT_FOUND: 403,
    ADMIN_FORBIDDEN: 403,
  };
  return known[message] ? { status: known[message], body: { error: message } } : null;
}
