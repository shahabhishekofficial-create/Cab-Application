import { FastifyInstance } from 'fastify';
import { getSupabaseAdmin } from '../db/supabase.js';
import { adminAuthErrorResponse, requireAdmin } from '../auth/admin-auth.js';

export async function registerAdminMetricsRoutes(app: FastifyInstance): Promise<void> {
  app.get('/v1/admin/metrics', async (request, reply) => {
    try {
      await requireAdmin(request);
      const supabase = getSupabaseAdmin();
      const [sessions, trips, fuel, expenses] = await Promise.all([
        supabase.from('sessions').select('status,start_odometer,close_odometer'),
        supabase.from('trips').select('gross_fare'),
        supabase.from('fuel_transactions').select('amount'),
        supabase.from('expenses').select('amount'),
      ]);
      for (const result of [sessions, trips, fuel, expenses]) if (result.error) throw result.error;
      const sessionRows = sessions.data ?? [];
      const tripRows = trips.data ?? [];
      const fuelRows = fuel.data ?? [];
      const expenseRows = expenses.data ?? [];
      const revenue = tripRows.reduce((sum, row) => sum + Number(row.gross_fare ?? 0), 0);
      const fuelCost = fuelRows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0);
      const expenseCost = expenseRows.reduce((sum, row) => sum + Number(row.amount ?? 0), 0);
      const runningKm = sessionRows.reduce((sum, row) => sum + (row.close_odometer == null ? 0 : Math.max(0, Number(row.close_odometer) - Number(row.start_odometer))), 0);
      return {
        sessions: sessionRows.length,
        openSessions: sessionRows.filter(row => row.status === 'OPEN').length,
        trips: tripRows.length,
        revenue,
        runningKm,
        fuelCost,
        expenses: expenseCost,
        netOperatingResult: revenue - fuelCost - expenseCost,
      };
    } catch (error) {
      const mapped = adminAuthErrorResponse(error);
      if (mapped) return reply.code(mapped.status).send(mapped.body);
      throw error;
    }
  });
}
