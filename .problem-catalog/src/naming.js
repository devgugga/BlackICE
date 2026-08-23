/** Conversões de nome usadas pelos geradores. Determinísticas e sem estado. */

export function words(ref) {
  return ref.split('-').filter((part) => part.length > 0);
}

export function pascalCase(ref) {
  return words(ref)
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join('');
}

export function screamingSnakeCase(ref) {
  return words(ref)
    .map((part) => part.toUpperCase())
    .join('_');
}

/**
 * Singular usado para nomear o item de uma coleção (`violations` -> `Violation`).
 * Cobre apenas o plural regular em `s`, que é o que o contrato usa.
 */
export function singularize(name) {
  if (name.endsWith('ies')) return `${name.slice(0, -3)}y`;
  if (name.endsWith('ses')) return name.slice(0, -2);
  if (name.endsWith('s')) return name.slice(0, -1);
  return name;
}

export function upperFirst(name) {
  return name.length === 0 ? name : name[0].toUpperCase() + name.slice(1);
}

/** Literal de string Java/TypeScript com aspas simples ou duplas escapadas. */
export function quote(value, mark = "'") {
  const escaped = String(value).replaceAll('\\', '\\\\').replaceAll(mark, `\\${mark}`);
  return `${mark}${escaped}${mark}`;
}
