# Integração project-scoped do Graphify

**Data:** 2026-07-27
**Status:** desenho aprovado; especificação aguardando revisão
**Escopo:** tooling de engenharia e contexto para agentes

## Objetivo

Integrar o Graphify ao BlackICE de forma reproduzível e versionada para que
Codex, Claude Code e demais colaboradores possam consultar um grafo de
conhecimento comum do monorepo. O grafo deve abranger código, configurações e
documentação Markdown sem se tornar uma dependência de runtime do PACS.

## Decisões

- Usar o pacote oficial `graphifyy` e o comando `graphify`.
- Adotar o Graphify 0.9.28 como baseline verificada desta integração.
- Instalar as skills oficiais em escopo de projeto para Codex e Claude Code.
- Manter as instruções permanentes e tool-agnostic no `AGENTS.md`; integrações
  específicas continuam como wrappers finos.
- Gerar e versionar `graphify-out/`, conforme o fluxo recomendado para equipes.
- Incluir código, configurações e toda a documentação Markdown rastreável pelo
  Git.
- Manter fora do Git somente custos, caches e demais estado estritamente local.
- Usar o hook oficial para atualizações incrementais e o merge driver oficial
  para `graph.json`.
- Não adicionar Graphify às imagens, aos containers nem às dependências de
  runtime de Quarkus ou Vue.
- Não configurar um servidor MCP compartilhado nesta fase.

## Arquitetura

Graphify é uma ferramenta de engenharia no nível do repositório. Sua instalação
fica isolada pelo `uv tool`, enquanto as skills project-scoped e as instruções
para agentes são arquivos versionáveis no próprio checkout.

```text
BlackICE/
├─ AGENTS.md                         instruções canônicas de uso do grafo
├─ CLAUDE.md                         wrapper específico já existente
├─ .agents/skills/graphify/          skill oficial para Codex
├─ .claude/skills/graphify/          skill oficial para Claude Code
├─ .codex/                           configuração/hook específico do Codex
├─ .claude/                          configuração/hook específico do Claude
├─ .graphifyignore                   exclusões adicionais ao .gitignore
├─ graphify-out/
│  ├─ GRAPH_REPORT.md                visão arquitetural para agentes
│  ├─ graph.json                     grafo consultável e portável
│  ├─ graph.html                     visualização navegável
│  └─ ...                            manifestos/sidecars portáveis
└─ docs/
   └─ architecture/
      └─ graphify.md                 guia operacional canônico
```

Os caminhos exatos gerados pelas skills e hooks serão aceitos a partir do
instalador oficial 0.9.28, em vez de serem recriados manualmente. Antes de
preservar uma alteração automática em `AGENTS.md`, `CLAUDE.md` ou arquivos de
configuração, o diff será inspecionado para impedir sobrescrita ou duplicação
das regras existentes.

## Conteúdo do grafo

A raiz do corpus será o checkout do BlackICE. A extração inclui:

- Java e propriedades do backend Quarkus;
- Vue, TypeScript, HTML e configurações do frontend;
- YAML, TOML, XML, shell, PowerShell e demais configurações rastreadas;
- `README.md`, `AGENTS.md`, `CLAUDE.md`, arquitetura, Domain Packs, specs e
  planos históricos;
- ligações entre documentos Markdown e relações AST entre arquivos de código.

O `.gitignore` continua sendo respeitado. A `.graphifyignore` adiciona somente
exclusões próprias do Graphify, como a saída dele mesmo e artefatos que não
representam fonte do projeto. Segredos e arquivos locais permanecem excluídos.
Não será usado `--no-gitignore`.

A extração AST do código é local e determinística. O passe semântico dos
documentos será executado pela skill no contexto do agente, sem registrar chaves
de API no repositório.

## Integração com agentes

### Orientação inicial

Antes de buscas amplas ou respostas sobre arquitetura, agentes devem consultar
o grafo com uma pergunta delimitada:

```powershell
graphify query "como o frontend obtém a sessão autenticada?"
```

`graphify-out/GRAPH_REPORT.md` é usado para orientação arquitetural ampla.
`graphify explain` detalha um nó e `graphify path` verifica a ligação entre dois
conceitos. O grafo orienta a leitura; arquivos-fonte permanecem a autoridade
final para implementação e revisão.

### Atualização

Depois de alterar arquivos indexados, o agente executa atualização incremental:

```powershell
graphify update .
```

O hook oficial cobre atualizações após commits e checkouts. Como o projeto não
autoriza commits automáticos, o hook não muda a política de commits do
`AGENTS.md`.

### Codex

A instalação project-scoped do Codex fornece a skill `$graphify`. A configuração
do projeto habilita multi-agent para a extração paralela, conforme o requisito
oficial do Graphify. As instruções permanentes ficam no `AGENTS.md`; o
`PreToolUse` do Codex é preservado como no-op intencional, pois o Codex Desktop
não aceita a injeção de contexto esperada pelo hook.

### Claude Code

A instalação project-scoped fornece a skill `/graphify` e o hook oficial de
orientação. O modo estrito não será habilitado inicialmente: o comportamento
soft-nudge é menos intrusivo e mantém o grafo como acelerador, sem bloquear a
primeira leitura de fonte.

## Versionamento e colaboração

Os artefatos portáveis de `graphify-out/` serão rastreados pelo Git para que um
novo checkout já possua o mapa. Serão ignorados:

- `graphify-out/cost.json`;
- `graphify-out/cache/`, por ser apenas uma otimização local;
- arquivos temporários ou logs locais criados pela ferramenta.

O manifest usa caminhos relativos e pode ser compartilhado. O hook oficial
configura o merge driver que faz união de `graph.json`; a configuração local do
driver será documentada porque `git config` não é transportado pelo repositório.
A linha correspondente em `.gitattributes`, gerada oficialmente, será
versionada.

## Instalação e onboarding

O guia operacional documentará:

1. pré-requisitos (`uv` e Python 3.10+ provido pelo ambiente isolado);
2. instalação ou upgrade de `graphifyy`;
3. registro project-scoped das skills de Codex e Claude;
4. geração inicial do grafo completo;
5. instalação e verificação do hook;
6. comandos de consulta e atualização;
7. política de arquivos versionados;
8. recuperação de PATH no Windows com `uv tool update-shell`;
9. diagnóstico de diferença entre a versão da CLI e das skills;
10. procedimento de upgrade com regeneração das skills e do grafo.

O README raiz terá apenas uma entrada curta apontando para esse guia, evitando
duplicação de instruções.

## Falhas e segurança

- A instalação deve parar se o instalador não conseguir interpretar um arquivo
  de configuração existente; nenhuma configuração válida pode ser substituída
  por um arquivo vazio.
- Backups `.graphify-bak` gerados pelo instalador serão inspecionados e tratados
  como artefatos locais, não como fonte canônica.
- Falha no hook não pode impedir commits nem modificar código de produto.
- Uma consulta sem resultado não autoriza concluir que um conceito inexiste; o
  agente deve verificar a fonte.
- Relações `INFERRED` são hipóteses do resolvedor e exigem confirmação na fonte;
  relações `EXTRACTED` também não substituem testes.
- Nenhuma chave, DSN, `.env` ou dado de paciente pode entrar no grafo.
- O grafo não contém pixel data e não altera nenhuma invariante DICOM.

## Validação

A integração será aceita quando:

1. a CLI instalada reportar Graphify 0.9.28;
2. as skills project-scoped de Codex e Claude existirem e forem reconhecíveis;
3. o instalador preservar as instruções atuais de `AGENTS.md` e `CLAUDE.md`;
4. `graphify hook status` confirmar o hook e o merge driver;
5. o grafo completo for gerado sem segredos e contiver nós de Java, Vue,
   configurações e Markdown;
6. `graphify query` localizar o contrato de sessão entre Vue e Quarkus;
7. outra consulta relacionar DICOMweb aos Domain Packs sem inventar UIDs;
8. `graphify path` ou `graphify explain` produzir uma resposta utilizável sobre
   componentes reais;
9. uma atualização incremental preservar o grafo e refletir uma alteração
   controlada;
10. `git status` mostrar apenas os arquivos intencionais da integração.

## Fontes oficiais e comunitárias

- [Graphify README](https://github.com/Graphify-Labs/graphify/blob/v8/README.md)
- [Graphify 0.9.28](https://github.com/Graphify-Labs/graphify/releases/tag/v0.9.28)
- [Solicitação comunitária de instalação project-scoped](https://github.com/Graphify-Labs/graphify/issues/817)
- [Releases e correções contribuídas pela comunidade](https://github.com/Graphify-Labs/graphify/releases)

## Fora do escopo

- servidor Graphify MCP compartilhado ou exposto por HTTP;
- Graphify em produção ou nos containers do PACS;
- CI para regenerar o passe semântico;
- indexação de bancos PostgreSQL vivos;
- Neo4j, FalkorDB, Obsidian, wiki ou exportação SVG;
- modo estrito do hook do Claude;
- reestruturação das aplicações para melhorar artificialmente o grafo.
