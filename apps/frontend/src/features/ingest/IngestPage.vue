<script setup lang="ts">
import { ref, computed } from 'vue';
import IngestFileList from '@/features/ingest/IngestFileList.vue';
import IngestResult from '@/features/ingest/IngestResult.vue';
import { useIngestBatch } from '@/features/ingest/useIngestBatch';
import { problemMessage } from '@/shared/api/problems/problem-messages.pt-BR';

const batch = useIngestBatch();
const limitWarning = ref<'MAX_FILES' | 'MAX_TOTAL_BYTES' | null>(null);

const formatBytes = (bytes: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'unit', unit: 'megabyte', maximumFractionDigits: 2 })
    .format(bytes / 1_048_576);

const errorMessage = computed(() =>
  batch.error.value === null ? '' : problemMessage(batch.error.value.code),
);

const errorTraceId = computed(() => batch.error.value?.traceId ?? null);

const allowsRetry = computed(
  () => batch.error.value?.retryPolicy === 'MANUAL' && batch.files.value.length > 0,
);
const busy = computed(() => ['UPLOADING', 'PROCESSING'].includes(batch.phase.value));

/**
 * Associa cada violação ao arquivo que o próprio usuário escolheu.
 *
 * <p>O backend envia apenas `itemIndex`, nunca o nome do arquivo: nomes podem
 * conter dado identificável. O índice pertence à ordem imutável do último lote
 * submetido, não à lista editável que permanece na tela depois de uma falha.
 */
const violations = computed(() =>
  (batch.error.value?.violations ?? []).map((violation) => ({
    ...violation,
    filename:
      batch.submittedFiles.value[violation.itemIndex]?.name ?? `Arquivo ${violation.itemIndex + 1}`,
  })),
);

const totalFiles = computed(() => batch.files.value.length);
const totalBytes = computed(() => batch.files.value.reduce((sum, file) => sum + file.size, 0));

const select = (event: Event) => {
  const input = event.target as HTMLInputElement;
  if (!input.files || input.files.length === 0) return;
  const warning = batch.addFiles(Array.from(input.files));
  limitWarning.value = warning;
  input.value = '';
};

const handleRemove = (index: number) => {
  limitWarning.value = null;
  batch.removeFile(index);
};

const handleReset = () => {
  limitWarning.value = null;
  batch.reset();
};
</script>

<template>
  <main>
    <h1>Importar DICOM</h1>

    <section aria-label="Seleção de arquivos">
      <label for="dicom-file-input">Selecionar arquivos DICOM</label>
      <input
        id="dicom-file-input"
        type="file"
        multiple
        accept=".dcm,application/dicom"
        :disabled="busy"
        @change="select"
      />

      <p v-if="totalFiles > 0">
        {{ totalFiles }} {{ totalFiles === 1 ? 'arquivo' : 'arquivos' }} ({{ formatBytes(totalBytes) }})
      </p>

      <p v-if="limitWarning === 'MAX_FILES'" role="alert">
        Limite máximo de 500 arquivos atingido.
      </p>
      <p v-if="limitWarning === 'MAX_TOTAL_BYTES'" role="alert">
        Limite máximo de 500 MB atingido.
      </p>
    </section>

    <IngestFileList :files="batch.files.value" :busy="busy" @remove="handleRemove" />

    <progress
      v-if="batch.phase.value === 'UPLOADING'"
      max="100"
      :value="batch.progress.value"
      aria-label="Progresso do upload"
    />

    <p v-if="batch.phase.value === 'PROCESSING'" role="status">Processando no Archive…</p>

    <div v-if="batch.phase.value === 'ERROR' && batch.error.value" role="alert">
      <p>{{ errorMessage }}</p>

      <ul v-if="violations.length > 0" data-testid="ingest-violations">
        <li v-for="violation in violations" :key="violation.itemIndex">
          {{ violation.filename }}: {{ violation.message }}
        </li>
      </ul>

      <p v-if="errorTraceId" class="error-reference">
        Referência: <code>{{ errorTraceId }}</code>
      </p>

      <button v-if="allowsRetry" type="button" @click="batch.retry">
        Tentar novamente
      </button>
    </div>

    <p v-if="batch.phase.value === 'CANCELLED'" role="status">Importação cancelada.</p>

    <div>
      <button
        type="button"
        :disabled="batch.phase.value !== 'READY'"
        @click="batch.start"
      >
        Importar
      </button>

      <button
        v-if="batch.phase.value === 'UPLOADING'"
        type="button"
        @click="batch.cancel"
      >
        Cancelar
      </button>

      <button
        v-if="['COMPLETE', 'ERROR', 'CANCELLED'].includes(batch.phase.value)"
        type="button"
        @click="handleReset"
      >
        Nova importação
      </button>
    </div>

    <IngestResult v-if="batch.response.value" :result="batch.response.value" />
  </main>
</template>
