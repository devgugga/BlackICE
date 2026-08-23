import { describe, it, expect, vi, afterEach } from 'vitest';
import { useWorklist, EMPTY_FILTERS, PAGE_SIZE } from '@/features/worklist/useWorklist';
import { ApiError } from '@/shared/api/problems/api-error';
import type { StudyPage, StudySummary, WorklistFilters } from '@/features/worklist/worklist.types';

afterEach(() => {
  vi.restoreAllMocks();
});

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function createStudy(overrides?: Partial<StudySummary>): StudySummary {
  return {
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
    ...overrides,
  };
}

function page(options: {
  offset?: number;
  limit?: number;
  hasPrevious?: boolean;
  hasNext?: boolean;
  items?: readonly StudySummary[];
} = {}): StudyPage {
  const limit = options.limit ?? PAGE_SIZE;
  const offset = options.offset ?? 0;
  return {
    items: options.items ?? [createStudy()],
    page: {
      limit,
      offset,
      hasPrevious: options.hasPrevious ?? offset > 0,
      hasNext: options.hasNext ?? false,
    },
  };
}

function pageWithPatient(name: string): StudyPage {
  return {
    items: [createStudy({ patientName: name })],
    page: {
      limit: PAGE_SIZE,
      offset: 0,
      hasPrevious: false,
      hasNext: false,
    },
  };
}

describe('useWorklist', () => {
  it('inicia em estado IDLE com valores padrao', () => {
    const api = vi.fn();
    const worklist = useWorklist(api);

    expect(worklist.phase.value).toBe('IDLE');
    expect(worklist.items.value).toEqual([]);
    expect(worklist.page.value).toEqual({
      limit: 20,
      offset: 0,
      hasPrevious: false,
      hasNext: false,
    });
    expect(worklist.error.value).toBeNull();
    expect(worklist.appliedFilters.value).toEqual(EMPTY_FILTERS);
    expect(worklist.appliedOffset.value).toBe(0);
    expect(api).not.toHaveBeenCalled();
  });

  it('carrega estudos recentes com vinte itens e offset zero', async () => {
    const api = vi.fn().mockResolvedValue(page({ offset: 0, hasNext: true }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();

    expect(api).toHaveBeenCalledWith(
      { filters: EMPTY_FILTERS, limit: 20, offset: 0 },
      expect.any(AbortSignal),
    );
    expect(worklist.phase.value).toBe('READY');
    expect(worklist.items.value).toHaveLength(1);
    expect(worklist.page.value.hasNext).toBe(true);
    expect(worklist.appliedFilters.value).toEqual(EMPTY_FILTERS);
    expect(worklist.appliedOffset.value).toBe(0);
  });

  it('aborta a anterior e ignora resposta obsoleta', async () => {
    const first = deferred<StudyPage>();
    const second = deferred<StudyPage>();
    const api = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const worklist = useWorklist(api);

    const firstRun = worklist.search({ ...EMPTY_FILTERS, patientName: 'A' });
    const firstSignal = api.mock.calls[0][1] as AbortSignal;
    const secondRun = worklist.search({ ...EMPTY_FILTERS, patientName: 'B' });

    expect(firstSignal.aborted).toBe(true);
    second.resolve(pageWithPatient('B'));
    await secondRun;

    first.resolve(pageWithPatient('A'));
    await firstRun;

    expect(worklist.items.value[0]?.patientName).toBe('B');
    expect(worklist.phase.value).toBe('READY');
  });

  it('transiciona para EMPTY quando o resultado nao possui estudos', async () => {
    const api = vi.fn().mockResolvedValue(page({ items: [] }));
    const worklist = useWorklist(api);

    await worklist.search({ ...EMPTY_FILTERS, modality: 'MR' });

    expect(worklist.phase.value).toBe('EMPTY');
    expect(worklist.items.value).toEqual([]);
  });

  it('busca com filtros customizados e reseta offset para zero', async () => {
    const customFilters: WorklistFilters = {
      patientName: 'SILVA',
      patientId: '987',
      modality: 'MR',
      dateFrom: '2026-01-01',
      dateTo: '2026-12-31',
    };
    const api = vi.fn().mockResolvedValue(page({ offset: 0, hasNext: false }));
    const worklist = useWorklist(api);

    await worklist.search(customFilters);

    expect(api).toHaveBeenCalledWith(
      { filters: customFilters, limit: 20, offset: 0 },
      expect.any(AbortSignal),
    );
    expect(worklist.appliedFilters.value).toEqual(customFilters);
    expect(worklist.appliedOffset.value).toBe(0);
    expect(worklist.phase.value).toBe('READY');
  });

  it('limpa filtros e volta ao offset zero via clear()', async () => {
    const api = vi.fn().mockResolvedValue(page());
    const worklist = useWorklist(api);

    await worklist.search({ ...EMPTY_FILTERS, modality: 'CT' });
    expect(worklist.appliedFilters.value.modality).toBe('CT');

    await worklist.clear();

    expect(api).toHaveBeenLastCalledWith(
      { filters: EMPTY_FILTERS, limit: 20, offset: 0 },
      expect.any(AbortSignal),
    );
    expect(worklist.appliedFilters.value).toEqual(EMPTY_FILTERS);
    expect(worklist.appliedOffset.value).toBe(0);
  });

  it('avanca para a proxima pagina via next()', async () => {
    const api = vi.fn()
      .mockResolvedValueOnce(page({ offset: 0, limit: 20, hasNext: true, hasPrevious: false }))
      .mockResolvedValueOnce(page({ offset: 20, limit: 20, hasNext: false, hasPrevious: true }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();
    expect(worklist.page.value.offset).toBe(0);

    await worklist.next();

    expect(api).toHaveBeenLastCalledWith(
      { filters: EMPTY_FILTERS, limit: 20, offset: 20 },
      expect.any(AbortSignal),
    );
    expect(worklist.appliedOffset.value).toBe(20);
  });

  it('nao avanca via next() quando hasNext e false', async () => {
    const api = vi.fn().mockResolvedValue(page({ offset: 0, limit: 20, hasNext: false }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();
    expect(api).toHaveBeenCalledTimes(1);

    await worklist.next();
    expect(api).toHaveBeenCalledTimes(1);
  });

  it('recua para a pagina anterior via previous()', async () => {
    const api = vi.fn()
      .mockResolvedValueOnce(page({ offset: 40, limit: 20, hasNext: true, hasPrevious: true }))
      .mockResolvedValueOnce(page({ offset: 20, limit: 20, hasNext: true, hasPrevious: true }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();
    await worklist.previous();

    expect(api).toHaveBeenLastCalledWith(
      { filters: EMPTY_FILTERS, limit: 20, offset: 20 },
      expect.any(AbortSignal),
    );
    expect(worklist.appliedOffset.value).toBe(20);
  });

  it('previous() usa Math.max(0, offset - limit)', async () => {
    const api = vi.fn()
      .mockResolvedValueOnce(page({ offset: 10, limit: 20, hasNext: true, hasPrevious: true }))
      .mockResolvedValueOnce(page({ offset: 0, limit: 20, hasNext: true, hasPrevious: false }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();
    await worklist.previous();

    expect(api).toHaveBeenLastCalledWith(
      { filters: EMPTY_FILTERS, limit: 20, offset: 0 },
      expect.any(AbortSignal),
    );
    expect(worklist.appliedOffset.value).toBe(0);
  });

  it('nao recua via previous() quando hasPrevious e false', async () => {
    const api = vi.fn().mockResolvedValue(page({ offset: 0, limit: 20, hasPrevious: false }));
    const worklist = useWorklist(api);

    await worklist.loadRecent();
    expect(api).toHaveBeenCalledTimes(1);

    await worklist.previous();
    expect(api).toHaveBeenCalledTimes(1);
  });

  it('ignora navegacao next() e previous() enquanto phase e LOADING', async () => {
    const def = deferred<StudyPage>();
    const api = vi.fn().mockReturnValue(def.promise);
    const worklist = useWorklist(api);

    const run = worklist.loadRecent();
    expect(worklist.phase.value).toBe('LOADING');

    await worklist.next();
    await worklist.previous();
    expect(api).toHaveBeenCalledTimes(1);

    def.resolve(page({ offset: 0, limit: 20, hasNext: true }));
    await run;
    expect(worklist.phase.value).toBe('READY');
  });

  it('executa retry com os ultimos filtros e offset aplicados', async () => {
    const api = vi.fn()
      .mockRejectedValueOnce(new ApiError('API_ARCHIVE_UNAVAILABLE'))
      .mockResolvedValueOnce(page({ offset: 0 }));
    const worklist = useWorklist(api);

    const filter = { ...EMPTY_FILTERS, patientName: 'CARLOS' };
    await worklist.search(filter);

    expect(worklist.phase.value).toBe('ERROR');
    expect(worklist.error.value?.code).toBe('API_ARCHIVE_UNAVAILABLE');

    await worklist.retry();

    expect(api).toHaveBeenLastCalledWith(
      { filters: filter, limit: 20, offset: 0 },
      expect.any(AbortSignal),
    );
    expect(worklist.phase.value).toBe('READY');
    expect(worklist.error.value).toBeNull();
  });

  it('preserva o ApiError catalogado, com politica de retentativa', async () => {
    const api = vi.fn().mockRejectedValue(new ApiError('API_SEARCH_INVALID'));
    const worklist = useWorklist(api);

    await worklist.search(EMPTY_FILTERS);

    expect(worklist.phase.value).toBe('ERROR');
    expect(worklist.error.value).toMatchObject({
      code: 'API_SEARCH_INVALID',
      retryPolicy: 'NEVER',
    });
    expect(worklist.items.value).toEqual([]);
  });

  it('traduz falha inesperada em problema local catalogado', async () => {
    const api = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    const worklist = useWorklist(api);

    await worklist.loadRecent();

    expect(worklist.phase.value).toBe('ERROR');
    expect(worklist.error.value).toMatchObject({
      code: 'CLIENT_UNEXPECTED_ERROR',
      scope: 'CLIENT',
    });
    expect(worklist.items.value).toEqual([]);
  });

  it('silencia AbortError sem transicionar para ERROR', async () => {
    const abortError = new DOMException('The user aborted a request.', 'AbortError');
    const api = vi.fn().mockRejectedValue(abortError);
    const worklist = useWorklist(api);

    await worklist.loadRecent();

    expect(worklist.phase.value).not.toBe('ERROR');
    expect(worklist.error.value).toBeNull();
  });

  it('usa searchStudies como api padrao', () => {
    const worklist = useWorklist();
    expect(worklist.phase.value).toBe('IDLE');
  });

  it('dispose() incrementa geracao, aborta requisicao ativa e silencia resultado posterior', async () => {
    const def = deferred<StudyPage>();
    const api = vi.fn().mockReturnValue(def.promise);
    const worklist = useWorklist(api);

    const run = worklist.loadRecent();
    const signal = api.mock.calls[0][1] as AbortSignal;
    expect(signal.aborted).toBe(false);

    worklist.dispose();
    expect(signal.aborted).toBe(true);

    def.resolve(pageWithPatient('TEST'));
    await run;

    expect(worklist.items.value).toEqual([]);
    expect(worklist.phase.value).toBe('LOADING'); // State was not modified by the late resolution
  });
});
