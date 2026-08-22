<script setup lang="ts">
import type { IngestResponse, InstanceStatus } from '@/features/ingest/ingest.types';

defineProps<{
  result: IngestResponse;
}>();

const labels: Record<InstanceStatus, string> = {
  ACCEPTED: 'Armazenado',
  WARNING: 'Armazenado com aviso',
  REJECTED: 'Rejeitado pelo Archive',
  UNCONFIRMED: 'Sem confirmação do Archive',
};
</script>

<template>
  <section aria-labelledby="ingest-result-title">
    <h2 id="ingest-result-title">Resultado da importação</h2>

    <ul aria-label="Sumário da importação">
      <li>Recebidos: {{ result.summary.received }}</li>
      <li>Válidos localmente: {{ result.summary.locallyValid }}</li>
      <li>Rejeitados localmente: {{ result.summary.locallyRejected }}</li>
      <li>Armazenados no Archive: {{ result.summary.archiveAccepted }}</li>
      <li>Rejeitados pelo Archive: {{ result.summary.archiveRejected }}</li>
    </ul>

    <p>{{ result.summary.archiveAccepted }} armazenados</p>
    <p>{{ result.summary.archiveRejected }} rejeitados ou sem confirmação</p>

    <details v-for="study in result.studies" :key="study.studyInstanceUid">
      <summary>
        Estudo {{ study.studyInstanceUid }} —
        {{ study.errorCode === 'ARCHIVE_UNAVAILABLE' ? 'Archive indisponível' : study.status }}
      </summary>
      <p v-if="study.errorCode === 'ARCHIVE_UNAVAILABLE'">Archive indisponível</p>
      <ul>
        <li v-for="instance in study.instances" :key="instance.sopInstanceUid">
          {{ instance.sopInstanceUid }} — {{ labels[instance.status] }}
        </li>
      </ul>
    </details>

    <section v-if="result.locallyRejectedFiles.length > 0" aria-labelledby="locally-rejected-title">
      <h3 id="locally-rejected-title">Rejeitado antes do envio</h3>
      <ul>
        <li
          v-for="(file, index) in result.locallyRejectedFiles"
          :key="`${file.filename}-${file.code}-${index}`"
        >
          {{ file.filename }}: {{ file.code }} — {{ file.message }}
        </li>
      </ul>
    </section>
  </section>
</template>
