package dev.blackice.reports.application.usecase;

import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.input.CreateReportCommand;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.ReportRepository;
import dev.blackice.reports.application.port.StudyExistenceGateway;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class CreateStudyReportTransactionTest {

    @Inject
    CreateStudyReportUseCase useCase;

    @Inject
    ReportRepository repository;

    @InjectMock
    StudyExistenceGateway gateway;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    @Test
    @DisplayName("QIDO gateway lookup executes with STATUS_NO_TRANSACTION before database insert transaction opens")
    void qido_gateway_executes_outside_database_transaction() {
        String uniqueStudyUid = "1.2.840.10008.1.999." + System.nanoTime();
        ReportStudyRef studyRef = new ReportStudyRef(uniqueStudyUid);
        ReportActor actor = new ReportActor("user-dr-tx", "Dr. Transaction");
        ReportContent content = new ReportContent("Lungs clear, no active consolidation.");
        String token = "server-side-token-abc";
        CreateReportCommand command = new CreateReportCommand(studyRef, actor, content, ReportStatus.DRAFT, token);

        AtomicInteger gatewayInvocations = new AtomicInteger(0);

        when(gateway.exists(eq(studyRef), eq(token))).thenAnswer(invocation -> {
            gatewayInvocations.incrementAndGet();
            int currentTxStatus = txRegistry.getTransactionStatus();
            assertEquals(
                Status.STATUS_NO_TRANSACTION,
                currentTxStatus,
                "StudyExistenceGateway.exists() must be called when no database transaction is active (STATUS_NO_TRANSACTION)"
            );
            return true;
        });

        StudyReportResult result = useCase.execute(command);

        assertEquals(1, gatewayInvocations.get(), "Gateway should have been called exactly once");
        assertNotNull(result);
        assertEquals(uniqueStudyUid, result.studyInstanceUid());
        assertEquals("Dr. Transaction", result.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, result.status());

        Optional<Report> persisted = repository.findByStudyInstanceUid(studyRef);
        assertTrue(persisted.isPresent(), "Report must be committed in PostgreSQL after execution");
        assertEquals(0L, persisted.get().version());
        assertEquals("Lungs clear, no active consolidation.", persisted.get().content());
    }

    @Test
    @DisplayName("QIDO gateway is never invoked if report already exists in database")
    void qido_gateway_never_invoked_if_report_already_exists() {
        String uniqueStudyUid = "1.2.840.10008.1.888." + System.nanoTime();
        ReportStudyRef studyRef = new ReportStudyRef(uniqueStudyUid);
        ReportActor actor = new ReportActor("user-dr-tx", "Dr. Transaction");
        ReportContent content = new ReportContent("Initial findings.");
        String token = "server-side-token-abc";

        CreateReportCommand firstCommand = new CreateReportCommand(studyRef, actor, content, ReportStatus.DRAFT, token);
        when(gateway.exists(eq(studyRef), eq(token))).thenReturn(true);
        useCase.execute(firstCommand);

        // Attempt second creation on the same study
        CreateReportCommand secondCommand = new CreateReportCommand(studyRef, actor, new ReportContent("Duplicate attempt"), ReportStatus.DRAFT, token);
        assertThrows(ReportConflictException.class, () -> useCase.execute(secondCommand));

        verify(gateway, never()).exists(eq(studyRef), eq("duplicate-token"));
    }
}
