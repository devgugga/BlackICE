import { describe, expect, it } from 'vitest';

import { PROBLEM_MESSAGES, problemMessage } from './problem-messages.pt-BR';
import { PROBLEM_CODES } from './problem-types.generated';

describe('mensagens PT-BR', () => {
  it('cobre exaustivamente todo code do catálogo', () => {
    for (const code of PROBLEM_CODES) {
      expect(PROBLEM_MESSAGES[code], `sem mensagem para ${code}`).toBeTruthy();
    }
    expect(Object.keys(PROBLEM_MESSAGES).sort()).toEqual([...PROBLEM_CODES].sort());
  });

  it('resolve a mensagem de um code', () => {
    expect(problemMessage('API_ARCHIVE_UNAVAILABLE')).toBe(
      'O Archive está temporariamente indisponível.',
    );
  });

  it('orienta a solicitar verificação quando o resultado do Archive é incerto', () => {
    expect(problemMessage('API_ARCHIVE_OUTCOME_UNKNOWN')).toBe(
      'Não foi possível confirmar o resultado no Archive. Use a referência exibida para solicitar verificação.',
    );
  });

  it('orienta quando a imagem DICOM não é suportada', () => {
    expect(problemMessage('CLIENT_DICOM_IMAGE_UNSUPPORTED')).toBe(
      'Esta imagem DICOM não é compatível com este visualizador. Selecione outra série.',
    );
  });

  it('orienta quando a operação conflita com o estado do laudo', () => {
    expect(problemMessage('API_RESOURCE_CONFLICT')).toBe(
      'O laudo está em um estado que não permite esta operação.',
    );
  });

  it('orienta quando o laudo foi alterado concorrentemente', () => {
    expect(problemMessage('API_RESOURCE_VERSION_CONFLICT')).toBe(
      'O laudo foi alterado em outra sessão. Revise a versão atual antes de continuar.',
    );
  });

  it('não expõe jargão interno ao usuário', () => {
    for (const [code, message] of Object.entries(PROBLEM_MESSAGES)) {
      expect(message, `${code} vaza o code`).not.toContain('API_');
      expect(message, `${code} vaza o code`).not.toContain('CLIENT_');
      expect(message, `${code} cita HTTP`).not.toMatch(/\bHTTP\b|\b[45]\d\d\b/);
    }
  });
});
