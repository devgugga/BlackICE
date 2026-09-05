# Fechamento de correção DICOM do MVP

## Objetivo

Encerrar os gates técnicos do MVP sem ampliar seu escopo funcional, corrigindo
as violações de identidade DICOM, interpretação de metadata, consolidação de
resultado STOW, completude da classificação QIDO e ordenação espacial encontradas
na auditoria de 31 de agosto de 2026.

Esta spec complementa, sem substituir:

- `2026-08-09-manual-dicom-import-design.md`;
- `2026-08-22-worklist-qido-rs-design.md`;
- `2026-08-24-study-viewer-design.md`;
- `2026-08-25-authenticated-reports-design.md`.

O MVP continua composto pelos quatro fluxos já aprovados: ingestão STOW-RS,
worklist QIDO-RS, viewer WADO-RS e laudos autenticados.

## Escopo

Entram neste fechamento:

1. comparação exata de identidade do estudo retornado por QIDO;
2. preservação exata e validação de VM dos UIDs DICOM JSON;
3. validação de VR e VM do metadata WADO publicado ao frontend;
4. tratamento correto de Rescale Intercept e Rescale Slope;
5. precedência global dos resultados STOW;
6. comprovação de completude da varredura QIDO usada para classificar séries;
7. validação dos vetores usados na ordenação espacial;
8. estabilização do teste de streaming WADO que deixou o CI vermelho;
9. validação do E2E de concorrência STOW/QIDO já presente no worktree;
10. execução de todos os gates do MVP e revisão DICOM final.

Não entram:

- novos SOP Classes, multi-frame, cine ou novos layouts do viewer;
- transcodificação ou re-encoding de Pixel Data;
- persistência de Pixel Data no PostgreSQL do produto;
- novos tipos no catálogo de problemas;
- jobs assíncronos, retries automáticos ou mudanças de workflow clínico;
- qualquer item do backlog de evolução sem priorização humana própria.

## Princípios de correção

- `StudyInstanceUID`, `SeriesInstanceUID` e `SOPInstanceUID` são identidade
  imutável e são comparados como strings exatas, sem `trim`, case folding ou
  outra normalização.
- Uma resposta HTTP 2xx do Archive não substitui a validação do contrato
  DICOMweb.
- Metadata inválida ou incompleta nunca recebe valores físicos inventados para
  aparentar uma imagem calibrada.
- Uma série só é classificada após comprovação de que todas as suas instâncias
  relevantes foram observadas.
- Nenhuma correção altera bytes de Pixel Data ou Transfer Syntax.
- Mensagens, logs e Problem Details não contêm UIDs, PatientID, payloads ou URLs
  internas.

## QIDO: identidade e hierarquia

### UIDs no parser

Cada atributo de UID exige:

- `vr` igual a `UI`;
- `Value` como array de cardinalidade exatamente 1;
- item textual não vazio;
- valor aceito por `UIDUtils.isValid` exatamente como recebido.

Espaço antes ou depois do UID não é corrigido: torna a resposta inválida. A
regra vale para UIDs de estudo, série, SOP Instance e SOP Class.

### Estudo solicitado versus retornado

`HttpStudyHierarchyGateway.findStudy` compara
`result.studyInstanceUid()` com `study.studyInstanceUid()` antes de devolver o
resultado. Divergência é `ArchiveViewerException.Reason.INVALID_RESPONSE`.

O caso de uso jamais publica cabeçalho de um estudo enquanto consulta séries ou
instâncias de outro.

## QIDO: paginação e completude

A varredura de instâncias continua usando `limit` e `offset` no Archive. Cada
resposta deve ser validada antes de acumular seus itens:

- um header HTTP `Warning` cujo warn-code seja `299` informa que há resultados
  adicionais e obriga a continuação da paginação;
- SOP Instance UID repetido entre páginas torna a resposta inválida;
- qualquer página não vazia com `299` avança exatamente pelo número de itens
  recebidos, ainda que o Archive aplique `maxResults` menor que o `limit` pedido;
- uma resposta sem `299` encerra a paginação;
- uma página vazia acompanhada de `299` é contraditória e invalida a resposta;
- `299` em consultas não paginadas de estudo ou séries invalida a resposta;
- `NumberOfSeriesRelatedInstances` é solicitado para cada série e é obrigatório
  para a prova de completude desta classificação;
- para cada série, a contagem declarada deve coincidir com a quantidade única de
  instâncias acumuladas antes da classificação.

Ausência de contagem comparável, truncamento ou divergência de contagem converge
para resposta inválida do Archive. O sistema não classifica uma amostra parcial
como série suportada.

Essas regras seguem DICOM PS3.18 §8.3.4.4: `Warning: 299` sinaliza resultados
restantes, e `offset` avança pela quantidade efetivamente recebida.

## WADO metadata: VR, VM e valores físicos

O parser valida o `vr` e a cardinalidade de cada atributo que consome. O baseline
aprovado é:

| Atributos | VR esperado | VM aceita |
| :-- | :-- | :-- |
| Study, Series, SOP Instance, SOP Class e Frame of Reference UID | `UI` | `1` |
| Number of Frames e Instance Number | `IS` | `1` |
| Rows, Columns, Samples per Pixel, Bits Allocated, Bits Stored, High Bit, Pixel Representation e Planar Configuration | `US` | `1` |
| Photometric Interpretation | `CS` | `1` |
| Image Position Patient | `DS` | `3` |
| Image Orientation Patient | `DS` | `6` |
| Pixel Spacing | `DS` | `2` |
| Rescale Intercept e Rescale Slope | `DS` | `1` |
| Window Center e Window Width | `DS` | `1-n`, preservado como array |

Atributo opcional ausente permanece ausente. Atributo presente com VR, VM, tipo
ou valor inválido invalida toda a resposta.

### Rescale

- CT Image Storage exige `RescaleIntercept` e `RescaleSlope` presentes e válidos.
- Nos demais SOP Classes aprovados, os dois podem estar ausentes quando o IOD não
  os exigir.
- Presença de apenas um dos dois é inválida.
- `RescaleSlope` igual a zero é inválido.
- O backend preserva os valores recebidos; não cria `0` ou `1`.
- O metadata provider do frontend não publica `modalityLutModule` quando o par
  estiver ausente. Quando presente, publica exatamente os valores recebidos.

O frontend não apresenta valores armazenados como HU ou outra unidade calibrada
sem metadata que prove essa transformação.

O suporte completo a VOI LUT Sequence fica adiado em `EVO-013`.

## STOW: consolidação determinística

O parser pode aceitar a forma objeto e a forma array já suportadas, mas agrega o
resultado de cada SOP Instance UID por precedência global:

```text
REJECTED > WARNING > ACCEPTED
```

Regras:

- uma rejeição nunca é sobrescrita por warning ou sucesso posterior;
- um warning nunca é sobrescrito por sucesso posterior;
- contradições entre datasets são resolvidas pela precedência acima;
- SOPs submetidos sem confirmação continuam `UNCONFIRMED`;
- o status HTTP isolado nunca confirma armazenamento.

A implementação terá teste explícito no qual um SOP aparece como falho em um
dataset e referenciado em outro posterior.

## Ordenação espacial

Antes de normalizar `ImageOrientationPatient`, cada vetor linha/coluna deve ter
norma aproximadamente igual a 1 dentro da tolerância `1e-4`. Depois disso, os
vetores também precisam ser ortogonais dentro de `1e-4` e consistentes entre as
instâncias.

Qualquer vetor não finito, degenerado, não unitário, não ortogonal ou
inconsistente aciona integralmente o fallback determinístico por
`InstanceNumber` e `SOPInstanceUID`. Metadata inválida não é silenciosamente
consertada pela normalização.

## Streaming WADO e estabilidade do CI

O teste de falha depois do primeiro chunk deve verificar o contrato observável
sem depender de um tamanho presumido do buffer HTTP do runner. A correção deve
preservar estes invariantes:

- antes do commit da resposta, falha conhecida pode virar Problem Details;
- depois do commit, o stream pode terminar, mas nenhum Problem Details é anexado
  aos bytes DICOM já iniciados;
- o proxy continua por streaming e não passa a bufferizar o frame completo;
- nenhum teste pode esconder uma regressão aceitando indiscriminadamente tanto
  sucesso quanto falha.

A causa da instabilidade deve ser reproduzida por teste antes da alteração. A
solução pode ajustar o limite/forma da fixture ou a fronteira de streaming, desde
que prove os invariantes acima e não introduza buffering do payload completo.

## Concorrência STOW/QIDO

As mudanças locais em `synthetic-dicom.ts` e `worklist.spec.ts` fazem parte deste
fechamento. O E2E deve provar, por eventos HTTP observáveis, que:

- um POST STOW está em andamento;
- pelo menos um estudo do lote já está consultável por QIDO;
- o último estudo sequencial ainda não está consultável nesse instante;
- o QIDO conclui antes da resposta do POST;
- o lote termina com todos os resultados esperados;
- o browser nunca chama o DCM4CHEE diretamente.

UIDs `2.25.<inteiro-UUID>` são permitidos exclusivamente para novos objetos
sintéticos criados pelo teste. O tamanho declarado da imagem e o Pixel Data da
fixture precisam permanecer coerentes.

## Catálogo de problemas

Nenhum tipo novo é criado. Resposta DICOMweb, identidade, hierarquia, metadata,
contagem ou truncamento fora do contrato reutiliza:

| Campo | Valor publicado |
| :-- | :-- |
| code | `API_ARCHIVE_RESPONSE_INVALID` |
| type | `urn:uuid:8a220e49-3e80-5e59-83e5-43483c4a6dd8` |
| HTTP | `502` |
| retry | `MANUAL` |

O texto público permanece vindo do catálogo. Exceções internas não incorporam
valores clínicos nas mensagens.

## Estratégia de implementação e commits

- Toda mudança comportamental segue RED → GREEN → REFACTOR.
- Cada tarefa é implementada por um subagente e revisada antes da seguinte.
- Commits são pequenos, focados e seguem `docs/domains/git/` e Gitmoji.
- As duas mudanças preexistentes do E2E são preservadas e recebem commit próprio
  somente depois de validadas.
- Não há push, merge ou publicação automática.
- `graphify-out/` é atualizado uma única vez, depois que implementação, testes,
  revisões e gates estiverem estáveis, em commit próprio.

## Gates e critérios de aceite

1. Testes novos falham pelo motivo esperado antes de cada correção.
2. UIDs QIDO são preservados e comparados exatamente, com VM 1.
3. O estudo retornado pelo Archive precisa ser o solicitado.
4. WADO metadata valida VR/VM e não inventa rescale.
5. CT sem o par de rescale é recusado; outros SOP Classes podem omitir o par.
6. Resultado STOW contraditório preserva a maior severidade.
7. Classificação de série só ocorre com paginação e contagens completas.
8. Orientação não unitária aciona fallback determinístico.
9. O teste de streaming WADO fica determinístico e preserva streaming real.
10. O E2E concorrente comprova QIDO durante o POST STOW.
11. `pnpm test` e `pnpm check` do catálogo passam sem diff gerado.
12. A suíte backend completa passa.
13. Testes e build do frontend passam.
14. A composição local valida e o Playwright completo passa contra a stack.
15. Nenhum token, UID, PatientID, payload ou URL interna vaza em erro ou log.
16. O revisor DICOM aprova o diff final.
17. O humano aprova o gate final antes de qualquer merge ou push.
