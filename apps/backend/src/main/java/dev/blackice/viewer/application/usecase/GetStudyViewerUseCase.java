package dev.blackice.viewer.application.usecase;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.port.StudyHierarchyGateway;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.SeriesSupport;
import dev.blackice.viewer.application.result.StudyMetadata;
import dev.blackice.viewer.application.result.StudyViewerSummary;
import dev.blackice.viewer.application.result.ViewerSeriesSummary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Use case orchestrating study hierarchy discovery, instance identity aggregation,
 * series support classification, and deterministic ordering for the study viewer.
 */
@ApplicationScoped
public class GetStudyViewerUseCase {

    private static final Comparator<ViewerSeriesSummary> SERIES_COMPARATOR =
        Comparator.comparing(ViewerSeriesSummary::seriesNumber, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ViewerSeriesSummary::seriesInstanceUid);

    private final StudyHierarchyGateway gateway;
    private final SeriesSupportClassifier classifier;

    @Inject
    public GetStudyViewerUseCase(StudyHierarchyGateway gateway, SeriesSupportClassifier classifier) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    }

    /**
     * Retrieves and classifies the study hierarchy for the viewer.
     *
     * @param study validated reference to the study
     * @param accessToken authorization token to propagate to the archive
     * @return curated study viewer summary
     * @throws InvalidArchiveMetadataException if archive metadata is corrupt, contradictory, or missing
     */
    public StudyViewerSummary execute(ViewerStudyRef study, String accessToken) {
        Objects.requireNonNull(study, "study must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        StudyMetadata studyMetadata = gateway.findStudy(study, accessToken);
        if (studyMetadata == null) {
            throw new InvalidArchiveMetadataException("Study metadata not found in archive");
        }

        List<SeriesMetadata> seriesList = gateway.findSeries(study, accessToken);
        if (seriesList == null) {
            throw new InvalidArchiveMetadataException("Null series list returned by archive");
        }

        List<InstanceIdentityMetadata> instancesList = gateway.findInstances(study, accessToken);
        if (instancesList == null) {
            throw new InvalidArchiveMetadataException("Null instance list returned by archive");
        }

        Set<String> knownSeriesUids = new HashSet<>();
        for (SeriesMetadata series : seriesList) {
            if (series == null || series.seriesInstanceUid() == null) {
                throw new InvalidArchiveMetadataException("Invalid series entry in series list");
            }
            if (!knownSeriesUids.add(series.seriesInstanceUid())) {
                throw new InvalidArchiveMetadataException("Duplicate seriesInstanceUid in series list");
            }
        }

        Map<String, List<InstanceIdentityMetadata>> instancesBySeries = new HashMap<>();
        for (InstanceIdentityMetadata instance : instancesList) {
            if (instance == null || instance.seriesInstanceUid() == null) {
                throw new InvalidArchiveMetadataException("Invalid instance metadata entry");
            }
            if (!knownSeriesUids.contains(instance.seriesInstanceUid())) {
                throw new InvalidArchiveMetadataException("Instance belongs to unknown series");
            }
            instancesBySeries.computeIfAbsent(instance.seriesInstanceUid(), k -> new ArrayList<>()).add(instance);
        }

        List<ViewerSeriesSummary> summaries = new ArrayList<>(seriesList.size());
        for (SeriesMetadata series : seriesList) {
            List<InstanceIdentityMetadata> instancesForSeries = instancesBySeries.get(series.seriesInstanceUid());
            if (instancesForSeries == null || instancesForSeries.isEmpty()) {
                throw new InvalidArchiveMetadataException("No instances found for series");
            }

            if (!Integer.valueOf(instancesForSeries.size()).equals(series.instanceCount())) {
                throw new InvalidArchiveMetadataException("Inconsistent instance count for series");
            }

            SeriesSupport support = classifier.classify(instancesForSeries);

            summaries.add(new ViewerSeriesSummary(
                series.seriesInstanceUid(),
                series.seriesNumber(),
                series.modality(),
                series.description(),
                instancesForSeries.size(),
                support.availability(),
                support.unsupportedReason()
            ));
        }

        List<ViewerSeriesSummary> sortedSeries = summaries.stream()
            .sorted(SERIES_COMPARATOR)
            .toList();

        return new StudyViewerSummary(
            studyMetadata.studyInstanceUid(),
            studyMetadata.patientName(),
            studyMetadata.patientId(),
            studyMetadata.patientIdIssuer(),
            studyMetadata.studyDate(),
            studyMetadata.studyTime(),
            studyMetadata.description(),
            sortedSeries
        );
    }
}
