export interface WorklistFilters {
  patientName: string;
  patientId: string;
  modality: string;
  dateFrom: string;
  dateTo: string;
}

export interface StudySummary {
  studyInstanceUid: string;
  patientName: string | null;
  patientId: string | null;
  patientIdIssuer: string | null;
  studyDate: string | null;
  studyTime: string | null;
  modalities: readonly string[];
  description: string | null;
  seriesCount: number | null;
  instanceCount: number | null;
}

export interface StudyPage {
  items: readonly StudySummary[];
  page: { limit: number; offset: number; hasPrevious: boolean; hasNext: boolean };
}

export interface StudySearchParams {
  filters: WorklistFilters;
  limit: number;
  offset: number;
}

export class WorklistError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string) {
    super(code);
    this.name = 'WorklistError';
    this.status = status;
    this.code = code;
  }
}
