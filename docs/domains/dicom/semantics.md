# DICOM — semântica e invariantes

Regras de **correção de negócio**. Violá-las corrompe dados de paciente ou quebra
interoperabilidade. Um revisor deve tratar qualquer violação como bloqueante.

## Modelo de dados (hierarquia)

```
Patient            (PatientID [+ IssuerOfPatientID], PatientName …)
  └─ Study         (StudyInstanceUID, StudyDate, AccessionNumber …)
       └─ Series   (SeriesInstanceUID, Modality, SeriesNumber …)
            └─ Instance / SOP (SOPInstanceUID, SOPClassUID, pixel data …)
```

- **Um estudo** agrupa tudo de um exame; sua chave estável é `StudyInstanceUID`.
- **Uma série** tem **uma única `Modality`** (CT, MR, US, CR, DX, …) e uma
  orientação/aquisição coerente. Não misture modalidades numa série.
- **Uma instância** é um objeto SOP (tipicamente uma imagem, mas pode ser SR, PDF
  encapsulado, KO, PR …). `SOPClassUID` diz o tipo; `SOPInstanceUID` a identidade.

## UIDs — a regra mais importante

- `StudyInstanceUID`, `SeriesInstanceUID`, `SOPInstanceUID` são **identidade
  imutável**. Vêm da modalidade na aquisição ou do archive.
- **Nunca fabrique um UID** em código de aplicação para representar algo que já
  existe. Buscar um estudo = usar o UID que o archive retornou (via QIDO).
- Ao **criar um objeto novo** (raro no MVP), gere o UID com um **gerador DICOM
  válido**, ancorado numa **raiz de organização registrada** (`<org root>.<sufixo>`),
  máx. 64 chars, apenas dígitos e pontos, sem zero à esquerda em cada componente.
  Nunca use UUID cru, timestamp solto ou string aleatória como UID.
- UID é **case-sensitive** e comparado como string exata. Não normalize.

## Identidade de paciente

- `PatientID` **não é globalmente único** por si só — só é único dentro do domínio
  de um `IssuerOfPatientID`. Não assuma unicidade global nem use `PatientID` como
  PK sem o issuer.
- `PatientName` (VR `PN`) é estruturado com `^`:
  `Sobrenome^Nome^NomeDoMeio^Prefixo^Sufixo`. Não trate como texto livre ao
  fazer matching.

## Tags, VRs e formatos

- Tags são `(gggg,eeee)` (grupo, elemento). Cada tag tem um **VR** (Value
  Representation) que define o formato. Respeite o VR ao ler/escrever.
- Datas/horas: `DA` = `YYYYMMDD`; `TM` = `HHMMSS.FFFFFF`; `DT` = combinação.
  Não converta cegamente para ISO sem preservar a semântica.
- `IS`/`DS` são números **em string**; `US`/`UL`/`SS`/`SL` são binários.
- **Character set:** `SpecificCharacterSet` (0008,0005) define a codificação de
  campos de texto. Não assuma UTF-8/Latin-1.

## Pixel data e Transfer Syntax

- O **Transfer Syntax** define encoding + compressão dos pixels (Implicit/Explicit
  VR, Little/Big Endian, JPEG Lossless, JPEG 2000, RLE …).
- **Não re-encode nem transcodifique** pixel data sem necessidade e sem entender o
  Transfer Syntax de origem e destino. Transcodificação lossy é perda irreversível
  de informação diagnóstica.
- Renderização para tela (window/level, LUT) é apresentação — **não** altera os
  pixels armazenados.

## Checklist de revisão (o que o revisor procura)

- [ ] Algum UID sendo gerado/inventado onde deveria vir do archive?
- [ ] `PatientID` usado como identificador único sem issuer?
- [ ] Série assumindo múltiplas modalidades, ou modalidade hardcoded errada?
- [ ] Datas/nomes DICOM parseados com formato errado (ISO em vez de DA/PN)?
- [ ] Transcodificação/re-encode de pixels sem justificativa?
- [ ] Hierarquia paciente→estudo→série→instância quebrada no modelo/queries?
