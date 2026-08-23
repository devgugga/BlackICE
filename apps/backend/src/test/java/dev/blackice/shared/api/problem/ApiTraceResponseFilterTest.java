package dev.blackice.shared.api.problem;

import java.net.URI;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiTraceResponseFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

    private ApiTraceResponseFilter filterWithTrace(String traceId) {
        return new ApiTraceResponseFilter(new TraceContext() {
            @Override
            public String traceId() {
                return traceId;
            }

            @Override
            public String spanId() {
                return "00f067aa0ba902b7";
            }
        });
    }

    private void filter(ApiTraceResponseFilter filter, String path) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getAbsolutePath()).thenReturn(URI.create("http://localhost" + path));
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getUriInfo()).thenReturn(uriInfo);

        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);
    }

    @Test
    void every_api_response_carries_the_active_trace_id() {
        filter(filterWithTrace(TRACE_ID), "/api/worklist/studies");

        assertEquals(TRACE_ID, headers.getFirst("X-Trace-ID"));
    }

    @Test
    void a_client_supplied_trace_id_is_always_replaced() {
        headers.putSingle("X-Trace-ID", "forjado-pelo-cliente");

        filter(filterWithTrace(TRACE_ID), "/api/session");

        assertEquals(1, headers.get("X-Trace-ID").size());
        assertEquals(TRACE_ID, headers.getFirst("X-Trace-ID"));
    }

    @Test
    void paths_outside_the_api_boundary_are_left_untouched() {
        filter(filterWithTrace(TRACE_ID), "/q/health");

        assertFalse(headers.containsKey("X-Trace-ID"));
    }

    @Test
    void without_an_active_trace_no_header_is_invented() {
        filter(filterWithTrace(null), "/api/session");

        assertFalse(headers.containsKey("X-Trace-ID"));
    }
}
