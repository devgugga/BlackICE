import { describe, it, expect, vi, afterEach } from 'vitest';
import { searchStudies } from '@/features/worklist/worklist.api';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';
import type { StudyPage, StudySearchParams, WorklistFilters } from '@/features/worklist/worklist.types';

afterEach(() => {
  vi.restoreAllMocks();
});

const emptyFilters: WorklistFilters = {
  patientName: '',
  patientId: '',
  modality: '',
  dateFrom: '',
  dateTo: '',
};

const emptyParams: StudySearchParams = {
  filters: emptyFilters,
  limit: 20,
  offset: 0,
};

const page: StudyPage = {
  items: [],
  page: {
    limit: 20,
    offset: 0,
    hasPrevious: false,
    hasNext: false,
  },
};

function okResponse(body: StudyPage): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

function errorResponse(code: keyof typeof PROBLEM_TYPES): Response {
  const definition = PROBLEM_TYPES[code];
  const status = 'httpStatus' in definition ? definition.httpStatus : 500;
  return new Response(
    JSON.stringify({
      type: definition.type,
      title: 'texto do operador',
      status,
      detail: 'texto do operador',
      code,
      traceId: TRACE_ID,
    }),
    {
      status,
      headers: { 'Content-Type': 'application/problem+json', 'X-Trace-ID': TRACE_ID },
    },
  );
}

function textResponse(status: number, text: string): Response {
  return new Response(text, {
    status,
    headers: { 'Content-Type': 'text/plain' },
  });
}

describe('searchStudies', () => {
  it('envia somente filtros preenchidos e paginação', async () => {
    const fetchFn = vi.fn().mockResolvedValue(okResponse(page));
    await searchStudies(
      { filters: { patientName: 'MARIA', patientId: '', modality: 'CT', dateFrom: '', dateTo: '' }, limit: 20, offset: 40 },
      new AbortController().signal,
      fetchFn,
    );
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/studies?patientName=MARIA&modality=CT&limit=20&offset=40',
      { credentials: 'include', signal: expect.any(AbortSignal) },
    );
  });

  it('omite filtros com apenas espaços e aplica trim nos preenchidos', async () => {
    const fetchFn = vi.fn().mockResolvedValue(okResponse(page));
    await searchStudies(
      {
        filters: {
          patientName: '  MARIA SILVA  ',
          patientId: '   ',
          modality: '  MR ',
          dateFrom: ' 2026-01-01 ',
          dateTo: ' 2026-12-31 ',
        },
        limit: 10,
        offset: 0,
      },
      undefined,
      fetchFn,
    );
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/studies?patientName=MARIA+SILVA&modality=MR&dateFrom=2026-01-01&dateTo=2026-12-31&limit=10&offset=0',
      { credentials: 'include', signal: undefined },
    );
  });

  it('envia apenas limit e offset quando todos os filtros estão vazios', async () => {
    const fetchFn = vi.fn().mockResolvedValue(okResponse(page));
    await searchStudies(emptyParams, undefined, fetchFn);
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/studies?limit=20&offset=0',
      { credentials: 'include', signal: undefined },
    );
  });

  it('retorna página de estudos com sucesso', async () => {
    const studyPage: StudyPage = {
      items: [
        {
          studyInstanceUid: '1.2.840.10008.1.1',
          patientName: 'SILVA^JOSE',
          patientId: '12345',
          patientIdIssuer: 'HOSPITAL',
          studyDate: '2026-08-20',
          studyTime: '14:30:00',
          modalities: ['CT'],
          description: 'CHEST CT',
          seriesCount: 3,
          instanceCount: 150,
        },
      ],
      page: {
        limit: 20,
        offset: 0,
        hasPrevious: false,
        hasNext: false,
      },
    };
    const fetchFn = vi.fn().mockResolvedValue(okResponse(studyPage));
    const result = await searchStudies(emptyParams, undefined, fetchFn);
    expect(result).toEqual(studyPage);
  });

  it.each([
    ['API_SEARCH_INVALID', 400],
    ['API_SEARCH_TOO_BROAD', 413],
    ['API_ARCHIVE_RESPONSE_INVALID', 502],
    ['API_ARCHIVE_UNAVAILABLE', 503],
  ] as const)('traduz %s em ApiError catalogado', async (code, status) => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(code));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ code, status, scope: 'API', traceId: TRACE_ID });
  });

  it('lanca ApiError cuja message e apenas o code catalogado', async () => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse('API_SEARCH_INVALID'));
    const promise = searchStudies(emptyParams, undefined, fetchFn);

    await expect(promise).rejects.toThrow(ApiError);
    await expect(promise).rejects.toSatisfy((err: unknown) => {
      const error = err as ApiError;
      expect(error.name).toBe('ApiError');
      expect(error.status).toBe(400);
      expect(error.message).toBe('API_SEARCH_INVALID');
      expect(error.retryPolicy).toBe('NEVER');
      return true;
    });
  });

  it('trata resposta fora do contrato como falha local', async () => {
    const fetchFn = vi.fn().mockResolvedValue(textResponse(500, '<html>500 Internal Error</html>'));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it('não vaza texto bruto da resposta no erro', async () => {
    const rawSensitiveBody = 'SQL Exception at line 42: secret db leak';
    const fetchFn = vi.fn().mockResolvedValue(textResponse(500, rawSensitiveBody));
    try {
      await searchStudies(emptyParams, undefined, fetchFn);
      expect.unreachable();
    } catch (err) {
      const error = err as ApiError;
      expect(error).toBeInstanceOf(ApiError);
      expect(error.message).toBe('CLIENT_RESPONSE_INVALID');
      expect(JSON.stringify(error)).not.toContain(rawSensitiveBody);
    }
  });

  it('traduz falha de rede em problema local catalogado', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE', scope: 'CLIENT' });
  });

  it('propaga cancelamento via AbortSignal', async () => {
    const controller = new AbortController();
    controller.abort();
    const abortError = new DOMException('The user aborted a request.', 'AbortError');
    const fetchFn = vi.fn().mockRejectedValue(abortError);
    await expect(searchStudies(emptyParams, controller.signal, fetchFn)).rejects.toThrow('The user aborted a request.');
    expect(fetchFn).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ signal: controller.signal }),
    );
  });

  it('usa fetch global por padrão quando fetchFn não é fornecido', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(okResponse(page));
    vi.stubGlobal('fetch', fetchSpy);
    await searchStudies(emptyParams);
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/studies?limit=20&offset=0',
      { credentials: 'include', signal: undefined },
    );
  });
});
