package lwj.code.server.identity.api;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.api.RequestTraceId;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUser;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "用户相关操作")
@RequiredArgsConstructor
public class UserController {

    private final RequestTraceId requestTraceId;

    @Operation(summary = "获取当前登录用户")
    @SecurityRequirement(name = "platformSession")
    @GetMapping("/me")
    public UserResponse me(
            @AuthenticationPrincipal PlatformOidcUser user,
            HttpServletRequest request
    ) {
        log.info("获取当前登陆的而用户");
        return UserResponse.from(
                user.platformUser(),
                requestTraceId.getOrCreate(request)
        );
    }
}
