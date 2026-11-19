package lwj.code.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lwj.code.server.shared.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Clock;

/**
 * 处理认证，授权，csrf异常
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;
    private final RequestTraceId requestTraceId;
    private final Clock clock;

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode code,
            String safeMessage,
            boolean retryable
    ) throws IOException {
        //判断是否已经提交response
        if (response.isCommitted()){
            return;
        }

        String traceId=requestTraceId.getOrCreate(request);

        ApiResponse<Void> apiresponse=ApiResponse.failure(
                code.toString(),
                safeMessage,
                traceId,
                retryable,
                clock
        );

        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Trace-Id", traceId);

        //使用统一的ObjectMapper序列化
        objectMapper.writeValue(
                response.getOutputStream(),
                apiresponse
        );
    }
}
