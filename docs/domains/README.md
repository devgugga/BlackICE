# Domain Packs — convenção de agentes

Este diretório é o **núcleo de conhecimento** do projeto. Cada subpasta é um
"domain pack" autocontido, e é a **fonte única da verdade** para aquele domínio.
Subagentes (Claude e Codex) e skills são apenas **wrappers finos** que leem estes
documentos — eles nunca duplicam o conteúdo.

## Por que assim

- **Portabilidade entre ferramentas.** Subagentes do Claude (markdown em
  `.claude/agents/`) e do Codex (TOML em `.codex/agents/`) têm **formatos
  diferentes e incompatíveis**. O que é portável é o markdown neutro daqui.
- **Reuso entre projetos.** Os packs marcados ♻️ transferem para outros projetos
  (ex.: o PACS em Django). Basta copiar a pasta do domínio e os wrappers; o
  conhecimento vai intacto.
- **Manutenção.** Uma regra mora em um lugar. Corrigiu? Todos os agentes herdam.

## Domínios

| Pack | Reutilizável | Conteúdo |
| :-- | :-- | :-- |
| `dicom/` | ♻️ sim | Semântica DICOM/DICOMweb (UIDs, hierarquia, STOW/QIDO/WADO). |
| `vue/` | ♻️ sim | Vue 3 + Vite; viewer Cornerstone3D. |
| `quarkus/` | ✗ project-scoped | Quarkus + Keycloak/OIDC + client DICOMweb. Não transfere. |

## Como adicionar um domínio novo

1. Crie `docs/domains/<novo>/` com um `README.md` (o que é, é reutilizável?) e os
   docs de conhecimento.
2. Crie os wrappers que referenciam esses docs:
   - Claude subagente: `.claude/agents/<novo>/<nome>.md` (frontmatter + corpo que
     manda ler `docs/domains/<novo>/*.md`).
   - Codex subagente: `.codex/agents/<nome>.toml` (`developer_instructions`
     mandando ler os mesmos docs).
3. Registre o subagente na tabela do `CLAUDE.md`.

## Como adicionar uma skill

Skills são workflows repetíveis que rodam no contexto principal (não isolado).
Crie `.claude/skills/<dominio>-<verbo>/SKILL.md`, nomeada por domínio, e faça o
corpo referenciar `docs/domains/<dominio>/`. Mantenha a skill fina — o passo-a-passo
mora nela, mas as **regras** moram no domain pack.

## Regra de ouro

> Wrapper aponta para o doc. Conhecimento nunca é copiado para dentro de um agente
> ou skill. Se você se pegar colando regra de domínio num `.md` de agente, pare e
> mova para o domain pack.
