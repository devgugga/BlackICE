import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { useStudyReport } from './useStudyReport';
import { ApiError } from '@/shared/api/problems/api-error';
import type { ReportSnapshot, StudyReport } from './report.types';

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function createReport(overrides?: Partial<StudyReport>): StudyReport {
  return {
    studyInstanceUid: '1.2.840.113619.2.55.3.604688123.123.1700000000.1',
    authorDisplayName: 'dr.teste',
    status: 'DRAFT',
    content: 'Achados normais no exame.',
    editable: true,
    createdAt: '2026-08-25T15:00:00Z',
    updatedAt: '2026-08-25T15:00:00Z',
    finalizedAt: null,
    ...overrides,
  };
}

function createSnapshot(overrides?: Partial<StudyReport>, etag = '"etag-v1"'): ReportSnapshot {
  return {
    report: createReport(overrides),
    etag,
  };
}

describe('useStudyReport', () => {
  let localStorageGetSpy: ReturnType<typeof vi.spyOn>;
  let localStorageSetSpy: ReturnType<typeof vi.spyOn>;
  let sessionStorageGetSpy: ReturnType<typeof vi.spyOn>;
  let sessionStorageSetSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorageGetSpy = vi.spyOn(Storage.prototype, 'getItem');
    localStorageSetSpy = vi.spyOn(Storage.prototype, 'setItem');
    sessionStorageGetSpy = vi.spyOn(Storage.prototype, 'getItem');
    sessionStorageSetSpy = vi.spyOn(Storage.prototype, 'setItem');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('inicia em estado ABSENT com valores padrão em memória', () => {
    const controller = useStudyReport();

    expect(controller.phase.value).toBe('ABSENT');
    expect(controller.report.value).toBeNull();
    expect(controller.content.value).toBe('');
    expect(controller.dirty.value).toBe(false);
    expect(controller.codePointCount.value).toBe(0);
    expect(controller.error.value).toBeNull();

    expect(localStorageGetSpy).not.toHaveBeenCalled();
    expect(localStorageSetSpy).not.toHaveBeenCalled();
    expect(sessionStorageGetSpy).not.toHaveBeenCalled();
    expect(sessionStorageSetSpy).not.toHaveBeenCalled();
  });

  describe('load()', () => {
    it('carrega laudo ausente (204 No Content) transicionando para ABSENT', async () => {
      const fetchStudyReport = vi.fn().mockResolvedValue(null);
      const controller = useStudyReport({ fetchStudyReport });

      const loadPromise = controller.load('1.2.840.113619.1');
      expect(controller.phase.value).toBe('LOADING');
      expect(controller.error.value).toBeNull();

      await loadPromise;

      expect(fetchStudyReport).toHaveBeenCalledWith('1.2.840.113619.1', expect.any(AbortSignal));
      expect(controller.phase.value).toBe('ABSENT');
      expect(controller.report.value).toBeNull();
      expect(controller.content.value).toBe('');
      expect(controller.dirty.value).toBe(false);
      expect(controller.error.value).toBeNull();
    });

    it('carrega laudo existente (200 OK) transicionando para READY e populando estado', async () => {
      const snapshot = createSnapshot({ content: 'Laudo prévio' }, '"etag-100"');
      const fetchStudyReport = vi.fn().mockResolvedValue(snapshot);
      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.113619.1');

      expect(controller.phase.value).toBe('READY');
      expect(controller.report.value).toEqual(snapshot.report);
      expect(controller.content.value).toBe('Laudo prévio');
      expect(controller.dirty.value).toBe(false);
      expect(controller.codePointCount.value).toBe('Laudo prévio'.length);
      expect(controller.error.value).toBeNull();
    });

    it('transiciona para ERROR ao receber ApiError do catálogo', async () => {
      const apiError = new ApiError('API_ACCESS_DENIED');
      const fetchStudyReport = vi.fn().mockRejectedValue(apiError);
      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.113619.1');

      expect(controller.phase.value).toBe('ERROR');
      expect(controller.error.value).toBe(apiError);
      expect(controller.report.value).toBeNull();
    });

    it('traduz falha inesperada em CLIENT_UNEXPECTED_ERROR', async () => {
      const fetchStudyReport = vi.fn().mockRejectedValue(new Error('Network crash'));
      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.113619.1');

      expect(controller.phase.value).toBe('ERROR');
      expect(controller.error.value).toMatchObject({
        code: 'CLIENT_UNEXPECTED_ERROR',
        scope: 'CLIENT',
      });
    });

    it('ignora AbortError intencional sem alterar para ERROR', async () => {
      const abortError = new DOMException('The user aborted a request.', 'AbortError');
      const fetchStudyReport = vi.fn().mockRejectedValue(abortError);
      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.113619.1');

      expect(controller.phase.value).not.toBe('ERROR');
      expect(controller.error.value).toBeNull();
    });

    it('aborta requisição anterior ao mudar de UID e ignora resposta obsoleta', async () => {
      const first = deferred<ReportSnapshot | null>();
      const second = deferred<ReportSnapshot | null>();
      const fetchStudyReport = vi.fn()
        .mockReturnValueOnce(first.promise)
        .mockReturnValueOnce(second.promise);
      const controller = useStudyReport({ fetchStudyReport });

      const run1 = controller.load('1.2.840.UID.1');
      const signal1 = fetchStudyReport.mock.calls[0][1] as AbortSignal;
      expect(signal1.aborted).toBe(false);

      const run2 = controller.load('1.2.840.UID.2');
      expect(signal1.aborted).toBe(true);

      const snapshot2 = createSnapshot({ studyInstanceUid: '1.2.840.UID.2', content: 'Segundo estudo' });
      second.resolve(snapshot2);
      await run2;

      expect(controller.phase.value).toBe('READY');
      expect(controller.content.value).toBe('Segundo estudo');

      const snapshot1 = createSnapshot({ studyInstanceUid: '1.2.840.UID.1', content: 'Primeiro estudo' });
      first.resolve(snapshot1);
      await run1;

      expect(controller.phase.value).toBe('READY');
      expect(controller.content.value).toBe('Segundo estudo');
      expect(controller.report.value?.studyInstanceUid).toBe('1.2.840.UID.2');
    });

    it('dispose() aborta requisição ativa e silencia resolução posterior', async () => {
      const def = deferred<ReportSnapshot | null>();
      const fetchStudyReport = vi.fn().mockReturnValue(def.promise);
      const controller = useStudyReport({ fetchStudyReport });

      const run = controller.load('1.2.840.UID.1');
      const signal = fetchStudyReport.mock.calls[0][1] as AbortSignal;
      expect(signal.aborted).toBe(false);

      controller.dispose();
      expect(signal.aborted).toBe(true);

      def.resolve(createSnapshot({ content: 'Tarde demais' }));
      await run;

      expect(controller.report.value).toBeNull();
      expect(controller.content.value).toBe('');
    });
  });

  describe('codePointCount e dirty tracking', () => {
    it('calcula code points Unicode corretamente com emojis e pares substitutos', () => {
      const controller = useStudyReport();

      controller.content.value = 'Normal';
      expect(controller.codePointCount.value).toBe(6);

      // Emoji de coração anatômico (🫀) e polegar (👍) são 2 code units UTF-16 cada, mas 1 code point
      controller.content.value = '🫀 Normal 👍';
      // '🫀' (1) + ' ' (1) + 'Normal' (6) + ' ' (1) + '👍' (1) = 10
      expect(controller.codePointCount.value).toBe(10);
      expect(controller.content.value.length).toBe(12); // UTF-16 length é 12
    });

    it('controla dirty quando ausente: dirty é true apenas se diferir de vazio', () => {
      const controller = useStudyReport();
      expect(controller.dirty.value).toBe(false);

      controller.content.value = 'Rascunho inicial';
      expect(controller.dirty.value).toBe(true);

      controller.content.value = '';
      expect(controller.dirty.value).toBe(false);
    });

    it('controla dirty quando carregado: dirty é true apenas se diferir do texto do servidor', async () => {
      const snapshot = createSnapshot({ content: 'Texto original' });
      const fetchStudyReport = vi.fn().mockResolvedValue(snapshot);
      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.UID.1');
      expect(controller.dirty.value).toBe(false);

      controller.content.value = 'Texto original modificado';
      expect(controller.dirty.value).toBe(true);

      controller.content.value = 'Texto original';
      expect(controller.dirty.value).toBe(false);
    });
  });

  describe('saveDraft() e finalize()', () => {
    it('primeiro salvamento (ausente) executa createStudyReport (POST) com status DRAFT', async () => {
      const fetchStudyReport = vi.fn().mockResolvedValue(null);
      const savedSnapshot = createSnapshot({ content: 'Meu rascunho', status: 'DRAFT' }, '"etag-v1"');
      const createStudyReport = vi.fn().mockResolvedValue(savedSnapshot);
      const updateStudyReport = vi.fn();

      const controller = useStudyReport({ fetchStudyReport, createStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Meu rascunho';
      expect(controller.dirty.value).toBe(true);

      const savePromise = controller.saveDraft();
      expect(controller.phase.value).toBe('SAVING');
      expect(controller.error.value).toBeNull();

      await savePromise;

      expect(createStudyReport).toHaveBeenCalledWith(
        '1.2.840.UID.1',
        'Meu rascunho',
        'DRAFT',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
      expect(updateStudyReport).not.toHaveBeenCalled();

      expect(controller.phase.value).toBe('READY');
      expect(controller.report.value).toEqual(savedSnapshot.report);
      expect(controller.dirty.value).toBe(false);
      expect(controller.error.value).toBeNull();
    });

    it('finalização direta quando ausente executa createStudyReport (POST) com status FINAL', async () => {
      const fetchStudyReport = vi.fn().mockResolvedValue(null);
      const finalizedSnapshot = createSnapshot(
        { content: 'Laudo final direto', status: 'FINAL', editable: false, finalizedAt: '2026-08-25T16:00:00Z' },
        '"etag-final"',
      );
      const createStudyReport = vi.fn().mockResolvedValue(finalizedSnapshot);
      const updateStudyReport = vi.fn();

      const controller = useStudyReport({ fetchStudyReport, createStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Laudo final direto';

      await controller.finalize();

      expect(createStudyReport).toHaveBeenCalledWith(
        '1.2.840.UID.1',
        'Laudo final direto',
        'FINAL',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
      expect(updateStudyReport).not.toHaveBeenCalled();

      expect(controller.phase.value).toBe('READY');
      expect(controller.report.value?.status).toBe('FINAL');
      expect(controller.report.value?.editable).toBe(false);
      expect(controller.dirty.value).toBe(false);
    });

    it('salvamento subsequente executa updateStudyReport (PUT) com ETag anterior', async () => {
      const initialSnapshot = createSnapshot({ content: 'Versao 1', status: 'DRAFT' }, '"etag-v1"');
      const fetchStudyReport = vi.fn().mockResolvedValue(initialSnapshot);
      const updatedSnapshot = createSnapshot({ content: 'Versao 2', status: 'DRAFT' }, '"etag-v2"');
      const updateStudyReport = vi.fn().mockResolvedValue(updatedSnapshot);
      const createStudyReport = vi.fn();

      const controller = useStudyReport({ fetchStudyReport, createStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Versao 2';
      expect(controller.dirty.value).toBe(true);

      await controller.saveDraft();

      expect(updateStudyReport).toHaveBeenCalledWith(
        '1.2.840.UID.1',
        'Versao 2',
        'DRAFT',
        '"etag-v1"',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
      expect(createStudyReport).not.toHaveBeenCalled();

      expect(controller.phase.value).toBe('READY');
      expect(controller.report.value).toEqual(updatedSnapshot.report);
      expect(controller.dirty.value).toBe(false);

      // Próximo update usará o novo ETag "etag-v2"
      controller.content.value = 'Versao 3';
      const thirdSnapshot = createSnapshot({ content: 'Versao 3', status: 'DRAFT' }, '"etag-v3"');
      updateStudyReport.mockResolvedValueOnce(thirdSnapshot);

      await controller.saveDraft();
      expect(updateStudyReport).toHaveBeenLastCalledWith(
        '1.2.840.UID.1',
        'Versao 3',
        'DRAFT',
        '"etag-v2"',
        expect.anything(),
      );
    });

    it('finalização de rascunho existente executa updateStudyReport (PUT) com status FINAL', async () => {
      const initialSnapshot = createSnapshot({ content: 'Rascunho em progresso', status: 'DRAFT' }, '"etag-v1"');
      const fetchStudyReport = vi.fn().mockResolvedValue(initialSnapshot);
      const finalSnapshot = createSnapshot(
        { content: 'Rascunho em progresso finalizado', status: 'FINAL', editable: false, finalizedAt: '2026-08-25T16:00:00Z' },
        '"etag-v2"',
      );
      const updateStudyReport = vi.fn().mockResolvedValue(finalSnapshot);

      const controller = useStudyReport({ fetchStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Rascunho em progresso finalizado';

      await controller.finalize();

      expect(updateStudyReport).toHaveBeenCalledWith(
        '1.2.840.UID.1',
        'Rascunho em progresso finalizado',
        'FINAL',
        '"etag-v1"',
        expect.anything(),
      );
      expect(controller.report.value?.status).toBe('FINAL');
      expect(controller.report.value?.editable).toBe(false);
    });

    it('ignora saveDraft() e finalize() se o laudo já não for editável (terceiro ou finalizado)', async () => {
      const readOnlySnapshot = createSnapshot({ status: 'FINAL', editable: false }, '"etag-final"');
      const fetchStudyReport = vi.fn().mockResolvedValue(readOnlySnapshot);
      const createStudyReport = vi.fn();
      const updateStudyReport = vi.fn();

      const controller = useStudyReport({ fetchStudyReport, createStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Tentativa de alteração não autorizada';

      await controller.saveDraft();
      expect(createStudyReport).not.toHaveBeenCalled();
      expect(updateStudyReport).not.toHaveBeenCalled();

      await controller.finalize();
      expect(createStudyReport).not.toHaveBeenCalled();
      expect(updateStudyReport).not.toHaveBeenCalled();
    });

    it('ignora saveDraft() concorrente se já estiver em fase SAVING ou LOADING', async () => {
      const def = deferred<ReportSnapshot>();
      const createStudyReport = vi.fn().mockReturnValue(def.promise);
      const fetchStudyReport = vi.fn().mockResolvedValue(null);
      const controller = useStudyReport({ fetchStudyReport, createStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Salvando...';

      const run1 = controller.saveDraft();
      expect(controller.phase.value).toBe('SAVING');

      // Segunda chamada simultânea é ignorada
      await controller.saveDraft();
      expect(createStudyReport).toHaveBeenCalledTimes(1);

      def.resolve(createSnapshot({ content: 'Salvando...' }));
      await run1;
      expect(controller.phase.value).toBe('READY');
    });
  });

  describe('preservação de conflito (409 e 412) e reloadServerVersion()', () => {
    it('preserva content local e dirty state em conflito 409 (API_RESOURCE_CONFLICT)', async () => {
      const fetchStudyReport = vi.fn().mockResolvedValue(null);
      const conflictError = new ApiError('API_RESOURCE_CONFLICT');
      const createStudyReport = vi.fn().mockRejectedValue(conflictError);

      const controller = useStudyReport({ fetchStudyReport, createStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Texto que sofreu conflito 409';
      expect(controller.dirty.value).toBe(true);

      await controller.saveDraft();

      expect(controller.phase.value).toBe('ERROR');
      expect(controller.error.value?.code).toBe('API_RESOURCE_CONFLICT');
      // Preservação do texto local sem perda de rascunho
      expect(controller.content.value).toBe('Texto que sofreu conflito 409');
      expect(controller.dirty.value).toBe(true);
    });

    it('preserva content local e dirty state em conflito 412 (API_RESOURCE_VERSION_CONFLICT) sem reload automático', async () => {
      const initialSnapshot = createSnapshot({ content: 'Versão 1' }, '"etag-1"');
      const fetchStudyReport = vi.fn().mockResolvedValue(initialSnapshot);
      const conflict412Error = new ApiError('API_RESOURCE_VERSION_CONFLICT');
      const updateStudyReport = vi.fn().mockRejectedValue(conflict412Error);

      const controller = useStudyReport({ fetchStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Minhas alterações não salvas';
      expect(controller.dirty.value).toBe(true);

      fetchStudyReport.mockClear();

      await controller.saveDraft();

      expect(controller.phase.value).toBe('ERROR');
      expect(controller.error.value?.code).toBe('API_RESOURCE_VERSION_CONFLICT');
      // Preservação do texto e dirty
      expect(controller.content.value).toBe('Minhas alterações não salvas');
      expect(controller.dirty.value).toBe(true);

      // Não houve merge ou reload automático
      expect(fetchStudyReport).not.toHaveBeenCalled();
    });

    it('mantém o ETag anterior intacto após falha de salvamento', async () => {
      const initialSnapshot = createSnapshot({ content: 'Versão 1' }, '"etag-1"');
      const fetchStudyReport = vi.fn().mockResolvedValue(initialSnapshot);
      const updateStudyReport = vi.fn()
        .mockRejectedValueOnce(new ApiError('CLIENT_NETWORK_UNAVAILABLE'))
        .mockResolvedValueOnce(createSnapshot({ content: 'Tentativa 2' }, '"etag-2"'));

      const controller = useStudyReport({ fetchStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Tentativa 1';

      await controller.saveDraft();
      expect(controller.phase.value).toBe('ERROR');
      expect(controller.content.value).toBe('Tentativa 1');
      expect(controller.dirty.value).toBe(true);

      // Próximo salvamento ainda usa "etag-1" (já que o anterior não teve sucesso)
      controller.content.value = 'Tentativa 2';
      await controller.saveDraft();

      expect(updateStudyReport).toHaveBeenLastCalledWith(
        '1.2.840.UID.1',
        'Tentativa 2',
        'DRAFT',
        '"etag-1"',
        expect.anything(),
      );
      expect(controller.phase.value).toBe('READY');
      expect(controller.dirty.value).toBe(false);
    });

    it('reloadServerVersion() efetua GET explícito e atualiza conteúdo e ETag', async () => {
      const initialSnapshot = createSnapshot({ content: 'Versão antiga' }, '"etag-1"');
      const updatedServerSnapshot = createSnapshot({ content: 'Versão do outro médico', status: 'DRAFT' }, '"etag-2"');

      const fetchStudyReport = vi.fn()
        .mockResolvedValueOnce(initialSnapshot)
        .mockResolvedValueOnce(updatedServerSnapshot);
      const updateStudyReport = vi.fn().mockRejectedValue(new ApiError('API_RESOURCE_VERSION_CONFLICT'));

      const controller = useStudyReport({ fetchStudyReport, updateStudyReport });

      await controller.load('1.2.840.UID.1');
      controller.content.value = 'Meu texto em conflito';

      await controller.saveDraft();
      expect(controller.phase.value).toBe('ERROR');

      // UI invoca reloadServerVersion explicitamente
      await controller.reloadServerVersion();

      expect(fetchStudyReport).toHaveBeenCalledTimes(2);
      expect(controller.phase.value).toBe('READY');
      expect(controller.report.value).toEqual(updatedServerSnapshot.report);
      expect(controller.content.value).toBe('Versão do outro médico');
      expect(controller.dirty.value).toBe(false);
      expect(controller.error.value).toBeNull();
    });

    it('reloadServerVersion() transiciona para ABSENT se o laudo não existir mais', async () => {
      const initialSnapshot = createSnapshot({ content: 'Versão antiga' }, '"etag-1"');
      const fetchStudyReport = vi.fn()
        .mockResolvedValueOnce(initialSnapshot)
        .mockResolvedValueOnce(null);

      const controller = useStudyReport({ fetchStudyReport });

      await controller.load('1.2.840.UID.1');
      expect(controller.phase.value).toBe('READY');

      await controller.reloadServerVersion();

      expect(controller.phase.value).toBe('ABSENT');
      expect(controller.report.value).toBeNull();
      expect(controller.content.value).toBe('');
      expect(controller.dirty.value).toBe(false);
    });
  });
});
