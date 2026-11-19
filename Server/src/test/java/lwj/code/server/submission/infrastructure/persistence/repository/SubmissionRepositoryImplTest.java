package lwj.code.server.submission.infrastructure.persistence.repository;

import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;
import lwj.code.server.submission.infrastructure.persistence.entity.SubmissionEntity;
import lwj.code.server.submission.infrastructure.persistence.mapper.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionRepositoryImplTest {

    @Mock
    private SubmissionMapper submissionMapper;

    private SubmissionRepositoryImpl repository;
    private Submission submission;
    private SubmissionEntity entity;

    @BeforeEach
    void setUp() {
        repository = new SubmissionRepositoryImpl(submissionMapper);

        LocalDateTime now = LocalDateTime.now();
        submission = new Submission(
                "submission-1",
                "owner-1",
                "assignment-1",
                "content-v1",
                "upload-1",
                1L,
                "request-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                128L,
                SubmissionStatus.ACCEPTED,
                now,
                now,
                now,
                0L
        );

        entity = new SubmissionEntity(
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

    @Test
    void saveInsertsEntityAndReturnsSubmission() {
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenReturn(1);

        Submission result = repository.save(submission);

        assertEquals(submission, result);

        ArgumentCaptor<SubmissionEntity> captor =
                ArgumentCaptor.forClass(SubmissionEntity.class);
        verify(submissionMapper).insert(captor.capture());
        assertEquals(submission.submissionId(), captor.getValue().getSubmissionId());
        assertEquals(submission.status().name(), captor.getValue().getStatus());
        assertEquals(submission.sourceUploadId(), captor.getValue().getSourceUploadId());
    }

    @Test
    void saveRejectsNullSubmission() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    void findByIdReturnsDomainObjectWhenEntityExists() {
        when(submissionMapper.selectById("submission-1")).thenReturn(entity);

        Optional<Submission> result = repository.findById("submission-1");

        assertEquals(Optional.of(submission), result);
        verify(submissionMapper).selectById("submission-1");
    }

    @Test
    void findByIdReturnsEmptyWhenEntityDoesNotExist() {
        when(submissionMapper.selectById("submission-1")).thenReturn(null);

        Optional<Submission> result = repository.findById("submission-1");

        assertEquals(Optional.empty(), result);
    }

    @Test
    void findByIdRejectsBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findById(" ")
        );
    }

    @Test
    void findByIdempotencyKeyReturnsDomainObjectWhenEntityExists() {
        when(submissionMapper.selectOne(any())).thenReturn(entity);

        Optional<Submission> result = repository.findByIdempotencyKey(
                "owner-1", "assignment-1", "request-1"
        );

        assertEquals(Optional.of(submission), result);
        verify(submissionMapper).selectOne(any());
    }

    @Test
    void findByIdempotencyKeyReturnsEmptyWhenEntityDoesNotExist() {
        when(submissionMapper.selectOne(any())).thenReturn(null);

        Optional<Submission> result = repository.findByIdempotencyKey(
                "owner-1", "assignment-1", "request-1"
        );

        assertEquals(Optional.empty(), result);
    }

    @Test
    void findByIdempotencyKeyRejectsBlankArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByIdempotencyKey(
                        "", "assignment-1", "request-1"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByIdempotencyKey(
                        "owner-1", "", "request-1"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByIdempotencyKey(
                        "owner-1", "assignment-1", ""
                )
        );
    }

    @Test
    void updateStatusReturnsPersistedSubmissionWithIncrementedVersion() {
        SubmissionEntity updatedEntity = new SubmissionEntity(
                entity.getSubmissionId(),
                entity.getOwnerId(),
                entity.getAssignmentId(),
                entity.getContentVersion(),
                entity.getSourceUploadId(),
                entity.getAttemptNo(),
                entity.getIdempotencyKey(),
                entity.getSourceSha256(),
                entity.getSourceSizeBytes(),
                SubmissionStatus.QUEUED.name(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt().plusSeconds(1),
                1L
        );
        when(submissionMapper.selectById("submission-1"))
                .thenReturn(updatedEntity);
        when(submissionMapper.update(any(SubmissionEntity.class), any()))
                .thenReturn(1);

        Submission result = repository.updateStatus(
                submission.transitionTo(
                        SubmissionStatus.QUEUED,
                        entity.getUpdatedAt().plusSeconds(1)
                )
        );

        assertEquals(SubmissionStatus.QUEUED, result.status());
        assertEquals(1L, result.version());
        verify(submissionMapper).update(any(SubmissionEntity.class), any());
    }
}
