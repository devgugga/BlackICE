# Bootstrap do Keycloak no Windows — Design

## Objetivo

Permitir aplicar a configuração idempotente do Keycloak a partir do PowerShell
no Windows, preservando o mesmo comportamento do launcher Bash existente.

## Decisão

A configuração Admin REST passa a viver em um único script POSIX que é enviado
para `sh` dentro do container `keycloak`. Os launchers do host não contêm regras
do Keycloak: cada um somente localiza o repositório, carrega `infra/.env`, valida
`QUARKUS_OIDC_SECRET` e `APP_HOST` e chama a stack Compose canônica.

- `configure-blackice.sh` continua sendo a entrada para Bash.
- `configure-blackice.ps1` é a entrada nativa para PowerShell.
- `configure-blackice-container.sh` é a fonte única da lógica de Admin REST,
  executada no container via stdin.

## Fluxo

1. O launcher lê `infra/.env` sem imprimir segredos.
2. O launcher chama `docker compose` com `infra/compose.yml`,
   `infra/dcm4chee/compose.yml` e `infra/compose.apps.yml`.
3. O launcher injeta apenas as quatro variáveis necessárias e envia o script
   compartilhado a `keycloak sh -s` em modo não interativo.
4. O script no container obtém o token administrativo e cria ou conserva o
   client, mapper, usuário de teste e tema por client.

## Erros e segurança

- A ausência de `.env` ou das variáveis obrigatórias falha antes de chamar o
  Docker.
- Falhas HTTP do Admin REST devem encerrar o script, em vez de parecerem
  configurações bem-sucedidas.
- Nenhum launcher registra `QUARKUS_OIDC_SECRET`, senha administrativa ou token.

## Testes

Será incluído um teste de contrato em PowerShell que substitui o executável
`docker` por uma função controlada. Ele verifica que o launcher envia a entrada
ao container, usa os três arquivos Compose e encaminha as variáveis exigidas,
sem depender de uma stack em execução. A execução real do script no Keycloak
continua coberta pelo fluxo E2E manual existente, pois envolve Docker e estado
de infraestrutura.

## Fora de escopo

- Alterar o modelo de realm, clients, roles ou segurança OIDC.
- Introduzir WSL, Git Bash ou uma dependência de runtime adicional.
- Alterar containers ou arquivos Compose.
