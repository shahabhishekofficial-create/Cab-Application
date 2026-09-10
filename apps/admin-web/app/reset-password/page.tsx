'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

export default function ResetPasswordPage() {
  const router = useRouter();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    getSupabaseBrowserClient().auth.getSession().then(({ data }) => {
      setReady(Boolean(data.session));
      if (!data.session) setError('This reset link is invalid or has expired. Request a new one from the login page.');
    });
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setMessage('');
    if (password.length < 8) { setError('Password must be at least 8 characters.'); return; }
    if (password !== confirm) { setError('Passwords do not match.'); return; }
    setBusy(true);
    try {
      const { error: updateError } = await getSupabaseBrowserClient().auth.updateUser({ password });
      if (updateError) throw updateError;
      setMessage('Password updated successfully. Redirecting to the dashboard…');
      setTimeout(() => router.replace('/'), 900);
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to update password'); }
    finally { setBusy(false); }
  }

  return (
    <main style={{ minHeight:'100vh', display:'grid', placeItems:'center', padding:24, fontFamily:'system-ui', background:'#f6f7f9' }}>
      <form onSubmit={submit} style={{ width:'100%', maxWidth:420, padding:32, border:'1px solid #ddd', borderRadius:18, background:'white', boxShadow:'0 10px 30px rgba(0,0,0,.06)' }}>
        <p style={{ opacity:.6, marginTop:0 }}>Cab Operations Management System</p>
        <h1>Set a new password</h1>
        <p style={{ color:'#666' }}>Choose a new password for your admin account.</p>
        <label>New password<input required minLength={8} type="password" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:16, padding:11, border:'1px solid #bbb', borderRadius:8 }} /></label>
        <label>Confirm password<input required minLength={8} type="password" autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:16, padding:11, border:'1px solid #bbb', borderRadius:8 }} /></label>
        {error && <p role="alert" style={{ color:'#b00020' }}>{error}</p>}
        {message && <p role="status" style={{ color:'#176b35' }}>{message}</p>}
        <button disabled={busy || !ready} type="submit" style={{ width:'100%', padding:12, borderRadius:8 }}>{busy ? 'Updating…' : 'Update password'}</button>
      </form>
    </main>
  );
}
