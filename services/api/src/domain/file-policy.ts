const allowedMimeTypes = new Set(['image/jpeg', 'image/png', 'application/octet-stream']);

export function isSafeObjectPath(value: string): boolean {
  if (!value || value.startsWith('/') || value.includes('\\')) return false;
  const parts = value.split('/');
  return parts.every((part) => part.length > 0 && part !== '.' && part !== '..');
}

export function isAllowedUploadMimeType(value: string): boolean {
  return allowedMimeTypes.has(value);
}
