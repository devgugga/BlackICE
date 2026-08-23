import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import IngestPage from '@/features/ingest/IngestPage.vue';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_MESSAGES } from '@/shared/api/problems/problem-messages.pt-BR';

const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

vi.mock('@/features/ingest/ingest.api', () => ({
  fetchCsrfToken: vi.fn(),
  uploadStudies: vi.fn(),
}));

const { fetchCsrfToken, uploadStudies } = await import('@/features/ingest/ingest.api');
const fetchCsrfTokenMock = vi.mocked(fetchCsrfToken);
const uploadStudiesMock = vi.mocked(uploadStudies);

function file(name: string): File {
  return new File(['conteudo'], name, { type: 'application/dicom' });
}

/** Seleciona arquivos pelo input, como faria o usuário. */
async function selectFiles(wrapper: ReturnType<typeof mount>, files: File[]): Promise<void> {
  const input = wrapper.find('input[type="file"]');
  Object.defineProperty(input.element, 'files', { value: files, configurable: true });
  await input.trigger('change');
  await flushPromises();
}

async function importFailingWith(error: ApiError, names = ['exame.dcm']) {
  fetchCsrfTokenMock.mockResolvedValue('csrf-abc');
  uploadStudiesMock.mockReturnValue({ promise: Promise.reject(error), abort: vi.fn() });

  const wrapper = mount(IngestPage);
  await selectFiles(wrapper, names.map(file));
  await wrapper.findAll('button').find((b) => b.text() === 'Importar')!.trigger('click');
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  fetchCsrfTokenMock.mockReset();
  uploadStudiesMock.mockReset();
});

afterEach(() => vi.clearAllMocks());

describe('IngestPage', () => {
  it('mostra a mensagem central do catálogo, não o detail do backend', async () => {
    const wrapper = await importFailingWith(new ApiError('API_ARCHIVE_UNAVAILABLE'));

    const alert = wrapper.find('[role="alert"]');
    expect(alert.text()).toContain(PROBLEM_MESSAGES.API_ARCHIVE_UNAVAILABLE);
    expect(wrapper.text()).not.toContain('The imaging archive');
  });

  it('associa cada violação ao arquivo local pelo itemIndex', async () => {
    const wrapper = await importFailingWith(
      new ApiError('API_DICOM_VALIDATION_FAILED', {
        violations: [
          { itemIndex: 1, code: 'MALFORMED_DICOM', message: 'The file is not valid DICOM.' },
        ],
      }),
      ['primeiro.dcm', 'segundo.dcm'],
    );

    const violations = wrapper.find('[data-testid="ingest-violations"]');
    expect(violations.text()).toContain('segundo.dcm');
    expect(violations.text()).toContain('The file is not valid DICOM.');
    expect(violations.text()).not.toContain('primeiro.dcm');
  });

  it('mostra o TraceID como referência quando disponível', async () => {
    const wrapper = await importFailingWith(
      new ApiError('API_INTERNAL_ERROR', { traceId: TRACE_ID }),
    );

    const alert = wrapper.find('[role="alert"]');
    expect(alert.text()).toContain('Referência');
    expect(alert.find('code').text()).toBe(TRACE_ID);
  });

  it('oferece retentativa somente quando a política é MANUAL', async () => {
    const manual = await importFailingWith(new ApiError('API_ARCHIVE_UNAVAILABLE'));
    expect(
      manual.find('[role="alert"]').findAll('button').some((b) => b.text() === 'Tentar novamente'),
    ).toBe(true);

    const never = await importFailingWith(new ApiError('API_DICOM_VALIDATION_FAILED'));
    expect(
      never.find('[role="alert"]').findAll('button').some((b) => b.text() === 'Tentar novamente'),
    ).toBe(false);
  });

  it('cancelamento termina em CANCELLED, sem alerta de erro', async () => {
    fetchCsrfTokenMock.mockResolvedValue('csrf-abc');
    let rejectUpload!: (reason: unknown) => void;
    const abort = vi.fn(() => {
      rejectUpload(new DOMException('The operation was aborted', 'AbortError'));
    });
    uploadStudiesMock.mockReturnValue({
      promise: new Promise((_resolve, reject) => {
        rejectUpload = reject;
      }),
      abort,
    });

    const wrapper = mount(IngestPage);
    await selectFiles(wrapper, [file('exame.dcm')]);
    await wrapper.findAll('button').find((b) => b.text() === 'Importar')!.trigger('click');
    await flushPromises();

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click');
    await flushPromises();

    expect(abort).toHaveBeenCalled();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Importação cancelada');
  });
});
