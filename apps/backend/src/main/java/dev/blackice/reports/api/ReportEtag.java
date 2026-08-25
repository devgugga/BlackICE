package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import jakarta.ws.rs.core.EntityTag;

import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Handles strong opaque ETag generation and parsing for study reports.
 * Encodes the 8-byte version as base64url without padding inside a strong quoted tag.
 */
public final class ReportEtag {

    private static final int ENCODED_LENGTH = 11;

    private ReportEtag() {}

    /**
     * Creates a strong quoted EntityTag from a non-negative version.
     *
     * @param version the report version (must be >= 0)
     * @return the strong EntityTag
     */
    public static EntityTag fromVersion(long version) {
        if (version < 0) {
            throw new InvalidReportRequestException();
        }
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(version).array();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new EntityTag(encoded, false);
    }

    /**
     * Parses an opaque strong quoted ETag (e.g. from an If-Match header) and decodes its version.
     *
     * @param ifMatch the If-Match header value
     * @return the non-negative decoded version
     * @throws InvalidReportRequestException on null, blank, weak, wildcard, multi-value, or invalid formats
     */
    public static long parseStrongSingle(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new InvalidReportRequestException();
        }
        if (ifMatch.startsWith("W/") || ifMatch.startsWith("w/") || ifMatch.equals("*") || ifMatch.contains(",")) {
            throw new InvalidReportRequestException();
        }
        if (!ifMatch.startsWith("\"") || !ifMatch.endsWith("\"") || ifMatch.length() < 2) {
            throw new InvalidReportRequestException();
        }

        String inner = ifMatch.substring(1, ifMatch.length() - 1);
        if (inner.length() != ENCODED_LENGTH || inner.contains("\"") || inner.contains(" ")) {
            throw new InvalidReportRequestException();
        }

        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(inner);
        } catch (IllegalArgumentException e) {
            throw new InvalidReportRequestException("Malformed ETag", e);
        }

        if (bytes.length != Long.BYTES) {
            throw new InvalidReportRequestException();
        }

        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (!canonical.equals(inner)) {
            throw new InvalidReportRequestException();
        }

        long version = ByteBuffer.wrap(bytes).getLong();
        if (version < 0) {
            throw new InvalidReportRequestException();
        }

        return version;
    }
}
