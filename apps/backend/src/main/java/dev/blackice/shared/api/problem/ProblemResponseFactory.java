package dev.blackice.shared.api.problem;

import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Traduz um tipo catalogado em resposta HTTP.
 *
 * <p>Só aceita tipo do catálogo e extensão tipada, então uma resposta de erro
 * fora do contrato não é representável. O media type é sempre
 * {@code application/problem+json}.
 */
@ApplicationScoped
public class ProblemResponseFactory {

    /** Media type de Problem Details, conforme RFC 9457. */
    public static final String PROBLEM_JSON = "application/problem+json";

    /** Cabeçalho de correlação presente em toda resposta sob {@code /api}. */
    public static final String TRACE_HEADER = "X-Trace-ID";

    private final ApiProblemFactory apiProblemFactory;

    @Inject
    public ProblemResponseFactory(ApiProblemFactory apiProblemFactory) {
        this.apiProblemFactory = apiProblemFactory;
    }

    public Response response(ProblemType type, ProblemExtensions extensions) {
        ApiProblem problem = apiProblemFactory.create(type, extensions);

        Response.ResponseBuilder builder = Response.status(problem.status())
            .type(PROBLEM_JSON)
            .entity(problem);

        if (problem.traceId() != null) {
            builder.header(TRACE_HEADER, problem.traceId());
        }
        return builder.build();
    }

    /** Atalho para os tipos sem extensão, que são a maioria do catálogo. */
    public Response response(ProblemType type) {
        return response(type, ProblemExtensions.none());
    }
}
