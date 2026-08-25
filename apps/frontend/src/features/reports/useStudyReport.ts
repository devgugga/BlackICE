import { ref, readonly, computed, type Ref } from 'vue';
import { ApiError } from '@/shared/api/problems/api-error';
import { isIntentionalAbort } from '@/shared/api/problems/parse-problem';
import {
  fetchStudyReport,
  createStudyReport,
  updateStudyReport,
  type ReportMutationOptions,
} from './report.api';
import type { ReportSnapshot, ReportStatus, StudyReport } from './report.types';

export type ReportPhase = 'LOADING' | 'ABSENT' | 'READY' | 'SAVING' | 'ERROR';

export interface StudyReportApi {
  fetchStudyReport: (uid: string, signal?: AbortSignal) => Promise<ReportSnapshot | null>;
  createStudyReport: (
    uid: string,
    content: string,
    status: ReportStatus,
    options?: ReportMutationOptions,
  ) => Promise<ReportSnapshot>;
  updateStudyReport: (
    uid: string,
    content: string,
    status: ReportStatus,
    etag: string,
    options?: ReportMutationOptions,
  ) => Promise<ReportSnapshot>;
}

export interface StudyReportController {
  phase: Readonly<Ref<ReportPhase>>;
  report: Readonly<Ref<StudyReport | null>>;
  content: Ref<string>;
  dirty: Readonly<Ref<boolean>>;
  codePointCount: Readonly<Ref<number>>;
  error: Readonly<Ref<ApiError | null>>;
  load(uid: string): Promise<void>;
  saveDraft(): Promise<void>;
  finalize(): Promise<void>;
  reloadServerVersion(): Promise<void>;
  dispose(): void;
}

const DEFAULT_API: StudyReportApi = {
  fetchStudyReport,
  createStudyReport,
  updateStudyReport,
};

/**
 * Controlador reativo do ciclo de vida e estado do laudo clínico em memória.
 *
 * <p>Preserva o rascunho em memória após falhas de validação, rede ou conflito
 * (409 / 412). Não acessa persistência de navegador (localStorage, sessionStorage,
 * IndexedDB) e trata concorrência com cancelamento de requisições anteriores.</p>
 */
export function useStudyReport(customApi: Partial<StudyReportApi> = {}): StudyReportController {
  const api: StudyReportApi = {
    ...DEFAULT_API,
    ...customApi,
  };

  let activeController: AbortController | null = null;
  let activeGeneration = 0;

  const currentStudyUid = ref<string | null>(null);
  const currentEtag = ref<string | null>(null);
  const lastAcceptedContent = ref<string>('');

  const phase = ref<ReportPhase>('ABSENT');
  const report = ref<StudyReport | null>(null);
  const content = ref<string>('');
  const error = ref<ApiError | null>(null);

  const dirty = computed(() => content.value !== lastAcceptedContent.value);
  const codePointCount = computed(() => Array.from(content.value).length);

  async function load(uid: string): Promise<void> {
    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    const generation = ++activeGeneration;

    currentStudyUid.value = uid;
    currentEtag.value = null;
    lastAcceptedContent.value = '';
    report.value = null;
    content.value = '';
    phase.value = 'LOADING';
    error.value = null;

    try {
      const result = await api.fetchStudyReport(uid, controller.signal);
      if (generation !== activeGeneration) return;

      if (result === null) {
        report.value = null;
        currentEtag.value = null;
        lastAcceptedContent.value = '';
        content.value = '';
        phase.value = 'ABSENT';
      } else {
        report.value = result.report;
        currentEtag.value = result.etag;
        lastAcceptedContent.value = result.report.content;
        content.value = result.report.content;
        phase.value = 'READY';
      }
    } catch (caught: unknown) {
      if (generation !== activeGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (generation === activeGeneration) {
        activeController = null;
      }
    }
  }

  async function mutate(status: ReportStatus): Promise<void> {
    if (!currentStudyUid.value) return;
    if (phase.value === 'SAVING' || phase.value === 'LOADING') return;
    if (report.value !== null && !report.value.editable) return;

    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    const generation = ++activeGeneration;

    phase.value = 'SAVING';
    error.value = null;

    const uid = currentStudyUid.value;
    const textToSave = content.value;
    const existingReport = report.value;
    const existingEtag = currentEtag.value;

    try {
      let snapshot: ReportSnapshot;
      if (existingReport === null) {
        snapshot = await api.createStudyReport(uid, textToSave, status, {
          signal: controller.signal,
        });
      } else {
        if (!existingEtag) {
          throw new ApiError('CLIENT_UNEXPECTED_ERROR');
        }
        snapshot = await api.updateStudyReport(uid, textToSave, status, existingEtag, {
          signal: controller.signal,
        });
      }

      if (generation !== activeGeneration) return;

      report.value = snapshot.report;
      currentEtag.value = snapshot.etag;
      lastAcceptedContent.value = snapshot.report.content;
      content.value = snapshot.report.content;
      phase.value = 'READY';
      error.value = null;
    } catch (caught: unknown) {
      if (generation !== activeGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (generation === activeGeneration) {
        activeController = null;
      }
    }
  }

  async function saveDraft(): Promise<void> {
    return mutate('DRAFT');
  }

  async function finalize(): Promise<void> {
    return mutate('FINAL');
  }

  async function reloadServerVersion(): Promise<void> {
    if (!currentStudyUid.value) return;

    activeController?.abort();
    const controller = new AbortController();
    activeController = controller;
    const generation = ++activeGeneration;

    phase.value = 'LOADING';
    error.value = null;

    try {
      const result = await api.fetchStudyReport(currentStudyUid.value, controller.signal);
      if (generation !== activeGeneration) return;

      if (result === null) {
        report.value = null;
        currentEtag.value = null;
        lastAcceptedContent.value = '';
        content.value = '';
        phase.value = 'ABSENT';
      } else {
        report.value = result.report;
        currentEtag.value = result.etag;
        lastAcceptedContent.value = result.report.content;
        content.value = result.report.content;
        phase.value = 'READY';
      }
    } catch (caught: unknown) {
      if (generation !== activeGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (generation === activeGeneration) {
        activeController = null;
      }
    }
  }

  function dispose(): void {
    activeGeneration++;
    activeController?.abort();
    activeController = null;
  }

  return {
    phase: readonly(phase) as Readonly<Ref<ReportPhase>>,
    report: readonly(report) as Readonly<Ref<StudyReport | null>>,
    content,
    dirty: readonly(dirty) as Readonly<Ref<boolean>>,
    codePointCount: readonly(codePointCount) as Readonly<Ref<number>>,
    error: readonly(error) as Readonly<Ref<ApiError | null>>,
    load,
    saveDraft,
    finalize,
    reloadServerVersion,
    dispose,
  };
}
