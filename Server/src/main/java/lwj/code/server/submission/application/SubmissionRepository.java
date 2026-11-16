package lwj.code.server.submission.application;

import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;

import java.util.Optional;

/**
 * 提交记录仓储接口 (Repository)
 * <p>
 * 负责定义提交实体（Submission）的数据持久化与查询契约。
 * 遵循依赖倒置原则，通过抽象接口将底层数据库操作与核心业务逻辑解耦。
 */
public interface SubmissionRepository {

    /**
     * 保存或更新提交记录。
     *
     * @param submission 待保存的提交实体对象
     * @return 持久化后的提交实体（可能包含数据库回填的自增主键、默认值或审计时间）
     */
    Submission save(Submission submission);

    /**
     * 根据唯一标识查询提交记录。
     *
     * @param submissionId 提交记录的全局唯一 ID
     * @return 若找到则返回包含该实体的 Optional，否则返回 Optional.empty()
     */
    Optional<Submission> findById(String submissionId);

    /**
     * 根据幂等键查询历史提交记录。
     * <p>
     * 核心业务场景：用于分布式环境下的防重放与接口幂等性保障。
     * 当客户端因网络超时发起重试，或用户频繁点击时，通过此方法检查该提交是否已被处理，
     * 从而防止同一用户对同一作业产生重复的提交任务调度。
     *
     * @param ownerId        提交所属的用户 ID
     * @param assignmentId   对应的作业或题目 ID
     * @param idempotencyKey 客户端请求时携带的唯一幂等标识（如 UUID 或前端生成的业务 Token）
     * @return 若该幂等键对应的记录已存在，则返回实体 Optional，否则返回 Optional.empty()
     */
    Optional<Submission> findByIdempotencyKey(
            String ownerId,
            String assignmentId,
            String idempotencyKey
    );

    Submission updateStatus(Submission submission);
}