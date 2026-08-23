package dev.blackice.worklist.application.usecase;

import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.port.StudyQueryGateway;
import dev.blackice.worklist.application.result.StudyPage;
import dev.blackice.worklist.application.result.StudySummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchStudiesUseCaseTest {

    @Test
    void requests_one_extra_match_and_builds_page_without_count() {
        CapturingGateway gateway = new CapturingGateway(studies(21));
        SearchStudiesUseCase useCase = new SearchStudiesUseCase(gateway);
        StudySearchRequest request = request(20, 40);

        StudyPage result = useCase.search(request, "user-token");

        assertEquals(21, gateway.fetchLimit);
        assertEquals(1, gateway.calls);
        assertEquals("user-token", gateway.token);
        assertEquals(20, result.items().size());
        assertEquals(new StudyPage.PageMetadata(20, 40, true, true), result.page());
    }

    @Test
    void final_partial_page_has_no_next_page() {
        StudyPage result = new SearchStudiesUseCase(new CapturingGateway(studies(7)))
            .search(request(20, 20), "user-token");

        assertEquals(7, result.items().size());
        assertTrue(result.page().hasPrevious());
        assertFalse(result.page().hasNext());
    }

    @Test
    void first_page_has_no_previous_page() {
        StudyPage result = new SearchStudiesUseCase(new CapturingGateway(studies(5)))
            .search(request(20, 0), "user-token");

        assertEquals(5, result.items().size());
        assertFalse(result.page().hasPrevious());
        assertFalse(result.page().hasNext());
    }

    @Test
    void rejects_null_arguments_or_dependencies() {
        SearchStudiesUseCase useCase = new SearchStudiesUseCase(new CapturingGateway(studies(0)));
        StudySearchRequest request = request(20, 0);

        assertThrows(NullPointerException.class, () -> new SearchStudiesUseCase(null));
        assertThrows(NullPointerException.class, () -> useCase.search(null, "user-token"));
        assertThrows(NullPointerException.class, () -> useCase.search(request, null));
    }

    private static StudySearchRequest request(int limit, int offset) {
        return new StudySearchRequest(null, null, null, null, null, limit, offset);
    }

    private static List<StudySummary> studies(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> new StudySummary(
                "1.2.840." + (index + 1),
                "PATIENT " + index,
                "ID" + index,
                null,
                "20260822",
                "120000",
                List.of("CT"),
                "Study Description " + index,
                1,
                1
            ))
            .toList();
    }

    private static final class CapturingGateway implements StudyQueryGateway {
        public int calls;
        public int fetchLimit;
        public String token;
        private final List<StudySummary> response;

        public CapturingGateway(List<StudySummary> response) {
            this.response = List.copyOf(response);
        }

        @Override
        public List<StudySummary> search(StudySearchRequest request, int fetchLimit, String accessToken) {
            this.calls++;
            this.fetchLimit = fetchLimit;
            this.token = accessToken;
            return this.response;
        }
    }
}
