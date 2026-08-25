<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ApiError } from '@/shared/api/problems/api-error';
import type { ProblemCode } from '@/shared/api/problems/problem-types.generated';
import { problemMessage } from '@/shared/api/problems/problem-messages.pt-BR';
import { loadDicomViewport } from './loadDicomViewport';
import { useStudyViewer } from './useStudyViewer';
import { useViewerCapability } from './useViewerCapability';
import StudyHeader from './StudyHeader.vue';
import SeriesRail from './SeriesRail.vue';
import ViewerToolbar from './ViewerToolbar.vue';
import type { ViewerTool } from './viewer.types';

const DicomViewport = defineAsyncComponent(loadDicomViewport);

const router = useRouter();
const route = useRoute();

const { canRenderViewer } = useViewerCapability();
const {
  phase,
  study,
  selectedSeriesUid,
  activeSeriesInstances,
  error,
  loadStudy,
  activateSeries,
  deactivateSeries,
  selectSeries,
  dispose: disposeViewer,
} = useStudyViewer();

const activeTool = ref<ViewerTool>('WINDOW_LEVEL');
const viewportError = ref<ApiError | null>(null);
const viewportComponentRef = ref<{ reset?: () => void } | null>(null);

const summaryErrorMessage = computed(() =>
  error.value ? problemMessage(error.value.code) : '',
);
const summaryErrorTraceId = computed(() => error.value?.traceId ?? null);
const allowsSummaryRetry = computed(() => error.value?.retryPolicy === 'MANUAL');

const currentViewportError = computed<ApiError | null>(() => {
  if (viewportError.value) return viewportError.value;
  if (phase.value === 'ERROR' && study.value !== null) return error.value;
  return null;
});

const viewportErrorMessage = computed(() =>
  currentViewportError.value ? problemMessage(currentViewportError.value.code) : '',
);
const viewportErrorTraceId = computed(() => currentViewportError.value?.traceId ?? null);
const allowsViewportRetry = computed(
  () => currentViewportError.value?.retryPolicy === 'MANUAL',
);

const noSupportedSeries = computed(() => {
  if (!study.value) return false;
  return (
    study.value.series.length === 0 ||
    study.value.series.every((s) => s.availability === 'UNSUPPORTED')
  );
});

async function initStudy(uid: string): Promise<void> {
  viewportError.value = null;
  await loadStudy(uid);
  if (canRenderViewer.value && selectedSeriesUid.value) {
    await activateSeries(selectedSeriesUid.value);
  }
}

onMounted(async () => {
  const studyUid = route?.params?.studyUid as string;
  if (studyUid) {
    await initStudy(studyUid);
  }
});

watch(
  () => route?.params?.studyUid,
  async (newUid, oldUid) => {
    if (newUid && newUid !== oldUid) {
      await initStudy(newUid as string);
    }
  },
);

watch(
  () => canRenderViewer.value,
  async (canRender) => {
    if (canRender) {
      if (study.value && selectedSeriesUid.value && !activeSeriesInstances.value) {
        await activateSeries(selectedSeriesUid.value);
      }
    } else {
      deactivateSeries();
    }
  },
);

onUnmounted(() => {
  disposeViewer();
});

async function handleBack(): Promise<void> {
  const backPath = window.history.state?.back;
  if (
    backPath === '/studies' ||
    (typeof backPath === 'string' && backPath.startsWith('/studies?'))
  ) {
    router?.back();
  } else {
    await router?.push({ name: 'worklist' });
  }
}

async function handleSelectSeries(seriesUid: string): Promise<void> {
  viewportError.value = null;
  if (canRenderViewer.value) {
    await selectSeries(seriesUid, true);
  } else {
    await selectSeries(seriesUid, false);
  }
}

function handleSelectTool(tool: ViewerTool): void {
  activeTool.value = tool;
}

function handleReset(): void {
  activeTool.value = 'WINDOW_LEVEL';
  viewportComponentRef.value?.reset?.();
}

function handleViewportFailure(code: ProblemCode): void {
  viewportError.value = new ApiError(code);
}

async function handleRetrySummary(): Promise<void> {
  const studyUid = route?.params?.studyUid as string;
  if (studyUid) {
    await initStudy(studyUid);
  }
}

async function handleRetrySeries(): Promise<void> {
  viewportError.value = null;
  if (selectedSeriesUid.value) {
    await activateSeries(selectedSeriesUid.value);
  }
}
</script>

<template>
  <main class="viewer-page" aria-label="Visualizador de estudo">
    <StudyHeader :study="study" @back="handleBack" />

    <!-- Capability gate para telas estreitas / mobile -->
    <div
      v-if="!canRenderViewer"
      class="capability-gate-message"
      role="status"
    >
      <p>Use uma tela maior para visualizar as imagens deste estudo.</p>
    </div>

    <!-- Erro em nível de página (falha no resumo do estudo) -->
    <div
      v-else-if="phase === 'ERROR' && study === null"
      role="alert"
      class="page-error"
    >
      <p class="error-title">{{ summaryErrorMessage }}</p>
      <p v-if="summaryErrorTraceId" class="error-reference">
        Referência: <code>{{ summaryErrorTraceId }}</code>
      </p>
      <button v-if="allowsSummaryRetry" type="button" class="retry-btn" @click="handleRetrySummary">
        Tentar novamente
      </button>
    </div>

    <!-- Carregamento inicial do estudo -->
    <div
      v-else-if="phase === 'LOADING_STUDY'"
      role="status"
      aria-live="polite"
      class="page-loading"
    >
      <p>Carregando estudo…</p>
    </div>

    <!-- Área de trabalho completa quando o estudo estiver carregado -->
    <div v-else-if="study !== null" class="viewer-workspace">
      <SeriesRail
        :series="study.series"
        :selected-series-uid="selectedSeriesUid"
        @select-series="handleSelectSeries"
      />

      <section class="viewport-area" aria-label="Área de visualização da série">
        <ViewerToolbar
          :active-tool="activeTool"
          @select-tool="handleSelectTool"
          @reset="handleReset"
        />

        <div class="viewport-container">
          <!-- Mensagem quando não houver séries suportadas -->
          <div
            v-if="noSupportedSeries"
            role="status"
            class="unsupported-series-message"
          >
            <p>Nenhuma série suportada para visualização neste estudo.</p>
          </div>

          <!-- Erro confinado ao viewport -->
          <div
            v-else-if="currentViewportError !== null"
            role="alert"
            class="viewport-error"
          >
            <p class="error-title">{{ viewportErrorMessage }}</p>
            <p v-if="viewportErrorTraceId" class="error-reference">
              Referência: <code>{{ viewportErrorTraceId }}</code>
            </p>
            <button v-if="allowsViewportRetry" type="button" class="retry-btn" @click="handleRetrySeries">
              Tentar novamente
            </button>
          </div>

          <!-- Carregando imagens da série -->
          <div
            v-else-if="phase === 'LOADING_SERIES'"
            role="status"
            aria-live="polite"
            class="series-loading"
          >
            <p>Carregando imagens da série…</p>
          </div>

          <!-- Viewport ativo -->
          <DicomViewport
            v-else-if="activeSeriesInstances"
            ref="viewportComponentRef"
            :instances="activeSeriesInstances"
            :active-tool="activeTool"
            @failure="handleViewportFailure"
          />
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.viewer-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  width: 100%;
  background-color: #0d0f12;
  color: #e2e8f0;
  overflow: hidden;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}

.capability-gate-message {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
  font-size: 1.125rem;
  color: #a0aec0;
  background-color: #121417;
}

.page-loading,
.series-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
  font-size: 1rem;
  color: #a0aec0;
}

.page-error {
  margin: 2rem auto;
  max-width: 600px;
  width: calc(100% - 4rem);
  padding: 1.5rem;
  background-color: #2d1a1d;
  border: 1px solid #742a2a;
  border-radius: 6px;
  color: #feb2b2;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: flex-start;
}

.viewport-error {
  padding: 1.5rem;
  max-width: 500px;
  background-color: #2d1a1d;
  border: 1px solid #742a2a;
  border-radius: 6px;
  color: #feb2b2;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: flex-start;
  text-align: left;
}

.unsupported-series-message {
  padding: 2rem;
  text-align: center;
  color: #a0aec0;
  font-size: 1rem;
}

.error-title {
  margin: 0;
  font-weight: 500;
  font-size: 0.9375rem;
}

.error-reference {
  margin: 0;
  font-size: 0.8125rem;
  color: #cbd5e0;
}

.error-reference code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background-color: rgba(0, 0, 0, 0.3);
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  user-select: all;
}

.retry-btn {
  padding: 0.4375rem 0.875rem;
  background-color: #e53e3e;
  color: #ffffff;
  border: 1px solid #c53030;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
  transition: background-color 0.15s ease-in-out;
}

.retry-btn:hover {
  background-color: #c53030;
}

.retry-btn:focus-visible {
  outline: 2px solid #feb2b2;
  outline-offset: 2px;
}

.viewer-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.viewport-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

.viewport-container {
  position: relative;
  flex: 1;
  min-height: 0;
  background-color: #000000;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
