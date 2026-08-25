import type { ProblemCode } from './problem-types.generated';

/**
 * Texto exibido ao usuário para cada problema catalogado.
 *
 * <p>O `satisfies` abaixo é o que torna o mapa exaustivo: um code novo no
 * catálogo quebra o build do frontend até ganhar sua mensagem, em vez de chegar
 * à tela como texto em branco.
 *
 * <p>As mensagens não citam status HTTP nem o code interno: elas dizem ao
 * usuário o que aconteceu e o que fazer. O `detail` do backend, em inglês e
 * voltado a operadores, nunca é renderizado.
 */
export const PROBLEM_MESSAGES = {
  API_REQUEST_INVALID: 'A requisição é inválida. Revise os dados informados.',
  API_UPLOAD_EMPTY: 'Selecione ao menos um arquivo para importar.',
  API_SEARCH_INVALID: 'Revise os filtros de busca informados.',
  API_AUTHENTICATION_REQUIRED: 'Sua sessão não está ativa. Entre novamente.',
  API_ACCESS_DENIED: 'Você não tem permissão para realizar esta ação.',
  API_CSRF_INVALID:
    'Não foi possível verificar a requisição. Atualize a página e tente novamente.',
  API_RESOURCE_NOT_FOUND: 'O recurso solicitado não foi encontrado.',
  API_METHOD_NOT_ALLOWED: 'Esta operação não é permitida neste recurso.',
  API_REPRESENTATION_NOT_ACCEPTABLE: 'O formato de resposta solicitado não é suportado.',
  API_PAYLOAD_TOO_LARGE: 'O conteúdo enviado excede o limite permitido.',
  API_SEARCH_TOO_BROAD: 'A busca retornou muitos resultados. Use filtros mais restritos.',
  API_MEDIA_TYPE_UNSUPPORTED: 'O formato do conteúdo enviado não é suportado.',
  API_DICOM_VALIDATION_FAILED: 'Nenhum arquivo enviado passou pela validação DICOM.',
  API_INTERNAL_ERROR: 'Ocorreu uma falha inesperada. Tente novamente.',
  API_ARCHIVE_OUTCOME_UNKNOWN:
    'Não foi possível confirmar o resultado no Archive. Use a referência exibida para solicitar verificação.',
  API_ARCHIVE_RESPONSE_INVALID: 'O Archive retornou uma resposta inválida.',
  API_ARCHIVE_UNAVAILABLE: 'O Archive está temporariamente indisponível.',
  CLIENT_NETWORK_UNAVAILABLE: 'Não foi possível alcançar o servidor.',
  CLIENT_REQUEST_TIMEOUT: 'A operação excedeu o tempo de espera.',
  CLIENT_RESPONSE_INVALID: 'O servidor retornou uma resposta inválida.',
  CLIENT_CSRF_COOKIE_MISSING: 'Não foi possível preparar a verificação de segurança.',
  CLIENT_DICOM_IMAGE_UNSUPPORTED:
    'Esta imagem DICOM não é compatível com este visualizador. Selecione outra série.',
  CLIENT_UNEXPECTED_ERROR: 'Ocorreu uma falha inesperada no navegador.',
} satisfies Record<ProblemCode, string>;

export function problemMessage(code: ProblemCode): string {
  return PROBLEM_MESSAGES[code];
}
