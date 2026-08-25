package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportAccessDeniedException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.ReportNotFoundException;
import dev.blackice.reports.application.exception.ReportVersionConflictException;
import dev.blackice.reports.application.input.UpdateReportCommand;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Use case to update an existing clinical report for a DICOM study.
 * Enforces authorship, terminal FINAL state, and optimistic concurrency via expected version.
 *
 * <p>Ordering of checks: load -&gt; author -&gt; terminal state -&gt; supplied version -&gt; pure transition -&gt; conditional update.</p>
 */
@ApplicationScoped
public class UpdateStudyReportUseCase {

    private final ReportRepository repository;
    private final Clock clock;

    public UpdateStudyReportUseCase(
        ReportRepository repository,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Inject
    public UpdateStudyReportUseCase(ReportRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public StudyReportResult execute(UpdateReportCommand command) {
        if (command == null) {
            throw new InvalidReportRequestException();
        }

        // 1. load
        Optional<Report> existingOpt = repository.findByStudyInstanceUid(command.study());
        if (existingOpt.isEmpty()) {
            throw new ReportNotFoundException("Report not found");
        }
        Report existing = existingOpt.get();

        // 2. author
        if (!existing.authorId().equals(command.actor().subject())) {
            throw new ReportAccessDeniedException("User is not the author of this report");
        }

        // 3. terminal state
        if (existing.status() == ReportStatus.FINAL) {
            throw new ReportConflictException("Report is in final state and cannot be modified");
        }

        // 4. supplied version
        if (existing.version() != command.expectedVersion()) {
            throw new ReportVersionConflictException("Supplied version does not match current version");
        }

        // 5. pure transition
        Instant now = clock.instant();
        Report revised = existing.revise(command.content(), command.status(), now);

        // 6. conditional update
        boolean updated = repository.updateIfVersionMatches(revised, command.expectedVersion());
        if (!updated) {
            throw new ReportVersionConflictException("Concurrent update detected");
        }

        return StudyReportResult.from(revised, command.actor());
    }
}
