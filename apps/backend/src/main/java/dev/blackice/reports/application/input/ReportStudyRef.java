package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import org.dcm4che3.util.UIDUtils;

/**
 * Validated immutable reference to a DICOM study for reports.
 * Preserves exact UID representation without normalization or modification.
 */
public record ReportStudyRef(String studyInstanceUid) {

    public ReportStudyRef {
        if (studyInstanceUid == null || !UIDUtils.isValid(studyInstanceUid)) {
            throw new InvalidReportRequestException();
        }
    }
}
