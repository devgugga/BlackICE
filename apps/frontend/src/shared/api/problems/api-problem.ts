import type { DicomValidationViolation } from './problem-extensions.generated';

/**
 * Shape RFC 9457 tal como recebido do backend.
 *
 * <p>É deliberadamente permissivo: o que chega pela rede ainda não é confiável.
 * O parser valida esta forma contra o catálogo antes de qualquer consumo.
 */
export interface ApiProblemPayload {
  readonly type?: unknown;
  readonly title?: unknown;
  readonly status?: unknown;
  readonly detail?: unknown;
  readonly code?: unknown;
  readonly traceId?: unknown;
  readonly violations?: unknown;
}

/** Extensões conhecidas que o parser sabe extrair do nível raiz. */
export interface ApiProblemExtensions {
  readonly violations?: readonly DicomValidationViolation[];
}

/** TraceID W3C: exatamente 32 hexadecimais minúsculos. */
export function isTraceId(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{32}$/.test(value);
}
