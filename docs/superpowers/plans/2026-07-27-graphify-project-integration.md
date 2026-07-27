# Graphify Project Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install Graphify as versioned project tooling for Codex and Claude,
generate a shared full-repository knowledge graph, and document a reproducible
query/update workflow for future agents.

**Architecture:** Graphify runs outside the PACS runtime as an isolated `uv
tool`. Official project-scoped skills and always-on integrations teach agents to
query the versioned `graphify-out/` corpus, while a local Git hook maintains its
AST layer and a tracked merge attribute makes concurrent graph updates safe.

**Tech Stack:** Graphify 0.9.28 (`graphifyy`), uv, Codex project skills, Claude
Code project skills, Git hooks/merge drivers, Markdown, PowerShell.

## Global Constraints

- Use the official PyPI package `graphifyy`; the executable remains `graphify`.
- Pin and verify Graphify 0.9.28 for this integration baseline.
- Index tracked code, configuration, and Markdown documentation from the
  BlackICE repository root.
- Do not use `--no-gitignore`; local secrets and ignored worktrees must remain
  outside the corpus.
- Version portable `graphify-out/` artifacts; ignore `cost.json`, cache, logs,
  and installer backups.
- Install both Codex and Claude Code skills in project scope.
- Preserve all pre-existing `AGENTS.md`, `CLAUDE.md`, `.codex/`, and `.claude/`
  content.
- Keep Graphify out of Quarkus, Vue, Docker, Compose, and production runtime
  dependencies.
- Use the default soft nudge for Claude; do not enable strict mode.
- Do not configure MCP, Neo4j, FalkorDB, live PostgreSQL, or CI extraction.
- After each task passes independent review, create one focused commit using a
  lower-cost model and the repository's emoji-prefixed Portuguese commit style.
  This is explicitly authorized for this plan by the repository owner.
- Configuration and generated-artifact work uses CLI acceptance tests rather
  than unit tests; no production code is added.

---

### Task 1: Install the official project-scoped agent integrations

**Files:**

- Modify: `.codex/config.toml`
- Modify by official installer: `AGENTS.md`
- Modify by official installer: `CLAUDE.md`
- Create by official installer: `.agents/skills/graphify/SKILL.md`
- Create by official installer: `.agents/skills/graphify/references/`
- Create by official installer: `.claude/skills/graphify/SKILL.md`
- Create by official installer: `.claude/skills/graphify/references/`
- Create or modify by official installer: `.codex/hooks.json`
- Create or modify by official installer: `.claude/settings.json`
- Create by official installer: version stamp files beside the installed skills

**Interfaces:**

- Consumes: `uv 0.11.21+`, the repository-root `AGENTS.md`/`CLAUDE.md`, and
  Graphify's project installer.
- Produces: `$graphify` for fresh Codex sessions, `/graphify` for fresh Claude
  sessions, soft graph-first instructions, and Codex multi-agent extraction.

- [ ] **Step 1: Capture the clean baseline and protect existing instructions**

Run from `C:\Users\Usuario\Projects\BlackICE`:

```powershell
git status --short
Get-FileHash AGENTS.md, CLAUDE.md, .codex/config.toml -Algorithm SHA256
Get-Content -Raw AGENTS.md
Get-Content -Raw CLAUDE.md
Get-Content -Raw .codex/config.toml
```

Expected: only the approved Graphify spec and this plan are untracked; the
existing project/domain instructions are readable and can be compared after the
installer runs.

- [ ] **Step 2: Run the pre-install acceptance checks and observe failure**

```powershell
graphify --version
Test-Path .agents/skills/graphify/SKILL.md
Test-Path .claude/skills/graphify/SKILL.md
```

Expected: `graphify` raises `CommandNotFoundException`, and both `Test-Path`
commands print `False`.

- [ ] **Step 3: Install the pinned CLI with the recommended isolated tool flow**

```powershell
uv tool install "graphifyy==0.9.28"
graphify --version
```

Expected: uv reports a successful isolated tool install and the second command
reports Graphify 0.9.28. If the executable is not yet on `PATH`, run:

```powershell
uv tool update-shell
uv tool dir --bin
```

Open a fresh terminal using the printed tool-bin path before continuing; do not
fall back to a global `pip install`.

- [ ] **Step 4: Install the two official skills in project scope**

```powershell
graphify install --project
graphify install --project --platform codex
```

Expected: the first command installs the Windows/Claude project skill under
`.claude/skills/graphify/`; the second installs the Codex skill under
`.agents/skills/graphify/`. Both include their `references/` sidecars and
version stamps, and both print a Git add hint rather than writing to the user
profile.

- [ ] **Step 5: Install the always-on project integrations**

```powershell
graphify claude install --project
graphify codex install --project
```

Expected: the Claude command creates or merges `.claude/settings.json` and its
marked instructions without strict mode; the Codex command creates or merges
`.codex/hooks.json` and a marked `## graphify` block in `AGENTS.md`. Existing
JSON/TOML/Markdown content remains intact. Any parse failure stops this task;
never replace an existing configuration with an empty object.

- [ ] **Step 6: Enable the Codex feature required for parallel extraction**

Modify `.codex/config.toml` so it contains exactly one features table while
preserving the existing agents table:

```toml
[features]
multi_agent = true

[agents]
max_concurrent_threads_per_session = 3
default_subagent_model = "gpt-5.6-terra"
default_subagent_reasoning_effort = "medium"
```

- [ ] **Step 7: Verify installation and audit the generated diff**

```powershell
graphify --version
Test-Path .agents/skills/graphify/SKILL.md
Test-Path .claude/skills/graphify/SKILL.md
Get-ChildItem .agents/skills/graphify -Recurse
Get-ChildItem .claude/skills/graphify -Recurse
Get-Content -Raw .codex/hooks.json | ConvertFrom-Json | Out-Null
Get-Content -Raw .claude/settings.json | ConvertFrom-Json | Out-Null
git diff -- AGENTS.md CLAUDE.md .codex/config.toml
git status --short
```

Expected: version 0.9.28; both skills exist; both JSON files parse; the diff
shows only marked Graphify additions plus `multi_agent = true`; no original
BlackICE instruction is removed.

- [ ] **Step 8: Pass the Task 1 review and create its focused commit**

Present the generated paths and the complete instruction/config diff. Do not
commit before the independent task reviewer approves both spec compliance and
quality. After approval, commit with:

```powershell
git commit -m "🔧 configura Graphify para Codex e Claude"
```

---

### Task 2: Define corpus, versioning, and onboarding policy

**Files:**

- Create: `.graphifyignore`
- Create: `.claudeignore`
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `docs/architecture/project-structure.md`
- Create: `docs/architecture/graphify.md`

**Interfaces:**

- Consumes: official Graphify ignore semantics and the installed CLI from Task
  1.
- Produces: a deterministic tracked corpus policy and the canonical operational
  guide used by humans and agents.

- [ ] **Step 1: Run policy checks before the files exist**

```powershell
Test-Path .graphifyignore
Test-Path .claudeignore
git check-ignore graphify-out/cost.json
git check-ignore graphify-out/cache/stat-index.json
```

Expected: both `Test-Path` calls print `False`; both `git check-ignore` commands
return exit code 1 because Graphify local state is not yet covered.

- [ ] **Step 2: Create the Graphify-specific corpus exclusions**

Create `.graphifyignore` with:

```gitignore
# Never recursively index Graphify's own generated corpus.
graphify-out/

# Visual-regression evidence is not source or architecture knowledge.
apps/frontend/e2e/**/*.png

# Installer safety backups and local diagnostics are not canonical input.
*.graphify-bak
*.graphify.log
```

This file adds exclusions only. `.gitignore` remains active and continues to
exclude `.env`, dependencies, build outputs, scratch data, and worktrees.

- [ ] **Step 3: Ignore only non-portable Graphify output in Git**

Append this focused section to `.gitignore`:

```gitignore

# graphify local-only state; portable graphify-out/ artifacts are versioned
graphify-out/cost.json
graphify-out/cache/
*.graphify-bak
*.graphify.log
```

Do not ignore the `graphify-out/` directory itself, `graph.json`,
`GRAPH_REPORT.md`, `graph.html`, or the portable manifest.

- [ ] **Step 4: Protect Claude's prompt cache from generated-file churn**

Create `.claudeignore` with:

```gitignore
# Query through the Graphify CLI/skill instead of uploading generated output.
graphify-out/
```

Agents still access the graph through `graphify query`, `graphify explain`, and
`graphify path`; the output directory is not injected wholesale into prompts.

- [ ] **Step 5: Write the canonical operational guide**

Create `docs/architecture/graphify.md` with these concrete sections and
commands:

```markdown
# Graphify no BlackICE

## Papel arquitetural
Graphify é tooling de engenharia local, não uma dependência do PACS.

## Pré-requisitos e baseline
`uv --version`
`uv tool install "graphifyy==0.9.28"`
`graphify --version`

## Instalação project-scoped
`graphify install --project`
`graphify install --project --platform codex`
`graphify claude install --project`
`graphify codex install --project`
`graphify hook install`

## Gerar o grafo
No Codex: `$graphify .`
No Claude Code: `/graphify .`
No PowerShell, não use a barra inicial.

## Consultar antes de buscar amplamente
`graphify query "como o frontend obtém a sessão autenticada?"`
`graphify explain "SessionResource"`
`graphify path "HomePage" "SessionResource"`

## Atualizar
`graphify update .`
`graphify hook status`

## Arquivos versionados e locais
Versionar os artefatos portáveis de `graphify-out/`.
Não versionar `cost.json`, `cache/`, logs ou backups.

## Segurança e autoridade
Não indexar `.env`, chaves, DSNs, dados de paciente ou pixel data.
Confirmar relações `INFERRED` e decisões DICOM na fonte.

## Upgrade
`uv tool upgrade graphifyy`
Reexecutar os quatro comandos de instalação project-scoped.
Reexecutar `graphify hook install`.
Regenerar/atualizar o grafo e revisar os diffs.

## Diagnóstico no Windows
`uv tool update-shell`
`uv tool dir --bin`
Reabrir o terminal e verificar `graphify --version`.
```

Expand the terse command list into concise Portuguese prose. Keep the exact
commands, explain that `.graphify_version` must match the CLI, and link the
official README, release 0.9.28, project-scoped issue #817, and releases page.

- [ ] **Step 6: Register Graphify in the repository maps**

Add a short `## Graphify` section to `README.md` that:

- says the shared graph lives at `graphify-out/`;
- directs agents to query it before broad codebase searches;
- links only to `docs/architecture/graphify.md`.

Update the root tree and descriptions in
`docs/architecture/project-structure.md` to include:

```text
├─ .agents/skills/graphify/
├─ .graphifyignore
├─ graphify-out/
```

Describe all three as engineering/agent tooling. Do not classify any of them as
application or production infrastructure.

- [ ] **Step 7: Verify ignore behavior and documentation links**

```powershell
git check-ignore -v graphify-out/cost.json
git check-ignore -v graphify-out/cache/stat-index.json
git check-ignore graphify-out/graph.json
git check-ignore graphify-out/GRAPH_REPORT.md
git check-ignore graphify-out/graph.html
uv tool run --from "graphifyy==0.9.28" python -c "from graphify.detect import detect; from pathlib import Path; result=detect(Path('.')); print('categorias:', {kind: len(paths) for kind, paths in result['files'].items()}); print('total_files:', result['total_files']); print('ignored:', result['ignored']); print('pruned_noise_dirs:', result['pruned_noise_dirs']); print('skipped_sensitive:', result['skipped_sensitive']); print('walk_errors:', result['walk_errors'])"
rg -n "graphify-out|graphify query|0\\.9\\.28" README.md docs/architecture/graphify.md docs/architecture/project-structure.md
```

Expected: cost and cache are ignored with the new `.gitignore` lines; the three
portable outputs are not ignored. The official `graphify.detect.detect(Path('.'))`
API reports code and document categories (including Java, Vue/TypeScript,
configuration, and Markdown), skips sensitive files and excludes local ignored
paths; all documentation entry points resolve.

- [ ] **Step 8: Pass the Task 2 review and create its focused commit**

Present the ignore checks, the official detection API summary, and documentation diff.
After independent approval, commit with:

```powershell
git commit -m "📝 documenta uso e política do Graphify"
```

---

### Task 3: Generate and version the complete knowledge graph

**Files:**

- Create by Graphify: `graphify-out/graph.json`
- Create by Graphify: `graphify-out/GRAPH_REPORT.md`
- Create by Graphify: `graphify-out/graph.html`
- Create by Graphify: other portable manifests and semantic sidecars reported by
  Graphify 0.9.28
- Create locally and ignore: `graphify-out/cost.json`
- Create locally and ignore: `graphify-out/cache/`

**Interfaces:**

- Consumes: tracked BlackICE source/docs, `.graphifyignore`, `.gitignore`, and
  the fresh Codex `$graphify` skill.
- Produces: a complete, queryable, portable corpus for later tasks and future
  agents.

- [ ] **Step 1: Start this task in a fresh Codex subagent session**

The fresh worker must read
`.agents/skills/graphify/SKILL.md` completely, including every reference it
routes to for full and update extraction. This is necessary because skills
installed during Task 1 are discovered only by a new agent session.

- [ ] **Step 2: Confirm the detected corpus before generation**

```powershell
uv tool run --from "graphifyy==0.9.28" python -c "from graphify.detect import detect; from pathlib import Path; result=detect(Path('.')); print('categorias:', {kind: len(paths) for kind, paths in result['files'].items()}); print('total_files:', result['total_files']); print('ignored:', result['ignored']); print('pruned_noise_dirs:', result['pruned_noise_dirs']); print('skipped_sensitive:', result['skipped_sensitive']); print('walk_errors:', result['walk_errors'])"
```

Expected: the official detector API reports the backend Java, frontend
Vue/TypeScript, tracked configuration formats, and Markdown documentation. Its
ignored/pruned/sensitive output excludes `.superpowers/`, `node_modules/`,
`target/`, `dist/`, `graphify-out/`, the PNG regression snapshots, and sensitive
environment files; `walk_errors` is empty.

- [ ] **Step 3: Invoke full extraction through the installed Codex skill**

In the agent interface, invoke:

```text
$graphify .
```

This is a skill invocation, not a PowerShell command. Follow its full-extraction
runbook so code uses local AST extraction and Markdown receives the semantic
pass from the agent session. Do not substitute `graphify extract . --code-only`,
because that would omit the required documentation semantics.

- [ ] **Step 4: Verify the required portable artifacts**

```powershell
Test-Path graphify-out/graph.json
Test-Path graphify-out/GRAPH_REPORT.md
Test-Path graphify-out/graph.html
Get-Item graphify-out/graph.json, graphify-out/GRAPH_REPORT.md, graphify-out/graph.html |
  Select-Object FullName, Length, LastWriteTime
Get-Content -Raw graphify-out/graph.json | ConvertFrom-Json | Out-Null
git status --short -- graphify-out
```

Expected: all three files exist and are non-empty, `graph.json` parses, and Git
shows portable files as trackable while local cost/cache files remain hidden.

- [ ] **Step 5: Validate source coverage and secret exclusions**

```powershell
$graph = Get-Content -Raw graphify-out/graph.json | ConvertFrom-Json
$sources = @($graph.nodes | ForEach-Object { "$($_.source_file)" })
$required = @('\.java$', '\.vue$', '\.(ts|tsx)$', '\.(yml|yaml|toml|xml|properties)$', '\.md$')
foreach ($pattern in $required) {
  if (-not ($sources | Where-Object { $_ -match $pattern })) {
    throw "Graphify source coverage missing: $pattern"
  }
}
$forbidden = $sources | Where-Object {
  $_ -match '(^|[\\/])\.env$' -or
  $_ -match '(^|[\\/])\.git([\\/]|$)' -or
  $_ -match '(^|[\\/])\.worktrees([\\/]|$)' -or
  $_ -match '(^|[\\/])graphify-out([\\/]|$)'
}
if ($forbidden) {
  $forbidden
  throw 'Forbidden source entered graphify-out/graph.json'
}
```

Expected: every required source family is present and the forbidden-source
collection is empty.

- [ ] **Step 6: Pass the Task 3 review and create its focused commit**

Present node/edge counts, detected communities, generated file sizes, source
coverage, and ignored local artifacts. After independent approval, commit with:

```powershell
git commit -m "🗺️ adiciona grafo de conhecimento do BlackICE"
```

---

### Task 4: Install and verify incremental Git maintenance

**Files:**

- Create or modify by official installer: `.gitattributes`
- Modify local Git metadata: `.git/config`
- Create or modify local Git hooks under the path returned by
  `git rev-parse --git-path hooks`
- Update by Graphify: `graphify-out/`

**Interfaces:**

- Consumes: the valid initial graph from Task 3 and the interpreter path owned
  by the uv tool installation.
- Produces: post-commit/post-checkout AST refresh, a graph union merge driver,
  and a verified incremental-update workflow.

- [ ] **Step 1: Observe the pre-hook state**

```powershell
graphify hook status
git check-attr merge -- graphify-out/graph.json
git rev-parse --git-path hooks
```

Expected: Graphify reports that its hook is not installed and the merge
attribute is `unspecified`.

- [ ] **Step 2: Install the official hook and merge driver**

```powershell
graphify hook install
```

This command writes local Git metadata and may require the Codex sandbox
approval already authorized by the design. Use the official installer only; do
not hand-write `.git/hooks` or `.git/config`.

- [ ] **Step 3: Verify hook, interpreter, and tracked merge attribute**

```powershell
graphify hook status
git config --get merge.graphify.driver
git check-attr merge -- graphify-out/graph.json
Get-Content -Raw .gitattributes
```

Expected: hook status is installed; the merge driver invokes the pinned
Graphify interpreter; the attribute result is
`graphify-out/graph.json: merge: graphify`; `.gitattributes` contains exactly
one corresponding line.

- [ ] **Step 4: Prove installer idempotency**

```powershell
graphify hook install
$attributeLines = @(Select-String -Path .gitattributes -SimpleMatch 'graphify-out/graph.json merge=graphify')
if ($attributeLines.Count -ne 1) {
  throw "Expected one Graphify merge attribute, found $($attributeLines.Count)"
}
graphify hook status
```

Expected: the second installation succeeds without duplicating the tracked
attribute, and status remains installed.

- [ ] **Step 5: Run and verify an incremental refresh**

```powershell
$before = (Get-FileHash graphify-out/graph.json -Algorithm SHA256).Hash
graphify update .
Get-Content -Raw graphify-out/graph.json | ConvertFrom-Json | Out-Null
$after = (Get-FileHash graphify-out/graph.json -Algorithm SHA256).Hash
"before=$before"
"after=$after"
```

Expected: update completes without a full rebuild or parse error. The hash may
change because this plan and operational guide entered the corpus; validity and
coverage must remain intact regardless of hash equality.

- [ ] **Step 6: Pass the Task 4 review and create its focused commit**

Present `hook status`, merge-driver configuration, the idempotency result, and
the incremental-update summary. After independent approval, commit with:

```powershell
git commit -m "🔧 automatiza atualização incremental do Graphify"
```

---

### Task 5: Validate real BlackICE queries and final repository state

**Files:**

- Update if required by Graphify: `graphify-out/`
- Modify only if an observed command differs: `docs/architecture/graphify.md`

**Interfaces:**

- Consumes: the generated graph, installed skills, hook, and operational guide.
- Produces: acceptance evidence that future agents can use Graphify across the
  frontend, backend, infrastructure, documentation, and DICOM domain knowledge.

- [ ] **Step 1: Query the authenticated session flow**

```powershell
graphify query "como o frontend Vue obtém a sessão autenticada do backend Quarkus?"
graphify explain "SessionResource"
graphify explain "HomePage"
```

Expected: results cite real BlackICE nodes/files from both applications,
including the session contract, without presenting unrelated inferred edges as
source facts.

- [ ] **Step 2: Query DICOMweb invariants in the documentation layer**

```powershell
graphify query "quais regras do BlackICE governam StudyInstanceUID e o uso de QIDO-RS, WADO-RS e STOW-RS?"
graphify path "StudyInstanceUID" "DICOMweb"
```

Expected: the response points to `docs/domains/dicom/` and distinguishes
QIDO-RS, WADO-RS, and STOW-RS. It must not invent patient/study/series/instance
UIDs or claim that `PatientID` alone is globally unique.

- [ ] **Step 3: Re-run the structural acceptance checks**

```powershell
graphify --version
graphify hook status
Get-Content -Raw graphify-out/graph.json | ConvertFrom-Json | Out-Null
git check-ignore -v graphify-out/cost.json
git check-ignore -v graphify-out/cache/stat-index.json
git check-ignore graphify-out/graph.json
git check-attr merge -- graphify-out/graph.json
```

Expected: version 0.9.28, hook installed, valid JSON, local state ignored,
portable graph not ignored, merge attribute set to `graphify`.

- [ ] **Step 4: Confirm the integration did not touch PACS runtime code**

```powershell
git diff --quiet -- apps infra
if ($LASTEXITCODE -ne 0) {
  git diff -- apps infra
  throw 'Graphify integration changed application or runtime infrastructure files'
}
git status --short -- apps infra
```

Expected: no tracked or untracked change under `apps/` or `infra/`.

- [ ] **Step 5: Audit the complete intentional diff**

```powershell
git status --short
git diff --stat
git diff -- AGENTS.md CLAUDE.md .codex/config.toml .gitignore README.md docs/architecture/project-structure.md
Get-ChildItem graphify-out -Recurse -File |
  Sort-Object FullName |
  Select-Object FullName, Length
```

Expected: only the approved spec/plan, official project-scoped skills/hooks,
Graphify policy/docs, `.gitattributes`, and portable graph artifacts are
present. No secret, backup, local cache, or unrelated user file is included.

- [ ] **Step 6: Run a final documentation command audit**

Execute every non-destructive command shown in
`docs/architecture/graphify.md` that does not reinstall or regenerate the
graph:

```powershell
uv --version
graphify --version
graphify hook status
graphify query "onde ficam as instruções operacionais do Graphify no BlackICE?"
```

Expected: all commands resolve on Windows PowerShell and the query points back
to the operational guide and agent instructions.

- [ ] **Step 7: Pass the final human gate**

Report query evidence, source coverage, hook/merge-driver status, generated
artifact sizes, and the exact repository status. If validation required a
documentation or graph correction, independently review it and commit with:

```powershell
git commit -m "✅ valida integração do Graphify"
```

Do not create an empty commit when validation produces no file change.

## Plan self-review

- Spec coverage: project-scoped Codex/Claude skills, full code/config/Markdown
  extraction, versioned output, ignore policy, hooks, merge driver, security,
  onboarding, upgrade, Windows recovery, and real queries each have an explicit
  task and acceptance check.
- Placeholder scan: this plan contains no deferred implementation markers.
- Interface consistency: Task 1 produces the skills and CLI used by Task 3;
  Task 2 produces the corpus policy used by Task 3; Task 3 produces the graph
  used by Tasks 4–5; Task 4 produces the maintenance interface validated by
  Task 5.
- Project policy: implementation stays uncommitted until independent task
  review; each approved task then receives one focused authorized commit.

## Sources

- [Official Graphify README](https://github.com/Graphify-Labs/graphify/blob/v8/README.md)
- [Official tutorial](https://graphify.com/docs/tutorial)
- [Graphify 0.9.28 release](https://github.com/Graphify-Labs/graphify/releases/tag/v0.9.28)
- [Community project-scoped installation request](https://github.com/Graphify-Labs/graphify/issues/817)
- [Official releases and community fixes](https://github.com/Graphify-Labs/graphify/releases)
