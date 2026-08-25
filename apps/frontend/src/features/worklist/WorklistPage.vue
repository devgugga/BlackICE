<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { problemMessage } from '@/shared/api/problems/problem-messages.pt-BR';

import { useWorklist, EMPTY_FILTERS, PAGE_SIZE } from './useWorklist';
import {
  parseWorklistQuery,
  canonicalWorklistQuery,
  getWorklistCanonicalKey,
  saveWorklistSnapshot,
  restoreWorklistSnapshot,
} from './worklist-navigation';
import type { WorklistFilters as FiltersType } from './worklist.types';
import WorklistFilters from './WorklistFilters.vue';
import StudyList from './StudyList.vue';
import StudyPagination from './StudyPagination.vue';

const router = useRouter();
const route = useRoute();

const draftFilters = ref<FiltersType>({ ...EMPTY_FILTERS });
const worklist = useWorklist();

const hasAppliedFilters = computed(() =>
  Object.values(worklist.appliedFilters.value).some((value) => Boolean(value && value.trim())),
);

// O texto vem do mapa central: a página controla layout e ação, nunca o
// significado. O `detail` do backend, voltado a operadores, não é renderizado.
const errorMessage = computed(() =>
  worklist.error.value === null ? '' : problemMessage(worklist.error.value.code),
);

const errorTraceId = computed(() => worklist.error.value?.traceId ?? null);

// Repetir a mesma busca só é oferecido quando o catálogo diz que ajuda.
const allowsRetry = computed(() => worklist.error.value?.retryPolicy === 'MANUAL');

function saveCurrentSnapshotAndSyncRoute(): void {
  const currentParams = {
    filters: { ...worklist.appliedFilters.value },
    limit: PAGE_SIZE,
    offset: worklist.appliedOffset.value,
  };
  const key = getWorklistCanonicalKey(currentParams);
  const canonicalQuery = canonicalWorklistQuery(currentParams);

  saveWorklistSnapshot({
    key,
    filters: { ...worklist.appliedFilters.value },
    page: {
      items: worklist.items.value,
      page: worklist.page.value,
    },
  });

  router?.replace({ query: canonicalQuery });
}

async function handleSearch(): Promise<void> {
  await worklist.search(draftFilters.value);
  if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
    saveCurrentSnapshotAndSyncRoute();
  }
}

async function handleClear(): Promise<void> {
  draftFilters.value = { ...EMPTY_FILTERS };
  await worklist.clear();
  if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
    saveCurrentSnapshotAndSyncRoute();
  }
}

async function handlePrevious(): Promise<void> {
  await worklist.previous();
  if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
    saveCurrentSnapshotAndSyncRoute();
  }
}

async function handleNext(): Promise<void> {
  await worklist.next();
  if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
    saveCurrentSnapshotAndSyncRoute();
  }
}

async function handleRetry(): Promise<void> {
  await worklist.retry();
  if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
    saveCurrentSnapshotAndSyncRoute();
  }
}

function handleOpenStudy(studyUid: string): void {
  router?.push({ name: 'viewer', params: { studyUid } });
}

onMounted(async () => {
  const query = route?.query ?? {};
  const currentParams = parseWorklistQuery(query);
  const key = getWorklistCanonicalKey(currentParams);
  const snapshot = restoreWorklistSnapshot(key);

  if (snapshot) {
    worklist.restoreSnapshot(snapshot);
    draftFilters.value = { ...snapshot.filters };
  } else {
    draftFilters.value = { ...currentParams.filters };
    await worklist.load(currentParams.filters, currentParams.offset);
    if (worklist.phase.value === 'READY' || worklist.phase.value === 'EMPTY') {
      saveCurrentSnapshotAndSyncRoute();
    }
  }
});

onUnmounted(() => {
  worklist.dispose();
});
</script>

<template>
  <main class="worklist-page">
    <header class="worklist-header">
      <h1>Worklist</h1>
    </header>

    <WorklistFilters
      v-model="draftFilters"
      :loading="worklist.phase.value === 'LOADING'"
      @search="handleSearch"
      @clear="handleClear"
    />

    <section aria-label="Resultados de estudos" class="worklist-results">
      <div class="results-heading-group">
        <h2>Estudos</h2>
        <span class="sort-order-hint">Mais recentes primeiro</span>
      </div>

      <p
        v-if="worklist.phase.value === 'LOADING'"
        role="status"
        aria-live="polite"
        class="status-message"
      >
        Carregando estudos…
      </p>

      <div
        v-else-if="worklist.phase.value === 'ERROR'"
        role="alert"
        class="error-message"
      >
        <p>{{ errorMessage }}</p>
        <p v-if="errorTraceId" class="error-reference">
          Referência: <code>{{ errorTraceId }}</code>
        </p>
        <button v-if="allowsRetry" type="button" @click="handleRetry">
          Tentar novamente
        </button>
      </div>

      <p
        v-else-if="worklist.phase.value === 'EMPTY'"
        role="status"
        aria-live="polite"
        class="status-message"
      >
        {{
          hasAppliedFilters
            ? 'Nenhum estudo encontrado para os filtros informados.'
            : 'Nenhum estudo disponível.'
        }}
      </p>

      <template v-else-if="worklist.phase.value === 'READY'">
        <StudyList :items="worklist.items.value" @open="handleOpenStudy" />
        <StudyPagination
          :has-previous="worklist.page.value.hasPrevious"
          :has-next="worklist.page.value.hasNext"
          @previous="handlePrevious"
          @next="handleNext"
        />
      </template>
    </section>
  </main>
</template>

<style scoped>
.worklist-page {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  color: #212529;
}

.worklist-header {
  margin-bottom: 1.5rem;
}

.worklist-header h1 {
  font-size: 1.75rem;
  margin: 0;
}

.worklist-results {
  margin-top: 2rem;
}

.results-heading-group {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  margin-bottom: 1rem;
  border-bottom: 2px solid #e9ecef;
  padding-bottom: 0.5rem;
}

.results-heading-group h2 {
  font-size: 1.25rem;
  margin: 0;
}

.sort-order-hint {
  font-size: 0.875rem;
  color: #6c757d;
}

.status-message {
  padding: 2rem;
  text-align: center;
  color: #495057;
  font-size: 1rem;
}

.error-message {
  padding: 1.5rem;
  border: 1px solid #f5c2c7;
  background-color: #f8d7da;
  color: #842029;
  border-radius: 6px;
  margin-top: 1rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
}

.error-message .error-reference {
  font-size: 0.85rem;
  opacity: 0.8;
}

.error-message .error-reference code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  user-select: all;
}

.error-message p {
  margin: 0;
  font-weight: 500;
}

.error-message button {
  padding: 0.5rem 1rem;
  background-color: #dc3545;
  color: #ffffff;
  border: 1px solid #dc3545;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
}

.error-message button:hover {
  background-color: #bb2d3b;
}

.error-message button:focus-visible {
  outline: 2px solid #842029;
  outline-offset: 2px;
}
</style>
