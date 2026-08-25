package dev.blackice.reports.infrastructure.dicomweb;

import dev.blackice.reports.application.input.ReportStudyRef;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportQidoQueryBuilderTest {

    private static final String BASE_URL = "http://127.0.0.1:8080/dcm4chee-arc/aets/DCM4CHEE/rs";
    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688416.741.100";

    @Test
    void maps_study_ref_to_deterministic_qido_query() {
        ReportStudyRef study = new ReportStudyRef(STUDY_UID);
        URI uri = new ReportQidoQueryBuilder().build(BASE_URL, study);

        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(8080, uri.getPort());
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uri.getPath());

        String rawQuery = uri.getRawQuery();
        assertEquals("StudyInstanceUID=" + STUDY_UID + "&limit=1&includefield=0020000D", rawQuery);

        String decodedQuery = URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
        assertTrue(decodedQuery.contains("StudyInstanceUID=" + STUDY_UID));
        assertTrue(decodedQuery.contains("limit=1"));
        assertTrue(decodedQuery.contains("includefield=0020000D"));
        assertFalse(decodedQuery.contains("includefield=all"));
    }

    @Test
    void handles_base_url_with_trailing_slash_or_existing_studies_path() {
        ReportStudyRef study = new ReportStudyRef(STUDY_UID);

        URI uriTrailingSlash = new ReportQidoQueryBuilder().build(BASE_URL + "/", study);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriTrailingSlash.getPath());

        URI uriWithStudies = new ReportQidoQueryBuilder().build(BASE_URL + "/studies", study);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriWithStudies.getPath());
    }

    @Test
    void preserves_exact_study_uid_representation() {
        String exactUid = "1.2.392.200036.9125.0.19951120.115";
        ReportStudyRef study = new ReportStudyRef(exactUid);

        URI uri = new ReportQidoQueryBuilder().build(BASE_URL, study);
        assertTrue(uri.getRawQuery().contains("StudyInstanceUID=" + exactUid));
    }

    @Test
    void rejects_null_and_blank_arguments() {
        ReportQidoQueryBuilder builder = new ReportQidoQueryBuilder();
        ReportStudyRef study = new ReportStudyRef(STUDY_UID);

        assertThrows(NullPointerException.class, () -> builder.build(null, study));
        assertThrows(NullPointerException.class, () -> builder.build(BASE_URL, null));
        assertThrows(IllegalArgumentException.class, () -> builder.build("   ", study));
        assertThrows(IllegalArgumentException.class, () -> builder.build("", study));
    }
}
