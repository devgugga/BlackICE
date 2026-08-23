package dev.blackice.shared.api.problem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import dev.blackice.shared.api.problem.generated.ProblemType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.ValidatableResponse;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Matriz HTTP da fronteira de erro.
 *
 * <p>Toda resposta de erro JSON sob {@code /api} precisa ser um tipo catalogado
 * em {@code application/problem+json}, com {@code traceId} igual ao
 * {@code X-Trace-ID}, e sem nenhum resquício do contrato antigo.
 */
@QuarkusTest
class ApiProblemHttpTest {

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    /** Asserções comuns a todo problema catalogado. */
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

    @Test
    void malformed_json_becomes_a_catalogued_bad_request() {
        assertProblem(
            given().contentType("application/json").body("{ isso nao e json ")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_REQUEST_INVALID);
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
    void unsupported_media_type_becomes_a_catalogued_problem() {
        assertProblem(
            given().contentType("text/plain").body("texto simples")
                .when().post("/api/problem-probe/json")
                .then(),
            ProblemType.API_MEDIA_TYPE_UNSUPPORTED);
    }

    /**
     * O limite {@code quarkus.http.limits.max-body-size} é aplicado pelo servidor
     * HTTP antes do roteador e encerra a conexão sem corpo — nenhum handler o
     * alcança. Por isso o limite efetivo é o da aplicação, configurado abaixo do
     * global, e é a recusa dela que precisa sair catalogada.
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

    /**
     * O redirect de {@code /api/login} está fora do contrato de erro. Isso vale
     * mesmo sem um servidor OIDC no ar: a rota nunca pode sair como Problem
     * Details catalogado, senão o SPA a trataria como falha da API.
     */
    @Test
    void the_login_route_never_produces_a_catalogued_problem() {
        String contentType = given().redirects().follow(false)
            .when().get("/api/login")
            .then().extract().contentType();

        assertFalse(contentType != null && contentType.contains("application/problem+json"),
            "/api/login não pertence ao contrato de erro catalogado");
    }

    /**
     * A verificação do redirect em si exige um servidor OIDC alcançável: sem ele
     * o Quarkus não consegue montar a authorization request. O teste é pulado,
     * não aprovado, quando o Keycloak não está no ar.
     */
    @Test
    void the_login_route_keeps_its_intentional_oidc_redirect() {
        assumeTrue(oidcServerIsReachable(), "Keycloak fora do ar: redirect OIDC não verificável");

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
