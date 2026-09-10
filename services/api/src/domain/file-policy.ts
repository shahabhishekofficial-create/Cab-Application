const allowedMimeTypes = new Set(['image/jpeg', 'image/png', 'application/octet-stream']);

export function isSafeObjectPath(value: string): boolean {
  if (!value || value.startsWith('/') || value.includes('\\')) return false;
  const parts = value.split('/');
  return parts.every((part) => part.length > 0 && part !== '.' && part !== '..');
}

export function sessionIdFromObjectPath(value: string): string | null {
  const match = /^sessions\/([0-9a-fA-F-]{36})\/(?:[A-Za-z0-9_-]+)\.(?:jpg|jpeg|png)$/i.exec(value);
  return match?.[1] ?? null;
}

export function isAllowedUploadMimeType(value: string): boolean {
  return allowedMimeTypes.has(value);
}
