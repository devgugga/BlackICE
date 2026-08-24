import { ref, readonly, type Ref } from 'vue';
import type { IngestResponse, UploadHandle } from '@/features/ingest/ingest.types';
import { fetchCsrfToken, uploadStudies } from '@/features/ingest/ingest.api';
import { ApiError } from '@/shared/api/problems/api-error';
import { isIntentionalAbort } from '@/shared/api/problems/parse-problem';

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
  submittedFiles: Readonly<Ref<readonly File[]>>;
  progress: Readonly<Ref<number>>;
  response: Readonly<Ref<IngestResponse | null>>;
  error: Readonly<Ref<ApiError | null>>;
  retry(): Promise<void>;
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
  const submittedFiles = ref<readonly File[]>([]);
  const progress = ref<number>(0);
  const response = ref<IngestResponse | null>(null);
  const error = ref<ApiError | null>(null);

  let activeUpload: UploadHandle | null = null;
  let activeGeneration = 0;

  function isBusy(): boolean {
    return phase.value === 'UPLOADING' || phase.value === 'PROCESSING';
  }

  function addFiles(incoming: readonly File[]): 'MAX_FILES' | 'MAX_TOTAL_BYTES' | null {
    if (incoming.length === 0 || isBusy()) {
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
    if (isBusy() || index < 0 || index >= files.value.length) {
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

    const generation = ++activeGeneration;
    const uploadFiles: readonly File[] = Object.freeze([...files.value]);
    submittedFiles.value = uploadFiles;
    phase.value = 'UPLOADING';
    progress.value = 0;
    error.value = null;
    response.value = null;

    try {
      const csrfToken = await api.fetchCsrfToken();
      if (generation !== activeGeneration) {
        return;
      }

      const uploadHandle = api.uploadStudies(uploadFiles, csrfToken, (percent) => {
        if (generation !== activeGeneration) return;
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

      if (generation !== activeGeneration) {
        return;
      }

      response.value = res;
      progress.value = 100;
      phase.value = 'COMPLETE';
    } catch (caught: unknown) {
      if (generation !== activeGeneration) {
        return;
      }

      // Cancelamento termina em CANCELLED, sem problema e sem log de erro.
      if (isIntentionalAbort(caught)) {
        phase.value = 'CANCELLED';
        return;
      }

      phase.value = 'ERROR';
      error.value = caught instanceof ApiError ? caught : new ApiError('CLIENT_UNEXPECTED_ERROR');
    } finally {
      if (generation === activeGeneration) activeUpload = null;
    }
  }

  /**
   * Repete o lote preservado depois de uma falha que admite retentativa.
   *
   * <p>Os arquivos continuam selecionados, então não é preciso pedi-los de novo.
   */
  async function retry(): Promise<void> {
    if (phase.value !== 'ERROR' || files.value.length === 0) return;
    if (error.value !== null && error.value.retryPolicy !== 'MANUAL') return;
    phase.value = 'READY';
    return start();
  }

  function cancel(): void {
    const currentPhase = phase.value as IngestPhase;
    if (currentPhase === 'UPLOADING' || currentPhase === 'PROCESSING') {
      activeGeneration += 1;
      phase.value = 'CANCELLED';
      if (activeUpload) {
        activeUpload.abort();
        activeUpload = null;
      }
    }
  }

  function reset(): void {
    activeGeneration += 1;
    if (activeUpload) {
      activeUpload.abort();
      activeUpload = null;
    }
    files.value = [];
    submittedFiles.value = [];
    progress.value = 0;
    response.value = null;
    error.value = null;
    phase.value = 'SELECTING';
  }

  return {
    phase: readonly(phase) as Readonly<Ref<IngestPhase>>,
    files: readonly(files) as Readonly<Ref<readonly File[]>>,
    submittedFiles: readonly(submittedFiles) as Readonly<Ref<readonly File[]>>,
    progress: readonly(progress) as Readonly<Ref<number>>,
    response: readonly(response) as unknown as Readonly<Ref<IngestResponse | null>>,
    error: readonly(error) as unknown as Readonly<Ref<ApiError | null>>,
    addFiles,
    removeFile,
    start,
    retry,
    cancel,
    reset,
  };
}
