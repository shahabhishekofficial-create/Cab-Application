"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getSupabaseBrowserClient } from "../../lib/supabase-browser";

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:3000";
const datasets = [
  ["Sessions", "sessions"], ["Trips", "trips"], ["Fuel", "fuel"], ["Expenses", "expenses"], ["Reconciliation", "reconciliation"], ["Exceptions", "exceptions"],
] as const;

export default function ExportsPage() {
  const [authorized, setAuthorized] = useState(false);
  const [message, setMessage] = useState("Checking admin session…");
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    getSupabaseBrowserClient().auth.getSession().then(({ data: { session } }) => {
      if (!session) { window.location.replace("/login"); return; }
      setAuthorized(true); setMessage("Select an operational dataset to download as CSV.");
    }).catch(() => setMessage("Unable to verify admin session."));
  }, []);

  async function download(dataset: string) {
    setBusy(dataset); setMessage(`Preparing ${dataset} export…`);
    try {
      const { data: { session } } = await getSupabaseBrowserClient().auth.getSession();
      if (!session?.access_token) { window.location.replace("/login"); return; }
      const response = await fetch(`${API}/v1/admin/exports/${dataset}`, { headers: { Authorization: `Bearer ${session.access_token}` } });
      if (response.status === 401 || response.status === 403) { await getSupabaseBrowserClient().auth.signOut(); window.location.replace("/login"); return; }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a"); anchor.href = url; anchor.download = `cab-${dataset}.csv`; anchor.click(); URL.revokeObjectURL(url);
      setMessage(`${dataset} export downloaded.`);
    } catch (e) { setMessage(e instanceof Error ? e.message : "Export failed"); }
    finally { setBusy(null); }
  }

  if (!authorized) return <main style={{ maxWidth: 900, margin: "40px auto", padding: 24, fontFamily: "Arial, sans-serif" }}>{message}</main>;

  return (
    <main style={{ maxWidth: 900, margin: "40px auto", padding: 24, fontFamily: "Arial, sans-serif" }}>
      <p><Link href="/">← Dashboard</Link></p>
      <h1>Data Export</h1>
      <p>Download database-backed operational datasets. Database remains the source of truth.</p>
      <section style={{ display: "grid", gap: 12, marginTop: 24 }}>
        {datasets.map(([label, dataset]) => <button key={dataset} disabled={busy !== null} onClick={() => download(dataset)} style={{ padding: 16, textAlign: "left", cursor: busy === null ? "pointer" : "wait" }}><strong>{label}</strong><br /><small>CSV download</small></button>)}
      </section>
      <p style={{ marginTop: 24 }}>{message}</p>
    </main>
  );
}
