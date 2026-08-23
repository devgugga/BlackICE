package dev.blackice.ingest.api;

import dev.blackice.ingest.application.result.IngestResult;

/** Maps the transport-independent ingest outcome to the established HTTP contract. */
public final class IngestHttpStatusResolver {

    private IngestHttpStatusResolver() {}

    public static int resolve(IngestResult result) {
        IngestResult.Summary summary = result.summary();
        if (summary.received() > 0 && summary.locallyValid() == 0) {
            return 422;
        }
        if (summary.locallyValid() > 0
            && summary.archiveAccepted() == 0
            && !result.studies().isEmpty()
            && result.studies().stream().allMatch(IngestHttpStatusResolver::isArchiveUnavailable)) {
            return 503;
        }
        return 200;
    }

    private static boolean isArchiveUnavailable(IngestResult.StudyResult study) {
        return "ARCHIVE_UNAVAILABLE".equals(study.errorCode());
    }
}
