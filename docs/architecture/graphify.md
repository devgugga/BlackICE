# Graphify no BlackICE

## Papel arquitetural

Graphify é tooling de engenharia local, não uma dependência do PACS. Ele produz
um mapa consultável do repositório para orientar investigação técnica; não
substitui a fonte, os Domain Packs nem a validação humana de decisões DICOM.

## Pré-requisitos e baseline

O baseline do projeto é a CLI `0.9.28`; a versão registrada em cada
`.graphify_version` das skills deve corresponder à CLI instalada. Confira o
ambiente:

```powershell
uv --version
graphify --version
```

Consulte o [README oficial](https://github.com/safishamsi/graphify/blob/v0.9.28/README.md),
a [release 0.9.28](https://github.com/safishamsi/graphify/releases/tag/v0.9.28),
a discussão de instalação project-scoped na [issue #817](https://github.com/safishamsi/graphify/issues/817)
e a página de [releases](https://github.com/safishamsi/graphify/releases) antes
de alterar o baseline.

## Instalação project-scoped

Execute no root do repositório:

```powershell
powershell -ExecutionPolicy Bypass -File .graphify/setup.ps1
```

O script instala exatamente `graphifyy==0.9.28`, executa os quatro instaladores
project-scoped recomendados, restaura do `HEAD` as skills BlackICE revisadas,
valida seus checksums e instala os hooks locais. Ele recusa execução quando
essas skills têm mudanças locais, para não apagar trabalho em andamento.

Não execute os instaladores project-scoped isoladamente. Em `0.9.28`, eles
substituem `SKILL.md` e todo o diretório `references/`, removendo silenciosamente
os ajustes de integridade do BlackICE. Para conferir o overlay sem instalar:

```powershell
powershell -ExecutionPolicy Bypass -File .graphify/setup.ps1 -VerifyOnly
```

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
graphify explain "HomePage"
```

## Atualizar

Há duas rotas de atualização, com escopos diferentes:

- Em um checkout Git normal, mudanças somente de código podem usar
  `graphify update .`; os hooks instalados também fazem essa atualização AST
  local, sem LLM. O hook pós-commit ignora configuração, documentação e imagens.
- Em linked worktrees, os hooks oficiais de `0.9.28` retornam sem atualizar o
  grafo, embora `graphify hook status` ainda mostre `installed`. Antes de cada
  commit de tarefa, a atualização pela skill é obrigatória nesse contexto.
- Para configuração, documentação, imagens ou qualquer mudança mista, use a skill:
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

Além disso:

- `.graphify/project.py` acrescenta ao passe semântico configurações textuais
  rastreadas que o detector oficial não classifica, mantendo `.env`, arquivos
  sensíveis, builds e `graphify-out/` fora do corpus;
- a etapa de comunidades exige keys exatas, rejeita placeholders/colapso de
  labels e grava assinaturas de membresia para invalidar reuso automático;
- `.graphify/setup.ps1` restaura e valida essas alterações depois de qualquer
  instalação project-scoped.

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

Também não versionar `.codex/hooks.json` nem `.claude/settings.json`: o instalador
oficial grava neles o caminho absoluto do executável desta máquina. O setup os
regenera localmente. O caminho absoluto do merge driver fica somente em
`.git/config`, que já é metadata local.

Quando a extração semântica é feita pelo próprio agente, o Graphify 0.9.28 não
recebe a telemetria de tokens do host; por isso `0 input · 0 output` no relatório
significa “não registrado”, não custo real zero.

## Segurança e autoridade

Não indexar `.env`, chaves, DSNs, dados de paciente ou pixel data. Confirmar
relações `INFERRED` e decisões DICOM na fonte canônica antes de utilizá-las.

## Upgrade

Não execute `uv tool upgrade graphifyy` sem antes alterar deliberadamente o
baseline do projeto. Para um upgrade: revisar as correções upstream, atualizar a
versão e os checksums em `.graphify/setup.ps1`, regenerar as duas skills,
reaplicar somente os workarounds ainda necessários, executar o setup e então
reconstruir/revisar o grafo completo.

## Diagnóstico no Windows

Se o shell não encontrar a CLI depois da instalação, atualize o ambiente de
shell, descubra o diretório de binários e abra um novo terminal:

```powershell
uv tool update-shell
uv tool dir --bin
```

Reabrir o terminal e verificar `graphify --version`.
