package dev.blackice.session.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class SessionResourceTest {

    @Test
    void anonymous_request_receives_a_catalogued_authentication_problem() {
        given().redirects().follow(false)
            .when().get("/api/me")
            .then()
            .statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void authenticated_request_returns_the_session_and_the_trace_header() {
        given()
            .when().get("/api/me")
            .then()
            .statusCode(200)
            .header("X-Trace-ID", not(blankOrNullString()));
    }
}
