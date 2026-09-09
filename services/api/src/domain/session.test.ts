import { describe, expect, it } from 'vitest';
import {
  assertCanStartSession,
  requireOpenSession,
  SessionDomainError,
  validateCloseOdometer,
} from './session.js';

const openSession = {
  id: 'session-1',
  driverId: 'driver-1',
  vehicleId: 'vehicle-1',
  status: 'OPEN' as const,
  startOdometer: 1000,
};

describe('session domain', () => {
  it('blocks a second open session for the same driver and vehicle', () => {
    expect(() => assertCanStartSession({
      driverId: 'driver-1',
      vehicleId: 'vehicle-1',
      openSessions: [openSession],
    })).toThrowError(new SessionDomainError('SESSION_ALREADY_OPEN', 'An open session already exists for this driver and vehicle'));
  });

  it('allows a session for another vehicle', () => {
    expect(() => assertCanStartSession({
      driverId: 'driver-1',
      vehicleId: 'vehicle-2',
      openSessions: [openSession],
    })).not.toThrow();
  });

  it('requires an open session belonging to the authenticated driver and vehicle', () => {
    expect(requireOpenSession({
      sessionId: 'session-1',
      driverId: 'driver-1',
      vehicleId: 'vehicle-1',
      sessions: [openSession],
    })).toEqual(openSession);
  });

  it('rejects a closed session', () => {
    expect(() => requireOpenSession({
      sessionId: 'session-1',
      driverId: 'driver-1',
      vehicleId: 'vehicle-1',
      sessions: [{ ...openSession, status: 'CLOSED' }],
    })).toThrowError('The session is not open');
  });

  it('rejects a driver mismatch', () => {
    expect(() => requireOpenSession({
      sessionId: 'session-1',
      driverId: 'driver-2',
      vehicleId: 'vehicle-1',
      sessions: [openSession],
    })).toThrowError('Session does not belong to the authenticated driver');
  });

  it('rejects a backward close odometer', () => {
    expect(() => validateCloseOdometer(openSession, 999)).toThrowError('Close odometer cannot be less than start odometer');
  });

  it('accepts a valid close odometer', () => {
    expect(() => validateCloseOdometer(openSession, 1012)).not.toThrow();
  });
});
