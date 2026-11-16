package lwj.code.server.submission.application;

import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;

/**
 * 提交记录应用服务接口 (Application Service)
 * <p>
 * 负责编排与“代码提交 (Submission)”相关的核心业务用例。
 * 作为领域驱动设计 (DDD) 中的应用层，该服务本身不包含复杂的业务规则，而是负责：
 * 1. 控制数据库事务边界 (Transaction Boundaries)。
 * 2. 处理用例级的权限与安全校验。
 * 3. 协调领域模型 (Domain Model)、仓储 (Repository) 以及外部组件（如任务调度引擎）来完成具体业务。
 */
public interface SubmissionService {

    /**
     * 创建新的代码评测提交。
     * <p>
     * 核心业务流程通常包含：
     * 1. 幂等性校验：通过 Command 中携带的幂等键（IdempotencyKey）防止重复提交。
     * 2. 实体构建与持久化：将 Command 转化为 Submission 领域实体并保存到数据库。
     * 3. 任务分发：保存成功后，通常会发布领域事件或直接调用外部组件，将评测任务下发给沙箱环境或分布式执行引擎。
     *
     * @param command 包含创建提交所需全部上下文参数的命令对象（CQRS 模式中的 Command）
     * @return 创建成功并持久化后的提交实体
     */
    Submission createSubmission(
            CreateSubmissionCommand command
    );

    /**
     * 获取指定的提交记录详情。
     * <p>
     * 强制要求传入 ownerId 是横向越权保护（BOLA/IDOR 防御）的典型实践。
     * 它确保只有该提交的拥有者（当前登录用户）才能查阅此提交的代码和评测结果，
     * 防止用户通过遍历 submissionId 窃取他人的代码。
     *
     * @param submissionId 待查询的提交记录 ID
     * @param ownerId      当前发起请求的用户 ID（通常从 SecurityContext / JWT 中解析获得）
     * @return 查询到的提交实体（若记录不存在或归属不匹配，具体实现中通常抛出 NotFound 或 AccessDenied 异常）
     */
    Submission getSubmission(
            String submissionId,
            String ownerId
    );

    /**
     * 更新提交任务的评测状态。
     * <p>
     * 场景说明：此方法通常不是由前端直接调用的，而是由后端的评测工作节点 (Worker)
     * 在处理代码编译、运行、比对后，通过回调或消息队列异步调用的。
     * 用于驱动 Submission 状态机的流转（如 PENDING -> RUNNING -> SUCCESS / FAILED）。
     *
     * @param submissionId 待更新状态的提交记录 ID
     * @param status       最新的评测状态（如编译失败、答案正确、运行超时等）
     * @return 状态流转后的提交实体
     */
    Submission updateStatus(
            String submissionId,
            SubmissionStatus status
    );
}