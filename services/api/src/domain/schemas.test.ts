import { describe, expect, it } from 'vitest';
import { closeSessionSchema, expenseSchema, fuelSchema, startSessionSchema, tripSchema } from './schemas.js';

const ids = {
  clientTransactionId: '11111111-1111-4111-8111-111111111111',
  sessionId: '22222222-2222-4222-8222-222222222222',
  driverId: '33333333-3333-4333-8333-333333333333',
  vehicleId: '44444444-4444-4444-8444-444444444444',
};
const timestamp = '2026-09-10T00:00:00.000Z';
function trip(overrides: Record<string, unknown> = {}) { return tripSchema.safeParse({ ...ids, startedAt: timestamp, startOdometer: 100, endOdometer: 110, grossFare: 100, status: 'COMPLETED', ...overrides }); }

describe('session start schema', () => {
  it('requires the stable client-generated session ID', () => { const { sessionId: _, ...withoutSessionId } = ids; expect(startSessionSchema.safeParse({ ...withoutSessionId, startedAt: timestamp, startOdometer: 100 }).success).toBe(false); });
  it('accepts a valid stable session ID', () => expect(startSessionSchema.safeParse({ ...ids, startedAt: timestamp, startOdometer: 100 }).success).toBe(true));
});

describe('transaction schemas', () => {
  it('accepts a normal completed trip', () => expect(trip().success).toBe(true));
  it('rejects a backward trip odometer', () => expect(trip({ endOdometer: 99 }).success).toBe(false));
  it('rejects trip end time before start time', () => expect(trip({ endedAt: '2026-09-09T23:59:00.000Z' }).success).toBe(false));
  it('defaults trip additional charges to zero', () => expect(tripSchema.parse({ ...ids, startedAt: timestamp, startOdometer: 100, endOdometer: 110, grossFare: 100, status: 'COMPLETED' }).additionalCharges).toBe(0));
  it('rejects negative trip additional charges', () => expect(trip({ additionalCharges: -1 }).success).toBe(false));
  it('rejects invalid payment methods', () => expect(trip({ paymentMethod: 'WALLET' }).success).toBe(false));
  it('rejects negative fare', () => expect(trip({ grossFare: -1 }).success).toBe(false));
  it('requires positive fuel quantity', () => expect(fuelSchema.safeParse({ ...ids, fuelType: 'CNG', odometer: 100, quantity: 0, unit: 'KG', rate: 90, amount: 0, recordedAt: timestamp }).success).toBe(false));
  it('rejects negative fuel amount', () => expect(fuelSchema.safeParse({ ...ids, fuelType: 'CNG', odometer: 100, quantity: 1, unit: 'KG', rate: 90, amount: -1, recordedAt: timestamp }).success).toBe(false));
  it('rejects negative fuel rate and odometer', () => expect(fuelSchema.safeParse({ ...ids, fuelType: 'CNG', odometer: -1, quantity: 1, unit: 'KG', rate: -1, amount: 90, recordedAt: timestamp }).success).toBe(false));
  it('accepts an expense without optional proof or GPS', () => expect(expenseSchema.safeParse({ ...ids, amount: 250, recordedAt: timestamp }).success).toBe(true));
  it('rejects negative expense amounts', () => expect(expenseSchema.safeParse({ ...ids, amount: -1, recordedAt: timestamp }).success).toBe(false));
  it('rejects invalid expense payment methods', () => expect(expenseSchema.safeParse({ ...ids, amount: 250, paymentMethod: 'WALLET', recordedAt: timestamp }).success).toBe(false));
});

describe('session close schema', () => {
  it('requires a stable client transaction ID for retry-safe close', () => {
    const { clientTransactionId: _, ...withoutClientId } = ids;
    expect(closeSessionSchema.safeParse({ ...withoutClientId, closedAt: '2026-09-10T18:00:00.000Z', closeOdometer: 250, reportedTripCount: 8, reportedIncome: 2400 }).success).toBe(false);
  });
  it('rejects negative reported trip count', () => expect(closeSessionSchema.safeParse({ clientTransactionId: ids.clientTransactionId, sessionId: ids.sessionId, closedAt: '2026-09-10T18:00:00.000Z', closeOdometer: 250, reportedTripCount: -1, reportedIncome: 1000 }).success).toBe(false));
  it('accepts valid close reconciliation inputs', () => expect(closeSessionSchema.safeParse({ clientTransactionId: ids.clientTransactionId, sessionId: ids.sessionId, closedAt: '2026-09-10T18:00:00.000Z', closeOdometer: 250, reportedTripCount: 8, reportedIncome: 2400 }).success).toBe(true));
});
