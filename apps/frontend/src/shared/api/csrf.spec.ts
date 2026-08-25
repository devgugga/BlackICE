import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchCsrfToken, readCookie } from '@/shared/api/csrf';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

function problemJson(code: keyof typeof PROBLEM_TYPES, extra: Record<string, unknown> = {}) {
  const definition = PROBLEM_TYPES[code];
  return JSON.stringify({
    type: definition.type,
    title: 'texto do operador',
    status: 'httpStatus' in definition ? definition.httpStatus : 500,
    detail: 'texto do operador',
    code,
    traceId: TRACE_ID,
    ...extra,
  });
}

function problemResponse(code: keyof typeof PROBLEM_TYPES): Response {
  const definition = PROBLEM_TYPES[code];
  const status = 'httpStatus' in definition ? definition.httpStatus : 500;
  return new Response(problemJson(code), {
    status,
    headers: { 'Content-Type': 'application/problem+json', 'X-Trace-ID': TRACE_ID },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('readCookie', () => {
  it('extrai valor do cookie pelo nome', () => {
    const cookies = 'foo=bar; csrf-token=token-123; other=val';
    expect(readCookie(cookies, 'csrf-token')).toBe('token-123');
  });

  it('decodifica valor url-encoded', () => {
    const cookies = 'csrf-token=abc%20123';
    expect(readCookie(cookies, 'csrf-token')).toBe('abc 123');
  });

  it('retorna null se o cookie nao existir', () => {
    const cookies = 'foo=bar; other=val';
    expect(readCookie(cookies, 'csrf-token')).toBeNull();
  });

  it('retorna null se a string de cookies estiver vazia', () => {
    expect(readCookie('', 'csrf-token')).toBeNull();
  });
});

describe('fetchCsrfToken', () => {
  it('obtem token com sucesso quando requisicao 204 e cookie presente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    const token = await fetchCsrfToken(() => 'csrf-token=secret-token-xyz');
    expect(token).toBe('secret-token-xyz');
    expect(fetch).toHaveBeenCalledWith('/api/csrf', { credentials: 'include' });
  });

  it('traduz falha do endpoint CSRF pelo parser compartilhado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_AUTHENTICATION_REQUIRED')));
    await expect(fetchCsrfToken(() => 'csrf-token=token'))
      .rejects.toMatchObject({ code: 'API_AUTHENTICATION_REQUIRED', traceId: TRACE_ID });
  });

  it('reporta cookie ausente como falha local catalogada', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(fetchCsrfToken(() => 'other-cookie=abc'))
      .rejects.toMatchObject({ code: 'CLIENT_CSRF_COOKIE_MISSING', scope: 'CLIENT' });
  });

  it('preserva traceId do header quando cookie estiver ausente', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 204, headers: { 'X-Trace-ID': TRACE_ID } })),
    );
    await expect(fetchCsrfToken(() => 'other-cookie=abc'))
      .rejects.toMatchObject({ code: 'CLIENT_CSRF_COOKIE_MISSING', traceId: TRACE_ID, scope: 'CLIENT' });
  });

  it('traduz falha de rede no endpoint CSRF', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(fetchCsrfToken(() => ''))
      .rejects.toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE' });
  });
});
