import { describe, it, expect, vi, afterEach } from 'vitest';
import { uploadStudies } from '@/features/ingest/ingest.api';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

/** Corpo Problem Details bem formado para o code pedido. */
function problemJson(code: keyof typeof PROBLEM_TYPES, extra: Record<string, unknown> = {}) {
  const definition = PROBLEM_TYPES[code];
  return JSON.stringify({
    type: definition.type,
    title: 'texto do operador',
    status: 'httpStatus' in definition ? definition.httpStatus : 500,
    detail: 'texto do operador',
    code,
    traceId: TRACE_ID,
    ...extra,
  });
}

import type { IngestResponse } from '@/features/ingest/ingest.types';

afterEach(() => {
  vi.restoreAllMocks();
});

class FakeXHR {
  method = '';
  url = '';
  headers: Record<string, string> = {};
  withCredentials = false;
  status = 0;
  responseText = '';
  sentBody: FormData | null = null;
  aborted = false;

  upload = {
    onprogress: null as ((event: ProgressEvent) => void) | null,
  };

  responseHeaders: Record<string, string> = {
    'content-type': 'application/problem+json',
    'x-trace-id': TRACE_ID,
  };

  getResponseHeader(name: string): string | null {
    return this.responseHeaders[name.toLowerCase()] ?? null;
  }

  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  ontimeout: (() => void) | null = null;
  onabort: (() => void) | null = null;

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(header: string, value: string) {
    this.headers[header] = value;
  }

  send(body?: Document | XMLHttpRequestBodyInit | null) {
    this.sentBody = (body as FormData) ?? null;
  }

  abort() {
    this.aborted = true;
    if (this.onabort) {
      this.onabort();
    }
  }

  // Helper methods for tests
  respondWith(status: number, body: unknown) {
    this.status = status;
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body);
    if (this.onload) {
      this.onload();
    }
  }

  triggerProgress(loaded: number, total: number, lengthComputable = true) {
    if (this.upload.onprogress) {
      this.upload.onprogress({
        loaded,
        total,
        lengthComputable,
      } as ProgressEvent);
    }
  }

  triggerError() {
    if (this.onerror) {
      this.onerror();
    }
  }

  triggerTimeout() {
    if (this.ontimeout) {
      this.ontimeout();
    }
  }
}

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


describe('uploadStudies', () => {
  it('configura FormData com campo files para cada arquivo', () => {
    let fakeXhr!: FakeXHR;
    const files = [
      new File(['data1'], 'file1.dcm', { type: 'application/dicom' }),
      new File(['data2'], 'file2.dcm', { type: 'application/dicom' }),
    ];

    uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    expect(fakeXhr.method).toBe('POST');
    expect(fakeXhr.url).toBe('/api/studies');
    expect(fakeXhr.withCredentials).toBe(true);
    expect(fakeXhr.headers['X-CSRF-TOKEN']).toBe('csrf-123');
    expect(fakeXhr.sentBody).toBeInstanceOf(FormData);
    expect(fakeXhr.sentBody?.getAll('files')).toHaveLength(2);
  });

  it('calcula e reporta progresso em porcentagem arredondada', () => {
    let fakeXhr!: FakeXHR;
    const progressValues: number[] = [];
    const files = [new File(['data'], 'test.dcm')];

    uploadStudies(files, 'csrf-123', (p) => progressValues.push(p), () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerProgress(33, 100);
    fakeXhr.triggerProgress(66, 100);
    fakeXhr.triggerProgress(100, 100);

    expect(progressValues).toEqual([33, 66, 100]);
  });

  it('ignora eventos de progresso nao computaveis ou com total zero', () => {
    let fakeXhr!: FakeXHR;
    const progressValues: number[] = [];
    const files = [new File(['data'], 'test.dcm')];

    uploadStudies(files, 'csrf-123', (p) => progressValues.push(p), () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerProgress(50, 0, false);
    expect(progressValues).toHaveLength(0);
  });

  it('resolve com IngestResponse em status 200', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.respondWith(200, mockResponse);
    const result = await handle.promise;
    expect(result).toEqual(mockResponse);
  });

  it('trata JSON invalido em 200 como resposta fora do contrato', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.responseHeaders['content-type'] = 'application/json';
    fakeXhr.respondWith(200, 'invalid json');
    await expect(handle.promise)
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', traceId: TRACE_ID });
  });

  it('expoe as violacoes do 422 catalogado', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.status = 422;
    fakeXhr.responseText = problemJson('API_DICOM_VALIDATION_FAILED', {
      violations: [{ itemIndex: 0, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' }],
    });
    fakeXhr.onload?.();

    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      const error = err as ApiError;
      expect(error).toBeInstanceOf(ApiError);
      expect(error.code).toBe('API_DICOM_VALIDATION_FAILED');
      expect(error.violations).toEqual([
        { itemIndex: 0, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' },
      ]);
      return true;
    });
  });

  it('traduz 503 catalogado do Archive', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.status = 503;
    fakeXhr.responseText = problemJson('API_ARCHIVE_UNAVAILABLE');
    fakeXhr.onload?.();

    await expect(handle.promise)
      .rejects.toMatchObject({ code: 'API_ARCHIVE_UNAVAILABLE', retryPolicy: 'MANUAL' });
  });

  it('trata resposta de erro fora do contrato como falha local', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.responseHeaders['content-type'] = 'text/plain';
    fakeXhr.respondWith(500, 'Internal Server Error');
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      const error = err as ApiError;
      expect(error).toBeInstanceOf(ApiError);
      expect(error.code).toBe('CLIENT_RESPONSE_INVALID');
      // O texto bruto do servidor nunca entra no erro.
      expect(error.message).toBe('CLIENT_RESPONSE_INVALID');
      expect(JSON.stringify(error)).not.toContain('Internal Server Error');
      return true;
    });
  });

  it('traduz erro de rede em problema local catalogado', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerError();
    await expect(handle.promise)
      .rejects.toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE', scope: 'CLIENT' });
  });

  it('traduz timeout em problema local catalogado', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerTimeout();
    await expect(handle.promise)
      .rejects.toMatchObject({ code: 'CLIENT_REQUEST_TIMEOUT', scope: 'CLIENT' });
  });

  it('preserva o cancelamento como DOMException, nao como problema', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    handle.abort();
    expect(fakeXhr.aborted).toBe(true);
    // Cancelamento é controle de fluxo: DOMException, nunca ApiError.
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(DOMException);
      expect((err as DOMException).name).toBe('AbortError');
      expect(err).not.toBeInstanceOf(ApiError);
      return true;
    });
  });
});
