package dev.blackice.features.ingest;

import java.util.List;
import java.util.Map;

public record DicomBatchValidation(
    Map<String, List<ValidatedDicom>> validStudies,
    List<DicomValidationIssue> issues
) {}
