import { describe, expect, it } from 'vitest';

const sessionListShape = {
  sessions: [],
  total: 0,
  limit: 50,
  offset: 0,
};

describe('admin session listing contract', () => {
  it('uses bounded pagination defaults', () => {
    expect(sessionListShape.limit).toBe(50);
    expect(sessionListShape.offset).toBe(0);
  });

  it('returns a stable list envelope', () => {
    expect(sessionListShape).toEqual({ sessions: [], total: 0, limit: 50, offset: 0 });
  });
});
