package dev.blackice.viewer.application.port;

import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.result.FrameStream;

/**
 * Gateway port for streaming raw DICOM pixel frame data from the archive.
 */
public interface DicomFrameGateway {

    /**
     * Retrieves the first frame multipart stream for the specified instance.
     *
     * @param instance validated reference identifying the target study, series, and SOP instance
     * @param accessToken active bearer access token for DCM4CHEE authentication
     * @return open frame stream containing the outer Content-Type and input stream
     */
    FrameStream retrieveFirstFrame(ViewerInstanceRef instance, String accessToken);
}
