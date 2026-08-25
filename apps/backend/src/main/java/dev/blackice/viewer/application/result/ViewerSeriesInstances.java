package dev.blackice.viewer.application.result;

import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import org.dcm4che3.util.UIDUtils;

import java.util.List;

/**
 * Curated metadata response record for all instances belonging to an active series.
 */
public record ViewerSeriesInstances(
    String studyInstanceUid,
    String seriesInstanceUid,
    List<ViewerInstance> instances
) {
    public ViewerSeriesInstances {
        if (studyInstanceUid == null || !UIDUtils.isValid(studyInstanceUid)
            || seriesInstanceUid == null || !UIDUtils.isValid(seriesInstanceUid)) {
            throw new InvalidViewerRequestException();
        }
        instances = instances != null ? List.copyOf(instances) : List.of();
    }
}
