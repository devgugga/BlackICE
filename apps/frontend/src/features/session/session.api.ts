import type { SessionResponse } from '@/features/session/session.types';
import { apiErrorFromResponse, clientError } from '@/shared/api/problems/parse-problem';

/**
 * Estado da sessão do BFF.
 *
 * <p>`null` significa exatamente uma coisa: o backend confirmou, pelo contrato,
 * que não há sessão. Qualquer outra falha — inclusive um 401 fora do contrato —
 * é um `ApiError`, para que a SPA não trate indisponibilidade como logout.
 */
export async function fetchSession(): Promise<SessionResponse | null> {
  let response: Response;
  try {
    response = await fetch('/api/me', { credentials: 'include' });
  } catch {
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) {
    const error = await apiErrorFromResponse(response);
    if (error.code === 'API_AUTHENTICATION_REQUIRED') return null;
    throw error;
  }

  try {
    return (await response.json()) as SessionResponse;
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}
