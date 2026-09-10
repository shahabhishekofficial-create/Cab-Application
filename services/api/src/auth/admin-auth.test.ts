import { describe, expect, it } from 'vitest';
import { adminAuthErrorResponse } from './admin-auth.js';

describe('admin auth error mapping', () => {
  it('maps missing credentials to 401', () => expect(adminAuthErrorResponse(new Error('AUTH_REQUIRED'))?.status).toBe(401));
  it('maps invalid tokens to 401', () => expect(adminAuthErrorResponse(new Error('INVALID_AUTH_TOKEN'))?.status).toBe(401));
  it('maps missing admin profile to 403', () => expect(adminAuthErrorResponse(new Error('ADMIN_PROFILE_NOT_FOUND'))?.status).toBe(403));
  it('maps driver access to 403', () => expect(adminAuthErrorResponse(new Error('ADMIN_FORBIDDEN'))?.status).toBe(403));
  it('does not hide unexpected database errors', () => expect(adminAuthErrorResponse(new Error('DB_FAILURE'))).toBeNull());
});
