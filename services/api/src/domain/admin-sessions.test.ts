import { describe, expect, it } from 'vitest';

function boundedPagination(limitValue?: string, offsetValue?: string) {
  const parsedLimit = Number(limitValue ?? 50);
  const parsedOffset = Number(offsetValue ?? 0);
  return {
    limit: Math.min(Math.max(Number.isFinite(parsedLimit) ? parsedLimit : 50, 1), 200),
    offset: Math.max(Number.isFinite(parsedOffset) ? parsedOffset : 0, 0),
  };
}

describe('admin session listing contract', () => {
  it('uses bounded pagination defaults', () => {
    expect(boundedPagination()).toEqual({ limit: 50, offset: 0 });
  });

  it('caps oversized limits and rejects negative offsets', () => {
    expect(boundedPagination('9999', '-20')).toEqual({ limit: 200, offset: 0 });
  });

  it('falls back safely for non-numeric values', () => {
    expect(boundedPagination('abc', 'xyz')).toEqual({ limit: 50, offset: 0 });
  });

  it('returns a stable list envelope', () => {
    const response = { sessions: [], total: 0, ...boundedPagination() };
    expect(response).toEqual({ sessions: [], total: 0, limit: 50, offset: 0 });
  });
});
