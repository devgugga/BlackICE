package dev.blackice.shared.api.problem;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Marca toda resposta sob {@code /api} com o TraceID do span ativo.
 *
 * <p>Vale inclusive para respostas de sucesso: o operador correlaciona qualquer
 * requisição, não apenas as que falharam. Um {@code X-Trace-ID} anterior é
 * sempre substituído, porque o valor canônico vem do contexto W3C e nunca do
 * cliente.
 */
@Provider
public class ApiTraceResponseFilter implements ContainerResponseFilter {

    private final TraceContext traceContext;

    @Inject
    public ApiTraceResponseFilter(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!isApiPath(request)) {
            return;
        }

        String traceId = traceContext.traceId();
        if (traceId == null) {
            return;
        }
        response.getHeaders().putSingle(ProblemResponseFactory.TRACE_HEADER, traceId);
    }

    private static boolean isApiPath(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        return path.equals("api") || path.equals("/api")
            || path.startsWith("api/") || path.startsWith("/api/");
    }
}
