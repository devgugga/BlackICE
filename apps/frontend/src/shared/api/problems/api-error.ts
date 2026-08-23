import type { DicomValidationViolation } from './problem-extensions.generated';
import type { ProblemCode, ProblemScope, RetryPolicy } from './problem-types.generated';
import { PROBLEM_TYPES } from './problem-types.generated';

/**
 * Falha segura que as features carregam.
 *
 * <p>`message` é sempre o code catalogado, nunca texto vindo do servidor: um
 * `Error` acaba em log, breadcrumb e tela de erro genérica, e nada disso pode
 * carregar dado de paciente. O texto para o usuário vem do mapa PT-BR.
 */
export class ApiError extends Error {
  readonly type: string;
  readonly code: ProblemCode;
  readonly scope: ProblemScope;
  /** Presente apenas em problemas `API_*`, que nascem de uma resposta HTTP. */
  readonly status?: number;
  readonly traceId?: string;
  readonly retryPolicy: RetryPolicy;
  readonly violations?: readonly DicomValidationViolation[];

  constructor(
    code: ProblemCode,
    options: { traceId?: string; violations?: readonly DicomValidationViolation[] } = {},
  ) {
    super(code);
    this.name = 'ApiError';

    const definition = PROBLEM_TYPES[code];
    this.type = definition.type;
    this.code = code;
    this.scope = definition.scope;
    this.retryPolicy = definition.retryPolicy;
    if ('httpStatus' in definition) {
      this.status = definition.httpStatus;
    }
    if (options.traceId !== undefined) {
      this.traceId = options.traceId;
    }
    if (options.violations !== undefined) {
      this.violations = options.violations;
    }
  }

  /** Verdadeiro quando a UI pode oferecer um botão de tentar de novo. */
  get allowsManualRetry(): boolean {
    return this.retryPolicy === 'MANUAL';
  }
}
