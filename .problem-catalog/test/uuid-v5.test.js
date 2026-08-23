import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DNS_NAMESPACE_UUID,
  createNamespaceUuid,
  deriveProblemUrn,
  deriveUuidV5,
  isProblemUrn,
  isUuid,
  toUrn,
} from '../src/uuid-v5.js';

test('reproduz o vetor conhecido RFC 9562 do namespace DNS', () => {
  assert.equal(
    toUrn(deriveUuidV5(DNS_NAMESPACE_UUID, 'www.widgets.com')),
    'urn:uuid:21f7f8de-8051-5b89-8680-0195ef798b6a',
  );
});

test('marca versão 5 e variante RFC 4122 nos bits corretos', () => {
  const uuid = deriveUuidV5(DNS_NAMESPACE_UUID, 'www.widgets.com');
  assert.equal(uuid[14], '5');
  assert.ok(['8', '9', 'a', 'b'].includes(uuid[19]));
  assert.ok(isUuid(uuid));
});

test('deriva a URN do problema a partir de blackice.problem.v1:{code}', () => {
  const namespace = '0b1f6e3a-9c2d-4f58-9a71-2d4c8e6f10b3';
  assert.equal(
    deriveProblemUrn(namespace, 'API_ARCHIVE_UNAVAILABLE'),
    'urn:uuid:cbe2c734-1873-570b-a498-a27a96ebadd4',
  );
  assert.equal(
    deriveProblemUrn(namespace, 'CLIENT_NETWORK_UNAVAILABLE'),
    'urn:uuid:850ffcf4-95bb-5902-90df-d06f1b9aeb2c',
  );
});

test('a derivação é determinística e sensível ao code e ao namespace', () => {
  const namespace = '0b1f6e3a-9c2d-4f58-9a71-2d4c8e6f10b3';
  const other = '9f2c1d84-7b3e-4a6f-8c05-1e7d3b9a4f62';

  assert.equal(
    deriveProblemUrn(namespace, 'API_SEARCH_INVALID'),
    deriveProblemUrn(namespace, 'API_SEARCH_INVALID'),
  );
  assert.notEqual(
    deriveProblemUrn(namespace, 'API_SEARCH_INVALID'),
    deriveProblemUrn(namespace, 'API_SEARCH_TOO_BROAD'),
  );
  assert.notEqual(
    deriveProblemUrn(namespace, 'API_SEARCH_INVALID'),
    deriveProblemUrn(other, 'API_SEARCH_INVALID'),
  );
});

test('recusa namespace que não é UUID', () => {
  assert.throws(() => deriveProblemUrn('não-é-uuid', 'API_SEARCH_INVALID'), /namespace/i);
});

test('produz URN de problema reconhecível', () => {
  const urn = deriveProblemUrn('0b1f6e3a-9c2d-4f58-9a71-2d4c8e6f10b3', 'API_INTERNAL_ERROR');
  assert.ok(isProblemUrn(urn));
  assert.ok(!isProblemUrn('urn:uuid:not-a-uuid'));
  assert.ok(!isProblemUrn('cbe2c734-1873-570b-a498-a27a96ebadd4'));
});

test('cria namespace aleatório válido para o bootstrap', () => {
  const first = createNamespaceUuid();
  const second = createNamespaceUuid();
  assert.ok(isUuid(first));
  assert.notEqual(first, second);
});
