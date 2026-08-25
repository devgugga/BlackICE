import { ref, readonly, computed, type Ref, type ComputedRef } from 'vue';
import { ApiError } from '@/shared/api/problems/api-error';
import { isIntentionalAbort } from '@/shared/api/problems/parse-problem';

import { fetchStudyViewer, fetchSeriesInstances } from './viewer.api';
import type {
  StudyViewerController,
  StudyViewerSummary,
  ViewerPhase,
  ViewerSeriesInstances,
  ViewerSeriesSummary,
} from './viewer.types';

export type FetchStudyViewer = (studyUid: string, signal?: AbortSignal) => Promise<StudyViewerSummary>;
export type FetchSeriesInstances = (
  studyUid: string,
  seriesUid: string,
  signal?: AbortSignal,
) => Promise<ViewerSeriesInstances>;

export interface StudyViewerComposable extends StudyViewerController {
  phase: Readonly<Ref<ViewerPhase>>;
  study: Readonly<Ref<StudyViewerSummary | null>>;
  selectedSeriesUid: Readonly<Ref<string | null>>;
  activeSeriesInstances: Readonly<Ref<ViewerSeriesInstances | null>>;
  error: Readonly<Ref<ApiError | null>>;
  selectedSeries: ComputedRef<ViewerSeriesSummary | null>;
}

export function useStudyViewer(
  fetchStudy: FetchStudyViewer = fetchStudyViewer,
  fetchInstances: FetchSeriesInstances = fetchSeriesInstances,
): StudyViewerComposable {
  let studyController: AbortController | null = null;
  let studyGeneration = 0;
  let seriesController: AbortController | null = null;
  let seriesGeneration = 0;

  const phase = ref<ViewerPhase>('IDLE');
  const study = ref<StudyViewerSummary | null>(null);
  const selectedSeriesUid = ref<string | null>(null);
  const activeSeriesInstances = ref<ViewerSeriesInstances | null>(null);
  const error = ref<ApiError | null>(null);

  const selectedSeries = computed<ViewerSeriesSummary | null>(() => {
    if (!study.value || !selectedSeriesUid.value) return null;
    return study.value.series.find((s) => s.seriesInstanceUid === selectedSeriesUid.value) ?? null;
  });

  async function loadStudy(studyUid: string): Promise<void> {
    studyController?.abort();
    studyController = null;
    seriesController?.abort();
    seriesController = null;

    const currentStudyGen = ++studyGeneration;
    ++seriesGeneration;

    phase.value = 'LOADING_STUDY';
    error.value = null;
    study.value = null;
    selectedSeriesUid.value = null;
    activeSeriesInstances.value = null;

    const controller = new AbortController();
    studyController = controller;

    try {
      const result = await fetchStudy(studyUid, controller.signal);
      if (currentStudyGen !== studyGeneration) return;

      study.value = result;
      const firstSupported = result.series.find((s) => s.availability === 'SUPPORTED');
      selectedSeriesUid.value = firstSupported?.seriesInstanceUid ?? null;
      phase.value = 'READY';
    } catch (caught) {
      if (currentStudyGen !== studyGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (currentStudyGen === studyGeneration) {
        studyController = null;
      }
    }
  }

  async function activateSeries(seriesUid?: string): Promise<void> {
    const targetUid = seriesUid ?? selectedSeriesUid.value;
    if (!targetUid || !study.value) return;

    const targetSeries = study.value.series.find((s) => s.seriesInstanceUid === targetUid);
    if (!targetSeries || targetSeries.availability !== 'SUPPORTED') return;

    selectedSeriesUid.value = targetUid;

    seriesController?.abort();
    const currentSeriesGen = ++seriesGeneration;
    const controller = new AbortController();
    seriesController = controller;

    phase.value = 'LOADING_SERIES';
    error.value = null;

    try {
      const result = await fetchInstances(study.value.studyInstanceUid, targetUid, controller.signal);
      if (currentSeriesGen !== seriesGeneration) return;

      activeSeriesInstances.value = result;
      phase.value = 'READY';
    } catch (caught) {
      if (currentSeriesGen !== seriesGeneration || isIntentionalAbort(caught)) return;
      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (currentSeriesGen === seriesGeneration) {
        seriesController = null;
      }
    }
  }

  function deactivateSeries(): void {
    seriesController?.abort();
    seriesController = null;
    ++seriesGeneration;

    activeSeriesInstances.value = null;
    if (phase.value === 'LOADING_SERIES') {
      phase.value = 'READY';
    }
  }

  async function selectSeries(seriesUid: string, activate: boolean): Promise<void> {
    if (!study.value) return;

    const targetSeries = study.value.series.find((s) => s.seriesInstanceUid === seriesUid);
    if (!targetSeries || targetSeries.availability !== 'SUPPORTED') return;

    if (activate) {
      return activateSeries(seriesUid);
    }

    selectedSeriesUid.value = seriesUid;
    deactivateSeries();
  }

  function dispose(): void {
    ++studyGeneration;
    ++seriesGeneration;
    studyController?.abort();
    studyController = null;
    seriesController?.abort();
    seriesController = null;
    phase.value = 'IDLE';
    study.value = null;
    selectedSeriesUid.value = null;
    activeSeriesInstances.value = null;
    error.value = null;
  }

  return {
    phase: readonly(phase) as Readonly<Ref<ViewerPhase>>,
    study: readonly(study) as Readonly<Ref<StudyViewerSummary | null>>,
    selectedSeriesUid: readonly(selectedSeriesUid) as Readonly<Ref<string | null>>,
    activeSeriesInstances: readonly(activeSeriesInstances) as Readonly<Ref<ViewerSeriesInstances | null>>,
    error: readonly(error) as Readonly<Ref<ApiError | null>>,
    selectedSeries,
    loadStudy,
    activateSeries,
    deactivateSeries,
    selectSeries,
    dispose,
  };
}
