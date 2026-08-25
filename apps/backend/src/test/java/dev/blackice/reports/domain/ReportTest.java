package dev.blackice.reports.domain;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportTest {

    private static final String STUDY_UID = "1.2.840.10008.1.2";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);
    private static final ReportActor ACTOR = new ReportActor("user-sub-42", "Dr. Bob");
    private static final ReportContent INITIAL_CONTENT = new ReportContent("Initial clinical impression.");
    private static final ReportContent REVISED_CONTENT = new ReportContent("Revised clinical impression.");
    private static final ReportContent FINAL_CONTENT = new ReportContent("Final approved report findings.");

    @Test
    @DisplayName("creates draft report with initial version 0, matching timestamps, and null finalizedAt")
    void creates_draft_report() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");

        Report draft = Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, now);

        assertEquals(STUDY_UID, draft.studyInstanceUid());
        assertEquals("user-sub-42", draft.authorId());
        assertEquals("Dr. Bob", draft.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, draft.status());
        assertEquals(INITIAL_CONTENT.value(), draft.content());
        assertEquals(0L, draft.version());
        assertEquals(now, draft.createdAt());
        assertEquals(now, draft.updatedAt());
        assertNull(draft.finalizedAt());
    }

    @Test
    @DisplayName("creates direct final report with version 0 and finalizedAt equal to createdAt")
    void creates_direct_final_report() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");

        Report finalReport = Report.create(STUDY_REF, ACTOR, FINAL_CONTENT, ReportStatus.FINAL, now);

        assertEquals(STUDY_UID, finalReport.studyInstanceUid());
        assertEquals("user-sub-42", finalReport.authorId());
        assertEquals("Dr. Bob", finalReport.authorDisplayName());
        assertEquals(ReportStatus.FINAL, finalReport.status());
        assertEquals(FINAL_CONTENT.value(), finalReport.content());
        assertEquals(0L, finalReport.version());
        assertEquals(now, finalReport.createdAt());
        assertEquals(now, finalReport.updatedAt());
        assertEquals(now, finalReport.finalizedAt());
    }

    @Test
    @DisplayName("revises draft to draft: increments version, preserves identity and createdAt, updates content and updatedAt")
    void revises_draft_to_draft() {
        Instant t0 = Instant.parse("2026-08-25T10:00:00Z");
        Instant t1 = Instant.parse("2026-08-25T10:15:00Z");

        Report draft = Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, t0);
        Report updated = draft.revise(REVISED_CONTENT, ReportStatus.DRAFT, t1);

        assertEquals(STUDY_UID, updated.studyInstanceUid());
        assertEquals("user-sub-42", updated.authorId());
        assertEquals("Dr. Bob", updated.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, updated.status());
        assertEquals(REVISED_CONTENT.value(), updated.content());
        assertEquals(1L, updated.version());
        assertEquals(t0, updated.createdAt());
        assertEquals(t1, updated.updatedAt());
        assertNull(updated.finalizedAt());
    }

    @Test
    @DisplayName("revises draft to final: increments version, sets finalizedAt and status to FINAL")
    void revises_draft_to_final() {
        Instant t0 = Instant.parse("2026-08-25T10:00:00Z");
        Instant t1 = Instant.parse("2026-08-25T10:15:00Z");
        Instant t2 = Instant.parse("2026-08-25T10:30:00Z");

        Report draft = Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, t0);
        Report saved = draft.revise(REVISED_CONTENT, ReportStatus.DRAFT, t1);
        Report finalized = saved.revise(FINAL_CONTENT, ReportStatus.FINAL, t2);

        assertEquals(STUDY_UID, finalized.studyInstanceUid());
        assertEquals("user-sub-42", finalized.authorId());
        assertEquals("Dr. Bob", finalized.authorDisplayName());
        assertEquals(ReportStatus.FINAL, finalized.status());
        assertEquals(FINAL_CONTENT.value(), finalized.content());
        assertEquals(2L, finalized.version());
        assertEquals(t0, finalized.createdAt());
        assertEquals(t2, finalized.updatedAt());
        assertEquals(t2, finalized.finalizedAt());
    }

    @Test
    @DisplayName("rejects any revision attempt from a FINAL report")
    void rejects_any_mutation_from_final_report() {
        Instant t0 = Instant.parse("2026-08-25T10:00:00Z");
        Instant t1 = Instant.parse("2026-08-25T10:30:00Z");
        Instant t2 = Instant.parse("2026-08-25T10:45:00Z");

        Report finalized = Report.create(STUDY_REF, ACTOR, FINAL_CONTENT, ReportStatus.FINAL, t0);

        assertThrows(
            InvalidReportRequestException.class,
            () -> finalized.revise(REVISED_CONTENT, ReportStatus.DRAFT, t1)
        );

        assertThrows(
            InvalidReportRequestException.class,
            () -> finalized.revise(new ReportContent("New text attempt"), ReportStatus.FINAL, t2)
        );
    }

    @Test
    @DisplayName("validates invariants on direct record instantiation")
    void validates_invariants_on_record_construction() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");

        // DRAFT must have null finalizedAt
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", ReportStatus.DRAFT, "Content", 0L, now, now, now)
        );

        // FINAL must have non-null finalizedAt
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", ReportStatus.FINAL, "Content", 0L, now, now, null)
        );

        // Null fields rejected
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(null, "sub", "User", ReportStatus.DRAFT, "Content", 0L, now, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, null, "User", ReportStatus.DRAFT, "Content", 0L, now, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", null, ReportStatus.DRAFT, "Content", 0L, now, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", null, "Content", 0L, now, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", ReportStatus.DRAFT, null, 0L, now, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", ReportStatus.DRAFT, "Content", 0L, null, now, null)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> new Report(STUDY_UID, "sub", "User", ReportStatus.DRAFT, "Content", 0L, now, null, null)
        );
    }

    @Test
    @DisplayName("rejects null parameters in factory and revision methods")
    void rejects_null_arguments_in_methods() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");

        assertThrows(
            InvalidReportRequestException.class,
            () -> Report.create(null, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> Report.create(STUDY_REF, null, INITIAL_CONTENT, ReportStatus.DRAFT, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> Report.create(STUDY_REF, ACTOR, null, ReportStatus.DRAFT, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, null, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, null)
        );

        Report draft = Report.create(STUDY_REF, ACTOR, INITIAL_CONTENT, ReportStatus.DRAFT, now);
        assertThrows(
            InvalidReportRequestException.class,
            () -> draft.revise(null, ReportStatus.DRAFT, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> draft.revise(REVISED_CONTENT, null, now)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> draft.revise(REVISED_CONTENT, ReportStatus.DRAFT, null)
        );
    }
}
