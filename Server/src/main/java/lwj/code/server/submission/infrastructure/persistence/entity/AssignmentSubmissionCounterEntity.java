package lwj.code.server.submission.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 作业提交计数器数据实体 (Data Object)
 * <p>
 * 映射数据库表: assignment_submission_counter
 * 职责: 持久层对象，仅用于 MyBatis-Plus 数据库交互。记录学生针对特定作业的提交限制与通过情况。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("assignment_submission_counter")
public class AssignmentSubmissionCounterEntity {

    /** 作业 ID。 */
    @TableField("assignment_id")
    private String assignmentId;

    /** 用户 ID。 */
    @TableField("student_id")
    private String studentId;

    /** 最近一次分配的提交序号。 */
    @TableField("last_attempt_no")
    private Long lastAttemptNo;

    /** 已通过的提交次数。 */
    @TableField("accepted_count")
    private Integer acceptedCount;

    /** 最大允许提交次数。 */
    @TableField("max_attempts")
    private Integer maxAttempts;

    /** 乐观锁版本号。 */
    @Version
    @TableField("version")
    private Long version;

    /** 最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
