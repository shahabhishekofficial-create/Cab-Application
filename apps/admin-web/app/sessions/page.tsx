import Link from 'next/link';

const rows = [
  { id: '—', date: 'No live data yet', driver: '—', vehicle: '—', start: '—', end: '—', status: '—', reconciliation: '—' },
];

export default function SessionsPage() {
  return (
    <main style={{ maxWidth: 1200, margin: '0 auto', padding: 32, fontFamily: 'system-ui' }}>
      <p style={{ opacity: .6 }}>Cab Operations Management System</p>
      <h1>Sessions</h1>
      <p style={{ color: '#666' }}>Operational session register. The API endpoint will populate this table once admin authentication is enabled.</p>
      <p><Link href="/">← Dashboard</Link></p>
      <section style={{ overflowX: 'auto', border: '1px solid #ddd', borderRadius: 12 }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 900 }}>
          <thead><tr>{['Session ID','Date','Driver','Vehicle','Start Odo','Close Odo','Status','Reconciliation'].map(h => <th key={h} style={{ textAlign:'left', padding:14, borderBottom:'1px solid #ddd' }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map((r, i) => <tr key={i}>{[r.id,r.date,r.driver,r.vehicle,r.start,r.end,r.status,r.reconciliation].map((v,j) => <td key={j} style={{ padding:14, borderBottom:'1px solid #eee' }}>{v}</td>)}</tr>)}</tbody>
        </table>
      </section>
    </main>
  );
}
