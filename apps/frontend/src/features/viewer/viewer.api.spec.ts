import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchStudyViewer, fetchSeriesInstances } from '@/features/viewer/viewer.api';
import { ApiError } from '@/shared/api/problems/api-error';
import { PROBLEM_TYPES } from '@/shared/api/problems/problem-types.generated';
import type { StudyViewerSummary, ViewerSeriesInstances } from '@/features/viewer/viewer.types';

afterEach(() => {
  vi.restoreAllMocks();
});

const STUDY_UID = '1.2.840.113619.2.55.3.604688435.123.1599720123.467';
const SERIES_UID = '1.2.840.113619.2.55.3.604688435.124';
const SOP_UID = '1.2.840.113619.2.55.3.604688435.126';
const TRACE_ID = '4bf92f3577b34da6a3ce929d0e0e4736';

const sampleStudySummary: StudyViewerSummary = {
  studyInstanceUid: STUDY_UID,
  patientName: 'SILVA^MARIA',
  patientId: '123',
  patientIdIssuer: 'HOSPITAL-A',
  studyDate: '2026-08-22',
  studyTime: '10:35:12',
  description: 'CT CHEST',
  series: [
    {
      seriesInstanceUid: SERIES_UID,
      seriesNumber: 2,
      modality: 'CT',
      description: 'AXIAL',
      instanceCount: 187,
      availability: 'SUPPORTED',
      unsupportedReason: null,
    },
    {
      seriesInstanceUid: '1.2.840.113619.2.55.3.604688435.125',
      seriesNumber: 900,
      modality: 'SR',
      description: 'REPORT',
      instanceCount: 1,
      availability: 'UNSUPPORTED',
      unsupportedReason: 'NON_IMAGE_OBJECT',
    },
  ],
};

const sampleSeriesInstances: ViewerSeriesInstances = {
  studyInstanceUid: STUDY_UID,
  seriesInstanceUid: SERIES_UID,
  instances: [
    {
      sopInstanceUid: SOP_UID,
      sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
      instanceNumber: 1,
      rows: 512,
      columns: 512,
      samplesPerPixel: 1,
      photometricInterpretation: 'MONOCHROME2',
      bitsAllocated: 16,
      bitsStored: 12,
      highBit: 11,
      pixelRepresentation: 1,
      planarConfiguration: null,
      imagePositionPatient: [-127.5, -127.5, -100.0],
      imageOrientationPatient: [1, 0, 0, 0, 1, 0],
      pixelSpacing: [0.5, 0.5],
      frameOfReferenceUid: '1.2.840.113619.2.55.3.604688435.127',
      rescaleIntercept: -1024,
      rescaleSlope: 1,
      windowCenter: [40],
      windowWidth: [400],
    },
  ],
};

function jsonResponse(body: unknown, status = 200, traceId?: string): Response {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (traceId) headers['X-Trace-ID'] = traceId;
  return new Response(JSON.stringify(body), { status, headers });
}

function errorResponse(code: keyof typeof PROBLEM_TYPES, traceId = TRACE_ID): Response {
  const definition = PROBLEM_TYPES[code];
  const status = 'httpStatus' in definition ? definition.httpStatus : 500;
  return new Response(
    JSON.stringify({
      type: definition.type,
      title: 'Erro de teste',
      status,
      detail: 'Detalhe do erro',
      code,
      traceId,
    }),
    {
      status,
      headers: { 'Content-Type': 'application/problem+json', 'X-Trace-ID': traceId },
    },
  );
}

function textResponse(status: number, text: string): Response {
  return new Response(text, {
    status,
    headers: { 'Content-Type': 'text/plain' },
  });
}

describe('fetchStudyViewer', () => {
  it('envia GET para /api/studies/{studyUid} com credentials: include e signal', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(sampleStudySummary));
    const controller = new AbortController();

    const result = await fetchStudyViewer(STUDY_UID, controller.signal, fetchFn);

    expect(fetchFn).toHaveBeenCalledWith(
      `/api/studies/${STUDY_UID}`,
      { credentials: 'include', signal: controller.signal },
    );
    expect(result).toEqual(sampleStudySummary);
  });

  it('usa global fetch quando fetchFn não é fornecido', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(jsonResponse(sampleStudySummary));
    vi.stubGlobal('fetch', fetchSpy);

    const result = await fetchStudyViewer(STUDY_UID);

    expect(fetchSpy).toHaveBeenCalledWith(
      `/api/studies/${STUDY_UID}`,
      { credentials: 'include', signal: undefined },
    );
    expect(result).toEqual(sampleStudySummary);
  });

  it.each([
    ['API_RESOURCE_NOT_FOUND', 404],
    ['API_AUTHENTICATION_REQUIRED', 401],
    ['API_ACCESS_DENIED', 403],
    ['API_ARCHIVE_UNAVAILABLE', 503],
    ['API_ARCHIVE_RESPONSE_INVALID', 502],
    ['API_INTERNAL_ERROR', 500],
  ] as const)('traduz resposta %s em ApiError catalogado', async (code, status) => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(code));

    await expect(fetchStudyViewer(STUDY_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code, status, scope: 'API', traceId: TRACE_ID });
  });

  it('lança ApiError cuja message é apenas o código catalogado', async () => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse('API_RESOURCE_NOT_FOUND'));

    const promise = fetchStudyViewer(STUDY_UID, undefined, fetchFn);

    await expect(promise).rejects.toThrow(ApiError);
    await expect(promise).rejects.toSatisfy((err: unknown) => {
      const error = err as ApiError;
      expect(error.name).toBe('ApiError');
      expect(error.message).toBe('API_RESOURCE_NOT_FOUND');
      expect(error.message).not.toContain(STUDY_UID);
      return true;
    });
  });

  it('traduz resposta não-JSON ou corpo inválido em CLIENT_RESPONSE_INVALID', async () => {
    const fetchFn = vi.fn().mockResolvedValue(textResponse(500, '<html>500 Server Error</html>'));

    await expect(fetchStudyViewer(STUDY_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it('traduz JSON malformado em CLIENT_RESPONSE_INVALID', async () => {
    const fetchFn = vi.fn().mockResolvedValue(
      new Response('invalid json{', {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(fetchStudyViewer(STUDY_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it.each([
    ['payload nulo', null],
    ['payload não-objeto', 'string'],
    ['studyInstanceUid ausente', { ...sampleStudySummary, studyInstanceUid: undefined }],
    ['studyInstanceUid vazio', { ...sampleStudySummary, studyInstanceUid: '' }],
    ['series não é array', { ...sampleStudySummary, series: 'not-array' }],
    ['series com availability inválido', {
      ...sampleStudySummary,
      series: [{ ...sampleStudySummary.series[0], availability: 'UNKNOWN' }],
    }],
    ['series com SUPPORTED mas unsupportedReason preenchido', {
      ...sampleStudySummary,
      series: [{ ...sampleStudySummary.series[0], availability: 'SUPPORTED', unsupportedReason: 'MULTI_FRAME' }],
    }],
    ['series com UNSUPPORTED mas unsupportedReason nulo', {
      ...sampleStudySummary,
      series: [{ ...sampleStudySummary.series[0], availability: 'UNSUPPORTED', unsupportedReason: null }],
    }],
    ['series com instanceCount negativo', {
      ...sampleStudySummary,
      series: [{ ...sampleStudySummary.series[0], instanceCount: -1 }],
    }],
  ])('rejeita DTO com violação de schema (%s) como CLIENT_RESPONSE_INVALID', async (_, invalidPayload) => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(invalidPayload));

    await expect(fetchStudyViewer(STUDY_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it('traduz falha de rede em CLIENT_NETWORK_UNAVAILABLE', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(fetchStudyViewer(STUDY_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE', scope: 'CLIENT' });
  });

  it('propaga cancelamento via AbortSignal sem alterar o erro', async () => {
    const controller = new AbortController();
    controller.abort();
    const abortError = new DOMException('The user aborted a request.', 'AbortError');
    const fetchFn = vi.fn().mockRejectedValue(abortError);

    await expect(fetchStudyViewer(STUDY_UID, controller.signal, fetchFn)).rejects.toThrow('The user aborted a request.');
  });
});

describe('fetchSeriesInstances', () => {
  it('envia GET para /api/studies/{studyUid}/series/{seriesUid}/instances com credentials e signal', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(sampleSeriesInstances));
    const controller = new AbortController();

    const result = await fetchSeriesInstances(STUDY_UID, SERIES_UID, controller.signal, fetchFn);

    expect(fetchFn).toHaveBeenCalledWith(
      `/api/studies/${STUDY_UID}/series/${SERIES_UID}/instances`,
      { credentials: 'include', signal: controller.signal },
    );
    expect(result).toEqual(sampleSeriesInstances);
  });

  it('usa global fetch quando fetchFn não é fornecido', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(jsonResponse(sampleSeriesInstances));
    vi.stubGlobal('fetch', fetchSpy);

    const result = await fetchSeriesInstances(STUDY_UID, SERIES_UID);

    expect(fetchSpy).toHaveBeenCalledWith(
      `/api/studies/${STUDY_UID}/series/${SERIES_UID}/instances`,
      { credentials: 'include', signal: undefined },
    );
    expect(result).toEqual(sampleSeriesInstances);
  });

  it.each([
    ['API_RESOURCE_NOT_FOUND', 404],
    ['API_AUTHENTICATION_REQUIRED', 401],
    ['API_ACCESS_DENIED', 403],
    ['API_ARCHIVE_UNAVAILABLE', 503],
    ['API_ARCHIVE_RESPONSE_INVALID', 502],
  ] as const)('traduz resposta %s em ApiError catalogado', async (code, status) => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse(code));

    await expect(fetchSeriesInstances(STUDY_UID, SERIES_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code, status, scope: 'API', traceId: TRACE_ID });
  });

  it('lança ApiError sem vazar UIDs na mensagem', async () => {
    const fetchFn = vi.fn().mockResolvedValue(errorResponse('API_RESOURCE_NOT_FOUND'));

    const promise = fetchSeriesInstances(STUDY_UID, SERIES_UID, undefined, fetchFn);

    await expect(promise).rejects.toThrow(ApiError);
    await expect(promise).rejects.toSatisfy((err: unknown) => {
      const error = err as ApiError;
      expect(error.message).toBe('API_RESOURCE_NOT_FOUND');
      expect(error.message).not.toContain(STUDY_UID);
      expect(error.message).not.toContain(SERIES_UID);
      return true;
    });
  });

  it.each([
    ['payload nulo', null],
    ['studyInstanceUid ausente', { ...sampleSeriesInstances, studyInstanceUid: undefined }],
    ['seriesInstanceUid ausente', { ...sampleSeriesInstances, seriesInstanceUid: '' }],
    ['instances não é array', { ...sampleSeriesInstances, instances: 'not-an-array' }],
    ['sopInstanceUid ausente na instância', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], sopInstanceUid: '' }],
    }],
    ['sopClassUid ausente na instância', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], sopClassUid: '' }],
    }],
    ['rows inválido (zero ou negativo)', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], rows: 0 }],
    }],
    ['columns inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], columns: -10 }],
    }],
    ['samplesPerPixel inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], samplesPerPixel: 0 }],
    }],
    ['photometricInterpretation vazio', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], photometricInterpretation: '' }],
    }],
    ['bitsAllocated inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], bitsAllocated: 0 }],
    }],
    ['bitsStored inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], bitsStored: 0 }],
    }],
    ['highBit negativo', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], highBit: -1 }],
    }],
    ['pixelRepresentation inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], pixelRepresentation: 2 }],
    }],
    ['planarConfiguration ausente quando samplesPerPixel > 1', {
      ...sampleSeriesInstances,
      instances: [{
        ...sampleSeriesInstances.instances[0],
        samplesPerPixel: 3,
        planarConfiguration: null,
      }],
    }],
    ['imagePositionPatient tamanho inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], imagePositionPatient: [1, 2] }],
    }],
    ['imageOrientationPatient tamanho inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], imageOrientationPatient: [1, 2, 3, 4, 5] }],
    }],
    ['pixelSpacing tamanho inválido', {
      ...sampleSeriesInstances,
      instances: [{ ...sampleSeriesInstances.instances[0], pixelSpacing: [1] }],
    }],
  ])('rejeita DTO de instâncias com violação de schema (%s) como CLIENT_RESPONSE_INVALID', async (_, invalidPayload) => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(invalidPayload));

    await expect(fetchSeriesInstances(STUDY_UID, SERIES_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_RESPONSE_INVALID', scope: 'CLIENT' });
  });

  it('traduz falha de rede em CLIENT_NETWORK_UNAVAILABLE', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Network offline'));

    await expect(fetchSeriesInstances(STUDY_UID, SERIES_UID, undefined, fetchFn))
      .rejects.toMatchObject({ code: 'CLIENT_NETWORK_UNAVAILABLE', scope: 'CLIENT' });
  });

  it('propaga cancelamento via AbortSignal', async () => {
    const controller = new AbortController();
    controller.abort();
    const abortError = new DOMException('The user aborted a request.', 'AbortError');
    const fetchFn = vi.fn().mockRejectedValue(abortError);

    await expect(fetchSeriesInstances(STUDY_UID, SERIES_UID, controller.signal, fetchFn)).rejects.toThrow('The user aborted a request.');
  });
});
