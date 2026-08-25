package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;

/**
 * Authenticated actor capturing OIDC subject and display name.
 */
public record ReportActor(String subject, String displayName) {

    public ReportActor {
        if (subject == null || subject.isBlank() || displayName == null || displayName.isBlank()) {
            throw new InvalidReportRequestException();
        }
    }
}
