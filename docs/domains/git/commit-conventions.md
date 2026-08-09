# Convenções de commit

Este documento é a fonte de verdade para agentes que criam commits locais.

## Autoridade e branch

1. Examine a branch atual antes de agir.
2. Em uma branch de trabalho, crie o commit local ao concluir uma tarefa com
   alterações versionáveis e verificadas.
3. Em `main`, só crie um commit com autorização explícita do humano, exceto se
   houver autorização anterior, inequívoca e ainda aplicável no contexto ativo.
4. Não crie branches, não altere o histórico existente e nunca faça push.

## Seleção segura do escopo

1. Inspecione `git status` e os diffs antes de selecionar arquivos.
2. Inclua somente as mudanças comprovadamente relacionadas à tarefa concluída.
3. Não misture trabalho alheio ou sem relação em uma árvore compartilhada.
4. Quando não for possível separar o escopo com segurança, pare e explique o
   motivo; não crie um commit parcial ou ambíguo.

## Pré-requisitos

1. Execute as validações relevantes e registre somente resultados comprovados.
2. Antes de commitar arquivos versionados, atualize o Graphify conforme
   `AGENTS.md` e `docs/architecture/graphify.md`, revise o diff de
   `graphify-out/` e inclua apenas os artefatos portáveis que representem a
   mudança.

## Mensagem do commit

1. Consulte [Gitmoji](https://gitmoji.dev/) e escolha o emoji cujo significado
   publicado corresponde à mudança.
2. Use o **caractere literal** do emoji (`🎉`), nunca o shortcode (`:tada:`).
3. Crie o commit diretamente; não exiba nem proponha a mensagem para aprovação.
4. Escreva em português um título no formato:

   ```text
   gitmoji verbo resultado: contexto
   ```

5. Não invente capacidades, testes ou garantias não demonstrados pelo diff e
   pelas validações executadas.

### Corpo (obrigatório)

O corpo é **obrigatório** em todo commit que altere código, configuração,
infraestrutura ou documentação. Título sozinho só é aceito no commit de
sincronização do grafo (ver adiante). Se você não consegue escrever o corpo, o
escopo do commit não foi entendido — pare, releia o diff e recomece.

Escreva cada seção como `### <emoji> <nome>`, separando seções consecutivas com
uma linha `---`. Use apenas as seções abaixo, sempre nesta ordem, e inclua
somente as que o diff comprova:

| Seção | Quando usar |
| :-- | :-- |
| `### ✅ Novas funcionalidades` | Capacidade que não existia antes. |
| `### 💡 Melhorias na arquitetura` | Estrutura, fronteiras, decisões de desenho. |
| `### 🧼 Boas práticas e validações` | Higiene, testes, healthchecks, `.gitignore`. |
| `### 🔐 Permissões e controle de acesso` | Auth, OIDC, realms, escopos, segredos. |
| `### 🚀 Resultado` | **Sempre presente.** |

Regras de conteúdo:

- Ao menos uma seção temática **mais** `Resultado`. `Resultado` nunca é omitida.
- Seções temáticas usam bullets; cada bullet descreve uma mudança verificável no
  diff, com o artefato concreto entre crases (arquivo, serviço, variável).
- `Resultado` é um parágrafo corrido — o estado em que o repositório ficou e o
  que passa a ser possível a seguir. Não repita os bullets.
- Negrito apenas em componentes e versões (`**Traefik v3.1**`), não para ênfase.

### Exemplo canônico

```text
🎉 estabelece a fundação da infraestrutura: rede blackice, traefik e postgres

### ✅ Novas funcionalidades

* Scaffolding do monorepo com diretórios `backend/`, `frontend/` e `infra/`.
* Orquestração da infraestrutura via `docker-compose.yml` com **Traefik v3.1**
  e **PostgreSQL 17**.

---

### 💡 Melhorias na arquitetura

* Estabelecimento da rede Docker `blackice` como ponto de integração
  (same-origin).
* **Traefik v3.1** como reverse proxy centralizado (porta 80, dashboard DEV
  em `:8081`).
* **PostgreSQL 17** dedicado aos dados do produto Quarkus, isolado do archive
  DCM4CHEE.
* Persistência via volume nomeado `product-db-data`.

---

### 🧼 Boas práticas e validações

* `.gitignore` para artefatos de build (`node_modules/`, `dist/`, `target/`) e
  segredos (`infra/.env`).
* `.env.example` como template seguro (`APP_HOST`, credenciais de produto,
  OIDC secret).
* Healthcheck do PostgreSQL para garantir disponibilidade antes dos dependentes.

---

### 🚀 Resultado

A fundação de infraestrutura do BlackICE está pronta: rede Docker `blackice`,
Traefik como borda, PostgreSQL do produto isolado e gestão de segredos via
`.env`. Pronto para a integração dos serviços backend e frontend.
```

### Commit de sincronização do grafo

O segundo commit focado que carrega **apenas** `graphify-out/**` é a única
exceção à obrigatoriedade do corpo. Ele usa exatamente:

```text
🕸️ sincroniza grafo de conhecimento
```

Sem corpo. Se o commit tocar qualquer arquivo fora de `graphify-out/`, ele não
é um commit de sincronização e as regras normais valem integralmente.

## Relato ao orquestrador

Você cria o commit sem exibir a mensagem para aprovação, então este relato é a
única janela do humano sobre o que você fez. Ao terminar, devolva:

1. A saída de `git log -1 --stat` do commit criado.
2. Uma linha por arquivo deixado de fora, com o motivo.
3. As validações executadas e o resultado real de cada uma.

Se você parou sem commitar, informe o motivo e o estado em que deixou a árvore.

## Alcance destas regras

Estas convenções valem **daqui para a frente**. O histórico existente contém
commits em shortcode (`:spider_web:`) e corpos com outra renderização (`##` sem
emoji); nada disso deve ser reescrito — o histórico é imutável por política.
Pelo mesmo motivo, **não use `git log` como referência de formato**: o exemplo
canônico acima é a única fonte.

## Atribuição

É absolutamente proibido incluir trailers `Co-authored-by`, `Co-authored-by:`
ou qualquer outra atribuição de coautoria no commit.
