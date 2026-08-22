import { describe, it, expect, vi } from 'vitest';
import { useIngestBatch } from '@/features/ingest/useIngestBatch';
import { UploadError } from '@/features/ingest/ingest.api';
import type { IngestResponse, UploadHandle } from '@/features/ingest/ingest.types';

const mockResponse: IngestResponse = {
  outcome: 'COMPLETE',
  summary: {
    received: 1,
    locallyValid: 1,
    locallyRejected: 0,
    archiveAccepted: 1,
    archiveRejected: 0,
  },
  studies: [
    {
      studyInstanceUid: '1.2.3',
      status: 'COMPLETE',
      instances: [
        {
          sopInstanceUid: '1.2.3.1',
          status: 'ACCEPTED',
          reason: null,
        },
      ],
      errorCode: null,
    },
  ],
  locallyRejectedFiles: [],
};

const createFile = (name: string, size = 1000) =>
  new File([new Uint8Array(size)], name, { type: 'application/dicom' });

describe('useIngestBatch', () => {
  it('inicia no estado SELECTING com lista vazia e valores zerados', () => {
    const batch = useIngestBatch();
    expect(batch.phase.value).toBe('SELECTING');
    expect(batch.files.value).toEqual([]);
    expect(batch.progress.value).toBe(0);
    expect(batch.response.value).toBeNull();
    expect(batch.errorCode.value).toBeNull();
  });

  describe('addFiles e limites', () => {
    it('adiciona arquivos e transita para READY', () => {
      const batch = useIngestBatch();
      const file1 = createFile('one.dcm');
      const res = batch.addFiles([file1]);

      expect(res).toBeNull();
      expect(batch.files.value).toEqual([file1]);
      expect(batch.phase.value).toBe('READY');
    });

    it('adiciona multiplos lotes cumulativamente', () => {
      const batch = useIngestBatch();
      const file1 = createFile('one.dcm');
      const file2 = createFile('two.dcm');

      batch.addFiles([file1]);
      batch.addFiles([file2]);

      expect(batch.files.value).toEqual([file1, file2]);
      expect(batch.phase.value).toBe('READY');
    });

    it('rejeita adicao que ultrapasse maxFiles', () => {
      const batch = useIngestBatch({ maxFiles: 2, maxTotalBytes: 1_000_000 });
      const f1 = createFile('1.dcm');
      const f2 = createFile('2.dcm');
      const f3 = createFile('3.dcm');

      batch.addFiles([f1]);
      const res = batch.addFiles([f2, f3]);

      expect(res).toBe('MAX_FILES');
      expect(batch.files.value).toEqual([f1]);
      expect(batch.phase.value).toBe('READY');
    });

    it('rejeita adicao que ultrapasse maxTotalBytes', () => {
      const batch = useIngestBatch({ maxFiles: 10, maxTotalBytes: 5000 });
      const f1 = createFile('1.dcm', 3000);
      const f2 = createFile('2.dcm', 3000);

      batch.addFiles([f1]);
      const res = batch.addFiles([f2]);

      expect(res).toBe('MAX_TOTAL_BYTES');
      expect(batch.files.value).toEqual([f1]);
    });

    it('nao altera fase se adicionar array vazio em SELECTING', () => {
      const batch = useIngestBatch();
      const res = batch.addFiles([]);
      expect(res).toBeNull();
      expect(batch.phase.value).toBe('SELECTING');
    });
  });

  describe('removeFile', () => {
    it('remove arquivo pelo indice e volta para SELECTING se esvaziar', () => {
      const batch = useIngestBatch();
      const f1 = createFile('1.dcm');
      batch.addFiles([f1]);
      expect(batch.phase.value).toBe('READY');

      batch.removeFile(0);
      expect(batch.files.value).toEqual([]);
      expect(batch.phase.value).toBe('SELECTING');
    });

    it('remove arquivo intermediario mantendo fase READY se ainda restarem arquivos', () => {
      const batch = useIngestBatch();
      const f1 = createFile('1.dcm');
      const f2 = createFile('2.dcm');
      const f3 = createFile('3.dcm');
      batch.addFiles([f1, f2, f3]);

      batch.removeFile(1);
      expect(batch.files.value).toEqual([f1, f3]);
      expect(batch.phase.value).toBe('READY');
    });

    it('ignora indice invalido', () => {
      const batch = useIngestBatch();
      const f1 = createFile('1.dcm');
      batch.addFiles([f1]);

      batch.removeFile(99);
      expect(batch.files.value).toEqual([f1]);
    });
  });

  describe('start e ciclo de upload', () => {
    it('nao inicia se nao estiver em READY', async () => {
      const fetchCsrfToken = vi.fn();
      const uploadStudies = vi.fn();
      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      await batch.start();
      expect(fetchCsrfToken).not.toHaveBeenCalled();
      expect(batch.phase.value).toBe('SELECTING');
    });

    it('executa fluxo completo com sucesso: SELECTING -> READY -> UPLOADING -> PROCESSING -> COMPLETE', async () => {
      let progressCallback: (percent: number) => void = () => {};
      let resolvePromise!: (res: IngestResponse) => void;

      const fetchCsrfToken = vi.fn().mockResolvedValue('csrf-token-abc');
      const uploadStudies = vi.fn().mockImplementation((_files, _csrf, onProgress) => {
        progressCallback = onProgress;
        const promise = new Promise<IngestResponse>((resolve) => {
          resolvePromise = resolve;
        });
        return { promise, abort: vi.fn() } as UploadHandle;
      });

      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      const f1 = createFile('1.dcm');
      batch.addFiles([f1]);
      expect(batch.phase.value).toBe('READY');

      const startPromise = batch.start();
      await vi.waitFor(() => expect(uploadStudies).toHaveBeenCalled());

      expect(batch.phase.value).toBe('UPLOADING');
      expect(batch.progress.value).toBe(0);

      // Atualiza progresso intermediario
      progressCallback(50);
      expect(batch.progress.value).toBe(50);
      expect(batch.phase.value).toBe('UPLOADING');

      // Ao atingir 100%, transita para PROCESSING
      progressCallback(100);
      expect(batch.progress.value).toBe(100);
      expect(batch.phase.value).toBe('PROCESSING');

      // Servidor responde
      resolvePromise(mockResponse);
      await startPromise;

      expect(batch.phase.value).toBe('COMPLETE');
      expect(batch.response.value).toEqual(mockResponse);
      expect(batch.errorCode.value).toBeNull();
    });

    it('transita para ERROR quando fetchCsrfToken falhar', async () => {
      const fetchCsrfToken = vi.fn().mockRejectedValue(new Error('CSRF_TOKEN_FAILED:401'));
      const uploadStudies = vi.fn();
      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      batch.addFiles([createFile('1.dcm')]);
      await batch.start();

      expect(batch.phase.value).toBe('ERROR');
      expect(batch.errorCode.value).toBe('CSRF_TOKEN_FAILED:401');
      expect(uploadStudies).not.toHaveBeenCalled();
    });

    it('transita para ERROR quando uploadStudies rejeitar com UploadError', async () => {
      const partialResponse: IngestResponse = {
        outcome: 'PARTIAL',
        summary: {
          received: 1,
          locallyValid: 1,
          locallyRejected: 0,
          archiveAccepted: 0,
          archiveRejected: 1,
        },
        studies: [],
        locallyRejectedFiles: [],
      };

      const fetchCsrfToken = vi.fn().mockResolvedValue('csrf-token-abc');
      const uploadStudies = vi.fn().mockReturnValue({
        promise: Promise.reject(new UploadError(422, partialResponse, 'UPLOAD_FAILED:422')),
        abort: vi.fn(),
      });

      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      batch.addFiles([createFile('1.dcm')]);
      await batch.start();

      expect(batch.phase.value).toBe('ERROR');
      expect(batch.errorCode.value).toBe('UPLOAD_FAILED:422');
      expect(batch.response.value).toEqual(partialResponse);
    });
  });

  describe('cancel e reset', () => {
    it('aborta requisicao em andamento e muda para CANCELLED ao chamar cancel()', async () => {
      const abortFn = vi.fn();
      const fetchCsrfToken = vi.fn().mockResolvedValue('csrf-token-abc');
      const uploadStudies = vi.fn().mockImplementation(() => {
        const promise = new Promise<IngestResponse>((_, reject) => {
          abortFn.mockImplementation(() => {
            reject(new UploadError(0, null, 'ABORTED'));
          });
        });
        return { promise, abort: abortFn };
      });

      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      batch.addFiles([createFile('1.dcm')]);
      const startPromise = batch.start();

      await vi.waitFor(() => expect(uploadStudies).toHaveBeenCalled());
      expect(batch.phase.value).toBe('UPLOADING');

      batch.cancel();
      expect(abortFn).toHaveBeenCalled();
      await startPromise;

      expect(batch.phase.value).toBe('CANCELLED');
    });

    it('reinicializa estado ao chamar reset()', async () => {
      const fetchCsrfToken = vi.fn().mockResolvedValue('csrf-token-abc');
      const uploadStudies = vi.fn().mockReturnValue({
        promise: Promise.resolve(mockResponse),
        abort: vi.fn(),
      });

      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      batch.addFiles([createFile('1.dcm')]);
      await batch.start();
      expect(batch.phase.value).toBe('COMPLETE');
      expect(batch.response.value).not.toBeNull();

      batch.reset();
      expect(batch.phase.value).toBe('SELECTING');
      expect(batch.files.value).toEqual([]);
      expect(batch.progress.value).toBe(0);
      expect(batch.response.value).toBeNull();
      expect(batch.errorCode.value).toBeNull();
    });

    it('reset aborta upload em andamento se executado durante UPLOADING', async () => {
      const abortFn = vi.fn();
      const fetchCsrfToken = vi.fn().mockResolvedValue('csrf-token-abc');
      const uploadStudies = vi.fn().mockReturnValue({
        promise: new Promise<IngestResponse>(() => {}),
        abort: abortFn,
      });

      const batch = useIngestBatch(
        { maxFiles: 500, maxTotalBytes: 524_288_000 },
        { fetchCsrfToken, uploadStudies },
      );

      batch.addFiles([createFile('1.dcm')]);
      batch.start();

      await vi.waitFor(() => expect(uploadStudies).toHaveBeenCalled());
      batch.reset();

      expect(abortFn).toHaveBeenCalled();
      expect(batch.phase.value).toBe('SELECTING');
    });
  });
});
