package dev.blackice.worklist.infrastructure.dicomweb;

import dev.blackice.worklist.application.input.StudySearchRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QidoQueryBuilderTest {

    private static final String BASE_URL = "http://127.0.0.1:8080/dcm4chee-arc/aets/DCM4CHEE/rs";

    @Test
    void maps_curated_filters_to_bounded_ordered_qido_query() {
        StudySearchRequest request = new StudySearchRequest(
            "MARIA", "123", "ct", LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 22), 20, 40);

        URI uri = new QidoQueryBuilder().build(BASE_URL, request, 21);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(8080, uri.getPort());
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uri.getPath());

        assertTrue(query.contains("PatientName=MARIA*"));
        assertTrue(query.contains("PatientID=123"));
        assertTrue(query.contains("ModalitiesInStudy=CT"));
        assertTrue(query.contains("StudyDate=20260801-20260822"));
        assertTrue(query.contains("limit=21"));
        assertTrue(query.contains("offset=40"));
        assertTrue(query.contains("orderby=-StudyDate,-StudyTime,StudyInstanceUID"));
        assertTrue(query.contains("includefield=00100021,00081030,00201206,00201208"));
        assertFalse(query.contains("includefield=all"));
    }

    @Test
    void omits_empty_filters_and_encodes_open_date_ranges() {
        URI uri = new QidoQueryBuilder().build(BASE_URL,
            new StudySearchRequest(null, null, null, null, LocalDate.of(2026, 8, 22), 20, 0), 21);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertTrue(query.contains("StudyDate=-20260822"));
        assertFalse(query.contains("PatientName"));
        assertFalse(query.contains("PatientID"));
        assertFalse(query.contains("ModalitiesInStudy"));
    }

    @Test
    void encodes_open_start_date_range() {
        URI uri = new QidoQueryBuilder().build(BASE_URL,
            new StudySearchRequest(null, null, null, LocalDate.of(2026, 8, 1), null, 20, 0), 21);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertTrue(query.contains("StudyDate=20260801-"));
    }

    @Test
    void encodes_spaces_as_percent_20_in_raw_query() {
        StudySearchRequest request = new StudySearchRequest(
            "SILVA MARIA", null, null, null, null, 20, 0);
        URI uri = new QidoQueryBuilder().build(BASE_URL, request, 21);

        assertTrue(uri.getRawQuery().contains("PatientName=SILVA%20MARIA*"));
        assertFalse(uri.getRawQuery().contains("+"));
    }

    @Test
    void handles_base_url_with_trailing_slash_or_existing_studies_path() {
        StudySearchRequest request = new StudySearchRequest(null, null, null, null, null, 20, 0);

        URI uriTrailingSlash = new QidoQueryBuilder().build(BASE_URL + "/", request, 21);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriTrailingSlash.getPath());

        URI uriWithStudies = new QidoQueryBuilder().build(BASE_URL + "/studies", request, 21);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriWithStudies.getPath());
    }

    @Test
    void rejects_null_inputs_and_non_positive_limit() {
        QidoQueryBuilder builder = new QidoQueryBuilder();
        StudySearchRequest request = new StudySearchRequest(null, null, null, null, null, 20, 0);

        assertThrows(NullPointerException.class, () -> builder.build(null, request, 21));
        assertThrows(NullPointerException.class, () -> builder.build(BASE_URL, null, 21));
        assertThrows(IllegalArgumentException.class, () -> builder.build(BASE_URL, request, 0));
        assertThrows(IllegalArgumentException.class, () -> builder.build(BASE_URL, request, -1));
        assertThrows(IllegalArgumentException.class, () -> builder.build("   ", request, 21));
    }
}
