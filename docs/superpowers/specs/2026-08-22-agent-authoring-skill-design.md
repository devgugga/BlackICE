# Desenho — skill de autoria de agentes

**Status:** proposto para revisão humana
**Data:** 2026-08-22

## Objetivo

Criar um workflow reutilizável para criar ou alterar subagentes de Claude Code,
Codex e Antigravity sem tornar nenhuma pasta de ferramenta a fonte de verdade.
O workflow deve escolher modelos por evidência atual e custo: o modelo elegível
menos caro para a tarefa, nunca automaticamente o mais capaz.

## Decisões

### Conhecimento neutro e wrappers

O conhecimento canônico ficará em `docs/domains/agent-authoring/`, marcado como
reutilizável. Ele conterá convenções, layouts por plataforma, validação e a
política de seleção de modelo. Os pontos de descoberta terão apenas instruções
de fluxo e links para esse pack:

```text
docs/domains/agent-authoring/        fonte de verdade
├── README.md                        escopo e limites
├── conventions.md                   fronteiras e wrappers finos
├── platform-layouts.md              Claude, Codex e Antigravity
├── model-selection.md               pesquisa e roteamento econômico
└── validation.md                    checklist de aceitação

.claude/skills/agent-authoring/      wrapper Claude
└── SKILL.md

.agents/skills/agent-authoring/      wrapper compartilhado Codex/Antigravity
└── SKILL.md
```

Não haverá `.codex/skills/`: Codex e Antigravity descobrem a skill
compartilhada em `.agents/skills/`, enquanto Claude a descobre em
`.claude/skills/`. Os wrappers não repetirão políticas ou especificações de
frontmatter; eles apontarão para os documentos canônicos.

### Seleção de modelo no momento da mudança

Antes de gravar ou alterar um agente, a skill deverá:

1. Definir a responsabilidade, ferramentas, permissões e risco da tarefa.
2. Pesquisar fontes oficiais atuais de Anthropic, OpenAI e Google: catálogo,
   capacidade, preço quando publicado e regras de configuração.
3. Verificar o que está disponível no ambiente e o que cada formato aceita.
   Uma opção indisponível, descontinuada ou não suportada no frontmatter é
   inelegível, mesmo que seja apresentada na documentação de um fornecedor.
4. Classificar a carga: simples/repetitiva, implementação rotineira,
   investigação/revisão de maior risco ou raciocínio especializado.
5. Escolher, para cada plataforma solicitada, o menor modelo/tier elegível e o
   menor esforço de raciocínio suficiente. A comparação é feita entre modelos
   realmente configuráveis naquela plataforma, não por nomes de marketing.
6. Registrar na proposta o fornecedor, modelo ou tier, esforço, data da
   pesquisa, URLs oficiais e uma justificativa curta. Para tiers, registrar
   também o modelo efetivo observado no ambiente, quando ele for exposto.

Agentes simples devem começar no tier/modelo econômico. Escalonamento exige
evidência concreta de que esse candidato não satisfaz a tarefa (por exemplo,
falha repetível, ferramentas insuficientes ou risco que demanda revisão mais
profunda), e é apresentado ao humano antes de aumentar custo. A skill não muda
modelos de agentes existentes sem autorização explícita.

O documento não listará modelos fixos: catálogos e preços mudam rapidamente.
Ele definirá fontes e critérios de decisão, não resultados que envelhecem.

### Sem subagente de autoria na primeira versão

Esta skill executa um workflow no contexto principal. Um revisor isolado de
configuração de agentes não será criado agora: ele só se justifica se auditorias
independentes de permissões, layouts e seleção de modelo se tornarem recorrentes.
Os subagentes atuais permanecem responsáveis exclusivamente pelos seus domínios
de produto.

### Segurança, governança e validação

A skill preservará o gate humano para mudanças de maior custo, permissões mais
amplas, semântica de domínio ou efeitos externos. A validação verificará
frontmatter/formato, descoberta no runtime aplicável, referências ao Domain Pack
e ausência de duplicação. A criação de agentes não autoriza criar commits nem
alterar código de produto.

## Alterações documentais previstas

- Adicionar o Domain Pack `agent-authoring` e os dois wrappers finos.
- Ajustar `AGENTS.md`, `docs/domains/README.md`,
  `docs/architecture/project-structure.md` e `.claude/skills/README.md` para
  refletir a fonte de verdade neutra e os dois locais de descoberta de skills.
- Criar um plano de implementação com comandos de validação estática e uma
  verificação de descoberta nos CLIs disponíveis.

## Fora de escopo

- Um catálogo local ou uma regra permanente de “melhor modelo”.
- Uma skill específica para commits ou alteração do curador de commits.
- Scripts de automação sem uma necessidade determinística comprovada.
- Alterar modelos dos agentes já existentes sem nova decisão humana.
