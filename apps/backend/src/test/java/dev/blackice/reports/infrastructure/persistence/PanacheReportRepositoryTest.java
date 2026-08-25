package dev.blackice.reports.infrastructure.persistence;

import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PanacheReportRepositoryTest {

    @Inject
    ReportRepository repository;

    @Test
    @DisplayName("ReportRepository port does not declare any delete method")
    void report_repository_port_has_no_delete_method() {
        boolean hasDelete = Arrays.stream(ReportRepository.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase().contains("delete") || name.toLowerCase().contains("remove"));
        assertFalse(hasDelete, "ReportRepository should not expose delete operations");
    }

    @Test
    @DisplayName("Returns empty optional when study has no report")
    void find_by_study_instance_uid_not_found() {
        String uid = "1.2.840.10008.1.999999999999." + System.nanoTime();
        Optional<Report> result = repository.findByStudyInstanceUid(new ReportStudyRef(uid));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Inserts and retrieves DRAFT report with exact round-trip fidelity")
    void insert_and_find_draft_report_exact_roundtrip() {
        String uid = "1.2.840.10008.1.111111." + System.nanoTime();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report report = Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-dr-smith", "Dr. Bob Smith"),
                new ReportContent("No acute intracranial abnormalities detected."),
                ReportStatus.DRAFT,
                now
        );

        Report inserted = repository.insert(report);
        assertAll(
                () -> assertEquals(uid, inserted.studyInstanceUid()),
                () -> assertEquals("user-dr-smith", inserted.authorId()),
                () -> assertEquals("Dr. Bob Smith", inserted.authorDisplayName()),
                () -> assertEquals(ReportStatus.DRAFT, inserted.status()),
                () -> assertEquals("No acute intracranial abnormalities detected.", inserted.content()),
                () -> assertEquals(0L, inserted.version()),
                () -> assertEquals(now, inserted.createdAt()),
                () -> assertEquals(now, inserted.updatedAt()),
                () -> assertNull(inserted.finalizedAt())
        );

        Optional<Report> retrieved = repository.findByStudyInstanceUid(new ReportStudyRef(uid));
        assertTrue(retrieved.isPresent());
        Report loaded = retrieved.get();
        assertAll(
                () -> assertEquals(uid, loaded.studyInstanceUid()),
                () -> assertEquals("user-dr-smith", loaded.authorId()),
                () -> assertEquals("Dr. Bob Smith", loaded.authorDisplayName()),
                () -> assertEquals(ReportStatus.DRAFT, loaded.status()),
                () -> assertEquals("No acute intracranial abnormalities detected.", loaded.content()),
                () -> assertEquals(0L, loaded.version()),
                () -> assertEquals(now, loaded.createdAt()),
                () -> assertEquals(now, loaded.updatedAt()),
                () -> assertNull(loaded.finalizedAt())
        );
    }

    @Test
    @DisplayName("Inserts and retrieves direct FINAL report with finalizedAt timestamp")
    void insert_and_find_direct_final_report() {
        String uid = "1.2.840.10008.1.222222." + System.nanoTime();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report report = Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-radiologist", "Dr. Jane Doe"),
                new ReportContent("Direct final diagnosis: Normal chest radiograph."),
                ReportStatus.FINAL,
                now
        );

        Report inserted = repository.insert(report);
        assertAll(
                () -> assertEquals(uid, inserted.studyInstanceUid()),
                () -> assertEquals(ReportStatus.FINAL, inserted.status()),
                () -> assertEquals(0L, inserted.version()),
                () -> assertNotNull(inserted.finalizedAt()),
                () -> assertEquals(now, inserted.finalizedAt())
        );

        Optional<Report> retrieved = repository.findByStudyInstanceUid(new ReportStudyRef(uid));
        assertTrue(retrieved.isPresent());
        Report loaded = retrieved.get();
        assertAll(
                () -> assertEquals(ReportStatus.FINAL, loaded.status()),
                () -> assertEquals(now, loaded.finalizedAt())
        );
    }

    @Test
    @DisplayName("Translates duplicate StudyInstanceUID insert to ReportConflictException")
    void insert_duplicate_study_instance_uid_throws_report_conflict_exception() {
        String uid = "1.2.840.10008.1.333333." + System.nanoTime();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report report1 = Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-author-1", "Author One"),
                new ReportContent("Initial impression."),
                ReportStatus.DRAFT,
                now
        );
        repository.insert(report1);

        Report report2 = Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-author-2", "Author Two"),
                new ReportContent("Conflicting initial impression."),
                ReportStatus.DRAFT,
                now.plusSeconds(10)
        );

        assertThrows(ReportConflictException.class, () -> repository.insert(report2));
    }

    @Test
    @DisplayName("Updates report atomically when expected version matches")
    void update_if_version_matches_success() {
        String uid = "1.2.840.10008.1.444444." + System.nanoTime();
        Instant created = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report initial = repository.insert(Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-dr", "Dr. House"),
                new ReportContent("Draft version 0 content"),
                ReportStatus.DRAFT,
                created
        ));

        Instant revisedTime = created.plusSeconds(60);
        Report revised = initial.revise(
                new ReportContent("Revised and finalized content"),
                ReportStatus.FINAL,
                revisedTime
        );

        boolean updated = repository.updateIfVersionMatches(revised, 0L);
        assertTrue(updated);

        Report updatedReport = repository.findByStudyInstanceUid(new ReportStudyRef(uid)).orElseThrow();
        assertAll(
                () -> assertEquals(1L, updatedReport.version()),
                () -> assertEquals("Revised and finalized content", updatedReport.content()),
                () -> assertEquals(ReportStatus.FINAL, updatedReport.status()),
                () -> assertEquals(revisedTime, updatedReport.updatedAt()),
                () -> assertEquals(revisedTime, updatedReport.finalizedAt()),
                () -> assertEquals(created, updatedReport.createdAt()),
                () -> assertEquals("user-dr", updatedReport.authorId()),
                () -> assertEquals("Dr. House", updatedReport.authorDisplayName())
        );
    }

    @Test
    @DisplayName("Fails update when expected version does not match")
    void update_if_version_matches_version_mismatch_returns_false() {
        String uid = "1.2.840.10008.1.555555." + System.nanoTime();
        Instant created = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report initial = repository.insert(Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-dr", "Dr. House"),
                new ReportContent("Draft version 0 content"),
                ReportStatus.DRAFT,
                created
        ));

        Instant revisedTime = created.plusSeconds(60);
        Report revised = initial.revise(
                new ReportContent("Outdated revision attempt"),
                ReportStatus.DRAFT,
                revisedTime
        );

        // Expect version 99L which does not match current version (0L)
        boolean updated = repository.updateIfVersionMatches(revised, 99L);
        assertFalse(updated);

        Report unchanged = repository.findByStudyInstanceUid(new ReportStudyRef(uid)).orElseThrow();
        assertEquals(0L, unchanged.version());
        assertEquals("Draft version 0 content", unchanged.content());
    }

    @Test
    @DisplayName("Concurrent updates: exactly one update wins and increments version, loser is rejected")
    void concurrent_updates_exactly_one_wins() throws ExecutionException, InterruptedException {
        String uid = "1.2.840.10008.1.666666." + System.nanoTime();
        Instant created = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Report initial = repository.insert(Report.create(
                new ReportStudyRef(uid),
                new ReportActor("user-dr", "Dr. House"),
                new ReportContent("Base draft v0"),
                ReportStatus.DRAFT,
                created
        ));

        Instant updateTimeA = created.plusSeconds(10);
        Instant updateTimeB = created.plusSeconds(15);
        Report revisionA = initial.revise(new ReportContent("Revision A content"), ReportStatus.DRAFT, updateTimeA);
        Report revisionB = initial.revise(new ReportContent("Revision B content"), ReportStatus.DRAFT, updateTimeB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> taskA = () -> repository.updateIfVersionMatches(revisionA, 0L);
            Callable<Boolean> taskB = () -> repository.updateIfVersionMatches(revisionB, 0L);

            Future<Boolean> futureA = executor.submit(taskA);
            Future<Boolean> futureB = executor.submit(taskB);

            boolean resultA = futureA.get();
            boolean resultB = futureB.get();

            // Exactly one must be true, the other must be false
            assertTrue((resultA && !resultB) || (!resultA && resultB),
                    "Expected exactly one update to succeed. resultA=" + resultA + ", resultB=" + resultB);

            Report current = repository.findByStudyInstanceUid(new ReportStudyRef(uid)).orElseThrow();
            assertEquals(1L, current.version(), "Version must be exactly 1 after one winning revision");

            if (resultA) {
                assertEquals("Revision A content", current.content());
            } else {
                assertEquals("Revision B content", current.content());
            }
        } finally {
            executor.shutdown();
        }
    }
}
