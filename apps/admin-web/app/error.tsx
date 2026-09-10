'use client';

import { useEffect } from 'react';

type ErrorPageProps = { error: Error & { digest?: string }; reset: () => void };

export default function ErrorPage({ error, reset }: ErrorPageProps) {
  useEffect(() => {
    console.error('Admin application error:', error);
  }, [error]);

  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, fontFamily: 'system-ui' }}>
      <section style={{ maxWidth: 720, width: '100%', border: '1px solid #ddd', borderRadius: 16, padding: 24, background: '#fff' }}>
        <h1>Admin application error</h1>
        <p>The page failed to load. Use Retry once, then share the error shown below if it persists.</p>
        <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', background: '#f5f5f5', padding: 16, borderRadius: 8 }}>{error.message || 'Unknown client error'}</pre>
        <button onClick={reset} style={{ padding: '10px 16px' }}>Retry</button>
      </section>
    </main>
  );
}
