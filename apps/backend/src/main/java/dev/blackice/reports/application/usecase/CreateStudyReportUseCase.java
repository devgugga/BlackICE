package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.StudyNotFoundException;
import dev.blackice.reports.application.input.CreateReportCommand;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.application.port.StudyExistenceGateway;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.domain.Report;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Use case to create a new clinical report for a DICOM study.
 * Validates local uniqueness first, verifies study existence in the archive via QIDO-RS,
 * and persists the new report.
 *
 * <p>Invariant: This class must not be annotated with {@code @Transactional} so external
 * archive I/O is never executed inside an open database transaction.</p>
 */
@ApplicationScoped
public class CreateStudyReportUseCase {

    private final ReportRepository repository;
    private final StudyExistenceGateway gateway;
    private final Clock clock;

    public CreateStudyReportUseCase(
        ReportRepository repository,
        StudyExistenceGateway gateway,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Inject
    public CreateStudyReportUseCase(
        ReportRepository repository,
        StudyExistenceGateway gateway
    ) {
        this(repository, gateway, Clock.systemUTC());
    }

    public StudyReportResult execute(CreateReportCommand command) {
        if (command == null) {
            throw new InvalidReportRequestException();
        }

        if (repository.findByStudyInstanceUid(command.study()).isPresent()) {
            throw new ReportConflictException("Report already exists for study");
        }

        if (!gateway.exists(command.study(), command.accessToken())) {
            throw new StudyNotFoundException("Study does not exist in archive");
        }

        Instant now = clock.instant();
        Report report = Report.create(
            command.study(),
            command.actor(),
            command.content(),
            command.status(),
            now
        );

        Report inserted = repository.insert(report);
        return StudyReportResult.from(inserted, command.actor());
    }
}
