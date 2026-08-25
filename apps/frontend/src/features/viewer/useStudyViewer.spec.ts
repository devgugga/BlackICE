import { describe, it, expect, vi, afterEach } from 'vitest';
import { useStudyViewer } from '@/features/viewer/useStudyViewer';
import { ApiError } from '@/shared/api/problems/api-error';
import type { StudyViewerSummary, ViewerSeriesInstances } from '@/features/viewer/viewer.types';

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

const STUDY_UID = '1.2.840.113619.2.55.3.604688435.123.1599720123.467';
const SERIES_SUPPORTED_1 = '1.2.840.113619.2.55.3.604688435.124';
const SERIES_UNSUPPORTED = '1.2.840.113619.2.55.3.604688435.125';
const SERIES_SUPPORTED_2 = '1.2.840.113619.2.55.3.604688435.126';

function createStudySummary(overrides?: Partial<StudyViewerSummary>): StudyViewerSummary {
  return {
    studyInstanceUid: STUDY_UID,
    patientName: 'SILVA^MARIA',
    patientId: '123',
    patientIdIssuer: 'HOSPITAL-A',
    studyDate: '2026-08-22',
    studyTime: '10:35:12',
    description: 'CT CHEST',
    series: [
      {
        seriesInstanceUid: SERIES_UNSUPPORTED,
        seriesNumber: 1,
        modality: 'SR',
        description: 'REPORT',
        instanceCount: 1,
        availability: 'UNSUPPORTED',
        unsupportedReason: 'NON_IMAGE_OBJECT',
      },
      {
        seriesInstanceUid: SERIES_SUPPORTED_1,
        seriesNumber: 2,
        modality: 'CT',
        description: 'AXIAL',
        instanceCount: 100,
        availability: 'SUPPORTED',
        unsupportedReason: null,
      },
      {
        seriesInstanceUid: SERIES_SUPPORTED_2,
        seriesNumber: 3,
        modality: 'CT',
        description: 'CORONAL',
        instanceCount: 80,
        availability: 'SUPPORTED',
        unsupportedReason: null,
      },
    ],
    ...overrides,
  };
}

function createSeriesInstances(seriesUid: string = SERIES_SUPPORTED_1): ViewerSeriesInstances {
  return {
    studyInstanceUid: STUDY_UID,
    seriesInstanceUid: seriesUid,
    instances: [
      {
        sopInstanceUid: '1.2.840.113619.2.55.3.604688435.999',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
        instanceNumber: 1,
        rows: 512,
        columns: 512,
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 1,
        planarConfiguration: null,
        imagePositionPatient: [0, 0, 0],
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        pixelSpacing: [0.5, 0.5],
        frameOfReferenceUid: '1.2.3.4',
        rescaleIntercept: 0,
        rescaleSlope: 1,
        windowCenter: [40],
        windowWidth: [400],
      },
    ],
  };
}

describe('useStudyViewer', () => {
  it('inicia no estado IDLE com referências nulas', () => {
    const fetchStudy = vi.fn();
    const fetchInstances = vi.fn();
    const viewer = useStudyViewer(fetchStudy, fetchInstances);

    expect(viewer.phase.value).toBe('IDLE');
    expect(viewer.study.value).toBeNull();
    expect(viewer.selectedSeriesUid.value).toBeNull();
    expect(viewer.activeSeriesInstances.value).toBeNull();
    expect(viewer.error.value).toBeNull();
    expect(viewer.selectedSeries.value).toBeNull();
    expect(fetchStudy).not.toHaveBeenCalled();
    expect(fetchInstances).not.toHaveBeenCalled();
  });

  describe('loadStudy', () => {
    it('carrega o estudo e seleciona a primeira série SUPPORTED sem carregar instâncias (lazy)', async () => {
      const studyData = createStudySummary();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      const promise = viewer.loadStudy(STUDY_UID);
      expect(viewer.phase.value).toBe('LOADING_STUDY');
      expect(viewer.error.value).toBeNull();

      await promise;

      expect(fetchStudy).toHaveBeenCalledWith(STUDY_UID, expect.any(AbortSignal));
      expect(viewer.phase.value).toBe('READY');
      expect(viewer.study.value).toEqual(studyData);
      // Selecionou a primeira SUPPORTED (série 2), pulando a série 1 que é UNSUPPORTED
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);
      expect(viewer.selectedSeries.value).toEqual(studyData.series[1]);
      // Não chamou fetchInstances (lazy)
      expect(fetchInstances).not.toHaveBeenCalled();
      expect(viewer.activeSeriesInstances.value).toBeNull();
    });

    it('quando todas as séries forem UNSUPPORTED, selectedSeriesUid fica nulo e não busca instâncias', async () => {
      const unsupportedStudy = createStudySummary({
        series: [
          {
            seriesInstanceUid: SERIES_UNSUPPORTED,
            seriesNumber: 1,
            modality: 'SR',
            description: 'REPORT',
            instanceCount: 1,
            availability: 'UNSUPPORTED',
            unsupportedReason: 'NON_IMAGE_OBJECT',
          },
        ],
      });
      const fetchStudy = vi.fn().mockResolvedValue(unsupportedStudy);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);

      expect(viewer.phase.value).toBe('READY');
      expect(viewer.study.value).toEqual(unsupportedStudy);
      expect(viewer.selectedSeriesUid.value).toBeNull();
      expect(viewer.selectedSeries.value).toBeNull();
      expect(fetchInstances).not.toHaveBeenCalled();
    });

    it('quando o estudo não tem nenhuma série, selectedSeriesUid fica nulo', async () => {
      const emptyStudy = createStudySummary({ series: [] });
      const fetchStudy = vi.fn().mockResolvedValue(emptyStudy);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);

      expect(viewer.phase.value).toBe('READY');
      expect(viewer.selectedSeriesUid.value).toBeNull();
      expect(viewer.selectedSeries.value).toBeNull();
    });

    it('transiciona para ERROR e armazena ApiError quando fetchStudy falha', async () => {
      const apiError = new ApiError('API_ARCHIVE_UNAVAILABLE');
      const fetchStudy = vi.fn().mockRejectedValue(apiError);
      const viewer = useStudyViewer(fetchStudy);

      await viewer.loadStudy(STUDY_UID);

      expect(viewer.phase.value).toBe('ERROR');
      expect(viewer.error.value).toBe(apiError);
      expect(viewer.error.value?.code).toBe('API_ARCHIVE_UNAVAILABLE');
      expect(viewer.error.value?.allowsManualRetry).toBe(true);
      expect(viewer.study.value).toBeNull();
    });

    it('traduz erro não-ApiError em CLIENT_UNEXPECTED_ERROR', async () => {
      const fetchStudy = vi.fn().mockRejectedValue(new Error('Unexpected JS crash'));
      const viewer = useStudyViewer(fetchStudy);

      await viewer.loadStudy(STUDY_UID);

      expect(viewer.phase.value).toBe('ERROR');
      expect(viewer.error.value).toMatchObject({
        code: 'CLIENT_UNEXPECTED_ERROR',
        scope: 'CLIENT',
      });
    });

    it('aborta requisição anterior de loadStudy e ignora resposta atrasada', async () => {
      const first = deferred<StudyViewerSummary>();
      const second = deferred<StudyViewerSummary>();
      const fetchStudy = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
      const viewer = useStudyViewer(fetchStudy);

      const run1 = viewer.loadStudy('study-1');
      const signal1 = fetchStudy.mock.calls[0][1] as AbortSignal;
      expect(signal1.aborted).toBe(false);

      const run2 = viewer.loadStudy('study-2');
      expect(signal1.aborted).toBe(true);

      const study2 = createStudySummary({ studyInstanceUid: 'study-2' });
      second.resolve(study2);
      await run2;

      expect(viewer.study.value?.studyInstanceUid).toBe('study-2');

      const study1 = createStudySummary({ studyInstanceUid: 'study-1' });
      first.resolve(study1);
      await run1;

      // Mantém o resultado da segunda chamada (geração mais recente)
      expect(viewer.study.value?.studyInstanceUid).toBe('study-2');
      expect(viewer.phase.value).toBe('READY');
    });

    it('ignora falha atrasada de requisição anterior abortada', async () => {
      const first = deferred<StudyViewerSummary>();
      const second = deferred<StudyViewerSummary>();
      const fetchStudy = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
      const viewer = useStudyViewer(fetchStudy);

      const run1 = viewer.loadStudy('study-1');
      const run2 = viewer.loadStudy('study-2');

      const study2 = createStudySummary({ studyInstanceUid: 'study-2' });
      second.resolve(study2);
      await run2;

      first.reject(new ApiError('API_RESOURCE_NOT_FOUND'));
      await run1;

      expect(viewer.phase.value).toBe('READY');
      expect(viewer.error.value).toBeNull();
      expect(viewer.study.value?.studyInstanceUid).toBe('study-2');
    });

    it('silencia AbortError intencional sem transicionar para ERROR', async () => {
      const abortError = new DOMException('The user aborted a request.', 'AbortError');
      const fetchStudy = vi.fn().mockRejectedValue(abortError);
      const viewer = useStudyViewer(fetchStudy);

      await viewer.loadStudy(STUDY_UID);

      expect(viewer.phase.value).not.toBe('ERROR');
      expect(viewer.error.value).toBeNull();
    });
  });

  describe('activateSeries', () => {
    it('busca instâncias da série selecionada por padrão e atualiza activeSeriesInstances', async () => {
      const studyData = createStudySummary();
      const instancesData = createSeriesInstances(SERIES_SUPPORTED_1);
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockResolvedValue(instancesData);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);

      const activatePromise = viewer.activateSeries();
      expect(viewer.phase.value).toBe('LOADING_SERIES');

      await activatePromise;

      expect(fetchInstances).toHaveBeenCalledWith(STUDY_UID, SERIES_SUPPORTED_1, expect.any(AbortSignal));
      expect(viewer.phase.value).toBe('READY');
      expect(viewer.activeSeriesInstances.value).toEqual(instancesData);
    });

    it('ativa série específica quando seriesUid é fornecido', async () => {
      const studyData = createStudySummary();
      const instancesData2 = createSeriesInstances(SERIES_SUPPORTED_2);
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockResolvedValue(instancesData2);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);

      await viewer.activateSeries(SERIES_SUPPORTED_2);

      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_2);
      expect(viewer.selectedSeries.value?.seriesInstanceUid).toBe(SERIES_SUPPORTED_2);
      expect(viewer.activeSeriesInstances.value).toEqual(instancesData2);
      expect(viewer.phase.value).toBe('READY');
    });

    it('não executa se não houver estudo carregado', async () => {
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(vi.fn(), fetchInstances);

      await viewer.activateSeries(SERIES_SUPPORTED_1);

      expect(fetchInstances).not.toHaveBeenCalled();
      expect(viewer.phase.value).toBe('IDLE');
    });

    it('não executa se a série for UNSUPPORTED', async () => {
      const studyData = createStudySummary();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      await viewer.activateSeries(SERIES_UNSUPPORTED);

      expect(fetchInstances).not.toHaveBeenCalled();
      expect(viewer.activeSeriesInstances.value).toBeNull();
      // selectedSeriesUid não muda para série não suportada
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);
    });

    it('não executa se a série não existir no estudo', async () => {
      const studyData = createStudySummary();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      await viewer.activateSeries('non-existent-series-uid');

      expect(fetchInstances).not.toHaveBeenCalled();
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);
    });

    it('transiciona para ERROR quando fetchInstances falha', async () => {
      const studyData = createStudySummary();
      const apiError = new ApiError('API_RESOURCE_NOT_FOUND');
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockRejectedValue(apiError);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      await viewer.activateSeries(SERIES_SUPPORTED_1);

      expect(viewer.phase.value).toBe('ERROR');
      expect(viewer.error.value).toBe(apiError);
      expect(viewer.activeSeriesInstances.value).toBeNull();
    });

    it('aborta busca anterior ao alternar de série rapidamente (race condition)', async () => {
      const studyData = createStudySummary();
      const def1 = deferred<ViewerSeriesInstances>();
      const def2 = deferred<ViewerSeriesInstances>();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockReturnValueOnce(def1.promise).mockReturnValueOnce(def2.promise);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);

      const run1 = viewer.activateSeries(SERIES_SUPPORTED_1);
      const signal1 = fetchInstances.mock.calls[0][2] as AbortSignal;
      expect(signal1.aborted).toBe(false);

      const run2 = viewer.activateSeries(SERIES_SUPPORTED_2);
      expect(signal1.aborted).toBe(true);

      const instances2 = createSeriesInstances(SERIES_SUPPORTED_2);
      def2.resolve(instances2);
      await run2;

      expect(viewer.activeSeriesInstances.value).toEqual(instances2);
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_2);

      const instances1 = createSeriesInstances(SERIES_SUPPORTED_1);
      def1.resolve(instances1);
      await run1;

      // Resposta atrasada da série 1 não sobrescreve série 2
      expect(viewer.activeSeriesInstances.value).toEqual(instances2);
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_2);
    });
  });

  describe('selectSeries', () => {
    it('seleciona série suportada e ativa imediatamente quando activate=true', async () => {
      const studyData = createStudySummary();
      const instances2 = createSeriesInstances(SERIES_SUPPORTED_2);
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockResolvedValue(instances2);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      await viewer.selectSeries(SERIES_SUPPORTED_2, true);

      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_2);
      expect(viewer.activeSeriesInstances.value).toEqual(instances2);
      expect(viewer.phase.value).toBe('READY');
    });

    it('seleciona série suportada sem ativar quando activate=false (deactivateSeries)', async () => {
      const studyData = createStudySummary();
      const instances1 = createSeriesInstances(SERIES_SUPPORTED_1);
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockResolvedValue(instances1);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      await viewer.activateSeries(SERIES_SUPPORTED_1);
      expect(viewer.activeSeriesInstances.value).toEqual(instances1);

      await viewer.selectSeries(SERIES_SUPPORTED_2, false);

      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_2);
      expect(viewer.activeSeriesInstances.value).toBeNull();
      expect(viewer.phase.value).toBe('READY');
    });

    it('ignora seleção de série UNSUPPORTED', async () => {
      const studyData = createStudySummary();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);

      await viewer.selectSeries(SERIES_UNSUPPORTED, true);

      // Não seleciona nem ativa
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);
      expect(fetchInstances).not.toHaveBeenCalled();
    });
  });

  describe('deactivateSeries', () => {
    it('aborta busca de instâncias pendente e limpa activeSeriesInstances preservando selectedSeriesUid', async () => {
      const studyData = createStudySummary();
      const def = deferred<ViewerSeriesInstances>();
      const fetchStudy = vi.fn().mockResolvedValue(studyData);
      const fetchInstances = vi.fn().mockReturnValue(def.promise);
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      await viewer.loadStudy(STUDY_UID);
      const activateRun = viewer.activateSeries(SERIES_SUPPORTED_1);
      const signal = fetchInstances.mock.calls[0][2] as AbortSignal;
      expect(signal.aborted).toBe(false);
      expect(viewer.phase.value).toBe('LOADING_SERIES');

      viewer.deactivateSeries();

      expect(signal.aborted).toBe(true);
      expect(viewer.activeSeriesInstances.value).toBeNull();
      expect(viewer.selectedSeriesUid.value).toBe(SERIES_SUPPORTED_1);
      expect(viewer.phase.value).toBe('READY');

      def.resolve(createSeriesInstances(SERIES_SUPPORTED_1));
      await activateRun;

      // Resolução tardia não altera estado
      expect(viewer.activeSeriesInstances.value).toBeNull();
      expect(viewer.phase.value).toBe('READY');
    });
  });

  describe('dispose', () => {
    it('aborta controladores pendentes e ignora qualquer resolução posterior', async () => {
      const defStudy = deferred<StudyViewerSummary>();
      const fetchStudy = vi.fn().mockReturnValue(defStudy.promise);
      const fetchInstances = vi.fn();
      const viewer = useStudyViewer(fetchStudy, fetchInstances);

      const loadRun = viewer.loadStudy(STUDY_UID);
      const signal = fetchStudy.mock.calls[0][1] as AbortSignal;
      expect(signal.aborted).toBe(false);

      viewer.dispose();
      expect(signal.aborted).toBe(true);

      defStudy.resolve(createStudySummary());
      await loadRun;

      expect(viewer.study.value).toBeNull();
      expect(viewer.selectedSeriesUid.value).toBeNull();
      expect(viewer.phase.value).toBe('IDLE');
    });
  });
});
