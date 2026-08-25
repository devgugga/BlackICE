<script setup lang="ts">
import { computed } from 'vue';
import type { StudyViewerSummary } from './viewer.types';

const props = defineProps<{
  study: StudyViewerSummary | null;
}>();

const emit = defineEmits<{
  (e: 'back'): void;
}>();

const informed = (value: string | null | undefined) => value || 'Não informado';

const patientName = computed(() => informed(props.study?.patientName));

const patientIdentifier = computed(() => {
  if (!props.study) return 'Não informado';
  const parts = [props.study.patientId, props.study.patientIdIssuer].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : 'Não informado';
});

const studyDateTime = computed(() => {
  if (!props.study) return 'Não informado';
  const parts = [props.study.studyDate, props.study.studyTime].filter(Boolean);
  return parts.length > 0 ? parts.join(' ') : 'Não informado';
});

const studyDescription = computed(() => informed(props.study?.description));
</script>

<template>
  <header class="study-header" aria-label="Cabeçalho do estudo">
    <div class="header-left">
      <button
        type="button"
        class="back-button"
        data-testid="back-button"
        aria-label="Voltar para a lista de estudos"
        @click="emit('back')"
      >
        <span class="back-icon" aria-hidden="true">&larr;</span>
        <span>Voltar</span>
      </button>
    </div>

    <div class="header-metadata">
      <div class="meta-item">
        <span class="meta-label">Paciente:</span>
        <strong class="meta-value">{{ patientName }}</strong>
      </div>

      <div class="meta-item">
        <span class="meta-label">ID:</span>
        <span class="meta-value">{{ patientIdentifier }}</span>
      </div>

      <div class="meta-item">
        <span class="meta-label">Data:</span>
        <span class="meta-value">{{ studyDateTime }}</span>
      </div>

      <div class="meta-item">
        <span class="meta-label">Descrição:</span>
        <span class="meta-value">{{ studyDescription }}</span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.study-header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  background-color: #1a1d20;
  color: #e2e8f0;
  padding: 0.625rem 1rem;
  border-bottom: 1px solid #2d3748;
  font-size: 0.875rem;
  min-height: 48px;
}

.header-left {
  display: flex;
  align-items: center;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background-color: #2d3748;
  color: #f7fafc;
  border: 1px solid #4a5568;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 500;
  transition: background-color 0.15s ease-in-out;
  white-space: nowrap;
}

.back-button:hover {
  background-color: #4a5568;
}

.back-button:focus-visible {
  outline: 2px solid #3182ce;
  outline-offset: 2px;
}

.back-icon {
  font-size: 1rem;
  line-height: 1;
}

.header-metadata {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.25rem;
}

.meta-item {
  display: flex;
  align-items: baseline;
  gap: 0.375rem;
}

.meta-label {
  color: #a0aec0;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

.meta-value {
  color: #f7fafc;
  font-size: 0.875rem;
}
</style>
