package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.viewer.application.input.ViewerStudyRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QidoViewerQueryBuilderTest {

    private static final String BASE_URL = "http://127.0.0.1:8080/dcm4chee-arc/aets/DCM4CHEE/rs";
    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688416.741.1234567890.100";
    private static final ViewerStudyRef STUDY_REF = new ViewerStudyRef(STUDY_UID);

    private final QidoViewerQueryBuilder builder = new QidoViewerQueryBuilder();

    @Test
    void builds_study_query_uri_with_limit_and_includefield() {
        URI uri = builder.study(BASE_URL, STUDY_REF);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(8080, uri.getPort());
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uri.getPath());

        assertTrue(query.contains("StudyInstanceUID=" + STUDY_UID));
        assertTrue(query.contains("limit=2"));
        assertTrue(query.contains("includefield="));
        assertTrue(query.contains("00100021")); // IssuerOfPatientID
        assertTrue(query.contains("00080030")); // StudyTime
        assertTrue(query.contains("00081030")); // StudyDescription
        assertFalse(query.contains("includefield=all"));
    }

    @Test
    void builds_series_query_uri_with_includefield() {
        URI uri = builder.series(BASE_URL, STUDY_REF);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", uri.getPath());
        assertTrue(query.contains("includefield="));
        assertTrue(query.contains("0008103E")); // SeriesDescription
        assertTrue(query.contains("00201209")); // NumberOfSeriesRelatedInstances
        assertFalse(query.contains("includefield=all"));
    }

    @Test
    void builds_instances_query_uri_with_limit_offset_orderby_and_includefield() {
        URI uri = builder.instances(BASE_URL, STUDY_REF, 500, 0);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", uri.getPath());
        assertTrue(query.contains("limit=500"));
        assertTrue(query.contains("offset=0"));
        assertTrue(query.contains("orderby=SeriesInstanceUID,SOPInstanceUID"));
        assertTrue(query.contains("includefield="));
        assertTrue(query.contains("00280008")); // NumberOfFrames
        assertTrue(query.contains("0020000E")); // SeriesInstanceUID
        assertTrue(query.contains("00080016")); // SOPClassUID
        assertTrue(query.contains("00080018")); // SOPInstanceUID
        assertFalse(query.contains("includefield=all"));
    }

    @Test
    void instances_query_supports_pagination_offsets() {
        URI uri = builder.instances(BASE_URL, STUDY_REF, 500, 1000);
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);

        assertTrue(query.contains("limit=500"));
        assertTrue(query.contains("offset=1000"));
    }

    @Test
    void percent_encodes_special_characters_without_plus_signs() {
        URI uri = builder.instances(BASE_URL, STUDY_REF, 500, 0);
        String rawQuery = uri.getRawQuery();

        assertTrue(rawQuery.contains("orderby=SeriesInstanceUID%2CSOPInstanceUID"));
        assertFalse(rawQuery.contains("+"));
    }

    @Test
    void handles_base_url_with_trailing_slash_or_existing_studies_path() {
        URI uriTrailing = builder.study(BASE_URL + "/", STUDY_REF);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriTrailing.getPath());

        URI uriWithStudies = builder.study(BASE_URL + "/studies", STUDY_REF);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", uriWithStudies.getPath());

        URI seriesTrailing = builder.series(BASE_URL + "/", STUDY_REF);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", seriesTrailing.getPath());

        URI seriesWithStudies = builder.series(BASE_URL + "/studies", STUDY_REF);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", seriesWithStudies.getPath());

        URI instancesTrailing = builder.instances(BASE_URL + "/", STUDY_REF, 500, 0);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", instancesTrailing.getPath());

        URI instancesWithStudies = builder.instances(BASE_URL + "/studies", STUDY_REF, 500, 0);
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", instancesWithStudies.getPath());
    }

    @Test
    void rejects_null_and_blank_arguments() {
        assertThrows(NullPointerException.class, () -> builder.study(null, STUDY_REF));
        assertThrows(NullPointerException.class, () -> builder.study(BASE_URL, null));
        assertThrows(IllegalArgumentException.class, () -> builder.study("   ", STUDY_REF));

        assertThrows(NullPointerException.class, () -> builder.series(null, STUDY_REF));
        assertThrows(NullPointerException.class, () -> builder.series(BASE_URL, null));
        assertThrows(IllegalArgumentException.class, () -> builder.series("   ", STUDY_REF));

        assertThrows(NullPointerException.class, () -> builder.instances(null, STUDY_REF, 500, 0));
        assertThrows(NullPointerException.class, () -> builder.instances(BASE_URL, null, 500, 0));
        assertThrows(IllegalArgumentException.class, () -> builder.instances("   ", STUDY_REF, 500, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -500})
    void rejects_non_positive_limit(int limit) {
        assertThrows(IllegalArgumentException.class, () -> builder.instances(BASE_URL, STUDY_REF, limit, 0));
    }

    @Test
    void rejects_negative_offset() {
        assertThrows(IllegalArgumentException.class, () -> builder.instances(BASE_URL, STUDY_REF, 500, -1));
    }
}
