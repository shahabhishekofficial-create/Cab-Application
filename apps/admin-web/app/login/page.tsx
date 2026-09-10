'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getSupabaseBrowserClient } from '../../lib/supabase-browser';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState<'login' | 'reset'>('login');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function signInWithGoogle() {
    setBusy(true); setError(''); setMessage('');
    const { error: oauthError } = await getSupabaseBrowserClient().auth.signInWithOAuth({
      provider: 'google', options: { redirectTo: `${window.location.origin}/` },
    });
    if (oauthError) { setError(oauthError.message); setBusy(false); }
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('');
    try {
      const supabase = getSupabaseBrowserClient();
      if (mode === 'reset') {
        const { error: resetError } = await supabase.auth.resetPasswordForEmail(email.trim(), {
          redirectTo: `${window.location.origin}/reset-password`,
        });
        if (resetError) throw resetError;
        setMessage('Password reset email sent. Check your inbox and follow the link.');
        return;
      }
      const { error: signInError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
      if (signInError) throw signInError;
      router.replace('/'); router.refresh();
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to continue'); }
    finally { setBusy(false); }
  }

  return (
    <main style={{ minHeight:'100vh', display:'grid', placeItems:'center', padding:24, fontFamily:'system-ui', background:'#f6f7f9' }}>
      <form onSubmit={submit} style={{ width:'100%', maxWidth:420, padding:32, border:'1px solid #ddd', borderRadius:18, background:'white', boxShadow:'0 10px 30px rgba(0,0,0,.06)' }}>
        <p style={{ opacity:.6, marginTop:0 }}>Cab Operations Management System</p>
        <h1 style={{ marginBottom:8 }}>{mode === 'login' ? 'Admin sign in' : 'Reset your password'}</h1>
        <p style={{ color:'#666', marginTop:0 }}>{mode === 'login' ? 'Sign in to manage drivers, vehicles and cab operations.' : 'Enter your admin email and we’ll send you a secure reset link.'}</p>
        {mode === 'login' && <button type="button" disabled={busy} onClick={signInWithGoogle} style={{ width:'100%', padding:12, margin:'8px 0 18px', borderRadius:8 }}>Continue with Google</button>}
        <label>Email<input required autoComplete="email" type="email" value={email} onChange={e=>setEmail(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:16, padding:11, border:'1px solid #bbb', borderRadius:8 }} /></label>
        {mode === 'login' && <label>Password<input required autoComplete="current-password" type="password" value={password} onChange={e=>setPassword(e.target.value)} style={{ display:'block', width:'100%', boxSizing:'border-box', marginTop:6, marginBottom:8, padding:11, border:'1px solid #bbb', borderRadius:8 }} /></label>}
        {error && <p role="alert" style={{ color:'#b00020' }}>{error}</p>}
        {message && <p role="status" style={{ color:'#176b35' }}>{message}</p>}
        <button disabled={busy} type="submit" style={{ width:'100%', padding:12, borderRadius:8 }}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Send reset email'}</button>
        <div style={{ marginTop:16, display:'flex', justifyContent:'space-between', fontSize:14 }}>
          {mode === 'login' ? <button type="button" onClick={()=>{setMode('reset');setError('');setMessage('')}} style={{ border:0, background:'none', padding:0, cursor:'pointer' }}>Forgot password?</button> : <button type="button" onClick={()=>{setMode('login');setError('');setMessage('')}} style={{ border:0, background:'none', padding:0, cursor:'pointer' }}>← Back to sign in</button>}
        </div>
      </form>
    </main>
  );
}
