package lwj.code.server.bootstrap.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;

@Configuration(proxyBeanMethods = false)
public class OidcUserConfiguration {

    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }
}
