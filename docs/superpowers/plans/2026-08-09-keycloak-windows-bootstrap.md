# Bootstrap do Keycloak no Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplicar a configuração idempotente do Keycloak a partir do PowerShell sem duplicar a lógica de Admin REST.

**Architecture:** A lógica de configuração é movida para um script POSIX enviado a `sh -s` dentro do serviço `keycloak`. Os launchers Bash e PowerShell apenas carregam e validam o ambiente do host, passam as quatro variáveis necessárias e usam a composição canônica de três arquivos.

**Tech Stack:** PowerShell 7+, Bash POSIX, Docker Compose, Keycloak Admin REST, Pester (teste de contrato).

## Global Constraints

- Não alterar o realm, clients, roles, audience nem configurações OIDC existentes.
- Não registrar ou imprimir segredos, tokens ou senhas.
- A lógica de Admin REST deve existir em somente um arquivo: `configure-blackice-container.sh`.
- Os dois launchers devem chamar `infra/compose.yml`, `infra/dcm4chee/compose.yml` e `infra/compose.apps.yml`.
- Os erros HTTP do Admin REST devem falhar o comando.

---

### Task 1: Launchers multiplataforma com núcleo único

**Files:**
- Create: `infra/keycloak/configure-blackice-container.sh`
- Create: `infra/keycloak/configure-blackice.ps1`
- Create: `infra/keycloak/configure-blackice.tests.ps1`
- Modify: `infra/keycloak/configure-blackice.sh`
- Modify: `infra/keycloak/README.md`

**Interfaces:**
- Consumes: `infra/.env` com `QUARKUS_OIDC_SECRET` e `APP_HOST`; a stack Compose iniciada.
- Produces: os launchers `bash infra/keycloak/configure-blackice.sh` e `pwsh -File infra/keycloak/configure-blackice.ps1`, ambos configurando o mesmo client, mapper, usuário e tema no realm `blackice`.

- [ ] **Step 1: Escrever o teste de contrato falhando**

Crie `infra/keycloak/configure-blackice.tests.ps1` sem exigir uma stack Docker. O teste deve definir uma função `docker` controlada que capture os argumentos e stdin e executar o launcher PowerShell contra um `.env` temporário contendo valores literais seguros. Deve falhar enquanto `configure-blackice.ps1` não existir e, depois, verificar:

```powershell
Assert-True ($capturedArguments -contains 'compose') 'invoca docker compose'
Assert-True ($capturedArguments -contains 'infra/compose.yml') 'inclui compose base'
Assert-True ($capturedArguments -contains 'infra/dcm4chee/compose.yml') 'inclui compose do archive'
Assert-True ($capturedArguments -contains 'infra/compose.apps.yml') 'inclui compose das aplicações'
Assert-True ($capturedArguments -contains 'QUARKUS_OIDC_SECRET=contract-secret') 'encaminha segredo ao container'
Assert-True ($capturedStdin -match 'blackice-quarkus') 'envia núcleo de configuração ao container'
```

O teste deve encerrar com código 0 apenas quando todas as asserções forem verdadeiras.

- [ ] **Step 2: Executar o teste para confirmar o estado RED**

Run:

```powershell
pwsh -NoProfile -File infra/keycloak/configure-blackice.tests.ps1
```

Expected: falha porque `infra/keycloak/configure-blackice.ps1` ainda não existe.

- [ ] **Step 3: Implementar o núcleo e os launchers mínimos**

Mova a lógica atualmente entre o heredoc `INNER` de `configure-blackice.sh` para `configure-blackice-container.sh`. Preserve os valores `KB=https://localhost:8843/auth`, `REALM=blackice`, `ARC_CLIENT=dcm4chee-arc-rs`, client `blackice-quarkus`, mapper `arc-audience`, usuário `dr.teste` e tema `blackice`.

Em todas as chamadas Admin REST que dependem de sucesso, use `curl -f` junto de `-k -s` para que resposta 4xx/5xx interrompa a configuração. Preserve a limpeza de `/tmp/client.json` em qualquer saída.

Substitua o heredoc do launcher Bash por envio do arquivo compartilhado ao comando abaixo, mantendo validações e sem expor valores:

```bash
docker compose -f "$ROOT/infra/compose.yml" -f "$ROOT/infra/dcm4chee/compose.yml" -f "$ROOT/infra/compose.apps.yml" \
  exec -T -e "QUARKUS_OIDC_SECRET=$QUARKUS_OIDC_SECRET" -e "APP_ORIGIN=http://${APP_HOST}" \
  -e "ARC_CLIENT=dcm4chee-arc-rs" -e "REALM=blackice" keycloak sh -s < "$ROOT/infra/keycloak/configure-blackice-container.sh"
```

Crie o launcher PowerShell com parâmetro obrigatório `-EnvFile`, com valor padrão `infra/.env`. Ele deve interpretar linhas `NOME=valor` sem executar o arquivo, ignorar linhas vazias/comentários, validar as duas chaves exigidas e executar o mesmo comando `docker compose exec -T ... keycloak sh -s`, enviando o núcleo compartilhado por stdin. Não inclua os valores capturados em mensagens de erro.

- [ ] **Step 4: Executar o teste para confirmar o estado GREEN**

Run:

```powershell
pwsh -NoProfile -File infra/keycloak/configure-blackice.tests.ps1
```

Expected: exit 0 e todas as asserções aprovadas.

- [ ] **Step 5: Atualizar a documentação operacional**

Em `infra/keycloak/README.md`, substitua a seção `Como aplicar` por instruções equivalentes para Bash e PowerShell:

```sh
bash infra/keycloak/configure-blackice.sh
```

```powershell
pwsh -File infra/keycloak/configure-blackice.ps1
```

Documente que ambos exigem Docker Desktop/CLI disponível e a stack de Keycloak/Archive em execução. Explique que PowerShell não requer WSL ou Git Bash.

- [ ] **Step 6: Verificar os artefatos e o comportamento**

Run:

```powershell
pwsh -NoProfile -File infra/keycloak/configure-blackice.tests.ps1
git diff --check
git diff -- infra/keycloak
```

Expected: teste com exit 0, nenhum erro de whitespace e diff limitado ao núcleo, launchers, teste e documentação.

- [ ] **Step 7: Commit focado após a verificação**

```bash
git add docs/superpowers/specs/2026-08-09-keycloak-windows-bootstrap-design.md docs/superpowers/plans/2026-08-09-keycloak-windows-bootstrap.md infra/keycloak
git commit -m "✨ melhora bootstrap Keycloak no Windows"
```
