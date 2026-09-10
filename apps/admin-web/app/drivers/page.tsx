'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

export default function DriversPage() {
  const [form, setForm] = useState({ email: '', password: '', displayName: '', phone: '', employeeCode: '', licenseNumber: '', registrationNumber: '', make: 'Hyundai', model: 'Aura', variant: '', fuelType: 'CNG', currentOdometer: '0' });
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

  const fields: [keyof typeof form, string, string, boolean][] = [
    ['displayName', 'Driver name', 'text', true], ['email', 'Login email', 'email', true], ['password', 'Temporary password', 'password', true],
    ['phone', 'Phone', 'tel', false], ['employeeCode', 'Employee code', 'text', false], ['licenseNumber', 'License number', 'text', false],
    ['registrationNumber', 'Vehicle registration', 'text', true], ['make', 'Vehicle make', 'text', true], ['model', 'Vehicle model', 'text', true],
    ['variant', 'Variant', 'text', false], ['fuelType', 'Fuel type', 'text', true], ['currentOdometer', 'Current odometer', 'number', true],
  ];

  return (
    <main style={{ maxWidth: 900, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <p><Link href="/">← Dashboard</Link></p><h1>Driver & Vehicle Setup</h1>
      <p style={{ color: '#666' }}>Create the driver's Supabase login, driver profile, vehicle and active assignment together.</p>
      <form onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(260px,1fr))', gap: 16, border: '1px solid #ddd', borderRadius: 16, padding: 24 }}>
        {fields.map(([key, label, type, required]) => <label key={key}>{label}<input required={required} type={type} min={type === 'number' ? '0' : undefined} value={form[key]} onChange={e => update(key, e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>)}
        <div style={{ gridColumn:'1/-1' }}><button disabled={busy} type="submit" style={{ padding:'12px 18px' }}>{busy ? 'Creating…' : 'Create driver & assign vehicle'}</button></div>
      </form>
      {message && <p role="status" style={{ marginTop:20 }}>{message}</p>}
      {error && <p role="alert" style={{ marginTop:20 }}>{error}</p>}
    </main>
  );
}
