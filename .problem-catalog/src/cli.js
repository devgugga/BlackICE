#!/usr/bin/env node
/**
 * CLI não interativa do catálogo de problemas.
 *
 *   check       valida catálogo, lock e artefatos sem escrever
 *   add         acrescenta uma entrada aprovada e deriva a URN
 *   deprecate   deprecia uma entrada e registra a substituição
 *
 * Nenhum comando aceita UUID por parâmetro: a identidade é sempre derivada do
 * code dentro do namespace persistido no catálogo.
 */
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { parseArgs } from 'node:util';

import {
  DEFAULT_PATHS,
  loadExtensionSchemas,
  loadSchema,
  normalizeCatalog,
  serializeCatalog,
  validateCatalog,
} from './catalog.js';
import { compareLock, createLock, serializeLock } from './lock.js';
import { deriveProblemUrn } from './uuid-v5.js';

const FORBIDDEN_FLAGS = ['--type', '--uuid', '--urn', '--id', '--namespace-uuid', '--namespace'];

class UsageError extends Error {}
class ViolationError extends Error {
  constructor(errors) {
    super(errors.join('\n'));
    this.errors = errors;
  }
}

const PATH_OPTIONS = {
  catalog: { type: 'string' },
  schema: { type: 'string' },
  lock: { type: 'string' },
  extensions: { type: 'string' },
};

function resolvePaths(values) {
  return {
    catalog: values.catalog ? path.resolve(values.catalog) : DEFAULT_PATHS.catalog,
    schema: values.schema ? path.resolve(values.schema) : DEFAULT_PATHS.schema,
    lock: values.lock ? path.resolve(values.lock) : DEFAULT_PATHS.lock,
    extensionsDir: values.extensions ? path.resolve(values.extensions) : DEFAULT_PATHS.extensionsDir,
  };
}

async function readJsonIfPresent(filePath) {
  try {
    return JSON.parse(await readFile(filePath, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    throw error;
  }
}

/** Carrega catálogo, schema, extensões e lock a partir de um conjunto de paths. */
export async function loadWorkspace(paths) {
  const [catalog, schema, extensionSchemas, lock] = await Promise.all([
    readJsonIfPresent(paths.catalog),
    loadSchema(paths.schema),
    loadExtensionSchemas(paths.extensionsDir),
    readJsonIfPresent(paths.lock),
  ]);
  return { catalog, schema, extensionSchemas, lock, paths };
}

/**
 * Valida catálogo, formatação canônica e lock. Nunca escreve.
 * @returns {Promise<{ok: boolean, errors: string[], changedPaths: string[]}>}
 */
export async function checkWorkspace(paths) {
  const { catalog, schema, extensionSchemas, lock } = await loadWorkspace(paths);
  const errors = [];
  const changedPaths = [];

  if (catalog === null) {
    return { ok: false, errors: [`catálogo ausente em ${paths.catalog}`], changedPaths };
  }

  errors.push(...validateCatalog(catalog, extensionSchemas, schema).errors);

  const canonical = serializeCatalog(catalog);
  if (canonical !== (await readFile(paths.catalog, 'utf8'))) {
    errors.push('catalog.json não está na forma canônica; rode generate');
    changedPaths.push(paths.catalog);
  }

  if (lock === null) {
    errors.push(`lock ausente em ${paths.lock}; rode generate`);
    changedPaths.push(paths.lock);
  } else {
    errors.push(...compareLock(lock, catalog, extensionSchemas).errors);
    if (serializeLock(createLock(catalog, extensionSchemas)) !== (await readFile(paths.lock, 'utf8'))) {
      errors.push('catalog.lock.json divergente do catálogo; rode generate');
      changedPaths.push(paths.lock);
    }
  }

  return { ok: errors.length === 0, errors, changedPaths };
}

function requireValue(values, flag, command) {
  const value = values[flag];
  if (value === undefined || value === '') {
    throw new UsageError(`${command}: --${flag} é obrigatório`);
  }
  return value;
}

function buildEntry(values, namespaceUuid) {
  const code = requireValue(values, 'code', 'add');
  const scope = requireValue(values, 'scope', 'add');

  if (scope !== 'API' && scope !== 'CLIENT') {
    throw new UsageError('add: --scope deve ser API ou CLIENT');
  }

  const common = {
    type: deriveProblemUrn(namespaceUuid, code),
    code,
    scope,
    description: requireValue(values, 'description', 'add'),
  };

  const tail = {
    retryPolicy: requireValue(values, 'retry-policy', 'add'),
    owner: requireValue(values, 'owner', 'add'),
    extensionsSchemaRef: values['extensions-schema-ref'] ?? null,
    status: 'active',
    replacedBy: null,
  };

  if (scope === 'CLIENT') {
    for (const flag of ['http-status', 'title', 'detail']) {
      if (values[flag] !== undefined) {
        throw new UsageError(`add: --${flag} não se aplica a scope CLIENT`);
      }
    }
    return { ...common, ...tail };
  }

  const httpStatus = Number(requireValue(values, 'http-status', 'add'));
  if (!Number.isInteger(httpStatus)) {
    throw new UsageError('add: --http-status deve ser um inteiro');
  }

  return {
    ...common,
    httpStatus,
    title: requireValue(values, 'title', 'add'),
    detail: requireValue(values, 'detail', 'add'),
    ...tail,
  };
}

async function persist(paths, catalog, extensionSchemas, schema) {
  const normalized = normalizeCatalog(catalog);

  const { errors } = validateCatalog(normalized, extensionSchemas, schema);
  if (errors.length > 0) throw new ViolationError(errors);

  const previousLock = await readJsonIfPresent(paths.lock);
  if (previousLock !== null) {
    const comparison = compareLock(previousLock, normalized, extensionSchemas);
    if (!comparison.ok) throw new ViolationError(comparison.errors);
  }

  await writeFile(paths.catalog, serializeCatalog(normalized), 'utf8');
  return normalized;
}

export async function addEntry(paths, values) {
  const { catalog, schema, extensionSchemas } = await loadWorkspace(paths);

  if (catalog === null) {
    throw new UsageError(`add: catálogo ausente em ${paths.catalog}; rode generate para inicializá-lo`);
  }
  if (catalog.entries.some((entry) => entry.code === values.code)) {
    throw new ViolationError([`add: o code ${values.code} já existe; reutilize a entrada publicada`]);
  }

  const entry = buildEntry(values, catalog.namespaceUuid);
  const next = { ...catalog, entries: [...catalog.entries, entry] };
  await persist(paths, next, extensionSchemas, schema);
  return entry;
}

export async function deprecateEntry(paths, values) {
  const { catalog, schema, extensionSchemas } = await loadWorkspace(paths);

  if (catalog === null) {
    throw new UsageError(`deprecate: catálogo ausente em ${paths.catalog}`);
  }

  const code = requireValue(values, 'code', 'deprecate');
  const replacedBy = values['replaced-by'] ?? null;

  const target = catalog.entries.find((entry) => entry.code === code);
  if (target === undefined) {
    throw new ViolationError([`deprecate: o code ${code} não existe no catálogo`]);
  }
  if (target.status === 'deprecated') {
    throw new ViolationError([`deprecate: o code ${code} já está depreciado`]);
  }

  if (replacedBy !== null) {
    const replacement = catalog.entries.find((entry) => entry.code === replacedBy);
    if (replacement === undefined) {
      throw new ViolationError([`deprecate: o substituto ${replacedBy} não existe no catálogo`]);
    }
    if (replacement.status !== 'active') {
      throw new ViolationError([`deprecate: o substituto ${replacedBy} não está ativo`]);
    }
    if (replacedBy === code) {
      throw new ViolationError(['deprecate: uma entrada não substitui a si mesma']);
    }
  }

  const next = {
    ...catalog,
    entries: catalog.entries.map((entry) =>
      entry.code === code ? { ...entry, status: 'deprecated', replacedBy } : entry,
    ),
  };
  await persist(paths, next, extensionSchemas, schema);
  return next.entries.find((entry) => entry.code === code);
}

const COMMANDS = {
  check: {
    options: { ...PATH_OPTIONS },
    async run(paths) {
      const result = await checkWorkspace(paths);
      if (!result.ok) throw new ViolationError(result.errors);
      console.log('catálogo, lock e artefatos gerados estão consistentes');
    },
  },
  add: {
    options: {
      ...PATH_OPTIONS,
      code: { type: 'string' },
      scope: { type: 'string' },
      description: { type: 'string' },
      'http-status': { type: 'string' },
      title: { type: 'string' },
      detail: { type: 'string' },
      'retry-policy': { type: 'string' },
      owner: { type: 'string' },
      'extensions-schema-ref': { type: 'string' },
    },
    async run(paths, values) {
      const entry = await addEntry(paths, values);
      console.log(`${entry.code} ${entry.type}`);
    },
  },
  deprecate: {
    options: { ...PATH_OPTIONS, code: { type: 'string' }, 'replaced-by': { type: 'string' } },
    async run(paths, values) {
      const entry = await deprecateEntry(paths, values);
      console.log(`${entry.code} depreciado, substituído por ${entry.replacedBy ?? '—'}`);
    },
  },
};

async function main(argv) {
  const [name, ...rest] = argv;

  if (name === undefined || !Object.hasOwn(COMMANDS, name)) {
    throw new UsageError(`uso: cli.js <${Object.keys(COMMANDS).join('|')}> [opções]`);
  }

  for (const token of rest) {
    const flag = token.split('=', 1)[0];
    if (FORBIDDEN_FLAGS.includes(flag)) {
      throw new UsageError(
        `${name}: ${flag} não é aceito; a identidade é derivada mecanicamente do code`,
      );
    }
  }

  const command = COMMANDS[name];
  const { values } = parseArgs({ args: rest, options: command.options, strict: true });
  await command.run(resolvePaths(values), values);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    await main(process.argv.slice(2));
  } catch (error) {
    if (error instanceof UsageError) {
      console.error(error.message);
      process.exit(2);
    }
    if (error instanceof ViolationError) {
      for (const message of error.errors) console.error(`- ${message}`);
      process.exit(1);
    }
    throw error;
  }
}

export { COMMANDS, FORBIDDEN_FLAGS, UsageError, ViolationError, main, resolvePaths };
