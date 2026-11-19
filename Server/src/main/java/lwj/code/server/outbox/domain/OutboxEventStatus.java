package lwj.code.server.outbox.domain;

/** Outbox 事件的发布状态。 */
public enum OutboxEventStatus {

    /** 事件已持久化，等待发布器领取。 */
    PENDING,

    /** 事件已被发布器领取，正在尝试发布。 */
    PUBLISHING,

    /** RabbitMQ 已确认接收事件。 */
    PUBLISHED,

    /** 发布重试耗尽或事件不可恢复，等待运维处理。 */
    DEAD
}
