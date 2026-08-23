package dev.blackice.ingest.application.result;

/**
 * Outcome for an individual SOP Instance submitted to the DICOM archive via STOW-RS.
 *
 * @param sopInstanceUid exact SOP Instance UID of the submitted instance
 * @param status storage status determined from archive response sequences
 * @param reason DICOM warning or failure reason code, if provided by the archive
 */
public record StowInstanceResult(
    String sopInstanceUid,
    Status status,
    Integer reason
) {
    /**
     * Storage status returned or inferred for a submitted SOP instance.
     */
    public enum Status {
        /** Instance was successfully stored without warnings (present in Referenced SOP Sequence). */
        ACCEPTED,
        /** Instance was stored with warnings (present in Referenced SOP Sequence with Warning Reason). */
        WARNING,
        /** Instance was rejected by the archive (present in Failed SOP Sequence). */
        REJECTED,
        /** Instance was submitted but omitted from both response sequences. */
        UNCONFIRMED
    }
}
