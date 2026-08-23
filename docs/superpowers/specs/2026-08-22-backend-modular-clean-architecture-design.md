# Backend modular com Clean Architecture

**Data:** 2026-08-22  
**Status:** implementada — suíte e empacotamento verificados; grafo AST sincronizado. A extração semântica incremental foi tentada, mas requer uma chave LLM não configurada.  
**Escopo:** `apps/backend/` e a documentação arquitetural que define sua organização.

## Decisão

O backend passa a ser organizado por **módulos de negócio no pacote raiz** e por
responsabilidade arquitetural *dentro de cada módulo*. O prefixo técnico
`dev.blackice.features` deixa de existir.

```text
dev.blackice/
├── ingest/
│   ├── api/                 # HTTP: recursos e contratos de resposta
│   ├── application/
│   │   ├── input/           # entrada independente de HTTP
│   │   ├── usecase/         # orquestração do fluxo
│   │   ├── validation/      # validação e seus modelos
│   │   ├── result/          # resultados do caso de uso e do STOW
│   │   ├── exception/       # falhas do contrato da aplicação
│   │   └── port/            # contratos de saída
│   └── infrastructure/      # adaptadores DICOM e DICOMweb
│       ├── dicom/
│       └── dicomweb/
├── security/
│   ├── api/                 # endpoints transversais de segurança
│   └── infrastructure/oidc/ # adaptação concreta ao Quarkus OIDC
└── session/
    └── api/                 # sessão ainda simples; não antecipar camadas
```

Isto é uma arquitetura **modular primeiro**: não haverá pacotes globais como
`controller`, `service`, `repository`, `dto`, `validator`, `exception` ou
`shared`. Um módulo pequeno pode continuar plano; ele ganha subpacotes quando
isso torna a fronteira do seu caso de uso mais clara. No módulo `ingest`, as
responsabilidades atuais já justificam subpacotes: não há classes de produção
diretamente em `ingest.application`.

`shared` não será criado nesta migração. Só pode existir após dois consumidores
reais demonstrarem uma responsabilidade comum, com uma API pequena e nomeada.

## Limites e direção de dependências

Em um módulo que possua as três responsabilidades, as dependências seguem esta
direção:

```text
api ───────────────► application ◄──────────── infrastructure
                          │
                          └── port
```

- `api` traduz HTTP, autenticação de endpoint e contratos públicos; não contém
  regra de orquestração DICOM nem detalhes de transporte ao archive.
- `application` contém o caso de uso e seus contratos. Define as portas que
  precisa, mas não importa classes de implementação de infraestrutura.
- `infrastructure` implementa portas e integra bibliotecas ou serviços externos
  (DCM4CHE, HTTP/DICOMweb, Quarkus OIDC, banco futuramente). Pode depender de
  `application`; o inverso é proibido.
- Um `domain/` só será criado **dentro do módulo** quando houver regra de
  negócio pura, identidade ou invariantes independentes de framework e I/O. Não
  será criado vazio apenas para reproduzir uma árvore de Clean Architecture.
- Dependências entre módulos usam uma porta/API explicitamente pública. Nenhum
  módulo importa o detalhe de infraestrutura de outro.

Essa forma preserva as vantagens da estrutura .NET de camadas, sem transformar
todo o monorepo em silos técnicos distantes do fluxo de negócio.

## Migração do fluxo de ingestão

A migração inicial é mecânica e não altera rotas, contratos HTTP, limites,
comportamento STOW-RS nem semântica DICOM. A única extração estrutural é tornar
explícita a porta do validador DICOM, para que o caso de uso não dependa de
DCM4CHE diretamente.

| Responsabilidade atual | Destino | Regra |
| --- | --- | --- |
| `IngestResource` | `ingest.api` | Borda HTTP que traduz multipart, autenticação e o resultado do caso de uso. |
| `UploadedDicom` | `ingest.application.input` | Descrição de arquivo recebido, sem depender de `FileUpload`. |
| `IngestStudiesUseCase` | `ingest.application.usecase` | Orquestra validação e armazenamento, sem conhecer HTTP ou adaptadores concretos. |
| `IngestResult`, `StowStudyResult`, `StowInstanceResult` | `ingest.application.result` | Resultados de aplicação e STOW, independentes da borda HTTP. |
| `ValidatedDicom`, `DicomBatchValidation`, `DicomValidationIssue` | `ingest.application.validation` | Modelos produzidos e consumidos no contrato de validação. |
| `ArchiveUnavailableException` | `ingest.application.exception` | Falha do contrato de archive que o caso de uso traduz em resultado. |
| `DicomArchiveGateway` | `ingest.application.port` | Porta de saída para armazenar um estudo no archive. |
| `DicomBatchValidator` | `ingest.application.port` | Contrato de validação que o caso de uso consome. |
| implementação atual de validação | `ingest.infrastructure.dicom` | Adaptador DCM4CHE; será renomeado para tornar a dependência concreta evidente. |
| `HttpDicomArchiveGateway`, `MultipartRelatedBodyPublisher`, `StowResponseParser` | `ingest.infrastructure.dicomweb` | Adaptador STOW-RS e seus detalhes de transporte/parser. |

O adaptador DICOM continuará aplicando as regras estabelecidas em
`docs/domains/dicom/`; a reorganização não autoriza gerar UIDs, assumir
unicidade de `PatientID` nem reencodar pixels.

### Tradução HTTP do resultado

O caso de uso devolve `IngestResult`, que não carrega código ou tipo HTTP. A
borda `ingest.api` decide o status a partir de fatos do resultado: rejeição
local total produz 422; indisponibilidade de archive para todos os estudos
válidos produz 503; os demais resultados completos ou parciais produzem 200.
Essa política fica no adaptador HTTP para que a aplicação permaneça reutilizável
por outra borda.

## Módulos de sessão e segurança

- `SessionResource` e `SessionResponse` tornam-se `session.api`. O módulo ainda
  é simples e não receberá camadas artificiais.
- `CsrfResource` torna-se `security.api`, pois o endpoint é uma borda de
  segurança transversal, não uma regra de ingestão.
- `AccessTokenProvider` será a API pública de `security.application` consumida
  por `ingest.api`; ela devolve o token de acesso da sessão corrente ou rejeita
  a requisição sem uma credencial válida.
- `CurrentAccessToken` torna-se
  `security.infrastructure.oidc` e implementa essa porta com
  `AccessTokenCredential` do Quarkus. Assim nenhum módulo precisa importar um
  adaptador interno de segurança.

A porta é justificada já nesta migração porque protege uma fronteira entre dois
módulos distintos e deixa aberta a troca futura do provedor OIDC, sem expor
detalhes de framework.

## Testes e proteção da arquitetura

- Os testes passam a espelhar os novos pacotes sob `src/test/java`.
- Todos os testes atuais devem continuar a verificar o mesmo comportamento,
  inclusive as falhas parciais do STOW e a validação de arquivos DICOM.
- Será adicionado um teste de arquitetura automatizado para impedir que
  `ingest.application` importe `ingest.infrastructure` e que módulos importem
  a infraestrutura interna de outros módulos. Ele usará `archunit-junit5`
  1.4.2, verificado no guia oficial do ArchUnit para JUnit 5.
- A migração será feita em etapas compiláveis: mover/ajustar imports, executar
  os testes do backend, então aplicar a regra arquitetural.

## Documentação de código

Usaremos Javadoc e comentários como contrato, não como narração do código.

- Javadoc curto em APIs REST públicas, casos de uso, portas e adaptadores cujo
  contrato tenha implicação de DICOM, autenticação, falha ou segurança.
- Javadoc, comentários, nomes de testes e mensagens geradas pelo backend são
  escritos em inglês. A documentação de repositório pode continuar em português
  quando este for o idioma do público.
- `package-info.java` nos módulos ou subpacotes que estabelecem uma fronteira
  arquitetural relevante, descrevendo responsabilidade e dependências aceitas.
- Registros e métodos triviais, privados ou autoexplicativos não receberão
  documentação redundante.
- Comentários explicam apenas o **porquê**: invariantes DICOM, decisões de
  segurança/OIDC, limites operacionais ou workarounds. Regras detalhadas ficam
  nos Domain Packs e podem ser referenciadas, sem copiar seu conteúdo.

## Fora de escopo

- Criar módulos vazios para `reports`, persistência, QIDO ou WADO.
- Mudar as rotas públicas, payloads, limites de upload ou comportamento da
  importação STOW-RS.
- Alterar a configuração OIDC/Keycloak ou decidir token exchange.
- Criar um Maven multi-módulo ou uma camada de domínio genérica global.
- Modificar frontend, infraestrutura Docker ou dados de produção.

## Critérios de aceite

1. Não existe mais código Java em `dev.blackice.features`.
2. O fluxo de ingestão possui as fronteiras `api`, `application` e
   `infrastructure`, com portas consumidas pela aplicação e implementadas pelos
   adaptadores.
3. Sessão e segurança estão em módulos coerentes, sem pasta `shared` criada por
   antecipação.
4. Testes, build e verificação arquitetural passam.
5. `docs/architecture/project-structure.md`, o Domain Pack Quarkus e o README
   do backend descrevem a nova receita sem contradizer `AGENTS.md`.
6. Javadoc e comentários seguem a política acima, sem duplicar os Domain Packs.
