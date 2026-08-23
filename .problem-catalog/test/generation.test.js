import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { DEFAULT_PATHS, loadExtensionSchemas } from '../src/catalog.js';
import { checkWorkspace, generateInMemory, main, resolvePaths } from '../src/cli.js';

const fixture = fileURLToPath(new URL('fixtures/valid-catalog.json', import.meta.url));
const golden = (name) => fileURLToPath(new URL(`fixtures/golden/${name}`, import.meta.url));

const schemas = await loadExtensionSchemas(DEFAULT_PATHS.extensionsDir);
const validCatalog = JSON.parse(await readFile(fixture, 'utf8'));

const GENERATED = [
  ['ProblemType.java', 'java'],
  ['ProblemExtensions.java', 'javaExtensions'],
  ['problem-types.generated.ts', 'typescript'],
  ['problem-extensions.generated.ts', 'typescriptExtensions'],
];

async function scratch(t) {
  const dir = await mkdtemp(path.join(tmpdir(), 'problem-catalog-gen-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  return dir;
}

/** Espaço completo em disco temporário, com todos os destinos redirecionados. */
async function scratchWorkspace(t, catalog = validCatalog) {
  const dir = await scratch(t);
  const paths = resolvePaths({
    catalog: path.join(dir, 'catalog.json'),
    lock: path.join(dir, 'catalog.lock.json'),
    markdown: path.join(dir, 'catalog.md'),
    java: path.join(dir, 'java'),
    typescript: path.join(dir, 'ts'),
  });

  await writeFile(paths.catalog, `${JSON.stringify(catalog, null, 2)}\n`, 'utf8');
  const flags = [
    '--catalog', paths.catalog,
    '--lock', paths.lock,
    '--markdown', paths.markdown,
    '--java', paths.javaDir,
    '--typescript', paths.typescriptDir,
    '--schema', DEFAULT_PATHS.schema,
    '--extensions', DEFAULT_PATHS.extensionsDir,
  ];
  return { dir, paths, flags };
}

test('a geração é determinística byte a byte e não carrega timestamp', async () => {
  const first = await generateInMemory(validCatalog, schemas);
  const second = await generateInMemory(validCatalog, schemas);

  assert.deepEqual(second, first);
  assert.equal(
    Object.values(first).some((text) => /generatedAt|timestamp|\d{4}-\d{2}-\d{2}/i.test(text)),
    false,
  );
});

test('todo artefato gerado carrega o cabeçalho DO NOT EDIT', async () => {
  const generated = await generateInMemory(validCatalog, schemas);
  for (const [name, key] of GENERATED) {
    assert.match(generated[key], /DO NOT EDIT/, `${name} sem cabeçalho DO NOT EDIT`);
  }
});

test('os artefatos gerados batem com os golden files', async () => {
  const generated = await generateInMemory(validCatalog, schemas);
  for (const [name, key] of GENERATED) {
    assert.equal(generated[key], await readFile(golden(name), 'utf8'), `${name} divergente do golden`);
  }
});

test('o enum Java expõe a superfície acordada', async () => {
  const { java } = await generateInMemory(validCatalog, schemas);

  assert.match(java, /package dev\.blackice\.shared\.api\.problem\.generated;/);
  assert.match(java, /public enum ProblemType/);
  assert.match(java, /public URI type\(\)/);
  assert.match(java, /public String code\(\)/);
  assert.match(java, /public ProblemScope scope\(\)/);
  assert.match(java, /public Integer httpStatus\(\)/);
  assert.match(java, /public String title\(\)/);
  assert.match(java, /public String detail\(\)/);
  assert.match(java, /public RetryPolicy retryPolicy\(\)/);
  assert.match(java, /API_ARCHIVE_UNAVAILABLE\(/);
  assert.match(java, /CLIENT_NETWORK_UNAVAILABLE\(/);
});

test('ProblemExtensions é selada e permite somente None e DicomValidationViolations', async () => {
  const { javaExtensions } = await generateInMemory(validCatalog, schemas);

  assert.match(javaExtensions, /public sealed interface ProblemExtensions/);
  assert.match(javaExtensions, /permits ProblemExtensions\.None, ProblemExtensions\.DicomValidationViolations/);
  assert.match(javaExtensions, /static ProblemExtensions none\(\)/);
  assert.match(javaExtensions, /record Violation\(int itemIndex, String code, String message\)/);
  assert.match(javaExtensions, /record DicomValidationViolations\(List<Violation> violations\)/);
});

test('o TypeScript gerado expõe PROBLEM_TYPES e as unions de código', async () => {
  const { typescript } = await generateInMemory(validCatalog, schemas);

  assert.match(typescript, /export const PROBLEM_TYPES = \{/);
  assert.match(typescript, /\} as const;/);
  assert.match(typescript, /export type ProblemCode = keyof typeof PROBLEM_TYPES;/);
  assert.match(typescript, /export type ApiProblemCode =/);
  assert.match(typescript, /export type ClientProblemCode =/);
  assert.match(
    typescript,
    /API_ARCHIVE_UNAVAILABLE: \{\n {4}type: 'urn:uuid:cbe2c734-1873-570b-a498-a27a96ebadd4',\n {4}scope: 'API',\n {4}httpStatus: 503,\n {4}retryPolicy: 'MANUAL',\n {2}\},/,
  );
  assert.match(typescript, /CLIENT_NETWORK_UNAVAILABLE: \{\n {4}type: '[^']+',\n {4}scope: 'CLIENT',\n {4}retryPolicy: 'MANUAL',\n {2}\},/);
});

test('generate grava catálogo canônico, lock, markdown e artefatos', async (t) => {
  const ws = await scratchWorkspace(t);
  await main(['generate', ...ws.flags]);

  const lock = JSON.parse(await readFile(ws.paths.lock, 'utf8'));
  assert.equal(lock.namespaceUuid, validCatalog.namespaceUuid);
  assert.equal(lock.entries.length, validCatalog.entries.length);

  const markdown = await readFile(ws.paths.markdown, 'utf8');
  assert.match(markdown, /API_ARCHIVE_UNAVAILABLE/);
  assert.match(markdown, /DO NOT EDIT/);

  for (const [name] of GENERATED) {
    const dir = name.endsWith('.java') ? ws.paths.javaDir : ws.paths.typescriptDir;
    assert.ok((await readFile(path.join(dir, name), 'utf8')).length > 0);
  }
});

test('uma segunda geração não altera nenhum byte', async (t) => {
  const ws = await scratchWorkspace(t);
  await main(['generate', ...ws.flags]);

  const snapshot = async () => ({
    catalog: await readFile(ws.paths.catalog, 'utf8'),
    lock: await readFile(ws.paths.lock, 'utf8'),
    markdown: await readFile(ws.paths.markdown, 'utf8'),
    java: await readFile(path.join(ws.paths.javaDir, 'ProblemType.java'), 'utf8'),
    ts: await readFile(path.join(ws.paths.typescriptDir, 'problem-types.generated.ts'), 'utf8'),
  });

  const before = await snapshot();
  await main(['generate', ...ws.flags]);
  assert.deepEqual(await snapshot(), before);
});

test('check acusa deriva de arquivo gerado sem escrever', async (t) => {
  const ws = await scratchWorkspace(t);
  await main(['generate', ...ws.flags]);

  const drifted = path.join(ws.paths.typescriptDir, 'problem-types.generated.ts');
  await writeFile(drifted, '// editado à mão\n', 'utf8');

  const result = await checkWorkspace(ws.paths);
  assert.equal(result.ok, false);
  assert.deepEqual(result.changedPaths, [drifted]);
  assert.equal(await readFile(drifted, 'utf8'), '// editado à mão\n');
});

test('generate inicializa um catálogo vazio com namespace novo', async (t) => {
  const dir = await scratch(t);
  const paths = resolvePaths({
    catalog: path.join(dir, 'catalog.json'),
    lock: path.join(dir, 'catalog.lock.json'),
    markdown: path.join(dir, 'catalog.md'),
    java: path.join(dir, 'java'),
    typescript: path.join(dir, 'ts'),
  });

  await main([
    'generate',
    '--catalog', paths.catalog,
    '--lock', paths.lock,
    '--markdown', paths.markdown,
    '--java', paths.javaDir,
    '--typescript', paths.typescriptDir,
    '--schema', DEFAULT_PATHS.schema,
    '--extensions', DEFAULT_PATHS.extensionsDir,
  ]);

  const bootstrapped = JSON.parse(await readFile(paths.catalog, 'utf8'));
  assert.equal(bootstrapped.schemaVersion, 1);
  assert.match(bootstrapped.namespaceUuid, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  assert.deepEqual(bootstrapped.entries, []);

  // O bootstrap acontece uma única vez: o namespace não é recalculado.
  await main([
    'generate',
    '--catalog', paths.catalog,
    '--lock', paths.lock,
    '--markdown', paths.markdown,
    '--java', paths.javaDir,
    '--typescript', paths.typescriptDir,
    '--schema', DEFAULT_PATHS.schema,
    '--extensions', DEFAULT_PATHS.extensionsDir,
  ]);
  assert.equal(
    JSON.parse(await readFile(paths.catalog, 'utf8')).namespaceUuid,
    bootstrapped.namespaceUuid,
  );
});

test('generate recusa transição proibida pelo lock e preserva o catálogo', async (t) => {
  const ws = await scratchWorkspace(t);
  await main(['generate', ...ws.flags]);

  const tampered = { ...validCatalog, entries: validCatalog.entries.slice(1) };
  await writeFile(ws.paths.catalog, `${JSON.stringify(tampered, null, 2)}\n`, 'utf8');

  await assert.rejects(main(['generate', ...ws.flags]), /removida do catálogo/);
});
