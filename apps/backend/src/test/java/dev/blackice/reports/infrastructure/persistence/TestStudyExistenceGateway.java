package dev.blackice.reports.infrastructure.persistence;

import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.StudyExistenceGateway;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test fallback bean for {@link StudyExistenceGateway} until the DICOMweb QIDO-RS gateway is implemented in Task 5.
 */
@DefaultBean
@ApplicationScoped
public class TestStudyExistenceGateway implements StudyExistenceGateway {

    @Override
    public boolean exists(ReportStudyRef study, String accessToken) {
        return true;
    }
}
