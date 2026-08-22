# Convenções de autoria de agentes

## Fronteiras

- **Domain Pack:** conhecimento e política canônicos, neutros de ferramenta.
- **Skill:** workflow repetível no contexto principal; orienta uma criação ou
  alteração quando solicitada.
- **Subagente:** papel isolado, com responsabilidade recorrente, ferramentas e
  permissões mínimas. Não o crie apenas para executar a skill.

Use uma skill antes de criar um novo subagente quando o trabalho demanda
contexto do pedido, comparação de opções ou gate humano. Crie um subagente
apenas quando houver uma responsabilidade independente que se repete e se
beneficia de contexto e permissões isolados.

## Fonte única de verdade

1. Crie ou atualize o conhecimento em `docs/domains/<dominio>/` antes do
   wrapper específico de plataforma.
2. Faça o corpo do agente ou skill instruir a leitura dos documentos canônicos;
   não copie regras de domínio, checklists de negócio ou decisões DICOM.
3. Mantenha no wrapper somente a configuração inevitavelmente específica da
   ferramenta: identidade, descrição de acionamento, modelo, esforço,
   ferramentas e permissões.
4. Ao corrigir uma regra, altere o documento canônico; não sincronize cópias em
   três prompts.

## Escopo e autorização

Antes de editar, explicite o papel, os consumidores, as ferramentas necessárias
e os efeitos externos. Uma mudança de agente não autoriza mudança no produto,
permissões externas, criação de commit, branch ou push.

Peça confirmação humana antes de ampliar permissões, elevar custo, alterar um
agente existente, ou tomar uma decisão de domínio que já tenha gate humano.
Para DICOM/DICOMweb, aplique o pack `docs/domains/dicom/` e mantenha o gate de
semântica e integridade de paciente.

## Forma de um wrapper

Uma descrição curta deve indicar **quando** o papel se aplica. O corpo deve
indicar os documentos que governam seu trabalho e os limites relevantes. Não
inclua catálogos de modelos, preços, regras de commit ou conhecimento de produto
quando eles já pertencem a um Domain Pack.

Consulte [`platform-layouts.md`](./platform-layouts.md) para formato e local, e
[`model-selection.md`](./model-selection.md) antes de escolher o modelo.
