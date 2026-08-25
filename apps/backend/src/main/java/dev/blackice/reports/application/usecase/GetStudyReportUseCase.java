package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.application.result.StudyReportResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case to retrieve a study report projection for an authenticated actor.
 * Does not query the external DICOM archive.
 */
@ApplicationScoped
public class GetStudyReportUseCase {

    private final ReportRepository repository;

    @Inject
    public GetStudyReportUseCase(ReportRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Optional<StudyReportResult> execute(ReportStudyRef study, ReportActor actor) {
        if (study == null || actor == null) {
            throw new InvalidReportRequestException();
        }
        return repository.findByStudyInstanceUid(study)
            .map(report -> StudyReportResult.from(report, actor));
    }
}
