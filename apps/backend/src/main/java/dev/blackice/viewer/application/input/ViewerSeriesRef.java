package dev.blackice.viewer.application.input;

import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import org.dcm4che3.util.UIDUtils;

/**
 * Validated immutable reference to a DICOM series within a study.
 * Preserves exact UID representations without normalization or modification.
 */
public record ViewerSeriesRef(String studyInstanceUid, String seriesInstanceUid) {

    public ViewerSeriesRef {
        if (studyInstanceUid == null || !UIDUtils.isValid(studyInstanceUid)
            || seriesInstanceUid == null || !UIDUtils.isValid(seriesInstanceUid)) {
            throw new InvalidViewerRequestException();
        }
    }
}
