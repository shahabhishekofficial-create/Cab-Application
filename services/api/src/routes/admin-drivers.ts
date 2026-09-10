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

export async function registerAdminDriverRoutes(app: FastifyInstance): Promise<void> {
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

      const { data: authData, error: authError } = await supabase.auth.admin.createUser({
        email: input.email.trim().toLowerCase(), password: input.password, email_confirm: true,
        user_metadata: { full_name: input.displayName.trim() },
      });
      if (authError || !authData.user) return reply.code(409).send({ error: 'DRIVER_AUTH_CREATE_FAILED', message: authError?.message ?? 'Unable to create driver account' });

      const userId = authData.user.id;
      let driverId: string | null = null;
      let vehicleId: string | null = existingVehicle?.id ?? null;
      let createdVehicle = false;
      try {
        const { error: userError } = await supabase.from('app_users').insert({ id: userId, role: 'DRIVER', display_name: input.displayName.trim(), phone: input.phone?.trim() || null, is_active: true });
        if (userError) throw userError;
        const { data: driver, error: driverError } = await supabase.from('drivers').insert({ user_id: userId, employee_code: input.employeeCode?.trim() || null, license_number: input.licenseNumber?.trim() || null, license_expiry: input.licenseExpiry || null }).select('id').single();
        if (driverError || !driver) throw driverError ?? new Error('DRIVER_CREATE_FAILED');
        driverId = driver.id;

        if (!vehicleId) {
          const { data: vehicle, error: vehicleError } = await supabase.from('vehicles').insert({ registration_number: registrationNumber, make: input.make.trim(), model: input.model.trim(), variant: input.variant?.trim() || null, fuel_type: input.fuelType.trim(), current_odometer: input.currentOdometer, status: 'ACTIVE' }).select('id').single();
          if (vehicleError || !vehicle) throw vehicleError ?? new Error('VEHICLE_CREATE_FAILED');
          vehicleId = vehicle.id; createdVehicle = true;
        }
        const { error: assignmentError } = await supabase.from('driver_vehicle_assignments').insert({ driver_id: driverId, vehicle_id: vehicleId });
        if (assignmentError) throw assignmentError;
      } catch (error) {
        if (driverId) await supabase.from('driver_vehicle_assignments').delete().eq('driver_id', driverId);
        if (driverId) await supabase.from('drivers').delete().eq('id', driverId);
        await supabase.from('app_users').delete().eq('id', userId);
        if (createdVehicle && vehicleId) await supabase.from('vehicles').delete().eq('id', vehicleId);
        await supabase.auth.admin.deleteUser(userId);
        throw error;
      }

      return reply.code(201).send({ driverId, userId, vehicleId, registrationNumber, createdBy: admin.userId });
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      if (error instanceof Error && error.message === 'VEHICLE_ALREADY_ASSIGNED') return reply.code(409).send({ error: error.message });
      throw error;
    }
  });
}
