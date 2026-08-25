package dev.blackice.viewer.application.result;

/**
 * High-level clinical reason why a DICOM series is unsupported in this viewer.
 * Does not expose SOP Class UIDs or internal details.
 */
public enum UnsupportedReason {
    MULTI_FRAME,
    NON_IMAGE_OBJECT,
    IMAGE_SOP_CLASS_UNSUPPORTED
}
