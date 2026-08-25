import type { IngestResponse, UploadHandle } from '@/features/ingest/ingest.types';
import {
  apiErrorFromXhr,
  clientError,
} from '@/shared/api/problems/parse-problem';

export { fetchCsrfToken, readCookie } from '@/shared/api/csrf';

export type XhrFactory = () => XMLHttpRequest;

/**
 * Envia o lote por XHR, que é o que dá progresso de upload.
 *
 * <p>O caminho de erro usa o mesmo parser do `fetch`, então XHR e `fetch` não
 * divergem. O cancelamento rejeita com `DOMException`/`AbortError` — nunca um
 * `ApiError` — porque parar a pedido do usuário não é falha.
 */
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
          resolve(JSON.parse(xhr.responseText) as IngestResponse);
        } catch {
          reject(clientError(
            'CLIENT_RESPONSE_INVALID',
            xhr.getResponseHeader('X-Trace-ID') ?? undefined,
          ));
        }
        return;
      }
      reject(apiErrorFromXhr(xhr));
    };

    xhr.onerror = () => reject(clientError('CLIENT_NETWORK_UNAVAILABLE'));
    xhr.ontimeout = () => reject(clientError('CLIENT_REQUEST_TIMEOUT'));
    xhr.onabort = () => reject(new DOMException('The operation was aborted', 'AbortError'));

    xhr.send(formData);
  });

  return {
    promise,
    abort: () => {
      xhr.abort();
    },
  };
}
