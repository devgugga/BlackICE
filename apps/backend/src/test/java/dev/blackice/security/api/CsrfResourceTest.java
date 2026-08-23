package dev.blackice.security.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CsrfResourceTest {

    @Test
    void anonymous_request_receives_a_catalogued_authentication_problem() {
        given().redirects().follow(false)
            .when().get("/api/csrf")
            .then()
            .statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"))
            .body("status", equalTo(401))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void authenticated_request_receives_204_and_csrf_cookie() {
        given()
            .when().get("/api/csrf")
            .then()
            .statusCode(204)
            .cookie("csrf-token", notNullValue());
    }
}
