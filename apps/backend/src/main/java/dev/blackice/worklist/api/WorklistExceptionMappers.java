package dev.blackice.worklist.api;

import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.generated.ProblemType;
import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.exception.InvalidStudySearchException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduz as duas exceções conhecidas da Worklist em problemas catalogados.
 *
 * <p>Vários motivos internos convergem para o mesmo problema público, e isso é
 * correto: o catálogo descreve o que o consumidor observa, não a taxonomia de
 * exceções. Nada além destas duas é tratado aqui — um bug inesperado sobe para
 * o fallback `500` da fronteira compartilhada, em vez de ser disfarçado de
 * indisponibilidade do Archive.
 */
public class WorklistExceptionMappers {

    @Inject
    ProblemResponseFactory problems;

    @ServerExceptionMapper
    public Response invalidSearch(InvalidStudySearchException exception) {
        return problems.response(ProblemType.API_SEARCH_INVALID);
    }

    @ServerExceptionMapper
    public Response archiveSearch(ArchiveSearchException exception) {
        return problems.response(switch (exception.reason()) {
            case QUERY_TOO_BROAD -> ProblemType.API_SEARCH_TOO_BROAD;
            case INVALID_RESPONSE, HTTP_STATUS -> ProblemType.API_ARCHIVE_RESPONSE_INVALID;
            case TIMEOUT, CONNECTION -> ProblemType.API_ARCHIVE_UNAVAILABLE;
        });
    }
}
