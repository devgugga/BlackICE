package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.port.SeriesMetadataGateway;
import dev.blackice.viewer.application.result.ViewerInstance;
import dev.blackice.viewer.application.result.ViewerSeriesInstances;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Use case retrieving, validating, and curating instance metadata for an active series.
 */
@ApplicationScoped
public class GetSeriesInstancesUseCase {

    private static final Set<String> SUPPORTED_IMAGE_SOP_CLASSES = Set.of(
        "1.2.840.10008.5.1.4.1.1.1",     // Computed Radiography Image Storage
        "1.2.840.10008.5.1.4.1.1.1.1",   // Digital X-Ray Image Storage - For Presentation
        "1.2.840.10008.5.1.4.1.1.2",     // CT Image Storage
        "1.2.840.10008.5.1.4.1.1.4"      // MR Image Storage
    );

    private final SeriesMetadataGateway seriesMetadataGateway;
    private final SpatialInstanceOrder spatialInstanceOrder;

    @Inject
    public GetSeriesInstancesUseCase(
        SeriesMetadataGateway seriesMetadataGateway,
        SpatialInstanceOrder spatialInstanceOrder
    ) {
        this.seriesMetadataGateway = Objects.requireNonNull(seriesMetadataGateway, "seriesMetadataGateway must not be null");
        this.spatialInstanceOrder = Objects.requireNonNull(spatialInstanceOrder, "spatialInstanceOrder must not be null");
    }

    /**
     * Executes curated metadata retrieval for the specified active series.
     *
     * @param series validated series reference
     * @param accessToken user bearer access token
     * @return curated viewer series instances response record
     */
    public ViewerSeriesInstances execute(ViewerSeriesRef series, String accessToken) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        List<ViewerInstance> instances = seriesMetadataGateway.retrieve(series, accessToken);
        if (instances == null) {
            instances = List.of();
        }

        // Verify allowlisted SOP Classes
        for (ViewerInstance instance : instances) {
            if (!SUPPORTED_IMAGE_SOP_CLASSES.contains(instance.sopClassUid())) {
                throw new InvalidArchiveMetadataException("Instance has non-allowlisted SOP Class UID");
            }
        }

        List<ViewerInstance> sortedInstances = spatialInstanceOrder.sort(instances);

        return new ViewerSeriesInstances(
            series.studyInstanceUid(),
            series.seriesInstanceUid(),
            sortedInstances
        );
    }
}
