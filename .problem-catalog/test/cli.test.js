import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { DEFAULT_PATHS, loadExtensionSchemas } from '../src/catalog.js';
import { checkWorkspace, main, resolvePaths } from '../src/cli.js';
import { createLock, serializeLock } from '../src/lock.js';

const fixture = fileURLToPath(new URL('fixtures/valid-catalog.json', import.meta.url));
const schemas = await loadExtensionSchemas(DEFAULT_PATHS.extensionsDir);

/** Espaço de trabalho descartável: nenhum teste escreve no repositório. */
async function workspace(t) {
  const dir = await mkdtemp(path.join(tmpdir(), 'problem-catalog-'));
  t.after(() => rm(dir, { recursive: true, force: true }));

  const catalogPath = path.join(dir, 'catalog.json');
  const lockPath = path.join(dir, 'catalog.lock.json');
  const catalog = JSON.parse(await readFile(fixture, 'utf8'));

  await writeFile(catalogPath, `${JSON.stringify(catalog, null, 2)}\n`, 'utf8');
  await writeFile(lockPath, serializeLock(createLock(catalog, schemas)), 'utf8');

  // Todo destino aponta para o diretório temporário: nenhum teste escreve no repositório.
  const overrides = {
    catalog: catalogPath,
    lock: lockPath,
    markdown: path.join(dir, 'catalog.md'),
    java: path.join(dir, 'java'),
    typescript: path.join(dir, 'ts'),
  };
  const flags = [
    '--catalog', catalogPath,
    '--lock', lockPath,
    '--markdown', overrides.markdown,
    '--java', overrides.java,
    '--typescript', overrides.typescript,
    '--schema', DEFAULT_PATHS.schema,
    '--extensions', DEFAULT_PATHS.extensionsDir,
  ];
  const read = async () => JSON.parse(await readFile(catalogPath, 'utf8'));
  return { dir, catalogPath, lockPath, flags, read, paths: resolvePaths(overrides) };
}

test('add deriva a URN a partir do code e do namespace persistido', async (t) => {
  const ws = await workspace(t);

  await main([
    'add',
    ...ws.flags,
    '--code', 'API_UPLOAD_EMPTY',
    '--scope', 'API',
    '--description', 'Requisição de ingestão sem nenhum arquivo.',
    '--http-status', '400',
    '--title', 'Empty upload',
    '--detail', 'Select at least one file to upload.',
    '--retry-policy', 'NEVER',
    '--owner', 'ingest',
  ]);

  const entry = (await ws.read()).entries.find((item) => item.code === 'API_UPLOAD_EMPTY');
  assert.equal(entry.type, 'urn:uuid:d6d16dca-20a5-5fbe-8309-677e5f769708');
  assert.equal(entry.status, 'active');
  assert.equal(entry.replacedBy, null);
  assert.equal(entry.extensionsSchemaRef, null);
});

test('add mantém o catálogo ordenado e canônico', async (t) => {
  const ws = await workspace(t);

  await main([
    'add',
    ...ws.flags,
    '--code', 'API_ACCESS_DENIED',
    '--scope', 'API',
    '--description', 'Identidade autenticada sem permissão para o recurso.',
    '--http-status', '403',
    '--title', 'Access denied',
    '--detail', 'You do not have permission to access this resource.',
    '--retry-policy', 'NEVER',
    '--owner', 'security',
  ]);

  const codes = (await ws.read()).entries.map((entry) => entry.code);
  assert.deepEqual(codes, [...codes].sort());
  assert.equal(codes[0], 'API_ACCESS_DENIED');
});

test('add recusa qualquer flag que tente informar a identidade', async (t) => {
  const ws = await workspace(t);

  for (const flag of ['--type', '--uuid', '--urn', '--namespace-uuid']) {
    await assert.rejects(
      main(['add', ...ws.flags, flag, 'urn:uuid:00000000-0000-5000-8000-000000000000']),
      /derivada mecanicamente/,
      `flag aceita indevidamente: ${flag}`,
    );
  }
});

test('add exige todos os campos semânticos', async (t) => {
  const ws = await workspace(t);

  await assert.rejects(
    main(['add', ...ws.flags, '--code', 'API_INTERNAL_ERROR', '--scope', 'API']),
    /--description é obrigatório/,
  );
  await assert.rejects(
    main([
      'add', ...ws.flags,
      '--code', 'API_INTERNAL_ERROR',
      '--scope', 'API',
      '--description', 'Falha inesperada.',
      '--retry-policy', 'MANUAL',
      '--owner', 'platform',
    ]),
    /--http-status é obrigatório/,
  );
});

test('add recusa campos HTTP em entrada CLIENT', async (t) => {
  const ws = await workspace(t);

  await assert.rejects(
    main([
      'add', ...ws.flags,
      '--code', 'CLIENT_REQUEST_TIMEOUT',
      '--scope', 'CLIENT',
      '--description', 'O browser observou timeout.',
      '--retry-policy', 'MANUAL',
      '--owner', 'frontend',
      '--http-status', '408',
    ]),
    /não se aplica a scope CLIENT/,
  );
});

test('add recusa code já publicado', async (t) => {
  const ws = await workspace(t);

  await assert.rejects(
    main([
      'add', ...ws.flags,
      '--code', 'API_ARCHIVE_UNAVAILABLE',
      '--scope', 'API',
      '--description', 'Duplicata.',
      '--http-status', '503',
      '--title', 'Archive unavailable',
      '--detail', 'The imaging archive is temporarily unavailable.',
      '--retry-policy', 'MANUAL',
      '--owner', 'platform',
    ]),
    /já existe/,
  );
});

test('deprecate exige substituto existente e ativo', async (t) => {
  const ws = await workspace(t);

  await assert.rejects(
    main(['deprecate', ...ws.flags, '--code', 'API_SEARCH_INVALID', '--replaced-by', 'API_NAO_EXISTE']),
    /não existe no catálogo/,
  );
  await assert.rejects(
    main(['deprecate', ...ws.flags, '--code', 'API_SEARCH_INVALID', '--replaced-by', 'API_SEARCH_MALFORMED']),
    /não está ativo/,
  );
  await assert.rejects(
    main(['deprecate', ...ws.flags, '--code', 'API_SEARCH_MALFORMED']),
    /já está depreciado/,
  );
});

test('deprecate registra status e substituição', async (t) => {
  const ws = await workspace(t);

  await main([
    'deprecate', ...ws.flags,
    '--code', 'API_DICOM_VALIDATION_FAILED',
    '--replaced-by', 'API_ARCHIVE_UNAVAILABLE',
  ]);

  const entry = (await ws.read()).entries.find((item) => item.code === 'API_DICOM_VALIDATION_FAILED');
  assert.equal(entry.status, 'deprecated');
  assert.equal(entry.replacedBy, 'API_ARCHIVE_UNAVAILABLE');
});

test('deprecate recusa deixar um ponteiro replacedBy sem destino ativo', async (t) => {
  const ws = await workspace(t);

  await assert.rejects(
    main(['deprecate', ...ws.flags, '--code', 'API_SEARCH_INVALID', '--replaced-by', 'API_ARCHIVE_UNAVAILABLE']),
    /API_SEARCH_MALFORMED[\s\S]*não está ativo/,
  );
});

test('check aprova espaço consistente e acusa deriva sem escrever', async (t) => {
  const ws = await workspace(t);
  await main(['generate', ...ws.flags]);

  const green = await checkWorkspace(ws.paths);
  assert.deepEqual(green.errors, []);
  assert.equal(green.ok, true);

  const before = await readFile(ws.catalogPath, 'utf8');
  await writeFile(ws.lockPath, serializeLock({ schemaVersion: 1, namespaceUuid: 'x', entries: [], extensions: {} }), 'utf8');

  const red = await checkWorkspace(ws.paths);
  assert.equal(red.ok, false);
  assert.deepEqual(red.changedPaths, [ws.lockPath]);
  assert.equal(await readFile(ws.catalogPath, 'utf8'), before);
});
