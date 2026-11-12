package lwj.code.server.identity.infrastructure.security;


import lombok.RequiredArgsConstructor;
import lwj.code.server.identity.application.IdentityDirectory;
import lwj.code.server.identity.domain.ExternalIdentity;
import lwj.code.server.identity.domain.User;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 自定义 Spring Security OAuth2 (OIDC) 的用户加载逻辑
 */

@RequiredArgsConstructor
@Component
public class PlatformOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;
    private final IdentityDirectory identityDirectory;

    @Override
    public @Nullable OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // *步骤 1: 让默认的 OidcUserService 去向第三方请求并加载用户信息。
        // 这一步会验证 Token、请求 UserInfo Endpoint，并返回标准的 OidcUser。
        OidcUser oidcUser=delegate.loadUser(userRequest);
        //* 步骤 2: 解析出用来标识这个第三方用户的唯一标识。
        // Issuer: 谁发的 Token（比如 https://accounts.google.com）
        // Subject (sub): 用户在那个平台上的唯一 ID
        ExternalIdentity identity=new ExternalIdentity(
                Objects.requireNonNull(oidcUser.getIssuer()).toString(),
                oidcUser.getSubject()
        );
        //* 步骤 3: 拿着第三方平台的标识，去你们自己的数据库里找对应的本地 User。
        // 如果找不到（Optional.empty），直接抛出 OAuth2AuthenticationException 异常，阻止登录。
        User user=identityDirectory.find(identity)
                .orElseThrow(()->new OAuth2AuthenticationException(
                        new OAuth2Error("identity_not_registered"),
                        "Identity is not registered"
                ));
        //*返回本平台的登陆用户凭证
        return  new PlatformOidcUser(
                oidcUser,
                user
        );
    }
}
