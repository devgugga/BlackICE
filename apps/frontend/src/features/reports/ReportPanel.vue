<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { problemMessage } from '@/shared/api/problems/problem-messages.pt-BR';
import { useStudyReport, type StudyReportApi } from './useStudyReport';
import { useReportLayout, type ReportLayoutController } from './useReportLayout';
import ReportEditor from './ReportEditor.vue';

interface Props {
  studyUid: string;
  customApi?: Partial<StudyReportApi>;
  customLayout?: ReportLayoutController;
}

const props = defineProps<Props>();

const reportController = useStudyReport(props.customApi);
const layout = props.customLayout ?? useReportLayout();

const {
  phase,
  report,
  content,
  dirty,
  error,
  load,
  saveDraft,
  finalize,
  reloadServerVersion,
  dispose: disposeReport,
} = reportController;

const liveAnnouncement = ref('');
const isReloadModalOpen = ref(false);

const isDrawer = computed(() => layout.mode.value === 'DRAWER');
const isReportOnly = computed(() => layout.mode.value === 'REPORT_ONLY');
const isSplit = computed(() => layout.mode.value === 'SPLIT');

const errorMessage = computed(() => (error.value ? problemMessage(error.value.code) : ''));
const errorTraceId = computed(() => error.value?.traceId ?? null);
const allowsRetry = computed(() => error.value?.allowsManualRetry ?? false);
const isVersionConflict = computed(
  () => error.value?.code === 'API_RESOURCE_VERSION_CONFLICT',
);

async function initReport(uid: string) {
  liveAnnouncement.value = 'Carregando laudo…';
  await load(uid);
  if (phase.value === 'ERROR' && error.value) {
    liveAnnouncement.value = problemMessage(error.value.code);
  } else if (phase.value === 'READY' || phase.value === 'ABSENT') {
    liveAnnouncement.value = 'Laudo carregado com sucesso.';
  }
}

// Start loading immediately in setup if studyUid is present
if (props.studyUid) {
  initReport(props.studyUid);
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload);
});

watch(
  () => props.studyUid,
  async (newUid, oldUid) => {
    if (newUid && newUid !== oldUid) {
      await initReport(newUid);
    }
  },
);

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload);
  disposeReport();
  layout.dispose();
});

function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = '';
  }
}

onBeforeRouteLeave(() => {
  if (dirty.value) {
    const answer = window.confirm(
      'Existem alterações não salvas no laudo. Deseja sair e descartar as alterações?',
    );
    if (!answer) {
      return false;
    }
  }
  return true;
});

onBeforeRouteUpdate(() => {
  if (dirty.value) {
    const answer = window.confirm(
      'Existem alterações não salvas no laudo. Deseja sair e descartar as alterações?',
    );
    if (!answer) {
      return false;
    }
  }
  return true;
});

function handlePanelKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isDrawer.value && !isReloadModalOpen.value) {
    layout.close();
  }
}

async function handleSaveDraft() {
  await saveDraft();
  if (phase.value === 'READY') {
    liveAnnouncement.value = 'Rascunho salvo com sucesso.';
  } else if (phase.value === 'ERROR' && error.value) {
    liveAnnouncement.value = problemMessage(error.value.code);
  }
}

async function handleFinalize() {
  await finalize();
  if (phase.value === 'READY') {
    liveAnnouncement.value = 'Laudo finalizado com sucesso.';
  } else if (phase.value === 'ERROR' && error.value) {
    liveAnnouncement.value = problemMessage(error.value.code);
  }
}

async function handleRetry() {
  if (report.value === null && content.value === '') {
    await initReport(props.studyUid);
  } else {
    await handleSaveDraft();
  }
}

function openReloadModal() {
  isReloadModalOpen.value = true;
}

function closeReloadModal() {
  isReloadModalOpen.value = false;
}

async function handleConfirmReload() {
  closeReloadModal();
  await reloadServerVersion();
  if (phase.value === 'READY') {
    liveAnnouncement.value = 'Versão do servidor recarregada com sucesso.';
  }
}

defineExpose({
  layout,
  reportController,
});
</script>

<template>
  <aside
    v-show="layout.isOpen.value"
    data-testid="report-panel"
    class="report-panel"
    :class="{
      'layout-split': isSplit,
      'layout-drawer': isDrawer,
      'layout-report-only': isReportOnly,
    }"
    :style="isSplit ? { width: `${layout.panelWidth.value}px` } : undefined"
    aria-label="Painel de laudo clínico"
    @keydown="handlePanelKeydown"
  >
    <!-- Screen reader live region -->
    <div
      role="status"
      aria-live="polite"
      class="sr-only"
      data-testid="live-announcer"
    >
      {{ liveAnnouncement }}
    </div>

    <!-- Panel Header -->
    <header class="panel-header">
      <div class="panel-title-group">
        <h2 class="panel-title">Laudo Clínico</h2>
      </div>

      <div class="panel-header-actions">
        <button
          type="button"
          data-testid="panel-close-button"
          class="btn-close"
          aria-label="Fechar painel de laudo"
          @click="layout.close()"
        >
          <span aria-hidden="true">&times;</span>
          <span class="sr-only">Fechar</span>
        </button>
      </div>
    </header>

    <!-- Error Banner / Notification -->
    <div
      v-if="phase === 'ERROR' && error"
      role="alert"
      data-testid="report-error"
      class="report-error-banner"
    >
      <div class="error-content">
        <p class="error-message">{{ errorMessage }}</p>
        <p v-if="errorTraceId" class="error-reference">
          Referência: <code>{{ errorTraceId }}</code>
        </p>
      </div>

      <div class="error-actions">
        <button
          v-if="allowsRetry && !isVersionConflict"
          type="button"
          data-testid="report-retry-button"
          class="btn btn-sm btn-retry"
          @click="handleRetry"
        >
          Tentar novamente
        </button>

        <button
          v-if="isVersionConflict"
          type="button"
          data-testid="reload-server-button"
          class="btn btn-sm btn-warning"
          @click="openReloadModal"
        >
          Recarregar versão do servidor
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <div
      v-if="phase === 'LOADING'"
      role="status"
      aria-live="polite"
      data-testid="report-loading"
      class="panel-loading"
    >
      <div class="spinner" aria-hidden="true"></div>
      <p>Carregando laudo…</p>
    </div>

    <!-- Main Editor -->
    <div v-else class="panel-body">
      <ReportEditor
        v-model="content"
        :status="report?.status ?? null"
        :author-display-name="report?.authorDisplayName ?? null"
        :updated-at="report?.updatedAt ?? null"
        :finalized-at="report?.finalizedAt ?? null"
        :editable="report ? report.editable : true"
        :saving="phase === 'SAVING'"
        @save-draft="handleSaveDraft"
        @finalize="handleFinalize"
      />
    </div>

    <!-- Confirmation Modal for Reloading Server Version -->
    <div
      v-if="isReloadModalOpen"
      data-testid="reload-confirm-modal"
      class="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reload-modal-title"
      aria-describedby="reload-modal-desc"
      @keydown.esc="closeReloadModal"
    >
      <div class="modal-card">
        <h3 id="reload-modal-title" class="modal-title">
          Recarregar versão do servidor?
        </h3>
        <p id="reload-modal-desc" class="modal-description">
          O laudo foi atualizado em outra sessão. Recarregar substituirá o texto local não salvo pela versão do servidor.
        </p>
        <div class="modal-actions">
          <button
            type="button"
            data-testid="reload-cancel-button"
            class="btn btn-secondary"
            @click="closeReloadModal"
          >
            Cancelar
          </button>
          <button
            type="button"
            data-testid="reload-confirm-submit-button"
            class="btn btn-warning"
            @click="handleConfirmReload"
          >
            Recarregar versão
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.report-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #121417;
  color: #e2e8f0;
  border-left: 1px solid #2d3748;
  box-sizing: border-box;
  overflow: hidden;
  z-index: 100;
}

.layout-split {
  position: relative;
  flex-shrink: 0;
}

.layout-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 90vw;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.7);
}

.layout-report-only {
  position: relative;
  width: 100%;
  border-left: none;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.625rem 1rem;
  background-color: #1a202c;
  border-bottom: 1px solid #2d3748;
}

.panel-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: #f7fafc;
}

.btn-close {
  background: transparent;
  border: none;
  color: #a0aec0;
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.btn-close:hover {
  color: #ffffff;
  background-color: #2d3748;
}

.btn-close:focus-visible {
  outline: 2px solid #63b3ed;
}

.report-error-banner {
  background-color: #2d1a1d;
  border-bottom: 1px solid #742a2a;
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.error-message {
  margin: 0;
  font-size: 0.875rem;
  color: #feb2b2;
  font-weight: 500;
}

.error-reference {
  margin: 0.25rem 0 0;
  font-size: 0.75rem;
  color: #cbd5e0;
}

.error-reference code {
  font-family: monospace;
  background-color: rgba(0, 0, 0, 0.4);
  padding: 0.125rem 0.25rem;
  border-radius: 3px;
}

.error-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.panel-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: #a0aec0;
  font-size: 0.9375rem;
}

.spinner {
  width: 2rem;
  height: 2rem;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #63b3ed;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-sm {
  padding: 0.375rem 0.75rem;
  font-size: 0.8125rem;
}

.btn-secondary {
  background-color: #2d3748;
  color: #edf2f7;
  border-color: #4a5568;
}

.btn-secondary:hover {
  background-color: #4a5568;
}

.btn-retry {
  background-color: #e53e3e;
  color: #ffffff;
}

.btn-retry:hover {
  background-color: #c53030;
}

.btn-warning {
  background-color: #d69e2e;
  color: #1a202c;
}

.btn-warning:hover {
  background-color: #b7791f;
}

/* Modal styles */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 1rem;
}

.modal-card {
  background-color: #1a202c;
  border: 1px solid #4a5568;
  border-radius: 6px;
  max-width: 480px;
  width: 100%;
  padding: 1.5rem;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.modal-title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: #f7fafc;
}

.modal-description {
  margin: 0;
  font-size: 0.875rem;
  color: #cbd5e0;
  line-height: 1.5;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
</style>
