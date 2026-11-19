package lwj.code.server.submission.application.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.api.UuidGenerator;
import lwj.code.server.course.application.AssignmentRepository;
import lwj.code.server.evaluation.infrastructure.EvaluationRequestedPayload;
import lwj.code.server.outbox.application.OutboxEventRepository;
import lwj.code.server.outbox.domain.OutboxAggregateType;
import lwj.code.server.outbox.domain.OutboxEvent;
import lwj.code.server.outbox.domain.OutboxEventStatus;
import lwj.code.server.outbox.domain.OutboxEventType;
import lwj.code.server.submission.application.*;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.course.domain.Assignment;
import lwj.code.server.submission.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Objects;

/**
 * 负责创建新的正式提交记录。
 * <p>
 * 创建过程中会校验作业和上传对象、分配提交序号并保存提交记录。
 * 方法上的事务保证计数器变更与提交记录写入一起提交或回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionCreator {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionCounterRepository assignmentSubmissionCounterRepository;
    private final UuidGenerator uuidGenerator;
    private final EvaluationTaskRepository evaluationTaskRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    /**
     * 在一个事务中校验并创建提交。
     * <p>
     * 如果保存提交记录时发生异常，事务会回滚此前的提交次数变更。
     * 本方法由外层 SubmissionService 调用，便于外层在事务回滚后处理幂等键冲突。
     *
     * @param command 创建提交所需的用户、作业、源对象和幂等键信息
     * @return 已保存的提交记录
     */
    @Transactional(rollbackFor=Exception.class)
    public Submission createSubmission(CreateSubmissionCommand command, SourceObject validatedObject,String traceId) {
        if (command == null) {
            throw new IllegalArgumentException("创建提交命令不能为空");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        validateCommandObject(command, validatedObject);

        //*1. 查询作业，并检查当前状态和时间是否允许提交。
        LocalDateTime now=LocalDateTime.now(clock);
        Assignment assignment=assignmentRepository.findById(command.assignmentId())
                .orElseThrow(() -> new IllegalStateException("作业不存在，assignmentId=" + command.assignmentId()));

        if (!assignment.canAcceptSubmissions(now)) {
            throw new IllegalArgumentException("当前作业不能提交");
        }

        if (!Objects.equals(assignment.contentVersion(), command.contentVersion())) {
            throw new IllegalArgumentException("作业内容版本不匹配");
        }
        //* 2. 在计数器仓储中分配本次提交序号。
        long nextNo=assignmentSubmissionCounterRepository.nextAttemptNo(assignment, command.ownerId());

        //* 3. 创建提交领域对象，初始状态为 QUEUED，表示评测任务已排队等待调度。
        Submission newSubmission=Submission.builder()
                .submissionId(uuidGenerator.nextUuid7().toString())
                .ownerId(command.ownerId())
                .assignmentId(command.assignmentId())
                .contentVersion(command.contentVersion())
                .sourceUploadId(command.uploadId())
                .attemptNo(nextNo)
                .idempotencyKey(command.idempotencyKey())
                .sourceSha256(validatedObject.sha256())
                .sourceSizeBytes(validatedObject.sizeBytes())
                .status(SubmissionStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .submittedAt(now)
                .version(0L)
                .build();

        //*4. 持久化提交记录，并返回创建结果。
        submissionRepository.save(newSubmission);
        //*5.创建EvalutaionTask并保存
        EvaluationTask task=EvaluationTask.builder()
                .evaluationTaskId(uuidGenerator.nextUuid7().toString())
                .submissionId(newSubmission.submissionId())
                .assignmentId(validatedObject.assignmentId())
                .studentId(command.ownerId())
                .contentVersion(newSubmission.contentVersion())
                .status(EvaluationTaskStatus.PENDING)
                .revision(0L)
                .attemptCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        evaluationTaskRepository.save(task);
        //*创建outbox事件
        //*构建提交内容
        EvaluationRequestedPayload payload=new EvaluationRequestedPayload(
                newSubmission.submissionId(),
                task.evaluationTaskId(),
                task.contentVersion(),
                null
        );
        String payloadJson=objectMapper.writeValueAsString(payload);
        OutboxEvent  event=OutboxEvent.builder()
                .eventId(uuidGenerator.nextUuid7().toString())
                .eventType(OutboxEventType.EVALUATION_REQUESTED)
                .schemaVersion(1)
                .aggregateType(OutboxAggregateType.EVALUATION_TASK)
                .aggregateId(task.evaluationTaskId())
                .aggregateRevision(task.revision())
                .traceId(traceId)
                .payloadJson(payloadJson)
                .occurredAt(now)
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .claimToken(null)
                .lastErrorCode(null)
                .publishedAt(null)
                .build();
        outboxEventRepository.save(event);
        return newSubmission;
    }

    private void validateCommandObject(CreateSubmissionCommand command, SourceObject validatedObject) {
        if (validatedObject == null) {
            throw new IllegalArgumentException("已校验的源对象 validatedObject 不能为空");
        }
        if (!Objects.equals(command.uploadId(), validatedObject.uploadId())) {
            throw new IllegalArgumentException("已校验源对象的 uploadId 与提交命令不一致");
        }

        if (!Objects.equals(command.assignmentId(), validatedObject.assignmentId())) {
            throw new IllegalArgumentException("已校验源对象不属于提交命令指定的作业");
        }

        if (!Objects.equals(command.ownerId(), validatedObject.ownerId())) {
            throw new IllegalArgumentException("已校验源对象不属于当前提交用户");
        }

        if (!Objects.equals(command.contentVersion(), validatedObject.contentVersion())) {
            throw new IllegalArgumentException("已校验源对象的内容版本与提交命令不一致");
        }

        if (command.sha256() == null || validatedObject.sha256() == null
                || !command.sha256().equalsIgnoreCase(validatedObject.sha256())) {
            throw new IllegalArgumentException("提交命令中的 SHA-256 与已校验源对象的摘要不一致");
        }
    }
}
