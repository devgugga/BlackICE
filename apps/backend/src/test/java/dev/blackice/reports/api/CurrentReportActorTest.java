package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentReportActorTest {

    @Test
    void extracts_subject_from_principal_and_display_name_from_jwt_preferred_username() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        JsonWebToken jwt = mock(JsonWebToken.class);

        when(principal.getName()).thenReturn("sub-456");
        when(identity.getPrincipal()).thenReturn(principal);
        when(jwt.getClaim("preferred_username")).thenReturn("dr.house");

        CurrentReportActor provider = new CurrentReportActor(identity, jwt);
        ReportActor actor = provider.actor();

        assertEquals("sub-456", actor.subject());
        assertEquals("dr.house", actor.displayName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void falls_back_to_subject_when_preferred_username_is_null_or_blank(String emptyDisplayName) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        JsonWebToken jwt = mock(JsonWebToken.class);

        when(principal.getName()).thenReturn("sub-789");
        when(identity.getPrincipal()).thenReturn(principal);
        when(jwt.getClaim("preferred_username")).thenReturn(emptyDisplayName);

        CurrentReportActor provider = new CurrentReportActor(identity, jwt);
        ReportActor actor = provider.actor();

        assertEquals("sub-789", actor.subject());
        assertEquals("sub-789", actor.displayName());
    }

    @Test
    void falls_back_to_subject_when_jwt_is_null() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);

        when(principal.getName()).thenReturn("sub-101");
        when(identity.getPrincipal()).thenReturn(principal);

        CurrentReportActor provider = new CurrentReportActor(identity, null);
        ReportActor actor = provider.actor();

        assertEquals("sub-101", actor.subject());
        assertEquals("sub-101", actor.displayName());
    }

    @Test
    void throws_invalid_report_request_exception_when_identity_is_null() {
        CurrentReportActor provider = new CurrentReportActor(null, null);
        assertThrows(InvalidReportRequestException.class, provider::actor);
    }

    @Test
    void throws_invalid_report_request_exception_when_principal_is_null() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.getPrincipal()).thenReturn(null);

        CurrentReportActor provider = new CurrentReportActor(identity, null);
        assertThrows(InvalidReportRequestException.class, provider::actor);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void throws_invalid_report_request_exception_when_subject_is_null_or_blank(String blankSubject) {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(blankSubject);
        when(identity.getPrincipal()).thenReturn(principal);

        CurrentReportActor provider = new CurrentReportActor(identity, null);
        assertThrows(InvalidReportRequestException.class, provider::actor);
    }
}
