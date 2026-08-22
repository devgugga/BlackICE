<script setup lang="ts">
import { ref, computed } from 'vue';
import IngestFileList from '@/features/ingest/IngestFileList.vue';
import IngestResult from '@/features/ingest/IngestResult.vue';
import { useIngestBatch } from '@/features/ingest/useIngestBatch';

const batch = useIngestBatch();
const limitWarning = ref<'MAX_FILES' | 'MAX_TOTAL_BYTES' | null>(null);

const formatBytes = (bytes: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'unit', unit: 'megabyte', maximumFractionDigits: 2 })
    .format(bytes / 1_048_576);

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
        :disabled="['UPLOADING', 'PROCESSING'].includes(batch.phase.value)"
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

    <IngestFileList :files="batch.files.value" @remove="handleRemove" />

    <progress
      v-if="batch.phase.value === 'UPLOADING'"
      max="100"
      :value="batch.progress.value"
      aria-label="Progresso do upload"
    />

    <p v-if="batch.phase.value === 'PROCESSING'" role="status">Processando no Archive…</p>

    <p v-if="batch.phase.value === 'ERROR' && batch.errorCode.value" role="alert">
      Erro na importação: {{ batch.errorCode.value }}
    </p>

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
