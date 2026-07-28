---
type: "query"
date: "2026-07-27T18:42:43.310522+00:00"
question: "Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?"
contributor: "graphify"
outcome: "useful"
source_nodes: ["DICOM UID Identity", "Patient Identity with Issuer", "DICOMweb Verbs", "QIDO-RS", "WADO-RS", "STOW-RS"]
---

# Q: Quais regras do BlackICE governam StudyInstanceUID e os verbos DICOMweb?

## Answer

StudyInstanceUID é identidade estável e imutável vinda da aquisição/archive: reutilize exatamente o UID retornado por QIDO-RS e nunca o invente. Gere UID novo apenas ao criar um objeto DICOM realmente novo, usando gerador DICOM válido com raiz registrada. QIDO-RS busca metadados, sem pixels; WADO-RS recupera objetos, dados e pixels; STOW-RS armazena e sua resposta deve ser inspecionada, inclusive FailedSOPSequence. PatientID não é globalmente único sem o issuer.

## Outcome

- Signal: useful

## Source Nodes

- DICOM UID Identity
- Patient Identity with Issuer
- DICOMweb Verbs
- QIDO-RS
- WADO-RS
- STOW-RS