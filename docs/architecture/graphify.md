# Graphify in BlackICE

## Architectural Role

Graphify is local engineering tooling, not a runtime dependency of the PACS. It produces a queryable knowledge graph of the repository to guide code investigation; it does not replace source files, Domain Packs, or clinical review gates.

## Prerequisites and Baseline

The project baseline is CLI version `0.9.32`.

```powershell
uv --version
graphify --version
```

## Project-Scoped Installation

Run from repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .graphify/setup.ps1
```

The script installs pinned `graphifyy==0.9.32`, installs hooks, and ensures skill overlays match canonical checksums.

## Querying the Knowledge Graph

```powershell
graphify query "how does the frontend obtain the authenticated session?"
graphify explain "SessionResource"
graphify explain "HomePage"
```

## Update Protocol

Semantic updates occur **once**, after code implementation, tests, and review gates are stable, immediately before creating a commit.

Protocol:
1. When a feature is stable, run semantic update via the skill and review `graphify-out/` diff.
2. Commit feature code without `graphify-out/`.
3. Allow post-commit hooks to run AST updates.
4. Author a focused second commit for graph synchronization:
   ```text
   🕸️ sincroniza grafo de conhecimento
   ```

## Security Guardrails

Never index `.env`, secrets, clinical patient data, or raw DICOM pixel files.
