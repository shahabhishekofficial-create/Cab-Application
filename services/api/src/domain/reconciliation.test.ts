import { describe, expect, it } from 'vitest';
import { calculateReconciliation } from './reconciliation.js';

const baseTrip = { startOdometer: 1000, endOdometer: 1010, grossFare: 500, additionalCharges: 100, status: 'COMPLETED' as const };

describe('calculateReconciliation', () => {
  it('passes when reported values and kilometres reconcile', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1015, trips: [baseTrip], reportedTripCount: 1, reportedIncome: 500 });
    expect(result.status).toBe('PASS'); expect(result.systemIncome).toBe(500); expect(result.unallocatedKm).toBe(5);
  });
  it('excludes additional charges from revenue', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1010, trips: [baseTrip], reportedTripCount: 1, reportedIncome: 500 });
    expect(result.systemIncome).toBe(500); expect(result.incomeDifference).toBe(0);
  });
  it('marks negative unallocated kilometres critical', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1005, trips: [baseTrip], reportedTripCount: 1, reportedIncome: 500 });
    expect(result.status).toBe('CRITICAL');
  });
  it('marks trip-count mismatch for review', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1015, trips: [baseTrip], reportedTripCount: 2, reportedIncome: 500 });
    expect(result.status).toBe('REVIEW');
  });
  it('marks income mismatch for review', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1015, trips: [baseTrip], reportedTripCount: 1, reportedIncome: 501 });
    expect(result.status).toBe('REVIEW');
  });
  it('includes cancelled/no-show kilometres in allocation', () => {
    const result = calculateReconciliation({ startOdometer: 1000, closeOdometer: 1025, trips: [baseTrip, { startOdometer: 1010, endOdometer: 1015, grossFare: 0, additionalCharges: 0, status: 'CUSTOMER_NO_SHOW' }], reportedTripCount: 2, reportedIncome: 500 });
    expect(result.completedTripKm).toBe(10); expect(result.cancelledNoShowKm).toBe(5); expect(result.unallocatedKm).toBe(10); expect(result.status).toBe('PASS');
  });
});
