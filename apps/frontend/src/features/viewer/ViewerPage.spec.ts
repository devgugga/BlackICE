import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { ref, readonly, reactive } from 'vue';
import ViewerPage from '@/features/viewer/ViewerPage.vue';
import ReportPanel from '@/features/reports/ReportPanel.vue';
import { fetchStudyViewer, fetchSeriesInstances } from '@/features/viewer/viewer.api';
import { fetchStudyReport, createStudyReport, updateStudyReport } from '@/features/reports/report.api';
import { loadDicomViewport } from '@/features/viewer/loadDicomViewport';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_MESSAGES } from '@/shared/api/problems/problem-messages.pt-BR';
import type { StudyViewerSummary, ViewerSeriesInstances } from '@/features/viewer/viewer.types';
import type { ReportSnapshot } from '@/features/reports/report.types';

const pushMock = vi.fn();
const backMock = vi.fn();
let currentRouteParams = reactive<Record<string, string>>({
  studyUid: '1.2.840.113619.2.55.3.123',
});
let currentRouteQuery: Record<string, string> = {};

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
    back: backMock,
  }),
  useRoute: () => ({
    params: currentRouteParams,
    query: currentRouteQuery,
  }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
}));

vi.mock('@/features/viewer/viewer.api', () => ({
  fetchStudyViewer: vi.fn(),
  fetchSeriesInstances: vi.fn(),
}));
const fetchStudyViewerMock = vi.mocked(fetchStudyViewer);
const fetchSeriesInstancesMock = vi.mocked(fetchSeriesInstances);

vi.mock('@/features/reports/report.api', () => ({
  fetchStudyReport: vi.fn(),
  createStudyReport: vi.fn(),
  updateStudyReport: vi.fn(),
}));
const fetchStudyReportMock = vi.mocked(fetchStudyReport);
const createStudyReportMock = vi.mocked(createStudyReport);
const updateStudyReportMock = vi.mocked(updateStudyReport);

let viewportMountCount = 0;
let viewportUnmountCount = 0;
const mockViewportReset = vi.fn();

vi.mock('@/features/viewer/loadDicomViewport', () => ({
  loadDicomViewport: vi.fn(async () => {
    const { defineComponent, h, onMounted, onUnmounted } = await import('vue');
    return defineComponent({
      name: 'DicomViewport',
      props: ['instances', 'activeTool'],
      emits: ['failure'],
      setup(props, { emit, expose }) {
        onMounted(() => {
          viewportMountCount++;
        });
        onUnmounted(() => {
          viewportUnmountCount++;
        });
        expose({ reset: mockViewportReset });
        return () =>
          h('div', {
            'data-testid': 'dicom-viewport',
            'data-active-tool': props.activeTool,
            'data-instance-count': props.instances?.instances.length ?? 0,
            onClick: () => emit('failure', 'CLIENT_DICOM_IMAGE_UNSUPPORTED'),
          });
      },
    });
  }),
}));
const loadDicomViewportMock = vi.mocked(loadDicomViewport);

let canRenderViewerRef = ref(true);
vi.mock('@/features/viewer/useViewerCapability', () => ({
  VIEWER_MEDIA_QUERY: '(min-width: 1024px)',
  useViewerCapability: () => ({
    get canRenderViewer() {
      return readonly(canRenderViewerRef);
    },
    dispose: vi.fn(),
  }),
}));

function createStudySummary(overrides?: Partial<StudyViewerSummary>): StudyViewerSummary {
  return {
    studyInstanceUid: '1.2.840.113619.2.55.3.123',
    patientName: 'MARIA^SILVA',
    patientId: 'PAT-001',
    patientIdIssuer: 'HOSPITAL-A',
    studyDate: '2026-08-22',
    studyTime: '10:30:00',
    description: 'CT CHEST',
    series: [
      {
        seriesInstanceUid: '1.2.840.113619.2.55.3.123.1',
        seriesNumber: 1,
        modality: 'CT',
        description: 'AXIAL 5mm',
        instanceCount: 40,
        availability: 'SUPPORTED',
        unsupportedReason: null,
      },
      {
        seriesInstanceUid: '1.2.840.113619.2.55.3.123.2',
        seriesNumber: 2,
        modality: 'SR',
        description: 'REPORT',
        instanceCount: 1,
        availability: 'UNSUPPORTED',
        unsupportedReason: 'NON_IMAGE_OBJECT',
      },
    ],
    ...overrides,
  };
}

function createSeriesInstances(seriesUid = '1.2.840.113619.2.55.3.123.1'): ViewerSeriesInstances {
  return {
    studyInstanceUid: '1.2.840.113619.2.55.3.123',
    seriesInstanceUid: seriesUid,
    instances: [
      {
        sopInstanceUid: `${seriesUid}.1`,
        sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
        instanceNumber: 1,
        rows: 512,
        columns: 512,
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 0,
        planarConfiguration: null,
        imagePositionPatient: [0, 0, 0],
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        pixelSpacing: [0.7, 0.7],
        frameOfReferenceUid: '1.2.3.4.5',
        rescaleIntercept: -1024,
        rescaleSlope: 1,
        windowCenter: [40],
        windowWidth: [400],
      },
    ],
  };
}

const sampleReportSnapshot: ReportSnapshot = {
  report: {
    studyInstanceUid: '1.2.840.113619.2.55.3.123',
    authorDisplayName: 'dr.teste',
    status: 'DRAFT',
    content: 'Achados pulmonares normais.',
    editable: true,
    createdAt: '2026-08-25T14:00:00Z',
    updatedAt: '2026-08-25T14:15:00Z',
    finalizedAt: null,
  },
  etag: '"etag-123"',
};

describe('ViewerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    viewportMountCount = 0;
    viewportUnmountCount = 0;
    canRenderViewerRef = ref(true);
    currentRouteParams.studyUid = '1.2.840.113619.2.55.3.123';
    currentRouteQuery = {};
    window.history.replaceState(null, '', '/studies/1.2.840.113619.2.55.3.123');

    fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);
    createStudyReportMock.mockResolvedValue(sampleReportSnapshot);
    updateStudyReportMock.mockResolvedValue(sampleReportSnapshot);
  });

  it('exibe indicador de carregamento de estudo enquanto a busca do resumo estiver pendente', async () => {
    let resolveStudy: (res: StudyViewerSummary) => void = () => {};
    fetchStudyViewerMock.mockReturnValue(
      new Promise<StudyViewerSummary>((resolve) => {
        resolveStudy = resolve;
      }),
    );

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchStudyViewerMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
    const loadingStatus = wrapper.find('[role="status"]');
    expect(loadingStatus.exists()).toBe(true);
    expect(loadingStatus.text()).toContain('Carregando estudo');

    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
    resolveStudy(createStudySummary());
    await flushPromises();

    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
  });

  it('renderiza falha de resumo em nível de página com TraceID e permite tentar novamente para política MANUAL', async () => {
    const traceId = '4bf92f3577b34da6a3ce929d0e0e4736';
    fetchStudyViewerMock.mockRejectedValueOnce(
      new ApiError('API_ARCHIVE_UNAVAILABLE', { traceId }),
    );

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain(PROBLEM_MESSAGES.API_ARCHIVE_UNAVAILABLE);
    expect(alert.find('code').text()).toBe(traceId);
    expect(wrapper.text()).not.toContain('1.2.840.113619.2.55.3.123'); // Nunca renderiza UID bruto

    const retryBtn = alert.find('button');
    expect(retryBtn.exists()).toBe(true);
    expect(retryBtn.text()).toContain('Tentar novamente');

    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
    await retryBtn.trigger('click');
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
  });

  it('não oferece botão de retentativa quando a política de erro de resumo for NEVER', async () => {
    fetchStudyViewerMock.mockRejectedValueOnce(new ApiError('API_RESOURCE_NOT_FOUND'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain(PROBLEM_MESSAGES.API_RESOURCE_NOT_FOUND);
    expect(alert.find('button').exists()).toBe(false);
  });

  it('carrega estudo com sucesso, seleciona e ativa a primeira série suportada', async () => {
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchStudyViewerMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
    expect(fetchSeriesInstancesMock).toHaveBeenCalledWith(
      '1.2.840.113619.2.55.3.123',
      '1.2.840.113619.2.55.3.123.1',
      expect.anything(),
    );

    // Header metadata
    expect(wrapper.text()).toContain('MARIA^SILVA');
    expect(wrapper.text()).toContain('PAT-001 · HOSPITAL-A');
    expect(wrapper.text()).toContain('2026-08-22 10:30:00');
    expect(wrapper.text()).toContain('CT CHEST');

    // Rail & Toolbar
    expect(wrapper.find('[aria-label="Painel de séries do estudo"]').exists()).toBe(true);
    expect(wrapper.find('[role="toolbar"]').exists()).toBe(true);

    // Viewport mounted
    const viewport = wrapper.find('[data-testid="dicom-viewport"]');
    expect(viewport.exists()).toBe(true);
    expect(viewport.attributes('data-active-tool')).toBe('WINDOW_LEVEL');
  });

  it('em tela estreita/mobile não requisita instâncias, não importa viewport e renderiza mensagem segura', async () => {
    canRenderViewerRef = ref(false);
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchStudyViewerMock).toHaveBeenCalledTimes(1);
    expect(fetchSeriesInstancesMock).not.toHaveBeenCalled();
    expect(loadDicomViewportMock).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Use uma tela maior para visualizar as imagens deste estudo.');
  });

  it('reage a transições de capacidade (crossing up ativa série, crossing down desativa e desmonta)', async () => {
    canRenderViewerRef = ref(false);
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchSeriesInstancesMock).not.toHaveBeenCalled();
    expect(loadDicomViewportMock).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('Use uma tela maior para visualizar as imagens deste estudo.');

    // Crossing up (tela aumentou / mudou para landscape)
    canRenderViewerRef.value = true;
    await flushPromises();

    expect(fetchSeriesInstancesMock).toHaveBeenCalledTimes(1);
    expect(loadDicomViewportMock).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('Use uma tela maior para visualizar as imagens deste estudo.');

    // Crossing down (tela diminuiu)
    canRenderViewerRef.value = false;
    await flushPromises();

    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Use uma tela maior para visualizar as imagens deste estudo.');
  });

  it('estudo com séries mistas exibe suporte no rail e permite trocar entre séries suportadas', async () => {
    const studyData = createStudySummary({
      series: [
        {
          seriesInstanceUid: '1.2.840.113619.2.55.3.123.1',
          seriesNumber: 1,
          modality: 'CT',
          description: 'AXIAL',
          instanceCount: 10,
          availability: 'SUPPORTED',
          unsupportedReason: null,
        },
        {
          seriesInstanceUid: '1.2.840.113619.2.55.3.123.2',
          seriesNumber: 2,
          modality: 'SR',
          description: 'REPORT',
          instanceCount: 1,
          availability: 'UNSUPPORTED',
          unsupportedReason: 'NON_IMAGE_OBJECT',
        },
        {
          seriesInstanceUid: '1.2.840.113619.2.55.3.123.3',
          seriesNumber: 3,
          modality: 'CT',
          description: 'CORONAL',
          instanceCount: 10,
          availability: 'SUPPORTED',
          unsupportedReason: null,
        },
      ],
    });

    fetchStudyViewerMock.mockResolvedValue(studyData);
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchSeriesInstancesMock).toHaveBeenCalledWith(
      '1.2.840.113619.2.55.3.123',
      '1.2.840.113619.2.55.3.123.1',
      expect.anything(),
    );

    const cards = wrapper.findAll('[data-testid="series-card"]');
    expect(cards.length).toBe(3);

    // Série 2 não é suportada e seu botão está desabilitado
    expect(cards[1].attributes('disabled')).toBeDefined();

    // Clicar na série 3 (suportada)
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.3'));
    await cards[2].trigger('click');
    await flushPromises();

    expect(fetchSeriesInstancesMock).toHaveBeenLastCalledWith(
      '1.2.840.113619.2.55.3.123',
      '1.2.840.113619.2.55.3.123.3',
      expect.anything(),
    );
  });

  it('exibe mensagem apropriada quando todas as séries do estudo forem não suportadas', async () => {
    const studyData = createStudySummary({
      series: [
        {
          seriesInstanceUid: '1.2.840.113619.2.55.3.123.1',
          seriesNumber: 1,
          modality: 'SR',
          description: 'REPORT',
          instanceCount: 1,
          availability: 'UNSUPPORTED',
          unsupportedReason: 'NON_IMAGE_OBJECT',
        },
      ],
    });

    fetchStudyViewerMock.mockResolvedValue(studyData);

    const wrapper = mount(ViewerPage);
    await flushPromises();

    expect(fetchSeriesInstancesMock).not.toHaveBeenCalled();
    expect(loadDicomViewportMock).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Nenhuma série suportada para visualização neste estudo.');
  });

  it('exibe erro confinado ao viewport quando a busca de instâncias da série falhar, mantendo header e rail', async () => {
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    const traceId = 'series-trace-id-123';
    fetchSeriesInstancesMock.mockRejectedValueOnce(
      new ApiError('API_ARCHIVE_UNAVAILABLE', { traceId }),
    );

    const wrapper = mount(ViewerPage);
    await flushPromises();

    // Header e Rail continuam visíveis
    expect(wrapper.text()).toContain('MARIA^SILVA');
    expect(wrapper.find('[aria-label="Painel de séries do estudo"]').exists()).toBe(true);

    // Erro confinado ao viewport
    const viewportAlert = wrapper.find('.viewport-container [role="alert"]');
    expect(viewportAlert.exists()).toBe(true);
    expect(viewportAlert.text()).toContain(PROBLEM_MESSAGES.API_ARCHIVE_UNAVAILABLE);
    expect(viewportAlert.find('code').text()).toBe(traceId);

    // Tentar novamente restaura a série
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));
    await viewportAlert.find('button').trigger('click');
    await flushPromises();

    expect(wrapper.find('.viewport-container [role="alert"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
  });

  it('exibe erro confinado ao viewport quando o DicomViewport emitir falha', async () => {
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const viewport = wrapper.find('[data-testid="dicom-viewport"]');
    expect(viewport.exists()).toBe(true);

    // Disparar erro do viewport
    await viewport.trigger('click');
    await flushPromises();

    const viewportAlert = wrapper.find('.viewport-container [role="alert"]');
    expect(viewportAlert.exists()).toBe(true);
    expect(viewportAlert.text()).toContain(PROBLEM_MESSAGES.CLIENT_DICOM_IMAGE_UNSUPPORTED);
  });

  it('integra ferramentas do toolbar e redefine para WINDOW_LEVEL ao acionar reset', async () => {
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances('1.2.840.113619.2.55.3.123.1'));

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const zoomBtn = wrapper.find('[data-testid="tool-ZOOM"]');
    await zoomBtn.trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="dicom-viewport"]').attributes('data-active-tool')).toBe('ZOOM');

    const resetBtn = wrapper.find('[data-testid="tool-reset"]');
    await resetBtn.trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="dicom-viewport"]').attributes('data-active-tool')).toBe('WINDOW_LEVEL');
    expect(mockViewportReset).toHaveBeenCalled();
  });

  it('navega de volta via router.back quando o histórico anterior vier da worklist (/studies)', async () => {
    window.history.replaceState({ back: '/studies' }, '', '/studies/1.2.840.113619.2.55.3.123');
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const backBtn = wrapper.find('[data-testid="back-button"]');
    await backBtn.trigger('click');

    expect(backMock).toHaveBeenCalledTimes(1);
    expect(pushMock).not.toHaveBeenCalled();
  });

  it('navega de volta via router.back quando o histórico anterior vier da worklist com filtros (/studies?...)', async () => {
    window.history.replaceState({ back: '/studies?patientName=SILVA' }, '', '/studies/1.2.840.113619.2.55.3.123');
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const backBtn = wrapper.find('[data-testid="back-button"]');
    await backBtn.trigger('click');

    expect(backMock).toHaveBeenCalledTimes(1);
    expect(pushMock).not.toHaveBeenCalled();
  });

  it('faz fallback para router.push({ name: "worklist" }) em deep link direto (sem histórico de worklist)', async () => {
    window.history.replaceState(null, '', '/studies/1.2.840.113619.2.55.3.123');
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const backBtn = wrapper.find('[data-testid="back-button"]');
    await backBtn.trigger('click');

    expect(pushMock).toHaveBeenCalledWith({ name: 'worklist' });
    expect(backMock).not.toHaveBeenCalled();
  });

  it('ignora parâmetros arbitrários de returnUrl na URL do deep link', async () => {
    currentRouteQuery = { returnUrl: 'https://malicious-site.example.com' };
    window.history.replaceState(null, '', '/studies/1.2.840.113619.2.55.3.123?returnUrl=https://malicious-site.example.com');
    fetchStudyViewerMock.mockResolvedValue(createStudySummary());
    fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());

    const wrapper = mount(ViewerPage);
    await flushPromises();

    const backBtn = wrapper.find('[data-testid="back-button"]');
    await backBtn.trigger('click');

    expect(pushMock).toHaveBeenCalledWith({ name: 'worklist' });
    expect(backMock).not.toHaveBeenCalled();
  });

  describe('Report Panel Composition and Cornerstone Isolation', () => {
    it('carrega estudo e laudo em paralelo passando studyUid para ReportPanel', async () => {
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(fetchStudyViewerMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
      expect(fetchStudyReportMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
      expect(wrapper.find('[data-testid="report-panel"]').exists()).toBe(true);
      expect(wrapper.find('textarea').element.value).toBe('Achados pulmonares normais.');
      expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
    });

    it('mantém o visualizador e viewport ativos caso a busca do laudo falhe', async () => {
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
      fetchStudyReportMock.mockRejectedValue(new ApiError('API_ARCHIVE_UNAVAILABLE'));

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
      expect(wrapper.find('[aria-label="Painel de séries do estudo"]').exists()).toBe(true);
      expect(wrapper.find('[data-testid="report-error"]').exists()).toBe(true);
    });

    it('mantém o painel de laudo utilizável caso a busca do resumo do estudo falhe', async () => {
      fetchStudyViewerMock.mockRejectedValue(new ApiError('API_ARCHIVE_UNAVAILABLE'));
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(wrapper.find('.page-error').exists()).toBe(true);
      expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(false);
      expect(wrapper.find('[data-testid="report-panel"]').exists()).toBe(true);
      expect(wrapper.find('textarea').element.value).toBe('Achados pulmonares normais.');
    });

    it('em REPORT_ONLY exibe mensagem de tela maior ao lado do painel de laudo completo sem requisitar instâncias', async () => {
      canRenderViewerRef = ref(false);
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(fetchSeriesInstancesMock).not.toHaveBeenCalled();
      expect(loadDicomViewportMock).not.toHaveBeenCalled();
      expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(false);
      expect(wrapper.text()).toContain('Use uma tela maior para visualizar as imagens deste estudo.');
      expect(wrapper.find('[data-testid="report-panel"]').exists()).toBe(true);
      expect(wrapper.find('textarea').element.value).toBe('Achados pulmonares normais.');
    });

    it('abrir, fechar e reabrir o painel de laudos repetidamente não recria nem desmonta o viewport Cornerstone', async () => {
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage, { attachTo: document.body });
      await flushPromises();

      expect(wrapper.find('[data-testid="dicom-viewport"]').exists()).toBe(true);
      expect(viewportMountCount).toBe(1);
      expect(viewportUnmountCount).toBe(0);

      const toggleBtn = wrapper.find('[data-testid="toggle-report-btn"]');
      expect(toggleBtn.exists()).toBe(true);

      // Alterna visibilidade do laudo várias vezes
      await toggleBtn.trigger('click');
      await flushPromises();
      expect(viewportMountCount).toBe(1);
      expect(viewportUnmountCount).toBe(0);

      await toggleBtn.trigger('click');
      await flushPromises();
      expect(viewportMountCount).toBe(1);
      expect(viewportUnmountCount).toBe(0);

      await toggleBtn.trigger('click');
      await flushPromises();
      expect(viewportMountCount).toBe(1);
      expect(viewportUnmountCount).toBe(0);

      // Reset/reload de instâncias não foram chamados pelo toggle
      expect(mockViewportReset).not.toHaveBeenCalled();
      expect(fetchSeriesInstancesMock).toHaveBeenCalledTimes(1);

      // Desmonte da página desfaz viewport
      wrapper.unmount();
      expect(viewportUnmountCount).toBe(1);
    });

    it('redimensionar o painel split não recria viewport nem invoca reset ou recarga', async () => {
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(viewportMountCount).toBe(1);

      const reportPanel = wrapper.findComponent(ReportPanel);
      expect(reportPanel.exists()).toBe(true);

      // Chama setPanelWidth no layout
      reportPanel.vm.layout.setPanelWidth(600, 1920);
      await flushPromises();

      expect(viewportMountCount).toBe(1);
      expect(viewportUnmountCount).toBe(0);
      expect(mockViewportReset).not.toHaveBeenCalled();
      expect(fetchSeriesInstancesMock).toHaveBeenCalledTimes(1);
    });

    it('dispara recarregamento de estudo e laudo ao alterar studyUid na rota', async () => {
      fetchStudyViewerMock.mockResolvedValue(createStudySummary());
      fetchSeriesInstancesMock.mockResolvedValue(createSeriesInstances());
      fetchStudyReportMock.mockResolvedValue(sampleReportSnapshot);

      const wrapper = mount(ViewerPage);
      await flushPromises();

      expect(fetchStudyViewerMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
      expect(fetchStudyReportMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.123', expect.anything());
      expect(wrapper.find('[data-testid="report-panel"]').exists()).toBe(true);

      // Muda o UID da rota reativa
      currentRouteParams.studyUid = '1.2.840.113619.2.55.3.999';
      fetchStudyViewerMock.mockResolvedValue(
        createStudySummary({ studyInstanceUid: '1.2.840.113619.2.55.3.999' }),
      );
      fetchSeriesInstancesMock.mockResolvedValue(
        createSeriesInstances('1.2.840.113619.2.55.3.999.1'),
      );
      await flushPromises();

      expect(fetchStudyViewerMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.999', expect.anything());
      expect(fetchStudyReportMock).toHaveBeenCalledWith('1.2.840.113619.2.55.3.999', expect.anything());
      expect(wrapper.find('[data-testid="report-panel"]').exists()).toBe(true);
    });
  });
});
