package dev.blackice.viewer.application.input;

import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewerRefsTest {

    private static final String VALID_STUDY_UID = "1.2.840.10008.1.2";
    private static final String VALID_SERIES_UID = "1.2.840.10008.1.2.1";
    private static final String VALID_SOP_UID = "1.2.840.10008.1.2.1.1";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        " ",
        "1..2",
        "1.02.3",
        "abc",
        "1.2.",
        ".1.2",
        "1.2.840.10008.1.2 ",
        " 1.2.840.10008.1.2",
        "1.2.840.113619.2.55.3.604688435.123.1599720123.467.12345678901234567890" // > 64 chars
    })
    @DisplayName("rejects invalid study UIDs without normalizing them")
    void rejects_invalid_study_uids_without_normalizing_them(String uid) {
        InvalidViewerRequestException ex = assertThrows(
            InvalidViewerRequestException.class,
            () -> new ViewerStudyRef(uid)
        );
        assertEquals("INVALID_VIEWER_REQUEST", ex.getMessage());
        if (uid != null && !uid.isEmpty()) {
            assertFalse(ex.getMessage().contains(uid));
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1..2", "1.02.3", "abc", "1.2."})
    @DisplayName("rejects invalid series UIDs in ViewerSeriesRef")
    void rejects_invalid_series_uids_in_viewer_series_ref(String uid) {
        assertThrows(InvalidViewerRequestException.class, () -> new ViewerSeriesRef(VALID_STUDY_UID, uid));
        assertThrows(InvalidViewerRequestException.class, () -> new ViewerSeriesRef(uid, VALID_SERIES_UID));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1..2", "1.02.3", "abc", "1.2."})
    @DisplayName("rejects invalid SOP instance UIDs in ViewerInstanceRef")
    void rejects_invalid_sop_uids_in_viewer_instance_ref(String uid) {
        assertThrows(InvalidViewerRequestException.class, () -> new ViewerInstanceRef(VALID_STUDY_UID, VALID_SERIES_UID, uid));
        assertThrows(InvalidViewerRequestException.class, () -> new ViewerInstanceRef(VALID_STUDY_UID, uid, VALID_SOP_UID));
        assertThrows(InvalidViewerRequestException.class, () -> new ViewerInstanceRef(uid, VALID_SERIES_UID, VALID_SOP_UID));
    }

    @Test
    @DisplayName("preserves valid UID exactly without mutation or trimming")
    void preserves_valid_uid_exactly() {
        ViewerStudyRef studyRef = new ViewerStudyRef(VALID_STUDY_UID);
        assertEquals(VALID_STUDY_UID, studyRef.studyInstanceUid());

        ViewerSeriesRef seriesRef = new ViewerSeriesRef(VALID_STUDY_UID, VALID_SERIES_UID);
        assertEquals(VALID_STUDY_UID, seriesRef.studyInstanceUid());
        assertEquals(VALID_SERIES_UID, seriesRef.seriesInstanceUid());

        ViewerInstanceRef instanceRef = new ViewerInstanceRef(VALID_STUDY_UID, VALID_SERIES_UID, VALID_SOP_UID);
        assertEquals(VALID_STUDY_UID, instanceRef.studyInstanceUid());
        assertEquals(VALID_SERIES_UID, instanceRef.seriesInstanceUid());
        assertEquals(VALID_SOP_UID, instanceRef.sopInstanceUid());
    }
}
