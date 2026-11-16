package lwj.code.server.submission.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import lwj.code.server.submission.application.SourceObjectRepository;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.infrastructure.objectstore.ObjectStoreException;
import lwj.code.server.submission.infrastructure.persistence.entity.SourceObjectEntity;
import lwj.code.server.submission.infrastructure.persistence.mapper.SourceObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class SourceObjectRepositoryImpl implements SourceObjectRepository {

    private final SourceObjectMapper sourceObjectMapper;

    @Override
    public Optional<SourceObject> findByUploadId(String uploadId) {
        if (uploadId==null||uploadId.isBlank()){
            throw new IllegalArgumentException("uploadId不能为空");
        }
        SourceObjectEntity entity=sourceObjectMapper.selectById(uploadId);
        return entity==null?
                Optional.empty()
                :Optional.of(toDomain(entity));
    }

    private SourceObject toDomain(SourceObjectEntity entity) {
        return new SourceObject(
                entity.getUploadId(),
                entity.getOwnerId(),
                entity.getAssignmentId(),
                entity.getContentVersion(),
                entity.getObjectBucket(),
                entity.getObjectKey(),
                entity.getObjectVersion(),
                entity.getSha256(),
                entity.getSizeBytes(),
                entity.getValidationStatus(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getValidatedAt()
        );
    }
}
