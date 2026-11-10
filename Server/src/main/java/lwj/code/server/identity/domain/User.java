package lwj.code.server.identity.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 表示平台内部用户 ID 与平台授权信息
 */
public record User(
        UUID userId,
        List<CourseMembership> memberships,
        Set<PlatformRole> platformRoles
) {
    public User{
        Objects.requireNonNull(userId,"userId is required");
        memberships=List.copyOf(memberships);
        platformRoles=Set.copyOf(platformRoles);
    }
}
