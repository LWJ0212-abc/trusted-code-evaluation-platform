package lwj.code.server.identity.domain;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/**
 * 表示平台用户在某一门课程中的成员关系。
 *
 * <p>课程角色由平台的成员目录决定，不从 Keycloak 用户声明中推断。
 * 一个用户可以同时属于多门课程，并且在不同课程中拥有不同角色。</p>
 *
 * @param courseId 课程的内部稳定标识
 * @param role     用户在该课程中的角色，例如学生、教师或助教
 */

public record CourseMembership(
        UUID courseId,
        CourseRole role
) {

    /**
     * 拒绝不完整的成员关系，避免后续授权判断遇到空课程或空角色。
     */
    public CourseMembership {
        Objects.requireNonNull(courseId, "courseId is required");
        Objects.requireNonNull(role, "role is required");
    }
}
