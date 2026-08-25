<script setup lang="ts">
import { ref, computed, nextTick } from 'vue';
import type { ReportStatus } from './report.types';

interface Props {
  modelValue?: string;
  status?: ReportStatus | null;
  authorDisplayName?: string | null;
  updatedAt?: string | null;
  finalizedAt?: string | null;
  editable?: boolean;
  saving?: boolean;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  status: null,
  authorDisplayName: null,
  updatedAt: null,
  finalizedAt: null,
  editable: true,
  saving: false,
  disabled: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'save-draft'): void;
  (e: 'finalize'): void;
}>();

const isConfirmModalOpen = ref(false);
const triggerButtonRef = ref<HTMLButtonElement | null>(null);
const cancelButtonRef = ref<HTMLButtonElement | null>(null);
const confirmButtonRef = ref<HTMLButtonElement | null>(null);

const MAX_CODE_POINTS = 32000;

const codePointCount = computed(() => Array.from(props.modelValue || '').length);
const isBlank = computed(() => !props.modelValue || props.modelValue.trim().length === 0);
const isOversized = computed(() => codePointCount.value > MAX_CODE_POINTS);

const isActionDisabled = computed(
  () =>
    isBlank.value ||
    isOversized.value ||
    props.saving ||
    props.disabled ||
    !props.editable ||
    props.status === 'FINAL',
);

const isReadOnly = computed(() => !props.editable || props.status === 'FINAL');

const statusLabel = computed(() => {
  if (props.status === 'FINAL') return 'Finalizado';
  if (props.status === 'DRAFT') return 'Rascunho';
  return 'Novo';
});

const statusBadgeClass = computed(() => {
  if (props.status === 'FINAL') return 'status-final';
  if (props.status === 'DRAFT') return 'status-draft';
  return 'status-new';
});

function formatTimestamp(isoString: string | null): string {
  if (!isoString) return '';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

function handleInput(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  emit('update:modelValue', target.value);
}

function handleSaveDraft() {
  if (isActionDisabled.value) return;
  emit('save-draft');
}

function openConfirmModal() {
  if (isActionDisabled.value) return;
  isConfirmModalOpen.value = true;
  nextTick(() => {
    cancelButtonRef.value?.focus();
  });
}

function closeConfirmModal() {
  isConfirmModalOpen.value = false;
  nextTick(() => {
    triggerButtonRef.value?.focus();
  });
}

function handleConfirmFinalize() {
  closeConfirmModal();
  emit('finalize');
}

function handleModalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeConfirmModal();
    return;
  }

  if (event.key === 'Tab') {
    const focusable = [cancelButtonRef.value, confirmButtonRef.value].filter(
      (el): el is HTMLButtonElement => el !== null,
    );
    if (focusable.length < 2) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
</script>

<template>
  <div class="report-editor" data-testid="report-editor">
    <!-- Header / Metadata -->
    <header class="editor-header">
      <div class="meta-row">
        <div class="meta-left">
          <span
            data-testid="report-status-badge"
            class="status-badge"
            :class="statusBadgeClass"
          >
            {{ statusLabel }}
          </span>
          <span
            v-if="authorDisplayName"
            data-testid="report-author"
            class="meta-author"
          >
            Autor: <strong>{{ authorDisplayName }}</strong>
          </span>
        </div>

        <div class="meta-right">
          <span
            v-if="finalizedAt"
            data-testid="report-timestamp"
            class="meta-timestamp"
          >
            Finalizado em: {{ formatTimestamp(finalizedAt) }}
          </span>
          <span
            v-else-if="updatedAt"
            data-testid="report-timestamp"
            class="meta-timestamp"
          >
            Última alteração: {{ formatTimestamp(updatedAt) }}
          </span>
        </div>
      </div>
    </header>

    <!-- Main Content Area -->
    <div class="editor-body">
      <!-- Read-Only Presentation (Finalized or Third-party Draft) -->
      <div
        v-if="isReadOnly"
        data-testid="report-content-view"
        class="report-content-view pre-wrap-content"
        role="region"
        aria-label="Conteúdo do laudo finalizado"
        tabindex="0"
      >{{ modelValue }}</div>

      <!-- Editable Plain-text Textarea -->
      <div v-else class="textarea-wrapper">
        <textarea
          id="report-text-input"
          data-testid="report-textarea"
          class="report-textarea"
          :value="modelValue"
          placeholder="Descreva os achados e a conclusão do exame..."
          aria-label="Conteúdo do laudo"
          :disabled="saving || disabled"
          @input="handleInput"
        ></textarea>

        <div class="editor-footer-info">
          <span
            data-testid="code-point-counter"
            class="code-point-counter"
            :class="{ 'counter-error': isOversized }"
            aria-live="polite"
          >
            {{ codePointCount.toLocaleString('pt-BR') }} / 32.000
          </span>
        </div>
      </div>
    </div>

    <!-- Actions Toolbar (Only for editable mode) -->
    <footer v-if="!isReadOnly" class="editor-actions">
      <button
        type="button"
        data-testid="save-draft-button"
        class="btn btn-secondary"
        :disabled="isActionDisabled"
        @click="handleSaveDraft"
      >
        Salvar rascunho
      </button>

      <button
        ref="triggerButtonRef"
        type="button"
        data-testid="finalize-button"
        class="btn btn-primary"
        :disabled="isActionDisabled"
        @click="openConfirmModal"
      >
        Finalizar
      </button>
    </footer>

    <!-- Confirmation Modal for Finalize -->
    <div
      v-if="isConfirmModalOpen"
      class="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="finalize-modal-title"
      aria-describedby="finalize-modal-desc"
      @keydown="handleModalKeydown"
    >
      <div class="modal-card">
        <h2 id="finalize-modal-title" class="modal-title">
          Confirmar finalização do laudo
        </h2>
        <p id="finalize-modal-desc" class="modal-description">
          Esta ação é irreversível. O laudo finalizado não poderá ser editado ou alterado posteriormente.
        </p>

        <div class="modal-actions">
          <button
            ref="cancelButtonRef"
            type="button"
            data-testid="modal-cancel-button"
            class="btn btn-secondary"
            @click="closeConfirmModal"
          >
            Cancelar
          </button>
          <button
            ref="confirmButtonRef"
            type="button"
            data-testid="modal-confirm-button"
            class="btn btn-danger"
            @click="handleConfirmFinalize"
          >
            Confirmar finalização
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background-color: #121417;
  color: #e2e8f0;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  box-sizing: border-box;
}

.editor-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #2d3748;
  background-color: #1a202c;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  font-size: 0.8125rem;
}

.meta-left,
.meta-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.status-badge {
  display: inline-block;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.status-new {
  background-color: #4a5568;
  color: #edf2f7;
}

.status-draft {
  background-color: #2b6cb0;
  color: #ebf8ff;
}

.status-final {
  background-color: #22543d;
  color: #f0fff4;
}

.meta-author {
  color: #cbd5e0;
}

.meta-timestamp {
  color: #a0aec0;
}

.editor-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 1rem;
  overflow: hidden;
}

.textarea-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.report-textarea {
  flex: 1;
  width: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 0.75rem;
  background-color: #1a202c;
  color: #f7fafc;
  border: 1px solid #4a5568;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.9375rem;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
}

.report-textarea:focus-visible {
  border-color: #63b3ed;
  box-shadow: 0 0 0 2px rgba(99, 179, 237, 0.3);
}

.report-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.report-content-view {
  flex: 1;
  padding: 0.75rem;
  background-color: #1a202c;
  color: #edf2f7;
  border: 1px solid #2d3748;
  border-radius: 4px;
  font-size: 0.9375rem;
  line-height: 1.6;
  overflow-y: auto;
}

.pre-wrap-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.editor-footer-info {
  display: flex;
  justify-content: flex-end;
}

.code-point-counter {
  font-size: 0.75rem;
  color: #a0aec0;
}

.counter-error {
  color: #fc8181;
  font-weight: 600;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-top: 1px solid #2d3748;
  background-color: #1a202c;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 4px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background-color 0.15s ease, opacity 0.15s ease;
}

.btn:focus-visible {
  outline: 2px solid #63b3ed;
  outline-offset: 2px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: #2d3748;
  color: #edf2f7;
  border-color: #4a5568;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #4a5568;
}

.btn-primary {
  background-color: #3182ce;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2b6cb0;
}

.btn-danger {
  background-color: #e53e3e;
  color: #ffffff;
}

.btn-danger:hover:not(:disabled) {
  background-color: #c53030;
}

/* Modal styles */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
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
