import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import WorklistPage from '@/features/worklist/WorklistPage.vue';
import { searchStudies } from '@/features/worklist/worklist.api';
import {
  saveWorklistSnapshot,
  restoreWorklistSnapshot,
  clearWorklistSnapshot,
} from '@/features/worklist/worklist-navigation';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_MESSAGES } from '@/shared/api/problems/problem-messages.pt-BR';
import type { StudyPage, StudySummary } from '@/features/worklist/worklist.types';

const pushMock = vi.fn();
const replaceMock = vi.fn();
let currentRouteQuery: Record<string, string | string[]> = {};

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
    replace: replaceMock,
  }),
  useRoute: () => ({
    query: currentRouteQuery,
  }),
}));

vi.mock('@/features/worklist/worklist.api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/worklist/worklist.api')>();
  return {
    ...actual,
    searchStudies: vi.fn(),
  };
});
const searchStudiesMock = vi.mocked(searchStudies);

function pageWithOneStudy(overrides?: Partial<StudySummary>): StudyPage {
  return {
    items: [
      {
        studyInstanceUid: '1.2.840.113619.2.55.3.604688435.123.1599720123.467',
        patientName: 'MARIA^SILVA',
        patientId: '123',
        patientIdIssuer: 'HOSPITAL-A',
        studyDate: '2026-08-22',
        studyTime: '10:35:12',
        modalities: ['CT'],
        description: 'CT CHEST',
        seriesCount: 3,
        instanceCount: 187,
        ...overrides,
      },
    ],
    page: {
      limit: 20,
      offset: 0,
      hasPrevious: false,
      hasNext: false,
    },
  };
}

function pageWithMissingOptionals(): StudyPage {
  return {
    items: [
      {
        studyInstanceUid: '1.2.840.113619.2.55.3.999.888',
        patientName: null,
        patientId: null,
        patientIdIssuer: null,
        studyDate: null,
        studyTime: null,
        modalities: [],
        description: null,
        seriesCount: null,
        instanceCount: null,
      },
    ],
    page: {
      limit: 20,
      offset: 0,
      hasPrevious: false,
      hasNext: false,
    },
  };
}

function emptyPage(): StudyPage {
  return {
    items: [],
    page: {
      limit: 20,
      offset: 0,
      hasPrevious: false,
      hasNext: false,
    },
  };
}

describe('WorklistPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    currentRouteQuery = {};
    clearWorklistSnapshot();
  });


  it('carrega recentes ao montar e busca somente no submit', async () => {
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const wrapper = mount(WorklistPage);
    await flushPromises();
    expect(searchStudiesMock).toHaveBeenCalledTimes(1);

    await wrapper.get('[name="patientName"]').setValue('MARIA');
    expect(searchStudiesMock).toHaveBeenCalledTimes(1);
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(searchStudiesMock).toHaveBeenCalledTimes(2);
    expect(searchStudiesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        filters: expect.objectContaining({ patientName: 'MARIA' }),
        limit: 20,
        offset: 0,
      }),
      expect.anything(),
    );
  });

  it('mostra campos ausentes como não informado e preserva o uid somente como chave', async () => {
    searchStudiesMock.mockResolvedValue(pageWithMissingOptionals());
    const wrapper = mount(WorklistPage);
    await flushPromises();
    expect(wrapper.text()).toContain('Não informado');
    expect(wrapper.find('[data-testid="open-study"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('1.2.840.113619.2.55.3.999.888');
  });

  it('renderiza os dados do estudo em tabela e cartões responsivos', async () => {
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const wrapper = mount(WorklistPage);
    await flushPromises();

    const table = wrapper.find('[data-testid="study-table"]');
    expect(table.exists()).toBe(true);
    const rows = wrapper.findAll('[data-testid="study-row"]');
    expect(rows.length).toBe(1);
    expect(rows[0].text()).toContain('MARIA^SILVA');
    expect(rows[0].text()).toContain('123 · HOSPITAL-A');
    expect(rows[0].text()).toContain('CT');
    expect(rows[0].text()).toContain('CT CHEST');
    expect(rows[0].text()).toContain('3 séries · 187 instâncias');

    const cards = wrapper.find('[data-testid="study-cards"]');
    expect(cards.exists()).toBe(true);
    const cardItems = wrapper.findAll('[data-testid="study-card"]');
    expect(cardItems.length).toBe(1);
    expect(cardItems[0].text()).toContain('MARIA^SILVA');
  });

  it('exibe indicador de carregamento durante a busca', async () => {
    let resolvePromise: (value: StudyPage) => void = () => {};
    searchStudiesMock.mockReturnValue(
      new Promise<StudyPage>((resolve) => {
        resolvePromise = resolve;
      }),
    );

    const wrapper = mount(WorklistPage);
    await flushPromises();
    expect(wrapper.find('[role="status"]').text()).toContain('Carregando');

    resolvePromise(pageWithOneStudy());
    await flushPromises();
    expect(wrapper.find('[data-testid="study-table"]').exists()).toBe(true);
  });

  it('exibe mensagem quando Archive está vazio sem filtros', async () => {
    searchStudiesMock.mockResolvedValue(emptyPage());
    const wrapper = mount(WorklistPage);
    await flushPromises();

    const status = wrapper.find('[role="status"]');
    expect(status.exists()).toBe(true);
    expect(status.text()).toContain('Nenhum estudo disponível.');
  });

  it('exibe mensagem quando busca com filtros não retorna resultados', async () => {
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const wrapper = mount(WorklistPage);
    await flushPromises();

    searchStudiesMock.mockResolvedValue(emptyPage());
    await wrapper.get('[name="patientName"]').setValue('INEXISTENTE');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const status = wrapper.find('[role="status"]');
    expect(status.exists()).toBe(true);
    expect(status.text()).toContain('Nenhum estudo encontrado para os filtros informados.');
  });

  it('limpa os filtros e recarrega estudos recentes ao clicar em Limpar filtros', async () => {
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const wrapper = mount(WorklistPage);
    await flushPromises();

    await wrapper.get('[name="patientName"]').setValue('MARIA');
    await wrapper.get('[name="modality"]').setValue('CT');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(searchStudiesMock).toHaveBeenCalledTimes(2);

    const clearButton = wrapper.findAll('button').find((b) => b.text().includes('Limpar'));
    expect(clearButton).toBeDefined();
    await clearButton!.trigger('click');
    await flushPromises();

    expect((wrapper.get('[name="patientName"]').element as HTMLInputElement).value).toBe('');
    expect((wrapper.get('[name="modality"]').element as HTMLSelectElement).value).toBe('');
    expect(searchStudiesMock).toHaveBeenCalledTimes(3);
    expect(searchStudiesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        filters: {
          patientName: '',
          patientId: '',
          modality: '',
          dateFrom: '',
          dateTo: '',
        },
        offset: 0,
      }),
      expect.anything(),
    );
  });

  it('exibe erro de Archive indisponível e permite Tentar novamente', async () => {
    searchStudiesMock.mockRejectedValueOnce(new ApiError('API_ARCHIVE_UNAVAILABLE', { traceId: '4bf92f3577b34da6a3ce929d0e0e4736' }));
    const wrapper = mount(WorklistPage);
    await flushPromises();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain(PROBLEM_MESSAGES.API_ARCHIVE_UNAVAILABLE);

    const retryButton = alert.find('button');
    expect(retryButton.text()).toContain('Tentar novamente');

    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    await retryButton.trigger('click');
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="study-table"]').exists()).toBe(true);
  });

  it('usa o mapa central de mensagens para cada code catalogado', async () => {
    const errorCases = [
      'API_SEARCH_INVALID',
      'API_SEARCH_TOO_BROAD',
      'API_ARCHIVE_RESPONSE_INVALID',
      'API_ARCHIVE_UNAVAILABLE',
      'CLIENT_NETWORK_UNAVAILABLE',
    ] as const;

    for (const code of errorCases) {
      searchStudiesMock.mockRejectedValueOnce(new ApiError(code));
      const wrapper = mount(WorklistPage);
      await flushPromises();

      const alert = wrapper.find('[role="alert"]');
      expect(alert.exists()).toBe(true);
      expect(alert.text()).toContain(PROBLEM_MESSAGES[code]);
    }
  });

  it('oferece retentativa somente quando a politica e MANUAL', async () => {
    searchStudiesMock.mockRejectedValueOnce(new ApiError('API_ARCHIVE_UNAVAILABLE'));
    let wrapper = mount(WorklistPage);
    await flushPromises();
    expect(wrapper.find('[role="alert"]').find('button').exists()).toBe(true);

    // API_SEARCH_INVALID tem retryPolicy NEVER: repetir a mesma busca nao ajuda.
    searchStudiesMock.mockRejectedValueOnce(new ApiError('API_SEARCH_INVALID'));
    wrapper = mount(WorklistPage);
    await flushPromises();
    expect(wrapper.find('[role="alert"]').find('button').exists()).toBe(false);
  });

  it('mostra o TraceID como referencia quando disponivel', async () => {
    const traceId = '4bf92f3577b34da6a3ce929d0e0e4736';
    searchStudiesMock.mockRejectedValueOnce(new ApiError('API_INTERNAL_ERROR', { traceId }));

    const wrapper = mount(WorklistPage);
    await flushPromises();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.text()).toContain('Referência');
    expect(alert.find('code').text()).toBe(traceId);
  });

  it('nunca renderiza o detail do backend', async () => {
    searchStudiesMock.mockRejectedValueOnce(new ApiError('API_ARCHIVE_UNAVAILABLE'));

    const wrapper = mount(WorklistPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain('The imaging archive');
  });

  it('lida com paginação anterior e próxima', async () => {
    searchStudiesMock.mockResolvedValue({
      items: pageWithOneStudy().items,
      page: {
        limit: 20,
        offset: 0,
        hasPrevious: false,
        hasNext: true,
      },
    });

    const wrapper = mount(WorklistPage);
    await flushPromises();

    const prevButton = wrapper.find('[data-testid="pagination-prev"]');
    const nextButton = wrapper.find('[data-testid="pagination-next"]');
    expect(prevButton.attributes('disabled')).toBeDefined();
    expect(nextButton.attributes('disabled')).toBeUndefined();

    searchStudiesMock.mockResolvedValue({
      items: pageWithOneStudy().items,
      page: {
        limit: 20,
        offset: 20,
        hasPrevious: true,
        hasNext: false,
      },
    });

    await nextButton.trigger('click');
    await flushPromises();

    expect(searchStudiesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ offset: 20 }),
      expect.anything(),
    );

    const prevButtonAfter = wrapper.find('[data-testid="pagination-prev"]');
    const nextButtonAfter = wrapper.find('[data-testid="pagination-next"]');
    expect(prevButtonAfter.attributes('disabled')).toBeUndefined();
    expect(nextButtonAfter.attributes('disabled')).toBeDefined();

    await prevButtonAfter.trigger('click');
    await flushPromises();
    expect(searchStudiesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ offset: 0 }),
      expect.anything(),
    );
  });

  it('exibe indicação de ordenação "Mais recentes primeiro"', async () => {
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const wrapper = mount(WorklistPage);
    await flushPromises();

    expect(wrapper.text()).toContain('Mais recentes primeiro');
  });

  it('navega para o viewer ao clicar em Abrir estudo', async () => {
    const studyUid = '1.2.840.113619.2.55.3.604688435.123.1599720123.467';
    searchStudiesMock.mockResolvedValue(pageWithOneStudy({ studyInstanceUid: studyUid }));
    const wrapper = mount(WorklistPage);
    await flushPromises();

    const openBtn = wrapper.find('[data-testid="open-study"]');
    await openBtn.trigger('click');

    expect(pushMock).toHaveBeenCalledWith({
      name: 'viewer',
      params: { studyUid },
    });
  });

  it('sincroniza a query da URL com router.replace e salva snapshot ao buscar e paginar', async () => {
    searchStudiesMock.mockResolvedValue({
      items: pageWithOneStudy().items,
      page: { limit: 20, offset: 0, hasPrevious: false, hasNext: true },
    });

    const wrapper = mount(WorklistPage);
    await flushPromises();
    expect(replaceMock).toHaveBeenCalledWith({ query: {} });

    // Busca com filtros
    await wrapper.get('[name="patientName"]').setValue('MARIA');
    await wrapper.get('[name="modality"]').setValue('CT');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(replaceMock).toHaveBeenLastCalledWith({
      query: { patientName: 'MARIA', modality: 'CT' },
    });

    // Próxima página
    searchStudiesMock.mockResolvedValue({
      items: pageWithOneStudy().items,
      page: { limit: 20, offset: 20, hasPrevious: true, hasNext: false },
    });

    const nextBtn = wrapper.find('[data-testid="pagination-next"]');
    await nextBtn.trigger('click');
    await flushPromises();

    expect(replaceMock).toHaveBeenLastCalledWith({
      query: { patientName: 'MARIA', modality: 'CT', offset: '20' },
    });

    // Limpar filtros
    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    const clearBtn = wrapper.findAll('button').find((b) => b.text().includes('Limpar'));
    await clearBtn!.trigger('click');
    await flushPromises();

    expect(replaceMock).toHaveBeenLastCalledWith({
      query: {},
    });
  });

  it('hidrata o estado a partir do snapshot em cache quando a URL coincidir (sem chamar a API)', async () => {
    const cachedPage = pageWithOneStudy({ patientName: 'CACHED_PATIENT' });
    saveWorklistSnapshot({
      key: 'patientName=SILVA',
      filters: {
        patientName: 'SILVA',
        patientId: '',
        modality: '',
        dateFrom: '',
        dateTo: '',
      },
      page: cachedPage,
    });

    currentRouteQuery = { patientName: 'SILVA' };

    const wrapper = mount(WorklistPage);
    await flushPromises();

    expect(searchStudiesMock).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="study-table"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('CACHED_PATIENT');
    expect((wrapper.get('[name="patientName"]').element as HTMLInputElement).value).toBe('SILVA');
  });

  it('executa busca via API em deep link quando não houver snapshot correspondente', async () => {
    // Salva snapshot para chave diferente
    saveWorklistSnapshot({
      key: 'modality=MR',
      filters: {
        patientName: '',
        patientId: '',
        modality: 'MR',
        dateFrom: '',
        dateTo: '',
      },
      page: pageWithOneStudy({ patientName: 'OTHER' }),
    });

    currentRouteQuery = { patientName: 'DEEP_LINK_PATIENT', offset: '20' };
    searchStudiesMock.mockResolvedValue(pageWithOneStudy({ patientName: 'DEEP_LINK_PATIENT' }));

    const wrapper = mount(WorklistPage);
    await flushPromises();

    expect(searchStudiesMock).toHaveBeenCalledWith(
      {
        filters: {
          patientName: 'DEEP_LINK_PATIENT',
          patientId: '',
          modality: '',
          dateFrom: '',
          dateTo: '',
        },
        limit: 20,
        offset: 20,
      },
      expect.anything(),
    );
    expect(wrapper.text()).toContain('DEEP_LINK_PATIENT');
    expect((wrapper.get('[name="patientName"]').element as HTMLInputElement).value).toBe('DEEP_LINK_PATIENT');

    // Novo snapshot foi salvo
    const restored = restoreWorklistSnapshot('offset=20&patientName=DEEP_LINK_PATIENT');
    expect(restored).not.toBeNull();
    expect(restored?.page.items[0].patientName).toBe('DEEP_LINK_PATIENT');
  });
});
