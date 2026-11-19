package lwj.code.server.submission.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测任务实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("evaluation_task")
public class EvaluationTaskEntity {

    /**
     * 任务 UUID
     */
    @TableId(value = "evaluation_task_id",type = IdType.INPUT)
    private String evaluationTaskId;

    /**
     * 关联提交
     */
    @TableField("submission_id")
    private String submissionId;

    /**
     * 作业 ID
     */
    @TableField("assignment_id")
    private String assignmentId;

    /**
     * 提交人 ID，和数据库列名一致
     */
    @TableField("student_id")
    private String studentId;

    /**
     * 本次评测使用的内容版本
     */
    @TableField("content_version")
    private String contentVersion;

    /**
     * 任务状态 (对应 EvaluationTaskStatus 枚举)
     */
    @TableField("status")
    private String status;

    /**
     * 并发版本，也是事件 aggregate_revision 来源（使用 @Version 实现乐观锁）
     */
    @Version
    @TableField("revision")
    private Long revision;

    /**
     * 已启动的执行次数
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

}