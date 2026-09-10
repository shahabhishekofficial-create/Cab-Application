import { getSupabaseAdmin } from './supabase.js';

export interface OcrRecord { sessionId: string; context: 'START' | 'CLOSE'; manualReading: number; ocrReading?: number | null; confidence: number; decision: 'PASS' | 'REVIEW' | 'FAIL'; rawText?: string | null; }
export interface StartSessionRecord { clientTransactionId: string; sessionId: string; driverId: string; vehicleId: string; deviceId?: string | null; startedAt: string; startOdometer: number; startLat?: number | null; startLng?: number | null; startAccuracyM?: number | null; startGpsAt?: string | null; startOdometerFileId?: string | null; ocrReading?: number | null; ocrConfidence?: number | null; ocrDecision?: 'PASS' | 'REVIEW' | 'FAIL' | null; ocrRawText?: string | null; notes?: string | null; }
export interface CloseSessionRecord { clientTransactionId: string; sessionId: string; driverId: string; vehicleId: string; closedAt: string; closeOdometer: number; closeLat?: number | null; closeLng?: number | null; closeAccuracyM?: number | null; closeGpsAt?: string | null; closeOdometerFileId?: string | null; reportedTripCount: number; reportedIncome: number; ocrReading?: number | null; ocrConfidence?: number | null; ocrDecision?: 'PASS' | 'REVIEW' | 'FAIL' | null; ocrRawText?: string | null; notes?: string | null; }

export async function startSession(input: StartSessionRecord): Promise<Record<string, unknown>> {
  const { data, error } = await getSupabaseAdmin().rpc('start_session', {
    p_client_transaction_id: input.clientTransactionId, p_session_id: input.sessionId, p_driver_id: input.driverId, p_vehicle_id: input.vehicleId,
    p_device_id: input.deviceId ?? null, p_started_at: input.startedAt, p_start_odometer: input.startOdometer, p_start_lat: input.startLat ?? null,
    p_start_lng: input.startLng ?? null, p_start_accuracy_m: input.startAccuracyM ?? null, p_start_gps_at: input.startGpsAt ?? null,
    p_start_odometer_file_id: input.startOdometerFileId ?? null, p_notes: input.notes ?? null,
  });
  if (error) throw error;
  const result = data as Record<string, unknown>;
  if (input.ocrDecision && input.ocrConfidence != null) await recordOcrVerification({ sessionId: input.sessionId, context: 'START', manualReading: input.startOdometer, ocrReading: input.ocrReading, confidence: input.ocrConfidence, decision: input.ocrDecision, rawText: input.ocrRawText });
  return result;
}

export async function closeSession(input: CloseSessionRecord): Promise<Record<string, unknown>> {
  const { data, error } = await getSupabaseAdmin().rpc('close_session', {
    p_client_transaction_id: input.clientTransactionId, p_session_id: input.sessionId, p_driver_id: input.driverId, p_vehicle_id: input.vehicleId,
    p_closed_at: input.closedAt, p_close_odometer: input.closeOdometer, p_close_lat: input.closeLat ?? null, p_close_lng: input.closeLng ?? null,
    p_close_accuracy_m: input.closeAccuracyM ?? null, p_close_gps_at: input.closeGpsAt ?? null, p_close_odometer_file_id: input.closeOdometerFileId ?? null,
    p_reported_trip_count: input.reportedTripCount, p_reported_income: input.reportedIncome, p_notes: input.notes ?? null,
  });
  if (error) throw error;
  const result = data as Record<string, unknown>;
  if (input.ocrDecision && input.ocrConfidence != null) await recordOcrVerification({ sessionId: input.sessionId, context: 'CLOSE', manualReading: input.closeOdometer, ocrReading: input.ocrReading, confidence: input.ocrConfidence, decision: input.ocrDecision, rawText: input.ocrRawText });
  return result;
}

export async function recordOcrVerification(input: OcrRecord): Promise<Record<string, unknown>> {
  const { data, error } = await getSupabaseAdmin().rpc('record_ocr_verification', { p_session_id: input.sessionId, p_context: input.context, p_manual_reading: input.manualReading, p_ocr_reading: input.ocrReading ?? null, p_confidence: input.confidence, p_decision: input.decision, p_raw_text: input.rawText ?? null });
  if (error) throw error;
  return data as Record<string, unknown>;
}
