package dev.blackice.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
class MeResourceTest {

    @Test
    void anonimo_recebe_401() {
        given().redirects().follow(false)
            .when().get("/api/me")
            .then().statusCode(401);
    }
}
