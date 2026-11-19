package lwj.code.server.submission.application.service;

import lwj.code.server.api.UuidGenerator;
import lwj.code.server.course.application.AssignmentRepository;
import lwj.code.server.course.domain.Assignment;
import lwj.code.server.course.domain.AssignmentStatus;
import lwj.code.server.submission.application.AssignmentSubmissionCounterRepository;
import lwj.code.server.submission.application.EvaluationTaskRepository;
import lwj.code.server.submission.application.SubmissionRepository;
import lwj.code.server.submission.application.command.CreateSubmissionCommand;
import lwj.code.server.outbox.application.OutboxEventRepository;
import lwj.code.server.outbox.domain.OutboxEventStatus;
import lwj.code.server.outbox.domain.OutboxEventType;
import lwj.code.server.outbox.domain.OutboxEvent;
import lwj.code.server.submission.domain.EvaluationTask;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.domain.Submission;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(SubmissionCreatorTransactionTest.TestConfiguration.class)
class SubmissionCreatorTransactionTest {

    private static final LocalDateTime TEST_NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubmissionCreator submissionCreator;

    @Autowired
    private AtomicBoolean failOutboxPersistence;

    @BeforeEach
    void createTables() {
        failOutboxPersistence.set(true);
        jdbcTemplate.execute("DROP TABLE IF EXISTS task_probe");
        jdbcTemplate.execute("DROP TABLE IF EXISTS outbox_probe");
        jdbcTemplate.execute("DROP TABLE IF EXISTS submission_probe");
        jdbcTemplate.execute("DROP TABLE IF EXISTS counter_probe");
        jdbcTemplate.execute("CREATE TABLE counter_probe (attempt_no BIGINT NOT NULL, assignment_id VARCHAR(36), owner_id VARCHAR(36))");
        jdbcTemplate.execute("CREATE TABLE submission_probe (submission_id VARCHAR(36) NOT NULL, owner_id VARCHAR(36), assignment_id VARCHAR(36), content_version VARCHAR(64), source_upload_id VARCHAR(36), attempt_no BIGINT, idempotency_key VARCHAR(128), source_sha256 VARCHAR(64), source_size_bytes BIGINT)");
        jdbcTemplate.execute("CREATE TABLE task_probe (task_id VARCHAR(36) NOT NULL, submission_id VARCHAR(36), assignment_id VARCHAR(36), student_id VARCHAR(36), content_version VARCHAR(64), status VARCHAR(32), revision BIGINT, attempt_count INT)");
        jdbcTemplate.execute("CREATE TABLE outbox_probe (event_id VARCHAR(36) NOT NULL, event_type VARCHAR(128), schema_version INT, aggregate_type VARCHAR(64), aggregate_id VARCHAR(36), aggregate_revision BIGINT, trace_id VARCHAR(128), payload_json VARCHAR(2000), occurred_at TIMESTAMP, status VARCHAR(16), attempts INT, next_attempt_at TIMESTAMP, claim_token VARCHAR(36), claimed_until TIMESTAMP, last_error_code VARCHAR(128), created_at TIMESTAMP, published_at TIMESTAMP)");
    }

    @Test
    void persistsCounterSubmissionTaskAndOutboxWithExpectedValues() {
        failOutboxPersistence.set(false);
        CreateSubmissionCommand command = command();
        SourceObject sourceObject = sourceObject();

        Submission submission = submissionCreator.createSubmission(command, sourceObject, "trace-success");

        assertEquals(1, countRows("counter_probe"));
        assertEquals(1, countRows("submission_probe"));
        assertEquals(1, countRows("task_probe"));
        assertEquals(1, countRows("outbox_probe"));

        var counter = jdbcTemplate.queryForMap("SELECT * FROM counter_probe");
        assertEquals(1L, ((Number) counter.get("attempt_no")).longValue());
        assertEquals("assignment-1", counter.get("assignment_id"));
        assertEquals("owner-1", counter.get("owner_id"));

        var submissionRow = jdbcTemplate.queryForMap("SELECT * FROM submission_probe");
        assertEquals(submission.submissionId(), submissionRow.get("submission_id"));
        assertEquals("owner-1", submissionRow.get("owner_id"));
        assertEquals("assignment-1", submissionRow.get("assignment_id"));
        assertEquals("upload-1", submissionRow.get("source_upload_id"));
        assertEquals("content-v1", submissionRow.get("content_version"));
        assertEquals(1L, ((Number) submissionRow.get("attempt_no")).longValue());
        assertEquals("idempotency-1", submissionRow.get("idempotency_key"));
        assertEquals("a".repeat(64), submissionRow.get("source_sha256"));
        assertEquals(128L, ((Number) submissionRow.get("source_size_bytes")).longValue());

        var task = jdbcTemplate.queryForMap("SELECT * FROM task_probe");
        assertEquals(submission.submissionId(), task.get("submission_id"));
        assertEquals("assignment-1", task.get("assignment_id"));
        assertEquals("owner-1", task.get("student_id"));
        assertEquals("content-v1", task.get("content_version"));
        assertEquals("PENDING", task.get("status"));
        assertEquals(0L, ((Number) task.get("revision")).longValue());
        assertEquals(0, ((Number) task.get("attempt_count")).intValue());

        var outbox = jdbcTemplate.queryForMap("SELECT * FROM outbox_probe");
        assertEquals(OutboxEventType.EVALUATION_REQUESTED.value(), outbox.get("event_type"));
        assertEquals(1, ((Number) outbox.get("schema_version")).intValue());
        assertEquals("EVALUATION_TASK", outbox.get("aggregate_type"));
        assertEquals(task.get("task_id"), outbox.get("aggregate_id"));
        assertEquals(0L, ((Number) outbox.get("aggregate_revision")).longValue());
        assertEquals("trace-success", outbox.get("trace_id"));
        assertEquals(OutboxEventStatus.PENDING.name(), outbox.get("status"));
        assertEquals(0, ((Number) outbox.get("attempts")).intValue());
        assertEquals(outbox.get("occurred_at"), outbox.get("created_at"));
        assertEquals(outbox.get("occurred_at"), outbox.get("next_attempt_at"));
        assertEquals(null, outbox.get("claim_token"));
        assertEquals(null, outbox.get("claimed_until"));
        assertEquals(null, outbox.get("last_error_code"));
        assertEquals(null, outbox.get("published_at"));
        assertEquals(
                "{\"submission_id\":\"" + submission.submissionId()
                        + "\",\"evaluation_task_id\":\"" + task.get("task_id")
                        + "\",\"content_version\":\"content-v1\",\"batch_id\":null}",
                outbox.get("payload_json")
        );
    }

    @Test
    void rollsBackCounterSubmissionTaskAndOutboxWhenOutboxPersistenceFails() {
        CreateSubmissionCommand command = new CreateSubmissionCommand(
                "owner-1",
                "assignment-1",
                "content-v1",
                "upload-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "idempotency-1"
        );
        SourceObject sourceObject = new SourceObject(
                "upload-1", "owner-1", "assignment-1", "content-v1", "source-bucket",
                "uploads/upload-1.zip", "version-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                128L, "VALIDATED", null, TEST_NOW, TEST_NOW
        );

        assertThrows(IllegalStateException.class,
                () -> submissionCreator.createSubmission(command, sourceObject, "trace-test"));

        assertEquals(0, countRows("counter_probe"));
        assertEquals(0, countRows("submission_probe"));
        assertEquals(0, countRows("task_probe"));
        assertEquals(0, countRows("outbox_probe"));
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:submission-transaction;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        AssignmentRepository assignmentRepository() {
            Assignment openAssignment = new Assignment(
                    "assignment-1", "course-1", "Assignment", "Description", "content-v1",
                    "teacher-1", AssignmentStatus.PUBLISHED, null, TEST_NOW.plusHours(1),
                    3, TEST_NOW.minusDays(1), TEST_NOW
            );
            return new AssignmentRepository() {
                @Override
                public Optional<Assignment> findById(String assignmentId) {
                    return Optional.of(openAssignment);
                }

                @Override
                public Assignment save(Assignment assignment) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Assignment update(Assignment assignment) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        AssignmentSubmissionCounterRepository counterRepository(JdbcTemplate jdbcTemplate) {
            return (assignment, ownerId) -> {
                jdbcTemplate.update(
                        "INSERT INTO counter_probe (attempt_no, assignment_id, owner_id) VALUES (?, ?, ?)",
                        1L, assignment.assignmentId(), ownerId
                );
                return 1L;
            };
        }

        @Bean
        SubmissionRepository submissionRepository(JdbcTemplate jdbcTemplate) {
            return new SubmissionRepository() {
                @Override
                public Submission save(Submission submission) {
                    jdbcTemplate.update(
                            "INSERT INTO submission_probe (submission_id, owner_id, assignment_id, content_version, source_upload_id, attempt_no, idempotency_key, source_sha256, source_size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            submission.submissionId(), submission.ownerId(), submission.assignmentId(),
                            submission.contentVersion(), submission.sourceUploadId(), submission.attemptNo(),
                            submission.idempotencyKey(), submission.sourceSha256(), submission.sourceSizeBytes()
                    );
                    return submission;
                }

                @Override
                public Optional<Submission> findById(String submissionId) {
                    return Optional.empty();
                }

                @Override
                public Optional<Submission> findByIdempotencyKey(
                        String ownerId, String assignmentId, String idempotencyKey) {
                    return Optional.empty();
                }

                @Override
                public Submission updateStatus(Submission submission) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        EvaluationTaskRepository evaluationTaskRepository(JdbcTemplate jdbcTemplate) {
            return task -> {
                jdbcTemplate.update(
                        "INSERT INTO task_probe (task_id, submission_id, assignment_id, student_id, content_version, status, revision, attempt_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        task.evaluationTaskId(), task.submissionId(), task.assignmentId(), task.studentId(),
                        task.contentVersion(), task.status().name(), task.revision(), task.attemptCount()
                );
                return task;
            };
        }

        @Bean
        OutboxEventRepository outboxEventRepository(
                JdbcTemplate jdbcTemplate,
                AtomicBoolean failOutboxPersistence) {
            return event -> {
                jdbcTemplate.update(
                        "INSERT INTO outbox_probe (event_id, event_type, schema_version, aggregate_type, aggregate_id, aggregate_revision, trace_id, payload_json, occurred_at, status, attempts, next_attempt_at, claim_token, claimed_until, last_error_code, created_at, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        event.eventId(), event.eventType().value(), event.schemaVersion(), event.aggregateType().name(),
                        event.aggregateId(), event.aggregateRevision(), event.traceId(), event.payloadJson(),
                        event.occurredAt(), event.status().name(), event.attempts(), event.nextAttemptAt(),
                        event.claimToken(), event.claimedUntil(), event.lastErrorCode(), event.createdAt(), event.publishedAt()
                );
                if (failOutboxPersistence.get()) {
                    throw new IllegalStateException("simulated outbox persistence failure");
                }
                return event;
            };
        }

        @Bean
        AtomicBoolean failOutboxPersistence() {
            return new AtomicBoolean(true);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        UuidGenerator uuidGenerator() {
            return new UuidGenerator();
        }

        @Bean
        Clock applicationClock() {
            return TEST_CLOCK;
        }

        @Bean
        SubmissionCreator submissionCreator(
                AssignmentRepository assignmentRepository,
                SubmissionRepository submissionRepository,
                AssignmentSubmissionCounterRepository counterRepository,
                UuidGenerator uuidGenerator,
                EvaluationTaskRepository evaluationTaskRepository,
                ObjectMapper objectMapper,
                OutboxEventRepository outboxEventRepository,
                Clock clock) {
            return new SubmissionCreator(
                    assignmentRepository,
                    submissionRepository,
                    counterRepository,
                    uuidGenerator,
                    evaluationTaskRepository,
                    objectMapper,
                    outboxEventRepository,
                    clock
            );
        }
    }

    private static CreateSubmissionCommand command() {
        return new CreateSubmissionCommand(
                "owner-1", "assignment-1", "content-v1", "upload-1",
                "a".repeat(64), "idempotency-1"
        );
    }

    private static SourceObject sourceObject() {
        return new SourceObject(
                "upload-1", "owner-1", "assignment-1", "content-v1", "source-bucket",
                "uploads/upload-1.zip", "version-1", "a".repeat(64), 128L,
                "VALIDATED", null, TEST_NOW, TEST_NOW
        );
    }
}
