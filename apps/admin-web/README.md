# Admin Web

Next.js operations dashboard for the Cab Operations Management System.

## Local development

1. Copy `.env.example` to `.env.local`.
2. Set `NEXT_PUBLIC_API_BASE_URL` to the API URL.
3. Install dependencies with `npm install`.
4. Start with `npm run dev`.

The sessions API is protected by admin authentication. Do not expose Supabase service-role credentials to browser code.
