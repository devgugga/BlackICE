package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.result.ViewerInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialInstanceOrderTest {

    private static final String SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";

    private SpatialInstanceOrder order;

    @BeforeEach
    void setUp() {
        order = new SpatialInstanceOrder();
    }

    @Test
    void sort_empty_and_single_element_lists() {
        assertTrue(order.sort(List.of()).isEmpty());

        ViewerInstance single = createInstance("1.2.3.1", 1, new double[]{0, 0, 0}, new double[]{1, 0, 0, 0, 1, 0});
        assertEquals(List.of(single), order.sort(List.of(single)));
    }

    @Test
    void sort_axial_slices_by_increasing_slice_normal_projection() {
        // Axial normal = [0, 0, 1]
        ViewerInstance sliceZ0 = createInstance("1.2.3.3", 3, new double[]{0, 0, 0.0}, new double[]{1, 0, 0, 0, 1, 0});
        ViewerInstance sliceZminus100 = createInstance("1.2.3.1", 1, new double[]{0, 0, -100.0}, new double[]{1, 0, 0, 0, 1, 0});
        ViewerInstance sliceZminus50 = createInstance("1.2.3.2", 2, new double[]{0, 0, -50.0}, new double[]{1, 0, 0, 0, 1, 0});

        List<ViewerInstance> unsorted = List.of(sliceZ0, sliceZminus100, sliceZminus50);
        List<ViewerInstance> sorted = order.sort(unsorted);

        assertEquals(
            List.of("1.2.3.1", "1.2.3.2", "1.2.3.3"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void sort_sagittal_slices_by_increasing_slice_normal_projection() {
        // Sagittal: row = [0, 1, 0], col = [0, 0, -1] -> normal = cross(row, col) = [-1, 0, 0]
        // dot([10, 0, 0], [-1, 0, 0]) = -10
        // dot([20, 0, 0], [-1, 0, 0]) = -20
        // dot([30, 0, 0], [-1, 0, 0]) = -30
        // Projected ascending order should be -30 (X=30), -20 (X=20), -10 (X=10)
        double[] sagittalIop = new double[]{0, 1, 0, 0, 0, -1};
        ViewerInstance sliceX10 = createInstance("1.2.3.1", 1, new double[]{10, 0, 0}, sagittalIop);
        ViewerInstance sliceX20 = createInstance("1.2.3.2", 2, new double[]{20, 0, 0}, sagittalIop);
        ViewerInstance sliceX30 = createInstance("1.2.3.3", 3, new double[]{30, 0, 0}, sagittalIop);

        List<ViewerInstance> sorted = order.sort(List.of(sliceX10, sliceX30, sliceX20));
        assertEquals(
            List.of("1.2.3.3", "1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_orientation_vectors_are_not_unit_length() {
        double[] nonUnit = new double[]{2, 0, 0, 0, 3, 0};
        ViewerInstance projectedFirst = createInstance(
            "1.2.3.1", 2, new double[]{0, 0, -10}, nonUnit);
        ViewerInstance fallbackFirst = createInstance(
            "1.2.3.2", 1, new double[]{0, 0, 10}, nonUnit);

        List<ViewerInstance> sorted = order.sort(List.of(projectedFirst, fallbackFirst));
        assertEquals(
            List.of("1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void sort_tie_breaks_equal_positions_by_instance_number_then_sop_uid() {
        double[] iop = new double[]{1, 0, 0, 0, 1, 0};
        // Same projection Z = 0
        ViewerInstance img1 = createInstance("1.2.3.2", 10, new double[]{0, 0, 0}, iop);
        ViewerInstance img2 = createInstance("1.2.3.1", 10, new double[]{5, 5, 0}, iop);
        ViewerInstance img3 = createInstance("1.2.3.3", 5, new double[]{10, 10, 0}, iop);
        ViewerInstance img4 = createInstance("1.2.3.4", null, new double[]{2, 2, 0}, iop);

        List<ViewerInstance> sorted = order.sort(List.of(img1, img4, img2, img3));
        assertEquals(
            List.of("1.2.3.3", "1.2.3.1", "1.2.3.2", "1.2.3.4"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_instance_is_missing_geometry() {
        double[] iop = new double[]{1, 0, 0, 0, 1, 0};
        ViewerInstance withGeo = createInstance("1.2.3.1", 3, new double[]{0, 0, -100}, iop);
        ViewerInstance missingIpp = createInstance("1.2.3.2", 1, null, iop);
        ViewerInstance missingIop = createInstance("1.2.3.3", 2, new double[]{0, 0, 0}, null);

        List<ViewerInstance> sorted = order.sort(List.of(withGeo, missingIpp, missingIop));
        // Complete fallback: sorted by instanceNumber ascending (nulls last) then SOP UID
        assertEquals(
            List.of("1.2.3.2", "1.2.3.3", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_vectors_are_not_orthogonal() {
        // dot(row, col) = 1.0 * 0.5 = 0.5 > 1e-4
        double[] nonOrthogonalIop = new double[]{1, 0, 0, 0.5, 1, 0};
        ViewerInstance img1 = createInstance("1.2.3.1", 2, new double[]{0, 0, 10}, nonOrthogonalIop);
        ViewerInstance img2 = createInstance("1.2.3.2", 1, new double[]{0, 0, 20}, nonOrthogonalIop);

        List<ViewerInstance> sorted = order.sort(List.of(img1, img2));
        // Fallback sorts by instanceNumber: img2 (1), then img1 (2)
        assertEquals(
            List.of("1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_orientation_diverges_across_instances() {
        // First slice is axial, second slice is coronal (e.g. scout slice)
        double[] axial = new double[]{1, 0, 0, 0, 1, 0};
        double[] coronal = new double[]{1, 0, 0, 0, 0, -1};

        ViewerInstance sliceAxial = createInstance("1.2.3.1", 2, new double[]{0, 0, 100}, axial);
        ViewerInstance sliceScout = createInstance("1.2.3.2", 1, new double[]{0, 0, 0}, coronal);

        List<ViewerInstance> sorted = order.sort(List.of(sliceAxial, sliceScout));
        // Fallback sorts by instanceNumber: sliceScout (1), sliceAxial (2)
        assertEquals(
            List.of("1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_vectors_contain_nan_or_infinity() {
        double[] iop = new double[]{1, 0, 0, 0, 1, 0};
        ViewerInstance imgNaN = createInstance("1.2.3.1", 2, new double[]{0, 0, Double.NaN}, iop);
        ViewerInstance imgNormal = createInstance("1.2.3.2", 1, new double[]{0, 0, 0}, iop);

        List<ViewerInstance> sorted = order.sort(List.of(imgNaN, imgNormal));
        assertEquals(
            List.of("1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_when_vectors_have_near_zero_norm() {
        double[] zeroRow = new double[]{0, 0, 0, 0, 1, 0};
        ViewerInstance imgZero = createInstance("1.2.3.1", 2, new double[]{0, 0, 0}, zeroRow);
        ViewerInstance imgNormal = createInstance("1.2.3.2", 1, new double[]{0, 0, 10}, new double[]{1, 0, 0, 0, 1, 0});

        List<ViewerInstance> sorted = order.sort(List.of(imgZero, imgNormal));
        assertEquals(
            List.of("1.2.3.2", "1.2.3.1"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void fallback_ordering_null_instance_numbers_and_sop_uid_tie_break() {
        ViewerInstance img1 = createInstance("1.2.3.2", null, null, null);
        ViewerInstance img2 = createInstance("1.2.3.1", null, null, null);
        ViewerInstance img3 = createInstance("1.2.3.9", 1, null, null);

        List<ViewerInstance> sorted = order.sort(List.of(img1, img2, img3));
        assertEquals(
            List.of("1.2.3.9", "1.2.3.1", "1.2.3.2"),
            sorted.stream().map(ViewerInstance::sopInstanceUid).toList()
        );
    }

    @Test
    void sort_throws_null_pointer_exception_for_null_input() {
        assertThrows(NullPointerException.class, () -> order.sort(null));
    }

    private ViewerInstance createInstance(String sopInstanceUid, Integer instanceNumber, double[] ipp, double[] iop) {
        return new ViewerInstance(
            sopInstanceUid,
            SOP_CLASS,
            instanceNumber,
            512,
            512,
            1,
            "MONOCHROME2",
            16,
            12,
            11,
            1,
            null,
            ipp,
            iop,
            new double[]{0.5, 0.5},
            null,
            null,
            null,
            null,
            null
        );
    }
}
