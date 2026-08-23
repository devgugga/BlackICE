package dev.blackice.security.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CsrfResourceTest {

    @Test
    void anonymous_request_receives_authentication_challenge() {
        given().redirects().follow(false)
            .when().get("/api/csrf")
            .then().statusCode(302);
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
