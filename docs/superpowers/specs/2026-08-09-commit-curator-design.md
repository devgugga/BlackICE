# Commit Curator — Design

## Objetivo

Padronizar os commits criados por agentes no BlackICE por meio de um subagente
portável entre Codex e Claude Code.

## Arquitetura

As regras independentes de ferramenta ficam no novo Domain Pack `docs/domains/git/`.
Dois wrappers finos, um em `.codex/agents/` e outro em `.claude/agents/git/`,
leem o mesmo documento e executam o commit. O registro de disponibilidade do
agente Claude fica em `CLAUDE.md`.

## Comportamento

- Em uma branch de trabalho, criar commit local automaticamente ao concluir uma
  tarefa que tenha alterações versionáveis e verificadas; não fazer push.
- Na branch `main`, exigir autorização explícita do humano antes de criar um
  commit, exceto quando uma autorização anterior para commits na `main` ainda
  estiver inequívoca no contexto da tarefa.
- Examinar estado e diffs Git antes de selecionar arquivos; não misturar trabalho
  sem relação em uma árvore compartilhada.
- Criar o commit diretamente, sem exibir mensagem para aprovação.
- Parar e explicar o motivo quando não for possível separar as mudanças com
  segurança.
- Redigir em português um título no formato `gitmoji verbo resultado: contexto`.
- Escolher o gitmoji semanticamente correspondente conforme gitmoji.dev.
- Usar corpo Markdown explicativo, com apenas as seções aplicáveis entre:
  `Novas funcionalidades`, `Melhorias na arquitetura`, `Boas práticas e
  validações`, `Permissões e controle de acesso` e `Resultado`.
- Não inventar capacidades, testes ou garantias não comprovadas pelo diff e
  pela verificação executada.
- Nunca incluir trailers `Co-authored-by`, `Co-authored-by:` ou qualquer
  atribuição de coautoria.

## Modelos

- Codex: `gpt-5.6-terra`, esforço `low`.
- Claude Code: `haiku`.

## Limites

O agente não cria branches, não envia commits remotos e não substitui os gates
humanos do projeto. Ele segue as regras do `AGENTS.md`, inclusive a atualização
do Graphify antes de commits que alterem arquivos versionados.

## Validação

Validar a sintaxe TOML e o frontmatter YAML, confirmar que ambos os wrappers
referenciam somente o Domain Pack compartilhado e verificar por busca que a
proibição de coautoria aparece na fonte canônica.
