package dev.blackice.worklist.api;

/**
 * Stable error payload for worklist query failures.
 *
 * @param code the machine-readable error code
 * @param message the user-facing explanation
 */
public record WorklistErrorResponse(String code, String message) {}
