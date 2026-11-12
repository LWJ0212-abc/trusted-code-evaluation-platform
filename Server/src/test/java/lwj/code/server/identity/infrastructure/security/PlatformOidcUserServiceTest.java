package lwj.code.server.identity.infrastructure.security;

import lwj.code.server.identity.application.IdentityDirectory;
import lwj.code.server.identity.domain.ExternalIdentity;
import lwj.code.server.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;





@ExtendWith(MockitoExtension.class)
class PlatformOidcUserServiceTest {

    private static final String ISSUER =
            "http://127.0.0.1:8765/realms/tcep-m0";

    private static final String SUBJECT =
            "50000000-0000-4000-8000-000000000001";

    @Mock
    private OidcUserService delegate;

    @Mock
    private IdentityDirectory identityDirectory;

    private PlatformOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new PlatformOidcUserService(
                delegate,
                identityDirectory
        );
    }

    @Test
    void registeredIdentityReturnsPlatformOidcUser() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser oidcUser = createOidcUser();

        User platformUser = new User(
                UUID.fromString(
                        "00000000-0000-4000-8000-000000000001"
                ),
                List.of(),
                Set.of()
        );

        when(delegate.loadUser(request))
                .thenReturn(oidcUser);

        when(identityDirectory.find(
                new ExternalIdentity(ISSUER, SUBJECT)
        )).thenReturn(Optional.of(platformUser));

        OidcUser result = service.loadUser(request);

        PlatformOidcUser platformOidcUser =
                assertInstanceOf(PlatformOidcUser.class, result);

        assertSame(platformUser, platformOidcUser.platformUser());
        assertEquals(oidcUser.getSubject(), result.getSubject());
        assertEquals(oidcUser.getClaims(), result.getClaims());
    }

    @Test
    void unknownIdentityIsRejected() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser oidcUser = createOidcUser();

        when(delegate.loadUser(request))
                .thenReturn(oidcUser);

        when(identityDirectory.find(
                new ExternalIdentity(ISSUER, SUBJECT)
        )).thenReturn(Optional.empty());

        OAuth2AuthenticationException exception =
                assertThrows(
                        OAuth2AuthenticationException.class,
                        () -> service.loadUser(request)
                );

        assertEquals(
                "identity_not_registered",
                exception.getError().getErrorCode()
        );
    }

    private OidcUser createOidcUser() {
        Instant issuedAt = Instant.now();

        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", ISSUER,
                        "sub", SUBJECT
                )
        );

        return new DefaultOidcUser(
                List.of(new OidcUserAuthority(idToken)),
                idToken
        );
    }
}




