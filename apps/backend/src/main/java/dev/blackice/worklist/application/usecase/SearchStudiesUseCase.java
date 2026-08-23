package dev.blackice.worklist.application.usecase;

import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.port.StudyQueryGateway;
import dev.blackice.worklist.application.result.StudyPage;
import dev.blackice.worklist.application.result.StudySummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;

/**
 * Use case orchestrating study search and offset-based pagination over the DICOM archive.
 */
@ApplicationScoped
public class SearchStudiesUseCase {

    private final StudyQueryGateway gateway;

    @Inject
    public SearchStudiesUseCase(StudyQueryGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    /**
     * Executes study search requesting limit + 1 items from the archive to determine hasNext without full count.
     *
     * @param request the validated study search criteria
     * @param accessToken the security token to propagate to the archive
     * @return the paginated study result
     */
    public StudyPage search(StudySearchRequest request, String accessToken) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        List<StudySummary> matches = gateway.search(request, request.limit() + 1, accessToken);
        boolean hasNext = matches.size() > request.limit();
        List<StudySummary> items = matches.stream().limit(request.limit()).toList();
        return new StudyPage(items, new StudyPage.PageMetadata(
            request.limit(), request.offset(), request.offset() > 0, hasNext));
    }
}
