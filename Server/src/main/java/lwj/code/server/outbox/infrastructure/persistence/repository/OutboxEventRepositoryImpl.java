package lwj.code.server.outbox.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.outbox.application.OutboxEventRepository;
import lwj.code.server.outbox.domain.OutboxEvent;
import lwj.code.server.outbox.application.exception.OutboxPersistenceException;
import lwj.code.server.outbox.infrastructure.persistence.entity.OutboxEventEntity;
import lwj.code.server.outbox.infrastructure.persistence.mapper.OutboxEventMapper;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventMapper outboxEventMapper;

    /** 新增 Outbox 事件记录；调用方负责将其纳入业务创建事务。 */
    @Override
    public OutboxEvent save(OutboxEvent event) {
        validateEvent(event);

        OutboxEventEntity entity = toEntity(event);
        int rows = outboxEventMapper.insert(entity);
        if (rows != 1) {
            throw new OutboxPersistenceException(
                    "Outbox 事件写入失败，eventId=" + event.eventId() + ", affectedRows=" + rows
            );
        }
        return event;
    }

    private void validateEvent(OutboxEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Outbox 事件不能为空");
        }
        requireText(event.eventId(), "eventId");
        if (event.eventType() == null) {
            throw new IllegalArgumentException("eventType 不能为空");
        }
        if (event.aggregateType() == null) {
            throw new IllegalArgumentException("aggregateType 不能为空");
        }
        requireText(event.aggregateId(), "aggregateId");
        requireText(event.traceId(), "traceId");
        requireText(event.payloadJson(), "payloadJson");
        if (event.schemaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion 必须大于或等于 1");
        }
        if (event.aggregateRevision() < 0) {
            throw new IllegalArgumentException("aggregateRevision 不能小于 0");
        }
        if (event.status() == null) {
            throw new IllegalArgumentException("Outbox 事件状态不能为空");
        }
        if (event.attempts() < 0) {
            throw new IllegalArgumentException("attempts 不能小于 0");
        }
        if (event.occurredAt() == null || event.nextAttemptAt() == null || event.createdAt() == null) {
            throw new IllegalArgumentException("Outbox 事件发生时间、下次投递时间和创建时间不能为空");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private OutboxEventEntity toEntity(OutboxEvent event) {
        return OutboxEventEntity.builder()
                .eventId(event.eventId())
                .eventType(event.eventType().value())
                .schemaVersion(event.schemaVersion())
                .aggregateType(event.aggregateType().name())
                .aggregateId(event.aggregateId())
                .aggregateRevision(event.aggregateRevision())
                .traceId(event.traceId())
                .payloadJson(event.payloadJson())
                .occurredAt(event.occurredAt())
                .status(event.status().name())
                .attempts(event.attempts())
                .nextAttemptAt(event.nextAttemptAt())
                .claimToken(event.claimToken())
                .claimedUntil(event.claimedUntil())
                .lastErrorCode(event.lastErrorCode())
                .createdAt(event.createdAt())
                .publishedAt(event.publishedAt())
                .build();
    }
}
