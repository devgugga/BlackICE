import { ref, readonly, type Ref } from 'vue';
import type { IngestResponse, UploadHandle } from '@/features/ingest/ingest.types';
import { fetchCsrfToken, uploadStudies, UploadError } from '@/features/ingest/ingest.api';

export type IngestPhase =
  | 'SELECTING'
  | 'READY'
  | 'UPLOADING'
  | 'PROCESSING'
  | 'COMPLETE'
  | 'ERROR'
  | 'CANCELLED';

export interface IngestLimits {
  maxFiles: number;
  maxTotalBytes: number;
}

export interface IngestApi {
  fetchCsrfToken: (readCookies?: () => string) => Promise<string>;
  uploadStudies: (
    files: readonly File[],
    csrfToken: string,
    onProgress: (percent: number) => void,
    xhrFactory?: () => XMLHttpRequest,
  ) => UploadHandle;
}

export interface IngestBatch {
  phase: Readonly<Ref<IngestPhase>>;
  files: Readonly<Ref<readonly File[]>>;
  progress: Readonly<Ref<number>>;
  response: Readonly<Ref<IngestResponse | null>>;
  errorCode: Readonly<Ref<string | null>>;
  addFiles(incoming: readonly File[]): 'MAX_FILES' | 'MAX_TOTAL_BYTES' | null;
  removeFile(index: number): void;
  start(): Promise<void>;
  cancel(): void;
  reset(): void;
}

const DEFAULT_LIMITS: IngestLimits = {
  maxFiles: 500,
  maxTotalBytes: 524_288_000,
};

const DEFAULT_API: IngestApi = {
  fetchCsrfToken,
  uploadStudies,
};

export function useIngestBatch(
  limits: IngestLimits = DEFAULT_LIMITS,
  api: IngestApi = DEFAULT_API,
): IngestBatch {
  const phase = ref<IngestPhase>('SELECTING');
  const files = ref<readonly File[]>([]);
  const progress = ref<number>(0);
  const response = ref<IngestResponse | null>(null);
  const errorCode = ref<string | null>(null);

  let activeUpload: UploadHandle | null = null;

  function addFiles(incoming: readonly File[]): 'MAX_FILES' | 'MAX_TOTAL_BYTES' | null {
    if (incoming.length === 0) {
      return null;
    }

    if (files.value.length + incoming.length > limits.maxFiles) {
      return 'MAX_FILES';
    }

    const currentTotalBytes = files.value.reduce((sum, file) => sum + file.size, 0);
    const incomingTotalBytes = incoming.reduce((sum, file) => sum + file.size, 0);

    if (currentTotalBytes + incomingTotalBytes > limits.maxTotalBytes) {
      return 'MAX_TOTAL_BYTES';
    }

    files.value = [...files.value, ...incoming];
    if (files.value.length > 0) {
      phase.value = 'READY';
    }

    return null;
  }

  function removeFile(index: number): void {
    if (index < 0 || index >= files.value.length) {
      return;
    }

    files.value = files.value.filter((_, i) => i !== index);
    if (files.value.length === 0) {
      phase.value = 'SELECTING';
    }
  }

  async function start(): Promise<void> {
    if (phase.value !== 'READY' || files.value.length === 0) {
      return;
    }

    phase.value = 'UPLOADING';
    progress.value = 0;
    errorCode.value = null;
    response.value = null;

    try {
      const csrfToken = await api.fetchCsrfToken();
      if ((phase.value as IngestPhase) === 'CANCELLED') {
        return;
      }

      const uploadHandle = api.uploadStudies(files.value, csrfToken, (percent) => {
        const currentPhase = phase.value as IngestPhase;
        if (currentPhase !== 'UPLOADING' && currentPhase !== 'PROCESSING') {
          return;
        }
        progress.value = percent;
        if (percent >= 100 && currentPhase === 'UPLOADING') {
          phase.value = 'PROCESSING';
        }
      });

      activeUpload = uploadHandle;
      const res = await uploadHandle.promise;

      if ((phase.value as IngestPhase) === 'CANCELLED') {
        return;
      }

      response.value = res;
      progress.value = 100;
      phase.value = 'COMPLETE';
    } catch (err: unknown) {
      if ((phase.value as IngestPhase) === 'CANCELLED') {
        return;
      }

      if (err instanceof UploadError && err.message === 'ABORTED') {
        phase.value = 'CANCELLED';
        return;
      }

      phase.value = 'ERROR';
      if (err instanceof UploadError) {
        response.value = err.response ?? null;
        errorCode.value = err.message;
      } else if (err instanceof Error) {
        errorCode.value = err.message;
      } else {
        errorCode.value = String(err);
      }
    } finally {
      activeUpload = null;
    }
  }

  function cancel(): void {
    const currentPhase = phase.value as IngestPhase;
    if (currentPhase === 'UPLOADING' || currentPhase === 'PROCESSING') {
      phase.value = 'CANCELLED';
      if (activeUpload) {
        activeUpload.abort();
        activeUpload = null;
      }
    }
  }

  function reset(): void {
    if (activeUpload) {
      activeUpload.abort();
      activeUpload = null;
    }
    files.value = [];
    progress.value = 0;
    response.value = null;
    errorCode.value = null;
    phase.value = 'SELECTING';
  }

  return {
    phase: readonly(phase) as Readonly<Ref<IngestPhase>>,
    files: readonly(files) as Readonly<Ref<readonly File[]>>,
    progress: readonly(progress) as Readonly<Ref<number>>,
    response: readonly(response) as unknown as Readonly<Ref<IngestResponse | null>>,
    errorCode: readonly(errorCode) as Readonly<Ref<string | null>>,
    addFiles,
    removeFile,
    start,
    cancel,
    reset,
  };
}
