// DO NOT EDIT.
//
// Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json.
// Altere o catálogo e execute `pnpm generate` em .problem-catalog/.

/** Tipos de problema publicados no catálogo oficial. */
export const PROBLEM_TYPES = {
  API_ACCESS_DENIED: {
    type: 'urn:uuid:8c7666e3-0c02-5a7b-8a7e-d511314c4ccc',
    scope: 'API',
    httpStatus: 403,
    retryPolicy: 'NEVER',
  },
  API_ARCHIVE_OUTCOME_UNKNOWN: {
    type: 'urn:uuid:f627ad94-4e6f-5b19-bdbc-2550eb221b63',
    scope: 'API',
    httpStatus: 502,
    retryPolicy: 'NEVER',
  },
  API_ARCHIVE_RESPONSE_INVALID: {
    type: 'urn:uuid:8a220e49-3e80-5e59-83e5-43483c4a6dd8',
    scope: 'API',
    httpStatus: 502,
    retryPolicy: 'MANUAL',
  },
  API_ARCHIVE_UNAVAILABLE: {
    type: 'urn:uuid:8dd49378-697d-5e0e-aa0f-5ab72a5e98a6',
    scope: 'API',
    httpStatus: 503,
    retryPolicy: 'MANUAL',
  },
  API_AUTHENTICATION_REQUIRED: {
    type: 'urn:uuid:a665ee9e-36bf-599f-814a-e5d5da82f3cb',
    scope: 'API',
    httpStatus: 401,
    retryPolicy: 'NEVER',
  },
  API_CSRF_INVALID: {
    type: 'urn:uuid:dac0c0fc-38bf-5d19-95f4-45568abb2380',
    scope: 'API',
    httpStatus: 403,
    retryPolicy: 'MANUAL',
  },
  API_DICOM_VALIDATION_FAILED: {
    type: 'urn:uuid:0908ac46-fe0e-5516-b4f8-e4f25882ed8e',
    scope: 'API',
    httpStatus: 422,
    retryPolicy: 'NEVER',
  },
  API_INTERNAL_ERROR: {
    type: 'urn:uuid:4685c43c-aff3-5a91-a688-1f05cb9bfe78',
    scope: 'API',
    httpStatus: 500,
    retryPolicy: 'MANUAL',
  },
  API_MEDIA_TYPE_UNSUPPORTED: {
    type: 'urn:uuid:c9210550-428c-5b67-9d83-d5af3770134f',
    scope: 'API',
    httpStatus: 415,
    retryPolicy: 'NEVER',
  },
  API_METHOD_NOT_ALLOWED: {
    type: 'urn:uuid:ad905e04-1351-5fab-a371-1d5c50382c6a',
    scope: 'API',
    httpStatus: 405,
    retryPolicy: 'NEVER',
  },
  API_PAYLOAD_TOO_LARGE: {
    type: 'urn:uuid:db695bb8-95d6-56b3-b938-f76b77e2d09b',
    scope: 'API',
    httpStatus: 413,
    retryPolicy: 'NEVER',
  },
  API_REPRESENTATION_NOT_ACCEPTABLE: {
    type: 'urn:uuid:9f6fda99-1849-58a1-85fe-deb9a5897740',
    scope: 'API',
    httpStatus: 406,
    retryPolicy: 'NEVER',
  },
  API_REQUEST_INVALID: {
    type: 'urn:uuid:4ab4a9cc-9774-5c32-b0fa-83594c7bf6e1',
    scope: 'API',
    httpStatus: 400,
    retryPolicy: 'NEVER',
  },
  API_RESOURCE_NOT_FOUND: {
    type: 'urn:uuid:25c41e16-7fd0-51f7-9731-1a9c0c0e8dd4',
    scope: 'API',
    httpStatus: 404,
    retryPolicy: 'NEVER',
  },
  API_SEARCH_INVALID: {
    type: 'urn:uuid:5fdeb44a-6add-5d54-a7f4-5f15f7cdc830',
    scope: 'API',
    httpStatus: 400,
    retryPolicy: 'NEVER',
  },
  API_SEARCH_TOO_BROAD: {
    type: 'urn:uuid:3059d5b4-bb73-52e9-b8ed-a73539b98460',
    scope: 'API',
    httpStatus: 413,
    retryPolicy: 'NEVER',
  },
  API_UPLOAD_EMPTY: {
    type: 'urn:uuid:74e415dc-1966-5124-be1f-8732a25fc777',
    scope: 'API',
    httpStatus: 400,
    retryPolicy: 'NEVER',
  },
  CLIENT_CSRF_COOKIE_MISSING: {
    type: 'urn:uuid:7c5107ac-deab-5dc7-b9b9-f87018515842',
    scope: 'CLIENT',
    retryPolicy: 'MANUAL',
  },
  CLIENT_NETWORK_UNAVAILABLE: {
    type: 'urn:uuid:958f8ed3-f5ea-50a5-af1c-6828e25df077',
    scope: 'CLIENT',
    retryPolicy: 'MANUAL',
  },
  CLIENT_REQUEST_TIMEOUT: {
    type: 'urn:uuid:928412fe-24d7-5395-901f-4b0231d4dc8f',
    scope: 'CLIENT',
    retryPolicy: 'MANUAL',
  },
  CLIENT_RESPONSE_INVALID: {
    type: 'urn:uuid:2c52e3d7-f437-5ff7-a3b0-55b896da2ae5',
    scope: 'CLIENT',
    retryPolicy: 'MANUAL',
  },
  CLIENT_UNEXPECTED_ERROR: {
    type: 'urn:uuid:063da3c0-f056-5e8f-b20d-cb494baf8652',
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
