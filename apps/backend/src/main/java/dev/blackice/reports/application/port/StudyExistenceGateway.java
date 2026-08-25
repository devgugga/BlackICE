package dev.blackice.reports.application.port;

import dev.blackice.reports.application.input.ReportStudyRef;

/**
 * Port for querying study existence in the external DICOM archive via QIDO-RS.
 */
public interface StudyExistenceGateway {

    boolean exists(ReportStudyRef study, String accessToken);
}
