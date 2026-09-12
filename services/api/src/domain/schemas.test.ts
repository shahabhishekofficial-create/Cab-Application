import { describe, expect, it } from 'vitest';
import { closeSessionSchema, expenseSchema, fuelSchema, startSessionSchema, tripSchema } from './schemas.js';

const ids = {
  clientTransactionId: '11111111-1111-4111-8111-111111111111', sessionId: '22222222-2222-4222-8222-222222222222',
  driverId: '33333333-3333-4333-8333-333333333333', vehicleId: '44444444-4444-4444-8444-444444444444',
};
const timestamp = '2026-09-10T00:00:00.000Z';
const gps = { latitude: 21.1702, longitude: 72.8311, gpsAccuracyM: 12, gpsAt: timestamp };
function trip(overrides: Record<string, unknown> = {}) { return tripSchema.safeParse({ ...ids, startedAt: timestamp, startOdometer: 100, endOdometer: 110, grossFare: 100, status: 'COMPLETED', ...gps, ...overrides }); }
function fuel(overrides: Record<string, unknown> = {}) { return fuelSchema.safeParse({ ...ids, fuelType: 'CNG', odometer: 100, quantity: 10, unit: 'KG', rate: 90, amount: 900, recordedAt: timestamp, latitude: gps.latitude, longitude: gps.longitude, gpsAccuracyM: gps.gpsAccuracyM, ...overrides }); }
function expense(overrides: Record<string, unknown> = {}) { return expenseSchema.safeParse({ ...ids, amount: 250, recordedAt: timestamp, latitude: gps.latitude, longitude: gps.longitude, gpsAccuracyM: gps.gpsAccuracyM, ...overrides }); }
const sessionStart = { ...ids, startedAt: timestamp, startOdometer: 100, startLat: gps.latitude, startLng: gps.longitude, startAccuracyM: gps.gpsAccuracyM, startGpsAt: timestamp };
const sessionClose = { clientTransactionId: ids.clientTransactionId, sessionId: ids.sessionId, closedAt: '2026-09-10T18:00:00.000Z', closeOdometer: 250, closeLat: gps.latitude, closeLng: gps.longitude, closeAccuracyM: gps.gpsAccuracyM, closeGpsAt: '2026-09-10T17:59:30.000Z', reportedTripCount: 8, reportedIncome: 2400 };

describe('session start schema', () => {
  it('requires the stable client-generated session ID', () => { const { sessionId: _, ...withoutSessionId } = ids; expect(startSessionSchema.safeParse({ ...withoutSessionId, startedAt: timestamp, startOdometer: 100 }).success).toBe(false); });
  it('accepts a valid stable session ID with GPS evidence', () => expect(startSessionSchema.safeParse(sessionStart).success).toBe(true));
  it('rejects poor GPS accuracy', () => expect(startSessionSchema.safeParse({ ...sessionStart, startAccuracyM: 51 }).success).toBe(false));
});

describe('transaction schemas', () => {
  it('accepts a normal completed trip with GPS evidence', () => expect(trip().success).toBe(true));
  it('rejects a backward trip odometer', () => expect(trip({ endOdometer: 99 }).success).toBe(false));
  it('rejects trip end time before start time', () => expect(trip({ endedAt: '2026-09-09T23:59:00.000Z' }).success).toBe(false));
  it('defaults trip additional charges to zero', () => expect(tripSchema.parse({ ...ids, startedAt: timestamp, startOdometer: 100, endOdometer: 110, grossFare: 100, status: 'COMPLETED', ...gps }).additionalCharges).toBe(0));
  it('rejects negative trip additional charges', () => expect(trip({ additionalCharges: -1 }).success).toBe(false));
  it('rejects invalid payment methods', () => expect(trip({ paymentMethod: 'WALLET' }).success).toBe(false));
  it('rejects negative fare', () => expect(trip({ grossFare: -1 }).success).toBe(false));
  it('rejects trip GPS accuracy above 50 m', () => expect(trip({ gpsAccuracyM: 51 }).success).toBe(false));
  it('requires positive fuel quantity', () => expect(fuel({ quantity: 0, amount: 0 }).success).toBe(false));
  it('rejects negative fuel amount', () => expect(fuel({ amount: -1 }).success).toBe(false));
  it('rejects negative fuel rate and odometer', () => expect(fuel({ odometer: -1, rate: -1, amount: 90 }).success).toBe(false));
  it('rejects a fuel amount that does not match quantity × rate', () => expect(fuel({ amount: 899 }).success).toBe(false));
  it('accepts fuel amount within currency rounding tolerance', () => expect(fuel({ quantity: 3, rate: 90.01, amount: 270.03 }).success).toBe(true));
  it('rejects fuel without GPS evidence', () => expect(fuel({ latitude: undefined, longitude: undefined, gpsAccuracyM: undefined }).success).toBe(false));
  it('accepts a positive expense with GPS evidence', () => expect(expense().success).toBe(true));
  it('rejects expense without GPS evidence', () => expect(expense({ latitude: undefined, longitude: undefined, gpsAccuracyM: undefined }).success).toBe(false));
  it('rejects zero expense amounts', () => expect(expense({ amount: 0 }).success).toBe(false));
  it('rejects negative expense amounts', () => expect(expense({ amount: -1 }).success).toBe(false));
  it('rejects invalid expense payment methods', () => expect(expense({ paymentMethod: 'WALLET' }).success).toBe(false));
});

describe('session close schema', () => {
  it('requires a stable client transaction ID for retry-safe close', () => { const { clientTransactionId: _, ...withoutClientId } = ids; expect(closeSessionSchema.safeParse({ ...withoutClientId, closedAt: '2026-09-10T18:00:00.000Z', closeOdometer: 250, reportedTripCount: 8, reportedIncome: 2400 }).success).toBe(false); });
  it('rejects negative reported trip count', () => expect(closeSessionSchema.safeParse({ ...sessionClose, reportedTripCount: -1 }).success).toBe(false));
  it('accepts valid close reconciliation inputs with GPS evidence', () => expect(closeSessionSchema.safeParse(sessionClose).success).toBe(true));
  it('rejects poor close GPS accuracy', () => expect(closeSessionSchema.safeParse({ ...sessionClose, closeAccuracyM: 51 }).success).toBe(false));
});
