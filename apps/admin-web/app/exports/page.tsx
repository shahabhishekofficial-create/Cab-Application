"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getSupabaseBrowserClient } from "../../lib/supabase-browser";

const datasets = [
  ["Sessions", "export_sessions"],
  ["Trips", "export_trips"],
  ["Fuel", "export_fuel"],
  ["Expenses", "export_expenses"],
  ["Reconciliation", "export_reconciliation"],
  ["Exceptions", "export_exceptions"],
] as const;

export default function ExportsPage() {
  const router = useRouter();
  const [authorized, setAuthorized] = useState(false);
  const [message, setMessage] = useState("Checking admin session…");

  useEffect(() => {
    getSupabaseBrowserClient().auth.getSession().then(({ data: { session } }) => {
      if (!session) {
        router.replace("/login");
        return;
      }
      setAuthorized(true);
      setMessage("Exports are ready for the selected operational dataset.");
    }).catch(() => setMessage("Unable to verify admin session."));
  }, [router]);

  if (!authorized) return <main style={{ maxWidth: 900, margin: "40px auto", padding: 24, fontFamily: "Arial, sans-serif" }}>{message}</main>;

  return (
    <main style={{ maxWidth: 900, margin: "40px auto", padding: 24, fontFamily: "Arial, sans-serif" }}>
      <p><Link href="/">← Dashboard</Link></p>
      <h1>Data Export</h1>
      <p>Excel/CSV-ready operational datasets. Database remains the source of truth.</p>
      <section style={{ display: "grid", gap: 12, marginTop: 24 }}>
        {datasets.map(([label, view]) => (
          <button key={view} onClick={() => setMessage(`${label}: export endpoint will generate a filtered workbook from ${view}.`)} style={{ padding: 16, textAlign: "left", cursor: "pointer" }}>
            <strong>{label}</strong><br /><small>{view}</small>
          </button>
        ))}
      </section>
      <p style={{ marginTop: 24 }}>{message}</p>
    </main>
  );
}
