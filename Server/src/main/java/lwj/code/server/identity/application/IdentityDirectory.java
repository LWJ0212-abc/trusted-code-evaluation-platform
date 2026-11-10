package lwj.code.server.identity.application;

import lwj.code.server.identity.domain.ExternalIdentity;
import lwj.code.server.identity.domain.User;

import java.util.Optional;

public interface IdentityDirectory {

    /**
     * 根据外部身份查找平台用户。
     *
     * @param identity OIDC issuer 和 subject 的组合
     * @return 已注册用户；未知身份返回 Optional.empty()
     */
       Optional<User> find(ExternalIdentity identity);
}
