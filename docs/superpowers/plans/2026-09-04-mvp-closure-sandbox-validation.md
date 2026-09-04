# Handoff de validação do fechamento do MVP no sandbox

Este registro acompanha a integração de `fix/mvp-closure-gates` em `main` por
autorização humana em 2026-09-04. A integração preserva os gates que dependem de
Docker como pendentes; ela não os declara aprovados.

## Evidência disponível antes da integração

Executado no checkout da branch em 2026-09-04:

- `.problem-catalog`: `mise exec -- pnpm test` passou com 7 testes e
  `mise exec -- pnpm check` confirmou catálogo, lock e artefatos consistentes;
- frontend: `mise exec -- pnpm test` passou com 414 testes em 30 arquivos;
- frontend: `mise exec -- pnpm build` terminou com exit code 0;
- Playwright: `mise exec -- pnpm exec playwright test --list --reporter=line`
  enumerou 42 testes em 6 arquivos, incluindo os dois projetos do caso
  concorrente STOW/QIDO;
- Compose: `docker compose --env-file .env.example -f compose.yml
  -f dcm4chee/compose.yml -f compose.apps.yml config --quiet` terminou com exit
  code 0;
- backend focado: `mise exec -- mvn -Dtest=ApiHttpFailureHandlerTest test`
  passou com 1 teste, sem falhas ou erros, quando executado fora das restrições
  de self-attach do sandbox;
- revisão geral: nenhum achado Critical ou Important; o único risco operacional
  registrado foi a execução ainda pendente do E2E concorrente de 30 MiB;
- revisão DICOM/DICOMweb: gate semântico aprovado para UIDs `2.25`, hierarquia,
  coerência entre dimensões e Pixel Data e separação dos papéis STOW/QIDO;
- `git diff --check` terminou sem erros.

A suíte backend completa não executou testes. O Quarkus tentou iniciar o
PostgreSQL por Dev Services, mas o ambiente negou acesso a
`/var/run/docker.sock`; o Maven encerrou durante a descoberta com 0 testes.
Isso é bloqueio de ambiente, não resultado verde nem falha funcional da suíte.

## Sintoma do Keycloak a reproduzir primeiro

No ambiente anterior, `bash infra/keycloak/configure-blackice.sh` informou
`service "keycloak" is not running`. O log do container mostrou
`/docker-entrypoint.sh: line 13: cmp: command not found`, copiou os artefatos
Quarkus, executou `kc.sh build` e terminou a saída em:

```text
Next time you run the server, just run:

        kc.sh start --import-realm --optimized
```

Ainda não foi possível inspecionar o estado e o exit code do container porque o
ambiente exigia `sudo` interativo. Não assumir que a ausência de `cmp` é a causa:
confirmar se ela é apenas aviso e localizar o ponto real de saída antes de
alterar a imagem ou o Compose.

## Gates pendentes no sandbox com Docker

Partindo da raiz do repositório e de um `infra/.env` válido:

1. Subir a composição e capturar o estado completo do Keycloak:

   ```bash
   docker compose --env-file infra/.env \
     -f infra/compose.yml \
     -f infra/dcm4chee/compose.yml \
     -f infra/compose.apps.yml up -d --build

   docker compose --env-file infra/.env \
     -f infra/compose.yml \
     -f infra/dcm4chee/compose.yml \
     -f infra/compose.apps.yml ps -a

   docker compose --env-file infra/.env \
     -f infra/compose.yml \
     -f infra/dcm4chee/compose.yml \
     -f infra/compose.apps.yml logs --no-color keycloak
   ```

   O serviço precisa permanecer em execução e responder nos dois caminhos
   documentados em `infra/dcm4chee/README.md`. Se encerrar, registrar o exit
   code, o comando efetivo e o entrypoint com `docker inspect` antes da correção.

2. Confirmar bootstrap e disponibilidade do realm:

   ```bash
   bash infra/keycloak/configure-blackice.sh
   curl -sk \
     https://localhost:8843/auth/realms/blackice/.well-known/openid-configuration
   curl -s -o /dev/null -w '%{http_code}\n' \
     http://blackice.localhost/auth/realms/blackice/.well-known/openid-configuration
   ```

   Esperado: o configurador termina em `OK`, o discovery HTTPS devolve JSON e o
   caminho same-origin devolve HTTP 200.

3. Executar a suíte backend completa e repetir o teste de streaming três vezes:

   ```bash
   cd apps/backend
   mise exec -- mvn test -Dquarkus.http.test-port=8082

   for run in 1 2 3; do
     mise exec -- mvn \
       -Dtest=ApiHttpFailureHandlerTest,WadoFrameResourceTest \
       test -Dquarkus.http.test-port=8082 || exit 1
   done
   cd ../..
   ```

   Esperado: zero falhas/erros em todas as execuções. O caso
   `body_throwing_after_first_chunk_is_not_replaced_by_problem_details` precisa
   preservar status e headers WADO sem anexar Problem Details ao stream já
   iniciado.

4. Executar isoladamente o gate concorrente STOW/QIDO:

   ```bash
   cd apps/frontend
   CI=true mise exec -- pnpm exec playwright test e2e/worklist.spec.ts \
     --grep "concurrent STOW import and QIDO worklist search" \
     --reporter=line
   cd ../..
   ```

   Esperado: desktop e mobile passam. Conferir em `concurrency-timings` que o
   QIDO e as duas provas de lote parcial terminam antes da resposta do POST de
   ingestão, e que as 30 instâncias são confirmadas pelo STOW.

5. Executar o Playwright completo contra a composição:

   ```bash
   cd apps/frontend
   CI=true mise exec -- pnpm exec playwright test --reporter=line
   ```

   Esperado: os 42 testes enumerados passam, cobrindo autenticação, ingestão,
   Problem Details, laudos, viewer e worklist. Não apagar volumes nem dados ao
   final sem autorização humana explícita.

## Critério de encerramento

O fechamento só recebe gate integral quando o Keycloak permanece saudável, o
bootstrap termina, a suíte backend completa passa, o streaming passa três vezes
e os Playwright focado e completo ficam verdes. Qualquer correção feita no novo
sandbox exige repetir os gates afetados e sincronizar novamente o Graphify antes
de novo commit.
