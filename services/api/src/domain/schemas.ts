import { z } from 'zod';

export const uuidSchema = z.string().uuid();

const ocrFields = {
  ocrReading: z.number().finite().nonnegative().nullable().optional(),
  ocrConfidence: z.number().finite().gte(0).lte(1).nullable().optional(),
  ocrDecision: z.enum(['PASS', 'REVIEW', 'FAIL']).nullable().optional(),
  ocrRawText: z.string().max(5000).nullable().optional(),
};

export const startSessionSchema = z.object({
  clientTransactionId: uuidSchema, sessionId: uuidSchema, driverId: uuidSchema, vehicleId: uuidSchema,
  deviceId: uuidSchema.optional(), startedAt: z.string().datetime(), startOdometer: z.number().finite().nonnegative(),
  startLat: z.number().gte(-90).lte(90).nullable().optional(), startLng: z.number().gte(-180).lte(180).nullable().optional(),
  startAccuracyM: z.number().finite().nonnegative().nullable().optional(), startGpsAt: z.string().datetime().nullable().optional(),
  startOdometerFileId: uuidSchema.nullable().optional(), ...ocrFields, notes: z.string().max(2000).nullable().optional(),
});

export const tripSchema = z.object({
  clientTransactionId: uuidSchema, sessionId: uuidSchema, driverId: uuidSchema, vehicleId: uuidSchema,
  platformId: uuidSchema.nullable().optional(), startedAt: z.string().datetime(), endedAt: z.string().datetime().nullable().optional(),
  pickup: z.string().max(500).nullable().optional(), dropoff: z.string().max(500).nullable().optional(),
  startOdometer: z.number().finite().nonnegative(), endOdometer: z.number().finite().nonnegative().nullable().optional(),
  grossFare: z.number().finite().nonnegative(), paymentMethod: z.enum(['CASH','UPI','CARD','BANK','OTHER']).nullable().optional(),
  additionalCharges: z.number().finite().nonnegative().default(0), status: z.enum(['COMPLETED','CANCELLED_BY_CUSTOMER','CANCELLED_BY_DRIVER','CUSTOMER_NO_SHOW']), notes: z.string().max(2000).nullable().optional(),
}).superRefine((value, ctx) => {
  if (value.endOdometer !== null && value.endOdometer !== undefined && value.endOdometer < value.startOdometer) ctx.addIssue({ code: 'custom', path: ['endOdometer'], message: 'End odometer cannot be less than start odometer' });
  if (value.endedAt && value.endedAt < value.startedAt) ctx.addIssue({ code: 'custom', path: ['endedAt'], message: 'End time cannot be before start time' });
});

export const fuelSchema = z.object({
  clientTransactionId: uuidSchema, sessionId: uuidSchema, driverId: uuidSchema, vehicleId: uuidSchema,
  fuelType: z.string().min(1).max(50), odometer: z.number().finite().nonnegative(), quantity: z.number().finite().positive(),
  unit: z.string().min(1).max(20), rate: z.number().finite().nonnegative(), amount: z.number().finite().nonnegative(),
  paymentMethod: z.enum(['CASH','UPI','CARD','BANK','OTHER']).nullable().optional(), receiptFileId: uuidSchema.nullable().optional(),
  latitude: z.number().gte(-90).lte(90).nullable().optional(), longitude: z.number().gte(-180).lte(180).nullable().optional(),
  gpsAccuracyM: z.number().finite().nonnegative().nullable().optional(), recordedAt: z.string().datetime(), notes: z.string().max(2000).nullable().optional(),
}).superRefine((value, ctx) => {
  const expectedAmount = value.quantity * value.rate;
  if (Math.abs(value.amount - expectedAmount) > 0.01) {
    ctx.addIssue({ code: 'custom', path: ['amount'], message: 'Fuel amount must equal quantity × rate within ₹0.01' });
  }
});

export const expenseSchema = z.object({
  clientTransactionId: uuidSchema, sessionId: uuidSchema, driverId: uuidSchema, vehicleId: uuidSchema,
  categoryId: uuidSchema.nullable().optional(), amount: z.number().finite().nonnegative(), paymentMethod: z.enum(['CASH','UPI','CARD','BANK','OTHER']).nullable().optional(), proofFileId: uuidSchema.nullable().optional(),
  odometer: z.number().finite().nonnegative().nullable().optional(), latitude: z.number().gte(-90).lte(90).nullable().optional(), longitude: z.number().gte(-180).lte(180).nullable().optional(),
  gpsAccuracyM: z.number().finite().nonnegative().nullable().optional(), recordedAt: z.string().datetime(), notes: z.string().max(2000).nullable().optional(),
});

export const closeSessionSchema = z.object({
  clientTransactionId: uuidSchema, sessionId: uuidSchema, closedAt: z.string().datetime(), closeOdometer: z.number().finite().nonnegative(),
  closeLat: z.number().gte(-90).lte(90).nullable().optional(), closeLng: z.number().gte(-180).lte(180).nullable().optional(), closeAccuracyM: z.number().finite().nonnegative().nullable().optional(), closeGpsAt: z.string().datetime().nullable().optional(), closeOdometerFileId: uuidSchema.nullable().optional(),
  reportedTripCount: z.number().int().nonnegative(), reportedIncome: z.number().finite().nonnegative(), ...ocrFields, notes: z.string().max(2000).nullable().optional(),
});
