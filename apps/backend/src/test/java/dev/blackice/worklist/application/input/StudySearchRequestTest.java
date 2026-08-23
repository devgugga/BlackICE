package dev.blackice.worklist.application.input;

import dev.blackice.worklist.application.exception.InvalidStudySearchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudySearchRequestTest {

    @Test
    void normalizes_filters_and_keeps_open_date_range() {
        StudySearchRequest request = new StudySearchRequest(
            "  MARIA  ", "  123  ", "ct", LocalDate.of(2026, 8, 1), null, 20, 0);

        assertEquals("MARIA", request.patientName());
        assertEquals("123", request.patientId());
        assertEquals("CT", request.modality());
        assertEquals(LocalDate.of(2026, 8, 1), request.dateFrom());
        assertNull(request.dateTo());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejects_invalid_filters(Executable invalidRequest) {
        assertThrows(InvalidStudySearchException.class, invalidRequest);
    }

    static Stream<Executable> invalidRequests() {
        return Stream.of(
            () -> new StudySearchRequest(null, null, null, null, null, 0, 0),
            () -> new StudySearchRequest(null, null, null, null, null, 101, 0),
            () -> new StudySearchRequest(null, null, null, null, null, 20, -1),
            () -> new StudySearchRequest(null, null, null, null, null, 20, 100_000),
            () -> new StudySearchRequest(null, null, null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), 20, 0),
            () -> new StudySearchRequest("MAR*IA", null, null, null, null, 20, 0),
            () -> new StudySearchRequest("MAR?IA", null, null, null, null, 20, 0),
            () -> new StudySearchRequest(null, "12*3", null, null, null, 20, 0),
            () -> new StudySearchRequest(null, "12?3", null, null, null, 20, 0),
            () -> new StudySearchRequest("MAR\nIA", null, null, null, null, 20, 0),
            () -> new StudySearchRequest(null, "12\t3", null, null, null, 20, 0),
            () -> new StudySearchRequest("A".repeat(65), null, null, null, null, 20, 0),
            () -> new StudySearchRequest(null, "1".repeat(65), null, null, null, 20, 0),
            () -> new StudySearchRequest(null, null, "INVALID-MOD", null, null, 20, 0),
            () -> new StudySearchRequest(null, null, "CT!", null, null, 20, 0),
            () -> new StudySearchRequest(null, null, "A".repeat(17), null, null, 20, 0)
        );
    }
}
