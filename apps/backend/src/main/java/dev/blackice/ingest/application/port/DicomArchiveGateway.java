package dev.blackice.ingest.application.port;

import dev.blackice.ingest.application.result.StowStudyResult;
import dev.blackice.ingest.application.validation.ValidatedDicom;

import java.util.List;

/** Stores one validated study through a DICOM archive and returns its STOW outcome. */
public interface DicomArchiveGateway {

    StowStudyResult storeStudy(
        String studyInstanceUid,
        List<ValidatedDicom> files,
        String accessToken
    );
}
