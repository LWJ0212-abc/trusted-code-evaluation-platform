package lwj.code.server.submission.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lwj.code.server.submission.application.SubmissionRepository;
import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;
import lwj.code.server.submission.infrastructure.persistence.entity.SubmissionEntity;
import lwj.code.server.submission.infrastructure.persistence.mapper.SubmissionMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SubmissionRepositoryImpl implements SubmissionRepository {

    private final SubmissionMapper submissionMapper;

    /**
     * @param submission 待保存的提交实体对象
     * @return
     */
    @Override
    public Submission save(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("submission 不能为空");
        }

        SubmissionEntity entity=toEntity(submission);
        int rows=submissionMapper.insert(entity);
        if (rows!=1){
            throw new IllegalStateException("保存提交记录失败");
        }
        return submission;
    }

    @Override
    public Optional<Submission> findById(String submissionId) {
        if (submissionId==null||submissionId.isBlank()){
            throw new IllegalArgumentException("submissionId 不能为空");
        }
        SubmissionEntity entity=submissionMapper.selectById(submissionId);
        return entity==null?
                Optional.empty()
                :Optional.of(toDomain(entity));
    }


    @Override
    public Optional<Submission> findByIdempotencyKey(String ownerId, String assignmentId, String idempotencyKey) {
            if (ownerId==null||ownerId.isBlank()){
                throw new IllegalArgumentException("ownerId 不能为空");
            }
            if (assignmentId==null||assignmentId.isBlank()){
                throw new IllegalArgumentException("assignmentId 不能为空");
            }
            if (idempotencyKey==null||idempotencyKey.isBlank()){
                throw new IllegalArgumentException("idempotencyKey 不能为空");
            }

            SubmissionEntity entity=submissionMapper.selectOne(
                    Wrappers.<SubmissionEntity>lambdaQuery()
                            .eq(SubmissionEntity::getOwnerId,ownerId)
                            .eq(SubmissionEntity::getAssignmentId,assignmentId)
                            .eq(SubmissionEntity::getIdempotencyKey,idempotencyKey)
            );

            return entity==null?
                    Optional.empty()
                    :Optional.of(toDomain(entity));
    }

    @Override
    public Submission updateStatus(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("submission 不能为空");
        }
        if (submission.submissionId() == null
                || submission.submissionId().isBlank()) {
            throw new IllegalArgumentException("submissionId 不能为空");
        }
        if (submission.status() == null) {
            throw new IllegalArgumentException("status 不能为空");
        }

        SubmissionEntity entity = toEntity(submission);
        int rows = submissionMapper.update(
                entity,
                Wrappers.<SubmissionEntity>lambdaUpdate()
                        .eq(SubmissionEntity::getSubmissionId,submission.submissionId())
        );
        if (rows != 1) {
            throw new IllegalStateException(
                    "提交状态未更新，记录可能已被并发修改或已不存在"
            );
        }

        SubmissionEntity updatedEntity =
                submissionMapper.selectById(submission.submissionId());
        if (updatedEntity == null) {
            throw new IllegalStateException("提交更新后无法读取记录");
        }
        return toDomain(updatedEntity);
    }

    /**
     * @param entity
     * @return domain
     */
    private Submission toDomain(SubmissionEntity entity) {
        return new Submission(
                entity.getSubmissionId(),
                entity.getOwnerId(),
                entity.getAssignmentId(),
                entity.getContentVersion(),
                entity.getSourceUploadId(),
                entity.getAttemptNo(),
                entity.getIdempotencyKey(),
                entity.getSourceSha256(),
                entity.getSourceSizeBytes(),
                SubmissionStatus.valueOf(entity.getStatus()),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * @param submission
     * @return entity
     */
    private SubmissionEntity toEntity(Submission submission){
        return new SubmissionEntity(
                submission.submissionId(),
                submission.ownerId(),
                submission.assignmentId(),
                submission.contentVersion(),
                submission.sourceUploadId(),
                submission.attemptNo(),
                submission.idempotencyKey(),
                submission.sourceSha256(),
                submission.sourceSizeBytes(),
                submission.status().name(),
                submission.submittedAt(),
                submission.createdAt(),
                submission.updatedAt(),
                submission.version()
        );
    }
}
