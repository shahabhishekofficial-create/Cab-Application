import { describe, expect, it } from 'vitest';
import { isAllowedUploadMimeType, isSafeObjectPath, sessionIdFromObjectPath } from './file-policy.js';

describe('file upload policy', () => {
  it('accepts safe nested object paths', () => {
    expect(isSafeObjectPath('sessions/123/start-odometer-456.jpg')).toBe(true);
  });

  it('rejects traversal and ambiguous paths', () => {
    expect(isSafeObjectPath('../secrets.txt')).toBe(false);
    expect(isSafeObjectPath('sessions/../secrets.txt')).toBe(false);
    expect(isSafeObjectPath('/absolute/path.jpg')).toBe(false);
    expect(isSafeObjectPath('sessions//file.jpg')).toBe(false);
    expect(isSafeObjectPath('sessions\\file.jpg')).toBe(false);
  });

  it('extracts only valid session-scoped image paths', () => {
    const sessionId = '11111111-1111-4111-8111-111111111111';
    expect(sessionIdFromObjectPath(`sessions/${sessionId}/start-odometer-22222222-2222-4222-8222-222222222222.jpg`)).toBe(sessionId);
    expect(sessionIdFromObjectPath(`sessions/${sessionId}/receipt.txt`)).toBeNull();
    expect(sessionIdFromObjectPath('drivers/anything/photo.jpg')).toBeNull();
  });

  it('limits uploads to supported content types', () => {
    expect(isAllowedUploadMimeType('image/jpeg')).toBe(true);
    expect(isAllowedUploadMimeType('image/png')).toBe(true);
    expect(isAllowedUploadMimeType('application/octet-stream')).toBe(true);
    expect(isAllowedUploadMimeType('text/html')).toBe(false);
  });
});
