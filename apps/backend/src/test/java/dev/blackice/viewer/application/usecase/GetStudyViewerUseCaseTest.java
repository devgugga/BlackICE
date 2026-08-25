package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.port.StudyHierarchyGateway;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesAvailability;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;
import dev.blackice.viewer.application.result.StudyViewerSummary;
import dev.blackice.viewer.application.result.UnsupportedReason;
import dev.blackice.viewer.application.result.ViewerSeriesSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStudyViewerUseCaseTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String TOKEN = "test-token";

    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";
    private static final String MR_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.4";
    private static final String SR_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.88.11";

    @Mock
    private StudyHierarchyGateway gateway;

    private SeriesSupportClassifier classifier;
    private GetStudyViewerUseCase useCase;
    private ViewerStudyRef studyRef;

    @BeforeEach
    void setUp() {
        classifier = new SeriesSupportClassifier();
        useCase = new GetStudyViewerUseCase(gateway, classifier);
        studyRef = new ViewerStudyRef(STUDY_UID);
    }

    private StudyMetadata sampleStudyMetadata() {
        return new StudyMetadata(
            STUDY_UID,
            "MARIA^SILVA",
            "123",
            "HOSPITAL-A",
            "20260822",
            "103512",
            "CT CHEST"
        );
    }

    @Test
    @DisplayName("executes study viewer retrieval with sorted series and support classification")
    void executes_study_viewer_retrieval() {
        String seriesUid1 = "1.2.3.1";
        String seriesUid2 = "1.2.3.2";
        String seriesUid9 = "1.2.3.9";

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
            new SeriesMetadata(seriesUid2, 2, "CT", "AXIAL", 1),
            new SeriesMetadata(seriesUid1, 1, "MR", "SAGITTAL", 2),
            new SeriesMetadata(seriesUid9, null, "SR", "REPORT", 1)
        ));
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
            new InstanceIdentityMetadata(seriesUid2, "1.2.3.2.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.1", MR_SOP_CLASS, 1),
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.2", MR_SOP_CLASS, 1),
            new InstanceIdentityMetadata(seriesUid9, "1.2.3.9.1", SR_SOP_CLASS, 1)
        ));

        StudyViewerSummary result = useCase.execute(studyRef, TOKEN);

        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals("MARIA^SILVA", result.patientName());
        assertEquals("123", result.patientId());
        assertEquals("HOSPITAL-A", result.patientIdIssuer());
        assertEquals("20260822", result.studyDate());
        assertEquals("103512", result.studyTime());
        assertEquals("CT CHEST", result.description());

        List<ViewerSeriesSummary> series = result.series();
        assertEquals(3, series.size());

        // Order: seriesNumber 1, then seriesNumber 2, then nulls last
        assertEquals(List.of(seriesUid1, seriesUid2, seriesUid9),
            series.stream().map(ViewerSeriesSummary::seriesInstanceUid).toList());

        ViewerSeriesSummary first = series.get(0);
        assertEquals(seriesUid1, first.seriesInstanceUid());
        assertEquals(1, first.seriesNumber());
        assertEquals("MR", first.modality());
        assertEquals("SAGITTAL", first.description());
        assertEquals(2, first.instanceCount());
        assertEquals(SeriesAvailability.SUPPORTED, first.availability());
        assertNull(first.unsupportedReason());

        ViewerSeriesSummary second = series.get(1);
        assertEquals(seriesUid2, second.seriesInstanceUid());
        assertEquals(2, second.seriesNumber());
        assertEquals(SeriesAvailability.SUPPORTED, second.availability());

        ViewerSeriesSummary third = series.get(2);
        assertEquals(seriesUid9, third.seriesInstanceUid());
        assertNull(third.seriesNumber());
        assertEquals(SeriesAvailability.UNSUPPORTED, third.availability());
        assertEquals(UnsupportedReason.NON_IMAGE_OBJECT, third.unsupportedReason());

        verify(gateway).findStudy(studyRef, TOKEN);
        verify(gateway).findSeries(studyRef, TOKEN);
        verify(gateway).findInstances(studyRef, TOKEN);
    }

    @Test
    @DisplayName("sorts series numerically by seriesNumber and tie-breaks by seriesInstanceUid")
    void sorts_series_numerically_and_breaks_ties() {
        String uid10 = "1.2.3.10";
        String uid2 = "1.2.3.2";
        String uid1A = "1.2.3.1.1";
        String uid1B = "1.2.3.1.2";
        String uidNullA = "1.2.3.99.1";
        String uidNullB = "1.2.3.99.2";

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
            new SeriesMetadata(uid10, 10, "CT", "TEN", 1),
            new SeriesMetadata(uidNullB, null, "CT", "NULL_B", 1),
            new SeriesMetadata(uid1B, 1, "CT", "ONE_B", 1),
            new SeriesMetadata(uid2, 2, "CT", "TWO", 1),
            new SeriesMetadata(uid1A, 1, "CT", "ONE_A", 1),
            new SeriesMetadata(uidNullA, null, "CT", "NULL_A", 1)
        ));
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
            new InstanceIdentityMetadata(uid10, "1.2.3.10.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(uidNullB, "1.2.3.99.2.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(uid1B, "1.2.3.1.2.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(uid2, "1.2.3.2.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(uid1A, "1.2.3.1.1.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(uidNullA, "1.2.3.99.1.1", CT_SOP_CLASS, 1)
        ));

        StudyViewerSummary result = useCase.execute(studyRef, TOKEN);

        List<String> sortedUids = result.series().stream()
            .map(ViewerSeriesSummary::seriesInstanceUid)
            .toList();

        // 1 (A then B), 2, 10, null (A then B)
        assertEquals(List.of(uid1A, uid1B, uid2, uid10, uidNullA, uidNullB), sortedUids);
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException when a series has zero instances in scan")
    void rejects_series_with_zero_instances() {
        String seriesUid1 = "1.2.3.1";
        String seriesUid2 = "1.2.3.2";

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
            new SeriesMetadata(seriesUid1, 1, "CT", "ONE", 1),
            new SeriesMetadata(seriesUid2, 2, "CT", "TWO", 1)
        ));
        // Instances only for seriesUid1; seriesUid2 is missing
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.1", CT_SOP_CLASS, 1)
        ));

        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException when instance scan contains unknown series UID")
    void rejects_unknown_series_in_instance_scan() {
        String seriesUid1 = "1.2.3.1";
        String unknownSeriesUid = "1.2.3.888";

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
            new SeriesMetadata(seriesUid1, 1, "CT", "ONE", 1)
        ));
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(unknownSeriesUid, "1.2.3.888.1", CT_SOP_CLASS, 1)
        ));

        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException when series instanceCount is inconsistent with scan")
    void rejects_inconsistent_instance_count() {
        String seriesUid1 = "1.2.3.1";

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of(
            new SeriesMetadata(seriesUid1, 1, "CT", "ONE", 5) // declares 5
        ));
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(List.of(
            // only returns 2 instances
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.1", CT_SOP_CLASS, 1),
            new InstanceIdentityMetadata(seriesUid1, "1.2.3.1.2", CT_SOP_CLASS, 1)
        ));

        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException when gateway returns null metadata")
    void rejects_null_gateway_responses() {
        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(null);
        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));

        when(gateway.findStudy(studyRef, TOKEN)).thenReturn(sampleStudyMetadata());
        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(null);
        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));

        when(gateway.findSeries(studyRef, TOKEN)).thenReturn(List.of());
        when(gateway.findInstances(studyRef, TOKEN)).thenReturn(null);
        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(studyRef, TOKEN));
    }

    @Test
    @DisplayName("validates arguments strictly")
    void validates_arguments() {
        assertThrows(NullPointerException.class, () -> useCase.execute(null, TOKEN));
        assertThrows(NullPointerException.class, () -> useCase.execute(studyRef, null));
    }
}
