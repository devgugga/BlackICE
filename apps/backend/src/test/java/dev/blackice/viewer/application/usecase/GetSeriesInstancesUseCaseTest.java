package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.port.SeriesMetadataGateway;
import dev.blackice.viewer.application.result.ViewerInstance;
import dev.blackice.viewer.application.result.ViewerSeriesInstances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetSeriesInstancesUseCaseTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.124";
    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";
    private static final ViewerSeriesRef SERIES_REF = new ViewerSeriesRef(STUDY_UID, SERIES_UID);

    private SeriesMetadataGateway gateway;
    private SpatialInstanceOrder spatialInstanceOrder;
    private GetSeriesInstancesUseCase useCase;

    @BeforeEach
    void setUp() {
        gateway = mock(SeriesMetadataGateway.class);
        spatialInstanceOrder = new SpatialInstanceOrder();
        useCase = new GetSeriesInstancesUseCase(gateway, spatialInstanceOrder);
    }

    @Test
    void execute_retrieves_orders_and_returns_curated_series_instances() {
        ViewerInstance slice2 = createInstance("1.2.3.2", 2, new double[]{0, 0, 10});
        ViewerInstance slice1 = createInstance("1.2.3.1", 1, new double[]{0, 0, -10});

        when(gateway.retrieve(eq(SERIES_REF), eq("test-token")))
            .thenReturn(List.of(slice2, slice1));

        ViewerSeriesInstances result = useCase.execute(SERIES_REF, "test-token");

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals(SERIES_UID, result.seriesInstanceUid());
        assertEquals(2, result.instances().size());
        assertEquals(List.of("1.2.3.1", "1.2.3.2"),
            result.instances().stream().map(ViewerInstance::sopInstanceUid).toList());

        verify(gateway).retrieve(SERIES_REF, "test-token");
    }

    @Test
    void execute_returns_empty_instances_when_gateway_returns_empty_list() {
        when(gateway.retrieve(eq(SERIES_REF), eq("test-token")))
            .thenReturn(List.of());

        ViewerSeriesInstances result = useCase.execute(SERIES_REF, "test-token");

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals(SERIES_UID, result.seriesInstanceUid());
        assertTrue(result.instances().isEmpty());
    }

    @Test
    void execute_rejects_non_allowlisted_sop_class() {
        String secondaryCapture = "1.2.840.10008.5.1.4.1.1.7";
        ViewerInstance nonAllowlisted = new ViewerInstance(
            "1.2.3.99",
            secondaryCapture,
            1,
            512,
            512,
            1,
            "MONOCHROME2",
            16,
            12,
            11,
            1,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        when(gateway.retrieve(any(), any())).thenReturn(List.of(nonAllowlisted));

        assertThrows(InvalidArchiveMetadataException.class, () -> useCase.execute(SERIES_REF, "token"));
    }

    @Test
    void execute_propagates_gateway_archive_viewer_exception() {
        when(gateway.retrieve(any(), any()))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND));

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> useCase.execute(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void execute_null_guards() {
        assertThrows(NullPointerException.class, () -> useCase.execute(null, "token"));
        assertThrows(NullPointerException.class, () -> useCase.execute(SERIES_REF, null));
    }

    private ViewerInstance createInstance(String sopInstanceUid, Integer instanceNumber, double[] ipp) {
        return new ViewerInstance(
            sopInstanceUid,
            CT_SOP_CLASS,
            instanceNumber,
            512,
            512,
            1,
            "MONOCHROME2",
            16,
            12,
            11,
            1,
            null,
            ipp,
            new double[]{1, 0, 0, 0, 1, 0},
            new double[]{0.5, 0.5},
            null,
            null,
            null,
            null,
            null
        );
    }
}
