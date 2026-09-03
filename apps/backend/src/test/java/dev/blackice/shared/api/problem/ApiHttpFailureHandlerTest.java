package dev.blackice.shared.api.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.shared.api.problem.generated.ProblemType;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiHttpFailureHandlerTest {

    @Test
    void response_with_written_headers_is_left_unchanged() {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(context.normalizedPath()).thenReturn("/api/dicomweb/frame");
        when(context.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(context.response()).thenReturn(response);
        when(response.headWritten()).thenReturn(true);
        when(response.ended()).thenReturn(false);

        ApiHttpFailureHandler handler = new ApiHttpFailureHandler();
        handler.apiProblemFactory = mock(ApiProblemFactory.class);
        handler.objectMapper = new ObjectMapper();
        handler.failureLogger = mock(ApiFailureLogger.class);
        ProblemType internalError = ProblemType.API_INTERNAL_ERROR;
        when(handler.apiProblemFactory.create(any(), any())).thenReturn(new ApiProblem(
            internalError.type(),
            internalError.title(),
            internalError.httpStatus(),
            internalError.detail(),
            internalError.code(),
            null,
            Map.of()
        ));

        handler.writeProblemForFailure(context);

        verify(context).next();
        verify(response, never()).setStatusCode(anyInt());
        verify(response, never()).putHeader(any(CharSequence.class), anyString());
        verify(response, never()).end(anyString());
    }
}
