package lwj.code.server.submission.domain;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record EvaluationTask(
        /**
         * 任务 UUID
         */
        String evaluationTaskId,

        /**
         * 关联提交
         */
        String submissionId,

        /**
         * 作业 ID
         */
        String assignmentId,

        /**
         * 提交人 ID，和数据库列名一致
         */
        String studentId,

        /**
         * 本次评测使用的内容版本
         */
        String contentVersion,

        /**
         * 任务状态
         */
        EvaluationTaskStatus status,

        /**
         * 并发版本，也是事件 aggregate_revision 来源
         */
        long revision,

        /**
         * 已启动的执行次数
         */
        int attemptCount,

        /**
         * 创建时间
         */
        LocalDateTime createdAt,

        /**
         * 更新时间
         */
        LocalDateTime updatedAt
) {}
