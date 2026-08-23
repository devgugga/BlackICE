/** Geração de `catalog.md`, a face humana do registry. */

function cell(value) {
  return value === null || value === undefined ? '—' : String(value).replaceAll('|', '\\|');
}

function table(headers, alignments, rows) {
  return [
    `| ${headers.join(' | ')} |`,
    `| ${alignments.join(' | ')} |`,
    ...rows.map((row) => `| ${row.join(' | ')} |`),
  ];
}

function apiSection(entries) {
  if (entries.length === 0) return ['## Problemas API', '', '_Nenhuma entrada publicada._'];

  return [
    '## Problemas API',
    '',
    'Toda resposta de erro JSON `4xx/5xx` sob `/api` usa um destes tipos, com',
    'media type `application/problem+json`.',
    '',
    ...table(
      ['Code', 'HTTP', 'Retry', 'Owner', 'Title', 'Detail', 'Extensão', 'Status'],
      [':--', '--:', ':--', ':--', ':--', ':--', ':--', ':--'],
      entries.map((entry) => [
        `\`${entry.code}\``,
        cell(entry.httpStatus),
        `\`${entry.retryPolicy}\``,
        cell(entry.owner),
        cell(entry.title),
        cell(entry.detail),
        entry.extensionsSchemaRef === null ? '—' : `\`${entry.extensionsSchemaRef}\``,
        cell(entry.status),
      ]),
    ),
  ];
}

function clientSection(entries) {
  if (entries.length === 0) return ['## Problemas CLIENT', '', '_Nenhuma entrada publicada._'];

  return [
    '## Problemas CLIENT',
    '',
    'Falhas locais do browser. Não são respostas HTTP e por isso não possuem',
    '`httpStatus`, `title` nem `detail`.',
    '',
    ...table(
      ['Code', 'Retry', 'Owner', 'Significado', 'Status'],
      [':--', ':--', ':--', ':--', ':--'],
      entries.map((entry) => [
        `\`${entry.code}\``,
        `\`${entry.retryPolicy}\``,
        cell(entry.owner),
        cell(entry.description),
        cell(entry.status),
      ]),
    ),
  ];
}

function identitySection(entries) {
  return [
    '## Identidades',
    '',
    'A URN é UUIDv5 derivado de `blackice.problem.v1:{code}` dentro do namespace',
    'acima. Ela nunca é informada à mão e nunca é reciclada.',
    '',
    ...table(
      ['Code', 'Type'],
      [':--', ':--'],
      entries.map((entry) => [`\`${entry.code}\``, `\`${entry.type}\``]),
    ),
  ];
}

function deprecationSection(entries) {
  const deprecated = entries.filter((entry) => entry.status === 'deprecated');
  if (deprecated.length === 0) return [];

  return [
    '## Entradas depreciadas',
    '',
    'Uma entrada depreciada nunca é apagada nem reativada, e seu UUID nunca é',
    'reutilizado.',
    '',
    ...table(
      ['Code', 'Substituída por'],
      [':--', ':--'],
      deprecated.map((entry) => [
        `\`${entry.code}\``,
        entry.replacedBy === null ? '—' : `\`${entry.replacedBy}\``,
      ]),
    ),
  ];
}

function extensionsSection(catalog, extensionSchemas) {
  const refs = [
    ...new Set(
      catalog.entries
        .map((entry) => entry.extensionsSchemaRef)
        .filter((ref) => ref !== null && ref !== undefined),
    ),
  ].sort();

  if (refs.length === 0) return [];

  return [
    '## Extensões',
    '',
    'Membros adicionais ficam no nível raiz do Problem Details, ao lado de',
    '`traceId`.',
    '',
    ...refs.flatMap((ref) => {
      const users = catalog.entries
        .filter((entry) => entry.extensionsSchemaRef === ref)
        .map((entry) => `\`${entry.code}\``)
        .join(', ');
      return [
        `### \`${ref}\``,
        '',
        `Schema: \`docs/contracts/problems/extensions/${ref}.schema.json\`.`,
        '',
        `Usada por: ${users}.`,
        '',
        extensionSchemas[ref].schema.description ?? '',
        '',
      ];
    }),
  ];
}

export function generateMarkdown(catalog, extensionSchemas) {
  const api = catalog.entries.filter((entry) => entry.scope === 'API');
  const client = catalog.entries.filter((entry) => entry.scope === 'CLIENT');

  const sections = [
    '<!-- DO NOT EDIT. -->',
    '<!-- Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json. -->',
    '<!-- Altere o catálogo e execute `pnpm generate` em .problem-catalog/. -->',
    '',
    '# Catálogo de problemas',
    '',
    'Fonte da verdade machine-readable: `docs/contracts/problems/catalog.json`.',
    'Política e workflow: `docs/domains/problem-catalog/`.',
    '',
    `Namespace do registry: \`${catalog.namespaceUuid}\`.`,
    '',
    ...apiSection(api),
    '',
    ...clientSection(client),
    '',
    ...extensionsSection(catalog, extensionSchemas),
    ...deprecationSection(catalog.entries),
    ...(deprecationSection(catalog.entries).length > 0 ? [''] : []),
    ...identitySection(catalog.entries),
  ];

  return `${sections.join('\n').replace(/\n{3,}/g, '\n\n').trimEnd()}\n`;
}
