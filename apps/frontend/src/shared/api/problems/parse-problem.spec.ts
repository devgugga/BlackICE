import { describe, expect, it } from 'vitest';

import {
  apiErrorFromResponse,
  apiErrorFromXhr,
  clientError,
  isIntentionalAbort,
} from './parse-problem';
import { PROBLEM_TYPES } from './problem-types.generated';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

/** Problema RFC 9457 bem formado, para servir de base às variações. */
function problemBody(overrides: Record<string, unknown> = {}) {
  return {
    type: PROBLEM_TYPES.API_ARCHIVE_UNAVAILABLE.type,
    title: 'Archive unavailable',
    status: 503,
    detail: 'The imaging archive is temporarily unavailable.',
    code: 'API_ARCHIVE_UNAVAILABLE',
    traceId: TRACE_ID,
    ...overrides,
  };
}

function responseWith(
  body: unknown,
  init: { status?: number; contentType?: string | null; traceHeader?: string | null } = {},
): Response {
  const headers = new Headers();
  if (init.contentType !== null) {
    headers.set('Content-Type', init.contentType ?? 'application/problem+json');
  }
  if (init.traceHeader !== null) {
    headers.set('X-Trace-ID', init.traceHeader ?? TRACE_ID);
  }
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return new Response(text, { status: init.status ?? 503, headers });
}

describe('apiErrorFromResponse', () => {
  it('reconhece um problema catalogado e resolve a política pelo tipo', async () => {
    const error = await apiErrorFromResponse(responseWith(problemBody()));

    expect(error).toMatchObject({
      type: PROBLEM_TYPES.API_ARCHIVE_UNAVAILABLE.type,
      code: 'API_ARCHIVE_UNAVAILABLE',
      scope: 'API',
      status: 503,
      traceId: TRACE_ID,
      retryPolicy: 'MANUAL',
    });
  });

  it('nunca copia detail ou corpo bruto para Error.message', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({ detail: 'Paciente Maria da Silva, UID 1.2.840' })),
    );

    expect(error.message).toBe('API_ARCHIVE_UNAVAILABLE');
    expect(error.message).not.toContain('Maria');
    expect(error.message).not.toContain('1.2.840');
  });

  it('recusa content type que não é problem+json', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody(), { contentType: 'application/json' }),
    );

    expect(error).toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it.each([
    'application/problem+json.evil',
    'text/application/problem+json',
  ])('recusa media type que apenas contém o token válido: %s', async (contentType) => {
    const error = await apiErrorFromResponse(responseWith(problemBody(), { contentType }));

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('aceita o token do media type sem distinguir caixa e com parâmetros', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody(), { contentType: 'Application/Problem+Json; Charset=UTF-8' }),
    );

    expect(error.code).toBe('API_ARCHIVE_UNAVAILABLE');
  });

  it('recusa JSON inválido', async () => {
    const error = await apiErrorFromResponse(responseWith('{ nao e json '));

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('recusa code fora do catálogo', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({ code: 'API_INVENTADO_AQUI' })),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('recusa status divergente do catálogo', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({ status: 502 }), { status: 502 }),
    );

    expect(error).toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      scope: 'CLIENT',
      retryPolicy: 'MANUAL',
    });
  });

  it('recusa type que não corresponde ao code', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({ type: PROBLEM_TYPES.API_INTERNAL_ERROR.type })),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('recusa quando o traceId do corpo diverge do cabeçalho', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody(), { traceHeader: 'f'.repeat(32) }),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('preserva o TraceID do cabeçalho quando o corpo não é confiável', async () => {
    const error = await apiErrorFromResponse(
      responseWith('corpo corrompido', { traceHeader: TRACE_ID }),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
    expect(error.traceId).toBe(TRACE_ID);
  });

  it('ignora um TraceID que não tem 32 hexadecimais', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({ traceId: 'nao-hexadecimal' }), { traceHeader: null }),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
    expect(error.traceId).toBeUndefined();
  });

  it('aceita problema sem traceId quando não há trace ativo', async () => {
    const body = problemBody();
    delete (body as Record<string, unknown>).traceId;

    const error = await apiErrorFromResponse(responseWith(body, { traceHeader: null }));

    expect(error.code).toBe('API_ARCHIVE_UNAVAILABLE');
    expect(error.traceId).toBeUndefined();
  });

  it('lê a extensão de violações de um 422 catalogado', async () => {
    const error = await apiErrorFromResponse(
      responseWith(
        {
          type: PROBLEM_TYPES.API_DICOM_VALIDATION_FAILED.type,
          title: 'DICOM validation failed',
          status: 422,
          detail: 'None of the uploaded files passed validation.',
          code: 'API_DICOM_VALIDATION_FAILED',
          traceId: TRACE_ID,
          violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' }],
        },
        { status: 422 },
      ),
    );

    expect(error.code).toBe('API_DICOM_VALIDATION_FAILED');
    expect(error.violations).toEqual([
      { itemIndex: 0, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' },
    ]);
  });

  it.each([
    { name: 'array vazio', violations: [] },
    {
      name: 'mensagem vazia',
      violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: '' }],
    },
    {
      name: 'propriedade adicional no item',
      violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: 'Invalid.', filename: 'x.dcm' }],
    },
  ])('recusa extensão fora do schema: $name', async ({ violations }) => {
    const error = await apiErrorFromResponse(
      responseWith(
        problemBody({
          type: PROBLEM_TYPES.API_DICOM_VALIDATION_FAILED.type,
          code: 'API_DICOM_VALIDATION_FAILED',
          status: 422,
          violations,
        }),
        { status: 422 },
      ),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('recusa a extensão de violações em Problem Type que não a declara', async () => {
    const error = await apiErrorFromResponse(
      responseWith(problemBody({
        violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: 'Invalid.' }],
      })),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('recusa membro adicional da extensão de validação DICOM', async () => {
    const error = await apiErrorFromResponse(
      responseWith(
        problemBody({
          type: PROBLEM_TYPES.API_DICOM_VALIDATION_FAILED.type,
          code: 'API_DICOM_VALIDATION_FAILED',
          status: 422,
          violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: 'Invalid.' }],
          rejectedFilename: 'x.dcm',
        }),
        { status: 422 },
      ),
    );

    expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
  });
});

describe('clientError', () => {
  it('produz falhas locais catalogadas', () => {
    const network = clientError('CLIENT_NETWORK_UNAVAILABLE');

    expect(network).toMatchObject({
      code: 'CLIENT_NETWORK_UNAVAILABLE',
      scope: 'CLIENT',
      retryPolicy: 'MANUAL',
    });
    expect(network.status).toBeUndefined();
    expect(network.message).toBe('CLIENT_NETWORK_UNAVAILABLE');
  });

  it('aceita um TraceID válido observado no cabeçalho', () => {
    expect(clientError('CLIENT_REQUEST_TIMEOUT', TRACE_ID).traceId).toBe(TRACE_ID);
    expect(clientError('CLIENT_REQUEST_TIMEOUT', 'invalido').traceId).toBeUndefined();
  });
});

describe('apiErrorFromXhr', () => {
  function xhr(status: number, body: string, contentType: string, trace?: string) {
    return {
      status,
      responseText: body,
      getResponseHeader: (name: string) =>
        name.toLowerCase() === 'content-type'
          ? contentType
          : name.toLowerCase() === 'x-trace-id'
            ? (trace ?? null)
            : null,
    } as unknown as XMLHttpRequest;
  }

  it('usa o mesmo núcleo do fetch', () => {
    const error = apiErrorFromXhr(
      xhr(503, JSON.stringify(problemBody()), 'application/problem+json', TRACE_ID),
    );

    expect(error).toMatchObject({ code: 'API_ARCHIVE_UNAVAILABLE', traceId: TRACE_ID });
  });

  it('trata corpo inválido como resposta fora do contrato', () => {
    const error = apiErrorFromXhr(xhr(500, '<html>erro</html>', 'text/html', TRACE_ID));

    expect(error).toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', traceId: TRACE_ID });
  });
});

describe('isIntentionalAbort', () => {
  it('reconhece o cancelamento pedido pelo usuário como controle de fluxo', () => {
    expect(isIntentionalAbort(new DOMException('The operation was aborted', 'AbortError'))).toBe(true);
  });

  it('não confunde falha real com cancelamento', () => {
    expect(isIntentionalAbort(clientError('CLIENT_NETWORK_UNAVAILABLE'))).toBe(false);
    expect(isIntentionalAbort(new Error('qualquer'))).toBe(false);
    expect(isIntentionalAbort(null)).toBe(false);
  });
});
