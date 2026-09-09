import { getSupabaseAdmin } from './supabase.js';

export async function createTrip(input: Record<string, unknown>) {
  const { data, error } = await getSupabaseAdmin().rpc('create_trip', {
    p_client_transaction_id: input.clientTransactionId,
    p_session_id: input.sessionId,
    p_driver_id: input.driverId,
    p_vehicle_id: input.vehicleId,
    p_platform_id: input.platformId ?? null,
    p_started_at: input.startedAt,
    p_ended_at: input.endedAt,
    p_pickup: input.pickup,
    p_dropoff: input.dropoff,
    p_start_odometer: input.startOdometer,
    p_end_odometer: input.endOdometer,
    p_gross_fare: input.grossFare,
    p_payment_method: input.paymentMethod,
    p_additional_charges: input.additionalCharges ?? 0,
    p_status: input.status,
    p_notes: input.notes ?? null,
  });
  if (error) throw error;
  return data;
}

export async function createFuel(input: Record<string, unknown>) {
  const { data, error } = await getSupabaseAdmin().rpc('create_fuel_transaction', {
    p_client_transaction_id: input.clientTransactionId, p_session_id: input.sessionId,
    p_driver_id: input.driverId, p_vehicle_id: input.vehicleId, p_fuel_type: input.fuelType,
    p_odometer: input.odometer, p_quantity: input.quantity, p_unit: input.unit,
    p_rate: input.rate, p_amount: input.amount, p_payment_method: input.paymentMethod,
    p_receipt_file_id: input.receiptFileId ?? null, p_latitude: input.latitude ?? null,
    p_longitude: input.longitude ?? null, p_accuracy_meters: input.accuracyMeters ?? null,
    p_recorded_at: input.recordedAt, p_notes: input.notes ?? null,
  });
  if (error) throw error;
  return data;
}

export async function createExpense(input: Record<string, unknown>) {
  const { data, error } = await getSupabaseAdmin().rpc('create_expense', {
    p_client_transaction_id: input.clientTransactionId, p_session_id: input.sessionId,
    p_driver_id: input.driverId, p_vehicle_id: input.vehicleId, p_category_id: input.categoryId,
    p_amount: input.amount, p_payment_method: input.paymentMethod, p_proof_file_id: input.proofFileId ?? null,
    p_odometer: input.odometer ?? null, p_latitude: input.latitude ?? null,
    p_longitude: input.longitude ?? null, p_accuracy_meters: input.accuracyMeters ?? null,
    p_recorded_at: input.recordedAt, p_notes: input.notes ?? null,
  });
  if (error) throw error;
  return data;
}
