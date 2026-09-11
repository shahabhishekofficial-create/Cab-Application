'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';
type Session = { id: string; session_date: string; status: string; started_at: string; closed_at?: string | null; start_odometer: number; close_odometer?: number | null; driver_id: string; vehicle_id: string; reconciliations?: { status?: string | null; reported_income?: number | null; system_income?: number | null; unallocated_km?: number | null }[] };

export default function SessionsPage() {
  const router = useRouter();
  const [rows, setRows] = useState<Session[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [offset, setOffset] = useState(0);
  const limit = 100;

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true); setError('');
      try {
        const { data: { session } } = await getSupabaseBrowserClient().auth.getSession();
        if (!session?.access_token) { router.replace('/login'); return; }
        const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
        if (status) params.set('status', status); if (from) params.set('from', from); if (to) params.set('to', to);
        const response = await fetch(`${API}/v1/admin/sessions?${params.toString()}`, { headers: { Authorization: `Bearer ${session.access_token}` } });
        if (response.status === 401 || response.status === 403) { await getSupabaseBrowserClient().auth.signOut(); router.replace('/login'); return; }
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
        if (!cancelled) { setRows(data.sessions ?? []); setTotal(Number(data.total ?? 0)); }
      } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : 'Unable to load sessions'); }
      finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
  }, [router, status, from, to, offset]);

  function resetFilters() { setStatus(''); setFrom(''); setTo(''); setOffset(0); }
  return <main style={{ maxWidth: 1400, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
    <p style={{ opacity: .6 }}>Cab Operations Management System</p><h1>Sessions</h1><p style={{ color: '#666' }}>Continuous driver work sessions, odometer evidence and reconciliation.</p><p><Link href="/">← Dashboard</Link></p>
    <section style={{ display:'flex', gap:12, flexWrap:'wrap', alignItems:'end', margin:'20px 0' }}>
      <label>Status<select value={status} onChange={e => {setStatus(e.target.value);setOffset(0)}} style={{ display:'block', padding:8, marginTop:5 }}><option value="">All</option><option value="OPEN">OPEN</option><option value="CLOSED">CLOSED</option></select></label>
      <label>From<input type="date" value={from} onChange={e => {setFrom(e.target.value);setOffset(0)}} style={{ display:'block', padding:8, marginTop:5 }} /></label>
      <label>To<input type="date" value={to} onChange={e => {setTo(e.target.value);setOffset(0)}} style={{ display:'block', padding:8, marginTop:5 }} /></label>
      <button type="button" onClick={resetFilters} style={{ padding:9 }}>Clear filters</button>
    </section>
    {loading && <p>Loading…</p>}{error && <p role="alert">Unable to load: {error}</p>}
    {!loading && !error && <p style={{ color:'#666' }}>{total === 0 ? 'No sessions found.' : `Showing ${offset + 1}–${Math.min(offset + rows.length,total)} of ${total}`}</p>}
    {!loading && !error && <section style={{ overflowX: 'auto', border: '1px solid #ddd', borderRadius: 12 }}><table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1200 }}><thead><tr>{['Session ID','Date','Driver','Vehicle','Start Odo','Close Odo','Status','Reconciliation','Reported ₹','System ₹','Unallocated KM','Open'].map(h => <th key={h} style={{ textAlign:'left', padding:14, borderBottom:'1px solid #ddd' }}>{h}</th>)}</tr></thead><tbody>{rows.length === 0 ? <tr><td colSpan={12} style={{ padding:20 }}>No sessions found.</td></tr> : rows.map(r => { const rec = r.reconciliations?.[0]; return <tr key={r.id}><td style={{padding:14,fontFamily:'monospace'}}>{r.id}</td><td style={{padding:14}}>{r.session_date}</td><td style={{padding:14,fontFamily:'monospace'}}>{r.driver_id}</td><td style={{padding:14,fontFamily:'monospace'}}>{r.vehicle_id}</td><td style={{padding:14}}>{r.start_odometer}</td><td style={{padding:14}}>{r.close_odometer ?? '—'}</td><td style={{padding:14}}>{r.status}</td><td style={{padding:14}}>{rec?.status ?? '—'}</td><td style={{padding:14}}>{rec?.reported_income ?? '—'}</td><td style={{padding:14}}>{rec?.system_income ?? '—'}</td><td style={{padding:14}}>{rec?.unallocated_km ?? '—'}</td><td style={{padding:14}}><Link href={`/sessions/${r.id}`}>View</Link></td></tr>; })}</tbody></table></section>}
    {!loading && !error && total > limit && <section style={{display:'flex',gap:12,marginTop:16}}><button disabled={offset===0} onClick={()=>setOffset(Math.max(0,offset-limit))}>Previous</button><button disabled={offset+limit>=total} onClick={()=>setOffset(offset+limit)}>Next</button></section>}
  </main>;
}
