# DICOM: Semantics and Invariants

These are **clinical correctness rules**. Violating them corrupts patient data or breaks system interoperability. Reviewers must treat any violation as a blocking defect.

## Data Model (Hierarchy)

```
Patient            (PatientID [+ IssuerOfPatientID], PatientName …)
  └─ Study         (StudyInstanceUID, StudyDate, AccessionNumber …)
       └─ Series   (SeriesInstanceUID, Modality, SeriesNumber …)
            └─ Instance / SOP (SOPInstanceUID, SOPClassUID, pixel data …)
```

- **A Study** groups all series and instances of a single clinical examination; its primary stable key is `StudyInstanceUID`.
- **A Series** contains **exactly one `Modality`** (CT, MR, US, CR, DX, etc.) and a single coherent spatial acquisition. Never mix modalities within a series.
- **An Instance** is an individual SOP object (typically an image frame, but can be a Structured Report (SR), Encapsulated PDF, Key Object (KO), Presentation State (PR), etc.). `SOPClassUID` defines the type; `SOPInstanceUID` defines the identity.

## UIDs: The Most Critical Invariant

- `StudyInstanceUID`, `SeriesInstanceUID`, and `SOPInstanceUID` represent **immutable identity**. They originate from the modality during acquisition or from the PACS Archive.
- **Never fabricate or invent a UID** in application code to represent existing objects. Searching or loading a study requires using the UID returned by the archive (via QIDO-RS).
- When **creating a new object**, generate the UID with a **valid DICOM UID generator** anchored on a **registered organization root** (`<org root>.<suffix>`), maximum 64 characters, containing only digits and dots, with no leading zeros in individual components. Never use raw UUIDs, random strings, or timestamps as UIDs.
- UIDs are **case-sensitive** and compared as exact strings. Do not normalize casing.

## Patient Identity

- `PatientID` is **not globally unique** on its own: it is only unique within the domain of an `IssuerOfPatientID`. Never assume global uniqueness or use `PatientID` as a primary key without its issuer.
- `PatientName` (VR `PN`) is structured using `^` delimiters:
  `LastName^FirstName^MiddleName^Prefix^Suffix`. Do not treat it as unstructured plain text during matching.

## Tags, VRs, and Formats

- Tags are structured as `(gggg,eeee)` (group, element). Each tag possesses a **VR** (Value Representation) defining its format. Respect the VR during read/write operations.
- Dates & Times: `DA` = `YYYYMMDD`; `TM` = `HHMMSS.FFFFFF`; `DT` = combined timestamp. Do not blindly convert to ISO-8601 without preserving DICOM semantics.
- `IS`/`DS` are numbers **stored as strings**; `US`/`UL`/`SS`/`SL` are binary numbers.
- **Character Encoding:** `SpecificCharacterSet` (0008,0005) defines the encoding of textual attributes. Never assume UTF-8 or Latin-1 without checking.

## Pixel Data and Transfer Syntax

- The **Transfer Syntax** specifies the pixel data encoding and compression (Implicit/Explicit VR, Little/Big Endian, JPEG Lossless, JPEG 2000, RLE, etc.).
- **Do not re-encode or transcode** pixel data without explicit architectural necessity and complete understanding of source and destination transfer syntaxes. Lossy transcoding introduces irreversible loss of diagnostic information.
- Viewport presentation rendering (*Window/Level*, LUT transformations) is client-side presentation: it **never** mutates stored pixel values.

## Review Checklist (What Reviewers Look For)

- [ ] Is any UID being invented or randomly generated where it should originate from the Archive?
- [ ] Is `PatientID` used as a primary key without `IssuerOfPatientID`?
- [ ] Does a series assume multiple modalities or use hardcoded wrong modalities?
- [ ] Are DICOM dates and names parsed with invalid format assumptions (ISO strings instead of DA/PN)?
- [ ] Is pixel data being transcoded or re-encoded without justification?
- [ ] Is the Patient -> Study -> Series -> Instance hierarchy broken in models or queries?
