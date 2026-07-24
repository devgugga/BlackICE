# Stack DCM4CHEE-secure

Compose adaptado do oficial (wiki "Run secured archive services on a single host"):
https://github.com/dcm4che/dcm4chee-arc-light/wiki/Running-on-Docker
(página específica: `Run-secured-archive-services-on-a-single-host.md`, obtida
clonando `https://github.com/dcm4che/dcm4chee-arc-light.wiki.git`).

O YAML publicado no HEAD atual do wiki (commit `cbdb3671`, "upgrade to 5.34.3")
já documenta **Keycloak 26.0.6**, que usa variáveis de bootstrap de admin
diferentes (`KC_BOOTSTRAP_ADMIN_USERNAME`/`PASSWORD`) e é incompatível com a tag
25.0.6 exigida pelos Global Constraints deste projeto. Para não misturar
variáveis de versões diferentes do Keycloak, o compose aqui foi montado a
partir do commit do wiki que efetivamente documentava **Keycloak 25.0.5**
(`6d8b8afe`, "Fixed errors in the keycloak container Docker command line"),
que usa `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` — e então as tags de imagem
foram substituídas pelas pinadas abaixo (25.0.5 → 25.0.6 é um bump de patch
dentro da mesma geração de variáveis, confirmado no README oficial da imagem:
https://github.com/dcm4che-dockerfiles/keycloak-quarkus).

## Tags pinadas (Global Constraints)

| Serviço | Imagem | Tag |
| :-- | :-- | :-- |
| `ldap` | `dcm4che/slapd-dcm4chee` | `2.6.10-34.3` |
| `mariadb` | `mariadb` | `10.11.4` |
| `keycloak` | `dcm4che/keycloak` | `25.0.6` |
| `arc-db` | `dcm4che/postgres-dcm4chee` | `17.4-34` |
| `arc` | `dcm4che/dcm4chee-arc-psql` | `5.34.3-secure` |

Conforme `docs/architecture/dcm4chee-archive.md` (baseline 5.34.3). Nenhuma tag
usa `latest`.

Anexado à rede `blackice`, definida no compose base `infra/docker-compose.yml`
(sempre incluído com `-f` nas invocações). Este arquivo apenas referencia a
rede — não a declara como `external`, para não quebrar o bring-up numa máquina
limpa onde ela ainda não existe.

## Desvios em relação ao compose oficial (documentados)

1. **Serviço `mariadb` adicionado.** As "Interfaces" da Task 2 listavam apenas
   4 serviços (`ldap`, `keycloak`, `arc-db`, `arc`), mas o compose oficial
   "secure" usa MariaDB como banco de dados do Keycloak desde antes da série
   5.30 (inclusive na versão 25.0.5/25.0.6 pinada aqui) — sem ele o Keycloak
   não sobe. Adicionado como 5º serviço, obrigatório.
2. **`db` → `arc-db`.** O serviço oficial se chama `db`; renomeado para
   `arc-db` para casar com o nome usado nas Interfaces da Task 2. Como a
   imagem do Archive resolve o Postgres pelo hostname `db` por padrão, foi
   necessário setar explicitamente `POSTGRES_HOST=arc-db` no serviço `arc`
   (confirmado no README oficial da imagem
   `dcm4che-dockerfiles/dcm4chee-arc-psql`) — sem isso o Archive não
   encontraria o banco.
3. **`REALM_NAME=dcm4chee`** setado em `keycloak` e `arc`. O nome padrão do
   realm importado no primeiro startup é `dcm4che` (sem o segundo "e"); foi
   sobrescrito para `dcm4chee` para casar com o nome do produto e com a
   verificação do Passo 6 da Task 2. Precisa estar setado nos DOIS serviços
   (documentado no wiki oficial), senão o Archive falha ao autorizar usuários
   via chamada REST out-of-band ao Keycloak.
4. **Volumes nomeados do Docker em vez de bind mounts em `/var/local/...`.**
   O compose oficial usa bind mounts para paths Linux (`/var/local/dcm4chee-arc/...`,
   `/etc/localtime`, `/etc/timezone`) que não existem no host Windows deste
   projeto. Substituídos por volumes nomeados (`dcm4chee-ldap-data`,
   `dcm4chee-mysql-data`, `dcm4chee-keycloak-data`, `dcm4chee-db-data`,
   `dcm4chee-wildfly-data`, `dcm4chee-storage`, `dcm4chee-slapd-conf`); os
   mounts de timezone foram removidos (opcionais no original).
5. **Credenciais e hostname parametrizados via `infra/.env`** (`DCM4CHEE_HOST`,
   `KEYCLOAK_ADMIN_PASSWORD`, `DCM4CHEE_DB*`, `KEYCLOAK_DB*`) em vez dos
   valores fixos (`secret`/`pacs`/`changeit`) do exemplo oficial, seguindo o
   padrão já usado por `PRODUCT_DB*` em `infra/docker-compose.yml`.
6. **Sem colisão de porta com o resto da stack.** Traefik usa `80` e `8081`;
   nenhum serviço do DCM4CHEE usa essas portas. Todas as portas do compose
   oficial (`389`, `636`, `3306`, `8843`, `5432`, `8080`, `8443`, `9990`,
   `9993`, `11112`, `2762`, `2575`, `12575`) estavam livres no host no momento
   da subida — **nenhum remapeamento de porta foi necessário**. (Havia um
   stack DCM4CHEE antigo/não relacionado de outro projeto — containers
   `dcm4chee-arc-1`, `dcm4chee-keycloak-1`, `dcm4chee-db-1`, `dcm4chee-ldap-1`,
   rede `hexmed_network` — mas estava parado (`Exited`) e não publicava
   portas no host.)

## Portas e acesso

- Keycloak (HTTPS, certificado autoassinado padrão da imagem):
  `https://localhost:8843` — use `curl -k` ou aceite o aviso do browser.
- Realm importado: **`dcm4chee`**. Issuer esperado:
  `https://localhost:8843/realms/dcm4chee`.
- Admin console do Keycloak (realm master): `https://localhost:8843/admin`
  (usuário `admin`, senha em `KEYCLOAK_ADMIN_PASSWORD` no `.env`).
- Archive (WildFly): HTTP `http://localhost:8080/dcm4chee-arc`, HTTPS
  `https://localhost:8443/dcm4chee-arc`, Admin Console `http://localhost:9990`.
- AE Title padrão: `DCM4CHEE`. Usuários pré-configurados no realm:
  `root`/`changeit`, `admin`/`changeit`, `user`/`changeit` (senhas fixas do
  realm importado pela imagem — trocar antes de qualquer uso além de dev
  local).

## Subir a stack

```bash
cd infra && docker compose -f docker-compose.yml -f dcm4chee/docker-compose.dcm4chee.yml up -d
```

Ordem de dependência (`depends_on`): `ldap`/`mariadb` → `keycloak` → `arc-db`
→ `arc`. O Archive (WildFly) leva 1-2 min para ficar pronto após o container
subir — aguarde e faça polling do log em vez de assumir falha.

## Verificação

```bash
curl -sk https://localhost:8843/realms/dcm4chee/.well-known/openid-configuration | head -c 300
```
Esperado: JSON com `"issuer":"https://localhost:8843/realms/dcm4chee"`.

```bash
docker compose -f infra/docker-compose.yml -f infra/dcm4chee/docker-compose.dcm4chee.yml \
  exec arc curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies
```
Esperado: `401` (QIDO exige Bearer token — confirma que o DICOMweb está de pé
e protegido).

Ver `.superpowers/sdd/task-2-report.md` para as saídas reais dessas
verificações.
