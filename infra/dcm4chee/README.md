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

Anexado à rede `blackice`, definida no compose base `infra/compose.yml`
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
3. **`REALM_NAME=blackice`** setado em `keycloak` e `arc`. O nome padrão do
   realm importado no primeiro startup é `dcm4che`; foi `dcm4chee` até a
   Fase 2 do spec 2026-08-07, que renomeou o realm para `blackice` para tirar
   o nome do produto de terceiro da URL de login. **Numa base vazia (cold
   start, volume novo) esta variável basta** — o import de boot cria o realm
   já com este nome. **Numa base já populada ela não renomeia nada**: o rename
   foi feito IN PLACE pela Admin REST
   (`PUT /admin/realms/<atual> {"realm":"blackice"}`), e este valor só
   acompanha o nome real para que o import de boot encontre o realm existente.
   Ver o comentário em `infra/dcm4chee/compose.yml` para o porquê (o
   `dcm4che-realm.json` embute 112 UUIDs literais, então reimportar sob outro
   nome contra um realm já presente colide em `KEYCLOAK_ROLE.ID`) e para a
   receita de reversão de 5 passos. Precisa estar setado nos DOIS serviços (documentado no wiki
   oficial), senão o Archive falha ao autorizar usuários via chamada REST
   out-of-band ao Keycloak.
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
   padrão já usado por `PRODUCT_DB*` em `infra/compose.yml`.
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

- Keycloak, caminho do browser (Fase 1 do spec 2026-08-07): servido
  **same-origin** pelo Traefik em `http://blackice.localhost/auth`. Sem porta,
  sem host diferente, sem aviso de certificado.
- Keycloak, listener HTTPS direto: `https://localhost:8843/auth` (certificado
  autoassinado padrão da imagem — use `curl -k`). É o backchannel do Archive;
  o browser não passa mais por aqui.
  **`KC_HTTP_RELATIVE_PATH=/auth` é o root path do servidor inteiro**, então o
  `/auth` vale também para a 8843.
- Realm: **`blackice`**. O issuer anunciado é `http://blackice.localhost/auth/realms/blackice`
  mesmo quando você consulta pela 8843 — `KC_HOSTNAME` é fixo, então as
  frontend URLs saem dele, não do endereço da requisição.
- Admin console do Keycloak (realm master): `https://localhost:8843/auth/admin`
  (usuário `admin`, senha em `KEYCLOAK_ADMIN_PASSWORD` no `.env`).
  ⚠️ **O admin console e a Admin REST também respondem pelo Traefik**, em
  `http://blackice.localhost/auth/admin/...` — HTTP puro, no mesmo origin do
  produto (medido: `/auth/admin/master/console/` devolve `200`). Não é
  intencional e sim consequência de `KC_HTTP_RELATIVE_PATH` ser o root path do
  servidor inteiro: o `PathPrefix(/auth)` do router carrega junto tudo que mora
  sob `/auth`. Aceitável em dev local (a stack não sai do host e o Traefik já
  roda com `--api.insecure=true`); antes de qualquer exposição além disso,
  ver a linha correspondente na tabela de Riscos do spec
  `docs/superpowers/specs/2026-08-07-keycloak-same-origin-design.md`.
- Archive (WildFly): **sem porta publicada no host** (invariante: DCM4CHEE
  nunca exposto ao browser — só o Quarkus fala DICOMweb com ele pela rede
  interna). Dentro da rede `blackice` responde em `http://arc:8080/dcm4chee-arc`
  (HTTP), `https://arc:8443/dcm4chee-arc` (HTTPS) e admin console `:9990` — use
  `docker compose exec arc ...` para alcançá-los; NÃO adicione `ports:` ao
  serviço `arc`.
- AE Title padrão: `DCM4CHEE`. Usuários pré-configurados no realm:
  `root`/`changeit`, `admin`/`changeit`, `user`/`changeit` (senhas fixas do
  realm importado pela imagem — trocar antes de qualquer uso além de dev
  local).

## Subir a stack

```bash
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml up -d
```

Ordem de dependência (`depends_on`): `ldap`/`mariadb` → `keycloak` → `arc-db`
→ `arc`. O Archive (WildFly) leva 1-2 min para ficar pronto após o container
subir — aguarde e faça polling do log em vez de assumir falha.

## Verificação

```bash
curl -sk https://localhost:8843/auth/realms/blackice/.well-known/openid-configuration | head -c 300
```
Esperado: JSON com `"issuer":"http://blackice.localhost/auth/realms/blackice"` —
o issuer é o mesmo pelos dois listeners (ver "Portas e acesso" acima).

O caminho que o browser usa de fato, pelo Traefik:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  http://blackice.localhost/auth/realms/blackice/.well-known/openid-configuration
```
Esperado: `200`.

```bash
docker compose -f infra/compose.yml -f infra/dcm4chee/compose.yml -f infra/compose.apps.yml \
  exec arc curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies
```
Esperado: `401` (QIDO exige Bearer token — confirma que o DICOMweb está de pé
e protegido).
