package dev.blackice.reports.infrastructure.persistence;

import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a clinical report in the product database.
 */
@Entity(name = "ReportEntity")
@Table(name = "reports")
public class ReportEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "study_instance_uid", nullable = false, unique = true, length = 64)
    public String studyInstanceUid;

    @Column(name = "author_id", nullable = false, columnDefinition = "TEXT")
    public String authorId;

    @Column(name = "author_display_name", nullable = false, columnDefinition = "TEXT")
    public String authorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public ReportStatus status;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    public String content;

    @Column(name = "version", nullable = false)
    public long version;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "finalized_at")
    public Instant finalizedAt;

    public static ReportEntity fromDomain(Report report) {
        ReportEntity entity = new ReportEntity();
        entity.studyInstanceUid = report.studyInstanceUid();
        entity.authorId = report.authorId();
        entity.authorDisplayName = report.authorDisplayName();
        entity.status = report.status();
        entity.content = report.content();
        entity.version = report.version();
        entity.createdAt = report.createdAt();
        entity.updatedAt = report.updatedAt();
        entity.finalizedAt = report.finalizedAt();
        return entity;
    }

    public Report toDomain() {
        return new Report(
                this.studyInstanceUid,
                this.authorId,
                this.authorDisplayName,
                this.status,
                this.content,
                this.version,
                this.createdAt,
                this.updatedAt,
                this.finalizedAt
        );
    }
}
