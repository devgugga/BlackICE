package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.StudyNotFoundException;
import dev.blackice.reports.application.input.CreateReportCommand;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
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

import java.lang.reflect.Method;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateStudyReportUseCaseTest {

    private static final String STUDY_UID = "1.2.840.10008.1.2.4";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);
    private static final ReportActor ACTOR = new ReportActor("subject-1", "Dr. Alice");
    private static final ReportContent CONTENT = new ReportContent("Lungs are clear.");
    private static final String ACCESS_TOKEN = "server-token";
    private static final Instant FIXED_NOW = Instant.parse("2026-08-25T11:00:00Z");

    @Mock
    private ReportRepository repository;

    @Mock
    private StudyExistenceGateway gateway;

    private Clock clock;
    private CreateStudyReportUseCase useCase;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        useCase = new CreateStudyReportUseCase(repository, gateway, clock);
    }

    @Test
    @DisplayName("throws ReportConflictException before contacting QIDO if report already exists locally")
    void throws_conflict_before_qido_when_report_already_exists() {
        Report existing = Report.create(STUDY_REF, ACTOR, CONTENT, ReportStatus.DRAFT, FIXED_NOW);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(existing));

        CreateReportCommand command = new CreateReportCommand(
            STUDY_REF, ACTOR, CONTENT, ReportStatus.DRAFT, ACCESS_TOKEN
        );

        assertThrows(ReportConflictException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verifyNoInteractions(gateway);
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("throws StudyNotFoundException when study does not exist in external DICOM archive")
    void throws_study_not_found_when_archive_study_missing() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());
        when(gateway.exists(eq(STUDY_REF), eq(ACCESS_TOKEN))).thenReturn(false);

        CreateReportCommand command = new CreateReportCommand(
            STUDY_REF, ACTOR, CONTENT, ReportStatus.DRAFT, ACCESS_TOKEN
        );

        assertThrows(StudyNotFoundException.class, () -> useCase.execute(command));

        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verify(gateway).exists(eq(STUDY_REF), eq(ACCESS_TOKEN));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("creates draft report when study exists in archive and has no existing report")
    void creates_draft_report_successfully() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());
        when(gateway.exists(eq(STUDY_REF), eq(ACCESS_TOKEN))).thenReturn(true);
        when(repository.insert(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateReportCommand command = new CreateReportCommand(
            STUDY_REF, ACTOR, CONTENT, ReportStatus.DRAFT, ACCESS_TOKEN
        );

        StudyReportResult result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals("Dr. Alice", result.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, result.status());
        assertEquals(CONTENT.value(), result.content());
        assertTrue(result.editable());
        assertEquals(FIXED_NOW, result.createdAt());
        assertEquals(FIXED_NOW, result.updatedAt());
        assertNull(result.finalizedAt());
        assertEquals(0L, result.version());

        verify(gateway).exists(eq(STUDY_REF), eq("server-token"));

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(repository).insert(reportCaptor.capture());
        Report inserted = reportCaptor.getValue();
        assertEquals(STUDY_UID, inserted.studyInstanceUid());
        assertEquals("subject-1", inserted.authorId());
        assertEquals("Dr. Alice", inserted.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, inserted.status());
        assertEquals(CONTENT.value(), inserted.content());
        assertEquals(0L, inserted.version());
        assertEquals(FIXED_NOW, inserted.createdAt());
        assertEquals(FIXED_NOW, inserted.updatedAt());
        assertNull(inserted.finalizedAt());
    }

    @Test
    @DisplayName("creates direct final report with finalizedAt set to creation time and editable=false")
    void creates_direct_final_report_successfully() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());
        when(gateway.exists(eq(STUDY_REF), eq(ACCESS_TOKEN))).thenReturn(true);
        when(repository.insert(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateReportCommand command = new CreateReportCommand(
            STUDY_REF, ACTOR, CONTENT, ReportStatus.FINAL, ACCESS_TOKEN
        );

        StudyReportResult result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(STUDY_UID, result.studyInstanceUid());
        assertEquals("Dr. Alice", result.authorDisplayName());
        assertEquals(ReportStatus.FINAL, result.status());
        assertEquals(CONTENT.value(), result.content());
        assertFalse(result.editable());
        assertEquals(FIXED_NOW, result.createdAt());
        assertEquals(FIXED_NOW, result.updatedAt());
        assertEquals(FIXED_NOW, result.finalizedAt());
        assertEquals(0L, result.version());

        verify(gateway).exists(eq(STUDY_REF), eq("server-token"));

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(repository).insert(reportCaptor.capture());
        Report inserted = reportCaptor.getValue();
        assertEquals("subject-1", inserted.authorId());
        assertEquals(ReportStatus.FINAL, inserted.status());
        assertEquals(FIXED_NOW, inserted.finalizedAt());
    }

    @Test
    @DisplayName("propagates ReportConflictException on repository unique race condition")
    void propagates_conflict_on_repository_insert_race() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());
        when(gateway.exists(eq(STUDY_REF), eq(ACCESS_TOKEN))).thenReturn(true);
        when(repository.insert(any(Report.class))).thenThrow(new ReportConflictException("Concurrent insert race"));

        CreateReportCommand command = new CreateReportCommand(
            STUDY_REF, ACTOR, CONTENT, ReportStatus.DRAFT, ACCESS_TOKEN
        );

        assertThrows(ReportConflictException.class, () -> useCase.execute(command));
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
    @DisplayName("verifies CreateStudyReportUseCase does not carry @Transactional")
    void verifies_no_transactional_annotation() {
        assertFalse(
            CreateStudyReportUseCase.class.isAnnotationPresent(jakarta.transaction.Transactional.class),
            "CreateStudyReportUseCase class must not be annotated with @Transactional"
        );

        for (Method method : CreateStudyReportUseCase.class.getDeclaredMethods()) {
            assertFalse(
                method.isAnnotationPresent(jakarta.transaction.Transactional.class),
                "CreateStudyReportUseCase method '" + method.getName() + "' must not be annotated with @Transactional"
            );
        }
    }
}
