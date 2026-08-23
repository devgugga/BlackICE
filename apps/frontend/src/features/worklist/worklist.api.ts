import type { StudyPage, StudySearchParams } from './worklist.types';
import { WorklistError } from './worklist.types';

export { WorklistError };

interface WorklistErrorBody {
  code?: unknown;
}

async function safeError(response: Response): Promise<{ code: string }> {
  try {
    const data = (await response.json()) as WorklistErrorBody;
    if (data && typeof data === 'object' && typeof data.code === 'string' && data.code.trim().length > 0) {
      return { code: data.code.trim() };
    }
  } catch {
    // Fall back to safe unknown error code without leaking raw text
  }
  return { code: 'UNKNOWN_ERROR' };
}

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

  const response = await fetchFn(`/api/studies?${query}`, { credentials: 'include', signal });
  if (!response.ok) {
    const body = await safeError(response);
    throw new WorklistError(response.status, body.code);
  }
  return (await response.json()) as StudyPage;
}
