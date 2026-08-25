package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStudyReportUseCaseTest {

    private static final String STUDY_UID = "1.2.840.10008.1.2.1";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);
    private static final ReportActor AUTHOR = new ReportActor("dr-alice", "Dr. Alice");
    private static final ReportActor OTHER_ACTOR = new ReportActor("dr-bob", "Dr. Bob");
    private static final ReportContent CONTENT = new ReportContent("Clear lungs, no active disease.");

    @Mock
    private ReportRepository repository;

    private GetStudyReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetStudyReportUseCase(repository);
    }

    @Test
    @DisplayName("returns empty Optional when report does not exist in repository")
    void returns_empty_when_report_does_not_exist() {
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.empty());

        Optional<StudyReportResult> result = useCase.execute(STUDY_REF, AUTHOR);

        assertTrue(result.isEmpty());
        verify(repository).findByStudyInstanceUid(STUDY_REF);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("returns draft report with editable=true when requesting actor is author")
    void returns_draft_report_editable_for_author() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        Report draft = Report.create(STUDY_REF, AUTHOR, CONTENT, ReportStatus.DRAFT, now);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(draft));

        Optional<StudyReportResult> result = useCase.execute(STUDY_REF, AUTHOR);

        assertTrue(result.isPresent());
        StudyReportResult reportResult = result.get();
        assertEquals(STUDY_UID, reportResult.studyInstanceUid());
        assertEquals("Dr. Alice", reportResult.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, reportResult.status());
        assertEquals(CONTENT.value(), reportResult.content());
        assertTrue(reportResult.editable());
        assertEquals(now, reportResult.createdAt());
        assertEquals(now, reportResult.updatedAt());
        assertNull(reportResult.finalizedAt());
        assertEquals(0L, reportResult.version());

        verify(repository).findByStudyInstanceUid(STUDY_REF);
    }

    @Test
    @DisplayName("returns draft report with editable=false when requesting actor is not author")
    void returns_draft_report_not_editable_for_other_actor() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        Report draft = Report.create(STUDY_REF, AUTHOR, CONTENT, ReportStatus.DRAFT, now);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(draft));

        Optional<StudyReportResult> result = useCase.execute(STUDY_REF, OTHER_ACTOR);

        assertTrue(result.isPresent());
        StudyReportResult reportResult = result.get();
        assertEquals(STUDY_UID, reportResult.studyInstanceUid());
        assertEquals("Dr. Alice", reportResult.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, reportResult.status());
        assertFalse(reportResult.editable());
    }

    @Test
    @DisplayName("returns final report with editable=false even when requesting actor is author")
    void returns_final_report_not_editable_even_for_author() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        Report finalReport = Report.create(STUDY_REF, AUTHOR, CONTENT, ReportStatus.FINAL, now);
        when(repository.findByStudyInstanceUid(STUDY_REF)).thenReturn(Optional.of(finalReport));

        Optional<StudyReportResult> result = useCase.execute(STUDY_REF, AUTHOR);

        assertTrue(result.isPresent());
        StudyReportResult reportResult = result.get();
        assertEquals(STUDY_UID, reportResult.studyInstanceUid());
        assertEquals(ReportStatus.FINAL, reportResult.status());
        assertFalse(reportResult.editable());
        assertEquals(now, reportResult.finalizedAt());
    }

    @Test
    @DisplayName("rejects null arguments with InvalidReportRequestException")
    void rejects_null_arguments() {
        assertThrows(
            InvalidReportRequestException.class,
            () -> useCase.execute(null, AUTHOR)
        );
        assertThrows(
            InvalidReportRequestException.class,
            () -> useCase.execute(STUDY_REF, null)
        );
    }

    @Test
    @DisplayName("proves GetStudyReportUseCase has no StudyExistenceGateway dependency")
    void proves_no_study_existence_gateway_dependency() {
        for (Constructor<?> constructor : GetStudyReportUseCase.class.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertFalse(
                    StudyExistenceGateway.class.isAssignableFrom(paramType),
                    "GetStudyReportUseCase constructor must not depend on StudyExistenceGateway"
                );
            }
        }

        for (Field field : GetStudyReportUseCase.class.getDeclaredFields()) {
            assertFalse(
                StudyExistenceGateway.class.isAssignableFrom(field.getType()),
                "GetStudyReportUseCase fields must not depend on StudyExistenceGateway"
            );
        }
    }
}
