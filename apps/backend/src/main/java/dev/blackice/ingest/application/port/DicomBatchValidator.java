package dev.blackice.ingest.application.port;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.validation.DicomBatchValidation;

import java.util.List;

/** Validates uploaded DICOM files before the ingestion use case groups them by study UID. */
public interface DicomBatchValidator {

    DicomBatchValidation validate(List<UploadedDicom> uploads);
}
