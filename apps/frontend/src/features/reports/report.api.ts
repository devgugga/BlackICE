import { fetchCsrfToken } from '@/shared/api/csrf';
import {
  apiErrorFromResponse,
  clientError,
  isIntentionalAbort,
} from '@/shared/api/problems/parse-problem';
import type { ReportSnapshot, ReportStatus, StudyReport } from './report.types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonEmptyString(val: unknown): val is string {
  return typeof val === 'string' && val.trim().length > 0;
}

function isValidDateString(val: unknown): val is string {
  return typeof val === 'string' && val.length > 0 && !Number.isNaN(Date.parse(val));
}

function isNullableDateString(val: unknown): val is string | null {
  if (val === null) return true;
  return isValidDateString(val);
}

function parseStudyReport(raw: unknown): StudyReport {
  if (!isRecord(raw)) throw new Error('Invalid report record');
  if (!isNonEmptyString(raw.studyInstanceUid)) throw new Error('Invalid studyInstanceUid');
  if (!isNonEmptyString(raw.authorDisplayName)) throw new Error('Invalid authorDisplayName');
  if (raw.status !== 'DRAFT' && raw.status !== 'FINAL') throw new Error('Invalid status');
  if (typeof raw.content !== 'string') throw new Error('Invalid content');
  if (typeof raw.editable !== 'boolean') throw new Error('Invalid editable');
  if (!isValidDateString(raw.createdAt)) throw new Error('Invalid createdAt');
  if (!isValidDateString(raw.updatedAt)) throw new Error('Invalid updatedAt');
  if (!isNullableDateString(raw.finalizedAt)) throw new Error('Invalid finalizedAt');

  return {
    studyInstanceUid: raw.studyInstanceUid,
    authorDisplayName: raw.authorDisplayName,
    status: raw.status,
    content: raw.content,
    editable: raw.editable,
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
    finalizedAt: raw.finalizedAt !== undefined && raw.finalizedAt !== null ? (raw.finalizedAt as string) : null,
  };
}

function extractRequiredEtag(response: Response): string {
  const etag = response.headers.get('ETag');
  if (!etag || etag.trim().length === 0) {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
  return etag;
}

export interface ReportMutationOptions {
  signal?: AbortSignal;
  fetchFn?: typeof fetch;
  fetchCsrfTokenFn?: typeof fetchCsrfToken;
}

/**
 * Consulta o laudo associado ao estudo no PostgreSQL.
 *
 * <p>Retorna `null` em 204 No Content sem tentar fazer parse de corpo. Em 200 OK,
 * valida os campos tipados e extrai o ETag forte retornado pelo servidor.</p>
 */
export async function fetchStudyReport(
  uid: string,
  signal?: AbortSignal,
  fetchFn: typeof fetch = fetch,
): Promise<ReportSnapshot | null> {
  let response: Response;
  try {
    response = await fetchFn(`/api/studies/${uid}/report`, {
      credentials: 'include',
      signal,
    });
  } catch (error) {
    if (isIntentionalAbort(error)) throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (response.status === 204) {
    return null;
  }

  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  const etag = extractRequiredEtag(response);

  try {
    const json = await response.json();
    const report = parseStudyReport(json);
    return { report, etag };
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}

/**
 * Cria ou finaliza diretamente um laudo para o estudo.
 *
 * <p>Obtém o token CSRF imediatamente antes da requisição mutante. Exige resposta
 * 201 Created com ETag forte e propaga erros catalogados via parser compartilhado.</p>
 */
export async function createStudyReport(
  uid: string,
  content: string,
  status: ReportStatus,
  options?: ReportMutationOptions,
): Promise<ReportSnapshot> {
  const fetchFn = options?.fetchFn ?? fetch;
  const csrfFetcher = options?.fetchCsrfTokenFn ?? fetchCsrfToken;

  const csrfToken = await csrfFetcher();

  let response: Response;
  try {
    response = await fetchFn(`/api/studies/${uid}/report`, {
      method: 'POST',
      credentials: 'include',
      signal: options?.signal,
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken,
      },
      body: JSON.stringify({ content, status }),
    });
  } catch (error) {
    if (isIntentionalAbort(error)) throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  const etag = extractRequiredEtag(response);

  try {
    const json = await response.json();
    const report = parseStudyReport(json);
    return { report, etag };
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}

/**
 * Atualiza o conteúdo ou finaliza um rascunho existente com concorrência otimista.
 *
 * <p>Envia o ETag opaco exato no header `If-Match` e obtém o novo ETag na resposta 200.</p>
 */
export async function updateStudyReport(
  uid: string,
  content: string,
  status: ReportStatus,
  etag: string,
  options?: ReportMutationOptions,
): Promise<ReportSnapshot> {
  const fetchFn = options?.fetchFn ?? fetch;
  const csrfFetcher = options?.fetchCsrfTokenFn ?? fetchCsrfToken;

  const csrfToken = await csrfFetcher();

  let response: Response;
  try {
    response = await fetchFn(`/api/studies/${uid}/report`, {
      method: 'PUT',
      credentials: 'include',
      signal: options?.signal,
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken,
        'If-Match': etag,
      },
      body: JSON.stringify({ content, status }),
    });
  } catch (error) {
    if (isIntentionalAbort(error)) throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  const nextEtag = extractRequiredEtag(response);

  try {
    const json = await response.json();
    const report = parseStudyReport(json);
    return { report, etag: nextEtag };
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}
