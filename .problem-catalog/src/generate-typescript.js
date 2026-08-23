/** Geração dos tipos TypeScript consumidos pelo frontend. */
import { pascalCase, quote, screamingSnakeCase, singularize, upperFirst } from './naming.js';

function header(source) {
  return [
    '// DO NOT EDIT.',
    '//',
    `// Gerado por .problem-catalog a partir de ${source}.`,
    '// Altere o catálogo e execute `pnpm generate` em .problem-catalog/.',
  ].join('\n');
}

function deprecationTag(entry) {
  if (entry.status !== 'deprecated') return [];
  const replacement = entry.replacedBy === null ? 'sem substituto' : `use ${entry.replacedBy}`;
  return [`  /** @deprecated Depreciado no catálogo: ${replacement}. */`];
}

function definition(entry) {
  const body = [
    `    type: ${quote(entry.type)},`,
    `    scope: ${quote(entry.scope)},`,
    ...(entry.scope === 'API' ? [`    httpStatus: ${entry.httpStatus},`] : []),
    `    retryPolicy: ${quote(entry.retryPolicy)},`,
  ];
  return [...deprecationTag(entry), `  ${entry.code}: {`, ...body, '  },'].join('\n');
}

export function generateTypeScript(catalog) {
  return `${[
    header('docs/contracts/problems/catalog.json'),
    '',
    '/** Tipos de problema publicados no catálogo oficial. */',
    'export const PROBLEM_TYPES = {',
    ...catalog.entries.map(definition),
    '} as const;',
    '',
    '/** Code de qualquer problema catalogado. */',
    'export type ProblemCode = keyof typeof PROBLEM_TYPES;',
    '',
    '/** Resposta HTTP observável ou falha local do cliente. */',
    "export type ProblemScope = 'API' | 'CLIENT';",
    '',
    '/** `AUTOMATIC` é reservado e não existe nesta versão. */',
    "export type RetryPolicy = 'NEVER' | 'MANUAL';",
    '',
    'type CodesInScope<S extends ProblemScope> = {',
    "  [C in ProblemCode]: (typeof PROBLEM_TYPES)[C]['scope'] extends S ? C : never;",
    '}[ProblemCode];',
    '',
    '/** Code de um problema que nasce de uma resposta HTTP do backend. */',
    "export type ApiProblemCode = CodesInScope<'API'>;",
    '',
    '/** Code de uma falha local do browser, sem resposta HTTP correspondente. */',
    "export type ClientProblemCode = CodesInScope<'CLIENT'>;",
    '',
    '/** Definição catalogada de um code. */',
    'export type ProblemTypeDefinition = (typeof PROBLEM_TYPES)[ProblemCode];',
    '',
    '/** Todos os codes, na ordem canônica do catálogo. */',
    'export const PROBLEM_CODES = Object.keys(PROBLEM_TYPES) as readonly ProblemCode[];',
  ].join('\n')}\n`;
}

const TS_SCALARS = { integer: 'number', number: 'number', string: 'string', boolean: 'boolean' };

function scalarType(schema) {
  if (Array.isArray(schema.enum)) {
    return schema.enum.map((value) => quote(value)).join(' | ');
  }
  return TS_SCALARS[schema.type] ?? 'unknown';
}

/**
 * Nome do item de uma coleção. Diferente do Java, o TypeScript exporta esses
 * tipos numa superfície plana e compartilhada, então o nome carrega a extensão
 * de origem: `violations` dentro de `dicom-validation-violations` vira
 * `DicomValidationViolation`, não `Violation`.
 */
function itemTypeName(ref, property) {
  const root = pascalCase(ref);
  const rootSingular = singularize(root);
  const own = upperFirst(singularize(property));
  return rootSingular.endsWith(own) ? rootSingular : `${root}${own}`;
}

function memberType(ref, name, schema, interfaces) {
  if (schema.type === 'array') {
    const item = schema.items ?? {};
    if (item.type === 'object') {
      const interfaceName = itemTypeName(ref, name);
      interfaces.set(interfaceName, item);
      return `readonly ${interfaceName}[]`;
    }
    return `readonly (${scalarType(item)})[]`;
  }
  return scalarType(schema);
}

function interfaceLines(ref, name, schema, interfaces, doc) {
  const members = Object.entries(schema.properties ?? {}).map(
    ([member, property]) => `  readonly ${member}: ${memberType(ref, member, property, interfaces)};`,
  );
  return [...doc, `export interface ${name} {`, ...members, '}'];
}

function enumTypeName(ref, property, field) {
  return `${itemTypeName(ref, property)}${upperFirst(field)}`;
}

function enumConstName(ref, property, field) {
  return `${screamingSnakeCase(singularize(pascalCase(ref)).replaceAll(/(?<=[a-z0-9])(?=[A-Z])/g, '-'))}_${field.toUpperCase()}S`;
}

/** Cada campo enumerado da extensão vira uma const de runtime e uma union. */
function enumFields(ref, schema) {
  const fields = [];
  for (const [property, propertySchema] of Object.entries(schema.properties ?? {})) {
    const item = propertySchema.type === 'array' ? (propertySchema.items ?? {}) : propertySchema;
    for (const [field, fieldSchema] of Object.entries(item.properties ?? {})) {
      if (!Array.isArray(fieldSchema.enum)) continue;
      fields.push({
        property,
        field,
        values: fieldSchema.enum,
        typeName: enumTypeName(ref, property, field),
        constName: enumConstName(ref, property, field),
      });
    }
  }
  return fields;
}

function enumConstants(fields) {
  return fields.flatMap(({ property, field, values, typeName, constName }) => [
    '',
    `/** Valores aceitos em \`${property}[].${field}\`. */`,
    `export const ${constName} = [`,
    ...values.map((value) => `  ${quote(value)},`),
    '] as const;',
    '',
    `export type ${typeName} = (typeof ${constName})[number];`,
  ]);
}

export function generateTypeScriptExtensions(catalog, extensionSchemas) {
  const byCode = catalog.entries
    .filter((entry) => entry.extensionsSchemaRef !== null && entry.extensionsSchemaRef !== undefined)
    .map((entry) => [entry.code, entry.extensionsSchemaRef]);

  const refs = [...new Set(byCode.map(([, ref]) => ref))].sort();
  const blocks = [];

  for (const ref of refs) {
    const schema = extensionSchemas[ref].schema;
    const fields = enumFields(ref, schema);
    const aliases = new Map(fields.map(({ field, typeName }) => [field, typeName]));
    const nested = new Map();
    const rootName = pascalCase(ref);

    blocks.push(...enumConstants(fields));

    const rootLines = interfaceLines(ref, rootName, schema, nested, [
      '',
      `/** Membros adicionais definidos por extensions/${ref}.schema.json. */`,
    ]);

    const nestedLines = [...nested.entries()]
      .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
      .flatMap(([name, itemSchema]) =>
        interfaceLines(ref, name, itemSchema, new Map(), [
          '',
          `/** Item de \`${rootName}\`. */`,
        ]).map((line) => {
          const match = /^ {2}readonly (\w+): /.exec(line);
          const alias = match === null ? undefined : aliases.get(match[1]);
          return alias === undefined ? line : `  readonly ${match[1]}: ${alias};`;
        }),
      );

    blocks.push(...nestedLines, ...rootLines);
  }

  const mapping =
    byCode.length === 0
      ? ['export type ProblemExtensionsByCode = Record<never, never>;']
      : [
          'export type ProblemExtensionsByCode = {',
          ...byCode
            .map(([code, ref]) => `  ${code}: ${pascalCase(ref)};`)
            .sort(),
          '};',
        ];

  return `${[
    header('docs/contracts/problems/catalog.json e docs/contracts/problems/extensions/'),
    "",
    "import type { ProblemCode } from './problem-types.generated';",
    ...blocks,
    '',
    '/** Extensão esperada no nível raiz do Problem Details, por code. */',
    ...mapping,
    '',
    '/** Extensão de um code específico; `never` quando o tipo não tem extensão. */',
    'export type ProblemExtensionsFor<C extends ProblemCode> = C extends keyof ProblemExtensionsByCode',
    '  ? ProblemExtensionsByCode[C]',
    '  : never;',
  ].join('\n')}\n`;
}
