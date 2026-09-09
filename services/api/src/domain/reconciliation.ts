export type ReconciliationStatus = 'PASS' | 'REVIEW' | 'CRITICAL';

export interface TripLike {
  startOdometer: number;
  endOdometer?: number | null;
  grossFare: number;
  additionalCharges: number;
  status: 'COMPLETED' | 'CANCELLED_BY_CUSTOMER' | 'CANCELLED_BY_DRIVER' | 'CUSTOMER_NO_SHOW';
}

export function tripKm(trip: TripLike): number {
  if (trip.endOdometer == null) return 0;
  return Math.max(0, trip.endOdometer - trip.startOdometer);
}

export function calculateReconciliation(input: {
  startOdometer: number;
  closeOdometer: number;
  trips: TripLike[];
  reportedTripCount: number;
  reportedIncome: number;
}): {
  systemTripCount: number;
  systemIncome: number;
  tripCountDifference: number;
  incomeDifference: number;
  runningKm: number;
  completedTripKm: number;
  cancelledNoShowKm: number;
  unallocatedKm: number;
  status: ReconciliationStatus;
} {
  const systemTripCount = input.trips.length;
  const systemIncome = input.trips.reduce((sum, trip) => sum + trip.grossFare, 0);
  const runningKm = input.closeOdometer - input.startOdometer;
  const completedTripKm = input.trips
    .filter((trip) => trip.status === 'COMPLETED')
    .reduce((sum, trip) => sum + tripKm(trip), 0);
  const cancelledNoShowKm = input.trips
    .filter((trip) => trip.status !== 'COMPLETED')
    .reduce((sum, trip) => sum + tripKm(trip), 0);
  const unallocatedKm = runningKm - completedTripKm - cancelledNoShowKm;
  const tripCountDifference = input.reportedTripCount - systemTripCount;
  const incomeDifference = input.reportedIncome - systemIncome;

  let status: ReconciliationStatus = 'PASS';
  if (runningKm < 0 || unallocatedKm < 0) status = 'CRITICAL';
  else if (tripCountDifference !== 0 || Math.abs(incomeDifference) >= 1) status = 'REVIEW';

  return {
    systemTripCount,
    systemIncome,
    tripCountDifference,
    incomeDifference,
    runningKm,
    completedTripKm,
    cancelledNoShowKm,
    unallocatedKm,
    status,
  };
}
