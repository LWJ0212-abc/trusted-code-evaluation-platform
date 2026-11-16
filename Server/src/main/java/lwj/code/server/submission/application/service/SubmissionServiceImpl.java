package lwj.code.server.submission.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.submission.application.*;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.submission.application.exception.IdempotencyConflictException;
import lwj.code.server.submission.domain.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionCounterRepository assignmentSubmissionCounterRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionCreator submissionCreator;
    /**
     * 创建一次正式提交
     * @param command 包含创建提交所需全部上下文参数的命令对象（CQRS 模式中的 Command）
     * @return
     */
    @Override
    public Submission createSubmission(CreateSubmissionCommand command) {
        validateCommand(command);

        Optional<Submission> existing = findByIdempotencyKey(command);
        if (existing.isPresent()) {
            return returnIfSameRequest(existing.get(), command);
        }

        try {
            return submissionCreator.createSubmission(command);
        } catch (DuplicateKeyException e) {
            Optional<Submission> winner = findByIdempotencyKey(command);
            if (winner.isEmpty()) {
                throw e;
            }
            return returnIfSameRequest(winner.get(), command);
        }
    }
    private Submission returnIfSameRequest(
            Submission existing,
            CreateSubmissionCommand command
    ) {
        if (!existing.matchesRequest(
                command.contentVersion(),
                command.uploadId(),
                command.sha256()
        )) {
            throw new IdempotencyConflictException("幂等键已用于不同的提交请求");
        }
        return existing;
    }

    private Optional<Submission> findByIdempotencyKey(CreateSubmissionCommand command) {
        return submissionRepository.findByIdempotencyKey(
                command.ownerId(),
                command.assignmentId(), 
                command.idempotencyKey()
        );
    }


    @Override
    public Submission getSubmission(String submissionId, String ownerId) {
        if (submissionId==null||submissionId.isBlank()){
            throw new IllegalArgumentException("submissionId不能为空");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空");
        }
        Submission submission = submissionRepository.findById(submissionId).orElseThrow(
                () -> new IllegalStateException("不存在submissionId对应下的记录")
        );
        if (!submission.ownerId().equals(ownerId)) {
            throw new AccessDeniedException("无权访问该提交");
        }

        return submission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission updateStatus(String submissionId, SubmissionStatus status) {
        if (submissionId==null||submissionId.isBlank()){
            throw new IllegalArgumentException("submissionId不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        //* 查询submission
        Submission submission=submissionRepository.findById(submissionId)
                .orElseThrow(()->new IllegalStateException("该提交不存在"));

        //*使用record自身的状态转化
        LocalDateTime now=LocalDateTime.now();
        Submission newSubmission = submission.transitionTo(status, now);
        return submissionRepository.updateStatus(newSubmission);
    }

    private void validateCommand(CreateSubmissionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command 不能为空");
        }

        if (command.ownerId() == null || command.ownerId().isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空");
        }

        if (command.assignmentId() == null
                || command.assignmentId().isBlank()) {
            throw new IllegalArgumentException("assignmentId 不能为空");
        }

        if (command.uploadId() == null
                || command.uploadId().isBlank()) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }

        if (command.sha256() == null
                || command.sha256().isBlank()) {
            throw new IllegalArgumentException("sha256 不能为空");
        }

        if (command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        if (command.contentVersion()==null||
        command.contentVersion().isBlank()){
            throw new IllegalArgumentException("contentVersion不能为空");
        }
    }
}
