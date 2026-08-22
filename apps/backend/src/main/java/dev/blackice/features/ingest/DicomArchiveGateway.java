package dev.blackice.features.ingest;

import java.util.List;

public interface DicomArchiveGateway {

    StowStudyResult storeStudy(
        String studyInstanceUid,
        List<ValidatedDicom> files,
        String accessToken
    );
}
