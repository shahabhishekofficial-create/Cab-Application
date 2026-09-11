'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

export default function DriversPage() {
  const [form, setForm] = useState({ email: '', password: '', displayName: '', phone: '', employeeCode: '', licenseNumber: '', registrationNumber: '', make: 'Hyundai', model: 'Aura', variant: '', fuelType: 'CNG', currentOdometer: '0' });
  const [showAdvanced, setShowAdvanced] = useState(false);
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
      setForm(current => ({ ...current, email: '', password: '', displayName: '', phone: '', employeeCode: '', licenseNumber: '', registrationNumber: '', variant: '', currentOdometer: '0' }));
      setShowAdvanced(false);
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to create driver'); }
    finally { setBusy(false); }
  }

  const basicFields: [keyof typeof form, string, string, boolean][] = [
    ['displayName', 'Driver name', 'text', true], ['email', 'Login email', 'email', true], ['password', 'Temporary password', 'password', true],
    ['registrationNumber', 'Vehicle registration', 'text', true], ['currentOdometer', 'Current odometer', 'number', true],
  ];
  const advancedFields: [keyof typeof form, string, string, boolean][] = [
    ['phone', 'Phone', 'tel', false], ['employeeCode', 'Employee code', 'text', false], ['licenseNumber', 'License number', 'text', false],
    ['make', 'Vehicle make', 'text', true], ['model', 'Vehicle model', 'text', true], ['variant', 'Variant', 'text', false], ['fuelType', 'Fuel type', 'text', true],
  ];

  const renderField = ([key, label, type, required]: [keyof typeof form, string, string, boolean]) => (
    <label key={key}>{label}{required ? ' *' : ' (optional)'}<input required={required} type={type} min={type === 'number' ? '0' : undefined} value={form[key]} onChange={e => update(key, e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', padding:11, marginTop:6 }} /></label>
  );

  return (
    <main style={{ maxWidth: 900, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <p><Link href="/">← Dashboard</Link></p><h1>Driver & Vehicle Setup</h1>
      <p style={{ color: '#666' }}>Create the driver's login, profile, vehicle and active assignment in one step.</p>
      <form onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(260px,1fr))', gap: 16, border: '1px solid #ddd', borderRadius: 16, padding: 24 }}>
        {basicFields.map(renderField)}
        <div style={{ gridColumn:'1/-1', borderTop:'1px solid #eee', paddingTop:12 }}>
          <button type="button" onClick={() => setShowAdvanced(value => !value)} style={{ padding:'8px 12px' }}>{showAdvanced ? 'Hide optional details' : 'Add optional driver & vehicle details'}</button>
        </div>
        {showAdvanced && advancedFields.map(renderField)}
        <div style={{ gridColumn:'1/-1', background:'#f7f7f7', borderRadius:10, padding:12 }}>
          <strong>Vehicle defaults:</strong> Hyundai Aura • CNG. Change them only if needed.
        </div>
        <div style={{ gridColumn:'1/-1' }}><button disabled={busy} type="submit" style={{ padding:'12px 18px' }}>{busy ? 'Creating…' : 'Create driver & assign vehicle'}</button></div>
      </form>
      {message && <p role="status" style={{ marginTop:20 }}>{message}</p>}
      {error && <p role="alert" style={{ marginTop:20 }}>{error}</p>}
    </main>
  );
}
