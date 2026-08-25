package dev.blackice.shared.api.problem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import dev.blackice.shared.api.problem.generated.ProblemType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HTTP error-boundary matrix.
 *
 * <p>Every JSON error response under {@code /api} must use a catalogued type with
 * {@code application/problem+json}, correlate {@code traceId} with {@code X-Trace-ID}, and omit
 * every remnant of the legacy contract.</p>
 */
@QuarkusTest
class ApiProblemHttpTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    /** Applies the assertions shared by every catalogued problem. */
    private static ValidatableResponse assertProblem(ValidatableResponse response, ProblemType type) {
        return response
            .statusCode(type.httpStatus())
            .contentType("application/problem+json")
            .body("type", equalTo(type.type().toString()))
            .body("code", equalTo(type.code()))
            .body("status", equalTo(type.httpStatus()))
            .body("title", equalTo(type.title()))
            .body("detail", equalTo(type.detail()))
            .body("traceId", not(blankOrNullString()))
            .body("instance", nullValue())
            .body("retryPolicy", nullValue())
            .header("X-Request-ID", nullValue());
    }

    private String getCsrfToken() {
        return given()
            .when().get("/api/csrf")
            .then().statusCode(204)
            .extract().cookie("csrf-token");
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void malformed_json_with_valid_csrf_token_becomes_a_catalogued_bad_request() {
        String csrf = getCsrfToken();
        assertProblem(
            given()
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json").body("{ isso nao e json ")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_REQUEST_INVALID);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void json_post_without_csrf_token_becomes_a_catalogued_csrf_problem() {
        assertProblem(
            given().contentType("application/json").body("{\"value\": \"test\"}")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_CSRF_INVALID);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void json_post_with_cookie_but_without_csrf_header_becomes_a_catalogued_csrf_problem() {
        String csrf = getCsrfToken();
        assertProblem(
            given()
                .cookie("csrf-token", csrf)
                .contentType("application/json").body("{\"value\": \"test\"}")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_CSRF_INVALID);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void json_post_with_mismatched_csrf_token_becomes_a_catalogued_csrf_problem() {
        String csrf = getCsrfToken();
        assertProblem(
            given()
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", "divergente")
                .contentType("application/json").body("{\"value\": \"test\"}")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_CSRF_INVALID);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void json_post_with_valid_csrf_token_succeeds() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType("application/json").body("{\"value\": \"ok-json\"}")
            .when().post("/api/problem-probe/json")
            .then()
            .statusCode(200)
            .body("value", equalTo("ok-json"));
    }

    @Test
    void get_requests_remain_unaffected_without_csrf_token() {
        given()
            .when().get("/api/problem-probe/json")
            .then()
            .statusCode(200)
            .body("value", equalTo("ok"));
    }

    @Test
    void anonymous_request_to_an_api_route_is_challenged_with_a_problem() {
        assertProblem(
            given().redirects().follow(false)
                .when().get("/api/problem-probe/secured")
                .then(),
            ProblemType.API_AUTHENTICATION_REQUIRED);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "outra-role")
    void authenticated_request_without_the_role_is_denied_with_a_problem() {
        assertProblem(
            given().when().get("/api/problem-probe/secured").then(),
            ProblemType.API_ACCESS_DENIED);
    }

    @Test
    void unknown_api_route_becomes_a_catalogued_not_found() {
        assertProblem(
            given().when().get("/api/problem-probe/rota-inexistente").then(),
            ProblemType.API_RESOURCE_NOT_FOUND);
    }

    @Test
    void unsupported_method_becomes_a_catalogued_method_not_allowed() {
        assertProblem(
            given().when().delete("/api/problem-probe/json").then(),
            ProblemType.API_METHOD_NOT_ALLOWED);
    }

    @Test
    void unacceptable_representation_becomes_a_catalogued_problem() {
        assertProblem(
            given().accept("application/xml")
                .when().get("/api/problem-probe/json")
                .then(),
            ProblemType.API_REPRESENTATION_NOT_ACCEPTABLE);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void unsupported_media_type_becomes_a_catalogued_problem() {
        String csrf = getCsrfToken();
        assertProblem(
            given()
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("text/plain").body("texto simples")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_MEDIA_TYPE_UNSUPPORTED);
    }

    /**
     * The HTTP server applies {@code quarkus.http.limits.max-body-size} before routing and can
     * close the request without a body. The effective application limit is configured below the
     * global limit so the refusal exercised here remains catalogued.
     */
    @Test
    void a_payload_rejected_by_an_application_limit_becomes_a_catalogued_problem() {
        assertProblem(
            given().when().get("/api/problem-probe/too-large").then(),
            ProblemType.API_PAYLOAD_TOO_LARGE);
    }

    @Test
    void an_unexpected_failure_becomes_a_catalogued_internal_error() {
        assertProblem(
            given().when().get("/api/problem-probe/fail").then(),
            ProblemType.API_INTERNAL_ERROR)
            .body("detail", not(org.hamcrest.Matchers.containsString("patient-secret")))
            .body("detail", not(org.hamcrest.Matchers.containsString("Maria")))
            .body("detail", not(org.hamcrest.Matchers.containsString("1.2.840")));
    }

    @Test
    void an_unexpected_api_failure_emits_exactly_one_correlated_error_event() {
        String traceId = "9b19b2081dc4417db6cd9a6eebfb7270";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given().header("traceparent", "00-" + traceId + "-d77271937a674926-01")
                .when().get("/api/problem-probe/fail")
                .then().statusCode(500);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.SEVERE, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_INTERNAL_ERROR"));
            assertTrue(event.contains("status=500"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/*"));
            Throwable diagnostic = events.getFirst().getThrown();
            assertNotNull(diagnostic);
            assertEquals("Unexpected API failure", diagnostic.getMessage());
            assertNull(diagnostic.getCause());
            assertEquals(0, diagnostic.getSuppressed().length);
            assertEquals(1, diagnostic.getStackTrace().length);
            assertEquals(ApiFailureLogger.class.getName(), diagnostic.getStackTrace()[0].getClassName());

            String fullyFormatted = logs.fullyFormatted(events.getFirst());
            assertFalse(fullyFormatted.contains("patient-secret"));
            assertFalse(fullyFormatted.contains("Maria da Silva"));
            assertFalse(fullyFormatted.contains("1.2.840"));
            assertFalse(fullyFormatted.contains("external-cause"));
            assertFalse(fullyFormatted.contains("suppressed patient-secret"));
        }
    }

    @Test
    void a_pre_rest_expected_failure_emits_exactly_one_safe_correlated_event() {
        String traceId = "46fcc70fc53946139c05c102a80ea5ac";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            assertProblem(
                given().header("traceparent", "00-" + traceId + "-3c068ccff2294b9c-01")
                    .when().get("/api/pre-rest-problem-probe")
                    .then(),
                ProblemType.API_REQUEST_INVALID)
                .body("traceId", equalTo(traceId));

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_REQUEST_INVALID"));
            assertTrue(event.contains("status=400"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/*"));
            assertTrue(event.contains("reason=PRE_REST"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    void a_server_web_application_exception_outside_api_keeps_the_framework_contract() {
        String traceId = "6328d0555cbe456aa3c87812ea476dec";
        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            Response response = given()
                .header("traceparent", "00-" + traceId + "-ec34f3d07cd84a79-01")
                .when().get("/outside/problem-probe/service-unavailable")
                .then().statusCode(503)
                .header("X-Trace-ID", nullValue())
                .extract().response();

            assertFalse(response.contentType() != null
                && response.contentType().contains("application/problem+json"));
            assertTrue(logs.containing("traceId=" + traceId).isEmpty());
        }
    }

    @Test
    void a_received_traceparent_is_continued_into_the_body_and_the_header() {
        given().header("traceparent", TRACEPARENT)
            .when().get("/api/problem-probe/fail")
            .then()
            .statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body("traceId", equalTo(TRACE_ID))
            .header("X-Trace-ID", equalTo(TRACE_ID));
    }

    @Test
    void a_client_supplied_trace_header_never_replaces_the_canonical_context() {
        given().header("traceparent", TRACEPARENT)
            .header("X-Trace-ID", "forjado-pelo-cliente")
            .when().get("/api/problem-probe/fail")
            .then()
            .header("X-Trace-ID", equalTo(TRACE_ID))
            .body("traceId", equalTo(TRACE_ID));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void a_successful_api_response_also_carries_the_trace_header() {
        given().header("traceparent", TRACEPARENT)
            .when().get("/api/problem-probe/secured")
            .then()
            .statusCode(204)
            .header("X-Trace-ID", equalTo(TRACE_ID));
    }

    /** The intentional {@code /api/login} redirect remains outside the error contract. */
    @Test
    void the_login_route_never_produces_a_catalogued_problem() {
        String contentType = given().redirects().follow(false)
            .when().get("/api/login")
            .then().extract().contentType();

        assertFalse(contentType != null && contentType.contains("application/problem+json"),
            "/api/login does not belong to the catalogued error contract");
    }

    /** The redirect itself can only be verified while the configured OIDC server is reachable. */
    @Test
    void the_login_route_keeps_its_intentional_oidc_redirect() {
        assumeTrue(oidcServerIsReachable(), "Keycloak is unavailable, so the OIDC redirect cannot be verified");

        given().redirects().follow(false)
            .when().get("/api/login")
            .then()
            .statusCode(302)
            .header("location", not(blankOrNullString()));
    }

    private static boolean oidcServerIsReachable() {
        String discovery = ConfigProvider.getConfig()
            .getValue("quarkus.oidc.auth-server-url", String.class)
            + "/.well-known/openid-configuration";
        try {
            HttpResponse<Void> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(HttpRequest.newBuilder(URI.create(discovery))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException unreachable) {
            if (unreachable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
