package lwj.code.server.submission.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.api.ApiResponse;
import lwj.code.server.api.RequestTraceId;
import lwj.code.server.identity.domain.User;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUser;
import lwj.code.server.submission.api.dto.CreateSubmissionRequest;
import lwj.code.server.submission.application.SubmissionService;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.submission.domain.Submission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

/**
 * 提交（Submission）相关的 RESTful API 控制器
 * 负责处理客户端发起的代码提交、作业上传等 HTTP 请求
 */
@RestController // 标识该类为 REST 控制器，方法返回值会自动序列化为 JSON
@RequiredArgsConstructor // Lombok 注解：自动为带有 final 修饰符的成员变量生成构造函数，用于依赖注入
@Slf4j // Lombok 注解：自动生成日志记录器 log
@RequestMapping("/api/v1/submissions") // 统一定义该控制器下所有接口的基础 URL 路径
@Tag(name = "提交相关接口") // Swagger/OpenAPI 注解：用于在接口文档中对该控制器进行分类和命名
public class SubmissionController {

    // 提交业务逻辑服务层
    private final SubmissionService submissionService;
    // 请求链路追踪 ID 工具类，用于日志和问题排查
    private final RequestTraceId requestTraceId;
    private final Clock clock;

    /**
     * 创建一个新的提交请求
     *
     * @param oidUser        当前已认证的登录用户（通过 OIDC 协议获取）
     * @param submissionDTO  客户端传入的提交数据 DTO，开启了 @Valid 数据校验
     * @param idempotencyKey 幂等性 Key，防止网络重试导致重复创建提交记录
     * @param request        原始的 HTTP 请求对象，用于提取或生成 Trace ID
     * @return 包含提交结果的统一 API 响应格式
     */
    @PostMapping() // 映射 HTTP POST 请求
    @Operation(summary = "创建提交请求") // Swagger 注解：定义该接口在文档中的摘要信息
    public ApiResponse<Submission> createSubmission(
            @AuthenticationPrincipal PlatformOidcUser oidUser, // 自动注入当前 Security 上下文中的用户信息
            @Valid @RequestBody CreateSubmissionRequest submissionDTO, // 解析请求体 JSON 并进行字段校验
            @RequestHeader("Idempotency-Key") String idempotencyKey, // 从请求头中强制获取幂等性 Key
            HttpServletRequest request
    ){
        // 记录接口被调用的初始日志（建议后续可以将 traceId 和 userId 加入日志以增强可观测性）
        log.info("创建上传请求");

        // 从认证主体中提取出我们系统的内部领域用户模型
        User platformUser = oidUser.platformUser();

        // 运用 CQRS/DDD 思想，将外部接收的 DTO 转换为内部应用层的命令（Command）对象
        // 剥离 HTTP 层与应用层的强耦合
        CreateSubmissionCommand command = CreateSubmissionCommand.builder()
                .assignmentId(submissionDTO.assignmentId()) // 关联的作业 ID
                .ownerId(platformUser.userId().toString())  // 提交者的用户 ID
                .contentVersion(submissionDTO.contentVersion()) // 内容版本号
                .uploadId(submissionDTO.uploadId()) // 对应的文件上传 ID
                .sha256(submissionDTO.sha256()) // 文件内容的防篡改 Hash 值
                .idempotencyKey(idempotencyKey) // 透传幂等键给服务层进行校验
                .build();

        // 获取当前请求的 Trace ID，如果请求头没有带，则会生成一个新的
        String traceId = requestTraceId.getOrCreate(request);

        // 调用应用层服务执行具体的创建提交业务逻辑
        Submission submission = submissionService.createSubmission(command, traceId);

        // 将领域模型（或结果）包装在统一的响应结构中，附带 Trace ID 返回给客户端
        return ApiResponse.success(submission, traceId, clock);
    }
}
