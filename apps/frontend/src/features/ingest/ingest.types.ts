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
    /**
     * Razão interna pela qual o estudo não foi armazenado, presente apenas em
     * resultados parciais. Não é um code do catálogo e não deve ser exibido:
     * serve para diagnóstico, e a fronteira já traduziu a falha total.
     */
    errorCode: 'TIMEOUT' | 'CONNECTION' | 'INTERRUPTED' | 'HTTP_STATUS' | 'INVALID_RESPONSE' | null;
  }>;
  locallyRejectedFiles: Array<{
    itemIndex: number;
    filename: string;
    code: string;
    message: string;
  }>;
}

export interface UploadHandle {
  promise: Promise<IngestResponse>;
  abort(): void;
}
