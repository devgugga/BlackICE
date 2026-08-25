package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.result.StudyReportResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Maps transport-independent StudyReportResult into public HTTP ReportResponse.
 */
@ApplicationScoped
public class ReportRepresentationMapper {

    public ReportResponse toResponse(StudyReportResult result) {
        if (result == null) {
            throw new InvalidReportRequestException();
        }
        return new ReportResponse(
            result.studyInstanceUid(),
            result.authorDisplayName(),
            result.status(),
            result.content(),
            result.editable(),
            result.createdAt(),
            result.updatedAt(),
            result.finalizedAt()
        );
    }
}
