'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getSupabaseBrowserClient } from '../../../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

type Session = { id:string; session_date:string; status:string; started_at:string; closed_at:string|null; start_odometer:number|string; close_odometer:number|string|null; driver_id:string; vehicle_id:string; start_lat:number|string|null; start_lng:number|string|null; start_accuracy_m:number|string|null; close_lat:number|string|null; close_lng:number|string|null; close_accuracy_m:number|string|null; reconciliations?: Record<string,unknown>[] };

type Row = Record<string, unknown>;

const money = (v: unknown) => { const n = Number(v); return Number.isFinite(n) ? `₹${n.toFixed(2)}` : '—'; };
const value = (v: unknown) => v == null || v === '' ? '—' : String(v);

export default function SessionDetailPage() {
  const params = useParams<{sessionId:string}>();
  const router = useRouter();
  const [data,setData] = useState<{session:Session;trips:Row[];fuel:Row[];expenses:Row[]}|null>(null);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const {data:{session:authSession}} = await getSupabaseBrowserClient().auth.getSession();
        if (!authSession?.access_token) { router.replace('/login'); return; }
        const r = await fetch(`${API}/v1/admin/sessions/${params.sessionId}`, {headers:{Authorization:`Bearer ${authSession.access_token}`}});
        if (r.status===401 || r.status===403) { await getSupabaseBrowserClient().auth.signOut(); router.replace('/login'); return; }
        const body = await r.json().catch(()=>({}));
        if (!r.ok) throw new Error(body.message || body.error || `HTTP ${r.status}`);
        if (!cancelled) setData(body);
      } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : 'Unable to load session'); }
      finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled=true; };
  }, [params.sessionId, router]);

  if (loading) return <main style={{maxWidth:1200,margin:'0 auto',padding:32,fontFamily:'system-ui'}}>Loading session…</main>;
  if (error || !data) return <main style={{maxWidth:1200,margin:'0 auto',padding:32,fontFamily:'system-ui'}}><p><Link href="/sessions">← Sessions</Link></p><p role="alert">{error || 'Session not found'}</p></main>;
  const {session,trips,fuel,expenses}=data;
  const rec=session.reconciliations?.[0];
  return <main style={{maxWidth:1400,margin:'0 auto',padding:32,fontFamily:'system-ui'}}>
    <p><Link href="/sessions">← Sessions</Link></p>
    <h1>Session Details</h1>
    <p style={{color:'#666'}}>{session.id}</p>
    <section style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(180px,1fr))',gap:12}}>{[['Date',session.session_date],['Status',session.status],['Driver',session.driver_id],['Vehicle',session.vehicle_id],['Start Odo',value(session.start_odometer)],['Close Odo',value(session.close_odometer)],['Started',new Date(session.started_at).toLocaleString()],['Closed',session.closed_at?new Date(session.closed_at).toLocaleString():'—']].map(([k,v])=><article key={String(k)} style={{border:'1px solid #ddd',borderRadius:10,padding:14}}><div style={{color:'#666'}}>{k}</div><strong>{v}</strong></article>)}</section>
    <h2>Reconciliation</h2>
    <section style={{display:'flex',gap:20,flexWrap:'wrap'}}>{[['Status',rec?.status],['Reported income',rec?.reported_income],['System income',rec?.system_income],['Trip difference',rec?.trip_count_difference],['Income difference',rec?.income_difference],['Running KM',rec?.running_km],['Unallocated KM',rec?.unallocated_km]].map(([k,v])=><div key={String(k)}><strong>{k}: </strong>{String(k).toLowerCase().includes('income')?money(v):value(v)}</div>)}</section>
    <h2>Trips ({trips.length})</h2><Table rows={trips} columns={['started_at','ended_at','start_odometer','end_odometer','gross_fare','additional_charges','payment_method','status','platform_id']} moneyColumns={['gross_fare','additional_charges']} />
    <h2>Fuel ({fuel.length})</h2><Table rows={fuel} columns={['recorded_at','fuel_type','odometer','quantity','unit','rate','amount','payment_method']} moneyColumns={['rate','amount']} />
    <h2>Expenses ({expenses.length})</h2><Table rows={expenses} columns={['recorded_at','category_id','odometer','amount','payment_method','notes']} moneyColumns={['amount']} />
  </main>;
}

function Table({rows,columns,moneyColumns}:{rows:Row[];columns:string[];moneyColumns:string[]}) {
  if (!rows.length) return <p style={{color:'#666'}}>None recorded.</p>;
  return <div style={{overflowX:'auto',border:'1px solid #ddd',borderRadius:10}}><table style={{width:'100%',borderCollapse:'collapse',minWidth:900}}><thead><tr>{columns.map(c=><th key={c} style={{textAlign:'left',padding:10,borderBottom:'1px solid #ddd'}}>{c.replaceAll('_',' ')}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={String(row.id??i)}>{columns.map(c=><td key={c} style={{padding:10,borderBottom:'1px solid #eee'}}>{moneyColumns.includes(c)?money(row[c]):value(row[c])}</td>)}</tr>)}</tbody></table></div>;
}
