package dev.blackice.features.ingest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CsrfResourceTest {

    @Test
    void anonimo_recebe_desafio_de_autenticacao() {
        given().redirects().follow(false)
            .when().get("/api/csrf")
            .then().statusCode(302);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void autenticado_recebe_204_e_cookie_csrf() {
        given()
            .when().get("/api/csrf")
            .then()
            .statusCode(204)
            .cookie("csrf-token", notNullValue());
    }
}
