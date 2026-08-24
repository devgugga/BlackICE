import { ApiError } from './api-error';
import { isTraceId, type ApiProblemPayload } from './api-problem';
import {
  DICOM_VALIDATION_VIOLATION_CODES,
  type DicomValidationViolation,
} from './problem-extensions.generated';
import {
  PROBLEM_TYPES,
  type ClientProblemCode,
  type ProblemCode,
} from './problem-types.generated';

const PROBLEM_MEDIA_TYPE = 'application/problem+json';
const BASE_PROBLEM_MEMBERS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'code',
  'traceId',
]);

/** Falha local usada sempre que a resposta não corresponde ao contrato. */
function invalidResponse(traceId?: string): ApiError {
  return new ApiError('CLIENT_RESPONSE_INVALID', traceId === undefined ? {} : { traceId });
}

export function clientError(code: ClientProblemCode, traceId?: string): ApiError {
  return isTraceId(traceId) ? new ApiError(code, { traceId }) : new ApiError(code);
}

function isCataloguedCode(value: unknown): value is ProblemCode {
  return typeof value === 'string' && Object.hasOwn(PROBLEM_TYPES, value);
}

function parseViolations(value: unknown): readonly DicomValidationViolation[] | undefined {
  if (!Array.isArray(value) || value.length === 0) return undefined;

  const violations: DicomValidationViolation[] = [];
  for (const item of value) {
    if (item === null || typeof item !== 'object') return undefined;
    const record = item as Record<string, unknown>;
    if (Object.keys(record).some((key) => !['itemIndex', 'code', 'message'].includes(key))) {
      return undefined;
    }
    const { itemIndex, code, message } = record;
    if (
      typeof itemIndex !== 'number'
      || !Number.isInteger(itemIndex)
      || itemIndex < 0
      || typeof message !== 'string'
      || message.length === 0
      || !DICOM_VALIDATION_VIOLATION_CODES.includes(code as never)
    ) {
      return undefined;
    }
    violations.push({ itemIndex, code: code as DicomValidationViolation['code'], message });
  }
  return violations;
}

function isProblemMediaType(contentType: string | null): boolean {
  if (contentType === null) return false;
  const [mediaType] = contentType.split(';', 1);
  return mediaType?.trim().toLowerCase() === PROBLEM_MEDIA_TYPE;
}

/**
 * Núcleo puro compartilhado por `fetch` e XHR.
 *
 * <p>A combinação `type + code + status` precisa identificar a mesma entrada do
 * catálogo. Qualquer divergência é tratada como resposta fora do contrato, e não
 * como o problema que o corpo alega ser — um servidor que se contradiz não é
 * fonte confiável para decidir o que a UI faz a seguir.
 */
export function parseProblem(input: {
  status: number;
  contentType: string | null;
  body: string;
  traceHeader: string | null;
}): ApiError {
  const headerTrace = isTraceId(input.traceHeader) ? input.traceHeader : undefined;

  if (!isProblemMediaType(input.contentType)) {
    return invalidResponse(headerTrace);
  }

  let payload: ApiProblemPayload;
  try {
    payload = JSON.parse(input.body) as ApiProblemPayload;
  } catch {
    return invalidResponse(headerTrace);
  }
  if (payload === null || typeof payload !== 'object') {
    return invalidResponse(headerTrace);
  }

  if (!isCataloguedCode(payload.code)) {
    return invalidResponse(headerTrace);
  }

  const definition = PROBLEM_TYPES[payload.code];
  if (definition.scope !== 'API' || payload.type !== definition.type) {
    return invalidResponse(headerTrace);
  }
  if (payload.status !== definition.httpStatus || input.status !== definition.httpStatus) {
    return invalidResponse(headerTrace);
  }

  const bodyTrace = isTraceId(payload.traceId) ? payload.traceId : undefined;
  if (payload.traceId !== undefined && bodyTrace === undefined) {
    return invalidResponse(headerTrace);
  }
  if (bodyTrace !== undefined && headerTrace !== undefined && bodyTrace !== headerTrace) {
    return invalidResponse(headerTrace);
  }

  const traceId = bodyTrace ?? headerTrace;
  const options: { traceId?: string; violations?: readonly DicomValidationViolation[] } = {};
  if (traceId !== undefined) options.traceId = traceId;

  if (payload.code === 'API_DICOM_VALIDATION_FAILED') {
    const allowedMembers = new Set([...BASE_PROBLEM_MEMBERS, 'violations']);
    if (Object.keys(payload).some((key) => !allowedMembers.has(key))) {
      return invalidResponse(traceId);
    }
    const violations = parseViolations(payload.violations);
    if (violations === undefined) {
      return invalidResponse(traceId);
    }
    options.violations = violations;
  } else if (payload.violations !== undefined) {
    return invalidResponse(traceId);
  }

  return new ApiError(payload.code, options);
}

export async function apiErrorFromResponse(response: Response): Promise<ApiError> {
  let body: string;
  try {
    body = await response.text();
  } catch {
    return clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }

  return parseProblem({
    status: response.status,
    contentType: response.headers.get('Content-Type'),
    body,
    traceHeader: response.headers.get('X-Trace-ID'),
  });
}

export function apiErrorFromXhr(xhr: XMLHttpRequest): ApiError {
  return parseProblem({
    status: xhr.status,
    contentType: xhr.getResponseHeader('Content-Type'),
    body: xhr.responseText,
    traceHeader: xhr.getResponseHeader('X-Trace-ID'),
  });
}

/**
 * Cancelamento pedido pelo usuário é controle de fluxo, não falha.
 *
 * <p>Quem chama precisa distinguir isso de um erro real para não pintar a tela
 * de vermelho quando o próprio usuário mandou parar.
 */
export function isIntentionalAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
