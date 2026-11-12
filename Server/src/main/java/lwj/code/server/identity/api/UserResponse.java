package lwj.code.server.identity.api;

import lwj.code.server.identity.domain.CourseRole;
import lwj.code.server.identity.domain.PlatformRole;
import lwj.code.server.identity.domain.User;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserResponse(
        UUID userId,
        List<MembershipResponse> memberships,
        String traceId,
        List<PlatformRole> platformRoles
) {
    public static UserResponse from(User user, String traceId) {

        List<MembershipResponse> membershipResponses=user.memberships()
                .stream()
                .map(member->new MembershipResponse(
                        member.courseId(),
                        member.role()
                ))
                .toList();
        List<PlatformRole> roles=List.copyOf(user.platformRoles());

        return new UserResponse(
                user.userId(),
                membershipResponses,
                traceId,
               roles
        );
    }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MembershipResponse(
            UUID courseId,
            CourseRole role
    ) {

    }
}
