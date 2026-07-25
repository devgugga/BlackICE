# Infraestrutura BlackICE

Os três arquivos Compose são usados juntos:

- `compose.yml`: fundação compartilhada.
- `dcm4chee/compose.yml`: archive seguro e dependências.
- `compose.apps.yml`: aplicações BlackICE.

Crie `infra/.env` localmente a partir de `.env.example`. O arquivo
`.env.example` é apenas o modelo; nunca versione `.env`.

## Subir a stack

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml up -d --build
```

## Validar a configuração

```powershell
cd infra
docker compose -f compose.yml -f dcm4chee/compose.yml -f compose.apps.yml config --quiet
```
