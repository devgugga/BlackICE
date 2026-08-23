// DO NOT EDIT.
//
// Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json.
// Altere o catálogo e execute `pnpm generate` em .problem-catalog/.

/** Tipos de problema publicados no catálogo oficial. */
export const PROBLEM_TYPES = {
  API_ARCHIVE_UNAVAILABLE: {
    type: 'urn:uuid:cbe2c734-1873-570b-a498-a27a96ebadd4',
    scope: 'API',
    httpStatus: 503,
    retryPolicy: 'MANUAL',
  },
  API_DICOM_VALIDATION_FAILED: {
    type: 'urn:uuid:d365af5c-a52e-549e-a322-cde01e2cba1e',
    scope: 'API',
    httpStatus: 422,
    retryPolicy: 'NEVER',
  },
  API_SEARCH_INVALID: {
    type: 'urn:uuid:47212223-d0b8-54f1-a288-d9781204bb9e',
    scope: 'API',
    httpStatus: 400,
    retryPolicy: 'NEVER',
  },
  /** @deprecated Depreciado no catálogo: use API_SEARCH_INVALID. */
  API_SEARCH_MALFORMED: {
    type: 'urn:uuid:a28f9550-6865-5a9c-94bf-98824b1c6775',
    scope: 'API',
    httpStatus: 400,
    retryPolicy: 'NEVER',
  },
  CLIENT_NETWORK_UNAVAILABLE: {
    type: 'urn:uuid:850ffcf4-95bb-5902-90df-d06f1b9aeb2c',
    scope: 'CLIENT',
    retryPolicy: 'MANUAL',
  },
} as const;

/** Code de qualquer problema catalogado. */
export type ProblemCode = keyof typeof PROBLEM_TYPES;

/** Resposta HTTP observável ou falha local do cliente. */
export type ProblemScope = 'API' | 'CLIENT';

/** `AUTOMATIC` é reservado e não existe nesta versão. */
export type RetryPolicy = 'NEVER' | 'MANUAL';

type CodesInScope<S extends ProblemScope> = {
  [C in ProblemCode]: (typeof PROBLEM_TYPES)[C]['scope'] extends S ? C : never;
}[ProblemCode];

/** Code de um problema que nasce de uma resposta HTTP do backend. */
export type ApiProblemCode = CodesInScope<'API'>;

/** Code de uma falha local do browser, sem resposta HTTP correspondente. */
export type ClientProblemCode = CodesInScope<'CLIENT'>;

/** Definição catalogada de um code. */
export type ProblemTypeDefinition = (typeof PROBLEM_TYPES)[ProblemCode];

/** Todos os codes, na ordem canônica do catálogo. */
export const PROBLEM_CODES = Object.keys(PROBLEM_TYPES) as readonly ProblemCode[];
