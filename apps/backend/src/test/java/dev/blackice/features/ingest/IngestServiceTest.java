package dev.blackice.features.ingest;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IngestServiceTest {

    @TempDir
    Path temp;

    private DicomBatchValidator validator;
    private FakeDicomArchiveGateway gateway;
    private IngestService service;

    @BeforeEach
    void setUp() {
        validator = new DicomBatchValidator();
        gateway = new FakeDicomArchiveGateway();
        service = new IngestService(validator, gateway, 1);
    }

    private Path dicom(String study, String series, String sop, byte pixel) throws Exception {
        Attributes ds = new Attributes();
        ds.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
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

        String name = (sop != null ? sop : "nosop_" + System.nanoTime()) + ".dcm";
        Path path = temp.resolve(name);
        try (DicomOutputStream out = new DicomOutputStream(path.toFile())) {
            Attributes fmi = (sop != null && study != null)
                ? ds.createFileMetaInformation(UID.ExplicitVRLittleEndian)
                : null;
            out.writeDataset(fmi, ds);
        }
        return path;
    }

    private List<UploadedDicom> twoStudyUploads() throws Exception {
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.4", "1.2.4.1", "1.2.4.1.1", (byte) 2);
        return List.of(
            new UploadedDicom(p1, "study1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "study2.dcm", Files.size(p2))
        );
    }

    @Test
    void zero_arquivos_validos_retorna_422_e_nunca_chama_gateway() throws Exception {
        Path corrupt = temp.resolve("corrupt.dcm");
        Files.write(corrupt, new byte[] {1, 2, 3, 4});

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(corrupt, "corrupt.dcm", Files.size(corrupt))
        );

        IngestExecution execution = service.ingest(uploads, "user-token");

        assertTrue(gateway.calledStudyUids.isEmpty());
        assertEquals(422, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.FAILED, execution.response().outcome());

        IngestResponse.Summary summary = execution.response().summary();
        assertEquals(1, summary.received());
        assertEquals(0, summary.locallyValid());
        assertEquals(1, summary.locallyRejected());
        assertEquals(0, summary.archiveAccepted());
        assertEquals(0, summary.archiveRejected());

        assertTrue(execution.response().studies().isEmpty());
        assertEquals(1, execution.response().locallyRejectedFiles().size());
        assertEquals("corrupt.dcm", execution.response().locallyRejectedFiles().getFirst().filename());
    }

    @Test
    void dois_grupos_de_estudos_ambos_executam_quando_o_primeiro_lanca_ArchiveUnavailableException() throws Exception {
        gateway.fail("1.2.3", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.CONNECTION, new IOException("offline")));

        IngestExecution execution = service.ingest(twoStudyUploads(), "user-token");

        assertEquals(List.of("1.2.3", "1.2.4"), gateway.calledStudyUids);
        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.PARTIAL, execution.response().outcome());

        List<IngestResponse.StudyResult> studies = execution.response().studies();
        assertEquals(2, studies.size());

        IngestResponse.StudyResult study1 = studies.get(0);
        assertEquals("1.2.3", study1.studyInstanceUid());
        assertEquals(IngestResponse.StudyStatus.FAILED, study1.status());
        assertEquals("ARCHIVE_UNAVAILABLE", study1.errorCode());
        assertTrue(study1.instances().isEmpty());

        IngestResponse.StudyResult study2 = studies.get(1);
        assertEquals("1.2.4", study2.studyInstanceUid());
        assertEquals(IngestResponse.StudyStatus.COMPLETE, study2.status());
        assertNull(study2.errorCode());
        assertEquals(1, study2.instances().size());
        assertEquals("1.2.4.1.1", study2.instances().getFirst().sopInstanceUid());
        assertEquals(StowInstanceResult.Status.ACCEPTED, study2.instances().getFirst().status());
    }

    @Test
    void um_grupo_com_sucesso_mais_uma_falha_de_infra_retorna_200_PARTIAL() throws Exception {
        gateway.fail("1.2.4", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.TIMEOUT, new IOException("timeout")));

        IngestExecution execution = service.ingest(twoStudyUploads(), "user-token");

        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.PARTIAL, execution.response().outcome());

        IngestResponse.Summary summary = execution.response().summary();
        assertEquals(2, summary.received());
        assertEquals(2, summary.locallyValid());
        assertEquals(0, summary.locallyRejected());
        assertEquals(1, summary.archiveAccepted());
        assertEquals(1, summary.archiveRejected());
    }

    @Test
    void todos_os_grupos_tentados_indisponiveis_retorna_503_FAILED() throws Exception {
        gateway.fail("1.2.3", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.CONNECTION, new IOException("offline")));
        gateway.fail("1.2.4", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.TIMEOUT, new IOException("timeout")));

        IngestExecution execution = service.ingest(twoStudyUploads(), "user-token");

        assertEquals(503, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.FAILED, execution.response().outcome());

        IngestResponse.Summary summary = execution.response().summary();
        assertEquals(2, summary.received());
        assertEquals(2, summary.locallyValid());
        assertEquals(0, summary.locallyRejected());
        assertEquals(0, summary.archiveAccepted());
        assertEquals(2, summary.archiveRejected());

        for (IngestResponse.StudyResult study : execution.response().studies()) {
            assertEquals(IngestResponse.StudyStatus.FAILED, study.status());
            assertEquals("ARCHIVE_UNAVAILABLE", study.errorCode());
        }
    }

    @Test
    void rejeicoes_locais_mais_rejeicoes_do_archive_produzem_contagens_exatas() throws Exception {
        Path invalidLocal = dicom(null, "1.2.3.1", "1.2.3.1.0", (byte) 0);
        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.2", (byte) 2);
        Path p3 = dicom("1.2.4", "1.2.4.1", "1.2.4.1.1", (byte) 3);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(invalidLocal, "invalid.dcm", Files.size(invalidLocal)),
            new UploadedDicom(p1, "s1_sop1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "s1_sop2.dcm", Files.size(p2)),
            new UploadedDicom(p3, "s2_sop1.dcm", Files.size(p3))
        );

        gateway.setCustomResult("1.2.3", new StowStudyResult("1.2.3", List.of(
            new StowInstanceResult("1.2.3.1.1", StowInstanceResult.Status.ACCEPTED, null),
            new StowInstanceResult("1.2.3.1.2", StowInstanceResult.Status.REJECTED, 272)
        )));

        gateway.setCustomResult("1.2.4", new StowStudyResult("1.2.4", List.of(
            new StowInstanceResult("1.2.4.1.1", StowInstanceResult.Status.WARNING, 1)
        )));

        IngestExecution execution = service.ingest(uploads, "user-token");

        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.PARTIAL, execution.response().outcome());

        IngestResponse.Summary summary = execution.response().summary();
        assertEquals(4, summary.received());
        assertEquals(3, summary.locallyValid());
        assertEquals(1, summary.locallyRejected());
        assertEquals(2, summary.archiveAccepted()); // ACCEPTED + WARNING
        assertEquals(1, summary.archiveRejected()); // REJECTED

        assertEquals(1, execution.response().locallyRejectedFiles().size());
        assertEquals("invalid.dcm", execution.response().locallyRejectedFiles().getFirst().filename());

        List<IngestResponse.StudyResult> studies = execution.response().studies();
        assertEquals(2, studies.size());

        IngestResponse.StudyResult s1 = studies.get(0);
        assertEquals("1.2.3", s1.studyInstanceUid());
        assertEquals(IngestResponse.StudyStatus.PARTIAL, s1.status());
        assertNull(s1.errorCode());
        assertEquals(2, s1.instances().size());
        assertEquals(StowInstanceResult.Status.ACCEPTED, s1.instances().get(0).status());
        assertEquals(StowInstanceResult.Status.REJECTED, s1.instances().get(1).status());
        assertEquals(272, s1.instances().get(1).reason());

        IngestResponse.StudyResult s2 = studies.get(1);
        assertEquals("1.2.4", s2.studyInstanceUid());
        assertEquals(IngestResponse.StudyStatus.COMPLETE, s2.status());
        assertNull(s2.errorCode());
        assertEquals(1, s2.instances().size());
        assertEquals(StowInstanceResult.Status.WARNING, s2.instances().getFirst().status());
    }

    @Test
    void limite_de_concorrencia_e_respeitado() throws Exception {
        gateway.sleepMs = 50;
        service = new IngestService(validator, gateway, 1);

        Path p1 = dicom("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        Path p2 = dicom("1.2.4", "1.2.4.1", "1.2.4.1.1", (byte) 2);
        Path p3 = dicom("1.2.5", "1.2.5.1", "1.2.5.1.1", (byte) 3);
        Path p4 = dicom("1.2.6", "1.2.6.1", "1.2.6.1.1", (byte) 4);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "s1.dcm", Files.size(p1)),
            new UploadedDicom(p2, "s2.dcm", Files.size(p2)),
            new UploadedDicom(p3, "s3.dcm", Files.size(p3)),
            new UploadedDicom(p4, "s4.dcm", Files.size(p4))
        );

        IngestExecution execution = service.ingest(uploads, "user-token");

        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.COMPLETE, execution.response().outcome());
        assertEquals(1, gateway.maxConcurrentCalls.get());
        assertEquals(List.of("1.2.3", "1.2.4", "1.2.5", "1.2.6"), gateway.calledStudyUids);
    }

    @Test
    void todas_instancias_aceitas_produz_outcome_COMPLETE() throws Exception {
        IngestExecution execution = service.ingest(twoStudyUploads(), "user-token");

        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.COMPLETE, execution.response().outcome());
        assertEquals(2, execution.response().summary().archiveAccepted());
        assertEquals(0, execution.response().summary().archiveRejected());
        assertEquals(0, execution.response().summary().locallyRejected());
    }

    @Test
    void lista_vazia_de_uploads_retorna_200_com_FAILED_sem_chamar_gateway() {
        IngestExecution execution = service.ingest(List.of(), "user-token");

        assertTrue(gateway.calledStudyUids.isEmpty());
        assertEquals(200, execution.suggestedStatus());
        assertEquals(IngestResponse.Outcome.FAILED, execution.response().outcome());
        assertEquals(0, execution.response().summary().received());
        assertTrue(execution.response().studies().isEmpty());
    }

    @Test
    void preserva_ordem_dos_estudos_nos_resultados() throws Exception {
        Path p1 = dicom("1.2.10", "1.2.10.1", "1.2.10.1.1", (byte) 10);
        Path p2 = dicom("1.2.20", "1.2.20.1", "1.2.20.1.1", (byte) 20);
        Path p3 = dicom("1.2.30", "1.2.30.1", "1.2.30.1.1", (byte) 30);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "s10.dcm", Files.size(p1)),
            new UploadedDicom(p2, "s20.dcm", Files.size(p2)),
            new UploadedDicom(p3, "s30.dcm", Files.size(p3))
        );

        IngestExecution execution = service.ingest(uploads, "user-token");

        List<String> studyUids = execution.response().studies().stream()
            .map(IngestResponse.StudyResult::studyInstanceUid)
            .toList();

        assertEquals(List.of("1.2.10", "1.2.20", "1.2.30"), studyUids);
    }

    static class FakeDicomArchiveGateway implements DicomArchiveGateway {
        final List<String> calledStudyUids = new CopyOnWriteArrayList<>();
        final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
        final Map<String, StowStudyResult> customResults = new ConcurrentHashMap<>();
        final AtomicInteger activeCalls = new AtomicInteger();
        final AtomicInteger maxConcurrentCalls = new AtomicInteger();
        volatile long sleepMs = 0;

        void fail(String studyUid, RuntimeException ex) {
            failures.put(studyUid, ex);
        }

        void setCustomResult(String studyUid, StowStudyResult result) {
            customResults.put(studyUid, result);
        }

        @Override
        public StowStudyResult storeStudy(String studyInstanceUid, List<ValidatedDicom> files, String accessToken) {
            calledStudyUids.add(studyInstanceUid);
            int current = activeCalls.incrementAndGet();
            maxConcurrentCalls.accumulateAndGet(current, Math::max);
            try {
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INTERRUPTED, e);
            } finally {
                activeCalls.decrementAndGet();
            }

            if (failures.containsKey(studyInstanceUid)) {
                throw failures.get(studyInstanceUid);
            }

            if (customResults.containsKey(studyInstanceUid)) {
                return customResults.get(studyInstanceUid);
            }

            List<StowInstanceResult> instances = files.stream()
                .map(f -> new StowInstanceResult(f.sopInstanceUid(), StowInstanceResult.Status.ACCEPTED, null))
                .toList();
            return new StowStudyResult(studyInstanceUid, instances);
        }
    }
}
