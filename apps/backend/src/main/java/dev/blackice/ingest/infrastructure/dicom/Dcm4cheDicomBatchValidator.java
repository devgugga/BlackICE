package dev.blackice.ingest.infrastructure.dicom;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.validation.DicomBatchValidation;
import dev.blackice.ingest.application.validation.DicomValidationIssue;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import dev.blackice.ingest.application.port.DicomBatchValidator;
import jakarta.enterprise.context.ApplicationScoped;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Value;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.UIDUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DCM4CHE-backed implementation that reads required metadata without loading pixel data. */
@ApplicationScoped
public class Dcm4cheDicomBatchValidator implements DicomBatchValidator {

    @Override
    public DicomBatchValidation validate(List<UploadedDicom> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            return new DicomBatchValidation(new LinkedHashMap<>(), new ArrayList<>());
        }

        List<DicomValidationIssue> issues = new ArrayList<>();
        Map<String, List<ValidatedDicom>> candidatesBySopUid = new LinkedHashMap<>();

        for (int itemIndex = 0; itemIndex < uploads.size(); itemIndex++) {
            UploadedDicom upload = uploads.get(itemIndex);
            try (DicomInputStream in = new DicomInputStream(upload.path().toFile())) {
                in.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
                Attributes ds = in.readDataset();
                if (ds == null) {
                    issues.add(DicomValidationIssue.of(
                        itemIndex, upload.filename(), DicomValidationIssue.Code.MALFORMED_DICOM));
                    continue;
                }

                String study = requireUid(ds, Tag.StudyInstanceUID, DicomValidationIssue.Code.MISSING_STUDY_INSTANCE_UID);
                String series = requireUid(ds, Tag.SeriesInstanceUID, DicomValidationIssue.Code.MISSING_SERIES_INSTANCE_UID);
                String sop = requireUid(ds, Tag.SOPInstanceUID, DicomValidationIssue.Code.MISSING_SOP_INSTANCE_UID);
                String sopClass = requireUid(ds, Tag.SOPClassUID, DicomValidationIssue.Code.MISSING_SOP_CLASS_UID);

                String sha256 = calculateSha256(upload.path());
                ValidatedDicom validated = new ValidatedDicom(
                    itemIndex,
                    upload.path(),
                    upload.filename(),
                    upload.size(),
                    study,
                    series,
                    sop,
                    sopClass,
                    sha256
                );

                candidatesBySopUid.computeIfAbsent(sop, k -> new ArrayList<>()).add(validated);
            } catch (ValidationException e) {
                issues.add(DicomValidationIssue.of(itemIndex, upload.filename(), e.code));
            } catch (IOException e) {
                // File and DICOM parsing failures are expected validation outcomes.
                // The exception text is intentionally discarded because it may contain a path.
                issues.add(DicomValidationIssue.of(
                    itemIndex, upload.filename(), DicomValidationIssue.Code.MALFORMED_DICOM));
            }
        }

        List<ValidatedDicom> retained = new ArrayList<>();

        for (Map.Entry<String, List<ValidatedDicom>> entry : candidatesBySopUid.entrySet()) {
            List<ValidatedDicom> group = entry.getValue();
            if (group.size() == 1) {
                retained.add(group.getFirst());
            } else {
                String firstHash = group.getFirst().sha256();
                boolean allIdentical = true;
                for (int i = 1; i < group.size(); i++) {
                    if (!group.get(i).sha256().equals(firstHash)) {
                        allIdentical = false;
                        break;
                    }
                }

                if (allIdentical) {
                    retained.add(group.getFirst());
                    for (int i = 1; i < group.size(); i++) {
                        ValidatedDicom dup = group.get(i);
                        issues.add(DicomValidationIssue.of(
                            dup.itemIndex(), dup.filename(), DicomValidationIssue.Code.DUPLICATE_IDENTICAL));
                    }
                } else {
                    for (ValidatedDicom collision : group) {
                        issues.add(DicomValidationIssue.of(
                            collision.itemIndex(), collision.filename(),
                            DicomValidationIssue.Code.SOP_UID_COLLISION));
                    }
                }
            }
        }

        Map<String, List<ValidatedDicom>> validStudies = new LinkedHashMap<>();
        for (ValidatedDicom valid : retained) {
            validStudies.computeIfAbsent(valid.studyInstanceUid(), k -> new ArrayList<>()).add(valid);
        }

        // Ordenação estável pela posição recebida: o consumidor associa cada
        // violação ao arquivo que ele mesmo selecionou.
        issues.sort(java.util.Comparator.comparingInt(DicomValidationIssue::itemIndex));
        return new DicomBatchValidation(validStudies, issues);
    }

    private String requireUid(Attributes ds, int tag, DicomValidationIssue.Code code) {
        Object rawValue = ds.getValue(tag);
        if (rawValue == null || rawValue == Value.NULL) {
            throw new ValidationException(code, "Required UID is missing: " + code);
        }
        if (ds.getVR(tag) != VR.UI || !(rawValue instanceof byte[] encodedUid)
            || (encodedUid.length & 1) != 0) {
            throw new ValidationException(
                DicomValidationIssue.Code.MALFORMED_DICOM,
                "Required UID has an invalid DICOM UI value"
            );
        }

        int uidLength = encodedUid.length;
        if (uidLength > 0 && encodedUid[uidLength - 1] == (byte) VR.UI.paddingByte()) {
            uidLength--;
        }
        String exactUid = new String(encodedUid, 0, uidLength, StandardCharsets.US_ASCII);
        if (!UIDUtils.isValid(exactUid)) {
            throw new ValidationException(
                DicomValidationIssue.Code.MALFORMED_DICOM,
                "Required UID has an invalid DICOM UI value"
            );
        }
        return exactUid;
    }

    private static String calculateSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static class ValidationException extends RuntimeException {
        private final DicomValidationIssue.Code code;

        ValidationException(DicomValidationIssue.Code code, String message) {
            super(message);
            this.code = code;
        }
    }
}
