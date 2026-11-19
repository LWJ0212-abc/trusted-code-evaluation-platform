package lwj.code.server.outbox.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox 事件记录的持久化实体。
 *
 * <p>映射 {@code outbox_event} 表。该对象只表示数据库中的待投递或已投递事件记录，
 * 不负责构造 RabbitMQ 消息、发布事件或管理业务聚合状态。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("outbox_event")
public class OutboxEventEntity {

    /** 事件唯一标识；消息重试和 RabbitMQ 重投时保持不变。 */
    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;

    /** 事件类型，例如 {@code evaluation.requested}。 */
    @TableField("event_type")
    private String eventType;

    /** 事件契约版本，例如 v1 对应的数值 1。 */
    @TableField("schema_version")
    private Integer schemaVersion;

    /** 聚合类型，例如 {@code EVALUATION_TASK}。 */
    @TableField("aggregate_type")
    private String aggregateType;

    /** 聚合标识；评测事件使用 evaluation_task_id。 */
    @TableField("aggregate_id")
    private String aggregateId;

    /** 事件描述的聚合版本，对应评测任务的 revision。 */
    @TableField("aggregate_revision")
    private Long aggregateRevision;

    /** 请求链路追踪 ID；不用于身份认证或授权。 */
    @TableField("trace_id")
    private String traceId;

    /** JSON 序列化后的事件 payload，不包含外层事件信封字段。 */
    @TableField("payload_json")
    private String payloadJson;

    /** 事件发生时间，按 UTC 语义存储。 */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    /** 投递状态：PENDING、PUBLISHING、PUBLISHED 或 DEAD。 */
    @TableField("status")
    private String status;

    /** 已尝试向消息代理发布的次数。 */
    @TableField("attempts")
    private Integer attempts;

    /** 下次允许尝试发布的时间。 */
    @TableField("next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 当前发布器领取事件时生成的令牌，用于确认领取所有权。 */
    @TableField("claim_token")
    private String claimToken;

    /** 当前领取租约的截止时间；过期后其他发布器可重新领取。 */
    @TableField("claimed_until")
    private LocalDateTime claimedUntil;

    /** 最近一次发布失败的安全错误码，不保存凭证或完整异常堆栈。 */
    @TableField("last_error_code")
    private String lastErrorCode;

    /** Outbox 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** RabbitMQ 确认成功后的时间。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
