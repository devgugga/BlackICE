import type { IngestResponse, UploadHandle } from '@/features/ingest/ingest.types';

export class UploadError extends Error {
  readonly status: number;
  readonly response: IngestResponse | null;

  constructor(status: number, response: IngestResponse | null = null, message?: string) {
    super(message ?? `Upload failed with status ${status}`);
    this.name = 'UploadError';
    this.status = status;
    this.response = response;
  }
}

export function readCookie(cookies: string, name: string): string | null {
  const match = cookies.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

export async function fetchCsrfToken(
  readCookies: () => string = () => document.cookie,
): Promise<string> {
  const response = await fetch('/api/csrf', { credentials: 'include' });
  if (!response.ok) {
    throw new Error(`CSRF_TOKEN_FAILED:${response.status}`);
  }
  const token = readCookie(readCookies(), 'csrf-token');
  if (!token) {
    throw new Error('CSRF_COOKIE_MISSING');
  }
  return token;
}

export type XhrFactory = () => XMLHttpRequest;

export function uploadStudies(
  files: readonly File[],
  csrfToken: string,
  onProgress: (percent: number) => void,
  xhrFactory: XhrFactory = () => new XMLHttpRequest(),
): UploadHandle {
  const xhr = xhrFactory();
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file);
  }

  const promise = new Promise<IngestResponse>((resolve, reject) => {
    xhr.open('POST', '/api/studies');
    xhr.withCredentials = true;
    xhr.setRequestHeader('X-CSRF-TOKEN', csrfToken);

    if (xhr.upload) {
      xhr.upload.onprogress = (event: ProgressEvent) => {
        if (event.lengthComputable && event.total > 0) {
          const percent = Math.min(100, Math.max(0, Math.round((event.loaded / event.total) * 100)));
          onProgress(percent);
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText) as IngestResponse;
          resolve(response);
        } catch {
          reject(new UploadError(xhr.status, null, 'INVALID_JSON'));
        }
      } else {
        let parsed: IngestResponse | null = null;
        try {
          if (xhr.responseText) {
            parsed = JSON.parse(xhr.responseText) as IngestResponse;
          }
        } catch {
          parsed = null;
        }
        reject(new UploadError(xhr.status, parsed, `UPLOAD_FAILED:${xhr.status}`));
      }
    };

    xhr.onerror = () => {
      reject(new UploadError(0, null, 'NETWORK_ERROR'));
    };

    xhr.ontimeout = () => {
      reject(new UploadError(0, null, 'TIMEOUT'));
    };

    xhr.onabort = () => {
      reject(new UploadError(0, null, 'ABORTED'));
    };

    xhr.send(formData);
  });

  return {
    promise,
    abort: () => {
      xhr.abort();
    },
  };
}
