import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import WorklistPage from '@/features/worklist/WorklistPage.vue';
import { searchStudies, WorklistError } from '@/features/worklist/worklist.api';
import type { StudyPage, StudySummary } from '@/features/worklist/worklist.types';

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
    expect(wrapper.find('[data-testid="open-study"]').exists()).toBe(false);
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
    searchStudiesMock.mockRejectedValueOnce(new WorklistError(503, 'ARCHIVE_UNAVAILABLE'));
    const wrapper = mount(WorklistPage);
    await flushPromises();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain('Archive temporariamente indisponível');

    const retryButton = alert.find('button');
    expect(retryButton.text()).toContain('Tentar novamente');

    searchStudiesMock.mockResolvedValue(pageWithOneStudy());
    await retryButton.trigger('click');
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="study-table"]').exists()).toBe(true);
  });

  it('mapeia códigos de erro estáveis para mensagens em português', async () => {
    const errorCases = [
      { code: 'INVALID_SEARCH', text: 'Verifique os filtros informados' },
      { code: 'SEARCH_TOO_BROAD', text: 'filtros mais restritos' },
      { code: 'ARCHIVE_INVALID_RESPONSE', text: 'Resposta inválida do Archive' },
      { code: 'ARCHIVE_UNAVAILABLE', text: 'Archive temporariamente indisponível' },
      { code: 'NETWORK_ERROR', text: 'temporariamente indisponível' },
    ];

    for (const { code, text } of errorCases) {
      searchStudiesMock.mockRejectedValueOnce(new WorklistError(500, code));
      const wrapper = mount(WorklistPage);
      await flushPromises();
      const alert = wrapper.find('[role="alert"]');
      expect(alert.exists()).toBe(true);
      expect(alert.text().toLowerCase()).toContain(text.toLowerCase());
      expect(alert.find('button').text()).toContain('Tentar novamente');
    }
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
});
