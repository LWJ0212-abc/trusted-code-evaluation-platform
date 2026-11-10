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

    @Autowired
    private IdentityDirectory identityDirectory;

    @Test
    void knownStudentMapsToPlatformUserAndCourseMembership() {
        ExternalIdentity identity = new ExternalIdentity(
                "http://127.0.0.1:8765/realms/tcep-m0",
                "50000000-0000-4000-8000-000000000001"
        );

        User user = identityDirectory.find(identity).orElseThrow();

        assertEquals(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                user.userId()
        );

        assertEquals(1, user.memberships().size());
        assertEquals(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                user.memberships().getFirst().courseId()
        );
        assertEquals(
                CourseRole.STUDENT,
                user.memberships().getFirst().role()
        );

        assertTrue(user.platformRoles().isEmpty());
    }

}
