// DO NOT EDIT.
//
// Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json e docs/contracts/problems/extensions/.
// Altere o catálogo e execute `pnpm generate` em .problem-catalog/.

import type { ProblemCode } from './problem-types.generated';

/** Valores aceitos em `violations[].code`. */
export const DICOM_VALIDATION_VIOLATION_CODES = [
  'MALFORMED_DICOM',
  'MISSING_STUDY_INSTANCE_UID',
  'MISSING_SERIES_INSTANCE_UID',
  'MISSING_SOP_INSTANCE_UID',
  'MISSING_SOP_CLASS_UID',
  'DUPLICATE_IDENTICAL',
  'SOP_UID_COLLISION',
] as const;

export type DicomValidationViolationCode = (typeof DICOM_VALIDATION_VIOLATION_CODES)[number];

/** Item de `DicomValidationViolations`. */
export interface DicomValidationViolation {
  readonly itemIndex: number;
  readonly code: DicomValidationViolationCode;
  readonly message: string;
}

/** Membros adicionais definidos por extensions/dicom-validation-violations.schema.json. */
export interface DicomValidationViolations {
  readonly violations: readonly DicomValidationViolation[];
}

/** Extensão esperada no nível raiz do Problem Details, por code. */
export type ProblemExtensionsByCode = {
  API_DICOM_VALIDATION_FAILED: DicomValidationViolations;
};

/** Extensão de um code específico; `never` quando o tipo não tem extensão. */
export type ProblemExtensionsFor<C extends ProblemCode> = C extends keyof ProblemExtensionsByCode
  ? ProblemExtensionsByCode[C]
  : never;
