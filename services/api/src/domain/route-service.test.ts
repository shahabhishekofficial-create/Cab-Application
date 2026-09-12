import { afterEach, describe, expect, it, vi } from 'vitest';
import { getRoadRoute } from './route-service.js';

describe('getRoadRoute', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('returns null when Google Routes is not configured', async () => {
    vi.stubEnv('GOOGLE_MAPS_API_KEY', '');
    await expect(getRoadRoute({ latitude: 21.17, longitude: 72.78 }, { latitude: 21.18, longitude: 72.79 })).resolves.toBeNull();
  });

  it('parses road distance, duration and polyline', async () => {
    vi.stubEnv('GOOGLE_MAPS_API_KEY', 'test-key');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        routes: [{ distanceMeters: 12345, duration: '987s', polyline: { encodedPolyline: 'abc123' } }],
      }),
    } as Response);

    await expect(getRoadRoute({ latitude: 21.17, longitude: 72.78 }, { latitude: 21.18, longitude: 72.79 })).resolves.toEqual({
      distanceMeters: 12345,
      durationSeconds: 987,
      polyline: 'abc123',
      provider: 'GOOGLE_ROUTES',
    });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toMatchObject({
      travelMode: 'DRIVE',
      computeAlternativeRoutes: false,
    });
  });

  it('accepts a zero-distance route', async () => {
    vi.stubEnv('GOOGLE_MAPS_API_KEY', 'test-key');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ routes: [{ distanceMeters: 0, duration: '0s' }] }),
    } as Response);

    await expect(getRoadRoute({ latitude: 21.17, longitude: 72.78 }, { latitude: 21.17, longitude: 72.78 })).resolves.toEqual({
      distanceMeters: 0,
      durationSeconds: 0,
      polyline: null,
      provider: 'GOOGLE_ROUTES',
    });
  });

  it('surfaces provider HTTP failures', async () => {
    vi.stubEnv('GOOGLE_MAPS_API_KEY', 'test-key');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: false, status: 403 } as Response);

    await expect(getRoadRoute({ latitude: 21.17, longitude: 72.78 }, { latitude: 21.18, longitude: 72.79 })).rejects.toThrow('ROUTE_PROVIDER_HTTP_403');
  });
});
