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

/**
 * Handles {@code /api} failures that originate before Quarkus REST dispatch.
 *
 * <p>This is the boundary safety net: routing failures that never reach Quarkus REST still leave
 * as catalogued Problem Details instead of empty responses.</p>
 *
 * <p>The global {@code quarkus.http.limits.max-body-size} remains outside its reach because the
 * HTTP server can close an oversized request before routing. The application limit is therefore
 * configured below the global one so the user-facing refusal remains catalogued.</p>
 *
 * <p>{@code /api/login} passes through unchanged because its redirect is intentional.</p>
 */
@ApplicationScoped
public class ApiHttpFailureHandler {

    @Inject
    ApiProblemFactory apiProblemFactory;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ApiFailureLogger failureLogger;

    /** Registers the failure handler only for API routes. */
    public void register(@Observes Router router) {
        router.route("/api/*").failureHandler(this::writeProblemForFailure);
    }

    void writeProblemForFailure(RoutingContext context) {
        if (!ApiJavaScriptRequestChecker.isApiRequest(context)
            || context.response().ended()
            || context.response().headWritten()) {
            context.next();
            return;
        }

        int status = context.statusCode() > 0 ? context.statusCode() : 500;
        ProblemType type = ApiProblemExceptionMappers.forStatus(status);
        String method = context.request().method().name();
        ApiProblem problem = apiProblemFactory.create(type, ProblemExtensions.none());
        String body;

        try {
            body = objectMapper.writeValueAsString(problem);
        } catch (Exception writingFailure) {
            failureLogger.unexpected(method, "/api/*");
            context.response().setStatusCode(500).end();
            return;
        }

        if (type == ProblemType.API_INTERNAL_ERROR) {
            failureLogger.unexpected(method, "/api/*");
        } else {
            failureLogger.known(type, method, "/api/*", ApiFailureLogger.Reason.PRE_REST);
        }
        write(context, problem, body);
    }

    private void write(RoutingContext context, ApiProblem problem, String body) {
        HttpServerResponse response = context.response();
        response.setStatusCode(problem.status());
        response.putHeader(HttpHeaders.CONTENT_TYPE, ProblemResponseFactory.PROBLEM_JSON);
        if (problem.traceId() != null) {
            response.putHeader(ProblemResponseFactory.TRACE_HEADER, problem.traceId());
        }
        response.end(body);
    }
}
