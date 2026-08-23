package dev.blackice.shared.api.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.security.api.ApiJavaScriptRequestChecker;
import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Cobre as falhas de {@code /api} que nascem antes do Quarkus REST.
 *
 * <p>É a rede de segurança da fronteira: uma falha de roteamento que nunca chega
 * ao Quarkus REST ainda assim sai como Problem Details catalogado, em vez de
 * resposta nua.
 *
 * <p>Fora do seu alcance: o limite {@code quarkus.http.limits.max-body-size} é
 * aplicado pelo servidor HTTP antes do roteador, e encerra a conexão com
 * {@code 413} sem corpo. Por isso o limite da aplicação é configurado abaixo do
 * global — o caso que o usuário alcança é o catalogado.
 *
 * <p>{@code /api/login} passa adiante intocado: seu redirect é intencional.
 */
@ApplicationScoped
public class ApiHttpFailureHandler {

    private static final Logger LOG = Logger.getLogger(ApiHttpFailureHandler.class);

    @Inject
    ApiProblemFactory apiProblemFactory;

    @Inject
    ObjectMapper objectMapper;

    /** Registra o handler somente para as rotas de API. */
    public void register(@Observes Router router) {
        router.route("/api/*").failureHandler(this::writeProblemForFailure);
    }

    private void writeProblemForFailure(RoutingContext context) {
        if (!ApiJavaScriptRequestChecker.isApiRequest(context) || context.response().ended()) {
            context.next();
            return;
        }

        int status = context.statusCode() > 0 ? context.statusCode() : 500;
        ProblemType type = ApiProblemExceptionMappers.forStatus(status);

        if (type == ProblemType.API_INTERNAL_ERROR) {
            LOG.error("falha inesperada antes do REST na fronteira /api", context.failure());
        }
        write(context, type);
    }

    private void write(RoutingContext context, ProblemType type) {
        ApiProblem problem = apiProblemFactory.create(type, ProblemExtensions.none());
        HttpServerResponse response = context.response();
        try {
            response.setStatusCode(problem.status());
            response.putHeader(HttpHeaders.CONTENT_TYPE, ProblemResponseFactory.PROBLEM_JSON);
            if (problem.traceId() != null) {
                response.putHeader(ProblemResponseFactory.TRACE_HEADER, problem.traceId());
            }
            response.end(objectMapper.writeValueAsString(problem));
        } catch (Exception failure) {
            LOG.error("não foi possível escrever o Problem Details", failure);
            response.setStatusCode(problem.status()).end();
        }
    }
}
