package dev.blackice.viewer.application.input;

import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import org.dcm4che3.util.UIDUtils;

/**
 * Validated immutable reference to a DICOM instance within a series and study.
 * Preserves exact UID representations without normalization or modification.
 */
public record ViewerInstanceRef(String studyInstanceUid, String seriesInstanceUid, String sopInstanceUid) {

    public ViewerInstanceRef {
        if (studyInstanceUid == null || !UIDUtils.isValid(studyInstanceUid)
            || seriesInstanceUid == null || !UIDUtils.isValid(seriesInstanceUid)
            || sopInstanceUid == null || !UIDUtils.isValid(sopInstanceUid)) {
            throw new InvalidViewerRequestException();
        }
    }
}
