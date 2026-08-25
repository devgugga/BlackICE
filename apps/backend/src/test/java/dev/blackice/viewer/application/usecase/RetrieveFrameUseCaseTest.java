package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.port.DicomFrameGateway;
import dev.blackice.viewer.application.result.FrameStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrieveFrameUseCaseTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.468";
    private static final String SOP_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.469";
    private static final ViewerInstanceRef INSTANCE_REF = new ViewerInstanceRef(STUDY_UID, SERIES_UID, SOP_UID);

    @Test
    void execute_delegates_to_gateway_and_returns_frame_stream() throws IOException {
        DicomFrameGateway gateway = mock(DicomFrameGateway.class);
        RetrieveFrameUseCase useCase = new RetrieveFrameUseCase(gateway);

        String contentType = "multipart/related; type=\"application/octet-stream\"; boundary=boundary123";
        byte[] payload = "raw-pixel-data".getBytes(StandardCharsets.UTF_8);
        FrameStream expectedStream = new FrameStream(contentType, new ByteArrayInputStream(payload));

        when(gateway.retrieveFirstFrame(INSTANCE_REF, "test-token")).thenReturn(expectedStream);

        try (FrameStream result = useCase.execute(INSTANCE_REF, "test-token")) {
            assertNotNull(result);
            assertEquals(contentType, result.contentType());
            assertEquals("raw-pixel-data", new String(result.body().readAllBytes(), StandardCharsets.UTF_8));
        }

        verify(gateway, times(1)).retrieveFirstFrame(INSTANCE_REF, "test-token");
    }

    @Test
    void execute_propagates_gateway_exceptions() {
        DicomFrameGateway gateway = mock(DicomFrameGateway.class);
        RetrieveFrameUseCase useCase = new RetrieveFrameUseCase(gateway);

        when(gateway.retrieveFirstFrame(INSTANCE_REF, "test-token"))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND));

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> useCase.execute(INSTANCE_REF, "test-token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void execute_null_guards() {
        DicomFrameGateway gateway = mock(DicomFrameGateway.class);
        RetrieveFrameUseCase useCase = new RetrieveFrameUseCase(gateway);

        assertThrows(NullPointerException.class, () -> new RetrieveFrameUseCase(null));
        assertThrows(NullPointerException.class, () -> useCase.execute(null, "token"));
        assertThrows(NullPointerException.class, () -> useCase.execute(INSTANCE_REF, null));
    }
}
