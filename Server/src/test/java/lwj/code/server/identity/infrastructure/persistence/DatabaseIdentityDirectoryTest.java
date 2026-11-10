package lwj.code.server.identity.infrastructure.persistence;

import lwj.code.server.identity.application.IdentityDirectory;
import lwj.code.server.identity.domain.CourseRole;
import lwj.code.server.identity.domain.ExternalIdentity;
import lwj.code.server.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class DatabaseIdentityDirectoryTest {

    private static final String ISSUER = "http://127.0.0.1:8765/realms/tcep-m0";
    private static final UUID COURSE_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    private IdentityDirectory identityDirectory;

    @Test
    void knownStudentMapsToPlatformUserAndCourseMembership() {
        ExternalIdentity identity = new ExternalIdentity(
                ISSUER,
                "50000000-0000-4000-8000-000000000001"
        );

        User user = identityDirectory.find(identity).orElseThrow();

        assertEquals(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                user.userId()
        );

        assertEquals(1, user.memberships().size());
        assertEquals(
                COURSE_ID,
                user.memberships().getFirst().courseId()
        );
        assertEquals(
                CourseRole.STUDENT,
                user.memberships().getFirst().role()
        );

        assertTrue(user.platformRoles().isEmpty());
    }

    @Test
    void knownTaMapsToTaMembership() {
        ExternalIdentity identity = new ExternalIdentity(
                ISSUER,
                "50000000-0000-4000-8000-000000000004"
        );

        User user = identityDirectory.find(identity).orElseThrow();

        assertEquals(
                UUID.fromString("00000000-0000-4000-8000-000000000004"),
                user.userId()
        );
        assertEquals(1, user.memberships().size());
        assertEquals(COURSE_ID, user.memberships().getFirst().courseId());
        assertEquals(CourseRole.TA, user.memberships().getFirst().role());
        assertTrue(user.platformRoles().isEmpty());
    }

    @Test
    void knownOutsiderHasNoMemberships() {
        ExternalIdentity identity = new ExternalIdentity(
                ISSUER,
                "50000000-0000-4000-8000-000000000005"
        );

        User user = identityDirectory.find(identity).orElseThrow();

        assertEquals(
                UUID.fromString("00000000-0000-4000-8000-000000000005"),
                user.userId()
        );
        assertTrue(user.memberships().isEmpty());
        assertTrue(user.platformRoles().isEmpty());
    }

    @Test
    void unknownSubjectReturnsEmpty() {
        ExternalIdentity identity = new ExternalIdentity(
                ISSUER,
                "50000000-0000-4000-8000-999999999999"
        );

        assertTrue(identityDirectory.find(identity).isEmpty());
    }

    @Test
    void wrongIssuerReturnsEmpty() {
        ExternalIdentity identity = new ExternalIdentity(
                "http://127.0.0.1:8765/realms/other",
                "50000000-0000-4000-8000-000000000001"
        );

        assertTrue(identityDirectory.find(identity).isEmpty());
    }

}
