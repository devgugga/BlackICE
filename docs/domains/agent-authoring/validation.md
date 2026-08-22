# Validação de uma alteração

Execute as verificações proporcionais ao papel e registre somente resultados
observados. Uma alteração não está pronta por ter um frontmatter sintaticamente
válido.

## Checklist

1. Confirme que o pedido define papel, gatilho, consumidores, ferramentas,
   permissões e limites.
2. Leia os Domain Packs referenciados e confirme que o wrapper não duplica seu
   conhecimento.
3. Faça e apresente a pesquisa atual de Anthropic, OpenAI e Google conforme
   [`model-selection.md`](./model-selection.md), incluindo disponibilidade local
   e justificativa do menor candidato elegível.
4. Valide o formato da plataforma: YAML para Markdown com frontmatter e
   `tomllib` para TOML. Confira `name`, `description` e os campos aceitos pela
   versão instalada.
5. Confirme o local de descoberta em
   [`platform-layouts.md`](./platform-layouts.md). Use somente CLIs disponíveis;
   uma ferramenta ausente é uma limitação registrada, não um motivo para simular
   descoberta.
6. Revise as ferramentas e permissões: leitura por padrão; escrita, comandos,
   rede e efeitos externos somente quando comprovadamente necessários.
7. Para alterações versionadas, execute `git diff --check`, atualize Graphify
   conforme `AGENTS.md` e revise todos os artefatos versionados alterados.

## Cenários de pressão

- Um pedido para “usar o melhor modelo” deve resultar em uma classificação e na
  comparação com o menor candidato elegível, não em uma escolha automática de
  topo de linha.
- Um pedido para copiar regras DICOM para um prompt deve mover a regra para o
  Domain Pack e manter o wrapper como referência.
- Um pedido para usar um modelo anunciado, mas indisponível no runtime, deve
  registrar a inelegibilidade e escolher somente entre candidatos configuráveis.
- Um pedido que amplia permissões, custo ou um modelo existente deve parar no
  gate humano antes da gravação.
