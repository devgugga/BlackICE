package dev.blackice.reports.application.port;

import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.domain.Report;

import java.util.Optional;

/**
 * Port for loading and persisting study reports in the product database.
 */
public interface ReportRepository {

    Optional<Report> findByStudyInstanceUid(ReportStudyRef study);

    Report insert(Report report);

    boolean updateIfVersionMatches(Report revised, long expectedVersion);
}
