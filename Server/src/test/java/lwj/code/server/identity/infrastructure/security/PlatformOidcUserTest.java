package lwj.code.server.identity.infrastructure.security;

import lwj.code.server.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
public class PlatformOidcUserTest {
    @Test
    void exposesPlatformUserAndDelegatesVerifiedOidcUser() {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", "http://127.0.0.1:8765/realms/tcep-m0",
                        "sub", "50000000-0000-4000-8000-000000000001"
                )
        );

        OidcUser delegate = new DefaultOidcUser(
                List.of(new OidcUserAuthority(idToken)),
                idToken
        );

        User platformUser = new User(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                List.of(),
                Set.of()
        );

        PlatformOidcUser principal =
                new PlatformOidcUser(delegate, platformUser);

        assertSame(platformUser, principal.platformUser());
        assertSame(idToken, principal.getIdToken());
        assertEquals(delegate.getName(), principal.getName());
        assertEquals(delegate.getAuthorities(), principal.getAuthorities());
        assertEquals(delegate.getAttributes(), principal.getAttributes());
        assertEquals(delegate.getClaims(), principal.getClaims());
        assertEquals(delegate.getUserInfo(), principal.getUserInfo());
    }
}
