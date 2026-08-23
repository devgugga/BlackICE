import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { DEFAULT_PATHS, loadExtensionSchemas } from '../src/catalog.js';
import { assertAllowedTransition, compareLock, createLock } from '../src/lock.js';

const fixture = (name) => fileURLToPath(new URL(`fixtures/${name}`, import.meta.url));
const schemas = await loadExtensionSchemas(DEFAULT_PATHS.extensionsDir);

async function fixtureCatalog() {
  return JSON.parse(await readFile(fixture('valid-catalog.json'), 'utf8'));
}

async function baseline() {
  const catalog = await fixtureCatalog();
  return { catalog, lock: createLock(catalog, schemas) };
}

async function changed(mutate) {
  const { catalog, lock } = await baseline();
  mutate(catalog);
  return { lock, catalog };
}

function rejection({ lock, catalog }, pattern) {
  const result = compareLock(lock, catalog, schemas);
  assert.equal(result.ok, false, 'esperava transição recusada');
  assert.match(result.errors.join('\n'), pattern);
  assert.throws(() => assertAllowedTransition(lock, catalog, schemas), pattern);
}

test('o lock registra somente os campos imutáveis e as fingerprints', async () => {
  const { lock } = await baseline();

  assert.equal(lock.schemaVersion, 1);
  assert.equal(lock.namespaceUuid, '0b1f6e3a-9c2d-4f58-9a71-2d4c8e6f10b3');
  assert.deepEqual(Object.keys(lock.entries[0]), [
    'code',
    'type',
    'scope',
    'httpStatus',
    'retryPolicy',
    'extensionsSchemaRef',
    'extensionsFingerprint',
    'status',
  ]);
  assert.equal(lock.entries[0].code, 'API_ARCHIVE_UNAVAILABLE');
  assert.equal(lock.entries[0].extensionsFingerprint, null);
  assert.match(lock.entries[1].extensionsFingerprint, /^sha256:[0-9a-f]{64}$/);
  assert.match(lock.extensions['dicom-validation-violations'], /^sha256:[0-9a-f]{64}$/);
});

test('a criação do lock é determinística', async () => {
  const catalog = await fixtureCatalog();
  assert.deepEqual(createLock(catalog, schemas), createLock(catalog, schemas));
});

test('aceita catálogo idêntico ao lock', async () => {
  const { lock, catalog } = await baseline();
  const result = compareLock(lock, catalog, schemas);
  assert.deepEqual(result.errors, []);
  assert.equal(result.ok, true);
  assert.doesNotThrow(() => assertAllowedTransition(lock, catalog, schemas));
});

test('recusa remoção de entrada bloqueada', async () => {
  rejection(await changed((c) => c.entries.splice(0, 1)), /remov/i);
});

test('recusa renomear um código publicado', async () => {
  rejection(
    await changed((c) => {
      c.entries[0].code = 'API_ARCHIVE_OFFLINE';
    }),
    /remov|code/i,
  );
});

test('recusa alterar o type de uma entrada publicada', async () => {
  rejection(
    await changed((c) => {
      c.entries[0].type = 'urn:uuid:850ffcf4-95bb-5902-90df-d06f1b9aeb2c';
    }),
    /type/,
  );
});

test('recusa alterar o scope de uma entrada publicada', async () => {
  rejection(
    await changed((c) => {
      c.entries[0].scope = 'CLIENT';
    }),
    /scope/,
  );
});

test('recusa alterar o httpStatus de uma entrada publicada', async () => {
  rejection(
    await changed((c) => {
      c.entries[0].httpStatus = 502;
    }),
    /httpStatus/,
  );
});

test('recusa alterar a retryPolicy de uma entrada publicada', async () => {
  rejection(
    await changed((c) => {
      c.entries[0].retryPolicy = 'NEVER';
    }),
    /retryPolicy/,
  );
});

test('recusa trocar a referência de extensão de uma entrada publicada', async () => {
  rejection(
    await changed((c) => {
      c.entries[1].extensionsSchemaRef = null;
    }),
    /extens/i,
  );
});

test('recusa mudança na fingerprint do schema de extensão', async () => {
  const { catalog, lock } = await baseline();
  const drifted = {
    'dicom-validation-violations': {
      ...schemas['dicom-validation-violations'],
      fingerprint: `sha256:${'0'.repeat(64)}`,
    },
  };

  const result = compareLock(lock, catalog, drifted);
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /fingerprint/i);
  assert.throws(() => assertAllowedTransition(lock, catalog, drifted), /fingerprint/i);
});

test('recusa reativar entrada depreciada', async () => {
  rejection(
    await changed((c) => {
      c.entries[3].status = 'active';
      c.entries[3].replacedBy = null;
    }),
    /reativ|status/i,
  );
});

test('permite transição de active para deprecated', async () => {
  const { lock, catalog } = await changed((c) => {
    c.entries[2].status = 'deprecated';
    c.entries[2].replacedBy = 'API_ARCHIVE_UNAVAILABLE';
  });
  assert.deepEqual(compareLock(lock, catalog, schemas).errors, []);
  assert.doesNotThrow(() => assertAllowedTransition(lock, catalog, schemas));
});

test('permite correção editorial de title, detail, description e owner', async () => {
  const { lock, catalog } = await changed((c) => {
    c.entries[0].title = 'Archive temporarily unavailable';
    c.entries[0].detail = 'The imaging archive is temporarily unavailable. Try again later.';
    c.entries[0].description = 'Indisponibilidade temporária do Archive DICOMweb.';
    c.entries[0].owner = 'ingest';
  });
  assert.deepEqual(compareLock(lock, catalog, schemas).errors, []);
});

test('permite acrescentar uma entrada nova sem tocar nas bloqueadas', async () => {
  const { lock, catalog } = await changed((c) => {
    c.entries.push({
      type: 'urn:uuid:0d581491-0971-5b10-8780-d8380efe42f5',
      code: 'CLIENT_REQUEST_TIMEOUT',
      scope: 'CLIENT',
      description: 'O browser observou timeout.',
      retryPolicy: 'MANUAL',
      owner: 'frontend',
      extensionsSchemaRef: null,
      status: 'active',
      replacedBy: null,
    });
  });
  assert.deepEqual(compareLock(lock, catalog, schemas).errors, []);
});

test('recusa alterar o namespace do catálogo', async () => {
  rejection(
    await changed((c) => {
      c.namespaceUuid = '9f2c1d84-7b3e-4a6f-8c05-1e7d3b9a4f62';
    }),
    /namespace/i,
  );
});
