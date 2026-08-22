import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchCsrfToken, readCookie, uploadStudies, UploadError } from '@/features/ingest/ingest.api';
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

describe('readCookie', () => {
  it('extrai valor do cookie pelo nome', () => {
    const cookies = 'foo=bar; csrf-token=token-123; other=val';
    expect(readCookie(cookies, 'csrf-token')).toBe('token-123');
  });

  it('decodifica valor url-encoded', () => {
    const cookies = 'csrf-token=abc%20123';
    expect(readCookie(cookies, 'csrf-token')).toBe('abc 123');
  });

  it('retorna null se o cookie nao existir', () => {
    const cookies = 'foo=bar; other=val';
    expect(readCookie(cookies, 'csrf-token')).toBeNull();
  });

  it('retorna null se a string de cookies estiver vazia', () => {
    expect(readCookie('', 'csrf-token')).toBeNull();
  });
});

describe('fetchCsrfToken', () => {
  it('obtem token com sucesso quando requisicao 204 e cookie presente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 204, ok: true }));
    const token = await fetchCsrfToken(() => 'csrf-token=secret-token-xyz');
    expect(token).toBe('secret-token-xyz');
    expect(fetch).toHaveBeenCalledWith('/api/csrf', { credentials: 'include' });
  });

  it('lanca erro se requisicao falhar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401, ok: false }));
    await expect(fetchCsrfToken(() => 'csrf-token=token')).rejects.toThrow('CSRF_TOKEN_FAILED:401');
  });

  it('lanca erro se cookie csrf-token nao for retornado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 204, ok: true }));
    await expect(fetchCsrfToken(() => 'other-cookie=abc')).rejects.toThrow('CSRF_COOKIE_MISSING');
  });
});

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

  it('rejeita com UploadError se JSON em 200 for invalido', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.respondWith(200, 'invalid json');
    await expect(handle.promise).rejects.toThrow('INVALID_JSON');
  });

  it('rejeita com UploadError e corpo parseado em status 422/503', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.respondWith(422, mockResponse);
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(UploadError);
      const uploadErr = err as UploadError;
      expect(uploadErr.status).toBe(422);
      expect(uploadErr.response).toEqual(mockResponse);
      expect(uploadErr.message).toBe('UPLOAD_FAILED:422');
      return true;
    });
  });

  it('rejeita com UploadError quando resposta de erro nao for JSON', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.respondWith(500, 'Internal Server Error');
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(UploadError);
      const uploadErr = err as UploadError;
      expect(uploadErr.status).toBe(500);
      expect(uploadErr.response).toBeNull();
      expect(uploadErr.message).toBe('UPLOAD_FAILED:500');
      return true;
    });
  });

  it('rejeita com UploadError em erro de rede (onerror)', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerError();
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(UploadError);
      const uploadErr = err as UploadError;
      expect(uploadErr.status).toBe(0);
      expect(uploadErr.message).toBe('NETWORK_ERROR');
      return true;
    });
  });

  it('rejeita com UploadError em timeout (ontimeout)', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    fakeXhr.triggerTimeout();
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(UploadError);
      const uploadErr = err as UploadError;
      expect(uploadErr.status).toBe(0);
      expect(uploadErr.message).toBe('TIMEOUT');
      return true;
    });
  });

  it('permite abortar via handle.abort() e dispara onabort', async () => {
    let fakeXhr!: FakeXHR;
    const files = [new File(['data'], 'test.dcm')];

    const handle = uploadStudies(files, 'csrf-123', () => {}, () => {
      fakeXhr = new FakeXHR();
      return fakeXhr as unknown as XMLHttpRequest;
    });

    handle.abort();
    expect(fakeXhr.aborted).toBe(true);
    await expect(handle.promise).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(UploadError);
      const uploadErr = err as UploadError;
      expect(uploadErr.status).toBe(0);
      expect(uploadErr.message).toBe('ABORTED');
      return true;
    });
  });
});
