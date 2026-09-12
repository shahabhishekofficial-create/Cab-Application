type Coordinates = { latitude: number; longitude: number };
export type RouteResult = {
  distanceMeters: number;
  durationSeconds: number | null;
  polyline: string | null;
  provider: 'GOOGLE_ROUTES';
};

export async function getRoadRoute(
  origin: Coordinates,
  destination: Coordinates,
): Promise<RouteResult | null> {
  const apiKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!apiKey) return null;

  const response = await fetch('https://routes.googleapis.com/directions/v2:computeRoutes', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Goog-Api-Key': apiKey,
      'X-Goog-FieldMask': 'routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline',
    },
    body: JSON.stringify({
      origin: { location: { latLng: { latitude: origin.latitude, longitude: origin.longitude } } },
      destination: { location: { latLng: { latitude: destination.latitude, longitude: destination.longitude } } },
      travelMode: 'DRIVE',
      routingPreference: 'TRAFFIC_AWARE',
      computeAlternativeRoutes: false,
      units: 'METRIC',
    }),
  });

  if (!response.ok) throw new Error(`ROUTE_PROVIDER_HTTP_${response.status}`);
  const body = (await response.json()) as {
    routes?: Array<{
      distanceMeters?: number;
      duration?: string;
      polyline?: { encodedPolyline?: string };
    }>;
  };
  const route = body.routes?.[0];
  if (route?.distanceMeters == null || route.distanceMeters < 0) throw new Error('ROUTE_NOT_FOUND');

  const durationSeconds = route.duration ? Number.parseFloat(route.duration.replace(/s$/, '')) : null;
  return {
    distanceMeters: route.distanceMeters,
    durationSeconds: Number.isFinite(durationSeconds) ? durationSeconds : null,
    polyline: route.polyline?.encodedPolyline ?? null,
    provider: 'GOOGLE_ROUTES',
  };
}
