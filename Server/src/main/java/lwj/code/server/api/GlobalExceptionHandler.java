package lwj.code.server.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lwj.code.server.shared.error.ApplicationException;
import lwj.code.server.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.time.Clock;


/**
 * 将 Controller 和应用层抛出的异常统一转换为标准 HTTP 错误响应。
 *
 * 注意：Spring Security Filter 中发生的认证、授权和 CSRF 异常
 * 不一定会进入这里，需要由 Security 配置单独处理。
 */
@RestControllerAdvice       //等同于ControllerAdvice+ResoponseBody . 打上全局异常大管家的标签
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final RequestTraceId requestTraceId;
    private final Clock clock;


    /**
     * 处理 @Valid参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        //将检验失败的字符串转化为List
        List<ApiResponse.FieldError> fieldErrors = e
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage()
                ))
                .toList();

        ApiResponse<Void> response=ApiResponse.validationFailure(
                "INVALID_REQUEST",
                "Request validation failed",
                requestTraceId.getOrCreate(request),
                fieldErrors,
                clock
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理请求格式错误。
     * 包括 JSON 无法解析、字段类型错误和缺少必要请求参数。
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>>handleInvalidRequest(
        Exception exception,
        HttpServletRequest request
    ){
        ApiResponse<Void> response=ApiResponse.failure(
                "INVALID_REQUEST",
                "Request format is invalid",
                requestTraceId.getOrCreate(request),
                false,
                clock
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理提交接口缺少幂等请求头的情况。
     * 幂等键是提交创建协议的一部分，因此使用专用错误码，便于客户端准确修复请求。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        String message = "缺少请求头: " + exception.getHeaderName();
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.MISSING_IDEMPOTENCY_KEY.name(),
                "缺少请求头: Idempotency-Key",
                requestTraceId.getOrCreate(request),
                false,
                clock
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理应用层参数错误。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.INVALID_REQUEST.name(),
                e.getMessage() == null
                        ? "Request is invalid"
                        : e.getMessage(),
                requestTraceId.getOrCreate(request),
                false,
                clock
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理已认证用户访问无权资源的情况。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException e,
            HttpServletRequest request
    ) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.ACCESS_DENIED.name(),
                "无权访问该资源",
                requestTraceId.getOrCreate(request),
                false,
                clock
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }


    /**
     * 处理自定义异常
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(
        ApplicationException e,
        HttpServletRequest request
    ){
       ApiResponse<Void> response=ApiResponse.failure(
                e.getCode().name(),
                e.getSafeMessage(),
                requestTraceId.getOrCreate(request),
                e.isRetryable(),
                clock
        );
        return ResponseEntity.status(e.getCode().getStatus()).body(response);
    }


    /**
     * 处理全局异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
        Exception e,
        HttpServletRequest request
    ){
        ApiResponse<Void> response=ApiResponse.failure(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                requestTraceId.getOrCreate(request),
                false,
                clock
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }




}
