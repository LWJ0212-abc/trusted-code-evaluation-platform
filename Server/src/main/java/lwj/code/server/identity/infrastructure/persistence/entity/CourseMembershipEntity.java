package lwj.code.server.identity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lwj.code.server.identity.domain.CourseRole;

import java.time.LocalDateTime;


/**
 * course_membership 表的持久化实体。
 *
 * 表示平台用户与课程之间的成员关系。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("course_membership")
public class CourseMembershipEntity {

    /**
     * 课程内部 ID。
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 平台内部用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 用户在课程中的角色。
     */
    @TableField("role")
    private CourseRole role;

    /**
     * 课程成员关系创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
