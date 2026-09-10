'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from '../lib/supabase-browser';

const cards = [['Revenue', '₹0'], ['Trips', '0'], ['Running KM', '0 km'], ['Fuel Cost', '₹0'], ['Expenses', '₹0'], ['Net Operating Result', '₹0']];

export default function Dashboard() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  useEffect(() => { supabase.auth.getSession().then(({ data: { session } }) => { if (!session) router.replace('/login'); else setEmail(session.user.email ?? ''); }); }, [router]);
  async function signOut() { await supabase.auth.signOut(); router.replace('/login'); }

  return (
    <main style={{ maxWidth: 1100, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <header style={{ marginBottom: 32, display:'flex', justifyContent:'space-between', gap:20, alignItems:'start' }}>
        <div><p style={{ margin: 0, opacity: .6 }}>Cab Operations Management System</p><h1 style={{ marginTop: 8 }}>Operations Dashboard</h1><small>{email}</small></div>
        <button onClick={signOut}>Sign out</button>
      </header>
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 16 }}>{cards.map(([label, value]) => <article key={label} style={{ border: '1px solid #ddd', borderRadius: 12, padding: 20 }}><div style={{ opacity: .65 }}>{label}</div><strong style={{ display: 'block', fontSize: 26, marginTop: 8 }}>{value}</strong></article>)}</section>
      <section style={{ marginTop: 32, display:'flex', gap:12, flexWrap:'wrap' }}><Link href="/sessions" style={{ border:'1px solid #ddd', borderRadius:10, padding:'10px 14px', textDecoration:'none' }}>Sessions</Link><Link href="/exports" style={{ border:'1px solid #ddd', borderRadius:10, padding:'10px 14px', textDecoration:'none' }}>Exports</Link></section>
    </main>
  );
}
