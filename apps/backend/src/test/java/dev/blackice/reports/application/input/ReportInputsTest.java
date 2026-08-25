package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportPayloadTooLargeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportInputsTest {

    private static final String VALID_STUDY_UID = "1.2.840.10008.1.2";

    @Nested
    @DisplayName("ReportStudyRef validation")
    class ReportStudyRefTests {

        @Test
        @DisplayName("preserves valid StudyInstanceUID exactly without modification")
        void preserves_valid_study_instance_uid() {
            ReportStudyRef ref = new ReportStudyRef(VALID_STUDY_UID);
            assertEquals(VALID_STUDY_UID, ref.studyInstanceUid());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {
            "",
            " ",
            " 1.2.3",
            "1.2.3 ",
            "1..2",
            "1.02.3",
            "abc",
            "1.2.",
            ".1.2",
            "1.2.840.113619.2.55.3.604688435.123.1599720123.467.12345678901234567890" // > 64 chars
        })
        @DisplayName("rejects null or syntactically invalid study UIDs without echoing raw input")
        void rejects_invalid_study_uids(String invalidUid) {
            InvalidReportRequestException ex = assertThrows(
                InvalidReportRequestException.class,
                () -> new ReportStudyRef(invalidUid)
            );
            assertEquals("INVALID_REPORT_REQUEST", ex.getMessage());
            if (invalidUid != null && !invalidUid.isBlank()) {
                assertFalse(ex.getMessage().contains(invalidUid));
            }
        }
    }

    @Nested
    @DisplayName("ReportActor validation")
    class ReportActorTests {

        @Test
        @DisplayName("preserves valid subject and displayName exactly")
        void preserves_valid_actor_fields() {
            ReportActor actor = new ReportActor("user-sub-123", "Dr. Alice");
            assertEquals("user-sub-123", actor.subject());
            assertEquals("Dr. Alice", actor.displayName());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n", "\r\n"})
        @DisplayName("rejects null or blank subject")
        void rejects_invalid_subject(String invalidSubject) {
            assertThrows(
                InvalidReportRequestException.class,
                () -> new ReportActor(invalidSubject, "Dr. Alice")
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n", "\r\n"})
        @DisplayName("rejects null or blank displayName")
        void rejects_invalid_display_name(String invalidDisplayName) {
            assertThrows(
                InvalidReportRequestException.class,
                () -> new ReportActor("user-sub-123", invalidDisplayName)
            );
        }
    }

    @Nested
    @DisplayName("ReportContent validation")
    class ReportContentTests {

        @Test
        @DisplayName("preserves exact text content including spacing and line breaks")
        void preserves_exact_text_content() {
            String text = "  Impression: Normal study.\nLine 2.  ";
            ReportContent content = new ReportContent(text);
            assertEquals(text, content.value());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
            " ",
            "   ",
            "\t",
            "\n",
            "\r\n",
            "\u00A0",          // non-breaking space
            "\u2000\u2001",    // en quad, em quad
            "\u3000"           // ideographic space
        })
        @DisplayName("rejects null, empty, or whitespace-only content")
        void rejects_empty_or_whitespace_content(String blankText) {
            assertThrows(
                InvalidReportRequestException.class,
                () -> new ReportContent(blankText)
            );
        }

        @Test
        @DisplayName("accepts exactly 32,000 Unicode code points with astral characters (64,000 UTF-16 chars)")
        void accepts_exact_max_code_points_with_astral_characters() {
            String astralRepeat = "😀".repeat(32_000);
            assertEquals(64_000, astralRepeat.length());
            assertEquals(32_000, astralRepeat.codePointCount(0, astralRepeat.length()));

            ReportContent content = new ReportContent(astralRepeat);
            assertEquals(32_000, content.value().codePointCount(0, content.value().length()));
        }

        @Test
        @DisplayName("rejects 32,001 Unicode code points with astral characters as payload too large")
        void rejects_code_points_exceeding_limit_with_astral_characters() {
            String astralRepeat = "😀".repeat(32_001);
            assertEquals(64_002, astralRepeat.length());
            assertEquals(32_001, astralRepeat.codePointCount(0, astralRepeat.length()));

            assertThrows(
                ReportPayloadTooLargeException.class,
                () -> new ReportContent(astralRepeat)
            );
        }

        @Test
        @DisplayName("accepts 32,000 ASCII characters and rejects 32,001 ASCII characters")
        void accepts_and_rejects_ascii_boundary() {
            String ascii32k = "a".repeat(32_000);
            ReportContent content = new ReportContent(ascii32k);
            assertEquals(32_000, content.value().length());

            String ascii32kPlus1 = "a".repeat(32_001);
            assertThrows(
                ReportPayloadTooLargeException.class,
                () -> new ReportContent(ascii32kPlus1)
            );
        }
    }
}
