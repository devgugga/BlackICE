import { describe, it, expect, vi, afterEach } from 'vitest';
import { searchStudies, WorklistError } from '@/features/worklist/worklist.api';
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

function errorResponse(status: number, code: string, message = 'Error description'): Response {
  return new Response(JSON.stringify({ code, message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
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
    [400, 'INVALID_SEARCH'],
    [413, 'SEARCH_TOO_BROAD'],
    [502, 'ARCHIVE_INVALID_RESPONSE'],
    [503, 'ARCHIVE_UNAVAILABLE'],
  ])('preserva status %s e código seguro %s', async (status, code) => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(status, code));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ status, code });
  });

  it('lanca instancia de WorklistError com name e status corretos', async () => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(400, 'INVALID_SEARCH'));
    const promise = searchStudies(emptyParams, undefined, fetchFn);
    await expect(promise).rejects.toThrow(WorklistError);
    await expect(promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(WorklistError);
      const error = err as WorklistError;
      expect(error.name).toBe('WorklistError');
      expect(error.status).toBe(400);
      expect(error.code).toBe('INVALID_SEARCH');
      expect(error.message).toBe('INVALID_SEARCH');
      return true;
    });
  });

  it('retorna UNKNOWN_ERROR quando o corpo do erro não é JSON', async () => {
    const fetchFn = vi.fn().mockResolvedValue(textResponse(500, '<html>500 Internal Error</html>'));
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ status: 500, code: 'UNKNOWN_ERROR' });
  });

  it('retorna UNKNOWN_ERROR quando o corpo JSON não possui campo code válido', async () => {
    const fetchFn = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ unexpected: 'value' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    await expect(searchStudies(emptyParams, undefined, fetchFn))
      .rejects.toMatchObject({ status: 500, code: 'UNKNOWN_ERROR' });
  });

  it('não vaza texto bruto da resposta no erro', async () => {
    const rawSensitiveBody = 'SQL Exception at line 42: secret db leak';
    const fetchFn = vi.fn().mockResolvedValue(textResponse(500, rawSensitiveBody));
    try {
      await searchStudies(emptyParams, undefined, fetchFn);
      expect.unreachable();
    } catch (err) {
      expect(err).toBeInstanceOf(WorklistError);
      const error = err as WorklistError;
      expect(error.message).toBe('UNKNOWN_ERROR');
      expect(error.code).toBe('UNKNOWN_ERROR');
      expect(JSON.stringify(error)).not.toContain(rawSensitiveBody);
    }
  });

  it('propaga falha de rede do fetch', async () => {
    const networkError = new TypeError('Failed to fetch');
    const fetchFn = vi.fn().mockRejectedValue(networkError);
    await expect(searchStudies(emptyParams, undefined, fetchFn)).rejects.toThrow('Failed to fetch');
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
