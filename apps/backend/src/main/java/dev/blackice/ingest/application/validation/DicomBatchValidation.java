package dev.blackice.ingest.application.validation;

import java.util.List;
import java.util.Map;

/**
 * Aggregated result of batch DICOM validation partitioned by Study Instance UID.
 *
 * @param validStudies map of Study Instance UID to ordered list of valid, de-duplicated DICOM instances
 * @param issues list of validation issues and rejected files detected during batch analysis
 */
public record DicomBatchValidation(
    Map<String, List<ValidatedDicom>> validStudies,
    List<DicomValidationIssue> issues
) {}
