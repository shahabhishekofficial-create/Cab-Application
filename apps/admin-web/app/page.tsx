'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getSupabaseBrowserClient } from '../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';
type Session = { status: string; start_odometer: number; close_odometer?: number | null; reconciliations?: { system_income?: number | null }[] };

export default function Dashboard() {
  const [email, setEmail] = useState('');
  const [checkingAuth, setCheckingAuth] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sessions, setSessions] = useState<Session[]>([]);
  const supabase = getSupabaseBrowserClient();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) { window.location.replace('/login'); return; }
      setEmail(session.user.email ?? '');
      try {
        const response = await fetch(`${API}/v1/admin/sessions?limit=200`, { headers: { Authorization: `Bearer ${session.access_token}` } });
        if (response.status === 401 || response.status === 403) { await supabase.auth.signOut(); window.location.replace('/login'); return; }
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        if (!cancelled) setSessions(data.sessions ?? []);
      } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : 'Unable to load dashboard'); }
      finally { if (!cancelled) { setLoading(false); setCheckingAuth(false); } }
    })();
    return () => { cancelled = true; };
  }, [supabase]);

  async function signOut() { await supabase.auth.signOut(); window.location.replace('/login'); }

  const trips = 0;
  const revenue = sessions.reduce((sum, s) => sum + (s.reconciliations?.[0]?.system_income ?? 0), 0);
  const runningKm = sessions.reduce((sum, s) => sum + (s.close_odometer == null ? 0 : Math.max(0, s.close_odometer - s.start_odometer)), 0);
  const cards = [['Sessions', String(sessions.length)], ['System Revenue', `₹${revenue.toFixed(2)}`], ['Running KM', `${runningKm.toFixed(1)} km`], ['Open Sessions', String(sessions.filter(s => s.status === 'OPEN').length)], ['Trips', String(trips)], ['Data status', error ? 'Error' : loading ? 'Loading…' : 'Live']];

  if (checkingAuth) return <main style={{ padding: 32, fontFamily: 'system-ui' }}>Checking admin session…</main>;

  return (
    <main style={{ maxWidth: 1100, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <header style={{ marginBottom: 32, display:'flex', justifyContent:'space-between', gap:20, alignItems:'start' }}>
        <div><p style={{ margin: 0, opacity: .6 }}>Cab Operations Management System</p><h1 style={{ marginTop: 8 }}>Operations Dashboard</h1><small>{email}</small></div>
        <button onClick={signOut}>Sign out</button>
      </header>
      {error && <p role="alert">Dashboard data unavailable: {error}</p>}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 16 }}>{cards.map(([label, value]) => <article key={label} style={{ border: '1px solid #ddd', borderRadius: 12, padding: 20 }}><div style={{ opacity: .65 }}>{label}</div><strong style={{ display: 'block', fontSize: 26, marginTop: 8 }}>{value}</strong></article>)}</section>
      <section style={{ marginTop: 32, display:'flex', gap:12, flexWrap:'wrap' }}><Link href="/sessions" style={{ border:'1px solid #ddd', borderRadius:10, padding:'10px 14px', textDecoration:'none' }}>Sessions</Link><Link href="/exports" style={{ border:'1px solid #ddd', borderRadius:10, padding:'10px 14px', textDecoration:'none' }}>Exports</Link></section>
    </main>
  );
}
