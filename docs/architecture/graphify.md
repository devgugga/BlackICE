# Graphify no BlackICE

## Papel arquitetural

Graphify é tooling de engenharia local, não uma dependência do PACS. Ele produz
um mapa consultável do repositório para orientar investigação técnica; não
substitui a fonte, os Domain Packs nem a validação humana de decisões DICOM.

## Pré-requisitos e baseline

O baseline do projeto é a CLI `0.9.28`; a versão registrada em cada
`.graphify_version` das skills deve corresponder à CLI instalada. Confira o
ambiente e instale a versão reproduzível:

```powershell
uv --version
uv tool install "graphifyy==0.9.28"
graphify --version
```

Consulte o [README oficial](https://github.com/safishamsi/graphify/blob/v0.9.28/README.md),
a [release 0.9.28](https://github.com/safishamsi/graphify/releases/tag/v0.9.28),
a discussão de instalação project-scoped na [issue #817](https://github.com/safishamsi/graphify/issues/817)
e a página de [releases](https://github.com/safishamsi/graphify/releases) antes
de alterar o baseline.

## Instalação project-scoped

Execute no root do repositório. A instalação é versionada e restrita ao projeto,
para que a skill e as instruções não vazem para outros repositórios:

```powershell
graphify install --project
graphify install --project --platform agents
graphify claude install --project
graphify codex install --project
graphify hook install
```

Em Graphify `0.9.28`, `graphify install --project --platform agents` é o comando
que reproduz a skill em `.agents/skills/graphify`. Não usamos
`--platform codex` para essa etapa: ele também cria `.codex/skills`, um diretório
extra que não faz parte da política deste repositório. `graphify codex install
--project` continua sendo necessário para as instruções AGENTS e o hook do
Codex; `graphify claude install --project` faz o equivalente para Claude.

## Gerar o grafo

No Codex, invoque `$graphify .`; no Claude Code, use `/graphify .`. Em
PowerShell não use a barra inicial, pois ela é interpretada como separador de
caminho: use `graphify .`.

## Consultar antes de buscar amplamente

Comece a investigação pelas relações do grafo e só então abra ou pesquise a
base de código em largura:

```powershell
graphify query "como o frontend obtém a sessão autenticada?"
graphify explain "SessionResource"
graphify path "HomePage" "SessionResource"
```

## Atualizar

Há duas rotas de atualização, com escopos diferentes:

- Para mudanças somente de código, `graphify update .` e os hooks instalados
  por `graphify hook install` fazem a atualização AST local, sem LLM. O hook
  pós-commit ignora mudanças somente de documentação e imagens; ele é uma
  manutenção incremental de código, não uma reextração semântica completa.
- Para documentação, imagens ou uma mudança mista, use a skill no agente:
  `$graphify . --update` no Codex ou `/graphify . --update` no Claude Code.
  Essa rota segue o fluxo incremental completo, incluindo a extração semântica
  dos arquivos de conteúdo alterados e a regeneração dos artefatos.

Confira a instalação do hook quando precisar da manutenção automática de código:

```powershell
graphify hook status
```

### Workarounds project-scoped do Graphify 0.9.28

O runbook incremental versionado aplica dois ajustes locais de integridade:

- conserva o total do corpus em `total_files`, embora apenas os arquivos
  alterados sigam para extração;
- chama `build_merge(..., dedup=False)` para preservar IDs de proveniência e
  membros de hiperarestas; o dedup padrão da versão 0.9.28 remove IDs sem
  remapear esses membros.

Esses ajustes não mudam o full build. Ao atualizar o Graphify, revalidar ambos
contra a nova versão e remover os workarounds quando a correção upstream tornar
cada um desnecessário.

## Arquivos versionados e locais

Versionar os artefatos portáveis de `graphify-out/`. Não versionar `cost.json`,
`cache/`, logs, backups, `.graphify_python` ou `.graphify_root`: os dois últimos
guardam caminhos locais do interpretador e do checkout. O `.graphifyignore`
evita que a própria saída do Graphify e evidências que não são conhecimento
arquitetural retornem ao corpus; o `.claudeignore` evita injetar essa saída
gerada no cache de prompts.

## Segurança e autoridade

Não indexar `.env`, chaves, DSNs, dados de paciente ou pixel data. Confirmar
relações `INFERRED` e decisões DICOM na fonte canônica antes de utilizá-las.

## Upgrade

Ao atualizar, alinhe CLI e skills e só então regenere os artefatos:

```powershell
uv tool upgrade graphifyy
```

Reexecutar os quatro comandos de instalação project-scoped. Reexecutar
`graphify hook install`. Regenerar/atualizar o grafo e revisar os diffs.

## Diagnóstico no Windows

Se o shell não encontrar a CLI depois da instalação, atualize o ambiente de
shell, descubra o diretório de binários e abra um novo terminal:

```powershell
uv tool update-shell
uv tool dir --bin
```

Reabrir o terminal e verificar `graphify --version`.
