# Skills do BlackICE

Home dos **workflows repetíveis** do Claude Code. Criamos skills quando um
workflow real se repetir, não antes (YAGNI).

> Este `README.md` é só documentação; o Claude Code descobre skills em
> `.claude/skills/<nome>/SKILL.md`, então este arquivo não vira uma skill.

## Convenção

- Uma skill por pasta: `.claude/skills/<dominio>-<verbo>/SKILL.md`.
- Se o workflow também se aplicar a Codex e Antigravity, mantenha um wrapper fino
  equivalente em `.agents/skills/<dominio>-<verbo>/SKILL.md`; o Domain Pack,
  nunca uma pasta de ferramenta, continua sendo a fonte de verdade.
- **Nomeie por domínio:** `dicom-*`, `vue-*`, `quarkus-*`.
- A skill é **fina**: o passo-a-passo mora nela, mas as **regras** moram no domain
  pack. O corpo deve referenciar `docs/domains/<dominio>/`.
- Subagente vs skill: subagente = papel isolado que devolve resumo (revisor,
  especialista); skill = workflow no contexto principal (scaffold, gerador).

## Skills existentes

- `agent-authoring` — cria ou altera agentes e seus modelos a partir de
  `docs/domains/agent-authoring/`.

## Candidatas futuras (não implementar ainda)

- `dicom-scaffold-endpoint` — gerar um endpoint DICOMweb (QIDO/WADO/STOW) no Quarkus
  seguindo `docs/domains/dicom/dicomweb.md` + `docs/domains/quarkus/conventions.md`.
- `vue-add-viewer-tool` — adicionar uma ferramenta ao viewer Cornerstone3D seguindo
  `docs/domains/vue/cornerstone3d.md`.
- `dicom-check-study` — rotina de sanity-check da hierarquia de um estudo.

Ao criar uma skill, siga o padrão em `docs/domains/README.md`.
