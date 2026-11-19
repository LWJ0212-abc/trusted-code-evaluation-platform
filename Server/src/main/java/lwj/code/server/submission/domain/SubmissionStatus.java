package lwj.code.server.submission.domain;

/**
 * 提交处理状态。
 */
public enum SubmissionStatus {

    /** 已通过源对象校验并创建提交记录。 */
    ACCEPTED,

    /** 已写入评测队列，等待执行。 */
    QUEUED,

    /** 评测任务执行中。 */
    RUNNING,

    /** 评测成功完成。 */
    SUCCEEDED,

    /** 评测执行失败。 */
    FAILED,

    /** 提交未通过校验或被系统拒绝。 */
    REJECTED


}
