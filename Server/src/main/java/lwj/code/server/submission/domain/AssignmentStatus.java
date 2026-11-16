package lwj.code.server.submission.domain;

/**
 * 作业状态
 */
/**
 * 作业/任务状态枚举
 * 用于标识当前作业或任务所处的生命周期阶段。
 */
public enum AssignmentStatus {

    /**
     * 草稿状态。
     * 此时作业正在创建或编辑中，尚未对学生/执行者可见。
     */
    DRAFT,

    /**
     * 已发布状态。
     * 此时作业已经正式对外发布，学生/执行者可以查看详情并进行提交操作。
     */
    PUBLISHED,

    /**
     * 已关闭状态。
     * 此时作业已过截止日期或被手动结束，通常不再接受任何新的提交。
     */
    CLOSED
}