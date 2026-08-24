package dev.blackice.ingest.infrastructure.dicom;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.validation.DicomBatchValidation;
import dev.blackice.ingest.application.validation.DicomValidationIssue;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class Dcm4cheDicomBatchValidatorTest {

    @TempDir
    Path temp;

    private Dcm4cheDicomBatchValidator validator;

    @BeforeEach
    void setUp() {
        validator = new Dcm4cheDicomBatchValidator();
    }

    private Path dicom(String study, String series, String sop, byte pixel) throws Exception {
        return dicom(study, series, sop, UID.SecondaryCaptureImageStorage, pixel);
    }

    private Path dicom(String study, String series, String sop, String sopClass, byte pixel) throws Exception {
        Attributes ds = new Attributes();
        if (sopClass != null) {
            setRawUid(ds, Tag.SOPClassUID, sopClass);
        }
        if (sop != null) {
            setRawUid(ds, Tag.SOPInstanceUID, sop);
        }
        if (study != null) {
            setRawUid(ds, Tag.StudyInstanceUID, study);
        }
        if (series != null) {
            setRawUid(ds, Tag.SeriesInstanceUID, series);
        }
        ds.setString(Tag.Modality, VR.CS, "OT");
        ds.setInt(Tag.Rows, VR.US, 1);
        ds.setInt(Tag.Columns, VR.US, 1);
        ds.setInt(Tag.SamplesPerPixel, VR.US, 1);
        ds.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        ds.setInt(Tag.BitsAllocated, VR.US, 8);
        ds.setInt(Tag.BitsStored, VR.US, 8);
        ds.setInt(Tag.HighBit, VR.US, 7);
        ds.setInt(Tag.PixelRepresentation, VR.US, 0);
        ds.setBytes(Tag.PixelData, VR.OB, new byte[] {pixel, 0});

        String name = (sop != null ? sop : "nosop_" + System.nanoTime()) + "_" + pixel + ".dcm";
        Path path = temp.resolve(name);
        try (DicomOutputStream out = new DicomOutputStream(path.toFile())) {
            out.writeDataset(null, ds);
        }
        return path;
    }

    private static void setRawUid(Attributes dataset, int tag, String uid) {
        byte[] encoded = uid.getBytes(StandardCharsets.US_ASCII);
        if ((encoded.length & 1) != 0) {
            encoded = Arrays.copyOf(encoded, encoded.length + 1);
            encoded[encoded.length - 1] = (byte) VR.UI.paddingByte();
        }
        dataset.setBytes(tag, VR.UI, encoded);
    }

    @Test
    void two_valid_studies_become_two_ordered_groups() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.2", (byte) 2);
        Path p3 = dicom("1.2.4", "1.2.4.1", "1.2.4.1.1", (byte) 3);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "f1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "f2.dcm", Files.size(p2)),
            new UploadedDicom(p3, "f3.dcm", Files.size(p3))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.issues().isEmpty());
        assertEquals(2, validation.validStudies().size());
        assertEquals(List.of("1.2.3", "1.2.4"), new ArrayList<>(validation.validStudies().keySet()));

        List<ValidatedDicom> study1 = validation.validStudies().get("1.2.3");
        assertEquals(2, study1.size());
        assertEquals("1.2.3.1.1", study1.get(0).sopInstanceUid());
        assertEquals("1.2.3.1.2", study1.get(1).sopInstanceUid());

        List<ValidatedDicom> study2 = validation.validStudies().get("1.2.4");
        assertEquals(1, study2.size());
        assertEquals("1.2.4.1.1", study2.get(0).sopInstanceUid());
    }

    @Test
    void corrupt_bytes_produce_malformed_dicom_issue() throws Exception {
        Path path = temp.resolve("corrupt.dcm");
        Files.write(path, new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc});

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(path, "corrupt.dcm", Files.size(path))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.validStudies().isEmpty());
        assertEquals(1, validation.issues().size());
        DicomValidationIssue issue = validation.issues().getFirst();
        assertEquals(0, issue.itemIndex());
        assertEquals("corrupt.dcm", issue.filename());
        assertEquals(DicomValidationIssue.Code.MALFORMED_DICOM, issue.code());
    }

    @Test
    void unexpected_runtime_failure_propagates_instead_of_becoming_malformed_dicom() {
        UploadedDicom invalidInternalInput = new UploadedDicom(null, "safe-test-name.dcm", 0);

        assertThrows(NullPointerException.class, () -> validator.validate(List.of(invalidInternalInput)));
    }

    @Test
    void missing_required_uids_produce_stable_code() throws Exception {
        Path noStudy = dicom(null, "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path noSeries = dicom("1.2.3", null, "1.2.3.1.2", (byte) 2);
        Path noSop = dicom("1.2.3", "1.2.3.1", null, (byte) 3);
        Path noSopClass = dicom("1.2.3", "1.2.3.1", "1.2.3.1.4", null, (byte) 4);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(noStudy, "noStudy.dcm", Files.size(noStudy)),
            new UploadedDicom(noSeries, "noSeries.dcm", Files.size(noSeries)),
            new UploadedDicom(noSop, "noSop.dcm", Files.size(noSop)),
            new UploadedDicom(noSopClass, "noSopClass.dcm", Files.size(noSopClass))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.validStudies().isEmpty());
        assertEquals(4, validation.issues().size());

        assertEquals(0, validation.issues().get(0).itemIndex());
        assertEquals("noStudy.dcm", validation.issues().get(0).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_STUDY_INSTANCE_UID, validation.issues().get(0).code());
        assertEquals(
            "Required DICOM attribute Study Instance UID is missing.",
            validation.issues().get(0).message()
        );

        assertEquals(1, validation.issues().get(1).itemIndex());
        assertEquals("noSeries.dcm", validation.issues().get(1).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SERIES_INSTANCE_UID, validation.issues().get(1).code());

        assertEquals(2, validation.issues().get(2).itemIndex());
        assertEquals("noSop.dcm", validation.issues().get(2).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SOP_INSTANCE_UID, validation.issues().get(2).code());

        assertEquals(3, validation.issues().get(3).itemIndex());
        assertEquals("noSopClass.dcm", validation.issues().get(3).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SOP_CLASS_UID, validation.issues().get(3).code());
    }

    @ParameterizedTest(name = "rejects invalid {0}")
    @MethodSource("invalidRequiredUids")
    void invalid_required_uids_produce_malformed_dicom_issue(
        String attribute,
        String study,
        String series,
        String sop,
        String sopClass
    ) throws Exception {
        Path path = dicom(study, series, sop, sopClass, (byte) 7);

        DicomBatchValidation validation = validator.validate(List.of(
            new UploadedDicom(path, "invalid.dcm", Files.size(path))
        ));

        assertTrue(validation.validStudies().isEmpty(), attribute);
        assertEquals(1, validation.issues().size(), attribute);
        assertEquals(DicomValidationIssue.Code.MALFORMED_DICOM, validation.issues().getFirst().code(), attribute);
    }

    private static Stream<Arguments> invalidRequiredUids() {
        String overlength = "1." + "2".repeat(63);
        return Stream.of(
            Arguments.of("StudyInstanceUID with leading whitespace",
                " 1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage),
            Arguments.of("StudyInstanceUID with a leading-zero component",
                "1.02.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage),
            Arguments.of("SeriesInstanceUID with trailing whitespace",
                "1.2.3", "1.2.3.1 ", "1.2.3.1.1", UID.SecondaryCaptureImageStorage),
            Arguments.of("SOPInstanceUID longer than 64 characters",
                "1.2.3", "1.2.3.1", overlength, UID.SecondaryCaptureImageStorage),
            Arguments.of("SOPClassUID with a leading-zero component",
                "1.2.3", "1.2.3.1", "1.2.3.1.1", "1.02.840.10008.5.1.4.1.1.7")
        );
    }

    @Test
    void canonical_ui_null_padding_is_accepted_without_changing_uid_identity() throws Exception {
        String oddLengthStudyUid = "1.2.3";
        Path path = dicom(
            oddLengthStudyUid,
            "1.2.3.1",
            "1.2.3.1.1",
            UID.SecondaryCaptureImageStorage,
            (byte) 8
        );

        DicomBatchValidation validation = validator.validate(List.of(
            new UploadedDicom(path, "canonical-padding.dcm", Files.size(path))
        ));

        assertTrue(validation.issues().isEmpty());
        assertEquals(oddLengthStudyUid, validation.validStudies().keySet().iterator().next());
        assertEquals(
            oddLengthStudyUid,
            validation.validStudies().get(oddLengthStudyUid).getFirst().studyInstanceUid()
        );
    }

    @Test
    void identical_bytes_with_same_sop_uid_keep_first_and_mark_duplicates() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = temp.resolve("duplicate.dcm");
        Files.copy(p1, p2);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "original.dcm", Files.size(p1)),
            new UploadedDicom(p2, "duplicate.dcm", Files.size(p2))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertEquals(1, validation.validStudies().size());
        List<ValidatedDicom> study = validation.validStudies().get("1.2.3");
        assertEquals(1, study.size());
        assertEquals("original.dcm", study.getFirst().filename());
        assertEquals(p1, study.getFirst().path());

        assertEquals(1, validation.issues().size());
        DicomValidationIssue issue = validation.issues().getFirst();
        assertEquals(1, issue.itemIndex());
        assertEquals("duplicate.dcm", issue.filename());
        assertEquals(DicomValidationIssue.Code.DUPLICATE_IDENTICAL, issue.code());
    }

    @Test
    void different_bytes_with_same_sop_uid_reject_all_as_sop_uid_collision() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 2);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "sop_v1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "sop_v2.dcm", Files.size(p2))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.validStudies().isEmpty());
        assertEquals(2, validation.issues().size());

        assertEquals(0, validation.issues().get(0).itemIndex());
        assertEquals("sop_v1.dcm", validation.issues().get(0).filename());
        assertEquals(DicomValidationIssue.Code.SOP_UID_COLLISION, validation.issues().get(0).code());

        assertEquals(1, validation.issues().get(1).itemIndex());
        assertEquals("sop_v2.dcm", validation.issues().get(1).filename());
        assertEquals(DicomValidationIssue.Code.SOP_UID_COLLISION, validation.issues().get(1).code());
    }

    @Test
    void issue_messages_never_carry_a_dicom_uid() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = temp.resolve("collision.dcm");
        Files.write(p2, changeLastByte(Files.readAllBytes(p1)));
        Path duplicate = temp.resolve("dup.dcm");
        Files.copy(p1, duplicate);
        Path corrupt = temp.resolve("corrupt.dcm");
        Files.write(corrupt, new byte[] {0x12, 0x34, 0x56});

        DicomBatchValidation validation = validator.validate(List.of(
            new UploadedDicom(p1, "a.dcm", Files.size(p1)),
            new UploadedDicom(p2, "b.dcm", Files.size(p2)),
            new UploadedDicom(duplicate, "c.dcm", Files.size(duplicate)),
            new UploadedDicom(corrupt, "d.dcm", Files.size(corrupt))
        ));

        assertFalse(validation.issues().isEmpty());
        for (DicomValidationIssue issue : validation.issues()) {
            assertFalse(issue.message().contains("1.2.3"),
                "Public message contains a UID: " + issue.message());
            assertFalse(issue.message().toLowerCase().contains("uid:"),
                "Public message contains a UID: " + issue.message());
        }
    }

    private static byte[] changeLastByte(byte[] bytes) {
        byte[] copy = bytes.clone();
        copy[copy.length - 1] = (byte) (copy[copy.length - 1] ^ 0xFF);
        return copy;
    }

    @Test
    void valid_objects_preserve_exact_uid_strings_and_paths() throws Exception {
        String exactStudy = "1.2.840.10008.1.99.1001";
        String exactSeries = "1.2.840.10008.1.99.1002";
        String exactSop = "1.2.840.10008.1.99.1003";
        String exactSopClass = UID.SecondaryCaptureImageStorage;

        Path p = dicom(exactStudy, exactSeries, exactSop, exactSopClass, (byte) 99);
        long size = Files.size(p);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p, "exact.dcm", size)
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.issues().isEmpty());
        assertEquals(1, validation.validStudies().size());
        assertTrue(validation.validStudies().containsKey(exactStudy));

        ValidatedDicom valid = validation.validStudies().get(exactStudy).getFirst();
        assertEquals(p, valid.path());
        assertEquals("exact.dcm", valid.filename());
        assertEquals(size, valid.size());
        assertEquals(exactStudy, valid.studyInstanceUid());
        assertEquals(exactSeries, valid.seriesInstanceUid());
        assertEquals(exactSop, valid.sopInstanceUid());
        assertEquals(exactSopClass, valid.sopClassUid());
        assertNotNull(valid.sha256());
        assertEquals(64, valid.sha256().length());
    }
}
