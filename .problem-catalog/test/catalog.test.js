import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  DEFAULT_PATHS,
  loadCatalog,
  loadExtensionSchemas,
  normalizeCatalog,
  validateCatalog,
} from '../src/catalog.js';

const fixture = (name) => fileURLToPath(new URL(`fixtures/${name}`, import.meta.url));

const schemas = await loadExtensionSchemas(DEFAULT_PATHS.extensionsDir);

async function fixtureCatalog(name = 'valid-catalog.json') {
  return JSON.parse(await readFile(fixture(name), 'utf8'));
}

/** Aplica uma mutação ao fixture válido sem contaminar os demais testes. */
async function mutated(mutate) {
  const catalog = await fixtureCatalog();
  mutate(catalog);
  return catalog;
}

function errorText(result) {
  assert.equal(result.ok, false, 'esperava catálogo inválido');
  return result.errors.join('\n');
}

test('aceita o catálogo de fixture válido', async () => {
  const catalog = await loadCatalog(fixture('valid-catalog.json'));
  const result = validateCatalog(catalog, schemas);
  assert.deepEqual(result.errors, []);
  assert.equal(result.ok, true);
});

test('rejeita entrada API sem os campos HTTP obrigatórios', async () => {
  const catalog = await fixtureCatalog('invalid-api-without-http.json');
  assert.match(errorText(validateCatalog(catalog, schemas)), /httpStatus|title|detail/);
});

test('rejeita entrada CLIENT que carrega campos HTTP', async () => {
  const catalog = await fixtureCatalog('invalid-client-with-http.json');
  assert.match(errorText(validateCatalog(catalog, schemas)), /httpStatus|title|detail/);
});

test('rejeita retryPolicy AUTOMATIC', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].retryPolicy = 'AUTOMATIC';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /retryPolicy/);
});

test('rejeita owner fora da lista fechada', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].owner = 'radiologia';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /owner/);
});

test('rejeita código fora da gramática {SCOPE}_{SUBJECT}_{CONDITION}', async () => {
  for (const code of ['api_archive_unavailable', 'ARCHIVE_UNAVAILABLE', 'API_ARCHIVE', 'API_Archive_Unavailable']) {
    const catalog = await mutated((c) => {
      c.entries[0].code = code;
    });
    assert.match(errorText(validateCatalog(catalog, schemas)), /code/i, `code aceito indevidamente: ${code}`);
  }
});

test('rejeita scope incompatível com o prefixo do código', async () => {
  const catalog = await mutated((c) => {
    c.entries[4].scope = 'API';
  });
  assert.equal(validateCatalog(catalog, schemas).ok, false);
});

test('rejeita type que não é urn:uuid', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].type = 'https://blackice.dev/problems/archive-unavailable';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /type/);
});

test('rejeita type que não corresponde ao UUIDv5 derivado do código', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].type = 'urn:uuid:850ffcf4-95bb-5902-90df-d06f1b9aeb2c';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /UUIDv5|derivad/i);
});

test('rejeita código duplicado', async () => {
  const catalog = await mutated((c) => {
    c.entries.push({ ...c.entries[0] });
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /duplicad/i);
});

test('rejeita referência a schema de extensão inexistente', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].extensionsSchemaRef = 'nao-existe';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /extens/i);
});

test('rejeita entrada ativa com replacedBy preenchido', async () => {
  const catalog = await mutated((c) => {
    c.entries[0].replacedBy = 'API_SEARCH_INVALID';
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /replacedBy/);
});

test('rejeita replacedBy apontando para código inexistente ou depreciado', async () => {
  const inexistente = await mutated((c) => {
    c.entries[3].replacedBy = 'API_NAO_EXISTE';
  });
  assert.match(errorText(validateCatalog(inexistente, schemas)), /replacedBy/);

  const depreciado = await mutated((c) => {
    c.entries[2].status = 'deprecated';
    c.entries[2].replacedBy = 'API_SEARCH_MALFORMED';
  });
  assert.match(errorText(validateCatalog(depreciado, schemas)), /replacedBy/);
});

test('rejeita entradas fora da ordem canônica por código', async () => {
  const catalog = await mutated((c) => {
    c.entries.reverse();
  });
  assert.match(errorText(validateCatalog(catalog, schemas)), /orden/i);
});

test('normaliza ordenando por código e fixando a ordem das chaves', async () => {
  const catalog = await mutated((c) => {
    c.entries.reverse();
  });
  const normalized = normalizeCatalog(catalog);

  assert.deepEqual(
    normalized.entries.map((entry) => entry.code),
    [
      'API_ARCHIVE_UNAVAILABLE',
      'API_DICOM_VALIDATION_FAILED',
      'API_SEARCH_INVALID',
      'API_SEARCH_MALFORMED',
      'CLIENT_NETWORK_UNAVAILABLE',
    ],
  );
  assert.deepEqual(Object.keys(normalized), ['schemaVersion', 'namespaceUuid', 'owners', 'entries']);
  assert.deepEqual(Object.keys(normalized.entries[0]), [
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
  ]);
  assert.deepEqual(Object.keys(normalized.entries[4]), [
    'type',
    'code',
    'scope',
    'description',
    'retryPolicy',
    'owner',
    'extensionsSchemaRef',
    'status',
    'replacedBy',
  ]);
  assert.equal(validateCatalog(normalized, schemas).ok, true);
});

test('carrega os schemas de extensão publicados com fingerprint estável', async () => {
  assert.ok(Object.hasOwn(schemas, 'dicom-validation-violations'));

  const again = await loadExtensionSchemas(DEFAULT_PATHS.extensionsDir);
  assert.equal(
    again['dicom-validation-violations'].fingerprint,
    schemas['dicom-validation-violations'].fingerprint,
  );
  assert.match(schemas['dicom-validation-violations'].fingerprint, /^sha256:[0-9a-f]{64}$/);
});
