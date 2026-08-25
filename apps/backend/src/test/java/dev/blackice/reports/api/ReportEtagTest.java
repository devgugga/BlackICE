package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import jakarta.ws.rs.core.EntityTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportEtagTest {

    @Test
    void from_version_produces_strong_quoted_entity_tag() {
        EntityTag tag0 = ReportEtag.fromVersion(0L);
        assertNotNull(tag0);
        assertFalse(tag0.isWeak(), "ETag must be strong");
        assertEquals("AAAAAAAAAAA", tag0.getValue());
        assertEquals("\"AAAAAAAAAAA\"", tag0.toString());

        EntityTag tag42 = ReportEtag.fromVersion(42L);
        assertNotNull(tag42);
        assertFalse(tag42.isWeak());
        assertEquals("AAAAAAAAACo", tag42.getValue());

        EntityTag tagMax = ReportEtag.fromVersion(Long.MAX_VALUE);
        assertNotNull(tagMax);
        assertFalse(tagMax.isWeak());
    }

    @Test
    void from_version_rejects_negative_version() {
        assertThrows(InvalidReportRequestException.class, () -> ReportEtag.fromVersion(-1L));
        assertThrows(InvalidReportRequestException.class, () -> ReportEtag.fromVersion(Long.MIN_VALUE));
    }

    @Test
    void parses_strong_single_valid_etags() {
        assertEquals(0L, ReportEtag.parseStrongSingle("\"AAAAAAAAAAA\""));
        assertEquals(42L, ReportEtag.parseStrongSingle("\"AAAAAAAAACo\""));

        long maxVal = Long.MAX_VALUE;
        String maxTag = "\"" + ReportEtag.fromVersion(maxVal).getValue() + "\"";
        assertEquals(maxVal, ReportEtag.parseStrongSingle(maxTag));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 2L, 5L, 100L, 9999L, 1_000_000L, Long.MAX_VALUE})
    void round_trip_from_version_and_parse_strong_single(long version) {
        EntityTag tag = ReportEtag.fromVersion(version);
        long parsed = ReportEtag.parseStrongSingle("\"" + tag.getValue() + "\"");
        assertEquals(version, parsed);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "*",
        "W/\"AAAAAAAAAAA\"",
        "w/\"AAAAAAAAAAA\"",
        "\"AAAAAAAAAAA\", \"AAAAAAAAACo\"",
        "\"AAAAAAAAAAA\", \"BBBBBBBBBBB\"",
        "AAAAAAAAAAA",
        "\"AAAAAAAAAAA",
        "AAAAAAAAAAA\"",
        "\"\"",
        "\"AAAA\"",
        "\"AAAAAAAAAAAA\"",
        "\"AAAAAAAAAAA==\"",
        "\"invalid!@#$%\"",
        "\"__________8\"" // decodes to -1L (negative)
    })
    void parse_strong_single_rejects_invalid_headers(String invalidHeader) {
        assertThrows(InvalidReportRequestException.class,
            () -> ReportEtag.parseStrongSingle(invalidHeader),
            "Expected parseStrongSingle to reject: " + invalidHeader);
    }
}
