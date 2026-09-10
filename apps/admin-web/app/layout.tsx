import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = {
  title: 'Cab Operations Management System',
  description: 'Operational dashboard for cab sessions, trips, fuel and expenses.',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, background: '#fafafa', color: '#111' }}>{children}</body>
    </html>
  );
}
