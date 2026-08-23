/**
 * Leitura, normalização e validação do registry de problemas.
 *
 * A validação tem duas camadas: o JSON Schema publicado em
 * `docs/contracts/problems/catalog.schema.json` cobre estrutura e campos
 * condicionais; as regras semânticas que um schema não expressa (derivação
 * UUIDv5, unicidade, ordenação canônica, existência da extensão e coerência de
 * depreciação) ficam aqui.
 */
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

import { deriveProblemUrn, isUuid } from './uuid-v5.js';

const REPO_ROOT = fileURLToPath(new URL('../..', import.meta.url));
const CONTRACTS_DIR = path.join(REPO_ROOT, 'docs', 'contracts', 'problems');

export const DEFAULT_PATHS = {
  repoRoot: REPO_ROOT,
  contractsDir: CONTRACTS_DIR,
  catalog: path.join(CONTRACTS_DIR, 'catalog.json'),
  schema: path.join(CONTRACTS_DIR, 'catalog.schema.json'),
  lock: path.join(CONTRACTS_DIR, 'catalog.lock.json'),
  markdown: path.join(CONTRACTS_DIR, 'catalog.md'),
  extensionsDir: path.join(CONTRACTS_DIR, 'extensions'),
  javaDir: path.join(
    REPO_ROOT,
    'apps/backend/src/main/java/dev/blackice/shared/api/problem/generated',
  ),
  typescriptDir: path.join(REPO_ROOT, 'apps/frontend/src/shared/api/problems'),
};

/** Schema publicado, carregado uma única vez por processo. */
export const PUBLISHED_SCHEMA = JSON.parse(await readFile(DEFAULT_PATHS.schema, 'utf8'));

export const ROOT_KEY_ORDER = ['schemaVersion', 'namespaceUuid', 'owners', 'entries'];

const API_KEY_ORDER = [
  'type',
  'code',
  'scope',
  'description',
  'httpStatus',
  'title',
  'detail',
  'retryPolicy',
  'owner',
  'extensionsSchemaRef',
  'status',
  'replacedBy',
];

const CLIENT_KEY_ORDER = API_KEY_ORDER.filter(
  (key) => !['httpStatus', 'title', 'detail'].includes(key),
);

export const SCHEMA_VERSION = 1;
export const OWNERS = ['platform', 'security', 'ingest', 'worklist', 'frontend'];
export const RETRY_POLICIES = ['NEVER', 'MANUAL'];
export const SCOPES = ['API', 'CLIENT'];

/** Serialização canônica: chaves ordenadas, dois espaços, LF, newline final. */
export function canonicalJson(value) {
  return `${JSON.stringify(sortDeep(value), null, 2)}\n`;
}

function sortDeep(value) {
  if (Array.isArray(value)) return value.map(sortDeep);
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(
    Object.keys(value)
      .sort()
      .map((key) => [key, sortDeep(value[key])]),
  );
}

export function fingerprint(value) {
  return `sha256:${createHash('sha256').update(canonicalJson(value), 'utf8').digest('hex')}`;
}

/** Serializa o catálogo preservando a ordem de chaves definida pelo contrato. */
export function serializeCatalog(catalog) {
  return `${JSON.stringify(normalizeCatalog(catalog), null, 2)}\n`;
}

export function compareCodes(a, b) {
  return a < b ? -1 : a > b ? 1 : 0;
}

function orderKeys(entry, order) {
  const ordered = {};
  for (const key of order) {
    if (Object.hasOwn(entry, key)) ordered[key] = entry[key];
  }
  for (const key of Object.keys(entry)) {
    if (!Object.hasOwn(ordered, key)) ordered[key] = entry[key];
  }
  return ordered;
}

export function normalizeEntry(entry) {
  return orderKeys(entry, entry.scope === 'CLIENT' ? CLIENT_KEY_ORDER : API_KEY_ORDER);
}

/** Ordena entradas por code e fixa a ordem das chaves. Não valida. */
export function normalizeCatalog(catalog) {
  const entries = [...(catalog.entries ?? [])]
    .map(normalizeEntry)
    .sort((a, b) => compareCodes(a.code, b.code));
  return orderKeys({ ...catalog, entries }, ROOT_KEY_ORDER);
}

export async function loadCatalog(catalogPath = DEFAULT_PATHS.catalog) {
  return JSON.parse(await readFile(catalogPath, 'utf8'));
}

export async function loadSchema(schemaPath = DEFAULT_PATHS.schema) {
  return JSON.parse(await readFile(schemaPath, 'utf8'));
}

/**
 * Carrega os schemas de extensão publicados.
 * @returns {Promise<Record<string, {ref: string, schema: object, fingerprint: string}>>}
 */
export async function loadExtensionSchemas(extensionsDir = DEFAULT_PATHS.extensionsDir) {
  const files = (await readdir(extensionsDir))
    .filter((name) => name.endsWith('.schema.json'))
    .sort();

  const schemas = {};
  for (const file of files) {
    const ref = file.replace(/\.schema\.json$/, '');
    const schema = JSON.parse(await readFile(path.join(extensionsDir, file), 'utf8'));
    schemas[ref] = { ref, schema, fingerprint: fingerprint(schema) };
  }
  return schemas;
}

const validatorCache = new WeakMap();

function structuralValidator(schema) {
  let validate = validatorCache.get(schema);
  if (validate === undefined) {
    const ajv = new Ajv2020({ allErrors: true, strict: true });
    addFormats(ajv);
    validate = ajv.compile(schema);
    validatorCache.set(schema, validate);
  }
  return validate;
}

/**
 * Valida estrutura e semântica do catálogo.
 *
 * @param {object} catalog
 * @param {Record<string, {fingerprint: string}>} [extensionSchemas]
 *   Quando omitido, a existência de `extensionsSchemaRef` não é verificada.
 * @param {object} [schema] JSON Schema alternativo; por padrão usa o publicado.
 * @returns {{ok: boolean, errors: string[]}}
 */
export function validateCatalog(catalog, extensionSchemas, schema = PUBLISHED_SCHEMA) {
  const errors = [];
  const validate = structuralValidator(schema);
  if (!validate(catalog)) {
    for (const error of validate.errors ?? []) {
      errors.push(`schema ${error.instancePath || '/'}: ${error.message}`);
    }
  }

  errors.push(...semanticErrors(catalog, extensionSchemas));
  return { ok: errors.length === 0, errors };
}

function semanticErrors(catalog, extensionSchemas) {
  const errors = [];
  const entries = catalog?.entries;
  if (!Array.isArray(entries)) return errors;

  const namespace = catalog.namespaceUuid;
  const byCode = new Map();
  const seenCodes = new Set();
  const seenTypes = new Map();

  for (const entry of entries) {
    if (typeof entry?.code === 'string') byCode.set(entry.code, entry);
  }

  for (const entry of entries) {
    const at = `entry ${entry?.code ?? '<sem code>'}`;

    if (typeof entry?.code !== 'string') continue;

    if (seenCodes.has(entry.code)) {
      errors.push(`${at}: code duplicado`);
    }
    seenCodes.add(entry.code);

    if (typeof entry.type === 'string') {
      const owner = seenTypes.get(entry.type);
      if (owner !== undefined) {
        errors.push(`${at}: type duplicado, já usado por ${owner}`);
      }
      seenTypes.set(entry.type, entry.code);
    }

    if (isUuid(namespace) && typeof entry.type === 'string') {
      const expected = deriveProblemUrn(namespace, entry.code);
      if (entry.type !== expected) {
        errors.push(`${at}: type não corresponde ao UUIDv5 derivado do code (esperado ${expected})`);
      }
    }

    if (Array.isArray(catalog.owners) && !catalog.owners.includes(entry.owner)) {
      errors.push(`${at}: owner "${entry.owner}" não está declarado em owners`);
    }

    if (entry.extensionsSchemaRef !== null && extensionSchemas !== undefined) {
      if (!Object.hasOwn(extensionSchemas, entry.extensionsSchemaRef)) {
        errors.push(`${at}: extensionsSchemaRef "${entry.extensionsSchemaRef}" não existe`);
      }
    }

    if (entry.status === 'active' && entry.replacedBy !== null) {
      errors.push(`${at}: replacedBy deve ser null enquanto a entrada está ativa`);
    }

    if (entry.status === 'deprecated' && entry.replacedBy !== null) {
      const replacement = byCode.get(entry.replacedBy);
      if (replacement === undefined) {
        errors.push(`${at}: replacedBy "${entry.replacedBy}" não existe no catálogo`);
      } else if (replacement.status !== 'active') {
        errors.push(`${at}: replacedBy "${entry.replacedBy}" não está ativo`);
      } else if (replacement.code === entry.code) {
        errors.push(`${at}: replacedBy aponta para a própria entrada`);
      }
    }
  }

  const codes = entries.map((entry) => entry?.code).filter((code) => typeof code === 'string');
  const sorted = [...codes].sort(compareCodes);
  if (codes.join(' ') !== sorted.join(' ')) {
    errors.push('entries fora da ordenação canônica por code');
  }

  return errors;
}
