package lwj.code.server.submission.application;

import lwj.code.server.submission.domain.EvaluationTask;

/**
 * 评测任务仓储接口
 * 用于处理评测任务领域对象的持久化操作
 */
public interface EvaluationTaskRepository {

    /**
     * 新增评测任务
     *
     * @param task 待新增的评测任务对象
     * @return 已新增的评测任务对象
     */
    EvaluationTask save(EvaluationTask task);
}
