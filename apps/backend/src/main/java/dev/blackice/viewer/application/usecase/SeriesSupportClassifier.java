package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesSupport;
import dev.blackice.viewer.application.result.UnsupportedReason;
import jakarta.enterprise.context.ApplicationScoped;
import org.dcm4che3.util.UIDUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rule engine classifying a DICOM series based on all its instance identities.
 * Evaluates SOP Class allowlist and multi-frame constraints without branching on modality.
 */
@ApplicationScoped
public class SeriesSupportClassifier {

    private static final Set<String> SUPPORTED_IMAGE_SOP_CLASSES = Set.of(
        "1.2.840.10008.5.1.4.1.1.1",     // Computed Radiography Image Storage
        "1.2.840.10008.5.1.4.1.1.1.1",   // Digital X-Ray Image Storage - For Presentation
        "1.2.840.10008.5.1.4.1.1.2",     // CT Image Storage
        "1.2.840.10008.5.1.4.1.1.4"      // MR Image Storage
    );

    private static final Set<String> NON_IMAGE_EXACT_SOP_CLASSES = Set.of(
        "1.2.840.10008.5.1.4.1.1.66.1",  // Spatial Registration
        "1.2.840.10008.5.1.4.1.1.66.2",  // Spatial Fiducials
        "1.2.840.10008.5.1.4.1.1.66.3",  // Deformable Spatial Registration
        "1.2.840.10008.5.1.4.1.1.66.4",  // Segmentation Storage
        "1.2.840.10008.5.1.4.1.1.66.5",  // Surface Segmentation Storage
        "1.2.840.10008.5.1.4.1.1.66.6",  // Tractography Results
        "1.2.840.10008.5.1.4.1.1.67",    // Real World Value Mapping
        "1.2.840.10008.5.1.4.1.1.68.1",  // Surface Scan Registration
        "1.2.840.10008.5.1.4.1.1.78.1",  // Spectacle Prescription Report
        "1.2.840.10008.5.1.4.1.1.79.1",  // Macular Grid Thickness and Volume Report
        "1.2.840.10008.5.1.4.1.1.90.1",  // Content Assessment Result
        "1.2.840.10008.5.1.4.1.1.481.3", // RT Structure Set
        "1.2.840.10008.5.1.4.1.1.481.4", // RT Beams Treatment Record
        "1.2.840.10008.5.1.4.1.1.481.5", // RT Plan
        "1.2.840.10008.5.1.4.1.1.481.6", // RT Brachy Treatment Record
        "1.2.840.10008.5.1.4.1.1.481.7", // RT Treatment Summary Record
        "1.2.840.10008.5.1.4.1.1.481.8", // RT Ion Plan
        "1.2.840.10008.5.1.4.1.1.481.9"  // RT Ion Beams Treatment Record
    );

    private static final List<String> NON_IMAGE_PREFIXES = List.of(
        "1.2.840.10008.5.1.4.1.1.88.",   // Structured Reporting & KO
        "1.2.840.10008.5.1.4.1.1.11.",   // Presentation State
        "1.2.840.10008.5.1.4.1.1.104.",  // Encapsulated Documents (PDF, CDA, STL, OBJ, MTL)
        "1.2.840.10008.5.1.4.1.1.9."     // Waveform / ECG
    );

    /**
     * Classifies a series based on all its instance identities according to deterministic precedence:
     * 1. Multi-frame (numberOfFrames > 1) -> MULTI_FRAME
     * 2. Non-image object (SR, PR, SEG, PDF, etc.) -> NON_IMAGE_OBJECT
     * 3. Unsupported image SOP class -> IMAGE_SOP_CLASS_UNSUPPORTED
     * 4. Fully allowlisted single-frame -> SUPPORTED
     *
     * @param instances list of instance identities in the series
     * @return series support result
     * @throws InvalidArchiveMetadataException if instance metadata is missing, malformed, or empty
     */
    public SeriesSupport classify(List<InstanceIdentityMetadata> instances) {
        if (instances == null || instances.isEmpty()) {
            throw new InvalidArchiveMetadataException("Empty or null instances list for series");
        }

        Set<String> seenSopUids = new HashSet<>();
        boolean hasMultiFrame = false;
        boolean hasNonImageObject = false;
        boolean hasUnsupportedImageSopClass = false;

        for (InstanceIdentityMetadata instance : instances) {
            if (instance == null) {
                throw new InvalidArchiveMetadataException("Null instance in series");
            }
            if (instance.seriesInstanceUid() == null || !UIDUtils.isValid(instance.seriesInstanceUid())) {
                throw new InvalidArchiveMetadataException("Invalid seriesInstanceUid in instance metadata");
            }
            if (instance.sopInstanceUid() == null || !UIDUtils.isValid(instance.sopInstanceUid())) {
                throw new InvalidArchiveMetadataException("Invalid sopInstanceUid in instance metadata");
            }
            if (instance.sopClassUid() == null || !UIDUtils.isValid(instance.sopClassUid())) {
                throw new InvalidArchiveMetadataException("Invalid sopClassUid in instance metadata");
            }
            if (!seenSopUids.add(instance.sopInstanceUid())) {
                throw new InvalidArchiveMetadataException("Duplicate sopInstanceUid in series");
            }

            Integer numberOfFrames = instance.numberOfFrames();
            if (numberOfFrames != null && numberOfFrames <= 0) {
                throw new InvalidArchiveMetadataException("Invalid numberOfFrames <= 0");
            }

            if (numberOfFrames != null && numberOfFrames > 1) {
                hasMultiFrame = true;
            } else if (isNonImageSopClass(instance.sopClassUid())) {
                hasNonImageObject = true;
            } else if (!SUPPORTED_IMAGE_SOP_CLASSES.contains(instance.sopClassUid())) {
                hasUnsupportedImageSopClass = true;
            }
        }

        // Apply strict deterministic precedence
        if (hasMultiFrame) {
            return SeriesSupport.unsupported(UnsupportedReason.MULTI_FRAME);
        }
        if (hasNonImageObject) {
            return SeriesSupport.unsupported(UnsupportedReason.NON_IMAGE_OBJECT);
        }
        if (hasUnsupportedImageSopClass) {
            return SeriesSupport.unsupported(UnsupportedReason.IMAGE_SOP_CLASS_UNSUPPORTED);
        }
        return SeriesSupport.supported();
    }

    private static boolean isNonImageSopClass(String sopClassUid) {
        if (NON_IMAGE_EXACT_SOP_CLASSES.contains(sopClassUid)) {
            return true;
        }
        for (String prefix : NON_IMAGE_PREFIXES) {
            if (sopClassUid.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
