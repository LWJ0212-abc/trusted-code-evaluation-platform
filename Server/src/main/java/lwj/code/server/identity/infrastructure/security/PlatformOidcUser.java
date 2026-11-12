package lwj.code.server.identity.infrastructure.security;

import lwj.code.server.identity.domain.User;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 平台认证成功后的用户主体。
 *
 * 保留已经由 Spring Security 验证的 OIDC 用户信息，
 * 同时携带平台内部用户及其授权信息。
 */
public class PlatformOidcUser implements OidcUser {


    // Spring Security 原始解析出来的 OIDC 用户（提供底层 OIDC 协议数据）
    private final OidcUser delegate;
    // 通过 DatabaseIdentityDirectory 从数据库查出来的核心领域用户
    private final User platformUser;

    public PlatformOidcUser(OidcUser delegate, User platformUser) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate is required"
        );
        this.platformUser = Objects.requireNonNull(
                platformUser,
                "platformUser is required"
        );
    }

    public User platformUser() {
        return platformUser;
    }

    @Override
    public Map<String, Object> getClaims() {
        return this.delegate.getClaims();
    }

    @Override
    public @Nullable OidcUserInfo getUserInfo() {
        return this.delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return this.delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return this.delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return this.delegate.getName();
    }
}
