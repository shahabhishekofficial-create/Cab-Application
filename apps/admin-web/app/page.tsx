'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { getSupabaseBrowserClient } from '../lib/supabase-browser';

type Metrics = { sessions:number; openSessions:number; trips:number; fuelCount:number; expenseCount:number; revenue:number; tripAdditionalCharges:number; fuelCost:number; expenses:number; totalCosts:number; runningKm:number; netOperatingResult:number };
type MetricsPayload = Record<string, unknown>;
function numberValue(payload: MetricsPayload, ...keys: string[]): number { for (const key of keys) { const value=payload[key]; if(typeof value==='number'&&Number.isFinite(value))return value; if(typeof value==='string'&&value.trim()!==''){const n=Number(value);if(Number.isFinite(n))return n;} } return 0; }
function normalizeMetrics(payload: unknown): Metrics { const d=payload&&typeof payload==='object'?payload as MetricsPayload:{}; return { sessions:numberValue(d,'sessions'), openSessions:numberValue(d,'openSessions','open_sessions'), trips:numberValue(d,'trips'), fuelCount:numberValue(d,'fuelCount','fuel_count'), expenseCount:numberValue(d,'expenseCount','expense_count'), revenue:numberValue(d,'revenue'), tripAdditionalCharges:numberValue(d,'tripAdditionalCharges','trip_additional_charges'), fuelCost:numberValue(d,'fuelCost','fuel_cost'), expenses:numberValue(d,'expenses'), totalCosts:numberValue(d,'totalCosts','total_costs'), runningKm:numberValue(d,'runningKm','running_km'), netOperatingResult:numberValue(d,'netOperatingResult','net_operating_result') }; }

const money=(n:number)=>`₹${n.toFixed(2)}`;

export default function Dashboard() {
  const [email,setEmail]=useState(''); const [checkingAuth,setCheckingAuth]=useState(true); const [loading,setLoading]=useState(true); const [error,setError]=useState(''); const [metrics,setMetrics]=useState<Metrics|null>(null); const [lastUpdated,setLastUpdated]=useState('');
  const supabase=getSupabaseBrowserClient();
  const loadMetrics=useCallback(async(showSpinner=true)=>{ if(showSpinner)setLoading(true); setError(''); try { const {data:{session}}=await supabase.auth.getSession(); if(!session){window.location.replace('/login');return;} setEmail(session.user.email??''); const response=await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000'}/v1/admin/metrics`,{headers:{Authorization:`Bearer ${session.access_token}`},cache:'no-store'}); if(response.status===401||response.status===403){await supabase.auth.signOut();window.location.replace('/login');return;} if(!response.ok)throw new Error(`HTTP ${response.status}`); setMetrics(normalizeMetrics(await response.json())); setLastUpdated(new Date().toLocaleTimeString()); } catch(e){setError(e instanceof Error?e.message:'Unable to load dashboard');} finally{setLoading(false);setCheckingAuth(false);} },[supabase]);
  useEffect(()=>{void loadMetrics(); const timer=window.setInterval(()=>void loadMetrics(false),15000); return()=>window.clearInterval(timer);},[loadMetrics]);
  async function signOut(){await supabase.auth.signOut();window.location.replace('/login');}
  if(checkingAuth)return <main style={{minHeight:'100vh',display:'grid',placeItems:'center',fontFamily:'system-ui'}}><div>Checking admin session…</div></main>;
  const operational=[['Sessions',metrics?.sessions??0],['Open',metrics?.openSessions??0],['Trips',metrics?.trips??0],['Fuel',metrics?.fuelCount??0],['Expenses',metrics?.expenseCount??0]];
  return <main style={{minHeight:'100vh',background:'linear-gradient(180deg,#f7f8fa 0%,#fff 45%)',fontFamily:'system-ui',color:'#171717'}}>
    <div style={{maxWidth:1240,margin:'0 auto',padding:'28px 20px 48px'}}>
      <header style={{display:'flex',justifyContent:'space-between',alignItems:'center',gap:20,marginBottom:28,flexWrap:'wrap'}}>
        <div><div style={{fontSize:13,letterSpacing:1,textTransform:'uppercase',opacity:.55}}>Cab Operations</div><h1 style={{fontSize:34,margin:'5px 0'}}>Operations Dashboard</h1><div style={{fontSize:14,opacity:.65}}>{email}{lastUpdated&&` • Updated ${lastUpdated}`}</div></div>
        <div style={{display:'flex',gap:8}}><button onClick={()=>void loadMetrics()} disabled={loading} style={buttonStyle}>{loading?'Refreshing…':'Refresh'}</button><button onClick={signOut} style={buttonStyle}>Sign out</button></div>
      </header>
      {error&&<div role="alert" style={{padding:14,marginBottom:20,border:'1px solid #f0b8b8',borderRadius:12,background:'#fff5f5'}}>Dashboard data unavailable. <button onClick={()=>void loadMetrics()} style={{textDecoration:'underline',border:0,background:'none',cursor:'pointer'}}>Retry</button></div>}
      <section style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(145px,1fr))',gap:12,marginBottom:24}}>{operational.map(([label,value])=><article key={label as string} style={statStyle}><div style={{fontSize:13,opacity:.6}}>{label}</div><strong style={{fontSize:27}}>{value}</strong></article>)}</section>
      <section style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(260px,1fr))',gap:16,marginBottom:28}}>
        <article style={{...panelStyle,background:'#171717',color:'#fff'}}><div style={{opacity:.65,fontSize:13}}>NET OPERATING RESULT</div><div style={{fontSize:38,fontWeight:700,margin:'8px 0'}}>{money(metrics?.netOperatingResult??0)}</div><div style={{opacity:.7}}>Gross revenue less trip charges, fuel and other expenses.</div></article>
        <article style={panelStyle}><div style={{fontSize:13,opacity:.6}}>GROSS REVENUE</div><div style={{fontSize:30,fontWeight:700,margin:'8px 0'}}>{money(metrics?.revenue??0)}</div><div style={{fontSize:14,opacity:.65}}>Running distance: {(metrics?.runningKm??0).toFixed(1)} km</div></article>
        <article style={panelStyle}><div style={{fontSize:13,opacity:.6}}>TOTAL COSTS</div><div style={{fontSize:30,fontWeight:700,margin:'8px 0'}}>{money(metrics?.totalCosts??0)}</div><div style={{fontSize:14,opacity:.65}}>Fuel {money(metrics?.fuelCost??0)} • Other {money(metrics?.expenses??0)}</div></article>
      </section>
      <section style={panelStyle}><h2 style={{fontSize:18,margin:'0 0 16px'}}>Financial breakdown</h2><div style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(190px,1fr))',gap:10}}>{[['Gross Revenue',metrics?.revenue],['Trip Charges',metrics?.tripAdditionalCharges],['Fuel Cost',metrics?.fuelCost],['Other Expenses',metrics?.expenses],['Total Costs',metrics?.totalCosts],['Net Result',metrics?.netOperatingResult]].map(([label,value])=><div key={label as string} style={{padding:'13px 14px',border:'1px solid #e5e5e5',borderRadius:10}}><div style={{fontSize:12,opacity:.58}}>{label}</div><strong>{money(Number(value??0))}</strong></div>)}</div></section>
      <nav style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(210px,1fr))',gap:12,marginTop:18}}><NavCard href="/drivers" title="Drivers & Vehicles" text="Manage driver profiles and assignments."/><NavCard href="/sessions" title="Sessions" text="Review sessions, reconciliation and entries."/><NavCard href="/exports" title="Exports" text="Download operational data for reporting."/></nav>
      <footer style={{marginTop:24,fontSize:12,opacity:.5}}>Live dashboard refreshes every 15 seconds.</footer>
    </div>
  </main>;
}

function NavCard({href,title,text}:{href:string;title:string;text:string}){return <Link href={href} style={{...panelStyle,textDecoration:'none',color:'inherit',display:'block'}}><strong style={{fontSize:17}}>{title}</strong><div style={{fontSize:13,opacity:.6,marginTop:5}}>{text}</div><div style={{marginTop:14,fontSize:13}}>Open →</div></Link>}
const panelStyle={background:'#fff',border:'1px solid #e5e5e5',borderRadius:16,padding:20,boxShadow:'0 3px 14px rgba(0,0,0,.04)'};
const statStyle={...panelStyle,padding:'15px 16px',display:'flex',flexDirection:'column' as const,gap:5};
const buttonStyle={border:'1px solid #d7d7d7',background:'#fff',borderRadius:10,padding:'10px 14px',cursor:'pointer',fontWeight:600};
