package lwj.code.server.identity.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lwj.code.server.identity.domain.CourseMembership;
import lwj.code.server.identity.domain.CourseRole;
import lwj.code.server.identity.domain.PlatformRole;
import lwj.code.server.identity.domain.User;
import lwj.code.server.identity.infrastructure.security.PlatformOidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID COURSE_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final String ISSUER =
            "http://127.0.0.1:8765/realms/tcep-m0";
    private static final String OIDC_SUBJECT =
            "50000000-0000-4000-8000-000000000001";
    private static final String ID_TOKEN_VALUE =
            "sensitive-id-token-must-not-be-returned";
    private static final String EMAIL_CLAIM =
            "private-user@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginEntryPointUsesFixedOauthRegistration() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/m0"));
    }

    @Test
    void authenticatedUserReceivesPlatformIdentityWithoutOidcSecrets() throws Exception {
        PlatformOidcUser principal = createPrincipal();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/me")
                                .with(oidcLogin().oidcUser(principal))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user_id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.memberships[0].course_id").value(COURSE_ID.toString()))
                .andExpect(jsonPath("$.memberships[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.platform_roles[0]").value("AUDITOR"))
                .andReturn();

        String traceIdHeader = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(traceIdHeader);

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertEquals(traceIdHeader, response.path("trace_id").asText());

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(ID_TOKEN_VALUE));
        assertFalse(body.contains(OIDC_SUBJECT));
        assertFalse(body.contains(ISSUER));
        assertFalse(body.contains(EMAIL_CLAIM));
        assertFalse(body.contains("\"id_token\""));
        assertFalse(body.contains("\"access_token\""));
        assertFalse(body.contains("\"sub\""));
        assertFalse(body.contains("\"iss\""));
        assertFalse(body.contains("platform_session"));
    }

    @Test
    void unauthenticatedRequestReturnsStandardJson401WithMatchingTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.field_errors").isArray())
                .andReturn();

        String traceIdHeader = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(traceIdHeader);

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertEquals(traceIdHeader, response.path("trace_id").asText());
    }

    private PlatformOidcUser createPrincipal() {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                ID_TOKEN_VALUE,
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", ISSUER,
                        "sub", OIDC_SUBJECT,
                        "email", EMAIL_CLAIM
                )
        );
        OidcUser delegate = new DefaultOidcUser(
                List.of(new OidcUserAuthority(idToken)),
                idToken
        );
        User platformUser = new User(
                USER_ID,
                List.of(new CourseMembership(COURSE_ID, CourseRole.STUDENT)),
                Set.of(PlatformRole.AUDITOR)
        );

        return new PlatformOidcUser(delegate, platformUser);
    }
}
