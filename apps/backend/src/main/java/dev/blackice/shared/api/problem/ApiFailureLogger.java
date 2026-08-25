package dev.blackice.shared.api.problem;

import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Emits one structured, safe event when the HTTP boundary finishes handling a failure. */
@ApplicationScoped
public class ApiFailureLogger {

    /** Closed vocabulary of internal reasons that are safe to include in operator logs. */
    public enum Reason {
        INVALID_SEARCH,
        INVALID_REQUEST,
        AUTHENTICATION,
        ACCESS_DENIED,
        NOT_FOUND,
        UNAVAILABLE,
        QUERY_TOO_BROAD,
        INVALID_RESPONSE,
        HTTP_STATUS,
        TIMEOUT,
        CONNECTION,
        INTERRUPTED,
        OUTCOME_UNKNOWN,
        LOCAL_VALIDATION,
        UNCLASSIFIED,
        REST,
        PRE_REST
    }

    private static final Logger LOG = Logger.getLogger(ApiFailureLogger.class);
    private static final String UNKNOWN_TRACE = "unavailable";

    private final TraceContext traceContext;

    @Inject
    public ApiFailureLogger(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    /** Logs a known failure without attaching its exception, message or external cause. */
    public void known(ProblemType type, String method, String routeTemplate, Reason reason) {
        String message = event(type, method, routeTemplate, reason);
        if (type.httpStatus() >= 500) {
            LOG.warn(message);
        } else {
            LOG.info(message);
        }
    }

    /** Logs an unexpected API failure once with a fixed diagnostic stack and no external data. */
    public void unexpected(String method, String routeTemplate) {
        RuntimeException diagnostic = new RuntimeException("Unexpected API failure");
        diagnostic.setStackTrace(new StackTraceElement[] {
            new StackTraceElement(ApiFailureLogger.class.getName(), "unexpected", "ApiFailureLogger.java", -1)
        });
        LOG.error(event(ProblemType.API_INTERNAL_ERROR, method, routeTemplate, null), diagnostic);
    }

    private String event(ProblemType type, String method, String routeTemplate, Reason reason) {
        StringBuilder event = new StringBuilder(160)
            .append("api failure: code=").append(type.code())
            .append(" status=").append(type.httpStatus())
            .append(" traceId=").append(traceId())
            .append(" method=").append(method)
            .append(" route=").append(routeTemplate);
        if (reason != null) {
            event.append(" reason=").append(reason.name());
        }
        return event.toString();
    }

    private String traceId() {
        String traceId = traceContext.traceId();
        return traceId == null ? UNKNOWN_TRACE : traceId;
    }
}
