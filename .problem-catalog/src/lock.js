/**
 * Lock append-only do catálogo.
 *
 * O lock congela os campos que a spec declara imutáveis depois da publicação —
 * `type`, `code`, `scope`, `httpStatus`, `retryPolicy` e a fingerprint do
 * schema de extensões — mais o `status`, para que a única transição aceita seja
 * `active -> deprecated`. Correções editoriais de `description`, `owner`,
 * `title` e `detail` não passam pelo lock e continuam livres.
 */
import { compareCodes, fingerprint } from './catalog.js';

export const LOCK_ENTRY_KEY_ORDER = [
  'code',
  'type',
  'scope',
  'httpStatus',
  'retryPolicy',
  'extensionsSchemaRef',
  'extensionsFingerprint',
  'status',
];

const IMMUTABLE_FIELDS = ['type', 'scope', 'httpStatus', 'retryPolicy', 'extensionsSchemaRef'];

export class LockViolationError extends Error {
  constructor(errors) {
    super(errors.join('\n'));
    this.name = 'LockViolationError';
    this.errors = errors;
  }
}

function extensionFingerprint(ref, extensionSchemas) {
  if (ref === null || ref === undefined) return null;
  return extensionSchemas?.[ref]?.fingerprint ?? null;
}

function lockEntry(entry, extensionSchemas) {
  return {
    code: entry.code,
    type: entry.type,
    scope: entry.scope,
    httpStatus: entry.httpStatus ?? null,
    retryPolicy: entry.retryPolicy,
    extensionsSchemaRef: entry.extensionsSchemaRef ?? null,
    extensionsFingerprint: extensionFingerprint(entry.extensionsSchemaRef, extensionSchemas),
    status: entry.status,
  };
}

/** Snapshot dos campos imutáveis e das fingerprints das extensões. */
export function createLock(catalog, extensionSchemas = {}) {
  const entries = [...(catalog.entries ?? [])]
    .map((entry) => lockEntry(entry, extensionSchemas))
    .sort((a, b) => compareCodes(a.code, b.code));

  const extensions = {};
  for (const ref of Object.keys(extensionSchemas).sort()) {
    extensions[ref] = extensionSchemas[ref].fingerprint ?? fingerprint(extensionSchemas[ref].schema);
  }

  return {
    schemaVersion: catalog.schemaVersion,
    namespaceUuid: catalog.namespaceUuid,
    entries,
    extensions,
  };
}

export function assertNoRemovedEntries(previousLock, nextCatalog, errors) {
  const present = new Set((nextCatalog.entries ?? []).map((entry) => entry.code));
  for (const locked of previousLock.entries ?? []) {
    if (!present.has(locked.code)) {
      errors.push(`entry ${locked.code}: removida do catálogo; entradas publicadas nunca são apagadas`);
    }
  }
}

export function assertImmutableFields(previousLock, nextCatalog, extensionSchemas, errors) {
  if (previousLock.namespaceUuid !== nextCatalog.namespaceUuid) {
    errors.push(
      `namespaceUuid alterado de ${previousLock.namespaceUuid} para ${nextCatalog.namespaceUuid}; ` +
        'o namespace é criado uma única vez no bootstrap',
    );
  }

  const byCode = new Map((nextCatalog.entries ?? []).map((entry) => [entry.code, entry]));
  for (const locked of previousLock.entries ?? []) {
    const entry = byCode.get(locked.code);
    if (entry === undefined) continue;

    const current = lockEntry(entry, extensionSchemas);
    for (const field of IMMUTABLE_FIELDS) {
      if (current[field] !== locked[field]) {
        errors.push(
          `entry ${locked.code}: ${field} é imutável após a publicação ` +
            `(lock ${JSON.stringify(locked[field])}, catálogo ${JSON.stringify(current[field])})`,
        );
      }
    }
  }
}

export function assertOnlyActiveToDeprecated(previousLock, nextCatalog, errors) {
  const byCode = new Map((nextCatalog.entries ?? []).map((entry) => [entry.code, entry]));
  for (const locked of previousLock.entries ?? []) {
    const entry = byCode.get(locked.code);
    if (entry === undefined) continue;
    if (locked.status === 'deprecated' && entry.status !== 'deprecated') {
      errors.push(`entry ${locked.code}: status deprecated não é reativado`);
    }
  }
}

export function assertExtensionFingerprints(previousLock, extensionSchemas, errors) {
  for (const [ref, locked] of Object.entries(previousLock.extensions ?? {})) {
    const current = extensionSchemas?.[ref]?.fingerprint;
    if (current === undefined) {
      errors.push(`extensão ${ref}: schema bloqueado no lock não existe mais`);
    } else if (current !== locked) {
      errors.push(
        `extensão ${ref}: fingerprint divergente do lock (lock ${locked}, atual ${current}); ` +
          'mudança semântica de extensão cria um tipo novo',
      );
    }
  }
}

/** Versão read-only: acumula violações em vez de lançar. */
export function compareLock(previousLock, nextCatalog, extensionSchemas = {}) {
  const errors = [];
  assertNoRemovedEntries(previousLock, nextCatalog, errors);
  assertImmutableFields(previousLock, nextCatalog, extensionSchemas, errors);
  assertOnlyActiveToDeprecated(previousLock, nextCatalog, errors);
  assertExtensionFingerprints(previousLock, extensionSchemas, errors);
  return { ok: errors.length === 0, errors };
}

export function assertAllowedTransition(previousLock, nextCatalog, extensionSchemas = {}) {
  const { ok, errors } = compareLock(previousLock, nextCatalog, extensionSchemas);
  if (!ok) throw new LockViolationError(errors);
}

export function serializeLock(lock) {
  const entries = lock.entries.map((entry) => {
    const ordered = {};
    for (const key of LOCK_ENTRY_KEY_ORDER) ordered[key] = entry[key];
    return ordered;
  });
  return `${JSON.stringify({ ...lock, entries }, null, 2)}\n`;
}
