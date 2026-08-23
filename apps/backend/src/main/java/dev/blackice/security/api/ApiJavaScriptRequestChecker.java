package dev.blackice.security.api;

import io.quarkus.oidc.JavaScriptRequestChecker;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Faz o OIDC distinguir navegação de chamada de API.
 *
 * <p>Uma rota sob {@code /api} é consumida por código, não por um navegador
 * seguindo links: responder com redirect esconderia a falha do SPA. Por isso
 * essas rotas recebem um desafio {@code 401}, que a fronteira de erro completa
 * com {@code API_AUTHENTICATION_REQUIRED}.
 *
 * <p>{@code /api/login} é a exceção deliberada: ali o redirect para o Keycloak
 * é o comportamento pretendido, e continua intacto.
 */
@ApplicationScoped
public final class ApiJavaScriptRequestChecker implements JavaScriptRequestChecker {

    /** Rota que inicia o fluxo OIDC de propósito. */
    public static final String LOGIN_PATH = "/api/login";

    @Override
    public boolean isJavaScriptRequest(RoutingContext context) {
        return isApiRequest(context);
    }

    /** Verdadeiro para rotas de API, falso para {@code /api/login} e o resto do site. */
    public static boolean isApiRequest(RoutingContext context) {
        String path = context.normalizedPath();
        return path.startsWith("/api/") && !path.equals(LOGIN_PATH);
    }

    @Override
    public ChallengeData getChallenge(RoutingContext context) {
        return new ChallengeData(401, "WWW-Authenticate", "OIDC");
    }
}
