import { describe, it, expect, beforeEach, vi } from 'vitest';
import type { LocationQuery } from 'vue-router';
import {
  parseWorklistQuery,
  canonicalWorklistQuery,
  getWorklistCanonicalKey,
  saveWorklistSnapshot,
  restoreWorklistSnapshot,
  type WorklistSnapshot,
} from '@/features/worklist/worklist-navigation';
import type { StudySearchParams, StudyPage } from '@/features/worklist/worklist.types';
import { EMPTY_FILTERS, PAGE_SIZE } from '@/features/worklist/useWorklist';

function createDummyPage(offset = 0): StudyPage {
  return {
    items: [
      {
        studyInstanceUid: '1.2.840.113619.2.55.3.604688435',
        patientName: 'SILVA^JOSE',
        patientId: '123',
        patientIdIssuer: null,
        studyDate: '2026-08-20',
        studyTime: '10:00:00',
        modalities: ['CT'],
        description: 'CHEST',
        seriesCount: 2,
        instanceCount: 100,
      },
    ],
    page: {
      limit: PAGE_SIZE,
      offset,
      hasPrevious: offset > 0,
      hasNext: false,
    },
  };
}

describe('worklist-navigation', () => {
  beforeEach(() => {
    // Reset cache state if needed
  });

  describe('parseWorklistQuery', () => {
    it('retorna parâmetros padrão e offset zero para query vazia', () => {
      const query: LocationQuery = {};
      const result = parseWorklistQuery(query);

      expect(result).toEqual({
        filters: EMPTY_FILTERS,
        limit: 20,
        offset: 0,
      });
    });

    it('faz trim dos campos de texto e ignora campos com apenas espaços', () => {
      const query: LocationQuery = {
        patientName: '  SILVA  ',
        patientId: '   ',
      };
      const result = parseWorklistQuery(query);

      expect(result.filters.patientName).toBe('SILVA');
      expect(result.filters.patientId).toBe('');
    });

    it('trata valores em array selecionando o primeiro elemento', () => {
      const query: LocationQuery = {
        patientName: ['SILVA', 'SANTOS'],
        patientId: ['123', '456'],
      };
      const result = parseWorklistQuery(query);

      expect(result.filters.patientName).toBe('SILVA');
      expect(result.filters.patientId).toBe('123');
    });

    it('valida e normaliza modalidades permitidas em maiúsculas', () => {
      const validCases = ['CT', 'MR', 'US', 'CR', 'DX', 'MG', 'NM', 'PT', 'XA', 'RF', 'OT', 'ct', 'mr'];
      for (const mod of validCases) {
        const query: LocationQuery = { modality: mod };
        const result = parseWorklistQuery(query);
        expect(result.filters.modality).toBe(mod.toUpperCase());
      }
    });

    it('faz fallback de modalidade inválida para string vazia', () => {
      const invalidCases = ['INVALID', '123', 'PET-CT', 'foo'];
      for (const mod of invalidCases) {
        const query: LocationQuery = { modality: mod };
        const result = parseWorklistQuery(query);
        expect(result.filters.modality).toBe('');
      }
    });

    it('valida datas no formato ISO YYYY-MM-DD', () => {
      const query: LocationQuery = {
        dateFrom: '2026-01-01',
        dateTo: '2026-12-31',
      };
      const result = parseWorklistQuery(query);

      expect(result.filters.dateFrom).toBe('2026-01-01');
      expect(result.filters.dateTo).toBe('2026-12-31');
    });

    it('faz fallback de datas inválidas para string vazia', () => {
      const invalidDates = ['invalid', '2026-13-01', '2026-00-10', '2026-02-30', '31/12/2026', '2026'];
      for (const d of invalidDates) {
        const query: LocationQuery = { dateFrom: d, dateTo: d };
        const result = parseWorklistQuery(query);
        expect(result.filters.dateFrom).toBe('');
        expect(result.filters.dateTo).toBe('');
      }
    });

    it('converte offset válido em inteiro não-negativo', () => {
      expect(parseWorklistQuery({ offset: '20' }).offset).toBe(20);
      expect(parseWorklistQuery({ offset: '0' }).offset).toBe(0);
      expect(parseWorklistQuery({ offset: '100' }).offset).toBe(100);
    });

    it('faz fallback de offset inválido ou negativo para zero', () => {
      expect(parseWorklistQuery({ offset: '-20' }).offset).toBe(0);
      expect(parseWorklistQuery({ offset: 'abc' }).offset).toBe(0);
      expect(parseWorklistQuery({ offset: '1.5' }).offset).toBe(0);
      expect(parseWorklistQuery({ offset: 'NaN' }).offset).toBe(0);
      expect(parseWorklistQuery({ offset: '' }).offset).toBe(0);
    });

    it('ignora parâmetros desconhecidos na query', () => {
      const query: LocationQuery = {
        unknownParam: 'value',
        patientName: 'MARIA',
      };
      const result = parseWorklistQuery(query);
      expect(result.filters.patientName).toBe('MARIA');
      expect((result as unknown as Record<string, unknown>).unknownParam).toBeUndefined();
    });

  });

  describe('canonicalWorklistQuery', () => {
    it('retorna objeto vazio quando filtros estão vazios e offset é zero', () => {
      const params: StudySearchParams = {
        filters: EMPTY_FILTERS,
        limit: 20,
        offset: 0,
      };
      const result = canonicalWorklistQuery(params);
      expect(result).toEqual({});
    });

    it('inclui apenas filtros preenchidos após trim', () => {
      const params: StudySearchParams = {
        filters: {
          patientName: '  SILVA  ',
          patientId: '',
          modality: 'CT',
          dateFrom: '2026-01-01',
          dateTo: '   ',
        },
        limit: 20,
        offset: 0,
      };
      const result = canonicalWorklistQuery(params);
      expect(result).toEqual({
        patientName: 'SILVA',
        modality: 'CT',
        dateFrom: '2026-01-01',
      });
    });

    it('inclui offset apenas quando for maior que zero', () => {
      const withZero: StudySearchParams = {
        filters: EMPTY_FILTERS,
        limit: 20,
        offset: 0,
      };
      expect(canonicalWorklistQuery(withZero)).toEqual({});

      const withOffset: StudySearchParams = {
        filters: { ...EMPTY_FILTERS, patientName: 'SILVA' },
        limit: 20,
        offset: 20,
      };
      expect(canonicalWorklistQuery(withOffset)).toEqual({
        patientName: 'SILVA',
        offset: '20',
      });
    });
  });

  describe('getWorklistCanonicalKey', () => {
    it('produz chave estável independente da ordem das propriedades', () => {
      const paramsA: StudySearchParams = {
        filters: { ...EMPTY_FILTERS, patientName: 'SILVA', modality: 'CT' },
        limit: 20,
        offset: 20,
      };
      const paramsB: StudySearchParams = {
        filters: { ...EMPTY_FILTERS, modality: 'CT', patientName: 'SILVA' },
        limit: 20,
        offset: 20,
      };

      expect(getWorklistCanonicalKey(paramsA)).toBe(getWorklistCanonicalKey(paramsB));
      expect(getWorklistCanonicalKey(paramsA)).toBe('modality=CT&offset=20&patientName=SILVA');
    });

    it('produz chave vazia para parâmetros vazios', () => {
      const params: StudySearchParams = {
        filters: EMPTY_FILTERS,
        limit: 20,
        offset: 0,
      };
      expect(getWorklistCanonicalKey(params)).toBe('');
    });
  });

  describe('saveWorklistSnapshot e restoreWorklistSnapshot', () => {
    it('retorna null quando nenhum snapshot foi salvo', () => {
      expect(restoreWorklistSnapshot('some-key-non-existent')).toBeNull();
    });

    it('restaura snapshot quando a chave coincide exatamente', () => {
      const snapshot: WorklistSnapshot = {
        key: 'patientName=SILVA',
        filters: { ...EMPTY_FILTERS, patientName: 'SILVA' },
        page: createDummyPage(0),
      };

      saveWorklistSnapshot(snapshot);
      const restored = restoreWorklistSnapshot('patientName=SILVA');

      expect(restored).not.toBeNull();
      expect(restored?.key).toBe('patientName=SILVA');
      expect(restored?.filters.patientName).toBe('SILVA');
      expect(restored?.page.items).toHaveLength(1);
    });

    it('retorna null se a chave for diferente', () => {
      const snapshot: WorklistSnapshot = {
        key: 'patientName=SILVA',
        filters: { ...EMPTY_FILTERS, patientName: 'SILVA' },
        page: createDummyPage(0),
      };

      saveWorklistSnapshot(snapshot);
      expect(restoreWorklistSnapshot('patientName=OUTRO')).toBeNull();
      expect(restoreWorklistSnapshot('')).toBeNull();
    });

    it('retorna clone estruturado isolado para evitar mutação acidental', () => {
      const snapshot: WorklistSnapshot = {
        key: 'test-clone',
        filters: { ...EMPTY_FILTERS, patientName: 'ORIGINAL' },
        page: createDummyPage(0),
      };

      saveWorklistSnapshot(snapshot);
      const restored = restoreWorklistSnapshot('test-clone');
      expect(restored).not.toBeNull();

      // Mutar o retornado
      (restored!.filters as { patientName: string }).patientName = 'MUTATED';

      const restoredAgain = restoreWorklistSnapshot('test-clone');
      expect(restoredAgain?.filters.patientName).toBe('ORIGINAL');
    });

    it('substitui a entrada anterior ao salvar novo snapshot (armazenamento de 1 entrada em memória)', () => {
      const first: WorklistSnapshot = {
        key: 'first',
        filters: { ...EMPTY_FILTERS, patientName: 'FIRST' },
        page: createDummyPage(0),
      };
      const second: WorklistSnapshot = {
        key: 'second',
        filters: { ...EMPTY_FILTERS, patientName: 'SECOND' },
        page: createDummyPage(20),
      };

      saveWorklistSnapshot(first);
      expect(restoreWorklistSnapshot('first')).not.toBeNull();

      saveWorklistSnapshot(second);
      expect(restoreWorklistSnapshot('first')).toBeNull();
      expect(restoreWorklistSnapshot('second')?.filters.patientName).toBe('SECOND');
    });

    it('mantém o cache estritamente em memória do módulo e nunca acessa localStorage ou sessionStorage', () => {
      const localGetSpy = vi.spyOn(Storage.prototype, 'getItem');
      const localSetSpy = vi.spyOn(Storage.prototype, 'setItem');

      const snapshot: WorklistSnapshot = {
        key: 'memory-only',
        filters: EMPTY_FILTERS,
        page: createDummyPage(0),
      };

      saveWorklistSnapshot(snapshot);
      restoreWorklistSnapshot('memory-only');

      expect(localGetSpy).not.toHaveBeenCalled();
      expect(localSetSpy).not.toHaveBeenCalled();
    });
  });
});
