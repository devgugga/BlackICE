export type ReportStatus = 'DRAFT' | 'FINAL';

export interface StudyReport {
  studyInstanceUid: string;
  authorDisplayName: string;
  status: ReportStatus;
  content: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
  finalizedAt: string | null;
}

export interface ReportSnapshot {
  report: StudyReport;
  etag: string;
}
