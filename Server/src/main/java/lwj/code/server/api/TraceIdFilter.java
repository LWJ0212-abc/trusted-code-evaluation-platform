package lwj.code.server.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个 HTTP 请求生成 trace ID。
 *
 * trace ID 会同时放入：
 * 1. Request attribute，供异常处理器读取；
 * 2. X-Trace-Id 响应头，供客户端排查问题；
 * 3. 日志 MDC，供服务端关联同一次请求的日志。
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private final RequestTraceId requestTraceId;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //统一生成和保存trace Id
        String traceId=requestTraceId.getOrCreate(request);
        //返回给客户端
        response.setHeader("X-Trace-Id",traceId);
        //添加到当前线程上下文
        MDC.put("trace_id",traceId);
        try{
            filterChain.doFilter(request,response);
        }finally {
            MDC.remove("trace_id");
        }
    }
}
