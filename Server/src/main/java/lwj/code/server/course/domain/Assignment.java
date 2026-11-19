package lwj.code.server.course.domain;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 作业领域模型（配置快照）
 * 采用 record 定义，天生不可变，适合作为领域内的数据流转载体。
 *
 * @param assignmentId   作业唯一标识
 * @param courseId       关联的课程 ID
 * @param title          作业标题
 * @param description    作业具体描述
 * @param contentVersion 内容版本号（每次修改内容可生成新版本快照）
 * @param createdBy      创建者 ID
 * @param status         当前状态（DRAFT, PUBLISHED, CLOSED）
 * @param opensAt        开放时间（允许为空，为空表示创建即开放）
 * @param dueAt          截止时间（允许为空，为空表示无截止期限）
 * @param maxAttempts    最大允许提交次数
 * @param createdAt      快照创建时间
 * @param updatedAt      快照更新时间
 */
@Builder(toBuilder = true)
public record Assignment(
        String assignmentId,
        String courseId,
        String title,
        String description,
        String contentVersion,
        String createdBy,
        AssignmentStatus status,
        LocalDateTime opensAt,
        LocalDateTime dueAt,
        Integer maxAttempts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 核心业务规则：判断当前时间下，该作业是否可以接受学生提交。
     *
     * @param now 传入需要对比的当前时间（由调用方传入，避免隐式依赖系统时钟，方便单元测试）
     * @return 如果状态已发布，且当前时间在开放和截止时间之间，则返回 true
     */
    public boolean canAcceptSubmissions(LocalDateTime now) {
        return status == AssignmentStatus.PUBLISHED
                && (opensAt == null || !now.isBefore(opensAt))
                && (dueAt == null || !now.isAfter(dueAt));
    }
}
