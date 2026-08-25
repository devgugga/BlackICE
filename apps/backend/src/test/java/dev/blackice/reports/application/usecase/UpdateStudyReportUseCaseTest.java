package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportAccessDeniedException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.ReportNotFoundException;
import dev.blackice.reports.application.exception.ReportVersionConflictException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.input.UpdateReportCommand;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.application.port.StudyExistenceGateway;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateStudyReportUseCaseTest {

    private static final String STUDY_UID = "1.2.840.10008.1.2.5";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);
    private static final ReportActor AUTHOR = new ReportActor("subject-author", "Dr. Author");
    private static final ReportActor OTHER_ACTOR = new ReportActor("subject-other", "Dr. Other");
    private static final ReportContent INITIAL_CONTENT = new ReportContent("Initial findings.");
    private static final ReportContent REVISED_CONTENT = new ReportContent("Revised findings.");
    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-25T11:30:00Z");

    @Mock
    private ReportRepository repository;

    private Clock clock;
    private UpdateStudyReportUseCase useCase;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(T1, ZoneOffset.UTC);
        useCase = new UpdateStudyReportUseCase(repository, clock);
    }

    @Test
    @DisplayName("throws ReportNotFoundException when report does not exist in repository")
    void throws_report_not_found_when_report_missing() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.DRAFT, 0L
        );

        assertThrows(ReportNotFoundException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(repository, never()).updateIfVersionMatches(any(), anyLong());
    }

    @Test
    @DisplayName("throws ReportAccessDeniedException when requesting actor is not the original author")
    void throws_access_denied_when_actor_is_not_author() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.DRAFT, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, OTHER_ACTOR, REVISED_CONTENT, ReportStatus.DRAFT, 0L
        );

        assertThrows(ReportAccessDeniedException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(repository, never()).updateIfVersionMatches(any(), anyLong());
    }

    @Test
    @DisplayName("throws ReportConflictException when existing report is in FINAL status")
    void throws_conflict_when_report_already_final() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.FINAL, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.DRAFT, 0L
        );

        assertThrows(ReportConflictException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(repository, never()).updateIfVersionMatches(any(), anyLong());
    }

    @Test
    @DisplayName("throws ReportVersionConflictException when supplied expectedVersion does not match current version")
    void throws_version_conflict_when_expected_version_mismatches() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.DRAFT, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.DRAFT, 4L
        );

        assertThrows(ReportVersionConflictException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(repository, never()).updateIfVersionMatches(any(), anyLong());
    }

    @Test
    @DisplayName("updates draft report to draft with version increment and updated timestamp")
    void updates_draft_to_draft_successfully() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.DRAFT, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));
        when(repository.updateIfVersionMatches(any(Report.class), eq(0L))).thenReturn(true);

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.DRAFT, 0L
        );

        StudyReportResult result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals("Dr. Author", result.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, result.status());
        assertEquals(REVISED_CONTENT.value(), result.content());
        assertTrue(result.editable());
        assertEquals(T0, result.createdAt());
        assertEquals(T1, result.updatedAt());
        assertNull(result.finalizedAt());
        assertEquals(1L, result.version());

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(repository).updateIfVersionMatches(reportCaptor.capture(), eq(0L));
        Report revised = reportCaptor.getValue();
        assertEquals(1L, revised.version());
        assertEquals(ReportStatus.DRAFT, revised.status());
        assertEquals(REVISED_CONTENT.value(), revised.content());
        assertEquals(T1, revised.updatedAt());
        assertNull(revised.finalizedAt());
    }

    @Test
    @DisplayName("updates draft report to final with finalizedAt set and editable=false")
    void updates_draft_to_final_successfully() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.DRAFT, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));
        when(repository.updateIfVersionMatches(any(Report.class), eq(0L))).thenReturn(true);

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.FINAL, 0L
        );

        StudyReportResult result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals("Dr. Author", result.authorDisplayName());
        assertEquals(ReportStatus.FINAL, result.status());
        assertEquals(REVISED_CONTENT.value(), result.content());
        assertFalse(result.editable());
        assertEquals(T0, result.createdAt());
        assertEquals(T1, result.updatedAt());
        assertEquals(T1, result.finalizedAt());
        assertEquals(1L, result.version());

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(repository).updateIfVersionMatches(reportCaptor.capture(), eq(0L));
        Report revised = reportCaptor.getValue();
        assertEquals(1L, revised.version());
        assertEquals(ReportStatus.FINAL, revised.status());
        assertEquals(T1, revised.finalizedAt());
    }

    @Test
    @DisplayName("throws ReportVersionConflictException when atomic update adapter returns false")
    void throws_version_conflict_when_atomic_update_fails() {
        Report existing = Report.create(STUDY_REF, AUTHOR, INITIAL_CONTENT, ReportStatus.DRAFT, T0);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));
        when(repository.updateIfVersionMatches(any(Report.class), eq(0L))).thenReturn(false);

        UpdateReportCommand command = new UpdateReportCommand(
            STUDY_REF, AUTHOR, REVISED_CONTENT, ReportStatus.DRAFT, 0L
        );

        assertThrows(ReportVersionConflictException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(repository).updateIfVersionMatches(any(Report.class), eq(0L));
    }

    @Test
    @DisplayName("rejects null command with InvalidReportRequestException")
    void rejects_null_command() {
        assertThrows(
            InvalidReportRequestException.class,
            () -> useCase.execute(null)
        );
    }

    @Test
    @DisplayName("proves UpdateStudyReportUseCase has no StudyExistenceGateway dependency")
    void proves_no_study_existence_gateway_dependency() {
        for (Constructor<?> constructor : UpdateStudyReportUseCase.class.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertFalse(
                    StudyExistenceGateway.class.isAssignableFrom(paramType),
                    "UpdateStudyReportUseCase constructor must not depend on StudyExistenceGateway"
                );
            }
        }

        for (Field field : UpdateStudyReportUseCase.class.getDeclaredFields()) {
            assertFalse(
                StudyExistenceGateway.class.isAssignableFrom(field.getType()),
                "UpdateStudyReportUseCase fields must not depend on StudyExistenceGateway"
            );
        }
    }
}
