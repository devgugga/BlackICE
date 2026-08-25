package dev.blackice.reports.application.input;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportPayloadTooLargeException;

/**
 * Validated immutable report content in plain text.
 * Enforces non-empty non-whitespace content and a maximum of 32,000 Unicode code points.
 * Preserves exact text without trimming or normalization.
 */
public record ReportContent(String value) {

    public static final int MAX_CODE_POINTS = 32_000;

    public ReportContent {
        if (value == null) {
            throw new InvalidReportRequestException();
        }
        boolean hasNonWhitespace = false;
        int length = value.length();
        for (int i = 0; i < length; ) {
            int cp = value.codePointAt(i);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
                hasNonWhitespace = true;
                break;
            }
            i += Character.charCount(cp);
        }
        if (!hasNonWhitespace) {
            throw new InvalidReportRequestException();
        }
        int codePointCount = value.codePointCount(0, length);
        if (codePointCount > MAX_CODE_POINTS) {
            throw new ReportPayloadTooLargeException();
        }
    }
}
