package lwj.code.server.api;

import lwj.code.server.shared.error.ErrorCode;
import lwj.code.server.submission.application.exception.IdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.MissingRequestHeaderException;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private RequestTraceId requestTraceId;

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(requestTraceId, Clock.systemUTC());
        when(requestTraceId.getOrCreate(request)).thenReturn("trace-test");
    }

    @Test
    void mapsMissingIdempotencyHeaderToBadRequest() throws NoSuchMethodException {
        Method endpoint = getClass().getDeclaredMethod("endpoint", String.class);
        MethodParameter parameter = new MethodParameter(endpoint, 0);
        MissingRequestHeaderException exception =
                new MissingRequestHeaderException("Idempotency-Key", parameter);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingRequestHeader(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.MISSING_IDEMPOTENCY_KEY.name(), response.getBody().getCode());
        assertEquals("trace-test", response.getBody().getTraceId());
        assertEquals("缺少请求头: Idempotency-Key", response.getBody().getMessage());
    }

    @Test
    void mapsIllegalArgumentToBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("invalid input"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.INVALID_REQUEST.name(), response.getBody().getCode());
        assertEquals("invalid input", response.getBody().getMessage());
        assertEquals("trace-test", response.getBody().getTraceId());
    }

    @Test
    void mapsAccessDeniedToForbidden() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.ACCESS_DENIED.name(), response.getBody().getCode());
        assertEquals("无权访问该资源", response.getBody().getMessage());
    }

    @Test
    void mapsIdempotencyConflictToConflict() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleApplicationException(
                new IdempotencyConflictException("idempotency key already used"),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.name(), response.getBody().getCode());
        assertEquals("idempotency key already used", response.getBody().getMessage());
    }

    @SuppressWarnings("unused")
    private void endpoint(@RequestHeader("Idempotency-Key") String idempotencyKey) {
    }
}
