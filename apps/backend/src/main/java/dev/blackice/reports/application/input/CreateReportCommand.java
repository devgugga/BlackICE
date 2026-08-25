package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.domain.ReportStatus;

/**
 * Command carrying parameters required to create a new study report.
 */
public record CreateReportCommand(
    ReportStudyRef study,
    ReportActor actor,
    ReportContent content,
    ReportStatus status,
    String accessToken
) {

    public CreateReportCommand {
        if (study == null || actor == null || content == null || status == null
                || accessToken == null || accessToken.isBlank()) {
            throw new InvalidReportRequestException();
        }
    }
}
