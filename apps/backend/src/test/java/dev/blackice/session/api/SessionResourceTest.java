package dev.blackice.session.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
class SessionResourceTest {

    @Test
    void anonymous_request_returns_401() {
        given().redirects().follow(false)
            .when().get("/api/me")
            .then().statusCode(401);
    }
}
