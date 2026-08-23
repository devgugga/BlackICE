package dev.blackice.ingest.api;

import dev.blackice.ingest.application.result.IngestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestHttpStatusResolverTest {

    @Test
    void total_local_rejection_returns_422() {
        IngestResult result = new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(1, 0, 1, 0, 0),
            List.of(),
            List.of()
        );

        assertEquals(422, IngestHttpStatusResolver.resolve(result));
    }

    @Test
    void unavailable_archive_for_all_valid_studies_returns_503() {
        IngestResult result = new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(2, 2, 0, 0, 2),
            List.of(
                unavailableStudy("1.2.3"),
                unavailableStudy("1.2.4")
            ),
            List.of()
        );

        assertEquals(503, IngestHttpStatusResolver.resolve(result));
    }

    @Test
    void partial_result_returns_200() {
        IngestResult result = new IngestResult(
            IngestResult.Outcome.PARTIAL,
            new IngestResult.Summary(2, 2, 0, 1, 1),
            List.of(),
            List.of()
        );

        assertEquals(200, IngestHttpStatusResolver.resolve(result));
    }

    private static IngestResult.StudyResult unavailableStudy(String studyInstanceUid) {
        return new IngestResult.StudyResult(
            studyInstanceUid,
            IngestResult.StudyStatus.FAILED,
            List.of(),
            "ARCHIVE_UNAVAILABLE"
        );
    }
}
