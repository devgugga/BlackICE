# Graphify no BlackICE

## Papel arquitetural

Graphify é tooling de engenharia local, não uma dependência do PACS. Ele produz
um mapa consultável do repositório para orientar investigação técnica; não
substitui a fonte, os Domain Packs nem a validação humana de decisões DICOM.

## Pré-requisitos e baseline

O baseline do projeto é a CLI `0.9.32` (subiu de `0.9.28` em 2026-08-09); a
versão registrada em cada `.graphify_version` das skills deve corresponder à CLI
instalada. Confira o ambiente:

```powershell
uv --version
graphify --version
```

Consulte o [README oficial](https://github.com/safishamsi/graphify/blob/v0.9.32/README.md),
a [release 0.9.32](https://github.com/safishamsi/graphify/releases/tag/v0.9.32),
a discussão de instalação project-scoped na [issue #817](https://github.com/safishamsi/graphify/issues/817)
e a página de [releases](https://github.com/safishamsi/graphify/releases) antes
de alterar o baseline.

## Instalação project-scoped

Execute no root do repositório:

```powershell
powershell -ExecutionPolicy Bypass -File .graphify/setup.ps1
```

O script instala exatamente `graphifyy==0.9.32`, executa os quatro instaladores
project-scoped recomendados, restaura do `HEAD` as skills BlackICE revisadas,
valida seus checksums e instala os hooks locais. Ele recusa execução quando
essas skills têm mudanças locais, para não apagar trabalho em andamento.

Não execute os instaladores project-scoped isoladamente. Em `0.9.32` — como em
`0.9.28` — eles
substituem `SKILL.md` e todo o diretório `references/`, removendo silenciosamente
os ajustes de integridade do BlackICE. Para conferir o overlay sem instalar:

```powershell
powershell -ExecutionPolicy Bypass -File .graphify/setup.ps1 -VerifyOnly
```

**Dois arquivos que o setup NÃO protege.** Os instaladores reescrevem a seção
`## graphify` de `AGENTS.md` e de `CLAUDE.md`, trocando a redação **opcional**
adotada por este projeto ("Guidance (optional)… You may instead use other
appropriate approaches") por "Rules:" imperativo. Eles estão fora de
`$CanonicalHashes` de propósito — são prosa do projeto, não overlay da skill.
Depois de qualquer execução do setup, confira `git diff AGENTS.md CLAUDE.md` e
restaure a redação opcional. Observado no bump para `0.9.32`.

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

### Momento da atualização em uma feature

A atualização de fechamento acontece **uma única vez**, depois que a
implementação, os testes, as correções de revisão e os gates da feature estão
estáveis, imediatamente antes do commit. Não execute o fluxo incremental entre
rodadas intermediárias: cada execução semântica pode repetir extração,
reagrupamento de comunidades e geração de labels sobre um estado que ainda será
alterado.

Só antecipe uma atualização quando:

- o humano pedir explicitamente `/graphify`;
- o grafo for necessário para investigar a tarefa em andamento; ou
- uma fase independente estiver concluída e for receber seu próprio commit.

Uma fase com commit próprio é uma unidade finalizada, não uma rodada provisória
de implementação ou revisão. Depois de uma atualização antecipada para
investigação, a feature ainda exige a atualização única de fechamento caso o
conteúdo tenha mudado.

Há duas rotas de atualização, com escopos diferentes:

- Em um checkout Git normal, mudanças somente de código podem usar
  `graphify update .`; os hooks instalados também fazem essa atualização AST
  local, sem LLM. O hook pós-commit ignora configuração, documentação e imagens.
- Em linked worktrees, os hooks oficiais retornam sem atualizar o
  grafo, embora `graphify hook status` ainda mostre `installed`. No fechamento
  de cada feature ou fase independente, a atualização pela skill é obrigatória
  nesse contexto.
  **Continua valendo em `0.9.32`** — `graphify/hooks.py` ainda compara
  `--git-dir` com `--git-common-dir` e sai cedo quando diferem.
- Para configuração, documentação, imagens ou qualquer mudança mista, use a skill:
  `$graphify . --update` no Codex ou `/graphify . --update` no Claude Code.
  Essa rota segue o fluxo incremental completo, incluindo a extração semântica
  dos arquivos de conteúdo alterados e a regeneração dos artefatos.

Confira a instalação do hook quando precisar da manutenção automática de código:

```powershell
graphify hook status
```

### Fluxo de commits com o hook

Em checkout normal, `graphify hook install` instala um hook pós-commit que
reconstrói a camada AST após commits de código. Por isso, alterações em
`graphify-out/` depois do commit são comportamento esperado, não uma falha do
Git nem artefatos a descartar.

Como os artefatos portáveis de `graphify-out/` são versionados neste projeto, o
protocolo é:

1. Com a feature estável, execute uma vez a atualização semântica pela skill e
   revise o diff de `graphify-out/`.
2. Faça o commit da feature sem incluir `graphify-out/`.
3. Aguarde o hook terminar. Se ele atualizar a camada AST, revise o diff final e
   valide que ele ainda representa a mudança.
4. Faça um segundo commit, focado apenas na sincronização do grafo.

O hook do Graphify ignora commits que alteram somente `graphify-out/`, evitando
um loop de reconstrução. Ele continua ignorando documentação, configuração e
imagens: para essas mudanças, ou para uma alteração mista, rode a atualização
incremental pela skill antes de criar o commit de sincronização. Não desinstale
ou altere o hook para obter um worktree limpo sem decisão explícita do humano;
isso abandona o fluxo oficial de grafo compartilhado.

### Workarounds project-scoped do Graphify

**Revalidados contra `0.9.32` em 2026-08-09: nenhum foi corrigido upstream, os
três continuam necessários.** As correções de hiperaresta estão previstas para
`0.9.34` e as de label de comunidade para `0.9.36`. Os arquivos de skill de
origem são byte-a-byte idênticos entre `0.9.28` e `0.9.32` (só
`.graphify_version` muda), então o overlay se aplica sem reescrita e os
checksums em `.graphify/setup.ps1` não precisaram ser recalculados.

O runbook incremental versionado aplica dois ajustes locais de integridade:

- conserva o total do corpus em `total_files`, embora apenas os arquivos
  alterados sigam para extração;
- chama `build_merge(..., dedup=False)` para preservar IDs de proveniência e
  membros de hiperarestas; o dedup padrão remove IDs sem
  remapear esses membros.

Além disso:

- `.graphify/project.py` acrescenta ao passe semântico configurações textuais
  rastreadas que o detector oficial não classifica, mantendo `.env`, arquivos
  sensíveis, builds e `graphify-out/` fora do corpus;
- a etapa de comunidades exige keys exatas, rejeita placeholders/colapso de
  labels e grava assinaturas de membresia para invalidar reuso automático;
- `.graphify/setup.ps1` restaura e valida essas alterações depois de qualquer
  instalação project-scoped.

**Os checksums de `$CanonicalHashes` são hashes do conteúdo do blob Git (LF).**
Este checkout usa `core.autocrlf=true`, então a árvore de trabalho tem CRLF e um
`Get-FileHash` cru nunca bate — era por isso que o setup abortava antes de
`graphify hook install` rodar, deixando o `post-commit` preso a um pin antigo. O
`Get-NormalizedHash` do setup remove o CR antes do LF e compara conteúdo, não
fim de linha; funciona igual num checkout Linux. **Não “conserte” isso
regravando hashes da árvore de trabalho** — isso quebraria o inverso.

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

Os instaladores também escrevem duas saídas **stock** que este projeto não
versiona, porque não são a fonte da verdade e o setup as regenera:

- `.codex/skills/graphify/` — cópia sem o overlay do BlackICE. A skill Codex
  canônica é `.agents/skills/graphify/`, e é ela que `AGENTS.md` manda usar.
- `.claude/CLAUDE.md` — ponteiro de três linhas para `.claude/skills/graphify/`,
  fora dos caminhos que o Claude Code carrega (`./CLAUDE.md`, `~/.claude/CLAUDE.md`).

Ambas estão no `.gitignore` desde `0.9.32`.

Quando a extração semântica é feita pelo próprio agente, o Graphify não
recebe a telemetria de tokens do host; por isso `0 input · 0 output` no relatório
significa “não registrado”, não custo real zero.

## Segurança e autoridade

Não indexar `.env`, chaves, DSNs, dados de paciente ou pixel data. Confirmar
relações `INFERRED` e decisões DICOM na fonte canônica antes de utilizá-las.

## Upgrade

Não execute `uv tool upgrade graphifyy` sem antes alterar deliberadamente o
baseline do projeto. A receita, na ordem:

1. Revisar as correções upstream e decidir quais workarounds desta página caem.
2. Atualizar `$ExpectedVersion` e, **se os arquivos de skill stock mudarem**, os
   `$CanonicalHashes` em `.graphify/setup.ps1`. Compare os arquivos stock das
   duas versões antes de recalcular: em `0.9.28 → 0.9.32` só `.graphify_version`
   mudou, e recalcular teria sido ruído.
3. Regenerar as duas skills e reaplicar somente os workarounds ainda necessários.
4. Executar `.graphify/setup.ps1`.
5. **Conferir `git diff AGENTS.md CLAUDE.md`** e restaurar a redação opcional —
   os instaladores reescrevem a seção `## graphify` dos dois para "Rules:"
   imperativo, e eles ficam fora de `$CanonicalHashes` de propósito.
6. Conferir `graphify hook status` e o pin do `.git/hooks/post-commit`: ele
   guarda o caminho absoluto do interpretador, que pode ter ficado de outra
   máquina ou outro perfil de usuário. `hook uninstall` + `hook install`
   corrigem. O `hook uninstall` apaga o `.gitattributes` versionado e o
   `install` o recria — confira o diff antes de commitar.
7. Reconstruir/revisar o grafo completo, **com a árvore de trabalho limpa**: um
   full build indexa o que está em disco, então rodá-lo sobre mudanças não
   commitadas grava no grafo um estado que nunca vira commit.

## Diagnóstico no Windows

Se o shell não encontrar a CLI depois da instalação, atualize o ambiente de
shell, descubra o diretório de binários e abra um novo terminal:

```powershell
uv tool update-shell
uv tool dir --bin
```

Reabrir o terminal e verificar `graphify --version`.
