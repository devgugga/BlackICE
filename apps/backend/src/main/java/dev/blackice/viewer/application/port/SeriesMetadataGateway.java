package dev.blackice.viewer.application.port;

import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;

import java.util.List;

/**
 * Port for retrieving curated instance metadata for a specific active series from the DICOM archive.
 */
public interface SeriesMetadataGateway {
    /**
     * Retrieves curated metadata for all instances belonging to the given series.
     *
     * @param series validated series reference
     * @param accessToken user bearer access token
     * @return list of parsed viewer instances
     */
    List<ViewerInstance> retrieve(ViewerSeriesRef series, String accessToken);
}
