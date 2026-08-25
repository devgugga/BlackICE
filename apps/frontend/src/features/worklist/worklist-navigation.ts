import type { LocationQuery, LocationQueryRaw } from 'vue-router';
import { PAGE_SIZE } from './useWorklist';
import type { StudyPage, StudySearchParams, WorklistFilters } from './worklist.types';


export const ALLOWED_MODALITIES = [
  'CT',
  'MR',
  'US',
  'CR',
  'DX',
  'MG',
  'NM',
  'PT',
  'XA',
  'RF',
  'OT',
] as const;

export type AllowedModality = (typeof ALLOWED_MODALITIES)[number];

export interface WorklistSnapshot {
  readonly key: string;
  readonly filters: WorklistFilters;
  readonly page: StudyPage;
}

let latestSnapshot: WorklistSnapshot | null = null;

function extractFirstString(value: unknown): string {
  if (Array.isArray(value)) {
    const first = value[0];
    return typeof first === 'string' ? first.trim() : '';
  }
  return typeof value === 'string' ? value.trim() : '';
}

function isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parts = value.split('-');
  const year = Number(parts[0]);
  const month = Number(parts[1]);
  const day = Number(parts[2]);
  if (month < 1 || month > 12 || day < 1 || day > 31 || year <= 0) return false;
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

function parseModality(value: unknown): string {
  const str = extractFirstString(value).toUpperCase();
  return ALLOWED_MODALITIES.includes(str as AllowedModality) ? str : '';
}

function parseIsoDate(value: unknown): string {
  const str = extractFirstString(value);
  return isValidIsoDate(str) ? str : '';
}

function parseOffset(value: unknown): number {
  const str = extractFirstString(value);
  if (!/^\d+$/.test(str)) return 0;
  const num = parseInt(str, 10);
  return Number.isSafeInteger(num) && num >= 0 ? num : 0;
}

export function parseWorklistQuery(query: LocationQuery): StudySearchParams {
  const patientName = extractFirstString(query.patientName);
  const patientId = extractFirstString(query.patientId);
  const modality = parseModality(query.modality);
  const dateFrom = parseIsoDate(query.dateFrom);
  const dateTo = parseIsoDate(query.dateTo);
  const offset = parseOffset(query.offset);

  return {
    filters: {
      patientName,
      patientId,
      modality,
      dateFrom,
      dateTo,
    },
    limit: PAGE_SIZE,
    offset,
  };
}

export function canonicalWorklistQuery(params: StudySearchParams): LocationQueryRaw {
  const query: Record<string, string> = {};

  const patientName = params.filters.patientName.trim();
  if (patientName) query.patientName = patientName;

  const patientId = params.filters.patientId.trim();
  if (patientId) query.patientId = patientId;

  const modality = parseModality(params.filters.modality);
  if (modality) query.modality = modality;

  const dateFrom = parseIsoDate(params.filters.dateFrom);
  if (dateFrom) query.dateFrom = dateFrom;

  const dateTo = parseIsoDate(params.filters.dateTo);
  if (dateTo) query.dateTo = dateTo;

  if (params.offset > 0) {
    query.offset = String(params.offset);
  }

  return query;
}

export function getWorklistCanonicalKey(params: StudySearchParams): string {
  const canonical = canonicalWorklistQuery(params);
  const searchParams = new URLSearchParams();
  const sortedKeys = Object.keys(canonical).sort();
  for (const k of sortedKeys) {
    const val = canonical[k];
    if (val !== undefined && val !== null) {
      searchParams.set(k, String(val));
    }
  }
  return searchParams.toString();
}

export function saveWorklistSnapshot(snapshot: WorklistSnapshot): void {
  latestSnapshot = structuredClone({
    key: snapshot.key,
    filters: { ...snapshot.filters },
    page: {
      items: snapshot.page.items.map((it) => ({
        studyInstanceUid: it.studyInstanceUid,
        patientName: it.patientName,
        patientId: it.patientId,
        patientIdIssuer: it.patientIdIssuer,
        studyDate: it.studyDate,
        studyTime: it.studyTime,
        modalities: [...it.modalities],
        description: it.description,
        seriesCount: it.seriesCount,
        instanceCount: it.instanceCount,
      })),
      page: { ...snapshot.page.page },
    },
  });
}

export function restoreWorklistSnapshot(key: string): WorklistSnapshot | null {
  return latestSnapshot?.key === key ? structuredClone(latestSnapshot) : null;
}

export function clearWorklistSnapshot(): void {
  latestSnapshot = null;
}


