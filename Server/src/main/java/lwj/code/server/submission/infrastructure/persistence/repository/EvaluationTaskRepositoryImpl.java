package lwj.code.server.submission.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.submission.application.EvaluationTaskRepository;
import lwj.code.server.submission.domain.EvaluationTask;
import lwj.code.server.submission.infrastructure.persistence.entity.EvaluationTaskEntity;
import lwj.code.server.submission.infrastructure.persistence.mapper.EvaluationTaskMapper;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class EvaluationTaskRepositoryImpl implements EvaluationTaskRepository {

    private final EvaluationTaskMapper evaluationTaskMapper;

    /**
     * @param task 待保存的评测任务对象
     */
    @Override
    public EvaluationTask save(EvaluationTask task) {
        if (task==null){
            throw new IllegalArgumentException("评测任务不能为空");
        }
        EvaluationTaskEntity entity=toEntity(task);
        int rows = evaluationTaskMapper.insert(entity);
        if (rows != 1) {
            throw new IllegalStateException("插入评估任务失败");
        }
        return task;
    }


    public EvaluationTaskEntity toEntity(EvaluationTask task){
        return EvaluationTaskEntity.builder()
                .evaluationTaskId(task.evaluationTaskId())
                .submissionId(task.submissionId())
                .assignmentId(task.assignmentId())
                .studentId(task.studentId())
                .contentVersion(task.contentVersion())
                .status(task.status().toString())
                .revision(task.revision())
                .attemptCount(task.attemptCount())
                .createdAt(task.createdAt())
                .updatedAt(task.updatedAt())
                .build();
    }
}
