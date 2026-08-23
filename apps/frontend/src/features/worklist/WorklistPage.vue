<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useWorklist, EMPTY_FILTERS } from './useWorklist';
import type { WorklistFilters as FiltersType } from './worklist.types';
import WorklistFilters from './WorklistFilters.vue';
import StudyList from './StudyList.vue';
import StudyPagination from './StudyPagination.vue';

const draftFilters = ref<FiltersType>({ ...EMPTY_FILTERS });
const worklist = useWorklist();

const hasAppliedFilters = computed(() =>
  Object.values(worklist.appliedFilters.value).some((value) => Boolean(value && value.trim())),
);

const errorMessage = computed(() => {
  const code = worklist.errorCode.value;
  switch (code) {
    case 'INVALID_SEARCH':
      return 'Filtros de busca inválidos. Verifique os filtros informados.';
    case 'SEARCH_TOO_BROAD':
      return 'A busca retornou muitos resultados. Especifique filtros mais restritos.';
    case 'ARCHIVE_INVALID_RESPONSE':
      return 'Resposta inválida do Archive.';
    case 'ARCHIVE_UNAVAILABLE':
      return 'Archive temporariamente indisponível.';
    case 'NETWORK_ERROR':
    default:
      return 'Serviço temporariamente indisponível. Tente novamente mais tarde.';
  }
});

async function handleSearch(): Promise<void> {
  await worklist.search(draftFilters.value);
}

async function handleClear(): Promise<void> {
  draftFilters.value = { ...EMPTY_FILTERS };
  await worklist.clear();
}

onMounted(() => {
  worklist.loadRecent();
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
        <button type="button" @click="worklist.retry">
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
        <StudyList :items="worklist.items.value" />
        <StudyPagination
          :has-previous="worklist.page.value.hasPrevious"
          :has-next="worklist.page.value.hasNext"
          @previous="worklist.previous"
          @next="worklist.next"
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

.error-message p {
  margin: 0;
  font-weight: 500;
}

.error-message button {
  padding: 0.5rem 1rem;
  background-color: #dc3545;
  color: #ffffff;
  border: none;
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
