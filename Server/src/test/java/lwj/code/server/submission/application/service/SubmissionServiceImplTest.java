package lwj.code.server.submission.application.service;

import lwj.code.server.submission.application.SourceObjectValidator;
import lwj.code.server.submission.application.SubmissionRepository;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.submission.application.exception.IdempotencyConflictException;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.domain.Submission;
import lwj.code.server.submission.domain.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final Clock TEST_CLOCK = Clock.fixed(
            TEST_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private static final String SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionCreator submissionCreator;

    @Mock
    private SourceObjectValidator sourceObjectValidator;

    private SubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubmissionServiceImpl(
                submissionRepository,
                submissionCreator,
                sourceObjectValidator,
                TEST_CLOCK
        );
    }

    @Test
    void createSubmissionReturnsExistingSubmissionForSameIdempotentRequest() {
        CreateSubmissionCommand command = command("upload-1", SHA256);
        Submission previous = previousSubmission();
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.of(previous));

        Submission result = service.createSubmission(command, "trace-test");

        assertSame(previous, result);
        verify(submissionRepository).findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        );
        verify(submissionCreator, never()).createSubmission(eq(command), any(SourceObject.class), eq("trace-test"));
        verify(sourceObjectValidator, never()).validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        );
    }

    @Test
    void createSubmissionRejectsDifferentRequestWithExistingIdempotencyKey() {
        CreateSubmissionCommand command = command("upload-2", SHA256);
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.of(previousSubmission()));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.createSubmission(command, "trace-test")
        );

        verify(submissionRepository).findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        );
        verify(submissionCreator, never()).createSubmission(eq(command), any(SourceObject.class), eq("trace-test"));
        verify(sourceObjectValidator, never()).validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        );
    }

    @Test
    void createSubmissionDelegatesNewRequestToCreator() {
        CreateSubmissionCommand command = command("upload-1", SHA256);
        Submission created = previousSubmission();
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.empty());
        SourceObject validatedObject = validatedObject();
        when(sourceObjectValidator.validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        )).thenReturn(validatedObject);
        when(submissionCreator.createSubmission(command, validatedObject, "trace-test")).thenReturn(created);

        Submission result = service.createSubmission(command, "trace-test");

        assertSame(created, result);
        verify(sourceObjectValidator).validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        );
        verify(submissionCreator).createSubmission(command, validatedObject, "trace-test");

        InOrder order = inOrder(submissionRepository, sourceObjectValidator, submissionCreator);
        order.verify(submissionRepository).findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        );
        order.verify(sourceObjectValidator).validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        );
        order.verify(submissionCreator).createSubmission(command, validatedObject, "trace-test");
    }

    @Test
    void createSubmissionReturnsWinnerAfterConcurrentDuplicateKey() {
        CreateSubmissionCommand command = command("upload-1", SHA256);
        Submission winner = previousSubmission();
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.empty(), Optional.of(winner));
        SourceObject validatedObject = validatedObject();
        when(sourceObjectValidator.validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        )).thenReturn(validatedObject);
        when(submissionCreator.createSubmission(command, validatedObject, "trace-test"))
                .thenThrow(new DuplicateKeyException("duplicate idempotency key"));

        Submission result = service.createSubmission(command, "trace-test");

        assertSame(winner, result);
        verify(submissionRepository, org.mockito.Mockito.times(2)).findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        );
    }

    @Test
    void createSubmissionRejectsDifferentRequestAfterConcurrentDuplicateKey() {
        CreateSubmissionCommand command = command("upload-2", SHA256);
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.empty(), Optional.of(previousSubmission()));
        SourceObject validatedObject = validatedObject();
        when(sourceObjectValidator.validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        )).thenReturn(validatedObject);
        when(submissionCreator.createSubmission(command, validatedObject, "trace-test"))
                .thenThrow(new DuplicateKeyException("duplicate idempotency key"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.createSubmission(command, "trace-test")
        );
    }

    @Test
    void createSubmissionRethrowsDuplicateKeyWhenNoWinnerExists() {
        CreateSubmissionCommand command = command("upload-1", SHA256);
        DuplicateKeyException duplicate = new DuplicateKeyException("another unique constraint");
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.empty(), Optional.empty());
        SourceObject validatedObject = validatedObject();
        when(sourceObjectValidator.validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        )).thenReturn(validatedObject);
        when(submissionCreator.createSubmission(command, validatedObject, "trace-test")).thenThrow(duplicate);

        DuplicateKeyException thrown = assertThrows(
                DuplicateKeyException.class,
                () -> service.createSubmission(command, "trace-test")
        );

        assertSame(duplicate, thrown);
    }

    @Test
    void createSubmissionDoesNotCallCreatorWhenSourceValidationFails() {
        CreateSubmissionCommand command = command("upload-1", SHA256);
        when(submissionRepository.findByIdempotencyKey(
                "owner-1", "assignment-1", "idempotency-1"
        )).thenReturn(Optional.empty());
        when(sourceObjectValidator.validate(
                command.uploadId(), command.ownerId(), command.assignmentId(), command.contentVersion(), command.sha256()
        )).thenThrow(new IllegalArgumentException("上传对象校验失败"));

        assertThrows(IllegalArgumentException.class, () -> service.createSubmission(command, "trace-test"));

        verify(submissionCreator, never()).createSubmission(eq(command), any(SourceObject.class), eq("trace-test"));
    }

    private static CreateSubmissionCommand command(String uploadId, String sha256) {
        return new CreateSubmissionCommand(
                "owner-1",
                "assignment-1",
                "content-v1",
                uploadId,
                sha256,
                "idempotency-1"
        );
    }

    private static Submission previousSubmission() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 11, 14, 12, 0);
        return new Submission(
                "submission-1",
                "owner-1",
                "assignment-1",
                "content-v1",
                "upload-1",
                1L,
                "idempotency-1",
                SHA256,
                128L,
                SubmissionStatus.ACCEPTED,
                submittedAt,
                submittedAt,
                submittedAt,
                0L
        );
    }

    private static SourceObject validatedObject() {
        return new SourceObject(
                "upload-1", "owner-1", "assignment-1", "content-v1", "source-bucket",
                "uploads/upload-1.zip", "version-1", SHA256, 128L, "VALIDATED",
                null, TEST_NOW, TEST_NOW
        );
    }
}
