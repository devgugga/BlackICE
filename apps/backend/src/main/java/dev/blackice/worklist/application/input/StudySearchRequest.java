package dev.blackice.worklist.application.input;

import dev.blackice.worklist.application.exception.InvalidStudySearchException;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Validated search criteria for querying studies from the worklist archive.
 */
public record StudySearchRequest(
    String patientName,
    String patientId,
    String modality,
    LocalDate dateFrom,
    LocalDate dateTo,
    int limit,
    int offset
) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_OFFSET = 99_999;

    public StudySearchRequest {
        patientName = normalize(patientName);
        patientId = normalize(patientId);
        modality = normalize(modality);
        modality = modality == null ? null : modality.toUpperCase(Locale.ROOT);
        validateText("patientName", patientName, 64);
        validateText("patientId", patientId, 64);
        if (modality != null && !modality.matches("[A-Z0-9_]{1,16}")) {
            throw new InvalidStudySearchException("INVALID_MODALITY");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new InvalidStudySearchException("INVALID_DATE_RANGE");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidStudySearchException("INVALID_LIMIT");
        }
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new InvalidStudySearchException("INVALID_OFFSET");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    private static void validateText(String field, String value, int maximumLength) {
        if (value == null) return;
        boolean invalid = value.length() > maximumLength || value.chars()
            .anyMatch(character -> Character.isISOControl(character) || character == '*' || character == '?');
        if (invalid) throw new InvalidStudySearchException("INVALID_" + field.toUpperCase(Locale.ROOT));
    }
}
