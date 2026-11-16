package lwj.code.server.submission.application.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.submission.application.*;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.submission.domain.Assignment;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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
    private final SourceObjectValidator sourceObjectValidator;
    private final SourceObjectRepository objectRepository;

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
    public Submission createSubmission(CreateSubmissionCommand command){
        // 1. 查询作业，并检查当前状态和时间是否允许提交。
        LocalDateTime now=LocalDateTime.now();
        Assignment assignment=assignmentRepository.findById(command.assignmentId())
                .orElseThrow(()->new IllegalStateException("不存在相关作业"));

        if (!assignment.canAcceptSubmissions(now)) {
            throw new IllegalArgumentException("当前作业不能提交");
        }

        if (!Objects.equals(assignment.contentVersion(), command.contentVersion())) {
            throw new IllegalArgumentException("作业内容版本不匹配");
        }

        // 2. 查询上传对象元数据，并校验归属、版本、有效期、大小和摘要。
        SourceObject object=objectRepository.findByUploadId(command.uploadId())
                        .orElseThrow(()->new IllegalStateException("不能存在相关元数据"));
        sourceObjectValidator.validate(
                command.uploadId(),
                command.ownerId(),
                command.assignmentId(),
                command.contentVersion(),
                command.sha256()
        );

        // 3. 在计数器仓储中分配本次提交序号。
        long nextNo=assignmentSubmissionCounterRepository.nextAttemptNo(assignment, command.ownerId());

        // 4. 创建提交领域对象，初始状态为 ACCEPTED。
        Submission newSubmission=Submission.builder()
                .submissionId(UUID.randomUUID().toString())
                .ownerId(command.ownerId())
                .assignmentId(command.assignmentId())
                .contentVersion(command.contentVersion())
                .sourceUploadId(command.uploadId())
                .attemptNo(nextNo)
                .idempotencyKey(command.idempotencyKey())
                .sourceSha256(command.sha256())
                .sourceSizeBytes(object.sizeBytes())
                .status(SubmissionStatus.ACCEPTED)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();

        // 5. 持久化提交记录，并返回创建结果。
        submissionRepository.save(newSubmission);
        return newSubmission;
    }
}
