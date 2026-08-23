package dev.blackice.ingest.application.validation;

import java.util.List;
import java.util.Map;

public record DicomBatchValidation(
    Map<String, List<ValidatedDicom>> validStudies,
    List<DicomValidationIssue> issues
) {}
