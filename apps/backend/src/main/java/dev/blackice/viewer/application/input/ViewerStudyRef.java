package dev.blackice.viewer.application.input;

import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import org.dcm4che3.util.UIDUtils;

/**
 * Validated immutable reference to a DICOM study.
 * Preserves exact UID representation without normalization or modification.
 */
public record ViewerStudyRef(String studyInstanceUid) {

    public ViewerStudyRef {
        if (studyInstanceUid == null || !UIDUtils.isValid(studyInstanceUid)) {
            throw new InvalidViewerRequestException();
        }
    }
}
