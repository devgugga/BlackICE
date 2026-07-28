# BlackICE — AGENTS.md

> **Fonte canônica e tool-agnostic** de instruções para qualquer agente de IA que
> trabalhe neste repositório (Claude Code, Codex, GLM, …). O `CLAUDE.md` importa
> este arquivo. Mantenha as regras aqui; ferramentas específicas apenas apontam
> para cá e para `docs/domains/`.

## O que é o BlackICE

PACS (Picture Archiving and Communication System) de portfólio/aprendizado.

- **Motor DICOM:** DCM4CHEE Archive 5.x — faz o trabalho DICOM pesado (storage,
  C-STORE, query/retrieve, DICOMweb). **Não reimplementamos isso.**
- **Backend:** Quarkus, como backend **de produto** com domínio próprio
  (pacientes/metadados de negócio, laudos, permissões) em PostgreSQL próprio.
  Consome o DCM4CHEE **apenas via DICOMweb** (STOW-RS, QIDO-RS, WADO-RS).
- **Frontend:** Vue 3 + Vite (SPA autenticado). Viewer com Cornerstone3D.
- **Auth:** Keycloak (OIDC/SSO) — Quarkus via `quarkus-oidc`; DCM4CHEE integra
  nativamente com Keycloak.

MVP (4 fluxos ponta-a-ponta): (1) ingestão via STOW-RS, (2) worklist + busca via
QIDO-RS, (3) viewer do estudo com Cornerstone3D, (4) laudos + autenticação.

## Estrutura do repositório

`docs/architecture/project-structure.md` é a fonte canônica operacional. Leia-a
antes de criar ou mover código.

- `apps/backend/`: API/BFF Quarkus.
- `apps/frontend/`: SPA Vue 3 + Vite.
- `infra/`: composição e configuração operacional local.
- `docs/`: arquitetura, Domain Packs e registros históricos.

Não crie novas pastas raiz de aplicação nem camadas técnicas globais como
`controller/`, `service/` e `repository/`. As receitas completas para features
Quarkus e Vue estão no documento canônico.

## Arquitetura de agentes: "Domain Packs"

O conhecimento vive **uma vez**, em markdown neutro, e é reaproveitado por todas
as ferramentas e (parte dele) por projetos futuros. Ver `docs/domains/README.md`.

```
docs/domains/<dominio>/   ← conhecimento portável (fonte da verdade)
.claude/agents/<dominio>/ ← subagentes Claude (wrappers finos → leem os docs)
.codex/agents/*.toml      ← subagentes Codex (wrappers finos → leem os docs)
.claude/skills/           ← skills Claude (workflows repetíveis; nomeadas por domínio)
```

**Regra de ouro:** um agente/skill nunca duplica conhecimento. Seu corpo diz
"aplique `docs/domains/<x>/*.md`". Corrigiu uma regra? Corrige no doc, e todos os
wrappers herdam.

Domínios atuais: `dicom/` (♻️ reutilizável), `vue/` (♻️ reutilizável),
`quarkus/` (específico deste projeto — não transfere para outros backends).

## Graphify

Leia `docs/architecture/graphify.md` antes de instalar, atualizar ou regenerar o
grafo. Use a skill project-scoped em `.agents/skills/graphify/` (Codex) ou seu
mirror em `.claude/skills/graphify/` (Claude); não execute os instaladores
oficiais isoladamente, pois `.graphify/setup.ps1` reaplica os ajustes validados
para a versão fixada.

Antes do commit de uma tarefa que altere código, configuração, documentação ou
imagens, execute a atualização semântica `--update` pela skill e revise o diff de
`graphify-out/`. Em linked worktrees essa etapa é obrigatoriamente manual: os
hooks Git oficiais do Graphify 0.9.28 se desativam nesse contexto, mesmo quando
`graphify hook status` informa que estão instalados.

## Invariantes de DICOM (resumo — detalhes em `docs/domains/dicom/`)

Estas são regras de **correção de negócio**. Violá-las corrompe dados de paciente.

1. **UIDs vêm do archive/da aquisição — nunca invente.** `StudyInstanceUID`,
   `SeriesInstanceUID`, `SOPInstanceUID` são identidade. Ao criar objetos novos,
   gere UIDs com gerador DICOM válido (raiz registrada), nunca strings aleatórias.
2. **Respeite a hierarquia** Paciente → Estudo → Série → Instância. Uma série tem
   uma única `Modality`. `StudyInstanceUID` é a chave estável de um estudo.
3. **Cada verbo DICOMweb tem seu papel:** buscar = QIDO-RS; recuperar pixels =
   WADO-RS; armazenar = STOW-RS. Não troque um pelo outro.
4. **`PatientID` não é globalmente único** sem o issuer. Não assuma unicidade.
5. **Não re-encode pixel data** sem entender o Transfer Syntax.

## Workflow (papéis e gates)

O humano (dono do repo) é o **orquestrador e o validador de lógica de negócio**.
Os agentes implementam; decisões de semântica DICOM e integridade de dados de
paciente são **apresentadas nos gates** para aprovação humana, nunca auto-decididas.

Loop: brainstorming → plano em fases → implementação por subagentes → gate humano
ao fim de cada fase. Specs em `docs/superpowers/specs/`.

## Convenções gerais

- Commits pequenos e focados; não commitar sem pedido explícito do humano.
- Ao tocar em DICOM/DICOMweb, consulte `docs/domains/dicom/` e passe pelo
  revisor de domínio antes do gate.
- Não coloque pixel data no banco do Quarkus; guarde referências (UIDs) + dados
  de negócio (laudos).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Guidance (optional):
- For codebase questions, consider `graphify query "<question>"` when graphify-out/graph.json exists. `graphify path "<A>" "<B>"` and `graphify explain "<concept>"` can provide focused relationship and concept views.
- You may instead use other appropriate approaches, including direct source browsing, targeted search, and project documentation.
- Dirty graphify-out/ files are expected after hooks or incremental updates; they do not prevent using Graphify when it is helpful.
- If graphify-out/wiki/index.md exists, consider it for broad navigation; consider GRAPH_REPORT.md for broad architecture review or when focused graph queries are insufficient.
- After modifying code, consider `graphify update .` to keep the graph current (AST-only, no API cost).
