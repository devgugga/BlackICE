package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesAvailability;
import dev.blackice.viewer.application.result.SeriesSupport;
import dev.blackice.viewer.application.result.UnsupportedReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeriesSupportClassifierTest {

    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.124";
    private static final String SOP_UID_1 = "1.2.840.113619.2.55.3.604688435.124.1";
    private static final String SOP_UID_2 = "1.2.840.113619.2.55.3.604688435.124.2";

    // 4 allowlisted SOP Classes
    private static final String CR_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.1";
    private static final String DX_PRESENTATION_STORAGE = "1.2.840.10008.5.1.4.1.1.1.1";
    private static final String CT_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.2";
    private static final String MR_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.4";

    // Non-image SOP Classes
    private static final String BASIC_TEXT_SR = "1.2.840.10008.5.1.4.1.1.88.11";
    private static final String ENHANCED_SR = "1.2.840.10008.5.1.4.1.1.88.22";
    private static final String COMPREHENSIVE_SR = "1.2.840.10008.5.1.4.1.1.88.33";
    private static final String KEY_OBJECT_SELECTION = "1.2.840.10008.5.1.4.1.1.88.59";
    private static final String GRAYSCALE_PRESENTATION_STATE = "1.2.840.10008.5.1.4.1.1.11.1";
    private static final String SEGMENTATION_STORAGE = "1.2.840.10008.5.1.4.1.1.66.4";
    private static final String ENCAPSULATED_PDF = "1.2.840.10008.5.1.4.1.1.104.1";
    private static final String ECG_12_LEAD = "1.2.840.10008.5.1.4.1.1.9.1.1";

    // Image SOP Classes outside allowlist
    private static final String SECONDARY_CAPTURE = "1.2.840.10008.5.1.4.1.1.7";
    private static final String ULTRASOUND_IMAGE = "1.2.840.10008.5.1.4.1.1.6.1";
    private static final String DX_PROCESSING_STORAGE = "1.2.840.10008.5.1.4.1.1.1.1.1";
    private static final String NUCLEAR_MEDICINE = "1.2.840.10008.5.1.4.1.1.20";

    private SeriesSupportClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new SeriesSupportClassifier();
    }

    private InstanceIdentityMetadata instance(String sopUid, String sopClassUid, Integer frames) {
        return new InstanceIdentityMetadata(SERIES_UID, sopUid, sopClassUid, frames);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        CR_IMAGE_STORAGE,
        DX_PRESENTATION_STORAGE,
        CT_IMAGE_STORAGE,
        MR_IMAGE_STORAGE
    })
    @DisplayName("accepts each of the four allowlisted SOP classes for single frame series")
    void accepts_allowlisted_sop_classes(String sopClassUid) {
        SeriesSupport support1 = classifier.classify(List.of(instance(SOP_UID_1, sopClassUid, 1)));
        assertEquals(SeriesAvailability.SUPPORTED, support1.availability());
        assertNull(support1.unsupportedReason());

        SeriesSupport supportNullFrames = classifier.classify(List.of(instance(SOP_UID_1, sopClassUid, null)));
        assertEquals(SeriesAvailability.SUPPORTED, supportNullFrames.availability());
        assertNull(supportNullFrames.unsupportedReason());
    }

    @Test
    @DisplayName("classifies series with numberOfFrames > 1 as MULTI_FRAME")
    void classifies_multi_frame_series() {
        SeriesSupport support2 = classifier.classify(List.of(instance(SOP_UID_1, CT_IMAGE_STORAGE, 2)));
        assertEquals(SeriesAvailability.UNSUPPORTED, support2.availability());
        assertEquals(UnsupportedReason.MULTI_FRAME, support2.unsupportedReason());

        SeriesSupport support100 = classifier.classify(List.of(instance(SOP_UID_1, MR_IMAGE_STORAGE, 100)));
        assertEquals(SeriesAvailability.UNSUPPORTED, support100.availability());
        assertEquals(UnsupportedReason.MULTI_FRAME, support100.unsupportedReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        BASIC_TEXT_SR,
        ENHANCED_SR,
        COMPREHENSIVE_SR,
        KEY_OBJECT_SELECTION,
        GRAYSCALE_PRESENTATION_STATE,
        SEGMENTATION_STORAGE,
        ENCAPSULATED_PDF,
        ECG_12_LEAD
    })
    @DisplayName("classifies non-image SOP classes as NON_IMAGE_OBJECT")
    void classifies_non_image_sop_classes(String sopClassUid) {
        SeriesSupport support = classifier.classify(List.of(instance(SOP_UID_1, sopClassUid, 1)));
        assertEquals(SeriesAvailability.UNSUPPORTED, support.availability());
        assertEquals(UnsupportedReason.NON_IMAGE_OBJECT, support.unsupportedReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        SECONDARY_CAPTURE,
        ULTRASOUND_IMAGE,
        DX_PROCESSING_STORAGE,
        NUCLEAR_MEDICINE
    })
    @DisplayName("classifies other image SOP classes outside allowlist as IMAGE_SOP_CLASS_UNSUPPORTED")
    void classifies_unsupported_image_sop_classes(String sopClassUid) {
        SeriesSupport support = classifier.classify(List.of(instance(SOP_UID_1, sopClassUid, 1)));
        assertEquals(SeriesAvailability.UNSUPPORTED, support.availability());
        assertEquals(UnsupportedReason.IMAGE_SOP_CLASS_UNSUPPORTED, support.unsupportedReason());
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException when numberOfFrames is <= 0")
    void rejects_invalid_number_of_frames() {
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(instance(SOP_UID_1, CT_IMAGE_STORAGE, 0))));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(instance(SOP_UID_1, CT_IMAGE_STORAGE, -1))));
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException for empty or null instances list")
    void rejects_empty_or_null_instances() {
        assertThrows(InvalidArchiveMetadataException.class, () -> classifier.classify(List.of()));
        assertThrows(InvalidArchiveMetadataException.class, () -> classifier.classify(null));
    }

    @Test
    @DisplayName("throws InvalidArchiveMetadataException for invalid instance metadata")
    void rejects_invalid_instance_metadata() {
        // null element
        List<InstanceIdentityMetadata> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        assertThrows(InvalidArchiveMetadataException.class, () -> classifier.classify(listWithNull));

        // invalid SOP Class UID
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(instance(SOP_UID_1, "not-a-valid-uid", 1))));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(new InstanceIdentityMetadata(SERIES_UID, SOP_UID_1, null, 1))));

        // invalid SOP Instance UID
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(new InstanceIdentityMetadata(SERIES_UID, "invalid..uid", CT_IMAGE_STORAGE, 1))));

        // invalid Series Instance UID
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(new InstanceIdentityMetadata("invalid.uid", SOP_UID_1, CT_IMAGE_STORAGE, 1))));

        // duplicate SOP Instance UID
        assertThrows(InvalidArchiveMetadataException.class,
            () -> classifier.classify(List.of(
                instance(SOP_UID_1, CT_IMAGE_STORAGE, 1),
                instance(SOP_UID_1, CT_IMAGE_STORAGE, 1)
            )));
    }

    @Test
    @DisplayName("respects strict precedence across mixed instances in the same series")
    void respects_precedence_rules() {
        // 1. MULTI_FRAME beats NON_IMAGE_OBJECT and IMAGE_SOP_CLASS_UNSUPPORTED
        SeriesSupport multiAndSr = classifier.classify(List.of(
            instance(SOP_UID_1, CT_IMAGE_STORAGE, 2),
            instance(SOP_UID_2, BASIC_TEXT_SR, 1)
        ));
        assertEquals(UnsupportedReason.MULTI_FRAME, multiAndSr.unsupportedReason());

        SeriesSupport multiAndSc = classifier.classify(List.of(
            instance(SOP_UID_1, CT_IMAGE_STORAGE, 5),
            instance(SOP_UID_2, SECONDARY_CAPTURE, 1)
        ));
        assertEquals(UnsupportedReason.MULTI_FRAME, multiAndSc.unsupportedReason());

        // 2. NON_IMAGE_OBJECT beats IMAGE_SOP_CLASS_UNSUPPORTED and SUPPORTED
        SeriesSupport srAndSc = classifier.classify(List.of(
            instance(SOP_UID_1, BASIC_TEXT_SR, 1),
            instance(SOP_UID_2, SECONDARY_CAPTURE, 1)
        ));
        assertEquals(UnsupportedReason.NON_IMAGE_OBJECT, srAndSc.unsupportedReason());

        SeriesSupport srAndCt = classifier.classify(List.of(
            instance(SOP_UID_1, BASIC_TEXT_SR, 1),
            instance(SOP_UID_2, CT_IMAGE_STORAGE, 1)
        ));
        assertEquals(UnsupportedReason.NON_IMAGE_OBJECT, srAndCt.unsupportedReason());

        // 3. IMAGE_SOP_CLASS_UNSUPPORTED beats SUPPORTED
        SeriesSupport scAndCt = classifier.classify(List.of(
            instance(SOP_UID_1, SECONDARY_CAPTURE, 1),
            instance(SOP_UID_2, CT_IMAGE_STORAGE, 1)
        ));
        assertEquals(UnsupportedReason.IMAGE_SOP_CLASS_UNSUPPORTED, scAndCt.unsupportedReason());

        // 4. All allowlisted single-frame instances yield SUPPORTED
        SeriesSupport allCt = classifier.classify(List.of(
            instance(SOP_UID_1, CT_IMAGE_STORAGE, 1),
            instance(SOP_UID_2, CT_IMAGE_STORAGE, 1)
        ));
        assertEquals(SeriesAvailability.SUPPORTED, allCt.availability());
        assertNull(allCt.unsupportedReason());
    }

    @Test
    @DisplayName("is order-independent when classifying mixed instances")
    void order_independent_classification() {
        List<InstanceIdentityMetadata> instances = new ArrayList<>(List.of(
            instance(SOP_UID_1, CT_IMAGE_STORAGE, 1),
            instance(SOP_UID_2, BASIC_TEXT_SR, 1)
        ));
        SeriesSupport s1 = classifier.classify(instances);
        Collections.reverse(instances);
        SeriesSupport s2 = classifier.classify(instances);

        assertEquals(s1.availability(), s2.availability());
        assertEquals(s1.unsupportedReason(), s2.unsupportedReason());
    }
}
