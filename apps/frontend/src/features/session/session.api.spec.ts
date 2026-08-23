import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchSession } from '@/features/session/session.api';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';

afterEach(() => vi.restoreAllMocks());

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

function problemResponse(code: keyof typeof PROBLEM_TYPES, status: number): Response {
  const definition = PROBLEM_TYPES[code];
  return new Response(
    JSON.stringify({
      type: definition.type,
      title: 'irrelevante para o cliente',
      status,
      detail: 'irrelevante para o cliente',
      code,
      traceId: TRACE_ID,
    }),
    {
      status,
      headers: { 'Content-Type': 'application/problem+json', 'X-Trace-ID': TRACE_ID },
    },
  );
}

describe('fetchSession', () => {
  it('trata ausência de sessão como null, não como erro', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(problemResponse('API_AUTHENTICATION_REQUIRED', 401)),
    );

    expect(await fetchSession()).toBeNull();
  });

  it('retorna a sessão em 200', async () => {
    const body = { subject: 's', username: 'dr.teste', roles: ['user'] };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    expect(await fetchSession()).toEqual(body);
  });

  it('lança ApiError catalogado nas demais falhas', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_INTERNAL_ERROR', 500)));

    const error = await fetchSession().catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: 'API_INTERNAL_ERROR', traceId: TRACE_ID });
  });

  it('trata 401 fora do contrato como resposta inválida, não como sessão ausente', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>login</html>', {
        status: 401,
        headers: { 'Content-Type': 'text/html' },
      })),
    );

    const error = await fetchSession().catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('CLIENT_RESPONSE_INVALID');
  });

  it('traduz falha de rede em problema local catalogado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await fetchSession().catch((caught: unknown) => caught);

    expect(error).toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE', scope: 'CLIENT' });
  });
});
