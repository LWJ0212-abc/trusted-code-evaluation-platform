package lwj.code.server.bootstrap.security;


import lombok.RequiredArgsConstructor;
import lwj.code.server.api.SecurityErrorWriter;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUserService;
import lwj.code.server.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 第一关：权限校验 (authorizeHttpRequests)
 * 当一个请求（比如 /api/v1/user/info）打到后端时，Spring Security 会先用 authorizeHttpRequests 里的规则去比对。
 *
 * 如果请求的是放行名单里的路径（如 /login/xxx），直接放行，结束。
 *
 * 如果请求不在放行名单里（命中 anyRequest().authenticated()），Spring 就会检查当前请求有没有合法的登录凭证（Session 或 Token）。
 *
 * 如果没有登录凭证，它不会自动去走 OAuth2，而是直接抛出一个“未认证异常 (AuthenticationException)”。
 *
 * 第二关：异常捕获 (exceptionHandling)
 * 刚才抛出的异常，会被这里的 exceptionHandling 拦截下来。
 *
 * 因为你自定义了 authenticationEntryPoint，Spring 就不再执行“默认重定向到第三方登录页”的动作了，而是执行你的 securityErrorWriter，给前端返回一个格式化的 JSON 报错（内容是 ErrorCode.UNAUTHENTICATED）。
 *
 * 到这里，这个 HTTP 请求就结束了。
 *
 * 第三关：前端响应与触发登录
 * 前端（Vue/React等）收到后端返回的 UNAUTHENTICATED JSON 报错后，知道用户还没登录。于是，前端通过代码强制页面跳转到第三方登录的触发地址（通常是 Spring Security 默认的 /oauth2/authorization/{registrationId}）。
 *
 * 第四关：OAuth2 登录处理 (oauth2Login)
 * 用户在第三方页面（比如微信、Google）扫码或点击同意后，第三方平台会把用户重定向回你配置的回调地址：/api/v1/auth/callback。
 *
 * 此时，你配置的 oauth2Login 里的过滤器才会真正开始工作。
 *
 * 它会拦截住 /api/v1/auth/callback 这个请求，拿着里面的 Code 去向第三方平台换取 Token，然后加载用户信息，最后把用户状态标记为“已登录”。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final SecurityErrorWriter securityErrorWriter;
    private final PlatformOidcUserService platformOidcUserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity security) {
        security
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers(
                                        "/error",
                                        "/oauth2/**",
                                        "/login/**",
                                        "/api/v1/auth/callback"
                                ).permitAll()
                                .anyRequest().authenticated()
                )
                .exceptionHandling(
                        exceptions -> exceptions
                                .authenticationEntryPoint(
                                        (request, response, exception) -> securityErrorWriter
                                                .write(
                                                        request,
                                                        response,
                                                        ErrorCode.UNAUTHENTICATED,
                                                        "Authentication is required",
                                                        false
                                                )
                                )
                                .accessDeniedHandler(
                                        ((request, response, exception) -> securityErrorWriter
                                                .write(
                                                        request,
                                                        response,
                                                        ErrorCode.ACCESS_DENIED,
                                                        "Access is denied",
                                                        false
                                                ))
                                )

                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(
                                userInfo -> userInfo.oidcUserService(platformOidcUserService))
                        .redirectionEndpoint(
                                endpoint -> endpoint.baseUri("/api/v1/auth/callback")
                        )
                );
        return security.build();
    }

    @Bean
    public OidcUserService oidcUserService(){
        return new OidcUserService();
    }
}