<script setup lang="ts">
import type { StudySummary } from './worklist.types';

defineProps<{
  items: readonly StudySummary[];
}>();

const informed = (value: string | number | null) => value ?? 'Não informado';
const patientIdentifier = (study: StudySummary) =>
  [study.patientId, study.patientIdIssuer].filter(Boolean).join(' · ') || 'Não informado';
const counts = (study: StudySummary) =>
  `${study.seriesCount ?? 'Não informado'} séries · ${study.instanceCount ?? 'Não informado'} instâncias`;
const modalitiesText = (study: StudySummary) =>
  study.modalities.length > 0 ? study.modalities.join(', ') : 'Não informado';
const studyDateTime = (study: StudySummary) =>
  [study.studyDate, study.studyTime].filter(Boolean).join(' ') || 'Não informado';
</script>

<template>
  <div class="study-list-wrapper">
    <!-- Desktop Table View -->
    <table class="study-table" data-testid="study-table">
      <thead>
        <tr>
          <th scope="col">Paciente</th>
          <th scope="col">Identificação</th>
          <th scope="col">Modalidade</th>
          <th scope="col">Descrição</th>
          <th scope="col">Data e hora</th>
          <th scope="col">Contagens</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="study in items"
          :key="study.studyInstanceUid"
          data-testid="study-row"
        >
          <td>{{ informed(study.patientName) }}</td>
          <td>{{ patientIdentifier(study) }}</td>
          <td>{{ modalitiesText(study) }}</td>
          <td>{{ informed(study.description) }}</td>
          <td>{{ studyDateTime(study) }}</td>
          <td>{{ counts(study) }}</td>
        </tr>
      </tbody>
    </table>

    <!-- Mobile Card View -->
    <div class="study-cards" data-testid="study-cards">
      <article
        v-for="study in items"
        :key="study.studyInstanceUid"
        class="study-card"
        data-testid="study-card"
      >
        <header class="card-header">
          <strong class="patient-name">{{ informed(study.patientName) }}</strong>
          <span class="modality-badge">{{ modalitiesText(study) }}</span>
        </header>
        <div class="card-body">
          <p><span class="label">ID:</span> {{ patientIdentifier(study) }}</p>
          <p><span class="label">Descrição:</span> {{ informed(study.description) }}</p>
          <p><span class="label">Data/Hora:</span> {{ studyDateTime(study) }}</p>
          <p><span class="label">Contagens:</span> {{ counts(study) }}</p>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.study-list-wrapper {
  width: 100%;
  overflow-x: auto;
}

.study-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 0.875rem;
}

.study-table th,
.study-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e0e0e0;
}

.study-table th {
  background-color: #f8f9fa;
  font-weight: 600;
  color: #333;
}

.study-table tbody tr:hover {
  background-color: #f5f8fc;
}

.study-cards {
  display: none;
}

@media (max-width: 720px) {
  .study-table {
    display: none;
  }

  .study-cards {
    display: grid;
    gap: 1rem;
  }

  .study-card {
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    padding: 1rem;
    background-color: #ffffff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.75rem;
    border-bottom: 1px solid #eee;
    padding-bottom: 0.5rem;
  }

  .patient-name {
    font-size: 1rem;
    color: #111;
  }

  .modality-badge {
    background-color: #e9ecef;
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 600;
  }

  .card-body p {
    margin: 0.25rem 0;
    font-size: 0.875rem;
    color: #444;
  }

  .card-body .label {
    font-weight: 500;
    color: #666;
  }
}
</style>
