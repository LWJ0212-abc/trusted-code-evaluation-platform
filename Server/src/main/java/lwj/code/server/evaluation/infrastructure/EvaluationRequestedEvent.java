package lwj.code.server.evaluation.infrastructure;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * {@code evaluation.requested} v1 事件信封。
 *
 * <p>该 DTO 仅表示跨服务传输契约，不负责发送消息或推进评测任务状态。</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationRequestedEvent(
        /** 事件唯一标识；消息重投时保持不变。 */
        String eventId,

        /** 固定为 {@code evaluation.requested}。 */
        String eventType,

        /** 固定为契约版本 1。 */
        int schemaVersion,

        /** RFC 3339 UTC 时间字符串，必须以 {@code Z} 结尾。 */
        String occurredAt,

        /** 评测任务聚合标识，必须等于 payload 中的 evaluationTaskId。 */
        String aggregateId,

        /** 任务 revision 的十进制字符串，避免 JSON 数字精度损失。 */
        String aggregateRevision,

        /** 请求追踪 ID，不得用于身份认证或授权。 */
        String traceId,

        /** 评测任务引用和内容版本信息。 */
        EvaluationRequestedPayload payload
) {
}
