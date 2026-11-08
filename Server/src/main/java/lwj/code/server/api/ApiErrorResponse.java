package lwj.code.server.api;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiErrorResponse(
        String code,    //错误码
        String message, // 错误提示消息
        String traceId, //链路追踪ID
        boolean retryable,  //是否可以重试
        Boolean resourceCreated,    //有时候请求虽然报错了，但可能资源已经部分创建成功，这个字段用于告知客户端状态
        UUID resourceId,    //如果涉及到具体某个资源的操作报错（或者部分创建）
        List<FieldError> fieldErrors    //专门用于存放表单/参数校验失败的明细列表
) {
    /**
     *构造方法
     */
    public ApiErrorResponse{
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        fieldErrors = fieldErrors == null
                ? List.of()
                : List.copyOf(fieldErrors);
    }
    //内部记录为static
    public record FieldError(
            String field,
            String reason
    ){
        public FieldError{
            Objects.requireNonNull(field, "field must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
