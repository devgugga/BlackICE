<script setup lang="ts">
import type { WorklistFilters } from './worklist.types';
import { EMPTY_FILTERS } from './useWorklist';

const MODALITIES = ['', 'CT', 'MR', 'US', 'CR', 'DX', 'MG', 'NM', 'PT', 'XA', 'RF', 'OT'] as const;

const props = withDefaults(
  defineProps<{
    modelValue: WorklistFilters;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: WorklistFilters): void;
  (e: 'search'): void;
  (e: 'clear'): void;
}>();

function updateField<K extends keyof WorklistFilters>(field: K, value: string): void {
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value,
  });
}

function handleClear(): void {
  emit('update:modelValue', { ...EMPTY_FILTERS });
  emit('clear');
}
</script>

<template>
  <form class="worklist-filters" @submit.prevent="emit('search')">
    <div class="filter-group">
      <label for="filter-patient-name">Nome do paciente</label>
      <input
        id="filter-patient-name"
        name="patientName"
        type="text"
        :value="modelValue.patientName"
        :disabled="loading"
        placeholder="Ex: SILVA"
        @input="updateField('patientName', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="filter-group">
      <label for="filter-patient-id">ID do paciente</label>
      <input
        id="filter-patient-id"
        name="patientId"
        type="text"
        :value="modelValue.patientId"
        :disabled="loading"
        placeholder="Ex: 12345"
        @input="updateField('patientId', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="filter-group">
      <label for="filter-modality">Modalidade</label>
      <select
        id="filter-modality"
        name="modality"
        :value="modelValue.modality"
        :disabled="loading"
        @change="updateField('modality', ($event.target as HTMLSelectElement).value)"
      >
        <option v-for="mod in MODALITIES" :key="mod" :value="mod">
          {{ mod || 'Todas' }}
        </option>
      </select>
    </div>

    <div class="filter-group">
      <label for="filter-date-from">Data inicial</label>
      <input
        id="filter-date-from"
        name="dateFrom"
        type="date"
        :value="modelValue.dateFrom"
        :disabled="loading"
        @input="updateField('dateFrom', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="filter-group">
      <label for="filter-date-to">Data final</label>
      <input
        id="filter-date-to"
        name="dateTo"
        type="date"
        :value="modelValue.dateTo"
        :disabled="loading"
        @input="updateField('dateTo', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="filter-actions">
      <button type="submit" :disabled="loading">
        Buscar
      </button>
      <button type="button" :disabled="loading" @click="handleClear">
        Limpar filtros
      </button>
    </div>
  </form>
</template>

<style scoped>
.worklist-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: flex-end;
  margin-bottom: 1.5rem;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 140px;
}

.filter-group label {
  font-size: 0.875rem;
  font-weight: 500;
}

.filter-group input,
.filter-group select {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.875rem;
}

.filter-group input:focus-visible,
.filter-group select:focus-visible,
.filter-actions button:focus-visible {
  outline: 2px solid #0066cc;
  outline-offset: 2px;
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.filter-actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  background-color: #f0f0f0;
  cursor: pointer;
  font-size: 0.875rem;
}

.filter-actions button[type='submit'] {
  background-color: #0066cc;
  color: #ffffff;
  border-color: #0055aa;
}

.filter-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
