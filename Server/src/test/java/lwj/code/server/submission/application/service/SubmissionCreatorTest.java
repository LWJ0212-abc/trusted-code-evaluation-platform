package lwj.code.server.submission.application.service;

import lwj.code.server.api.UuidGenerator;
import lwj.code.server.course.application.AssignmentRepository;
import lwj.code.server.submission.application.AssignmentSubmissionCounterRepository;
import lwj.code.server.submission.application.EvaluationTaskRepository;
import lwj.code.server.submission.application.SubmissionRepository;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.course.domain.Assignment;
import lwj.code.server.course.domain.AssignmentStatus;
import lwj.code.server.submission.domain.EvaluationTask;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.outbox.application.OutboxEventRepository;
import lwj.code.server.outbox.domain.OutboxAggregateType;
import lwj.code.server.outbox.domain.OutboxEvent;
import lwj.code.server.outbox.domain.OutboxEventStatus;
import lwj.code.server.outbox.domain.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class SubmissionCreatorTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentSubmissionCounterRepository counterRepository;

    @Mock
    private UuidGenerator uuidGenerator;

    @Mock
    private EvaluationTaskRepository evaluationTaskRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;

    private SubmissionCreator creator;

    private SourceObject validatedObject;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        creator = new SubmissionCreator(
                assignmentRepository,
                submissionRepository,
                counterRepository,
                uuidGenerator,
                evaluationTaskRepository,
                objectMapper,
                outboxEventRepository,
                TEST_CLOCK
        );
        validatedObject = validSourceObject();
    }

    @Test
    void createRejectsClosedAssignmentEvenWhenContentVersionMatches() {
        CreateSubmissionCommand command = command("content-v1");
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.CLOSED,
                        "content-v1",
                        TEST_NOW.plusHours(1)
                )));

        assertThrows(IllegalArgumentException.class, () -> creator.createSubmission(command, validatedObject, "trace-test"));

        verifyNoInteractions(counterRepository, submissionRepository, uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsVersionMismatchForOpenAssignmentBeforeLoadingSourceObject() {
        CreateSubmissionCommand command = command("content-v1");
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.PUBLISHED,
                        "content-v2",
                        TEST_NOW.plusHours(1)
                )));
        assertThrows(IllegalArgumentException.class, () -> creator.createSubmission(command, validatedObject, "trace-test"));

        verifyNoInteractions(counterRepository, submissionRepository, uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsClosedAssignmentWithDifferentContentVersion() {
        CreateSubmissionCommand command = command("content-v1");
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.CLOSED,
                        "content-v2",
                        TEST_NOW.plusHours(1)
                )));
        assertThrows(IllegalArgumentException.class, () -> creator.createSubmission(command, validatedObject, "trace-test"));

        verifyNoInteractions(counterRepository, submissionRepository, uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsAssignmentPastDeadline() {
        CreateSubmissionCommand command = command("content-v1");
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.PUBLISHED,
                        "content-v1",
                        TEST_NOW.minusMinutes(1)
                )));

        assertThrows(IllegalArgumentException.class, () -> creator.createSubmission(command, validatedObject, "trace-test"));

        verifyNoInteractions(counterRepository, submissionRepository, uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createUsesValidatedObjectAndPersistsSubmission() {
        CreateSubmissionCommand command = command("content-v1");
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.PUBLISHED,
                        "content-v1",
                        TEST_NOW.plusHours(1)
                )));
        when(counterRepository.nextAttemptNo(any(Assignment.class), eq("owner-1"))).thenReturn(1L);
        when(uuidGenerator.nextUuid7())
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000001"))
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000002"))
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000003"));

        var result = creator.createSubmission(command, validatedObject, "trace-test");

        assertEquals(128L, result.sourceSizeBytes());
        assertEquals("upload-1", result.sourceUploadId());
        verify(submissionRepository).save(result);
        verify(evaluationTaskRepository).save(any(EvaluationTask.class));
        verify(outboxEventRepository).save(argThat(event ->
                event.eventId().equals("018f0000-0000-7000-8000-000000000003")
                        && event.eventType() == OutboxEventType.EVALUATION_REQUESTED
                        && event.schemaVersion() == 1
                        && event.aggregateType() == OutboxAggregateType.EVALUATION_TASK
                        && event.aggregateId().equals("018f0000-0000-7000-8000-000000000002")
                        && event.aggregateRevision() == 0
                        && event.traceId().equals("trace-test")
                        && event.status() == OutboxEventStatus.PENDING
                        && event.attempts() == 0
                        && event.claimToken() == null
                        && event.claimedUntil() == null
                        && event.lastErrorCode() == null
                        && event.publishedAt() == null
                        && event.occurredAt().equals(event.createdAt())
                        && event.occurredAt().equals(event.nextAttemptAt())
                        && event.payloadJson().equals("{\"submission_id\":\"018f0000-0000-7000-8000-000000000001\",\"evaluation_task_id\":\"018f0000-0000-7000-8000-000000000002\",\"content_version\":\"content-v1\",\"batch_id\":null}")
        ));
    }

    @Test
    void createRejectsNullCommandWithClearMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> creator.createSubmission(null, validatedObject, "trace-test")
        );

        assertEquals("创建提交命令不能为空", exception.getMessage());
        verifyNoInteractions(assignmentRepository, counterRepository, submissionRepository,
                uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsBlankTraceIdBeforePerformingPersistence() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> creator.createSubmission(command("content-v1"), validatedObject, " ")
        );

        assertEquals("traceId 不能为空", exception.getMessage());
        verifyNoInteractions(assignmentRepository, counterRepository, submissionRepository,
                uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsNullValidatedObjectBeforeReadingAssignment() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> creator.createSubmission(command("content-v1"), null, "trace-test")
        );

        assertEquals("已校验的源对象 validatedObject 不能为空", exception.getMessage());
        verifyNoInteractions(assignmentRepository, counterRepository, submissionRepository,
                uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createRejectsValidatedObjectForDifferentUpload() {
        SourceObject mismatchedObject = new SourceObject(
                "another-upload", validatedObject.ownerId(), validatedObject.assignmentId(),
                validatedObject.contentVersion(), validatedObject.objectBucket(), validatedObject.objectKey(),
                validatedObject.objectVersion(), validatedObject.sha256(), validatedObject.sizeBytes(),
                validatedObject.validationStatus(), validatedObject.expiresAt(),
                validatedObject.createdAt(), validatedObject.validatedAt()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> creator.createSubmission(command("content-v1"), mismatchedObject, "trace-test")
        );

        assertEquals("已校验源对象的 uploadId 与提交命令不一致", exception.getMessage());
        verifyNoInteractions(assignmentRepository, counterRepository, submissionRepository,
                uuidGenerator, evaluationTaskRepository, outboxEventRepository);
    }

    @Test
    void createAcceptsSha256WithDifferentLetterCase() {
        CreateSubmissionCommand uppercaseHashCommand = new CreateSubmissionCommand(
                "owner-1", "assignment-1", "content-v1", "upload-1",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "idempotency-1"
        );
        when(assignmentRepository.findById("assignment-1"))
                .thenReturn(Optional.of(assignment(
                        AssignmentStatus.PUBLISHED,
                        "content-v1",
                        TEST_NOW.plusHours(1)
                )));
        when(counterRepository.nextAttemptNo(any(Assignment.class), eq("owner-1"))).thenReturn(1L);
        when(uuidGenerator.nextUuid7())
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000003"))
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000004"))
                .thenReturn(UUID.fromString("018f0000-0000-7000-8000-000000000005"));

        var result = creator.createSubmission(uppercaseHashCommand, validatedObject, "trace-test");

        assertEquals(validatedObject.sha256(), result.sourceSha256());
        verify(submissionRepository).save(result);
    }

    private static CreateSubmissionCommand command(String contentVersion) {
        return new CreateSubmissionCommand(
                "owner-1",
                "assignment-1",
                contentVersion,
                "upload-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "idempotency-1"
        );
    }

    private static Assignment assignment(
            AssignmentStatus status,
            String contentVersion,
            LocalDateTime dueAt
    ) {
        return new Assignment(
                "assignment-1",
                "course-1",
                "Assignment",
                "Description",
                contentVersion,
                "teacher-1",
                status,
                null,
                dueAt,
                3,
                TEST_NOW.minusDays(1),
                TEST_NOW
        );
    }

    private static SourceObject validSourceObject() {
        return new SourceObject(
                "upload-1",
                "owner-1",
                "assignment-1",
                "content-v1",
                "tcep-source",
                "uploads/upload-1.zip",
                null,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                128L,
                "VALIDATED",
                null,
                TEST_NOW.minusMinutes(1),
                TEST_NOW.minusMinutes(1)
        );
    }
}
