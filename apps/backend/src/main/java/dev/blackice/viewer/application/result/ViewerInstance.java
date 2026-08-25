package dev.blackice.viewer.application.result;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import org.dcm4che3.util.UIDUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Curated metadata record for a single DICOM instance required for Cornerstone viewport rendering.
 * All arrays and collections are defensively copied to maintain immutability.
 */
public record ViewerInstance(
    String sopInstanceUid,
    String sopClassUid,
    Integer instanceNumber,
    Integer rows,
    Integer columns,
    Integer samplesPerPixel,
    String photometricInterpretation,
    Integer bitsAllocated,
    Integer bitsStored,
    Integer highBit,
    Integer pixelRepresentation,
    Integer planarConfiguration,
    double[] imagePositionPatient,
    double[] imageOrientationPatient,
    double[] pixelSpacing,
    String frameOfReferenceUid,
    Double rescaleIntercept,
    Double rescaleSlope,
    List<Double> windowCenter,
    List<Double> windowWidth
) {
    public ViewerInstance {
        if (sopInstanceUid == null || !UIDUtils.isValid(sopInstanceUid)) {
            throw new InvalidArchiveMetadataException("Invalid or missing sopInstanceUid");
        }
        if (sopClassUid == null || !UIDUtils.isValid(sopClassUid)) {
            throw new InvalidArchiveMetadataException("Invalid or missing sopClassUid");
        }
        if (rows == null || rows <= 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing rows");
        }
        if (columns == null || columns <= 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing columns");
        }
        if (samplesPerPixel == null || samplesPerPixel <= 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing samplesPerPixel");
        }
        if (photometricInterpretation == null || photometricInterpretation.isBlank()) {
            throw new InvalidArchiveMetadataException("Invalid or missing photometricInterpretation");
        }
        if (bitsAllocated == null || bitsAllocated <= 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing bitsAllocated");
        }
        if (bitsStored == null || bitsStored <= 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing bitsStored");
        }
        if (highBit == null || highBit < 0) {
            throw new InvalidArchiveMetadataException("Invalid or missing highBit");
        }
        if (pixelRepresentation == null || (pixelRepresentation != 0 && pixelRepresentation != 1)) {
            throw new InvalidArchiveMetadataException("Invalid or missing pixelRepresentation");
        }
        if (samplesPerPixel > 1 && planarConfiguration == null) {
            throw new InvalidArchiveMetadataException("PlanarConfiguration is required when SamplesPerPixel > 1");
        }
        if (frameOfReferenceUid != null && !UIDUtils.isValid(frameOfReferenceUid)) {
            throw new InvalidArchiveMetadataException("Invalid frameOfReferenceUid");
        }

        imagePositionPatient = imagePositionPatient != null ? imagePositionPatient.clone() : null;
        imageOrientationPatient = imageOrientationPatient != null ? imageOrientationPatient.clone() : null;
        pixelSpacing = pixelSpacing != null ? pixelSpacing.clone() : null;
        windowCenter = windowCenter != null ? List.copyOf(windowCenter) : null;
        windowWidth = windowWidth != null ? List.copyOf(windowWidth) : null;
    }

    @Override
    public double[] imagePositionPatient() {
        return imagePositionPatient != null ? imagePositionPatient.clone() : null;
    }

    @Override
    public double[] imageOrientationPatient() {
        return imageOrientationPatient != null ? imageOrientationPatient.clone() : null;
    }

    @Override
    public double[] pixelSpacing() {
        return pixelSpacing != null ? pixelSpacing.clone() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViewerInstance that)) return false;
        return Objects.equals(sopInstanceUid, that.sopInstanceUid)
            && Objects.equals(sopClassUid, that.sopClassUid)
            && Objects.equals(instanceNumber, that.instanceNumber)
            && Objects.equals(rows, that.rows)
            && Objects.equals(columns, that.columns)
            && Objects.equals(samplesPerPixel, that.samplesPerPixel)
            && Objects.equals(photometricInterpretation, that.photometricInterpretation)
            && Objects.equals(bitsAllocated, that.bitsAllocated)
            && Objects.equals(bitsStored, that.bitsStored)
            && Objects.equals(highBit, that.highBit)
            && Objects.equals(pixelRepresentation, that.pixelRepresentation)
            && Objects.equals(planarConfiguration, that.planarConfiguration)
            && Arrays.equals(imagePositionPatient, that.imagePositionPatient)
            && Arrays.equals(imageOrientationPatient, that.imageOrientationPatient)
            && Arrays.equals(pixelSpacing, that.pixelSpacing)
            && Objects.equals(frameOfReferenceUid, that.frameOfReferenceUid)
            && Objects.equals(rescaleIntercept, that.rescaleIntercept)
            && Objects.equals(rescaleSlope, that.rescaleSlope)
            && Objects.equals(windowCenter, that.windowCenter)
            && Objects.equals(windowWidth, that.windowWidth);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
            sopInstanceUid, sopClassUid, instanceNumber, rows, columns,
            samplesPerPixel, photometricInterpretation, bitsAllocated, bitsStored,
            highBit, pixelRepresentation, planarConfiguration, frameOfReferenceUid,
            rescaleIntercept, rescaleSlope, windowCenter, windowWidth
        );
        result = 31 * result + Arrays.hashCode(imagePositionPatient);
        result = 31 * result + Arrays.hashCode(imageOrientationPatient);
        result = 31 * result + Arrays.hashCode(pixelSpacing);
        return result;
    }
}
