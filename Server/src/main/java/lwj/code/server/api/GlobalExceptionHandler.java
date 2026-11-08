package lwj.code.server.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lwj.code.server.shared.error.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;


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


    /**
     * 处理 @Valid参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        //将检验失败的字符串转化为List
        List<ApiErrorResponse.FieldError> fieldErrors = e
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage()
                ))
                .toList();
        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_REQUEST",
                "Request validation failed",
                requestTraceId.getOrCreate(request),
                false,
                false,
                null,
                fieldErrors
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理请求格式错误。
     * 包括 JSON 无法解析、字段类型错误和缺少必要请求参数。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse>handleInvalidRequest(
        Exception exception,
        HttpServletRequest request
    ){
        ApiErrorResponse response=new ApiErrorResponse(
                "INVALID_REQUEST",
                "Request format is invalid",
                requestTraceId.getOrCreate(request),
                false,
                false,
                null,
                List.of()
        );

        return ResponseEntity.badRequest().body(response);
    }


    /**
     * 处理自定义异常
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
        ApplicationException e,
        HttpServletRequest request
    ){
        ApiErrorResponse response=new ApiErrorResponse(
                e.getCode().name(),
                e.getSafeMessage(),
                requestTraceId.getOrCreate(request),
                e.isRetryable(),
                e.getResourceCreated(),
                e.getResourceId(),
                List.of()
        );
        return ResponseEntity.status(e.getCode().getStatus()).body(response);
    }


    /**
     * 处理全局异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(
        Exception e,
        HttpServletRequest request
    ){
        ApiErrorResponse response=new ApiErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                requestTraceId.getOrCreate(request),
                false,
                null,
                null,
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }




}
