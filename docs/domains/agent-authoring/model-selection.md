# Seleção de modelo

## Regra central

Para cada agente criado ou alterado, pesquise o estado atual de Anthropic,
OpenAI e Google antes de escolher. Selecione, em cada plataforma solicitada, o
modelo ou tier de **menor custo elegível** para a tarefa. “Mais novo” e “mais
capaz” não são, por si, justificativa para custo maior.

Não mantenha neste documento uma tabela de modelos, preços ou rankings. Eles
mudam rapidamente e seriam uma fonte de decisão desatualizada.

## Pesquisa obrigatória

Na mesma sessão que propõe a alteração, consulte fontes oficiais atuais de cada
fornecedor:

| Fornecedor | Consultar | Também verificar localmente |
| :-- | :-- | :-- |
| Anthropic | documentação de subagentes, configuração e custos | aliases/IDs permitidos, política e plano da conta |
| OpenAI | documentação de subagentes Codex, modelos e preços quando publicados | versão do Codex, allowlists e modelos/esforços que o cliente expõe |
| Google | documentação de custom agents/skills e catálogo aplicável | `agy models` e os tiers aceitos pelo frontmatter |

Fontes de partida oficiais: [Claude subagents](https://code.claude.com/docs/en/sub-agents),
[custos Claude Code](https://code.claude.com/docs/en/costs),
[Codex subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents),
[preços OpenAI](https://platform.openai.com/docs/pricing),
[Antigravity custom agents](https://antigravity.google/blog/introducing-custom-agents)
e [Antigravity skills](https://antigravity.google/docs/skills).

Uma opção é inelegível se estiver indisponível no ambiente, descontinuada ou não
for aceita no campo de configuração daquela plataforma. Tiers não são modelos:
registre o modelo efetivo observado quando o runtime o expuser.

## Classificação e roteamento

1. Defina a responsabilidade, ferramentas, permissões, impacto e necessidade
   de julgamento de domínio.
2. Classifique a carga:
   - **simples/repetitiva:** transformação pequena, inventário, resumo ou tarefa
     mecânica bem delimitada;
   - **rotineira:** implementação ou revisão comum com testes e limites claros;
   - **complexa/de alto risco:** investigação ambígua, múltiplas etapas,
     arquitetura, segurança ou semântica de domínio;
   - **especializada:** exige raciocínio profundo, mas continua sujeita a gate
     humano quando há dados de paciente ou decisão clínica.
3. Escolha o candidato de menor preço capaz de cumprir a classe, com o menor
   esforço de raciocínio suficiente e as ferramentas mínimas.
4. Prefira um modelo econômico para a classe simples. Para classes maiores,
   compare qualidade, latência, custo e disponibilidade reais — não o nome do
   fornecedor.

Uma tarefa crítica não dispensa revisão humana; aumentar o modelo não substitui
um gate de domínio. Inversamente, um modelo avançado não deve ser usado para uma
tarefa barata só por ser o padrão da sessão.

## Proposta e escalonamento

Antes de gravar a configuração, apresente ao humano:

```text
Papel e tarefa:
Plataforma:
Classificação:
Candidato econômico elegível:
Modelo/tier e esforço configuráveis:
Disponibilidade local verificada em:
Pesquisa: data + URLs oficiais
Justificativa de custo/capacidade:
Gate ou permissão que exige aprovação:
```

Não altere o modelo de um agente existente sem autorização explícita. Só proponha
escalonamento depois de evidência concreta — falha reproduzível, limitação de
ferramenta, qualidade insuficiente ou risco que a classe atual não cobre — e
apresente a alternativa e seu impacto de custo antes de aplicá-la.
