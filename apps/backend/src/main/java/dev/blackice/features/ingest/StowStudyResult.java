package dev.blackice.features.ingest;

import java.util.List;

public record StowStudyResult(
    String studyInstanceUid,
    List<StowInstanceResult> instances
) {}
