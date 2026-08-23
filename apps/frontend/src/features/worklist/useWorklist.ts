import { ref, readonly, type Ref } from 'vue';
import { ApiError } from '@/shared/api/problems/api-error';
import { isIntentionalAbort } from '@/shared/api/problems/parse-problem';

import { searchStudies } from './worklist.api';
import type { StudyPage, StudySearchParams, StudySummary, WorklistFilters } from './worklist.types';

export type WorklistPhase = 'IDLE' | 'LOADING' | 'READY' | 'EMPTY' | 'ERROR';
export type SearchStudies = (params: StudySearchParams, signal?: AbortSignal) => Promise<StudyPage>;

export const PAGE_SIZE = 20;

export const EMPTY_FILTERS: WorklistFilters = {
  patientName: '',
  patientId: '',
  modality: '',
  dateFrom: '',
  dateTo: '',
};

export interface WorklistComposable {
  phase: Readonly<Ref<WorklistPhase>>;
  items: Readonly<Ref<readonly StudySummary[]>>;
  page: Readonly<Ref<StudyPage['page']>>;
  error: Readonly<Ref<ApiError | null>>;
  appliedFilters: Readonly<Ref<WorklistFilters>>;
  appliedOffset: Readonly<Ref<number>>;
  loadRecent(): Promise<void>;
  search(filters: WorklistFilters): Promise<void>;
  clear(): Promise<void>;
  next(): Promise<void>;
  previous(): Promise<void>;
  retry(): Promise<void>;
  dispose(): void;
}

export function useWorklist(api: SearchStudies = searchStudies): WorklistComposable {
  let activeController: AbortController | null = null;
  let activeGeneration = 0;

  const phase = ref<WorklistPhase>('IDLE');
  const items = ref<readonly StudySummary[]>([]);
  const page = ref<StudyPage['page']>({
    limit: PAGE_SIZE,
    offset: 0,
    hasPrevious: false,
    hasNext: false,
  });
  const error = ref<ApiError | null>(null);
  const appliedFilters = ref<WorklistFilters>({ ...EMPTY_FILTERS });
  const appliedOffset = ref<number>(0);

  async function load(filters: WorklistFilters, offset: number): Promise<void> {
    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    const generation = ++activeGeneration;
    phase.value = 'LOADING';
    error.value = null;
    appliedFilters.value = { ...filters };
    appliedOffset.value = offset;
    try {
      const result = await api({ filters: { ...filters }, limit: PAGE_SIZE, offset }, controller.signal);
      if (generation !== activeGeneration) return;
      items.value = result.items;
      page.value = result.page;
      phase.value = result.items.length === 0 ? 'EMPTY' : 'READY';
    } catch (caught) {
      // Cancelamento não é falha: a UI não muda de estado.
      if (generation !== activeGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (generation === activeGeneration) activeController = null;
    }
  }

  async function loadRecent(): Promise<void> {
    return load(EMPTY_FILTERS, 0);
  }

  async function search(filters: WorklistFilters): Promise<void> {
    return load(filters, 0);
  }

  async function clear(): Promise<void> {
    return load(EMPTY_FILTERS, 0);
  }

  async function next(): Promise<void> {
    if (!page.value.hasNext || phase.value === 'LOADING') return;
    return load(appliedFilters.value, page.value.offset + page.value.limit);
  }

  async function previous(): Promise<void> {
    if (!page.value.hasPrevious || phase.value === 'LOADING') return;
    return load(appliedFilters.value, Math.max(0, page.value.offset - page.value.limit));
  }

  async function retry(): Promise<void> {
    return load(appliedFilters.value, appliedOffset.value);
  }

  function dispose(): void {
    activeGeneration++;
    activeController?.abort();
    activeController = null;
  }

  return {
    phase: readonly(phase) as Readonly<Ref<WorklistPhase>>,
    items: readonly(items) as Readonly<Ref<readonly StudySummary[]>>,
    page: readonly(page) as Readonly<Ref<StudyPage['page']>>,
    error: readonly(error) as unknown as Readonly<Ref<ApiError | null>>,
    appliedFilters: readonly(appliedFilters) as Readonly<Ref<WorklistFilters>>,
    appliedOffset: readonly(appliedOffset) as Readonly<Ref<number>>,
    loadRecent,
    search,
    clear,
    next,
    previous,
    retry,
    dispose,
  };
}
