package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.result.ViewerInstance;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Domain service sorting DICOM instances along their canonical slice normal,
 * with deterministic fallback to instance number and SOP instance UID if spatial geometry is incomplete or invalid.
 */
@ApplicationScoped
public class SpatialInstanceOrder {

    private static final double EPSILON = 1e-4;
    private static final double MIN_VECTOR_NORM = 1e-6;

    private static final Comparator<ViewerInstance> FALLBACK_COMPARATOR = Comparator
        .comparing(ViewerInstance::instanceNumber, Comparator.nullsLast(Integer::compareTo))
        .thenComparing(ViewerInstance::sopInstanceUid);

    /**
     * Sorts a list of viewer instances spatially by slice normal projection if geometry is valid,
     * or falls back to instance number and SOP instance UID ordering.
     *
     * @param instances list of viewer instances
     * @return immutable sorted list of instances
     */
    public List<ViewerInstance> sort(List<ViewerInstance> instances) {
        Objects.requireNonNull(instances, "instances must not be null");
        if (instances.size() <= 1) {
            return List.copyOf(instances);
        }

        // Validate geometry across all instances
        List<InstanceGeometry> geometries = new ArrayList<>(instances.size());
        for (ViewerInstance instance : instances) {
            InstanceGeometry geom = validateGeometry(instance);
            if (geom == null) {
                return fallbackSort(instances);
            }
            geometries.add(geom);
        }

        // Use the first instance's normalized cross product as canonical normal
        InstanceGeometry firstGeom = geometries.getFirst();
        double[] canonicalNormal = crossProduct(firstGeom.normalizedRow, firstGeom.normalizedCol);
        double normalNorm = norm(canonicalNormal);
        if (normalNorm <= MIN_VECTOR_NORM) {
            return fallbackSort(instances);
        }
        canonicalNormal = normalize(canonicalNormal, normalNorm);

        // Verify orientation consistency with the first instance within 1e-4 tolerance
        for (int i = 1; i < geometries.size(); i++) {
            InstanceGeometry geom = geometries.get(i);
            if (!isOrientationConsistent(firstGeom, geom)) {
                return fallbackSort(instances);
            }
        }

        // Compute projected position along canonical normal for each instance
        List<ProjectedInstance> projected = new ArrayList<>(instances.size());
        for (int i = 0; i < instances.size(); i++) {
            ViewerInstance inst = instances.get(i);
            InstanceGeometry geom = geometries.get(i);
            double proj = dotProduct(geom.position, canonicalNormal);
            if (!Double.isFinite(proj)) {
                return fallbackSort(instances);
            }
            projected.add(new ProjectedInstance(inst, proj));
        }

        // Sort by projected position ascending, tie-break by instanceNumber (nulls-last) and sopInstanceUid
        projected.sort(Comparator
            .comparingDouble(ProjectedInstance::projection)
            .thenComparing(pi -> pi.instance().instanceNumber(), Comparator.nullsLast(Integer::compareTo))
            .thenComparing(pi -> pi.instance().sopInstanceUid())
        );

        List<ViewerInstance> result = new ArrayList<>(projected.size());
        for (ProjectedInstance pi : projected) {
            result.add(pi.instance());
        }
        return List.copyOf(result);
    }

    private List<ViewerInstance> fallbackSort(List<ViewerInstance> instances) {
        List<ViewerInstance> copy = new ArrayList<>(instances);
        copy.sort(FALLBACK_COMPARATOR);
        return List.copyOf(copy);
    }

    private InstanceGeometry validateGeometry(ViewerInstance instance) {
        double[] ipp = instance.imagePositionPatient();
        double[] iop = instance.imageOrientationPatient();

        if (ipp == null || ipp.length != 3 || iop == null || iop.length != 6) {
            return null;
        }

        for (double v : ipp) {
            if (!Double.isFinite(v)) {
                return null;
            }
        }
        for (double v : iop) {
            if (!Double.isFinite(v)) {
                return null;
            }
        }

        double[] row = new double[]{iop[0], iop[1], iop[2]};
        double[] col = new double[]{iop[3], iop[4], iop[5]};

        double rowNorm = norm(row);
        double colNorm = norm(col);

        if (rowNorm <= MIN_VECTOR_NORM || colNorm <= MIN_VECTOR_NORM) {
            return null;
        }

        double[] normRow = normalize(row, rowNorm);
        double[] normCol = normalize(col, colNorm);

        // Check orthogonality: abs(dot(normRow, normCol)) <= 1e-4
        if (Math.abs(dotProduct(normRow, normCol)) > EPSILON) {
            return null;
        }

        return new InstanceGeometry(ipp, normRow, normCol);
    }

    private boolean isOrientationConsistent(InstanceGeometry first, InstanceGeometry other) {
        double rowDist = vectorDistance(first.normalizedRow, other.normalizedRow);
        double colDist = vectorDistance(first.normalizedCol, other.normalizedCol);
        return rowDist <= EPSILON && colDist <= EPSILON;
    }

    private static double norm(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double[] normalize(double[] v, double norm) {
        return new double[]{v[0] / norm, v[1] / norm, v[2] / norm};
    }

    private static double dotProduct(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] crossProduct(double[] a, double[] b) {
        return new double[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double vectorDistance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record InstanceGeometry(double[] position, double[] normalizedRow, double[] normalizedCol) {}
    private record ProjectedInstance(ViewerInstance instance, double projection) {}
}
