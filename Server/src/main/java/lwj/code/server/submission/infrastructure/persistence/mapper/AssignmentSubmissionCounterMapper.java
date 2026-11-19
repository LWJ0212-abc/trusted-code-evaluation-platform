package lwj.code.server.submission.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lwj.code.server.submission.infrastructure.persistence.entity.AssignmentSubmissionCounterEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface AssignmentSubmissionCounterMapper extends BaseMapper<AssignmentSubmissionCounterEntity> {

    /**
     * 插入提交计数记录（忽略重复）。
     * 如果插入的数据与表中已有的主键（Primary Key）或唯一索引（Unique Key）发生冲突，
     * 就静默跳过（Ignore）这条数据，不执行插入，也不会报错；如果没有冲突，则正常插入。
     *
     * @param assignmentId 作业的唯一标识 ID
     * @param studentId    学生的唯一标识 ID
     * @param maxAttempts  该作业允许的最大提交/尝试次数
     * @param updatedAt    记录的创建/更新时间
     * @return 影响的行数（成功插入返回 1，发生冲突被忽略则返回 0）
     */
    @Insert(
            """
INSERT IGNORE INTO assignment_submission_counter
(
    assignment_id,
    student_id,
    last_attempt_no,
    accepted_count,
    max_attempts,
    version,
    updated_at
)
VALUES
(
    #{assignmentId},
    #{studentId},
    1,
    0,
    #{maxAttempts},
    0,
    #{updatedAt}
)
"""
    )
    int insertIgnore(
            @Param("assignmentId") String assignmentId,
            @Param("studentId") String studentId,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("updatedAt") LocalDateTime updatedAt
    );


    @Select("""
    SELECT assignment_id,
           student_id,
           last_attempt_no,
           accepted_count,
           max_attempts,
           version,
           updated_at
    FROM assignment_submission_counter
    WHERE assignment_id = #{assignmentId}
      AND student_id = #{studentId}
    FOR UPDATE
    """)
    AssignmentSubmissionCounterEntity selectForUpdate(
            @Param("assignmentId") String assignmentId,
            @Param("studentId") String studentId
    );

}
