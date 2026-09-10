import { describe, expect, it } from 'vitest';
import { expenseSchema, fuelSchema, startSessionSchema, tripSchema } from './schemas.js';

const ids = {
  clientTransactionId: '11111111-1111-4111-8111-111111111111',
  sessionId: '22222222-2222-4222-8222-222222222222',
  driverId: '33333333-3333-4333-8333-333333333333',
  vehicleId: '44444444-4444-4444-8444-444444444444',
};

describe('session start schema', () => {
  it('requires the stable client-generated session ID', () => {
    const { sessionId: _, ...withoutSessionId } = ids;
    const result = startSessionSchema.safeParse({
      ...withoutSessionId,
      startedAt: '2026-09-10T00:00:00.000Z',
      startOdometer: 100,
    });
    expect(result.success).toBe(false);
  });

  it('accepts a valid stable session ID', () => {
    const result = startSessionSchema.safeParse({
      ...ids,
      startedAt: '2026-09-10T00:00:00.000Z',
      startOdometer: 100,
    });
    expect(result.success).toBe(true);
  });
});

describe('transaction schemas', () => {
  it('rejects a backward trip odometer', () => {
    const result = tripSchema.safeParse({ ...ids, startedAt: '2026-09-10T00:00:00.000Z', startOdometer: 100, endOdometer: 99, grossFare: 100, status: 'COMPLETED' });
    expect(result.success).toBe(false);
  });

  it('defaults trip additional charges to zero', () => {
    const result = tripSchema.parse({ ...ids, startedAt: '2026-09-10T00:00:00.000Z', startOdometer: 100, endOdometer: 110, grossFare: 100, status: 'COMPLETED' });
    expect(result.additionalCharges).toBe(0);
  });

  it('requires positive fuel quantity', () => {
    const result = fuelSchema.safeParse({ ...ids, fuelType: 'CNG', odometer: 100, quantity: 0, unit: 'KG', rate: 90, amount: 0, recordedAt: '2026-09-10T00:00:00.000Z' });
    expect(result.success).toBe(false);
  });

  it('accepts an expense without optional proof or GPS', () => {
    const result = expenseSchema.safeParse({ ...ids, amount: 250, recordedAt: '2026-09-10T00:00:00.000Z' });
    expect(result.success).toBe(true);
  });
});
