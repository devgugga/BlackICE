package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.port.DicomFrameGateway;
import dev.blackice.viewer.application.result.FrameStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

/**
 * Use case that retrieves an open pixel frame stream for a single DICOM instance.
 */
@ApplicationScoped
public class RetrieveFrameUseCase {

    private final DicomFrameGateway frameGateway;

    @Inject
    public RetrieveFrameUseCase(DicomFrameGateway frameGateway) {
        this.frameGateway = Objects.requireNonNull(frameGateway, "frameGateway must not be null");
    }

    /**
     * Executes first frame retrieval against the DICOM archive.
     *
     * @param instance validated reference identifying the target study, series, and SOP instance
     * @param accessToken bearer access token for authentication
     * @return open frame stream ready for consumption or HTTP streaming
     */
    public FrameStream execute(ViewerInstanceRef instance, String accessToken) {
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        return frameGateway.retrieveFirstFrame(instance, accessToken);
    }
}
