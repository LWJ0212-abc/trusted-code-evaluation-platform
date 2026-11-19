package lwj.code.server.submission.domain;

/**
 * 评测任务状态枚举
 */
public enum EvaluationTaskStatus {

    /**
     * 待处理：任务已创建，等待分配或调度执行
     */
    PENDING,

    /**
     * 运行中：任务正在进行评测逻辑
     */
    RUNNING,

    /**
     * 等待重试：任务执行遇到临时性故障，等待进行下一次重试
     */
    RETRY_WAIT,

    /**
     * 成功：评测任务已顺利执行完成
     */
    SUCCEEDED,

    /**
     * 失败：评测任务最终失败（如达到最大重试次数或遇到不可恢复的错误）
     */
    FAILED,

    /**
     * 死信：任务无法被正常处理，已进入死信状态或被永久废除
     */
    DEAD_LETTER
}