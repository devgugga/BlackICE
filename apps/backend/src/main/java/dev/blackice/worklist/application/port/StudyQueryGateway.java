package dev.blackice.worklist.application.port;

import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.result.StudySummary;

import java.util.List;

/**
 * Port for querying study records from the external DICOM archive.
 */
public interface StudyQueryGateway {

    /**
     * Searches studies in the DICOM archive matching the given criteria.
     *
     * @param request the search criteria
     * @param fetchLimit the maximum number of studies to fetch from the archive (typically limit + 1)
     * @param accessToken the security access token to propagate to the archive
     * @return an immutable list of matching study summaries
     */
    List<StudySummary> search(StudySearchRequest request, int fetchLimit, String accessToken);
}
