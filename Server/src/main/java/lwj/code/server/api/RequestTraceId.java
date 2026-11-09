package lwj.code.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RequestTraceId {


    private static final String ATTRIBUTE=RequestTraceId.class.getName()+".value";

    /**
     * 获取当前请求的追踪编号
     * 正常情况下由 TraceIdFilter 在请求进入系统时生成；
     * UUID 只是防御性兜底，避免错误响应缺少 trace_id。
     */
    public String getOrCreate(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);

        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        String traceId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, traceId);
        return traceId;
    }

}
