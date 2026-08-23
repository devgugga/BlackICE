package dev.blackice.shared.api.problem;

import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Converte em problema catalogado as respostas de erro que o framework devolve
 * vazias.
 *
 * <p>Um {@link ApiProblem} já construído passa intacto: este filtro só preenche
 * o vazio. Requisições mutantes com {@code 400} e CSRF ausente ou divergente são
 * reclassificadas para {@code 403 API_CSRF_INVALID}, porque a requisição foi
 * compreendida e recusada na verificação de segurança.
 */
@Provider
public class ApiProblemResponseFilter implements ContainerResponseFilter {

    private static final String CSRF_COOKIE = "csrf-token";
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Inject
    ApiProblemFactory apiProblemFactory;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (response.getStatus() < 400 || !isApiPath(request)) {
            return;
        }
        if (response.getEntity() instanceof ApiProblem) {
            return;
        }
        if (response.getEntity() != null && !isEmptyText(response.getEntity())) {
            return;
        }

        ProblemType type = classify(request, response.getStatus());
        ApiProblem problem = apiProblemFactory.create(type, dev.blackice.shared.api.problem
            .generated.ProblemExtensions.none());

        response.setStatus(problem.status());
        response.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, ProblemResponseFactory.PROBLEM_JSON);
        response.setEntity(problem, null, jakarta.ws.rs.core.MediaType.valueOf(
            ProblemResponseFactory.PROBLEM_JSON));
    }

    private static ProblemType classify(ContainerRequestContext request, int status) {
        if (status == 400 && isCsrfProtected(request) && csrfIsMissingOrMismatched(request)) {
            return ProblemType.API_CSRF_INVALID;
        }
        return ApiProblemExceptionMappers.forStatus(status);
    }

    /**
     * O filtro CSRF do Quarkus so verifica requisicoes mutantes com corpo de
     * formulario. Fora disso, um 400 e apenas requisicao invalida.
     */
    private static boolean isCsrfProtected(ContainerRequestContext request) {
        if (!isMutating(request.getMethod())) {
            return false;
        }
        jakarta.ws.rs.core.MediaType mediaType = request.getMediaType();
        return mediaType != null
            && (mediaType.isCompatible(jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                || mediaType.isCompatible(jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    private static boolean isMutating(String method) {
        return switch (method) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private static boolean csrfIsMissingOrMismatched(ContainerRequestContext request) {
        var cookie = request.getCookies().get(CSRF_COOKIE);
        String header = request.getHeaderString(CSRF_HEADER);
        return cookie == null || header == null || !header.equals(cookie.getValue());
    }

    private static boolean isEmptyText(Object entity) {
        return entity instanceof String text && text.isBlank();
    }

    private static boolean isApiPath(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        return path.equals("api") || path.equals("/api")
            || path.startsWith("api/") || path.startsWith("/api/");
    }
}
