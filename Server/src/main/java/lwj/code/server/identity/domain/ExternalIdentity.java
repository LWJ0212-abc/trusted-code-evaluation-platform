package lwj.code.server.identity.domain;

/**
 * 外部身份标识
 * 用于唯一标识来自外部认证中心（如 OIDC/OAuth2 提供商）的用户。
 * 在 OIDC 规范中，通常由 {@code issuer} (签发者) 和 {@code subject} (主题，即用户唯一ID) 
 * 组合起来构成一个全局唯一的用户标识。
 *
 * @param issuer  签发者标识 (例如："https://accounts.google.com" 或你本地配置的 Keycloak URL)
 * @param subject 用户在签发者处的唯一标识 (例如：OIDC id_token 中的 "sub" 字段值)
 */
public record ExternalIdentity(
        String issuer,
        String subject
) {
    /**
     * 紧凑型构造函数 (Compact Constructor)
     * 在 record 实例化时自动调用，用于进行参数的合法性校验。
     */
    public ExternalIdentity {
        // 校验签发者不能为空或纯空白字符
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer is required");
        }

        // 校验用户唯一标识不能为空或纯空白字符
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
    }
}