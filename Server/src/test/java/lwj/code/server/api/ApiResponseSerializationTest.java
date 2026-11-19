package lwj.code.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseSerializationTest {

    private static final tools.jackson.databind.ObjectMapper JACKSON3 =
            JsonMapper.builder().build();
    private static final ObjectMapper JSON_READER = new ObjectMapper();

    @Test
    void serializesSuccessResponseWithSnakeCaseFields() throws Exception {
        ApiResponse<TestData> response = ApiResponse.success(
                new TestData("submission-1", 1),
                "trace-123"
        );

        JsonNode json = JSON_READER.readTree(JACKSON3.writeValueAsString(response));

        assertEquals("SUCCESS", json.path("code").textValue());
        assertEquals("请求成功", json.path("message").textValue());
        assertEquals("trace-123", json.path("trace_id").textValue());
        assertTrue(json.path("timestamp").isTextual());
        assertFalse(json.path("retryable").booleanValue());
        assertEquals("submission-1", json.path("data").path("submission_id").textValue());
        assertEquals(1, json.path("data").path("attempt_no").intValue());
        assertTrue(json.has("field_errors"));
        assertTrue(json.path("field_errors").isArray());
        assertEquals(0, json.path("field_errors").size());
        assertTrue(json.has("resource_created"));
        assertTrue(json.path("resource_created").isNull());
        assertTrue(json.has("resource_id"));
        assertTrue(json.path("resource_id").isNull());
        assertFalse(json.has("msg"));
    }

    @Test
    void usesInjectedClockForResponseTimestamp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);

        ApiResponse<Void> response = ApiResponse.failure(
                "TEST_ERROR", "test", "trace-clock", false, clock);

        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), response.getTimestamp());
    }

    @Test
    void serializesFailureResponseWithNullDataAndEmptyFieldErrors() throws Exception {
        ApiResponse<Void> response = ApiResponse.failure(
                "IDEMPOTENCY_CONFLICT",
                "幂等键已用于不同的提交请求",
                "trace-409",
                false
        );

        JsonNode json = JSON_READER.readTree(JACKSON3.writeValueAsString(response));

        assertEquals("IDEMPOTENCY_CONFLICT", json.path("code").textValue());
        assertEquals("幂等键已用于不同的提交请求", json.path("message").textValue());
        assertEquals("trace-409", json.path("trace_id").textValue());
        assertTrue(json.has("data"));
        assertTrue(json.path("data").isNull());
        assertTrue(json.has("field_errors"));
        assertTrue(json.path("field_errors").isArray());
        assertEquals(0, json.path("field_errors").size());
        assertTrue(json.path("resource_created").isNull());
        assertTrue(json.path("resource_id").isNull());
    }

    @Test
    void serializesValidationErrorsWithSnakeCaseNestedFields() throws Exception {
        ApiResponse<Void> response = ApiResponse.validationFailure(
                "INVALID_REQUEST",
                "请求参数校验失败",
                "trace-400",
                List.of(new ApiResponse.FieldError("contentVersion", "不能为空"))
        );

        JsonNode json = JSON_READER.readTree(JACKSON3.writeValueAsString(response));
        JsonNode fieldError = json.path("field_errors").get(0);

        assertNotNull(fieldError);
        assertEquals("contentVersion", fieldError.path("field").textValue());
        assertEquals("不能为空", fieldError.path("reason").textValue());
        assertFalse(json.has("errors"));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record TestData(String submissionId, int attemptNo) {
    }
}
