export type SessionStatus = 'NOT_STARTED' | 'OPEN' | 'CLOSED';

export interface SessionLike {
  id: string;
  driverId: string;
  vehicleId: string;
  status: SessionStatus;
  startOdometer: number;
  closeOdometer?: number | null;
}

export type SessionErrorCode =
  | 'NO_OPEN_SESSION'
  | 'MULTIPLE_OPEN_SESSIONS'
  | 'SESSION_ALREADY_OPEN'
  | 'SESSION_CLOSED'
  | 'SESSION_DRIVER_MISMATCH'
  | 'SESSION_VEHICLE_MISMATCH'
  | 'INVALID_CLOSE_ODOMETER';

export class SessionDomainError extends Error {
  constructor(public readonly code: SessionErrorCode, message: string) {
    super(message);
    this.name = 'SessionDomainError';
  }
}

export function assertCanStartSession(input: {
  driverId: string;
  vehicleId: string;
  openSessions: SessionLike[];
}): void {
  const conflicting = input.openSessions.filter(
    (session) => session.driverId === input.driverId && session.vehicleId === input.vehicleId && session.status === 'OPEN',
  );

  if (conflicting.length > 0) {
    throw new SessionDomainError('SESSION_ALREADY_OPEN', 'An open session already exists for this driver and vehicle');
  }
}

export function requireOpenSession(input: {
  sessionId: string;
  driverId: string;
  vehicleId: string;
  sessions: SessionLike[];
}): SessionLike {
  const matches = input.sessions.filter((session) => session.id === input.sessionId);

  if (matches.length === 0) {
    throw new SessionDomainError('NO_OPEN_SESSION', 'No matching session was found');
  }
  if (matches.length > 1) {
    throw new SessionDomainError('MULTIPLE_OPEN_SESSIONS', 'Multiple matching sessions were found; admin resolution is required');
  }

  const session = matches[0];
  if (session.status !== 'OPEN') {
    throw new SessionDomainError('SESSION_CLOSED', 'The session is not open');
  }
  if (session.driverId !== input.driverId) {
    throw new SessionDomainError('SESSION_DRIVER_MISMATCH', 'Session does not belong to the authenticated driver');
  }
  if (session.vehicleId !== input.vehicleId) {
    throw new SessionDomainError('SESSION_VEHICLE_MISMATCH', 'Session does not belong to the selected vehicle');
  }

  return session;
}

export function validateCloseOdometer(session: SessionLike, closeOdometer: number): void {
  if (!Number.isFinite(closeOdometer) || closeOdometer < session.startOdometer) {
    throw new SessionDomainError('INVALID_CLOSE_ODOMETER', 'Close odometer cannot be less than start odometer');
  }
}
