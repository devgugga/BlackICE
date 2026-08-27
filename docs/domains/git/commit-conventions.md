# Git Commit Conventions

This document is the canonical source of truth for agents authoring and curating local Git commits.

## Authority and Branching Rules

1. Inspect the active branch before taking action.
2. On a dedicated working branch, author the local commit upon completing a verified task with versionable changes.
3. On the `main` branch, only create a commit with explicit human authorization, unless prior, unequivocal authorization exists in the active session context.
4. Do not create branches, rewrite historical commits, or execute `git push`.

## Safe Scope Selection

1. Inspect `git status` and diffs before selecting and staging files.
2. Stage strictly the changes directly related to the completed task.
3. Never mix unrelated edits or foreign work into a shared worktree.
4. If it is impossible to separate the scope safely, stop and explain why; never produce ambiguous or partial commits.

## Prerequisites

1. Run relevant automated checks and log verified outputs.
2. Before committing versioned changes, update Graphify in accordance with `AGENTS.md` and `docs/architecture/graphify.md`, reviewing the diff in `graphify-out/`.
3. Include **all** tracked files modified by the Graphify update; never leave tracked Graphify artifacts behind.

## Commit Message Format

1. Select the Gitmoji directly from the table below.
2. Use the **literal emoji character** (`🎉`), never the colon shortcode (`:tada:`).
3. Commit directly without asking for message confirmation.
4. Write the title in Portuguese or English following the convention:

   ```text
   gitmoji verb outcome: context
   ```

5. Never claim features, tests, or guarantees unsupported by the diff.

### Gitmoji Selection

| Gitmoji | When to Use |
| :-- | :-- |
| `📝` | Any documentation-only change (`.md` docs, specs, plans, domain packs, agent instructions). |
| `✨` | New user-facing capability in executable code. |
| `🐛` | Bugfix. |
| `♻️` | Refactoring without observable behavioral change. |
| `🔧` | Configuration: Compose, `.env`, `.gitignore`, tooling scripts. |
| `🔐` | Auth, OIDC, Keycloak, realms, scopes, secrets. |
| `✅` | Adding or correcting automated tests. |
| `🚚` | Moving or renaming files and directories. |
| `⬆️` | Upgrading dependencies or toolchain baselines. |
| `🕸️` | Graphify knowledge graph synchronization. |

### Commit Body (Mandatory)

The body is **mandatory** for every commit touching code, configuration, infrastructure, or documentation.

Use the structured sections in this order:

| Section | When to Use |
| :-- | :-- |
| `### ✅ Novas funcionalidades` (or `### ✅ New features`) | New capabilities added. |
| `### 💡 Melhorias na arquitetura` (or `### 💡 Architecture improvements`) | Structural changes, boundaries, design decisions. |
| `### 🧼 Boas práticas e validações` (or `### 🧼 Best practices & validations`) | Code hygiene, tests, healthchecks, linting. |
| `### 🔐 Permissões e controle de acesso` (or `### 🔐 Security & Access Control`) | Auth, OIDC, roles, secrets. |
| `### 🚀 Resultado` (or `### 🚀 Outcome`) | **Always present.** Summary of repository state and next steps. |

Wrap all lines at **76 columns**.

### Graph Synchronization Commit

The dedicated commit carrying **strictly** `graphify-out/**` is the sole exception to the mandatory body rule:

```text
🕸️ sincroniza grafo de conhecimento
```

No body required.

## Attribution

It is strictly forbidden to append `Co-authored-by` or similar co-authorship trailers to commits.
