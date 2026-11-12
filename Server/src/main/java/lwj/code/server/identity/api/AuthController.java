package lwj.code.server.identity.api;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.api.RequestTraceId;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "用户相关操作")
@RequiredArgsConstructor
public class AuthController {

    //生成追踪id
    private final RequestTraceId requestTraceId;

    @Operation(summary = "获取当前登录用户")
    @SecurityRequirement(name = "platformSession")
    @GetMapping("/me")
    public UserResponse me(
            @AuthenticationPrincipal PlatformOidcUser user,
            HttpServletRequest request,
            CsrfToken csrfToken
    ) {
        // 访问一次 Token，确保 CookieCsrfTokenRepository 为 SPA 下发可读的 XSRF-TOKEN。
        csrfToken.getToken();
        log.info("获取当前登陆的用户");
        return UserResponse.from(
                user.platformUser(),
                requestTraceId.getOrCreate(request)
        );
    }

    /**
     * 用户登陆，进行重定向到spring security自带的/oauth2/authorization/m0
     */
    @Operation(summary = "用户登陆")
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        //让浏览器自动去访问另一个地址
        response.sendRedirect("/oauth2/authorization/m0");
    }
}
