import { apiErrorFromResponse, clientError } from '@/shared/api/problems/parse-problem';
import type {
  SeriesAvailability,
  StudyViewerSummary,
  UnsupportedReason,
  ViewerInstance,
  ViewerSeriesInstances,
  ViewerSeriesSummary,
} from './viewer.types';

const VALID_AVAILABILITIES: ReadonlySet<SeriesAvailability> = new Set(['SUPPORTED', 'UNSUPPORTED']);
const VALID_UNSUPPORTED_REASONS: ReadonlySet<UnsupportedReason> = new Set([
  'MULTI_FRAME',
  'NON_IMAGE_OBJECT',
  'IMAGE_SOP_CLASS_UNSUPPORTED',
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonEmptyString(val: unknown): val is string {
  return typeof val === 'string' && val.trim().length > 0;
}

function isNullableString(val: unknown): val is string | null | undefined {
  return val === null || val === undefined || typeof val === 'string';
}

function isPositiveInteger(val: unknown): val is number {
  return typeof val === 'number' && Number.isInteger(val) && val > 0;
}

function isNonNegativeInteger(val: unknown): val is number {
  return typeof val === 'number' && Number.isInteger(val) && val >= 0;
}

function isNullableInteger(val: unknown): val is number | null | undefined {
  return val === null || val === undefined || (typeof val === 'number' && Number.isInteger(val));
}

function isNullableNumber(val: unknown): val is number | null | undefined {
  return val === null || val === undefined || (typeof val === 'number' && Number.isFinite(val));
}

function isNumberArray(val: unknown, expectedLength?: number): val is number[] {
  if (!Array.isArray(val)) return false;
  if (expectedLength !== undefined && val.length !== expectedLength) return false;
  return val.every((item) => typeof item === 'number' && Number.isFinite(item));
}

function isNullableNumberArray(val: unknown, expectedLength?: number): val is number[] | null | undefined {
  if (val === null || val === undefined) return true;
  return isNumberArray(val, expectedLength);
}

function parseSeriesSummary(raw: unknown): ViewerSeriesSummary {
  if (!isRecord(raw)) throw new Error('Invalid series record');

  if (!isNonEmptyString(raw.seriesInstanceUid)) throw new Error('Invalid seriesInstanceUid');
  if (!isNullableInteger(raw.seriesNumber)) throw new Error('Invalid seriesNumber');
  if (!isNonEmptyString(raw.modality)) throw new Error('Invalid modality');
  if (!isNullableString(raw.description)) throw new Error('Invalid description');
  if (!isNonNegativeInteger(raw.instanceCount)) throw new Error('Invalid instanceCount');

  const availability = raw.availability as SeriesAvailability;
  if (!VALID_AVAILABILITIES.has(availability)) throw new Error('Invalid availability');

  const unsupportedReason = (raw.unsupportedReason ?? null) as UnsupportedReason;
  if (availability === 'SUPPORTED') {
    if (unsupportedReason !== null) throw new Error('Supported series cannot have unsupportedReason');
  } else {
    if (!VALID_UNSUPPORTED_REASONS.has(unsupportedReason)) {
      throw new Error('Unsupported series must have valid unsupportedReason');
    }
  }

  return {
    seriesInstanceUid: raw.seriesInstanceUid,
    seriesNumber: raw.seriesNumber !== undefined ? (raw.seriesNumber as number | null) : null,
    modality: raw.modality,
    description: raw.description !== undefined ? (raw.description as string | null) : null,
    instanceCount: raw.instanceCount,
    availability,
    unsupportedReason,
  };
}

function parseStudyViewerSummary(data: unknown): StudyViewerSummary {
  if (!isRecord(data)) throw new Error('Invalid study summary payload');

  if (!isNonEmptyString(data.studyInstanceUid)) throw new Error('Invalid studyInstanceUid');
  if (!isNullableString(data.patientName)) throw new Error('Invalid patientName');
  if (!isNullableString(data.patientId)) throw new Error('Invalid patientId');
  if (!isNullableString(data.patientIdIssuer)) throw new Error('Invalid patientIdIssuer');
  if (!isNullableString(data.studyDate)) throw new Error('Invalid studyDate');
  if (!isNullableString(data.studyTime)) throw new Error('Invalid studyTime');
  if (!isNullableString(data.description)) throw new Error('Invalid description');
  if (!Array.isArray(data.series)) throw new Error('Invalid series list');

  const series = data.series.map(parseSeriesSummary);

  return {
    studyInstanceUid: data.studyInstanceUid,
    patientName: data.patientName !== undefined ? (data.patientName as string | null) : null,
    patientId: data.patientId !== undefined ? (data.patientId as string | null) : null,
    patientIdIssuer: data.patientIdIssuer !== undefined ? (data.patientIdIssuer as string | null) : null,
    studyDate: data.studyDate !== undefined ? (data.studyDate as string | null) : null,
    studyTime: data.studyTime !== undefined ? (data.studyTime as string | null) : null,
    description: data.description !== undefined ? (data.description as string | null) : null,
    series,
  };
}

function parseViewerInstance(raw: unknown): ViewerInstance {
  if (!isRecord(raw)) throw new Error('Invalid instance record');

  if (!isNonEmptyString(raw.sopInstanceUid)) throw new Error('Invalid sopInstanceUid');
  if (!isNonEmptyString(raw.sopClassUid)) throw new Error('Invalid sopClassUid');
  if (!isNullableInteger(raw.instanceNumber)) throw new Error('Invalid instanceNumber');
  if (!isPositiveInteger(raw.rows)) throw new Error('Invalid rows');
  if (!isPositiveInteger(raw.columns)) throw new Error('Invalid columns');
  if (!isPositiveInteger(raw.samplesPerPixel)) throw new Error('Invalid samplesPerPixel');
  if (!isNonEmptyString(raw.photometricInterpretation)) throw new Error('Invalid photometricInterpretation');
  if (!isPositiveInteger(raw.bitsAllocated)) throw new Error('Invalid bitsAllocated');
  if (!isPositiveInteger(raw.bitsStored)) throw new Error('Invalid bitsStored');
  if (!isNonNegativeInteger(raw.highBit)) throw new Error('Invalid highBit');
  if (raw.pixelRepresentation !== 0 && raw.pixelRepresentation !== 1) throw new Error('Invalid pixelRepresentation');

  if (raw.samplesPerPixel > 1 && (raw.planarConfiguration !== 0 && raw.planarConfiguration !== 1)) {
    throw new Error('PlanarConfiguration is required when samplesPerPixel > 1');
  }
  if (
    raw.planarConfiguration !== null
    && raw.planarConfiguration !== undefined
    && raw.planarConfiguration !== 0
    && raw.planarConfiguration !== 1
  ) {
    throw new Error('Invalid planarConfiguration');
  }

  if (!isNullableNumberArray(raw.imagePositionPatient, 3)) throw new Error('Invalid imagePositionPatient');
  if (!isNullableNumberArray(raw.imageOrientationPatient, 6)) throw new Error('Invalid imageOrientationPatient');
  if (!isNullableNumberArray(raw.pixelSpacing, 2)) throw new Error('Invalid pixelSpacing');
  if (!isNullableString(raw.frameOfReferenceUid)) throw new Error('Invalid frameOfReferenceUid');
  if (!isNullableNumber(raw.rescaleIntercept)) throw new Error('Invalid rescaleIntercept');
  if (!isNullableNumber(raw.rescaleSlope)) throw new Error('Invalid rescaleSlope');
  if (!isNullableNumberArray(raw.windowCenter)) throw new Error('Invalid windowCenter');
  if (!isNullableNumberArray(raw.windowWidth)) throw new Error('Invalid windowWidth');

  return {
    sopInstanceUid: raw.sopInstanceUid,
    sopClassUid: raw.sopClassUid,
    instanceNumber: raw.instanceNumber !== undefined ? (raw.instanceNumber as number | null) : null,
    rows: raw.rows,
    columns: raw.columns,
    samplesPerPixel: raw.samplesPerPixel,
    photometricInterpretation: raw.photometricInterpretation,
    bitsAllocated: raw.bitsAllocated,
    bitsStored: raw.bitsStored,
    highBit: raw.highBit,
    pixelRepresentation: raw.pixelRepresentation,
    planarConfiguration: raw.planarConfiguration !== undefined ? (raw.planarConfiguration as number | null) : null,
    imagePositionPatient: raw.imagePositionPatient !== undefined ? (raw.imagePositionPatient as number[] | null) : null,
    imageOrientationPatient:
      raw.imageOrientationPatient !== undefined ? (raw.imageOrientationPatient as number[] | null) : null,
    pixelSpacing: raw.pixelSpacing !== undefined ? (raw.pixelSpacing as number[] | null) : null,
    frameOfReferenceUid:
      raw.frameOfReferenceUid !== undefined ? (raw.frameOfReferenceUid as string | null) : null,
    rescaleIntercept: raw.rescaleIntercept !== undefined ? (raw.rescaleIntercept as number | null) : null,
    rescaleSlope: raw.rescaleSlope !== undefined ? (raw.rescaleSlope as number | null) : null,
    windowCenter: raw.windowCenter !== undefined ? (raw.windowCenter as number[] | null) : null,
    windowWidth: raw.windowWidth !== undefined ? (raw.windowWidth as number[] | null) : null,
  };
}

function parseViewerSeriesInstances(data: unknown): ViewerSeriesInstances {
  if (!isRecord(data)) throw new Error('Invalid series instances payload');

  if (!isNonEmptyString(data.studyInstanceUid)) throw new Error('Invalid studyInstanceUid');
  if (!isNonEmptyString(data.seriesInstanceUid)) throw new Error('Invalid seriesInstanceUid');
  if (!Array.isArray(data.instances)) throw new Error('Invalid instances list');

  const instances = data.instances.map(parseViewerInstance);

  return {
    studyInstanceUid: data.studyInstanceUid,
    seriesInstanceUid: data.seriesInstanceUid,
    instances,
  };
}

export async function fetchStudyViewer(
  studyUid: string,
  signal?: AbortSignal,
  fetchFn: typeof fetch = fetch,
): Promise<StudyViewerSummary> {
  let response: Response;
  try {
    response = await fetchFn(`/api/studies/${studyUid}`, { credentials: 'include', signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) throw await apiErrorFromResponse(response);

  try {
    const json = await response.json();
    return parseStudyViewerSummary(json);
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}

export async function fetchSeriesInstances(
  studyUid: string,
  seriesUid: string,
  signal?: AbortSignal,
  fetchFn: typeof fetch = fetch,
): Promise<ViewerSeriesInstances> {
  let response: Response;
  try {
    response = await fetchFn(`/api/studies/${studyUid}/series/${seriesUid}/instances`, {
      credentials: 'include',
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw clientError('CLIENT_NETWORK_UNAVAILABLE');
  }

  if (!response.ok) throw await apiErrorFromResponse(response);

  try {
    const json = await response.json();
    return parseViewerSeriesInstances(json);
  } catch {
    throw clientError('CLIENT_RESPONSE_INVALID', response.headers.get('X-Trace-ID') ?? undefined);
  }
}
