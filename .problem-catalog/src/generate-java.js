/** Geração do enum `ProblemType` e da interface selada `ProblemExtensions`. */
import { pascalCase, quote, singularize, upperFirst } from './naming.js';

export const JAVA_PACKAGE = 'dev.blackice.shared.api.problem.generated';

function header(source) {
  return [
    '/*',
    ' * DO NOT EDIT.',
    ' *',
    ` * Gerado por .problem-catalog a partir de ${source}.`,
    ' * Altere o catálogo e execute `pnpm generate` em .problem-catalog/.',
    ' */',
  ].join('\n');
}

function javaString(value) {
  return value === null || value === undefined ? 'null' : quote(value, '"');
}

function deprecationTag(entry) {
  if (entry.status !== 'deprecated') return [];
  const replacement = entry.replacedBy === null ? 'sem substituto' : `use ${entry.replacedBy}`;
  return [`    /** Depreciado no catálogo: ${replacement}. */`, '    @Deprecated'];
}

function constant(entry, isLast) {
  return [
    ...deprecationTag(entry),
    `    ${entry.code}(`,
    `            ${javaString(entry.type)},`,
    `            ProblemScope.${entry.scope},`,
    `            ${entry.httpStatus ?? 'null'},`,
    `            ${javaString(entry.title ?? null)},`,
    `            ${javaString(entry.detail ?? null)},`,
    `            RetryPolicy.${entry.retryPolicy})${isLast ? ';' : ','}`,
  ].join('\n');
}

export function generateJava(catalog) {
  const entries = catalog.entries;
  const constants =
    entries.length === 0
      ? ['    ;']
      : entries.map((entry, index) => constant(entry, index === entries.length - 1));

  return `${[
    header('docs/contracts/problems/catalog.json'),
    '',
    `package ${JAVA_PACKAGE};`,
    '',
    'import java.net.URI;',
    '',
    '/**',
    ' * Tipos de problema publicados no catálogo oficial.',
    ' *',
    ' * <p>O nome da constante é o {@code code} do catálogo. Os textos são públicos,',
    ' * estáveis e nunca derivam de uma exceção.',
    ' */',
    'public enum ProblemType {',
    '',
    ...constants,
    '',
    '    /** Resposta HTTP observável ({@code API}) ou falha local do cliente ({@code CLIENT}). */',
    '    public enum ProblemScope {',
    '        API,',
    '        CLIENT',
    '    }',
    '',
    '    /** {@code AUTOMATIC} é reservado e não existe nesta versão. */',
    '    public enum RetryPolicy {',
    '        NEVER,',
    '        MANUAL',
    '    }',
    '',
    '    private final URI type;',
    '    private final ProblemScope scope;',
    '    private final Integer httpStatus;',
    '    private final String title;',
    '    private final String detail;',
    '    private final RetryPolicy retryPolicy;',
    '',
    '    ProblemType(',
    '            String type,',
    '            ProblemScope scope,',
    '            Integer httpStatus,',
    '            String title,',
    '            String detail,',
    '            RetryPolicy retryPolicy) {',
    '        this.type = URI.create(type);',
    '        this.scope = scope;',
    '        this.httpStatus = httpStatus;',
    '        this.title = title;',
    '        this.detail = detail;',
    '        this.retryPolicy = retryPolicy;',
    '    }',
    '',
    '    /** URN {@code urn:uuid} estável do tipo. */',
    '    public URI type() {',
    '        return type;',
    '    }',
    '',
    '    /** Código legível, idêntico ao nome da constante. */',
    '    public String code() {',
    '        return name();',
    '    }',
    '',
    '    public ProblemScope scope() {',
    '        return scope;',
    '    }',
    '',
    '    /** {@code null} para tipos {@code CLIENT}, que não são respostas HTTP. */',
    '    public Integer httpStatus() {',
    '        return httpStatus;',
    '    }',
    '',
    '    /** {@code null} para tipos {@code CLIENT}. */',
    '    public String title() {',
    '        return title;',
    '    }',
    '',
    '    /** {@code null} para tipos {@code CLIENT}. */',
    '    public String detail() {',
    '        return detail;',
    '    }',
    '',
    '    public RetryPolicy retryPolicy() {',
    '        return retryPolicy;',
    '    }',
    '}',
  ].join('\n')}\n`;
}

const JAVA_SCALARS = { integer: 'int', number: 'double', string: 'String', boolean: 'boolean' };

function componentType(name, schema, records) {
  if (schema.type === 'array') {
    const item = schema.items ?? {};
    if (item.type === 'object') {
      const recordName = upperFirst(singularize(name));
      records.set(recordName, item);
      return `List<${recordName}>`;
    }
    return `List<${JAVA_SCALARS[item.type] ?? 'String'}>`;
  }
  return JAVA_SCALARS[schema.type] ?? 'String';
}

function recordComponents(schema, records) {
  return Object.entries(schema.properties ?? {})
    .map(([name, property]) => `${componentType(name, property, records)} ${name}`)
    .join(', ');
}

/** Condição `appliesTo` de uma variante, a partir dos codes que a declaram. */
function appliesToBody(codes) {
  if (codes.length === 1) return [`            return type == ProblemType.${codes[0]};`];
  return [
    '            return switch (type) {',
    ...codes.map((code) => `                case ${code} -> true;`),
    '                default -> false;',
    '            };',
  ];
}

function extensionVariant(ref, schema, records, codes) {
  const name = pascalCase(ref);
  const components = recordComponents(schema, records);
  return {
    name,
    lines: [
      `    /** Membros adicionais definidos por extensions/${ref}.schema.json. */`,
      `    record ${name}(${components}) implements ProblemExtensions {`,
      '',
      '        @Override',
      '        public boolean appliesTo(ProblemType type) {',
      ...appliesToBody(codes),
      '        }',
      '    }',
    ],
  };
}

export function generateJavaExtensions(catalog, extensionSchemas) {
  const refs = [
    ...new Set(
      catalog.entries
        .map((entry) => entry.extensionsSchemaRef)
        .filter((ref) => ref !== null && ref !== undefined),
    ),
  ].sort();

  const codesByRef = new Map(
    refs.map((ref) => [
      ref,
      catalog.entries.filter((entry) => entry.extensionsSchemaRef === ref).map((entry) => entry.code),
    ]),
  );

  const nested = new Map();
  const variants = refs.map((ref) =>
    extensionVariant(ref, extensionSchemas[ref].schema, nested, codesByRef.get(ref)),
  );
  const permits = ['None', ...variants.map((variant) => variant.name)]
    .map((name) => `ProblemExtensions.${name}`)
    .join(', ');

  const nestedRecords = [...nested.entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .flatMap(([name, schema]) => [
      '',
      `    /** Item tipado de uma extensão do catálogo. */`,
      `    record ${name}(${recordComponents(schema, nested)}) {`,
      '    }',
    ]);

  return `${[
    header('docs/contracts/problems/catalog.json e docs/contracts/problems/extensions/'),
    '',
    `package ${JAVA_PACKAGE};`,
    '',
    'import java.util.List;',
    '',
    '/**',
    ' * Membros adicionais permitidos no nível raiz de um Problem Details.',
    ' *',
    ' * <p>A interface é selada: um tipo do catálogo só aceita a variante declarada',
    ' * pelo seu {@code extensionsSchemaRef}, ou {@link None}.',
    ' */',
    `public sealed interface ProblemExtensions`,
    `        permits ${permits} {`,
    '',
    '    /** Indica se esta variante pertence ao tipo, conforme o catálogo. */',
    '    boolean appliesTo(ProblemType type);',
    '',
    '    /** Ausência de membros adicionais; aceita por qualquer tipo. */',
    '    record None() implements ProblemExtensions {',
    '',
    '        @Override',
    '        public boolean appliesTo(ProblemType type) {',
    '            return true;',
    '        }',
    '    }',
    '',
    '    /** Instância canônica para tipos sem extensão. */',
    '    static ProblemExtensions none() {',
    '        return new None();',
    '    }',
    ...variants.flatMap((variant) => ['', ...variant.lines]),
    ...nestedRecords,
    '}',
  ].join('\n')}\n`;
}
