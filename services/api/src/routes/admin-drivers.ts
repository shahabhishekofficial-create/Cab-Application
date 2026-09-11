import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

const createDriverSchema = z.object({
  email: z.string().email(), password: z.string().min(8).max(72), displayName: z.string().min(1).max(200),
  phone: z.string().max(30).nullable().optional(), employeeCode: z.string().max(100).nullable().optional(),
  licenseNumber: z.string().max(100).nullable().optional(), licenseExpiry: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
  registrationNumber: z.string().min(1).max(50), make: z.string().min(1).max(100), model: z.string().min(1).max(100),
  variant: z.string().max(100).nullable().optional(), fuelType: z.string().min(1).max(50).default('CNG'),
  currentOdometer: z.number().finite().nonnegative().default(0),
}).strict();

const updateDriverSchema = z.object({
  displayName: z.string().min(1).max(200), phone: z.string().max(30).nullable(),
  employeeCode: z.string().max(100).nullable(), licenseNumber: z.string().max(100).nullable(),
  licenseExpiry: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable(), isActive: z.boolean(),
}).strict();

const idSchema = z.object({ driverId: z.string().uuid() });

export async function registerAdminDriverRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/drivers', async (request, reply) => {
    try {
      await requireAdmin(request);
      const supabase = getSupabaseAdmin();
      const { data: drivers, error: driverError } = await supabase.from('drivers').select('id,user_id,employee_code,license_number,license_expiry,created_at,updated_at').order('created_at', { ascending: false });
      if (driverError) throw driverError;
      const driverIds = (drivers ?? []).map(d => d.id);
      const userIds = (drivers ?? []).map(d => d.user_id);
      if (!driverIds.length) return { drivers: [] };
      const [{ data: users, error: userError }, { data: assignments, error: assignmentError }] = await Promise.all([
        supabase.from('app_users').select('id,display_name,phone,is_active').in('id', userIds),
        supabase.from('driver_vehicle_assignments').select('driver_id,vehicle_id,assigned_from').in('driver_id', driverIds).is('assigned_to', null),
      ]);
      if (userError) throw userError;
      if (assignmentError) throw assignmentError;
      const vehicleIds = [...new Set((assignments ?? []).map(a => a.vehicle_id))];
      const { data: vehicles, error: vehicleError } = vehicleIds.length
        ? await supabase.from('vehicles').select('id,registration_number,make,model,variant,fuel_type,current_odometer,status').in('id', vehicleIds)
        : { data: [], error: null };
      if (vehicleError) throw vehicleError;
      const { data: authUsers, error: authError } = await supabase.auth.admin.listUsers({ page: 1, perPage: 1000 });
      if (authError) throw authError;
      const userMap = new Map((users ?? []).map(u => [u.id, u]));
      const authMap = new Map((authUsers.users ?? []).map(u => [u.id, u]));
      const vehicleMap = new Map((vehicles ?? []).map(v => [v.id, v]));
      const assignmentMap = new Map((assignments ?? []).map(a => [a.driver_id, a]));
      return { drivers: (drivers ?? []).map(d => ({ ...d, user: userMap.get(d.user_id) ?? null, email: authMap.get(d.user_id)?.email ?? null, vehicle: vehicleMap.get(assignmentMap.get(d.id)?.vehicle_id ?? '') ?? null })) };
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });

  app.patch('/v1/admin/drivers/:driverId', async (request, reply) => {
    try {
      const admin = await requireAdmin(request);
      const params = idSchema.safeParse(request.params);
      const parsed = updateDriverSchema.safeParse(request.body);
      if (!params.success || !parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: [...(params.success ? [] : params.error.issues), ...(parsed.success ? [] : parsed.error.issues)] });

      const input = parsed.data;
      const supabase = getSupabaseAdmin();
      const { data, error } = await supabase.rpc('update_driver_profile', {
        p_driver_id: params.data.driverId,
        p_display_name: input.displayName.trim(),
        p_phone: input.phone?.trim() || null,
        p_employee_code: input.employeeCode?.trim() || null,
        p_license_number: input.licenseNumber?.trim() || null,
        p_license_expiry: input.licenseExpiry || null,
        p_is_active: input.isActive,
        p_actor_user_id: admin.userId,
      });
      if (error) {
        if (error.message.includes('DRIVER_NOT_FOUND')) return reply.code(404).send({ error: 'DRIVER_NOT_FOUND' });
        throw error;
      }
      return data;
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });

  app.post('/v1/admin/drivers', async (request, reply) => {
    try {
      const admin = await requireAdmin(request);
      const parsed = createDriverSchema.safeParse(request.body);
      if (!parsed.success) return reply.code(400).send({ error: 'VALIDATION_ERROR', details: parsed.error.issues });
      const input = parsed.data;
      const supabase = getSupabaseAdmin();
      const registrationNumber = input.registrationNumber.trim().toUpperCase();
      const { data: existingVehicle, error: vehicleLookupError } = await supabase.from('vehicles').select('id,status').eq('registration_number', registrationNumber).maybeSingle();
      if (vehicleLookupError) throw vehicleLookupError;
      if (existingVehicle && existingVehicle.status !== 'ACTIVE') return reply.code(409).send({ error: 'VEHICLE_NOT_ACTIVE' });
      if (existingVehicle) {
        const { count, error } = await supabase.from('driver_vehicle_assignments').select('id', { count: 'exact', head: true }).eq('vehicle_id', existingVehicle.id).is('assigned_to', null);
        if (error) throw error;
        if ((count ?? 0) > 0) return reply.code(409).send({ error: 'VEHICLE_ALREADY_ASSIGNED' });
      }
      const { data: authData, error: authError } = await supabase.auth.admin.createUser({ email: input.email.trim().toLowerCase(), password: input.password, email_confirm: true, user_metadata: { full_name: input.displayName.trim() } });
      if (authError || !authData.user) return reply.code(409).send({ error: 'DRIVER_AUTH_CREATE_FAILED', message: authError?.message ?? 'Unable to create driver account' });
      const userId = authData.user.id; let driverId: string | null = null; let vehicleId: string | null = existingVehicle?.id ?? null; let createdVehicle = false;
      try {
        const { error: userError } = await supabase.from('app_users').insert({ id: userId, role: 'DRIVER', display_name: input.displayName.trim(), phone: input.phone?.trim() || null, is_active: true });
        if (userError) throw userError;
        const { data: driver, error: driverError } = await supabase.from('drivers').insert({ user_id: userId, employee_code: input.employeeCode?.trim() || null, license_number: input.licenseNumber?.trim() || null, license_expiry: input.licenseExpiry || null }).select('id').single();
        if (driverError || !driver) throw driverError ?? new Error('DRIVER_CREATE_FAILED'); driverId = driver.id;
        if (!vehicleId) { const { data: vehicle, error: vehicleError } = await supabase.from('vehicles').insert({ registration_number: registrationNumber, make: input.make.trim(), model: input.model.trim(), variant: input.variant?.trim() || null, fuel_type: input.fuelType.trim(), current_odometer: input.currentOdometer, status: 'ACTIVE' }).select('id').single(); if (vehicleError || !vehicle) throw vehicleError ?? new Error('VEHICLE_CREATE_FAILED'); vehicleId = vehicle.id; createdVehicle = true; }
        const { error: assignmentError } = await supabase.from('driver_vehicle_assignments').insert({ driver_id: driverId, vehicle_id: vehicleId }); if (assignmentError) throw assignmentError;
      } catch (error) { if (driverId) await supabase.from('driver_vehicle_assignments').delete().eq('driver_id', driverId); if (driverId) await supabase.from('drivers').delete().eq('id', driverId); await supabase.from('app_users').delete().eq('id', userId); if (createdVehicle && vehicleId) await supabase.from('vehicles').delete().eq('id', vehicleId); await supabase.auth.admin.deleteUser(userId); throw error; }
      return reply.code(201).send({ driverId, userId, vehicleId, registrationNumber, createdBy: admin.userId });
    } catch (error) { const mapped = adminAuthErrorResponse(error); if (mapped) return reply.code(mapped.status).send(mapped.body); if (error instanceof Error && error.message === 'VEHICLE_ALREADY_ASSIGNED') return reply.code(409).send({ error: error.message }); throw error; }
  });
}
