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
- Usar corpo Markdown explicativo, **obrigatório** em todo commit que não seja a
  sincronização isolada de `graphify-out/`. As seções aplicáveis, sua
  renderização exata (`### <emoji> <nome>`, separador `---`) e um exemplo
  canônico completo vivem no Domain Pack.
- Reportar ao orquestrador o `git log -1 --stat` do commit criado, os arquivos
  deixados de fora e as validações executadas — o commit é feito sem exibir
  rascunho, então esse relato é a única janela do humano sobre o resultado.
- Não inventar capacidades, testes ou garantias não comprovadas pelo diff e
  pela verificação executada.
- Nunca incluir trailers `Co-authored-by`, `Co-authored-by:` ou qualquer
  atribuição de coautoria.

## Modelos

- Codex: `gpt-5.6-luna`, esforço `medium`.
- Claude Code: `haiku`.

Decisão deliberada de manter modelos econômicos. Redigir o corpo é preenchimento
de um template explícito, não invenção de formato: o Domain Pack carrega o
exemplo canônico completo, então a tarefa é copiar a renderização e substituir o
conteúdo pelo que o diff comprova. O esforço do Codex subiu de `low` para
`medium` apenas para cobrir a leitura do diff e a escolha do gitmoji.

## Limites

O agente não cria branches, não envia commits remotos e não substitui os gates
humanos do projeto. Ele segue as regras do `AGENTS.md`, inclusive a atualização
do Graphify antes de commits que alterem arquivos versionados.

## Validação

Validar a sintaxe TOML e o frontmatter YAML, confirmar que ambos os wrappers
referenciam somente o Domain Pack compartilhado e verificar por busca que a
proibição de coautoria aparece na fonte canônica.
