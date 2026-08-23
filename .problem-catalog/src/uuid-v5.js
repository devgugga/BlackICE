/**
 * Derivação de identidades do catálogo de problemas.
 *
 * As URNs são UUIDv5 (RFC 4122 / RFC 9562): SHA-1 sobre os 16 bytes do
 * namespace concatenados ao nome, com os bits de versão e variante forçados.
 * Elas identificam tipos de problema HTTP e de cliente; não são UIDs DICOM e
 * nunca entram em tags, estudos, séries ou instâncias.
 */
import { createHash, randomUUID } from 'node:crypto';

/** Namespace DNS da RFC, usado apenas para exercitar o vetor conhecido. */
export const DNS_NAMESPACE_UUID = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';

/** Prefixo estável do nome derivado. Mudá-lo reidentificaria todo o catálogo. */
export const PROBLEM_NAME_PREFIX = 'blackice.problem.v1:';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const PROBLEM_URN_PATTERN =
  /^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export function isUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

export function isProblemUrn(value) {
  return typeof value === 'string' && PROBLEM_URN_PATTERN.test(value);
}

function namespaceBytes(uuid) {
  if (!isUuid(uuid)) {
    throw new TypeError(`namespace inválido: esperava UUID em minúsculas, recebi ${String(uuid)}`);
  }
  return Buffer.from(uuid.replaceAll('-', ''), 'hex');
}

function formatUuid(bytes) {
  const hex = Buffer.from(bytes).toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** UUIDv5 puro, sem prefixo de URN. */
export function deriveUuidV5(namespaceUuid, name) {
  const bytes = createHash('sha1')
    .update(namespaceBytes(namespaceUuid))
    .update(Buffer.from(name, 'utf8'))
    .digest();
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  return formatUuid(bytes.subarray(0, 16));
}

export function toUrn(uuid) {
  return `urn:uuid:${uuid}`;
}

/** URN canônica de um code do catálogo dentro do namespace do registry. */
export function deriveProblemUrn(namespaceUuid, code) {
  return toUrn(deriveUuidV5(namespaceUuid, `${PROBLEM_NAME_PREFIX}${code}`));
}

/**
 * Cria o namespace do registry. Chamado uma única vez, no bootstrap, e o
 * resultado é persistido em `catalog.json`; nunca é recalculado depois.
 */
export function createNamespaceUuid() {
  return randomUUID();
}
