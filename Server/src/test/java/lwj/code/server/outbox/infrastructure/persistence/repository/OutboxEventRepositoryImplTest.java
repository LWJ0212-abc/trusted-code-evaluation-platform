package lwj.code.server.outbox.infrastructure.persistence.repository;

import lwj.code.server.outbox.application.exception.OutboxPersistenceException;
import lwj.code.server.outbox.domain.OutboxAggregateType;
import lwj.code.server.outbox.domain.OutboxEvent;
import lwj.code.server.outbox.domain.OutboxEventStatus;
import lwj.code.server.outbox.domain.OutboxEventType;
import lwj.code.server.outbox.infrastructure.persistence.entity.OutboxEventEntity;
import lwj.code.server.outbox.infrastructure.persistence.mapper.OutboxEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryImplTest {

    @Mock
    private OutboxEventMapper outboxEventMapper;

    private OutboxEventRepositoryImpl repository;
    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        repository = new OutboxEventRepositoryImpl(outboxEventMapper);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 18, 3, 30);
        event = OutboxEvent.builder()
                .eventId("018f0000-0000-7000-8000-000000000001")
                .eventType(OutboxEventType.EVALUATION_REQUESTED)
                .schemaVersion(1)
                .aggregateType(OutboxAggregateType.EVALUATION_TASK)
                .aggregateId("018f0000-0000-7000-8000-000000000002")
                .aggregateRevision(0L)
                .traceId("trace-test-01")
                .payloadJson("{\"submission_id\":\"submission-1\",\"evaluation_task_id\":\"task-1\",\"content_version\":\"content-v1\",\"batch_id\":null}")
                .occurredAt(occurredAt)
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(occurredAt)
                .claimToken(null)
                .claimedUntil(null)
                .lastErrorCode(null)
                .createdAt(occurredAt)
                .publishedAt(null)
                .build();
    }

    @Test
    void saveMapsEveryDomainFieldToEntityAndInsertsIt() {
        when(outboxEventMapper.insert(any(OutboxEventEntity.class))).thenReturn(1);

        OutboxEvent saved = repository.save(event);

        assertSame(event, saved);
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventMapper).insert(captor.capture());
        OutboxEventEntity entity = captor.getValue();

        assertEquals(event.eventId(), entity.getEventId());
        assertEquals("evaluation.requested", entity.getEventType());
        assertEquals(1, entity.getSchemaVersion());
        assertEquals("EVALUATION_TASK", entity.getAggregateType());
        assertEquals(event.aggregateId(), entity.getAggregateId());
        assertEquals(0L, entity.getAggregateRevision());
        assertEquals(event.traceId(), entity.getTraceId());
        assertEquals(event.payloadJson(), entity.getPayloadJson());
        assertEquals(event.occurredAt(), entity.getOccurredAt());
        assertEquals("PENDING", entity.getStatus());
        assertEquals(0, entity.getAttempts());
        assertEquals(event.nextAttemptAt(), entity.getNextAttemptAt());
        assertNull(entity.getClaimToken());
        assertNull(entity.getClaimedUntil());
        assertNull(entity.getLastErrorCode());
        assertEquals(event.createdAt(), entity.getCreatedAt());
        assertNull(entity.getPublishedAt());
    }

    @Test
    void saveRejectsNullEventWithoutCallingMapper() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));

        verifyNoInteractions(outboxEventMapper);
    }

    @Test
    void saveThrowsPersistenceExceptionWhenInsertDoesNotAffectExactlyOneRow() {
        when(outboxEventMapper.insert(any(OutboxEventEntity.class))).thenReturn(0);

        OutboxPersistenceException exception = assertThrows(
                OutboxPersistenceException.class,
                () -> repository.save(event)
        );

        assertEquals(
                "Outbox 事件写入失败，eventId=" + event.eventId() + ", affectedRows=0",
                exception.getMessage()
        );
    }
}
