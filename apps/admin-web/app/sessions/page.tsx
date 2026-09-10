'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

type Session = { id: string; session_date: string; status: string; started_at: string; closed_at?: string | null; start_odometer: number; close_odometer?: number | null; driver_id: string; vehicle_id: string; reconciliations?: { status?: string | null; reported_income?: number | null; system_income?: number | null; unallocated_km?: number | null }[] };

export default function SessionsPage() {
  const [rows, setRows] = useState<Session[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API}/v1/admin/sessions?limit=100`, { credentials: 'include' })
      .then(async r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
      .then(data => setRows(data.sessions ?? []))
      .catch(e => setError(e.message ?? 'Unable to load sessions'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main style={{ maxWidth: 1400, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <p style={{ opacity: .6 }}>Cab Operations Management System</p>
      <h1>Sessions</h1>
      <p style={{ color: '#666' }}>Continuous driver work sessions, with odometer and reconciliation state.</p>
      <p><Link href="/">← Dashboard</Link></p>
      {loading && <p>Loading…</p>}
      {error && <p role="alert">Unable to load: {error}. Admin authentication is required.</p>}
      {!loading && !error && <section style={{ overflowX: 'auto', border: '1px solid #ddd', borderRadius: 12 }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1100 }}>
          <thead><tr>{['Session ID','Date','Driver','Vehicle','Start Odo','Close Odo','Status','Reconciliation','Reported ₹','System ₹','Unallocated KM'].map(h => <th key={h} style={{ textAlign:'left', padding:14, borderBottom:'1px solid #ddd' }}>{h}</th>)}</tr></thead>
          <tbody>{rows.length === 0 ? <tr><td colSpan={11} style={{ padding:20 }}>No sessions found.</td></tr> : rows.map(r => { const rec = r.reconciliations?.[0]; return <tr key={r.id}><td style={{padding:14}}>{r.id}</td><td style={{padding:14}}>{r.session_date}</td><td style={{padding:14}}>{r.driver_id}</td><td style={{padding:14}}>{r.vehicle_id}</td><td style={{padding:14}}>{r.start_odometer}</td><td style={{padding:14}}>{r.close_odometer ?? '—'}</td><td style={{padding:14}}>{r.status}</td><td style={{padding:14}}>{rec?.status ?? '—'}</td><td style={{padding:14}}>{rec?.reported_income ?? '—'}</td><td style={{padding:14}}>{rec?.system_income ?? '—'}</td><td style={{padding:14}}>{rec?.unallocated_km ?? '—'}</td></tr>; })}</tbody>
        </table>
      </section>}
    </main>
  );
}
