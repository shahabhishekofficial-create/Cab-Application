import { describe, expect, it } from 'vitest';
import { isAllowedUploadMimeType, isSafeObjectPath } from './file-policy.js';

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

  it('limits uploads to supported content types', () => {
    expect(isAllowedUploadMimeType('image/jpeg')).toBe(true);
    expect(isAllowedUploadMimeType('image/png')).toBe(true);
    expect(isAllowedUploadMimeType('application/octet-stream')).toBe(true);
    expect(isAllowedUploadMimeType('text/html')).toBe(false);
  });
});
