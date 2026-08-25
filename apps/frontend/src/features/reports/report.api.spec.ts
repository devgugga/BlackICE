import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import {
  fetchStudyReport,
  createStudyReport,
  updateStudyReport,
} from '@/features/reports/report.api';
import type { StudyReport } from '@/features/reports/report.types';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';
const STUDY_UID = '1.2.840.113619.2.55.3.604688419';

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

const mockDraftReport: StudyReport = {
  studyInstanceUid: STUDY_UID,
  authorDisplayName: 'Dr. John Doe',
  status: 'DRAFT',
  content: 'Achados preliminares normais.',
  editable: true,
  createdAt: '2026-08-25T18:00:00.000Z',
  updatedAt: '2026-08-25T18:00:00.000Z',
  finalizedAt: null,
};

const mockFinalReport: StudyReport = {
  studyInstanceUid: STUDY_UID,
  authorDisplayName: 'Dr. John Doe',
  status: 'FINAL',
  content: 'Laudo conclusivo sem alterações.',
  editable: false,
  createdAt: '2026-08-25T18:00:00.000Z',
  updatedAt: '2026-08-25T18:30:00.000Z',
  finalizedAt: '2026-08-25T18:30:00.000Z',
};

beforeEach(() => {
  // Mock standard document.cookie for CSRF
  Object.defineProperty(document, 'cookie', {
    writable: true,
    value: 'csrf-token=test-csrf-token',
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('fetchStudyReport', () => {
  it('retorna null em resposta 204 No Content sem tentar fazer parse de corpo', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(null, {
        status: 204,
        headers: { 'X-Trace-ID': TRACE_ID, 'Cache-Control': 'no-store' },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    const result = await fetchStudyReport(STUDY_UID);
    expect(result).toBeNull();
    expect(mockFetch).toHaveBeenCalledWith(
      `/api/studies/${STUDY_UID}/report`,
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('retorna snapshot com report e ETag em resposta 200 OK com laudo DRAFT', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(mockDraftReport), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"1"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    const result = await fetchStudyReport(STUDY_UID);
    expect(result).toEqual({
      report: mockDraftReport,
      etag: '"1"',
    });
  });

  it('retorna snapshot com laudo FINAL e timestamp de finalizacao', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(mockFinalReport), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"2"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    const result = await fetchStudyReport(STUDY_UID);
    expect(result).toEqual({
      report: mockFinalReport,
      etag: '"2"',
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se header ETag estiver ausente em resposta 200', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(mockDraftReport), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se corpo 200 nao for JSON valido', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response('not-a-json', {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"1"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se campos obrigatorios estiverem invalidos ou ausentes', async () => {
    const invalidReport = { ...mockDraftReport, status: 'INVALID_STATUS' };
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(invalidReport), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"1"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se timestamps forem invalidos', async () => {
    const invalidReport = { ...mockDraftReport, createdAt: 'not-a-date' };
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(invalidReport), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"1"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
    });
  });

  it('nunca inclui authorId, id ou version no objeto StudyReport resultante', async () => {
    const rawWithInternalFields = {
      ...mockDraftReport,
      id: 42,
      authorId: 'sub-uuid-123',
      version: 1,
    };
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(rawWithInternalFields), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          ETag: '"1"',
          'X-Trace-ID': TRACE_ID,
        },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    const result = await fetchStudyReport(STUDY_UID);
    expect(result).not.toBeNull();
    expect(result?.report).toEqual(mockDraftReport);
    expect(result?.report).not.toHaveProperty('id');
    expect(result?.report).not.toHaveProperty('authorId');
    expect(result?.report).not.toHaveProperty('version');
  });

  it('traduz erro de autenticacao 401 via parser compartilhado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_AUTHENTICATION_REQUIRED')));

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'API_AUTHENTICATION_REQUIRED',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro de autorizacao 403 via parser compartilhado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_ACCESS_DENIED')));

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'API_ACCESS_DENIED',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro de requisicao 400 via parser compartilhado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_REQUEST_INVALID')));

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'API_REQUEST_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 500 via parser compartilhado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse('API_INTERNAL_ERROR')));

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'API_INTERNAL_ERROR',
      traceId: TRACE_ID,
    });
  });

  it('traduz resposta fora do contrato como CLIENT_RESPONSE_INVALID', async () => {
    const mockFetch = vi.fn().mockResolvedValue(
      new Response('Internal Server Error', {
        status: 500,
        headers: { 'Content-Type': 'text/plain', 'X-Trace-ID': TRACE_ID },
      }),
    );
    vi.stubGlobal('fetch', mockFetch);

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('traduz falha de rede em CLIENT_NETWORK_UNAVAILABLE', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(fetchStudyReport(STUDY_UID)).rejects.toMatchObject({
      code: 'CLIENT_NETWORK_UNAVAILABLE',
    });
  });

  it('preserva cancelamento por AbortSignal como DOMException sem envolver em ApiError', async () => {
    const abortErr = new DOMException('The operation was aborted', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortErr));

    const controller = new AbortController();
    controller.abort();

    await expect(fetchStudyReport(STUDY_UID, controller.signal)).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(DOMException);
      expect((err as DOMException).name).toBe('AbortError');
      expect(err).not.toBeInstanceOf(ApiError);
      return true;
    });
  });
});

describe('createStudyReport', () => {
  it('obtem CSRF e envia POST com JSON body, credenciais e header CSRF, retornando 201 + ETag', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url === `/api/studies/${STUDY_UID}/report` && init?.method === 'POST') {
        expect(init.credentials).toBe('include');
        expect(init.headers).toEqual(
          expect.objectContaining({
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': 'test-csrf-token',
          }),
        );
        expect(JSON.parse(init.body as string)).toEqual({
          content: 'Achados preliminares normais.',
          status: 'DRAFT',
        });
        return Promise.resolve(
          new Response(JSON.stringify(mockDraftReport), {
            status: 201,
            headers: {
              'Content-Type': 'application/json',
              ETag: '"1"',
              Location: `/api/studies/${STUDY_UID}/report`,
              'X-Trace-ID': TRACE_ID,
            },
          }),
        );
      }
      return Promise.reject(new Error(`Unexpected call: ${url}`));
    });
    vi.stubGlobal('fetch', mockFetch);

    const snapshot = await createStudyReport(STUDY_UID, 'Achados preliminares normais.', 'DRAFT');
    expect(snapshot).toEqual({
      report: mockDraftReport,
      etag: '"1"',
    });
  });

  it('permite criacao direta com status FINAL', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url === `/api/studies/${STUDY_UID}/report` && init?.method === 'POST') {
        expect(JSON.parse(init.body as string)).toEqual({
          content: 'Laudo conclusivo sem alterações.',
          status: 'FINAL',
        });
        return Promise.resolve(
          new Response(JSON.stringify(mockFinalReport), {
            status: 201,
            headers: {
              'Content-Type': 'application/json',
              ETag: '"1"',
              'X-Trace-ID': TRACE_ID,
            },
          }),
        );
      }
      return Promise.reject(new Error(`Unexpected call: ${url}`));
    });
    vi.stubGlobal('fetch', mockFetch);

    const snapshot = await createStudyReport(STUDY_UID, 'Laudo conclusivo sem alterações.', 'FINAL');
    expect(snapshot).toEqual({
      report: mockFinalReport,
      etag: '"1"',
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se resposta 201 nao tiver ETag', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(
        new Response(JSON.stringify(mockDraftReport), {
          status: 201,
          headers: { 'Content-Type': 'application/json', 'X-Trace-ID': TRACE_ID },
        }),
      );
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(createStudyReport(STUDY_UID, 'Texto', 'DRAFT')).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 404 (estudo inexistente no archive) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_RESOURCE_NOT_FOUND'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(createStudyReport(STUDY_UID, 'Texto', 'DRAFT')).rejects.toMatchObject({
      code: 'API_RESOURCE_NOT_FOUND',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 409 (laudo ja existente) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_RESOURCE_CONFLICT'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(createStudyReport(STUDY_UID, 'Texto', 'DRAFT')).rejects.toMatchObject({
      code: 'API_RESOURCE_CONFLICT',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 403 CSRF invalido via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_CSRF_INVALID'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(createStudyReport(STUDY_UID, 'Texto', 'DRAFT')).rejects.toMatchObject({
      code: 'API_CSRF_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro de rede no POST para CLIENT_NETWORK_UNAVAILABLE', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.reject(new TypeError('Failed to fetch'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(createStudyReport(STUDY_UID, 'Texto', 'DRAFT')).rejects.toMatchObject({
      code: 'CLIENT_NETWORK_UNAVAILABLE',
    });
  });

  it('preserva cancelamento por AbortSignal no createStudyReport', async () => {
    const abortErr = new DOMException('The operation was aborted', 'AbortError');
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.reject(abortErr);
    });
    vi.stubGlobal('fetch', mockFetch);

    const controller = new AbortController();
    controller.abort();

    await expect(
      createStudyReport(STUDY_UID, 'Texto', 'DRAFT', { signal: controller.signal }),
    ).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(DOMException);
      expect((err as DOMException).name).toBe('AbortError');
      expect(err).not.toBeInstanceOf(ApiError);
      return true;
    });
  });
});

describe('updateStudyReport', () => {
  it('obtem CSRF e envia PUT com header If-Match exato e opaco, retornando 200 + novo ETag', async () => {
    const previousEtag = '"1"';
    const newEtag = '"2"';
    const updatedDraft: StudyReport = {
      ...mockDraftReport,
      content: 'Texto atualizado.',
      updatedAt: '2026-08-25T18:15:00.000Z',
    };

    const mockFetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url === `/api/studies/${STUDY_UID}/report` && init?.method === 'PUT') {
        expect(init.credentials).toBe('include');
        expect(init.headers).toEqual(
          expect.objectContaining({
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': 'test-csrf-token',
            'If-Match': previousEtag,
          }),
        );
        expect(JSON.parse(init.body as string)).toEqual({
          content: 'Texto atualizado.',
          status: 'DRAFT',
        });
        return Promise.resolve(
          new Response(JSON.stringify(updatedDraft), {
            status: 200,
            headers: {
              'Content-Type': 'application/json',
              ETag: newEtag,
              'X-Trace-ID': TRACE_ID,
            },
          }),
        );
      }
      return Promise.reject(new Error(`Unexpected call: ${url}`));
    });
    vi.stubGlobal('fetch', mockFetch);

    const snapshot = await updateStudyReport(STUDY_UID, 'Texto atualizado.', 'DRAFT', previousEtag);
    expect(snapshot).toEqual({
      report: updatedDraft,
      etag: newEtag,
    });
  });

  it('permite finalizar rascunho com PUT status FINAL na mesma chamada', async () => {
    const previousEtag = '"1"';
    const newEtag = '"2"';

    const mockFetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url === `/api/studies/${STUDY_UID}/report` && init?.method === 'PUT') {
        expect(init.headers).toEqual(
          expect.objectContaining({
            'If-Match': previousEtag,
          }),
        );
        expect(JSON.parse(init.body as string)).toEqual({
          content: 'Laudo conclusivo sem alterações.',
          status: 'FINAL',
        });
        return Promise.resolve(
          new Response(JSON.stringify(mockFinalReport), {
            status: 200,
            headers: {
              'Content-Type': 'application/json',
              ETag: newEtag,
              'X-Trace-ID': TRACE_ID,
            },
          }),
        );
      }
      return Promise.reject(new Error(`Unexpected call: ${url}`));
    });
    vi.stubGlobal('fetch', mockFetch);

    const snapshot = await updateStudyReport(
      STUDY_UID,
      'Laudo conclusivo sem alterações.',
      'FINAL',
      previousEtag,
    );
    expect(snapshot).toEqual({
      report: mockFinalReport,
      etag: newEtag,
    });
  });

  it('traduz erro 412 (conflito de versao / ETag obsoleto) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_RESOURCE_VERSION_CONFLICT'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"stale-etag"'),
    ).rejects.toMatchObject({
      code: 'API_RESOURCE_VERSION_CONFLICT',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 409 (laudo ja finalizado) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_RESOURCE_CONFLICT'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"'),
    ).rejects.toMatchObject({
      code: 'API_RESOURCE_CONFLICT',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 403 (autor divergente) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_ACCESS_DENIED'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"'),
    ).rejects.toMatchObject({
      code: 'API_ACCESS_DENIED',
      traceId: TRACE_ID,
    });
  });

  it('traduz erro 404 (laudo nao encontrado) via parser compartilhado', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(problemResponse('API_RESOURCE_NOT_FOUND'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"'),
    ).rejects.toMatchObject({
      code: 'API_RESOURCE_NOT_FOUND',
      traceId: TRACE_ID,
    });
  });

  it('rejeita com CLIENT_RESPONSE_INVALID se resposta 200 nao tiver novo ETag', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(
        new Response(JSON.stringify(mockDraftReport), {
          status: 200,
          headers: { 'Content-Type': 'application/json', 'X-Trace-ID': TRACE_ID },
        }),
      );
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"'),
    ).rejects.toMatchObject({
      code: 'CLIENT_RESPONSE_INVALID',
      traceId: TRACE_ID,
    });
  });

  it('traduz falha de rede no PUT para CLIENT_NETWORK_UNAVAILABLE', async () => {
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.reject(new TypeError('Failed to fetch'));
    });
    vi.stubGlobal('fetch', mockFetch);

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"'),
    ).rejects.toMatchObject({
      code: 'CLIENT_NETWORK_UNAVAILABLE',
    });
  });

  it('preserva cancelamento por AbortSignal no updateStudyReport', async () => {
    const abortErr = new DOMException('The operation was aborted', 'AbortError');
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/csrf') {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.reject(abortErr);
    });
    vi.stubGlobal('fetch', mockFetch);

    const controller = new AbortController();
    controller.abort();

    await expect(
      updateStudyReport(STUDY_UID, 'Texto', 'DRAFT', '"1"', { signal: controller.signal }),
    ).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(DOMException);
      expect((err as DOMException).name).toBe('AbortError');
      expect(err).not.toBeInstanceOf(ApiError);
      return true;
    });
  });
});
