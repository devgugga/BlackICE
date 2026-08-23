import { apiErrorFromResponse, clientError } from '@/shared/api/problems/parse-problem';

import type { StudyPage, StudySearchParams } from './worklist.types';

export async function searchStudies(
  params: StudySearchParams,
  signal?: AbortSignal,
  fetchFn: typeof fetch = fetch,
): Promise<StudyPage> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params.filters)) {
    if (value.trim()) query.set(key, value.trim());
  }
  query.set('limit', String(params.limit));
  query.set('offset', String(params.offset));

  let response: Response;
  try {
    response = await fetchFn(`/api/studies?${query}`, { credentials: 'include', signal });
  } catch (error) {
    // O cancelamento é controle de fluxo e segue adiante como está.
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) throw await apiErrorFromResponse(response);

  try {
    return (await response.json()) as StudyPage;
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}
