'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from '../../lib/supabase-browser';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const { error: signInError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    if (signInError) {
      setError(signInError.message);
      setBusy(false);
      return;
    }
    router.replace('/');
    router.refresh();
  }

  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, fontFamily: 'system-ui' }}>
      <form onSubmit={submit} style={{ width: '100%', maxWidth: 420, padding: 28, border: '1px solid #ddd', borderRadius: 16, background: 'white' }}>
        <p style={{ opacity: .6, marginTop: 0 }}>Cab Operations Management System</p>
        <h1>Admin sign in</h1>
        <label>Email<input required type="email" value={email} onChange={e => setEmail(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:16, padding:11 }} /></label>
        <label>Password<input required type="password" value={password} onChange={e => setPassword(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:16, padding:11 }} /></label>
        {error && <p role="alert" style={{ color:'#b00020' }}>{error}</p>}
        <button disabled={busy} type="submit" style={{ width:'100%', padding:12 }}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </main>
  );
}
