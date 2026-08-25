package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.domain.ReportStatus;

/**
 * Command carrying parameters required to update an existing study report.
 */
public record UpdateReportCommand(
    ReportStudyRef study,
    ReportActor actor,
    ReportContent content,
    ReportStatus status,
    long expectedVersion
) {

    public UpdateReportCommand {
        if (study == null || actor == null || content == null || status == null || expectedVersion < 0) {
            throw new InvalidReportRequestException();
        }
    }
}
