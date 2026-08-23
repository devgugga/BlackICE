<script setup lang="ts">
withDefaults(
  defineProps<{
    hasPrevious: boolean;
    hasNext: boolean;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const emit = defineEmits<{
  (e: 'previous'): void;
  (e: 'next'): void;
}>();
</script>

<template>
  <nav class="study-pagination" aria-label="Paginação de estudos">
    <button
      type="button"
      data-testid="pagination-prev"
      :disabled="!hasPrevious || loading"
      @click="emit('previous')"
    >
      Anterior
    </button>
    <button
      type="button"
      data-testid="pagination-next"
      :disabled="!hasNext || loading"
      @click="emit('next')"
    >
      Próxima
    </button>
  </nav>
</template>

<style scoped>
.study-pagination {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.study-pagination button {
  padding: 0.5rem 1rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  background-color: #ffffff;
  cursor: pointer;
  font-size: 0.875rem;
}

.study-pagination button:focus-visible {
  outline: 2px solid #0066cc;
  outline-offset: 2px;
}

.study-pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
