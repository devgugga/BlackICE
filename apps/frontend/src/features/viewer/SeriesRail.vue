<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ViewerSeriesSummary, UnsupportedReason } from './viewer.types';

const props = withDefaults(
  defineProps<{
    series: readonly ViewerSeriesSummary[];
    selectedSeriesUid?: string | null;
    collapsed?: boolean;
  }>(),
  {
    selectedSeriesUid: null,
    collapsed: false,
  },
);

const emit = defineEmits<{
  (e: 'selectSeries', seriesUid: string): void;
  (e: 'update:collapsed', value: boolean): void;
}>();

const isCollapsed = ref(props.collapsed);

watch(
  () => props.collapsed,
  (val) => {
    isCollapsed.value = val;
  },
);

function toggleCollapse(): void {
  isCollapsed.value = !isCollapsed.value;
  emit('update:collapsed', isCollapsed.value);
}

function handleSelect(s: ViewerSeriesSummary): void {
  if (s.availability === 'SUPPORTED') {
    emit('selectSeries', s.seriesInstanceUid);
  }
}

function getUnsupportedMessage(reason: UnsupportedReason): string {
  switch (reason) {
    case 'MULTI_FRAME':
      return 'Objeto multi-frame ainda não suportado';
    case 'NON_IMAGE_OBJECT':
      return 'Tipo de objeto não suportado';
    case 'IMAGE_SOP_CLASS_UNSUPPORTED':
      return 'Formato de imagem não suportado';
    default:
      return 'Série não suportada';
  }
}

const formatSeriesNumber = (num: number | null) => (num !== null ? `Série ${num}` : 'Não informado');
const formatDescription = (desc: string | null) => desc || 'Não informado';
</script>

<template>
  <aside
    class="series-rail"
    :class="{ 'is-collapsed': isCollapsed }"
    aria-label="Painel de séries do estudo"
  >
    <div class="rail-header">
      <button
        type="button"
        class="rail-toggle-btn"
        data-testid="rail-toggle"
        :aria-expanded="!isCollapsed"
        :aria-label="isCollapsed ? 'Expandir painel de séries' : 'Recolher painel de séries'"
        @click="toggleCollapse"
      >
        <span class="toggle-icon" aria-hidden="true">{{ isCollapsed ? '▶' : '◀' }}</span>
        <span v-if="!isCollapsed" class="toggle-text">Séries ({{ series.length }})</span>
      </button>
    </div>

    <div v-show="!isCollapsed" class="rail-content">
      <ul class="series-list" role="listbox" aria-label="Lista de séries">
        <li
          v-for="s in series"
          :key="s.seriesInstanceUid"
          class="series-item"
          data-testid="series-item"
          role="none"
        >
          <button
            type="button"
            role="option"
            class="series-card"
            :class="{
              'is-selected': s.seriesInstanceUid === selectedSeriesUid,
              'is-unsupported': s.availability === 'UNSUPPORTED',
            }"
            data-testid="series-card"
            :aria-selected="s.seriesInstanceUid === selectedSeriesUid ? 'true' : 'false'"
            :aria-disabled="s.availability === 'UNSUPPORTED' ? 'true' : 'false'"
            :disabled="s.availability === 'UNSUPPORTED'"
            @click="handleSelect(s)"
          >
            <div class="series-card-top">
              <span class="series-number">{{ formatSeriesNumber(s.seriesNumber) }}</span>
              <span class="modality-badge">{{ s.modality }}</span>
            </div>

            <div class="series-description">
              {{ formatDescription(s.description) }}
            </div>

            <div class="series-card-bottom">
              <span class="instance-count">{{ s.instanceCount }} instâncias</span>
            </div>

            <div
              v-if="s.availability === 'UNSUPPORTED'"
              class="unsupported-reason"
              role="note"
            >
              {{ getUnsupportedMessage(s.unsupportedReason) }}
            </div>
          </button>
        </li>
      </ul>
    </div>
  </aside>
</template>

<style scoped>
.series-rail {
  display: flex;
  flex-direction: column;
  background-color: #121417;
  color: #e2e8f0;
  border-right: 1px solid #2d3748;
  width: 260px;
  min-width: 260px;
  height: 100%;
  transition: width 0.2s ease-in-out, min-width 0.2s ease-in-out;
  overflow: hidden;
  user-select: none;
}

.series-rail.is-collapsed {
  width: 48px;
  min-width: 48px;
}

.rail-header {
  padding: 0.5rem;
  border-bottom: 1px solid #2d3748;
  background-color: #1a1d20;
}

.rail-toggle-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.375rem 0.5rem;
  background: transparent;
  color: #cbd5e0;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
  text-align: left;
}

.rail-toggle-btn:hover {
  background-color: #2d3748;
  color: #f7fafc;
}

.rail-toggle-btn:focus-visible {
  outline: 2px solid #3182ce;
  outline-offset: 2px;
}

.toggle-icon {
  font-size: 0.75rem;
}

.rail-content {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
}

.series-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.series-item {
  margin: 0;
  padding: 0;
}

.series-card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  width: 100%;
  padding: 0.625rem 0.75rem;
  background-color: #1e2227;
  color: #e2e8f0;
  border: 1px solid #2d3748;
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease-in-out;
}

.series-card:hover:not(:disabled) {
  background-color: #2a3038;
  border-color: #4a5568;
}

.series-card:focus-visible {
  outline: 2px solid #3182ce;
  outline-offset: 2px;
}

.series-card.is-selected {
  background-color: #2b4360;
  border-color: #3182ce;
  box-shadow: 0 0 0 1px #3182ce;
}

.series-card.is-unsupported {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #1a1c20;
  border-color: #262a30;
}

.series-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.series-number {
  font-weight: 600;
  font-size: 0.8125rem;
  color: #f7fafc;
}

.modality-badge {
  background-color: #2d3748;
  color: #90cdf4;
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.series-description {
  font-size: 0.75rem;
  color: #a0aec0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.series-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.125rem;
}

.instance-count {
  font-size: 0.6875rem;
  color: #718096;
}

.unsupported-reason {
  margin-top: 0.25rem;
  padding-top: 0.25rem;
  border-top: 1px dashed #4a5568;
  font-size: 0.6875rem;
  color: #feb2b2;
}
</style>
