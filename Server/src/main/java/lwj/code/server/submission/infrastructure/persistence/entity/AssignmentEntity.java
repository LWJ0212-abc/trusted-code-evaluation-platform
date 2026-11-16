package lwj.code.server.submission.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lwj.code.server.submission.domain.AssignmentStatus;

import java.time.LocalDateTime;

/**
 * 作业/任务实体类
 * 对应数据库中的 assignment 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("assignment")
public class AssignmentEntity {

    /**
     * 作业唯一标识 ID（主键）
     */
    @TableId
    private String assignmentId;

    /**
     * 所属课程 ID
     * 用于关联该作业属于哪个特定的课程
     */
    private String courseId;

    /**
     * 作业标题
     */
    private String title;

    /**
     * 作业详细描述或要求
     */
    private String description;

    /**
     * 内容版本号
     * 用于追踪作业内容的修改版本，防止并发覆盖或支持版本回退
     */
    private String contentVersion;

    /**
     * 创建人 ID
     * 记录是哪位教师或管理员创建了此作业
     */
    private String createdBy;

    /**
     * 作业状态
     * 对应 AssignmentStatus 枚举（DRAFT: 草稿, PUBLISHED: 已发布, CLOSED: 已关闭）
     * 数据库中默认值为 'DRAFT'
     */
    private AssignmentStatus status;

    /**
     * 作业开放时间
     * 到达此时间后，学生才能看到或开始进行该作业
     */
    private LocalDateTime opensAt;

    /**
     * 作业截止时间
     * 超过此时间后，通常不允许提交或标记为逾期提交
     */
    private LocalDateTime dueAt;

    /**
     * 最大允许尝试/提交次数
     * 限制学生可以重新提交该作业的次数
     */
    private Integer maxAttempts;

    /**
     * 记录创建时间
     * 数据库默认值为 CURRENT_TIMESTAMP
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间
     * 数据库默认值为 CURRENT_TIMESTAMP，更新时自动刷新
     */
    private LocalDateTime updatedAt;
}