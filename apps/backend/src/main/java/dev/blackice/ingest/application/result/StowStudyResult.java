package dev.blackice.ingest.application.result;

import java.util.List;

public record StowStudyResult(
    String studyInstanceUid,
    List<StowInstanceResult> instances
) {}
