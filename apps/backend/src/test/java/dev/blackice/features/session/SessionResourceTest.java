package dev.blackice.features.session;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
class SessionResourceTest {

    @Test
    void anonimo_recebe_401() {
        given().redirects().follow(false)
            .when().get("/api/me")
            .then().statusCode(401);
    }
}
