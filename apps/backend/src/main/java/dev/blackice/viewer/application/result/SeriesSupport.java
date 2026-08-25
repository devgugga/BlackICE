package dev.blackice.viewer.application.result;

import java.util.Objects;

/**
 * Support status and optional reason for a DICOM series.
 */
public record SeriesSupport(
    SeriesAvailability availability,
    UnsupportedReason unsupportedReason
) {
    public SeriesSupport {
        Objects.requireNonNull(availability, "availability must not be null");
        if (availability == SeriesAvailability.SUPPORTED && unsupportedReason != null) {
            throw new IllegalArgumentException("supported series must not have an unsupported reason");
        }
        if (availability == SeriesAvailability.UNSUPPORTED && unsupportedReason == null) {
            throw new IllegalArgumentException("unsupported series must have an unsupported reason");
        }
    }

    public static SeriesSupport supported() {
        return new SeriesSupport(SeriesAvailability.SUPPORTED, null);
    }

    public static SeriesSupport unsupported(UnsupportedReason reason) {
        return new SeriesSupport(SeriesAvailability.UNSUPPORTED, Objects.requireNonNull(reason, "reason must not be null"));
    }
}
