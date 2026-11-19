package lwj.code.server.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;
    private boolean retryable;
    private Boolean resourceCreated;
    private UUID resourceId;
    private List<FieldError> fieldErrors = List.of();

    private ApiResponse(
            String code,
            String message,
            T data,
            String traceId,
            Instant timestamp,
            boolean retryable,
            Boolean resourceCreated,
            UUID resourceId,
            List<FieldError> fieldErrors
    ) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.data = data;
        this.traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.retryable = retryable;
        this.resourceCreated = resourceCreated;
        this.resourceId = resourceId;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return success(data, traceId, Clock.systemUTC());
    }

    public static <T> ApiResponse<T> success(T data, String traceId, Clock clock) {
        return new ApiResponse<>(
                "SUCCESS",
                "请求成功",
                data,
                traceId,
                Instant.now(clock),
                false,
                null,
                null,
                List.of()
        );
    }

    public static ApiResponse<Void> failure(
            String code,
            String message,
            String traceId,
            boolean retryable
    ) {
        return failure(code, message, traceId, retryable, Clock.systemUTC());
    }

    public static ApiResponse<Void> failure(
            String code,
            String message,
            String traceId,
            boolean retryable,
            Clock clock
    ) {
        return new ApiResponse<>(
                code,
                message,
                null,
                traceId,
                Instant.now(clock),
                retryable,
                null,
                null,
                List.of()
        );
    }

    public static ApiResponse<Void> validationFailure(
            String code,
            String message,
            String traceId,
            List<FieldError> fieldErrors
    ) {
        return validationFailure(code, message, traceId, fieldErrors, Clock.systemUTC());
    }

    public static ApiResponse<Void> validationFailure(
            String code,
            String message,
            String traceId,
            List<FieldError> fieldErrors,
            Clock clock
    ) {
        return new ApiResponse<>(
                code,
                message,
                null,
                traceId,
                Instant.now(clock),
                false,
                null,
                null,
                fieldErrors
        );
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FieldError(String field, String reason) {
        public FieldError {
            Objects.requireNonNull(field, "field must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
