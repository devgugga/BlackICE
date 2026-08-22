package dev.blackice.features.ingest;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DicomBatchValidatorTest {

    @TempDir
    Path temp;

    private DicomBatchValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DicomBatchValidator();
    }

    private Path dicom(String study, String series, String sop, byte pixel) throws Exception {
        return dicom(study, series, sop, UID.SecondaryCaptureImageStorage, pixel);
    }

    private Path dicom(String study, String series, String sop, String sopClass, byte pixel) throws Exception {
        Attributes ds = new Attributes();
        if (sopClass != null) {
            ds.setString(Tag.SOPClassUID, VR.UI, sopClass);
        }
        if (sop != null) {
            ds.setString(Tag.SOPInstanceUID, VR.UI, sop);
        }
        if (study != null) {
            ds.setString(Tag.StudyInstanceUID, VR.UI, study);
        }
        if (series != null) {
            ds.setString(Tag.SeriesInstanceUID, VR.UI, series);
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
            Attributes fmi = null;
            if (sopClass != null && sop != null) {
                fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian);
            }
            out.writeDataset(fmi, ds);
        }
        return path;
    }

    @Test
    void dois_estudos_validos_viram_dois_grupos_ordenados() throws Exception {
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
    void bytes_corrompidos_produzem_MALFORMED_DICOM() throws Exception {
        Path path = temp.resolve("corrupt.dcm");
        Files.write(path, new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc});

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(path, "corrupt.dcm", Files.size(path))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.validStudies().isEmpty());
        assertEquals(1, validation.issues().size());
        DicomValidationIssue issue = validation.issues().getFirst();
        assertEquals("corrupt.dcm", issue.filename());
        assertEquals(DicomValidationIssue.Code.MALFORMED_DICOM, issue.code());
    }

    @Test
    void falta_de_uids_obrigatorios_produz_codigo_estavel() throws Exception {
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

        assertEquals("noStudy.dcm", validation.issues().get(0).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_STUDY_INSTANCE_UID, validation.issues().get(0).code());

        assertEquals("noSeries.dcm", validation.issues().get(1).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SERIES_INSTANCE_UID, validation.issues().get(1).code());

        assertEquals("noSop.dcm", validation.issues().get(2).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SOP_INSTANCE_UID, validation.issues().get(2).code());

        assertEquals("noSopClass.dcm", validation.issues().get(3).filename());
        assertEquals(DicomValidationIssue.Code.MISSING_SOP_CLASS_UID, validation.issues().get(3).code());
    }

    @Test
    void bytes_identicos_mesmo_sop_uid_mantem_primeiro_e_marca_duplicados() throws Exception {
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
        assertEquals("duplicate.dcm", issue.filename());
        assertEquals(DicomValidationIssue.Code.DUPLICATE_IDENTICAL, issue.code());
    }

    @Test
    void bytes_diferentes_mesmo_sop_uid_rejeita_todas_as_ocorrencias_como_SOP_UID_COLLISION() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 2);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "sop_v1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "sop_v2.dcm", Files.size(p2))
        );

        DicomBatchValidation validation = validator.validate(uploads);

        assertTrue(validation.validStudies().isEmpty());
        assertEquals(2, validation.issues().size());

        assertEquals("sop_v1.dcm", validation.issues().get(0).filename());
        assertEquals(DicomValidationIssue.Code.SOP_UID_COLLISION, validation.issues().get(0).code());

        assertEquals("sop_v2.dcm", validation.issues().get(1).filename());
        assertEquals(DicomValidationIssue.Code.SOP_UID_COLLISION, validation.issues().get(1).code());
    }

    @Test
    void objetos_validos_preservam_strings_exatas_de_uids_e_paths() throws Exception {
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
