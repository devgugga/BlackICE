package dev.blackice.worklist.application.result;

import java.util.List;

/**
 * Paginated outcome of a study search containing matching study summaries and page navigation metadata.
 */
public record StudyPage(List<StudySummary> items, PageMetadata page) {
    public StudyPage {
        items = List.copyOf(items);
    }

    public record PageMetadata(int limit, int offset, boolean hasPrevious, boolean hasNext) {}
}
