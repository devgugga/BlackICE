# Agentes Antigravity — desenho

## Objetivo

Disponibilizar no repositório os quatro papéis especializados já existentes para
Claude e Codex também no Google Antigravity, sem duplicar conhecimento de
domínio.

## Escopo

Criar wrappers versionados em `.agents/agents/<nome>/agent.md` para:

- `dicom-domain-reviewer`;
- `quarkus-backend`;
- `dicom-viewer-frontend`;
- `commit-curator`.

A pasta por agente é uma forma de descoberta oficialmente suportada pelo
Antigravity. Não serão usados níveis adicionais de agrupamento, como
`.agents/agents/dicom/<nome>/agent.md`, pois a documentação só garante o
caminho com um diretório por agente.

## Configuração

Todos os agentes terão `model: flash`. No schema de custom agents do
Antigravity, `flash` é a camada aceita no frontmatter; neste ambiente ela
resolve para Gemini 3.7 Flash. Os agentes serão somente subagentes
reutilizáveis (`subagent: true`, `mainAgent: false`).

O revisor DICOM terá apenas ferramentas de leitura e pesquisa. Os agentes
implementadores terão as ferramentas de leitura, pesquisa, criação/edição de
arquivos e execução de comandos. O curador de commits terá o mesmo conjunto
para inspecionar o diff e executar Git. Todos usarão a política `sandbox` para
comandos.

## Conhecimento e documentação

Os corpos dos wrappers continuarão finos: instruem a leitura dos Domain Packs
em `docs/domains/`, que permanecem a única fonte de verdade. A documentação de
estrutura e a convenção de Domain Packs passarão a listar o Antigravity como
terceiro ponto de descoberta, sem modificar código de produto ou backlog de
MVP.

## Verificação

Após a criação, a sintaxe YAML será validada e `agy agents` será executado para
confirmar que o Antigravity descobre os quatro agentes no workspace. Nenhum
commit será criado sem solicitação explícita do mantenedor.
