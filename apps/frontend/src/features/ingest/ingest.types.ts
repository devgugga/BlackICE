export type IngestOutcome = 'COMPLETE' | 'PARTIAL' | 'FAILED';
export type InstanceStatus = 'ACCEPTED' | 'WARNING' | 'REJECTED' | 'UNCONFIRMED';
export type StudyStatus = 'COMPLETE' | 'PARTIAL' | 'FAILED';

export interface IngestResponse {
  outcome: IngestOutcome;
  summary: {
    received: number;
    locallyValid: number;
    locallyRejected: number;
    archiveAccepted: number;
    archiveRejected: number;
  };
  studies: Array<{
    studyInstanceUid: string;
    status: StudyStatus;
    instances: Array<{
      sopInstanceUid: string;
      status: InstanceStatus;
      reason: number | null;
    }>;
    errorCode: 'ARCHIVE_UNAVAILABLE' | null;
  }>;
  locallyRejectedFiles: Array<{
    filename: string;
    code: string;
    message: string;
  }>;
}

export interface UploadHandle {
  promise: Promise<IngestResponse>;
  abort(): void;
}
