"use client";

import { useState } from "react";

const datasets = [
  ["Sessions", "export_sessions"],
  ["Trips", "export_trips"],
  ["Fuel", "export_fuel"],
  ["Expenses", "export_expenses"],
  ["Reconciliation", "export_reconciliation"],
  ["Exceptions", "export_exceptions"],
] as const;

export default function ExportsPage() {
  const [message, setMessage] = useState("Exports will use the selected date range and filters once the admin API is connected.");

  return (
    <main style={{ maxWidth: 900, margin: "40px auto", padding: 24, fontFamily: "Arial, sans-serif" }}>
      <h1>Data Export</h1>
      <p>Excel/CSV-ready operational datasets. Database remains the source of truth.</p>
      <section style={{ display: "grid", gap: 12, marginTop: 24 }}>
        {datasets.map(([label, view]) => (
          <button
            key={view}
            onClick={() => setMessage(`${label}: export endpoint will generate a filtered workbook from ${view}.`)}
            style={{ padding: 16, textAlign: "left", cursor: "pointer" }}
          >
            <strong>{label}</strong><br />
            <small>{view}</small>
          </button>
        ))}
      </section>
      <p style={{ marginTop: 24 }}>{message}</p>
    </main>
  );
}
