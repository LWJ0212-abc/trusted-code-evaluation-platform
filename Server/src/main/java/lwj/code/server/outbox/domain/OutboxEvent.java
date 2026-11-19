package lwj.code.server.outbox.domain;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Outbox 中待发布或已发布的事件记录。
 *
 * <p>该对象描述事件信封数据及其投递进度。{@code payloadJson} 只保存事件 payload；
 * 发布器根据本记录的其他字段组装完整事件信封。</p>
 */
@Builder
public record OutboxEvent(
        /** 事件唯一标识，重试和重投时保持不变。 */
        String eventId,

        /** 事件类型，例如 {@code evaluation.requested}。 */
        OutboxEventType eventType,

        /** 事件契约版本，例如 v1 对应的数值 1。 */
        int schemaVersion,

        /** 聚合类型，例如 {@code EVALUATION_TASK}。 */
        OutboxAggregateType aggregateType,

        /** 聚合标识；评测事件使用 evaluation_task_id。 */
        String aggregateId,

        /** 事件描述的聚合版本，对应 evaluation_task.revision。 */
        long aggregateRevision,

        /** 请求链路追踪 ID，不用于身份认证或授权。 */
        String traceId,

        /** JSON 序列化后的 payload，不包含外层事件信封字段。 */
        String payloadJson,

        /** 事件发生时间，按 UTC 语义存储。 */
        LocalDateTime occurredAt,

        /** Outbox 事件的发布状态。 */
        OutboxEventStatus status,

        /** 已尝试向消息代理发布的次数。 */
        int attempts,

        /** 下次允许尝试发布的时间。 */
        LocalDateTime nextAttemptAt,

        /** 当前发布器领取事件时生成的令牌；未领取时为 null。 */
        String claimToken,

        /** 当前领取租约截止时间；未领取时为 null。 */
        LocalDateTime claimedUntil,

        /** 最近一次失败的安全错误码；没有失败时为 null。 */
        String lastErrorCode,

        /** Outbox 记录创建时间。 */
        LocalDateTime createdAt,

        /** 收到 RabbitMQ Publisher Confirm 的时间；未发布时为 null。 */
        LocalDateTime publishedAt
) {
}
