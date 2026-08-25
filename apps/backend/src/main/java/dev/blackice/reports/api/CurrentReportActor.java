package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * CDI provider extracting the authenticated ReportActor from the current SecurityIdentity and JsonWebToken.
 */
@ApplicationScoped
public class CurrentReportActor {

    private final SecurityIdentity identity;
    private final JsonWebToken jwt;

    @Inject
    public CurrentReportActor(SecurityIdentity identity, JsonWebToken jwt) {
        this.identity = identity;
        this.jwt = jwt;
    }

    /**
     * Extracts the current authenticated actor.
     *
     * @return the non-null ReportActor
     * @throws InvalidReportRequestException if the identity or principal is missing/invalid
     */
    public ReportActor actor() {
        if (identity == null || identity.getPrincipal() == null) {
            throw new InvalidReportRequestException();
        }
        String subject = identity.getPrincipal().getName();
        if (subject == null || subject.isBlank()) {
            throw new InvalidReportRequestException();
        }

        String displayName = null;
        if (jwt != null) {
            Object claim = jwt.getClaim("preferred_username");
            if (claim instanceof String s && !s.isBlank()) {
                displayName = s;
            }
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = subject;
        }

        return new ReportActor(subject, displayName);
    }
}
