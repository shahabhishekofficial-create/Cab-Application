'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

export default function DriversPage() {
  const [form, setForm] = useState({
    email: '', password: '', displayName: '', phone: '', employeeCode: '', licenseNumber: '',
    registrationNumber: '', make: 'Hyundai', model: 'Aura', variant: '', fuelType: 'CNG', currentOdometer: '0',
  });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  function update(key: keyof typeof form, value: string) { setForm(current => ({ ...current, [key]: value })); }

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('');
    try {
      const { data: { session } } = await getSupabaseBrowserClient().auth.getSession();
      if (!session?.access_token) { window.location.replace('/login'); return; }
      const response = await fetch(`${API}/v1/admin/drivers`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.access_token}` },
        body: JSON.stringify({ ...form, phone: form.phone || null, employeeCode: form.employeeCode || null, licenseNumber: form.licenseNumber || null, variant: form.variant || null, currentOdometer: Number(form.currentOdometer) }),
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) { await getSupabaseBrowserClient().auth.signOut(); window.location.replace('/login'); return; }
      if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
      setMessage(`Driver created. Vehicle ${data.registrationNumber} is assigned and ready.`);
      setForm(current => ({ ...current, email: '', password: '', displayName: '', phone: '', employeeCode: '', licenseNumber: '', registrationNumber: '' }));
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to create driver'); }
    finally { setBusy(false); }
  }

  return (
    <main style={{ maxWidth: 760, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <p><Link href="/">← Dashboard</Link></p>
      <h1>Create Driver</h1>
      <p style={{ color: '#666' }}>Create the driver login and assign their vehicle in one step.</p>
      <form onSubmit={submit} style={{ display: 'grid', gap: 16, border: '1px solid #ddd', borderRadius: 16, padding: 24 }}>
        <h3 style={{ margin: 0 }}>Driver details</h3>
        <label>Driver name *<input required value={form.displayName} onChange={e => update('displayName', e.target.value)} placeholder="Abhishek Shah" autoComplete="name" style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        <label>Login email *<input required type="email" value={form.email} onChange={e => update('email', e.target.value)} placeholder="driver@example.com" autoComplete="email" style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        <label>Temporary password *<input required type="password" minLength={8} value={form.password} onChange={e => update('password', e.target.value)} placeholder="Minimum 8 characters" autoComplete="new-password" style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        <label>Phone (optional)<input type="tel" value={form.phone} onChange={e => update('phone', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>

        <h3 style={{ margin: '8px 0 0' }}>Vehicle details</h3>
        <label>Vehicle registration *<input required value={form.registrationNumber} onChange={e => update('registrationNumber', e.target.value)} placeholder="GJ05AB1234" autoCapitalize="characters" style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:16 }}>
          <label>Make<input value={form.make} onChange={e => update('make', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
          <label>Model<input value={form.model} onChange={e => update('model', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        </div>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:16 }}>
          <label>Variant (optional)<input value={form.variant} onChange={e => update('variant', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
          <label>Fuel type<input value={form.fuelType} onChange={e => update('fuelType', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
        </div>
        <label>Current odometer (km) *<input required type="number" min="0" step="0.1" value={form.currentOdometer} onChange={e => update('currentOdometer', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>

        <details>
          <summary>Additional information (optional)</summary>
          <div style={{ display:'grid', gap:16, marginTop:16 }}>
            <label>Employee code<input value={form.employeeCode} onChange={e => update('employeeCode', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
            <label>License number<input value={form.licenseNumber} onChange={e => update('licenseNumber', e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
          </div>
        </details>

        <button disabled={busy} type="submit" style={{ padding:'13px 18px', fontSize:16 }}>{busy ? 'Creating driver…' : 'Create driver & assign vehicle'}</button>
      </form>
      {message && <p role="status" style={{ marginTop:20 }}>{message}</p>}
      {error && <p role="alert" style={{ marginTop:20 }}>{error}</p>}
    </main>
  );
}
