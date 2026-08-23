package dev.blackice.ingest.application.usecase;

import dev.blackice.ingest.application.exception.ArchiveUnavailableException;
import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.port.DicomArchiveGateway;
import dev.blackice.ingest.application.port.DicomBatchValidator;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import dev.blackice.ingest.infrastructure.dicom.Dcm4cheDicomBatchValidator;
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

class IngestStudiesUseCaseTest {

    @TempDir
    Path temp;

    private DicomBatchValidator validator;
    private FakeDicomArchiveGateway gateway;
    private IngestStudiesUseCase useCase;

    @BeforeEach
    void setUp() {
        validator = new Dcm4cheDicomBatchValidator();
        gateway = new FakeDicomArchiveGateway();
        useCase = new IngestStudiesUseCase(validator, gateway, 1);
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
    void zero_valid_files_return_failed_result_without_calling_gateway() throws Exception {
        Path corrupt = temp.resolve("corrupt.dcm");
        Files.write(corrupt, new byte[] {1, 2, 3, 4});

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(corrupt, "corrupt.dcm", Files.size(corrupt))
        );

        IngestResult result = useCase.ingest(uploads, "user-token");

        assertTrue(gateway.calledStudyUids.isEmpty());
        assertEquals(IngestResult.Outcome.FAILED, result.outcome());

        IngestResult.Summary summary = result.summary();
        assertEquals(1, summary.received());
        assertEquals(0, summary.locallyValid());
        assertEquals(1, summary.locallyRejected());
        assertEquals(0, summary.archiveAccepted());
        assertEquals(0, summary.archiveRejected());

        assertTrue(result.studies().isEmpty());
        assertEquals(1, result.locallyRejectedFiles().size());
        assertEquals("corrupt.dcm", result.locallyRejectedFiles().getFirst().filename());
    }

    @Test
    void both_study_groups_run_when_the_first_throws_archive_unavailable() throws Exception {
        gateway.fail("1.2.3", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.CONNECTION, new IOException("offline")));

        IngestResult result = useCase.ingest(twoStudyUploads(), "user-token");

        assertEquals(List.of("1.2.3", "1.2.4"), gateway.calledStudyUids);
        assertEquals(IngestResult.Outcome.PARTIAL, result.outcome());

        List<IngestResult.StudyResult> studies = result.studies();
        assertEquals(2, studies.size());

        IngestResult.StudyResult study1 = studies.get(0);
        assertEquals("1.2.3", study1.studyInstanceUid());
        assertEquals(IngestResult.StudyStatus.FAILED, study1.status());
        assertEquals("CONNECTION", study1.errorCode());
        assertTrue(study1.instances().isEmpty());

        IngestResult.StudyResult study2 = studies.get(1);
        assertEquals("1.2.4", study2.studyInstanceUid());
        assertEquals(IngestResult.StudyStatus.COMPLETE, study2.status());
        assertNull(study2.errorCode());
        assertEquals(1, study2.instances().size());
        assertEquals("1.2.4.1.1", study2.instances().getFirst().sopInstanceUid());
        assertEquals(StowInstanceResult.Status.ACCEPTED, study2.instances().getFirst().status());
    }

    @Test
    void successful_group_and_infrastructure_failure_produce_partial_result() throws Exception {
        gateway.fail("1.2.4", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.TIMEOUT, new IOException("timeout")));

        IngestResult result = useCase.ingest(twoStudyUploads(), "user-token");

        assertEquals(IngestResult.Outcome.PARTIAL, result.outcome());

        IngestResult.Summary summary = result.summary();
        assertEquals(2, summary.received());
        assertEquals(2, summary.locallyValid());
        assertEquals(0, summary.locallyRejected());
        assertEquals(1, summary.archiveAccepted());
        assertEquals(1, summary.archiveRejected());
    }

    @Test
    void all_attempted_groups_unavailable_produce_failed_result() throws Exception {
        gateway.fail("1.2.3", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.CONNECTION, new IOException("offline")));
        gateway.fail("1.2.4", new ArchiveUnavailableException(
            ArchiveUnavailableException.Reason.TIMEOUT, new IOException("timeout")));

        IngestResult result = useCase.ingest(twoStudyUploads(), "user-token");

        assertEquals(IngestResult.Outcome.FAILED, result.outcome());

        IngestResult.Summary summary = result.summary();
        assertEquals(2, summary.received());
        assertEquals(2, summary.locallyValid());
        assertEquals(0, summary.locallyRejected());
        assertEquals(0, summary.archiveAccepted());
        assertEquals(2, summary.archiveRejected());

        // A razão interna de cada estudo é preservada: é ela que permite à
        // fronteira distinguir indisponibilidade de resposta inutilizável.
        for (IngestResult.StudyResult study : result.studies()) {
            assertEquals(IngestResult.StudyStatus.FAILED, study.status());
        }
        assertEquals(
            List.of("CONNECTION", "TIMEOUT"),
            result.studies().stream().map(IngestResult.StudyResult::errorCode).sorted().toList());
    }

    @Test
    void local_and_archive_rejections_produce_exact_counts() throws Exception {
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

        IngestResult result = useCase.ingest(uploads, "user-token");

        assertEquals(IngestResult.Outcome.PARTIAL, result.outcome());

        IngestResult.Summary summary = result.summary();
        assertEquals(4, summary.received());
        assertEquals(3, summary.locallyValid());
        assertEquals(1, summary.locallyRejected());
        assertEquals(2, summary.archiveAccepted());
        assertEquals(1, summary.archiveRejected());

        assertEquals(1, result.locallyRejectedFiles().size());
        assertEquals("invalid.dcm", result.locallyRejectedFiles().getFirst().filename());

        List<IngestResult.StudyResult> studies = result.studies();
        assertEquals(2, studies.size());

        IngestResult.StudyResult s1 = studies.get(0);
        assertEquals("1.2.3", s1.studyInstanceUid());
        assertEquals(IngestResult.StudyStatus.PARTIAL, s1.status());
        assertNull(s1.errorCode());
        assertEquals(2, s1.instances().size());
        assertEquals(StowInstanceResult.Status.ACCEPTED, s1.instances().get(0).status());
        assertEquals(StowInstanceResult.Status.REJECTED, s1.instances().get(1).status());
        assertEquals(272, s1.instances().get(1).reason());

        IngestResult.StudyResult s2 = studies.get(1);
        assertEquals("1.2.4", s2.studyInstanceUid());
        assertEquals(IngestResult.StudyStatus.COMPLETE, s2.status());
        assertNull(s2.errorCode());
        assertEquals(1, s2.instances().size());
        assertEquals(StowInstanceResult.Status.WARNING, s2.instances().getFirst().status());
    }

    @Test
    void concurrency_limit_is_respected() throws Exception {
        gateway.sleepMs = 50;
        useCase = new IngestStudiesUseCase(validator, gateway, 1);

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

        IngestResult result = useCase.ingest(uploads, "user-token");

        assertEquals(IngestResult.Outcome.COMPLETE, result.outcome());
        assertEquals(1, gateway.maxConcurrentCalls.get());
        assertEquals(List.of("1.2.3", "1.2.4", "1.2.5", "1.2.6"), gateway.calledStudyUids);
    }

    @Test
    void all_accepted_instances_produce_complete_outcome() throws Exception {
        IngestResult result = useCase.ingest(twoStudyUploads(), "user-token");

        assertEquals(IngestResult.Outcome.COMPLETE, result.outcome());
        assertEquals(2, result.summary().archiveAccepted());
        assertEquals(0, result.summary().archiveRejected());
        assertEquals(0, result.summary().locallyRejected());
    }

    @Test
    void empty_upload_list_produces_failed_result_without_calling_gateway() {
        IngestResult result = useCase.ingest(List.of(), "user-token");

        assertTrue(gateway.calledStudyUids.isEmpty());
        assertEquals(IngestResult.Outcome.FAILED, result.outcome());
        assertEquals(0, result.summary().received());
        assertTrue(result.studies().isEmpty());
    }

    @Test
    void preserves_study_order_in_results() throws Exception {
        Path p1 = dicom("1.2.10", "1.2.10.1", "1.2.10.1.1", (byte) 10);
        Path p2 = dicom("1.2.20", "1.2.20.1", "1.2.20.1.1", (byte) 20);
        Path p3 = dicom("1.2.30", "1.2.30.1", "1.2.30.1.1", (byte) 30);

        List<UploadedDicom> uploads = List.of(
            new UploadedDicom(p1, "s10.dcm", Files.size(p1)),
            new UploadedDicom(p2, "s20.dcm", Files.size(p2)),
            new UploadedDicom(p3, "s30.dcm", Files.size(p3))
        );

        IngestResult result = useCase.ingest(uploads, "user-token");

        List<String> studyUids = result.studies().stream()
            .map(IngestResult.StudyResult::studyInstanceUid)
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
