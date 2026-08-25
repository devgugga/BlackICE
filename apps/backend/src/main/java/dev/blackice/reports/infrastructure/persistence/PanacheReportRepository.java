package dev.blackice.reports.infrastructure.persistence;

import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.domain.Report;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL Panache implementation of the {@link ReportRepository} port.
 */
@ApplicationScoped
public class PanacheReportRepository implements ReportRepository {

    @Override
    public Optional<Report> findByStudyInstanceUid(ReportStudyRef study) {
        Objects.requireNonNull(study, "study cannot be null");
        ReportEntity entity = ReportEntity.find("studyInstanceUid", study.studyInstanceUid()).firstResult();
        return Optional.ofNullable(entity).map(ReportEntity::toDomain);
    }

    @Override
    @Transactional
    public Report insert(Report report) {
        Objects.requireNonNull(report, "report cannot be null");
        ReportEntity entity = ReportEntity.fromDomain(report);
        try {
            entity.persistAndFlush();
            return entity.toDomain();
        } catch (PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new ReportConflictException("Report already exists for study: " + report.studyInstanceUid(), e);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean updateIfVersionMatches(Report revised, long expectedVersion) {
        Objects.requireNonNull(revised, "revised cannot be null");
        int changed = ReportEntity.update(
                "content = ?1, status = ?2, updatedAt = ?3, finalizedAt = ?4, "
                        + "version = version + 1 where studyInstanceUid = ?5 and version = ?6",
                revised.content(),
                revised.status(),
                revised.updatedAt(),
                revised.finalizedAt(),
                revised.studyInstanceUid(),
                expectedVersion
        );
        return changed == 1;
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && constraintName.toLowerCase().contains("uq_reports_study_instance_uid")) {
                    return true;
                }
                if ("23505".equals(cve.getSQLState())) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    return true;
                }
                String message = sqlEx.getMessage();
                if (message != null && message.contains("uq_reports_study_instance_uid")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
