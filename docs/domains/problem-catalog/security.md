# Segurança de textos, extensões e logs

O BlackICE trata dados de paciente. Uma mensagem de erro é uma superfície de
vazamento como qualquer outra, com o agravante de circular por telas, prints,
tickets e logs de terceiros.

## Nunca aparecem em resposta, extensão ou log

- corpo da requisição;
- query clínica ou seus parâmetros;
- token, cookie, cabeçalho de autorização ou segredo;
- nome de arquivo enviado;
- `StudyInstanceUID`, `SeriesInstanceUID`, `SOPInstanceUID`, `PatientID` ou
  qualquer identificador clínico;
- payload DICOMweb, íntegro ou em recorte;
- URL interna, host ou porta de serviço de infraestrutura;
- `Exception.getMessage()`, causa, stack trace ou nome de classe.

A regra vale inclusive para exceções inesperadas: a aplicação não incorpora dados
de paciente em mensagens ou causas que ela mesma cria.

## Texto público

`title` e `detail` vêm do catálogo, são escritos uma vez em inglês e são
estáveis. Eles descrevem a **classe** do problema, não a ocorrência — por isso
podem ser escritos com antecedência e revisados por um humano.

Um texto público seguro não muda conforme a entrada do usuário. Se você precisa
interpolar algo para a frase fazer sentido, ou o dado pertence a uma extensão
tipada, ou o tipo está errado.

O frontend não renderiza `detail` diretamente: as mensagens ao usuário são PT-BR
e vivem em um mapa central indexado por `ProblemCode`.

## Extensões

Uma extensão só existe quando o consumidor precisa **agir** sobre o detalhe. Ela
é tipada por schema, e cada campo passa pelo mesmo crivo da lista acima.

O caso publicado ilustra a regra: `dicom-validation-violations` carrega
`itemIndex`, `code` e `message`, e **não** carrega `filename`, porque nomes de
arquivo frequentemente contêm nome de paciente, data ou identificador. O
consumidor associa `itemIndex` aos arquivos que já mantém localmente — o dado
nunca precisou atravessar a fronteira.

Prefira sempre um índice, um enum ou uma contagem a um texto livre vindo do
domínio.

## TraceID

O TraceID é a ponte segura entre o que o usuário vê e o que o operador
investiga. Ele identifica uma execução, não uma pessoa.

- toda resposta `/api`, inclusive de sucesso, carrega `X-Trace-ID`;
- todo Problem Details carrega o mesmo valor em `traceId`;
- `traceparent` W3C é a única entrada canônica; um `X-Trace-ID` recebido do
  cliente nunca substitui o contexto;
- o frontend mostra o TraceID apenas em falhas, como referência copiável;
- o TraceID não é guardado dentro de exceções de domínio ou aplicação.

Quando alguém precisar do detalhe real, ele está no log do servidor, correlacionado
pelo TraceID. É isso que permite que a resposta ao usuário seja genérica.

## Logging

Registre **uma vez**, na fronteira:

- erro esperado do cliente: `INFO` ou `WARN`, sem stack trace;
- indisponibilidade externa conhecida: `WARN` com razão segura;
- falha inesperada: um único `ERROR` com stack trace.

O log usa code, status, `traceId`, método HTTP e template de rota. Não use a URI
com query — ela carrega a busca clínica.
