package dev.blackice.viewer.application.port;

import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;

import java.util.List;

/**
 * Outbound port for discovering study hierarchy and instance identities from the DICOM archive.
 */
public interface StudyHierarchyGateway {

    /**
     * Finds metadata for a single study.
     *
     * @param study validated study reference
     * @param accessToken security token to propagate to the archive
     * @return study metadata, or null if not found
     */
    StudyMetadata findStudy(ViewerStudyRef study, String accessToken);

    /**
     * Finds all series belonging to a study.
     *
     * @param study validated study reference
     * @param accessToken security token to propagate to the archive
     * @return list of series metadata
     */
    List<SeriesMetadata> findSeries(ViewerStudyRef study, String accessToken);

    /**
     * Finds instance identities for all series belonging to a study.
     *
     * @param study validated study reference
     * @param accessToken security token to propagate to the archive
     * @return list of instance identity metadata
     */
    List<InstanceIdentityMetadata> findInstances(ViewerStudyRef study, String accessToken);
}
